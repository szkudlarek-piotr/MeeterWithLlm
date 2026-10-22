package org.example.meeter.quote;

import org.springframework.data.jpa.repository.*;

public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {}
