package org.monolites.monolit.schedulers;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.AvailabilityCheckService;
import org.monolites.monolit.services.VKService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilityCheckScheduler {

    private final AvailabilityCheckService availabilityCheckService;
    private final VKService vkService;
    @Value("${MONOLIT_TARGET_HOST}")
    private String host;
    @Value("${MONOLIT_TARGET_PORT}")
    private String portValue;

    @Scheduled(fixedDelayString = "${monolit.check.delay-ms}")
    public void availabilityCheck() {
        String result = availabilityCheckService.checkAvailability(host.trim(), portValue.trim());
        if(result != null) {
            vkService.sendMessage(result);
        }
    }
}
