package org.example.meeter.recommendations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ParticipantModelClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ParticipantModelClient.class);
    public record Model(String id, String mode, boolean recommended) {
        public Model(String id, String mode) { this(id, mode, false); }
    }
    public record Models(List<Model> models) {}
    public record Trained(int trained) {}
    public record Suggestions(List<Long> human_ids) {}
    private final RestClient client;
    private final ParticipantModelWorker worker;
    private final JsonMapper json = JsonMapper.builder().build();

    public ParticipantModelClient(ParticipantModelWorker worker) {
        this.worker = worker;
        var http = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(120));
        client = RestClient.builder().baseUrl("http://127.0.0.1:" + worker.port())
            .requestFactory(factory).defaultHeader("Authorization", "Bearer " + worker.token()).build();
    }

    public List<Model> models(Long accountId, Long selfId) {
        if (!worker.enabled()) return List.of();
        try {
            var response = post("/models", Map.of("account_id", accountId, "self_id", selfId))
                .retrieve().body(Models.class);
            if (response == null || response.models() == null) throw new IllegalStateException();
            return response.models();
        } catch (Exception exception) { diagnose("models", exception); throw unavailable(); }
    }

    public List<Long> suggest(Map<String, Object> request) {
        if (!worker.enabled()) throw unavailable();
        try {
            var response = post("/suggest", request).retrieve().body(Suggestions.class);
            if (response == null || response.human_ids() == null || response.human_ids().size() > 20)
                throw new IllegalStateException();
            return response.human_ids();
        } catch (Exception exception) { diagnose("suggest", exception); throw unavailable(); }
    }

    /** Learns new interactions in the worker without ranking. Returns how many were learned; 0 means up to date. */
    public int train(Map<String, Object> request) {
        if (!worker.enabled()) return 0;
        try {
            var response = post("/train", request).retrieve().body(Trained.class);
            return response == null ? 0 : response.trained();
        } catch (Exception exception) { diagnose("train", exception); return 0; }
    }

    private RestClient.RequestHeadersSpec<?> post(String path, Map<String, ?> request) {
        // Python's HTTPServer expects a fixed-length body, not chunked transfer encoding.
        byte[] body = json.writeValueAsBytes(request);
        return client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
            .contentLength(body.length).body(body);
    }

    private void diagnose(String operation, Exception exception) {
        // Exception messages/bodies may contain request data. Log only types and HTTP status.
        Throwable cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        int status = exception instanceof org.springframework.web.client.RestClientResponseException response
            ? response.getStatusCode().value() : 0;
        log.warn("Participant models: operation={}, errorType={}, causeType={}, httpStatus={}",
            operation, exception.getClass().getSimpleName(), cause.getClass().getSimpleName(), status);
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Modele podpowiedzi są niedostępne. Spróbuj ponownie za chwilę; jeśli błąd wraca, sprawdź logi usługi modeli.");
    }
}
