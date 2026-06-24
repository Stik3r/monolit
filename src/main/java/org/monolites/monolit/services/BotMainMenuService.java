package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BotMainMenuService {

    public static final String REMINDERS = "Напоминания";
    public static final String SHOPPING = "Покупки";
    public static final String MAIN_MENU = "Главное меню";
    public static final String NEW_REMINDER = "Новое напоминание";
    public static final String MY_REMINDERS = "Мои напоминания";
    public static final String SHOPPING_LIST = "Список покупок";
    public static final String FULL_SHOPPING_LIST = "Весь список покупок";
    public static final String ADD_SHOPPING_ITEM = "Добавить покупку";

    private static final List<String> MAIN_MENU_LABELS = List.of(
            REMINDERS,
            SHOPPING
    );
    private static final List<Integer> MAIN_MENU_ROWS = List.of(2);
    private static final List<String> REMINDER_MENU_LABELS = List.of(
            NEW_REMINDER,
            MY_REMINDERS,
            MAIN_MENU
    );
    private static final List<Integer> REMINDER_MENU_ROWS = List.of(2, 1);
    private static final List<String> SHOPPING_MENU_LABELS = List.of(
            SHOPPING_LIST,
            ADD_SHOPPING_ITEM,
            MAIN_MENU
    );
    private static final List<Integer> SHOPPING_MENU_ROWS = List.of(2, 1);

    private final VkMessageSenderService messageSender;

    public void show(String message) {
        messageSender.sendPersistentKeyboard(message, MAIN_MENU_LABELS, MAIN_MENU_ROWS);
    }

    public void showReminderMenu() {
        messageSender.sendPersistentKeyboard("Выберите действие с напоминаниями.", REMINDER_MENU_LABELS, REMINDER_MENU_ROWS);
    }

    public void showShoppingMenu() {
        messageSender.sendPersistentKeyboard("Выберите действие со списком покупок.", SHOPPING_MENU_LABELS, SHOPPING_MENU_ROWS);
    }
}
