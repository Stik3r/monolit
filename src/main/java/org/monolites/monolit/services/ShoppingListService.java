package org.monolites.monolit.services;

import com.vk.api.sdk.queries.EnumParam;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.ShoppingListActionDto;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.monolites.monolit.models.entities.ShoppingItem;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.models.enums.ShoppingItemStatus;
import org.monolites.monolit.models.enums.ShoppingListAction;
import org.monolites.monolit.repositories.ShoppingItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;

@Service
@RequiredArgsConstructor
public class ShoppingListService {

    private static final int PAGE_SIZE = 5;
    private static final int MAX_KEYBOARD_ROW_SIZE = 5;
    private static final int PAYLOAD_VERSION = 1;

    private final ShoppingItemRepository repository;
    private final VkMessageSenderService messageSender;
    private final BotMainMenuService mainMenuService;
    private final Clock reminderClock;

    @Transactional
    public int addItems(List<String> names) {
        int saved = 0;
        for (String name : names) {
            ShoppingItem item = new ShoppingItem();
            item.setName(name);
            item.setStatus(ShoppingItemStatus.TO_BUY);
            item.setCreatedAt(reminderClock.instant());
            repository.save(item);
            saved++;
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public void sendList(int requestedPage) {
        sendListContent(requestedPage, "");
    }

    @Transactional
    public void handle(ShoppingListActionDto dto) {
        if (dto == null || dto.action() == null) {
            return;
        }
        switch (dto.action()) {
            case LIST -> sendListContent(dto.page(), "");
            case ADD -> mainMenuService.show("Отправьте покупки одним сообщением. Можно несколько строк или через запятую.");
            case CLOSE -> messageSender.sendMessage("Список покупок закрыт.");
            case TOGGLE -> toggle(dto.itemId(), dto.page());
            case CLEAR_PURCHASED_REQUEST -> requestClearPurchased(dto.page());
            case CLEAR_PURCHASED_CONFIRM -> clearPurchased(dto.page());
        }
    }

    private void sendListContent(int requestedPage, String prefix) {
        List<ShoppingItem> items = orderedItems();
        if (items.isEmpty()) {
            messageSender.sendMessage(
                    prefix + "Список покупок пуст.",
                    List.of(TEXT, TEXT),
                    List.of("Добавить", "Закрыть"),
                    List.of(payload(ShoppingListAction.ADD, null, 0), payload(ShoppingListAction.CLOSE, null, 0)),
                    List.of(2),
                    true
            );
            return;
        }

        int pageCount = (items.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, items.size());
        List<ShoppingItem> visible = items.subList(from, to);

        StringBuilder text = new StringBuilder(prefix)
                .append("Список покупок, ")
                .append(from + 1).append('-').append(to).append(" из ").append(items.size()).append(":\n\n");
        for (int i = 0; i < visible.size(); i++) {
            ShoppingItem item = visible.get(i);
            text.append(from + i + 1).append(". ")
                    .append(displayName(item))
                    .append('\n');
        }

        List<String> labels = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();
        List<EnumParam<String>> types = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            labels.add(String.valueOf(from + i + 1));
            payloads.add(payload(ShoppingListAction.TOGGLE, visible.get(i).getId(), page));
            types.add(TEXT);
        }
        List<Integer> rows = itemButtonRows(visible.size());

        int navigationButtons = 0;
        if (page > 0) {
            labels.add("Назад");
            payloads.add(payload(ShoppingListAction.LIST, null, page - 1));
            types.add(TEXT);
            navigationButtons++;
        }
        if (page + 1 < pageCount) {
            labels.add("Вперед");
            payloads.add(payload(ShoppingListAction.LIST, null, page + 1));
            types.add(TEXT);
            navigationButtons++;
        }
        labels.add("Добавить");
        payloads.add(payload(ShoppingListAction.ADD, null, page));
        types.add(TEXT);
        navigationButtons++;
        labels.add("Очистить купленные");
        payloads.add(payload(ShoppingListAction.CLEAR_PURCHASED_REQUEST, null, page));
        types.add(TEXT);
        navigationButtons++;
        labels.add("Закрыть");
        payloads.add(payload(ShoppingListAction.CLOSE, null, page));
        types.add(TEXT);
        navigationButtons++;
        rows.add(navigationButtons);

        messageSender.sendMessage(text.toString().stripTrailing(), types, labels, payloads, rows, true);
    }

    private void toggle(Long itemId, int page) {
        Optional<ShoppingItem> optionalItem = itemId == null ? Optional.empty() : repository.findById(itemId);
        if (optionalItem.isEmpty()) {
            sendListContent(page, "Покупка уже недоступна.\n\n");
            return;
        }
        ShoppingItem item = optionalItem.get();
        item.setStatus(item.getStatus() == ShoppingItemStatus.PURCHASED
                ? ShoppingItemStatus.TO_BUY
                : ShoppingItemStatus.PURCHASED);
        repository.save(item);
        sendListContent(page, "Статус обновлен.\n\n");
    }

    private void requestClearPurchased(int page) {
        if (repository.countByStatus(ShoppingItemStatus.PURCHASED) == 0) {
            sendListContent(page, "Купленных покупок нет.\n\n");
            return;
        }
        messageSender.sendMessage(
                "Удалить все купленные покупки?",
                List.of(TEXT, TEXT),
                List.of("Удалить купленные", "К списку"),
                List.of(
                        payload(ShoppingListAction.CLEAR_PURCHASED_CONFIRM, null, page),
                        payload(ShoppingListAction.LIST, null, page)
                ),
                List.of(2),
                true
        );
    }

    private void clearPurchased(int page) {
        long deleted = repository.deleteByStatus(ShoppingItemStatus.PURCHASED);
        sendListContent(page, deleted == 0 ? "Купленных покупок нет.\n\n" : "Купленные покупки удалены.\n\n");
    }

    private List<ShoppingItem> orderedItems() {
        return repository.findAllByOrderByCreatedAtAscIdAsc();
    }

    private static List<Integer> itemButtonRows(int itemButtonCount) {
        List<Integer> rows = new ArrayList<>();
        int remaining = itemButtonCount;
        while (remaining > 0) {
            int rowSize = Math.min(remaining, MAX_KEYBOARD_ROW_SIZE);
            rows.add(rowSize);
            remaining -= rowSize;
        }
        return rows;
    }

    private static String displayName(ShoppingItem item) {
        return item.getStatus() == ShoppingItemStatus.PURCHASED
                ? item.getName() + " ✅"
                : item.getName();
    }

    private CallbackPayloadEnvelope payload(ShoppingListAction action, Long itemId, int page) {
        return new CallbackPayloadEnvelope(
                CallbackPayloadType.SHOPPING_LIST_ACTION.value(),
                PAYLOAD_VERSION,
                new ShoppingListActionDto(action, itemId, page)
        );
    }
}
