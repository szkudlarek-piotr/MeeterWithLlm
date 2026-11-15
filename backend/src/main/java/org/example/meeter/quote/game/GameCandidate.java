package org.example.meeter.quote.game;

import jakarta.persistence.*;
import lombok.*;
import org.example.meeter.Human;
import org.example.meeter.auth.User;
import org.example.meeter.interactions.RelationshipRecord;

@Entity @Table(name = "quote_game_candidates", uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "human_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameCandidate extends RelationshipRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private QuoteGame game;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "human_id", nullable = false) private Human human;
    GameCandidate(QuoteGame game, Human human, User creator) { this.game = game; this.human = human; recordCreator(creator); }
}
