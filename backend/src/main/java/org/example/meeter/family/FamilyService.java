package org.example.meeter.family;

import jakarta.validation.constraints.*;
import java.util.*;
import org.example.meeter.*;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.auth.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Family graph kept in SQL. Each account sees only the links it recorded; the graph is small, so it is walked in memory. */
@Service
public class FamilyService {
    static final int MAX_GENERATIONS = 4;
    static final int MAX_PARENTS = 2;
    public record Input(@NotNull FamilyLink.Type type, @NotNull @Positive Long personAId, @NotNull @Positive Long personBId) {}
    public record LinkView(Long id, FamilyLink.Type type, Long personAId, Long personBId) {}
    public record NodeView(ApiModels.HumanView person, int generation) {}
    public record TreeView(Long focusId, List<NodeView> people, List<LinkView> links) {}

    private final FamilyLinkRepository links;
    private final HumanRepository humans;
    private final MeeterService people;
    private final UserRepository users;
    private final CurrentAccount account;
    public FamilyService(FamilyLinkRepository links, HumanRepository humans, MeeterService people, UserRepository users, CurrentAccount account) {
        this.links = links; this.humans = humans; this.people = people; this.users = users; this.account = account;
    }

    private static Map<Long, Set<Long>> index(List<FamilyLink> all, FamilyLink.Type type, boolean forward) {
        var result = new HashMap<Long, Set<Long>>();
        for (var link : all) if (link.getType() == type) {
            Long a = link.getPersonA().getId(), b = link.getPersonB().getId();
            result.computeIfAbsent(forward ? a : b, key -> new LinkedHashSet<>()).add(forward ? b : a);
        }
        return result;
    }
    private static Set<Long> descendants(Map<Long, Set<Long>> children, Long start) {
        var seen = new HashSet<Long>(); var queue = new ArrayDeque<Long>(List.of(start));
        while (!queue.isEmpty()) for (Long child : children.getOrDefault(queue.poll(), Set.of())) if (seen.add(child)) queue.add(child);
        return seen;
    }

    @Transactional(readOnly = true)
    public TreeView tree(Long focusId) {
        people.browseHuman(focusId);
        var all = links.findByCreatedById(account.id());
        var parentsOf = index(all, FamilyLink.Type.PARENT, false);
        var childrenOf = index(all, FamilyLink.Type.PARENT, true);
        var spousesOf = new HashMap<Long, Set<Long>>();
        for (var link : all) if (link.getType() == FamilyLink.Type.SPOUSE) {
            Long a = link.getPersonA().getId(), b = link.getPersonB().getId();
            spousesOf.computeIfAbsent(a, key -> new LinkedHashSet<>()).add(b);
            spousesOf.computeIfAbsent(b, key -> new LinkedHashSet<>()).add(a);
        }
        var generation = new LinkedHashMap<Long, Integer>();
        generation.put(focusId, 0);
        for (int direction : new int[] { -1, 1 }) {
            Set<Long> frontier = Set.of(focusId);
            for (int step = 1; step <= MAX_GENERATIONS && !frontier.isEmpty(); step++) {
                var next = new LinkedHashSet<Long>();
                for (Long id : frontier) for (Long other : (direction < 0 ? parentsOf : childrenOf).getOrDefault(id, Set.of()))
                    if (generation.putIfAbsent(other, direction * step) == null) next.add(other);
                frontier = next;
            }
        }
        for (Long parent : parentsOf.getOrDefault(focusId, Set.of()))
            for (Long sibling : childrenOf.getOrDefault(parent, Set.of())) generation.putIfAbsent(sibling, 0);
        for (Long id : new ArrayList<>(generation.keySet()))
            for (Long spouse : spousesOf.getOrDefault(id, Set.of())) generation.putIfAbsent(spouse, generation.get(id));

        var visible = new HashMap<Long, Human>();
        for (Human human : humans.findAllById(generation.keySet()))
            if (human.getClique() == null || !"my_exes".equalsIgnoreCase(human.getClique().getCliqueName().trim())) visible.put(human.getId(), human);
        var nodes = generation.entrySet().stream().filter(entry -> visible.containsKey(entry.getKey()))
            .map(entry -> new NodeView(ApiModels.HumanView.forBrowse(visible.get(entry.getKey())), entry.getValue())).toList();
        var edges = all.stream().filter(link -> visible.containsKey(link.getPersonA().getId()) && visible.containsKey(link.getPersonB().getId()))
            .map(link -> new LinkView(link.getId(), link.getType(), link.getPersonA().getId(), link.getPersonB().getId())).toList();
        return new TreeView(focusId, nodes, edges);
    }

    @Transactional
    public LinkView add(FamilyLink.Type type, Long personAId, Long personBId) {
        if (personAId.equals(personBId)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Osoba nie może być spokrewniona sama ze sobą.");
        Human a = people.browseHuman(personAId), b = people.browseHuman(personBId);
        if (type == FamilyLink.Type.SPOUSE && a.getId() > b.getId()) { Human swap = a; a = b; b = swap; }
        var all = links.findByCreatedById(account.id());
        Long aId = a.getId(), bId = b.getId();
        if (all.stream().anyMatch(l -> l.getType() == type && l.getPersonA().getId().equals(aId) && l.getPersonB().getId().equals(bId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ta relacja już istnieje.");
        if (type == FamilyLink.Type.PARENT) {
            if (index(all, FamilyLink.Type.PARENT, false).getOrDefault(bId, Set.of()).size() >= MAX_PARENTS)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Osoba może mieć najwyżej dwoje rodziców.");
            if (descendants(index(all, FamilyLink.Type.PARENT, true), bId).contains(aId))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ta relacja utworzyłaby pętlę w drzewie rodzinnym.");
        }
        var link = new FamilyLink(type, a, b);
        link.assignOwner(users.getReferenceById(account.id()));
        link = links.saveAndFlush(link);
        return new LinkView(link.getId(), type, aId, bId);
    }

    @Transactional
    public void remove(Long id) {
        links.delete(links.findByIdAndCreatedById(id, account.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono relacji.")));
    }
}
