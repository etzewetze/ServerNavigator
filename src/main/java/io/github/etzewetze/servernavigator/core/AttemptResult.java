package io.github.etzewetze.servernavigator.core;

import net.kyori.adventure.text.Component;

public record AttemptResult(Kind kind, Component reason) {
    public enum Kind { CONNECTED, STARTING, NETWORK_ERROR, REJECTED, CANCELLED, BUSY, START_FAILED }
    public AttemptResult { if (reason == null) reason = Component.empty(); }
    public static AttemptResult of(Kind kind) { return new AttemptResult(kind, Component.empty()); }
}
