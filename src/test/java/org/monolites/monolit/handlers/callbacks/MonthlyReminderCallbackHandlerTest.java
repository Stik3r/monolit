package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.MonthlyReminderDto;
import org.monolites.monolit.models.enums.ReminderType;
import org.monolites.monolit.services.MonthlyReminderService;
import org.monolites.monolit.services.VkMessageSenderService;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyReminderCallbackHandlerTest {

    @Test
    void confirmsUpdatedReminder() {
        MonthlyReminderService reminderService = mock(MonthlyReminderService.class);
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        MonthlyReminderDto payload = new MonthlyReminderDto(LocalDate.of(2026, Month.JUNE, 1), ReminderType.METER_READING);
        when(reminderService.markReminderAsDone(payload)).thenReturn(true);
        MonthlyReminderCallbackHandler handler = new MonthlyReminderCallbackHandler(reminderService, messageSender);

        handler.handle(payload, mock(MessageNew.class));

        verify(messageSender).sendMessage("Напоминание отмечено как выполненное.");
    }

    @Test
    void reportsAlreadyDoneOrMissingReminder() {
        MonthlyReminderService reminderService = mock(MonthlyReminderService.class);
        VkMessageSenderService messageSender = mock(VkMessageSenderService.class);
        MonthlyReminderDto payload = new MonthlyReminderDto(LocalDate.of(2026, Month.JUNE, 1), ReminderType.UTILITY_PAYMENT);
        when(reminderService.markReminderAsDone(payload)).thenReturn(false);
        MonthlyReminderCallbackHandler handler = new MonthlyReminderCallbackHandler(reminderService, messageSender);

        handler.handle(payload, mock(MessageNew.class));

        verify(messageSender).sendMessage("Напоминание уже было отмечено или не найдено.");
    }

    @Test
    void exposesCallbackRouteContract() {
        MonthlyReminderCallbackHandler handler = new MonthlyReminderCallbackHandler(
                mock(MonthlyReminderService.class),
                mock(VkMessageSenderService.class)
        );

        assertThat(handler.type()).isEqualTo("monthly_reminder_done");
        assertThat(handler.version()).isEqualTo(1);
        assertThat(handler.payloadClass()).isEqualTo(MonthlyReminderDto.class);
    }
}
