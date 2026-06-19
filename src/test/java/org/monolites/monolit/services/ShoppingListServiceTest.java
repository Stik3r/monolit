package org.monolites.monolit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.dtos.ShoppingListActionDto;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.monolites.monolit.models.entities.ShoppingItem;
import org.monolites.monolit.models.enums.ShoppingItemStatus;
import org.monolites.monolit.models.enums.ShoppingListAction;
import org.monolites.monolit.repositories.ShoppingItemRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-11T10:00:30Z");

    @Mock
    private ShoppingItemRepository repository;
    @Mock
    private VkMessageSenderService messageSender;
    @Mock
    private BotMainMenuService mainMenuService;

    private ShoppingListService service;

    @BeforeEach
    void setUp() {
        service = new ShoppingListService(
                repository,
                messageSender,
                mainMenuService,
                Clock.fixed(NOW, ZoneId.of("Europe/Moscow"))
        );
    }

    @Test
    void addsItemsAsToBuyWithCreatedAt() {
        ArgumentCaptor<ShoppingItem> itemCaptor = ArgumentCaptor.forClass(ShoppingItem.class);

        int saved = service.addItems(List.of("Молоко", "Хлеб"));

        assertThat(saved).isEqualTo(2);
        verify(repository, times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(ShoppingItem::getName)
                .containsExactly("Молоко", "Хлеб");
        assertThat(itemCaptor.getAllValues())
                .allSatisfy(item -> {
                    assertThat(item.getStatus()).isEqualTo(ShoppingItemStatus.TO_BUY);
                    assertThat(item.getCreatedAt()).isEqualTo(NOW);
                });
    }

    @Test
    void sendsEmptyListWithAddAndCloseActions() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of());

        service.sendList(0);

        verify(messageSender).sendMessage(
                eq("Список покупок пуст."),
                anyList(),
                eq(List.of("➕", "✖")),
                anyList(),
                eq(List.of(2)),
                eq(true)
        );
    }

    @Test
    void listsFiveItemsPerPageWithNavigationAndActions() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(items(12));
        ArgumentCaptor<List<String>> labels = listCaptor();

        service.sendList(0);

        verify(messageSender).sendMessage(
                contains("1-5 из 12"),
                anyList(),
                labels.capture(),
                anyList(),
                eq(List.of(5, 4)),
                eq(true)
        );
        assertThat(labels.getValue())
                .containsExactly("1", "2", "3", "4", "5",
                        "→", "➕", "🧹", "✖");
    }

    @Test
    void listsMiddlePageWithCompactPreviousAndNextNavigation() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(items(12));
        ArgumentCaptor<List<String>> labels = listCaptor();

        service.sendList(1);

        verify(messageSender).sendMessage(
                contains("6-10 из 12"),
                anyList(),
                labels.capture(),
                anyList(),
                eq(List.of(5, 5)),
                eq(true)
        );
        assertThat(labels.getValue())
                .containsExactly("6", "7", "8", "9", "10",
                        "←", "→", "➕", "🧹", "✖");
    }

    @Test
    void listsLastPageWithCompactPreviousNavigationOnly() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(items(12));
        ArgumentCaptor<List<String>> labels = listCaptor();

        service.sendList(2);

        verify(messageSender).sendMessage(
                contains("11-12 из 12"),
                anyList(),
                labels.capture(),
                anyList(),
                eq(List.of(2, 4)),
                eq(true)
        );
        assertThat(labels.getValue())
                .containsExactly("11", "12", "←", "➕", "🧹", "✖");
    }

    @Test
    void keepsActionPayloadsWhenUsingCompactLabels() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(items(6));
        ArgumentCaptor<List<Object>> payloads = payloadCaptor();

        service.sendList(0);

        verify(messageSender).sendMessage(
                contains("1-5 из 6"),
                anyList(),
                anyList(),
                payloads.capture(),
                anyList(),
                eq(true)
        );
        assertThat(payloads.getValue())
                .map(ShoppingListServiceTest::shoppingListAction)
                .containsExactly(
                        ShoppingListAction.TOGGLE,
                        ShoppingListAction.TOGGLE,
                        ShoppingListAction.TOGGLE,
                        ShoppingListAction.TOGGLE,
                        ShoppingListAction.TOGGLE,
                        ShoppingListAction.LIST,
                        ShoppingListAction.ADD,
                        ShoppingListAction.CLEAR_PURCHASED_REQUEST,
                        ShoppingListAction.CLOSE
                );
    }

    @Test
    void paginatesSixItemsToKeepKeyboardWithinVkButtonLimit() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(items(6));

        service.sendList(0);

        verify(messageSender).sendMessage(
                contains("1-5 из 6"),
                anyList(),
                anyList(),
                anyList(),
                eq(List.of(5, 4)),
                eq(true)
        );
    }

    @Test
    void paginatesEightItemsToKeepKeyboardWithinVkButtonLimit() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(items(8));
        ArgumentCaptor<List<String>> labels = listCaptor();

        service.sendList(0);

        verify(messageSender).sendMessage(
                contains("1-5 из 8"),
                anyList(),
                labels.capture(),
                anyList(),
                eq(List.of(5, 4)),
                eq(true)
        );
        assertThat(labels.getValue())
                .containsExactly("1", "2", "3", "4", "5",
                        "→", "➕", "🧹", "✖");
    }

    @Test
    void displaysItemsInCreatedOrderWithStatusOnlyInText() {
        ShoppingItem purchased = item(1L, "Куплено", ShoppingItemStatus.PURCHASED, NOW);
        ShoppingItem active = item(2L, "Нужно купить", ShoppingItemStatus.TO_BUY, NOW.plusSeconds(1));
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(purchased, active));

        service.sendList(0);

        verify(messageSender).sendMessage(
                argThat(text -> text.contains("1. Куплено ✅")
                        && text.contains("2. Нужно купить")
                        && !text.contains("[ ]")
                        && !text.contains("[x]")
                        && text.indexOf("Куплено ✅") < text.indexOf("Нужно купить")),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                eq(true)
        );
    }

    @Test
    void sendsFullListAsPlainReadOnlyMessageWithExistingStatusStyle() {
        ShoppingItem milk = item(1L, "Молоко", ShoppingItemStatus.TO_BUY, NOW);
        ShoppingItem bread = item(2L, "Хлеб", ShoppingItemStatus.PURCHASED, NOW.plusSeconds(1));
        ShoppingItem cheese = item(3L, "Сыр", ShoppingItemStatus.TO_BUY, NOW.plusSeconds(2));
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(milk, bread, cheese));

        service.sendFullList();

        verify(messageSender).sendMessage("""
                Весь список покупок, 3:

                1. Молоко
                2. Хлеб ✅
                3. Сыр""");
        verify(messageSender, never()).sendMessage(anyString(), anyList(), anyList(), anyList(), anyList(), anyBoolean());
        verify(repository, never()).save(any());
        verify(repository, never()).deleteByStatus(any());
    }

    @Test
    void sendsEmptyFullListAsPlainMessage() {
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of());

        service.sendFullList();

        verify(messageSender).sendMessage("Список покупок пуст.");
        verify(messageSender, never()).sendMessage(anyString(), anyList(), anyList(), anyList(), anyList(), anyBoolean());
    }

    @Test
    void togglesItemStatusAndRefreshesList() {
        ShoppingItem item = item(1L, "Молоко", ShoppingItemStatus.TO_BUY, NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(item));
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(item));

        service.handle(new ShoppingListActionDto(ShoppingListAction.TOGGLE, 1L, 0));

        assertThat(item.getStatus()).isEqualTo(ShoppingItemStatus.PURCHASED);
        verify(repository).save(item);
        verify(messageSender).sendMessage(
                argThat(text -> text.contains("Статус обновлен.") && text.contains("Молоко ✅")),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                eq(true)
        );
    }

    @Test
    void keepsToggledItemAtTheSameDisplayedNumber() {
        ShoppingItem milk = item(1L, "Молоко", ShoppingItemStatus.TO_BUY, NOW);
        ShoppingItem bread = item(2L, "Хлеб", ShoppingItemStatus.TO_BUY, NOW.plusSeconds(1));
        ShoppingItem cheese = item(3L, "Сыр", ShoppingItemStatus.TO_BUY, NOW.plusSeconds(2));
        ShoppingItem butter = item(4L, "Масло", ShoppingItemStatus.TO_BUY, NOW.plusSeconds(3));
        when(repository.findById(1L)).thenReturn(Optional.of(milk));
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(milk, bread, cheese, butter));

        service.handle(new ShoppingListActionDto(ShoppingListAction.TOGGLE, 1L, 0));

        assertThat(milk.getStatus()).isEqualTo(ShoppingItemStatus.PURCHASED);
        verify(messageSender).sendMessage(
                argThat(text -> text.contains("1. Молоко ✅")
                        && text.contains("2. Хлеб")
                        && text.contains("3. Сыр")
                        && text.contains("4. Масло")
                        && !text.contains("4. Молоко ✅")),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                eq(true)
        );
    }

    @Test
    void togglesPurchasedItemBackToPlainActiveItem() {
        ShoppingItem item = item(1L, "Молоко", ShoppingItemStatus.PURCHASED, NOW);
        when(repository.findById(1L)).thenReturn(Optional.of(item));
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(item));

        service.handle(new ShoppingListActionDto(ShoppingListAction.TOGGLE, 1L, 0));

        assertThat(item.getStatus()).isEqualTo(ShoppingItemStatus.TO_BUY);
        verify(repository).save(item);
        verify(messageSender).sendMessage(
                argThat(text -> text.contains("1. Молоко") && !text.contains("Молоко ✅")),
                anyList(),
                anyList(),
                anyList(),
                anyList(),
                eq(true)
        );
    }

    @Test
    void handlesMissingItemAsUnavailableAndShowsCurrentList() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of());

        service.handle(new ShoppingListActionDto(ShoppingListAction.TOGGLE, 99L, 0));

        verify(messageSender).sendMessage(startsWith("Покупка уже недоступна."), anyList(), anyList(), anyList(), eq(List.of(2)), eq(true));
    }

    @Test
    void asksBeforeClearingPurchasedItemsAndDeletesOnlyAfterConfirmation() {
        when(repository.countByStatus(ShoppingItemStatus.PURCHASED)).thenReturn(2L);
        when(repository.deleteByStatus(ShoppingItemStatus.PURCHASED)).thenReturn(2L);
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(item(1L, "Молоко", ShoppingItemStatus.TO_BUY, NOW)));

        service.handle(new ShoppingListActionDto(ShoppingListAction.CLEAR_PURCHASED_REQUEST, null, 0));
        service.handle(new ShoppingListActionDto(ShoppingListAction.CLEAR_PURCHASED_CONFIRM, null, 0));

        verify(messageSender).sendMessage(eq("Удалить все купленные покупки?"), anyList(), eq(List.of("🧹", "↩")), anyList(), eq(List.of(2)), eq(true));
        verify(repository).deleteByStatus(ShoppingItemStatus.PURCHASED);
        verify(messageSender).sendMessage(contains("Купленные покупки удалены."), anyList(), anyList(), anyList(), anyList(), eq(true));
    }

    @Test
    void reportsWhenThereAreNoPurchasedItemsToClear() {
        when(repository.countByStatus(ShoppingItemStatus.PURCHASED)).thenReturn(0L);
        when(repository.findAllByOrderByCreatedAtAscIdAsc()).thenReturn(List.of(item(1L, "Молоко", ShoppingItemStatus.TO_BUY, NOW)));

        service.handle(new ShoppingListActionDto(ShoppingListAction.CLEAR_PURCHASED_REQUEST, null, 0));

        verify(repository, never()).deleteByStatus(any());
        verify(messageSender).sendMessage(contains("Купленных покупок нет."), anyList(), anyList(), anyList(), anyList(), eq(true));
    }

    @Test
    void closesListWithNewMessage() {
        service.handle(new ShoppingListActionDto(ShoppingListAction.CLOSE, null, 0));

        verify(messageSender).sendMessage("Список покупок закрыт.");
        verifyNoMoreInteractions(messageSender);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<String>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<Object>> payloadCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private static ShoppingListAction shoppingListAction(Object payload) {
        CallbackPayloadEnvelope envelope = (CallbackPayloadEnvelope) payload;
        ShoppingListActionDto dto = (ShoppingListActionDto) envelope.data();
        return dto.action();
    }

    private List<ShoppingItem> items(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> item((long) i, "Покупка " + i, ShoppingItemStatus.TO_BUY, NOW.plusSeconds(i)))
                .toList();
    }

    private ShoppingItem item(long id, String name, ShoppingItemStatus status, Instant createdAt) {
        ShoppingItem item = new ShoppingItem();
        item.setId(id);
        item.setName(name);
        item.setStatus(status);
        item.setCreatedAt(createdAt);
        return item;
    }
}
