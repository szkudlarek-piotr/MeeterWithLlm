package org.example.meeter.superpowers;

import java.io.IOException;
import java.nio.file.*;
import org.example.meeter.photos.EventPhotoStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Stores superpower photos as re-encoded JPEG files named {photo-uuid}.jpg. */
@Service
public class SuperpowerPhotoStorage {
    private final EventPhotoStorage images;

    public SuperpowerPhotoStorage(@Value("${app.photos.superpowers-directory:./data/photos/superpowers}") String directory) {
        this.images = new EventPhotoStorage(directory);
    }

    public Path path(String name) {
        Path path = images.path(name);
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        return path;
    }

    public String storeAs(byte[] bytes, String name) {
        String temporary = images.store(bytes);
        Path source = images.path(temporary);
        Path target = images.path(name);
        try {
            Files.move(source, target);
        } catch (IOException failure) {
            try { Files.deleteIfExists(source); } catch (IOException ignored) { /* Orphan cleanup can be retried later. */ }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nie udało się zapisać zdjęcia.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { Files.deleteIfExists(target); } catch (IOException ignored) { /* Orphan cleanup can be retried later. */ }
                }
            }
        });
        return name;
    }
}
