package org.monolites.monolit.services;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CherinfoNewsImageDownloaderTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void downloadsSupportedImageResponseToConfiguredTempDirectory(@TempDir Path tempDir) throws Exception {
        byte[] jpeg = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46
        };
        startServer("/image.jpg", "image/jpeg", jpeg);
        CherinfoNewsImageDownloader downloader = new CherinfoNewsImageDownloader(tempDir);

        List<File> files = downloader.downloadImages(List.of(url("/image.jpg")));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file).exists();
            assertThat(file.toPath()).hasParentRaw(tempDir);
            assertThat(file.getName()).endsWith(".jpg");
        });
        Files.deleteIfExists(files.getFirst().toPath());
    }

    @Test
    void downloadsSupportedImageResponseToPrivateTempDirectory() throws Exception {
        byte[] jpeg = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46
        };
        startServer("/private-image.jpg", "image/jpeg", jpeg);
        CherinfoNewsImageDownloader downloader = new CherinfoNewsImageDownloader();

        List<File> files = downloader.downloadImages(List.of(url("/private-image.jpg")));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file).exists();
            assertThat(file.toPath().getParent().getFileName().toString()).startsWith("cherinfo-news-");
            assertThat(file.getName()).endsWith(".jpg");
        });
        Files.deleteIfExists(files.getFirst().toPath());
        Files.deleteIfExists(files.getFirst().toPath().getParent());
    }

    @Test
    void skipsHtmlResponseWithImageExtension() throws Exception {
        startServer("/image.jpg", "text/html", "<html>not an image</html>".getBytes());
        CherinfoNewsImageDownloader downloader = new CherinfoNewsImageDownloader();

        List<File> files = downloader.downloadImages(List.of(url("/image.jpg")));

        assertThat(files).isEmpty();
    }

    @Test
    void skipsNonSuccessfulImageResponse() throws Exception {
        startServer("/missing.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 500);
        CherinfoNewsImageDownloader downloader = new CherinfoNewsImageDownloader();

        List<File> files = downloader.downloadImages(List.of(url("/missing.jpg")));

        assertThat(files).isEmpty();
    }

    @Test
    void acceptsPngWhenContentTypeIsMissingAndPathLooksSupported(@TempDir Path tempDir) throws Exception {
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        startServer("/image.png", null, png);
        CherinfoNewsImageDownloader downloader = new CherinfoNewsImageDownloader(tempDir);

        List<File> files = downloader.downloadImages(List.of(url("/image.png")));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file).exists();
            assertThat(file.getName()).endsWith(".png");
        });
        Files.deleteIfExists(files.getFirst().toPath());
    }

    @Test
    void usesDefaultJpgExtensionForExtensionlessImageUrl(@TempDir Path tempDir) throws Exception {
        byte[] webp = new byte[] {
                0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
        };
        startServer("/image", "image/webp", webp);
        CherinfoNewsImageDownloader downloader = new CherinfoNewsImageDownloader(tempDir);

        List<File> files = downloader.downloadImages(List.of(url("/image")));

        assertThat(files).singleElement().satisfies(file -> {
            assertThat(file).exists();
            assertThat(file.getName()).endsWith(".jpg");
        });
        Files.deleteIfExists(files.getFirst().toPath());
    }

    @Test
    void createsNonPosixFallbackTempDirectoryBelowPrivateBase(@TempDir Path tempDir) throws Exception {
        Path baseDirectory = tempDir.resolve("cherinfo-base");

        Path directory = CherinfoNewsImageDownloader.createPrivateTempDirectoryForTest(baseDirectory);

        assertThat(directory).hasParentRaw(baseDirectory);
        assertThat(directory.getFileName().toString()).startsWith("cherinfo-news-");
        Files.deleteIfExists(directory);
    }

    private void startServer(String path, String contentType, byte[] responseBody) throws Exception {
        startServer(path, contentType, responseBody, 200);
    }

    private void startServer(String path, String contentType, byte[] responseBody, int statusCode) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(statusCode, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }
}
