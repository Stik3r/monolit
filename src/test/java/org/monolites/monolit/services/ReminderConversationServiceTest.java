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
import java.time.ZoneId;
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
}
