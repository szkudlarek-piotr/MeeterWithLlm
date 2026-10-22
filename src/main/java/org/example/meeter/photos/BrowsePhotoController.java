package org.example.meeter.photos;

import org.example.meeter.MeeterService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Read-only portraits for the authenticated, shared people directory. */
@RestController
public class BrowsePhotoController {
    private final MeeterService service;
    private final PhotoController photos;
    public BrowsePhotoController(MeeterService service, PhotoController photos) { this.service=service; this.photos=photos; }
    @GetMapping("/api/people/browse/{id}/photo")
    public ResponseEntity<Resource> image(@PathVariable Long id) { return photos.imageForHuman(service.browseHuman(id)); }
}
