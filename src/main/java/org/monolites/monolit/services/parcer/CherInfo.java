package org.monolites.monolit.services.parcer;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.monolites.monolit.models.dtos.ImageDto;
import org.monolites.monolit.models.dtos.NewsData;
import org.monolites.monolit.models.entities.NewsState;
import org.monolites.monolit.models.exception.NewsParseException;
import org.monolites.monolit.repositories.NewsStateRepository;
import org.monolites.monolit.utils.ImageDownloader;
import org.monolites.monolit.utils.RssData;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CherInfo implements Parser {

    private static final String RSS_LINK = "https://cherinfo.ru/rss/news";

    private final NewsStateRepository newsStateRepository;

    private NewsState newsState;

    @Override
    public List<NewsData> parseData() {
        List<NewsData> newsList = new ArrayList<>();
        try {

            newsState = newsStateRepository.findNewsStateByStateKey(getName());
            if (newsState == null) {
                newsState = new NewsState();
                newsState.setStateKey(getName());
                newsStateRepository.save(newsState);
            }

            SyndFeed feed = checkNewsUpdate();

            if (feed == null) {
                return newsList;
            }

            feed.setEntries(findFirstNews(feed.getEntries()));

            for (SyndEntry entry : feed.getEntries()) {
                NewsData newsData = getNews(entry.getLink());
                newsData.setTitle(entry.getTitle());
                newsData.setDate(entry.getPublishedDate());

                newsList.add(newsData);
            }

        }
        catch (Exception e) {
            log.error("Error parsing cherinfo news", e);
        }
        return newsList;
    }

    @Override
    public String getName() {
        return "CherInfo";
    }

    private SyndFeed checkNewsUpdate() {
        SyndFeed feed = RssData.getData(RSS_LINK);
        Date lastNewsDate = feed.getEntries().get(0).getPublishedDate();
        if (lastNewsDate.equals(newsState.getLatestNewsDate())) {
            return null;
        }

        return feed;
    }

    private List<SyndEntry> findFirstNews(List<SyndEntry> entries) {
        int indx = 0;

        for (SyndEntry entry : entries) {
            if (entry.getPublishedDate().equals(newsState.getLatestNewsDate()) || indx == 4) {
                indx = entries.indexOf(entry) + 1;
                break;
            }
            indx++;
        }

        entries = entries.subList(0, indx);
        newsState.setLatestNewsDate(entries.get(0).getPublishedDate());
        newsState.setLatestNewsUrl(entries.get(0).getLink());
        newsStateRepository.save(newsState);
        Collections.reverse(entries);

        return entries;
    }

    private NewsData getNews(String link) {
        try {
            Document doc = Jsoup.connect(link).get();
            Element element = doc.getElementsByClass("article-text").first();
            StringBuilder description = new StringBuilder();
            NewsData news = new NewsData();

            for (Element e : element.getAllElements().subList(1, element.getAllElements().size())) {

                switch (e.tagName()) {
                    case "p":
                        description.append((e.text().isEmpty() || !e.getElementsByTag("iframe").isEmpty()) ? "" : e.text() + "\n\n");
                        Element img = e.getElementsByTag("img").first();
                        if (img != null) {
                            news.setImages(getImagesLinks(new Elements(List.of(img))));
                        }
                        break;
                    case "div":
                        if(e.attr("class").equals("fotorama")){
                            news.setImages(getImagesLinks(e.getElementsByTag("a")));
                        }
                        break;
                    default:
                }
            }

            news.setDescription(description.toString());
            return news;

        } catch (IOException e) {
            throw new NewsParseException("Ошибка парсинга", e);
        }
    }

    private Map<String, File> getImagesLinks(Elements images) {
        Map<String, File> result = new HashMap<>();

        for (Element img : images) {
            String link = img.attr("href").isEmpty() ? img.attr("src") : img.attr("href");
            ImageDto imageDto = ImageDownloader.downloadImage(link);
            File file = new File(imageDto.getPath().toUri());
            result.put(imageDto.getName(), file);
        }
        return result;
    }
}
