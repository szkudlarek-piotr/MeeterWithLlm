package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Collection;
import lombok.*;
import org.example.meeter.*;

@Entity @Table(name = "meetings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Meeting extends Interaction {
    public Meeting(String name, String description, LocalDate date, Collection<Human> people, Place place, String placeString) {
        super(name, description, date.atStartOfDay(), date.atStartOfDay(), people, place);
        this.placeString = placeString;
    }
    @Getter @Column(length = 200)
    private String placeString;
    @Override public InteractionType getType() { return InteractionType.MEETING; }
}
