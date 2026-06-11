package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.dtos.MonthlyReminderPostponeDto;
import org.monolites.monolit.models.dtos.ReminderPostponeResult;
import org.monolites.monolit.models.enums.ReminderPostponeAction;
import org.monolites.monolit.models.enums.ReminderType;
import org.monolites.monolit.services.MonthlyReminderService;
import org.monolites.monolit.services.VkMessageSenderService;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MonthlyReminderPostponeCallbackHandlerTest {

    @Mock
    private MonthlyReminderService reminderService;
    @Mock
    private VkMessageSenderService messageSender;
    @Mock
    private MessageNew event;

    @Test
    void confirmsNewReminderTime() {
        MonthlyReminderPostponeDto payload = new MonthlyReminderPostponeDto(
                LocalDate.of(2026, Month.JUNE, 1),
                ReminderType.METER_READING,
                ReminderPostponeAction.TEN_MINUTES
        );
        when(reminderService.postponeReminder(payload)).thenReturn(new ReminderPostponeResult(
                true,
                ReminderPostponeAction.TEN_MINUTES,
                ZonedDateTime.parse("2026-06-11T13:10:00+03:00[Europe/Moscow]")
        ));
        MonthlyReminderPostponeCallbackHandler handler =
                new MonthlyReminderPostponeCallbackHandler(reminderService, messageSender);

        handler.handle(payload, event);

        verify(messageSender).sendMessage("Следующее напоминание: 11.06.2026 13:10.");
    }

    @Test
    void rejectsPostponementForMissingOrDoneReminder() {
        MonthlyReminderPostponeDto payload = new MonthlyReminderPostponeDto();
        when(reminderService.postponeReminder(payload)).thenReturn(ReminderPostponeResult.notUpdated());
        MonthlyReminderPostponeCallbackHandler handler =
                new MonthlyReminderPostponeCallbackHandler(reminderService, messageSender);

        handler.handle(payload, event);

        verify(messageSender).sendMessage("Напоминание уже выполнено или не найдено.");
    }

    @Test
    void confirmsSuppressionForToday() {
        MonthlyReminderPostponeDto payload = new MonthlyReminderPostponeDto(
                LocalDate.of(2026, Month.JUNE, 1),
                ReminderType.UTILITY_PAYMENT,
                ReminderPostponeAction.TODAY
        );
        when(reminderService.postponeReminder(payload)).thenReturn(new ReminderPostponeResult(
                true,
                ReminderPostponeAction.TODAY,
                ZonedDateTime.parse("2026-06-12T00:00:00+03:00[Europe/Moscow]")
        ));
        MonthlyReminderPostponeCallbackHandler handler =
                new MonthlyReminderPostponeCallbackHandler(reminderService, messageSender);

        handler.handle(payload, event);

        verify(messageSender).sendMessage("Сегодня больше не буду напоминать.");
    }

    @Test
    void exposesCallbackRouteContract() {
        MonthlyReminderPostponeCallbackHandler handler =
                new MonthlyReminderPostponeCallbackHandler(reminderService, messageSender);

        assertThat(handler.type()).isEqualTo("monthly_reminder_postpone");
        assertThat(handler.version()).isEqualTo(1);
        assertThat(handler.payloadClass()).isEqualTo(MonthlyReminderPostponeDto.class);
    }
}
