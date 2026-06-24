package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.objects.callback.MessageNew;
import org.monolites.monolit.handlers.callbacks.CallbackPayloadDispatcher;
import org.monolites.monolit.services.BotMainMenuService;
import org.monolites.monolit.services.ReminderConversationService;
import org.monolites.monolit.services.ShoppingListConversationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GroupLongPoolApiHandler extends GroupLongPollApi {

    private final CallbackPayloadDispatcher callbackPayloadDispatcher;
    private final BotMainMenuService mainMenuService;
    private final ReminderConversationService reminderConversationService;
    private final ShoppingListConversationService shoppingListConversationService;
    private final long ownerId;

    public GroupLongPoolApiHandler(
            VkApiClient client,
            GroupActor actor,
            CallbackPayloadDispatcher callbackPayloadDispatcher,
            BotMainMenuService mainMenuService,
            ReminderConversationService reminderConversationService,
            ShoppingListConversationService shoppingListConversationService,
            @Value("${VK_MY_ID}") String ownerId
    ) {
        super(client, actor, 25);
        this.callbackPayloadDispatcher = callbackPayloadDispatcher;
        this.mainMenuService = mainMenuService;
        this.reminderConversationService = reminderConversationService;
        this.shoppingListConversationService = shoppingListConversationService;
        this.ownerId = Long.parseLong(ownerId.trim());
    }

    @Override
    public void messageNew(Integer groupId, MessageNew message) {
        if (message == null || message.getObject() == null || message.getObject().getMessage() == null) {
            return;
        }

        var vkMessage = message.getObject().getMessage();
        if (vkMessage.getFromId() == null || vkMessage.getFromId() != ownerId) {
            return;
        }
        String payload = vkMessage.getPayload();
        if (payload != null && !payload.isBlank()) {
            callbackPayloadDispatcher.dispatch(payload, message);
            return;
        }

        if (reminderConversationService.isActive()) {
            reminderConversationService.handle(vkMessage.getText());
            return;
        }
        if (shoppingListConversationService.isActive()) {
            shoppingListConversationService.handle(vkMessage.getText());
            return;
        }
        if (handleMenuNavigation(vkMessage.getText())) {
            return;
        }
        if (reminderConversationService.handle(vkMessage.getText())) {
            return;
        }
        shoppingListConversationService.handle(vkMessage.getText());
    }

    private boolean handleMenuNavigation(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        if (BotMainMenuService.REMINDERS.equalsIgnoreCase(text)) {
            mainMenuService.showReminderMenu();
            return true;
        }
        if (BotMainMenuService.SHOPPING.equalsIgnoreCase(text)) {
            mainMenuService.showShoppingMenu();
            return true;
        }
        if (BotMainMenuService.MAIN_MENU.equalsIgnoreCase(text)) {
            mainMenuService.show("Главное меню");
            return true;
        }
        return false;
    }
}
