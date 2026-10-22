package org.example.meeter.photos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.file.*;
import org.example.meeter.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/people/{id}/photo")
public class PhotoController {
    private final MeeterService service;
    private final PhotoStorage storage;
    public PhotoController(MeeterService service, PhotoStorage storage) { this.service=service; this.storage=storage; }
    public record PhotoInput(@Size(max=2048) String url, @Size(max=5592408) String photoData) {}
    @PostMapping public ApiModels.HumanView update(@PathVariable Long id,@Valid @RequestBody PhotoInput input) {
        boolean url = input.url()!=null && !input.url().isBlank();
        boolean data = input.photoData()!=null && !input.photoData().isBlank();
        if (url == data) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Wybierz jedno źródło zdjęcia.");
        if (url) return service.updatePhoto(id,input.url());
        byte[] bytes;
        try { bytes=java.util.Base64.getDecoder().decode(input.photoData()); }
        catch(IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nieprawidłowy plik zdjęcia."); }
        return service.updatePhotoBytes(id,bytes);
    }
    @GetMapping public ResponseEntity<Resource> image(@PathVariable Long id) {
        return imageForHuman(service.ownHuman(id));
    }
    public ResponseEntity<Resource> imageForHuman(Human human) {
        var photo=human.getProfilePhoto();
        if(photo!=null && photo.getPhotoType()!=org.example.meeter.multimedia.photo.PhotoType.PROFILE_PICTURE)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono zdjęcia.");
        Path file=photo==null ? storage.path(human.getProfilePhotoFile()) : storage.importedPath(photo.getPhotoName());
        if(!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono zdjęcia.");
        MediaType type=MediaType.IMAGE_JPEG;
        if(photo!=null) {
            try (var input=javax.imageio.ImageIO.createImageInputStream(file.toFile())) {
                var readers=javax.imageio.ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw new java.io.IOException();
                var reader=readers.next();
                try {
                    type=switch(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)) {
                        case "jpeg", "jpg" -> MediaType.IMAGE_JPEG;
                        case "png" -> MediaType.IMAGE_PNG;
                        case "gif" -> MediaType.IMAGE_GIF;
                        default -> throw new java.io.IOException();
                    };
                } finally { reader.dispose(); }
            } catch(java.io.IOException exception) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono zdjęcia.");
            }
        }
        return ResponseEntity.ok().contentType(type)
            .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
            .body(new FileSystemResource(file));
    }
}
