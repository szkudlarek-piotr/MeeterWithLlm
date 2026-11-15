package org.example.meeter.quote;

import static org.example.meeter.quote.QuoteModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/quotes") @Validated
public class QuoteController {
    public static final String PUBLIC_ONLY_COOKIE = "meeter-public-quotes";
    private final QuoteService service;
    public QuoteController(QuoteService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Input input) { return service.create(input); }
    @GetMapping("/{id}")
    public View get(@PathVariable @Positive Long id,
                    @CookieValue(name = PUBLIC_ONLY_COOKIE, defaultValue = "false") boolean publicOnly) {
        return service.get(id, publicOnly);
    }

    @PutMapping("/{id}/vote") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vote(@PathVariable @Positive Long id, @Valid @RequestBody VoteInput input,
                     @CookieValue(name = PUBLIC_ONLY_COOKIE, defaultValue = "false") boolean publicOnly) {
        service.vote(id, input.vote(), publicOnly);
    }

    @DeleteMapping("/{id}/vote") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retractVote(@PathVariable @Positive Long id,
                            @CookieValue(name = PUBLIC_ONLY_COOKIE, defaultValue = "false") boolean publicOnly) {
        service.retractVote(id, publicOnly);
    }
}
