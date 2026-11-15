package org.example.meeter.quote.game;

import static org.example.meeter.quote.game.QuoteGameModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/quote-games") @Validated
public class QuoteGameController {
    private final QuoteGameService service;
    private final QuoteGameVisits visits;
    public QuoteGameController(QuoteGameService service, QuoteGameVisits visits) { this.service = service; this.visits = visits; }
    @GetMapping("/visits")
    public QuoteGameVisits.Page visits(@RequestParam(defaultValue = "") @Size(max = 200) String q,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "5") @Min(1) @Max(100) int size) {
        return visits.list(q, page, size);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody CreateInput input) { return service.create(input); }
    @PostMapping("/single") @ResponseStatus(HttpStatus.CREATED)
    public View createSingle(@Valid @RequestBody SingleInput input) { return service.createSingle(input); }
    @GetMapping("/single/history") public History singleHistory(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page) { return service.history(page, true); }
    @GetMapping("/single/leaderboard") public List<Score> singleLeaderboard() { return service.leaderboard(true); }
    @GetMapping public History history(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "false") boolean single) { return service.history(page, single); }
    @GetMapping("/leaderboard") public List<Score> leaderboard(@RequestParam(defaultValue = "false") boolean single) { return service.leaderboard(single); }
    @GetMapping("/{id}") public View get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping("/{id}/next") public View next(@PathVariable @Positive Long id) { return service.next(id); }
    @PostMapping("/{id}/guesses") public View guess(@PathVariable @Positive Long id, @Valid @RequestBody GuessInput input) { return service.guess(id, input); }
}
