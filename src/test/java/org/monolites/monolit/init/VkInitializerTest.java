package org.monolites.monolit.init;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.VkLongPollRunnerService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VkInitializerTest {

    @Test
    void startsLongPollRunnerOnStartup() {
        VkLongPollRunnerService runner = mock(VkLongPollRunnerService.class);

        new VkInitializer(runner).run();

        verify(runner).run();
    }
}
