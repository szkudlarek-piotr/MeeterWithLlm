package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;
import lombok.*;
import org.example.meeter.*;

@Entity @Table(name = "visits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Visit extends Interaction {
    @Getter @Column(nullable = false)
    private int duration;
    public Visit(String name, String description, LocalDateTime date, int duration, Collection<Human> people, Place place) {
        super(name, description, date, date.plusDays(duration - 1L), people, place);
        this.duration = duration;
    }
    @Override public InteractionType getType() { return InteractionType.VISIT; }
}
