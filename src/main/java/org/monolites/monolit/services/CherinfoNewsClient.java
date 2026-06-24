package org.monolites.monolit.services;

import org.monolites.monolit.models.dtos.CherinfoNewsDetails;
import org.monolites.monolit.models.dtos.CherinfoNewsItem;

import java.io.IOException;
import java.util.List;

public interface CherinfoNewsClient {

    List<CherinfoNewsItem> fetchLatestNews() throws IOException, InterruptedException;

    CherinfoNewsDetails fetchNewsDetails(String newsUrl) throws IOException, InterruptedException;
}
