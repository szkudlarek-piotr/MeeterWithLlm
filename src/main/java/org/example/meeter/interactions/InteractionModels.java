package org.example.meeter.interactions;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.example.meeter.*;

public final class InteractionModels {
    private InteractionModels() {}

    public record Details(
        @NotBlank @Size(min = 2, max = 128) String name,
        @Size(max = 10240) String longDesc,
        @NotNull @Size(max = 200) Set<@NotNull @Positive Long> participantIds) {}

    public record ParticipantsInput(@NotEmpty @Size(max = 200) Set<@NotNull @Positive Long> participantIds) {}

    public record MeetingInput(@NotNull @Valid Details details, @NotNull LocalDate date,
                               @Positive Long placeId, @Size(max = 200) String placeString) {}
    public record VisitInput(@NotNull @Valid Details details, @NotNull LocalDateTime visitDate,
                             @NotNull @Min(1) @Max(3650) Integer duration, @Positive Long placeId) {}
    public record EventInput(@NotNull @Valid Details details, @NotNull LocalDateTime startDate,
                             @NotNull LocalDateTime stopDate, LocalDateTime meComingDate, LocalDateTime meLeavingDate,
                             @Positive Long placeId,
                             @Size(max = 255) String photoName, @Size(max = 200) String placeString,
                             @Size(max = 2048) String photoUrl, @Size(max = 5592408) String photoData,
                             @Positive Long photoEventId) {}
    public record TripInput(@NotNull @Valid Details details, @NotNull LocalDateTime startDate,
                            @NotNull LocalDateTime endDate, @NotNull @Size(max = 100) Set<@NotNull @Positive Long> placeIds) {}
    public record FlatmateInput(@NotNull @Valid Details details, @NotNull @Positive Long humanId,
                                @NotNull LocalDate startDate, LocalDate stopDate, @Positive Long placeId) {}
    public record WeddingInput(@NotNull @Valid Details details, @NotNull LocalDateTime dateTime,
                               @NotNull @Positive Long groomId, @NotNull @Positive Long brideId,
                               @Positive Long ceremonyPlaceId, @Positive Long partyPlaceId,
                               @Positive Long hotelId, boolean hadAfter, Boolean wasIInvited) {}

    public record PersonView(Long id, UUID uuid, String fullName, String profilePhotoUrl, boolean demo) {
        static PersonView of(Human human) { return human == null ? null : new PersonView(human.getId(), human.getUuid(), human.getFullName(), human.getProfilePhotoUrl(), human.isDemo()); }
        static PersonView participant(Human human, Long accountId) {
            var value = of(human);
            if (value.profilePhotoUrl() == null || (human.getCreatedBy() != null
                    && human.getCreatedBy().getId().equals(accountId))) return value;
            boolean hidden = human.getClique() != null
                && "my_exes".equalsIgnoreCase(human.getClique().getCliqueName().trim());
            String photo = hidden ? null : value.profilePhotoUrl().replace("/api/people/", "/api/people/browse/");
            return new PersonView(value.id(), value.uuid(), value.fullName(), photo, value.demo());
        }
    }
    public record View(Long id, UUID uuid, Instant createdAt, InteractionType type, String name, String longDesc,
                       LocalDateTime startDate, LocalDateTime endDate, List<PersonView> participants,
                       ApiModels.PlaceView place, Map<String, Object> details,
                       boolean canAddParticipants, Set<Long> removableParticipantIds) {}
    public record PageView(List<View> content, int page, int size, long totalElements, boolean hasNext) {}

    static ApiModels.PlaceView place(Place p) {
        return p == null ? null : new ApiModels.PlaceView(p.getId(), p.getName(), p.getLatitude(), p.getLongitude(), p.getCategory());
    }
    public static View view(Interaction item, Long accountId, Long humanId) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (item instanceof Meeting meeting) details.put("placeString", meeting.getPlaceString());
        if (item instanceof Visit visit) details.put("duration", visit.getDuration());
        if (item instanceof Event event) {
            details.put("meComingDate", event.getMeComingDate());
            details.put("meLeavingDate", event.getMeLeavingDate());
            details.put("photoName", event.getPhotoName());
            details.put("placeString", event.getPlaceString());
            details.put("photoUrl", event.getPhotoFile() == null ? null : "/api/events/" + event.getId() + "/photo");
        }
        if (item instanceof Citybreak trip)
            details.put("places", trip.getPlaces().stream().map(InteractionModels::place).toList());
        if (item instanceof Flatmate flatmate) details.put("human", PersonView.of(flatmate.getHuman()));
        if (item instanceof Wedding wedding) {
            details.put("groom", PersonView.of(wedding.getGroom()));
            details.put("bride", PersonView.of(wedding.getBride()));
            details.put("ceremonyPlace", place(wedding.getCeremonyPlace()));
            details.put("partyPlace", place(wedding.getPartyPlace()));
            details.put("hotel", place(wedding.getHotel()));
            details.put("hadAfter", wedding.isHadAfter());
            details.put("wasIInvited", wedding.isWasIInvited());
        }
        return new View(item.getId(), item.getUuid(), item.getCreatedAt(), item.getType(), item.getName(), item.getLongDesc(),
            item.getStartDate(), item.getEndDate(), item.getParticipants().stream().map(person -> PersonView.participant(person, accountId))
                .sorted(Comparator.comparing(PersonView::fullName).thenComparing(PersonView::id)).toList(),
            place(item.getPlace()), details, item.getCreatedBy().getId().equals(accountId),
            item.getParticipants().stream().map(Human::getId)
                .filter(id -> item.canRemoveParticipant(item.participantLink(id).orElseThrow(), accountId, humanId))
                .collect(java.util.stream.Collectors.toSet()));
    }
}
