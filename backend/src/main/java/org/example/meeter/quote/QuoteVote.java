package org.example.meeter.quote;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.example.meeter.auth.User;

@Entity
@Table(name = "quote_votes", uniqueConstraints = @UniqueConstraint(
    name = "uk_quote_vote_account", columnNames = {"quote_id", "voter_id"}))
class QuoteVote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Quote quote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User voter;

    @Column(name = "vote_value", nullable = false)
    private int value;

    protected QuoteVote() {}

    QuoteVote(Quote quote, User voter, int value) {
        this.quote = quote;
        this.voter = voter;
        this.value = value;
    }

    int value() { return value; }
}
