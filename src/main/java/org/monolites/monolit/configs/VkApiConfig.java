package org.monolites.monolit.configs;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VkApiConfig {

    @Bean
    public TransportClient transportClient() {
        return new HttpTransportClient();
    }

    @Bean
    public VkApiClient vkApiClient(TransportClient transportClient) {
        return new VkApiClient(transportClient);
    }

    @Bean
    public GroupActor groupActor(
            @Value("${VK_GROUP_TOKEN}") String accessToken,
            @Value("${VK_GROUP_ID}") String groupId
    ) {
        return new GroupActor(Long.parseLong(groupId.trim()), accessToken.trim());
    }
}
