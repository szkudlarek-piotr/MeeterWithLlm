package org.example.meeter.interactions;
import org.springframework.data.jpa.repository.*;
public interface InteractionRepository extends JpaRepository<Interaction, Long>, JpaSpecificationExecutor<Interaction> {
    interface LastSharedInteraction {
        String getName();
        java.time.LocalDateTime getSeenAt();
    }

    @Query("""
        select i.name as name,
            case when i.endDate is null or i.endDate > :now then :now else i.endDate end as seenAt
        from Interaction i
        where i.startDate <= :now
          and exists (select p.id from InteractionParticipant p where p.interaction = i and p.human.id = :personId)
          and exists (select p.id from InteractionParticipant p where p.interaction = i and p.human.id = :selfId)
        order by case when i.endDate is null or i.endDate > :now then :now else i.endDate end desc,
            i.startDate desc, i.id desc
        """)
    java.util.List<LastSharedInteraction> lastSharedInteraction(Long personId, Long selfId,
        java.time.LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Interaction i where i.id = :id")
    java.util.Optional<Interaction> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
}
