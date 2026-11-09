package io.github.etzewetze.servernavigator.config;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.bossbar.BossBar;

/** Immutable, fully validated configuration. Durations use milliseconds. */
public record NavigatorConfig(long cooldown, long pollInterval, long maxWait, long attemptTimeout,
                              int maxAttempts, int transientRetries, Map<String, Backend> servers,
                              Map<String, String> messages, List<Pattern> startingPatterns,
                              List<Pattern> terminalPatterns) {
    public NavigatorConfig {
        servers = Map.copyOf(servers); messages = Map.copyOf(messages);
        startingPatterns = List.copyOf(startingPatterns); terminalPatterns = List.copyOf(terminalPatterns);
    }
    public Backend byVelocityName(String name) {
        return servers.values().stream().filter(s -> s.velocityName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public enum Mode { BOSSBAR, TITLE, ACTIONBAR, CHAT, NONE }
    public enum WakeType { AMP_LOGIN, HTTP }
    public record Wake(WakeType type, URI url, String tokenEnvironment, String body) { }
    public record Backend(String id, String velocityName, String displayName, String command,
                          List<String> aliases, boolean permissionRequired, String permission,
                          long expectedStartup, Wake wake, Display display) {
        public Backend { aliases = List.copyOf(aliases); }
        public List<String> commandNames() {
            var all = new java.util.ArrayList<String>(); all.add(command); all.addAll(aliases); return List.copyOf(all);
        }
    }
    public record Display(Set<Mode> modes, long updateInterval, long chatInterval,
                          BossBar.Color color, BossBar.Overlay overlay,
                          long fadeIn, long stay, long fadeOut,
                          String bossbar, String title, String subtitle, String actionbar, String chat,
                          String bossbarLonger, String titleLonger, String actionbarLonger, String chatLonger,
                          String checking, String starting, String longer, String connecting) {
        public Display { modes = Set.copyOf(modes); }
    }
}
