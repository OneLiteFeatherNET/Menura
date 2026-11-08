package net.onelitefeather.menura.core.channel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Production transport on the JDK HTTP client. Only HTTPS is used, with a connect and a request timeout. Bearer tokens
 * and form bodies are sent but never written to any log by this class.
 */
public final class JdkHttpTransport implements HttpTransport {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public Reply send(String method, URI uri, String bearerToken, String formBody) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("platform endpoints are reached over HTTPS only");
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        if (formBody != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, HttpRequest.BodyPublishers.ofString(formBody, StandardCharsets.UTF_8));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(),
                    response.headers().firstValue("Retry-After").map(value -> value.trim()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while reaching the platform", e);
        }
    }
}
