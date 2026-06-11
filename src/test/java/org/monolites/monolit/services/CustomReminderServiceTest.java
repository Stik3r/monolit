package org.monolites.monolit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.dtos.CustomReminderActionDto;
import org.monolites.monolit.models.entities.CustomReminder;
import org.monolites.monolit.models.enums.CustomReminderAction;
import org.monolites.monolit.models.enums.CustomReminderStatus;
import org.monolites.monolit.repositories.CustomReminderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T10:00:30Z");

    @Mock
    private CustomReminderRepository repository;
    @Mock
    private VkMessageSenderService messageSender;

    private CustomReminderService service;

    @BeforeEach
    void setUp() {
        service = new CustomReminderService(
                repository,
                messageSender,
                Clock.fixed(NOW, ZoneId.of("Europe/Moscow"))
        );
    }

    @Test
    void sendsOnlyCurrentMinuteAndMarksOlderReminderMissed() {
        CustomReminder missed = reminder(1L, Instant.parse("2026-06-11T10:00:29Z"));
        CustomReminder due = reminder(2L, Instant.parse("2026-06-11T10:00:30Z"));
        when(repository.findAllByStatusAndScheduledAtBefore(
                CustomReminderStatus.SCHEDULED,
                Instant.parse("2026-06-11T10:00:30Z")
        )).thenReturn(List.of(missed));
        when(repository.findAllByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                CustomReminderStatus.SCHEDULED,
                Instant.parse("2026-06-11T10:00:30Z"),
                Instant.parse("2026-06-11T10:00:31Z")
        )).thenReturn(List.of(due));

        service.sendDueReminders();

        assertThat(missed.getStatus()).isEqualTo(CustomReminderStatus.MISSED);
        assertThat(due.getStatus()).isEqualTo(CustomReminderStatus.SENT);
        verify(messageSender).sendMessage(anyString(), anyList(), anyList(), anyList(), eq(List.of(1, 4, 1)), eq(true));
    }

    @Test
    void postponingSentReminderSchedulesItAgain() {
        CustomReminder reminder = reminder(3L, NOW.minusSeconds(60));
        reminder.setStatus(CustomReminderStatus.SENT);
        when(repository.findById(3L)).thenReturn(Optional.of(reminder));

        service.handle(new CustomReminderActionDto(CustomReminderAction.POSTPONE_ONE_HOUR, 3L, 0));

        assertThat(reminder.getStatus()).isEqualTo(CustomReminderStatus.SCHEDULED);
        assertThat(reminder.getScheduledAt()).isEqualTo(NOW.plusSeconds(3600));
        verify(repository).save(reminder);
    }

    @Test
    void listsFiveNearestActiveRemindersPerPage() {
        List<CustomReminder> reminders = List.of(
                reminder(1L, NOW.plusSeconds(1)),
                reminder(2L, NOW.plusSeconds(2)),
                reminder(3L, NOW.plusSeconds(3)),
                reminder(4L, NOW.plusSeconds(4)),
                reminder(5L, NOW.plusSeconds(5)),
                reminder(6L, NOW.plusSeconds(6))
        );
        when(repository.findAllByStatusInOrderByScheduledAtAsc(List.of(CustomReminderStatus.SCHEDULED)))
                .thenReturn(reminders);
        ArgumentCaptor<List<String>> labels = listCaptor();

        service.sendList(0);

        verify(messageSender).sendMessage(
                contains("1-5 из 6"),
                anyList(),
                labels.capture(),
                anyList(),
                eq(List.of(5, 2)),
                eq(true)
        );
        assertThat(labels.getValue()).containsExactly("1", "2", "3", "4", "5", "Вперед", "Закрыть");
    }

    @Test
    void opensReminderCardBySendingNewMessageInsteadOfEditingUserMessage() {
        CustomReminder reminder = reminder(2L, NOW.plusSeconds(3600));
        when(repository.findById(2L)).thenReturn(Optional.of(reminder));

        service.handle(new CustomReminderActionDto(CustomReminderAction.OPEN, 2L, 0));

        verify(messageSender).sendMessage(
                contains("Напоминание 2"),
                anyList(),
                anyList(),
                anyList(),
                eq(List.of(1, 4, 1)),
                eq(true)
        );
        verifyNoMoreInteractions(messageSender);
    }

    @Test
    void closesListBySendingNewConfirmationInsteadOfEditingUserMessage() {
        service.handle(new CustomReminderActionDto(CustomReminderAction.CLOSE, null, 0));

        verify(messageSender).sendMessage("Список напоминаний закрыт.");
        verifyNoMoreInteractions(messageSender);
    }

    @Test
    void marksReminderDoneAndSendsNewConfirmationInsteadOfEditingUserMessage() {
        CustomReminder reminder = reminder(2L, NOW.plusSeconds(3600));
        when(repository.findById(2L)).thenReturn(Optional.of(reminder));

        service.handle(new CustomReminderActionDto(CustomReminderAction.DONE, 2L, 0));

        assertThat(reminder.getStatus()).isEqualTo(CustomReminderStatus.DONE);
        verify(repository).save(reminder);
        verify(messageSender).sendMessage("Напоминание выполнено.");
        verifyNoMoreInteractions(messageSender);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<String>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private CustomReminder reminder(long id, Instant scheduledAt) {
        CustomReminder reminder = new CustomReminder();
        reminder.setId(id);
        reminder.setText("Напоминание " + id);
        reminder.setScheduledAt(scheduledAt);
        reminder.setStatus(CustomReminderStatus.SCHEDULED);
        return reminder;
    }
}
