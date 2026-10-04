package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Staged excluded probe. No control identity, task completion, permit or keepalive is fabricated. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aParkingProbe {
    private static final int REWARD = 23;
    private static final ThreadLocal<ResumeCall> RESUME = new ThreadLocal<>();
    private static volatile Run active;

    private P11C4aParkingProbe() { }

    /** Excluded accessor implemented on the actual private Entry; observations return no permit. */
    public interface EntryAccess {
        Connection c4a$parkingConnection();
        boolean c4a$parkingWaiting();
    }

    public static final class ResumeCall {
        private final Connection connection;
        private final ResumeCall previous;
        private ResumeCall(Connection connection, ResumeCall previous) {
            this.connection = connection; this.previous = previous;
        }
    }

    static void start(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && List.of("a", "b").contains(role) && actor.getServer() == server
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor && !actor.isFakePlayer()
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor), "PARK_START_OWNER");
        var connection = actor.connection.getConnection();
        // Native keepalive deliberately skips an integrated owner; use the real LAN partner there.
        require(connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection()
                && !server.isSingleplayerOwner(actor.getGameProfile()), "PARK_REQUIRES_REMOTE_AUTHENTICATED_ACTOR");
        var run = new Run(server, actor, role, output);
        active = run;
        run.nativeBefore = roots(run, P11ControlBudgets.Root.NATIVE_CREDIT);
        run.cloudReset = P11C4aConfigParkingResetProbe.selected();
        if (!run.cloudReset) {
        var cow = EntityType.COW.create(actor.serverLevel());
        require(cow != null, "PARK_NATIVE_COW_UNAVAILABLE");
        run.victim = cow;
        cow.moveTo(actor.getX() + 2, actor.getY(), actor.getZ(), 0, 0);
        require(actor.serverLevel().addFreshEntity(cow)
                && cow.hurt(actor.damageSources().playerAttack(actor), 1.0F)
                && cow.getLastHurtByMob() == actor && cow.getKillCredit() == actor
                && cow.getLastDamageSource() != null && cow.getLastDamageSource().getEntity() == actor,
                "PARK_NATIVE_CREDIT_FIELDS");
        require(roots(run, P11ControlBudgets.Root.NATIVE_CREDIT) >= run.nativeBefore + 3,
                "PARK_NATIVE_CREDIT_ROOTS");
        }
        P11C4aEvidence.write(output, "parking-armed.json", report(run, "ARMED_NOT_ACCEPTANCE"));
        P11C4aEvidence.cue(output, role + "-parking-arm.ready");
    }

    static void startPendingTransfer(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        start(server, actor, role, output);
        active.pendingTransfer = true;
        P11C4aParkingTransferProbe.start(server, actor.connection.getConnection());
    }

    static void startOldTick(MinecraftServer server, ServerPlayer actor, String role, Path output) throws IOException {
        start(server, actor, role, output);
        active.oldTick = true;
        P11C4aOldTickProbe.start(server, actor, output);
    }

    /** Called only by the real second reload's game application while the original old tick is suspended. */
    static void releaseOldTickRetryCue() throws IOException {
        var run = active;
        require(run != null && run.oldTick && run.server.isSameThread() && !run.retryCue
                && run.ownerReturned && run.mayState != null && run.keepAliveAcks > 0
                && run.connection.getPacketListener() instanceof P11ParkingPacketListener,
                "OLD_TICK_RETRY_CUE_OWNER");
        run.retryCue = true;
        P11C4aEvidence.write(run.output, "parking-keepalive.json", report(run, "ACTUAL_OLD_DUE_TICK_SUSPENDED_BEFORE_NATIVE_FIELDS"));
        P11C4aEvidence.cue(run.output, run.role + "-parking-retry.ready");
    }

    /** Parent calls on owning server ticks. The successor may be null while truly playerless. */
    static boolean tick(ServerPlayer successor, Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400, "PARK_PHASE_DEADLINE");
        require(run.failure == null && run.connection.isConnected(), "PARK_OBSERVER_OR_CONNECTION");
        if (!run.switched) {
            if (!P11C4aEvidence.receiptPresent(clientOutput, "parking-armed.json")) { return false; }
            run.switched = true;
            run.actor.connection.switchToConfig();
            return false;
        }
        if (!run.returnRequested) {
            if (run.enterCompleted == null
                    || !P11C4aEvidence.receiptPresent(clientOutput, "parking-config-terminal.json")) { return false; }
            require(run.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null
                    && run.factories == 0 && run.constructed == 0 && run.loginFrames == 0,
                    "PARK_INDEPENDENT_CONFIG_TERMINAL");
            if (P11C4aScenario.c6() && !P11C4aC6Coordinator.beforeReturn(run.actor)) { return false; }
            if (run.cloudReset && !P11C4aConfigResetProbe.beforeParkingReturn(clientOutput)) { return false; }
            run.returnRequested = true;
            ((ServerConfigurationPacketListenerImpl) run.connection.getPacketListener()).returnToWorld();
            return false;
        }
        if (run.ownerReturned && run.mayState != null && !run.retryCue) {
            require(run.connection.getPacketListener() instanceof P11ParkingPacketListener
                    && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null
                    && run.factories == 0 && run.constructed == 0 && run.loginFrames == 0
                    && run.liveWaiting && run.liveK > 0, "PARK_BEFORE_RETRY_NOT_ACTUALLY_PLAYERLESS");
            // C6's failed retry must consume its genuine live-captured native credit before
            // that credit's unchanged TTL expires. Natural parked ACK is required at expiry.
            if (P11C4aScenario.c6()) { P11C4aC6Coordinator.parkedReady(); return false; }
            if (run.pendingTransfer && !P11C4aEvidence.receiptPresent(clientOutput, "parking-transfer-challenge-ready.json")) { return false; }
            if (run.keepAliveSends == 0 || (run.pendingTransfer
                    ? !P11C4aParkingTransferProbe.readyForRetry() : run.keepAliveAcks == 0)) { return false; }
            if (run.oldTick) {
                P11C4aOldTickProbe.arm((P11ParkingPacketListener) run.connection.getPacketListener());
                return false;
            }
            if (P11C4aPreplayChatProbe.selected() && !P11C4aPreplayChatProbe.beforeRetry()) { return false; }
            run.retryCue = true;
            P11C4aEvidence.write(run.output, "parking-keepalive.json", report(run, run.pendingTransfer ? "REAL_PENDING_CHALLENGE_BEFORE_TRANSFER_NOT_ACK_PROOF" : "REAL_PARKED_CHALLENGE_ACK_AFTER_RELOAD"));
            P11C4aEvidence.cue(run.output, run.role + "-parking-retry.ready");
            return false;
        }
        if (P11C4aScenario.c6()) { P11C4aC6Coordinator.pendingConfiguration(); return false; }
        if (run.completed == null || !P11C4aEvidence.receiptPresent(clientOutput, "parking.json")) { return false; }
        if (run.oldTick && !P11C4aOldTickProbe.complete()) { return false; }
        if (run.pendingTransfer && !P11C4aParkingTransferProbe.nativeAckReturned()) { return false; }
        if (run.pendingTransfer) { P11C4aParkingTransferProbe.verifyComplete(); }
        require(run.retryCue && run.tries == 1 && run.earlyTaskCalls == 1 && run.finalChecks == 1
                && run.parkingInstalls == 1 && run.resumeCalls == 1 && run.resumeReturns == 1 && run.resumeThrows == 0
                && run.factories == 1 && run.constructed == 1 && run.places == 1 && run.placeReturns == 1
                && run.loginFrames == 1 && run.loginSends == 1 && run.configCalls == 1 && run.configReturns == 1
                && run.managedEntries == 1 && run.managedReturns == 1 && run.reloadReturned && run.gateReleased
                && !run.liveWaiting && run.liveSamples > 0 && successor != null && successor != run.actor
                && successor.connection.getConnection() == run.connection
                && run.server.getPlayerList().getPlayer(successor.getUUID()) == successor
                && successor.getUUID().equals(run.actor.getUUID()) && successor.totalExperience == run.xpBefore + REWARD
                && successor.getAdvancements() == run.actor.getAdvancements()
                && P11NativeStorageBoundary.nativeDeliveryEligible(successor), "PARK_FINAL_COUNTS_OR_SUCCESSOR");
        require(run.server.saveEverything(true, false, false), "PARK_ORIGINAL_SAVE_FAILED");
        var saved = NbtIo.readCompressed(run.server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                .resolve(successor.getUUID() + ".dat"), NbtAccounter.unlimitedHeap());
        require(saved.getInt("XpTotal") == successor.totalExperience, "PARK_XP_ORIGINAL_READBACK");
        P11C4aEvidence.write(run.output, "parking.json", report(run, "ACTUAL_FINAL_REFUSAL_PARKING_KEEPALIVE_MANUAL_RETRY_AND_READBACK"));
        cleanup(run); active = null;
        return true;
    }

    /** Exact completeTask TAIL: native JoinWorld start/send happened, but its ACK body has not been replayed. */
    public static void earlyTaskReturned(ServerConfigurationPacketListenerImpl listener) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection || !run.returnRequested) { return; }
        if (run.cloudReset) {
            require(++run.earlyTaskCalls == 1 && run.server.isSameThread() && run.enterCompleted != null
                    && run.connection.getPacketListener() == listener && run.factories == 0
                    && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                    && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                    "CP_RESET_ORIGINAL_EARLY_TASK_NOT_ALLOW");
            return;
        }
        require(++run.earlyTaskCalls == 1 && run.server.isSameThread() && run.enterCompleted != null
                && run.connection.getPacketListener() == listener && run.factories == 0
                && run.victim.getKillCredit() == run.actor
                && roots(run, P11ControlBudgets.Root.OPERATION) == 0, "PARK_EARLY_TASK_OR_CREDIT_EXPIRED");
        // Native baseTick may already clear lastHurtByMob when A leaves the roster.
        // The unchanged die wrapper consumes getKillCredit's preferred live player-credit
        // field; require that actual selection and its real Fop below, not all old fields.
        run.mobCreditStillSelectedAtTask = run.victim.getLastHurtByMob() == run.actor;
        creditCaller(run, () -> require(run.victim.hurt(run.victim.damageSources().magic(), 1000.0F),
                "PARK_NATIVE_LETHAL_DAMAGE"));
    }

    static void cloudCaller(LivingEntity victim, Runnable original) {
        var run = active;
        require(run != null && run.cloudReset && run.server.isSameThread() && run.victim == null
                && run.earlyTaskCalls == 1 && run.deaths == 0 && victim.isAlive(), "CP_RESET_CLOUD_CALLER_SCOPE");
        run.victim = victim;
        creditCaller(run, original);
    }

    private static void creditCaller(Run run, Runnable original) {
        run.deathArmed = true;
        var lootRule = run.server.getGameRules().getRule(GameRules.RULE_DOMOBLOOT);
        run.mobLootBefore = lootRule.get();
        lootRule.set(false, run.server);
        run.mobLootSuppressed = true;
        run.mobLootObserved = true;
        run.creditCallerXpBefore = run.actor.totalExperience;
        Throwable primary = null;
        try {
            original.run();
            require(run.deaths == 1 && run.reloadReturned && run.parkingInstalls == 1 && run.waitState != null
                    && run.configReturns == 1 && run.factories == 0 && run.loginFrames == 0
                    && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                    && run.actor.totalExperience == run.creditCallerXpBefore + REWARD, "PARK_REAL_CREDIT_CALLER_TERMINAL");
            run.ownerReturned = true;
            P11C4aEvidence.write(run.output, "parking-owner-returned.json", report(run, "ORIGINAL_CREDIT_DEATH_AND_RELOAD_RETURNED"));
        } catch (IOException failure) { primary = failure; fail(run, "PARK_OWNER_EVIDENCE_IO"); }
        catch (RuntimeException | Error failure) { primary = failure; fail(run, "PARK_CREDIT_CALLER_FAILURE"); throw failure; }
        finally {
            run.deathArmed = false;
            try { lootRule.set(run.mobLootBefore, run.server); run.mobLootSuppressed = false; }
            catch (RuntimeException | Error secondary) {
                fail(run, "PARK_LOOT_RULE_RESTORE_FAILED");
                if (primary == null) { throw secondary; }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void death(LivingDeathEvent event) {
        var run = active;
        if (run == null || !run.deathArmed || event.getEntity() != run.victim) { return; }
        if (run.cloudReset) { P11C4aConfigParkingResetProbe.death(event); }
        run.deathArmed = false; run.deaths++;
        run.nativeAtDeath = roots(run, P11ControlBudgets.Root.NATIVE_CREDIT);
        run.fopAtDeath = roots(run, P11ControlBudgets.Root.OPERATION);
        run.qctxAtDeath = roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT);
        run.exactCreditAtDeath = run.victim.getKillCredit() == run.actor;
        require(run.server.isSameThread() && run.server.getPlayerList().getPlayer(run.actor.getUUID()) == null
                && run.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                && run.exactCreditAtDeath && run.nativeAtDeath > 0
                && run.fopAtDeath > 0 && run.qctxAtDeath == 0,
                "PARK_DETACHED_A_REAL_CREDIT_FOP_MISSING");
        run.reloadActive = true; run.gate.orTimeout(30, TimeUnit.SECONDS);
        try {
            run.server.reloadResources(List.copyOf(run.server.getPackRepository().getSelectedIds())).join();
            run.reloadReturned = true;
            require(run.gateReleased && run.waitState != null && run.parkingInstalls == 1
                    && run.factories == 0 && run.loginFrames == 0, "PARK_RELOAD_RETURNED_WITHOUT_REAL_FINAL_REFUSAL");
            AdvancementRewards.Builder.experience(REWARD).build().grant(run.actor);
        } finally { run.reloadActive = false; }
    }

    @SubscribeEvent static void addReloadListener(AddReloadListenerEvent event) {
        var run = active;
        if (run == null || !run.reloadActive) { return; }
        // Pinned registry future can post registration on its background executor.
        synchronized (run) {
            if (active != run || !run.reloadActive) { return; }
            require(++run.listenerRegistrations == 1, "PARK_RELOAD_LISTENER_DUPLICATED");
            run.listenerRegistrationOnServerThread = run.server.isSameThread();
        }
        event.addListener(new PreparableReloadListener() {
            @Override public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                    ProfilerFiller preparation, ProfilerFiller application,
                    java.util.concurrent.Executor background, java.util.concurrent.Executor game) {
                run.listenerCalls++;
                return barrier.wait(Boolean.TRUE).thenComposeAsync(ignored -> {
                    require(active == run && run.reloadActive && run.server.isSameThread(),
                            "PARK_RELOAD_APPLICATION_OWNER");
                    run.listenerApplicationOnServerThread = true;
                    run.listenerWaiting = true;
                    if (run.cloudReset) {
                        try { P11C4aConfigParkingResetProbe.releaseFinish(run.connection); }
                        catch (IOException failure) { throw new IllegalStateException("CP_RESET_RELEASE_IO", failure); }
                    }
                    releaseGateIfObserved(run);
                    return run.gate;
                }, game);
            }
        });
    }

    public static void managed(MinecraftServer server, boolean entering, boolean normal) {
        var run = active;
        if (run == null || server != run.server || !run.reloadActive) { return; }
        if (entering) { run.managedEntries++; run.inManaged = true; }
        else { run.inManaged = false; if (normal) { run.managedReturns++; } }
    }

    public static void liveScalars(Connection connection, int waiting, boolean entryWaiting) {
        var run = active;
        if (run == null || connection != run.connection) { return; }
        run.liveK = waiting; run.liveWaiting = entryWaiting; run.liveSamples++;
        if (waiting < 0 || entryWaiting && waiting == 0) { fail(run, "PARK_INVALID_ACTUAL_LIVE_K"); }
    }

    public static void configCaller(Connection connection, boolean entering, boolean normal) {
        var run = active;
        if (run == null || connection != run.connection || !run.returnRequested || !run.server.isSameThread()) { return; }
        if (entering) { run.configCalls++; run.configDepth++; }
        else {
            run.configDepth--;
            if (normal) { run.configReturns++; } else { fail(run, "PARK_FINAL_CONFIG_CALL_THROW"); }
        }
    }

    public static void finalCheck(ServerConfigurationPacketListenerImpl listener) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        run.finalChecks++;
        if (!run.inManaged || !run.reloadActive || run.earlyTaskCalls != 1
                || roots(run, P11ControlBudgets.Root.OPERATION) <= 0) { fail(run, "PARK_FINAL_CHECK_NOT_AFTER_EARLY_TASK_CONFLICT"); }
    }

    public static void parked(P11ParkingPacketListener parking) {
        var run = active;
        if (run == null || parking.getConnection() != run.connection) { return; }
        run.parkingInstalls++;
        if (run.connection.getPacketListener() != parking || !run.inManaged || run.factories != 0
                || run.server.getPlayerList().getPlayer(run.actor.getUUID()) != null) {
            fail(run, "PARK_NOT_REAL_ACTORLESS_LISTENER"); return;
        }
        releaseGateIfObserved(run);
    }

    public static void submitted(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        if (packet instanceof ClientboundKeepAlivePacket challenge) {
            if (listener instanceof P11ParkingPacketListener && run.ownerReturned && !run.inManaged
                    && run.connection.getPacketListener() == listener) {
                run.keepAliveSends++;
            }
            return;
        }
        if (packet instanceof ClientboundLoginPacket) { run.loginSends++; return; }
        if (!(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state = payload.state();
        if (state.kind() == Kind.ENTER_CONFIG && state.scope() == Scope.CONFIG && state.outcome() == Outcome.COMPLETED) {
            run.enterCompleted = state; return;
        }
        if (state.kind() != Kind.RETURN_TO_WORLD) { return; }
        try {
            if (P11C4aScenario.c6() && P11C4aC6Coordinator.laterState(state)) { return; }
            if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                // A retained notification may return through the send observer after the
                // original blocker ended. Only its first exact observation establishes
                // historical admission facts; repetition cannot start a new refusal.
                if (state.equals(run.waitState)) { run.repeatedWaitObservations++; return; }
                require(run.waitState == null && state.scope() == Scope.PREPLAY && state.actorGeneration() == 0
                        && (P11C4aScenario.c6() ? P11C4aC6Coordinator.finalRequest(state) : state.requestSeq() == 0)
                        && state.reason() == Reason.ACTIVE_OPERATION && run.inManaged && run.reloadActive
                        && roots(run, P11ControlBudgets.Root.OPERATION) > 0 && run.factories == 0
                        && run.constructed == 0 && run.loginFrames == 0 && run.earlyTaskCalls == 1,
                        "PARK_WRONG_FINAL_REFUSAL");
                run.waitState = state;
                if (P11C4aScenario.c6()) { P11C4aC6Coordinator.firstFinal(state); }
                releaseGateIfObserved(run);
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                if (state.equals(run.mayState)) { run.repeatedMayObservations++; return; }
                require(run.mayState == null && same(run.waitState, state) && !run.inManaged && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                        && listener instanceof P11ParkingPacketListener && run.factories == 0,
                        "PARK_MAY_BEFORE_REAL_OWNER_RETURN");
                run.mayState = state;
            } else if (state.outcome() == Outcome.COMPLETED) {
                require(run.retryRequest != null && run.mayState != null && state.requestSeq() == run.retryRequest.requestSeq()
                        && state.connectionEpoch() == run.mayState.connectionEpoch()
                        && state.sceneSerial() == run.mayState.sceneSerial(), "PARK_COMPLETION_WITHOUT_FRESH_RETRY");
                run.completed = state;
            }
        } catch (RuntimeException | Error failure) {
            if (run.failedState == null) {
                run.failedState = state;
                run.failedStateInManaged = run.inManaged;
                run.failedStateReloadActive = run.reloadActive;
                run.failedStateOwnerReturned = run.ownerReturned;
            }
            fail(run, "PARK_STATE_OBSERVER_" + P11C4aEvidence.failureCode(failure));
        }
    }

    /** Records only the exact challenge scalar before original send, allowing a fast real ACK. */
    public static void sending(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run != null && listener instanceof P11ParkingPacketListener && run.ownerReturned
                && listener.getConnection() == run.connection && run.connection.getPacketListener() == listener
                && packet instanceof ClientboundKeepAlivePacket challenge) {
            run.challenge = challenge.getId(); run.keepAliveAttempts++;
            if (run.pendingTransfer) { P11C4aParkingTransferProbe.challenge(listener, packet); }
        }
    }

    public static void keepAliveAck(ServerCommonPacketListenerImpl listener, long packetId,
            boolean wasPending, long beforeChallenge, boolean pending, long afterChallenge) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection
                || !(listener instanceof P11ParkingPacketListener) || !run.ownerReturned
                || run.retryRequest != null) { return; }
        if (run.connection.getPacketListener() == listener && wasPending && !pending
                && beforeChallenge == afterChallenge && packetId == beforeChallenge
                && run.keepAliveAttempts > 0 && packetId == run.challenge) { run.keepAliveAcks++; }
        else { fail(run, "PARK_KEEPALIVE_ACK_NOT_MATCHED_NATIVE_FIELDS"); }
    }

    public static void ingress(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof Request request)
                || request.kind() != Kind.RETURN_TO_WORLD) { return; }
        if (P11C4aScenario.c6()) { P11C4aC6Coordinator.ingress(request, connection); return; }
        if (request.command() != Command.TRY) { return; }
        run.tries++; run.retryRequest = request;
        if (!run.retryCue || run.tries != 1 || run.mayState == null || request.scope() != Scope.PREPLAY
                || request.actorGeneration() != 0 || request.requestSeq() <= run.mayState.requestSeq()
                || request.connectionEpoch() != run.mayState.connectionEpoch()
                || request.sceneSerial() != run.mayState.sceneSerial()) { fail(run, "PARK_RETRY_NOT_FRESH_MANUAL_REQUEST"); }
    }

    public static ResumeCall beginResume(P11ParkingPacketListener listener) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return null; }
        if (run.pendingTransfer) { P11C4aParkingTransferProbe.beforeResume(listener); }
        var scope = new ResumeCall(run.connection, RESUME.get()); RESUME.set(scope);
        run.resumeCalls++;
        if (!run.retryCue || run.tries != 1 || run.inManaged || run.resumeCalls != 1) { fail(run, "PARK_RESUME_WITHOUT_FRESH_RETRY"); }
        return scope;
    }

    public static void endResume(ResumeCall scope, boolean normal) {
        if (scope == null) { return; }
        var run = active;
        if (RESUME.get() != scope) { if (run != null) { fail(run, "PARK_RESUME_SCOPE_ORDER"); } return; }
        if (scope.previous == null) { RESUME.remove(); } else { RESUME.set(scope.previous); }
        if (run != null && run.connection == scope.connection) {
            if (normal) { run.resumeReturns++; } else { run.resumeThrows++; }
        }
    }

    public static void factory() {
        var run = observedRun();
        if (run != null) { run.factories++; if (resumeRun() == null) { fail(run, "PARK_FACTORY_BEFORE_MANUAL_RESUME"); } }
    }
    public static void constructed(ServerPlayer actor) {
        var run = observedRun();
        if (run != null) { run.constructed++; if (actor == run.actor || !actor.getUUID().equals(run.actor.getUUID())) { fail(run, "PARK_WRONG_CONSTRUCTED_B"); } }
    }
    public static void frame(Packet<?> packet) { var run = observedRun(); if (run != null && packet instanceof ClientboundLoginPacket) { run.loginFrames++; } }
    public static void place(Connection connection, boolean entering, boolean normal) {
        var run = active;
        if (run == null || connection != run.connection) { return; }
        if (resumeRun() == null) { fail(run, "PARK_PLACE_BEFORE_MANUAL_RESUME"); }
        if (entering) { run.places++; } else if (normal) { run.placeReturns++; }
        else { fail(run, "PARK_ORIGINAL_PLACE_THROW"); }
    }

    static Map<String, Object> pending() { var run = active; return run == null ? Map.of("active", false) : report(run, "PENDING_NOT_ACCEPTANCE"); }
    static void finishC6Wait() throws IOException {
        var run = active;
        require(P11C4aScenario.c6() && run != null && run.server.isSameThread() && run.failure == null
                && !run.connection.isConnected() && run.ownerReturned && run.reloadReturned
                && run.keepAliveAttempts > 0 && run.keepAliveSends > 0 && run.keepAliveAcks > 0
                && run.parkingInstalls == 1 && run.earlyTaskCalls == 1 && run.finalChecks == 1
                && run.factories == 0 && run.constructed == 0 && run.loginFrames == 0,
                "C6_EXPIRY_WITHOUT_REQUIRED_NATIVE_PARKED_ACK_OR_ZERO_BODY");
        P11C4aEvidence.write(run.output, "parking-c6-expiry.json", report(run,
                "ACTUAL_PARKED_KEEPALIVE_ACK_AFTER_FAILED_RETRY_BEFORE_EXPIRY"));
    }
    static void abort() { var run = active; if (run != null) { require(run.server.isSameThread(), "PARK_ABORT_NOT_MAIN"); cleanup(run); active = null; } }
    @SubscribeEvent static void stopped(ServerStoppedEvent event) {
        var run = active;
        if (run != null && event.getServer() == run.server) {
            if (run.pendingTransfer) { P11C4aParkingTransferProbe.release(); }
            run.gate.completeExceptionally(new IllegalStateException("PARK_SERVER_STOP")); active = null; RESUME.remove();
        }
    }

    private static void releaseGateIfObserved(Run run) {
        if (!run.gateReleased && run.waitState != null && run.parkingInstalls == 1 && run.listenerWaiting && run.inManaged) {
            require(run.factories == 0 && run.constructed == 0 && run.loginFrames == 0
                    && run.connection.getPacketListener() instanceof P11ParkingPacketListener,
                    "PARK_GATE_RELEASE_WITH_BODY");
            run.gateReleased = run.gate.complete(null);
            require(run.gateReleased, "PARK_RELOAD_GATE_ALREADY_TERMINAL");
        }
    }
    private static Run resumeRun() { var run = active; var call = RESUME.get(); return run != null && call != null && call.connection == run.connection ? run : null; }
    private static Run observedRun() { var run = active; return run != null && run.server.isSameThread()
            && (run.configDepth > 0 || resumeRun() == run) ? run : null; }
    private static long roots(Run run, P11ControlBudgets.Root root) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
        var body = source == null ? null : source.nativeRecipient(run.actor);
        require(body != null && body.actor == run.actor, "PARK_EXACT_SOURCE_A_MISSING");
        return body.account.nativeCounts[root.ordinal()];
    }
    private static boolean same(State first, State second) { return first != null && first.connectionEpoch() == second.connectionEpoch()
            && first.sceneSerial() == second.sceneSerial() && first.actorGeneration() == second.actorGeneration() && first.requestSeq() == second.requestSeq(); }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } run.gate.completeExceptionally(new IllegalStateException(code)); }
    private static void cleanup(Run run) {
        if (run.oldTick) { P11C4aOldTickProbe.release(); }
        if (run.pendingTransfer) { P11C4aParkingTransferProbe.release(); }
        run.gate.completeExceptionally(new IllegalStateException("PARK_OWNER_CLOSED"));
        if (run.mobLootSuppressed) { run.server.getGameRules().getRule(GameRules.RULE_DOMOBLOOT).set(run.mobLootBefore, run.server); run.mobLootSuppressed = false; }
        if (run.victim != null && !run.victim.isRemoved()) { run.victim.discard(); }
        RESUME.remove();
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static Map<String, Object> report(Run run, String status) {
        var out = new LinkedHashMap<String, Object>();
        if (run.pendingTransfer) { out.put("pendingTransfer", P11C4aParkingTransferProbe.report()); }
        if (run.oldTick) { out.put("oldTickInterleaving", P11C4aOldTickProbe.report()); }
        out.put("status", status); out.put("failure", run.failure == null ? "NONE" : run.failure);
        out.put("ticks", run.ticks); out.put("creditBaseline", run.nativeBefore); out.put("actualFopAtLivingDeath", run.fopAtDeath);
        out.put("earlyTasks", run.earlyTaskCalls); out.put("finalGuardChecks", run.finalChecks); out.put("nativeDeathCallbacks", run.deaths);
        out.put("actualNativeCreditAtLivingDeath", run.nativeAtDeath);
        out.put("actualQctxAtLivingDeath", run.qctxAtDeath);
        out.put("exactOriginalKillCreditAtLivingDeath", run.exactCreditAtDeath);
        out.put("lastHurtByMobStillExactAtEarlyTask", run.mobCreditStillSelectedAtTask);
        out.put("reloadReturned", run.reloadReturned); out.put("creditCallerReturned", run.ownerReturned); out.put("gateReleasedByRefusalAndParking", run.gateReleased);
        out.put("reloadListeners", run.listenerRegistrations); out.put("reloadListenerCalls", run.listenerCalls);
        out.put("listenerRegistrationOnServerThread", run.listenerRegistrationOnServerThread);
        out.put("listenerApplicationOnServerThread", run.listenerApplicationOnServerThread);
        out.put("managedEntries", run.managedEntries); out.put("managedReturns", run.managedReturns);
        out.put("configCalls", run.configCalls); out.put("configReturns", run.configReturns); out.put("parkingInstalls", run.parkingInstalls);
        out.put("resumeCalls", run.resumeCalls); out.put("resumeReturns", run.resumeReturns); out.put("resumeThrows", run.resumeThrows);
        out.put("factoryChecks", run.factories); out.put("constructedB", run.constructed); out.put("places", run.places); out.put("placeReturns", run.placeReturns);
        out.put("loginFrames", run.loginFrames); out.put("loginSends", run.loginSends); out.put("tries", run.tries);
        out.put("actualLiveK", run.liveK); out.put("actualEntryWaiting", run.liveWaiting); out.put("actualLiveScalarSamples", run.liveSamples);
        out.put("parkedKeepAliveAttempts", run.keepAliveAttempts); out.put("parkedKeepAliveSends", run.keepAliveSends);
        out.put("parkedMatchingAckNormalReturns", run.keepAliveAcks);
        out.put("enterConfig", run.enterCompleted); out.put("wait", run.waitState); out.put("may", run.mayState); out.put("completed", run.completed);
        out.put("repeatedExactWaitObservations", run.repeatedWaitObservations);
        out.put("repeatedExactMayObservations", run.repeatedMayObservations);
        if (run.failedState != null) {
            out.put("failedState", run.failedState);
            out.put("failedStateInManaged", run.failedStateInManaged);
            out.put("failedStateReloadActive", run.failedStateReloadActive);
            out.put("failedStateOwnerReturned", run.failedStateOwnerReturned);
        }
        out.put("xpBefore", run.xpBefore); out.put("nativeRewardXp", REWARD); out.put("fullC4aAcceptance", false);
        out.put("creditCallerXpBefore", run.creditCallerXpBefore);
        out.put("fixtureMobLootSuppressedOnlyAcrossNativeDeathCaller", run.mobLootObserved);
        if (run.mobLootObserved) {
            out.put("originalMobLootRule", run.mobLootBefore); out.put("mobLootRuleRestored", !run.mobLootSuppressed);
        }
        return out;
    }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor; final String role; final Path output; final Connection connection;
        final int xpBefore; final CompletableFuture<Void> gate = new CompletableFuture<>();
        LivingEntity victim;
        volatile String failure; volatile int tries, keepAliveAttempts, keepAliveSends, keepAliveAcks, listenerCalls;
        volatile long challenge; volatile Request retryRequest;
        State enterCompleted, waitState, mayState, completed, failedState;
        boolean failedStateInManaged, failedStateReloadActive, failedStateOwnerReturned;
        volatile boolean ownerReturned, reloadActive, listenerRegistrationOnServerThread;
        volatile int listenerRegistrations;
        boolean listenerApplicationOnServerThread;
        boolean pendingTransfer, oldTick, cloudReset;
        boolean switched, returnRequested, deathArmed, reloadReturned, listenerWaiting, inManaged, gateReleased, retryCue, liveWaiting,
                mobLootBefore, mobLootSuppressed, mobLootObserved, mobCreditStillSelectedAtTask, exactCreditAtDeath;
        long nativeBefore, nativeAtDeath, fopAtDeath, qctxAtDeath, liveSamples;
        long repeatedWaitObservations, repeatedMayObservations;
        int ticks, earlyTaskCalls, deaths, finalChecks, parkingInstalls, configCalls, configReturns, configDepth, managedEntries, managedReturns,
                resumeCalls, resumeReturns, resumeThrows, factories, constructed, places, placeReturns, loginFrames, loginSends, liveK;
        int creditCallerXpBefore = -1;
        Run(MinecraftServer server, ServerPlayer actor, String role, Path output) {
            this.server = server; this.actor = actor; this.role = role; this.output = output;
            connection = actor.connection.getConnection(); xpBefore = actor.totalExperience;
        }
    }
}
