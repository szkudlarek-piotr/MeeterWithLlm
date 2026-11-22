package org.example.meeter.activity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalActivityRepository extends JpaRepository<PhysicalActivity, Long> {
    List<PhysicalActivity> findByCreatedByIdOrderByActivityDateDescIdDesc(Long ownerId);
    Optional<PhysicalActivity> findByIdAndCreatedById(Long id, Long ownerId);

    /** The owner's categories, most used first, then most recently used. */
    @Query("select a.activityType from PhysicalActivity a where a.createdBy.id = :ownerId "
        + "group by a.activityType order by count(a) desc, max(a.activityDate) desc")
    List<String> findCategories(@Param("ownerId") Long ownerId);
}
