package org.example.meeter.superpowers;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.example.meeter.auth.User;
import org.example.meeter.Human;
import org.hibernate.annotations.CurrentTimestamp;

/** A superpower assigned to a person. The record keeps its creator and creation time. */
@Entity @Table(name = "human_superpower")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HumanSuperpower {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "human_id", nullable = false)
    private Human human;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "superpower_id", nullable = false)
    private Superpower superpower;
    @CurrentTimestamp
    @Column(name = "add_time", nullable = false, updatable = false)
    private LocalDateTime addTime;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by_id", nullable = false)
    private User addedBy;

    public HumanSuperpower(Human human, Superpower superpower, User addedBy) {
        this.human = human;
        this.superpower = superpower;
        this.addedBy = addedBy;
    }
}
