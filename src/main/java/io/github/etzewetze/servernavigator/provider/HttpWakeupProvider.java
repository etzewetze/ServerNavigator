package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.core.AttemptResult;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;

/** Optional explicit POST integration with an independently configured start service. */
public final class HttpWakeupProvider implements WakeupProvider, AutoCloseable {
    private final HttpClient client;
    public HttpWakeupProvider(long timeout) {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public CompletionStage<AttemptResult> wake(AttemptContext context) {
        if (!context.allowed().getAsBoolean()) return CompletableFuture.completedFuture(AttemptResult.of(AttemptResult.Kind.CANCELLED));
        var wake = context.server().wake();
        var request = HttpRequest.newBuilder(wake.url()).timeout(Duration.ofMillis(context.timeout()))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(wake.body()));
        if (!wake.tokenEnvironment().isEmpty()) {
            String token = System.getenv(wake.tokenEnvironment());
            if (token == null || token.isBlank()) return CompletableFuture.completedFuture(AttemptResult.of(AttemptResult.Kind.START_FAILED));
            request.header("Authorization", "Bearer " + token);
        }
        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
            .handle((response, error) -> error != null ? AttemptResult.of(AttemptResult.Kind.START_FAILED)
                : response.statusCode() >= 200 && response.statusCode() < 300 ? AttemptResult.of(AttemptResult.Kind.STARTING)
                : new AttemptResult(AttemptResult.Kind.START_FAILED, Component.text("HTTP " + response.statusCode())));
    }
    @Override public void close() { client.shutdownNow(); }
}
