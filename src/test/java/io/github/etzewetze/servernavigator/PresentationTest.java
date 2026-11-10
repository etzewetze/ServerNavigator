package io.github.etzewetze.servernavigator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.core.Progress;
import io.github.etzewetze.servernavigator.presentation.ComponentLines;
import io.github.etzewetze.servernavigator.presentation.WaitingPresentation;
import io.github.etzewetze.servernavigator.velocity.MessageRenderer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresentationTest {
    @TempDir Path temp;
    String source;
    MessageRenderer renderer = new MessageRenderer();
    WaitingPresentation presentation = new WaitingPresentation(renderer);
    @BeforeEach void setup() throws Exception {
        try (var in = getClass().getResourceAsStream("/config.toml")) { source = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
    }
    NavigatorConfig config(String text) throws Exception {
        Path p = temp.resolve("config.toml"); Files.writeString(p, text); return new ConfigLoader().load(p, s -> true);
    }
    Progress progress(NavigatorConfig c, Progress.Phase phase) {
        return new Progress(c.servers().get("testserver"), phase == Progress.Phase.LONGER ? 0 : 23, 7, 0, phase, .7f, 1, "Player", 2, Component.empty());
    }
    static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    @Test void defaultTitleContainsShortHeadingAndTimerInRealSubtitle() throws Exception {
        var p = progress(config(source), Progress.Phase.STARTING); var title = presentation.title(p);
        assertEquals("Testserver Startet", plain(title.title())); assertEquals("~23 Sekunden", plain(title.subtitle()));
        assertEquals("Sobald der Server antwortet, wirst du teleportiert.", plain(presentation.initialChat(p).orElseThrow()));
        assertEquals("Testserver startet | ~23 s", plain(presentation.bossbar(p)));
        assertFalse(plain(title.title()).contains("teleportiert"));
    }
    @Test void bothSelectableModesHaveIndependentInitialChatWithoutRequiringChatMode() throws Exception {
        for (String mode : java.util.List.of("TITLE", "BOSSBAR")) {
            var c = config(source.replace("modes = [\"TITLE\"]", "modes = [\"" + mode + "\"]"));
            assertEquals(java.util.Set.of(NavigatorConfig.Mode.valueOf(mode)), c.servers().get("testserver").display().modes());
            assertTrue(presentation.initialChat(progress(c, Progress.Phase.STARTING)).isPresent());
        }
    }
    @Test void initialChatCanBeOverriddenAndDisabledPerServer() throws Exception {
        var c = config(source + "\n[servers.testserver.display]\nwait-chat = '<green>Bitte warten.</green>'\n");
        assertEquals("Bitte warten.", plain(presentation.initialChat(progress(c, Progress.Phase.STARTING)).orElseThrow()));
        c = config(source + "\n[servers.testserver.display]\nwait-chat-enabled = false\n");
        assertTrue(presentation.initialChat(progress(c, Progress.Phase.STARTING)).isEmpty());
        c = config(source + "\n[servers.testserver.display]\nwait-chat = ''\n");
        assertTrue(presentation.initialChat(progress(c, Progress.Phase.STARTING)).isEmpty());
    }
    @Test void allSupportedNewlineFormsMapToTitleAndSubtitleWithFormattingPreserved() throws Exception {
        for (String newline : java.util.List.of("<newline>", "<newline/>", "<br>", "\\n")) {
            var c = config(source + "\n[servers.testserver.display]\ntitle = '<gold><server> Startet" + newline + "<zeit> s</gold>'\n");
            var title = presentation.title(progress(c, Progress.Phase.STARTING));
            assertEquals("Testserver Startet", plain(title.title())); assertEquals("23 s", plain(title.subtitle()));
            assertEquals(NamedTextColor.GOLD, firstText(title.subtitle()).color());
        }
    }
    @Test void actualTomlEscapedNewlineAndMultilineChatAreSupported() throws Exception {
        var c = config(source + "\n[servers.testserver.display]\ntitle = \"<server> Startet\\n~<seconds> s\"\nwait-chat = 'Zeile 1<newline/>Zeile 2\\nZeile 3'\n");
        var p = progress(c, Progress.Phase.STARTING);
        assertEquals("~23 s", plain(presentation.title(p).subtitle()));
        assertEquals("Zeile 1\nZeile 2\nZeile 3", plain(presentation.initialChat(p).orElseThrow()));
    }
    @Test void splitKeepsInheritedStylesAndSeparatesEmptyLines() {
        Component text = MiniMessage.miniMessage().deserialize("<gold><bold>A<newline/>B</bold></gold>");
        var lines = ComponentLines.split(text); assertEquals(2, lines.size());
        assertEquals(NamedTextColor.GOLD, firstText(lines.get(1)).color());
        assertEquals(TextDecoration.State.TRUE, firstText(lines.get(1)).decoration(TextDecoration.BOLD));
        assertEquals(3, ComponentLines.split(Component.text("A\n\nB")).size());
    }
    @Test void bossbarAndActionbarStaySingleLineWithoutDroppingWords() throws Exception {
        var c = config(source + "\n[servers.testserver.display]\nbossbar = '<server><newline/>~<zeit> s'\nactionbar = 'A\\nB'\n");
        var p = progress(c, Progress.Phase.STARTING);
        assertEquals("Testserver ~23 s", plain(presentation.bossbar(p)));
        assertEquals("A B", plain(presentation.actionbar(p)));
    }
    @Test void expiredEstimateKeepsHeadingAndReplacesTimerWithLongerMessage() throws Exception {
        var title = presentation.title(progress(config(source), Progress.Phase.LONGER));
        assertEquals("Testserver Startet", plain(title.title())); assertEquals("Start dauert länger …", plain(title.subtitle()));
    }
    @Test void germanAndOriginalPlaceholdersResolveToSameValues() throws Exception {
        var p = progress(config(source), Progress.Phase.STARTING);
        assertEquals("Testserver/Testserver 23/23", plain(renderer.render("<server>/<servername> <seconds>/<zeit>", p)));
    }
    private static TextComponent firstText(Component c) {
        if (c instanceof TextComponent text && !text.content().isEmpty()) return text;
        for (Component child : c.children()) { TextComponent found = firstText(child); if (found != null) return found; }
        return null;
    }
}
