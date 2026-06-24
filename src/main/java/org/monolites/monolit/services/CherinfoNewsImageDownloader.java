package org.monolites.monolit.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CherinfoNewsImageDownloader {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "monolit-vk-bot/1.0";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    public List<File> downloadImages(List<String> imageUrls) {
        List<File> files = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            try {
                files.add(downloadImage(imageUrl));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Cherinfo news image downloading was interrupted for {}", imageUrl, e);
                break;
            } catch (Exception e) {
                log.warn("Failed to download Cherinfo news image {}", imageUrl, e);
            }
        }
        return files;
    }

    private File downloadImage(String imageUrl) throws IOException, InterruptedException {
        URI uri = URI.create(imageUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected Cherinfo image response status: " + response.statusCode());
        }
        Path file = Files.createTempFile("cherinfo-news-", extension(uri.getPath()));
        try {
            Files.write(file, response.body());
            return file.toFile();
        } catch (IOException e) {
            Files.deleteIfExists(file);
            throw e;
        }
    }

    private static String extension(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) {
            return ".jpg";
        }
        String extension = path.substring(dotIndex);
        return extension.length() <= 6 ? extension : ".jpg";
    }
}
