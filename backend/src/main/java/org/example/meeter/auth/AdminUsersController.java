package org.example.meeter.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.example.meeter.quote.QuoteModels.AuthorView;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/users") @Validated
public class AdminUsersController {
    private final AdminUsers service;
    public AdminUsersController(AdminUsers service) { this.service=service; }
    public record Input(@NotNull @Positive Long humanId,@NotBlank @Size(max=64) String username) {
        @Override public String toString() { return "CreateUserInput[redacted]"; }
    }
    @GetMapping("/people")
    public ResponseEntity<List<AuthorView>> people(@RequestParam(defaultValue="") @Size(max=200) String q,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.candidates(q,limit));
    }
    @PostMapping
    public ResponseEntity<AdminUsers.Created> create(@Valid @RequestBody Input input) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
            .body(service.create(input.humanId(),input.username()));
    }
}
