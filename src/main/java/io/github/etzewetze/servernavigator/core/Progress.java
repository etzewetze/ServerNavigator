package io.github.etzewetze.servernavigator.core;

import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import net.kyori.adventure.text.Component;

public record Progress(Backend server, long seconds, long elapsed, long cooldown, Phase phase,
                       float fraction, int count, String waiters, int attempts, Component reason) {
    public enum Phase { CHECKING, STARTING, LONGER, CONNECTING, IDLE }
    public Progress { if (reason == null) reason = Component.empty(); }
    public static Progress basic(Backend backend) {
        return new Progress(backend, 0, 0, 0, Phase.IDLE, 0, 0, "", 0, Component.empty());
    }
    public Progress cooldown(long value) {
        return new Progress(server, seconds, elapsed, value, phase, fraction, count, waiters, attempts, reason);
    }
    public Progress reason(Component value) {
        return new Progress(server, seconds, elapsed, cooldown, phase, fraction, count, waiters, attempts, value);
    }
}
