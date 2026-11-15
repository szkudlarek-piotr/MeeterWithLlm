package org.example.meeter;
import org.springframework.data.jpa.repository.*;
public interface CliqueRepository extends JpaRepository<Clique,Long>, JpaSpecificationExecutor<Clique> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Clique c where c.id=:id")
 java.util.Optional<Clique> lockForPhoto(@org.springframework.data.repository.query.Param("id") Long id);
}

