package org.example.meeter.quote.game;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.example.meeter.*;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.interactions.*;
import org.example.meeter.quote.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Public-only drawing rules are deliberately independent of administrator quote access. */
@Service @Transactional(readOnly = true)
public class QuoteGamePool {
    public record Selection(Visit visit, List<Clique> cliques, Set<Human> people, List<Quote> quotes) {}
    private final CurrentAccount account;
    private final CliqueRepository cliques;
    private final HumanRepository people;
    private final InteractionRepository interactions;
    private final InteractionService interactionService;
    private final QuoteRepository quotes;
    public QuoteGamePool(CurrentAccount account, CliqueRepository cliques, HumanRepository people,
                         InteractionRepository interactions, InteractionService interactionService, QuoteRepository quotes) {
        this.account = account; this.cliques = cliques; this.people = people;
        this.interactions = interactions; this.interactionService = interactionService; this.quotes = quotes;
    }
    private <T> Specification<T> owned() {
        Long id = account.id();
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), id);
    }
    public Selection draw(Long visitId, Set<Long> cliqueIds) {
        Set<Long> ids = cliqueIds == null ? Set.of() : cliqueIds;
        if ((visitId == null) == ids.isEmpty()) throw bad("Wybierz jedną wizytę albo zestaw klik.");
        Visit visit = null;
        List<Clique> selectedCliques = List.of();
        Set<Human> candidates;
        if (visitId != null) {
            if (interactionService.get(visitId).type() != InteractionType.VISIT) throw missing();
            visit = (Visit) interactions.findById(visitId).orElseThrow(QuoteGamePool::missing);
            candidates = new LinkedHashSet<>(visit.getParticipants());
        } else {
            selectedCliques = cliques.findAll(this.<Clique>owned().and((root, q, cb) -> root.get("id").in(ids)));
            if (selectedCliques.size() != ids.size()) throw missing();
            candidates = new LinkedHashSet<>(people.findAll(this.<Human>owned().and((root, q, cb) -> root.get("clique").get("id").in(ids))));
        }
        List<Quote> selected = new ArrayList<>();
        for (Human person : candidates) {
            Specification<Quote> publicByAuthor = (root, q, cb) -> cb.and(cb.isTrue(root.get("isPublic")), cb.equal(root.get("author").get("id"), person.getId()));
            long count = quotes.count(publicByAuthor);
            if (count == 0) continue;
            if (count > Integer.MAX_VALUE) throw bad("Zbyt duża pula cytatów autora.");
            Set<Integer> offsets = new LinkedHashSet<>();
            while (offsets.size() < Math.min(2, count)) offsets.add(ThreadLocalRandom.current().nextInt((int) count));
            for (int offset : offsets) {
                var page = quotes.findAll(publicByAuthor, PageRequest.of(offset, 1, Sort.by("id")));
                if (page.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Pula cytatów zmieniła się. Spróbuj ponownie.");
                selected.add(page.getContent().getFirst());
            }
        }
        if (selected.isEmpty()) throw bad("Uczestnicy nie mają publicznych cytatów. Dodaj je przed rozpoczęciem gry.");
        Collections.shuffle(selected);
        return new Selection(visit, selectedCliques, candidates, selected);
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono wybranej wizyty lub kliki."); }
}
