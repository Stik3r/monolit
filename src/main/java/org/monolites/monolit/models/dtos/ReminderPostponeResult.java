package org.monolites.monolit.models.dtos;

import org.monolites.monolit.models.enums.ReminderPostponeAction;

import java.time.ZonedDateTime;

public record ReminderPostponeResult(
        boolean updated,
        ReminderPostponeAction action,
        ZonedDateTime postponedUntil
) {

    public static ReminderPostponeResult notUpdated() {
        return new ReminderPostponeResult(false, null, null);
    }
}
