package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.core.AttemptResult;
import java.util.concurrent.CompletionStage;

/** Uses a regular login; any external wake-on-connection service remains optional. */
public final class ConnectionWakeupProvider implements WakeupProvider {
    @Override public CompletionStage<AttemptResult> wake(AttemptContext context) { return context.connect(); }
}
