package org.monolites.monolit.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CherinfoNewsHttpClientTest {

    private static final CherinfoNewsParser PARSER = new CherinfoNewsParser(
            "/image_big/",
            "/image_medium/",
            "/image_small/"
    );

    private HttpServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchLatestNewsParsesRssFromConfiguredUrl() throws Exception {
        respond("/rss", 200, """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                    <channel>
                        <item>
                            <title>Новость RSS</title>
                            <link>/news/1-news</link>
                            <pubDate>Wed, 24 Jun 2026 18:30:00 +0300</pubDate>
                        </item>
                    </channel>
                </rss>
                """);
        server.start();
        CherinfoNewsHttpClient client = new CherinfoNewsHttpClient(PARSER, url("/rss"));

        List<NewsItem> news = client.fetchLatestNews();

        assertThat(news).containsExactly(new NewsItem(
                "Новость RSS",
                url("/news/1-news"),
                "24.06.2026 18:30"
        ));
    }

    @Test
    void fetchNewsDetailsParsesRequestedPage() throws Exception {
        respond("/news/1-news", 200, """
                <html>
                <body>
                <article>
                    <div class="article-text">
                        <p>Полный текст новости.</p>
                        <img src="/upload/news/photo.jpg">
                    </div>
                </article>
                </body>
                </html>
                """);
        server.start();
        CherinfoNewsHttpClient client = new CherinfoNewsHttpClient(PARSER, url("/rss"));

        NewsDetails details = client.fetchNewsDetails(url("/news/1-news"));

        assertThat(details.text()).isEqualTo("Полный текст новости.");
        assertThat(details.imageUrls()).containsExactly(url("/upload/news/photo.jpg"));
    }

    @Test
    void throwsIOExceptionForUnexpectedResponseStatus() {
        respond("/rss", 503, "unavailable");
        server.start();
        CherinfoNewsHttpClient client = new CherinfoNewsHttpClient(PARSER, url("/rss"));

        assertThatThrownBy(client::fetchLatestNews)
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Unexpected Cherinfo response status: 503");
    }

    private void respond(String path, int status, String responseBody) {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
