package org.monolites.monolit.services;

import org.monolites.monolit.models.dtos.CherinfoNewsDetails;
import org.monolites.monolit.models.dtos.CherinfoNewsItem;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CherinfoNewsParser {

    private static final Pattern NEWS_LINK_PATTERN = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*([\"'])((?:https?://(?:www\\.)?cherinfo\\.ru)?/news/\\d+[^\"']*)\\1[^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern PUBLISHED_AT_PATTERN = Pattern.compile(
            "((?:Сегодня|Вчера|\\d{2}\\.\\d{2}\\.\\d{4})\\s+\\d{1,2}:\\d{2})"
    );
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "<article\\b[^>]*>(.*?)</article>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern MAIN_PATTERN = Pattern.compile(
            "<main\\b[^>]*>(.*?)</main>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ARTICLE_TEXT_PATTERN = Pattern.compile(
            "<div\\b[^>]*class\\s*=\\s*([\"'])(?:article-text(?:\\s[^\"']*)?|[^\"']*\\sarticle-text(?:\\s[^\"']*)?)\\1[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<h1\\b[^>]*>.*?</h1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern META_IMAGE_PATTERN = Pattern.compile(
            "<meta\\b[^>]*(?:property|itemprop)\\s*=\\s*([\"'])(?:og:image|image)\\1[^>]*content\\s*=\\s*([\"'])([^\"']+)\\2[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile(
            "<p\\b[^>]*>(.*?)</p>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "<img\\b[^>]*(?:src|data-src)\\s*=\\s*([\"'])([^\"']+)\\1[^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile(
            "</?(?:p|div|section|article|main|h1|h2|h3|br)\\b[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern NEWS_META_LINE_PATTERN = Pattern.compile(
            "(?:Сегодня|Вчера|\\d{2}\\.\\d{2}\\.\\d{4})\\s+\\d{1,2}:\\d{2}(?:\\s+\\d+){0,2}"
    );

    public List<CherinfoNewsItem> parse(String html, URI baseUri) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Matcher matcher = NEWS_LINK_PATTERN.matcher(html);
        List<Candidate> candidates = new ArrayList<>();
        while (matcher.find()) {
            String title = cleanText(matcher.group(3));
            String url = normalizeUrl(baseUri, matcher.group(2));
            if (!title.isBlank() && !url.isBlank()) {
                candidates.add(new Candidate(title, url, matcher.start(), matcher.end()));
            }
        }

        Map<String, CherinfoNewsItem> result = new LinkedHashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            int nextStart = i + 1 < candidates.size() ? candidates.get(i + 1).linkStart() : html.length();
            String publishedAt = extractPublishedAt(html.substring(candidate.contentStart(), nextStart));
            result.putIfAbsent(
                    candidate.url(),
                    new CherinfoNewsItem(candidate.title(), candidate.url(), publishedAt)
            );
        }
        return List.copyOf(result.values());
    }

    public CherinfoNewsDetails parseDetails(String html, URI pageUri) {
        if (html == null || html.isBlank()) {
            return new CherinfoNewsDetails("", List.of());
        }

        String textContent = findTextContent(html);
        List<String> imageUrls = extractImageUrls(html, textContent, pageUri);
        return new CherinfoNewsDetails(extractText(textContent), imageUrls);
    }

    private static String normalizeUrl(URI baseUri, String href) {
        String cleanedHref = decodeHtml(href).trim();
        URI resolved = baseUri.resolve(cleanedHref);
        return resolved.toString();
    }

    private static String extractPublishedAt(String htmlFragment) {
        String text = cleanText(htmlFragment);
        Matcher matcher = PUBLISHED_AT_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String cleanText(String html) {
        String withoutTags = TAG_PATTERN.matcher(html).replaceAll(" ");
        String decoded = decodeHtml(withoutTags);
        String normalized = WHITESPACE_PATTERN.matcher(decoded).replaceAll(" ").trim();
        return normalized.replaceAll("\\s+([.,!?;:])", "$1");
    }

    private static String findTextContent(String html) {
        Matcher articleTextMatcher = ARTICLE_TEXT_PATTERN.matcher(html);
        if (articleTextMatcher.find()) {
            return trimArticleContent(html.substring(articleTextMatcher.end()));
        }
        Matcher articleMatcher = ARTICLE_PATTERN.matcher(html);
        if (articleMatcher.find()) {
            return trimArticleContent(articleMatcher.group(1));
        }
        Matcher mainMatcher = MAIN_PATTERN.matcher(html);
        return trimArticleContent(mainMatcher.find() ? mainMatcher.group(1) : html);
    }

    private static String extractText(String content) {
        Matcher paragraphMatcher = PARAGRAPH_PATTERN.matcher(content);
        List<String> paragraphs = new ArrayList<>();
        while (paragraphMatcher.find()) {
            String paragraph = cleanText(paragraphMatcher.group(1));
            if (!paragraph.isBlank()) {
                paragraphs.add(paragraph);
            }
        }
        if (!paragraphs.isEmpty()) {
            return String.join("\n\n", paragraphs);
        }
        return extractTextLines(content);
    }

    private static String trimArticleContent(String content) {
        String trimmed = content;
        Matcher titleMatcher = TITLE_PATTERN.matcher(trimmed);
        if (titleMatcher.find()) {
            trimmed = trimmed.substring(titleMatcher.end());
        }
        trimmed = cutBefore(trimmed, "Если вы нашли ошибку");
        trimmed = cutBefore(trimmed, "Другие материалы");
        trimmed = cutBefore(trimmed, "main-new-small-img");
        trimmed = cutBefore(trimmed, "block-share");
        trimmed = cutBefore(trimmed, "blue-block-header");
        return trimmed;
    }

    private static String cutBefore(String content, String marker) {
        int markerIndex = content.indexOf(marker);
        if (markerIndex < 0) {
            return content;
        }
        return content.substring(0, markerIndex);
    }

    private static String extractTextLines(String content) {
        String withLineBreaks = BLOCK_TAG_PATTERN.matcher(content).replaceAll("\n");
        String[] lines = withLineBreaks.split("\\R");
        List<String> textLines = new ArrayList<>();
        for (String line : lines) {
            String text = cleanText(line);
            if (!text.isBlank() && !isServiceLine(text)) {
                textLines.add(text);
            }
        }
        return String.join("\n\n", textLines);
    }

    private static boolean isServiceLine(String text) {
        return text.startsWith("#")
                || NEWS_META_LINE_PATTERN.matcher(text).matches()
                || "Новости".equalsIgnoreCase(text);
    }

    private static List<String> extractImageUrls(String html, String textContent, URI pageUri) {
        Set<String> imageUrls = new LinkedHashSet<>();
        Matcher metaImageMatcher = META_IMAGE_PATTERN.matcher(html);
        while (metaImageMatcher.find()) {
            String imageUrl = normalizeUrl(pageUri, metaImageMatcher.group(3));
            if (isNewsImage(imageUrl)) {
                imageUrls.add(imageUrl);
            }
        }

        Matcher matcher = IMAGE_PATTERN.matcher(textContent);
        while (matcher.find()) {
            String imageUrl = normalizeUrl(pageUri, matcher.group(2));
            if (isNewsImage(imageUrl)) {
                imageUrls.add(imageUrl);
            }
        }
        return List.copyOf(imageUrls);
    }

    private static boolean isNewsImage(String imageUrl) {
        String lowerCaseUrl = imageUrl.toLowerCase();
        int queryIndex = lowerCaseUrl.indexOf('?');
        if (queryIndex >= 0) {
            lowerCaseUrl = lowerCaseUrl.substring(0, queryIndex);
        }
        int fragmentIndex = lowerCaseUrl.indexOf('#');
        if (fragmentIndex >= 0) {
            lowerCaseUrl = lowerCaseUrl.substring(0, fragmentIndex);
        }
        return (lowerCaseUrl.endsWith(".jpg")
                || lowerCaseUrl.endsWith(".jpeg")
                || lowerCaseUrl.endsWith(".png")
                || lowerCaseUrl.endsWith(".webp"))
                && !lowerCaseUrl.contains("logo")
                && !lowerCaseUrl.contains("banner")
                && !lowerCaseUrl.contains("advert")
                && !lowerCaseUrl.contains("reklam")
                && !lowerCaseUrl.contains("counter")
                && !lowerCaseUrl.contains("icon");
    }

    private static String decodeHtml(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&#39;", "'")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&bdquo;", "„")
                .replace("&ldquo;", "“")
                .replace("&mdash;", "—")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private record Candidate(String title, String url, int linkStart, int contentStart) {
    }
}
