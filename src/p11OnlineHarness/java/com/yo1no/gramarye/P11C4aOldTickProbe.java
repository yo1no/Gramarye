package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Excluded: a real due old tick spans one same-PLAY handoff; native fields and clocks are read only. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aOldTickProbe {
    private static volatile Run active;
    private P11C4aOldTickProbe() { }
    private record Frame(long time, boolean pending, long challenge, int latency, long phase) { }

    static void start(MinecraftServer server, ServerPlayer actor, Path output) {
        require(active == null && P11C4aEvidence.enabled() && server.isSameThread()
                && actor.connection.getConnection().isConnected()
                && !actor.connection.getConnection().isMemoryConnection(), "OLD_TICK_START_OWNER");
        active = new Run(server, actor, output);
    }

    /** Existing parking producer has already observed a real matching ACK and MAY. */
    static void arm(P11ParkingPacketListener parking) {
        var r = active;
        require(r != null && r.server.isSameThread() && parking.getConnection() == r.connection
                && r.connection.getPacketListener() == parking && unlocked(r), "OLD_TICK_ARM_OWNER");
        if (r.parking != null) { require(r.parking == parking, "OLD_TICK_CHANGED_PARKING"); return; }
        r.before = frame(parking);
        require(!r.before.pending && r.before.challenge > 0 && r.before.phase > 0, "OLD_TICK_NO_ACTUAL_CONSUMED_CHALLENGE");
        r.parking = parking;
    }

    /** Called after the one original Util.getMillis() returned, before the first native field read. */
    public static void clock(ServerCommonPacketListenerImpl caller, long originalNow) {
        var r = active;
        if (r == null || caller != r.parking || r.started) { return; }
        require(r.server.isSameThread() && r.connection.getPacketListener() == caller && unlocked(r),
                "OLD_TICK_CLOCK_OWNER_OR_LOCK");
        require(frame(caller).equals(r.before), "OLD_TICK_FIELDS_CHANGED_BEFORE_DUE");
        if (originalNow - r.before.time < 15000L) { return; }
        r.started = true; r.originalNow = originalNow; r.capturedTicks++;
        require(rootsZero(r), "OLD_TICK_ROOTS_NOT_ZERO_BEFORE_RELOAD");
        r.decoder = transport(r).c4a$decoder(); r.autoRead = transport(r).c4a$autoRead();
        require(r.decoder != null && r.autoRead, "OLD_TICK_TRANSPORT_NOT_READY");
        r.reloadActive = true;
        r.gate.orTimeout(5, TimeUnit.SECONDS);
        try {
            r.server.reloadResources(List.copyOf(r.server.getPackRepository().getSelectedIds())).join();
            r.reloadReturned = true;
            require(r.failure == null && r.callerTerminal != null && r.executeReturns == 1
                    && r.installs == 1 && r.factories == 1
                    && r.managedEntries == 1 && r.managedReturns == 1 && r.registrations == 1 && r.applications == 1
                    && r.next != null && r.connection.getPacketListener() == r.next
                    && r.protocolInstalls == 0 && frame(r.parking).equals(r.before) && frame(r.next).equals(r.before),
                    "OLD_TICK_RELOAD_WITHOUT_COMPLETED_HANDOFF");
        } catch (RuntimeException | Error failure) {
            if (r.failure == null) { r.failure = "OLD_TICK_RELOAD_OR_INTERLEAVING_UNPROVEN"; }
            throw failure;
        } finally { r.reloadActive = false; }
    }

    @SubscribeEvent static void registration(AddReloadListenerEvent event) {
        var r = active;
        if (r == null || !r.reloadActive) { return; }
        synchronized (r) {
            if (active != r || !r.reloadActive) { return; }
            require(++r.registrations == 1, "OLD_TICK_RELOAD_DUPLICATE");
        }
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier,
                    net.minecraft.server.packs.resources.ResourceManager resources,
                    net.minecraft.util.profiling.ProfilerFiller preparation,
                    net.minecraft.util.profiling.ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == r && r.reloadActive && r.server.isSameThread() && r.inManaged
                            && unlocked(r) && ++r.applications == 1 && rootsZero(r),
                            "OLD_TICK_GAME_APPLICATION_OWNER_OR_ROOT");
                    r.waiting = true;
                    try { P11C4aParkingProbe.releaseOldTickRetryCue(); }
                    catch (IOException failure) { fail(r, "OLD_TICK_CUE_IO"); r.gate.completeExceptionally(failure); }
                    return r.gate;
                }, game);
            }
        });
    }

    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var r = active;
        if (r == null || r.server != server || !r.reloadActive) { return; }
        require(unlocked(r), "OLD_TICK_MANAGED_UNDER_FIELD_LOCK");
        if (entering) { r.inManaged = true; r.managedEntries++; }
        else { r.inManaged = false; if (normal) { r.managedReturns++; } }
    }

    /** Observes the real private gate's return, never computes or grants admission. */
    public static void gate(Object entry, Object result) {
        var r = active;
        if (r == null || !r.waiting || !r.reloadActive
                || !(entry instanceof P11C4aParkingProbe.EntryAccess access)
                || access.c4a$parkingConnection() != r.connection) { return; }
        require(r.server.isSameThread() && r.inManaged && unlocked(r), "OLD_TICK_GATE_OWNER");
        require(entry instanceof P11C4aC6NativeProbe.EntryView, "OLD_TICK_REAL_ENTRY_VIEW_MISSING");
        var control = (P11TransitionControl) ((P11C4aC6NativeProbe.EntryView) entry).c6$control();
        require(r.control == null || r.control == control, "OLD_TICK_CHANGED_CONTROL");
        r.entry = entry; r.control = control;
        r.lastGate = result;
        if (result == P11TransitionControl.Gate.ALLOW) {
            require(rootsZero(r), "OLD_TICK_GATE_ALLOW_WITH_ROOTS");
            r.allowObservations++;
        }
    }

    /** Normal original execute TAIL is after finish, custody/drain release and call restoration.
     * The notification may need a later q=1 quantum: it cannot be this reload's release condition. */
    public static void executeReturned(Object entry) {
        var r = active;
        if (r == null || !r.waiting || !r.reloadActive || entry != r.entry) { return; }
        require(r.server.isSameThread() && r.inManaged && unlocked(r) && r.control != null
                && !r.control.executableHeld() && r.next != null && r.connection.getPacketListener() == r.next
                && r.installs == 1 && r.factories == 1 && ++r.executeReturns == 1,
                "OLD_TICK_EXECUTE_WITHOUT_ORIGINAL_TERMINAL");
        var state = r.control.state().orElseThrow();
        require(state.kind() == Kind.RETURN_TO_WORLD && state.outcome() == Outcome.COMPLETED
                && state.requestSeq() > 0 && state.targetActorGeneration() > 0,
                "OLD_TICK_CALLER_NOT_COMPLETED");
        r.callerTerminal = state;
        require(r.gate.complete(null), "OLD_TICK_GATE_ALREADY_TERMINAL");
    }

    public static void factory() {
        var r = active;
        if (r == null || !r.waiting || !r.reloadActive) { return; }
        require(r.server.isSameThread() && r.inManaged && r.lastGate == P11TransitionControl.Gate.ALLOW
                && r.allowObservations > 0 && r.connection.getPacketListener() == r.parking && ++r.factories == 1,
                "OLD_TICK_FACTORY_WITHOUT_ORIGINAL_ALLOW");
    }

    public static void beforeInstall(Connection connection, P11ParkingPacketListener previous, ServerGamePacketListenerImpl next) {
        var r = active; if (r == null || connection != r.connection) { return; }
        require(r.server.isSameThread() && r.inManaged && unlocked(r) && r.waiting && previous == r.parking
                && connection.getPacketListener() == previous && frame(previous).equals(r.before)
                && r.factories == 1 && r.installs == 0, "OLD_TICK_INSTALL_OWNER");
    }
    public static void afterInstall(Connection connection, P11ParkingPacketListener previous, ServerGamePacketListenerImpl next, boolean normal) {
        var r = active; if (r == null || connection != r.connection) { return; }
        if (!normal) { fail(r, "OLD_TICK_ORIGINAL_INSTALL_THROW"); return; }
        require(r.server.isSameThread() && unlocked(r) && previous == r.parking
                && connection.getPacketListener() == next && frame(next).equals(r.before)
                && transport(r).c4a$decoder() == r.decoder && transport(r).c4a$autoRead()
                && ++r.installs == 1, "OLD_TICK_SAME_PLAY_FIELDS_OR_TRANSPORT");
        r.next = next;
    }

    public static void sent(ServerCommonPacketListenerImpl caller, Packet<?> packet, boolean returned) {
        var r = active; if (r == null || caller.getConnection() != r.connection || !r.started) { return; }
        if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
            require(caller != r.parking, "OLD_TICK_STALE_CHALLENGE_SEND");
            if (caller != r.next) { return; }
            if (!returned) {
                require(r.tickReturns == 1 && r.reloadReturned && unlocked(r) && r.connection.getPacketListener() == caller
                        && r.fresh == null, "OLD_TICK_FRESH_CHALLENGE_ORDER");
                r.fresh = frame(caller);
                require(r.fresh.pending && r.fresh.challenge == keepAlive.getId() && r.fresh.time == r.fresh.challenge
                        && r.fresh.time - r.before.time >= 15000L && r.fresh.phase == r.before.phase,
                        "OLD_TICK_FRESH_NOT_ORIGINAL_DUE_CHALLENGE");
            } else { require(r.fresh != null && r.fresh.challenge == keepAlive.getId() && ++r.freshSends == 1,
                    "OLD_TICK_FRESH_SEND_RETURN"); }
        }
        if (returned && packet instanceof ClientboundCustomPayloadPacket custom
                && custom.payload() instanceof P11TransitionStatePayload payload
                && payload.state().kind() == Kind.RETURN_TO_WORLD && payload.state().outcome() == Outcome.COMPLETED) {
            if (r.completion != null) { require(r.completion.equals(payload.state()), "OLD_TICK_CHANGED_COMPLETION"); return; }
            require(r.waiting && r.executeReturns == 1 && r.installs == 1 && caller == r.next
                    && r.connection.getPacketListener() == caller && payload.state().equals(r.callerTerminal),
                    "OLD_TICK_COMPLETION_BEFORE_HANDOFF");
            r.completion = payload.state();
        }
    }

    public static void tickReturned(ServerCommonPacketListenerImpl caller) {
        var r = active; if (r == null || caller != r.parking || !r.started || r.tickReturns != 0) { return; }
        require(r.reloadReturned && r.connection.getPacketListener() == r.next && unlocked(r)
                && frame(r.parking).equals(r.before) && frame(r.next).equals(r.before)
                && r.fresh == null && ++r.tickReturns == 1, "OLD_TICK_RETURN_MUTATED_OR_SENT");
    }

    public static void protocolInstall(Connection connection) {
        var r = active;
        if (r != null && connection == r.connection && r.started) { r.protocolInstalls++; }
    }

    public static Object beforeAck(ServerCommonPacketListenerImpl caller, long id) {
        var r = active;
        if (r == null || caller != r.next || r.fresh == null || id != r.fresh.challenge) { return null; }
        require(r.connection.getPacketListener() == caller && frame(caller).equals(r.fresh) && unlocked(r),
                "OLD_TICK_FRESH_ACK_INPUT");
        return r;
    }
    public static void afterAck(Object observed, boolean normal) {
        if (!(observed instanceof Run r)) { return; }
        if (!normal) { fail(r, "OLD_TICK_ORIGINAL_ACK_THROW"); return; }
        r.consumed = frame(r.next);
        require(r.connection.getPacketListener() == r.next && unlocked(r) && !r.consumed.pending
                && r.consumed.time == r.fresh.time && r.consumed.challenge == r.fresh.challenge
                && r.consumed.phase == r.fresh.phase && ++r.freshAcks == 1, "OLD_TICK_FRESH_ACK_NOT_CONSUMED");
    }

    static boolean complete() {
        var r = active;
        require(r != null && r.failure == null, "OLD_TICK_OBSERVER_FAILURE");
        return r.tickReturns == 1 && r.freshSends == 1 && r.freshAcks == 1
                && r.executeReturns == 1 && r.completion != null;
    }
    static Map<String, Object> report() {
        var r = active; if (r == null) { return Map.of("active", false); }
        var out = new LinkedHashMap<String, Object>();
        out.put("status", "ORIGINAL_OLD_TICK_INTERLEAVING_OBSERVATIONS_NOT_FULL_C4A");
        out.put("failure", r.failure == null ? "NONE" : r.failure);
        out.put("capturedDueTicks", r.capturedTicks); out.put("originalTickReturns", r.tickReturns);
        out.put("originalClockAtCapture", r.originalNow); out.put("unchangedTransferredFields", r.before);
        out.put("freshCurrentChallenge", r.fresh); out.put("afterOriginalCurrentAck", r.consumed);
        out.put("originalSamePlayInstalls", r.installs); out.put("originalFactoryChecks", r.factories);
        out.put("actualAllowObservationsWithRootsZero", r.allowObservations);
        out.put("originalManagedEntries", r.managedEntries); out.put("originalManagedReturns", r.managedReturns);
        out.put("reloadRegistrations", r.registrations); out.put("gameApplications", r.applications);
        out.put("freshNativeSends", r.freshSends); out.put("freshNativeAcks", r.freshAcks);
        out.put("originalExecuteNormalReturns", r.executeReturns); out.put("originalCallerTerminal", r.callerTerminal);
        out.put("completion", r.completion); out.put("sameDecoder", r.next != null && transport(r).c4a$decoder() == r.decoder);
        out.put("protocolInstallCallsDuringWindow", r.protocolInstalls); out.put("originalAutoRead", r.autoRead);
        out.put("clockFieldsOrChallengeWritten", false); out.put("scheduledSendRaceQualified", false);
        out.put("configResetQualified", false); out.put("fullC4aAcceptance", false); return out;
    }
    static void release() {
        var r = active; active = null;
        if (r != null && !r.gate.isDone()) { r.gate.completeExceptionally(new IllegalStateException("OLD_TICK_CLOSED")); }
    }
    private static boolean rootsZero(Run r) {
        var owner = P11NativeStorageBoundary.nativeSourceOwner(r.actor);
        var body = owner == null ? null : owner.body(r.actor);
        require(body != null && body.actor == r.actor && body.complete, "OLD_TICK_EXACT_BODY_MISSING");
        for (var root : P11ControlBudgets.Root.values()) { if (body.account.nativeCounts[root.ordinal()] != 0) { return false; } }
        return true;
    }
    private static Frame frame(ServerCommonPacketListenerImpl listener) {
        var f = (P11C4aParkingTransferProbe.Fields) listener;
        return new Frame(f.c4a$time(), f.c4a$pending(), f.c4a$challenge(), f.c4a$latency(),
                ((P11KeepAliveBoundary.CommonAccess) listener).p11$keepAlivePhase());
    }
    private static P11C4aParkingTransferProbe.Transport transport(Run r) { return (P11C4aParkingTransferProbe.Transport) r.connection; }
    private static boolean unlocked(Run r) { return !((P11C4aParkingTransferProbe.LockObservation)
            (Object) ((P11KeepAliveBoundary.ConnectionAccess) r.connection).p11$keepAliveGuard()).c4a$heldByCurrentThread(); }
    private static void fail(Run r, String code) { if (r.failure == null) { r.failure = code; } r.gate.completeExceptionally(new IllegalStateException(code)); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor; final Connection connection; final Path output;
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        volatile boolean reloadActive; volatile int registrations; volatile String failure;
        volatile P11ParkingPacketListener parking; volatile ServerGamePacketListenerImpl next; Frame before; volatile Frame fresh, consumed;
        Object decoder, lastGate, entry; P11TransitionControl control;
        boolean autoRead, started, waiting, inManaged, reloadReturned;
        State completion, callerTerminal; long originalNow;
        int capturedTicks, tickReturns, installs, factories, executeReturns, managedEntries, managedReturns, applications, allowObservations;
        volatile int freshSends, freshAcks, protocolInstalls;
        Run(MinecraftServer s, ServerPlayer a, Path o) { server=s; actor=a; output=o; connection=a.connection.getConnection(); }
    }
}
