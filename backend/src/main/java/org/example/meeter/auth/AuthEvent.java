package org.example.meeter.auth;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Durable audit snapshots, independent of revocable sessions and account deletion. */
@Entity
@Table(name = "auth_events", indexes = {
    @Index(name = "idx_auth_event_time", columnList = "occurred_at,id"),
    @Index(name = "idx_auth_event_account_time", columnList = "account_id,occurred_at,id")
})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthEvent {
    public enum Type { LOGIN_SUCCESS, LOGIN_FAILURE, LOGIN_THROTTLED, LOGOUT, PASSWORD_CHANGED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Instant occurredAt;
    // Deliberately no entity relation/FK: deleting an account must not erase its audit.
    @Column(updatable = false) private Long accountId;
    @Column(updatable = false) private Long sessionRecordId;
    @Column(nullable = false, length = 64, updatable = false) private String username;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 32, updatable = false) private Type type;
    @Column(nullable = false, length = 64, updatable = false) private String ipAddress;
    @Column(length = 512, updatable = false) private String userAgent;

    public AuthEvent(Type type, Long accountId, Long sessionRecordId, String username, String ipAddress, String userAgent) {
        this.occurredAt = Instant.now(); this.type = type; this.accountId = accountId;
        this.sessionRecordId = sessionRecordId;
        this.username = bounded(username, 64); this.ipAddress = bounded(ipAddress, 64);
        this.userAgent = userAgent == null ? null : bounded(userAgent, 512);
    }
    private static String bounded(String value, int limit) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\p{Cntrl}]", " ");
        return clean.substring(0, Math.min(clean.length(), limit));
    }
}
