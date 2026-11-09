package io.github.etzewetze.servernavigator.core;

import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Adapter boundary: core tests need no live proxy or Minecraft client. */
public interface NavigationClient {
    UUID id();
    String name();
    boolean online();
    String currentServer(); // null: no existing game server (no limbo implementation)
    boolean hasPermission(String node);
    CompletionStage<AttemptResult> connect(Backend server, long token, BooleanSupplier stillAllowed);
    void message(NavigatorConfig config, String key, Progress progress);
    void display(Progress progress);
    void clearDisplay();
}
