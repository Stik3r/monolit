package org.monolites.monolit.services;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.GroupActor;
import com.vk.api.sdk.events.longpoll.GroupLongPollApi;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.groups.responses.GetLongPollServerResponse;
import com.vk.api.sdk.objects.photos.responses.GetMessagesUploadServerResponse;
import com.vk.api.sdk.objects.photos.responses.PhotoUploadResponse;
import com.vk.api.sdk.objects.photos.responses.SaveMessagesPhotoResponse;
import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.handlers.GroupLongPoolApiHandler;
import org.monolites.monolit.model.exception.LongPoolException;
import org.monolites.monolit.model.exception.SendMessageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.URI;
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
    private final URI poolServer;
    private final String poolKey;
    private String ts;
    private final GroupLongPoolApiHandler groupLongPoolApiHandler;

    public VKService(
            @Value("${VK_GROUP_TOKEN}") String accessToken,
            @Value("${VK_GROUP_ID}") String groupId,
            @Value("${VK_MY_ID}") String userId) {
        TransportClient transportClient = new HttpTransportClient();
        vk = new VkApiClient(transportClient);
        actor = new GroupActor(Long.parseLong(groupId.trim()), accessToken.trim());
        this.userId = Long.parseLong(userId.trim());

        GetLongPollServerResponse response = getLongPoolServer();
        poolServer = response.getServer();
        poolKey = response.getKey();
        ts = response.getTs();

        groupLongPoolApiHandler = new GroupLongPoolApiHandler(vk, actor, 25);

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

    @Async
    public void run() {
        log.info("Запуск прослушивания LongPoll...");
        try {
            // Метод run() у GroupLongPollApi внутри содержит while(true)
            // и сам обновляет ts, используя данные из твоего getLongPollServer
            groupLongPoolApiHandler.run();
        } catch (Exception e) {
            log.error("LongPoll упал, перезапуск через 10 секунд", e);
            try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
            run(); // Рекурсивный перезапуск
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

    private GetLongPollServerResponse getLongPoolServer(){
        try {
            return vk.groups().getLongPollServer(actor).execute();
        }
        catch (ApiException | ClientException ex){
            log.error("Ошибка получения LongPoolServer {}", ex.getMessage(), ex);
            throw new LongPoolException(ex);
        }
    }
}
