package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.ShoppingListActionDto;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.services.ShoppingListConversationService;
import org.monolites.monolit.services.ShoppingListService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShoppingListCallbackHandler implements CallbackPayloadHandler<ShoppingListActionDto> {

    private final ShoppingListService shoppingListService;
    private final ShoppingListConversationService conversationService;

    @Override
    public String type() {
        return CallbackPayloadType.SHOPPING_LIST_ACTION.value();
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public Class<ShoppingListActionDto> payloadClass() {
        return ShoppingListActionDto.class;
    }

    @Override
    public void handle(ShoppingListActionDto payload, MessageNew event) {
        if (payload != null && payload.action() == org.monolites.monolit.models.enums.ShoppingListAction.ADD) {
            conversationService.startAdding();
            return;
        }
        shoppingListService.handle(payload);
    }
}
