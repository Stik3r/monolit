package org.monolites.monolit.schedulers;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.services.CherinfoNewsService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CherinfoNewsScheduler {

    private final CherinfoNewsService cherinfoNewsService;

    @EventListener(ApplicationReadyEvent.class)
    public void publishNewsOnStartup() {
        cherinfoNewsService.publishLatestNews();
    }

    @Scheduled(cron = "${monolit.news.cherinfo.cron}", zone = "${monolit.reminders.zone}")
    public void publishNewsHourly() {
        cherinfoNewsService.publishLatestNews();
    }
}
