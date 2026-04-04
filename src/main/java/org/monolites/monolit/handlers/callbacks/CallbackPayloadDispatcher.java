package org.monolites.monolit.handlers.callbacks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CallbackPayloadDispatcher {

    private final ObjectMapper objectMapper;
    private final Map<CallbackRouteKey, CallbackPayloadHandler<?>> handlers;

    public CallbackPayloadDispatcher(ObjectMapper objectMapper, List<CallbackPayloadHandler<?>> handlers) {
        this.objectMapper = objectMapper;
        this.handlers = buildHandlerMap(handlers);
    }

    public void dispatch(String rawPayload, MessageNew event) {
        CallbackPayloadEnvelope envelope = parseEnvelope(rawPayload);
        if (envelope == null) {
            return;
        }

        CallbackPayloadHandler<?> handler = handlers.get(new CallbackRouteKey(envelope.type(), envelope.version()));
        if (handler == null) {
            log.warn("Неизвестный тип обработчика callback type={}, version={}", envelope.type(), envelope.version());
            return;
        }

        dispatchToHandler(handler, envelope, event);
    }

    private CallbackPayloadEnvelope parseEnvelope(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, CallbackPayloadEnvelope.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse callback payload: {}", e.getMessage());
            return null;
        }
    }

    private Map<CallbackRouteKey, CallbackPayloadHandler<?>> buildHandlerMap(List<CallbackPayloadHandler<?>> handlers) {
        Map<CallbackRouteKey, CallbackPayloadHandler<?>> handlerMap = new HashMap<>();
        for (CallbackPayloadHandler<?> handler : handlers) {
            CallbackRouteKey key = new CallbackRouteKey(handler.type(), handler.version());
            CallbackPayloadHandler<?> previous = handlerMap.put(key, handler);
            if (previous != null) {
                throw new IllegalStateException("Дублирование обработчиков callback =" + key.type + ", version=" + key.version);
            }
        }
        return Map.copyOf(handlerMap);
    }

    @SuppressWarnings("unchecked")
    private <T> void dispatchToHandler(CallbackPayloadHandler<?> rawHandler, CallbackPayloadEnvelope envelope, MessageNew event) {
        try {
            CallbackPayloadHandler<T> typedHandler = (CallbackPayloadHandler<T>) rawHandler;
            T payload = objectMapper.convertValue(envelope.data(), typedHandler.payloadClass());
            typedHandler.handle(payload, event);
        } catch (IllegalArgumentException e) {
            log.warn("Ошибка конвертации payload type={}, version={}: {}", envelope.type(), envelope.version(), e.getMessage());
        } catch (Exception e) {
            log.error("Ошибка обработчика type={}, version={}", envelope.type(), envelope.version(), e);
        }
    }

    private record CallbackRouteKey(String type, int version) {
    }
}
