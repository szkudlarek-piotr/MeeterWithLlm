package org.example.meeter.quote;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuoteVoteRepository extends JpaRepository<QuoteVote, Long> {
    Optional<QuoteVote> findByQuoteIdAndVoterId(Long quoteId, Long voterId);
}
