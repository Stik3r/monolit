package org.monolites.monolit.schedulers;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.AvailabilityCheckService;
import org.monolites.monolit.services.VkMessageSenderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilityCheckScheduler {

    private final AvailabilityCheckService availabilityCheckService;
    private final VkMessageSenderService vkMessageSenderService;
    @Value("${MONOLIT_TARGET_HOST}")
    private String host;

    @Scheduled(fixedDelayString = "${monolit.check.delay-ms}")
    public void availabilityCheck() {
        String result = availabilityCheckService.checkAvailability(host.trim());
        if(result != null) {
            vkMessageSenderService.sendMessage(result);
        }
    }
}
