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
    public QuoteGameController(QuoteGameService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody CreateInput input) { return service.create(input); }
    @GetMapping public History history(@RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page) { return service.history(page); }
    @GetMapping("/leaderboard") public List<Score> leaderboard() { return service.leaderboard(); }
    @GetMapping("/{id}") public View get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping("/{id}/next") public View next(@PathVariable @Positive Long id) { return service.next(id); }
    @PostMapping("/{id}/guesses") public View guess(@PathVariable @Positive Long id, @Valid @RequestBody GuessInput input) { return service.guess(id, input); }
}
