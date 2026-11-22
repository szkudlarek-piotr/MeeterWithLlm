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
    public enum ActivityType { RUN, WALK, BIKE, GYM, SWIM, OTHER }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 32)
    private ActivityType activityType;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(length = 1000)
    private String description;

    @CurrentTimestamp
    @Column(name = "add_time", nullable = false, updatable = false)
    private LocalDateTime addTime;

    public PhysicalActivity(ActivityType type, LocalDate date, int durationMinutes, String description) {
        update(type, date, durationMinutes, description);
    }

    public void update(ActivityType type, LocalDate date, int durationMinutes, String description) {
        this.activityType = type;
        this.activityDate = date;
        this.durationMinutes = durationMinutes;
        this.description = description;
    }
}
