package org.example.meeter.activity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;
import org.example.meeter.OwnedEntity;
import org.hibernate.annotations.CurrentTimestamp;

@Entity
@Table(name = "physical_activities", indexes = @Index(name = "idx_physical_activity_owner_date", columnList = "created_by_id,activity_date"))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhysicalActivity extends OwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    /** Free-text category chosen by the owner, e.g. "siłownia" or "joga". */
    @Column(name = "activity_type", nullable = false, length = 64)
    private String activityType;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(length = 1000)
    private String description;

    @CurrentTimestamp
    @Column(name = "add_time", nullable = false, updatable = false)
    private LocalDateTime addTime;

    public PhysicalActivity(String category, LocalDate date, String description) {
        update(category, date, description);
    }

    public void update(String category, LocalDate date, String description) {
        this.activityType = category;
        this.activityDate = date;
        this.description = description;
    }
}
