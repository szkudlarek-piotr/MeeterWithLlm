package org.example.meeter.photos;

import java.nio.file.Files;
import org.example.meeter.interactions.InteractionService;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class EventPhotoController {
    private final InteractionService service;
    private final EventPhotoStorage storage;
    public EventPhotoController(InteractionService service, EventPhotoStorage storage) { this.service = service; this.storage = storage; }
    @GetMapping("/api/events/{id}/photo")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        var file = storage.path(service.ownEvent(id).getPhotoFile());
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(file));
    }
}
