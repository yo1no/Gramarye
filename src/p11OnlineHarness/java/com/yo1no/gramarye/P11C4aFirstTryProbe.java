package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Excluded finite ingress-loss experiment; never creates a control, attempt, identity or source. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aFirstTryProbe {
    private static final ThreadLocal<Ingress> INGRESS = new ThreadLocal<>();
    private static volatile Run active;

    private P11C4aFirstTryProbe() { }

    /** Integrate after the normal/reload scenes, whose actual positive sender sequence is retained. */
    static void start(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && server.isSameThread() && active == null
                && java.util.List.of("a", "host").contains(role), "FIRST_TRY_START_OWNER");
        require(!actor.isFakePlayer() && actor.getServer() == server
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "FIRST_TRY_NATIVE_ACTOR");
        var connection = actor.connection.getConnection();
        var prior = P11C4aNativeObservations.snapshot(connection).get("lastRequest");
        require(prior instanceof Request, "FIRST_TRY_NO_PRIOR_REAL_INGRESS");
        var request = (Request) prior;
        require(request.scope() == Scope.PLAY && request.kind() == Kind.DEATH
                && request.requestSeq() > 0 && request.requestSeq() < 40, "FIRST_TRY_BASELINE_NOT_RELOAD_TERMINAL");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        require(source != null && source.controlGate(actor.getUUID(), actor) == P11QualifiedSourceOwner.ControlGate.CLEAR,
                "FIRST_TRY_SOURCE_NOT_CLEAR");
        var run = new Run(server, actor, role, output, P11C4aEvidence.root().resolve("client-" + role), request);
        active = run;
        var rules = server.getGameRules();
        run.keepInventory = rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        run.immediate = rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
        P11C4aEvidence.write(output, "first-try-armed.json", report(run, "ARMED_NOT_ACCEPTANCE"));
        P11C4aEvidence.cue(output, role + "-first-try.ready");
    }

    /** The existing server owner calls once per real tick; no alternate native scheduler. */
    static void tick(ServerPlayer current) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400, "FIRST_TRY_SERVER_DEADLINE");
        require(run.failure == null && current == run.current && run.connection.isConnected()
                && current.connection.getConnection() == run.connection
                && run.server.getPlayerList().getPlayer(current.getUUID()) == current, "FIRST_TRY_CURRENT_ACTOR");
        if (!P11C4aEvidence.cuePresent(run.clientOutput, "first-try-armed.ready")) { return; }
        // Fixture preparation only: refill is configured at >=1 token/s. No bucket or
        // sequence is changed, and the target 41/STATUS/42 path has no artificial wait.
        boolean setupRefilled = System.nanoTime() - run.lastSetupDeathNanos >= 1_000_000_000L;
        if (!run.awaiting && run.completedSequence < 40) {
            if (!setupRefilled) { return; }
            require(current.isAlive() && run.nextSequence == run.completedSequence + 1, "FIRST_TRY_SETUP_SEQUENCE");
            run.awaiting = true;
            run.lastSetupDeathNanos = System.nanoTime();
            current.kill();
            require(current.isDeadOrDying(), "FIRST_TRY_SETUP_DEATH_NOT_NATIVE");
        } else if (!run.awaiting && run.completedSequence == 40 && !run.testDeath) {
            if (!setupRefilled) { return; }
            run.testDeath = true;
            run.awaiting = true;
            run.sceneActor = current;
            current.kill();
            require(current.isDeadOrDying() && run.binding41 != null, "FIRST_TRY_41_PUBLICATION_MISSING");
            run.epoch41 = sourceEpoch(run, current);
            run.lastAction41 = current.getLastActionTime();
        }
        if (run.lateOffered && !run.lateChecked) {
            require(run.fenced && run.suppressed == 1 && run.body41 == 0 && run.frame41 == 0
                    && run.current == run.sceneActor && sourceEpoch(run, run.current) == run.epoch41
                    && run.current.getLastActionTime() == run.lastAction41 && run.current.getHealth() <= 0
                    && !run.current.wonGame && run.completedSequence == 40, "FIRST_TRY_LATE_MUTATED_NATIVE_PREFIX");
            run.lateChecked = true;
            P11C4aEvidence.write(run.output, "first-try-late.json", report(run, "LATE_41_OBSERVED_NO_NATIVE_BODY"));
            P11C4aEvidence.cue(run.output, "first-try-late-checked.ready");
        }
    }

    static boolean finish(ServerPlayer current, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.failure == null, "FIRST_TRY_FINISH_OWNER");
        if (run.completed42 == null || !P11C4aEvidence.receiptPresent(clientOutput, "first-try.json")) { return false; }
        require(current == run.current && current != run.sceneActor && run.connection.isConnected()
                && current.connection.getConnection() == run.connection
                && run.server.getPlayerList().getPlayer(current.getUUID()) == current
                && current.getAdvancements() == run.initial.getAdvancements()
                && current.getStats() == run.initial.getStats()
                && run.epoch41 < Long.MAX_VALUE && sourceEpoch(run, current) == run.epoch41 + 1
                && run.setupBodies == 40 - run.baseline && run.setupFrames == run.setupBodies
                && run.setupRespawns == run.setupBodies && run.suppressed == 1
                && run.status41 > 0 && run.fenced && run.lateIngress == 1 && run.lateOffered && run.lateChecked
                && run.body41 == 0 && run.frame41 == 0 && run.body42 == 1 && run.frame42 == 1
                && run.finalRespawns == 1 && run.original42 == 1 && run.may41 != null,
                "FIRST_TRY_FINAL_COUNTS_OR_SOURCE");
        P11C4aEvidence.write(run.output, "first-try.json", report(run, "ACTUAL_41_STATUS_FENCE_LATE_DROP_AND_FRESH_42"));
        restore(run);
        active = null;
        return true;
    }

    static void abort() {
        var run = active;
        if (run == null) { return; }
        require(run.server.isSameThread(), "FIRST_TRY_ABORT_NOT_MAIN");
        restore(run);
        active = null;
    }

    static Map<String, Object> pending() {
        var run = active;
        return run == null ? Map.of("active", false) : report(run, "PENDING_NOT_ACCEPTANCE");
    }

    /** Exact injected Boundary call scope, not a transport/admission capability. */
    public static Ingress beginIngress(Object value, Connection connection, ICommonPacketListener listener) {
        var run = active;
        if (run == null || connection != run.connection) { return null; }
        if (!(value instanceof Request request) || !(listener instanceof ServerGamePacketListenerImpl)
                || listener.getConnection() != connection || connection.getPacketListener() != listener
                || !connection.isConnected() || request.connectionEpoch() != run.connectionEpoch
                || request.scope() != Scope.PLAY || request.kind() != Kind.DEATH) {
            fail(run, "FIRST_TRY_WRONG_INGRESS_IDENTITY"); return null;
        }
        var scope = new Ingress(run, request, INGRESS.get());
        INGRESS.set(scope);
        if (!sameScene(request, run.binding)) {
            fail(run, "FIRST_TRY_WRONG_CURRENT_SCENE"); return scope;
        }
        if (request.requestSeq() == 41) {
            var binding = run.binding41;
            if (binding == null || !sameScene(request, binding)) {
                fail(run, "FIRST_TRY_41_WRONG_SCENE"); return scope;
            }
            if (request.command() == Command.TRY) {
                synchronized (run) {
                    if (run.suppressed == 0 && run.testDeath && !run.fenced) {
                        run.first41 = request;
                        run.suppressed = 1;
                        scope.suppressed = true;
                    } else if (run.fenced && run.first41 != null && run.first41.equals(request) && run.lateIngress == 0) {
                        run.lateIngress++;
                        scope.late = true;
                    } else { fail(run, "FIRST_TRY_UNPLANNED_41_TRY"); }
                }
            } else { run.status41++; }
        } else if (request.command() == Command.TRY) {
            synchronized (run) {
                if (request.requestSeq() != run.nextSequence || request.requestSeq() > 42
                        || request.requestSeq() == 42 && !run.lateChecked) {
                    fail(run, "FIRST_TRY_UNEXPECTED_ORIGINAL_SEQUENCE");
                } else {
                    run.nextSequence++;
                    if (request.requestSeq() == 42) { run.original42++; }
                }
            }
        }
        return scope;
    }

    public static boolean suppress(Ingress scope) {
        return scope != null && scope == INGRESS.get() && scope.run == active && scope.suppressed;
    }

    public static void endIngress(Ingress scope) {
        if (scope == null) { return; }
        if (INGRESS.get() != scope) { fail(scope.run, "FIRST_TRY_INGRESS_ORDER"); return; }
        if (scope.previous == null) { INGRESS.remove(); } else { INGRESS.set(scope.previous); }
    }

    /** Before the actual offer can publish an inbox visible to the main dispatcher. */
    public static void offering(Object control, Object value) {
        var scope = INGRESS.get();
        if (scope == null || scope.run != active || value != scope.request) { return; }
        var run = scope.run;
        if (scope.suppressed) { fail(run, "FIRST_TRY_SUPPRESSED_WAS_OFFERED"); return; }
        if (scope.request.requestSeq() == 41 && scope.request.command() == Command.STATUS && !run.fenced) {
            if (run.control != null && run.control != control) { fail(run, "FIRST_TRY_CONTROL_CHANGED"); return; }
            run.control = control;
        }
    }

    /** Actual offer normal return, still inside the authenticated Boundary ingress scope. */
    public static void offered(Object control, Object value, Object result) {
        var scope = INGRESS.get();
        if (scope == null || scope.run != active || value != scope.request) { return; }
        var run = scope.run;
        if (scope.late) {
            if (control != run.control || result != P11TransitionControl.Offer.COALESCED
                    && result != P11TransitionControl.Offer.STALE) {
                fail(run, "FIRST_TRY_LATE_WAS_RETAINED");
            } else { run.lateOffered = true; }
        }
    }

    /** Read-only exact recordNotStarted tail: fence is observed before native notification send. */
    public static void fenced(Object control, long sequence, Object reason, long fence, long scene,
            long actor, long version, long lastSequence) {
        var run = active;
        if (run == null || sequence != 41 || control != run.control) { return; }
        if (!run.server.isSameThread() || run.first41 == null || run.suppressed != 1 || run.fenced
                || reason != Reason.CONTROL_DISPATCH_BUSY || fence != 41 || lastSequence != 41
                || scene != run.first41.sceneSerial() || actor != run.first41.actorGeneration()
                || version <= run.binding41.statusVersion() || run.body41 != 0) {
            fail(run, "FIRST_TRY_FENCE_RECORD_MISMATCH"); return;
        }
        run.fenceVersion = version;
        run.fenced = true;
        run.nextSequence = 42;
    }

    public static void body(ServerPlayer actor) {
        var run = active;
        if (run == null || actor.connection.getConnection() != run.connection) { return; }
        if (!run.server.isSameThread() || actor != run.current) { fail(run, "FIRST_TRY_BODY_WRONG_ACTOR"); return; }
        if (!run.testDeath) { run.setupBodies++; }
        else if (run.original42 == 0) { run.body41++; fail(run, "FIRST_TRY_41_ENTERED_BODY"); }
        else { run.body42++; }
    }

    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        if (packet instanceof ClientboundRespawnPacket) {
            if (!run.testDeath) { run.setupFrames++; }
            else if (run.original42 == 0) { run.frame41++; fail(run, "FIRST_TRY_41_EMITTED_FRAME"); }
            else { run.frame42++; }
            return;
        }
        if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state = payload.state();
        if (state.scope() != Scope.PLAY || state.kind() != Kind.DEATH) { return; }
        if (!run.server.isSameThread() || state.connectionEpoch() != run.connectionEpoch) {
            fail(run, "FIRST_TRY_SUBMISSION_OWNER"); return;
        }
        if (state.outcome() == Outcome.BINDING) {
            run.binding = state;
            if (run.testDeath && run.binding41 == null) { run.binding41 = state; }
        }
        if (state.requestSeq() == 41 && state.outcome() == Outcome.NOT_STARTED) {
            if (!run.fenced || state.statusVersion() < run.fenceVersion || !sameScene(run.first41, state)
                    || !(state.availability() == Availability.WAIT_NOTIFY && state.reason() == Reason.CONTROL_DISPATCH_BUSY
                        || state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE)) {
                fail(run, "FIRST_TRY_NOTIFICATION_BEFORE_FENCE"); return;
            }
            if (state.availability() == Availability.WAIT_NOTIFY) { run.wait41 = state; }
            if (state.availability() == Availability.MAY_TRY) { run.may41 = state; }
            run.notStarted41 = state;
        }
        if (state.outcome() == Outcome.COMPLETED) {
            if (!run.testDeath) {
                // STATUS can legitimately resubmit the retained completed result.
                if (state.equals(run.lastCompleted)) { return; }
                if (state.requestSeq() != run.completedSequence + 1 || state.requestSeq() > 40) {
                    fail(run, "FIRST_TRY_SETUP_COMPLETION_SEQUENCE"); return;
                }
                run.completedSequence = state.requestSeq(); run.awaiting = false; run.lastCompleted = state;
            } else if (state.requestSeq() == 42 && sameScene(run.first41, state)) {
                run.completed42 = state;
            } else { fail(run, "FIRST_TRY_UNEXPECTED_COMPLETION"); }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        var run = active;
        if (run == null || !(event.getEntity() instanceof ServerPlayer player)
                || player.getServer() != run.server || !player.getUUID().equals(run.initial.getUUID())) { return; }
        if (player.connection.getConnection() != run.connection || player == run.current || event.isEndConquered()) {
            fail(run, "FIRST_TRY_RESPAWN_IDENTITY"); return;
        }
        run.current = player;
        if (!run.testDeath) { run.setupRespawns++; } else { run.finalRespawns++; }
    }

    private static long sourceEpoch(Run run, ServerPlayer actor) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        require(body != null && body.complete && source.canSerialize(body), "FIRST_TRY_SOURCE_NOT_COMPLETE");
        return body.source.epoch();
    }

    private static boolean sameScene(Request request, State state) {
        return request != null && state != null && request.scope() == state.scope()
                && request.connectionEpoch() == state.connectionEpoch() && request.sceneSerial() == state.sceneSerial()
                && request.actorGeneration() == state.actorGeneration() && request.kind() == state.kind();
    }

    private static void restore(Run run) {
        run.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(run.keepInventory, run.server);
        run.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(run.immediate, run.server);
    }

    private static Map<String, Object> report(Run run, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("failure", run.failure == null ? "NONE" : run.failure);
        values.put("baselineActualSequence", run.baseline); values.put("setupNativeBodies", run.setupBodies);
        values.put("setupNativeFrames", run.setupFrames); values.put("setupNativeRespawns", run.setupRespawns);
        values.put("setupCompletedSequence", run.completedSequence); values.put("suppressedBeforeOffer", run.suppressed);
        values.put("suppressionKind", "EXCLUDED_EXACT_ONE_INGRESS_LOSS_NOT_RATE_LIMIT");
        values.put("actualStatus41Ingress", run.status41); values.put("fence41ObservedBeforeSend", run.fenced);
        values.put("fenceStatusVersion", run.fenceVersion); values.put("late41Ingress", run.lateIngress);
        values.put("late41OfferRejectedOrCoalesced", run.lateOffered); values.put("late41ZeroBodyChecked", run.lateChecked);
        values.put("nativeBodies41", run.body41); values.put("nativeFrames41", run.frame41);
        values.put("original42Ingress", run.original42); values.put("nativeBodies42", run.body42);
        values.put("nativeFrames42", run.frame42); values.put("nativeRespawns42", run.finalRespawns);
        values.put("binding41", run.binding41); values.put("capturedActual41", run.first41);
        values.put("notStarted41", run.notStarted41); values.put("waitNotification41", run.wait41);
        values.put("mayTry41", run.may41); values.put("completed42", run.completed42);
        values.put("setupDeathMinimumIntervalNanos", 1_000_000_000L);
        values.put("sourceEpochBefore42", run.epoch41); values.put("fullC4aAcceptance", false);
        return values;
    }

    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }

    public static final class Ingress {
        private final Run run;
        private final Request request;
        private final Ingress previous;
        private boolean suppressed, late;
        private Ingress(Run run, Request request, Ingress previous) {
            this.run = run; this.request = request; this.previous = previous;
        }
    }

    private static final class Run {
        final MinecraftServer server;
        final ServerPlayer initial;
        final Connection connection;
        final Path output, clientOutput;
        final long baseline, connectionEpoch;
        volatile ServerPlayer current;
        ServerPlayer sceneActor;
        volatile String failure;
        volatile Object control;
        volatile Request first41;
        volatile State binding, binding41;
        State notStarted41, wait41, may41, completed42, lastCompleted;
        volatile long nextSequence;
        long completedSequence, epoch41, lastAction41, lastSetupDeathNanos;
        volatile long fenceVersion;
        volatile int suppressed, status41, lateIngress, original42;
        int ticks, setupBodies, setupFrames, setupRespawns, body41, frame41, body42, frame42, finalRespawns;
        volatile boolean testDeath, fenced, lateOffered, lateChecked;
        boolean keepInventory, immediate, awaiting;
        Run(MinecraftServer server, ServerPlayer actor, String role, Path output, Path clientOutput, Request prior) {
            this.server = server; initial = actor; current = actor; connection = actor.connection.getConnection();
            this.output = output; this.clientOutput = clientOutput;
            baseline = prior.requestSeq(); completedSequence = baseline; nextSequence = baseline + 1;
            connectionEpoch = prior.connectionEpoch();
            lastSetupDeathNanos = System.nanoTime();
        }
    }
}
