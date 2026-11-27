package org.example.meeter.superpowers;

import java.time.LocalDateTime;
import java.util.List;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.auth.User;
import org.example.meeter.auth.UserRepository;
import org.example.meeter.Human;
import org.example.meeter.HumanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly = true)
public class HumanSuperpowerService {
    private final HumanSuperpowerRepository links;
    private final HumanRepository people;
    private final SuperpowerRepository superpowers;
    private final UserRepository users;
    private final SuperpowerAccess access;
    private final CurrentAccount account;

    public HumanSuperpowerService(HumanSuperpowerRepository links, HumanRepository people,
            SuperpowerRepository superpowers, UserRepository users, SuperpowerAccess access, CurrentAccount account) {
        this.links = links;
        this.people = people;
        this.superpowers = superpowers;
        this.users = users;
        this.access = access;
        this.account = account;
    }

    public record View(Long superpowerId, String superpowerName, String photoUrl, LocalDateTime addTime) {
        static View of(HumanSuperpower link) {
            return new View(link.getSuperpower().getId(), link.getSuperpower().getSuperpowerName(), link.getSuperpower().photoUrl(), link.getAddTime());
        }
    }

    /** Admins, the person themselves, the person's creator and the person's spouse. */
    private Human managedHuman(Long humanId) {
        Human human = people.findById(humanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono osoby."));
        if (!access.canManage(human))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono osoby.");
        return human;
    }

    public List<View> list(Long humanId) {
        // Reading is open to every signed-in user; only add and remove need managedHuman.
        if (!people.existsById(humanId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono osoby.");
        return links.findByHumanIdOrderById(humanId).stream().map(View::of).toList();
    }

    @Transactional
    public View add(Long humanId, Long superpowerId) {
        Human human = managedHuman(humanId);
        Superpower superpower = superpowers.findById(superpowerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono supermocy."));
        return links.findByHumanIdAndSuperpowerId(humanId, superpowerId)
            .map(View::of)
            .orElseGet(() -> {
                User actor = users.getReferenceById(account.id());
                return View.of(links.save(new HumanSuperpower(human, superpower, actor)));
            });
    }

    @Transactional
    public void remove(Long humanId, Long superpowerId) {
        managedHuman(humanId);
        HumanSuperpower link = links.findByHumanIdAndSuperpowerId(humanId, superpowerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono powiązania."));
        links.delete(link);
    }
}
