package org.example.meeter.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    private final CurrentAccount current;
    private final boolean secure;
    public AuthController(AuthService service, CurrentAccount current, @Value("${app.auth.secure-cookie:false}") boolean secure) { this.service = service; this.current = current; this.secure = secure; }
    public record LoginInput(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 72) String password) {}
    public record PasswordInput(@NotBlank @Size(max = 72) String currentPassword, @NotBlank @Size(min = 12, max = 72) String newPassword) {}
    public record CsrfView(String token, String headerName) {}

    @GetMapping("/csrf") public ResponseEntity<CsrfView> csrf(CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new CsrfView(token.getToken(), token.getHeaderName()));
    }
    @GetMapping("/me") public ResponseEntity<CurrentAccount.Principal> me() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(current.principal()); }
    @PostMapping("/login") public ResponseEntity<CurrentAccount.Principal> login(@Valid @RequestBody LoginInput input, HttpServletRequest request) {
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Hasło może mieć maksymalnie 72 bajty UTF-8.");
        var result = service.login(input.username(), input.password(), request.getRemoteAddr(), TokenAuthenticationFilter.cookie(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE, cookie(result.rawToken(), AuthService.LIFETIME_SECONDS)).body(result.user());
    }
    @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request) {
        service.revoke(TokenAuthenticationFilter.cookie(request));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }
    @PostMapping("/password") public ResponseEntity<Void> password(@Valid @RequestBody PasswordInput input) {
        if (input.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || input.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Hasło może mieć maksymalnie 72 bajty UTF-8.");
        service.changePassword(current.id(), input.currentPassword(), input.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }
    private String cookie(String token, long age) {
        return ResponseCookie.from("meeterAuth", token).httpOnly(true).secure(secure).sameSite("Lax").path("/api").maxAge(age).build().toString();
    }
}
