package org.example.meeter;
import org.springframework.data.jpa.repository.*;
public interface CliqueRepository extends JpaRepository<Clique,Long>, JpaSpecificationExecutor<Clique> {}

