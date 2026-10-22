package org.example.meeter.interactions;

import jakarta.persistence.*;
import lombok.*;
import org.example.meeter.Place;

@Entity
@Table(name = "trip_place", uniqueConstraints = @UniqueConstraint(name = "uk_trip_place_pair", columnNames = {"trip_id", "place_id"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPlace extends RelationshipRecord {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false, updatable = false)
    private Citybreak trip;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false, updatable = false)
    private Place place;

    TripPlace(Citybreak trip, Place place) { this.trip = trip; this.place = place; }
}
