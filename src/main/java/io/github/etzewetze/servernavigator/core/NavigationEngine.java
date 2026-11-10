package io.github.etzewetze.servernavigator.core;

import static io.github.etzewetze.servernavigator.core.AttemptResult.Kind.*;

import io.github.etzewetze.servernavigator.config.NavigatorConfig;
import io.github.etzewetze.servernavigator.config.NavigatorConfig.Backend;
import io.github.etzewetze.servernavigator.provider.AttemptContext;
import io.github.etzewetze.servernavigator.provider.ReadinessChecker;
import io.github.etzewetze.servernavigator.provider.WakeupProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.LongSupplier;
import net.kyori.adventure.text.Component;

/**
 * A single monitor serializes commands, scheduler ticks, events and future completions.
 * Nothing awaited under this lock: provider methods must return immediately.
 * Object identity + monotonically increasing tokens reject stale completions (ABA-safe).
 */
public final class NavigationEngine implements AutoCloseable {
    private NavigatorConfig config;
    private final TaskScheduler scheduler;
    private final LongSupplier time;
    private final Function<Backend, WakeupProvider> wakeups;
    private final ReadinessChecker readiness;
    private final Map<UUID, Request> requests = new HashMap<>();
    private final Map<String, Operation> operations = new HashMap<>();
    private final Map<UUID, Long> lastAccepted = new HashMap<>();
    private TaskScheduler.Task ticker;
    private long tokenSequence;
    private boolean closed;

    private static final class Operation {
        final Backend target;
        final long started;
        final Map<UUID, Request> waiters = new LinkedHashMap<>();
        long nextCheck;
        boolean startedWake, startingConfirmed, ready, busy;
        Operation(Backend target, long now) { this.target = target; this.started = now; this.nextCheck = now; }
    }
    private static final class Request {
        final NavigationClient client;
        final Operation operation;
        final long token, created;
        final String origin;
        long lastDisplay = Long.MIN_VALUE, attemptStarted;
        int attempts, networkFailures;
        boolean inFlight;
        Request(NavigationClient client, Operation operation, long token, long now) {
            this.client = client; this.operation = operation; this.token = token; this.created = now;
            this.origin = client.currentServer();
        }
    }
    public NavigationEngine(NavigatorConfig config, TaskScheduler scheduler, LongSupplier time,
                            Function<Backend, WakeupProvider> wakeups, ReadinessChecker readiness) {
        this.config = config; this.scheduler = scheduler; this.time = time; this.wakeups = wakeups; this.readiness = readiness;
    }
    public synchronized NavigatorConfig config() { return config; }

