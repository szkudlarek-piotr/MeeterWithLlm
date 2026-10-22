package org.example.meeter.auth;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
public interface TokenRepository extends JpaRepository<AuthToken, Long> {
    @EntityGraph(attributePaths = {"owner", "owner.human"})
    Optional<AuthToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByTokenHash(String hash);
    void deleteByOwnerId(Long id);
    void deleteByExpiresAtBefore(Instant now);
}
