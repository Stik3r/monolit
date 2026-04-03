package org.monolites.monolit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.monolites.monolit.handlers.GroupLongPoolApiHandler;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkLongPollRunnerService {

    private final GroupLongPoolApiHandler groupLongPoolApiHandler;

    @Async
    public void run() {
        log.info("Запуск прослушивания LongPoll...");
        while (true) {
            try {
                groupLongPoolApiHandler.run();
                return;
            } catch (Exception e) {
                log.error("LongPoll упал, перезапуск через 10 секунд", e);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