    public synchronized void navigate(NavigationClient player, String id) {
        if (closed) return;
        Backend target = config.servers().get(id);
        if (target == null) { player.message(config, "unknown-server", Progress.basic(null)); return; }
        if (!allowed(player, target)) { player.message(config, "no-permission", Progress.basic(target)); return; }
        String current = player.currentServer();
        if (!player.online() || current == null) { player.message(config, "no-current-server", Progress.basic(target)); return; }
        if (current.equalsIgnoreCase(target.velocityName())) { player.message(config, "already-there", Progress.basic(target)); return; }
        long now = time.getAsLong();
        Request existing = requests.get(player.id());
        Long accepted = lastAccepted.get(player.id());
        long remaining = accepted == null ? 0 : Math.max(0, config.cooldown() - (now - accepted));
        if (remaining > 0) {
            Progress p = existing == null ? Progress.basic(target) : progress(existing, now);
            player.message(config, existing == null ? "cooldown" : "cooldown-pending", p.cooldown(ceilSeconds(remaining)));
            return;
        }
        if (existing != null && existing.operation.target.id().equals(id)) {
            player.message(config, "already-pending", progress(existing, now)); return;
        }
        if (existing != null) finish(existing, "replaced", null);
        // Only accepted, new navigation requests consume cooldown. Rejected repeats never extend it.
        lastAccepted.put(player.id(), now);
        Operation op = operations.computeIfAbsent(id, ignored -> new Operation(target, now));
        Request r = new Request(player, op, ++tokenSequence, now);
        requests.put(player.id(), r); op.waiters.put(player.id(), r);
        player.message(config, "queued", progress(r, now));
        player.display(progress(r, now)); r.lastDisplay = now;
        if (ticker == null) ticker = scheduler.repeat(this::tick, Duration.ofMillis(100));
        drive(op, now);
    }
    public synchronized void cancel(NavigationClient client) {
        Request r = requests.get(client.id());
        if (r == null) client.message(config, "nothing-pending", Progress.basic(null));
        else finish(r, "cancelled", null);
    }
    public synchronized void disconnect(UUID id) {
        Request r = requests.get(id);
        if (r != null) finish(r, null, null);
        lastAccepted.remove(id);
    }
    public synchronized void serverConnected(UUID id, String target) {
        Request r = requests.get(id);
        if (r == null) return;
        if (r.inFlight && r.operation.target.velocityName().equalsIgnoreCase(target)) connected(r);
        else finish(r, "independent-switch", null);
    }
    /** Permission enforcement for /server, plugin redirects and initial connections alike. */
    public synchronized boolean mayAccess(NavigationClient client, String velocityName) {
        Backend target = config.byVelocityName(velocityName);
        return target == null || allowed(client, target);
    }
    public synchronized void accessDenied(NavigationClient client, String name) {
        client.message(config, "no-permission", Progress.basic(config.byVelocityName(name)));
    }
    public synchronized List<Progress> status(String id) {
        List<Progress> list = new ArrayList<>(); long now = time.getAsLong();
        for (Backend s : config.servers().values().stream().sorted(java.util.Comparator.comparing(Backend::id)).toList()) {
            if (id != null && !s.id().equals(id) && !s.velocityName().equalsIgnoreCase(id)) continue;
            Operation op = operations.get(s.id());
            if (op == null) list.add(Progress.basic(s));
            else list.add(progress(op.waiters.values().iterator().next(), now));
        }
        return List.copyOf(list);
    }
    public synchronized int pendingCount() { return requests.size(); }
    public synchronized void reload(NavigatorConfig replacement) {
        for (Request r : List.copyOf(requests.values())) finish(r, "reload-cancelled", null);
        config = replacement;
    }
    public synchronized void tick() {
        if (closed) return;
        long now = time.getAsLong();
        for (Request r : List.copyOf(requests.values())) {
            if (!r.client.online()) { finish(r, null, null); continue; }
            if (!allowed(r.client, r.operation.target)) { finish(r, "no-permission", null); continue; }
            // In flight, ServerConnectedEvent owns the successful move; before any later attempt
            // we still compare the live origin. A failed independent attempt keeps the wish.
            String current = r.client.currentServer();
            if (current == null || !current.equalsIgnoreCase(r.origin)) {
                if (r.inFlight && r.operation.target.velocityName().equalsIgnoreCase(current)) connected(r);
                else finish(r, "independent-switch", null);
                continue;
            }
            if (now - r.created >= config.maxWait()) { finish(r, "timeout", null); continue; }
            if (r.inFlight && now - r.attemptStarted >= config.attemptTimeout()) {
                finish(r, "attempt-timeout", null); continue;
            }
            if (now - r.lastDisplay >= r.operation.target.display().updateInterval()) {
                r.client.display(progress(r, now)); r.lastDisplay = now;
            }
        }
        for (Operation op : List.copyOf(operations.values())) drive(op, now);
        // Cooldown entries are also removed when no longer useful, not retained until disconnect.
        lastAccepted.entrySet().removeIf(e -> now - e.getValue() >= config.cooldown());
    }
    private void drive(Operation op, long now) {
        if (!live(op) || op.busy || now < op.nextCheck) return;
        if (op.ready) {
            for (Request r : List.copyOf(op.waiters.values())) if (!r.inFlight) attempt(r, false, false, now);
        } else {
            for (Request r : List.copyOf(op.waiters.values())) if (!r.inFlight) {
                boolean wake = !op.startedWake; op.startedWake = true;
                attempt(r, wake, true, now); break;
            }
        }
    }
    private void attempt(Request r, boolean wake, boolean shared, long now) {
        if (!valid(r)) return;
        if (!allowed(r.client, r.operation.target)) { finish(r, "no-permission", null); return; }
        if (!r.client.online() || r.client.currentServer() == null || !r.origin.equalsIgnoreCase(r.client.currentServer())) {
            finish(r, "independent-switch", null); return;
        }
        if (r.attempts >= config.maxAttempts()) { finish(r, "attempt-limit", null); return; }
        Operation op = r.operation;
        r.inFlight = true; r.attemptStarted = now; r.attempts++;
        if (shared) op.busy = true;
        var context = new AttemptContext(op.target, r.client, r.token, config.attemptTimeout(), () -> attemptAllowed(r));
        CompletionStage<AttemptResult> future;
        try { future = wake ? wakeups.apply(op.target).wake(context) : readiness.check(context); }
        catch (RuntimeException error) { complete(r, shared, AttemptResult.of(wake ? START_FAILED : NETWORK_ERROR)); return; }
        future.whenComplete((result, error) -> complete(r, shared,
            error != null || result == null ? AttemptResult.of(wake ? START_FAILED : NETWORK_ERROR) : result));
    }
    private synchronized boolean attemptAllowed(Request r) {
        return valid(r) && r.inFlight && r.client.online() && allowed(r.client, r.operation.target)
            && r.origin.equalsIgnoreCase(r.client.currentServer())
            && time.getAsLong() - r.created < config.maxWait()
            && time.getAsLong() - r.attemptStarted < config.attemptTimeout();
    }
    private synchronized void complete(Request r, boolean shared, AttemptResult result) {
        Operation op = r.operation;
        // Clear only this operation's flight. Even a removed leader must release other waiters.
        if (shared && live(op)) { op.busy = false; op.nextCheck = time.getAsLong() + config.pollInterval(); }
        if (!valid(r)) return;
        r.inFlight = false;
        if (!allowed(r.client, op.target)) { finish(r, "no-permission", null); return; }
        if (!r.client.online()) { finish(r, null, null); return; }
        switch (result.kind()) {
            case CONNECTED -> connected(r);
            case STARTING -> { op.startingConfirmed = true; op.ready = false; r.networkFailures = 0; }
            case NETWORK_ERROR -> {
                // An unavailable server is not evidence of sleep or a successful start. Keep
                // waiting for it to answer, bounded by retries, total attempts and the deadline.
                op.ready = false;
                if (++r.networkFailures > config.networkRetries()) finish(r, "network-error", result.reason());
            }
            case REJECTED -> finish(r, "rejected", result.reason());
            case CANCELLED -> finish(r, "connection-cancelled", result.reason());
            case BUSY -> finish(r, "connection-busy", result.reason());
            case START_FAILED -> failOperation(op, result.reason());
        }
        // A successful leader releases the remaining players immediately, without a poll delay.
        if (live(op) && op.ready) drive(op, time.getAsLong());
    }
    private void connected(Request r) {
        Operation op = r.operation; op.ready = true;
        // The event can precede the future. Release the shared gate here; the later callback
        // is discarded by request identity and must never reset a newer operation.
        op.busy = false; op.nextCheck = time.getAsLong();
        finish(r, "connected", null);
        if (live(op)) drive(op, time.getAsLong());
    }
    private void failOperation(Operation op, Component reason) {
        for (Request r : List.copyOf(op.waiters.values())) finish(r, "start-error", reason);
    }
    private void finish(Request r, String message, Component reason) {
        if (!valid(r)) return;
        Progress p = progress(r, time.getAsLong());
        requests.remove(r.client.id()); r.operation.waiters.remove(r.client.id());
        r.client.clearDisplay();
        if (message != null && r.client.online()) r.client.message(config, message, reason == null ? p : p.reason(reason));
        if (r.operation.waiters.isEmpty()) operations.remove(r.operation.target.id(), r.operation);
        if (requests.isEmpty() && ticker != null) { ticker.cancel(); ticker = null; }
    }
    private boolean live(Operation op) { return operations.get(op.target.id()) == op && !op.waiters.isEmpty(); }
    private boolean valid(Request r) { return !closed && requests.get(r.client.id()) == r && live(r.operation); }
    private static boolean allowed(NavigationClient player, Backend target) {
        return !target.permissionRequired() || player.hasPermission(target.permission());
    }
    private Progress progress(Request r, long now) {
        Operation op = r.operation; long elapsed = Math.max(0, now - op.started);
        long left = Math.max(0, op.target.expectedStartup() - elapsed);
        Progress.Phase phase = op.ready ? Progress.Phase.CONNECTING : left == 0 ? Progress.Phase.LONGER
            : !op.startingConfirmed ? Progress.Phase.CHECKING : Progress.Phase.STARTING;
        return new Progress(op.target, ceilSeconds(left), elapsed / 1000, 0, phase,
            (float) Math.max(0, Math.min(1, (double) left / op.target.expectedStartup())), op.waiters.size(),
            String.join(", ", op.waiters.values().stream().map(v -> v.client.name()).toList()), r.attempts, Component.empty());
    }
    private static long ceilSeconds(long millis) { return (millis + 999) / 1000; }
    @Override public synchronized void close() {
        if (closed) return;
        for (Request r : List.copyOf(requests.values())) finish(r, "shutting-down", null);
        closed = true; lastAccepted.clear();
    }
}
