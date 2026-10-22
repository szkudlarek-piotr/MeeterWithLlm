package org.example.meeter.quote.game;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.example.meeter.Human;
import org.example.meeter.auth.User;
import org.example.meeter.interactions.RelationshipRecord;
import org.example.meeter.quote.Quote;

@Entity @Table(name = "quote_game_rounds", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"game_id", "position"}), @UniqueConstraint(columnNames = {"game_id", "quote_id"})})
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameRound extends RelationshipRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private QuoteGame game;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private Quote quote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false) private Human correctAuthor;
    @Column(nullable = false, length = 10240) private String quoteText;
    @Column(nullable = false) private int position;
    private Instant startedAt;
    private Instant deadlineAt;
    @OneToOne(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true) private QuoteGuess guess;
    GameRound(QuoteGame game, Quote quote, int position, User creator) {
        this.game = game; this.quote = quote; this.position = position;
        correctAuthor = quote.getAuthor(); quoteText = quote.getQuote(); recordCreator(creator);
    }
    void start(int seconds) {
        if (startedAt == null) { startedAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS); deadlineAt = startedAt.plusSeconds(seconds); }
    }
    void answer(Human chosen, User player) {
        if (guess != null) throw new IllegalStateException("This round has already been answered.");
        boolean timedOut = !Instant.now().isBefore(deadlineAt);
        boolean self = correctAuthor.getId().equals(game.getPlayerHuman().getId());
        long elapsed = Math.max(0, Math.min(java.time.Duration.between(startedAt, Instant.now()).toMillis(), game.getSecondsPerQuestion() * 1000L));
        guess = new QuoteGuess(this, chosen, chosen != null && !timedOut && correctAuthor.getId().equals(chosen.getId()), timedOut, self, elapsed, player);
    }
}
