package org.example.meeter.quote;

import static org.example.meeter.quote.QuoteModels.*;
import org.example.meeter.MeeterService;
import org.example.meeter.auth.*;
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
    public QuoteService(QuoteRepository quotes, CurrentAccount account, UserRepository users, MeeterService people) {
        this.quotes = quotes; this.account = account; this.users = users; this.people = people;
    }
    public Specification<Quote> visibleToCurrentAccount() {
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
    public View get(Long id) {
        return view(quotes.findOne(visibleToCurrentAccount().and((root, query, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono cytatu.")));
    }
    public record AuthorPage(java.util.List<View> content, int page, int size, long totalElements, boolean hasNext) {}
    public AuthorPage forAuthor(Long authorId, int page, int size) {
        var result = quotes.findAll(visibleToCurrentAccount().and((root, query, cb) -> cb.equal(root.get("author").get("id"), authorId)),
            org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "addTime", "id")));
        return new AuthorPage(result.stream().map(this::view).toList(), page, size, result.getTotalElements(), result.hasNext());
    }
    private View view(Quote quote) {
        View result = View.of(quote);
        if (quote.getAuthor() == null || quote.getAuthor().getCreatedBy() != null && quote.getAuthor().getCreatedBy().getId().equals(account.id())) return result;
        return new View(result.id(), result.uuid(), result.quote(), new AuthorView(result.author().id(), result.author().fullName(), null), result.isPublic(), result.addTime());
    }
}
