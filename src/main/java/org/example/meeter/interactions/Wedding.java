package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;
import lombok.*;
import org.example.meeter.*;

@Entity @Table(name = "weddings")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wedding extends Interaction {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Human groom;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Human bride;
    @ManyToOne(fetch = FetchType.LAZY) private Place ceremonyPlace;
    @ManyToOne(fetch = FetchType.LAZY) private Place partyPlace;
    @ManyToOne(fetch = FetchType.LAZY) private Place hotel;
    @Column(nullable = false) private boolean hadAfter;
    @Column(name = "was_i_invited", nullable = false, columnDefinition = "boolean default false")
    @Setter private boolean wasIInvited;
    public Wedding(String name, String description, LocalDateTime date, Collection<Human> guests,
                   Human groom, Human bride, Place ceremony, Place party, Place hotel, boolean hadAfter) {
        super(name, description, date, date, guests, null);
        this.groom = groom; this.bride = bride; ceremonyPlace = ceremony;
        partyPlace = party; this.hotel = hotel; this.hadAfter = hadAfter;
    }
    @Override public InteractionType getType() { return InteractionType.WEDDING; }
}
