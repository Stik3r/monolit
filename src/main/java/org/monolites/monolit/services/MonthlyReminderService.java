package org.monolites.monolit.services;

import com.vk.api.sdk.queries.EnumParam;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.MonthlyReminderDto;
import org.monolites.monolit.models.dtos.MonthlyReminderPostponeDto;
import org.monolites.monolit.models.dtos.ReminderPostponeResult;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.monolites.monolit.models.entities.MonthlyReminder;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.models.enums.ReminderPostponeAction;
import org.monolites.monolit.models.enums.ReminderPostponementType;
import org.monolites.monolit.models.enums.ReminderType;
import org.monolites.monolit.repositories.MonthlyReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;

@Service
@RequiredArgsConstructor
public class MonthlyReminderService {

    private static final int MONTHLY_REMINDER_PAYLOAD_VERSION = 1;

    private static final String METER_READING_MESSAGE =
            "Напоминание: передайте показания счетчиков.";
    private static final String UTILITY_PAYMENT_MESSAGE =
            "Напоминание: оплатите коммунальные услуги.";

    private static final List<String> POSTPONE_LABELS = List.of(
            "10 минут",
            "1 час",
            "3 часа",
            "12 часов",
            "Не напоминать сегодня"
    );
    private static final List<ReminderPostponeAction> POSTPONE_ACTIONS = List.of(
            ReminderPostponeAction.TEN_MINUTES,
            ReminderPostponeAction.ONE_HOUR,
            ReminderPostponeAction.THREE_HOURS,
            ReminderPostponeAction.TWELVE_HOURS,
            ReminderPostponeAction.TODAY
    );

    private final VkMessageSenderService vkMessageSenderService;
    private final MonthlyReminderRepository monthlyReminderRepository;
    private final Clock reminderClock;

    @Transactional
    public void sendMeterReadingReminder() {
        sendScheduledReminder(ReminderType.METER_READING);
    }

    @Transactional
    public void sendUtilityPaymentReminder() {
        sendScheduledReminder(ReminderType.UTILITY_PAYMENT);
    }

    @Transactional
    public void sendPostponedReminders() {
        Instant now = reminderClock.instant();
        List<MonthlyReminder> reminders = monthlyReminderRepository
                .findAllByDoneFalseAndPostponementTypeAndPostponedUntilLessThanEqual(
                        ReminderPostponementType.INTERVAL,
                        now
                );

        for (MonthlyReminder reminder : reminders) {
            clearPostponement(reminder);
            monthlyReminderRepository.save(reminder);
            sendReminder(reminder);
        }
    }

    @Transactional
    public void createMeterReadingRecord() {
        createReminderRecord(ReminderType.METER_READING);
    }

    @Transactional
    public void createUtilityPaymentRecord() {
        createReminderRecord(ReminderType.UTILITY_PAYMENT);
    }

    @Transactional
    public void initializeCurrentMonthRecords() {
        createReminderRecord(ReminderType.METER_READING);
        createReminderRecord(ReminderType.UTILITY_PAYMENT);
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
        clearPostponement(monthlyReminder);
        monthlyReminderRepository.save(monthlyReminder);
        return true;
    }

    @Transactional
    public ReminderPostponeResult postponeReminder(MonthlyReminderPostponeDto dto) {
        if (dto == null || dto.getDate() == null || dto.getReminderType() == null || dto.getAction() == null) {
            return ReminderPostponeResult.notUpdated();
        }

        MonthlyReminder reminder = monthlyReminderRepository.findByReminderTypeAndDate(dto.getReminderType(), dto.getDate());
        if (reminder == null || reminder.isDone()) {
            return ReminderPostponeResult.notUpdated();
        }

        ZonedDateTime now = ZonedDateTime.now(reminderClock);
        ZonedDateTime postponedUntil;
        if (dto.getAction() == ReminderPostponeAction.TODAY) {
            postponedUntil = now.toLocalDate().plusDays(1).atStartOfDay(reminderClock.getZone());
            reminder.setPostponementType(ReminderPostponementType.TODAY);
        } else {
            postponedUntil = now.plus(dto.getAction().duration());
            reminder.setPostponementType(ReminderPostponementType.INTERVAL);
        }
        reminder.setPostponedUntil(postponedUntil.toInstant());
        monthlyReminderRepository.save(reminder);
        return new ReminderPostponeResult(true, dto.getAction(), postponedUntil);
    }

