package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.callback.MessageNew;
import com.vk.api.sdk.objects.callback.MessageObject;
import com.vk.api.sdk.objects.messages.Message;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.handlers.callbacks.CallbackPayloadDispatcher;
import org.monolites.monolit.services.ReminderConversationService;
import org.monolites.monolit.services.ShoppingListConversationService;

import static org.mockito.Mockito.*;

class GroupLongPoolApiHandlerTest {

    @Test
    void ignoresMessagesFromUsersOtherThanConfiguredOwner() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        ShoppingListConversationService shoppingListConversationService = mock(ShoppingListConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService, shoppingListConversationService);

        handler.messageNew(1, event(99L, "Новое напоминание", null));

        verifyNoInteractions(dispatcher, conversationService);
        verifyNoInteractions(shoppingListConversationService);
    }

    @Test
    void routesOwnerPayloadBeforePlainTextConversation() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        ShoppingListConversationService shoppingListConversationService = mock(ShoppingListConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService, shoppingListConversationService);
        MessageNew event = event(42L, "10 минут", "{\"type\":\"monthly_reminder_postpone\"}");

        handler.messageNew(1, event);

        verify(dispatcher).dispatch("{\"type\":\"monthly_reminder_postpone\"}", event);
        verifyNoInteractions(conversationService);
        verifyNoInteractions(shoppingListConversationService);
    }

    @Test
    void routesOwnerPlainTextToReminderConversation() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        ShoppingListConversationService shoppingListConversationService = mock(ShoppingListConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService, shoppingListConversationService);
        when(conversationService.handle("Новое напоминание")).thenReturn(true);

        handler.messageNew(1, event(42L, "Новое напоминание", null));

        verify(conversationService).handle("Новое напоминание");
        verify(shoppingListConversationService, never()).handle(anyString());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void routesActiveReminderConversationBeforeShoppingConversation() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        ShoppingListConversationService shoppingListConversationService = mock(ShoppingListConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService, shoppingListConversationService);
        when(conversationService.isActive()).thenReturn(true);

        handler.messageNew(1, event(42L, "Список покупок", null));

        verify(conversationService).handle("Список покупок");
        verifyNoInteractions(dispatcher);
        verify(shoppingListConversationService, never()).handle(anyString());
    }

    @Test
    void routesActiveShoppingConversationBeforeMainMenuCommands() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        ShoppingListConversationService shoppingListConversationService = mock(ShoppingListConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService, shoppingListConversationService);
        when(shoppingListConversationService.isActive()).thenReturn(true);

        handler.messageNew(1, event(42L, "Новое напоминание", null));

        verify(shoppingListConversationService).handle("Новое напоминание");
        verify(conversationService, never()).handle(anyString());
        verifyNoInteractions(dispatcher);
    }

    @Test
    void routesUnrecognizedTextToShoppingConversationAfterReminderConversation() {
        CallbackPayloadDispatcher dispatcher = mock(CallbackPayloadDispatcher.class);
        ReminderConversationService conversationService = mock(ReminderConversationService.class);
        ShoppingListConversationService shoppingListConversationService = mock(ShoppingListConversationService.class);
        GroupLongPoolApiHandler handler = handler(dispatcher, conversationService, shoppingListConversationService);
        when(conversationService.handle("Список покупок")).thenReturn(false);

        handler.messageNew(1, event(42L, "Список покупок", null));

        verify(conversationService).handle("Список покупок");
        verify(shoppingListConversationService).handle("Список покупок");
        verifyNoInteractions(dispatcher);
    }

    private GroupLongPoolApiHandler handler(
            CallbackPayloadDispatcher dispatcher,
            ReminderConversationService conversationService,
            ShoppingListConversationService shoppingListConversationService
    ) {
        return new GroupLongPoolApiHandler(
                mock(VkApiClient.class),
                mock(GroupActor.class),
                dispatcher,
                conversationService,
                shoppingListConversationService,
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
