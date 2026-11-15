package org.example.meeter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.example.meeter.photos.*;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") @Validated
public class CliqueDirectoryController {
    private final CliqueDirectory directory;
    private final CliquePhotoStorage photos;
    private final CliquePhotoImport importer;
    public CliqueDirectoryController(CliqueDirectory directory,CliquePhotoStorage photos,CliquePhotoImport importer) {
        this.directory=directory; this.photos=photos; this.importer=importer;
    }
    @GetMapping("/cliques/browse")
    public CliqueDirectory.Page browse(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="12") @Min(1) @Max(100) int size) {
        return directory.browse(page,size);
    }
    @GetMapping("/cliques/{id}/photo")
    public ResponseEntity<Resource> photo(@PathVariable @Positive Long id) {
        String filename=directory.photoFilename(id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").body(new FileSystemResource(photos.path(filename)));
    }
    public record ImportInput(@NotBlank @Size(max=4096) String folder) {}
    @PostMapping("/admin/cliques/photos/import")
    public CliquePhotoImport.Report importPhotos(@Valid @RequestBody ImportInput input) {
        return importer.importFolder(input.folder());
    }
}
