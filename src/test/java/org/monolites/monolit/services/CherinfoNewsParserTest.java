package org.monolites.monolit.services;

import org.junit.jupiter.api.Test;
import org.monolites.monolit.models.dtos.CherinfoNewsDetails;
import org.monolites.monolit.models.dtos.CherinfoNewsItem;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CherinfoNewsParserTest {

    private final CherinfoNewsParser parser = new CherinfoNewsParser();

    @Test
    void parsesNewsLinksWithTitlesDatesAndAbsoluteUrls() {
        String html = """
                <a href="/news/100-empty"><img src="/image.jpg"></a>
                <a class="news-item" href="/news/101-first-news">Первая &amp; важная новость</a>
                <span>Сегодня 08:30 46 0</span>
                <a href="/news/102-second-news">Вторая новость</a>
                <div>23.06.2026 17:01 1176 40</div>
                """;

        List<CherinfoNewsItem> news = parser.parse(html, URI.create("https://cherinfo.ru/news"));

        assertThat(news).containsExactly(
                new CherinfoNewsItem(
                        "Первая & важная новость",
                        "https://cherinfo.ru/news/101-first-news",
                        "Сегодня 08:30"
                ),
                new CherinfoNewsItem(
                        "Вторая новость",
                        "https://cherinfo.ru/news/102-second-news",
                        "23.06.2026 17:01"
                )
        );
    }

    @Test
    void ignoresItemsWithoutTextAndDeduplicatesByUrl() {
        String html = """
                <a href="/news/101-first-news"><img alt="Лента новостей"></a>
                <a href="/news/101-first-news">Первая новость</a>
                Сегодня 08:30
                <a href="/news/101-first-news">Дубль первой новости</a>
                Сегодня 08:30
                """;

        List<CherinfoNewsItem> news = parser.parse(html, URI.create("https://cherinfo.ru/news"));

        assertThat(news).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Первая новость");
            assertThat(item.url()).isEqualTo("https://cherinfo.ru/news/101-first-news");
            assertThat(item.publishedAtText()).isEqualTo("Сегодня 08:30");
        });
    }

    @Test
    void returnsEmptyListForBlankHtml() {
        assertThat(parser.parse("", URI.create("https://cherinfo.ru/news"))).isEmpty();
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

        CherinfoNewsDetails details = parser.parseDetails(
                html,
                URI.create("https://cherinfo.ru/news/101-first-news")
        );

        assertThat(details.text()).isEqualTo("Первый абзац с внутренней ссылкой.\n\nВторой абзац.");
        assertThat(details.imageUrls()).containsExactly(
                "https://st.cherinfo.ru/med/101.jpg",
                "https://cherinfo.ru/upload/news/first.jpg",
                "https://cherinfo.ru/upload/news/second.webp?size=large"
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

        CherinfoNewsDetails details = parser.parseDetails(
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
        assertThat(details.imageUrls()).containsExactly(
                "https://st.cherinfo.ru/med/main.jpg",
                "https://cherinfo.ru/upload/news/main.jpg"
        );
    }

    @Test
    void returnsBlankDetailsForBlankHtml() {
        CherinfoNewsDetails details = parser.parseDetails("", URI.create("https://cherinfo.ru/news/101-first-news"));

        assertThat(details.text()).isBlank();
        assertThat(details.imageUrls()).isEmpty();
    }
}
