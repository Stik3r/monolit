package org.monolites.monolit.service;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.photos.responses.GetMessagesUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.PhotoUploadResponse;
import com.vk.api.sdk.objects.photos.responses.SaveMessagesPhotoResponse;
import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.model.exception.SendMessageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@Service
public class VKService {

    private static final Random RANDOM = new SecureRandom();

    private final VkApiClient vk;
    private final GroupActor actor;
    private final long userId;

    public VKService(
            @Value("${VK_GROUP_TOKEN}") String accessToken,
            @Value("${VK_GROUP_ID}") String groupId,
            @Value("${VK_MY_ID}") String userId) {
        TransportClient transportClient = new HttpTransportClient();
        vk = new VkApiClient(transportClient);
        actor = new GroupActor(Long.parseLong(groupId.trim()), accessToken.trim());
        this.userId = Long.parseLong(userId.trim());
    }

    public void sendMessage(String message) {
        try {
            vk.messages().sendDeprecated(actor).randomId(RANDOM.nextInt()).userId(userId).message(message).execute();
        } catch (ApiException | ClientException e) {
            log.error("Ошибка отправки сообщения {}", e.getMessage());
        }
    }

    public void sendMessage(String message, Map<String, File> images) {
        try {

            String attachment = uploadImage(images);

            vk.messages()
                    .sendDeprecated(actor)
                    .randomId(RANDOM.nextInt())
                    .userId(userId)
                    .message(message)
                    .attachment(attachment)
                    .execute();

        } catch (ApiException | ClientException e) {
            log.error("Ошибка отправки сообщения {}", e.getMessage());
        }
    }

    private String uploadImage(Map<String, File> images) {
        try {
            List<SaveMessagesPhotoResponse> responses = new ArrayList<>();
            for (Map.Entry<String, File> entry : images.entrySet()) {
                GetMessagesUploadServerResponse serverResponse = vk.photos()
                        .getMessagesUploadServer(actor)
                        .execute();

                PhotoUploadResponse uploadResponse = vk.upload().photo(serverResponse.getUploadUrl().toString(), entry.getValue()).execute();
                List<SaveMessagesPhotoResponse> saveResponse = vk.photos().saveMessagesPhoto(actor)
                        .photo(uploadResponse.getPhoto())
                        .server(uploadResponse.getServer())
                        .hash(uploadResponse.getHash())
                        .execute();
                responses.addAll(saveResponse);

            }

            return makeStringForAttachment(responses);

        } catch (ApiException | ClientException e) {
            log.error("Ошибка загрузки изображения {}", e.getMessage());
            throw new SendMessageException(e.getMessage());
        }
    }

    private String makeStringForAttachment(List<SaveMessagesPhotoResponse> saveResponse) {
        StringBuilder sb = new StringBuilder();
        for (SaveMessagesPhotoResponse response : saveResponse) {
            sb.append(String.format("photo%d_%d,", response.getOwnerId(), response.getId()));
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
