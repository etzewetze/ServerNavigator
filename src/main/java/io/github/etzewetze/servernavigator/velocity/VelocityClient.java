package io.github.etzewetze.servernavigator.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import io.github.etzewetze.servernavigator.config.NavigatorConfig.Mode;
import io.github.etzewetze.servernavigator.core.AttemptResult;
import io.github.etzewetze.servernavigator.core.NavigationClient;
import io.github.etzewetze.servernavigator.core.Progress;
import io.github.etzewetze.servernavigator.presentation.WaitingPresentation;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class VelocityClient implements NavigationClient {
    public record Permit(String target, long token, BooleanSupplier valid) { }
    private final Player player;
    private final ProxyServer proxy;
    private final Supplier<NavigatorConfig> config;
    private final MessageRenderer renderer;
    private final WaitingPresentation presentation;
    private final ConnectionClassifier classifier = new ConnectionClassifier();
    private volatile Permit permit;
    private BossBar bossbar;
    private boolean ownTitle, ownActionbar;
    private long lastChat = Long.MIN_VALUE;

    public VelocityClient(Player player, ProxyServer proxy, Supplier<NavigatorConfig> config, MessageRenderer renderer) {
        this.player = player; this.proxy = proxy; this.config = config; this.renderer = renderer;
        this.presentation = new WaitingPresentation(renderer);
    }
    public Permit permit() { return permit; }
    @Override public UUID id() { return player.getUniqueId(); }
    @Override public String name() { return player.getUsername(); }
    @Override public boolean online() { return player.isActive(); }
    @Override public String currentServer() {
        return player.getCurrentServer().map(ServerConnection::getServerInfo).map(s -> s.getName()).orElse(null);
    }
    @Override public boolean hasPermission(String node) { return player.hasPermission(node); }
    @Override public CompletionStage<AttemptResult> connect(Backend server, long token, BooleanSupplier stillAllowed) {
        if (!stillAllowed.getAsBoolean()) return CompletableFuture.completedFuture(AttemptResult.of(AttemptResult.Kind.CANCELLED));
        var registered = proxy.getServer(server.velocityName());
        if (registered.isEmpty()) return CompletableFuture.completedFuture(new AttemptResult(AttemptResult.Kind.REJECTED,
            renderer.render(config.get().messages().get("unknown-server"), Progress.basic(server))));
        Permit flight;
        synchronized (this) {
            // A timed-out/cancelled request may still be settling inside Velocity. Never overlap
            // another request or overwrite its stale-event guard.
            if (permit != null) return CompletableFuture.completedFuture(AttemptResult.of(AttemptResult.Kind.BUSY));
            flight = new Permit(server.velocityName(), token, stillAllowed); permit = flight;
        }
        try {
            return player.createConnectionRequest(registered.get()).connect().handle((result, error) -> {
                synchronized (this) { if (permit == flight) permit = null; }
                if (error != null) return AttemptResult.of(AttemptResult.Kind.NETWORK_ERROR);
                return switch (result.getStatus()) {
                    case SUCCESS, ALREADY_CONNECTED -> AttemptResult.of(AttemptResult.Kind.CONNECTED);
                    case CONNECTION_CANCELLED -> AttemptResult.of(AttemptResult.Kind.CANCELLED);
                    case CONNECTION_IN_PROGRESS -> AttemptResult.of(AttemptResult.Kind.BUSY);
                    case SERVER_DISCONNECTED -> classifier.rejection(config.get(), result.getReasonComponent().orElse(Component.empty()));
                };
            });
        } catch (RuntimeException failure) {
            synchronized (this) { if (permit == flight) permit = null; }
            return CompletableFuture.completedFuture(AttemptResult.of(AttemptResult.Kind.NETWORK_ERROR));
        }
    }
    @Override public void message(NavigatorConfig cfg, String key, Progress progress) {
        if (key.equals("queued")) { presentation.initialChat(progress).ifPresent(player::sendMessage); return; }
        player.sendMessage(renderer.render(cfg.messages().get(key), progress));
    }
    @Override public synchronized void display(Progress p) {
        var d = p.server().display();
        boolean longer = p.phase() == Progress.Phase.LONGER;
        if (d.modes().contains(Mode.NONE)) return;
        if (d.modes().contains(Mode.BOSSBAR)) {
            if (bossbar == null) {
                bossbar = BossBar.bossBar(presentation.bossbar(p), p.fraction(), d.color(), d.overlay()); player.showBossBar(bossbar);
            } else bossbar.name(presentation.bossbar(p)).progress(p.fraction()).color(d.color()).overlay(d.overlay());
        }
        if (d.modes().contains(Mode.TITLE)) {
            player.showTitle(presentation.title(p));
            ownTitle = true;
        }
        if (d.modes().contains(Mode.ACTIONBAR)) { player.sendActionBar(presentation.actionbar(p)); ownActionbar = true; }
        long now = System.nanoTime() / 1_000_000;
        if (d.modes().contains(Mode.CHAT) && (lastChat == Long.MIN_VALUE || now - lastChat >= d.chatInterval())) {
            player.sendMessage(renderer.render(longer ? d.chatLonger() : d.chat(), p)); lastChat = now;
        }
    }
    @Override public synchronized void clearDisplay() {
        if (bossbar != null) { player.hideBossBar(bossbar); bossbar = null; }
        if (ownTitle) { player.clearTitle(); ownTitle = false; }
        if (ownActionbar) { player.sendActionBar(Component.empty()); ownActionbar = false; }
        lastChat = Long.MIN_VALUE;
    }
}
