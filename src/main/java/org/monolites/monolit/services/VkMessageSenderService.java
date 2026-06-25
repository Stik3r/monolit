package org.monolites.monolit.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.messages.*;
import com.vk.api.sdk.objects.photos.responses.GetMessagesUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.PhotoUploadResponse;
import com.vk.api.sdk.objects.photos.responses.SaveMessagesPhotoResponse;
import com.vk.api.sdk.queries.EnumParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.security.SecureRandom;
import java.util.*;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionCallbackType.CALLBACK;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionLocationType.LOCATION;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenAppType.OPEN_APP;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenLinkType.OPEN_LINK;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenPhotoType.OPEN_PHOTO;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionVkpayType.VKPAY;

@Slf4j
@Service
public class VkMessageSenderService {

    private static final Random RANDOM = new SecureRandom();

    private static final String ERROR = "Ошибка отправки сообщения {}";

    private final VkApiClient vk;
    private final GroupActor actor;
    private final Long userId;
    private final ObjectMapper objectMapper;

    public VkMessageSenderService(
            VkApiClient vk,
            GroupActor actor,
            ObjectMapper objectMapper,
            @Value("${VK_MY_ID}") String userId
    ) {
        this.vk = vk;
        this.actor = actor;
        this.objectMapper = objectMapper;
        this.userId = Long.parseLong(userId.trim());
    }

    public void sendMessage(String message) {
        try {
            vk.messages().sendDeprecated(actor).randomId(RANDOM.nextInt()).userId(userId).message(message).execute();
        } catch (ApiException | ClientException e) {
            log.error(ERROR, e.getMessage());
        }
    }

    public void sendMessage(String message, List<EnumParam<String>> params, List<String> labels, List<Object> payloads, boolean inline) {
        sendMessage(message, params, labels, payloads, List.of(params.size()), inline);
    }

    public void sendPersistentKeyboard(String message, List<String> labels, List<Integer> rowSizes) {
        List<Object> emptyPayloads = new ArrayList<>(Collections.nCopies(labels.size(), null));
        sendMessage(
                message,
                Collections.nCopies(labels.size(), com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT),
                labels,
                emptyPayloads,
                rowSizes,
                false
        );
    }

    public void sendMessage(
            String message,
            List<EnumParam<String>> params,
            List<String> labels,
            List<Object> payloads,
            List<Integer> rowSizes,
            boolean inline
    ) {
        try {
            vk.messages().sendDeprecated(actor)
                    .randomId(RANDOM.nextInt())
                    .userId(userId)
                    .message(message)
                    .keyboard(buildKeyboard(params, labels, payloads, rowSizes, inline))
                    .execute();
        } catch (ApiException | ClientException | JsonProcessingException e) {
            log.error(ERROR, e.getMessage());
        }
    }

    public void sendMessage(String message, Map<String, File> images) {
        String attachment = uploadImage(images);
        if (attachment.isBlank()) {
            sendMessage(message);
            return;
        }

        try {
            vk.messages()
                    .sendDeprecated(actor)
                    .randomId(RANDOM.nextInt())
                    .userId(userId)
                    .message(message)
                    .attachment(attachment)
                    .execute();

        } catch (ApiException | ClientException e) {
            log.error(ERROR, e.getMessage());
            sendMessage(message);
        }
    }

    public Keyboard buildKeyboard(List<EnumParam<String>> params, List<String> labels, List<Object> payloads, boolean inline) throws JsonProcessingException {
        return buildKeyboard(params, labels, payloads, List.of(params.size()), inline);
    }

    public Keyboard buildKeyboard(
            List<EnumParam<String>> params,
            List<String> labels,
            List<Object> payloads,
            List<Integer> rowSizes,
            boolean inline
    ) throws JsonProcessingException {
        validateKeyboardArguments(params, labels, payloads, rowSizes);

        List<List<KeyboardButton>> rows = new ArrayList<>();
        List<KeyboardButton> row = new ArrayList<>();
        int rowIndex = 0;
        for (int i = 0; i < params.size(); i++) {
            KeyboardButton button = new KeyboardButton();
            String payload = payloads.get(i) == null ? null : objectMapper.writeValueAsString(payloads.get(i));
            button.setAction(buildAction(params.get(i), labels.get(i), payload));
            row.add(button);
            if (row.size() == rowSizes.get(rowIndex)) {
                rows.add(row);
                row = new ArrayList<>();
                rowIndex++;
            }
        }
        Keyboard keyboard = new Keyboard();
        keyboard.setButtons(rows);
        keyboard.setInline(inline);
        keyboard.setOneTime(false);
        return keyboard;
    }

