package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.models.dtos.NewsData;
import org.monolites.monolit.models.exception.NewsParseException;
import org.monolites.monolit.services.parcer.Parser;
import org.monolites.monolit.services.vk.VkMessageSenderService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsService {

    private final List<Parser> parsers;
    private final VkMessageSenderService vkMessageSenderService;

    public void publishLatestNews(){
        for (Parser p : parsers) {
            List<NewsData> data = p.parseData();
            for (NewsData d : data) {
                try {
                    if(d.getImages() == null) {
                        vkMessageSenderService.sendMessage(d.getDescription());
                    }
                    else {
                        vkMessageSenderService.sendMessage(d.getDescription(), d.getImages());
                    }
                }
                catch (Exception e) {
                    log.error("Error while send parsed news data", e);
                }
                finally {
                    clearFiles(d.getImages());
                }
            }
        }
    }

    public void clearFiles(Map<String, File> files) {
        if (files == null){
            return;
        }

        for (Map.Entry<String, File> entry : files.entrySet()) {
            try{
                Files.deleteIfExists(entry.getValue().toPath());
            }
            catch(IOException e){
                throw new NewsParseException(e);
            }
        }
    }

}
