package org.monolites.monolit.init;

import org.monolites.monolit.services.VkLongPollRunnerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class VkInitializer implements CommandLineRunner {

    private final VkLongPollRunnerService vkLongPollRunnerService;

    public VkInitializer(VkLongPollRunnerService vkLongPollRunnerService) {
        this.vkLongPollRunnerService = vkLongPollRunnerService;
    }

    @Override
    public void run(String... args) {
        vkLongPollRunnerService.run();
    }
}
