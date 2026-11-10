package io.github.etzewetze.servernavigator.presentation;

import io.github.etzewetze.servernavigator.core.Progress;
import io.github.etzewetze.servernavigator.velocity.MessageRenderer;
import java.time.Duration;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

/** Pure presentation construction, shared by the live adapter and rendering tests. */
public final class WaitingPresentation {
    private final MessageRenderer renderer;
    public WaitingPresentation(MessageRenderer renderer) { this.renderer = renderer; }
    public Optional<Component> initialChat(Progress p) {
        var d = p.server().display();
        return d.waitChatEnabled() && !d.waitChat().isBlank()
            ? Optional.of(renderer.render(d.waitChat(), p)) : Optional.empty();
    }
    public Title title(Progress p) {
        var d = p.server().display(); boolean late = p.phase() == Progress.Phase.LONGER;
        var titleLines = ComponentLines.split(renderer.render(late ? d.titleLonger() : d.title(), p));
        // Vanilla supplies exactly two rows. An inline newline in 'title' selects its second
        // row explicitly; otherwise the dedicated subtitle template supplies that row.
        Component subtitle = titleLines.size() > 1 ? titleLines.get(1)
            : ComponentLines.singleLine(renderer.render(late ? d.subtitleLonger() : d.subtitle(), p));
        return Title.title(titleLines.getFirst(), subtitle, Title.Times.times(
            Duration.ofMillis(d.fadeIn()), Duration.ofMillis(d.stay()), Duration.ofMillis(d.fadeOut())));
    }
    public Component bossbar(Progress p) {
        var d = p.server().display();
        return ComponentLines.singleLine(renderer.render(p.phase() == Progress.Phase.LONGER ? d.bossbarLonger() : d.bossbar(), p));
    }
    public Component actionbar(Progress p) {
        var d = p.server().display();
        return ComponentLines.singleLine(renderer.render(p.phase() == Progress.Phase.LONGER ? d.actionbarLonger() : d.actionbar(), p));
    }
}
