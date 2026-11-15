package org.example.meeter.auth;

import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import org.example.meeter.HumanRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(10)
@ConditionalOnProperty(name = "app.dev.bootstrap", havingValue = "true")
public class DevBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final HumanRepository humans;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public DevBootstrap(JdbcTemplate jdbc, HumanRepository humans, UserRepository users, PasswordEncoder encoder) {
        this.jdbc=jdbc; this.humans=humans; this.users=users; this.encoder=encoder;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (users.findByUsername("pszkudlarek").isPresent()) return;
        var existing = humans.findById(77L);
        if (existing.isPresent() && (!"Piotr".equals(existing.get().getName()) || !"Szkudlarek".equals(existing.get().getSurname()) || existing.get().getCreatedBy()!=null))
            throw new IllegalStateException("ID 77 jest zajęte. Nie nadpisano danych.");
        if (existing.isEmpty()) jdbc.update("insert into people (id,uuid,name,surname) values (?,?,?,?)", 77L, UUID.randomUUID(), "Piotr", "Szkudlarek");
        Long next = jdbc.queryForObject("select max(id)+1 from people", Long.class);
        jdbc.execute("ALTER TABLE people AUTO_INCREMENT = " + next);
        var human = humans.findById(77L).orElseThrow();
        byte[] bytes = new byte[24]; new SecureRandom().nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        User user = users.saveAndFlush(new User("pszkudlarek", encoder.encode(password), human));
        // Jednorazowo przypisz dotychczasowe dane tej nowej lokalnej bazy do pierwszego konta.
        for (String table : List.of("people", "places", "cliques"))
            jdbc.update("update " + table + " set created_by_id=? where created_by_id is null", user.getId());
        Path file = Path.of("data", "initial-login.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "Lokalny Meeter — konto startowe\nLogin: pszkudlarek\nHasło: " + password + "\n\nPo zalogowaniu zmień hasło i usuń ten plik.\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
