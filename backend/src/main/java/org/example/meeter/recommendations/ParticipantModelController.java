package org.example.meeter.recommendations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.example.meeter.*;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.quote.QuoteModels.AuthorView;
import org.example.meeter.interactions.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/participant-models")
@Transactional(readOnly = true)
public class ParticipantModelController {
    public record Input(
        @NotBlank @Pattern(regexp="(context|guests)_(logistic|extra_trees|boosting|neural)") String model,
        @NotBlank @Size(min=2,max=128) String name,
        @Size(max=200) String placeText,
        @NotNull @Size(max=100) List<@NotNull @Positive Long> placeIds,
        @NotNull @Size(max=200) List<@NotNull @Positive Long> guestIds,
        @Positive Long interactionId,
        java.time.LocalDate startDate,
        @Pattern(regexp="MEETING|VISIT|EVENT|TRIP|WEDDING|FLATMATE") String type) {}
    private final ParticipantModelClient models;
    private final CurrentAccount account;
    private final PlaceRepository places;
    private final HumanRepository people;
    private final ParticipantHistory history;
    private final InteractionRepository interactions;
    public ParticipantModelController(ParticipantModelClient models, CurrentAccount account,
                                      PlaceRepository places, HumanRepository people, ParticipantHistory history, InteractionRepository interactions) {
        this.models=models; this.account=account; this.places=places; this.people=people; this.history=history; this.interactions=interactions;
    }

    @GetMapping public List<ParticipantModelClient.Model> models() {
        return models.models(account.id(), account.principal().humanId());
    }

    @PostMapping("/suggest") public List<AuthorView> suggest(@Valid @RequestBody Input input) {
        var owner = account.id();
        var self = account.principal().humanId();
        Interaction current = input.interactionId() == null ? null : interactions.findById(input.interactionId()).orElseThrow(() -> missing("interakcji"));
        if (current != null && (current.getCreatedBy() == null || !owner.equals(current.getCreatedBy().getId()))) throw missing("interakcji");
        var retainedPlaces = new HashSet<Long>();
        if (current != null) {
            if (current.getPlace() != null) retainedPlaces.add(current.getPlace().getId());
            if (current instanceof Citybreak trip) trip.getPlaces().forEach(p -> retainedPlaces.add(p.getId()));
            if (current instanceof Wedding wedding) {
                for (var p : new Place[] {wedding.getCeremonyPlace(), wedding.getPartyPlace(), wedding.getHotel()})
                    if (p != null) retainedPlaces.add(p.getId());
            }
        }
        if (input.name().trim().length() < 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Podaj nazwę o długości co najmniej 2 znaków.");
        var placeIds = new TreeSet<>(input.placeIds());
        var locations = new ArrayList<Map<String,Object>>();
        for (var id : placeIds) {
            var place = places.findById(id).orElseThrow(() -> missing("miejsca"));
            if (!retainedPlaces.contains(id) && (place.getCreatedBy() == null || !owner.equals(place.getCreatedBy().getId()))) throw missing("miejsca");
            locations.add(Map.of("id", place.getId(), "name", place.getName()));
        }
        var guests = new LinkedHashSet<>(input.guestIds());
        if (people.findAllById(guests).size() != guests.size()) throw missing("osoby");
        if (current != null) current.getParticipants().forEach(person -> guests.add(person.getId()));
        if (models.models(owner, self).stream().noneMatch(m -> input.model().equals(m.id())))
            throw missing("modelu");
        var query = new HashMap<String,Object>(Map.of("account_id", owner, "self_id", self,
            "model", input.model(), "name", input.name().trim(),
            "place_text", input.placeText() == null ? "" : input.placeText().trim(),
            "places", locations, "guest_ids", guests, "k", 10, "history", history.rows(owner, self)));
        var queryDate = input.startDate() != null ? input.startDate()
            : current != null ? current.getStartDate().toLocalDate() : java.time.LocalDate.now();
        query.put("start_date", queryDate.toString());
        if (input.type() != null) query.put("type", input.type());
        var ids = models.suggest(query);
        // Same all-Human scope and minimal DTO as the participant picker; no admin bypass.
        var found = new HashMap<Long,Human>();
        people.findAllById(ids).forEach(h -> found.put(h.getId(), h));
        return ids.stream().distinct().filter(id -> !id.equals(self) && !guests.contains(id))
            .map(found::get).filter(Objects::nonNull)
            .map(h -> new AuthorView(h.getId(), h.getFullName(), h.getProfilePhotoUrl() == null ? null
                : h.getProfilePhotoUrl().replace("/api/people/", "/api/people/participants/"))).toList();
    }

    private ResponseStatusException missing(String entity) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono " + entity + ".");
    }
}
