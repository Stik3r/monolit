package org.monolites.monolit.services;

import org.monolites.monolit.models.dtos.NewsDetails;
import org.monolites.monolit.models.dtos.NewsItem;

import java.io.IOException;
import java.util.List;

public interface NewsSourceClient {

    List<NewsItem> fetchLatestNews() throws IOException, InterruptedException;

    NewsDetails fetchNewsDetails(String newsUrl) throws IOException, InterruptedException;
}
