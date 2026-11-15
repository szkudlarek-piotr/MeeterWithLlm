package org.example.meeter;
import org.springframework.data.jpa.repository.*;
public interface HumanRepository extends JpaRepository<Human,Long>, JpaSpecificationExecutor<Human> {}
