package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.gui.ModMismatchDisconnectedScreen;
import net.neoforged.neoforge.network.payload.ModdedNetworkSetupFailedPayload;

/** Client-only compile-selected fault and observations; original failure is not converted into native success. */
public final class P11C4aRequiredClientProbe {
    private static volatile Run active;
    private static int registrarEntries, registrarReturns, versionMutations;
    private static String registrationFailure;
    private P11C4aRequiredClientProbe() { }

    private static boolean enabled() {
        return P11C4aRequiredProbe.selected() && P11C4aEvidence.enabled()
                && "c4a-dedicated".equals(System.getProperty("gramarye.p11.online.case"));
    }
    private static boolean faulty() { return "a".equals(System.getProperty("gramarye.p11.online.role")); }
    /** Only the exact P11 register(event) client mixin calls this; no general registry mutation API. */
    public static synchronized boolean originalRegistration() {
        if (!enabled()) { return true; }
        if (++registrarEntries != 1 || !faulty() && !"b".equals(System.getProperty("gramarye.p11.online.role"))) {
            registrationFailure = "REQUIRED_REGISTRATION_SCOPE";
        }
        return !(faulty() && P11C4aRequiredProbe.missing());
    }
    public static synchronized String registrarVersion(String original) {
        if (!enabled()) { return original; }
        if (!original.equals(P11C4aRequiredProbe.ORIGINAL_VERSION)) { registrationFailure = "REQUIRED_ORIGINAL_VERSION_CHANGED"; }
        if (faulty() && !P11C4aRequiredProbe.missing()) { versionMutations++; return P11C4aRequiredProbe.WRONG_VERSION; }
        return original;
    }
    public static synchronized void registrationReturned() { if (enabled()) { registrarReturns++; } }

