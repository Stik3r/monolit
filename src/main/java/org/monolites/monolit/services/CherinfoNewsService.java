package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;
import org.monolites.monolit.models.entities.CherinfoNewsState;
import org.monolites.monolit.repositories.CherinfoNewsStateRepository;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CherinfoNewsService {

    private static final String CHERINFO_STATE_KEY = "cherinfo";
    private static final int INITIAL_NEWS_LIMIT = 5;
    private static final int MAX_IMAGE_COUNT = 10;
    private static final int MAX_MESSAGE_LENGTH = 3_900;
    private static final int RECENT_SENT_URL_LIMIT = 50;

    private final NewsSourceClient newsClient;
    private final CherinfoNewsImageDownloader imageDownloader;
    private final CherinfoNewsStateRepository newsStateRepository;
    private final VkMessageSenderService messageSender;
    private final Clock reminderClock;

    public synchronized void publishLatestNews() {
        Optional<List<NewsItem>> fetchedNewsOptional = fetchNews();
        if (fetchedNewsOptional.isEmpty()) {
            return;
        }
        List<NewsItem> fetchedNews = fetchedNewsOptional.get();
        if (fetchedNews.isEmpty()) {
            log.warn("No Cherinfo news items were parsed");
            return;
        }

        CherinfoNewsState state = loadState();
        List<NewsItem> newsToSend = state.getLatestNewsUrl() == null || state.getLatestNewsUrl().isBlank()
                ? fetchedNews.stream().limit(INITIAL_NEWS_LIMIT).toList()
                : newNewsBeforeCheckpoint(fetchedNews, state);
        if (newsToSend.isEmpty()) {
            log.debug("No new Cherinfo news to send");
            return;
        }

        for (NewsItem news : newsToSend.reversed()) {
            if (publishNews(news)) {
                rememberSentUrl(state, news.url());
                newsStateRepository.save(state);
            } else {
                log.warn("Cherinfo news batch stopped at {} to preserve news checkpoint", news.url());
                break;
            }
        }
    }

    private boolean publishNews(NewsItem news) {
        Optional<NewsDetails> detailsOptional = fetchDetails(news);
        if (detailsOptional.isEmpty()) {
            return false;
        }
        NewsDetails details = detailsOptional.get();
        if (details.text() == null || details.text().isBlank()) {
            log.warn("Cherinfo news details do not contain full text for {}", news.url());
            return false;
        }

        List<String> detailImageUrls = details.imageUrls() == null ? List.of() : details.imageUrls();
        List<String> imageUrls = detailImageUrls.stream()
                .limit(MAX_IMAGE_COUNT)
                .toList();
        List<File> imageFiles = imageDownloader.downloadImages(imageUrls);
        if (imageFiles == null) {
            imageFiles = List.of();
        }
        if (!imageUrls.isEmpty() && imageFiles.isEmpty()) {
            log.warn("No Cherinfo news images were downloaded for {}", news.url());
        }
        try {
            sendNewsMessages(formatMessage(news, details), imageFiles);
            return true;
        } catch (Exception e) {
            log.warn("Failed to send Cherinfo news {}", news.url(), e);
            return false;
        } finally {
            deleteTemporaryFiles(imageFiles);
        }
    }

    private Optional<List<NewsItem>> fetchNews() {
        try {
            return Optional.of(newsClient.fetchLatestNews());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Cherinfo news parsing was interrupted", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch Cherinfo news", e);
            return Optional.empty();
        }
    }

    private Optional<NewsDetails> fetchDetails(NewsItem news) {
        try {
            return Optional.of(newsClient.fetchNewsDetails(news.url()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Cherinfo news details parsing was interrupted for {}", news.url(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to fetch Cherinfo news details for {}", news.url(), e);
            return Optional.empty();
        }
    }

    private CherinfoNewsState loadState() {
        return newsStateRepository.findById(CHERINFO_STATE_KEY)
                .orElseGet(() -> {
                    CherinfoNewsState state = new CherinfoNewsState();
                    state.setStateKey(CHERINFO_STATE_KEY);
                    state.setLatestNewsUrl("");
                    state.setSentUrls("");
                    state.setUpdatedAt(reminderClock.instant());
                    return state;
                });
    }

    private static List<NewsItem> newNewsBeforeCheckpoint(
            List<NewsItem> fetchedNews,
            CherinfoNewsState state
    ) {
        List<NewsItem> newsToSend = new ArrayList<>();
        Set<String> sentUrls = sentUrls(state);
        boolean checkpointFound = false;
        for (NewsItem news : fetchedNews) {
            if (news.url().equals(state.getLatestNewsUrl())) {
                checkpointFound = true;
                break;
            }
            if (!sentUrls.contains(news.url())) {
                newsToSend.add(news);
            }
        }
        if (checkpointFound) {
            return newsToSend;
        }
        return fetchedNews.stream()
                .filter(news -> !sentUrls.contains(news.url()))
                .toList();
    }

    private static Set<String> sentUrls(CherinfoNewsState state) {
        Set<String> sentUrls = new LinkedHashSet<>();
        if (state.getSentUrls() == null || state.getSentUrls().isBlank()) {
            return sentUrls;
        }
        for (String url : state.getSentUrls().split("\\R")) {
            String trimmedUrl = url.trim();
            if (!trimmedUrl.isBlank()) {
                sentUrls.add(trimmedUrl);
            }
        }
        return sentUrls;
    }

    private void rememberSentUrl(CherinfoNewsState state, String url) {
        List<String> urls = new ArrayList<>();
        urls.add(url);
        for (String sentUrl : sentUrls(state)) {
            if (!sentUrl.equals(url)) {
                urls.add(sentUrl);
            }
            if (urls.size() >= RECENT_SENT_URL_LIMIT) {
                break;
            }
        }
        state.setSentUrls(String.join("\n", urls));
        state.setLatestNewsUrl(url);
        state.setUpdatedAt(reminderClock.instant());
    }

    private void sendNewsMessages(String message, List<File> imageFiles) {
        List<String> messageParts = splitMessage(message);
        if (messageParts.isEmpty()) {
            return;
        }
        if (imageFiles.isEmpty()) {
            messageSender.sendMessage(messageParts.getFirst());
        } else {
            try {
                messageSender.sendMessage(messageParts.getFirst(), imageMap(imageFiles));
            } catch (RuntimeException e) {
                log.warn("Failed to send Cherinfo news images, sending text without attachments: {}", e.getMessage());
                messageSender.sendMessage(messageParts.getFirst());
            }
        }
        for (int i = 1; i < messageParts.size(); i++) {
            messageSender.sendMessage(messageParts.get(i));
        }
    }

    private static Map<String, File> imageMap(List<File> imageFiles) {
        Map<String, File> images = new LinkedHashMap<>();
        for (int i = 0; i < imageFiles.size(); i++) {
            images.put("image-" + i, imageFiles.get(i));
        }
        return images;
    }

    private static List<String> splitMessage(String message) {
        List<String> parts = new ArrayList<>();
        String remaining = message.strip();
        while (!remaining.isEmpty()) {
            if (remaining.length() <= MAX_MESSAGE_LENGTH) {
                parts.add(remaining);
                break;
            }
            int splitIndex = splitIndex(remaining);
            parts.add(remaining.substring(0, splitIndex).strip());
            remaining = remaining.substring(splitIndex).strip();
        }
        return parts;
    }

    private static int splitIndex(String message) {
        int paragraphBreak = message.lastIndexOf("\n\n", MAX_MESSAGE_LENGTH);
        if (paragraphBreak > 0) {
            return paragraphBreak;
        }
        int lineBreak = message.lastIndexOf('\n', MAX_MESSAGE_LENGTH);
        if (lineBreak > 0) {
            return lineBreak;
        }
        int space = message.lastIndexOf(' ', MAX_MESSAGE_LENGTH);
        return space > 0 ? space : MAX_MESSAGE_LENGTH;
    }

    private static void deleteTemporaryFiles(List<File> imageFiles) {
        for (File imageFile : imageFiles) {
            try {
                Files.deleteIfExists(imageFile.toPath());
            } catch (IOException e) {
                log.warn("Failed to delete Cherinfo temporary image {}", imageFile.getAbsolutePath(), e);
            }
        }
    }

    private static String formatMessage(NewsItem news, NewsDetails details) {
        String publishedAt = news.publishedAtText() == null || news.publishedAtText().isBlank()
                ? "Дата не указана"
                : news.publishedAtText();
        return "Новость Cherinfo%n%n%s%n%s%n%n%s".formatted(news.title(), publishedAt, details.text());
    }
}
