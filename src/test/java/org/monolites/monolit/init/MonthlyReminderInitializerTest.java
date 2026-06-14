package org.monolites.monolit.init;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.MonthlyReminderService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MonthlyReminderInitializerTest {

    @Test
    void initializesCurrentMonthRecords() {
        MonthlyReminderService service = mock(MonthlyReminderService.class);

        new MonthlyReminderInitializer(service).run();

        verify(service).initializeCurrentMonthRecords();
    }
}
