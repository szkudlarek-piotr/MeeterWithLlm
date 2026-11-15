package org.example.meeter.interactions;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/participation-backfill") @Validated
public class ParticipationBackfillController {
    private final ParticipationBackfill service;
    public ParticipationBackfillController(ParticipationBackfill service) { this.service=service; }
    @GetMapping public ParticipationBackfill.Preview preview() { return service.preview(); }
    @PostMapping public ParticipationBackfill.Result apply() { return service.apply(); }
}
