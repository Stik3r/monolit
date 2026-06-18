package org.monolites.monolit.services;

import com.vk.api.sdk.queries.EnumParam;
import lombok.RequiredArgsConstructor;
import org.monolites.monolit.models.dtos.CustomReminderActionDto;
import org.monolites.monolit.models.dtos.callback.CallbackPayloadEnvelope;
import org.monolites.monolit.models.entities.CustomReminder;
import org.monolites.monolit.models.enums.CallbackPayloadType;
import org.monolites.monolit.models.enums.CustomReminderAction;
import org.monolites.monolit.models.enums.CustomReminderStatus;
import org.monolites.monolit.repositories.CustomReminderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;

@Service
@RequiredArgsConstructor
public class CustomReminderService {

    private static final int PAGE_SIZE = 5;
    private static final int PAYLOAD_VERSION = 1;
    private static final DateTimeFormatter FULL_TIME =
            DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("ru"));
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CustomReminderRepository repository;
    private final VkMessageSenderService messageSender;
    private final BotMainMenuService mainMenuService;
    private final Clock reminderClock;

    @Transactional
    public CustomReminder create(String text, Instant scheduledAt) {
        CustomReminder reminder = new CustomReminder();
        reminder.setText(text);
        reminder.setScheduledAt(scheduledAt);
        reminder.setStatus(CustomReminderStatus.SCHEDULED);
        return repository.save(reminder);
    }

    @Transactional
    public void sendDueReminders() {
        Instant secondStart = reminderClock.instant().truncatedTo(ChronoUnit.SECONDS);
        for (CustomReminder missed : repository.findAllByStatusAndScheduledAtBefore(
                CustomReminderStatus.SCHEDULED,
                secondStart
        )) {
            missed.setStatus(CustomReminderStatus.MISSED);
            repository.save(missed);
        }

        Instant secondEnd = secondStart.plus(1, ChronoUnit.SECONDS);
        for (CustomReminder reminder : repository
                .findAllByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                CustomReminderStatus.SCHEDULED,
                secondStart,
                secondEnd
        )) {
            reminder.setStatus(CustomReminderStatus.SENT);
            repository.save(reminder);
            sendCard(reminder, 0, "Напоминание:\n\n");
        }
    }

    @Transactional(readOnly = true)
    public void sendList(int requestedPage) {
        sendListContent(requestedPage);
    }

    private void sendListContent(int requestedPage) {
        List<CustomReminder> reminders = repository.findAllByStatusInOrderByScheduledAtAsc(
                List.of(CustomReminderStatus.SCHEDULED)
        );
        if (reminders.isEmpty()) {
            mainMenuService.show("Активных напоминаний нет.");
            return;
        }

        int pageCount = (reminders.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int page = Math.clamp(requestedPage, 0, pageCount - 1);
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, reminders.size());
        List<CustomReminder> visible = reminders.subList(from, to);

        StringBuilder text = new StringBuilder("Мои напоминания, ")
                .append(from + 1).append('-').append(to).append(" из ").append(reminders.size()).append(":\n\n");
        for (int i = 0; i < visible.size(); i++) {
            text.append(from + i + 1).append(". ")
                    .append(shortDateTime(visible.get(i).getScheduledAt())).append(" - ")
                    .append(shorten(visible.get(i).getText())).append('\n');
        }

        List<String> labels = new ArrayList<>();
        List<Object> payloads = new ArrayList<>();
        List<EnumParam<String>> types = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            labels.add(String.valueOf(from + i + 1));
            payloads.add(payload(CustomReminderAction.OPEN, visible.get(i).getId(), page));
            types.add(TEXT);
        }
        List<Integer> rows = new ArrayList<>();
        rows.add(visible.size());
        int navigationButtons = 0;
        if (page > 0) {
            labels.add("Назад");
            payloads.add(payload(CustomReminderAction.LIST, null, page - 1));
            types.add(TEXT);
            navigationButtons++;
        }
        if (page + 1 < pageCount) {
            labels.add("Вперед");
            payloads.add(payload(CustomReminderAction.LIST, null, page + 1));
            types.add(TEXT);
            navigationButtons++;
        }
        labels.add("Закрыть");
        payloads.add(payload(CustomReminderAction.CLOSE, null, page));
        types.add(TEXT);
        navigationButtons++;
        rows.add(navigationButtons);
        messageSender.sendMessage(text.toString().stripTrailing(), types, labels, payloads, rows, true);
    }

    @Transactional
    public void handle(CustomReminderActionDto dto) {
        if (dto == null || dto.action() == null) {
            return;
        }
        if (dto.action() == CustomReminderAction.LIST) {
            sendListContent(dto.page());
            return;
        }
        if (dto.action() == CustomReminderAction.CLOSE) {
            messageSender.sendMessage("Список напоминаний закрыт.");
            return;
        }
        CustomReminder reminder = dto.reminderId() == null ? null : repository.findById(dto.reminderId()).orElse(null);
        if (reminder == null || reminder.getStatus() == CustomReminderStatus.DELETED
                || reminder.getStatus() == CustomReminderStatus.DONE
                || reminder.getStatus() == CustomReminderStatus.MISSED) {
            messageSender.sendMessage("Напоминание уже недоступно.");
            return;
        }
        switch (dto.action()) {
            case OPEN -> sendCard(reminder, dto.page(), "");
            case DONE -> updateStatus(reminder, CustomReminderStatus.DONE, "Напоминание выполнено.");
            case DELETE_REQUEST -> sendDeleteConfirmation(reminder, dto.page());
            case DELETE_CONFIRM -> updateStatus(reminder, CustomReminderStatus.DELETED, "Напоминание удалено.");
            case POSTPONE_TEN_MINUTES -> postpone(reminder, Duration.ofMinutes(10), dto.page());
            case POSTPONE_ONE_HOUR -> postpone(reminder, Duration.ofHours(1), dto.page());
            case POSTPONE_THREE_HOURS -> postpone(reminder, Duration.ofHours(3), dto.page());
            case LIST, CLOSE -> throw new IllegalStateException("Action must be handled before reminder lookup");
        }
    }

    private void postpone(CustomReminder reminder, Duration duration, int page) {
        reminder.setScheduledAt(reminderClock.instant().plus(duration).truncatedTo(ChronoUnit.SECONDS));
        reminder.setStatus(CustomReminderStatus.SCHEDULED);
        repository.save(reminder);
        sendCard(reminder, page, "Напоминание отложено.\n\n");
    }

    private void updateStatus(CustomReminder reminder, CustomReminderStatus status, String message) {
        reminder.setStatus(status);
        repository.save(reminder);
        messageSender.sendMessage(message);
    }

    private void sendCard(CustomReminder reminder, int page, String prefix) {
        String text = prefix + reminder.getText() + "\n\nВремя: "
                + FULL_TIME.format(reminder.getScheduledAt().atZone(reminderClock.getZone()))
                + "\nСтатус: " + statusLabel(reminder.getStatus()) + ".";
        List<String> labels = List.of("Выполнено", "10 минут", "1 час", "3 часа", "Удалить", "К списку");
        List<Object> payloads = List.of(
                payload(CustomReminderAction.DONE, reminder.getId(), page),
                payload(CustomReminderAction.POSTPONE_TEN_MINUTES, reminder.getId(), page),
                payload(CustomReminderAction.POSTPONE_ONE_HOUR, reminder.getId(), page),
                payload(CustomReminderAction.POSTPONE_THREE_HOURS, reminder.getId(), page),
                payload(CustomReminderAction.DELETE_REQUEST, reminder.getId(), page),
                payload(CustomReminderAction.LIST, null, page)
        );
        messageSender.sendMessage(
                text,
                List.of(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT),
                labels,
                payloads,
                List.of(1, 4, 1),
                true
        );
    }

    private void sendDeleteConfirmation(CustomReminder reminder, int page) {
        messageSender.sendMessage(
                "Удалить напоминание?\n\n" + reminder.getText(),
                List.of(TEXT, TEXT),
                List.of("Удалить", "К списку"),
                List.of(
                        payload(CustomReminderAction.DELETE_CONFIRM, reminder.getId(), page),
                        payload(CustomReminderAction.LIST, null, page)
                ),
                List.of(2),
                true
        );
    }

    private CallbackPayloadEnvelope payload(CustomReminderAction action, Long reminderId, int page) {
        return new CallbackPayloadEnvelope(
                CallbackPayloadType.CUSTOM_REMINDER_ACTION.value(),
                PAYLOAD_VERSION,
                new CustomReminderActionDto(action, reminderId, page)
        );
    }

    private String shortDateTime(Instant instant) {
        ZonedDateTime time = instant.atZone(reminderClock.getZone());
        LocalDate today = LocalDate.now(reminderClock);
        if (time.toLocalDate().equals(today)) {
            return "Сегодня, " + SHORT_TIME.format(time);
        }
        if (time.toLocalDate().equals(today.plusDays(1))) {
            return "Завтра, " + SHORT_TIME.format(time);
        }
        return DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")).format(time);
    }

    private String shorten(String text) {
        return text.length() <= 60 ? text : text.substring(0, 57) + "...";
    }

    private String statusLabel(CustomReminderStatus status) {
        return switch (status) {
            case SCHEDULED -> "ожидает";
            case SENT -> "отправлено";
            case DONE -> "выполнено";
            case DELETED -> "удалено";
            case MISSED -> "пропущено";
        };
    }
}
