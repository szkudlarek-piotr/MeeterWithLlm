package org.example.meeter.quote.game;

import jakarta.persistence.*;
import lombok.*;
import org.example.meeter.Clique;
import org.example.meeter.auth.User;
import org.example.meeter.interactions.RelationshipRecord;

@Entity @Table(name = "quote_game_cliques", uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "clique_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameClique extends RelationshipRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private QuoteGame game;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private Clique clique;
    GameClique(QuoteGame game, Clique clique, User creator) { this.game = game; this.clique = clique; recordCreator(creator); }
}
