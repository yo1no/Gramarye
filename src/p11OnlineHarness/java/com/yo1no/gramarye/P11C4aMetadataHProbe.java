package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import static com.yo1no.gramarye.P11C4aNativeObservations.Event.*;

import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataInitialStage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Excluded H-only scenario. The sole controllable future belongs to a real reload listener. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aMetadataHProbe {
    private static volatile Run active;
    private P11C4aMetadataHProbe() { }

    /** Arm before an additional genuine ENTER_CONFIG then unparked RETURN; never starts either. */
    static void arm(MinecraftServer server, ServerPlayer before, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && server.isSameThread() && active == null,
                "METADATA_H_ARM_OWNER");
        require(role.equals(server.isDedicatedServer() ? "a" : "host")
                && before.getServer() == server && !before.isFakePlayer()
                && server.getPlayerList().getPlayer(before.getUUID()) == before
                && P11NativeStorageBoundary.nativeDeliveryEligible(before), "METADATA_H_ARM_ACTOR");
        require(output.equals(P11C4aEvidence.root().resolve("server"))
                && Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(output),
                "METADATA_H_OUTPUT");
        require(Boolean.FALSE.equals(P11C4aReloadBlockerProbe.pending().get("active")),
                "METADATA_H_OTHER_RELOAD_ACTIVE");
        var source = P11NativeStorageBoundary.nativeSourceOwner(before);
        var body = source == null ? null : source.body(before);
        require(body != null && body.account.metadata == null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0,
                "METADATA_H_ARM_NOT_QUIESCENT");
        P11C4aNativeObservations.requireHealthy();
        var run = new Run(server, before, role, output);
        require(count(run, CONFIG_CALL_ENTER) == run.configReturns
                && count(run, SWITCH_CALL_ENTER) == run.switchReturns
                && count(run, CONFIG_CALL_THROW) == 0 && count(run, SWITCH_CALL_THROW) == 0,
                "METADATA_H_PRIOR_CALLER_NOT_TERMINAL");
        var rules = server.getGameRules();
        run.keepInventory = rules.getBoolean(GameRules.RULE_KEEPINVENTORY);
        run.immediate = rules.getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
        P11C4aEvidence.write(output, "reload-h-armed.json", Map.of(
                "status", "ARMED_NOT_ACCEPTANCE", "mode", "REAL_METADATA_H",
                "configCallerReturnsBefore", run.configReturns, "switchCallerReturnsBefore", run.switchReturns));
        active = run;
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, server);
    }

    /** Original metadataInitialSync RETURN only. No receipt, root, stage or session is manufactured. */
    public static void metadataReturned(ServerPlayer actor, long epoch, MetadataInitialStage stage) {
        var run = active;
        if (run == null || actor.getServer() != run.server || actor.connection == null
                || actor.connection.getConnection() != run.connection
                || !actor.getUUID().equals(run.before.getUUID())) { return; }
        if (stage == MetadataInitialStage.MANA_STARTED) {
            beginRealWait(run, actor, epoch);
            return;
        }
        if (actor != run.actor || epoch != run.sessionEpoch) { return; }
        if (stage == MetadataInitialStage.MANA_FAILED || stage == MetadataInitialStage.COOLDOWN_FAILED) {
            fail(run, "METADATA_H_ORIGINAL_SUBMISSION_FAILED");
            return; // Preserve the native sender's original primary.
        }
        try {
            require(run.server.isSameThread() && run.reloadReturned && !run.reloadActive,
                    "METADATA_H_SUBMISSION_ORDER");
            switch (stage) {
                case MANA_SUBMITTED -> {
                    require(++run.manaSubmitted == 1 && run.cooldownStarted == 0, "METADATA_H_MANA_DUPLICATE");
                    requireHOnly(run);
                }
                case COOLDOWN_STARTED -> {
                    require(run.manaSubmitted == 1 && ++run.cooldownStarted == 1, "METADATA_H_COOLDOWN_ORDER");
                    requireHOnly(run);
                }
                case COOLDOWN_SUBMITTED -> {
                    require(run.manaSubmitted == 1 && run.cooldownStarted == 1 && ++run.cooldownSubmitted == 1
                            && run.body.account.metadata == null
                            && !P11NativeStorageBoundary.metadataSessionCurrent(run.lease)
                            && roots(run, P11ControlBudgets.Root.TRANSITION) == 0
                            && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                            && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                            "METADATA_H_ORIGINAL_RELEASE_MISSING");
                    run.metadataReleased = true;
                    run.releasedRoots = rootSnapshot(run);
                }
                default -> { }
            }
        } catch (RuntimeException | Error observerFailure) { fail(run, "METADATA_H_STAGE_OBSERVER"); }
    }

    private static void beginRealWait(Run run, ServerPlayer actor, long epoch) {
        require(run.server.isSameThread() && run.actor == null && epoch > 0
                && actor != run.before && !actor.isFakePlayer()
                && run.server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor)
                && actor.getAdvancements() == run.canonical, "METADATA_H_REAL_RETURN_ACTOR");
        require(count(run, CONFIG_CALL_ENTER) == run.configReturns + 1
                && count(run, CONFIG_CALL_RETURN) == run.configReturns + 1
                && count(run, SWITCH_CALL_ENTER) == run.switchReturns + 1
                && count(run, SWITCH_CALL_RETURN) == run.switchReturns + 1
                && count(run, CONFIG_CALL_THROW) == 0 && count(run, SWITCH_CALL_THROW) == 0
                && count(run, PARKING_INSTALLED) == run.parkingBefore
                && count(run, LOGIN_FRAME_PRODUCED) == run.loginFrames + 1
                && count(run, LOGIN_SEND_RETURN) == run.loginSends + 1,
                "METADATA_H_RETURN_CALLER_STILL_HELD_OR_PARKED");
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        require(body != null && body.actor == actor && body.account.metadata != null,
                "METADATA_H_NO_REAL_METADATA_LEASE");
        run.actor = actor; run.body = body; run.lease = body.account.metadata; run.sessionEpoch = epoch;
        run.priorCallerTerminal = true;
        requireHOnly(run);
        run.callbackRoots = rootSnapshot(run);
        run.xpBefore = actor.totalExperience;
        run.respawnReturns = count(run, RESPAWN_CALL_RETURN);
        run.respawnFrames = count(run, RESPAWN_FRAME_PRODUCED);
        try {
            actor.kill();
            require(actor.isDeadOrDying() && run.deathBinding != null,
                    "METADATA_H_NATIVE_DEATH_BINDING_MISSING");
            requireHOnly(run);
            run.reloadActive = true;
            run.gate.orTimeout(30, TimeUnit.SECONDS);
            // This is the original reload's own managedBlock, not an engineering pump.
            run.server.reloadResources(List.copyOf(run.server.getPackRepository().getSelectedIds())).join();
            run.reloadReturned = true;
            require(run.failure == null && run.gateReleased && run.waitState != null
                    && run.bodies == 0 && run.frames == 0 && run.manaSubmitted == 0
                    && run.listenerRegistrations == 1 && run.listenerCalls == 1
                    && run.listenerApplicationOnServerThread,
                    "METADATA_H_RELOAD_DID_NOT_PROVE_ZERO_RESPAWN");
            requireHOnly(run); // Original P7 submit has still not run.
            run.reloadTailRoots = rootSnapshot(run);
            P11C4aEvidence.write(run.output, "reload-h-tail.json", report(run,
                    "ORIGINAL_RELOAD_RETURNED_BEFORE_MANA_SUBMISSION"));
        } catch (IOException evidenceFailure) {
            fail(run, "METADATA_H_EVIDENCE_IO");
            throw new IllegalStateException("METADATA_H_EVIDENCE_IO");
        } catch (RuntimeException | Error primary) {
            fail(run, "METADATA_H_NATIVE_WAIT_FAILED");
            throw primary;
        } finally { run.reloadActive = false; }
    }

    @SubscribeEvent
    static void addReloadListener(AddReloadListenerEvent event) {
        var run = active;
        if (run == null || !run.reloadActive) { return; }
        require(Boolean.FALSE.equals(P11C4aReloadBlockerProbe.pending().get("active")),
                "METADATA_H_OTHER_RELOAD_ACTIVE");
        // Locked Z posts this event from the reloadable-registry background continuation.
        // This short scalar claim never invokes the event, performs IO, or inspects actor state.
        synchronized (run) {
            if (active != run || !run.reloadActive) { return; }
            require(++run.listenerRegistrations == 1, "METADATA_H_RELOAD_NOT_EXCLUSIVE");
            run.listenerRegistrationOnServerThread = run.server.isSameThread();
        }
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparation, ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                run.listenerCalls++;
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == run && run.reloadActive && run.server.isSameThread(),
                            "METADATA_H_LISTENER_APPLICATION_OWNER");
                    run.listenerApplicationOnServerThread = true;
                    run.listenerWaiting = true;
                    try {
                        requireHOnly(run);
                        P11C4aEvidence.cue(run.output, run.role + "-reload-h.ready");
                    } catch (IOException evidenceFailure) { fail(run, "METADATA_H_CUE_IO"); }
                    return run.gate;
                }, game);
            }
        });
    }

    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var run = active;
        if (run == null || run.server != server || !run.reloadActive) { return; }
        if (entering) { run.managedEntries++; run.inManagedBlock = true; }
        else { run.inManagedBlock = false; if (normal) { run.managedReturns++; } }
    }

    public static void ingress(Object value, Connection connection) {
        var run = active;
        if (run == null || run.actor == null || connection != run.connection
                || !(value instanceof Request request) || request.command() != Command.TRY
                || request.kind() != Kind.DEATH) { return; }
        synchronized (run) {
            if (++run.tries == 1) { run.firstRequest = request; }
            else if (run.tries == 2) { run.secondRequest = request; }
            else { fail(run, "METADATA_H_EXTRA_TRY"); }
        }
    }

    /** Actual native send return. A BINDING alone is not proof this is an executable Death scene. */
    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || run.actor == null || listener.getConnection() != run.connection) { return; }
        if (!(packet instanceof ClientboundRespawnPacket)
                && !(packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof P11TransitionStatePayload)) { return; }
        try {
            require(run.server.isSameThread() && run.connection.getPacketListener() == listener,
                    "METADATA_H_SEND_OWNER");
            if (packet instanceof ClientboundRespawnPacket) { run.frames++; return; }
            var state = ((P11TransitionStatePayload) ((ClientboundCustomPayloadPacket) packet).payload()).state();
            if (state.kind() != Kind.DEATH) { return; }
            if (state.outcome() == Outcome.BINDING) {
                require(run.deathBinding == null && state.scope() == Scope.PLAY && state.requestSeq() == 0,
                        "METADATA_H_DEATH_BINDING");
                run.deathBinding = state;
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                requireHOnly(run);
                require(matches(run.firstRequest, state) && sameScene(run.deathBinding, state)
                        && state.reason() == Reason.ACTIVE_TRANSITION && run.priorCallerTerminal
                        && run.inManagedBlock && run.reloadActive && run.listenerWaiting
                        && run.bodies == 0 && run.frames == 0
                        && count(run, RESPAWN_CALL_RETURN) == run.respawnReturns
                        && count(run, RESPAWN_FRAME_PRODUCED) == run.respawnFrames,
                        "METADATA_H_WAIT_NOT_H_ONLY_ACTIVE_DEATH");
                if (run.waitState == null) {
                    run.refusalRoots = rootSnapshot(run);
                    run.waitState = state;
                    run.gateReleased = run.gate.complete(null);
                    require(run.gateReleased, "METADATA_H_GATE_ALREADY_TERMINAL");
                }
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                require(matches(run.firstRequest, state) && run.waitState != null && run.metadataReleased
                        && run.cooldownSubmitted == 1 && !run.inManagedBlock && !run.reloadActive
                        && roots(run, P11ControlBudgets.Root.TRANSITION) == 0
                        && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                        && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0 && run.bodies == 0,
                        "METADATA_H_MAY_BEFORE_ORIGINAL_RELEASE");
                run.mayState = state;
            } else if (state.outcome() == Outcome.COMPLETED) {
                require(run.mayState != null && matches(run.secondRequest, state)
                        && run.secondRequest.requestSeq() > run.firstRequest.requestSeq(),
                        "METADATA_H_COMPLETION_NOT_FRESH_RETRY");
                run.completed = state;
            }
        } catch (RuntimeException | Error observerFailure) { fail(run, "METADATA_H_SEND_OBSERVER"); }
    }

    public static void body(ServerPlayer original) {
        var run = active;
        if (run == null || original != run.actor) { return; }
        run.bodies++;
        if (run.mayState == null || !run.metadataReleased || run.tries != 2 || run.inManagedBlock) {
            fail(run, "METADATA_H_BODY_BEFORE_FRESH_RETRY");
        }
    }

    static boolean finish(ServerPlayer successor, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.failure == null, "METADATA_H_FINISH_OWNER");
        require(clientOutput.equals(P11C4aEvidence.root().resolve("client-" + run.role)), "METADATA_H_CLIENT_OUTPUT");
        if (run.completed == null || !P11C4aEvidence.receiptPresent(clientOutput, "reload-h.json")) { return false; }
        var source = P11NativeStorageBoundary.nativeSourceOwner(successor);
        var current = source == null ? null : source.body(successor);
        require(run.priorCallerTerminal && run.reloadReturned && run.metadataReleased
                && run.manaSubmitted == 1 && run.cooldownStarted == 1 && run.cooldownSubmitted == 1
                && run.tries == 2 && run.bodies == 1 && run.frames == 1
                && run.listenerRegistrations == 1 && run.listenerCalls == 1
                && run.managedEntries == 1 && run.managedReturns == 1 && run.gateReleased
                && count(run, RESPAWN_CALL_RETURN) == run.respawnReturns + 1
                && count(run, RESPAWN_FRAME_PRODUCED) == run.respawnFrames + 1
                && successor != run.actor && successor.getUUID().equals(run.actor.getUUID())
                && successor.connection.getConnection() == run.connection
                && run.server.getPlayerList().getPlayer(successor.getUUID()) == successor
                && successor.getAdvancements() == run.canonical
                && current != null && current.account == run.body.account
                && P11NativeStorageBoundary.nativeDeliveryEligible(successor)
                && successor.totalExperience == run.xpBefore && run.body.account.metadata == null
                && roots(run, P11ControlBudgets.Root.TRANSITION) == 0
                && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                "METADATA_H_TERMINAL_FACTS");
        require(run.server.saveEverything(true, false, false), "METADATA_H_ORIGINAL_SAVE_FAILED");
        var saved = NbtIo.readCompressed(run.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(successor.getUUID() + ".dat"), NbtAccounter.unlimitedHeap());
        require(saved.getInt("XpTotal") == successor.totalExperience, "METADATA_H_ORIGINAL_XP_READBACK");
        P11C4aEvidence.write(run.output, "reload-h.json", report(run,
                "ACTUAL_H_ONLY_REFUSAL_ORIGINAL_RELEASE_MANUAL_RETRY_AND_READBACK"));
        restoreRules(run); active = null;
        return true;
    }

    static Map<String, Object> pending() {
        var run = active;
        return run == null ? Map.of("active", false) : report(run, "PENDING_NOT_ACCEPTANCE");
    }

    static void abort() {
        var run = active;
        if (run == null) { return; }
        require(run.server.isSameThread(), "METADATA_H_ABORT_NOT_MAIN");
        fail(run, "METADATA_H_OWNER_ABORT"); restoreRules(run); active = null;
    }

    @SubscribeEvent static void stopped(ServerStoppedEvent event) {
        var run = active;
        if (run != null && run.server == event.getServer()) { fail(run, "METADATA_H_SERVER_STOP"); active = null; }
    }

    private static void requireHOnly(Run run) {
        require(run.server.isSameThread() && run.body != null && run.body.actor == run.actor
                && run.body.account.metadata == run.lease && P11NativeStorageBoundary.metadataSessionCurrent(run.lease)
                && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                && roots(run, P11ControlBudgets.Root.TRANSITION) == 1, "METADATA_H_NOT_EXACT_H_ONLY");
    }

    private static long roots(Run run, P11ControlBudgets.Root root) { return run.body.account.nativeCounts[root.ordinal()]; }
    private static RootSnapshot rootSnapshot(Run run) {
        return new RootSnapshot(roots(run, P11ControlBudgets.Root.OPERATION),
                roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT), roots(run, P11ControlBudgets.Root.TRANSITION));
    }
    private record RootSnapshot(long operation, long commandContext, long transition) { }
    private static long count(Run run, P11C4aNativeObservations.Event event) { return P11C4aNativeObservations.count(run.connection, event); }
    private static boolean matches(Request request, State state) {
        return request != null && request.scope() == state.scope() && request.connectionEpoch() == state.connectionEpoch()
                && request.sceneSerial() == state.sceneSerial() && request.actorGeneration() == state.actorGeneration()
                && request.requestSeq() == state.requestSeq() && request.kind() == state.kind();
    }
    private static boolean sameScene(State first, State next) {
        return first != null && first.scope() == next.scope() && first.connectionEpoch() == next.connectionEpoch()
                && first.sceneSerial() == next.sceneSerial() && first.actorGeneration() == next.actorGeneration()
                && first.kind() == next.kind();
    }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static void fail(Run run, String code) {
        if (run.failure == null) { run.failure = code; }
        run.gate.completeExceptionally(new IllegalStateException(code));
        writeFailureReceipt(run);
    }
    private static void writeFailureReceipt(Run run) {
        // Network ingress can fail under its own scalar monitor. Leave evidence IO to
        // the owning server thread (including ServerStopped), never to that callback.
        if (!run.server.isSameThread() || Thread.holdsLock(run) || run.failureReceiptAttempted) { return; }
        run.failureReceiptAttempted = true;
        try {
            P11C4aEvidence.write(run.output, "reload-h-failure.json", report(run,
                    "FAILED_NATIVE_METADATA_H_NOT_ACCEPTANCE"));
        } catch (IOException | RuntimeException | Error evidenceFailure) {
            // This observer must not replace the original native sender/reload primary.
        }
    }
    private static void restoreRules(Run run) {
        run.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(run.keepInventory, run.server);
        run.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(run.immediate, run.server);
    }
    private static Map<String, Object> report(Run run, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("active", true); values.put("status", status); values.put("mode", "REAL_METADATA_H");
        values.put("failure", run.failure == null ? "NONE" : run.failure);
        values.put("priorNativeReturnCallerTerminal", run.priorCallerTerminal); values.put("sessionEpoch", run.sessionEpoch);
        values.put("metadataLeaseObserved", run.lease != null); values.put("metadataReleasedByOriginalCooldown", run.metadataReleased);
        values.put("manaSubmitted", run.manaSubmitted); values.put("cooldownStarted", run.cooldownStarted);
        values.put("cooldownSubmitted", run.cooldownSubmitted); values.put("reloadReturned", run.reloadReturned);
        values.put("gateReleasedByActualRefusal", run.gateReleased); values.put("listenerRegistrations", run.listenerRegistrations);
        values.put("listenerRegistrationOnServerThread", run.listenerRegistrationOnServerThread);
        values.put("listenerApplicationOnServerThread", run.listenerApplicationOnServerThread);
        values.put("listenerCalls", run.listenerCalls); values.put("managedBlockEntries", run.managedEntries);
        values.put("managedBlockNormalReturns", run.managedReturns); values.put("tries", run.tries);
        values.put("nativeBodies", run.bodies); values.put("nativeRespawnFrames", run.frames);
        values.put("callbackRoots", run.callbackRoots); values.put("refusalRoots", run.refusalRoots);
        values.put("reloadTailRoots", run.reloadTailRoots); values.put("releasedRoots", run.releasedRoots);
        values.put("deathBinding", run.deathBinding); values.put("wait", run.waitState);
        values.put("mayTry", run.mayState); values.put("completed", run.completed);
        values.put("physicalHeldKeyClaimed", false); values.put("fullC4aAcceptance", false);
        return values;
    }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer before; final Connection connection;
        final PlayerAdvancements canonical; final String role; final Path output;
        final CompletableFuture<Void> gate = new CompletableFuture<>();
        final long configReturns, switchReturns, loginFrames, loginSends, parkingBefore;
        volatile String failure; volatile int tries, listenerCalls, listenerRegistrations;
        volatile boolean reloadActive, listenerRegistrationOnServerThread;
        volatile Request firstRequest, secondRequest;
        volatile ServerPlayer actor;
        P11QualifiedSourceOwner.Body body; P11NativeStorageBoundary.MetadataLease lease;
        State deathBinding, waitState, mayState, completed;
        RootSnapshot callbackRoots, refusalRoots, reloadTailRoots, releasedRoots;
        boolean keepInventory, immediate, priorCallerTerminal, reloadReturned, metadataReleased;
        boolean failureReceiptAttempted;
        boolean listenerApplicationOnServerThread;
        boolean inManagedBlock, listenerWaiting, gateReleased;
        long sessionEpoch, respawnReturns, respawnFrames;
        int xpBefore, bodies, frames, managedEntries, managedReturns;
        int manaSubmitted, cooldownStarted, cooldownSubmitted;
        Run(MinecraftServer server, ServerPlayer before, String role, Path output) {
            this.server = server; this.before = before; this.role = role; this.output = output;
            connection = before.connection.getConnection(); canonical = before.getAdvancements();
            configReturns = count(this, CONFIG_CALL_RETURN); switchReturns = count(this, SWITCH_CALL_RETURN);
            loginFrames = count(this, LOGIN_FRAME_PRODUCED); loginSends = count(this, LOGIN_SEND_RETURN);
            parkingBefore = count(this, PARKING_INSTALLED);
        }
    }
}
