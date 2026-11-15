package org.example.meeter.quote.game;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.interactions.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class QuoteGameVisits {
    public record Option(Long id, String name, LocalDateTime startDate, int participantCount) {}
    public record Page(List<Option> content, int page, boolean hasNext) {}
    private final InteractionRepository interactions;
    private final CurrentAccount account;

    public QuoteGameVisits(InteractionRepository interactions, CurrentAccount account) {
        this.interactions = interactions;
        this.account = account;
    }

    private Specification<Interaction> accessible() {
        Long accountId = account.id();
        Long humanId = account.principal().humanId();
        return (root, query, cb) -> {
            var membership = query.subquery(Long.class);
            var link = membership.from(InteractionParticipant.class);
            membership.select(link.get("id")).where(cb.equal(link.get("interaction"), root),
                cb.equal(link.get("human").get("id"), humanId));
            return cb.and(cb.equal(root.type(), Visit.class),
                cb.or(cb.equal(root.get("createdBy").get("id"), accountId), cb.exists(membership)));
        };
    }

    public Visit get(Long id) {
        return (Visit) interactions.findOne(accessible().and((root, query, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono wybranej wizyty."));
    }

    public Page list(String query, int page, int size) {
        Specification<Interaction> filter = accessible();
        if (!query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filter = filter.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        var result = interactions.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate", "id")));
        return new Page(result.stream().map(visit -> new Option(visit.getId(), visit.getName(),
            visit.getStartDate(), visit.getParticipants().size())).toList(), page, result.hasNext());
    }
}
