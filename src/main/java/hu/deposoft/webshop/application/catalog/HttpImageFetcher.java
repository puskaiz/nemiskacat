package hu.deposoft.webshop.application.catalog;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Production {@link ImageFetcher}: downloads image bytes over HTTP with
 * {@link HttpClient}. The content type comes from the response header, falling
 * back to the URL extension (the storage only needs it to pick a key extension;
 * the app does no image decoding — CLAUDE.md #8). Tests inject a stub instead, so
 * no network is hit (design §Testing).
 */
@Slf4j
@Component
public class HttpImageFetcher implements ImageFetcher {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public FetchedImage fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " fetching " + url);
            }
            String contentType = response.headers().firstValue("content-type")
                    .map(HttpImageFetcher::stripParams)
                    .filter(ct -> ct.startsWith("image/"))
                    .orElseGet(() -> contentTypeFromUrl(url));
            return new FetchedImage(response.body(), contentType);
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch image " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted fetching image " + url, e);
        }
    }

    private static String stripParams(String contentType) {
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase();
    }

    private static String contentTypeFromUrl(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        String lower = path.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        // Default to JPEG (covers .jpg/.jpeg and unknown extensions on the legacy site).
        return "image/jpeg";
    }
}
