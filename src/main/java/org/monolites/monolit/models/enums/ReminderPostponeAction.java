package org.monolites.monolit.models.enums;

import java.time.Duration;

public enum ReminderPostponeAction {
    TEN_MINUTES(Duration.ofMinutes(10)),
    ONE_HOUR(Duration.ofHours(1)),
    THREE_HOURS(Duration.ofHours(3)),
    TWELVE_HOURS(Duration.ofHours(12)),
    TODAY(null);

    private final Duration duration;

    ReminderPostponeAction(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }
}
