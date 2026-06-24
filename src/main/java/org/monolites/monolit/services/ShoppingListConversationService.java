package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.entities.ShoppingListDraft;
import org.monolites.monolit.repositories.ShoppingListDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.monolites.monolit.models.entities.ShoppingListDraft.SINGLE_USER_DRAFT_ID;

@Service
@RequiredArgsConstructor
public class ShoppingListConversationService {

    private static final String ADD_ITEM = "Добавить покупку";
    private static final String ADD = "Добавить";
    private static final String DONE = "Готово";
    private static final String CANCEL = "Отмена";
    private static final int MAX_ITEM_LENGTH = 255;
    private static final Pattern SPLITTER = Pattern.compile("[,\\n]");

    private final ShoppingListDraftRepository draftRepository;
    private final ShoppingListService shoppingListService;
    private final VkMessageSenderService messageSender;
    private final BotMainMenuService mainMenuService;

    @Transactional(readOnly = true)
    public boolean isActive() {
        return draftRepository.existsById(SINGLE_USER_DRAFT_ID);
    }

    @Transactional
    public boolean handle(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        ShoppingListDraft draft = draftRepository.findById(SINGLE_USER_DRAFT_ID).orElse(null);
        if (draft == null) {
            if (BotMainMenuService.SHOPPING_LIST.equalsIgnoreCase(text)) {
                shoppingListService.sendList(0);
                return true;
            }
            if (BotMainMenuService.FULL_SHOPPING_LIST.equalsIgnoreCase(text)) {
                shoppingListService.sendFullList();
                return true;
            }
            if (ADD_ITEM.equalsIgnoreCase(text) || ADD.equalsIgnoreCase(text)) {
                doStartAdding();
                return true;
            }
            return false;
        }
        if (CANCEL.equalsIgnoreCase(text)) {
            draftRepository.delete(draft);
            mainMenuService.show("Добавление покупок отменено.");
            return true;
        }
        if (DONE.equalsIgnoreCase(text)) {
            finishAdding(draft);
            return true;
        }
        acceptItems(draft, text);
        return true;
    }

    @Transactional
    public void startAdding() {
        doStartAdding();
    }

    private void doStartAdding() {
        if (draftRepository.existsById(SINGLE_USER_DRAFT_ID)) {
            messageSender.sendPersistentKeyboard(
                    "Отправьте покупки одним сообщением. Можно несколько строк или через запятую.",
                    List.of(DONE, CANCEL),
                    List.of(2)
            );
            return;
        }
        ShoppingListDraft draft = new ShoppingListDraft();
        draft.setId(SINGLE_USER_DRAFT_ID);
        draftRepository.save(draft);
        messageSender.sendPersistentKeyboard(
                "Отправьте покупки одним сообщением. Можно несколько строк или через запятую.",
                List.of(DONE, CANCEL),
                List.of(2)
        );
    }

    private void acceptItems(ShoppingListDraft draft, String text) {
        ParseResult result = parseItems(text);
        if (!result.invalidItems().isEmpty()) {
            messageSender.sendPersistentKeyboard(
                    "Не удалось добавить часть покупок. Каждый элемент должен быть от 1 до "
                            + MAX_ITEM_LENGTH + " символов.",
                    List.of(DONE, CANCEL),
                    List.of(2)
            );
        }
        if (result.validItems().isEmpty()) {
            messageSender.sendPersistentKeyboard(
                    "Отправьте хотя бы одну покупку текстом.",
                    List.of(DONE, CANCEL),
                    List.of(2)
            );
            return;
        }
        List<String> pendingItems = pendingItems(draft);
        pendingItems.addAll(result.validItems());
        draft.setPendingItems(String.join("\n", pendingItems));
        draftRepository.save(draft);
        messageSender.sendPersistentKeyboard(
                "Добавлено к черновику: " + result.validItems().size()
                        + ". Можно отправить еще или нажать `Готово`.",
                List.of(DONE, CANCEL),
                List.of(2)
        );
    }

    private void finishAdding(ShoppingListDraft draft) {
        List<String> items = pendingItems(draft);
        if (items.isEmpty()) {
            messageSender.sendPersistentKeyboard(
                    "Сначала отправьте хотя бы одну покупку или нажмите `Отмена`.",
                    List.of(DONE, CANCEL),
                    List.of(2)
            );
            return;
        }
        int saved = shoppingListService.addItems(items);
        draftRepository.delete(draft);
        mainMenuService.show("Покупки добавлены: " + saved + ".");
    }

    private List<String> pendingItems(ShoppingListDraft draft) {
        if (draft.getPendingItems() == null || draft.getPendingItems().isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(draft.getPendingItems().lines().toList());
    }

    private ParseResult parseItems(String text) {
        List<String> validItems = new ArrayList<>();
        List<String> invalidItems = new ArrayList<>();
        for (String rawItem : SPLITTER.split(text)) {
            String item = rawItem.strip();
            if (item.isEmpty()) {
                continue;
            }
            if (item.length() > MAX_ITEM_LENGTH) {
                invalidItems.add(item);
            } else {
                validItems.add(item);
            }
        }
        if (text.isBlank() || validItems.isEmpty() && invalidItems.isEmpty()) {
            invalidItems.add(text);
        }
        return new ParseResult(validItems, invalidItems);
    }

    private record ParseResult(List<String> validItems, List<String> invalidItems) {
    }
}
