package org.example.meeter.interactions;

import static org.example.meeter.interactions.InteractionModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Validated
public class InteractionController {
    private final InteractionService service;
    public InteractionController(InteractionService service) { this.service = service; }

    @GetMapping("/interactions")
    public PageView list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) InteractionType type,
        @RequestParam(defaultValue = "") @Size(max = 200) String q,
        @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(from, to, type, q, page, size);
    }
    @GetMapping("/interactions/{id}")
    public View get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping("/interactions/{id}/participants")
    public View addParticipants(@PathVariable @Positive Long id, @Valid @RequestBody ParticipantsInput input) {
        return service.addParticipants(id, input);
    }
    @DeleteMapping("/interactions/{id}/participants/{humanId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(@PathVariable @Positive Long id, @PathVariable @Positive Long humanId) {
        service.removeParticipant(id, humanId);
    }
    @PostMapping("/meetings") @ResponseStatus(HttpStatus.CREATED)
    public View meeting(@Valid @RequestBody MeetingInput input) { return service.meeting(input); }
    @PostMapping("/visits") @ResponseStatus(HttpStatus.CREATED)
    public View visit(@Valid @RequestBody VisitInput input) { return service.visit(input); }
    @PostMapping("/events") @ResponseStatus(HttpStatus.CREATED)
    public View event(@Valid @RequestBody EventInput input) { return service.event(input); }
    @PostMapping("/trips") @ResponseStatus(HttpStatus.CREATED)
    public View trip(@Valid @RequestBody TripInput input) { return service.trip(input); }
    @PostMapping("/flatmates") @ResponseStatus(HttpStatus.CREATED)
    public View flatmate(@Valid @RequestBody FlatmateInput input) { return service.flatmate(input); }
    @PostMapping("/weddings") @ResponseStatus(HttpStatus.CREATED)
    public View wedding(@Valid @RequestBody WeddingInput input) { return service.wedding(input); }

    @PutMapping("/meetings/{id}")
    public View editMeeting(@PathVariable @Positive Long id, @Valid @RequestBody MeetingInput input) { return service.editMeeting(id, input); }
    @PutMapping("/visits/{id}")
    public View editVisit(@PathVariable @Positive Long id, @Valid @RequestBody VisitInput input) { return service.editVisit(id, input); }
    @PutMapping("/events/{id}")
    public View editEvent(@PathVariable @Positive Long id, @Valid @RequestBody EventInput input) { return service.editEvent(id, input); }
    @PutMapping("/trips/{id}")
    public View editTrip(@PathVariable @Positive Long id, @Valid @RequestBody TripInput input) { return service.editTrip(id, input); }
    @PutMapping("/flatmates/{id}")
    public View editFlatmate(@PathVariable @Positive Long id, @Valid @RequestBody FlatmateInput input) { return service.editFlatmate(id, input); }
    @PutMapping("/weddings/{id}")
    public View editWedding(@PathVariable @Positive Long id, @Valid @RequestBody WeddingInput input) { return service.editWedding(id, input); }
}
