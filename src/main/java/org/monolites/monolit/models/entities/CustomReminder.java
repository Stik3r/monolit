package org.monolites.monolit.models.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.monolites.monolit.models.enums.CustomReminderStatus;

import java.time.Instant;

@Entity
@Table(name = "custom_reminder", indexes = {
        @Index(name = "custom_reminder_status_scheduled_at_idx", columnList = "status, scheduled_at")
})
@Getter
@Setter
public class CustomReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text", nullable = false, length = 2000)
    private String text;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CustomReminderStatus status;
}
