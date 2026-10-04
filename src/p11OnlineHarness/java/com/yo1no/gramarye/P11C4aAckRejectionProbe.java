package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Excluded, real remote ACK input perturbations; no challenge/time/actor field is written. */
public final class P11C4aAckRejectionProbe {
    public enum Mode { WRONG_ID, STALE_ID, NATIVE_TIMEOUT }
    public interface Fields {
        long c4a$ackTime(); boolean c4a$ackPending(); long c4a$ackChallenge(); int c4a$ackLatency();
    }
    public interface LockObservation { boolean c4a$ackLockHeldByCurrentThread(); }
    private record Frame(long time, boolean pending, long challenge, int latency, long phase) { }
    private static volatile Run active;
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();
    private P11C4aAckRejectionProbe() { }

    static Mode selectedMode() {
        return switch (P11C4aScenario.MODE) {
            case KEEPALIVE_WRONG -> Mode.WRONG_ID;
            case KEEPALIVE_STALE -> Mode.STALE_ID;
            case KEEPALIVE_TIMEOUT -> Mode.NATIVE_TIMEOUT;
            default -> null;
        };
    }

    static void start(MinecraftServer server, ServerPlayer target, ServerPlayer peer, Mode mode, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && active == null && server.isSameThread()
                && server.isDedicatedServer() && server.usesAuthentication() && target != peer
                && target.getServer() == server && peer.getServer() == server
                && !target.isFakePlayer() && !peer.isFakePlayer()
                && server.getPlayerList().getPlayer(target.getUUID()) == target
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(target)
                && P11NativeStorageBoundary.nativeDeliveryEligible(peer), "ACK_FIXTURE_REAL_DEDICATED_ACTORS");
        var connection = target.connection.getConnection();
        require(connection.isEncrypted() && !connection.isMemoryConnection() && connection.isConnected()
                && peer.connection.getConnection().isEncrypted()
                && peer.connection.getConnection() != connection, "ACK_FIXTURE_REAL_CONNECTIONS");
        active = new Run(server, target, peer, mode, output);
        P11C4aEvidence.write(output, "ack-rejection-armed.json", report("ARMED_NOT_ACCEPTANCE"));
        P11C4aEvidence.cue(output, "a-ack-rejection-arm.ready");
    }

    static boolean tick(Path clientOutput) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && ++run.ticks <= 1200
                && run.failure == null, "ACK_FIXTURE_DEADLINE_OR_OBSERVER_FAILURE");
        if (run.rejected == null) {
            require(run.target.isAlive() && run.server.getPlayerList().getPlayer(run.target.getUUID()) == run.target,
                    "ACK_TARGET_CHANGED_BEFORE_NATIVE_REJECTION");
        }
        require(run.peer.connection.getConnection() == run.peerConnection && run.peerConnection.isConnected()
                && run.peer.isAlive()
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && run.peer.getAdvancements() == run.peerAdvancements && run.peer.getStats() == run.peerStatistics
                && P11NativeStorageBoundary.nativeSourceOwner(run.peer) == run.peerSource
                && run.peerSource.nativeRecipient(run.peer) == run.peerBody
                && P11NativeStorageBoundary.nativeDeliveryEligible(run.peer), "ACK_REJECTION_DISTURBED_PEER");
        run.peerExactLastTick = true;
        if (run.rejected == null || run.disconnectReturns == 0 || run.targetLogouts == 0
                || run.connection.isConnected() || !P11C4aEvidence.receiptPresent(clientOutput, "ack-rejection.json")) { return false; }
        require(run.disconnectCalls == 1 && run.disconnectReturns == 1 && run.targetLogouts == 1
                && run.server.getPlayerList().getPlayer(run.target.getUUID()) == null
                && run.lastChallengeSend == run.rejected.challenge && run.rejected.pending
                && run.rejected.time == run.rejected.challenge && run.rejected.phase > 0
                && run.disconnectUnlocked && run.nativeReasonWasTimeout,
                "ACK_REJECTION_NOT_ORIGINAL_NATIVE_TERMINAL");
        if (run.mode == Mode.NATIVE_TIMEOUT) {
            require(run.nativePath.equals("NATIVE_KEEPALIVE_TIMEOUT") && run.rejectionAckReturns == 0
                    && run.nativeDueTicks == 1 && run.timeoutNow - run.rejected.time >= 15000L
                    && run.afterReject != null && run.afterReject.equals(run.rejected), "ACK_NATIVE_TIMEOUT_NOT_FIRST_DUE_TICK");
        } else {
            require(run.nativePath.equals("NATIVE_ACK_REJECTION") && run.rejectionAckReturns == 1
                    && run.afterReject != null && run.afterReject.equals(run.rejected)
                    && run.rejectedId != run.rejected.challenge, "ACK_NATIVE_REJECTION_FIELDS_CHANGED");
            if (run.mode == Mode.WRONG_ID) {
                require(run.rejectedId == (run.rejected.challenge ^ 1L), "ACK_WRONG_INPUT_NOT_OBSERVED_DERIVATION");
            } else {
                require(run.previousConsumed != null && !run.previousConsumed.pending
                        && run.rejectedId == run.previousConsumed.challenge
                        && run.previousConsumed.phase == run.rejected.phase
                        && run.previousConsumed.challenge != run.rejected.challenge,
                        "ACK_STALE_INPUT_NOT_PREVIOUS_ACTUALLY_CONSUMED_CHALLENGE");
            }
        }
        P11C4aEvidence.write(run.output, "ack-rejection.json", report("ACTUAL_NATIVE_ACK_TERMINAL_CONTAINED_NOT_FULL_C4A"));
        active = null;
        return true;
    }

    public static void challenge(ServerCommonPacketListenerImpl listener, Packet<?> packet, boolean returned) {
        var run = matching(listener);
        if (run == null || !(packet instanceof ClientboundKeepAlivePacket challenge)) { return; }
        if (returned) {
            require(run.lastChallengeStarted == challenge.getId(), "ACK_CHALLENGE_RETURN_WITHOUT_ORIGINAL_ENTRY");
            run.lastChallengeSend = challenge.getId(); run.challengeReturns++;
            return;
        }
        require(run.server.isSameThread() && listener == run.listener && run.connection.getPacketListener() == listener
                && unlocked(run), "ACK_CHALLENGE_NOT_CURRENT_OR_UNLOCKED");
        var fields = frame(listener);
        require(fields.pending && fields.time == fields.challenge && fields.challenge == challenge.getId(), "ACK_CHALLENGE_NOT_NATIVE_FIELDS");
        // Publish before original send: a real Netty ACK may arrive before this send returns.
        run.lastChallengeStarted = challenge.getId();
    }

    public static void nativeClock(ServerCommonPacketListenerImpl listener, long now) {
        var run = matching(listener);
        if (run == null || run.mode != Mode.NATIVE_TIMEOUT || listener != run.listener) { return; }
        var fields = frame(listener);
        if (fields.pending && now - fields.time >= 15000L) { run.nativeDueTicks++; run.timeoutNow = now; }
    }

    /** Call-local observation established before the original native field region. */
    public static final class Scope {
        private final Run run; private final ServerCommonPacketListenerImpl listener;
        private final long id; private final boolean timeout; private final Frame before; private final Scope previous;
        private Scope(Run run, ServerCommonPacketListenerImpl listener, long id, boolean timeout) {
            this.run = run; this.listener = listener; this.id = id; this.timeout = timeout;
            before = frame(listener); previous = SCOPE.get();
        }
    }
    public static Scope begin(ServerCommonPacketListenerImpl listener, long id, boolean timeout) {
        var run = matching(listener);
        if (run == null || listener != run.listener) { return null; }
        var scope = new Scope(run, listener, id, timeout);
        SCOPE.set(scope);
        return scope;
    }
    /** Actual native disconnect(Component) HEAD, after all call-site wrappers released locks. */
    public static void rejecting(ServerCommonPacketListenerImpl listener, Component reason) {
        var run = matching(listener);
        var scope = SCOPE.get();
        if (run == null || scope == null || scope.run != run || scope.listener != listener) { return; }
        require(listener == run.listener && run.connection.getPacketListener() == listener && run.target.isAlive() && unlocked(run)
                && ++run.disconnectCalls == 1 && run.rejected == null
                && reason.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("disconnect.timeout"), "ACK_REJECTION_NOT_EXACT_NATIVE_DISCONNECT");
        run.rejected = frame(listener);
        require(run.rejected.pending && run.rejected.challenge == run.lastChallengeStarted
                && (scope.timeout == (run.mode == Mode.NATIVE_TIMEOUT)), "ACK_REJECTION_NOT_SELECTED_NATIVE_CHALLENGE");
        run.rejectedId = scope.id;
        run.nativePath = scope.timeout ? "NATIVE_KEEPALIVE_TIMEOUT" : "NATIVE_ACK_REJECTION";
        run.disconnectUnlocked = true; run.nativeReasonWasTimeout = true;
    }

    public static void disconnectReturned(ServerCommonPacketListenerImpl listener) {
        var run = matching(listener);
        var scope = SCOPE.get();
        if (run == null || scope == null || scope.run != run || run.rejected == null) { return; }
        run.disconnectReturns++;
        if (run.mode == Mode.NATIVE_TIMEOUT) { run.afterReject = frame(listener); }
    }

    public static void end(Scope scope, boolean normal) {
        if (scope == null) { return; }
        try {
            var run = scope.run;
            if (!normal) { run.failure = "ACK_ORIGINAL_NATIVE_BODY_THROW"; return; }
            if (scope.timeout) { return; }
            if (run.rejected != null && scope.id == run.rejectedId) {
                require(scope.before.pending && scope.before.challenge == run.rejected.challenge && unlocked(run),
                        "ACK_REJECTION_ORIGINAL_ENTRY_MISMATCH");
                run.afterReject = frame(scope.listener); run.rejectionAckReturns++;
            } else if (scope.before.pending && scope.id == scope.before.challenge) {
                var consumed = frame(scope.listener);
                require(!consumed.pending && consumed.challenge == scope.id && consumed.time == scope.id,
                        "ACK_PRIOR_REAL_SUCCESS_NOT_CONSUMED");
                run.previousConsumed = consumed; run.successfulAcks++;
            }
        } finally {
            if (scope.previous == null) { SCOPE.remove(); } else { SCOPE.set(scope.previous); }
        }
    }

    static void loggedOut(ServerPlayer actor) {
        var run = active;
        if (run == null || actor != run.target) { return; }
        require(run.server.isSameThread() && run.rejected != null, "ACK_UNEXPECTED_TARGET_LOGOUT");
        run.targetLogouts++;
    }
    static boolean expectsTerminal() { var run = active; return run != null && run.rejected != null; }
    static Map<String, Object> pending() { return report("PENDING_NOT_ACCEPTANCE"); }
    static void abort() { active = null; }
    private static Run matching(ServerCommonPacketListenerImpl listener) {
        var run = active; return run != null && listener.getConnection() == run.connection ? run : null;
    }
    private static Frame frame(ServerCommonPacketListenerImpl listener) {
        var fields = (Fields) listener;
        return new Frame(fields.c4a$ackTime(), fields.c4a$ackPending(), fields.c4a$ackChallenge(), fields.c4a$ackLatency(),
                ((P11KeepAliveBoundary.CommonAccess) listener).p11$keepAlivePhase());
    }
    private static boolean unlocked(Run run) {
        return !((LockObservation) (Object) ((P11KeepAliveBoundary.ConnectionAccess) run.connection)
                .p11$keepAliveGuard()).c4a$ackLockHeldByCurrentThread();
    }
    private static Map<String, Object> report(String status) {
        var run = active;
        if (run == null) { return Map.of("active", false); }
        var out = new LinkedHashMap<String, Object>();
        out.put("status", status); out.put("mode", run.mode.name()); out.put("ticks", run.ticks);
        out.put("failure", run.failure == null ? "NONE" : run.failure); out.put("nativePath", run.nativePath);
        out.put("rejectedInputId", run.mode == Mode.NATIVE_TIMEOUT ? null : run.rejectedId);
        out.put("actualPendingChallenge", run.rejected); out.put("afterOriginalHandler", run.afterReject);
        out.put("previousActuallyConsumedChallenge", run.previousConsumed);
        out.put("nativeChallengeSendReturns", run.challengeReturns); out.put("actualSuccessfulAcks", run.successfulAcks);
        out.put("rejectedAckHandlerReturns", run.rejectionAckReturns); out.put("originalDisconnectCalls", run.disconnectCalls);
        out.put("originalDisconnectReturns", run.disconnectReturns); out.put("disconnectOutsideP11Lock", run.disconnectUnlocked);
        out.put("nativeTimeoutTranslationKeyMatched", run.nativeReasonWasTimeout); out.put("originalTargetLogouts", run.targetLogouts);
        out.put("nativeDueTicks", run.nativeDueTicks); out.put("originalNativeTimeoutClock", run.timeoutNow);
        out.put("peerActorConnectionSourceBodyCanonicalObjectsExactAtLastTick", run.peerExactLastTick);
        out.put("packetInputPerturbation", run.mode != Mode.NATIVE_TIMEOUT);
        out.put("originalAckPredicateDeferral", run.mode == Mode.NATIVE_TIMEOUT);
        out.put("oldPhaseCapturedCallbackQualified", false); out.put("hostExemptionQualified", false);
        out.put("fullC4aAcceptance", false);
        return out;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final MinecraftServer server; final ServerPlayer target, peer;
        final Connection connection, peerConnection; final ServerGamePacketListenerImpl listener;
        final Object peerAdvancements, peerStatistics; final Mode mode; final Path output;
        final P11QualifiedSourceOwner peerSource; final Object peerBody;
        volatile String failure, nativePath = "NONE";
        volatile Frame rejected, afterReject, previousConsumed;
        volatile long lastChallengeStarted, lastChallengeSend, rejectedId, timeoutNow;
        volatile int challengeReturns, successfulAcks, rejectionAckReturns, disconnectCalls, disconnectReturns,
                targetLogouts, nativeDueTicks;
        volatile boolean disconnectUnlocked, nativeReasonWasTimeout;
        boolean peerExactLastTick;
        int ticks;
        Run(MinecraftServer server, ServerPlayer target, ServerPlayer peer, Mode mode, Path output) {
            this.server = server; this.target = target; this.peer = peer; this.mode = mode; this.output = output;
            connection = target.connection.getConnection(); peerConnection = peer.connection.getConnection();
            listener = target.connection; peerAdvancements = peer.getAdvancements(); peerStatistics = peer.getStats();
            peerSource = P11NativeStorageBoundary.nativeSourceOwner(peer);
            require(peerSource != null, "ACK_FIXTURE_PEER_SOURCE_MISSING");
            peerBody = peerSource.nativeRecipient(peer);
            require(peerBody != null, "ACK_FIXTURE_PEER_BODY_MISSING");
        }
    }
}
