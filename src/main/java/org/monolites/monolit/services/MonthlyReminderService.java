package org.monolites.monolit.services;

import com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.MonthlyReminderDto;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.monolites.monolit.models.entities.MonthlyReminder;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.models.enums.ReminderType;
import org.monolites.monolit.repositories.MonthlyReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MonthlyReminderService {

    private static final int MONTHLY_REMINDER_PAYLOAD_VERSION = 1;

    private static final String METER_READING_MESSAGE =
            "Напоминание: передайте показания счетчиков. Если вы уже это сделали, сообщение можно проигнорировать.";
    private static final String UTILITY_PAYMENT_MESSAGE =
            "Напоминание: оплатите коммунальные услуги. Если оплата уже проведена, сообщение можно проигнорировать.";

    private final VkMessageSenderService vkMessageSenderService;
    private final MonthlyReminderRepository monthlyReminderRepository;

    public void sendMeterReadingReminder() {
        LocalDate now = LocalDate.now()
                .with(TemporalAdjusters.firstDayOfMonth());
        MonthlyReminder monthlyReminder = monthlyReminderRepository.findByReminderTypeAndDate(ReminderType.METER_READING, now);

        if (monthlyReminder != null && !monthlyReminder.isDone()) {
            vkMessageSenderService.sendMessage(
                    METER_READING_MESSAGE,
                    List.of(KeyboardButtonActionTextType.TEXT),
                    List.of("Передал"),
                    List.of(new CallbackPayloadEnvelope(
                            CallbackPayloadType.MONTHLY_REMINDER_DONE.value(),
                            MONTHLY_REMINDER_PAYLOAD_VERSION,
                            new MonthlyReminderDto(now, ReminderType.METER_READING)
                    )),
                    true
            );
        }
    }

    public void sendUtilityPaymentReminder() {
        LocalDate now = LocalDate.now()
                .with(TemporalAdjusters.firstDayOfMonth());
        MonthlyReminder monthlyReminder = monthlyReminderRepository.findByReminderTypeAndDate(ReminderType.UTILITY_PAYMENT, now);

        if (monthlyReminder != null && !monthlyReminder.isDone()) {
            vkMessageSenderService.sendMessage(
                    UTILITY_PAYMENT_MESSAGE,
                    List.of(KeyboardButtonActionTextType.TEXT),
                    List.of("Оплатил"),
                    List.of(new CallbackPayloadEnvelope(
                            CallbackPayloadType.MONTHLY_REMINDER_DONE.value(),
                            MONTHLY_REMINDER_PAYLOAD_VERSION,
                            new MonthlyReminderDto(now, ReminderType.UTILITY_PAYMENT)
                    )),
                    true
            );
        }
    }

    @Transactional
    public void createMeterReadingRecord() {
        LocalDate now = LocalDate.now()
                .with(TemporalAdjusters.firstDayOfMonth());
        MonthlyReminder monthlyReminder = new MonthlyReminder();
        monthlyReminder.setReminderType(ReminderType.METER_READING);
        monthlyReminder.setDate(now);
        monthlyReminder.setDone(false);
        monthlyReminderRepository.save(monthlyReminder);
    }

    @Transactional
    public void createUtilityPaymentRecord() {
        LocalDate now = LocalDate.now()
                .with(TemporalAdjusters.firstDayOfMonth());
        MonthlyReminder monthlyReminder = new MonthlyReminder();
        monthlyReminder.setReminderType(ReminderType.UTILITY_PAYMENT);
        monthlyReminder.setDate(now);
        monthlyReminder.setDone(false);
        monthlyReminderRepository.save(monthlyReminder);
    }

    @Transactional
    public boolean markReminderAsDone(MonthlyReminderDto dto) {
        if (dto == null || dto.getDate() == null || dto.getReminderType() == null) {
            return false;
        }

        MonthlyReminder monthlyReminder = monthlyReminderRepository.findByReminderTypeAndDate(dto.getReminderType(), dto.getDate());
        if (monthlyReminder == null || monthlyReminder.isDone()) {
            return false;
        }

        monthlyReminder.setDone(true);
        monthlyReminderRepository.save(monthlyReminder);
        return true;
    }
}