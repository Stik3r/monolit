package org.monolites.monolit.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BotMainMenuServiceTest {

    @Test
    void showsMainMenuGroups() {
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        BotMainMenuService service = new BotMainMenuService(messageSender);

        service.show("Главное меню");

        verify(messageSender).sendPersistentKeyboard(
                "Главное меню",
                List.of("Напоминания", "Покупки"),
                List.of(2)
        );
    }

    @Test
    void showsReminderSubmenu() {
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        BotMainMenuService service = new BotMainMenuService(messageSender);

        service.showReminderMenu();

        verify(messageSender).sendPersistentKeyboard(
                "Выберите действие с напоминаниями.",
                List.of("Новое напоминание", "Мои напоминания", "Главное меню"),
                List.of(2, 1)
        );
    }

    @Test
    void showsShoppingSubmenu() {
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        BotMainMenuService service = new BotMainMenuService(messageSender);

        service.showShoppingMenu();

        verify(messageSender).sendPersistentKeyboard(
                "Выберите действие со списком покупок.",
                List.of("Список покупок", "Добавить покупку", "Главное меню"),
                List.of(2, 1)
        );
    }
}
