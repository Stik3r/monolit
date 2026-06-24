package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.ShoppingListActionDto;
import org.monolites.monolit.models.enums.ShoppingListAction;
import org.monolites.monolit.services.ShoppingListConversationService;
import org.monolites.monolit.services.ShoppingListService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ShoppingListCallbackHandlerTest {

    @Test
    void startsConversationForAddAction() {
        ShoppingListService shoppingListService = mock(ShoppingListService.class);
        ShoppingListConversationService conversationService = mock(ShoppingListConversationService.class);
        ShoppingListCallbackHandler handler = new ShoppingListCallbackHandler(shoppingListService, conversationService);
        ShoppingListActionDto payload = new ShoppingListActionDto(ShoppingListAction.ADD, null, 0);

        handler.handle(payload, mock(MessageNew.class));

        verify(conversationService).startAdding();
        verify(shoppingListService, never()).handle(payload);
    }

    @Test
    void delegatesOtherActionsToShoppingListService() {
        ShoppingListService shoppingListService = mock(ShoppingListService.class);
        ShoppingListConversationService conversationService = mock(ShoppingListConversationService.class);
        ShoppingListCallbackHandler handler = new ShoppingListCallbackHandler(shoppingListService, conversationService);
        ShoppingListActionDto payload = new ShoppingListActionDto(ShoppingListAction.LIST, null, 0);

        handler.handle(payload, mock(MessageNew.class));

        verify(shoppingListService).handle(payload);
        verify(conversationService, never()).startAdding();
    }

    @Test
    void exposesCallbackRouteContract() {
        ShoppingListCallbackHandler handler = new ShoppingListCallbackHandler(
                mock(ShoppingListService.class),
                mock(ShoppingListConversationService.class)
        );

        assertThat(handler.type()).isEqualTo("shopping_list_action");
        assertThat(handler.version()).isEqualTo(1);
        assertThat(handler.payloadClass()).isEqualTo(ShoppingListActionDto.class);
    }
}
