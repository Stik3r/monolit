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

import java.time.ZonedDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                java.time.LocalDate.of(2026, 6, 1),
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
}
