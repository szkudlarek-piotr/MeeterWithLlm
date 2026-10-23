package org.example.meeter;

import jakarta.validation.constraints.*;
import org.example.meeter.interactions.*;
import org.example.meeter.quote.QuoteService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/people/browse/{id}")
public class PersonProfileController {
    private final MeeterService people;
    private final InteractionService interactions;
    private final QuoteService quotes;
    public PersonProfileController(MeeterService people, InteractionService interactions, QuoteService quotes) {
        this.people=people; this.interactions=interactions; this.quotes=quotes;
    }
    @GetMapping public ApiModels.PersonProfileView profile(@PathVariable Long id) {
        return people.profile(id);
    }
    @GetMapping("/interactions") public InteractionModels.PageView interactions(@PathVariable Long id,
        @RequestParam(required=false) InteractionType type, @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        people.browseHuman(id);
        return interactions.forPerson(id,type,page,size);
    }
    @GetMapping("/map") public java.util.List<InteractionService.MapPoint> map(@PathVariable Long id) {
        people.browseHuman(id); return interactions.personMap(id);
    }
    @GetMapping("/quotes") public QuoteService.AuthorPage quotes(@PathVariable Long id,
        @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        people.browseHuman(id); return quotes.forAuthor(id,page,size);
    }
}
