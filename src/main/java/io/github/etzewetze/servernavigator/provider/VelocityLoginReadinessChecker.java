package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.core.AttemptResult;
import java.util.concurrent.CompletionStage;

/** No ping/port heuristic: whitelist, capacity and maintenance are evaluated by the backend. */
public final class VelocityLoginReadinessChecker implements ReadinessChecker {
    @Override public CompletionStage<AttemptResult> check(AttemptContext context) { return context.connect(); }
}
