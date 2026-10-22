package org.example.meeter.news;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity @Table(name = "app_news")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String slug;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 10240)
    private String content;
    @Column(nullable = false)
    private LocalDateTime publishedAt;

    public NewsEntry(String slug, String title, String content, LocalDateTime publishedAt) {
        this.slug = slug; this.title = title; this.content = content; this.publishedAt = publishedAt;
    }
}
