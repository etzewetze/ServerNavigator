package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import io.github.etzewetze.servernavigator.core.AttemptResult;
import io.github.etzewetze.servernavigator.core.NavigationClient;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

public record AttemptContext(Backend server, NavigationClient client, long token, long timeout,
                             BooleanSupplier allowed) {
    public CompletionStage<AttemptResult> connect() {
        if (!allowed.getAsBoolean()) return CompletableFuture.completedFuture(AttemptResult.of(AttemptResult.Kind.CANCELLED));
        return client.connect(server, token, allowed);
    }
}
