package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import static com.yo1no.gramarye.P11C4aNativeObservations.Event.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameRules;

/** Excluded host-only finite episode. No native timeout, root, actor, or halt is fabricated. */
public final class P11C4aHostLeaveProbe {
    public interface Fields { boolean c4a$hostPending(); long c4a$hostChallenge(); }
    private static volatile Run active;
    private P11C4aHostLeaveProbe() { }

    static boolean ready(ServerPlayer host, ServerPlayer peer) {
        return rootsClear(host) && rootsClear(peer);
    }

    static void start(MinecraftServer server, ServerPlayer host, ServerPlayer peer, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && P11C4aEvidence.property("case").equals("c4a-host-lan")
                && server.isSameThread() && active == null && server.isSingleplayer() && server.isPublished()
                && server.isRunning() && host != peer && !host.isFakePlayer() && !peer.isFakePlayer()
                && server.isSingleplayerOwner(host.getGameProfile()) && !server.isSingleplayerOwner(peer.getGameProfile()),
                "HOST_LEAVE_START_TOPOLOGY");
        require(output.equals(P11C4aEvidence.root().resolve("server")), "HOST_LEAVE_OUTPUT");
        var run = new Run(server, host, peer, output);
        require(run.hostC.isMemoryConnection() && !run.peerC.isMemoryConnection() && run.peerC.isEncrypted(),
                "HOST_LEAVE_TRANSPORT_ROLES");
        unchanged(run); quiescent(host); quiescent(peer);
        active = run;
        P11C4aEvidence.cue(output, "host-leave-arm.ready");
    }

    static void tick() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 2400 && run.failure == null,
                "HOST_LEAVE_OWNER_DEADLINE_OR_OBSERVER");
        if (run.intent) { return; }
        unchanged(run);
        if (!run.dead) {
            if (!cue(run, "host", "host-leave-armed.ready") || !cue(run, "b", "host-leave-armed.ready")) { return; }
            quiescent(run.host); quiescent(run.peer);
            run.keepInventory = run.server.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            run.immediate = run.server.getGameRules().getBoolean(GameRules.RULE_DO_IMMEDIATE_RESPAWN);
            run.server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, run.server);
            run.server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(false, run.server);
            P11C4aC6EarlyGateProbe.arm(run.host, P11C4aC6EarlyGateProbe.Point.PLAY_FIRST);
            run.dead = true;
            run.host.kill();
            require(run.host.isDeadOrDying(), "HOST_LEAVE_NATIVE_DEATH");
            run.idle = run.host.getLastActionTime();
            return;
        }
        require(run.host.isDeadOrDying() && run.host.getLastActionTime() == run.idle
                && count(run, RESPAWN_CALL_ENTER) == run.bodies && count(run, RESPAWN_FRAME_PRODUCED) == run.frames
                && count(run, LOGIN_FRAME_PRODUCED) == run.logins, "HOST_LEAVE_REFUSAL_MUTATED_BODY_OR_IDLE");
        if (run.may == null || !cue(run, "host", "host-leave-stay.ready")) { return; }
        require(run.wait != null && P11C4aNativeObservations.tries(run.hostC) == run.tries + 1,
                "HOST_LEAVE_NOT_ONE_REFUSED_NATIVE_TRY");
        quiescent(run.host); quiescent(run.peer);
        if (run.since == 0) {
            require(P11C4aC6EarlyGateProbe.returned(), "HOST_LEAVE_REWARD_GATE_NOT_RETURNED");
            run.gate = P11C4aC6EarlyGateProbe.reportAndRelease();
            run.since = Util.getMillis(); run.firstTick = run.server.getTickCount();
            run.hostOwnerBaseline = run.hostOwners; run.peerOwnerBaseline = run.peerOwners;
            P11C4aEvidence.cue(run.output, "host-leave-observe.ready");
            return;
        }
        if (Util.getMillis() - run.since < 16_000 || run.peerAckAt < run.since
                || run.peerChallengeAt < run.since || run.peerSendReturns == 0 || run.animations != 1) { return; }
        if (!run.peerSampleRequested) {
            run.peerSampleRequested = true;
            P11C4aEvidence.cue(run.output, "host-leave-peer-sample.ready");
            return;
        }
        var peerOutput = run.output.getParent().resolve("client-b");
        if (!P11C4aEvidence.receiptPresent(peerOutput, "host-leave-peer-healthy.json")) { return; }
        var peerReceipt = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                peerOutput.resolve("host-leave-peer-healthy.json"))).getAsJsonObject();
        require(peerReceipt.get("challenge").getAsLong() == run.peerChallenge
                && peerReceipt.get("ack").getAsLong() == run.peerAck && run.peerAck == run.peerChallenge,
                "HOST_LEAVE_CROSS_ENDPOINT_CHALLENGE_MISMATCH");
        require(run.hostOwners > run.hostOwnerBaseline && run.peerOwners > run.peerOwnerBaseline && run.hostChallenges == 0
                && run.server.getTickCount() > run.firstTick && run.haltEntries == 0 && run.causeEntries == 0
                && run.logouts == 0 && run.cleanup == 0, "HOST_LEAVE_HEALTHY_NATIVE_COUNTS");
        if (!run.healthy) {
            run.healthy = true;
            P11C4aEvidence.write(run.output, "host-leave-healthy.json", report(run, "ACTUAL_HOST_REFUSAL_STAY_EXEMPTION_AND_LIVE_PEER"));
            P11C4aEvidence.cue(run.output, "host-leave-confirm.ready");
        }
    }

    /** Called before the original native MouseHandler callback, not a disconnect implementation. */
    static void beginTerminal(MinecraftServer server, Connection clientMemoryConnection) {
        var run = active;
        require(run != null && run.server == server && run.healthy && !run.intent && run.failure == null
                && clientMemoryConnection.isMemoryConnection() && clientMemoryConnection.isConnected()
                && run.peerC.isConnected() && run.server.isRunning(), "HOST_LEAVE_TERMINAL_INTENT");
        run.intent = true;
    }

    static boolean terminalIntent() { var run = active; return run != null && run.intent; }
    static boolean serverStopped() { var run = active; return run != null && run.stops == 1 && run.failure == null; }
    static void failureCleanup() { var run = active; if (run != null) { run.cleanup++; fail(run, "HOST_LEAVE_ENGINEERING_CLEANUP"); } }

    static void logout(ServerPlayer actor) {
        var run = active;
        if (run == null || actor.getServer() != run.server) { return; }
        if (!run.intent || run.cleanup != 0 || (run.logouts == 0 ? actor != run.host : run.logouts != 1 || actor != run.peer)) {
            fail(run, "HOST_LEAVE_UNEXPECTED_LOGOUT"); return;
        }
        run.logouts++;
    }

    static void stopped(MinecraftServer server) throws IOException {
        var run = active;
        if (run == null || run.server != server) { return; }
        run.stops++;
        boolean proved = run.intent && run.healthy && run.failure == null && run.cleanup == 0 && run.stops == 1
                && run.causeEntries == 1 && run.causeReturns == 1 && run.haltEntries == 1 && run.haltReturns == 1
                && run.logouts == 2 && run.leaveEntries == 1 && run.server.getPlayerList().getPlayerCount() == 0;
        if (!proved) { fail(run, "HOST_LEAVE_ORIGINAL_STOP_NOT_PROVED"); }
        P11C4aEvidence.write(run.output, "host-leave-stopped.json", report(run,
                proved ? "ORIGINAL_HOST_HALT_AND_SERVER_STOPPED" : "FAILED_ORIGINAL_STOP_EVIDENCE"));
        // Client leave RETURN is later: Minecraft.disconnect waits for this original stop.
    }

    public static void nativeLeave(boolean returned) {
        var run = active;
        if (run == null) { return; }
        if (!run.intent || !run.healthy || run.cleanup != 0) { fail(run, "HOST_LEAVE_UNARMED_BUTTON"); return; }
        if (returned) { run.leaveReturns++; } else { run.leaveEntries++; }
    }

    static Map<String, Object> terminalReport() {
        var run = active;
        require(run != null && run.failure == null && run.stops == 1 && run.leaveEntries == 1
                && run.leaveReturns == 1 && run.server.isShutdown() && run.cleanup == 0,
                "HOST_LEAVE_CLIENT_RETURN_WITHOUT_ORIGINAL_STOP");
        return report(run, "ORIGINAL_LEAVE_RETURN_AFTER_RETAINED_SERVER_SHUTDOWN");
    }

    public static void owner(ServerCommonPacketListenerImpl listener, boolean owner) {
        var run = active;
        if (run == null || run.intent) { return; }
        if (listener == run.hostListener) {
            if (!owner) { fail(run, "HOST_LEAVE_NATIVE_OWNER_FALSE"); } else { run.hostOwners++; }
        } else if (listener == run.peerListener) {
            if (owner) { fail(run, "HOST_LEAVE_NATIVE_PEER_OWNER_TRUE"); } else { run.peerOwners++; }
        }
    }

    public static void sent(ServerCommonPacketListenerImpl listener, Packet<?> packet, boolean returned) {
        var run = active;
        if (run == null || run.intent) { return; }
        if (packet instanceof ClientboundKeepAlivePacket keep) {
            if (listener == run.hostListener) { if (!returned) { run.hostChallenges++; } return; }
            if (listener != run.peerListener) { return; }
            if (!returned) {
                var fields = (Fields) listener;
                if (!fields.c4a$hostPending() || fields.c4a$hostChallenge() != keep.getId()) { fail(run, "HOST_LEAVE_CHALLENGE_FIELDS"); return; }
                run.peerChallenge = keep.getId(); run.peerChallengeAt = Util.getMillis();
            } else { run.peerSendReturns++; }
        }
        if (!returned || listener != run.hostListener || !(packet instanceof ClientboundCustomPayloadPacket custom)
                || !(custom.payload() instanceof P11TransitionStatePayload payload)) { return; }
        var state = payload.state();
        if (!run.dead || state.scope() != Scope.PLAY || state.kind() != Kind.DEATH || state.outcome() != Outcome.NOT_STARTED) { return; }
        if (state.availability() == Availability.WAIT_NOTIFY && state.reason() == Reason.ACTIVE_OPERATION) {
            if (run.wait != null && !run.wait.equals(state)) { fail(run, "HOST_LEAVE_CHANGED_WAIT"); }
            else { run.wait = state; }
        } else if (state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE) {
            if (run.wait == null || !sameRequest(run.wait, state)) { fail(run, "HOST_LEAVE_MAY_WITHOUT_WAIT"); }
            else { run.may = state; }
        }
    }

    public static long beforeAck(ServerCommonPacketListenerImpl listener, long id) {
        var run = active;
        if (run == null || run.intent || listener != run.peerListener) { return Long.MIN_VALUE; }
        var fields = (Fields) listener;
        return fields.c4a$hostPending() && fields.c4a$hostChallenge() == id ? id : Long.MIN_VALUE;
    }

    public static void afterAck(ServerCommonPacketListenerImpl listener, long selected) {
        var run = active;
        if (run == null || listener != run.peerListener || selected == Long.MIN_VALUE) { return; }
        var fields = (Fields) listener;
        if (!fields.c4a$hostPending() && fields.c4a$hostChallenge() == selected && selected == run.peerChallenge) {
            run.peerAcks++; run.peerAckAt = Util.getMillis(); run.peerAck = selected;
        } else { fail(run, "HOST_LEAVE_ACK_NOT_NATIVE_CONSUMPTION"); }
    }

    public static void animate(ServerGamePacketListenerImpl listener) {
        var run = active;
        if (run == null || listener != run.peerListener || run.since == 0 || run.intent) { return; }
        if (!run.server.isSameThread() || listener.player != run.peer) { fail(run, "HOST_LEAVE_PEER_ACTION_OWNER"); }
        else { run.animations++; }
    }

    public static void haltCause(ServerCommonPacketListenerImpl listener, MinecraftServer server, boolean wait, boolean returned) {
        var run = active;
        if (run == null || server != run.server) { return; }
        if (!run.intent || listener != run.hostListener || wait || run.cleanup != 0 || !run.peerC.isConnected()) {
            fail(run, "HOST_LEAVE_HALT_CAUSE"); return;
        }
        if (returned) { run.causeReturns++; } else { run.causeEntries++; }
    }

    public static void integratedHalt(MinecraftServer server, boolean wait, boolean returned) {
        var run = active;
        if (run == null || server != run.server) { return; }
        if (!run.intent || wait || run.causeEntries != 1 || run.cleanup != 0) { fail(run, "HOST_LEAVE_FOREIGN_HALT"); }
        if (returned) { run.haltReturns++; } else { run.haltEntries++; }
    }

    private static void unchanged(Run run) {
        require(run.hostC.isConnected() && run.peerC.isConnected() && run.server.isRunning()
                && run.hostC.getPacketListener() == run.hostListener && run.peerC.getPacketListener() == run.peerListener
                && run.server.getPlayerList().getPlayer(run.host.getUUID()) == run.host
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && P11NativeStorageBoundary.nativeSourceOwner(run.host) == run.hostSource
                && run.hostSource.nativeRecipient(run.host) == run.hostBody && run.hostBody.source.epoch() == run.hostEpoch
                && run.host.getStats() == run.hostBody.stats && run.host.getAdvancements() == run.hostBody.advancements
                && run.peer.isAlive(), "HOST_LEAVE_CURRENT_NATIVE_IDENTITIES");
    }

    private static void quiescent(ServerPlayer actor) {
        require(rootsClear(actor), "HOST_LEAVE_ROOTS_NOT_QUIESCENT");
    }

    private static boolean rootsClear(ServerPlayer actor) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        return body != null && body.complete && body.actor == actor && body.account.metadata == null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0;
    }

    private static long count(Run run, P11C4aNativeObservations.Event event) { return P11C4aNativeObservations.count(run.hostC, event); }
    private static boolean cue(Run run, String role, String leaf) throws IOException {
        return P11C4aEvidence.cuePresent(run.output.getParent().resolve("client-" + role), leaf);
    }
    private static boolean sameRequest(State a, State b) {
        return a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
                && a.actorGeneration() == b.actorGeneration() && a.requestSeq() == b.requestSeq()
                && a.scope() == b.scope() && a.kind() == b.kind();
    }
    private static Map<String, Object> report(Run run, String status) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", status); result.put("gate", run.gate); result.put("wait", run.wait); result.put("may", run.may);
        result.put("nativeHostOwnerReturnsTrue", run.hostOwners); result.put("nativePeerOwnerReturnsFalse", run.peerOwners);
        result.put("nativeHostOwnerReturnsDuringInterval", run.hostOwners - run.hostOwnerBaseline);
        result.put("nativePeerOwnerReturnsDuringInterval", run.peerOwners - run.peerOwnerBaseline);
        result.put("sameSourceBodyEpochAndCanonicalObjectsBeforeLeave", run.healthy);
        result.put("hostChallenges", run.hostChallenges); result.put("peerChallenge", run.peerChallenge); result.put("peerAck", run.peerAck);
        result.put("peerNativeAckConsumptions", run.peerAcks); result.put("peerChallengeSendReturns", run.peerSendReturns);
        result.put("peerNativeAnimateReturns", run.animations); result.put("nativeObservationMillis", run.since == 0 ? 0 : Util.getMillis() - run.since);
        result.put("serverTickAdvance", run.server.getTickCount() - run.firstTick); result.put("hostIdleUnchanged", run.host.getLastActionTime() == run.idle);
        result.put("originalHaltCauseEntries", run.causeEntries); result.put("originalHaltCauseReturns", run.causeReturns);
        result.put("integratedHaltEntries", run.haltEntries); result.put("integratedHaltReturns", run.haltReturns);
        result.put("nativeLogouts", run.logouts); result.put("serverStoppedEvents", run.stops);
        result.put("originalLeaveEntries", run.leaveEntries); result.put("originalLeaveReturns", run.leaveReturns);
        result.put("engineeringCleanupCalls", run.cleanup); result.put("failure", run.failure == null ? "NONE" : run.failure);
        result.put("hostHasJoinedAuthenticationClaimed", false); result.put("physicalInputClaimed", false); result.put("fullC4aAcceptance", false);
        return result;
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer host, peer; final Path output;
        final Connection hostC, peerC; final ServerGamePacketListenerImpl hostListener, peerListener;
        final P11QualifiedSourceOwner hostSource; final P11QualifiedSourceOwner.Body hostBody; final long hostEpoch;
        final long bodies, frames, logins, tries;
        volatile String failure; volatile boolean intent, healthy;
        boolean dead, keepInventory, immediate, peerSampleRequested; int ticks, firstTick;
        volatile int causeEntries, causeReturns, haltEntries, haltReturns, leaveEntries, leaveReturns, logouts, stops, cleanup;
        volatile long hostOwners, peerOwners, hostChallenges, peerChallenge, peerChallengeAt, peerAck, peerAckAt, peerAcks, peerSendReturns, animations;
        long idle, since, hostOwnerBaseline, peerOwnerBaseline; volatile State wait, may; Map<String, Object> gate;
        Run(MinecraftServer server, ServerPlayer host, ServerPlayer peer, Path output) {
            this.server = server; this.host = host; this.peer = peer; this.output = output;
            hostListener = host.connection; peerListener = peer.connection;
            hostSource = P11NativeStorageBoundary.nativeSourceOwner(host);
            hostBody = hostSource == null ? null : hostSource.nativeRecipient(host);
            require(hostBody != null, "HOST_LEAVE_INITIAL_SOURCE");
            hostEpoch = hostBody.source.epoch();
            hostC = hostListener.getConnection(); peerC = peerListener.getConnection();
            bodies = count(this, RESPAWN_CALL_ENTER); frames = count(this, RESPAWN_FRAME_PRODUCED);
            logins = count(this, LOGIN_FRAME_PRODUCED); tries = P11C4aNativeObservations.tries(hostC);
        }
    }
}
