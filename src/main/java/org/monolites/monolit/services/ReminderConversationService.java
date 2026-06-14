package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.entities.ReminderCreationDraft;
import org.monolites.monolit.models.enums.ReminderCreationStep;
import org.monolites.monolit.repositories.ReminderCreationDraftRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.monolites.monolit.models.entities.ReminderCreationDraft.SINGLE_USER_DRAFT_ID;

@Service
@RequiredArgsConstructor
public class ReminderConversationService {

    private static final String NEW_REMINDER = "Новое напоминание";
    private static final String MY_REMINDERS = "Мои напоминания";
    private static final String CANCEL = "Отмена";
    private static final String BACK = "Назад";
    private static final String ASK_DATE = "Когда напомнить?";
    private static final String ONE_OR_TWO_DIGITS = "\\d{1,2}";
    private static final DateTimeFormatter CONFIRMATION_TIME =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"));
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("января", Month.JANUARY),
            Map.entry("февраля", Month.FEBRUARY),
            Map.entry("марта", Month.MARCH),
            Map.entry("апреля", Month.APRIL),
            Map.entry("мая", Month.MAY),
            Map.entry("июня", Month.JUNE),
            Map.entry("июля", Month.JULY),
            Map.entry("августа", Month.AUGUST),
            Map.entry("сентября", Month.SEPTEMBER),
            Map.entry("октября", Month.OCTOBER),
            Map.entry("ноября", Month.NOVEMBER),
            Map.entry("декабря", Month.DECEMBER)
    );

    private final ReminderCreationDraftRepository draftRepository;
    private final CustomReminderService customReminderService;
    private final VkMessageSenderService messageSender;
    private final Clock reminderClock;

    @Transactional
    public boolean handle(String rawText) {
        String text = rawText == null ? "" : rawText.strip();
        ReminderCreationDraft draft = draftRepository.findById(SINGLE_USER_DRAFT_ID).orElse(null);
        if (draft == null) {
            if (NEW_REMINDER.equalsIgnoreCase(text)) {
                startCreation();
                return true;
            }
            if (MY_REMINDERS.equalsIgnoreCase(text)) {
                customReminderService.sendList(0);
                return true;
            }
            return false;
        }
        if (CANCEL.equalsIgnoreCase(text)) {
            draftRepository.delete(draft);
            showMainMenu("Создание напоминания отменено.");
            return true;
        }
        process(draft, text);
        return true;
    }

    public void showMainMenu(String message) {
        messageSender.sendPersistentKeyboard(message, List.of(NEW_REMINDER, MY_REMINDERS), List.of(2));
    }

    private void startCreation() {
        ReminderCreationDraft draft = new ReminderCreationDraft();
        draft.setId(SINGLE_USER_DRAFT_ID);
        draft.setStep(ReminderCreationStep.WAITING_TEXT);
        draftRepository.save(draft);
        messageSender.sendPersistentKeyboard("Что напомнить?", List.of(CANCEL), List.of(1));
    }

    private void process(ReminderCreationDraft draft, String text) {
        switch (draft.getStep()) {
            case WAITING_TEXT -> acceptText(draft, text);
            case WAITING_DATE -> acceptDateChoice(draft, text);
            case WAITING_CUSTOM_DATE -> acceptCustomDate(draft, text);
            case WAITING_TIME -> acceptTimeChoice(draft, text);
            case WAITING_CUSTOM_TIME -> acceptCustomTime(draft, text);
            case WAITING_CONFIRMATION -> acceptConfirmation(draft, text);
        }
    }

    private void acceptText(ReminderCreationDraft draft, String text) {
        if (text.isBlank() || text.length() > 2000) {
            messageSender.sendPersistentKeyboard(
                    "Введите текст напоминания длиной от 1 до 2000 символов.",
                    List.of(CANCEL),
                    List.of(1)
            );
            return;
        }
        draft.setReminderText(text);
        draft.setStep(ReminderCreationStep.WAITING_DATE);
        draftRepository.save(draft);
        askDate(ASK_DATE);
    }

    private void acceptDateChoice(ReminderCreationDraft draft, String text) {
        LocalDate today = LocalDate.now(reminderClock);
        switch (text.toLowerCase(Locale.ROOT)) {
            case "сегодня" -> selectDate(draft, today);
            case "завтра" -> selectDate(draft, today.plusDays(1));
            case "послезавтра" -> selectDate(draft, today.plusDays(2));
            case "через 1 час" -> selectRelativeTime(draft, Duration.ofHours(1));
            case "через 3 часа" -> selectRelativeTime(draft, Duration.ofHours(3));
            case "выбрать дату" -> {
                draft.setStep(ReminderCreationStep.WAITING_CUSTOM_DATE);
                draftRepository.save(draft);
                messageSender.sendPersistentKeyboard(
                        "Введите дату: например, 15, 15.06 или 15 июня.",
                        List.of(BACK, CANCEL),
                        List.of(2)
                );
            }
            default -> askDate("Выберите дату кнопкой.");
        }
    }

    private void acceptCustomDate(ReminderCreationDraft draft, String text) {
        if (BACK.equalsIgnoreCase(text)) {
            draft.setStep(ReminderCreationStep.WAITING_DATE);
            draftRepository.save(draft);
            askDate(ASK_DATE);
            return;
        }
        LocalDate date = parseDate(text);
        if (date == null) {
            messageSender.sendPersistentKeyboard(
                    "Не удалось определить будущую дату. Введите, например, 15, 15.06 или 15 июня.",
                    List.of(BACK, CANCEL),
                    List.of(2)
            );
            return;
        }
        selectDate(draft, date);
    }

    private void acceptTimeChoice(ReminderCreationDraft draft, String text) {
        if (BACK.equalsIgnoreCase(text)) {
            draft.setSelectedDate(null);
            draft.setStep(ReminderCreationStep.WAITING_DATE);
            draftRepository.save(draft);
            askDate(ASK_DATE);
            return;
        }
        if ("Другое время".equalsIgnoreCase(text)) {
            draft.setStep(ReminderCreationStep.WAITING_CUSTOM_TIME);
            draftRepository.save(draft);
            messageSender.sendPersistentKeyboard(
                    "Введите время: например, 19 или 19:30.",
                    List.of(BACK, CANCEL),
                    List.of(2)
            );
            return;
        }
        LocalTime time = parseTime(text);
        if (time == null) {
            askTime("Выберите время кнопкой.");
            return;
        }
        selectTime(draft, time);
    }

    private void acceptCustomTime(ReminderCreationDraft draft, String text) {
        if (BACK.equalsIgnoreCase(text)) {
            draft.setStep(ReminderCreationStep.WAITING_TIME);
            draftRepository.save(draft);
            askTime("Во сколько напомнить?");
            return;
        }
        LocalTime time = parseTime(text);
        if (time == null) {
            messageSender.sendPersistentKeyboard(
                    "Не удалось определить время. Введите, например, 19 или 19:30.",
                    List.of(BACK, CANCEL),
                    List.of(2)
            );
            return;
        }
        selectTime(draft, time);
    }

    private void acceptConfirmation(ReminderCreationDraft draft, String text) {
        if ("Сохранить".equalsIgnoreCase(text)) {
            customReminderService.create(draft.getReminderText(), draft.getScheduledAt());
            draftRepository.delete(draft);
            showMainMenu("Напоминание сохранено.");
        } else if ("Изменить время".equalsIgnoreCase(text)) {
            draft.setSelectedDate(null);
            draft.setScheduledAt(null);
            draft.setStep(ReminderCreationStep.WAITING_DATE);
            draftRepository.save(draft);
            askDate(ASK_DATE);
        } else {
            showConfirmation(draft, "Выберите действие кнопкой.\n\n");
        }
    }

    private void selectDate(ReminderCreationDraft draft, LocalDate date) {
        draft.setSelectedDate(date);
        draft.setStep(ReminderCreationStep.WAITING_TIME);
        draftRepository.save(draft);
        askTime("Во сколько напомнить?");
    }

    private void selectRelativeTime(ReminderCreationDraft draft, Duration duration) {
        draft.setScheduledAt(reminderClock.instant().plus(duration).truncatedTo(ChronoUnit.SECONDS));
        draft.setStep(ReminderCreationStep.WAITING_CONFIRMATION);
        draftRepository.save(draft);
        showConfirmation(draft, "");
    }

    private void selectTime(ReminderCreationDraft draft, LocalTime time) {
        Instant scheduledAt = draft.getSelectedDate().atTime(time).atZone(reminderClock.getZone()).toInstant();
        if (!scheduledAt.isAfter(reminderClock.instant())) {
            askTime("Это время уже прошло. Выберите другое.");
            return;
        }
        draft.setScheduledAt(scheduledAt);
        draft.setStep(ReminderCreationStep.WAITING_CONFIRMATION);
        draftRepository.save(draft);
        showConfirmation(draft, "");
    }

    private void askDate(String message) {
        messageSender.sendPersistentKeyboard(
                message,
                List.of("Сегодня", "Завтра", "Послезавтра", "Через 1 час", "Через 3 часа", "Выбрать дату", CANCEL),
                List.of(3, 2, 2)
        );
    }

    private void askTime(String message) {
        messageSender.sendPersistentKeyboard(
                message,
                List.of("09:00", "12:00", "18:00", "21:00", "Другое время", BACK, CANCEL),
                List.of(2, 2, 3)
        );
    }

    private void showConfirmation(ReminderCreationDraft draft, String prefix) {
        String time = CONFIRMATION_TIME.format(draft.getScheduledAt().atZone(reminderClock.getZone()));
        messageSender.sendPersistentKeyboard(
                prefix + "Сохранить напоминание?\n\n" + draft.getReminderText() + "\n\n" + time,
                List.of("Сохранить", "Изменить время", CANCEL),
                List.of(2, 1)
        );
    }

    private LocalDate parseDate(String text) {
        LocalDate today = LocalDate.now(reminderClock);
        String normalized = text.toLowerCase(Locale.ROOT).strip();
        if (normalized.matches(ONE_OR_TWO_DIGITS)) {
            return nearestFutureDay(Integer.parseInt(normalized), today);
        }
        if (normalized.matches("\\d{1,2}\\.\\d{1,2}")) {
            String[] parts = normalized.split("\\.");
            return futureDate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), today);
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 2 && parts[0].matches(ONE_OR_TWO_DIGITS) && MONTHS.containsKey(parts[1])) {
            return futureDate(Integer.parseInt(parts[0]), MONTHS.get(parts[1]).getValue(), today);
        }
        return null;
    }

    private LocalDate nearestFutureDay(int day, LocalDate today) {
        for (int offset = 0; offset <= 12; offset++) {
            YearMonth month = YearMonth.from(today).plusMonths(offset);
            if (day <= month.lengthOfMonth()) {
                LocalDate candidate = month.atDay(day);
                if (!candidate.isBefore(today)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private LocalDate futureDate(int day, int month, LocalDate today) {
        try {
            LocalDate candidate = LocalDate.of(today.getYear(), month, day);
            return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        } catch (DateTimeException e) {
            return null;
        }
    }

    private LocalTime parseTime(String text) {
        String normalized = text.strip();
        try {
            if (normalized.matches(ONE_OR_TWO_DIGITS)) {
                return LocalTime.of(Integer.parseInt(normalized), 0);
            }
            return LocalTime.parse(normalized, DateTimeFormatter.ofPattern("H:mm"));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
