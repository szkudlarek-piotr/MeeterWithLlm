package org.example.meeter.photos;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Fixed category assets only, never arbitrary files from the photo directory. */
@RestController
@RequestMapping("/api/places/category-icons")
public class PlaceCategoryIconController {
    private final Path directory;
    public PlaceCategoryIconController(@Value("${app.photos.place-categories-directory:./data/photos/placeCategories}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> icon(@PathVariable String filename) {
        if (!filename.matches("[A-Za-z0-9_-]{1,80}\\.png")) throw missing();
        try {
            Path root = directory.toRealPath();
            Path file = root.resolve(filename).toRealPath();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw missing();
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache()).header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
        } catch (IOException exception) { throw missing(); }
    }
    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono ikony kategorii.");
    }
}
