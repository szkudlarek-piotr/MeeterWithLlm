package org.example.meeter.superpowers;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HumanSuperpowerRepository extends JpaRepository<HumanSuperpower, Long> {
    Optional<HumanSuperpower> findByHumanIdAndSuperpowerId(Long humanId, Long superpowerId);
    List<HumanSuperpower> findByHumanIdOrderById(Long humanId);
}
