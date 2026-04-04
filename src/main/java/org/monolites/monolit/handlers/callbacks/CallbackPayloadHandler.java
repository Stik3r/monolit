package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;

public interface CallbackPayloadHandler<T> {

    String type();

    int version();

    Class<T> payloadClass();

    void handle(T payload, MessageNew event);
}
