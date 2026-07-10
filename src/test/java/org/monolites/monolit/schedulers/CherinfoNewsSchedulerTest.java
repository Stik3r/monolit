package org.monolites.monolit.schedulers;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.NewsService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CherinfoNewsSchedulerTest {

    @Test
    void delegatesStartupAndHourlyPublication() {
        NewsService service = mock(NewsService.class);
        NewsScheduler scheduler = new NewsScheduler(service);

        scheduler.publishNewsOnStartup();
        scheduler.publishNewsHourly();

        verify(service, times(2)).publishLatestNews();
    }
}
