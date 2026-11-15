package org.example.meeter.quote;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.example.meeter.Human;
import org.example.meeter.OwnedEntity;
import org.hibernate.annotations.CurrentTimestamp;

@Entity
@Table(name = "quotes", indexes = @Index(name = "idx_quote_owner_time", columnList = "created_by_id,add_time"))
@AssociationOverride(name = "createdBy", joinColumns = @JoinColumn(name = "created_by_id", nullable = false, updatable = false))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote extends OwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Size(min = 4, max = 10240, message = "Cytat powinien mieć od 4 do 10240 znaków.")
    @Column(name = "quote_text", nullable = false, length = 10240)
    private String quote;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Human author;
    @Column(name = "is_public", nullable = false)
    private boolean isPublic;
    @CurrentTimestamp
    @Column(name = "add_time", nullable = false, updatable = false)
    private LocalDateTime addTime;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    public Quote(String quote, Human author, boolean isPublic) {
        this.quote = quote; this.author = author; this.isPublic = isPublic;
    }
}
