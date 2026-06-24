package org.monolites.monolit.services;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CherinfoNewsParserTest {

    private static final String IMAGE_BIG_PATH = "/image_big/";
    private static final String IMAGE_MEDIUM_PATH = "/image_medium/";
    private static final String IMAGE_SMALL_PATH = "/image_small/";

    private final CherinfoNewsParser parser = parser();

    @Test
    void parsesNewsItemsFromRss() {
        String rss = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                    <channel>
                        <item>
                            <title>Первая &amp; важная новость</title>
                            <link>https://cherinfo.ru/news/101-first-news</link>
                            <pubDate>Wed, 24 Jun 2026 18:30:00 +0300</pubDate>
                        </item>
                        <item>
                            <title>Вторая новость</title>
                            <link>/news/102-second-news</link>
                            <pubDate>Wed, 24 Jun 2026 17:30:00 +0300</pubDate>
                        </item>
                        <item>
                            <title>Без ссылки</title>
                        </item>
                    </channel>
                </rss>
                """;

        List<NewsItem> news = parser.parseRss(rss, URI.create("https://cherinfo.ru/rss/news/"));

        assertThat(news).containsExactly(
                new NewsItem(
                        "Первая & важная новость",
                        "https://cherinfo.ru/news/101-first-news",
                        "24.06.2026 18:30"
                ),
                new NewsItem(
                        "Вторая новость",
                        "https://cherinfo.ru/news/102-second-news",
                        "24.06.2026 17:30"
                )
        );
    }

    @Test
    void returnsEmptyListForBlankRss() {
        assertThat(parser.parseRss("", URI.create("https://cherinfo.ru/rss/news/"))).isEmpty();
    }

    @Test
    void parsesFullNewsTextAndImagesFromArticle() {
        String html = """
                <html>
                <head>
                    <meta property="og:image" content="https://st.cherinfo.ru/med/101.jpg">
                </head>
                <body>
                <header><img src="/static/logo.png"></header>
                <article>
                    <h1>Заголовок</h1>
                    <div class="js-mediator-article article-text">
                    <img src="/upload/news/first.jpg">
                    <p>Первый абзац с <a href="/news/1">внутренней ссылкой</a>.</p>
                    <img data-src="https://cherinfo.ru/upload/news/second.webp?size=large">
                    <img src="/upload/banner.png">
                    <p>Второй&nbsp;абзац.</p>
                    </div>
                    <div class="main-new-small-img">
                        <img src="https://st.cherinfo.ru/med/related.jpg">
                    </div>
                </article>
                </body>
                </html>
                """;

        NewsDetails details = parser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/101-first-news")
        );

        assertThat(details.text()).isEqualTo("Первый абзац с внутренней ссылкой.\n\nВторой абзац.");
        assertThat(details.imageUrls()).containsExactly(
                "https://cherinfo.ru/upload/news/first.jpg",
                "https://cherinfo.ru/upload/news/second.webp?size=large"
        );
    }

    @Test
    void returnsOnlyArticleImageWhenMetaImageDuplicatesSingleNewsImage() {
        String html = """
                <html>
                <head>
                    <meta property="og:image" content="https://st.cherinfo.ru/med/146922.jpg">
                    <meta itemprop="image" content="https://st.cherinfo.ru/med/146922.jpg">
                </head>
                <body>
                <main>
                    <div class="js-mediator-article article-text">
                        <p>Текст новости.</p>
                        <p><img alt="Новости Черинфо" class="wide img-responsive" src="https://st.cherinfo.ru/pages/2026/06/24/gosuslugi001.jpg"></p>
                    </div>
                </main>
                </body>
                </html>
                """;

        NewsDetails details = parser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/146922-vologodskaa-oblast")
        );

        assertThat(details.imageUrls()).containsExactly(
                "https://st.cherinfo.ru/pages/2026/06/24/gosuslugi001.jpg"
        );
    }

    @Test
    void returnsGalleryImagesInHighQualityWithoutMetaImage() {
        String html = """
                <html>
                <head>
                    <meta property="og:image" content="https://st.cherinfo.ru/med/146919.jpg">
                </head>
                <body>
                <main>
                    <div class="js-mediator-article article-text">
                        <p>Текст новости.</p>
                        <div class="widget print-hidden">
                            <div class="fotorama" data-nav="thumbs">
                                <a href="https://st.cherinfo.ru/image_big/first.jpg">
                                    <img src="https://st.cherinfo.ru/image_medium/first.jpg" class="img-responsive">
                                </a>
                                <a href="/image_big/second.jpg">
                                    <img src="/image_medium/second.jpg" class="img-responsive">
                                </a>
                            </div>
                        </div>
                    </div>
                </main>
                </body>
                </html>
                """;

        NewsDetails details = parser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/146919-v-cerepovce")
        );

        assertThat(details.imageUrls()).containsExactly(
                "https://st.cherinfo.ru/image_big/first.jpg",
                "https://cherinfo.ru/image_big/second.jpg"
        );
    }

    @Test
    void usesConfiguredImageQualityPathsForGalleryImages() {
        CherinfoNewsParser configuredParser = new CherinfoNewsParser(
                "/large/",
                "/middle/",
                "/preview/"
        );
        String html = """
                <html>
                <body>
                <main>
                    <div class="js-mediator-article article-text">
                        <p>Текст новости.</p>
                        <div class="fotorama">
                            <a href="/middle/first.jpg">
                                <img src="/preview/first.jpg">
                            </a>
                        </div>
                    </div>
                </main>
                </body>
                </html>
                """;

        NewsDetails details = configuredParser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/146919-v-cerepovce")
        );

        assertThat(details.imageUrls()).containsExactly("https://cherinfo.ru/large/first.jpg");
    }

    @Test
    void deduplicatesNormalizedImageUrls() {
        String html = """
                <html>
                <body>
                <main>
                    <div class="js-mediator-article article-text">
                        <p>Текст новости.</p>
                        <img src="/upload/news/photo.jpg?size=large&amp;v=1">
                        <img data-src="https://cherinfo.ru/upload/news/photo.jpg?size=small">
                    </div>
                </main>
                </body>
                </html>
                """;

        NewsDetails details = parser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/101-first-news")
        );

        assertThat(details.imageUrls()).containsExactly(
                "https://cherinfo.ru/upload/news/photo.jpg?size=large&v=1"
        );
    }

    @Test
    void parsesFullNewsTextFromCherinfoPageMainContent() {
        String html = """
                <html>
                <head>
                    <meta property="og:image" content="https://st.cherinfo.ru/med/main.jpg">
                </head>
                <body>
                <main>
                    <div class="article-text-rubrik">
                        <span class="text-rubric"><a href="/news">#Новости</a></span>
                        <span class="text-rubric"><a href="/news/vlast">#Власть</a></span>
                    </div>
                    <h1>Георгий Филимонов: «Дефицита бензина в области нет»</h1>
                    <div class="news-stats">Сегодня 13:30 492 0</div>
                    <div class="js-mediator-article article-text">
                        <p>Глава региона провел оперативный штаб по вопросам обеспечения топливом.</p>
                        <p>Ситуация с поставками топлива находится под полным контролем.</p>
                        <div class="author">Василий Корешков</div>
                        <img src="/upload/news/main.jpg">
                    </div>
                    <div class="mistake">Если вы нашли ошибку, выделите ее и нажмите ctrl+enter</div>
                    <h2>Другие материалы</h2>
                    <p>Этот текст не относится к статье.</p>
                    <img src="/upload/news/related.jpg">
                </main>
                </body>
                </html>
                """;

        NewsDetails details = parser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/146906-georgij-filimonov")
        );

        assertThat(details.text()).isEqualTo("""
                Глава региона провел оперативный штаб по вопросам обеспечения топливом.

                Ситуация с поставками топлива находится под полным контролем.""");
        assertThat(details.text())
                .doesNotContain("#Новости")
                .doesNotContain("Сегодня 13:30")
                .doesNotContain("Другие материалы");
        assertThat(details.imageUrls()).containsExactly("https://cherinfo.ru/upload/news/main.jpg");
    }

    @Test
    void returnsBlankDetailsForBlankHtml() {
        NewsDetails details = parser.parseDetails("", URI.create("https://cherinfo.ru/news/101-first-news"));

        assertThat(details.text()).isBlank();
        assertThat(details.imageUrls()).isEmpty();
    }

    private static CherinfoNewsParser parser() {
        return new CherinfoNewsParser(IMAGE_BIG_PATH, IMAGE_MEDIUM_PATH, IMAGE_SMALL_PATH);
    }
}
