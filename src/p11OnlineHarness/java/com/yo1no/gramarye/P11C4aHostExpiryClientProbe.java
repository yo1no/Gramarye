package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** No retry/leave invocation: real actorless CONFIG waits until the server's original expiry. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aHostExpiryClientProbe {
    private static volatile Run active;
    private P11C4aHostExpiryClientProbe() { }
    static void start(Minecraft minecraft, Connection connection, String role, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_EXPIRY
                && P11C4aEvidence.property("case").equals("c4a-host-lan") && minecraft.isSameThread() && active == null
                && java.util.List.of("host", "b").contains(role) && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                && connection.isConnected(), "HOST_EXPIRY_CLIENT_START");
        var run = new Run(connection, role, output, minecraft.getSingleplayerServer(), minecraft.player);
        require(run.host == connection.isMemoryConnection() && (run.host ? run.retained != null : run.retained == null),
                "HOST_EXPIRY_CLIENT_TOPOLOGY");
        active = run;
        P11C4aEvidence.cue(output, "host-expiry-armed.ready");
    }
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null && ++run.ticks <= 2400,
                "HOST_EXPIRY_CLIENT_OWNER_OR_DEADLINE");
        var serverOutput = run.output.getParent().resolve("server");
        if (run.terminalWritten) {
            return !run.host || P11C4aEvidence.receiptPresent(run.output.getParent().resolve("client-b"), "host-expiry-terminal.json");
        }
        if (!run.connection.isConnected()) {
            // Native disconnect can run nested ticks before actual player/level/screen teardown.
            if (minecraft.player != null || minecraft.level != null || minecraft.getSingleplayerServer() != null
                    || !(minecraft.screen instanceof DisconnectedScreen)
                    || !P11C4aEvidence.receiptPresent(serverOutput, "host-expiry-stopped.json")) { return false; }
            if (run.host) {
                require(run.enter != null && run.wait != null && run.may != null && run.mayTicks > 0 && run.tries == 0
                        && run.retained.isShutdown() && P11C4aHostExpiryProbe.nativeStopped() && run.disconnectPackets == 1,
                        "HOST_EXPIRY_HOST_NATIVE_TERMINAL_MISSING");
            } else {
                require(run.swings == 1 && run.receives > 0 && run.acks > 0,
                        "HOST_EXPIRY_PEER_LIVE_PROOF_MISSING");
            }
            var out = report(run, "ACTUAL_NATIVE_EXPIRY_DEPARTURE_K_AND_DATA_REQUIRE_SERVER_RECEIPT");
            if (run.host) { out.put("serverAllComponentsComplete", P11C4aHostExpiryProbe.complete()); }
            P11C4aEvidence.write(run.output, "host-expiry-terminal.json", out);
            run.terminalWritten = true;
            return !run.host; // Keep host JVM alive until peer independently reaches native DisconnectedScreen.
        }
        if (!run.host) {
            if (run.swings == 0) {
                require(minecraft.player == run.actor && run.actor.isAlive() && minecraft.level == run.actor.level()
                        && minecraft.getConnection() == run.actor.connection
                        && run.connection.getPacketListener() == run.actor.connection,
                        "HOST_EXPIRY_PEER_ACTION_CONTEXT");
                run.peerLoginReturns = P11C4aNativeObservations.count(run.connection,
                        P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN);
                require(run.peerLoginReturns <= 1, "HOST_EXPIRY_PEER_UNEXPECTED_LOGIN");
                // LoggingIn occurs before handleLogin returns and before the native receiving-level screen closes.
                // Wait for ordinary gameplay readiness; never close a screen or dismiss an overlay for the fixture.
                if (run.peerLoginReturns == 0 || minecraft.screen != null || minecraft.getOverlay() != null) {
                    run.peerReadyWaitTicks++;
                    return false;
                }
                minecraft.player.swing(InteractionHand.MAIN_HAND); run.swings++;
            }
            return false;
        }
        if (run.enter != null && !run.configCue) {
            if (!(run.connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl)
                    || minecraft.player != null || minecraft.level != null) { return false; }
            require(P11C4aNativeObservations.count(run.connection,
                    P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN) == run.starts + 1,
                    "HOST_EXPIRY_NO_ACTUAL_START_CONFIGURATION");
            run.configCue = true;
            P11C4aEvidence.cue(run.output, "host-expiry-config-terminal.ready");
        }
        if (run.may != null && run.expired == null) {
            require(run.connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                    && minecraft.player == null && minecraft.level == null && run.tries == 0
                    && minecraft.screen instanceof P11ClientTransitionScreen && !minecraft.screen.isPauseScreen()
                    && !minecraft.isPaused(), "HOST_EXPIRY_MAY_NOT_ACTORLESS_NONPAUSING");
            run.mayTicks++;
        }
        return false;
    }
    static void release() { active = null; }
    public static void accepted(Object value, Connection connection) {
        var run = active;
        if (run == null || !run.host || connection != run.connection || !(value instanceof State state)
                || P11ClientTransitions.view() != state || state.scope() != Scope.CONFIG) { return; }
        if (state.kind() == Kind.ENTER_CONFIG && state.outcome() == Outcome.COMPLETED) {
            if (state.actorGeneration() != 0 || state.requestSeq() != 0) { fail(run, "HOST_EXPIRY_CLIENT_ENTER_NOT_ACTORLESS_ZERO"); return; }
            if (run.enter != null && !run.enter.equals(state)) { fail(run, "HOST_EXPIRY_CLIENT_CHANGED_ENTER"); } else { run.enter = state; }
        }
        if (state.kind() != Kind.RETURN_TO_WORLD) { return; }
        if (run.enter == null || state.sceneSerial() <= run.enter.sceneSerial() || state.actorGeneration() != 0
                || state.requestSeq() != 0) { fail(run, "HOST_EXPIRY_CLIENT_RETURN_NOT_FRESH_SERVER_SCENE"); return; }
        if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.WAIT_NOTIFY && state.reason() == Reason.ACTIVE_OPERATION) {
            if (run.wait != null && !run.wait.equals(state)) { fail(run, "HOST_EXPIRY_CLIENT_CHANGED_WAIT"); } else { run.wait = state; }
        } else if (state.outcome() == Outcome.NOT_STARTED && state.availability() == Availability.MAY_TRY && state.reason() == Reason.NONE) {
            if (run.wait == null || !same(run.wait, state)) { fail(run, "HOST_EXPIRY_CLIENT_MAY_IDENTITY"); } else { run.may = state; }
        } else if (state.outcome() == Outcome.EXPIRED) {
            if (run.may == null || !same(run.may, state) || state.reason() != Reason.WAIT_EXPIRED
                    || state.availability() != Availability.DISABLED || run.mayTicks == 0) { fail(run, "HOST_EXPIRY_CLIENT_EXPIRY_IDENTITY"); }
            else { run.expired = state; }
        }
    }
    public static void submitted(Object value) {
        var run = active;
        if (run != null && run.host && value instanceof Request request && request.command() == Command.TRY) { run.tries++; }
    }
    public static void received(ClientCommonPacketListenerImpl listener, ClientboundKeepAlivePacket packet) {
        var run = active;
        if (run != null && !run.host && listener.getConnection() == run.connection) { run.challenge = packet.getId(); run.receives++; }
    }
    public static void sent(ClientCommonPacketListenerImpl listener, Packet<?> packet) {
        var run = active;
        if (run != null && !run.host && listener.getConnection() == run.connection
                && packet instanceof ServerboundKeepAlivePacket ack && ack.getId() == run.challenge) { run.acks++; }
    }
    public static void disconnectPacket(ClientCommonPacketListenerImpl listener, ClientboundDisconnectPacket ignored) {
        var run = active; if (run != null && listener.getConnection() == run.connection) { run.disconnectPackets++; }
    }
    private static boolean same(State a, State b) { return a.connectionEpoch() == b.connectionEpoch() && a.sceneSerial() == b.sceneSerial()
            && a.scope() == b.scope() && a.kind() == b.kind() && a.actorGeneration() == b.actorGeneration() && a.requestSeq() == b.requestSeq(); }
    private static LinkedHashMap<String, Object> report(Run run, String status) {
        var out = new LinkedHashMap<String, Object>(); out.put("status", status); out.put("role", run.role);
        out.put("enter", run.enter); out.put("wait", run.wait); out.put("may", run.may); out.put("expired", run.expired);
        out.put("expiredHandlerObserved", run.expired != null); out.put("expiredHandlerRequiredForTerminal", false);
        out.put("serverExpiryReceiptRequired", true);
        out.put("mayActorlessNonpausedTicks", run.mayTicks); out.put("nativeTrySendReturns", run.tries);
        out.put("nativeKeepAliveReceives", run.receives); out.put("nativeAckSendReturns", run.acks);
        out.put("nativeSwingInvocations", run.swings); out.put("nativeDisconnectPacketReturns", run.disconnectPackets);
        out.put("peerReadyWaitTicks", run.peerReadyWaitTicks); out.put("peerLoginReturnsBeforeSwing", run.peerLoginReturns);
        out.put("nativeLeaveInvoked", false); out.put("peerDisconnectPacketRequired", false);
        out.put("clockOrDeadlineChanged", false); out.put("fullC4aAcceptance", false); return out;
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output; final boolean host; final IntegratedServer retained; final long starts;
        final LocalPlayer actor;
        volatile String failure; volatile State enter, wait, may, expired; volatile long challenge, receives, acks, disconnectPackets;
        int ticks, tries, swings, mayTicks, peerReadyWaitTicks; long peerLoginReturns; boolean configCue, terminalWritten;
        Run(Connection connection, String role, Path output, IntegratedServer retained, LocalPlayer actor) {
            this.connection = connection; this.role = role; this.output = output; this.retained = retained; this.actor = actor; host = role.equals("host");
            starts = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN);
        }
    }
}
