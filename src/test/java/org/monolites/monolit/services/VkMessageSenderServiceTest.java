package org.monolites.monolit.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.Keyboard;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VkMessageSenderServiceTest {

    @Test
    void buildsDoneButtonAndFivePostponeButtonsInSeparateRows() throws Exception {
        VkMessageSenderService service = new VkMessageSenderService(
                mock(VkApiClient.class),
                mock(GroupActor.class),
                new ObjectMapper(),
                "1"
        );

        Keyboard keyboard = service.buildKeyboard(
                List.of(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT),
                List.of("Передал", "10 минут", "1 час", "3 часа", "12 часов", "Не напоминать сегодня"),
                List.of("done", "10m", "1h", "3h", "12h", "today"),
                List.of(1, 5),
                true
        );

        assertThat(keyboard.getButtons()).hasSize(2);
        assertThat(keyboard.getButtons().get(0)).hasSize(1);
        assertThat(keyboard.getButtons().get(1)).hasSize(5);
        assertThat(keyboard.getInline()).isTrue();
    }
}
