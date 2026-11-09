package io.github.etzewetze.servernavigator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {
    @TempDir Path temp;
    String example;
    @BeforeEach void source() throws Exception {
        try (var input = getClass().getResourceAsStream("/config.toml")) { example = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    }
    NavigatorConfig load(String content) throws Exception {
        Path path = temp.resolve("config.toml"); Files.writeString(path, content); return new ConfigLoader().load(path, ignored -> true);
    }
    @Test void shippedExampleIsCompleteAndContainsAllFiveTargetsAndAliases() throws Exception {
        var config = load(example); assertEquals(5, config.servers().size()); assertEquals(15000, config.cooldown());
        assertEquals(java.util.List.of("lobby", "l", "hub"), config.servers().get("lobby").commandNames());
        assertEquals("server.survival", config.servers().get("survival").permission());
        assertEquals(net.kyori.adventure.bossbar.BossBar.Color.GREEN, config.servers().get("survival").display().color());
        assertEquals(config.servers().get("creative").display().updateInterval(), config.servers().get("survival").display().updateInterval());
    }
    @Test void duplicateAliasAcrossTargetsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> load(example.replace("aliases = [\"surv\"]", "aliases = [\"hub\"]")));
    }
    @Test void aliasEqualToItsOwnMainCommandAndAdminAliasAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> load(example.replace("aliases = [\"surv\"]", "aliases = [\"survival\"]")));
        assertThrows(IllegalArgumentException.class, () -> load(example.replace("aliases = [\"surv\"]", "aliases = [\"servernavigator\"]")));
    }
    @Test void unknownRegisteredTargetAndTwoIdsForSameTargetAreRejected() throws Exception {
        Path path = temp.resolve("unknown.toml"); Files.writeString(path, example);
        assertThrows(IllegalArgumentException.class, () -> new ConfigLoader().load(path, s -> !s.equals("survival")));
        assertThrows(IllegalArgumentException.class, () -> load(example.replace("velocity-name = \"creative\"", "velocity-name = \"survival\"")));
    }
    @Test void permissionsInheritGlobalAndAllowLocalOverrideAndCustomNode() throws Exception {
        var config = load(example.replace("require-server-permissions = false", "require-server-permissions = true")
            .replace("# require-permission = false", "require-permission = false")
            .replace("# permission = \"servernavigator.testzugang\"", "permission = \"servernavigator.testzugang\""));
        assertFalse(config.servers().get("lobby").permissionRequired());
        assertTrue(config.servers().get("survival").permissionRequired());
        assertEquals("servernavigator.testzugang", config.servers().get("testserver").permission());
    }
    @Test void unknownSettingsWrongTypesBadModesAndInvalidMiniMessageAreRejected() {
        for (String invalid : java.util.List.of(
            example.replace("cooldown-seconds = 15", "cooldown-second = 15"),
            example.replace("cooldown-seconds = 15", "cooldown-seconds = \"15\""),
            example.replace("poll-interval-ms = 2000", "poll-interval-ms = 0"),
            example.replace("modes = [\"BOSSBAR\", \"TITLE\", \"ACTIONBAR\"]", "modes = [\"NONE\", \"TITLE\"]"),
            example.replace("bossbar-color = \"PURPLE\"", "bossbar-color = \"#ff0000\""),
            example.replace("<red>Du hast dafür keine Berechtigung.</red>", "<red>Du hast dafür keine Berechtigung."))) {
            assertThrows(IllegalArgumentException.class, () -> load(invalid));
        }
    }
    @Test void extensionNeedsNoCodeChanges() throws Exception {
        var c = load(example + "\n[servers.skyblock]\nvelocity-name = \"skyblock\"\ncommand = \"skyblock\"\naliases = [\"sky\"]\n");
        assertEquals(6, c.servers().size()); assertEquals("server.skyblock", c.servers().get("skyblock").permission());
    }
    @Test void externalHttpPlaceholderCannotAccidentallyBecomeActive() {
        assertThrows(IllegalArgumentException.class, () -> load(example.replace("[servers.creative.wake]\ntype = \"AMP_LOGIN\"",
            "[servers.creative.wake]\ntype = \"HTTP\"\nurl = \"https://AMP-BRIDGE-EXAMPLE.invalid/wake/creative\"")));
    }
}
