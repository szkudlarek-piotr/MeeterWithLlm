package org.example.meeter;

import static org.example.meeter.quote.QuoteController.PUBLIC_ONLY_COOKIE;
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
    private final PeopleRanking ranking;
    public PersonProfileController(MeeterService people, InteractionService interactions, QuoteService quotes, PeopleRanking ranking) {
        this.people=people; this.interactions=interactions; this.quotes=quotes; this.ranking=ranking;
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
    @GetMapping("/seen-with") public java.util.List<ApiModels.HumanView> seenWith(@PathVariable Long id,
        @RequestParam(defaultValue="10") @Min(1) @Max(20) int limit) {
        people.browseHuman(id); return ranking.seenWith(id,limit);
    }
    @GetMapping("/map") public java.util.List<InteractionService.MapPoint> map(@PathVariable Long id) {
        people.browseHuman(id); return interactions.personMap(id);
    }
    @GetMapping("/quotes") public QuoteService.AuthorPage quotes(@PathVariable Long id,
        @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
        @CookieValue(name = PUBLIC_ONLY_COOKIE, defaultValue = "false") boolean publicOnly) {
        people.browseHuman(id); return quotes.forAuthor(id,page,size,publicOnly);
    }
}
