package org.example.meeter.superpowers;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SuperpowerRepository extends JpaRepository<Superpower, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Superpower s where s.id = :id")
    Optional<Superpower> lockForPhoto(@Param("id") Long id);
}
