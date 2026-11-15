package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import org.example.meeter.auth.User;

/** Metadata belongs to the relationship, independently of either endpoint. */
@MappedSuperclass
@Getter
public abstract class RelationshipRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    public void recordCreator(User creator) {
        if (creator == null) throw new IllegalArgumentException("A relationship requires its creator.");
        if (createdBy != null) throw new IllegalStateException("The relationship creator is immutable.");
        createdBy = creator;
    }
}
