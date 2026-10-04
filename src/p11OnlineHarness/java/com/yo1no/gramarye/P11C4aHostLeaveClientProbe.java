package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Exact owned game-window callbacks only; does not capture desktop, auth UI or native logs. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aHostLeaveClientProbe {
    private static volatile Run active;
    private P11C4aHostLeaveClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && P11C4aEvidence.property("case").equals("c4a-host-lan")
                && minecraft.isSameThread() && active == null && java.util.List.of("host", "b").contains(role)
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                && connection.isConnected() && minecraft.player != null && minecraft.level != null,
                "HOST_LEAVE_CLIENT_START");
        var run = new Run(connection, role, output, P11C4aEvidence.root().resolve("server"), minecraft.getSingleplayerServer());
        require(run.host == connection.isMemoryConnection() && (run.host ? run.retained != null : run.retained == null),
                "HOST_LEAVE_CLIENT_TOPOLOGY");
        active = run;
        P11C4aEvidence.cue(output, "host-leave-armed.ready");
    }

    /** Parent sets nativeCall=true around this entire call, including nested disconnect ticks. */
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null && ++run.ticks <= 2400,
                "HOST_LEAVE_CLIENT_OWNER_OR_DEADLINE");
        if (run.host && run.stage == 5) {
            require(run.intent && !run.connection.isConnected() && run.retained.isShutdown()
                    && minecraft.player == null && minecraft.level == null && minecraft.screen instanceof TitleScreen,
                    "HOST_LEAVE_POST_RETURN_CONTEXT");
            // Do not stop the host JVM (and its Netty worker sockets) until the peer has
            // independently completed native teardown. A process exit is not a halt proof.
            return P11C4aEvidence.receiptPresent(run.serverOutput.getParent().resolve("client-b"), "host-leave-terminal.json");
        }
        if (!run.host && terminalExpected()) {
            if (run.connection.isConnected() || minecraft.player != null || minecraft.level != null
                    || !(minecraft.screen instanceof DisconnectedScreen)) { return false; }
            // This may be the original native transport timeout after IntegratedServer.halt
            // removed the peer roster. A disconnect packet or a particular reason is not required.
            require(P11C4aEvidence.receiptPresent(run.serverOutput, "host-leave-stopped.json")
                    && run.peerHealthy && run.swings == 1, "HOST_LEAVE_PEER_TERMINAL_WITHOUT_SERVER_STOP");
            P11C4aEvidence.write(run.output, "host-leave-terminal.json", report(run, "ACTUAL_REMOTE_NATIVE_DISCONNECT_AFTER_HOST_STOP"));
            return true;
        }
        require(run.connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection, "HOST_LEAVE_CLIENT_CONNECTION");
        if (!run.host) { peer(minecraft, run); return false; }
        if (run.stage == 0) {
            if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN)) { run.stage++; }
            return false;
        }
        if (run.may == null) { return false; }
        require(run.wait != null && run.sends == 1 && run.frameBaseline == P11C4aNativeObservations.count(run.connection,
                P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN), "HOST_LEAVE_CLIENT_REFUSAL_COUNTS");
        if (run.stage == 1) {
            if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.STATUS_ESCAPE)) { run.stage++; }
        } else if (run.stage == 2) {
            require(minecraft.screen instanceof P11ClientLeaveScreen && !minecraft.screen.isPauseScreen(),
                    "HOST_LEAVE_CONFIRM_PAUSED");
            if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.LEAVE_STAY)) {
                require(minecraft.screen instanceof P11ClientTransitionScreen && !minecraft.screen.isPauseScreen(),
                        "HOST_LEAVE_STAY_NOT_STATUS");
                run.stage++; P11C4aEvidence.cue(run.output, "host-leave-stay.ready");
            }
        } else if (run.stage == 3) {
            require(minecraft.screen instanceof P11ClientTransitionScreen && !minecraft.screen.isPauseScreen()
                    && !minecraft.isPaused() && minecraft.getSingleplayerServer() == run.retained,
                    "HOST_LEAVE_STATUS_CONTEXT");
            if (!P11C4aEvidence.cuePresent(run.serverOutput, "host-leave-confirm.ready")) { return false; }
            P11C4aEvidence.write(run.output, "host-leave-healthy.json", report(run, "ACTUAL_HOST_STATUS_ESCAPE_STAY_NONPAUSING"));
            if (P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.STATUS_ESCAPE)) { run.stage++; }
        } else if (run.stage == 4) {
            if (!(minecraft.screen instanceof P11ClientLeaveScreen) || minecraft.getOverlay() != null) { return false; }
            require(!minecraft.screen.isPauseScreen(), "HOST_LEAVE_TERMINAL_PAUSED");
            // Publish the finite intent BEFORE entering the original MouseHandler callback.
            P11C4aEvidence.cue(run.serverOutput, "host-leave-terminal.ready");
            P11C4aHostLeaveProbe.beginTerminal(run.retained, run.connection);
            run.intent = true;
            require(P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.LEAVE_CONFIRM),
                    "HOST_LEAVE_ORIGINAL_CONFIRM_NOT_INVOKED");
            require(!run.connection.isConnected() && minecraft.getSingleplayerServer() == null
                    && minecraft.player == null && minecraft.level == null && minecraft.screen instanceof TitleScreen
                    && run.retained.isShutdown() && P11C4aHostLeaveProbe.serverStopped(),
                    "HOST_LEAVE_NATIVE_CLIENT_TERMINAL");
            var values = report(run, "ACTUAL_ORIGINAL_LEAVE_AND_RETAINED_INTEGRATED_SHUTDOWN");
            values.put("serverOriginalCause", P11C4aHostLeaveProbe.terminalReport());
            P11C4aEvidence.write(run.output, "host-leave-terminal.json", values);
            run.stage = 5;
            return false;
        }
        return false;
    }

    private static void peer(Minecraft minecraft, Run run) throws IOException {
        if (!P11C4aEvidence.cuePresent(run.serverOutput, "host-leave-observe.ready")) { return; }
        run.observing = true;
        if (run.swings == 0) {
            require(minecraft.player != null && minecraft.player.isAlive() && minecraft.level != null
                    && minecraft.screen == null, "HOST_LEAVE_PEER_ACTION_CONTEXT");
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            run.swings++;
        }
        if (!run.peerHealthy && run.receives > 0 && run.acks > 0 && run.ack == run.challenge
                && P11C4aEvidence.cuePresent(run.serverOutput, "host-leave-peer-sample.ready")) {
            run.peerHealthy = true;
            P11C4aEvidence.write(run.output, "host-leave-peer-healthy.json", report(run, "ACTUAL_REMOTE_CHALLENGE_ACK_AND_NATIVE_SWING"));
        }
    }

    static boolean terminalExpected() throws IOException {
        var run = active;
        return run != null && (run.intent || P11C4aEvidence.cuePresent(run.serverOutput, "host-leave-terminal.ready"));
    }
    static void release() { active = null; }

    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || !run.host || run.connection != connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state || state.scope() != Scope.PLAY || state.kind() != Kind.DEATH
                || state.outcome() != Outcome.NOT_STARTED) { return; }
        if (state.availability() == Availability.WAIT_NOTIFY && state.reason() == Reason.ACTIVE_OPERATION) {
            if (run.wait != null && !run.wait.equals(state)) { fail(run, "HOST_LEAVE_CLIENT_CHANGED_WAIT"); }
            else { run.wait = state; }
        } else if (state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE) {
            if (run.wait == null || run.wait.connectionEpoch() != state.connectionEpoch()
                    || run.wait.sceneSerial() != state.sceneSerial() || run.wait.actorGeneration() != state.actorGeneration()
                    || run.wait.requestSeq() != state.requestSeq()) { fail(run, "HOST_LEAVE_CLIENT_WRONG_MAY"); }
            else { run.may = state; }
        }
    }

    public static void submitted(Object value) {
        var run = active;
        if (run == null || !run.host || !(value instanceof Request request) || request.command() != Command.TRY) { return; }
        var state = P11ClientTransitions.view();
        if (state == null || request.scope() != Scope.PLAY || request.kind() != Kind.DEATH
                || request.sceneSerial() != state.sceneSerial() || request.connectionEpoch() != state.connectionEpoch()
                || request.actorGeneration() != state.actorGeneration()) { fail(run, "HOST_LEAVE_CLIENT_SEND_IDENTITY"); }
        run.sends++;
    }

    public static void received(ClientCommonPacketListenerImpl listener, ClientboundKeepAlivePacket packet) {
        var run = active;
        if (run == null || run.host || !run.observing || listener.getConnection() != run.connection || run.connection.getPacketListener() != listener) { return; }
        run.challenge = packet.getId(); run.receives++;
    }
    public static void sent(ClientCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run == null || run.host || listener.getConnection() != run.connection || !(packet instanceof ServerboundKeepAlivePacket ack)) { return; }
        if (ack.getId() == run.challenge) { run.ack = ack.getId(); run.acks++; }
    }
    public static void disconnectPacket(ClientCommonPacketListenerImpl listener, ClientboundDisconnectPacket ignored) {
        var run = active;
        if (run != null && listener.getConnection() == run.connection) { run.disconnectPackets++; }
    }
    private static LinkedHashMap<String, Object> report(Run run, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("role", run.role); values.put("wait", run.wait); values.put("may", run.may);
        values.put("originalTrySendReturns", run.sends); values.put("keepAliveReceives", run.receives);
        values.put("nativeAckSendReturns", run.acks); values.put("challenge", run.challenge); values.put("ack", run.ack);
        values.put("nativeSwingInvocations", run.swings); values.put("nativeDisconnectPacketsObserved", run.disconnectPackets);
        values.put("inputKind", "CONTROLLED_ORIGINAL_WINDOW_CALLBACK_OR_EXPLICIT_NATIVE_SWING");
        values.put("peerDisconnectPacketRequired", false); values.put("physicalInputClaimed", false);
        values.put("hostHasJoinedAuthenticationClaimed", false); values.put("fullC4aAcceptance", false);
        return values;
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output, serverOutput; final boolean host;
        final IntegratedServer retained; final long frameBaseline;
        volatile String failure; volatile State wait, may; volatile boolean intent, observing;
        volatile long receives, acks, challenge, ack, disconnectPackets;
        int ticks, stage, sends, swings; boolean peerHealthy;
        Run(Connection connection, String role, Path output, Path serverOutput, IntegratedServer retained) {
            this.connection = connection; this.role = role; this.output = output; this.serverOutput = serverOutput;
            this.host = role.equals("host"); this.retained = retained;
            frameBaseline = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        }
    }
}
