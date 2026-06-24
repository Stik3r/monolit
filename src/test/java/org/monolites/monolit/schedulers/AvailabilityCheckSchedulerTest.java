package org.monolites.monolit.schedulers;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.AvailabilityCheckService;
import org.monolites.monolit.services.VkMessageSenderService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvailabilityCheckSchedulerTest {

    @Test
    void sendsMessageWhenTargetHostIsUnavailable() {
        AvailabilityCheckService availabilityCheckService = mock(AvailabilityCheckService.class);
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        AvailabilityCheckScheduler scheduler = new AvailabilityCheckScheduler(availabilityCheckService, messageSender);
        ReflectionTestUtils.setField(scheduler, "host", " example.org ");
        when(availabilityCheckService.checkAvailability("example.org")).thenReturn("Сервер недоступен");

        scheduler.availabilityCheck();

        verify(messageSender).sendMessage("Сервер недоступен");
    }

    @Test
    void skipsMessageWhenTargetHostIsAvailable() {
        AvailabilityCheckService availabilityCheckService = mock(AvailabilityCheckService.class);
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        AvailabilityCheckScheduler scheduler = new AvailabilityCheckScheduler(availabilityCheckService, messageSender);
        ReflectionTestUtils.setField(scheduler, "host", " example.org ");
        when(availabilityCheckService.checkAvailability("example.org")).thenReturn(null);

        scheduler.availabilityCheck();

        verify(messageSender, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());
    }
}
