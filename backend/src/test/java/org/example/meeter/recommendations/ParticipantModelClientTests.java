package org.example.meeter.recommendations;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParticipantModelClientTests {
    @Test void sendsFixedLengthJsonForModelsAndSuggestions() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        record Request(String path, String length, String transfer, String type, String authorization, byte[] body) {}
        var requests = new CopyOnWriteArrayList<Request>();
        server.createContext("/", exchange -> {
            var headers = exchange.getRequestHeaders();
            requests.add(new Request(exchange.getRequestURI().getPath(), headers.getFirst("Content-Length"),
                headers.getFirst("Transfer-Encoding"), headers.getFirst("Content-Type"),
                headers.getFirst("Authorization"), exchange.getRequestBody().readAllBytes()));
            byte[] reply = (exchange.getRequestURI().getPath().equals("/models")
                ? "{\"models\":[]}" : "{\"human_ids\":[123]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, reply.length);
            try (var output = exchange.getResponseBody()) { output.write(reply); }
        });
        server.start();
        try {
            var worker = mock(ParticipantModelWorker.class);
            when(worker.enabled()).thenReturn(true);
            when(worker.port()).thenReturn(server.getAddress().getPort());
            when(worker.token()).thenReturn("synthetic-test-token");
            var client = new ParticipantModelClient(worker);
            assertTrue(client.models(1L, 2L).isEmpty());
            assertEquals(List.of(123L), client.suggest(Map.of("name", "Zażółć gęślą jaźń", "history", List.of())));
            assertEquals(List.of("/models", "/suggest"), requests.stream().map(Request::path).toList());
            for (var request : requests) {
                assertEquals(Integer.toString(request.body().length), request.length());
                assertNull(request.transfer());
                assertEquals("application/json", request.type());
                assertEquals("Bearer synthetic-test-token", request.authorization());
            }
            assertTrue(new String(requests.get(1).body(), StandardCharsets.UTF_8).contains("Zażółć gęślą jaźń"));
        } finally {
            server.stop(0);
        }
    }
}
