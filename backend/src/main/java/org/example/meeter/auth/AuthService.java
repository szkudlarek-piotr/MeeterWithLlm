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
    private final AuthAudit audit;
    private final String dummyHash;
    public static final long LIFETIME_SECONDS = 24 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();
    public record LoginResult(String rawToken, CurrentAccount.Principal user) {}

    public AuthService(UserRepository users, TokenRepository tokens, PasswordEncoder encoder, LoginThrottle throttle, AuthAudit audit) {
        this.users = users; this.tokens = tokens; this.encoder = encoder; this.throttle = throttle;
        this.audit = audit;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResult login(String username, String password, String address, String previousToken, String agent) {
        username = username.trim().toLowerCase(Locale.ROOT);
        try { throttle.check(username, address); }
        catch (ResponseStatusException exception) {
            audit.rejected(AuthEvent.Type.LOGIN_THROTTLED, null, username, address, agent);
            throw exception;
        }
        User user = users.findByUsername(username).orElse(null);
        boolean matches = encoder.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            audit.rejected(AuthEvent.Type.LOGIN_FAILURE, user == null ? null : user.getId(), username, address, agent);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowy login lub hasło.");
        }
        throttle.success(username);
        revoke(previousToken);
        tokens.deleteByExpiresAtBefore(Instant.now());
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = HexFormat.of().formatHex(bytes);
        AuthToken token = tokens.save(new AuthToken(hash(raw), Instant.now().plusSeconds(LIFETIME_SECONDS), user));
        audit.accepted(AuthEvent.Type.LOGIN_SUCCESS, user, token.getId(), address, agent);
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
    public void logout(String raw, String address, String agent) {
        if (!valid(raw)) return;
        var token = tokens.findByTokenHashAndExpiresAtAfter(hash(raw), Instant.now());
        if (token.isEmpty()) return;
        audit.accepted(AuthEvent.Type.LOGOUT, token.get().getOwner(), token.get().getId(), address, agent);
        revoke(raw);
    }

    @Transactional
    public void changePassword(Long userId, String current, String next, String address, String agent) {
        User user = users.findById(userId).orElseThrow();
        if (!encoder.matches(current, user.getPasswordHash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Nieprawidłowe obecne hasło.");
        user.changePassword(encoder.encode(next));
        tokens.deleteByOwnerId(userId);
        audit.accepted(AuthEvent.Type.PASSWORD_CHANGED, user, null, address, agent);
    }
    public static boolean valid(String raw) { return raw != null && raw.matches("[a-f0-9]{64}"); }
    public static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
