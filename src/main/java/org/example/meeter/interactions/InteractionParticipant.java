package org.example.meeter.interactions;

import jakarta.persistence.*;
import lombok.*;
import org.example.meeter.Human;

@Entity
@Table(name = "interaction_human", uniqueConstraints = @UniqueConstraint(name = "uk_interaction_human_pair", columnNames = {"interaction_id", "human_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionParticipant extends RelationshipRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interaction_id", nullable = false, updatable = false)
    private Interaction interaction;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "human_id", nullable = false, updatable = false)
    private Human human;

    InteractionParticipant(Interaction interaction, Human human) {
        this.interaction = interaction; this.human = human;
    }
}
