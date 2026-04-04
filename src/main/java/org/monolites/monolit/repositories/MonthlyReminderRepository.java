package org.monolites.monolit.repositories;

import org.monolites.monolit.models.entities.MonthlyReminder;
import org.monolites.monolit.models.enums.ReminderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface MonthlyReminderRepository extends JpaRepository<MonthlyReminder, Long> {

    MonthlyReminder findByReminderTypeAndDate(ReminderType reminderType, LocalDate date);
}
