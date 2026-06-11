package org.monolites.monolit.schedulers;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.CustomReminderService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomReminderSchedulerTest {

    @Test
    void delegatesDueReminderCheck() {
        CustomReminderService service = mock(CustomReminderService.class);

        new CustomReminderScheduler(service).sendDueReminders();

        verify(service).sendDueReminders();
    }
}
