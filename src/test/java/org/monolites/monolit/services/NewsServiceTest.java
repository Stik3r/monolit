package org.monolites.monolit.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.monolites.monolit.services.parcer.CherInfo;
import org.monolites.monolit.services.parcer.Parser;
import org.monolites.monolit.services.vk.VkMessageSenderService;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    NewsService newsService;

    @Mock
    VkMessageSenderService vkMessageSenderService;

    @BeforeEach
    void setUp() {
        List<Parser> parsers = new ArrayList<>();
        parsers.add(Mockito.mock(CherInfo.class));
        newsService = new NewsService(
                parsers,
                vkMessageSenderService
        );
    }

    @Test
    void publishLatestNews() {
        Assertions.assertDoesNotThrow(newsService::publishLatestNews);
    }
}
