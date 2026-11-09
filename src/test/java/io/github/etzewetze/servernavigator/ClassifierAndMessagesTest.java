package io.github.etzewetze.servernavigator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.core.AttemptResult;
import io.github.etzewetze.servernavigator.core.Progress;
import io.github.etzewetze.servernavigator.velocity.ConnectionClassifier;
import io.github.etzewetze.servernavigator.velocity.MessageRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClassifierAndMessagesTest {
    @TempDir Path temp;
    NavigatorConfig config;
    ConnectionClassifier classifier = new ConnectionClassifier();
    @BeforeEach void setup() throws Exception {
        Path path = temp.resolve("config.toml");
        try (var input = getClass().getResourceAsStream("/config.toml")) { Files.copy(input, path); }
        config = new ConfigLoader().load(path, s -> true);
    }
    @Test void recognisedEnglishAndGermanAmpMessagesAreRetryable() {
        for (String text : java.util.List.of("Server is starting, please wait!", "This server is currently sleeping. Waking up...",
            "Dieser Server wird gerade gestartet.", "Der Server startet gerade."))
            assertEquals(AttemptResult.Kind.STARTING, classifier.rejection(config, Component.text(text)).kind());
    }
    @Test void whitelistCapacityAndMaintenanceOverrideStartingWords() {
        for (String text : java.util.List.of("Server is starting in maintenance mode", "Not whitelisted", "Server is full", "Custom plugin says no"))
            assertEquals(AttemptResult.Kind.REJECTED, classifier.rejection(config, Component.text(text)).kind());
    }
    @Test void untrustedBackendReasonIsNotInterpretedAsMiniMessage() {
        var progress = Progress.basic(config.servers().get("survival")).reason(Component.text("<red>NOT A TAG</red>"));
        var rendered = new MessageRenderer().render("<reason> ~<seconds> <server>", progress);
        assertEquals("<red>NOT A TAG</red> ~0 Survival", PlainTextComponentSerializer.plainText().serialize(rendered));
    }
}
