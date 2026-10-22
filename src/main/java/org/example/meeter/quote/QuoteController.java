package org.example.meeter.quote;

import static org.example.meeter.quote.QuoteModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/quotes") @Validated
public class QuoteController {
    private final QuoteService service;
    public QuoteController(QuoteService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Input input) { return service.create(input); }
    @GetMapping("/{id}")
    public View get(@PathVariable @Positive Long id) { return service.get(id); }

}
