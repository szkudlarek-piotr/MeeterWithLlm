package org.example.meeter.superpowers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/superpowers") @Validated
public class SuperpowerController {
    private final SuperpowerService service;
    private final SuperpowerPhotoStorage photos;

    public SuperpowerController(SuperpowerService service, SuperpowerPhotoStorage photos) {
        this.service = service;
        this.photos = photos;
    }

    public record View(Long id, String superpowerName, String photoUrl) {
        static View of(Superpower superpower) {
            return new View(superpower.getId(), superpower.getSuperpowerName(), superpower.photoUrl());
        }
    }

    public record Input(@NotBlank @Size(max = 255) String superpowerName) {}

    @GetMapping
    public List<View> list() {
        return service.list().stream().map(View::of).toList();
    }

    @GetMapping("/{id}")
    public View get(@PathVariable @Positive Long id) {
        return View.of(service.get(id));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable @Positive Long id) {
        var photo = service.get(id).getSuperpowerPhoto();
        if (photo == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono zdjęcia.");
        Path path = photos.path(photo.getPhotoName());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff").body(new FileSystemResource(path));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Input input) {
        return View.of(service.create(input.superpowerName()));
    }
}
