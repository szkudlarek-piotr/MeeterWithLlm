package org.example.meeter.photos;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CliquePhotoStorage {
    private final EventPhotoStorage images;
    private final Path directory;
    public CliquePhotoStorage(@Value("${app.photos.cliques-directory:./data/photos/cliques}") String directory) {
        this.directory=Path.of(directory).toAbsolutePath().normalize();
        this.images=new EventPhotoStorage(directory);
    }
    public String store(byte[] bytes) { return images.store(bytes); }
    public Path path(String filename) {
        Path path=images.path(filename);
        try {
            if(Files.isRegularFile(path) && path.toRealPath().startsWith(directory.toRealPath())) return path;
        } catch(java.io.IOException ignored) { }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono zdjęcia.");
    }
}
