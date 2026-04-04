package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.MonthlyReminderDto;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.services.MonthlyReminderService;
import org.monolites.monolit.services.VkMessageSenderService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MonthlyReminderCallbackHandler implements CallbackPayloadHandler<MonthlyReminderDto> {

    private static final int VERSION = 1;

    private final MonthlyReminderService monthlyReminderService;
    private final VkMessageSenderService vkMessageSenderService;

    @Override
    public String type() {
        return CallbackPayloadType.MONTHLY_REMINDER_DONE.value();
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public Class<MonthlyReminderDto> payloadClass() {
        return MonthlyReminderDto.class;
    }

    @Override
    public void handle(MonthlyReminderDto payload, MessageNew event) {
        boolean updated = monthlyReminderService.markReminderAsDone(payload);
        if (updated) {
            vkMessageSenderService.sendMessage("Напоминание отмечено как выполненное.");
        } else {
            vkMessageSenderService.sendMessage("Напоминание уже было отмечено или не найдено.");
        }
    }
}