package org.monolites.monolit.handlers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.objects.callback.MessageNew;

public class GroupLongPoolApiHandler extends GroupLongPollApi {
    protected GroupLongPoolApiHandler(VkApiClient client, GroupActor actor, int waitTime) {
        super(client, actor, waitTime);
    }

    @Override
    public void messageNew(Integer groupId, MessageNew message){

    }
}
