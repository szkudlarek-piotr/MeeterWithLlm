package org.example.meeter.auth;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.*;
public interface TokenRepository extends JpaRepository<AuthToken, Long> {
    @EntityGraph(attributePaths = {"owner", "owner.human", "owner.roles"})
    Optional<AuthToken> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    /**
     * Conditional renewal in its own short transaction. Concurrent requests with the same token race here: the first one
     * moves the expiry, the others match no row (0) and the database never sees a stale-read conflict.
     */
    @Modifying @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("update AuthToken t set t.expiresAt = :expiry where t.id = :id and t.expiresAt < :renewBefore")
    int renew(@Param("id") Long id, @Param("expiry") Instant expiry, @Param("renewBefore") Instant renewBefore);
    void deleteByTokenHash(String hash);
    void deleteByOwnerId(Long id);
    void deleteByExpiresAtBefore(Instant now);
}
