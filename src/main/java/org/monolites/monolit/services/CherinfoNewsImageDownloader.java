package org.monolites.monolit.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class CherinfoNewsImageDownloader {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "monolit-vk-bot/1.0";
    private static final String TEMP_FILE_PREFIX = "cherinfo-news-";
    private static final Path NON_POSIX_TEMP_BASE_DIRECTORY = Path.of(
            System.getProperty("user.home"),
            ".monolit",
            "tmp",
            "cherinfo-news"
    );
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_PERMISSIONS = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private Path tempDirectory;

    @Autowired
    public CherinfoNewsImageDownloader() {
        this(null);
    }

    CherinfoNewsImageDownloader(Path tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    public List<File> downloadImages(List<String> imageUrls) {
        List<File> files = new ArrayList<>();
        for (String imageUrl : imageUrls) {
            try {
                files.add(downloadImage(imageUrl));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Cherinfo news image downloading was interrupted for {}", imageUrl);
                break;
            } catch (Exception e) {
                log.warn("Failed to download Cherinfo news image {}: {}", imageUrl, e.getMessage());
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
        if (!isSupportedImageResponse(response, uri)) {
            throw new IOException("Unexpected Cherinfo image content for " + imageUrl);
        }
        Path file = Files.createTempFile(tempDirectory(), TEMP_FILE_PREFIX, extension(uri.getPath()));
        try {
            Files.write(file, response.body());
            return file.toFile();
        } catch (IOException e) {
            Files.deleteIfExists(file);
            throw e;
        }
    }

    private synchronized Path tempDirectory() throws IOException {
        if (tempDirectory == null) {
            tempDirectory = createPrivateTempDirectory();
        }
        return tempDirectory;
    }

    private static Path createPrivateTempDirectory() throws IOException {
        try {
            return Files.createTempDirectory(
                    TEMP_FILE_PREFIX,
                    PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_PERMISSIONS)
            );
        } catch (UnsupportedOperationException e) {
            return createPrivateTempDirectoryWithoutPosixAttributes();
        }
    }

    private static Path createPrivateTempDirectoryWithoutPosixAttributes() throws IOException {
        return createPrivateTempDirectoryWithoutPosixAttributes(NON_POSIX_TEMP_BASE_DIRECTORY);
    }

    private static void restrictOwnerAccess(Path path) {
        File directoryFile = path.toFile();
        boolean permissionsUpdated = directoryFile.setReadable(false, false);
        permissionsUpdated = directoryFile.setWritable(false, false) && permissionsUpdated;
        permissionsUpdated = directoryFile.setExecutable(false, false) && permissionsUpdated;
        permissionsUpdated = directoryFile.setReadable(true, true) && permissionsUpdated;
        permissionsUpdated = directoryFile.setWritable(true, true) && permissionsUpdated;
        permissionsUpdated = directoryFile.setExecutable(true, true) && permissionsUpdated;
        if (!permissionsUpdated) {
            log.warn("Failed to restrict Cherinfo image temp directory permissions for {}", path);
        }
    }

    private static Path createPrivateTempDirectoryWithoutPosixAttributes(Path baseDirectory) throws IOException {
        Files.createDirectories(baseDirectory);
        restrictOwnerAccess(baseDirectory);
        Path directory = Files.createTempDirectory(baseDirectory, TEMP_FILE_PREFIX);
        restrictOwnerAccess(directory);
        return directory;
    }

    static Path createPrivateTempDirectoryForTest(Path baseDirectory) throws IOException {
        return createPrivateTempDirectoryWithoutPosixAttributes(baseDirectory);
    }

    private static String extension(String path) {
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) {
            return ".jpg";
        }
        String extension = path.substring(dotIndex);
        return extension.length() <= 6 ? extension : ".jpg";
    }

    private static boolean isSupportedImageResponse(HttpResponse<byte[]> response, URI uri) {
        byte[] body = response.body();
        if (body == null || body.length < 4 || !hasSupportedImageSignature(body)) {
            return false;
        }
        return response.headers()
                .firstValue("Content-Type")
                .map(contentType -> contentType.toLowerCase().startsWith("image/"))
                .orElseGet(() -> isSupportedImagePath(uri.getPath()));
    }

    private static boolean hasSupportedImageSignature(byte[] body) {
        return isJpeg(body) || isPng(body) || isWebp(body);
    }

    private static boolean isJpeg(byte[] body) {
        return body.length >= 3
                && (body[0] & 0xFF) == 0xFF
                && (body[1] & 0xFF) == 0xD8
                && (body[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] body) {
        return body.length >= 8
                && (body[0] & 0xFF) == 0x89
                && body[1] == 0x50
                && body[2] == 0x4E
                && body[3] == 0x47
                && body[4] == 0x0D
                && body[5] == 0x0A
                && body[6] == 0x1A
                && body[7] == 0x0A;
    }

    private static boolean isWebp(byte[] body) {
        return body.length >= 12
                && body[0] == 0x52
                && body[1] == 0x49
                && body[2] == 0x46
                && body[3] == 0x46
                && body[8] == 0x57
                && body[9] == 0x45
                && body[10] == 0x42
                && body[11] == 0x50;
    }

    private static boolean isSupportedImagePath(String path) {
        String lowerCasePath = path.toLowerCase();
        return lowerCasePath.endsWith(".jpg")
                || lowerCasePath.endsWith(".jpeg")
                || lowerCasePath.endsWith(".png")
                || lowerCasePath.endsWith(".webp");
    }
}
