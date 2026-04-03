package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroupLongPoolApiHandler extends GroupLongPollApi {
    public GroupLongPoolApiHandler(VkApiClient client, GroupActor actor, int waitTime) {
        super(client, actor, waitTime);
    }

    @Override
    public void messageNew(Integer groupId, MessageNew message){
        log.info("Получено сообщение от {}: {}", message.getObject().getMessage().getFromId(),
                message.getObject().getMessage().getText());
    }
}
