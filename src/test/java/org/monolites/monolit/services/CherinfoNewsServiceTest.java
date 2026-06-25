package org.monolites.monolit.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;
import org.monolites.monolit.models.entities.CherinfoNewsState;
import org.monolites.monolit.models.exception.SendMessageException;
import org.monolites.monolit.repositories.CherinfoNewsStateRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CherinfoNewsServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-24T08:00:00Z");

    @Mock
    private NewsSourceClient newsClient;
    @Mock
    private CherinfoNewsImageDownloader imageDownloader;
    @Mock
    private CherinfoNewsStateRepository newsStateRepository;
    @Mock
    private VkMessageSenderService messageSender;

    private CherinfoNewsService service;

    @BeforeEach
    void setUp() {
        service = new CherinfoNewsService(
                newsClient,
                imageDownloader,
                newsStateRepository,
                messageSender,
                Clock.fixed(NOW, ZoneId.of("Europe/Moscow"))
        );
    }

    @Test
    void sendsLastFiveNewsOnEmptyStorageAndStoresThem() throws Exception {
        List<NewsItem> fetchedNews = news(6);
        when(newsClient.fetchLatestNews()).thenReturn(fetchedNews);
        for (NewsItem news : fetchedNews.subList(0, 5)) {
            when(newsClient.fetchNewsDetails(news.url())).thenReturn(detailsWithoutImages(news));
        }
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CherinfoNewsState> stateCaptor = ArgumentCaptor.forClass(CherinfoNewsState.class);

        service.publishLatestNews();

        verify(messageSender, times(5)).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(CherinfoNewsServiceTest::messageTitle)
                .containsExactly("Новость 5", "Новость 4", "Новость 3", "Новость 2", "Новость 1");
        assertThat(messageCaptor.getAllValues())
                .allSatisfy(message -> {
                    assertThat(message).doesNotContain("https://cherinfo.ru/news/");
                    assertThat(message).contains("Полный текст новости");
                });
        verify(newsStateRepository, times(5)).save(stateCaptor.capture());
        assertThat(sentUrls(stateCaptor.getValue()))
                .containsExactly(
                        "https://cherinfo.ru/news/1-news",
                        "https://cherinfo.ru/news/2-news",
                        "https://cherinfo.ru/news/3-news",
                        "https://cherinfo.ru/news/4-news",
                        "https://cherinfo.ru/news/5-news"
                );
        assertThat(stateCaptor.getValue().getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void storesOnlyRecentSentNewsUrlsInSingleStateRow() throws Exception {
        List<NewsItem> fetchedNews = news(55);
        when(newsClient.fetchLatestNews()).thenReturn(fetchedNews);
        for (NewsItem news : fetchedNews) {
            when(newsClient.fetchNewsDetails(news.url())).thenReturn(detailsWithoutImages(news));
        }
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.of(state("https://cherinfo.ru/news/old-news")));
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        ArgumentCaptor<CherinfoNewsState> stateCaptor = ArgumentCaptor.forClass(CherinfoNewsState.class);

        service.publishLatestNews();

        verify(messageSender, times(55)).sendMessage(anyString());
        verify(newsStateRepository, times(55)).save(stateCaptor.capture());
        assertThat(sentUrls(stateCaptor.getValue()))
                .hasSize(50)
                .containsExactlyElementsOf(newsUrls(1, 50));
    }

    @Test
    void sendsOnlyFiveNewsWhenStartupAndHourlyChecksOverlap() throws Exception {
        List<NewsItem> fetchedNews = news(10);
        AtomicReference<CherinfoNewsState> storedState = new AtomicReference<>();
        when(newsClient.fetchLatestNews()).thenReturn(fetchedNews);
        for (NewsItem news : fetchedNews.subList(0, 5)) {
            when(newsClient.fetchNewsDetails(news.url())).thenReturn(detailsWithoutImages(news));
        }
        when(newsStateRepository.findById("cherinfo")).thenAnswer(invocation -> Optional.ofNullable(storedState.get()));
        when(newsStateRepository.save(any())).thenAnswer(invocation -> {
            CherinfoNewsState state = invocation.getArgument(0);
            storedState.set(state);
            return state;
        });
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());

        service.publishLatestNews();
        service.publishLatestNews();

        verify(messageSender, times(5)).sendMessage(anyString());
        verify(newsStateRepository, times(5)).save(any());
        assertThat(sentUrls(storedState.get()))
                .containsExactly(
                        "https://cherinfo.ru/news/1-news",
                        "https://cherinfo.ru/news/2-news",
                        "https://cherinfo.ru/news/3-news",
                        "https://cherinfo.ru/news/4-news",
                        "https://cherinfo.ru/news/5-news"
                );
    }

    @Test
    void doesNotSendDuplicatesAfterInitialPublication() throws Exception {
        List<NewsItem> fetchedNews = news(3);
        AtomicReference<CherinfoNewsState> storedState = new AtomicReference<>();
        when(newsClient.fetchLatestNews()).thenReturn(fetchedNews);
        for (NewsItem news : fetchedNews) {
            when(newsClient.fetchNewsDetails(news.url())).thenReturn(detailsWithoutImages(news));
        }
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        when(newsStateRepository.findById("cherinfo")).thenAnswer(invocation -> Optional.ofNullable(storedState.get()));
        when(newsStateRepository.save(any())).thenAnswer(invocation -> {
            CherinfoNewsState state = invocation.getArgument(0);
            storedState.set(state);
            return state;
        });

        service.publishLatestNews();
        service.publishLatestNews();

        verify(messageSender, times(3)).sendMessage(anyString());
        verify(newsStateRepository, times(3)).save(any());
    }

    @Test
    void sendsOnlyNewNewsOnNonEmptyStorage() throws Exception {
        NewsItem newest = newsItem(3);
        NewsItem middle = newsItem(2);
        NewsItem old = newsItem(1);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(newest, middle, old));
        when(newsClient.fetchNewsDetails(newest.url())).thenReturn(detailsWithoutImages(newest));
        when(newsClient.fetchNewsDetails(middle.url())).thenReturn(detailsWithoutImages(middle));
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.of(state(old.url())));
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.publishLatestNews();

        verify(messageSender, atLeast(2)).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .extracting(CherinfoNewsServiceTest::messageTitle)
                .containsExactly("Новость 2", "Новость 3");
        verify(newsStateRepository, times(2)).save(any());
    }

    @Test
    void skipsStateChangesWhenFetchFails() throws Exception {
        when(newsClient.fetchLatestNews()).thenThrow(new IOException("unavailable"));

        service.publishLatestNews();

        verify(newsStateRepository, never()).findById(anyString());
        verify(newsStateRepository, never()).save(any());
        verify(messageSender, never()).sendMessage(anyString());
    }

    @Test
    void restoresInterruptFlagWhenNewsFetchIsInterrupted() throws Exception {
        when(newsClient.fetchLatestNews()).thenThrow(new InterruptedException("interrupted"));

        try {
            service.publishLatestNews();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(newsStateRepository, never()).findById(anyString());
            verify(newsStateRepository, never()).save(any());
            verify(messageSender, never()).sendMessage(anyString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void stopsBatchWithoutStateChangeWhenDetailsFetchIsInterrupted() throws Exception {
        NewsItem news = newsItem(1);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenThrow(new InterruptedException("interrupted"));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());

        try {
            service.publishLatestNews();

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(newsStateRepository, never()).save(any());
            verify(messageSender, never()).sendMessage(anyString());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void treatsNullDownloadedImagesAsNoAttachments() throws Exception {
        NewsItem news = newsItem(1);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(new NewsDetails("Полный текст новости", null));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(null);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.publishLatestNews();

        verify(messageSender).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Полный текст новости");
        verify(newsStateRepository).save(any());
    }

    @Test
    void doesNotStoreNewsWhenTextMessageSendingFails() throws Exception {
        NewsItem news = newsItem(1);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(detailsWithoutImages(news));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        doThrow(new IllegalStateException("VK is down")).when(messageSender).sendMessage(anyString());

        service.publishLatestNews();

        verify(newsStateRepository, never()).save(any());
    }

    @Test
    void usesFallbackPublishedAtWhenNewsDateIsBlank() throws Exception {
        NewsItem news = new NewsItem("Новость без даты", "https://cherinfo.ru/news/no-date", " ");
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(new NewsDetails("Полный текст новости", List.of()));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.publishLatestNews();

        verify(messageSender).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Дата не указана");
        verify(newsStateRepository).save(any());
    }

    @Test
    void sendsFullNewsWithUpToTenImagesAndDeletesTemporaryFiles(@TempDir Path tempDir) throws Exception {
        NewsItem news = newsItem(1);
        List<String> imageUrls = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> "https://cherinfo.ru/upload/news/" + index + ".jpg")
                .toList();
        List<File> imageFiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Path imageFile = tempDir.resolve("image-" + i + ".jpg");
            Files.writeString(imageFile, "image-" + i);
            imageFiles.add(imageFile.toFile());
        }
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(new NewsDetails(
                "Полный текст новости без ссылки.",
                imageUrls
        ));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(imageFiles);
        ArgumentCaptor<List<String>> requestedImages = listCaptor();
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, File>> imagesCaptor = mapCaptor();

        service.publishLatestNews();

        verify(imageDownloader).downloadImages(requestedImages.capture());
        assertThat(requestedImages.getValue()).hasSize(10);
        verify(messageSender).sendMessage(messageCaptor.capture(), imagesCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("Новость 1")
                .contains("Полный текст новости без ссылки.")
                .doesNotContain(news.url());
        assertThat(imagesCaptor.getValue()).hasSize(10);
        assertThat(imageFiles).allSatisfy(file -> assertThat(file).doesNotExist());
        verify(newsStateRepository).save(any());
    }

    @Test
    void sendsFullNewsWithoutImagesWhenImageDownloadFails() throws Exception {
        NewsItem news = newsItem(1);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(details(news));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.publishLatestNews();

        verify(messageSender).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("Полный текст новости 1")
                .doesNotContain(news.url());
        verify(messageSender, never()).sendMessage(anyString(), anyMap());
        verify(newsStateRepository).save(any());
    }

    @Test
    void fallsBackToTextMessageWhenVkRejectsImageAttachments(@TempDir Path tempDir) throws Exception {
        NewsItem news = newsItem(1);
        Path imageFile = tempDir.resolve("image.jpg");
        Files.writeString(imageFile, "image");
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(details(news));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of(imageFile.toFile()));
        doThrow(new SendMessageException("photo is undefined"))
                .when(messageSender).sendMessage(anyString(), anyMap());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.publishLatestNews();

        verify(messageSender).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .contains("Полный текст новости 1")
                .doesNotContain(news.url());
        assertThat(imageFile).doesNotExist();
        verify(newsStateRepository).save(any());
    }

    @Test
    void skipsNewsWithoutFullTextAndDoesNotStoreIt() throws Exception {
        NewsItem news = newsItem(1);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(new NewsDetails("", List.of()));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());

        service.publishLatestNews();

        verify(messageSender, never()).sendMessage(anyString());
        verify(messageSender, never()).sendMessage(anyString(), anyMap());
        verify(newsStateRepository, never()).save(any());
    }

    @Test
    void splitsLongFullNewsWithoutAddingUrl() throws Exception {
        NewsItem news = newsItem(1);
        String longText = "Абзац ".repeat(700);
        when(newsClient.fetchLatestNews()).thenReturn(List.of(news));
        when(newsClient.fetchNewsDetails(news.url())).thenReturn(new NewsDetails(longText, List.of()));
        when(newsStateRepository.findById("cherinfo")).thenReturn(Optional.empty());
        when(imageDownloader.downloadImages(anyList())).thenReturn(List.of());
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        service.publishLatestNews();

        verify(messageSender, atLeast(2)).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getAllValues())
                .allSatisfy(message -> {
                    assertThat(message).hasSizeLessThanOrEqualTo(3_900);
                    assertThat(message).doesNotContain(news.url());
                });
        verify(newsStateRepository).save(any());
    }

    @Test
    void skipsStateChangesWhenNoNewsParsed() throws Exception {
        when(newsClient.fetchLatestNews()).thenReturn(List.of());

        service.publishLatestNews();

        verify(newsStateRepository, never()).findById(anyString());
        verify(newsStateRepository, never()).save(any());
        verify(messageSender, never()).sendMessage(anyString());
    }

    private static List<NewsItem> news(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(CherinfoNewsServiceTest::newsItem)
                .toList();
    }

    private static NewsItem newsItem(int id) {
        return new NewsItem(
                "Новость " + id,
                "https://cherinfo.ru/news/" + id + "-news",
                "Сегодня 0" + id + ":00"
        );
    }

    private static NewsDetails details(NewsItem news) {
        return new NewsDetails(
                "Полный текст новости " + news.title().replace("Новость ", ""),
                List.of("https://cherinfo.ru/upload/news/image.jpg")
        );
    }

    private static NewsDetails detailsWithoutImages(NewsItem news) {
        return new NewsDetails(
                "Полный текст новости " + news.title().replace("Новость ", ""),
                List.of()
        );
    }

    private static String messageTitle(String message) {
        return message.lines()
                .filter(line -> line.startsWith("Новость ") && !"Новость Cherinfo".equals(line))
                .findFirst()
                .orElseThrow();
    }

    private static CherinfoNewsState state(String... urls) {
        CherinfoNewsState state = new CherinfoNewsState();
        state.setStateKey("cherinfo");
        state.setLatestNewsUrl(urls.length == 0 ? "" : urls[0]);
        state.setSentUrls(String.join("\n", urls));
        state.setUpdatedAt(NOW);
        return state;
    }

    private static List<String> sentUrls(CherinfoNewsState state) {
        return state.getSentUrls().lines().toList();
    }

    private static List<String> newsUrls(int firstId, int lastId) {
        return java.util.stream.IntStream.rangeClosed(firstId, lastId)
                .mapToObj(id -> "https://cherinfo.ru/news/" + id + "-news")
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<String>> listCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, File>> mapCaptor() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
