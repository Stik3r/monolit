package org.monolites.monolit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.entities.ShoppingListDraft;
import org.monolites.monolit.repositories.ShoppingListDraftRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingListConversationServiceTest {

    @Mock
    private ShoppingListDraftRepository draftRepository;
    @Mock
    private ShoppingListService shoppingListService;
    @Mock
    private VkMessageSenderService messageSender;
    @Mock
    private BotMainMenuService mainMenuService;

    private ShoppingListConversationService service;

    @BeforeEach
    void setUp() {
        service = new ShoppingListConversationService(
                draftRepository,
                shoppingListService,
                messageSender,
                mainMenuService
        );
    }

    @Test
    void opensShoppingListFromMainMenu() {
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.empty());

        assertThat(service.handle("Список покупок")).isTrue();

        verify(shoppingListService).sendList(0);
        verifyNoInteractions(messageSender, mainMenuService);
    }

    @Test
    void leavesUnrecognizedMessagesUntouchedWithoutActiveDraft() {
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.empty());

        assertThat(service.handle("Обычное сообщение")).isFalse();

        verifyNoInteractions(shoppingListService, messageSender, mainMenuService);
    }

    @Test
    void startsAddingFromCommand() {
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.empty());
        ArgumentCaptor<ShoppingListDraft> draftCaptor = ArgumentCaptor.forClass(ShoppingListDraft.class);

        assertThat(service.handle("Добавить покупку")).isTrue();

        verify(draftRepository).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getId()).isEqualTo(ShoppingListDraft.SINGLE_USER_DRAFT_ID);
        verify(messageSender).sendPersistentKeyboard(anyString(), eq(List.of("Готово", "Отмена")), eq(List.of(2)));
    }

    @Test
    void keepsItemsInDraftUntilDoneAndThenSavesThem() {
        ShoppingListDraft draft = new ShoppingListDraft();
        draft.setId(ShoppingListDraft.SINGLE_USER_DRAFT_ID);
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));
        when(shoppingListService.addItems(List.of("Молоко", "Хлеб", "Сыр"))).thenReturn(3);

        service.handle("Молоко, Хлеб\nСыр");
        service.handle("Готово");

        assertThat(draft.getPendingItems()).isEqualTo("Молоко\nХлеб\nСыр");
        verify(shoppingListService).addItems(List.of("Молоко", "Хлеб", "Сыр"));
        verify(draftRepository).delete(draft);
        verify(mainMenuService).show("Покупки добавлены: 3.");
    }

    @Test
    void cancelDeletesDraftWithoutSavingItems() {
        ShoppingListDraft draft = new ShoppingListDraft();
        draft.setId(ShoppingListDraft.SINGLE_USER_DRAFT_ID);
        draft.setPendingItems("Молоко");
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        assertThat(service.handle("Отмена")).isTrue();

        verify(draftRepository).delete(draft);
        verify(mainMenuService).show("Добавление покупок отменено.");
        verifyNoInteractions(shoppingListService);
    }

    @Test
    void rejectsBlankAndTooLongItems() {
        ShoppingListDraft draft = new ShoppingListDraft();
        draft.setId(ShoppingListDraft.SINGLE_USER_DRAFT_ID);
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle(" , ");
        service.handle("x".repeat(256));

        assertThat(draft.getPendingItems()).isNull();
        verify(draftRepository, never()).save(draft);
        verify(messageSender, atLeastOnce()).sendPersistentKeyboard(anyString(), eq(List.of("Готово", "Отмена")), eq(List.of(2)));
    }

    @Test
    void doesNotFinishEmptyDraft() {
        ShoppingListDraft draft = new ShoppingListDraft();
        draft.setId(ShoppingListDraft.SINGLE_USER_DRAFT_ID);
        when(draftRepository.findById(ShoppingListDraft.SINGLE_USER_DRAFT_ID)).thenReturn(Optional.of(draft));

        service.handle("Готово");

        verifyNoInteractions(shoppingListService, mainMenuService);
        verify(draftRepository, never()).delete(draft);
        verify(messageSender).sendPersistentKeyboard(startsWith("Сначала отправьте"), eq(List.of("Готово", "Отмена")), eq(List.of(2)));
    }
}
