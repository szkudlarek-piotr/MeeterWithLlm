package org.example.meeter;
import org.springframework.data.jpa.repository.*;
public interface PlaceRepository extends JpaRepository<Place,Long>, JpaSpecificationExecutor<Place> {}

