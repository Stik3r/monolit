package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.MonthlyReminder;
import org.monolites.monolit.models.enums.ReminderPostponementType;
import org.monolites.monolit.models.enums.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface MonthlyReminderRepository extends JpaRepository<MonthlyReminder, Long> {

    MonthlyReminder findByReminderTypeAndDate(ReminderType reminderType, LocalDate date);

    List<MonthlyReminder> findAllByDoneFalseAndPostponementTypeAndPostponedUntilLessThanEqual(
            ReminderPostponementType postponementType,
            Instant postponedUntil
    );
}
