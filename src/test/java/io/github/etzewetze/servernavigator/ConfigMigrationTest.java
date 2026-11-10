package io.github.etzewetze.servernavigator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigMigrationTest {
    @TempDir Path temp;
    Path path;
    String legacy;
    @BeforeEach void setup() throws Exception {
        String source;
        try (var in = getClass().getResourceAsStream("/config.toml")) { source = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        legacy = source.replace("config-version = 2", "config-version = 1")
            .replace("network-retries = 100", "transient-retries = 3")
            .replace("modes = [\"TITLE\"]", "modes = [\"BOSSBAR\", \"TITLE\", \"ACTIONBAR\"]")
            .replace("[messages]", "[messages]\nqueued = 'Alte Erklärung'\n")
            .replaceAll("(?m)^(wait-chat|wait-chat-enabled|subtitle-longer) = .*\\R", "")
            + "\n[servers.lobby.wake]\ntype = \"LEGACY_CONNECTION\"\n";
        path = temp.resolve("config.toml"); Files.writeString(path, legacy);
    }
    ConfigLoader.Candidate candidate() throws Exception { return new ConfigLoader().loadCandidate(path, s -> true); }
    @Test void migrationIsPureUntilExplicitlyCommittedAndCreatesExactBackup() throws Exception {
        var c = candidate(); assertTrue(c.migrated()); assertEquals(legacy, Files.readString(path));
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
        Path backup = c.saveMigration(path); assertEquals(legacy, Files.readString(backup));
        assertTrue(Files.readString(path).contains("<server> <gold>Startet</gold>"));
        assertTrue(Files.readString(path).contains("Sobald der Server antwortet, wirst du teleportiert."));
        var next = candidate(); assertFalse(next.migrated()); assertEquals(100, next.config().networkRetries());
    }
    @Test void migrationKeepsServersPermissionsAliasesAndTimesButUsesCompactPresentation() throws Exception {
        var c = candidate().config();
        assertEquals(5, c.servers().size()); assertEquals(java.util.List.of("l", "hub"), c.servers().get("lobby").aliases());
        assertEquals("server.survival", c.servers().get("survival").permission());
        assertEquals(60000, c.servers().get("survival").expectedStartup());
        assertEquals(NavigatorConfig.WakeType.CONNECTION, c.servers().get("lobby").wake().type());
        assertEquals(java.util.Set.of(NavigatorConfig.Mode.TITLE), c.servers().get("lobby").display().modes());
        assertEquals("<server> <gold>Startet</gold>", c.servers().get("lobby").display().title());
        assertTrue(c.servers().get("lobby").display().waitChatEnabled());
    }
    @Test void customServerHttpTriggerAndNonDefaultRetryLimitSurvive() throws Exception {
        Files.writeString(path, legacy.replace("transient-retries = 3", "transient-retries = 7")
            + "\n[servers.skyblock]\ncommand = 'skyblock'\naliases = ['sky']\nexpected-startup-seconds = 75\nrequire-permission = true\npermission = 'custom.sky'\n"
            + "[servers.skyblock.wake]\ntype = 'HTTP'\nurl = 'https://start.example.org/sky'\nbody = '{\"target\":\"sky\"}'\n");
        var c = candidate().config(); var s = c.servers().get("skyblock");
        assertEquals(7, c.networkRetries()); assertEquals(75000, s.expectedStartup()); assertEquals("custom.sky", s.permission());
        assertTrue(s.permissionRequired()); assertEquals(NavigatorConfig.WakeType.HTTP, s.wake().type());
        assertEquals("{\"target\":\"sky\"}", s.wake().body());
    }
    @Test void invalidLegacyConfigurationIsNeverWrittenOrBackedUpAsSuccessfulMigration() throws Exception {
        String invalid = legacy.replace("cooldown-seconds = 15", "cooldown-seconds = -1"); Files.writeString(path, invalid);
        assertThrows(IllegalArgumentException.class, this::candidate); assertEquals(invalid, Files.readString(path));
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
    }
    @Test void concurrentExternalEditIsNotOverwritten() throws Exception {
        var c = candidate(); Files.writeString(path, "# External edit\n");
        assertThrows(java.io.IOException.class, () -> c.saveMigration(path)); assertEquals("# External edit\n", Files.readString(path));
    }
}
