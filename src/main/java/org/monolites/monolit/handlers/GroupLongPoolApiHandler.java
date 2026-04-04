package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.objects.callback.MessageNew;
import org.monolites.monolit.handlers.callbacks.CallbackPayloadDispatcher;
import org.springframework.stereotype.Component;

@Component
public class GroupLongPoolApiHandler extends GroupLongPollApi {

    private final CallbackPayloadDispatcher callbackPayloadDispatcher;

    public GroupLongPoolApiHandler(
            VkApiClient client,
            GroupActor actor,
            CallbackPayloadDispatcher callbackPayloadDispatcher
    ) {
        super(client, actor, 25);
        this.callbackPayloadDispatcher = callbackPayloadDispatcher;
    }

    @Override
    public void messageNew(Integer groupId, MessageNew message) {
        if (message == null || message.getObject() == null || message.getObject().getMessage() == null) {
            return;
        }

        String payload = message.getObject().getMessage().getPayload();
        if (payload == null || payload.isBlank()) {
            return;
        }

        callbackPayloadDispatcher.dispatch(payload, message);
    }
}
