package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Collection;
import lombok.*;
import org.example.meeter.*;

@Entity @Table(name = "flatmates")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flatmate extends Interaction {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Human human;
    public Flatmate(String name, String description, LocalDate start, LocalDate end,
                    Human human, Collection<Human> people, Place home) {
        super(name, description, start.atStartOfDay(), end == null ? null : end.atStartOfDay(), people, home);
        this.human = human;
    }
    @Override public InteractionType getType() { return InteractionType.FLATMATE; }
}
