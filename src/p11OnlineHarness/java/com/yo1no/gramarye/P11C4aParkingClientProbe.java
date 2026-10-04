package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

/** Client-only staged probe: original packet handlers and fresh native-window Retry callback. */
public final class P11C4aParkingClientProbe {
    private static volatile Run active;
    private P11C4aParkingClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        require(P11C4aEvidence.enabled() && minecraft.isSameThread() && active == null
                && minecraft.player != null && minecraft.player.connection.getConnection() == connection
                && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection(),
                "PARK_CLIENT_REAL_REMOTE_PLAY");
        active = new Run(connection, role, output, serverOutput);
        P11C4aEvidence.write(output, "parking-armed.json", Map.of("status", "CLIENT_OBSERVERS_ARMED_NOT_ACCEPTANCE"));
    }

    static void startPendingTransfer(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        start(minecraft, connection, role, output, serverOutput);
        active.pendingTransfer = true;
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400
                && run.connection.isConnected() && run.failure == null, "PARK_CLIENT_PHASE_OWNER_OR_DEADLINE");
        if (run.enterCompleted != null && !run.configReported) {
            require(run.connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                    && minecraft.player == null && minecraft.level == null
                    && frames(run) == run.beforeLogins
                    && P11C4aNativeObservations.count(run.connection,
                            P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN) == run.beforeStarts + 1,
                    "PARK_CLIENT_INDEPENDENT_CONFIG_TERMINAL");
            run.configReported = true;
            P11C4aEvidence.write(run.output, "parking-config-terminal.json", Map.of(
                    "status", "ACTUAL_CONFIG_ENTER_COMPLETED_BEFORE_RETURN", "state", run.enterCompleted,
                    "nativeStartDelta", 1, "playerPresent", false, "levelPresent", false));
        }
        if (run.mayState != null && !run.retryClicked) {
            require(run.waitState != null && run.connection.getPacketListener() instanceof ClientPacketListener
                    && minecraft.player == null && minecraft.level == null && frames(run) == run.beforeLogins,
                    "PARK_CLIENT_NOT_REAL_PRELOGIN_PLAY");
            if (run.pendingTransfer && run.deferredAcks == 1 && run.keepAliveReceives > 0 && !run.challengeReported) {
                require(run.keepAliveSends == 0, "TRANSFER_DEFERRED_ACK_SENT_BEFORE_TRY");
                P11C4aEvidence.write(run.output, "parking-transfer-challenge-ready.json", Map.of(
                        "status", "ORIGINAL_NATIVE_ACK_DEFERRED_AFTER_ACTUAL_MAY_NOT_ACCEPTANCE",
                        "state", run.mayState, "challenge", run.receivedChallenge));
                run.challengeReported = true;
            }
            if (!P11C4aEvidence.cuePresent(run.serverOutput, run.role + "-parking-retry.ready")) { return false; }
            // Filesystem cue can beat actual challenge delivery; it is not packet receipt.
            if (run.pendingTransfer && (run.keepAliveReceives == 0 || run.deferredAcks == 0)) { return false; }
            require(run.keepAliveReceives > 0 && (run.pendingTransfer
                    ? run.deferredAcks == 1 && run.keepAliveSends == 0
                    : run.keepAliveSends > 0 && run.receivedChallenge == run.sentChallenge),
                    "PARK_CLIENT_REAL_KEEPALIVE_HANDLERS_MISSING");
            var view = P11ClientTransitions.view();
            require(view == run.mayState && view.scope() == Scope.PREPLAY && view.actorGeneration() == 0,
                    "PARK_CLIENT_MAY_NOT_CURRENT");
            if (run.ticks <= run.mayAtTick + 1) { return false; }
            var input = P11C4aClientInputProbe.snapshot(minecraft);
            if (!input.glfwActivationNeutral() || !P11ClientTransitions.retryArmed()) { return false; }
            run.neutral = input;
            run.retryClicked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.RETRY);
            if (run.retryClicked) {
                require(run.submits == 1 && run.retry != null && run.retry.scope() == Scope.PREPLAY
                        && run.retry.requestSeq() > run.mayState.requestSeq()
                        && run.retry.connectionEpoch() == run.mayState.connectionEpoch()
                        && run.retry.sceneSerial() == run.mayState.sceneSerial()
                        && run.retry.actorGeneration() == 0, "PARK_NATIVE_RETRY_DID_NOT_SEND_FRESH_TRY");
            }
            return false;
        }
        if (run.completed == null) { return false; }
        if (run.pendingTransfer) {
            require(run.deferredAcks == 1 && run.keepAliveSends == 1 && !run.deferTimedOut
                    && run.receivedChallenge == run.sentChallenge, "TRANSFER_CLIENT_ORIGINAL_ACK_MISSING");
        }
        require(run.configReported && run.retryClicked && run.submits == 1 && run.retry != null
                && run.completed.requestSeq() == run.retry.requestSeq() && frames(run) == run.beforeLogins + 1
                && minecraft.player != null && minecraft.level != null
                && minecraft.player.connection.getConnection() == run.connection
                && P11ClientTransitions.view() == run.completed, "PARK_CLIENT_EXACT_NATIVE_LOGIN_COMPLETION");
        var values = new LinkedHashMap<String, Object>();
        values.put("status", "ACTUAL_PRELOGIN_PARKED_KEEPALIVE_FRESH_RETRY_NATIVE_LOGIN");
        values.put("enterConfig", run.enterCompleted); values.put("wait", run.waitState); values.put("may", run.mayState);
        values.put("completed", run.completed); values.put("nativeLoginDelta", 1); values.put("originalTrySendReturns", run.submits);
        values.put("keepAliveHandlerReturns", run.keepAliveReceives); values.put("keepAliveSendReturns", run.keepAliveSends);
        values.put("pendingTransferVariant", run.pendingTransfer);
        values.put("deferredOriginalAcks", run.deferredAcks); values.put("deferTimedOut", run.deferTimedOut);
        values.put("matchingKeepAliveScalar", run.receivedChallenge == run.sentChallenge);
        values.put("beforeRetryInput", run.neutral); values.put("ticks", run.ticks);
        values.put("inputKind", "ORIGINAL_WINDOW_CALLBACK_AFTER_OBSERVED_GLFW_NEUTRAL");
        values.put("physicalHeldKeyClaimed", false); values.put("visualAppearanceClaimed", false);
        values.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "parking.json", values);
        active = null;
        return true;
    }

    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || connection != run.connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state) { return; }
        if (state.kind() == Kind.ENTER_CONFIG && state.scope() == Scope.CONFIG && state.outcome() == Outcome.COMPLETED) {
            run.enterCompleted = state; return;
        }
        if (state.kind() != Kind.RETURN_TO_WORLD) { return; }
        try {
            if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY) {
                require(run.configReported && state.scope() == Scope.PREPLAY && state.actorGeneration() == 0
                        && state.requestSeq() == 0 && state.reason() == Reason.ACTIVE_OPERATION
                        && run.submits == 0 && frames(run) == run.beforeLogins, "PARK_CLIENT_FINAL_REFUSAL_MISMATCH");
                run.waitState = state;
            } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY) {
                require(same(run.waitState, state), "PARK_CLIENT_MAY_WITHOUT_EXACT_WAIT");
                if (run.mayState == null) { run.mayAtTick = run.ticks; }
                run.mayState = state;
            } else if (state.outcome() == Outcome.COMPLETED) { run.completed = state; }
        } catch (RuntimeException | Error failure) { run.failure = "PARK_CLIENT_STATE_OBSERVER"; }
    }

    public static void submitted(Object value) {
        var run = active;
        if (run == null || !(value instanceof Request request) || request.command() != Command.TRY
                || request.kind() != Kind.RETURN_TO_WORLD) { return; }
        run.submits++; run.retry = request;
        if (run.submits != 1 || run.mayState == null) { run.failure = "PARK_CLIENT_UNEXPECTED_TRY"; }
    }

    /** Called after original native handlers/sends; no client polling or clock is substituted. */
    public static void keepAlive(Connection connection, Packet<?> packet, boolean sending) {
        var run = active;
        if (run == null || connection != run.connection || run.mayState == null || run.retryClicked && !run.pendingTransfer) { return; }
        if (run.pendingTransfer && (run.deferredAcks != 1
                || sending && (!(packet instanceof ServerboundKeepAlivePacket ack) || ack.getId() != run.deferredChallenge)
                || !sending && (!(packet instanceof ClientboundKeepAlivePacket challenge) || challenge.getId() != run.deferredChallenge))) { return; }
        if (sending && packet instanceof ServerboundKeepAlivePacket ack) { run.sentChallenge = ack.getId(); run.keepAliveSends++; }
        else if (!sending && packet instanceof ClientboundKeepAlivePacket challenge) { run.receivedChallenge = challenge.getId(); run.keepAliveReceives++; }
    }

    /** Gate only the original native sendWhen predicate; packet, queue and expiry stay native. */
    public static BooleanSupplier deferAck(Connection connection, Packet<?> packet, BooleanSupplier original) {
        var run = active;
        if (run == null || !run.pendingTransfer || connection != run.connection || run.mayState == null
                || !(packet instanceof ServerboundKeepAlivePacket ack) || run.submits != 0) { return original; }
        require(run.connection.getPacketListener() instanceof ClientPacketListener && run.deferredAcks == 0,
                "TRANSFER_CLIENT_DEFER_SCOPE");
        run.deferredChallenge = ack.getId();
        run.deferredAcks = 1;
        final long started = System.nanoTime();
        return () -> {
            boolean nativeReady = original.getAsBoolean();
            if (active != run) { return nativeReady; }
            boolean expired = System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(5);
            if (expired && run.submits == 0) {
                run.deferTimedOut = true; run.failure = "TRANSFER_CLIENT_TRY_BEFORE_ACK_DEADLINE";
            }
            return nativeReady && (run.submits == 1 || expired);
        };
    }

    static void release() { active = null; }
    private static long frames(Run run) { return P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN); }
    private static boolean same(State first, State second) { return first != null && first.connectionEpoch() == second.connectionEpoch()
            && first.sceneSerial() == second.sceneSerial() && first.actorGeneration() == second.actorGeneration() && first.requestSeq() == second.requestSeq(); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output, serverOutput; final long beforeLogins, beforeStarts;
        volatile String failure; volatile int keepAliveReceives, keepAliveSends; volatile long receivedChallenge, sentChallenge;
        volatile State mayState;
        State enterCompleted, waitState, completed;
        Request retry;
        int ticks, mayAtTick;
        volatile int submits, deferredAcks;
        volatile long deferredChallenge;
        volatile boolean pendingTransfer, deferTimedOut;
        boolean configReported, challengeReported;
        volatile boolean retryClicked;
        P11C4aClientInputProbe.Snapshot neutral;
        Run(Connection connection, String role, Path output, Path serverOutput) {
            this.connection = connection; this.role = role; this.output = output; this.serverOutput = serverOutput;
            beforeLogins = frames(this);
            beforeStarts = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN);
        }
    }
}
