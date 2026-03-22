package org.monolites.monolit.scheduler;

import org.monolites.monolit.service.AvailabilityCheckService;
import org.monolites.monolit.service.VKService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityCheckScheduler {

    private final AvailabilityCheckService availabilityCheckService;
    private final VKService vkService;
    private final String host;
    private final String portValue;

    public AvailabilityCheckScheduler(
            AvailabilityCheckService availabilityCheckService,
            @Value("${MONOLIT_TARGET_HOST}") String host,
            @Value("${MONOLIT_TARGET_PORT}") String portValue,
            @Value("${VK_GROUP_TOKEN}") String groupToken,
            @Value("${VK_GROUP_ID}") String groupId,
            @Value("${VK_MY_ID}") String userId) {
        this.availabilityCheckService = availabilityCheckService;
        this.vkService = new VKService(groupToken, Long.parseLong(groupId.trim()), Long.parseLong(userId.trim()));
        this.host = host.trim();
        this.portValue = portValue.trim();
    }

    @Scheduled(fixedDelayString = "${monolit.check.delay-ms}")
    public void availabilityCheck() {
        String result = availabilityCheckService.checkAvailability(host, portValue);
        if(result != null) {
            vkService.sendMessage(result);
        }
    }
}
