package org.example.meeter.multimedia.photo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

/** Metadata compatible with the original Meeter photos table. Files remain outside static resources. */
@Entity
@Table(name = "photos")
@Getter @Setter @NoArgsConstructor
public class Photo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String photoName;
    private Double latitude;
    private Double longitude;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PhotoType photoType;
    private LocalDateTime generationTime;
    private UUID uuid = UUID.randomUUID();
}
