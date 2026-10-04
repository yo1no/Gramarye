package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Excluded bounded observations of original C6 calls. No clock, bucket, queue or control setter. */
public final class P11C4aC6NativeProbe {
    public interface EntryView {
        Connection c6$connection(); Object c6$control(); boolean c6$waiting();
        boolean c6$capacityRejected(); long c6$terminalPhase();
    }
    public interface ControlView {
        Object c6$tryRate(); Object c6$statusRate(); Object c6$wait();
    }
    public record EntrySample(Connection connection, Object control, boolean waiting,
            boolean capacityRejected, long terminalPhase) { }
    public record WaitSample(boolean started, boolean expired, boolean unavailable,
            long start, long last, long timeout) { }
    private static volatile Run active;
    private static final ThreadLocal<OfferCall> OFFER = new ThreadLocal<>();
    private P11C4aC6NativeProbe() { }

    static void start(MinecraftServer server, ServerPlayer a, ServerPlayer b, Path output) {
        P11C4aEvidence.require(P11C4aEvidence.enabled() && server.isSameThread() && active == null
                && a != b && !a.getUUID().equals(b.getUUID()) && !a.isFakePlayer() && !b.isFakePlayer()
                && a.getServer() == server && b.getServer() == server
                && server.getPlayerList().getPlayer(a.getUUID()) == a
                && server.getPlayerList().getPlayer(b.getUUID()) == b,
                "C6_OBSERVER_REAL_TWO_ACTORS");
        active = new Run(server, a.connection.getConnection(), b.connection.getConnection(), output);
        P11C4aC6ExpiryProbe.start(server, a.connection.getConnection(), output);
    }

    /** Values copied under the real entries monitor; this observer runs after releasing it. */
    public static void live(MinecraftServer server, Object owner, Object suppliedLimits,
            int k, List<EntrySample> entries) {
        var run = active;
        if (run == null || run.server != server) { return; }
        try {
            var limits = (P11StartupLimits) suppliedLimits;
            var captured = new ArrayList<BoundSample>();
            for (var sample : entries) {
                int role = role(run, sample.connection());
                if (role < 0) { continue; }
                var control = (P11TransitionControl) sample.control();
                synchronized (control) {
                    var state = control.state().orElse(null);
                    var access = (ControlView) (Object) control;
                    captured.add(new BoundSample(sample, role, state == null ? 0 : state.connectionEpoch(),
                            access.c6$tryRate(), access.c6$statusRate(), access.c6$wait()));
                }
            }
            synchronized (run) {
                if (run.owner != null && run.owner != owner) { fault(run, "C6_DIFFERENT_LIVE_OWNER"); return; }
                run.owner = owner;
                if (limits.maxWaitingConnections() != 1 || limits.mainQuantaPerTick() != 1
                        || limits.admissionWaitMillis() != 45_000 || limits.maxUuids() < 2
                        || limits.maxSealedSnapshots() != 1 || limits.maxSealedBytes() != 1) {
                    fault(run, "C6_WRONG_STARTUP_LIMITS");
                }
                if (run.limits == null) {
                    run.limits = Map.of("K", limits.maxWaitingConnections(), "q", limits.mainQuantaPerTick(),
                            "admissionWaitMillis", limits.admissionWaitMillis(), "tryBurst", limits.tryBurst(),
                            "tryRefill", limits.tryRefillPerSecond(), "statusBurst", limits.statusBurst(),
                            "statusRefill", limits.statusRefillPerSecond(), "T", limits.maxUuids(),
                            "sealedSnapshots", limits.maxSealedSnapshots(), "sealedBytes", limits.maxSealedBytes());
                }
                int mask = 0;
                for (var bound : captured) {
                    var sample = bound.entry();
                    int role = bound.role();
                    if (run.controls[role] != null && run.controls[role] != sample.control()) {
                        fault(run, "C6_CONTROL_REPLACED"); continue;
                    }
                    run.controls[role] = sample.control();
                    if (bound.epoch() > 0) { run.epochs[role] = bound.epoch(); }
                    run.buckets.put(bound.tryRate(), role * 2);
                    run.buckets.put(bound.statusRate(), role * 2 + 1);
                    run.waits.put(bound.waiting(), role);
                    if (sample.waiting()) { mask |= 1 << role; }
                    if (sample.capacityRejected()) { mask |= 4 << role; }
                }
                if (k != run.lastK || mask != run.lastMask) {
                    append(run, Map.of("event", "LIVE_K", "tick", nativeTick(run), "K", k,
                            "waitingAndRejectionBits", mask));
                    run.lastK = k; run.lastMask = mask;
                }
                if (k < 0 || k > 1) { fault(run, "C6_K_OUT_OF_RANGE"); }
            }
        } catch (RuntimeException | Error observerFailure) { failed(run, "C6_LIVE_OBSERVER"); }
    }

