package org.example.meeter.superpowers;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.example.meeter.auth.User;
import org.example.meeter.multimedia.photo.Photo;
import org.hibernate.annotations.CurrentTimestamp;

/** Dedicated superpower dictionary, compatible with the original Meeter superpowers table. */
@Entity @Table(name = "superpowers")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Superpower {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();
    @Column(name = "superpower_name", nullable = false, length = 255)
    private String superpowerName;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "superpower_photo_id")
    @Setter
    private Photo superpowerPhoto;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by_id")
    private User addedBy;
    @CurrentTimestamp
    @Column(name = "add_time", nullable = false, updatable = false)
    private LocalDateTime addTime;

    public Superpower(String superpowerName, User addedBy) {
        this.superpowerName = superpowerName;
        this.addedBy = addedBy;
    }
}
