package org.example.meeter.photos;

import java.io.*;
import java.net.*;
import java.util.Locale;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.*;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RemotePhotoClient {
    public static final int MAX_BYTES = 4 * 1024 * 1024;
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread=new Thread(task,"photo-download-timeout"); thread.setDaemon(true); return thread;
    });
    @PreDestroy public void close() { deadlines.shutdownNow(); }
    private record Result(byte[] bytes, String redirect) {}
    private static final DnsResolver PUBLIC_DNS = new DnsResolver() {
        @Override public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) if (!PhotoUrlPolicy.isPublic(address))
                throw new UnknownHostException("Dozwolone są wyłącznie publiczne adresy internetowe.");
            return addresses;
        }
        @Override public String resolveCanonicalHostname(String host) { return host; }
    };

    public byte[] download(URI initial) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
        try (var client = HttpClients.custom().useSystemProperties().disableRedirectHandling().disableAutomaticRetries()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().useSystemProperties().setDnsResolver(PUBLIC_DNS)
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(5)).setSocketTimeout(Timeout.ofSeconds(8)).build()).build())
            .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(8)).setContentCompressionEnabled(false).build()).build()) {
            URI current = initial;
            for (int redirects = 0; redirects <= 3; redirects++) {
                current = PhotoUrlPolicy.validate(current.toString());
                // Biblioteka omija DnsResolver dla literałów IP, więc sprawdzamy je osobno.
                String host = current.getHost().replace("[", "").replace("]", "");
                if ((host.contains(":") || host.matches("[0-9.]+")) && !PhotoUrlPolicy.isPublic(InetAddress.getByName(host)))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zdjęcie musi pochodzić z publicznego serwera.");
                var request = new HttpGet(current);
                request.setHeader("Accept", "image/jpeg,image/png,image/gif");
                request.setHeader("User-Agent", "Meeter-PhotoImporter/1.0");
                long remaining=deadline-System.nanoTime();
                if(remaining<=0) throw new IOException("Przekroczono czas pobierania.");
                ScheduledFuture<?> cancellation=deadlines.schedule(request::cancel,remaining,TimeUnit.NANOSECONDS);
                Result result;
                try { result = client.execute(request, response -> {
                    int code = response.getCode();
                    if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                        var location = response.getFirstHeader("Location");
                        if (location == null) throw new IOException("Brak adresu przekierowania.");
                        request.cancel(); response.setEntity(null);
                        return new Result(null, location.getValue());
                    }
                    if (code != 200 || response.getEntity() == null) throw new IOException("Serwer nie zwrócił zdjęcia.");
                    var entity = response.getEntity();
                    String contentType = entity.getContentType();
                    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/"))
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adres musi prowadzić bezpośrednio do pliku zdjęcia, nie do strony WWW.");
                    if (entity.getContentLength() > MAX_BYTES) throw tooLarge();
                    try (var stream = entity.getContent()) {
                        byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                        if (bytes.length > MAX_BYTES) throw tooLarge();
                        return new Result(bytes, null);
                    }
                }); } finally { cancellation.cancel(false); }
                if (result.bytes() != null) return result.bytes();
                URI next = current.resolve(result.redirect());
                if ("https".equalsIgnoreCase(current.getScheme()) && !"https".equalsIgnoreCase(next.getScheme()))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Przekierowanie nie może obniżać bezpieczeństwa HTTPS.");
                current = next;
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zbyt wiele przekierowań do zdjęcia.");
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Nie udało się pobrać zdjęcia. Sprawdź publiczny link do pliku JPG lub PNG.");
        }
    }
    private static ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Zdjęcie może mieć maksymalnie 4 MB.");
    }
}
