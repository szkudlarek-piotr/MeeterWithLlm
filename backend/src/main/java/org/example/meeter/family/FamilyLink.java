package org.example.meeter.family;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.example.meeter.Human;
import org.example.meeter.OwnedEntity;
import org.hibernate.annotations.CurrentTimestamp;

/**
 * A family relation recorded by one account. PARENT: personA is the parent of personB.
 * SPOUSE: unordered pair, stored with the smaller ID in personA.
 */
@Entity @Table(name = "family_links")
@AssociationOverride(name = "createdBy", joinColumns = @JoinColumn(name = "created_by_id", nullable = false, updatable = false))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FamilyLink extends OwnedEntity {
    public enum Type { PARENT, SPOUSE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(name = "link_type", nullable = false, length = 16)
    private Type type;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "person_a_id", nullable = false)
    private Human personA;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "person_b_id", nullable = false)
    private Human personB;
    @CurrentTimestamp @Column(name = "add_time", nullable = false, updatable = false)
    private LocalDateTime addTime;

    public FamilyLink(Type type, Human personA, Human personB) {
        this.type = type; this.personA = personA; this.personB = personB;
    }
}
