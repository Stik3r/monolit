package org.monolites.monolit.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.objects.messages.Keyboard;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionCallback;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionLocation;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenApp;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenLink;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenPhoto;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionText;
import com.vk.api.sdk.objects.messages.KeyboardButtonActionVkpay;
import com.vk.api.sdk.objects.photos.responses.GetMessagesUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.PhotoUploadResponse;
import com.vk.api.sdk.objects.photos.responses.SaveMessagesPhotoResponse;
import com.vk.api.sdk.queries.EnumParam;
import com.vk.api.sdk.queries.messages.MessagesSendQueryWithDeprecated;
import com.vk.api.sdk.queries.photos.PhotosGetMessagesUploadServerQuery;
import com.vk.api.sdk.queries.photos.PhotosSaveMessagesPhotoQuery;
import com.vk.api.sdk.queries.upload.UploadPhotoQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.vk.api.sdk.objects.messages.KeyboardButtonActionCallbackType.CALLBACK;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionLocationType.LOCATION;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenAppType.OPEN_APP;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenLinkType.OPEN_LINK;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionOpenPhotoType.OPEN_PHOTO;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionTextType.TEXT;
import static com.vk.api.sdk.objects.messages.KeyboardButtonActionVkpayType.VKPAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VkMessageSenderServiceTest {

    @Test
    void sendsPlainMessageToConfiguredUser() throws Exception {
        VkApiClient vk = mock(VkApiClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        GroupActor actor = mock(GroupActor.class);
        MessagesSendQueryWithDeprecated sendQuery = mockSendQuery(vk, actor);
        VkMessageSenderService service = new VkMessageSenderService(vk, actor, new ObjectMapper(), " 1 ");

        service.sendMessage("hello");

        verify(sendQuery).userId(1L);
        verify(sendQuery).message("hello");
        verify(sendQuery).execute();
    }

    @Test
    void sendsKeyboardUsingSingleRowShortcut() throws Exception {
        VkApiClient vk = mock(VkApiClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        GroupActor actor = mock(GroupActor.class);
        MessagesSendQueryWithDeprecated sendQuery = mockSendQuery(vk, actor);
        ArgumentCaptor<Keyboard> keyboardCaptor = ArgumentCaptor.forClass(Keyboard.class);
        VkMessageSenderService service = new VkMessageSenderService(vk, actor, new ObjectMapper(), "1");

        service.sendMessage("choose", List.of(TEXT, TEXT), List.of("one", "two"), List.of("1", "2"), true);

        verify(sendQuery).keyboard(keyboardCaptor.capture());
        assertThat(keyboardCaptor.getValue().getButtons()).singleElement().satisfies(row -> assertThat(row).hasSize(2));
        assertThat(keyboardCaptor.getValue().getInline()).isTrue();
        verify(sendQuery).execute();
    }

    @Test
    void sendsPersistentKeyboardWithEmptyPayloads() throws Exception {
        VkApiClient vk = mock(VkApiClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        GroupActor actor = mock(GroupActor.class);
        MessagesSendQueryWithDeprecated sendQuery = mockSendQuery(vk, actor);
        ArgumentCaptor<Keyboard> keyboardCaptor = ArgumentCaptor.forClass(Keyboard.class);
        VkMessageSenderService service = new VkMessageSenderService(vk, actor, new ObjectMapper(), "1");

        service.sendPersistentKeyboard("menu", List.of("one", "two"), List.of(2));

        verify(sendQuery).keyboard(keyboardCaptor.capture());
        Keyboard keyboard = keyboardCaptor.getValue();
        assertThat(keyboard.getInline()).isFalse();
        assertThat(keyboard.getButtons()).singleElement().satisfies(row -> assertThat(row).hasSize(2));
        KeyboardButtonActionText action = (KeyboardButtonActionText) keyboard.getButtons().getFirst().getFirst().getAction();
        assertThat(action.getPayload()).isNull();
        verify(sendQuery).execute();
    }

    @Test
    void buildsDoneButtonAndFivePostponeButtonsInSeparateRows() throws Exception {
        VkMessageSenderService service = new VkMessageSenderService(
                mock(VkApiClient.class),
                mock(GroupActor.class),
                new ObjectMapper(),
                "1"
        );

        Keyboard keyboard = service.buildKeyboard(
                List.of(TEXT, TEXT, TEXT, TEXT, TEXT, TEXT),
                List.of("Передал", "10 минут", "1 час", "3 часа", "12 часов", "Не напоминать сегодня"),
                List.of("done", "10m", "1h", "3h", "12h", "today"),
                List.of(1, 5),
                true
        );

        assertThat(keyboard.getButtons()).hasSize(2);
        assertThat(keyboard.getButtons().get(0)).hasSize(1);
        assertThat(keyboard.getButtons().get(1)).hasSize(5);
        assertThat(keyboard.getInline()).isTrue();
    }

    @Test
    void buildsAllSupportedVkButtonActionTypes() throws Exception {
        VkMessageSenderService service = service();

        Keyboard keyboard = service.buildKeyboard(
                List.of(VKPAY, LOCATION, OPEN_APP, OPEN_LINK, OPEN_PHOTO, CALLBACK),
                List.of("pay", "location", "app", "link", "photo", "callback"),
                List.of("pay-payload", "location-payload", "app-payload", "link-payload", "photo-payload", "callback-payload"),
                List.of(6),
                true
        );
        List<String> actionTypes = keyboard.getButtons().getFirst().stream()
                .map(button -> button.getAction().getClass().getName())
                .toList();

        assertThat(actionTypes)
                .containsExactly(
                        KeyboardButtonActionVkpay.class.getName(),
                        KeyboardButtonActionLocation.class.getName(),
                        KeyboardButtonActionOpenApp.class.getName(),
                        KeyboardButtonActionOpenLink.class.getName(),
                        KeyboardButtonActionOpenPhoto.class.getName(),
                        KeyboardButtonActionCallback.class.getName()
                );
        assertThat(((KeyboardButtonActionOpenApp) keyboard.getButtons().getFirst().get(2).getAction()).getLabel())
                .isEqualTo("app");
        assertThat(((KeyboardButtonActionOpenLink) keyboard.getButtons().getFirst().get(3).getAction()).getPayload())
                .isEqualTo("\"link-payload\"");
        assertThat(((KeyboardButtonActionCallback) keyboard.getButtons().getFirst().get(5).getAction()).getPayload())
                .isEqualTo("\"callback-payload\"");
    }

    @Test
    void keepsSingleRowContractOfExistingKeyboardMethod() throws Exception {
        VkMessageSenderService service = service();

        Keyboard keyboard = service.buildKeyboard(
                List.of(TEXT, TEXT),
                List.of("one", "two"),
                List.of("one", "two"),
                true
        );

        assertThat(keyboard.getButtons()).singleElement().satisfies(row -> assertThat(row).hasSize(2));
    }

    @Test
    void buildsPersistentKeyboardWithoutButtonPayloads() throws Exception {
        VkMessageSenderService service = service();

        Keyboard keyboard = service.buildKeyboard(
                List.of(TEXT, TEXT),
                List.of("Новое напоминание", "Мои напоминания"),
                java.util.Arrays.asList(null, null),
                List.of(2),
                false
        );

        assertThat(keyboard.getInline()).isFalse();
        assertThat(keyboard.getOneTime()).isFalse();
        KeyboardButtonActionText action =
                (KeyboardButtonActionText) keyboard.getButtons().getFirst().getFirst().getAction();
        assertThat(action.getPayload()).isNull();
    }

    @Test
    void rejectsInconsistentAndNonPositiveRowSizes() {
        VkMessageSenderService service = service();
        List<EnumParam<String>> buttonTypes = List.of(TEXT);
        List<String> labels = List.of("one");
        List<Object> payloads = List.of("one");
        List<Integer> inconsistentRowSizes = List.of(2);
        List<Integer> nonPositiveRowSizes = List.of(0, 1);

        assertThatThrownBy(() -> service.buildKeyboard(
                buttonTypes,
                labels,
                payloads,
                inconsistentRowSizes,
                true
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.buildKeyboard(
                buttonTypes,
                labels,
                payloads,
                nonPositiveRowSizes,
                true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipsRejectedImageUploadsAndSendsRemainingAttachments() throws Exception {
        VkApiClient vk = mock(VkApiClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        GroupActor actor = mock(GroupActor.class);
        PhotosGetMessagesUploadServerQuery uploadServerQuery = mock(PhotosGetMessagesUploadServerQuery.class);
        UploadPhotoQuery firstUploadQuery = mock(UploadPhotoQuery.class);
        UploadPhotoQuery secondUploadQuery = mock(UploadPhotoQuery.class);
        PhotosSaveMessagesPhotoQuery savePhotoQuery = mock(PhotosSaveMessagesPhotoQuery.class);
        MessagesSendQueryWithDeprecated sendQuery = mock(MessagesSendQueryWithDeprecated.class);
        File firstImage = new File("first.jpg");
        File secondImage = new File("second.jpg");
        Map<String, File> images = new LinkedHashMap<>();
        images.put("image-0", firstImage);
        images.put("image-1", secondImage);
        GetMessagesUploadServerResponse uploadServer = new GetMessagesUploadServerResponse();
        uploadServer.setUploadUrl(URI.create("https://upload.vk.test/photo"));
        PhotoUploadResponse rejectedUpload = new PhotoUploadResponse().setPhoto("");
        PhotoUploadResponse acceptedUpload = new PhotoUploadResponse()
                .setPhoto("photo-json")
                .setServer(1)
                .setHash("hash");
        SaveMessagesPhotoResponse savedPhoto = new SaveMessagesPhotoResponse();
        savedPhoto.setOwnerId(10L);
        savedPhoto.setId(20);
        when(vk.photos().getMessagesUploadServer(actor)).thenReturn(uploadServerQuery);
        when(uploadServerQuery.execute()).thenReturn(uploadServer, uploadServer);
        when(vk.upload().photo(anyString(), any(File.class))).thenReturn(firstUploadQuery, secondUploadQuery);
        when(firstUploadQuery.execute()).thenReturn(rejectedUpload);
        when(secondUploadQuery.execute()).thenReturn(acceptedUpload);
        when(vk.photos().saveMessagesPhoto(actor, "photo-json")).thenReturn(savePhotoQuery);
        when(savePhotoQuery.server(1)).thenReturn(savePhotoQuery);
        when(savePhotoQuery.hash("hash")).thenReturn(savePhotoQuery);
        when(savePhotoQuery.execute()).thenReturn(List.of(savedPhoto));
        when(vk.messages().sendDeprecated(actor)).thenReturn(sendQuery);
        when(sendQuery.randomId(any())).thenReturn(sendQuery);
        when(sendQuery.userId(1L)).thenReturn(sendQuery);
        when(sendQuery.message("message")).thenReturn(sendQuery);
        when(sendQuery.attachment("photo10_20")).thenReturn(sendQuery);
        VkMessageSenderService service = new VkMessageSenderService(vk, actor, new ObjectMapper(), "1");

        service.sendMessage("message", images);

        verify(sendQuery).attachment("photo10_20");
        verify(sendQuery).execute();
    }

    @Test
    void fallsBackToTextMessageWhenEveryImageUploadIsRejected() throws Exception {
        VkApiClient vk = mock(VkApiClient.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        GroupActor actor = mock(GroupActor.class);
        PhotosGetMessagesUploadServerQuery uploadServerQuery = mock(PhotosGetMessagesUploadServerQuery.class);
        MessagesSendQueryWithDeprecated sendQuery = mockSendQuery(vk, actor);
        GetMessagesUploadServerResponse uploadServer = new GetMessagesUploadServerResponse();
        uploadServer.setUploadUrl(null);
        when(vk.photos().getMessagesUploadServer(actor)).thenReturn(uploadServerQuery);
        when(uploadServerQuery.execute()).thenReturn(uploadServer);
        VkMessageSenderService service = new VkMessageSenderService(vk, actor, new ObjectMapper(), "1");

        service.sendMessage("message", Map.of("image-0", new File("missing.jpg")));

        verify(sendQuery).message("message");
        verify(sendQuery).execute();
    }

    private MessagesSendQueryWithDeprecated mockSendQuery(VkApiClient vk, GroupActor actor) {
        MessagesSendQueryWithDeprecated sendQuery = mock(MessagesSendQueryWithDeprecated.class);
        when(vk.messages().sendDeprecated(actor)).thenReturn(sendQuery);
        when(sendQuery.randomId(any())).thenReturn(sendQuery);
        when(sendQuery.userId(1L)).thenReturn(sendQuery);
        when(sendQuery.message(anyString())).thenReturn(sendQuery);
        when(sendQuery.keyboard(any())).thenReturn(sendQuery);
        when(sendQuery.attachment(anyString())).thenReturn(sendQuery);
        return sendQuery;
    }

    private VkMessageSenderService service() {
        return new VkMessageSenderService(
                mock(VkApiClient.class),
                mock(GroupActor.class),
                new ObjectMapper(),
                "1"
        );
    }
}
