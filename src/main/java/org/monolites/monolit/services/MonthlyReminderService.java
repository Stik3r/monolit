package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MonthlyReminderService {

    private static final String METER_READING_MESSAGE =
            "Напоминание: передайте показания счетчиков. Если вы уже это сделали, сообщение можно проигнорировать.";
    private static final String UTILITY_PAYMENT_MESSAGE =
            "Напоминание: оплатите коммунальные услуги. Если оплата уже проведена, сообщение можно проигнорировать.";

    private final VKService vkService;

    public void sendMeterReadingReminder() {
        vkService.sendMessage(METER_READING_MESSAGE);
    }

    public void sendUtilityPaymentReminder() {
        vkService.sendMessage(UTILITY_PAYMENT_MESSAGE);
    }
}
