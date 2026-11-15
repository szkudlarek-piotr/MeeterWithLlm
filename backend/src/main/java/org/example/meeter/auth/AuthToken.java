package org.example.meeter.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "auth_tokens", indexes = @Index(name = "idx_token_expiry", columnList = "expires_at"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User owner;

    public AuthToken(String tokenHash, Instant expiresAt, User owner) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.owner = owner;
    }
}
