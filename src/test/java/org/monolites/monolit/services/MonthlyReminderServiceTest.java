package org.monolites.monolit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.dtos.MonthlyReminderPostponeDto;
import org.monolites.monolit.models.dtos.ReminderPostponeResult;
import org.monolites.monolit.models.entities.MonthlyReminder;
import org.monolites.monolit.models.enums.ReminderPostponeAction;
import org.monolites.monolit.models.enums.ReminderPostponementType;
import org.monolites.monolit.models.enums.ReminderType;
import org.monolites.monolit.repositories.MonthlyReminderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthlyReminderServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final Instant NOW = Instant.parse("2026-06-11T10:00:00Z");
    private static final LocalDate REMINDER_DATE = LocalDate.of(2026, Month.JUNE, 1);

    @Mock
    private VkMessageSenderService messageSender;
    @Mock
    private MonthlyReminderRepository repository;

    private MonthlyReminderService service;

    @BeforeEach
    void setUp() {
        service = new MonthlyReminderService(messageSender, repository, Clock.fixed(NOW, ZONE));
    }

    @Test
    void postponesReminderForSelectedIntervalAndReplacesPreviousPostponement() {
        MonthlyReminder reminder = reminder(ReminderType.METER_READING);
        reminder.setPostponedUntil(NOW.plusSeconds(60));
        reminder.setPostponementType(ReminderPostponementType.INTERVAL);
        when(repository.findByReminderTypeAndDate(ReminderType.METER_READING, REMINDER_DATE)).thenReturn(reminder);

        ReminderPostponeResult result = service.postponeReminder(new MonthlyReminderPostponeDto(
                REMINDER_DATE,
                ReminderType.METER_READING,
                ReminderPostponeAction.THREE_HOURS
        ));

        assertThat(result.updated()).isTrue();
        assertThat(reminder.getPostponedUntil()).isEqualTo(NOW.plusSeconds(3 * 60 * 60));
        assertThat(reminder.getPostponementType()).isEqualTo(ReminderPostponementType.INTERVAL);
        verify(repository).save(reminder);
    }

    @Test
    void suppressesReminderUntilNextDayInConfiguredZone() {
        MonthlyReminder reminder = reminder(ReminderType.UTILITY_PAYMENT);
        when(repository.findByReminderTypeAndDate(ReminderType.UTILITY_PAYMENT, REMINDER_DATE)).thenReturn(reminder);

        ReminderPostponeResult result = service.postponeReminder(new MonthlyReminderPostponeDto(
                REMINDER_DATE,
                ReminderType.UTILITY_PAYMENT,
                ReminderPostponeAction.TODAY
        ));

        assertThat(result.updated()).isTrue();
        assertThat(reminder.getPostponedUntil()).isEqualTo(Instant.parse("2026-06-11T21:00:00Z"));
        assertThat(reminder.getPostponementType()).isEqualTo(ReminderPostponementType.TODAY);
    }

    @Test
    void scheduledReminderDoesNotSendBeforeIntervalExpires() {
        MonthlyReminder reminder = reminder(ReminderType.METER_READING);
        reminder.setPostponedUntil(NOW.plusSeconds(600));
        reminder.setPostponementType(ReminderPostponementType.INTERVAL);
        when(repository.findByReminderTypeAndDate(ReminderType.METER_READING, REMINDER_DATE)).thenReturn(reminder);

        service.sendMeterReadingReminder();

        verify(messageSender, never()).sendMessage(any(), anyList(), anyList(), anyList(), anyList(), eq(true));
    }

    @Test
    void sendsDueIntervalReminderWithDoneAndFivePostponeButtons() {
        MonthlyReminder reminder = reminder(ReminderType.METER_READING);
        reminder.setPostponedUntil(NOW.minusSeconds(1));
        reminder.setPostponementType(ReminderPostponementType.INTERVAL);
        when(repository.findAllByDoneFalseAndPostponementTypeAndPostponedUntilLessThanEqual(
                ReminderPostponementType.INTERVAL,
                NOW
        )).thenReturn(List.of(reminder));
        ArgumentCaptor<List<String>> labelsCaptor = listCaptor();

        service.sendPostponedReminders();

        verify(messageSender).sendMessage(
                any(),
                anyList(),
                labelsCaptor.capture(),
                anyList(),
                eq(List.of(1, 5)),
                eq(true)
        );
        assertThat(labelsCaptor.getValue()).containsExactly(
                "Передал",
                "10 минут",
                "1 час",
                "3 часа",
                "12 часов",
                "Не напоминать сегодня"
        );
        assertThat(reminder.getPostponedUntil()).isNull();
        assertThat(reminder.getPostponementType()).isNull();
    }

    @Test
    void todaySuppressionExpiresOnlyForRegularScheduler() {
        MonthlyReminder reminder = reminder(ReminderType.UTILITY_PAYMENT);
        reminder.setPostponedUntil(NOW);
        reminder.setPostponementType(ReminderPostponementType.TODAY);
        when(repository.findByReminderTypeAndDate(ReminderType.UTILITY_PAYMENT, REMINDER_DATE)).thenReturn(reminder);

        service.sendUtilityPaymentReminder();

        verify(messageSender).sendMessage(any(), anyList(), anyList(), anyList(), eq(List.of(1, 5)), eq(true));
        assertThat(reminder.getPostponedUntil()).isNull();
        assertThat(reminder.getPostponementType()).isNull();
    }

    @Test
    void initializesOnlyMissingCurrentMonthRecords() {
        MonthlyReminder existing = reminder(ReminderType.METER_READING);
        when(repository.findByReminderTypeAndDate(ReminderType.METER_READING, REMINDER_DATE)).thenReturn(existing);
        when(repository.findByReminderTypeAndDate(ReminderType.UTILITY_PAYMENT, REMINDER_DATE)).thenReturn(null);
        ArgumentCaptor<MonthlyReminder> reminderCaptor = ArgumentCaptor.forClass(MonthlyReminder.class);

        service.initializeCurrentMonthRecords();

        verify(repository).save(reminderCaptor.capture());
        assertThat(reminderCaptor.getValue().getReminderType()).isEqualTo(ReminderType.UTILITY_PAYMENT);
        assertThat(reminderCaptor.getValue().getDate()).isEqualTo(REMINDER_DATE);
    }

    @Test
    void rejectsInvalidMissingAndDonePostponements() {
        assertThat(service.postponeReminder(null).updated()).isFalse();
        assertThat(service.postponeReminder(new MonthlyReminderPostponeDto()).updated()).isFalse();

        MonthlyReminderPostponeDto payload = new MonthlyReminderPostponeDto(
                REMINDER_DATE,
                ReminderType.METER_READING,
                ReminderPostponeAction.ONE_HOUR
        );
        when(repository.findByReminderTypeAndDate(ReminderType.METER_READING, REMINDER_DATE))
                .thenReturn(null, doneReminder(ReminderType.METER_READING));

        assertThat(service.postponeReminder(payload).updated()).isFalse();
        assertThat(service.postponeReminder(payload).updated()).isFalse();
        verify(repository, never()).save(any(MonthlyReminder.class));
    }

    @Test
    void skipsMissingAndDoneScheduledReminders() {
        when(repository.findByReminderTypeAndDate(ReminderType.METER_READING, REMINDER_DATE))
                .thenReturn(null, doneReminder(ReminderType.METER_READING));

        service.sendMeterReadingReminder();
        service.sendMeterReadingReminder();

        verify(messageSender, never()).sendMessage(any(), anyList(), anyList(), anyList(), anyList(), eq(true));
    }

    @Test
    void sendsUtilityReminderWithUtilityDoneLabel() {
        MonthlyReminder reminder = reminder(ReminderType.UTILITY_PAYMENT);
        when(repository.findByReminderTypeAndDate(ReminderType.UTILITY_PAYMENT, REMINDER_DATE)).thenReturn(reminder);
        ArgumentCaptor<List<String>> labelsCaptor = listCaptor();

        service.sendUtilityPaymentReminder();

        verify(messageSender).sendMessage(
                any(),
                anyList(),
                labelsCaptor.capture(),
                anyList(),
                eq(List.of(1, 5)),
                eq(true)
        );
        assertThat(labelsCaptor.getValue().getFirst()).isEqualTo("Оплатил");
    }

    @Test
    void createsBothMissingRecords() {
        when(repository.findByReminderTypeAndDate(any(ReminderType.class), eq(REMINDER_DATE))).thenReturn(null);

        service.initializeCurrentMonthRecords();

        verify(repository, times(2)).save(any(MonthlyReminder.class));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<String>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private MonthlyReminder reminder(ReminderType type) {
        MonthlyReminder reminder = new MonthlyReminder();
        reminder.setReminderType(type);
        reminder.setDate(REMINDER_DATE);
        return reminder;
    }

    private MonthlyReminder doneReminder(ReminderType type) {
        MonthlyReminder reminder = reminder(type);
        reminder.setDone(true);
        return reminder;
    }
}
