package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.core.AttemptResult;
import java.util.concurrent.CompletionStage;

/** AMP's 'When a user joins' wake mode is activated by a normal authenticated login. */
public final class AmpLoginWakeupProvider implements WakeupProvider {
    @Override public CompletionStage<AttemptResult> wake(AttemptContext context) { return context.connect(); }
}
