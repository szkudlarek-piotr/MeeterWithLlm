package org.example.meeter.superpowers;

import java.util.List;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.auth.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly = true)
public class SuperpowerService {
    private final SuperpowerRepository superpowers;
    private final UserRepository users;
    private final CurrentAccount account;

    public SuperpowerService(SuperpowerRepository superpowers, UserRepository users, CurrentAccount account) {
        this.superpowers = superpowers;
        this.users = users;
        this.account = account;
    }

    public List<Superpower> list() {
        return superpowers.findAll(Sort.by(Sort.Direction.ASC, "superpowerName", "id"));
    }

    public Superpower get(Long id) {
        return superpowers.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono supermocy."));
    }

    @Transactional
    public Superpower create(String name) {
        var creator = users.findById(account.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Brak sesji."));
        return superpowers.save(new Superpower(name.strip(), creator));
    }
}
