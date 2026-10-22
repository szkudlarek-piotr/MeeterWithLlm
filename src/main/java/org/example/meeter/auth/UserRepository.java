package org.example.meeter.auth;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "human")
    Optional<User> findByUsername(String username);
}
