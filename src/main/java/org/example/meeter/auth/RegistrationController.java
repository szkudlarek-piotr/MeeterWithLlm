package org.example.meeter.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {
    private final RegistrationService registration;

    RegistrationController(RegistrationService registration) {
        this.registration = registration;
    }

    public record Invitation(String token) {}
    record RegisterInput(@NotBlank @Size(max = 512) String token,
                         @NotBlank @Size(max = 64) String username,
                         @NotBlank @Email @Size(max = 254) String email,
                         @NotBlank @Size(min = 12, max = 72) String password) {}
    record VerifyInput(@NotBlank @Pattern(regexp = "[a-f0-9]{64}") String token) {}

    @PostMapping("/invitations/{humanId}")
    ResponseEntity<Invitation> invite(@PathVariable Long humanId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registration.invite(humanId));
    }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterInput input) {
        registration.register(input);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyInput input) {
        registration.verifyEmail(input.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
