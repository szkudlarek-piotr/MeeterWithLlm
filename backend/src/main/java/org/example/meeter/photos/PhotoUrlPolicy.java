package org.example.meeter.photos;

import java.net.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PhotoUrlPolicy {
    private PhotoUrlPolicy() {}
    public static URI validate(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (value.length() > 2048 || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getFragment() != null ||
                !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) ||
                port != -1 && port != ("https".equalsIgnoreCase(scheme) ? 443 : 80)) throw new IllegalArgumentException();
            String host=uri.getHost().replace("[", "").replace("]", "");
            if(host.contains(":") || host.matches("[0-9.]+")) {
                try { if(!isPublic(InetAddress.getByName(host))) throw new IllegalArgumentException(); }
                catch(UnknownHostException exception) { throw new IllegalArgumentException(); }
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj bezpośredni adres HTTP/HTTPS do zdjęcia, bez danych logowania i niestandardowego portu.");
        }
    }

    public static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (bytes.length == 4) {
            return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 169 && b == 254 ||
                a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168 || a == 100 && b >= 64 && b <= 127 ||
                a == 192 && b == 0 || a == 192 && b == 2 || a == 198 && (b == 18 || b == 19 || b == 51) || a == 203 && b == 0);
        }
        // Wyłącznie globalne IPv6; bez tuneli 6to4/Teredo i prefiksu dokumentacyjnego.
        return (a & 224) == 32 && !(a == 32 && b == 2) &&
            !(a == 32 && b == 1 && ((bytes[2] == 0 && bytes[3] == 0) || (bytes[2] & 255) == 13 && (bytes[3] & 255) == 184));
    }
}