    private void validateKeyboardArguments(
            List<EnumParam<String>> params,
            List<String> labels,
            List<Object> payloads,
            List<Integer> rowSizes
    ) {
        int buttonCount = params.size();
        int configuredButtonCount = rowSizes.stream().mapToInt(Integer::intValue).sum();
        if (buttonCount != labels.size() || buttonCount != payloads.size() || buttonCount != configuredButtonCount) {
            throw new IllegalArgumentException("Keyboard parameters, labels, payloads and row sizes must describe the same buttons");
        }
        if (rowSizes.stream().anyMatch(size -> size <= 0)) {
            throw new IllegalArgumentException("Keyboard row size must be positive");
        }
    }

    private String uploadImage(Map<String, File> images) {
        List<SaveMessagesPhotoResponse> responses = new ArrayList<>();
        for (Map.Entry<String, File> entry : images.entrySet()) {
            responses.addAll(uploadMessageImage(entry.getValue()));
        }
        return makeStringForAttachment(responses);
    }

    private List<SaveMessagesPhotoResponse> uploadMessageImage(File image) {
        try {
            GetMessagesUploadServerResponse serverResponse = vk.photos()
                    .getMessagesUploadServer(actor)
                    .execute();
            if (serverResponse.getUploadUrl() == null) {
                log.warn("VK image upload server response does not contain upload URL for {}", image.getName());
                return List.of();
            }

            PhotoUploadResponse uploadResponse = vk.upload().photo(serverResponse.getUploadUrl().toString(), image).execute();
            if (uploadResponse.getPhoto() == null || uploadResponse.getPhoto().isBlank()) {
                log.warn("VK image upload response does not contain photo for {}", image.getName());
                return List.of();
            }
            List<SaveMessagesPhotoResponse> saveResponse = vk.photos()
                    .saveMessagesPhoto(actor, uploadResponse.getPhoto())
                    .server(uploadResponse.getServer())
                    .hash(uploadResponse.getHash())
                    .execute();
            if (saveResponse == null || saveResponse.isEmpty()) {
                log.warn("VK did not save uploaded image {}", image.getName());
                return List.of();
            }
            return saveResponse;
        } catch (ApiException | ClientException e) {
            log.warn("Failed to upload VK message image {}: {}", image.getName(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Unexpected VK image upload failure for {}: {}", image.getName(), e.getMessage());
        }
        return List.of();
    }

    private String makeStringForAttachment(List<SaveMessagesPhotoResponse> saveResponse) {
        if (saveResponse.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (SaveMessagesPhotoResponse response : saveResponse) {
            sb.append(String.format("photo%d_%d,", response.getOwnerId(), response.getId()));
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private KeyboardButtonPropertyAction buildAction(EnumParam<String> param, String label, String payload) {
        switch (param) {
            case VKPAY:
                KeyboardButtonActionVkpay actionVkpay = new KeyboardButtonActionVkpay();
                actionVkpay.setType(KeyboardButtonActionVkpayType.VKPAY);
                actionVkpay.setPayload(payload);
                return actionVkpay;
            case LOCATION:
                KeyboardButtonActionLocation actionLocation = new KeyboardButtonActionLocation();
                actionLocation.setType(KeyboardButtonActionLocationType.LOCATION);
                actionLocation.setPayload(payload);
                return actionLocation;
            case OPEN_APP:
                KeyboardButtonActionOpenApp actionOpenApp = new KeyboardButtonActionOpenApp();
                actionOpenApp.setType(KeyboardButtonActionOpenAppType.OPEN_APP);
                actionOpenApp.setLabel(label);
                actionOpenApp.setPayload(payload);
                return actionOpenApp;
            case OPEN_LINK:
                KeyboardButtonActionOpenLink actionOpenLink = new KeyboardButtonActionOpenLink();
                actionOpenLink.setType(KeyboardButtonActionOpenLinkType.OPEN_LINK);
                actionOpenLink.setLabel(label);
                actionOpenLink.setPayload(payload);
                return actionOpenLink;
            case OPEN_PHOTO:
                KeyboardButtonActionOpenPhoto actionOpenPhoto = new KeyboardButtonActionOpenPhoto();
                actionOpenPhoto.setType(KeyboardButtonActionOpenPhotoType.OPEN_PHOTO);
                return actionOpenPhoto;
            case CALLBACK:
                KeyboardButtonActionCallback actionCallback = new KeyboardButtonActionCallback();
                actionCallback.setType(KeyboardButtonActionCallbackType.CALLBACK);
                actionCallback.setLabel(label);
                actionCallback.setPayload(payload);
                return actionCallback;
            default:
                KeyboardButtonActionText actionText = new KeyboardButtonActionText();
                actionText.setType(KeyboardButtonActionTextType.TEXT);
                actionText.setLabel(label);
                if (payload != null) {
                    actionText.setPayload(payload);
                }
                return actionText;
        }
    }
}