    private void sendScheduledReminder(ReminderType reminderType) {
        MonthlyReminder reminder = monthlyReminderRepository.findByReminderTypeAndDate(reminderType, currentReminderDate());
        if (reminder == null || reminder.isDone() || isPostponed(reminder)) {
            return;
        }
        sendReminder(reminder);
    }

    private boolean isPostponed(MonthlyReminder reminder) {
        if (reminder.getPostponedUntil() == null || reminder.getPostponementType() == null) {
            return false;
        }

        if (reminder.getPostponementType() == ReminderPostponementType.TODAY
                && !reminderClock.instant().isBefore(reminder.getPostponedUntil())) {
            clearPostponement(reminder);
            monthlyReminderRepository.save(reminder);
            return false;
        }
        return true;
    }

    private void sendReminder(MonthlyReminder reminder) {
        List<EnumParam<String>> buttonTypes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();

        buttonTypes.add(TEXT);
        labels.add(doneLabel(reminder.getReminderType()));
        payloads.add(donePayload(reminder));

        for (int i = 0; i < POSTPONE_ACTIONS.size(); i++) {
            buttonTypes.add(TEXT);
            labels.add(POSTPONE_LABELS.get(i));
            payloads.add(postponePayload(reminder, POSTPONE_ACTIONS.get(i)));
        }

        vkMessageSenderService.sendMessage(
                message(reminder.getReminderType()),
                buttonTypes,
                labels,
                payloads,
                List.of(1, 5),
                true
        );
    }

    private void createReminderRecord(ReminderType reminderType) {
        if (monthlyReminderRepository.findByReminderTypeAndDate(reminderType, currentReminderDate()) != null) {
            return;
        }
        MonthlyReminder monthlyReminder = new MonthlyReminder();
        monthlyReminder.setReminderType(reminderType);
        monthlyReminder.setDate(currentReminderDate());
        monthlyReminder.setDone(false);
        monthlyReminderRepository.save(monthlyReminder);
    }

    private LocalDate currentReminderDate() {
        return LocalDate.now(reminderClock).with(TemporalAdjusters.firstDayOfMonth());
    }

    private CallbackPayloadEnvelope donePayload(MonthlyReminder reminder) {
        return new CallbackPayloadEnvelope(
                CallbackPayloadType.MONTHLY_REMINDER_DONE.value(),
                MONTHLY_REMINDER_PAYLOAD_VERSION,
                new MonthlyReminderDto(reminder.getDate(), reminder.getReminderType())
        );
    }

    private CallbackPayloadEnvelope postponePayload(MonthlyReminder reminder, ReminderPostponeAction action) {
        return new CallbackPayloadEnvelope(
                CallbackPayloadType.MONTHLY_REMINDER_POSTPONE.value(),
                MONTHLY_REMINDER_PAYLOAD_VERSION,
                new MonthlyReminderPostponeDto(reminder.getDate(), reminder.getReminderType(), action)
        );
    }

    private String message(ReminderType reminderType) {
        return switch (reminderType) {
            case METER_READING -> METER_READING_MESSAGE;
            case UTILITY_PAYMENT -> UTILITY_PAYMENT_MESSAGE;
        };
    }

    private String doneLabel(ReminderType reminderType) {
        return switch (reminderType) {
            case METER_READING -> "Передал";
            case UTILITY_PAYMENT -> "Оплатил";
        };
    }

    private void clearPostponement(MonthlyReminder reminder) {
        reminder.setPostponedUntil(null);
        reminder.setPostponementType(null);
    }
}
