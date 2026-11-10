package io.github.etzewetze.servernavigator;

import static io.github.etzewetze.servernavigator.core.AttemptResult.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.etzewetze.servernavigator.config.ConfigLoader;
import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import io.github.etzewetze.servernavigator.core.*;
import io.github.etzewetze.servernavigator.provider.WakeupProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NavigationEngineTest {
    @TempDir Path temp;
    NavigatorConfig config;
    AtomicLong now;
    FakeScheduler scheduler;
    Map<String, Integer> wakeCalls;
    NavigationEngine engine;

    @BeforeEach void setup() throws Exception {
        Path file = temp.resolve("config.toml");
        try (var input = getClass().getResourceAsStream("/config.toml")) { Files.copy(input, file); }
        config = new ConfigLoader().load(file, ignored -> true);
        now = new AtomicLong(); scheduler = new FakeScheduler(); wakeCalls = new HashMap<>();
        createEngine(config);
    }
    void createEngine(NavigatorConfig cfg) {
        engine = new NavigationEngine(cfg, scheduler, now::get, backend -> context -> {
            wakeCalls.merge(backend.id(), 1, Integer::sum); return context.connect();
        }, context -> context.connect());
    }
    static class FakeScheduler implements TaskScheduler {
        int live, created;
        @Override public Task repeat(Runnable action, Duration interval) {
            live++; created++;
            return new Task() { boolean done; @Override public void cancel() { if (!done) { done = true; live--; } } };
        }
    }
    static class FakeClient implements NavigationClient {
        final UUID id = UUID.randomUUID();
        String current = "lobby";
        boolean online = true, permission = true;
        final List<String> messages = new ArrayList<>();
        final List<Progress> views = new ArrayList<>();
        final List<CompletableFuture<AttemptResult>> futures = new ArrayList<>();
        final List<BooleanSupplier> guards = new ArrayList<>();
        int clears;
        @Override public UUID id() { return id; }
        @Override public String name() { return id.toString().substring(0, 8); }
        @Override public boolean online() { return online; }
        @Override public String currentServer() { return current; }
        @Override public boolean hasPermission(String node) { return permission; }
        @Override public CompletionStage<AttemptResult> connect(Backend server, long token, BooleanSupplier allowed) {
            assertTrue(allowed.getAsBoolean(), "No connect may be submitted without a current authorised wish");
            var future = new CompletableFuture<AttemptResult>(); futures.add(future); guards.add(allowed); return future;
        }
        @Override public void message(NavigatorConfig config, String key, Progress p) { messages.add(key); views.add(p); }
        @Override public void display(Progress p) { views.add(p); }
        @Override public void clearDisplay() { clears++; }
        void result(int index, AttemptResult.Kind kind) { futures.get(index).complete(AttemptResult.of(kind)); }
        Progress view() { return views.getLast(); }
    }
    @Test void cooldownIsSharedBetweenTargetsAndRejectedRepeatsDoNotExtendIt() {
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, STARTING);
        now.set(4000); engine.navigate(p, "creative");
        assertEquals("cooldown-pending", p.messages.getLast()); assertEquals(11, p.view().cooldown());
        assertEquals("survival", p.view().server().id());
        now.set(14000); engine.navigate(p, "challenges"); assertEquals(1, p.view().cooldown());
        assertEquals(1, engine.pendingCount()); assertEquals(1, p.futures.size()); assertEquals(0, p.clears);
        now.set(15000); engine.navigate(p, "creative");
        assertEquals("creative", p.view().server().id()); assertEquals(2, p.futures.size());
        assertEquals(1, p.clears); assertEquals(1, engine.pendingCount());
    }
    @Test void sameTargetAfterCooldownDoesNotResetCountdownOrCreateAnotherWish() {
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, STARTING);
        now.set(16000); engine.navigate(p, "survival");
        assertEquals("already-pending", p.messages.getLast()); assertEquals(16, p.view().elapsed());
        assertEquals(1, p.futures.size()); assertEquals(1, wakeCalls.get("survival"));
    }
    @Test void automaticConnectionDoesNotWaitForCommandCooldown() {
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, STARTING);
        now.set(2000); engine.tick(); assertEquals(2, p.futures.size());
        p.result(1, CONNECTED);
        assertEquals(0, engine.pendingCount()); assertEquals("connected", p.messages.getLast()); assertEquals(0, scheduler.live);
        assertTrue(now.get() < config.cooldown());
    }
    @Test void twentyConcurrentWaitersShareOneWakeAndConnectImmediatelyAfterLeader() {
        List<FakeClient> players = new ArrayList<>();
        for (int i = 0; i < 20; i++) { var p = new FakeClient(); players.add(p); engine.navigate(p, "survival"); }
        assertEquals(1, wakeCalls.get("survival")); assertEquals(1, scheduler.live);
        assertEquals(1, players.stream().mapToInt(p -> p.futures.size()).sum());
        players.getFirst().result(0, STARTING);
        now.set(2000); engine.tick(); players.getFirst().result(1, CONNECTED);
        for (var p : players.subList(1, players.size())) { assertEquals(1, p.futures.size()); p.result(0, CONNECTED); }
        assertEquals(0, engine.pendingCount()); assertEquals(0, scheduler.live); assertEquals(1, wakeCalls.get("survival"));
    }
    @Test void concurrentCommandsAreSerializedAndStillProduceOneWake() throws Exception {
        List<FakeClient> players = java.util.stream.IntStream.range(0, 50).mapToObj(i -> new FakeClient()).toList();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
            var jobs = players.stream().<java.util.concurrent.Callable<Void>>map(p -> () -> { engine.navigate(p, "survival"); return null; }).toList();
            for (var future : pool.invokeAll(jobs)) future.get(); // Test harness only, never plugin code.
        }
        assertEquals(50, engine.pendingCount()); assertEquals(1, wakeCalls.get("survival")); assertEquals(1, scheduler.live);
    }
    @Test void deniedPermissionNeverStartsServerAndDirectServerAccessIsDenied() {
        createEngine(restricted(config)); var p = new FakeClient(); p.permission = false;
        engine.navigate(p, "survival");
        assertEquals(0, engine.pendingCount()); assertTrue(wakeCalls.isEmpty()); assertTrue(p.futures.isEmpty());
        assertFalse(engine.mayAccess(p, "survival")); assertTrue(engine.mayAccess(p, "lobby"));
        assertEquals("no-permission", p.messages.getLast());
    }
    @Test void permissionsAreRecheckedBeforeReadinessAndInTheLateEventGuard() {
        createEngine(restricted(config)); var p = new FakeClient(); engine.navigate(p, "survival");
        assertTrue(p.guards.getFirst().getAsBoolean()); p.permission = false;
        assertFalse(p.guards.getFirst().getAsBoolean()); p.result(0, STARTING);
        now.set(2000); engine.tick();
        assertEquals(1, p.futures.size()); assertEquals(0, engine.pendingCount()); assertEquals("no-permission", p.messages.getLast());
    }
    @Test void cancellingHasNoCooldownAndLateCompletionCannotResurrectRequest() {
        var p = new FakeClient(); engine.navigate(p, "survival"); engine.cancel(p);
        assertFalse(p.guards.getFirst().getAsBoolean()); assertEquals("cancelled", p.messages.getLast());
        p.result(0, STARTING); now.set(4000); engine.tick();
        assertEquals(0, engine.pendingCount()); assertEquals(1, p.futures.size()); assertEquals(0, scheduler.live);
    }
    @Test void cancelledLeaderReleasesRemainingWaiterWithoutAnotherWake() {
        var a = new FakeClient(); var b = new FakeClient();
        engine.navigate(a, "survival"); engine.navigate(b, "survival"); engine.cancel(a); a.result(0, STARTING);
        now.set(2000); engine.tick();
        assertEquals(1, b.futures.size()); assertEquals(1, wakeCalls.get("survival"));
        b.result(0, CONNECTED); assertEquals(0, engine.pendingCount());
    }
    @Test void replacedWishCannotBeRestoredByOldResponse() {
        var p = new FakeClient(); engine.navigate(p, "survival");
        now.set(15000); engine.navigate(p, "creative");
        assertFalse(p.guards.getFirst().getAsBoolean()); p.result(0, STARTING);
        assertEquals(1, engine.pendingCount()); assertEquals("creative", engine.status("creative").getFirst().server().id());
        assertEquals(0, engine.status("survival").getFirst().count());
        p.result(1, CONNECTED); assertEquals(0, engine.pendingCount());
    }
    @Test void independentSwitchAndDisconnectClearWishesAndDisplays() {
        var a = new FakeClient(); engine.navigate(a, "survival"); engine.serverConnected(a.id(), "creative");
        assertEquals(0, engine.pendingCount()); assertEquals(1, a.clears); assertFalse(a.guards.getFirst().getAsBoolean());
        var b = new FakeClient(); engine.navigate(b, "survival"); engine.disconnect(b.id());
        assertEquals(0, engine.pendingCount()); assertEquals(1, b.clears); assertEquals(0, scheduler.live);
    }
    @Test void successfulVelocityEventBeforeFutureConnectsFollowersOnlyOnce() {
        var a = new FakeClient(); var b = new FakeClient(); engine.navigate(a, "survival"); engine.navigate(b, "survival");
        engine.serverConnected(a.id(), "survival"); assertEquals(1, b.futures.size());
        a.result(0, CONNECTED); assertEquals(1, b.futures.size()); b.result(0, CONNECTED);
        assertEquals(0, engine.pendingCount()); assertEquals(1, a.clears);
    }
    @Test void estimateMayExpireWithoutMovingPlayerOrBecomingNegative() {
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, STARTING);
        now.set(61000); engine.tick();
        assertEquals(Progress.Phase.LONGER, p.view().phase()); assertEquals(0, p.view().seconds());
        assertEquals("lobby", p.current); assertEquals(1, engine.pendingCount());
        p.result(1, STARTING); now.set(180000); engine.tick();
        assertEquals("timeout", p.messages.getLast()); assertEquals(0, engine.pendingCount()); assertEquals(0, scheduler.live);
    }
    @Test void timeoutOfSingleNetworkAttemptDoesNotCreateOverlappingRetries() {
        var p = new FakeClient(); engine.navigate(p, "survival"); now.set(10000); engine.tick();
        assertEquals("attempt-timeout", p.messages.getLast()); assertFalse(p.guards.getFirst().getAsBoolean());
        p.result(0, STARTING); now.set(12000); engine.tick(); assertEquals(1, p.futures.size());
    }
    @Test void fullOrWhitelistedResponseIsTerminalAndReasonIsPreserved() {
        var p = new FakeClient(); engine.navigate(p, "survival");
        p.futures.getFirst().complete(new AttemptResult(REJECTED, Component.text("Server is full")));
        assertEquals("rejected", p.messages.getLast()); assertEquals(Component.text("Server is full"), p.view().reason());
        now.set(2000); engine.tick(); assertEquals(1, p.futures.size()); assertEquals(0, engine.pendingCount());
    }
    @Test void offlineServerCanBeWaitedForWithoutAnyPanelOrStartupMessage() {
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, NETWORK_ERROR);
        assertEquals(1, engine.pendingCount());
        assertEquals(Progress.Phase.CHECKING, engine.status("survival").getFirst().phase());
        now.set(2000); engine.tick(); p.result(1, CONNECTED);
        assertEquals("connected", p.messages.getLast()); assertEquals(0, engine.pendingCount());
        assertEquals(1, wakeCalls.get("survival"));
    }
    @Test void networkRetriesAreBoundedEvenWithoutAnExplicitStartupResponse() {
        createEngine(new NavigatorConfig(config.cooldown(), config.pollInterval(), config.maxWait(), config.attemptTimeout(),
            config.maxAttempts(), 3, config.servers(), config.messages(), config.startingPatterns(), config.terminalPatterns()));
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, NETWORK_ERROR);
        for (int i = 1; i <= 3; i++) { now.addAndGet(2000); engine.tick(); p.result(i, NETWORK_ERROR); }
        assertEquals("network-error", p.messages.getLast()); assertEquals(0, engine.pendingCount()); assertEquals(4, p.futures.size());
    }
    @Test void networkRetryCounterCanBeResetByARealStartupResponse() {
        createEngine(new NavigatorConfig(config.cooldown(), config.pollInterval(), config.maxWait(), config.attemptTimeout(),
            config.maxAttempts(), 3, config.servers(), config.messages(), config.startingPatterns(), config.terminalPatterns()));
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, NETWORK_ERROR);
        now.addAndGet(2000); engine.tick(); p.result(1, NETWORK_ERROR);
        now.addAndGet(2000); engine.tick(); p.result(2, STARTING);
        for (int i = 3; i <= 5; i++) { now.addAndGet(2000); engine.tick(); p.result(i, NETWORK_ERROR); }
        assertEquals(1, engine.pendingCount(), "The recognised startup response must reset consecutive failures");
        now.addAndGet(2000); engine.tick(); p.result(6, NETWORK_ERROR);
        assertEquals("network-error", p.messages.getLast()); assertEquals(0, engine.pendingCount()); assertEquals(7, p.futures.size());
    }
    @Test void waitingChatIsRequestedOnlyOnceDespiteTicksAndCooldownRepeats() {
        var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, NETWORK_ERROR);
        for (int i = 1; i <= 3; i++) {
            now.set(i * 2000); engine.tick(); p.result(i, NETWORK_ERROR); engine.navigate(p, "survival");
        }
        assertEquals(1, p.messages.stream().filter("queued"::equals).count());
        assertEquals(3, p.messages.stream().filter("cooldown-pending"::equals).count());
    }
    @Test void connectionAttemptLimitIsIndependentOfTimeoutAndEstimate() {
        var c = new NavigatorConfig(config.cooldown(), config.pollInterval(), config.maxWait(), config.attemptTimeout(), 2,
            config.networkRetries(), config.servers(), config.messages(), config.startingPatterns(), config.terminalPatterns());
        createEngine(c); var p = new FakeClient(); engine.navigate(p, "survival"); p.result(0, STARTING);
        now.set(2000); engine.tick(); p.result(1, STARTING); now.set(4000); engine.tick();
        assertEquals("attempt-limit", p.messages.getLast()); assertEquals(2, p.futures.size()); assertEquals(0, scheduler.live);
    }
    @Test void httpOrOtherWakeFailureEndsAllWaiters() {
        var start = new CompletableFuture<AttemptResult>();
        engine = new NavigationEngine(config, scheduler, now::get, backend -> context -> { wakeCalls.merge(backend.id(), 1, Integer::sum); return start; }, context -> context.connect());
        var a = new FakeClient(); var b = new FakeClient(); engine.navigate(a, "survival"); engine.navigate(b, "survival");
        start.complete(AttemptResult.of(START_FAILED));
        assertEquals("start-error", a.messages.getLast()); assertEquals("start-error", b.messages.getLast());
        assertEquals(0, engine.pendingCount()); assertEquals(0, scheduler.live);
    }
    @Test void reloadAndShutdownInvalidateAllTokensAndRemoveScheduler() {
        var p = new FakeClient(); engine.navigate(p, "survival"); engine.reload(config);
        assertFalse(p.guards.getFirst().getAsBoolean()); assertEquals("reload-cancelled", p.messages.getLast());
        p.result(0, STARTING); now.set(15000); engine.navigate(p, "creative"); engine.close();
        assertFalse(p.guards.getLast().getAsBoolean()); assertEquals("shutting-down", p.messages.getLast());
        assertEquals(0, engine.pendingCount()); assertEquals(0, scheduler.live);
    }
    @Test void noCurrentServerAndAlreadyThereDoNotConsumeCooldown() {
        var p = new FakeClient(); p.current = null; engine.navigate(p, "survival"); assertEquals("no-current-server", p.messages.getLast());
        p.current = "survival"; engine.navigate(p, "survival"); assertEquals("already-there", p.messages.getLast());
        p.current = "lobby"; engine.navigate(p, "survival"); assertEquals(1, p.futures.size());
    }
    private NavigatorConfig restricted(NavigatorConfig c) {
        var map = new HashMap<>(c.servers()); var s = map.get("survival");
        map.put(s.id(), new Backend(s.id(), s.velocityName(), s.displayName(), s.command(), s.aliases(), true,
            s.permission(), s.expectedStartup(), s.wake(), s.display()));
        return new NavigatorConfig(c.cooldown(), c.pollInterval(), c.maxWait(), c.attemptTimeout(), c.maxAttempts(), c.networkRetries(),
            map, c.messages(), c.startingPatterns(), c.terminalPatterns());
    }
}
