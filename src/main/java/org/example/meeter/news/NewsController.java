package org.example.meeter.news;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.time.*;
import java.util.List;
import java.util.UUID;
import org.example.meeter.auth.CurrentAccount;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/news")
@Transactional(readOnly = true)
public class NewsController {
    private final NewsRepository news;
    private final CurrentAccount account;
    public NewsController(NewsRepository news, CurrentAccount account) {
        this.news = news;
        this.account = account;
    }
    public record PublishInput(@NotBlank @Size(max = 160) String title,
                               @NotBlank @Size(max = 10240) String content) {}

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void publish(@Valid @RequestBody PublishInput input) {
        if (!account.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        news.save(new NewsEntry(UUID.randomUUID().toString(), input.title().strip(), input.content().strip(),
            LocalDateTime.now(ZoneId.of("Europe/Warsaw"))));
    }
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
