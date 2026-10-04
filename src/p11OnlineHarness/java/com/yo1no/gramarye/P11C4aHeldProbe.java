package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Excluded, three real held windows. No production owner, permit, counter or root is written. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aHeldProbe {
    enum Window { INBOX, DRAIN, RUNNING }
    private static volatile Run active;
    private static final ThreadLocal<Ingress> INGRESS = new ThreadLocal<>();
    private P11C4aHeldProbe() { }

    static boolean selected() { return P11C4aScenario.MODE == P11C4aScenario.Mode.HELD_NEGATIVES; }
    static boolean started() { return active != null; }

    static void start(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(selected() && P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && actor != peer && !actor.getUUID().equals(peer.getUUID()), "HELD_START_OWNER");
        exact(server, actor); exact(server, peer);
        var run = new Run(server, actor, peer, output);
        active = run;
        server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, server);
        prepare(run, actor, 1);
        P11C4aEvidence.cue(output, "b-held-arm.ready");
    }

    /** Normal server ticks only; nested managedBlock never calls this method as a substitute tick. */
    static boolean tick(ServerPlayer current) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 3600, "HELD_SERVER_DEADLINE");
        if (run.failed != null) { throw new IllegalStateException(run.failed); }
        if (run.complete) { return run.logouts == 2; }
        exact(run.server, run.peer);
        require(run.peer.connection.getConnection() == run.peerConnection && run.peer.isAlive()
                && run.peer.getAdvancements() == run.peerCanonical
                && P11C4aNativeObservations.tries(run.peerConnection) == run.peerTries,
                "HELD_PEER_CHANGED");
        var e = run.episode;
        if (!e.killed) {
            if (!P11C4aEvidence.receiptPresent(run.client, "held-armed-" + e.index + ".json")) { return false; }
            require(current == e.actor && current.isAlive(), "HELD_ORIGINAL_DEATH_OWNER");
            e.killed = true; current.kill();
            require(current.isDeadOrDying(), "HELD_ORIGINAL_DEATH_MISSING");
            return false;
        }
        if (e.completed == null || !P11C4aEvidence.receiptPresent(run.client, "held-client-" + e.index + ".json")) { return false; }
        exact(run.server, current);
        require(e.failure == null && e.reloadReturned && e.released && e.sameOffers >= 1 && e.differentOffers == 1
                && e.tries == 1 && e.bodies == 1 && e.frames == 1 && e.reserves == 1
                && e.managedEntries == 1 && e.managedReturns == 1 && e.registrations == 1 && e.applications == 1
                && e.control != null && !e.control.executableHeld() && e.control.fence() == e.request.requestSeq()
                && current != e.actor && current.getUUID().equals(e.actor.getUUID())
                && current.connection.getConnection() == run.connection && current.isAlive()
                && current.getAdvancements() == run.canonical && current.getStats() == run.statistics
                && source(current).source.epoch() == e.sourceEpoch + 1,
                "HELD_TERMINAL_COUNTS_OR_SOURCE");
        P11C4aEvidence.write(run.output, "held-server-" + e.index + ".json", report(e));
        run.lastSequence = e.request.requestSeq();
        if (e.index < 3) { prepare(run, current, e.index + 1); return false; }
        require(run.server.saveEverything(true, false, false), "HELD_ORIGINAL_SAVE_FAILED");
        run.finalActor = current; run.complete = true; restore(run);
        P11C4aEvidence.write(run.output, "held-subset.json", Map.of(
                "status", "THREE_ORIGINAL_HELD_WINDOWS_COMPLETED_NOT_FULL_C4A", "windows", 3,
                "sameSequence", "ACTUAL_PERIODIC_STATUS", "differentSequence", "ONE_ORIGINAL_STATUS_ENCODE_N_TO_N_PLUS_ONE_PER_WINDOW",
                "newRequestSender", false, "unknownInjected", false, "peerUnchanged", true));
        P11C4aEvidence.cue(run.output, "b-held-finish.ready");
        return false;
    }

    static void logout(ServerPlayer actor) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.complete
                && actor == (run.logouts == 0 ? run.peer : run.finalActor) && ++run.logouts <= 2,
                "HELD_UNEXPECTED_LOGOUT");
        if (run.logouts == 1) { P11C4aEvidence.cue(run.output, "a-held-finish.ready"); }
    }

    static void stopped(MinecraftServer server) throws IOException {
        var run = active;
        if (run == null || run.server != server) { return; }
        P11C4aEvidence.write(run.output, "held-stopped.json", Map.of("originalServerStopped", true,
                "allThreeWindowsComplete", run.complete, "nativeLogouts", run.logouts,
                "failure", run.failed == null ? "NONE" : run.failed));
        active = null;
    }

    static void abort() {
        var run = active;
        if (run == null) { return; }
        run.failed = "HELD_OWNER_ABORT";
        run.episode.gate.completeExceptionally(new IllegalStateException(run.failed));
        if (run.server.isSameThread()) { restore(run); }
    }

    private static void prepare(Run run, ServerPlayer actor, int index) throws IOException {
        run.episode = new Episode(actor, index, source(actor).source.epoch());
        P11C4aEvidence.cue(run.output, "a-held-prepare-" + index + ".ready");
    }

    /** Call-local provenance surrounds only original ingress; its finally is outside the control monitor. */
    public static Object beginIngress(Object value, Connection connection, ICommonPacketListener listener) {
        var run = active;
        if (run == null || !selected() || connection != run.connection || !(value instanceof Request request)) { return null; }
        var e = run.episode;
        if (!e.killed || run.complete) { return null; }
        var scope = new Ingress(run, e, request, INGRESS.get());
        INGRESS.set(scope);
        if (connection.getPacketListener() != listener || listener.getConnection() != connection
                || request.scope() != Scope.PLAY || request.kind() != Kind.DEATH) { fail(e, "HELD_INGRESS_ROUTE"); }
        if (request.command() == Command.TRY) {
            if (++e.tries != 1) { fail(e, "HELD_SECOND_TRY"); }
            else {
                e.request = request;
                if (e.index > 1 && request.requestSeq() != run.lastSequence + 1) { fail(e, "HELD_NEXT_NATIVE_SEQUENCE_POISONED"); }
            }
        }
        return scope;
    }

    public static void endIngress(Object value, Throwable primary) {
        if (!(value instanceof Ingress scope)) { return; }
        var e = scope.episode;
        try {
            if (scope.previous == null) { INGRESS.remove(); } else { INGRESS.set(scope.previous); }
            if (primary != null) { fail(e, "HELD_ORIGINAL_INGRESS_THREW"); }
            if (e.control != null && Thread.holdsLock(e.control)) { fail(e, "HELD_GATE_INSIDE_CONTROL_MONITOR"); return; }
            if (e.failure != null) {
                e.gate.completeExceptionally(primary != null ? primary : new IllegalStateException(e.failure)); return;
            }
            if (e.waiting && e.sameOffers >= 1 && e.differentOffers == 1 && !e.released) {
                if (!e.control.executableHeld() || !e.differentAfter.equals(e.control.state().orElse(null))
                        || e.control.fence() != e.fenceAtWindow || e.frames != 0
                        || e.bodies != (e.window == Window.RUNNING ? 1 : 0)) {
                    fail(e, "HELD_INGRESS_RETURN_CHANGED_OWNER_OR_BODY");
                    e.gate.completeExceptionally(new IllegalStateException(e.failure)); return;
                }
                // Publish the observation before completing the future can wake native managedBlock.
                e.released = true;
                if (!e.gate.complete(null)) { fail(e, "HELD_GATE_ALREADY_TERMINAL"); }
            }
        } catch (RuntimeException | Error secondary) { fail(e, "HELD_INGRESS_FINALLY_OBSERVER"); }
    }

    public static Object beforeOffer(Object owner, Object request) {
        var scope = INGRESS.get();
        if (scope == null || scope.request != request || !(owner instanceof P11TransitionControl control)) { return null; }
        var e = scope.episode;
        if (e.control == null) { e.control = control; }
        else if (e.control != control) { fail(e, "HELD_CONTROL_OWNER_CHANGED"); }
        return new OfferObservation(e, control, control.state().orElse(null), control.fence());
    }

    /** Scalar snapshots only: no completion, IO, native call, callback or root acquisition here. */
    public static void afterOffer(Object value, Object result) {
        if (!(value instanceof OfferObservation observation)) { return; }
        try { observeOffer(observation, result); }
        catch (RuntimeException | Error secondary) { fail(observation.episode, "HELD_OFFER_OBSERVER"); }
    }

    private static void observeOffer(OfferObservation observation, Object result) {
        var e = observation.episode;
        var scope = INGRESS.get();
        if (scope == null || scope.episode != e) { fail(e, "HELD_OFFER_SCOPE_LOST"); return; }
        var request = scope.request;
        if (request.command() == Command.TRY) {
            if (result != P11TransitionControl.Offer.RETAINED) { fail(e, "HELD_FIRST_TRY_NOT_RETAINED"); }
            return;
        }
        // Late ordinary STATUS after the held window may legitimately race native completion.
        // Only the selected live window is this negative experiment's observation scope.
        if (!e.waiting || e.released) { return; }
        var after = observation.control.state().orElse(null);
        if (!sameScene(e.request, request) || !observation.control.executableHeld()
                || after == null || !after.equals(observation.before) || observation.control.fence() != observation.fence
                || after.requestSeq() != e.request.requestSeq()
                || after.outcome() != (e.window == Window.RUNNING ? Outcome.RUNNING : Outcome.PENDING)
                || e.frames != 0 || e.bodies != (e.window == Window.RUNNING ? 1 : 0)) {
            fail(e, "HELD_STATUS_CHANGED_OWNER_RECORD_OR_NATIVE_BODY"); return;
        }
        if (request.requestSeq() == e.request.requestSeq() && result == P11TransitionControl.Offer.COALESCED) {
            if (++e.sameOffers > 40) { fail(e, "HELD_EXCESS_SAME_STATUS"); }
            e.sameBefore = observation.before; e.sameAfter = after;
        } else if (request.requestSeq() == e.request.requestSeq() + 1 && result == P11TransitionControl.Offer.BUSY) {
            if (++e.differentOffers != 1) { fail(e, "HELD_DUPLICATE_DIFFERENT_STATUS"); }
            e.differentBefore = observation.before; e.differentAfter = after;
        } else { fail(e, "HELD_UNEXPECTED_STATUS_RESULT"); }
    }

    /** Unique service→reserve callsite, deliberately outside the synchronized native control method. */
    public static Optional<?> reserve(Object owner, Object listener, Operation<Optional<?>> original) {
        var run = active;
        var e = run == null ? null : run.episode;
        if (e == null || e.control != owner || e.request == null || e.windowStarted || run.complete) {
            return original.call(owner, listener);
        }
        require(run.server.isSameThread() && !Thread.holdsLock(owner), "HELD_RESERVE_NOT_OUTSIDE_MONITOR");
        if (e.window == Window.INBOX) { reload(run, e); }
        Optional<?> reserved = original.call(owner, listener);
        require(reserved.isPresent() && reserved.get() instanceof P11TransitionControl.Drain drain
                && drain.request() == e.request && ++e.reserves == 1, "HELD_ORIGINAL_DRAIN_NOT_EXACT");
        if (e.window == Window.DRAIN) { reload(run, e); }
        return reserved;
    }

    /** Original ticket verification has returned; no copy/remove/new-B side effect has started here. */
    public static void body(ServerPlayer actor) {
        var run = active;
        if (run == null || run.complete || run.episode.actor != actor) { return; }
        var e = run.episode;
        require(++e.bodies == 1 && e.reserves == 1 && e.request != null, "HELD_BODY_WITHOUT_EXACT_DRAIN");
        if (e.window == Window.RUNNING) { reload(run, e); }
        else { require(e.reloadReturned && e.released, "HELD_BODY_BEFORE_GATE_RETURN"); }
    }

    private static void reload(Run run, Episode e) {
        require(run.server.isSameThread() && !e.windowStarted && e.control.executableHeld()
                && !Thread.holdsLock(e.control) && e.control.state().orElseThrow().requestSeq() == e.request.requestSeq()
                && e.control.state().orElseThrow().outcome() == (e.window == Window.RUNNING ? Outcome.RUNNING : Outcome.PENDING),
                "HELD_WINDOW_NOT_NATIVE_HELD");
        e.windowStarted = true;
        e.fenceAtWindow = e.control.fence();
        e.transitionRootsAtWindow = source(e.actor).account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()];
        require(e.window != Window.RUNNING || e.transitionRootsAtWindow > 0, "HELD_RUNNING_WITHOUT_TRUE_CUSTODY");
        e.reloadActive = true; e.gate.orTimeout(30, TimeUnit.SECONDS);
        try {
            run.server.reloadResources(List.copyOf(run.server.getPackRepository().getSelectedIds())).join();
            e.reloadReturned = true;
            require(e.failure == null && e.released && e.sameOffers >= 1 && e.differentOffers == 1
                    && e.frames == 0 && e.bodies == (e.window == Window.RUNNING ? 1 : 0), "HELD_RELOAD_RETURN_WITHOUT_NEGATIVES");
        } finally { e.reloadActive = false; }
    }

    @SubscribeEvent static void registration(AddReloadListenerEvent event) {
        var run = active; var e = run == null ? null : run.episode;
        if (e == null || !e.reloadActive) { return; }
        synchronized (e) {
            if (active != run || run.episode != e || !e.reloadActive) { return; }
            require(++e.registrations == 1, "HELD_RELOAD_REGISTRATION_DUPLICATE");
        }
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparation, ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == run && run.episode == e && e.reloadActive && run.server.isSameThread()
                            && e.inManaged && ++e.applications == 1, "HELD_RELOAD_APPLICATION_OWNER");
                    e.waiting = true;
                    try { P11C4aEvidence.cue(run.output, "a-held-window-" + e.index + ".ready"); }
                    catch (IOException failure) { fail(e, "HELD_CUE_IO_FAILURE"); e.gate.completeExceptionally(failure); }
                    return e.gate;
                }, game);
            }
        });
    }

    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var run = active; var e = run == null ? null : run.episode;
        if (e == null || run.server != server || !e.reloadActive) { return; }
        if (entering) { e.managedEntries++; e.inManaged = true; }
        else { e.inManaged = false; if (normal) { e.managedReturns++; } }
    }

    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || run.complete || listener.getConnection() != run.connection) { return; }
        var e = run.episode;
        if (packet instanceof ClientboundRespawnPacket) { e.frames++; }
        if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof P11TransitionStatePayload payload) {
            var state = payload.state();
            if (state.outcome() == Outcome.COMPLETED && state.kind() == Kind.DEATH) {
                if (e.request == null || state.sceneSerial() != e.request.sceneSerial()) { return; }
                if (!sameScene(e.request, state) || state.requestSeq() != e.request.requestSeq()) {
                    fail(e, "HELD_COMPLETION_NOT_ORIGINAL_REQUEST");
                } else { e.completed = state; }
            }
        }
    }

    private static void exact(MinecraftServer server, ServerPlayer actor) {
        require(server.isSameThread() && actor.getServer() == server && !actor.isFakePlayer()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && actor.connection != null
                && actor.connection.getConnection().isConnected() && !actor.connection.getConnection().isMemoryConnection()
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "HELD_EXACT_AUTHENTICATED_ROSTER_ACTOR");
    }
    private static P11QualifiedSourceOwner.Body source(ServerPlayer actor) {
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.nativeRecipient(actor);
        require(body != null && body.actor == actor && body.complete, "HELD_EXACT_SOURCE"); return body;
    }
    private static boolean sameScene(Request a, Request b) {
        return a != null && a.scope() == b.scope() && a.connectionEpoch() == b.connectionEpoch()
                && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind();
    }
    private static boolean sameScene(Request a, State b) {
        return a != null && a.scope() == b.scope() && a.connectionEpoch() == b.connectionEpoch()
                && a.sceneSerial() == b.sceneSerial() && a.actorGeneration() == b.actorGeneration() && a.kind() == b.kind();
    }
    private static Map<String, Object> report(Episode e) {
        var map = new LinkedHashMap<String, Object>();
        map.put("status", "ACTUAL_HELD_ORIGINAL_INGRESS_NEGATIVES_AND_NATIVE_TERMINAL"); map.put("window", e.window.name());
        map.put("request", e.request); map.put("sameOffers", e.sameOffers); map.put("differentOffers", e.differentOffers);
        map.put("sameBefore", e.sameBefore); map.put("sameAfter", e.sameAfter);
        map.put("differentBefore", e.differentBefore); map.put("differentAfter", e.differentAfter);
        map.put("sameOffer", "COALESCED"); map.put("differentOffer", "BUSY");
        map.put("originalReserveCalls", e.reserves); map.put("actualTryIngress", e.tries); map.put("ticketBodyEntries", e.bodies);
        map.put("nativeRespawnFrames", e.frames); map.put("completed", e.completed); map.put("sourceEpochBefore", e.sourceEpoch);
        map.put("transitionRootsAtWindow", e.transitionRootsAtWindow);
        map.put("managedBlockEntries", e.managedEntries); map.put("managedBlockNormalReturns", e.managedReturns);
        map.put("reloadListenerRegistrations", e.registrations); map.put("gameExecutorApplications", e.applications);
        map.put("gateCompletedAfterIngressReturn", e.released); map.put("fixtureMutatesOnlyOneStatusSequence", true);
        map.put("fenceAtHeldWindow", e.fenceAtWindow);
        map.put("normalUiDifferentSequenceClaimed", false); map.put("fullC4aAcceptance", false); return map;
    }
    private static void restore(Run run) {
        run.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(run.keep, run.server);
        run.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(run.immediate, run.server);
    }
    private static void fail(Episode e, String code) { if (e.failure == null) { e.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private record Ingress(Run run, Episode episode, Request request, Ingress previous) { }
    private record OfferObservation(Episode episode, P11TransitionControl control, State before, long fence) { }
    private static final class Run {
        final MinecraftServer server; final Connection connection, peerConnection; final ServerPlayer peer;
        final Object canonical, statistics, peerCanonical; final Path output, client; final long peerTries;
        final boolean keep, immediate; volatile Episode episode; volatile String failed;
        int ticks, logouts; volatile long lastSequence; volatile boolean complete; ServerPlayer finalActor;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) {
            this.server = server; this.peer = peer; this.output = output; client = output.resolveSibling("client-a");
            connection = actor.connection.getConnection(); peerConnection = peer.connection.getConnection();
            canonical = actor.getAdvancements(); statistics = actor.getStats(); peerCanonical = peer.getAdvancements();
            peerTries = P11C4aNativeObservations.tries(peerConnection);
            keep = server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            immediate = server.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        }
    }
    private static final class Episode {
        final ServerPlayer actor; final int index; final Window window; final long sourceEpoch;
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        volatile P11TransitionControl control; volatile Request request; volatile String failure;
        volatile State sameBefore, sameAfter, differentBefore, differentAfter, completed;
        volatile int tries, sameOffers, differentOffers, bodies, frames, registrations, applications;
        volatile boolean reloadActive, waiting, released;
        volatile boolean killed; boolean windowStarted, reloadReturned, inManaged; int reserves, managedEntries, managedReturns;
        long transitionRootsAtWindow, fenceAtWindow;
        Episode(ServerPlayer actor, int index, long epoch) { this.actor = actor; this.index = index; sourceEpoch = epoch; window = Window.values()[index - 1]; }
    }
}
