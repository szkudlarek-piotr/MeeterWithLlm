package org.example.meeter.auth;

import jakarta.persistence.*;
import java.util.*;
import org.example.meeter.Human;
import org.example.meeter.HumanRepository;
import org.example.meeter.quote.QuoteModels.AuthorView;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly=true)
public class AdminUsers {
    private final UserRepository users;
    private final HumanRepository humans;
    private final CurrentAccount account;
    private final PasswordEncoder encoder;
    private final GeneratedPasswords passwords;
    @PersistenceContext private EntityManager entities;
    public AdminUsers(UserRepository users,HumanRepository humans,CurrentAccount account,PasswordEncoder encoder,GeneratedPasswords passwords) {
        this.users=users; this.humans=humans; this.account=account; this.encoder=encoder; this.passwords=passwords;
    }
    public record Created(Long id,String username,Long humanId,String fullName) {}
    private void requireAdmin() {
        if(!account.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Wymagane uprawnienia administratora.");
    }
    public List<AuthorView> candidates(String search,int limit) {
        requireAdmin();
        String pattern="%"+search.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        Specification<Human> filter=(root,query,cb)-> {
            var existing=query.subquery(Long.class);
            var user=existing.from(User.class);
            existing.select(user.get("id")).where(cb.equal(user.get("human").get("id"),root.get("id")));
            return cb.and(cb.not(cb.exists(existing)),
                cb.like(cb.lower(cb.concat(cb.concat(root.get("name")," "),cb.coalesce(root.get("surname"),""))),pattern,'!'));
        };
        return humans.findAll(filter,PageRequest.of(0,limit,Sort.by("surname","name","id"))).stream()
            .map(h->new AuthorView(h.getId(),h.getFullName(),h.getProfilePhotoUrl()==null?null:
                h.getProfilePhotoUrl().replace("/api/people/","/api/people/participants/"))).toList();
    }
    @Transactional
    public Created create(Long humanId,String username) {
        requireAdmin();
        username=username.trim().toLowerCase(Locale.ROOT);
        if(!username.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}._@+\\-]{0,63}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Login musi zaczynać się literą lub cyfrą i zawierać do 64 znaków: litery, cyfry oraz . _ @ + -.");
        var human=entities.find(Human.class,humanId,LockModeType.PESSIMISTIC_WRITE);
        if(human==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Nie znaleziono osoby.");
        if(users.existsByHumanId(humanId)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Ta osoba ma już konto.");
        if(users.existsByUsernameIgnoreCase(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Ten login jest już zajęty.");
        String password=passwords.generate();
        var user=new User(username,encoder.encode(password),human);
        try { users.saveAndFlush(user); }
        catch(DataIntegrityViolationException failure) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Login lub osoba są już przypisane do konta.");
        }
        passwords.append(user.getUsername(),password,human.getId());
        return new Created(user.getId(),user.getUsername(),human.getId(),human.getFullName());
    }
}
