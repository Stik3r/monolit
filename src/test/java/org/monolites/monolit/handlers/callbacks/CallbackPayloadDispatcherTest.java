package org.monolites.monolit.handlers.callbacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.objects.callback.MessageNew;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.ShoppingListActionDto;
import org.monolites.monolit.models.enums.ShoppingListAction;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CallbackPayloadDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dispatchesKnownPayloadToMatchingHandler() {
        TestShoppingListHandler handler = mock(TestShoppingListHandler.class);
        MessageNew event = mock(MessageNew.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("shopping_list_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(ShoppingListActionDto.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(handler));

        dispatcher.dispatch("""
                {"type":"shopping_list_action","version":1,"data":{"action":"LIST","itemId":5,"page":2}}
                """, event);

        verify(handler).handle(new ShoppingListActionDto(ShoppingListAction.LIST, 5L, 2), event);
    }

    @Test
    void ignoresInvalidJsonAndUnknownRoutes() {
        TestShoppingListHandler handler = mock(TestShoppingListHandler.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("shopping_list_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(ShoppingListActionDto.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(handler));

        dispatcher.dispatch("not-json", mock(MessageNew.class));
        dispatcher.dispatch("""
                {"type":"missing","version":1,"data":{"action":"LIST","itemId":5,"page":2}}
                """, mock(MessageNew.class));

        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresPayloadConversionErrors() {
        TestShoppingListHandler handler = mock(TestShoppingListHandler.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("shopping_list_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(ShoppingListActionDto.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(handler));

        dispatcher.dispatch("""
                {"type":"shopping_list_action","version":1,"data":{"action":"LIST","itemId":"bad","page":2}}
                """, mock(MessageNew.class));

        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateHandlerRoutes() {
        TestShoppingListHandler first = mock(TestShoppingListHandler.class);
        TestShoppingListHandler second = mock(TestShoppingListHandler.class);
        org.mockito.Mockito.when(first.type()).thenReturn("shopping_list_action");
        org.mockito.Mockito.when(first.version()).thenReturn(1);
        org.mockito.Mockito.when(second.type()).thenReturn("shopping_list_action");
        org.mockito.Mockito.when(second.version()).thenReturn(1);
        List<CallbackPayloadHandler<?>> handlers = List.of(first, second);

        assertThatThrownBy(() -> dispatcherWith(handlers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Дублирование обработчиков callback");
    }

    private CallbackPayloadDispatcher dispatcherWith(List<CallbackPayloadHandler<?>> handlers) {
        return new CallbackPayloadDispatcher(objectMapper, handlers);
    }

    private interface TestShoppingListHandler extends CallbackPayloadHandler<ShoppingListActionDto> {
    }
}
