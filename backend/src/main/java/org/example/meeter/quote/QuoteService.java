package org.example.meeter.quote;

import static org.example.meeter.quote.QuoteModels.*;
import org.example.meeter.MeeterService;
import org.example.meeter.auth.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly = true)
public class QuoteService {
    private final QuoteRepository quotes;
    private final CurrentAccount account;
    private final UserRepository users;
    private final MeeterService people;
    private final QuoteVoteRepository votes;
    public QuoteService(QuoteRepository quotes, CurrentAccount account, UserRepository users, MeeterService people,
                        QuoteVoteRepository votes) {
        this.quotes = quotes; this.account = account; this.users = users; this.people = people;
        this.votes = votes;
    }
    private Specification<Quote> visibleToCurrentAccount(boolean publicOnly) {
        if (publicOnly) return (root, query, cb) -> cb.isTrue(root.get("isPublic"));
        if (account.isAdmin()) return (root, query, cb) -> cb.conjunction();
        Long owner = account.id();
        Long human = account.principal().humanId();
        return (root, query, cb) -> cb.or(cb.isTrue(root.get("isPublic")),
            cb.equal(root.join("author", jakarta.persistence.criteria.JoinType.LEFT).get("id"), human),
            cb.and(cb.isNull(root.get("author")), cb.equal(root.get("createdBy").get("id"), owner)));
    }
    @Transactional public View create(Input input) {
        String text = input.quote().strip();
        if (text.length() < 4 || text.length() > 10240)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cytat powinien mieć od 4 do 10240 znaków.");
        if (input.authorId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz autora cytatu.");
        var author = people.browseHuman(input.authorId());
        Quote quote = new Quote(text, author, Boolean.TRUE.equals(input.isPublic()));
        quote.assignOwner(users.getReferenceById(account.id()));
        return view(quotes.saveAndFlush(quote));
    }
    public View get(Long id, boolean publicOnly) {
        return view(visibleQuote(id, publicOnly));
    }

    private Quote visibleQuote(Long id, boolean publicOnly) {
        return quotes.findOne(visibleToCurrentAccount(publicOnly).and((root, query, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(QuoteService::missing);
    }

    @Transactional
    public void vote(Long id, Vote vote, boolean publicOnly) {
        Quote quote = lockVisibleQuote(id, publicOnly);
        Long voterId = account.id();
        var existing = votes.findByQuoteIdAndVoterId(id, voterId);
        if (existing.isPresent()) {
            if (existing.get().value() != vote.value)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Twój głos na ten cytat jest już zapisany.");
            return;
        }
        votes.save(new QuoteVote(quote, users.getReferenceById(voterId), vote.value));
    }

    @Transactional
    public void retractVote(Long id, boolean publicOnly) {
        lockVisibleQuote(id, publicOnly);
        votes.findByQuoteIdAndVoterId(id, account.id()).ifPresent(votes::delete);
    }

    private Quote lockVisibleQuote(Long id, boolean publicOnly) {
        quotes.lockById(id).orElseThrow(QuoteService::missing);
        return visibleQuote(id, publicOnly);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono cytatu.");
    }

    public record AuthorPage(java.util.List<RankedView> content, int page, int size, long totalElements, boolean hasNext) {}
    public AuthorPage forAuthor(Long authorId, int page, int size, boolean publicOnly) {
        boolean seePrivate = !publicOnly && (account.isAdmin() || authorId.equals(account.principal().humanId()));
        var result = quotes.rankedByAuthor(authorId, seePrivate, account.id(), PageRequest.of(page, size));
        return new AuthorPage(result.stream()
            .map(row -> RankedView.of(view(row.getQuote()), row.getScore(), row.getMyVote()))
            .toList(), page, size, result.getTotalElements(), result.hasNext());
    }
    private View view(Quote quote) {
        View result = View.of(quote);
        if (quote.getAuthor() == null || quote.getAuthor().getCreatedBy() != null && quote.getAuthor().getCreatedBy().getId().equals(account.id())) return result;
        return new View(result.id(), result.uuid(), result.quote(), new AuthorView(result.author().id(), result.author().fullName(), null), result.isPublic(), result.addTime());
    }
}
