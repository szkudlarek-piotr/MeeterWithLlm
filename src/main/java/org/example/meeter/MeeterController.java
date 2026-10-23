package org.example.meeter;
import static org.example.meeter.ApiModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") @Validated
public class MeeterController {
 private final MeeterService service;
 public MeeterController(MeeterService service) { this.service=service; }
 @GetMapping("/places") public List<PlaceView> places(@RequestParam(defaultValue="") @Size(max=200) String q,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) { return service.places(q,limit); }
 @PostMapping("/places") @ResponseStatus(HttpStatus.CREATED) public PlaceView addPlace(@Valid @RequestBody PlaceInput input) { return service.addPlace(input); }
 @GetMapping("/cliques") public List<CliqueView> cliques(@RequestParam(defaultValue="") @Size(max=200) String q,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) { return service.cliques(q,limit); }
 @PostMapping("/cliques") @ResponseStatus(HttpStatus.CREATED) public CliqueView addClique(@Valid @RequestBody CliqueInput input) { return service.addClique(input); }
 @GetMapping("/people") public List<HumanView> people(@RequestParam(defaultValue="") @Size(max=200) String q,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) { return service.people(q,limit); }
 @GetMapping("/quotes/authors") public List<org.example.meeter.quote.QuoteModels.AuthorView> quoteAuthors(@RequestParam(defaultValue="") @Size(max=200) String q,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) { return service.quoteAuthors(q,limit); }
 @PostMapping("/people") @ResponseStatus(HttpStatus.CREATED) public HumanView addHuman(@Valid @RequestBody HumanInput input) { return service.addHuman(input); }
 @GetMapping("/people/browse") public PeoplePage browsePeople(@RequestParam(defaultValue="") @Size(max=200) String q,
  @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.browsePeople(q,page,size); }
}
