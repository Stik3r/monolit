package org.monolites.monolit.schedulers;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.CustomReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomReminderScheduler {

    private final CustomReminderService customReminderService;

    @Scheduled(cron = "${monolit.reminders.custom.cron}", zone = "${monolit.reminders.zone}")
    public void sendDueReminders() {
        customReminderService.sendDueReminders();
    }
}
