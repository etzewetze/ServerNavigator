package io.github.etzewetze.servernavigator.velocity;

import io.github.etzewetze.servernavigator.core.Progress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MessageRenderer {
    private final MiniMessage mini = MiniMessage.miniMessage();
    public Component render(String template, Progress p) {
        return mini.deserialize(template,
            Placeholder.component("server", p.server() == null ? Component.empty() : mini.deserialize(p.server().displayName())),
            Placeholder.unparsed("seconds", Long.toString(p.seconds())),
            Placeholder.unparsed("elapsed", Long.toString(p.elapsed())),
            Placeholder.unparsed("cooldown", Long.toString(p.cooldown())),
            Placeholder.component("status", status(p)),
            Placeholder.component("reason", p.reason()),
            Placeholder.unparsed("count", Integer.toString(p.count())),
            Placeholder.unparsed("waiters", p.waiters()),
            Placeholder.unparsed("attempts", Integer.toString(p.attempts())));
    }
    public Component status(Progress p) {
        if (p.server() == null || p.phase() == Progress.Phase.IDLE) return Component.empty();
        var d = p.server().display();
        String template = switch (p.phase()) {
            case CHECKING -> d.checking(); case STARTING -> d.starting(); case LONGER -> d.longer();
            case CONNECTING -> d.connecting(); case IDLE -> "";
        };
        // Status strings can use every ordinary placeholder, but <status> is deliberately empty
        // here to prevent self-reference/recursion in an administrator's template.
        return mini.deserialize(template,
            Placeholder.component("server", mini.deserialize(p.server().displayName())),
            Placeholder.unparsed("seconds", Long.toString(p.seconds())),
            Placeholder.unparsed("elapsed", Long.toString(p.elapsed())),
            Placeholder.unparsed("cooldown", Long.toString(p.cooldown())),
            Placeholder.component("status", Component.empty()),
            Placeholder.component("reason", p.reason()),
            Placeholder.unparsed("count", Integer.toString(p.count())),
            Placeholder.unparsed("waiters", p.waiters()),
            Placeholder.unparsed("attempts", Integer.toString(p.attempts())));
    }
}
