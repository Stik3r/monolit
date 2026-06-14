package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.CustomReminderActionDto;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.services.CustomReminderService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomReminderCallbackHandler implements CallbackPayloadHandler<CustomReminderActionDto> {

    private final CustomReminderService customReminderService;

    @Override
    public String type() {
        return CallbackPayloadType.CUSTOM_REMINDER_ACTION.value();
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public Class<CustomReminderActionDto> payloadClass() {
        return CustomReminderActionDto.class;
    }

    @Override
    public void handle(CustomReminderActionDto payload, MessageNew event) {
        customReminderService.handle(payload);
    }
}
