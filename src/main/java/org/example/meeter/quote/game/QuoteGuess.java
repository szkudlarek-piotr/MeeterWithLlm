package org.example.meeter.quote.game;

import jakarta.persistence.*;
import lombok.*;
import org.example.meeter.Human;
import org.example.meeter.auth.User;
import org.example.meeter.interactions.RelationshipRecord;

@Entity @Table(name = "quote_guesses")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteGuess extends RelationshipRecord {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true, updatable = false) private GameRound round;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(updatable = false) private Human guessedAuthor;
    @Column(nullable = false, updatable = false) private boolean correct;
    @Column(nullable = false, updatable = false) private boolean timedOut;
    @Column(nullable = false, updatable = false) private boolean selfQuote;
    @Column(nullable = false, updatable = false) private long elapsedMillis;
    QuoteGuess(GameRound round, Human guessedAuthor, boolean correct, boolean timedOut, boolean selfQuote, long elapsedMillis, User player) {
        this.round = round; this.guessedAuthor = guessedAuthor; this.correct = correct; this.timedOut = timedOut;
        this.selfQuote = selfQuote; this.elapsedMillis = elapsedMillis; recordCreator(player);
    }
}
