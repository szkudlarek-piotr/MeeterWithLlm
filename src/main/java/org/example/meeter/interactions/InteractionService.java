package org.example.meeter.interactions;

import static org.example.meeter.interactions.InteractionModels.*;
import java.time.*;
import java.util.*;
import org.example.meeter.*;
import org.example.meeter.auth.*;
import org.example.meeter.photos.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InteractionService {
    private final InteractionRepository interactions;
    private final HumanRepository humans;
    private final PlaceRepository places;
    private final UserRepository users;
    private final CurrentAccount account;
    private final EventPhotoStorage eventPhotos;
    private final RemotePhotoClient photoClient;

    public InteractionService(InteractionRepository interactions, HumanRepository humans, PlaceRepository places,
                              UserRepository users, CurrentAccount account, EventPhotoStorage eventPhotos, RemotePhotoClient photoClient) {
        this.interactions = interactions; this.humans = humans; this.places = places;
        this.users = users; this.account = account;
        this.eventPhotos = eventPhotos; this.photoClient = photoClient;
    }

    private <T> Specification<T> owned() {
        Long id = account.id();
        return (root, query, cb) -> cb.equal(root.get("createdBy").get("id"), id);
    }
    private Human human(Long id) {
        return humans.findOne(this.<Human>owned().and((root, q, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> missing("osoby"));
    }
    private Place place(Long id) {
        if (id == null) return null;
        return places.findOne(this.<Place>owned().and((root, q, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> missing("miejsca"));
    }
    private Set<Human> participants(Details details) {
        Set<Long> ids = new LinkedHashSet<>(details.participantIds());
        ids.add(account.principal().humanId());
        Set<Human> result = new LinkedHashSet<>(humans.findAll(
            this.<Human>owned().and((root, q, cb) -> root.get("id").in(ids))));
        if (result.size() != ids.size()) throw missing("uczestnika");
        return result;
    }
    private Set<Place> route(Set<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        Set<Place> result = new LinkedHashSet<>(places.findAll(
            this.<Place>owned().and((root, q, cb) -> root.get("id").in(ids))));
        if (result.size() != ids.size()) throw missing("miejsca podróży");
        return result;
    }
    private View save(Interaction item) {
        item.assignOwner(users.getReferenceById(account.id()));
        return view(interactions.save(item));
    }
    private View view(Interaction item) {
        return InteractionModels.view(item, account.id(), account.principal().humanId());
    }

    @Transactional
    public View addParticipants(Long interactionId, ParticipantsInput input) {
        Interaction item = interactions.findLockedById(interactionId).orElseThrow(() -> missing("interakcji"));
        if (!item.getCreatedBy().getId().equals(account.id())) throw missing("interakcji");
        // Resolve every person before mutating, so a foreign ID fails the entire request.
        List<Human> additions = input.participantIds().stream().map(this::human).toList();
        User creator = users.getReferenceById(account.id());
        additions.forEach(person -> item.addParticipant(person, creator));
        interactions.flush();
        return view(item);
    }

    @Transactional
    public void removeParticipant(Long interactionId, Long humanId) {
        Interaction item = interactions.findLockedById(interactionId).orElseThrow(() -> missing("uczestnika"));
        InteractionParticipant link = item.participantLink(humanId).orElseThrow(() -> missing("uczestnika"));
        if (!item.canRemoveParticipant(link, account.id(), account.principal().humanId())) throw missing("uczestnika");
        item.removeParticipant(link);
        interactions.flush();
    }
    private String name(Details details) {
        String name = details.name().trim();
        if (name.length() < 2) throw bad("Nazwa musi mieć od 2 do 128 znaków.");
        return name;
    }
    private static String clean(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String what) { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono " + what + "."); }

    private static void dates(LocalDateTime start, LocalDateTime end) {
        if (start.getYear() < 1 || start.getYear() > 9999 ||
            end != null && (end.getYear() < 1 || end.getYear() > 9999))
            throw bad("Daty muszą mieścić się w latach 1–9999.");
        if (end != null && end.isBefore(start)) throw bad("Koniec nie może być wcześniejszy niż początek.");
    }

    @Transactional
    public View meeting(MeetingInput input) {
        dates(input.date().atStartOfDay(), input.date().atStartOfDay());
        return save(new Meeting(name(input.details()), clean(input.details().longDesc()), input.date(),
            participants(input.details()), place(input.placeId()), clean(input.placeString())));
    }
    @Transactional
    public View visit(VisitInput input) {
        dates(input.visitDate(), input.visitDate());
        LocalDateTime end = input.visitDate().plusDays(input.duration() - 1L);
        dates(input.visitDate(), end);
        return save(new Visit(name(input.details()), clean(input.details().longDesc()), input.visitDate(), input.duration(),
            participants(input.details()), place(input.placeId())));
    }
    @Transactional
    public View event(EventInput input) {
        dates(input.startDate(), input.stopDate());
        LocalDateTime coming = input.meComingDate() == null ? input.startDate() : input.meComingDate();
        LocalDateTime leaving = input.meLeavingDate() == null ? input.stopDate() : input.meLeavingDate();
        dates(coming, leaving);
        if (coming.isBefore(input.startDate()) || leaving.isAfter(input.stopDate()))
            throw bad("Twój udział musi mieścić się w czasie trwania eventu.");
        Event event = new Event(name(input.details()), clean(input.details().longDesc()), input.startDate(), input.stopDate(),
            input.meComingDate(), input.meLeavingDate(), participants(input.details()), place(input.placeId()), clean(input.photoName()));
        event.setPlaceString(clean(input.placeString()));
        int sources = (clean(input.photoUrl()) == null ? 0 : 1) + (clean(input.photoData()) == null ? 0 : 1) + (input.photoEventId() == null ? 0 : 1);
        if (sources > 1) throw bad("Wybierz jedno źródło zdjęcia.");
        if (input.photoEventId() != null) {
            Event source = ownEvent(input.photoEventId());
            if (source.getPhotoFile() == null) throw missing("zdjęcia");
            event.setPhotoFile(source.getPhotoFile());
        } else if (clean(input.photoUrl()) != null) {
            event.setPhotoFile(eventPhotos.store(photoClient.download(PhotoUrlPolicy.validate(input.photoUrl()))));
        } else if (clean(input.photoData()) != null) {
            byte[] bytes;
            try { bytes = Base64.getDecoder().decode(input.photoData()); }
            catch (IllegalArgumentException exception) { throw bad("Nieprawidłowy plik zdjęcia."); }
            event.setPhotoFile(eventPhotos.store(bytes));
        }
        return save(event);
    }
    @Transactional
    public View trip(TripInput input) {
        dates(input.startDate(), input.endDate());
        return save(new Citybreak(name(input.details()), clean(input.details().longDesc()), input.startDate(), input.endDate(),
            participants(input.details()), route(input.placeIds())));
    }
    @Transactional
    public View flatmate(FlatmateInput input) {
        dates(input.startDate().atStartOfDay(), input.stopDate() == null ? null : input.stopDate().atStartOfDay());
        if (input.humanId().equals(account.principal().humanId())) throw bad("Wybierz współlokatora innego niż Ty.");
        Human roommate = human(input.humanId());
        Set<Human> people = participants(input.details());
        people.add(roommate);
        return save(new Flatmate(name(input.details()), clean(input.details().longDesc()), input.startDate(), input.stopDate(),
            roommate, people, place(input.placeId())));
    }
    @Transactional
    public View wedding(WeddingInput input) {
        dates(input.dateTime(), input.dateTime());
        if (input.groomId().equals(input.brideId())) throw bad("Wybierz dwie różne osoby jako parę młodą.");
        Human groom = human(input.groomId());
        Human bride = human(input.brideId());
        Set<Human> people = participants(input.details());
        people.add(groom); people.add(bride);
        Wedding wedding=new Wedding(name(input.details()), clean(input.details().longDesc()), input.dateTime(), people,
            groom, bride, place(input.ceremonyPlaceId()), place(input.partyPlaceId()), place(input.hotelId()), input.hadAfter());
        wedding.setWasIInvited(Boolean.TRUE.equals(input.wasIInvited()));
        return save(wedding);
    }

    public View get(Long id) {
        return view(interactions.findOne(this.<Interaction>owned().and((root, q, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> missing("interakcji")));
    }

    // Profile reads have their own visibility rules; ordinary interaction APIs stay owner-only.
    private Specification<Interaction> profileFilter(Long personId, InteractionType type) {
        Long viewer = account.principal().humanId();
        boolean admin = account.isAdmin();
        Long owner = account.id();
        return (root, query, cb) -> {
            var subject = query.subquery(Long.class);
            var link = subject.from(InteractionParticipant.class);
            subject.select(link.get("id")).where(cb.equal(link.get("interaction"), root), cb.equal(link.get("human").get("id"), personId));
            var member = query.subquery(Long.class);
            var ownLink = member.from(InteractionParticipant.class);
            member.select(ownLink.get("id")).where(cb.equal(ownLink.get("interaction"), root), cb.equal(ownLink.get("human").get("id"), viewer));
            var allowed = admin ? cb.conjunction() : cb.or(cb.equal(root.get("createdBy").get("id"), owner), cb.exists(member));
            var supported = root.type().in(Meeting.class, Visit.class, Event.class, Citybreak.class);
            if (type != null) supported = cb.equal(root.type(), switch (type) {
                case MEETING -> Meeting.class; case VISIT -> Visit.class; case EVENT -> Event.class; case TRIP -> Citybreak.class;
                default -> Interaction.class;
            });
            return cb.and(cb.exists(subject), allowed, supported);
        };
    }

    public PageView forPerson(Long personId, InteractionType type, int page, int size) {
        var result = interactions.findAll(profileFilter(personId, type), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDate", "id")));
        return new PageView(result.stream().map(item -> {
            var value = view(item);
            // The profile is read-only, and does not grant access to owner-only event images.
            if (!item.getCreatedBy().getId().equals(account.id())) value.details().remove("photoUrl");
            return new View(value.id(), value.uuid(), value.createdAt(), value.type(), value.name(), value.longDesc(), value.startDate(), value.endDate(), value.participants(), value.place(), value.details(), false, Set.of());
        }).toList(), page, size, result.getTotalElements(), result.hasNext());
    }

    public record MapPoint(Long id, String name, double latitude, double longitude, String category) {}
    public List<MapPoint> personMap(Long personId) {
        List<MapPoint> points = new ArrayList<>();
        for (Interaction item : interactions.findAll(profileFilter(personId, null), Sort.by("startDate", "id"))) {
            Collection<Place> locations = item instanceof Citybreak trip ? trip.getPlaces() : item.getPlace() == null ? List.of() : List.of(item.getPlace());
            for (Place location : locations) points.add(new MapPoint((long) points.size(), item.getName() + " · " + item.getStartDate().toLocalDate() + " · " + location.getName(), location.getLatitude(), location.getLongitude(), location.getCategory()));
        }
        return points;
    }

    public Event ownEvent(Long id) {
        Interaction item = interactions.findOne(this.<Interaction>owned().and((root, q, cb) -> cb.equal(root.get("id"), id)))
            .orElseThrow(() -> missing("wydarzenia"));
        if (!(item instanceof Event event)) throw missing("wydarzenia");
        return event;
    }

    public PageView list(LocalDate from, LocalDate to, InteractionType type, String query, int page, int size) {
        if (from != null) dates(from.atStartOfDay(), from.atStartOfDay());
        if (to != null) dates(to.atStartOfDay(), to.atStartOfDay());
        if (from != null && to != null && to.isBefore(from)) throw bad("Nieprawidłowy zakres dat.");
        Specification<Interaction> filter = owned();
        if (from != null) filter = filter.and((root, q, cb) -> cb.or(
            cb.greaterThanOrEqualTo(root.get("endDate"), from.atStartOfDay()), cb.isNull(root.get("endDate"))));
        if (to != null) filter = filter.and((root, q, cb) -> cb.lessThan(root.get("startDate"), to.plusDays(1).atStartOfDay()));
        if (type != null) {
            Class<? extends Interaction> entity = switch (type) {
                case MEETING -> Meeting.class; case VISIT -> Visit.class; case EVENT -> Event.class;
                case TRIP -> Citybreak.class; case FLATMATE -> Flatmate.class; case WEDDING -> Wedding.class;
            };
            filter = filter.and((root, q, cb) -> cb.equal(root.type(), entity));
        }
        if (!query.isBlank()) {
            String pattern = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filter = filter.and((root, q, cb) -> cb.like(cb.lower(root.get("name")), pattern, '\\'));
        }
        var result = interactions.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PageView(result.stream().map(this::view).toList(), page, size, result.getTotalElements(), result.hasNext());
    }
}
