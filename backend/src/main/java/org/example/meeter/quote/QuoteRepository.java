package org.example.meeter.quote;

import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quote q where q.id = :id")
    Optional<Quote> lockById(Long id);

    interface RankedQuote {
        Quote getQuote();
        long getScore();
        Integer getMyVote();
    }

    String VISIBLE_AUTHOR = " from Quote q where q.author.id = :authorId and (:seePrivate = true or q.isPublic = true)";

    @Query(value = """
        select q as quote,
            coalesce((select sum(v.value) from QuoteVote v where v.quote = q), 0) as score,
            (select v.value from QuoteVote v where v.quote = q and v.voter.id = :voterId) as myVote
        """ + VISIBLE_AUTHOR + " order by score desc, q.addTime desc, q.id desc",
        countQuery = "select count(q)" + VISIBLE_AUTHOR)
    Page<RankedQuote> rankedByAuthor(Long authorId, boolean seePrivate, Long voterId, Pageable pageable);
}
