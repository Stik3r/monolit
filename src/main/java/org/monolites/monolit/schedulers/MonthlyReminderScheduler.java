package org.monolites.monolit.schedulers;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.MonthlyReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MonthlyReminderScheduler {

    private final MonthlyReminderService monthlyReminderService;

    @Scheduled(cron = "${monolit.reminders.meter-reading.cron}", zone = "${monolit.reminders.zone}")
    public void sendMeterReadingReminder() {
        monthlyReminderService.sendMeterReadingReminder();
    }

    @Scheduled(cron = "${monolit.reminders.meter-reading.record.cron}", zone = "${monolit.reminders.zone}")
    public void createMeterReadingRecord() {
        monthlyReminderService.createMeterReadingRecord();
    }

    @Scheduled(cron = "${monolit.reminders.utility-payment.cron}", zone = "${monolit.reminders.zone}")
    public void sendUtilityPaymentReminder() {
        monthlyReminderService.sendUtilityPaymentReminder();
    }

    @Scheduled(cron = "${monolit.reminders.utility-payment.record.cron}", zone = "${monolit.reminders.zone}")
    public void createUtilityPaymentRecord() {
        monthlyReminderService.createUtilityPaymentRecord();
    }

    @Scheduled(cron = "${monolit.reminders.postponed.cron}", zone = "${monolit.reminders.zone}")
    public void sendPostponedReminders() {
        monthlyReminderService.sendPostponedReminders();
    }
}
