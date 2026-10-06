package org.monolites.monolit.schedulers;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.services.CherinfoNewsService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class CherinfoNewsSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulerConfiguration.class)
            .withBean(CherinfoNewsService.class, () -> mock(CherinfoNewsService.class))
            .withPropertyValues(
                    "monolit.news.cherinfo.cron=0 0 0 1 1 *",
                    "monolit.news.zone=Europe/Moscow"
            );

    @Test
    void disablesStartupAndScheduledPublicationWhenFlagIsMissing() {
        assertPublicationDisabled(contextRunner);
    }

    @Test
    void disablesStartupAndScheduledPublicationWhenFlagIsFalse() {
        assertPublicationDisabled(contextRunner.withPropertyValues("monolit.news.cherinfo.enabled=false"));
    }

    @Test
    void enablesStartupAndScheduledPublicationWhenExplicitlyRequested() {
        contextRunner.withPropertyValues("monolit.news.cherinfo.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(CherinfoNewsScheduler.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(SchedulerConfiguration.class), new String[0],
                    context.getSourceApplicationContext(), Duration.ZERO
            ));
            verify(context.getBean(CherinfoNewsService.class)).publishLatestNews();
        });
    }

    @Test
    void delegatesStartupAndHourlyPublication() {
        CherinfoNewsService service = mock(CherinfoNewsService.class);
        CherinfoNewsScheduler scheduler = new CherinfoNewsScheduler(service);

        scheduler.publishNewsOnStartup();
        scheduler.publishNewsHourly();

        verify(service, times(2)).publishLatestNews();
    }

    private void assertPublicationDisabled(ApplicationContextRunner runner) {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(CherinfoNewsScheduler.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).isEmpty();
            context.publishEvent(new ApplicationReadyEvent(
                    new SpringApplication(SchedulerConfiguration.class), new String[0],
                    context.getSourceApplicationContext(), Duration.ZERO
            ));
            verifyNoInteractions(context.getBean(CherinfoNewsService.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @Import(CherinfoNewsScheduler.class)
    static class SchedulerConfiguration {
    }
}
