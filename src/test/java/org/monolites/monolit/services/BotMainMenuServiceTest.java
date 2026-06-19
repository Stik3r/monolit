package org.monolites.monolit.services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BotMainMenuServiceTest {

    @Test
    void showsShoppingListAndFullShoppingListCommands() {
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        BotMainMenuService service = new BotMainMenuService(messageSender);

        service.show("Главное меню");

        verify(messageSender).sendPersistentKeyboard(
                "Главное меню",
                List.of("Новое напоминание", "Мои напоминания", "Список покупок", "Весь список покупок"),
                List.of(2, 2)
        );
    }
}
