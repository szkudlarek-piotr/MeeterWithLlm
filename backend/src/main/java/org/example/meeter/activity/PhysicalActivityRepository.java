package org.example.meeter.activity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalActivityRepository extends JpaRepository<PhysicalActivity, Long> {
    List<PhysicalActivity> findByCreatedByIdOrderByActivityDateDescIdDesc(Long ownerId);
    Optional<PhysicalActivity> findByIdAndCreatedById(Long id, Long ownerId);
}
