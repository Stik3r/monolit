package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BotMainMenuService {

    public static final String NEW_REMINDER = "Новое напоминание";
    public static final String MY_REMINDERS = "Мои напоминания";
    public static final String SHOPPING_LIST = "Список покупок";
    public static final String FULL_SHOPPING_LIST = "Весь список покупок";

    private static final List<String> LABELS = List.of(
            NEW_REMINDER,
            MY_REMINDERS,
            SHOPPING_LIST,
            FULL_SHOPPING_LIST
    );
    private static final List<Integer> ROWS = List.of(2, 2);

    private final VkMessageSenderService messageSender;

    public void show(String message) {
        messageSender.sendPersistentKeyboard(message, LABELS, ROWS);
    }
}
