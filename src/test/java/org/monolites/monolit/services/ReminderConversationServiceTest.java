package org.monolites.monolit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.entities.ReminderCreationDraft;
import org.monolites.monolit.models.enums.ReminderCreationStep;
import org.monolites.monolit.repositories.ReminderCreationDraftRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReminderConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T10:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    @Mock
    private ReminderCreationDraftRepository draftRepository;
    @Mock
    private CustomReminderService customReminderService;
    @Mock
    private VkMessageSenderService messageSender;

    private ReminderConversationService service;

    @BeforeEach
    void setUp() {
        service = new ReminderConversationService(
                draftRepository,
                customReminderService,
                messageSender,
                Clock.fixed(NOW, ZONE)
        );
    }

    @Test
    void createsReminderThroughPersistentKeyboardFlowWithoutFullDateInput() {
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<ReminderCreationDraft> draftCaptor = ArgumentCaptor.forClass(ReminderCreationDraft.class);

        assertThat(service.handle("Новое напоминание")).isTrue();
        verify(draftRepository).save(draftCaptor.capture());
        ReminderCreationDraft draft = draftCaptor.getValue();
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("Купить фильтр");
        service.handle("Через 1 час");
        service.handle("Сохранить");

        verify(customReminderService).create("Купить фильтр", NOW.plusSeconds(3600));
        verify(draftRepository).delete(draft);
        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_CONFIRMATION);
        verify(messageSender, atLeastOnce()).sendPersistentKeyboard(anyString(), anyList(), anyList());
    }

    @Test
    void interpretsPassedDayNumberAsNearestFutureMonth() {
        ReminderCreationDraft draft = new ReminderCreationDraft();
        draft.setId(ReminderCreationDraft.SINGLE_USER_DRAFT_ID);
        draft.setReminderText("Купить фильтр");
        draft.setStep(ReminderCreationStep.WAITING_CUSTOM_DATE);
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("10");
        service.handle("19");

        assertThat(draft.getScheduledAt()).isEqualTo(Instant.parse("2026-07-10T16:00:00Z"));
        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_CONFIRMATION);
    }

    @Test
    void leavesUnrecognizedMessagesOutsideConversationUntouched() {
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.empty());

        assertThat(service.handle("Обычное сообщение")).isFalse();

        verifyNoInteractions(customReminderService, messageSender);
    }

    @Test
    void opensReminderListFromMainMenu() {
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.empty());

        assertThat(service.handle("Мои напоминания")).isTrue();

        verify(customReminderService).sendList(0);
        verifyNoInteractions(messageSender);
    }

    @Test
    void cancelsActiveCreation() {
        ReminderCreationDraft draft = draft(ReminderCreationStep.WAITING_TEXT);
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        assertThat(service.handle("Отмена")).isTrue();

        verify(draftRepository).delete(draft);
        verify(messageSender).sendPersistentKeyboard(
                "Создание напоминания отменено.",
                List.of("Новое напоминание", "Мои напоминания"),
                List.of(2)
        );
    }

    @Test
    void rejectsInvalidReminderText() {
        ReminderCreationDraft draft = draft(ReminderCreationStep.WAITING_TEXT);
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle(" ");

        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_TEXT);
        verify(messageSender).sendPersistentKeyboard(
                "Введите текст напоминания длиной от 1 до 2000 символов.",
                List.of("Отмена"),
                List.of(1)
        );
        verify(draftRepository, never()).save(draft);
    }

    @Test
    void acceptsCustomDateAndTime() {
        ReminderCreationDraft draft = draft(ReminderCreationStep.WAITING_DATE);
        draft.setReminderText("Купить фильтр");
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("Выбрать дату");
        service.handle("15.06");
        service.handle("Другое время");
        service.handle("19:30");

        assertThat(draft.getScheduledAt()).isEqualTo(Instant.parse("2026-06-15T16:30:00Z"));
        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_CONFIRMATION);
    }

    @Test
    void returnsFromCustomDateAndTimeSteps() {
        ReminderCreationDraft draft = draft(ReminderCreationStep.WAITING_CUSTOM_DATE);
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("Назад");
        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_DATE);

        draft.setSelectedDate(java.time.LocalDate.of(2026, Month.JUNE, 15));
        draft.setStep(ReminderCreationStep.WAITING_TIME);
        service.handle("Другое время");
        service.handle("Назад");

        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_TIME);
        verify(messageSender, atLeastOnce()).sendPersistentKeyboard(eq("Когда напомнить?"), anyList(), anyList());
        verify(messageSender, atLeastOnce()).sendPersistentKeyboard(eq("Во сколько напомнить?"), anyList(), anyList());
    }

    @Test
    void rejectsInvalidCustomDateAndPastTime() {
        ReminderCreationDraft draft = draft(ReminderCreationStep.WAITING_CUSTOM_DATE);
        draft.setReminderText("Купить фильтр");
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("31.02");
        draft.setSelectedDate(java.time.LocalDate.of(2026, Month.JUNE, 11));
        draft.setStep(ReminderCreationStep.WAITING_TIME);
        service.handle("12:00");

        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_TIME);
        verify(messageSender).sendPersistentKeyboard(
                startsWith("Не удалось определить будущую дату."),
                eq(List.of("Назад", "Отмена")),
                eq(List.of(2))
        );
        verify(messageSender).sendPersistentKeyboard(eq("Это время уже прошло. Выберите другое."), anyList(), anyList());
    }

    @Test
    void resetsDateWhenChangingConfirmedTime() {
        ReminderCreationDraft draft = draft(ReminderCreationStep.WAITING_CONFIRMATION);
        draft.setSelectedDate(java.time.LocalDate.of(2026, Month.JUNE, 15));
        draft.setScheduledAt(NOW.plusSeconds(3600));
        when(draftRepository.findById(ReminderCreationDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("Изменить время");

        assertThat(draft.getSelectedDate()).isNull();
        assertThat(draft.getScheduledAt()).isNull();
        assertThat(draft.getStep()).isEqualTo(ReminderCreationStep.WAITING_DATE);
        verify(draftRepository).save(draft);
    }

    private ReminderCreationDraft draft(ReminderCreationStep step) {
        ReminderCreationDraft draft = new ReminderCreationDraft();
        draft.setId(ReminderCreationDraft.SINGLE_USER_DRAFT_ID);
        draft.setStep(step);
        return draft;
    }
}
