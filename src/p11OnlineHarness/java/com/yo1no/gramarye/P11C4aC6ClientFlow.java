package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.Connection;

/** Literal C6 cue sequence only. A cue is never recorded as a native admission fact. */
final class P11C4aC6ClientFlow {
    private static boolean started, parkingArmed, configurationRecorded, capacityExpected;
    private static int ticks;
    private static P11C4aC6ClientProbe.Step selected;
    private P11C4aC6ClientFlow() { }

    static boolean started() { return started; }
    static boolean tick(Minecraft minecraft, Connection connection, String role,
            Path output, Path serverOutput) throws IOException {
        P11C4aEvidence.require(minecraft.isSameThread() && connection != null && ++ticks <= 12_000,
                "C6_CLIENT_FLOW_OWNER_OR_TOTAL_DEADLINE");
        if (!started) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-c6-arm.ready")) { return false; }
            P11C4aC6ClientProbe.start(minecraft, connection, role, output); started = true;
        }
        if (role.equals("b") && P11C4aEvidence.cuePresent(serverOutput, "b-c6-capacity-close.ready")) {
            capacityExpected = true;
        }
        if (!connection.isConnected()) {
            if (role.equals("b") && capacityExpected) {
                P11C4aEvidence.write(output, "c6-capacity-disconnected.json", Map.of(
                        "status", "ACTUAL_B_CONNECTION_CLOSED_SERVER_CAPACITY_OBSERVATION_REQUIRED",
                        "nativeConnectionConnected", false, "fullC4aAcceptance", false));
                P11C4aC6ClientProbe.release(); return true;
            }
            P11C4aEvidence.require(role.equals("a") && selected == P11C4aC6ClientProbe.Step.OBSERVE_EXPIRY,
                    "C6_UNEXPECTED_DISCONNECT");
            if (P11C4aC6ClientProbe.tick(minecraft)) { P11C4aC6ClientProbe.release(); return true; }
            return false;
        }
        if (!parkingArmed && role.equals("a")
                && P11C4aEvidence.cuePresent(serverOutput, "a-parking-arm.ready")) {
            parkingArmed = true;
            P11C4aEvidence.write(output, "parking-armed.json", Map.of(
                    "status", "C6_CLIENT_ARMED_FOR_ORIGINAL_CONFIGURATION_NOT_ACCEPTANCE"));
        }
        var state = P11ClientTransitions.view();
        if (parkingArmed && !configurationRecorded && state != null && state.scope() == Scope.CONFIG
                && state.kind() == Kind.ENTER_CONFIG && state.outcome() == Outcome.COMPLETED) {
            P11C4aEvidence.require(minecraft.player == null && minecraft.level == null
                    && connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                    && state.actorGeneration() == 0 && state.requestSeq() == 0,
                    "C6_CONFIG_NOT_ACTUAL_ACTORLESS_TERMINAL");
            configurationRecorded = true;
            P11C4aEvidence.write(output, "parking-config-terminal.json", Map.of(
                    "status", "ACTUAL_C6_CONFIG_COMPLETED_BEFORE_RETURN", "state", state,
                    "playerPresent", false, "levelPresent", false));
        }
        if (selected == null) {
            for (var step : P11C4aC6ClientProbe.Step.values()) {
                if (P11C4aC6ClientProbe.complete(step)) { continue; }
                String suffix = step.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
                if (step == P11C4aC6ClientProbe.Step.OBSERVE_EXPIRY) { suffix = "expiry"; }
                if (P11C4aEvidence.cuePresent(serverOutput, role + "-c6-" + suffix + ".ready")) {
                    selected = step; P11C4aC6ClientProbe.select(step); break;
                }
            }
        }
        if (selected != null && P11C4aC6ClientProbe.tick(minecraft)) { selected = null; }
        return false;
    }
}
