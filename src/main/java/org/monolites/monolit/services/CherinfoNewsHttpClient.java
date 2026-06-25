package org.monolites.monolit.services;

import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class CherinfoNewsHttpClient implements CherinfoNewsClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String USER_AGENT = "monolit-vk-bot/1.0";

    private final CherinfoNewsParser parser;
    private final HttpClient httpClient;
    private final URI rssUri;

    public CherinfoNewsHttpClient(
            CherinfoNewsParser parser,
            @Value("${monolit.news.cherinfo.rss-url}") String rssUrl
    ) {
        this.parser = parser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.rssUri = URI.create(rssUrl.trim());
    }

    @Override
    public List<NewsItem> fetchLatestNews() throws IOException, InterruptedException {
        String rss = fetchText(rssUri);
        List<NewsItem> news = parser.parseRss(rss, rssUri);
        log.debug("Parsed {} Cherinfo news items", news.size());
        return news;
    }

    @Override
    public NewsDetails fetchNewsDetails(String newsUrl) throws IOException, InterruptedException {
        URI uri = URI.create(newsUrl.trim());
        return parser.parseDetails(fetchText(uri), uri);
    }

    private String fetchText(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected Cherinfo response status: " + response.statusCode());
        }
        return response.body();
    }
}
