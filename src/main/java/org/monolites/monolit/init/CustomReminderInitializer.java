package org.monolites.monolit.init;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.ReminderConversationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "monolit.vk.long-poll.enabled", havingValue = "true", matchIfMissing = true)
public class CustomReminderInitializer implements CommandLineRunner {

    private final ReminderConversationService reminderConversationService;

    @Override
    public void run(String... args) {
        reminderConversationService.showMainMenu("Бот готов.");
    }
}
