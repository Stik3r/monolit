package org.monolites.monolit.init;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.MonthlyReminderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MonthlyReminderInitializer implements CommandLineRunner {

    private final MonthlyReminderService monthlyReminderService;

    @Override
    public void run(String... args) {
        monthlyReminderService.initializeCurrentMonthRecords();
    }
}
