package org.monolites.monolit.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.monolites.monolit.models.enums.ReminderType;

import java.time.LocalDate;

@Entity
@Table(name = "monthly_reminder", indexes = {
        @Index(name = "monthly_reminder_reminder_type_idx", columnList = "reminder_type, date", unique = true)
})
@Getter
@Setter
public class MonthlyReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type")
    private ReminderType reminderType;


    @Column(name = "date")
    private LocalDate date;

    @Column(name = "done")
    private boolean done;
}
