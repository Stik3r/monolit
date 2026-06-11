package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.MonthlyReminderPostponeDto;
import org.monolites.monolit.models.dtos.ReminderPostponeResult;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.models.enums.ReminderPostponeAction;
import org.monolites.monolit.services.MonthlyReminderService;
import org.monolites.monolit.services.VkMessageSenderService;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class MonthlyReminderPostponeCallbackHandler implements CallbackPayloadHandler<MonthlyReminderPostponeDto> {

    private static final int VERSION = 1;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final MonthlyReminderService monthlyReminderService;
    private final VkMessageSenderService vkMessageSenderService;

    @Override
    public String type() {
        return CallbackPayloadType.MONTHLY_REMINDER_POSTPONE.value();
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public Class<MonthlyReminderPostponeDto> payloadClass() {
        return MonthlyReminderPostponeDto.class;
    }

    @Override
    public void handle(MonthlyReminderPostponeDto payload, MessageNew event) {
        ReminderPostponeResult result = monthlyReminderService.postponeReminder(payload);
        if (!result.updated()) {
            vkMessageSenderService.sendMessage("Напоминание уже выполнено или не найдено.");
        } else if (result.action() == ReminderPostponeAction.TODAY) {
            vkMessageSenderService.sendMessage("Сегодня больше не буду напоминать.");
        } else {
            vkMessageSenderService.sendMessage(
                    "Следующее напоминание: " + TIME_FORMATTER.format(result.postponedUntil()) + "."
            );
        }
    }
}
