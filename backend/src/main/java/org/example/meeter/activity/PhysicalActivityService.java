package org.example.meeter.activity;

import static org.example.meeter.activity.PhysicalActivityModels.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.example.meeter.auth.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly = true)
public class PhysicalActivityService {
    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private final PhysicalActivityRepository activities;
    private final CurrentAccount account;
    private final UserRepository users;

    public PhysicalActivityService(PhysicalActivityRepository activities, CurrentAccount account, UserRepository users) {
        this.activities = activities; this.account = account; this.users = users;
    }

    public List<View> list() {
        return activities.findByCreatedByIdOrderByActivityDateDescIdDesc(account.id()).stream().map(View::of).toList();
    }

    public Summary summary() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        List<PhysicalActivity> all = activities.findByCreatedByIdOrderByActivityDateDescIdDesc(account.id());
        int weekCount = 0;
        Set<LocalDate> days = new HashSet<>();
        for (PhysicalActivity activity : all) {
            LocalDate date = activity.getActivityDate();
            days.add(date);
            if (!date.isBefore(monday) && !date.isAfter(today)) {
                weekCount++;
            }
        }
        // The streak may still count yesterday if nothing has been logged yet today.
        LocalDate day = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(day)) { streak++; day = day.minusDays(1); }
        return new Summary(weekCount, streak);
    }

    @Transactional public View create(Input input) {
        var activity = new PhysicalActivity(input.type(), validDate(input.date()), clean(input.description()));
        activity.assignOwner(users.getReferenceById(account.id()));
        return View.of(activities.saveAndFlush(activity));
    }

    @Transactional public View update(Long id, Input input) {
        var activity = owned(id);
        activity.update(input.type(), validDate(input.date()), clean(input.description()));
        return View.of(activities.saveAndFlush(activity));
    }

    @Transactional public void delete(Long id) {
        activities.delete(owned(id));
    }

    private PhysicalActivity owned(Long id) {
        return activities.findByIdAndCreatedById(id, account.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono aktywności."));
    }

    private static LocalDate validDate(LocalDate date) {
        if (date.isAfter(LocalDate.now(ZONE)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data aktywności nie może być z przyszłości.");
        return date;
    }

    private static String clean(String description) {
        if (description == null || description.isBlank()) return null;
        return description.strip();
    }
}
