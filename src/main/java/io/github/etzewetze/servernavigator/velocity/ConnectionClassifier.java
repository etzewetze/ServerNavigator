package io.github.etzewetze.servernavigator.velocity;

import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.core.AttemptResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ConnectionClassifier {
    public AttemptResult rejection(NavigatorConfig config, Component reason) {
        String plain = PlainTextComponentSerializer.plainText().serialize(reason);
        // A full/whitelisted/maintenance response containing e.g. "restarting" must not be
        // mistaken for an ordinary startup response. Unknown messages are terminal.
        boolean terminal = config.terminalPatterns().stream().anyMatch(p -> p.matcher(plain).find());
        boolean starting = !terminal && config.startingPatterns().stream().anyMatch(p -> p.matcher(plain).find());
        return new AttemptResult(starting ? AttemptResult.Kind.STARTING : AttemptResult.Kind.REJECTED, reason);
    }
}
