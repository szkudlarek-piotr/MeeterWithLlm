package org.example.meeter.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "pending_registrations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PendingRegistration {
    @Id @Column(length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Long humanId;
    @Column(nullable = false)
    private Long inviterId;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(length = 64)
    private String verificationHash;
    @Column(length = 64)
    private String username;
    @Column(length = 254)
    private String email;
    private String passwordHash;

    void prepareVerification(String verificationHash, String username, String email, String passwordHash) {
        this.verificationHash = verificationHash;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    PendingRegistration(String tokenHash, Long humanId, Long inviterId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.humanId = humanId;
        this.inviterId = inviterId;
        this.expiresAt = expiresAt;
    }
}
