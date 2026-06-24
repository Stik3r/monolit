package org.monolites.monolit.configs;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VkApiConfigTest {

    @Test
    void createsVkApiBeansFromConfiguredValues() {
        VkApiConfig config = new VkApiConfig();
        TransportClient transportClient = mock(TransportClient.class);

        VkApiClient vkApiClient = config.vkApiClient(transportClient);
        GroupActor groupActor = config.groupActor(" token ", " 42 ");

        assertThat(config.transportClient()).isInstanceOf(TransportClient.class);
        assertThat(vkApiClient).isNotNull();
        assertThat(groupActor.getGroupId()).isEqualTo(42L);
        assertThat(groupActor.getAccessToken()).isEqualTo("token");
    }
}
