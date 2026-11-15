package org.example.meeter.recommendations;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ParticipantModelWorkerTests {
    @TempDir Path directory;

    private Path files() throws Exception {
        var python = Files.createFile(directory.resolve("python.exe"));
        Files.createFile(directory.resolve("server.py"));
        Files.createDirectories(directory.resolve("artifacts"));
        Files.createFile(directory.resolve("artifacts/context_logistic.joblib"));
        return python;
    }

    @Test void startsWithPrivateTokenAndStopsOnlyOwnProcess() throws Exception {
        var python = files();
        var process = mock(Process.class);
        when(process.waitFor(3, TimeUnit.SECONDS)).thenReturn(true);
        var captured = new AtomicReference<ProcessBuilder>();
        var worker = new ParticipantModelWorker(true, directory, python.toString(), 8092, "",
            builder -> { captured.set(builder); return process; });
        worker.start();
        assertTrue(worker.enabled());
        assertTrue(worker.token().length() >= 32);
        var builder = captured.get();
        assertTrue(builder.command().contains("--managed"));
        assertFalse(builder.command().contains(worker.token()));
        assertEquals(worker.token(), builder.environment().get("MEETER_RECOMMENDER_TOKEN"));
        assertTrue(builder.environment().keySet().stream().noneMatch(k -> k.startsWith("DB_") || k.startsWith("NEO4J_")));
        worker.stop();
        verify(process).destroy();
        verify(process, never()).destroyForcibly();
    }

    @Test void missingFilesAndDisabledAutostartDoNotSpawnPython() {
        var worker = new ParticipantModelWorker(true, directory, "", 8092, "",
            builder -> { fail("Must not spawn"); return null; });
        worker.start();
        assertFalse(worker.enabled());
        var disabled = new ParticipantModelWorker(false, directory, "", 8092, "",
            builder -> { fail("Must not spawn"); return null; });
        disabled.start();
        assertFalse(disabled.enabled());
    }

    @Test void externalTokenReusesWorkerWithoutOwningIt() {
        var worker = new ParticipantModelWorker(false, directory, "", 8092, "x".repeat(32),
            builder -> { fail("Must not spawn external worker"); return null; });
        worker.start();
        worker.stop();
        assertTrue(worker.enabled());
        assertEquals("x".repeat(32), worker.token());
    }

    @Test void configuredTokenDoesNotDisableAutomaticStartup() throws Exception {
        var python = files();
        var captured = new AtomicReference<ProcessBuilder>();
        var worker = new ParticipantModelWorker(true, directory, python.toString(), 8092, "x".repeat(32),
            builder -> { captured.set(builder); return mock(Process.class); });
        worker.start();
        assertNotNull(captured.get());
        assertEquals("x".repeat(32), captured.get().environment().get("MEETER_RECOMMENDER_TOKEN"));
    }

    @Test void failedStartLeavesUsefulUnavailableStateAndStubbornChildIsKilled() throws Exception {
        var python = files();
        var failed = new ParticipantModelWorker(true, directory, python.toString(), 8092, "",
            builder -> { throw new java.io.IOException("Synthetic failure"); });
        failed.start();
        assertTrue(failed.enabled()); // Client surfaces 503 rather than claiming no models exist.
        var process = mock(Process.class);
        var worker = new ParticipantModelWorker(true, directory, python.toString(), 8092, "", builder -> process);
        worker.start();
        worker.stop();
        verify(process).destroyForcibly();
    }
}
