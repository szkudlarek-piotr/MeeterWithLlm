package org.example.meeter.superpowers;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api") @Validated
public class SuperpowerPhotoController {
    public record ImportInput(@NotBlank @Size(max = 4096) String folder, boolean dryRun) {}
    private final SuperpowerPhotoImport importer;

    public SuperpowerPhotoController(SuperpowerPhotoImport importer) { this.importer = importer; }

    @PostMapping("/admin/superpowers/photos/import")
    public SuperpowerPhotoImport.Report importPhotos(@Valid @RequestBody ImportInput input) {
        return importer.importFolder(input.folder(), input.dryRun());
    }
}
