package org.monolites.monolit.handlers.callbacks;

import com.vk.api.sdk.objects.callback.MessageNew;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.CustomReminderActionDto;
import org.monolites.monolit.models.enums.CustomReminderAction;
import org.monolites.monolit.services.CustomReminderService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CustomReminderCallbackHandlerTest {

    @Test
    void delegatesPayloadToCustomReminderService() {
        CustomReminderService service = mock(CustomReminderService.class);
        CustomReminderCallbackHandler handler = new CustomReminderCallbackHandler(service);
        CustomReminderActionDto payload = new CustomReminderActionDto(CustomReminderAction.DONE, 7L, 1);

        handler.handle(payload, mock(MessageNew.class));

        verify(service).handle(payload);
    }

    @Test
    void exposesCallbackRouteContract() {
        CustomReminderCallbackHandler handler = new CustomReminderCallbackHandler(mock(CustomReminderService.class));

        assertThat(handler.type()).isEqualTo("custom_reminder_action");
        assertThat(handler.version()).isEqualTo(1);
        assertThat(handler.payloadClass()).isEqualTo(CustomReminderActionDto.class);
    }
}
