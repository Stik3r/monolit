package org.monolites.monolit.init;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.reminder.ReminderConversationService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomReminderInitializerTest {

    @Test
    void publishesPersistentMainMenuOnStartup() {
        ReminderConversationService service = mock(ReminderConversationService.class);

        new CustomReminderInitializer(service).run();

        verify(service).showMainMenu("Бот готов.");
    }
}
