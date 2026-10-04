package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Excluded continuation/readout only. First TRY remains the existing original DeathScreen sender. */
@OnlyIn(Dist.CLIENT)
public final class P11C4aCompleteBFaultClientProbe {
    private static Run active;
    private P11C4aCompleteBFaultClientProbe() { }

    static void start(Minecraft minecraft, Connection connection, Path output, String role) {
        require(P11C4aCompleteBFaultProbe.selected() && active == null && minecraft.isSameThread()
                && (role.equals("a") || role.equals("b")) && connection.isConnected()
                && minecraft.player != null && minecraft.player.isAlive() && minecraft.level != null
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection,
                "FULL_B_CLIENT_ORIGINAL_PLAY");
        active = new Run(minecraft, connection, output, role);
    }

    /** True only after an independently recorded actual connection terminal, never a disconnect request. */
    static boolean tick(Minecraft minecraft, boolean faultClientProof) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && ++run.ticks <= 2400,
                "FULL_B_CLIENT_OWNER_OR_DEADLINE");
        if (run.terminal) { return true; }
        var server = run.output.resolveSibling("server");
        if (run.role.equals("b")) {
            if (!run.exitInvoked) {
                require(run.connection.isConnected() && minecraft.getConnection() == run.listener
                        && minecraft.player == run.player && minecraft.player.isAlive() && minecraft.level != null
                        && run.trySends == 0
                        && P11C4aNativeObservations.count(run.connection,
                                P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == run.beforeFrames,
                        "FULL_B_PEER_CLIENT_ORIGINAL_ACTOR_CHANGED");
                if (!run.peerBaseline && P11C4aEvidence.cuePresent(server, "complete-b-policy-returned.ready")) {
                    run.peerBaseline = true; run.peerTick = minecraft.player.tickCount;
                    run.peerTime = minecraft.level.getGameTime();
                }
                if (run.peerBaseline && !run.peerProved && minecraft.player.tickCount - run.peerTick >= 20
                        && minecraft.level.getGameTime() - run.peerTime >= 20) {
                    run.peerProved = true;
                    P11C4aEvidence.write(run.output, "complete-b-peer-continuation.json", report(run, minecraft,
                            "OTHER_AUTHENTICATED_PLAYER_SAME_NATIVE_CLIENT_ACTOR_AND_TICKS"));
                }
                if (P11C4aEvidence.cuePresent(server, "b-complete-b-exit.ready")) {
                    require(run.peerProved, "FULL_B_PEER_EXIT_BEFORE_PROOF");
                    run.exitInvoked = true;
                    // Existing parent's ordinary teardown API, after peer proofs and BEFORE A's later Leave.
                    minecraft.level.disconnect();
                    minecraft.disconnect(new TitleScreen());
                }
            }
        } else {
            if (!faultClientProof || !P11C4aEvidence.cuePresent(server, "a-complete-b-leave.ready")) { return false; }
            if (run.connection.isConnected()) {
                var state = P11ClientTransitions.view();
                require(P11C4aNativeErrorClientProbe.completeBFaultLeaveAllowed(run.connection, state),
                        "FULL_B_A_LEAVE_REQUIRES_SAME_OBSERVED_FAULT_OR_LATER_UNKNOWN");
                if (!run.escapeInvoked) {
                    run.escapeInvoked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.STATUS_ESCAPE);
                    if (run.escapeInvoked) { run.leaveState = state; }
                } else if (!run.exitInvoked) {
                    run.exitInvoked = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.LEAVE_CONFIRM);
                }
            }
        }
        if (run.connection.isConnected() || minecraft.player != null || minecraft.level != null) { return false; }
        require(minecraft.screen instanceof DisconnectedScreen || run.exitInvoked,
                "FULL_B_UNEXPLAINED_CLIENT_TERMINAL");
        require(run.trySends == (run.role.equals("a") ? 1 : 0)
                && (!run.role.equals("a") || P11C4aNativeErrorClientProbe.terminalCountersUnchanged()),
                "FULL_B_CLIENT_LATE_REPLAY_OR_FALSE_COMPLETION");
        run.terminal = true;
        P11C4aEvidence.write(run.output, "complete-b-client-terminal.json", report(run, minecraft,
                run.exitInvoked ? "POST_PROOF_ORIGINAL_UI_OR_PARENT_NORMAL_EXIT" : "ORIGINAL_NATIVE_CONNECTION_CLOSED"));
        return true;
    }

    static boolean exitInvoked() { return active != null && active.exitInvoked; }
    /** Reuses the existing actual production send TAIL observer on both real clients. */
    static void submitted(Object value) {
        var run = active;
        if (run != null && value instanceof Request request && request.command() == Command.TRY) { run.trySends++; }
    }
    static void release() { active = null; }
    private static java.util.Map<String,Object> report(Run run, Minecraft minecraft, String status) {
        var values = new LinkedHashMap<String,Object>();
        values.put("status", status); values.put("role", run.role);
        values.put("peerSameActorAndConnectionNativeTicksObserved", run.peerProved);
        values.put("nativeRespawnReturnsDelta", P11C4aNativeObservations.count(run.connection,
                P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) - run.beforeFrames);
        values.put("actualProductionTrySendReturns", run.trySends);
        values.put("actualConnectionClosed", !run.connection.isConnected());
        values.put("nativePlayerPresent", minecraft.player != null); values.put("nativeLevelPresent", minecraft.level != null);
        values.put("nativeDisconnectedScreen", minecraft.screen instanceof DisconnectedScreen);
        values.put("postProofOriginalLeaveOrParentExitInvoked", run.exitInvoked);
        if (run.role.equals("a")) { values.put("actualNonRetryableStateAtOriginalEscape", run.leaveState); }
        values.put("nativeErrorAutoDisconnectOrHaltInferred", false); values.put("fullC4aAcceptance", false);
        return values;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final LocalPlayer player; final ClientPacketListener listener;
        final Path output; final String role; final long beforeFrames;
        int ticks, peerTick, trySends; long peerTime; State leaveState;
        boolean peerBaseline, peerProved, escapeInvoked, exitInvoked, terminal;
        Run(Minecraft minecraft, Connection connection, Path output, String role) {
            this.connection=connection; this.output=output; this.role=role;
            player=minecraft.player; listener=minecraft.getConnection();
            beforeFrames=P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        }
    }
}