    static void arm(Minecraft minecraft, String role, Path output) throws IOException {
        require(enabled() && active == null && minecraft.isSameThread() && role.matches("[ab]")
                && minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null,
                "REQUIRED_CLIENT_ARM_SCOPE");
        synchronized (P11C4aRequiredClientProbe.class) {
            require(registrationFailure == null && registrarEntries == 1
                    && registrarReturns == (role.equals("a") && P11C4aRequiredProbe.missing() ? 0 : 1)
                    && versionMutations == (role.equals("a") && !P11C4aRequiredProbe.missing() ? 1 : 0),
                    "REQUIRED_REGISTRATION_NOT_EXACT");
        }
        P11C4aEvidence.write(output, "required-registration.json", Map.of(
                "status", "EXACT_CLIENT_REGISTRAR_FIXTURE_NOT_RUNTIME_ACCEPTANCE", "role", role,
                "mode", P11C4aScenario.MODE.name(), "originalEntries", registrarEntries,
                "originalReturns", registrarReturns, "versionMutations", versionMutations,
                "p7P8RegistrarModified", false));
        active = new Run(role, output);
    }
    public static void installed(Connection connection, PacketListener listener) {
        var run = active;
        if (run == null || !(listener instanceof ClientConfigurationPacketListenerImpl config)) { return; }
        synchronized (run) {
            if (connection.getPacketListener() != listener || config.getConnection() != connection
                    || !connection.isEncrypted() || connection.isMemoryConnection()
                    || run.connection != null && run.connection != connection) { run.failure = "REQUIRED_CLIENT_CONFIG_IDENTITY"; return; }
            run.connection = connection; run.configurations++;
        }
    }
    public static void failedPayload(Connection connection, ClientboundCustomPayloadPacket packet) {
        var run = active;
        if (run == null || !(packet.payload() instanceof ModdedNetworkSetupFailedPayload failure)) { return; }
        synchronized (run) {
            if (!run.role.equals("a") || connection != run.connection
                    || !P11C4aRequiredProbe.expectedIds(failure.failureReasons().keySet())) { run.failure = "REQUIRED_CLIENT_FAILURE_CHANNELS"; return; }
            // Only the exact two public channel IDs are inspected, never the component arguments/text.
            run.failurePackets++;
        }
    }
    public static void disconnected(Connection connection, Component reason) {
        var run = active; if (run == null) { return; }
        synchronized (run) {
            if (!run.role.equals("a") || connection != run.connection || !P11C4aRequiredProbe.incompatible(reason)) {
                run.failure = "REQUIRED_CLIENT_NATIVE_REASON"; return;
            }
            run.disconnects++;
        }
    }
    static boolean tick(Minecraft minecraft) throws IOException {
        var run = active;
        require(run != null && minecraft.isSameThread() && run.failure == null && ++run.ticks <= 24000,
                "REQUIRED_CLIENT_DEADLINE_OR_OBSERVER");
        if (run.connection == null) { return false; }
        long logins = P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN);
        long respawns = P11C4aNativeObservations.count(run.connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        if (run.role.equals("a")) {
            require(logins == 0 && respawns == 0 && minecraft.player == null && minecraft.level == null,
                    "REQUIRED_FAULTY_CLIENT_REACHED_PLAY");
            if (run.connection.isConnected() || !(minecraft.screen instanceof ModMismatchDisconnectedScreen)) { return false; }
            require(run.configurations == 1 && run.failurePackets == 1 && run.disconnects == 1,
                    "REQUIRED_CLIENT_REJECTION_NOT_EXACT");
            P11C4aEvidence.write(run.output, "required-rejection.json", Map.of(
                    "status", "ORIGINAL_CONFIG_NEGOTIATION_REJECTED_EXPECTED_FAULT_NOT_NATIVE_SUCCESS",
                    "mode", P11C4aScenario.MODE.name(), "nativeConfigInstalls", run.configurations,
                    "decodedSetupFailurePackets", run.failurePackets, "nativeDisconnectReturns", run.disconnects,
                    "reasonKey", "multiplayer.disconnect.incompatible", "nativeLoginReturns", logins,
                    "nativeRespawnReturns", respawns, "nativeMismatchScreen", true, "fullC4aAcceptance", false));
            return true;
        }
        var server = P11C4aEvidence.root().resolve("server");
        if (run.departed) { return minecraft.getConnection() == null && minecraft.player == null && minecraft.level == null; }
        require(run.connection.isConnected() && run.configurations == 1 && run.failurePackets == 0 && run.disconnects == 0,
                "REQUIRED_CONTROL_CLIENT_DISTURBED");
        if (!run.play) {
            var state = P11ClientTransitions.view();
            if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null
                    || state == null || state.outcome() != P11TransitionProtocol.Outcome.COMPLETED) { return false; }
            require(logins == 1 && respawns == 0 && minecraft.getConnection().getConnection() == run.connection
                    && state.kind() == P11TransitionProtocol.Kind.JOIN, "REQUIRED_CONTROL_CLIENT_PLAY");
            run.play = true;
            P11C4aEvidence.write(run.output, "required-control-play.json", Map.of(
                    "status", "ORIGINAL_NORMAL_CONTROL_CLIENT_INITIAL_PLAY", "role", "b",
                    "identityPseudonym", P11C4aEvidence.pseudonym(minecraft.player.getUUID()),
                    "nativeLoginReturns", logins, "nativeRespawnReturns", respawns));
        }
        if (!run.swung && P11C4aEvidence.cuePresent(server, "b-required-action.ready")) {
            minecraft.player.swing(InteractionHand.MAIN_HAND); run.swung = true;
        }
        if (!P11C4aEvidence.cuePresent(server, "b-required-finish.ready")) { return false; }
        require(run.swung && logins == 1 && respawns == 0, "REQUIRED_CONTROL_FINISH_WITHOUT_ACTION");
        P11C4aEvidence.write(run.output, "required-control-finish.json", Map.of(
                "status", "NORMAL_CONTROL_NATIVE_SWING_SENT_SERVER_RETURN_PROOF_SEPARATE", "originalSwingCalls", 1,
                "nativeLoginReturns", logins, "nativeRespawnReturns", respawns, "fullC4aAcceptance", false));
        run.departed = true;
        minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen());
        return false;
    }
    static void release() { active = null; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final String role; final Path output;
        volatile Connection connection; volatile String failure;
        volatile int configurations, failurePackets, disconnects;
        int ticks; boolean play, swung, departed;
        Run(String role, Path output) { this.role = role; this.output = output; }
    }
}
