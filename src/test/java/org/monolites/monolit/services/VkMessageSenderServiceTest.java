package org.monolites.monolit.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionText;
import com.vk.api.sdk.queries.EnumParam;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    void keepsSingleRowContractOfExistingKeyboardMethod() throws Exception {
        VkMessageSenderService service = service();

        Keyboard keyboard = service.buildKeyboard(
                List.of(TEXT, TEXT),
                List.of("one", "two"),
                List.of("one", "two"),
                true
        );

        assertThat(keyboard.getButtons()).singleElement().satisfies(row -> assertThat(row).hasSize(2));
    }

    @Test
    void buildsPersistentKeyboardWithoutButtonPayloads() throws Exception {
        VkMessageSenderService service = service();

        Keyboard keyboard = service.buildKeyboard(
                List.of(TEXT, TEXT),
                List.of("Новое напоминание", "Мои напоминания"),
                java.util.Arrays.asList(null, null),
                List.of(2),
                false
        );

        assertThat(keyboard.getInline()).isFalse();
        assertThat(keyboard.getOneTime()).isFalse();
        KeyboardButtonActionText action =
                (KeyboardButtonActionText) keyboard.getButtons().getFirst().getFirst().getAction();
        assertThat(action.getPayload()).isNull();
    }

    @Test
    void rejectsInconsistentAndNonPositiveRowSizes() {
        VkMessageSenderService service = service();
        List<EnumParam<String>> buttonTypes = List.of(TEXT);
        List<String> labels = List.of("one");
        List<Object> payloads = List.of("one");
        List<Integer> inconsistentRowSizes = List.of(2);
        List<Integer> nonPositiveRowSizes = List.of(0, 1);

        assertThatThrownBy(() -> service.buildKeyboard(
                buttonTypes,
                labels,
                payloads,
                inconsistentRowSizes,
                true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.buildKeyboard(
                buttonTypes,
                labels,
                payloads,
                nonPositiveRowSizes,
                true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private VkMessageSenderService service() {
        return new VkMessageSenderService(
                mock(VkApiClient.class),
                mock(GroupActor.class),
                new ObjectMapper(),
                "1"
        );
    }
}
