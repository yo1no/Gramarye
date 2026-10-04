package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;

/** Client-only ordinary CONFIG reset observation; no old listener/handler is replayed. */
public final class P11C4aConfigResetClientProbe {
    private static volatile Run active;
    private P11C4aConfigResetClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && minecraft.isSameThread() && active == null
                && minecraft.player != null && minecraft.player.connection.getConnection() == connection
                && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection(),
                "RESET_CLIENT_REMOTE_PLAY_REQUIRED");
        active = new Run(connection, output);
        P11C4aEvidence.write(output, "config-reset-armed.json", Map.of("status", "CLIENT_ARMED_NOT_ACCEPTANCE"));
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400
                && run.connection.isConnected() && run.failure == null, "RESET_CLIENT_OWNER_OR_DEADLINE");
        var state = P11ClientTransitions.view();
        if (!run.configReported && state != null && state.kind() == Kind.ENTER_CONFIG
                && state.scope() == Scope.CONFIG && state.outcome() == Outcome.COMPLETED) {
            require(state.actorGeneration() == 0 && state.requestSeq() == 0
                    && run.connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                    && minecraft.player == null && minecraft.level == null
                    && frames(run) == run.beforeLogins
                    && count(run, P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN) == run.beforeStarts + 1,
                    "RESET_CLIENT_CONFIG_TERMINAL_NOT_NATIVE");
            run.enterCompleted = state;
            run.configReported = true;
            P11C4aEvidence.write(run.output, "config-reset-config-terminal.json", Map.of(
                    "status", "ACTUAL_CONFIG_ENTER_COMPLETED_NOT_ACCEPTANCE", "state", state,
                    "playerPresent", false, "levelPresent", false, "nativeStartDelta", 1));
        }
        if (!run.deferredReported && run.deferredStores == 1 && run.configHandlerReturns == 1) {
            require(run.configReported && run.connection.getPacketListener() == run.configuration
                    && run.oldSendAttempts == 0 && run.oldSendReturns == 0
                    && run.selectedAck != null && !run.expired, "RESET_CLIENT_DEFERRED_ACK_NOT_EXACT");
            run.deferredReported = true;
            P11C4aEvidence.write(run.output, "config-reset-deferred.json", Map.of(
                    "status", "ACTUAL_ORIGINAL_CONFIG_ACK_IN_NATIVE_DEFERRED_QUEUE_NOT_ACCEPTANCE",
                    "challenge", run.oldChallenge, "nativeQueueAdds", run.deferredStores,
                    "nativeHandlerReturns", run.configHandlerReturns, "originalAckSendAttempts", 0));
        }
        if (state == null || state.kind() != Kind.RETURN_TO_WORLD || state.outcome() != Outcome.COMPLETED) { return false; }
        if (run.freshAckReturns == 0 || run.freshHandlerReturns == 0) { return false; }
        require(run.deferredReported && run.finishReturns == 1 && run.oldSendAttempts == 0 && run.oldSendReturns == 0
                && !run.expired && run.failure == null && run.freshAckReturns == 1 && run.freshHandlerReturns == 1
                && run.freshChallenge != run.oldChallenge && run.enterCompleted != null
                && state.connectionEpoch() == run.enterCompleted.connectionEpoch()
                && state.sceneSerial() > run.enterCompleted.sceneSerial()
                && (run.parkingMode ? state.requestSeq() > 0 : state.requestSeq() == 0)
                && run.connection.getPacketListener() == run.game && minecraft.player != null && minecraft.level != null
                && minecraft.player.connection == run.game && frames(run) == run.beforeLogins + 1,
                "RESET_CLIENT_FINAL_NATIVE_LOGIN_OR_ACK_MISSING");
        var out = new LinkedHashMap<String, Object>();
        out.put("status", run.parkingMode ? "ACTUAL_CONFIG_ACK_RETIRED_AND_FRESH_PARKING_ACK_THEN_LOGIN"
                : "ACTUAL_CONFIG_DEFERRED_ACK_RETIRED_AND_FRESH_GAME_ACK");
        out.put("enterConfig", run.enterCompleted); out.put("returnCompleted", state);
        out.put("oldConfigChallenge", run.oldChallenge); out.put("nativeDeferredQueueAdds", run.deferredStores);
        out.put("configHandlerReturns", run.configHandlerReturns); out.put("originalConfigFinishReturns", run.finishReturns);
        out.put("oldAckSendAttempts", run.oldSendAttempts); out.put("oldAckSendReturns", run.oldSendReturns);
        out.put("freshGameChallenge", run.freshChallenge); out.put("freshGameHandlerReturns", run.freshHandlerReturns);
        out.put("freshGameAckSendReturns", run.freshAckReturns); out.put("deferTimedOut", run.expired);
        out.put("nativeLoginDelta", 1); out.put("ticks", run.ticks); out.put("configToParkingQualified", run.parkingMode);
        out.put("capturedOldPhaseAckExecuted", false); out.put("fullC4aAcceptance", false);
        P11C4aEvidence.write(run.output, "config-reset.json", out);
        active = null;
        return true;
    }

    static void startParking(Minecraft minecraft, Connection connection, Path output) throws IOException {
        start(minecraft, connection, output); active.parkingMode = true;
    }
    static boolean pendingParkingAck() {
        var run = active;
        return run != null && run.parkingMode && run.deferredReported && !run.expired
                && run.deferredStores == 1 && run.configHandlerReturns == 1
                && run.oldSendAttempts == 0 && run.oldSendReturns == 0
                && run.connection.getPacketListener() == run.configuration;
    }

    /** Wraps only the original native queue predicate; original packet and one-minute duration are unchanged. */
    public static BooleanSupplier defer(ClientCommonPacketListenerImpl listener, Packet<?> packet, BooleanSupplier original) {
        var run = active;
        if (run == null || !run.configReported || listener.getConnection() != run.connection
                || !(listener instanceof ClientConfigurationPacketListenerImpl configuration)
                || !(packet instanceof ServerboundKeepAlivePacket ack)) { return original; }
        require(run.connection.getPacketListener() == listener && run.selectedAck == null,
                "RESET_CLIENT_DUPLICATE_OR_STALE_CONFIG_ACK");
        run.configuration = configuration;
        run.oldChallenge = ack.getId();
        run.selectedAck = packet;
        final long started = System.nanoTime();
        return () -> {
            boolean nativeReady = original.getAsBoolean();
            if (active != run || run.connection.getPacketListener() != configuration) { return nativeReady; }
            boolean expired = System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(5);
            if (expired) { run.expired = true; run.failure = "RESET_CLIENT_RETURN_DID_NOT_FINISH_BEFORE_DEFER_BOUND"; }
            return nativeReady && expired;
        };
    }

    /** Unique original List.add in private sendWhen returned true, with its actual enclosing packet argument. */
    public static void queued(ClientCommonPacketListenerImpl listener, Packet<?> packet, boolean added) {
        var run = active;
        if (run == null || listener != run.configuration || packet != run.selectedAck) { return; }
        require(added && ++run.deferredStores == 1 && run.connection.getPacketListener() == listener,
                "RESET_CLIENT_ORIGINAL_QUEUE_ADD_MISSING");
    }

    public static void received(ClientCommonPacketListenerImpl listener, ClientboundKeepAlivePacket packet) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection) { return; }
        if (listener == run.configuration && run.selectedAck != null && packet.getId() == run.oldChallenge) {
            require(run.deferredStores == 1 && ++run.configHandlerReturns == 1, "RESET_CLIENT_CONFIG_HANDLER_WITHOUT_NATIVE_QUEUE");
        } else if (listener == run.game && run.finishReturns == 1) {
            require(packet.getId() != run.oldChallenge && ++run.freshHandlerReturns == 1,
                    "RESET_CLIENT_FRESH_CHALLENGE_MISMATCH");
            run.freshChallenge = packet.getId();
        }
    }

    public static void sending(ClientCommonPacketListenerImpl listener, Packet<?> packet, boolean normalReturn) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection || !(packet instanceof ServerboundKeepAlivePacket ack)) { return; }
        if (run.selectedAck != null && ack.getId() == run.oldChallenge) {
            if (normalReturn) { run.oldSendReturns++; } else { run.oldSendAttempts++; }
            run.failure = "RESET_CLIENT_OLD_CONFIG_ACK_WAS_SUBMITTED";
        } else if (normalReturn && listener == run.game && run.finishReturns == 1) {
            // send() returns inside handleKeepAlive, before its RETURN observer records freshChallenge.
            if (run.freshChallenge != 0 && run.freshChallenge != ack.getId()) { run.failure = "RESET_CLIENT_FRESH_ACK_ID_MISMATCH"; }
            run.freshChallenge = ack.getId();
            run.freshAckReturns++;
        }
    }

    public static boolean beforeFinish(ClientConfigurationPacketListenerImpl listener) {
        var run = active;
        if (run == null || listener.getConnection() != run.connection || !Minecraft.getInstance().isSameThread()) { return false; }
        require(listener == run.configuration && run.deferredReported && run.deferredStores == 1
                && run.oldSendAttempts == 0 && run.oldSendReturns == 0 && run.connection.getPacketListener() == listener,
                "RESET_CLIENT_FINISH_WITHOUT_ORIGINAL_PENDING_ACK");
        return true;
    }

    public static void afterFinish(ClientConfigurationPacketListenerImpl listener, boolean observed, boolean normal) {
        var run = active;
        if (!observed || run == null || listener.getConnection() != run.connection) { return; }
        if (!normal) { run.failure = "RESET_CLIENT_ORIGINAL_FINISH_THROW"; return; }
        require(Minecraft.getInstance().isSameThread() && run.connection.getPacketListener() instanceof ClientPacketListener
                && run.connection.getPacketListener() != listener && run.oldSendAttempts == 0 && run.oldSendReturns == 0,
                "RESET_CLIENT_NATIVE_LISTENER_NOT_REPLACED");
        run.game = (ClientPacketListener) run.connection.getPacketListener();
        require(++run.finishReturns == 1, "RESET_CLIENT_DUPLICATE_FINISH");
    }

    static void release() { active = null; }
    private static long count(Run run, P11C4aNativeObservations.Event event) { return P11C4aNativeObservations.count(run.connection, event); }
    private static long frames(Run run) { return count(run, P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }

    private static final class Run {
        final Connection connection; final Path output; final long beforeLogins, beforeStarts;
        volatile String failure;
        volatile ClientConfigurationPacketListenerImpl configuration;
        volatile ClientPacketListener game;
        volatile Packet<?> selectedAck;
        volatile long oldChallenge, freshChallenge;
        volatile boolean configReported, expired;
        boolean deferredReported, parkingMode;
        State enterCompleted;
        int ticks;
        volatile int deferredStores, configHandlerReturns, oldSendAttempts, oldSendReturns,
                finishReturns, freshHandlerReturns, freshAckReturns;
        Run(Connection connection, Path output) {
            this.connection = connection; this.output = output;
            beforeLogins = frames(this); beforeStarts = count(this, P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN);
        }
    }
}
