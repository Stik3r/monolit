package org.monolites.monolit.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class AvailabilityCheckServiceTest {

    @Test
    void reportsUnavailableHostWhenPingFails() {
        AvailabilityCheckService service = spy(new AvailabilityCheckService());
        doReturn(false).when(service).pingHost("example.org");

        assertThat(service.checkAvailability("example.org")).isEqualTo("Сервер недоступен");
    }

    @Test
    void returnsNullWhenHostIsAvailable() {
        AvailabilityCheckService service = spy(new AvailabilityCheckService());
        doReturn(true).when(service).pingHost("example.org");

        assertThat(service.checkAvailability("example.org")).isNull();
    }
}
