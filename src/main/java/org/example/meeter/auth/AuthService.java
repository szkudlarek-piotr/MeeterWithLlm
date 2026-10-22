package org.example.meeter.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final TokenRepository tokens;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final String dummyHash;
    public static final long LIFETIME_SECONDS = 24 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    public record LoginResult(String rawToken, CurrentAccount.Principal user) {}

    public AuthService(UserRepository users, TokenRepository tokens, PasswordEncoder encoder, LoginThrottle throttle) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.throttle = throttle;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String username, String password, String address, String previousToken) {
        username = username.trim().toLowerCase(Locale.ROOT);
        throttle.check(username, address);
        User user = users.findByUsername(username).orElse(null);
        boolean matches = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy login lub hasło.");
        throttle.success(username);
        revoke(previousToken);
        tokens.deleteByExpiresAtBefore(Instant.now());
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        tokens.save(new AuthToken(hash(raw), Instant.now().plusSeconds(LIFETIME_SECONDS), user));
        return new LoginResult(raw, CurrentAccount.of(user));
    }

    @Transactional(readOnly = true)
    public Optional<CurrentAccount.Principal> authenticate(String raw) {
        if (!valid(raw)) return Optional.empty();
        return tokens.findByTokenHashAndExpiresAtAfter(hash(raw), Instant.now()).map(token -> CurrentAccount.of(token.getOwner()));
    }
    @Transactional
    public void revoke(String raw) { if (valid(raw)) tokens.deleteByTokenHash(hash(raw)); }

    @Transactional
    public void changePassword(Long userId, String current, String next) {
        User user = users.findById(userId).orElseThrow();
        if (!encoder.matches(current, user.getPasswordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowe obecne hasło.");
        user.changePassword(encoder.encode(next));
        tokens.deleteByOwnerId(userId);
    }
    public static boolean valid(String raw) { return raw != null && raw.matches("[a-f0-9]{64}"); }
    public static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
