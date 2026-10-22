package org.example.meeter.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.*;
import lombok.*;
import org.example.meeter.Human;

@Entity
@Table(name = "app_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role"}))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    @Getter(AccessLevel.NONE)
    private Set<Role> roles = EnumSet.of(Role.ROLE_USER);
    public Set<Role> getRoles() { return Collections.unmodifiableSet(roles); }
    public void grantRole(Role role) { roles.add(Objects.requireNonNull(role)); }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String username;
    @Column(nullable = false, length = 100)
    private String passwordHash;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "human_id", unique = true, nullable = false)
    private Human human;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User(String username, String passwordHash, Human human) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.human = human;
    }

    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }
}
