package org.example.meeter.news;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/news")
@Transactional(readOnly = true)
public class NewsController {
    private final NewsRepository news;
    public NewsController(NewsRepository news) { this.news = news; }
    public record Item(Long id, String title, String content, LocalDateTime publishedAt) {}
    public record PageView(List<Item> content, int page, int size, long totalElements, boolean hasNext) {}
    @GetMapping
    public PageView list(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
                         @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        var result = news.findByPublishedAtLessThanEqual(LocalDateTime.now(ZoneId.of("Europe/Warsaw")),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt", "id")));
        return new PageView(result.stream().map(n -> new Item(n.getId(), n.getTitle(), n.getContent(), n.getPublishedAt())).toList(),
            page, size, result.getTotalElements(), result.hasNext());
    }
}
