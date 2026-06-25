package org.monolites.monolit.services;

import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@Component
public class CherinfoNewsParser {

    private static final DateTimeFormatter RSS_DATE_FORMATTER = DateTimeFormatter.ofPattern(
            "dd.MM.yyyy HH:mm",
            Locale.forLanguageTag("ru-RU")
    );
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "<article\\b[^>]*>(.*?)</article>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern MAIN_PATTERN = Pattern.compile(
            "<main\\b[^>]*>(.*?)</main>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern OPENING_DIV_PATTERN = Pattern.compile(
            "<div\\b[^>]*>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CLASS_ATTRIBUTE_PATTERN = Pattern.compile(
            "\\bclass\\s*=\\s*([\"'])([^\"']*)\\1",
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
    private static final Pattern GALLERY_PATTERN = Pattern.compile(
            "<div\\b[^>]*class\\s*=\\s*([\"'])[^\"']*\\bfotorama\\b[^\"']*\\1[^>]*>(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern LINK_PATTERN = Pattern.compile(
            "<a\\b[^>]*href\\s*=\\s*([\"'])([^\"']+)\\1[^>]*>",
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
    private static final Pattern SPACE_BEFORE_PUNCTUATION_PATTERN = Pattern.compile("\\s+([.,!?;:])");
    private static final Pattern NEWS_META_LINE_PATTERN = Pattern.compile(
            "(?:Сегодня|Вчера|\\d{2}\\.\\d{2}\\.\\d{4})\\s+\\d{1,2}:\\d{2}(?:\\s+\\d+){0,2}"
    );

    private final ImagePaths imagePaths;

    public CherinfoNewsParser(
            @Value("${monolit.news.cherinfo.image.big-path}") String imageBigPath,
            @Value("${monolit.news.cherinfo.image.medium-path}") String imageMediumPath,
            @Value("${monolit.news.cherinfo.image.small-path}") String imageSmallPath
    ) {
        this.imagePaths = new ImagePaths(imageBigPath.trim(), imageMediumPath.trim(), imageSmallPath.trim());
    }

    public List<NewsItem> parseRss(String rss, URI baseUri) {
        if (rss == null || rss.isBlank()) {
            return List.of();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(rss)));
            NodeList items = document.getElementsByTagName("item");
            Map<String, NewsItem> result = new LinkedHashMap<>();
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                String title = textContent(item, "title");
                String link = textContent(item, "link");
                if (title.isBlank() || link.isBlank()) {
                    continue;
                }
                String url = normalizeUrl(baseUri, link);
                result.putIfAbsent(url, new NewsItem(title, url, formatRssDate(textContent(item, "pubDate"))));
            }
            return List.copyOf(result.values());
        } catch (Exception e) {
            return List.of();
        }
    }

    public NewsDetails parseDetails(String html, URI pageUri) {
        if (html == null || html.isBlank()) {
            return new NewsDetails("", List.of());
        }

        String textContent = findTextContent(html);
        List<String> imageUrls = extractImageUrls(html, textContent, pageUri);
        return new NewsDetails(extractText(textContent), imageUrls);
    }

    private static String textContent(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static String formatRssDate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return OffsetDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .format(RSS_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            return value.trim();
        }
    }

    private static String normalizeUrl(URI baseUri, String href) {
        String cleanedHref = decodeHtml(href).trim();
        URI resolved = baseUri.resolve(cleanedHref);
        return resolved.toString();
    }

    private static String cleanText(String html) {
        String withoutTags = TAG_PATTERN.matcher(html).replaceAll(" ");
        String decoded = decodeHtml(withoutTags);
        String normalized = WHITESPACE_PATTERN.matcher(decoded).replaceAll(" ").trim();
        return SPACE_BEFORE_PUNCTUATION_PATTERN.matcher(normalized).replaceAll("$1");
    }

    private static String findTextContent(String html) {
        int articleTextStart = articleTextStart(html);
        if (articleTextStart >= 0) {
            return trimArticleContent(html.substring(articleTextStart));
        }
        Matcher articleMatcher = ARTICLE_PATTERN.matcher(html);
        if (articleMatcher.find()) {
            return trimArticleContent(articleMatcher.group(1));
        }
        Matcher mainMatcher = MAIN_PATTERN.matcher(html);
        return trimArticleContent(mainMatcher.find() ? mainMatcher.group(1) : html);
    }

    private static int articleTextStart(String html) {
        Matcher openingDivMatcher = OPENING_DIV_PATTERN.matcher(html);
        while (openingDivMatcher.find()) {
            if (hasCssClass(openingDivMatcher.group(), "article-text")) {
                return openingDivMatcher.end();
            }
        }
        return -1;
    }

    private static boolean hasCssClass(String tag, String cssClass) {
        Matcher classAttributeMatcher = CLASS_ATTRIBUTE_PATTERN.matcher(tag);
        if (!classAttributeMatcher.find()) {
            return false;
        }
        String[] classes = WHITESPACE_PATTERN.split(classAttributeMatcher.group(2).trim());
        for (String currentClass : classes) {
            if (cssClass.equals(currentClass)) {
                return true;
            }
        }
        return false;
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

    private List<String> extractImageUrls(String html, String textContent, URI pageUri) {
        List<String> galleryImageUrls = extractGalleryImageUrls(textContent, pageUri);
        if (!galleryImageUrls.isEmpty()) {
            return galleryImageUrls;
        }

        List<String> articleImageUrls = extractArticleImageUrls(textContent, pageUri);
        if (!articleImageUrls.isEmpty()) {
            return articleImageUrls;
        }

        return extractMetaImageUrls(html, pageUri);
    }

    private List<String> extractGalleryImageUrls(String textContent, URI pageUri) {
        ImageUrls imageUrls = new ImageUrls(imagePaths);
        Matcher galleryMatcher = GALLERY_PATTERN.matcher(textContent);
        while (galleryMatcher.find()) {
            Matcher linkMatcher = LINK_PATTERN.matcher(galleryMatcher.group(2));
            while (linkMatcher.find()) {
                imageUrls.add(bestQualityImageUrl(normalizeUrl(pageUri, linkMatcher.group(2))));
            }
        }
        return imageUrls.values();
    }

    private List<String> extractArticleImageUrls(String textContent, URI pageUri) {
        ImageUrls imageUrls = new ImageUrls(imagePaths);
        Matcher matcher = IMAGE_PATTERN.matcher(textContent);
        while (matcher.find()) {
            String imageUrl = normalizeUrl(pageUri, matcher.group(2));
            imageUrls.add(bestQualityImageUrl(imageUrl));
        }
        return imageUrls.values();
    }

    private List<String> extractMetaImageUrls(String html, URI pageUri) {
        ImageUrls imageUrls = new ImageUrls(imagePaths);
        Matcher metaImageMatcher = META_IMAGE_PATTERN.matcher(html);
        while (metaImageMatcher.find()) {
            imageUrls.add(normalizeUrl(pageUri, metaImageMatcher.group(3)));
        }
        return imageUrls.values();
    }

    private String bestQualityImageUrl(String imageUrl) {
        return imageUrl
                .replace(imagePaths.mediumPath(), imagePaths.bigPath())
                .replace(imagePaths.smallPath(), imagePaths.bigPath());
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

    private static final class ImageUrls {

        private final ImagePaths imagePaths;
        private final Set<String> keys = new LinkedHashSet<>();
        private final List<String> values = new ArrayList<>();

        private ImageUrls(ImagePaths imagePaths) {
            this.imagePaths = imagePaths;
        }

        private void add(String imageUrl) {
            if (imageUrl.isBlank() || !isNewsImage(imageUrl)) {
                return;
            }
            if (keys.add(imageDeduplicationKey(imageUrl))) {
                values.add(imageUrl);
            }
        }

        private List<String> values() {
            return List.copyOf(values);
        }

        private String imageDeduplicationKey(String imageUrl) {
            URI uri = URI.create(imageUrl);
            String path = uri.getPath()
                    .replace(imagePaths.mediumPath(), imagePaths.bigPath())
                    .replace(imagePaths.smallPath(), imagePaths.bigPath());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            return scheme + "://" + host + path;
        }

        private static boolean isNewsImage(String imageUrl) {
            String lowerCaseUrl = imageUrl.toLowerCase(Locale.ROOT);
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
    }

    private record ImagePaths(String bigPath, String mediumPath, String smallPath) {
    }
}
