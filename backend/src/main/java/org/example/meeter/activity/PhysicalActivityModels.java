package org.example.meeter.activity;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class PhysicalActivityModels {
    private PhysicalActivityModels() {}

    public record Input(@NotBlank @Size(max = 64) String category, @NotNull LocalDate date,
                        @Size(max = 1000) String description) {}

    public record View(Long id, UUID uuid, String category, LocalDate date, String description, LocalDateTime addTime) {
        static View of(PhysicalActivity activity) {
            return new View(activity.getId(), activity.getUuid(), activity.getActivityType(),
                activity.getActivityDate(), activity.getDescription(), activity.getAddTime());
        }
    }

    /** weekCount covers Monday to today; streakDays counts consecutive days with activity. */
    public record Summary(int weekCount, int streakDays) {}
}
