package org.example.meeter.activity;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.example.meeter.activity.PhysicalActivity.ActivityType;

public final class PhysicalActivityModels {
    private PhysicalActivityModels() {}

    public record Input(@NotNull ActivityType type, @NotNull LocalDate date,
                        @NotNull @Min(1) @Max(1440) Integer durationMinutes,
                        @Size(max = 1000) String description) {}

    public record View(Long id, UUID uuid, ActivityType type, LocalDate date, int durationMinutes,
                       String description, LocalDateTime addTime) {
        static View of(PhysicalActivity activity) {
            return new View(activity.getId(), activity.getUuid(), activity.getActivityType(),
                activity.getActivityDate(), activity.getDurationMinutes(), activity.getDescription(), activity.getAddTime());
        }
    }

    /** weekCount/weekMinutes cover Monday to today; streakDays counts consecutive days with activity. */
    public record Summary(int weekCount, int weekMinutes, int streakDays) {}
}
