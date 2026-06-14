package org.monolites.monolit.configs;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {

    @Test
    void createsClockForConfiguredReminderZone() {
        Clock clock = new TimeConfig().reminderClock("Europe/Moscow");

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
    }
}
