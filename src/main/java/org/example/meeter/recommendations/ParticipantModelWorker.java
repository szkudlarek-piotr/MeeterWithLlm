package org.example.meeter.recommendations;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Owns only the worker it starts; external workers retain their own lifecycle. */
@Component
public class ParticipantModelWorker {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(ParticipantModelWorker.class);
    private final boolean autostart;
    private final Path directory;
    private final Path python;
    private final int port;
    private final String token;
    private final boolean external;
    private final Starter starter;
    private Process process;
    private boolean attempted;
    private boolean verifyStartup;

    @FunctionalInterface interface Starter { Process start(ProcessBuilder builder) throws IOException; }

    @Autowired
    public ParticipantModelWorker(@Value("${app.recommendations.autostart:false}") boolean autostart,
            @Value("${app.recommendations.directory:../experiments/participants}") String directory,
            @Value("${app.recommendations.python:}") String python,
            @Value("${app.recommendations.port:8092}") int port,
            @Value("${app.recommendations.token:}") String token) {
        this(autostart, Path.of(directory), python, port, token, ProcessBuilder::start);
        verifyStartup = true;
    }

    ParticipantModelWorker(boolean autostart, Path directory, String python, int port, String token, Starter starter) {
        this.autostart = autostart;
        Path resolved = directory.toAbsolutePath().normalize();
        if (directory.normalize().equals(Path.of("../experiments/participants").normalize()) && !Files.isRegularFile(resolved.resolve("server.py"))
                && Files.isRegularFile(Path.of("experiments/participants/server.py")))
            resolved = Path.of("experiments/participants").toAbsolutePath().normalize();
        this.directory = resolved;
        this.python = python.isBlank() ? this.directory.resolve(System.getProperty("os.name").startsWith("Windows")
            ? ".venv/Scripts/python.exe" : ".venv/bin/python") : Path.of(python).toAbsolutePath().normalize();
        this.port = port;
        external = !autostart && !token.isBlank();
        if (!token.isBlank() && (token.length() < 32 || !token.chars().allMatch(c -> c >= 33 && c <= 126)))
            throw new IllegalArgumentException("MEETER_RECOMMENDER_TOKEN musi mieć minimum 32 drukowalne znaki ASCII.");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Nieprawidłowy port modeli.");
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.token = !token.isBlank() ? token : Base64.getEncoder().encodeToString(bytes);
        this.starter = starter;
    }

    @PostConstruct void start() {
        if (!autostart || external) return;
        if (!Files.isRegularFile(python) || !Files.isRegularFile(directory.resolve("server.py")) || !hasModels()) {
            log.info("Automatyczny start modeli pominięty: brak środowiska Python, server.py lub wytrenowanych modeli.");
            return;
        }
        attempted = true;
        if (verifyStartup && healthy()) {
            log.info("Połączono z działającą usługą modeli uczestników.");
            return;
        }
        try {
            var artifacts = directory.resolve("artifacts");
            var builder = new ProcessBuilder(python.toString(), directory.resolve("server.py").toString(),
                "--port", Integer.toString(port), "--managed").directory(directory.toFile());
            // The model worker has no database access requirement. Token is never a command-line argument.
            builder.environment().keySet().removeIf(key -> key.startsWith("DB_") || key.startsWith("NEO4J_"));
            builder.environment().put("MEETER_RECOMMENDER_TOKEN", token);
            builder.redirectOutput(artifacts.resolve("worker.out.log").toFile());
            builder.redirectError(artifacts.resolve("worker.err.log").toFile());
            process = starter.start(builder);
            if (verifyStartup) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
                while (process.isAlive() && System.nanoTime() < deadline && !healthy()) {
                    try { Thread.sleep(500); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
                if (!healthy()) {
                    log.warn("Usługa modeli nie osiągnęła gotowości. Sprawdź artifacts/worker.err.log.");
                    return;
                }
            }
            log.info("Uruchomiono lokalną usługę modeli uczestników na porcie {}.", port);
        } catch (IOException exception) {
            log.warn("Nie udało się uruchomić modeli uczestników. Sprawdź środowisko Python i katalog artifacts.");
        }
    }

    private boolean healthy() {
        try {
            var client = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(1)).build();
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(java.time.Duration.ofSeconds(2)).header("Authorization", "Bearer " + token).GET().build();
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception exception) { return false; }
    }

    private boolean hasModels() {
        if (List.of("logistic", "extra_trees", "boosting").stream().anyMatch(model ->
                Files.isRegularFile(directory.resolve("artifacts").resolve("shared_" + model + ".joblib")))) return true;
        return List.of("context", "guests").stream().anyMatch(mode ->
            List.of("logistic", "extra_trees", "boosting").stream().anyMatch(model ->
                Files.isRegularFile(directory.resolve("artifacts").resolve(mode + "_" + model + ".joblib"))));
    }

    public boolean enabled() { return external || attempted; }
    public String token() { return token; }
    public int port() { return port; }

    @PreDestroy void stop() {
        if (process == null) return;
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }
}
