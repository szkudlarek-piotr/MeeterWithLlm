package org.example.meeter.news;

import java.time.LocalDateTime;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<NewsEntry, Long> {
    Page<NewsEntry> findByPublishedAtLessThanEqual(LocalDateTime now, Pageable pageable);
}
