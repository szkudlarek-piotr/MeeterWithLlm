package org.example.meeter.family;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyLinkRepository extends JpaRepository<FamilyLink, Long> {
    List<FamilyLink> findByCreatedById(Long ownerId);
    Optional<FamilyLink> findByIdAndCreatedById(Long id, Long ownerId);
}
