package org.example.meeter.quote.game;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.*;
import org.example.meeter.*;
import org.example.meeter.auth.User;
import org.example.meeter.interactions.Visit;
import org.example.meeter.quote.Quote;

@Entity @Table(name = "quote_games")
@AssociationOverride(name = "createdBy", joinColumns = @JoinColumn(name = "created_by_id", nullable = false, updatable = false))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteGame extends OwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, updatable = false) private UUID uuid = UUID.randomUUID();
    @Column(nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false, length = 10) private String scopeType;
    @Column(nullable = false, length = 512) private String scopeLabel;
    @Column(nullable = false) private int secondsPerQuestion;
    private Instant finishedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, updatable = false) private Human playerHuman;
    @ManyToOne(fetch = FetchType.LAZY) private Visit visit;
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameClique> cliques = new ArrayList<>();
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameCandidate> candidates = new ArrayList<>();
    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") private List<GameRound> rounds = new ArrayList<>();

    QuoteGame(User player, Visit visit, Collection<Clique> cliques, Collection<Human> people, List<Quote> quotes, int secondsPerQuestion) {
        assignOwner(player); this.visit = visit;
        this.secondsPerQuestion = secondsPerQuestion; this.playerHuman = player.getHuman();
        scopeType = visit == null ? "CLIQUES" : "VISIT";
        scopeLabel = visit == null ? "Kliki: " + cliques.stream().map(Clique::getCliqueName).sorted().collect(java.util.stream.Collectors.joining(", ")) : visit.getName();
        if (scopeLabel.length() > 512) scopeLabel = scopeLabel.substring(0, 509) + "…";
        cliques.forEach(clique -> this.cliques.add(new GameClique(this, clique, player)));
        people.forEach(person -> candidates.add(new GameCandidate(this, person, player)));
        for (int i = 0; i < quotes.size(); i++) rounds.add(new GameRound(this, quotes.get(i), i + 1, player));
    }
    void finish() { if (finishedAt == null) finishedAt = Instant.now(); }
}
