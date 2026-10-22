package org.example.meeter.quote.game;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QuoteGameRepository extends JpaRepository<QuoteGame, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from QuoteGame g where g.id=:id and g.createdBy.id=:owner")
    Optional<QuoteGame> ownedForUpdate(@Param("id") Long id, @Param("owner") Long owner);
    Page<QuoteGame> findByCreatedById(Long owner, Pageable pageable);

    interface ScoreRow {
        Long getGameId(); Long getPlayerId(); String getFirstName(); String getSurname(); String getScopeLabel();
        Instant getFinishedAt(); Long getEligible(); Long getCorrect(); Long getElapsedMillis();
    }
    @Query("""
        select g.id as gameId, g.createdBy.id as playerId, g.createdBy.human.name as firstName,
               g.createdBy.human.surname as surname, g.scopeType as scopeLabel, g.finishedAt as finishedAt,
               sum(case when a.selfQuote=false then 1 else 0 end) as eligible,
               sum(case when a.selfQuote=false and a.correct=true then 1 else 0 end) as correct,
               sum(case when a.selfQuote=false then a.elapsedMillis else 0 end) as elapsedMillis
        from QuoteGame g join g.rounds r join r.guess a where g.finishedAt is not null
        group by g.id, g.createdBy.id, g.createdBy.human.name, g.createdBy.human.surname, g.scopeType, g.finishedAt
        """)
    List<ScoreRow> scores();
}
