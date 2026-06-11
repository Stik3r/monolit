package org.monolites.monolit.models.dtos;

import org.monolites.monolit.models.enums.CustomReminderAction;

public record CustomReminderActionDto(
        CustomReminderAction action,
        Long reminderId,
        int page
) {
}
