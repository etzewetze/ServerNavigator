package io.github.etzewetze.servernavigator.provider;

import io.github.etzewetze.servernavigator.core.AttemptResult;
import java.util.concurrent.CompletionStage;

/** A successful real login both proves admission and transfers this waiting player. */
@FunctionalInterface
public interface ReadinessChecker {
    CompletionStage<AttemptResult> check(AttemptContext context);
}
