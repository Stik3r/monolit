package org.monolites.monolit.schedulers;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.CherinfoNewsService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CherinfoNewsSchedulerTest {

    @Test
    void delegatesStartupAndHourlyPublication() {
        CherinfoNewsService service = mock(CherinfoNewsService.class);
        CherinfoNewsScheduler scheduler = new CherinfoNewsScheduler(service);

        scheduler.publishNewsOnStartup();
        scheduler.publishNewsHourly();

        verify(service, times(2)).publishLatestNews();
    }
}
