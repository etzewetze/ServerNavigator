package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.core.AttemptResult;
import java.util.concurrent.CompletionStage;

/** Invoked at most once for a shared operation. Never a Velocity 'start server' API. */
@FunctionalInterface
public interface WakeupProvider {
    CompletionStage<AttemptResult> wake(AttemptContext context);
}
