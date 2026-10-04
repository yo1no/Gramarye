package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;

/** Captures only the actual installed target CONFIG/C, including pre-PLAY initial JOIN. */
public final class P11C4aConfigDepartureClientProbe {
    private static volatile Run active;
    private P11C4aConfigDepartureClientProbe() { }

    static void arm(Minecraft minecraft, String role, Path output) {
        require(P11C4aEvidence.enabled() && P11C4aConfigDepartureProbe.selectedMode() != null
                && role.equals("b") && minecraft.isSameThread() && active == null
                && minecraft.player == null && minecraft.getConnection() == null,
                "DEPARTURE_CLIENT_FRESH_NATIVE_CONNECT");
        active = new Run(output, P11C4aConfigDepartureProbe.selectedMode());
    }

    /** Original setupInboundProtocol RETURN only; no constructor guess or pending-connection seed. */
    public static void installed(Connection connection, PacketListener listener) {
        var run = active;
        if (run == null || !(listener instanceof ClientConfigurationPacketListenerImpl config)) { return; }
        if (connection.getPacketListener() != listener || config.getConnection() != connection
                || !connection.isEncrypted() || connection.isMemoryConnection()) {
            run.failure = "DEPARTURE_CLIENT_CONFIGURATION_IDENTITY"; return;
        }
        if (run.connection != null && run.connection != connection) { run.failure = "DEPARTURE_CLIENT_CONNECTION_REPLACED"; return; }
        run.connection = connection; run.configurations++;
    }

    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null && ++run.ticks <= 1200,
                "DEPARTURE_CLIENT_DEADLINE_OR_OWNER");
        if (run.connection == null) { return false; }
        long logins = P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN);
        if (run.mode == P11C4aConfigDepartureProbe.Mode.ENTER_CONFIG_ACK && !run.playReady
                && logins == 1 && minecraft.player != null && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == run.connection && run.connection.isConnected()) {
            run.playReady = true;
            P11C4aEvidence.write(run.output, "config-departure-play.json", Map.of(
                    "status", "ACTUAL_INITIAL_PLAY_BEFORE_ACK_CLOSE", "originalClientLoginReturns", logins));
        }
        if (run.connection.isConnected() || minecraft.player != null || minecraft.level != null
                || !(minecraft.screen instanceof DisconnectedScreen)) { return false; }
        require(run.configurations == (run.mode == P11C4aConfigDepartureProbe.Mode.INITIAL_JOIN ? 1 : 2)
                && logins == (run.mode == P11C4aConfigDepartureProbe.Mode.INITIAL_JOIN ? 0 : 1)
                && P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == 0,
                "DEPARTURE_CLIENT_WRONG_NATIVE_FRAME_COUNTS");
        // A closed client is only one observation. Server's real bind-empty/retire receipt
        // independently proves which exact intervention occurred; no setting is auth proof.
        P11C4aEvidence.write(run.output, "config-departure-terminal.json", Map.of(
                "status", "ACTUAL_CURRENT_CONFIG_TRANSPORT_CLOSED_NO_NEW_LOGIN_OR_RESPAWN",
                "mode", run.mode.name(), "originalConfigInstallReturns", run.configurations,
                "originalClientLoginReturns", logins, "actualDisconnectedScreen", true,
                "actualNullPlayerAndLevel", true, "serverInterleaveProofStillRequired", true,
                "initialTargetPlayClaimed", run.mode != P11C4aConfigDepartureProbe.Mode.INITIAL_JOIN,
                "fullC4aAcceptance", false));
        active = null; return true;
    }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Path output; final P11C4aConfigDepartureProbe.Mode mode;
        volatile Connection connection; volatile String failure; volatile int configurations;
        int ticks; boolean playReady;
        Run(Path output, P11C4aConfigDepartureProbe.Mode mode) { this.output = output; this.mode = mode; }
    }
}
