package org.monolites.monolit.schedulers;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.MonthlyReminderService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MonthlyReminderSchedulerTest {

    @Test
    void delegatesAllScheduledActions() {
        MonthlyReminderService service = mock(MonthlyReminderService.class);
        MonthlyReminderScheduler scheduler = new MonthlyReminderScheduler(service);

        scheduler.sendMeterReadingReminder();
        scheduler.createMeterReadingRecord();
        scheduler.sendUtilityPaymentReminder();
        scheduler.createUtilityPaymentRecord();
        scheduler.sendPostponedReminders();

        verify(service).sendMeterReadingReminder();
        verify(service).createMeterReadingRecord();
        verify(service).sendUtilityPaymentReminder();
        verify(service).createUtilityPaymentRecord();
        verify(service).sendPostponedReminders();
    }
}
