package org.example.meeter.interactions;
import org.springframework.data.jpa.repository.*;
public interface InteractionRepository extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Interaction i where i.id = :id")
    java.util.Optional<Interaction> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
