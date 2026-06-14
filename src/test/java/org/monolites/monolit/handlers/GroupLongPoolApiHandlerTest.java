package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.MessageObject;
import com.vk.api.sdk.objects.messages.Message;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.handlers.callbacks.CallbackPayloadDispatcher;
import org.monolites.monolit.services.ReminderConversationService;

import static org.mockito.Mockito.*;

class GroupLongPoolApiHandlerTest {

    @Test
    void ignoresMessagesFromUsersOtherThanConfiguredOwner() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService);

        handler.messageNew(1, event(99L, "Новое напоминание", null));

        verifyNoInteractions(dispatcher, conversationService);
    }

    @Test
    void routesOwnerPayloadBeforePlainTextConversation() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService);
        MessageNew event = event(42L, "10 минут", "{\"type\":\"monthly_reminder_postpone\"}");

        handler.messageNew(1, event);

        verify(dispatcher).dispatch("{\"type\":\"monthly_reminder_postpone\"}", event);
        verifyNoInteractions(conversationService);
    }

    @Test
    void routesOwnerPlainTextToReminderConversation() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService);

        handler.messageNew(1, event(42L, "Новое напоминание", null));

        verify(conversationService).handle("Новое напоминание");
        verifyNoInteractions(dispatcher);
    }

    private GroupLongPoolApiHandler handler(
            CallbackPayloadDispatcher dispatcher,
            ReminderConversationService conversationService
    ) {
        return new GroupLongPoolApiHandler(
                mock(VkApiClient.class),
                mock(GroupActor.class),
                dispatcher,
                conversationService,
                "42"
        );
    }

    private MessageNew event(long fromId, String text, String payload) {
        Message message = new Message()
                .setFromId(fromId)
                .setText(text)
                .setPayload(payload);
        return new MessageNew().setObject(new MessageObject().setMessage(message));
    }
}
