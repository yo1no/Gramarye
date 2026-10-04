package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import static com.yo1no.gramarye.P11C4aNativeObservations.Event.*;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Excluded actual host CONFIG expiry. No synthetic time, native credit or teardown. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aHostExpiryProbe {
    public record Snapshot(int waiting, boolean targetEntry, boolean stopping) { }
    public interface ServiceView { Snapshot expiry$snapshot(Connection connection); Object expiry$limits(); }
    public interface EntryView { Connection expiry$connection(); Object expiry$control(); }
    public interface Fields { boolean expiry$pending(); long expiry$challenge(); }
    private static volatile Run active;
    private static final ThreadLocal<GateCall> GATE = new ThreadLocal<>();
    private P11C4aHostExpiryProbe() { }

    static boolean ready(ServerPlayer actor) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        return body != null && body.complete && source.canCopy(body) && body.account.metadata == null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0;
    }

    static void start(MinecraftServer server, ServerPlayer host, ServerPlayer peer, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_EXPIRY
                && P11C4aEvidence.property("case").equals("c4a-host-lan") && server.isSameThread()
                && active == null && server.isSingleplayer() && server.isPublished() && server.isRunning()
                && host != peer && !host.isFakePlayer() && !peer.isFakePlayer()
                && server.isSingleplayerOwner(host.getGameProfile()) && !server.isSingleplayerOwner(peer.getGameProfile())
                && ready(host) && ready(peer) && output.equals(P11C4aEvidence.root().resolve("server")),
                "HOST_EXPIRY_START");
        var run = new Run(server, host, peer, output);
        require(run.hostC.isMemoryConnection() && !run.peerC.isMemoryConnection() && run.peerC.isEncrypted(),
                "HOST_EXPIRY_REAL_TOPOLOGY");
        unchangedPeer(run); active = run;
        P11C4aEvidence.cue(output, "host-expiry-arm.ready");
    }

    static void tick() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400 && run.failure == null,
                "HOST_EXPIRY_OWNER_OR_DEADLINE");
        if (run.expiring) { return; }
        unchangedPeer(run);
        require(run.hostC.isConnected() && run.server.isRunning(), "HOST_EXPIRY_EARLY_DEPARTURE");
        if (!run.switched) {
            if (!cue(run, "host", "host-expiry-armed.ready") || !cue(run, "b", "host-expiry-armed.ready")) { return; }
            require(ready(run.host) && ready(run.peer), "HOST_EXPIRY_START_NOT_QUIESCENT");
            // Credit is established only while A is genuinely live/current, immediately before switch.
            run.nativeBefore = roots(run, P11ControlBudgets.Root.NATIVE_CREDIT);
            run.victim = EntityType.COW.create(run.host.serverLevel());
            require(run.victim != null, "HOST_EXPIRY_COW_UNAVAILABLE");
            run.victim.moveTo(run.host.getX() + 2, run.host.getY(), run.host.getZ(), 0, 0);
            require(run.host.serverLevel().addFreshEntity(run.victim)
                    && run.victim.hurt(run.host.damageSources().playerAttack(run.host), 1.0F)
                    && run.victim.getKillCredit() == run.host && run.victim.getLastHurtByMob() == run.host
                    && roots(run, P11ControlBudgets.Root.NATIVE_CREDIT) >= run.nativeBefore + 3,
                    "HOST_EXPIRY_LIVE_NATIVE_CREDIT");
            run.switched = true;
            run.host.connection.switchToConfig();
            return;
        }
        unchangedBodyAndCounts(run);
        if (!run.returnRequested) {
            if (run.enter == null || !cue(run, "host", "host-expiry-config-terminal.ready")) { return; }
            require(run.hostC.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                    && run.server.getPlayerList().getPlayer(run.host.getUUID()) == null
                    && run.hostLogouts == 1 && run.victim.getKillCredit() == run.host
                    && P11C4aNativeObservations.count(run.hostC, SWITCH_CALL_RETURN) == run.switchReturns + 1
                    && P11C4aNativeObservations.count(run.hostC, START_CONFIGURATION_SEND_RETURN) == run.starts + 1
                    && roots(run, P11ControlBudgets.Root.OPERATION) == 0,
                    "HOST_EXPIRY_REAL_CONFIG_TERMINAL_OR_CREDIT_EXPIRED");
            run.configuration = (ServerConfigurationPacketListenerImpl) run.hostC.getPacketListener();
            run.returnRequested = true;
            run.configuration.returnToWorld();
            return;
        }
        if (run.may == null || run.mayRecorded) { return; }
        require(run.wait != null && run.gateNormal && run.gateCalls == 1 && run.deaths == 1
                && run.refusals == 1 && run.firstMillis >= 0 && run.waitObject != null
                && run.host.totalExperience == run.xpBefore + 31 && roots(run, P11ControlBudgets.Root.OPERATION) == 0
                && roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                && run.hostC.getPacketListener() == run.configuration && snapshot(run).waiting() == 1,
                "HOST_EXPIRY_MAY_WITHOUT_REAL_REFUSAL");
        run.mayRecorded = true;
        P11C4aEvidence.write(run.output, "host-expiry-may.json", report(run, "ACTUAL_CONFIG_REFUSAL_MAY_NOT_EXPIRY_ACCEPTANCE"));
    }

    /** This operation stays entirely within the original earlyTask call stack. */
    public static Object originalGate(Object service, Object entry, Operation<Object> original) {
        var run = active;
        if (run == null || !(entry instanceof EntryView view) || view.expiry$connection() != run.hostC) {
            return original.call(service, entry);
        }
        require(run.returnRequested && run.server.isSameThread() && run.service == null && GATE.get() == null
                && run.hostC.getPacketListener() == run.configuration && run.victim.getKillCredit() == run.host,
                "HOST_EXPIRY_EARLY_GATE_SELECTION");
        run.service = service; run.control = (P11TransitionControl) view.expiry$control();
        var limits = (P11StartupLimits) ((ServiceView) service).expiry$limits();
        require(limits.maxWaitingConnections() == 1 && limits.mainQuantaPerTick() == 1
                && limits.admissionWaitMillis() == 45_000 && snapshot(run).waiting() == 1,
                "HOST_EXPIRY_EXACT_LOADED_K_Q_WAIT");
        var call = new GateCall(run, service, entry, original); GATE.set(call);
        try {
            // Native die selects the previously established player-credit field and opens real Fop.
            require(run.victim.hurt(run.victim.damageSources().magic(), 1000.0F), "HOST_EXPIRY_NATIVE_LETHAL_HURT");
            require(run.deaths == 1 && call.calls == 1 && call.result == P11TransitionControl.Gate.ACTIVE_OPERATION
                    && run.host.totalExperience == run.xpBefore + 31, "HOST_EXPIRY_ORIGINAL_GATE_AND_REWARD_TAIL");
            run.gateNormal = true; run.gateCalls = call.calls;
            // Whole die/loot/credit caller has returned. Ordinary entity removal releases its fields.
            run.victim.discard();
            require(run.victim.isRemoved() && roots(run, P11ControlBudgets.Root.OPERATION) == 0,
                    "HOST_EXPIRY_NATIVE_DEATH_UNWIND");
            return call.result;
        } finally { GATE.remove(); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void death(LivingDeathEvent event) {
        var call = GATE.get();
        if (call == null || event.getEntity() != call.run.victim) { return; }
        var run = call.run;
        run.nativeAtDeath = roots(run, P11ControlBudgets.Root.NATIVE_CREDIT);
        run.fopAtDeath = roots(run, P11ControlBudgets.Root.OPERATION);
        run.qctxAtDeath = roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT);
        run.victimCreditExactAtDeath = run.victim.getKillCredit() == run.host;
        require(++run.deaths == 1 && !event.isCanceled() && run.server.isSameThread()
                && run.server.getPlayerList().getPlayer(run.host.getUUID()) == null
                && run.victimCreditExactAtDeath && run.nativeAtDeath > 0
                && run.fopAtDeath > 0 && run.qctxAtDeath == 0,
                "HOST_EXPIRY_REAL_DETACHED_CREDIT_FOP");
        AdvancementRewards.Builder.experience(31).build().grant(run.host);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void xp(PlayerXpEvent.XpChange event) {
        var call = GATE.get();
        if (call == null || event.getEntity() != call.run.host || event.getAmount() != 31) { return; }
        var run = call.run;
        run.nativeAtGate = roots(run, P11ControlBudgets.Root.NATIVE_CREDIT);
        run.fopAtGate = roots(run, P11ControlBudgets.Root.OPERATION);
        run.qctxAtGate = roots(run, P11ControlBudgets.Root.COMMAND_CONTEXT);
        require(++call.calls == 1 && run.deaths == 1 && run.fopAtGate > 0
                && run.qctxAtGate == 0, "HOST_EXPIRY_XP_REAL_FOP");
        call.result = call.original.call(call.service, call.entry);
    }

    public static void waited(Object control, Object wait, long now, Object result, boolean refused) {
        var run = active;
        if (run == null || control != run.control) { return; }
        if (run.waitObject != null && run.waitObject != wait) { fail(run, "HOST_EXPIRY_WAIT_REPLACED"); return; }
        if (refused) {
            if (++run.refusals != 1 || !run.gateNormal || result != P11ControlBudgets.WaitResult.WAITING) {
                fail(run, "HOST_EXPIRY_REFUSAL_OBSERVATION"); return;
            }
            run.waitObject = wait; run.firstMillis = now; run.lastMillis = now;
        } else if (run.waitObject != null) {
            if (now < run.lastMillis) { fail(run, "HOST_EXPIRY_CLOCK_REGRESSION"); return; }
            run.lastMillis = now; run.observations++;
            if (result == P11ControlBudgets.WaitResult.EXPIRED) {
                if (now - run.firstMillis < 45_000) { fail(run, "HOST_EXPIRY_EARLY_CLOCK_EXPIRY"); }
                if (run.expiredMillis < 0) { run.expiredMillis = now; }
            } else if (result == P11ControlBudgets.WaitResult.WAITING) {
                if (now - run.firstMillis >= 45_000) { fail(run, "HOST_EXPIRY_LATE_WAITING_RESULT"); }
                run.lastWaitingMillis = now;
            } else { fail(run, "HOST_EXPIRY_WAIT_UNAVAILABLE"); }
        }
    }

    public static void disconnect(Object service, Object entry, String reason, boolean returned) {
        var run = active;
        if (run == null || service != run.service || !(entry instanceof EntryView view) || view.expiry$connection() != run.hostC) { return; }
        if (returned) { run.disconnectReturns++; return; }
        require(run.failure == null && reason.equals("gramarye.transition.expired") && run.expired != null
                && run.may != null && run.expiredMillis - run.firstMillis >= 45_000
                && run.hostC.getPacketListener() == run.configuration && run.peerC.isConnected()
                && snapshot(run).waiting() == 1 && run.hostLogouts == 1 && run.peerLogouts == 0,
                "HOST_EXPIRY_NOT_ORIGINAL_DEADLINE_CLOSE");
        unchangedPeer(run); unchangedBodyAndCounts(run);
        require(run.peerAcks > 0 && run.peerSends > 0 && run.animations == 1 && run.hostChallenges == 0
                && run.hostOwners > 0 && run.peerOwners > 0, "HOST_EXPIRY_LIVE_PEER_OR_HOST_EXEMPTION_MISSING");
        run.expiring = true; run.disconnectEntries++;
    }

    public static void retired(Object service, Object entry) {
        var run = active;
        if (run != null && service == run.service && entry instanceof EntryView view && view.expiry$connection() == run.hostC) {
            run.retireReturns++; run.afterRetire = snapshot(run);
        }
    }
    public static void stoppedControl(Object service) {
        var run = active;
        if (run != null && service == run.service) { run.controlStops++; run.atControlStop = snapshot(run); }
    }

    /** Observes only the real Foundation stop caller, never retires a root itself. */
    public static void foundationStopping(Object owner, MinecraftServer server) {
        var run = active;
        if (run == null || server != run.server) { return; }
        if (!(owner instanceof P11FoundationService foundation)) { fail(run, "HOST_EXPIRY_FOREIGN_FOUNDATION"); return; }
        run.foundationStopEntries++;
        run.foundation = foundation;
        run.foundationBeforeExact = run.foundationStopEntries == 1 && run.expiring && run.controlStops == 1
                && server.isSameThread() && foundation.transitions() == run.service
                && foundation.sourceOwner(server) == run.source && foundation.terminalSummary() == null
                && run.atControlStop != null && run.atControlStop.stopping();
        if (!run.foundationBeforeExact) { fail(run, "HOST_EXPIRY_FOUNDATION_ENTRY_MISMATCH"); }
    }

    /** RETURN means original source/slot retirement and root clearing have completed normally. */
    public static void foundationStopped(Object owner, MinecraftServer server) {
        var run = active;
        if (run == null || server != run.server) { return; }
        run.foundationStopReturns++;
        var foundation = run.foundation;
        if (foundation == null || foundation != owner || !run.foundationBeforeExact || !server.isSameThread()) {
            fail(run, "HOST_EXPIRY_FOUNDATION_RETURN_MISMATCH"); return;
        }
        var summary = foundation.terminalSummary();
        run.foundationSummaryExact = summary != null && summary == P11NativeStorageBoundary.terminalDiagnostics();
        run.foundationNativeStopNormal = summary != null && summary.nativeStopNormal();
        run.foundationRootCleared = foundation.transitions() == null && foundation.sourceOwner(server) == null
                && foundation.startupState(server).isEmpty();
        run.rootRetired = run.foundationStopReturns == 1 && run.foundationSummaryExact
                && run.foundationNativeStopNormal && run.foundationRootCleared;
        if (!run.rootRetired) { fail(run, "HOST_EXPIRY_FOUNDATION_RETIREMENT_UNPROVED"); }
        // Both EventBus listener orders are supported; neither callback alone fabricates the other.
        if (run.stops != 0) {
            try { sealStopped(run); }
            catch (IOException evidenceFailure) { fail(run, "HOST_EXPIRY_TERMINAL_EVIDENCE_IO"); }
        }
    }

    public static void owner(ServerCommonPacketListenerImpl listener, boolean owner) {
        var run = active; if (run == null || run.expiring) { return; }
        if (listener.getConnection() == run.hostC) { if (!owner) { fail(run, "HOST_EXPIRY_OWNER_FALSE"); } else { run.hostOwners++; } }
        if (listener == run.peer.connection) { if (owner) { fail(run, "HOST_EXPIRY_PEER_OWNER_TRUE"); } else { run.peerOwners++; } }
    }
    public static void sent(ServerCommonPacketListenerImpl listener, Packet<?> packet, boolean returned) {
        var run = active; if (run == null) { return; }
        if (packet instanceof ClientboundKeepAlivePacket keep) {
            if (listener.getConnection() == run.hostC) { if (!returned) { run.hostChallenges++; } return; }
            if (listener != run.peer.connection) { return; }
            if (!returned) {
                var fields = (Fields) listener;
                if (!fields.expiry$pending() || fields.expiry$challenge() != keep.getId()) { fail(run, "HOST_EXPIRY_NATIVE_CHALLENGE_FIELDS"); }
                run.peerChallenge = keep.getId();
            } else { run.peerSends++; }
        }
        if (!returned || listener.getConnection() != run.hostC || !(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state = payload.state();
        if (state.scope() != Scope.CONFIG) { return; }
        if (state.kind() == Kind.ENTER_CONFIG && state.outcome() == Outcome.COMPLETED) {
            if (state.actorGeneration() != 0 || state.requestSeq() != 0) { fail(run, "HOST_EXPIRY_ENTER_NOT_ACTORLESS_ZERO"); return; }
            if (run.enter != null && !run.enter.equals(state)) { fail(run, "HOST_EXPIRY_CHANGED_ENTER_TERMINAL"); }
            else { run.enter = state; }
        }
        if (state.kind() != Kind.RETURN_TO_WORLD) { return; }
        if (run.enter == null || state.sceneSerial() <= run.enter.sceneSerial() || state.actorGeneration() != 0
                || state.requestSeq() != 0) { fail(run, "HOST_EXPIRY_RETURN_NOT_FRESH_SERVER_SCENE"); return; }
        if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY && state.reason() == Reason.ACTIVE_OPERATION) {
            if (run.wait != null && !run.wait.equals(state)) { fail(run, "HOST_EXPIRY_CHANGED_REFUSAL"); } else { run.wait = state; }
        } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE) {
            if (run.wait == null || !same(run.wait, state)) { fail(run, "HOST_EXPIRY_MAY_IDENTITY"); } else { run.may = state; }
        } else if (state.outcome() == Outcome.EXPIRED) {
            if (run.may == null || !same(run.may, state) || state.reason() != Reason.WAIT_EXPIRED
                    || state.availability() != Availability.DISABLED || run.expiredMillis < 0) { fail(run, "HOST_EXPIRY_EXPIRED_IDENTITY"); }
            else { run.expired = state; }
        }
    }
    public static long beforeAck(ServerCommonPacketListenerImpl listener, long id) {
        var run = active; if (run == null || listener != run.peer.connection) { return Long.MIN_VALUE; }
        var fields = (Fields) listener;
        return fields.expiry$pending() && fields.expiry$challenge() == id ? id : Long.MIN_VALUE;
    }
    public static void afterAck(ServerCommonPacketListenerImpl listener, long selected) {
        var run = active; if (run == null || listener != run.peer.connection || selected == Long.MIN_VALUE) { return; }
        var fields = (Fields) listener;
        if (!fields.expiry$pending() && fields.expiry$challenge() == selected && selected == run.peerChallenge) { run.peerAcks++; }
        else { fail(run, "HOST_EXPIRY_ORIGINAL_ACK_NOT_CONSUMED"); }
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var run = active; if (run != null && listener == run.peer.connection && !run.expiring) { run.animations++; }
    }
    public static void haltCause(ServerCommonPacketListenerImpl listener, MinecraftServer server, boolean wait, boolean returned) {
        var run = active; if (run == null || run.server != server) { return; }
        if (!run.expiring || listener != run.configuration || wait || run.cleanup != 0 || !run.peerC.isConnected()) { fail(run, "HOST_EXPIRY_FOREIGN_HALT_CAUSE"); }
        if (returned) { run.causeReturns++; } else { run.causeEntries++; }
    }
    public static void integratedHalt(MinecraftServer server, boolean wait, boolean returned) {
        var run = active; if (run == null || run.server != server) { return; }
        if (!run.expiring || wait || run.causeEntries != 1 || run.cleanup != 0) { fail(run, "HOST_EXPIRY_FOREIGN_HALT"); }
        if (returned) { run.haltReturns++; } else { run.haltEntries++; }
    }
    static void logout(ServerPlayer actor) {
        var run = active; if (run == null || actor.getServer() != run.server) { return; }
        if (actor == run.host && run.switched && !run.returnRequested && run.hostLogouts++ == 0) { return; }
        if (actor == run.peer && run.expiring && run.haltEntries == 1 && run.peerLogouts++ == 0) { return; }
        fail(run, "HOST_EXPIRY_UNEXPECTED_LOGOUT");
    }
    static void failureCleanup() { var run = active; if (run != null) { run.cleanup++; fail(run, "HOST_EXPIRY_ENGINEERING_CLEANUP"); } }
    static boolean terminalExpected() { var run = active; return run != null && run.expiring; }

    static void stopped(MinecraftServer server) throws IOException {
        var run = active; if (run == null || run.server != server) { return; }
        run.stops++;
        if (run.foundationStopReturns != 0) { sealStopped(run); }
    }

    private static void sealStopped(Run run) throws IOException {
        if (run.stoppedWritten || run.stops == 0 || run.foundationStopReturns == 0) { return; }
        var server = run.server;
        boolean nativeTerminal = run.failure == null && run.expiring && run.stops == 1 && run.cleanup == 0
                && run.disconnectEntries == 1 && run.disconnectReturns == 1 && run.causeEntries == 1
                && run.causeReturns == 1 && run.haltEntries == 1 && run.haltReturns == 1
                && run.hostLogouts == 1 && run.peerLogouts == 1 && run.controlStops == 1
                && server.getPlayerList().getPlayerCount() == 0;
        var terminal = P11NativeStorageBoundary.terminalDiagnostics();
        boolean data = terminal != null && terminal.nativeStopNormal() && terminal.failures() == 0
                && terminal.resources().inFlight() == 0 && terminal.resources().sealedSnapshots() == 0
                && terminal.resources().dirtyUuids() == 0;
        boolean playerXpSaved = false, hostXpSaved = false, readbackNormal = false;
        try {
            var player = NbtIo.readCompressed(server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(run.host.getUUID() + ".dat"), NbtAccounter.unlimitedHeap());
            var level = NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT)
                    .resolve("level.dat"), NbtAccounter.unlimitedHeap());
            playerXpSaved = savedXp(run, player);
            hostXpSaved = level.get("Data") instanceof CompoundTag dataTag
                    && dataTag.get("Player") instanceof CompoundTag hostTag && savedXp(run, hostTag);
            readbackNormal = true;
        } catch (IOException readbackFailure) {
            // No replacement save or primary masking. The original stop evidence remains separate.
        }
        data = data && readbackNormal && playerXpSaved && hostXpSaved;
        boolean entryKReleased = run.retireReturns == 1 && run.afterRetire != null
                && run.afterRetire.waiting() == 0 && !run.afterRetire.targetEntry();
        // Whole-slot terminal is distinct from a per-entry decrement; it requires the exact
        // original Foundation normal RETURN after native saves, never just isShutdown/stop().
        boolean kReleased = entryKReleased || run.rootRetired;
        var values = report(run, nativeTerminal && data && kReleased ? "ACTUAL_HOST_EXPIRY_WITH_SEPARATE_K_AND_DATA_TERMINALS"
                : "INCOMPLETE_HOST_EXPIRY_TERMINAL_COMPONENTS");
        values.put("nativeExpiryHaltTerminal", nativeTerminal); values.put("actualKReleaseProved", kReleased);
        values.put("perEntryKReleaseProved", entryKReleased); values.put("wholeFoundationRootRetirementProved", run.rootRetired);
        values.put("originalDataStopComplete", data); values.put("terminalDiagnosticsPresent", terminal != null);
        values.put("originalFilesReadbackNormal", readbackNormal); values.put("originalPlayerDatContainsActualXp", playerXpSaved);
        values.put("originalLevelPlayerContainsActualXp", hostXpSaved);
        if (terminal != null) { values.put("nativeStopNormal", terminal.nativeStopNormal()); values.put("nativeFailures", terminal.failures());
            values.put("remainingDirtyUuids", terminal.resources().dirtyUuids()); values.put("physicalWrites", terminal.writes()); }
        P11C4aEvidence.write(run.output, "host-expiry-stopped.json", values);
        run.stoppedWritten = true; run.nativeTerminal = nativeTerminal; run.complete = nativeTerminal && data && kReleased;
    }
    static boolean nativeStopped() { var run = active; return run != null && run.stops == 1 && run.nativeTerminal; }
    static boolean complete() { var run = active; return run != null && run.complete; }
    private static boolean savedXp(Run run, CompoundTag player) {
        return player.hasUUID("UUID") && player.getUUID("UUID").equals(run.host.getUUID())
                && player.contains("XpTotal", 3) && player.getInt("XpTotal") == run.xpBefore + 31;
    }
    private static Snapshot snapshot(Run run) { return ((ServiceView) run.service).expiry$snapshot(run.hostC); }
    private static long roots(Run run, P11ControlBudgets.Root kind) { return run.body.account.nativeCounts[kind.ordinal()]; }
    private static boolean cue(Run run, String role, String name) throws IOException { return P11C4aEvidence.cuePresent(run.output.getParent().resolve("client-" + role), name); }
    private static boolean same(State a, State b) { return a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
            && a.scope() == b.scope() && a.kind() == b.kind() && a.actorGeneration() == b.actorGeneration() && a.requestSeq() == b.requestSeq(); }
    private static void unchangedPeer(Run run) {
        require(run.peer.isAlive() && run.peerC.isConnected() && run.peerC.getPacketListener() == run.peer.connection
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && P11NativeStorageBoundary.nativeSourceOwner(run.peer) == run.peerSource
                && run.peerSource.nativeRecipient(run.peer) == run.peerBody, "HOST_EXPIRY_PEER_CHANGED");
    }
    private static void unchangedBodyAndCounts(Run run) {
        require(P11NativeStorageBoundary.nativeSourceOwner(run.host) == run.source && run.source.nativeRecipient(run.host) == run.body
                && run.body.source.epoch() == run.epoch && run.host.getAdvancements() == run.body.advancements && run.host.getStats() == run.body.stats
                && P11C4aNativeObservations.count(run.hostC, LOGIN_FRAME_PRODUCED) == run.loginFrames
                && P11C4aNativeObservations.count(run.hostC, RESPAWN_FRAME_PRODUCED) == run.respawnFrames
                && P11C4aNativeObservations.count(run.hostC, FACTORY_TICKET_CHECKED) == run.factories
                && P11C4aNativeObservations.count(run.hostC, CONFIG_CALL_ENTER) == run.configBodies
                && P11C4aNativeObservations.tries(run.hostC) == 0, "HOST_EXPIRY_EXTRA_BODY_FRAME_OR_TRY");
    }
    private static Map<String, Object> report(Run run, String status) {
        var out = new LinkedHashMap<String, Object>(); out.put("status", status);
        out.put("enterConfiguration", run.enter); out.put("firstRefusal", run.wait); out.put("may", run.may); out.put("expired", run.expired);
        out.put("firstRefusalClockMillis", run.firstMillis); out.put("lastObservedClockMillis", run.lastMillis);
        out.put("firstExpiredClockMillis", run.expiredMillis); out.put("actualElapsedMillis", run.expiredMillis < 0 ? -1 : run.expiredMillis - run.firstMillis);
        out.put("lastWaitingClockMillis", run.lastWaitingMillis);
        out.put("startupK", 1); out.put("startupMainQuanta", 1); out.put("startupAdmissionWaitMillis", 45_000);
        out.put("originalRefusals", run.refusals); out.put("originalWaitObservations", run.observations);
        out.put("originalGateCalls", run.gateCalls); out.put("originalGateReturned", run.gateNormal);
        out.put("nativeDeathCallbacks", run.deaths); out.put("realFopAtDeath", run.fopAtDeath); out.put("realFopAtXpGate", run.fopAtGate);
        out.put("realNativeCreditAtDeath", run.nativeAtDeath); out.put("realQctxAtDeath", run.qctxAtDeath);
        out.put("exactVictimKillCreditAtDeath", run.victimCreditExactAtDeath);
        out.put("realNativeCreditAtXpGate", run.nativeAtGate); out.put("realQctxAtXpGate", run.qctxAtGate);
        out.put("nativeRewardXpDelta", run.host.totalExperience - run.xpBefore); out.put("nativeHostOwnerTrue", run.hostOwners);
        out.put("nativeHostChallenges", run.hostChallenges); out.put("nativePeerOwnerFalse", run.peerOwners);
        out.put("nativePeerChallengeSends", run.peerSends); out.put("nativePeerAckConsumptions", run.peerAcks); out.put("nativePeerActions", run.animations);
        out.put("originalExpiryDisconnectEntries", run.disconnectEntries); out.put("originalExpiryDisconnectReturns", run.disconnectReturns);
        out.put("originalCommonHaltEntries", run.causeEntries); out.put("originalCommonHaltReturns", run.causeReturns);
        out.put("originalIntegratedHaltEntries", run.haltEntries); out.put("originalIntegratedHaltReturns", run.haltReturns);
        out.put("hostSwitchLogouts", run.hostLogouts); out.put("peerHaltLogouts", run.peerLogouts); out.put("serverStoppedEvents", run.stops);
        out.put("nativeControlRetireReturns", run.retireReturns); out.put("afterRetire", run.afterRetire); out.put("atControlStop", run.atControlStop);
        out.put("originalFoundationStopEntries", run.foundationStopEntries); out.put("originalFoundationStopReturns", run.foundationStopReturns);
        out.put("foundationExactBefore", run.foundationBeforeExact); out.put("foundationExactTerminalSummary", run.foundationSummaryExact);
        out.put("foundationNativeStopNormal", run.foundationNativeStopNormal); out.put("foundationRootCleared", run.foundationRootCleared);
        out.put("engineeringCleanupCalls", run.cleanup); out.put("failure", run.failure == null ? "NONE" : run.failure);
        out.put("fullC4aAcceptance", false); out.put("hostHasJoinedAuthenticationClaimed", false); return out;
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class GateCall {
        final Run run; final Object service, entry; final Operation<Object> original; int calls; Object result;
        GateCall(Run run, Object service, Object entry, Operation<Object> original) { this.run = run; this.service = service; this.entry = entry; this.original = original; }
    }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer host, peer; final Connection hostC, peerC; final Path output;
        final P11QualifiedSourceOwner source, peerSource; final P11QualifiedSourceOwner.Body body, peerBody;
        final long epoch, loginFrames, respawnFrames, factories, configBodies, switchReturns, starts; final int xpBefore;
        volatile String failure; volatile State enter, wait, may, expired; volatile boolean expiring;
        Object service, waitObject; P11TransitionControl control; ServerConfigurationPacketListenerImpl configuration; Cow victim;
        P11FoundationService foundation;
        int foundationStopEntries, foundationStopReturns;
        boolean foundationBeforeExact, foundationSummaryExact, foundationNativeStopNormal, foundationRootCleared, rootRetired, stoppedWritten;
        long nativeBefore, nativeAtDeath, fopAtDeath, qctxAtDeath, nativeAtGate, fopAtGate, qctxAtGate;
        long firstMillis = -1, lastMillis = -1, expiredMillis = -1, lastWaitingMillis = -1, observations;
        boolean victimCreditExactAtDeath;
        int ticks, deaths, gateCalls, refusals, disconnectEntries, disconnectReturns, retireReturns, controlStops, stops, cleanup;
        volatile int hostLogouts, peerLogouts, causeEntries, causeReturns, haltEntries, haltReturns;
        volatile long hostOwners, hostChallenges, peerOwners, peerChallenge, peerSends, peerAcks, animations;
        boolean switched, returnRequested, gateNormal, mayRecorded, nativeTerminal, complete; Snapshot afterRetire, atControlStop;
        Run(MinecraftServer server, ServerPlayer host, ServerPlayer peer, Path output) {
            this.server = server; this.host = host; this.peer = peer; this.output = output;
            hostC = host.connection.getConnection(); peerC = peer.connection.getConnection();
            source = P11NativeStorageBoundary.nativeSourceOwner(host); body = source.nativeRecipient(host); epoch = body.source.epoch();
            peerSource = P11NativeStorageBoundary.nativeSourceOwner(peer); peerBody = peerSource.nativeRecipient(peer); xpBefore = host.totalExperience;
            loginFrames = P11C4aNativeObservations.count(hostC, LOGIN_FRAME_PRODUCED); respawnFrames = P11C4aNativeObservations.count(hostC, RESPAWN_FRAME_PRODUCED);
            factories = P11C4aNativeObservations.count(hostC, FACTORY_TICKET_CHECKED); configBodies = P11C4aNativeObservations.count(hostC, CONFIG_CALL_ENTER);
            switchReturns = P11C4aNativeObservations.count(hostC, SWITCH_CALL_RETURN); starts = P11C4aNativeObservations.count(hostC, START_CONFIGURATION_SEND_RETURN);
        }
    }
}
