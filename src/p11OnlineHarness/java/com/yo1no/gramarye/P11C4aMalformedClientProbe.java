package com.yo1no.gramarye;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.world.InteractionHand;

/** Original Death callback / periodic STATUS senders, never a manually constructed request. */
final class P11C4aMalformedClientProbe {
    private static Run active;
    private P11C4aMalformedClientProbe() { }
    static boolean tick(Minecraft minecraft, Connection connection, String role, Path output, Path serverOutput) throws IOException {
        require(minecraft.isSameThread() && role.matches("[ab]"), "MALFORMED_CLIENT_OWNER");
        if (active == null) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-malformed-arm.ready")) { return false; }
            require(connection != null && connection.isConnected() && connection.isEncrypted() && !connection.isMemoryConnection()
                    && minecraft.player != null && minecraft.player.isAlive() && minecraft.getConnection() != null
                    && minecraft.getConnection().getConnection() == connection, "MALFORMED_CLIENT_ACTUAL_PLAY");
            active = new Run(connection, role, output);
            if (role.equals("a")) { P11C4aMalformedWireProbe.arm(connection, false); P11C4aMalformedWireProbe.enable(); }
            P11C4aEvidence.write(output, "malformed-armed.json", Map.of("status", "ACTUAL_INITIAL_PLAY_CLIENT_ARMED_NOT_ACCEPTANCE", "role", role,
                    "identityPseudonym", P11C4aEvidence.pseudonym(minecraft.player.getUUID()), "mode", P11C4aScenario.MODE.name()));
            return false;
        }
        var run = active;
        require(run.connection == connection && ++run.ticks <= 2400, "MALFORMED_CLIENT_DEADLINE_OR_CONNECTION");
        if (role.equals("b")) {
            if (run.departed) { return minecraft.getConnection() == null && minecraft.player == null && minecraft.level == null; }
            require(connection.isConnected() && minecraft.player != null && minecraft.player.isAlive(), "MALFORMED_CONTROL_CLIENT_LOST");
            if (!run.swung && P11C4aEvidence.cuePresent(serverOutput, "b-malformed-action.ready")) {
                run.swung = true; minecraft.player.swing(InteractionHand.MAIN_HAND);
            }
            if (P11C4aEvidence.cuePresent(serverOutput, "b-malformed-finish.ready")) {
                require(run.swung, "MALFORMED_CONTROL_NO_ACTION"); run.departed = true;
                minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen());
            }
            return false;
        }
        if (connection.isConnected()) {
            if (P11C4aMalformedWireProbe.config()) {
                var state = P11ClientTransitions.view();
                if (!run.configReported && state != null && state.scope() == P11TransitionProtocol.Scope.CONFIG
                        && state.kind() == P11TransitionProtocol.Kind.ENTER_CONFIG && state.outcome() == P11TransitionProtocol.Outcome.COMPLETED) {
                    require(connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                            && minecraft.player == null && minecraft.level == null, "MALFORMED_CLIENT_CONFIG_TERMINAL");
                    run.configReported = true;
                    P11C4aEvidence.write(output, "malformed-config-terminal.json", Map.of("status", "ORIGINAL_INDEPENDENT_ENTER_CONFIG_COMPLETED", "scene", state.sceneSerial()));
                }
            } else if (P11C4aMalformedWireProbe.request() && !run.pressed) {
                run.pressed = P11C4aClientInputProbe.act(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN);
            }
            return false;
        }
        if (!(minecraft.screen instanceof DisconnectedScreen) || minecraft.player != null || minecraft.level != null) { return false; }
        require(P11C4aMalformedWireProbe.terminalReady()
                && (P11C4aMalformedWireProbe.config() || !P11C4aMalformedWireProbe.request() || run.pressed)
                && P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN) == 1
                && P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == 0,
                "MALFORMED_CLIENT_NATIVE_TERMINAL");
        P11C4aEvidence.write(output, "malformed-client-terminal.json", P11C4aMalformedWireProbe.report());
        return true;
    }
    static void release() { active = null; P11C4aMalformedWireProbe.release(); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final Connection connection; final String role; final Path output;
        int ticks; boolean configReported, pressed, swung, departed;
        Run(Connection connection, String role, Path output) { this.connection = connection; this.role = role; this.output = output; }
    }
}
