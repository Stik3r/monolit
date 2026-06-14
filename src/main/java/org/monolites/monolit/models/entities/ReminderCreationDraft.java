package org.monolites.monolit.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.monolites.monolit.models.enums.ReminderCreationStep;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "reminder_creation_draft")
@Getter
@Setter
public class ReminderCreationDraft {

    public static final long SINGLE_USER_DRAFT_ID = 1L;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false)
    private ReminderCreationStep step;

    @Column(name = "reminder_text", length = 2000)
    private String reminderText;

    @Column(name = "selected_date")
    private LocalDate selectedDate;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;
}