    public static Object offering(Object control, Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request)) { return null; }
        int role;
        synchronized (run) { role = controlRole(run, control); }
        if (role < 0) { return null; }
        var call = new OfferCall(run, role, request, OFFER.get());
        OFFER.set(call);
        return call;
    }

    public static void offered(Object token, Object result, boolean normal) {
        if (!(token instanceof OfferCall call)) { return; }
        try {
            synchronized (call.run) {
                append(call.run, Map.of("event", "OFFER", "role", call.role, "request", call.request,
                        "normal", normal, "result", result instanceof Enum<?> e ? e.name() : "NO_RETURN"));
            }
        } catch (RuntimeException | Error observerFailure) { failed(call.run, "C6_OFFER_OBSERVER"); }
        finally { if (call.previous == null) { OFFER.remove(); } else { OFFER.set(call.previous); } }
    }

    public static void took(Object bucket, long now, long before, long after, Object value) {
        var call = OFFER.get();
        if (call == null) { return; }
        var run = call.run;
        try {
            synchronized (run) {
                Integer owner = run.buckets.get(bucket);
                if (owner == null || owner / 2 != call.role
                        || owner % 2 != (call.request.command() == Command.TRY ? 0 : 1)) {
                    fault(run, "C6_BUCKET_IDENTITY_MISMATCH"); return;
                }
                append(run, Map.of("event", "TOKEN_TAKE", "role", call.role,
                        "command", call.request.command(), "n", call.request.requestSeq(),
                        "now", now, "beforeUnits", before, "afterUnits", after,
                        "result", ((P11ControlBudgets.RateResult) value).name()));
                if (value == P11ControlBudgets.RateResult.ACCEPTED) { run.accepted[owner]++; }
                if (value == P11ControlBudgets.RateResult.RATE_LIMITED) { run.limited[owner]++; }
            }
        } catch (RuntimeException | Error observerFailure) { failed(run, "C6_BUCKET_OBSERVER"); }
    }

    public static void polled(Object dispatcher, long tick, List<Long> before, int used,
            Object supplied) {
        var run = active;
        if (run == null || !run.server.isSameThread()) { return; }
        try {
            var result = (java.util.Optional<?>) supplied;
            if (result.isEmpty()) { return; }
            long selected = ((P11ControlBudgets.FairDispatcher.Dispatch) result.orElseThrow()).connectionId();
            synchronized (run) {
                if (selected != run.epochs[0] && selected != run.epochs[1]) { return; }
                if (run.dispatcher != null && run.dispatcher != dispatcher) { fault(run, "C6_DISPATCHER_REPLACED"); }
                run.dispatcher = dispatcher;
                if (tick != nativeTick(run) || used != 1 || before.isEmpty() || before.getFirst() != selected) {
                    fault(run, "C6_Q1_OR_FIFO_VIOLATION");
                }
                if (tick == run.lastDispatchTick) { fault(run, "C6_SECOND_QUANTUM_SAME_REAL_TICK"); }
                run.lastDispatchTick = tick;
                boolean both = before.contains(run.epochs[0]) && before.contains(run.epochs[1]);
                if (both) { run.bothQueuedPolls++; }
                run.services[selected == run.epochs[0] ? 0 : 1]++;
                append(run, Map.of("event", "POLL", "tick", tick, "before", before,
                        "selected", selected, "usedQuanta", used, "bothActuallyQueued", both));
            }
        } catch (RuntimeException | Error observerFailure) { failed(run, "C6_POLL_OBSERVER"); }
    }

    public static void completed(Object dispatcher, Object value, boolean more, boolean result, List<Long> after) {
        var run = active;
        if (run == null || run.dispatcher != dispatcher) { return; }
        try {
            long epoch = ((P11ControlBudgets.FairDispatcher.Dispatch) value).connectionId();
            synchronized (run) {
                append(run, Map.of("event", "COMPLETE", "tick", nativeTick(run), "selected", epoch,
                        "stillEligible", more, "normalResult", result, "after", after));
            }
        } catch (RuntimeException | Error observerFailure) { failed(run, "C6_COMPLETE_OBSERVER"); }
    }

    public static void service(Object control, Object value) {
        var run = active;
        if (run == null) { return; }
        try {
            synchronized (run) {
                int role = controlRole(run, control);
                if (role < 0 || value == P11TransitionControl.Service.NONE) { return; }
                if (value == P11TransitionControl.Service.INBOX) { run.inbox[role]++; }
                if (value == P11TransitionControl.Service.NOTIFICATION) { run.notifications[role]++; }
                append(run, Map.of("event", "NEXT_SERVICE", "role", role, "tick", nativeTick(run),
                        "kind", ((P11TransitionControl.Service) value).name()));
            }
        } catch (RuntimeException | Error observerFailure) { failed(run, "C6_SERVICE_OBSERVER"); }
    }

    public static void waited(Object wait, boolean refusal, long now, Object result, WaitSample sample) {
        var run = active;
        if (run == null) { return; }
        try {
            synchronized (run) {
                Integer role = run.waits.get(wait);
                if (role == null) { return; }
                if (!sample.started()) { return; }
                P11C4aC6ExpiryProbe.waited(run.server, run.connections[role], wait, refusal, now, result, sample);
                if (run.startedWait[role] == null) { run.startedWait[role] = wait; run.starts[role] = sample.start(); }
                else if (run.startedWait[role] != wait || run.starts[role] != sample.start()) {
                    fault(run, "C6_REFUSAL_INTERVAL_REPLACED");
                }
                var kind = (P11ControlBudgets.WaitResult) result;
                if (sample.unavailable()) { fault(run, "C6_CLOCK_UNAVAILABLE"); }
                if (kind == P11ControlBudgets.WaitResult.WAITING && now >= 0) { run.lastWaiting[role] = now; }
                if (kind == P11ControlBudgets.WaitResult.EXPIRED && run.expiredAt[role] == 0) { run.expiredAt[role] = now; }
                // Keep every refusal and terminal, plus one real observation per elapsed second.
                long second = now < 0 ? -1 : now / 1000;
                if (refusal || kind != P11ControlBudgets.WaitResult.WAITING || second != run.lastWaitSecond[role]) {
                    append(run, Map.of("event", refusal ? "REFUSED" : "OBSERVE_WAIT", "role", role,
                            "now", now, "result", kind.name(), "fields", sample));
                    run.lastWaitSecond[role] = second;
                }
            }
        } catch (RuntimeException | Error observerFailure) { failed(run, "C6_WAIT_OBSERVER"); }
    }

    /** Saves the observations as observations, never infers missing native proof from a cue. */
    static void write(String leaf) throws IOException {
        var run = active;
        P11C4aEvidence.require(run != null && run.server.isSameThread(), "C6_REPORT_OWNER");
        Map<String, Object> report;
        synchronized (run) {
            var values = new LinkedHashMap<String, Object>();
            values.put("status", "BOUNDED_ACTUAL_NATIVE_OBSERVATIONS_NOT_AUTOMATIC_ACCEPTANCE");
            values.put("failure", run.failure == null ? "NONE" : run.failure);
            values.put("limits", run.limits); values.put("events", List.copyOf(run.events));
            values.put("services", run.services.clone()); values.put("inbox", run.inbox.clone());
            values.put("notifications", run.notifications.clone()); values.put("bothQueuedPolls", run.bothQueuedPolls);
            values.put("acceptedTryStatusByRole", run.accepted.clone()); values.put("limitedTryStatusByRole", run.limited.clone());
            values.put("firstRefusalStarts", run.starts.clone()); values.put("lastWaiting", run.lastWaiting.clone());
            values.put("firstExpiry", run.expiredAt.clone()); values.put("fullC4aAcceptance", false);
            report = java.util.Collections.unmodifiableMap(values);
        }
        P11C4aEvidence.write(run.output, leaf, report);
    }

    static void release() { var run = active; if (run != null) {
        P11C4aEvidence.require(run.server.isSameThread() && OFFER.get() == null, "C6_RELEASE_OWNER");
        P11C4aC6ExpiryProbe.release(); active = null;
    } }
    private static long nativeTick(Run run) { return Integer.toUnsignedLong(run.server.getTickCount()); }
    private static int role(Run run, Connection connection) { return connection == run.connections[0] ? 0 : connection == run.connections[1] ? 1 : -1; }
    private static int controlRole(Run run, Object control) { return control == run.controls[0] ? 0 : control == run.controls[1] ? 1 : -1; }
    private static void fault(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void failed(Run run, String code) { synchronized (run) { fault(run, code); } }
    private static void append(Run run, Map<String, Object> event) {
        if (run.events.size() >= 4096) { fault(run, "C6_OBSERVATION_BOUND_EXHAUSTED"); return; }
        run.events.add(event);
    }
    private static final class Run {
        final MinecraftServer server; final Connection[] connections; final Path output;
        final Object[] controls = new Object[2], startedWait = new Object[2];
        final IdentityHashMap<Object, Integer> buckets = new IdentityHashMap<>(), waits = new IdentityHashMap<>();
        final ArrayList<Map<String, Object>> events = new ArrayList<>();
        final long[] epochs = new long[2], services = new long[2], inbox = new long[2], notifications = new long[2];
        final long[] starts = new long[2], lastWaiting = new long[2], expiredAt = new long[2], lastWaitSecond = {-2, -2};
        final long[] accepted = new long[4], limited = new long[4];
        Object owner, dispatcher; Map<String, Object> limits; String failure;
        int lastK = -1, lastMask = -1, bothQueuedPolls; long lastDispatchTick = -1;
        Run(MinecraftServer server, Connection a, Connection b, Path output) {
            this.server = server; connections = new Connection[] {a, b}; this.output = output;
        }
    }
    private record OfferCall(Run run, int role, Request request, OfferCall previous) { }
    private record BoundSample(EntrySample entry, int role, long epoch, Object tryRate, Object statusRate, Object waiting) { }
}
