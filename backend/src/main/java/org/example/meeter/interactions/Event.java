package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.*;
import java.util.Collection;
import lombok.*;
import org.example.meeter.*;

@Entity @Table(name = "events")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends Interaction {
    @Setter private LocalDateTime meComingDate;
    @Setter private LocalDateTime meLeavingDate;
    @Column(length = 255)
    @Setter private String photoName;
    @Column(length = 200)
    @Setter private String placeString;
    @Setter private String photoFile;
    public Event(String name, String description, LocalDateTime start, LocalDateTime end,
                 LocalDateTime coming, LocalDateTime leaving, Collection<Human> people, Place place, String photoName) {
        super(name, description, start, end, people, place);
        this.meComingDate = coming;
        this.meLeavingDate = leaving;
        this.photoName = photoName;
    }
    @Override public InteractionType getType() { return InteractionType.EVENT; }
    @Override public LocalDate getInteractionPointsDate() {
        return (meLeavingDate == null ? getEndDate() : meLeavingDate).toLocalDate();
    }
}
