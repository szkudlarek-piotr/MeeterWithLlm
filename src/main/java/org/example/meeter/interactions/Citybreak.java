package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;
import org.example.meeter.*;
import org.example.meeter.auth.User;
import org.hibernate.annotations.BatchSize;

@Entity @Table(name = "trips")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Citybreak extends Interaction {
    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private Set<TripPlace> placeLinks = new LinkedHashSet<>();
    public Set<Place> getPlaces() {
        Set<Place> places = new LinkedHashSet<>();
        placeLinks.forEach(link -> places.add(link.getPlace()));
        return Collections.unmodifiableSet(places);
    }
    @Override public void assignOwner(User owner) {
        super.assignOwner(owner);
        placeLinks.forEach(link -> link.recordCreator(owner));
    }
    public Citybreak(String name, String description, LocalDateTime start, LocalDateTime end,
                     Collection<Human> people, Collection<Place> places) {
        super(name, description, start, end, people, null);
        new LinkedHashSet<>(places).forEach(place -> placeLinks.add(new TripPlace(this, place)));
    }
    @Override public InteractionType getType() { return InteractionType.TRIP; }

    void editPlaces(Collection<Place> places, User editor) {
        Set<Long> ids = new HashSet<>();
        places.forEach(place -> ids.add(place.getId()));
        placeLinks.removeIf(link -> !ids.contains(link.getPlace().getId()));
        for (Place place : places) {
            if (placeLinks.stream().anyMatch(link -> link.getPlace().getId().equals(place.getId()))) continue;
            TripPlace link = new TripPlace(this, place);
            link.recordCreator(editor);
            placeLinks.add(link);
        }
    }
}
