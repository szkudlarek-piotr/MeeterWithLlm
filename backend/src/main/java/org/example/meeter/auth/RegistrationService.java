package org.example.meeter.auth;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.example.meeter.Human;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RegistrationService {
    private static final Base64.Encoder BASE64 = Base64.getUrlEncoder().withoutPadding();
    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    private final CurrentAccount account;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final byte[] signingKey;
    private final RegistrationMail mail;
    private static final SecureRandom RANDOM = new SecureRandom();
    @PersistenceContext private EntityManager entities;

    RegistrationService(UserRepository users, PasswordEncoder encoder, CurrentAccount account, RegistrationMail mail,
                        @Value("${MEETER_INVITATION_JWT_SECRET:}") String signingSecret) {
        this.users = users;
        this.account = account;
        this.encoder = encoder;
        this.mail = mail;
        this.signingKey = signingSecret.strip().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public RegistrationController.Invitation invite(Long humanId) {
        var now = Instant.now();
        entities.createQuery("delete from PendingRegistration p where p.expiresAt <= :now")
            .setParameter("now", now).executeUpdate();
        Long inviterId = account.id();
        ownedHuman(humanId, inviterId);
        if (users.existsByHumanId(humanId)) throw alreadyLinked();

        var expiresAt = now.plus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String claims = "{\"jti\":\"" + UUID.randomUUID() + "\",\"exp\":" + expiresAt.getEpochSecond()
            + ",\"aud\":\"meeter-registration\"}";
        String unsigned = HEADER + "." + encode(claims);
        String token = unsigned + "." + sign(unsigned);
        entities.persist(new PendingRegistration(AuthService.hash(token), humanId, inviterId, expiresAt));
        return new RegistrationController.Invitation(token);
    }

    @Transactional
    public void register(RegistrationController.RegisterInput input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hasło może mieć maksymalnie 72 bajty UTF-8.");
        String token = input.token().strip();
        int signatureStart = token.lastIndexOf('.');
        if (!token.startsWith(HEADER + ".") || signatureStart <= HEADER.length()) throw invalidInvitation();
        String expected = sign(token.substring(0, signatureStart));
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                token.substring(signatureStart + 1).getBytes(StandardCharsets.US_ASCII))) throw invalidInvitation();

        var pending = entities.find(PendingRegistration.class, AuthService.hash(token), LockModeType.PESSIMISTIC_WRITE);
        if (pending == null || !pending.getExpiresAt().isAfter(Instant.now())) throw invalidInvitation();
        var human = ownedHuman(pending.getHumanId(), pending.getInviterId());
        if (users.existsByHumanId(human.getId())) throw alreadyLinked();

        String username = input.username().strip().toLowerCase(Locale.ROOT);
        if (!username.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}._@+\\-]{0,63}") || username.length() > 64)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nieprawidłowa nazwa użytkownika.");
        if ("pszkudlarek".equals(username) || users.existsByUsernameIgnoreCase(username)) throw loginUnavailable();
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adres e-mail jest już przypisany do konta.");
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String verification = HexFormat.of().formatHex(bytes);
        pending.prepareVerification(AuthService.hash(verification), username, email, encoder.encode(input.password()));
        entities.flush();
        mail.send(email, verification);
    }

    @Transactional
    public void verifyEmail(String token) {
        var pending = entities.createQuery(
                "select p from PendingRegistration p where p.verificationHash = :hash", PendingRegistration.class)
            .setParameter("hash", AuthService.hash(token)).setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList().stream().findFirst().orElseThrow(RegistrationService::invalidVerification);
        if (!pending.getExpiresAt().isAfter(Instant.now())) throw invalidVerification();
        var human = ownedHuman(pending.getHumanId(), pending.getInviterId());
        if (users.existsByHumanId(human.getId())) throw alreadyLinked();
        if (users.existsByUsernameIgnoreCase(pending.getUsername()) || users.existsByEmail(pending.getEmail()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login lub adres e-mail są już przypisane do konta.");
        var user = new User(pending.getUsername(), pending.getPasswordHash(), human, pending.getEmail());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException conflict) {
            throw loginUnavailable();
        }
        entities.persist(new AccountInvitationHistory(user.getId(), pending.getInviterId(), human.getId(), Instant.now()));
        entities.remove(pending);
    }

    private static ResponseStatusException invalidVerification() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link potwierdzający jest nieważny, wygasł lub został wykorzystany.");
    }

    private String sign(String unsigned) {
        if (signingKey.length < 32)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Zaproszenia są obecnie niedostępne.");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return BASE64.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException failure) {
            throw new IllegalStateException("Nie można podpisać zaproszenia.", failure);
        }
    }

    private static String encode(String value) {
        return BASE64.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Human ownedHuman(Long humanId, Long inviterId) {
        var human = entities.find(Human.class, humanId, LockModeType.PESSIMISTIC_WRITE);
        if (human == null || human.getCreatedBy() == null || !inviterId.equals(human.getCreatedBy().getId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono osoby.");
        return human;
    }

    private static ResponseStatusException invalidInvitation() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zaproszenie jest nieważne, wygasło lub zostało wykorzystane.");
    }

    private static ResponseStatusException alreadyLinked() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Ta osoba ma już konto.");
    }

    private static ResponseStatusException loginUnavailable() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Nazwa użytkownika jest już zajęta.");
    }
}
