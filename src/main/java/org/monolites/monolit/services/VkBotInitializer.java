package org.monolites.monolit.services;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class VkBotInitializer implements CommandLineRunner {

    private final VKService vkService;

    public VkBotInitializer(VKService vkService) {
        this.vkService = vkService;
    }

    @Override
    public void run(String... args) {
        vkService.run();
    }
}
