package org.monolites.monolit.handlers.callbacks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.objects.callback.MessageNew;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.configs.JacksonConfig;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CallbackPayloadDispatcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void startsSpringContextWithoutConcreteActionHandlers() {
        new ApplicationContextRunner()
                .withUserConfiguration(JacksonConfig.class, CallbackPayloadDispatcher.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CallbackPayloadDispatcher.class);
                    assertThat(context.getBeansOfType(CallbackPayloadHandler.class)).isEmpty();
                    context.getBean(CallbackPayloadDispatcher.class).dispatch("""
                            {"type":"unknown","version":1,"data":{}}
                            """, mock(MessageNew.class));
                });
    }

    @Test
    void discoversHandlerBeanAndDispatchesItsTypedData() {
        TestPayloadHandler handler = mock(TestPayloadHandler.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("test_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(TestPayload.class);
        MessageNew event = mock(MessageNew.class);

        new ApplicationContextRunner()
                .withUserConfiguration(JacksonConfig.class, CallbackPayloadDispatcher.class)
                .withBean(TestPayloadHandler.class, () -> handler)
                .run(context -> {
                    assertThat(context).hasSingleBean(CallbackPayloadDispatcher.class);
                    context.getBean(CallbackPayloadDispatcher.class).dispatch("""
                            {"type":"test_action","version":1,"data":{"id":5,"page":2}}
                            """, event);
                    verify(handler).handle(new TestPayload(5L, 2), event);
                });
    }

    @Test
    void dispatchesKnownPayloadToMatchingHandler() {
        TestPayloadHandler handler = mock(TestPayloadHandler.class);
        MessageNew event = mock(MessageNew.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("test_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(TestPayload.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(handler));

        dispatcher.dispatch("""
                {"type":"test_action","version":1,"data":{"id":5,"page":2}}
                """, event);

        verify(handler).handle(new TestPayload(5L, 2), event);
    }

    @Test
    void ignoresInvalidJsonAndUnknownRoutes() {
        TestPayloadHandler handler = mock(TestPayloadHandler.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("test_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(TestPayload.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(handler));

        dispatcher.dispatch("not-json", mock(MessageNew.class));
        dispatcher.dispatch("""
                {"type":"missing","version":1,"data":{"id":5,"page":2}}
                """, mock(MessageNew.class));

        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresPayloadConversionErrors() {
        TestPayloadHandler handler = mock(TestPayloadHandler.class);
        org.mockito.Mockito.when(handler.type()).thenReturn("test_action");
        org.mockito.Mockito.when(handler.version()).thenReturn(1);
        org.mockito.Mockito.when(handler.payloadClass()).thenReturn(TestPayload.class);
        CallbackPayloadDispatcher dispatcher = new CallbackPayloadDispatcher(objectMapper, List.of(handler));

        dispatcher.dispatch("""
                {"type":"test_action","version":1,"data":{"id":"bad","page":2}}
                """, mock(MessageNew.class));

        verify(handler, never()).handle(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateHandlerRoutes() {
        TestPayloadHandler first = mock(TestPayloadHandler.class);
        TestPayloadHandler second = mock(TestPayloadHandler.class);
        org.mockito.Mockito.when(first.type()).thenReturn("test_action");
        org.mockito.Mockito.when(first.version()).thenReturn(1);
        org.mockito.Mockito.when(second.type()).thenReturn("test_action");
        org.mockito.Mockito.when(second.version()).thenReturn(1);
        List<CallbackPayloadHandler<?>> handlers = List.of(first, second);

        assertThatThrownBy(() -> dispatcherWith(handlers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Дублирование обработчиков callback");
    }

    private CallbackPayloadDispatcher dispatcherWith(List<CallbackPayloadHandler<?>> handlers) {
        return new CallbackPayloadDispatcher(objectMapper, handlers);
    }

    private record TestPayload(long id, int page) {
    }

    private interface TestPayloadHandler extends CallbackPayloadHandler<TestPayload> {
    }
}
