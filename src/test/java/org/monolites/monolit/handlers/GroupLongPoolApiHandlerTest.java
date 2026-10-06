package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.MessageObject;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionText;
import com.vk.api.sdk.objects.messages.Message;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.configs.JacksonConfig;
import org.monolites.monolit.handlers.callbacks.CallbackPayloadDispatcher;
import org.monolites.monolit.handlers.callbacks.CallbackPayloadHandler;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.monolites.monolit.services.VkMessageSenderService;

import java.util.List;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;
import static org.mockito.Mockito.*;

class GroupLongPoolApiHandlerTest {

    @Test
    void ignoresPayloadFromUsersOtherThanConfiguredOwner() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);

        handler(dispatcher).messageNew(1, event(99L, "Action", "{\"type\":\"test_action\"}"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void routesOwnerPayloadToDispatcher() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        String payload = "{\"type\":\"test_action\",\"version\":1,\"data\":{\"id\":7}}";
        MessageNew event = event(42L, "Action", payload);

        handler(dispatcher).messageNew(1, event);

        verify(dispatcher).dispatch(payload, event);
    }

    @Test
    void ignoresMessagesWithoutSender() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);

        handler(dispatcher).messageNew(1, event(null, "Action", "{}"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void ignoresIncompleteEvents() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        GroupLongPoolApiHandler handler = handler(dispatcher);

        handler.messageNew(1, null);
        handler.messageNew(1, new MessageNew());
        handler.messageNew(1, new MessageNew().setObject(new MessageObject()));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void doesNotDispatchPlainTextOrBlankPayload() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        GroupLongPoolApiHandler handler = handler(dispatcher);

        handler.messageNew(1, event(42L, "Text", null));
        handler.messageNew(1, event(42L, "Text", ""));
        handler.messageNew(1, event(42L, "Text", "  "));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void routesSerializedKeyboardPayloadToRegisteredHandler() throws Exception {
        var objectMapper = new JacksonConfig().objectMapper();
        TestPayloadHandler actionHandler = mock(TestPayloadHandler.class);
        when(actionHandler.type()).thenReturn("test_action");
        when(actionHandler.version()).thenReturn(1);
        when(actionHandler.payloadClass()).thenReturn(TestPayload.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(actionHandler));
        VkMessageSenderService sender = new VkMessageSenderService(
                mock(VkApiClient.class), mock(GroupActor.class), objectMapper, "42"
        );
        var keyboard = sender.buildKeyboard(
                List.of(TEXT), List.of("Action"),
                List.of(new CallbackPayloadEnvelope("test_action", 1, new TestPayload(7))), true
        );
        String payload = ((KeyboardButtonActionText) keyboard.getButtons().getFirst().getFirst().getAction())
                .getPayload();
        MessageNew event = event(42L, "Action", payload);

        handler(dispatcher).messageNew(1, event);

        verify(actionHandler).handle(new TestPayload(7), event);
    }

    private GroupLongPoolApiHandler handler(CallbackPayloadDispatcher dispatcher) {
        return new GroupLongPoolApiHandler(mock(VkApiClient.class), mock(GroupActor.class), dispatcher, "42");
    }

    private MessageNew event(Long fromId, String text, String payload) {
        Message message = new Message().setFromId(fromId).setText(text).setPayload(payload);
        return new MessageNew().setObject(new MessageObject().setMessage(message));
    }

    private record TestPayload(long id) {
    }

    private interface TestPayloadHandler extends CallbackPayloadHandler<TestPayload> {
    }
}
