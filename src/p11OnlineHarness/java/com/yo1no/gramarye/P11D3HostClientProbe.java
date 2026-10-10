package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.network.P11D3ClientObservation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

/** Exactly two native host lifetimes. The host process remains alive between them; the remote
 * client independently observes native disconnection before reauthenticating to server two. */
public final class P11D3HostClientProbe {
    private enum Phase { BOOT, WAIT_CONNECT, CONNECTING, PLAY, WAIT_STOP, BETWEEN, WAIT_FINAL_PEER, TERMINAL }
    private static Phase phase = Phase.BOOT;
    private static Path output, serverOutput;
    private static String role;
    private static Connection connection;
    private static IntegratedServer retainedServer;
    private static UUID identity;
    private static int ordinal, logins, ticks, phaseTicks, sends, casts, ackCount;
    private static boolean nativeCall, onboarding, published, starter, ready, armed, focused;
    private P11D3HostClientProbe() { }
    static void tick(Minecraft minecraft) {
        if (nativeCall || phase == Phase.TERMINAL) return;
        try {
            require(minecraft.isSameThread() && ++ticks <= 36_000 && ++phaseTicks <= 18_000, "FINITE_NATIVE_CLIENT_PROGRESS");
            if (serverOutput != null && cue("abort.ready")) { fail(minecraft, "D3_HOST_OWNED_SUPERVISOR_ABORT"); return; }
            switch (phase) {
                case BOOT -> bootstrap(minecraft);
                case WAIT_CONNECT -> { if (cue("host-" + ordinal + "-b-connect.ready")) connect(minecraft); }
                case CONNECTING -> { }
                case PLAY -> play(minecraft);
                case WAIT_STOP -> stopped(minecraft);
                case BETWEEN -> {
                    if (host()) {
                        if (receipt(output.getParent().resolve("client-b"), "host-1-terminal.json")) create(minecraft, 2);
                    } else {
                        ordinal = 2; transition(Phase.WAIT_CONNECT);
                    }
                }
                case WAIT_FINAL_PEER -> {
                    if (receipt(output.getParent().resolve("client-b"), "host-2-terminal.json")) finish(minecraft);
                }
                case TERMINAL -> { }
            }
        } catch (Exception | LinkageError primary) { fail(minecraft, P11C4aEvidence.failureCode(primary)); }
    }
    private static void bootstrap(Minecraft minecraft) throws IOException {
        if (output == null) {
            role = P11C4aEvidence.property("role"); require(role.equals("host") || role.equals("b"), "CLOSED_HOST_ROLE");
            output = P11C4aEvidence.reserve("client-" + role); serverOutput = P11C4aEvidence.root().resolve("server");
            minecraft.getWindow().setTitle("Gramarye D3 " + P11C4aEvidence.property("runId") + " " + role);
            P11C4aEvidence.write(output, "bootstrap.json", Map.of("status", "CLIENT_BOOTSTRAP_NOT_AUTH_PROOF",
                    "productionJarSha256", P11OnlineInputs.verifyFrozenJar()));
        }
        require(minecraft.player == null && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null, "FRESH_HOST_CLIENT");
        if (minecraft.getOverlay() != null) return;
        if (!onboarding && minecraft.screen instanceof AccessibilityOnboardingScreen) {
            onboarding = true; nativeCall = true; try { minecraft.screen.onClose(); } finally { nativeCall = false; } return;
        }
        if (!(minecraft.screen instanceof TitleScreen)) return;
        minecraft.options.pauseOnLostFocus = false;
        P11C4aEvidence.write(output, "inputs.json", Map.of("status", "ARMED_NOT_AUTH_PROOF", "role", role,
                "case", "d3-host-lan", "runId", P11C4aEvidence.property("runId")));
        ordinal = 1; if (host()) create(minecraft, 1); else transition(Phase.WAIT_CONNECT);
    }
    private static void create(Minecraft minecraft, int next) {
        require(host() && minecraft.player == null && minecraft.getConnection() == null
                && minecraft.getSingleplayerServer() == null && minecraft.screen instanceof TitleScreen
                && (next == 1 || retainedServer != null && retainedServer.isShutdown()), "PREVIOUS_REAL_SERVER_HALTED");
        String world = "p11-d3-owned-host-" + next;
        require(!Files.exists(minecraft.gameDirectory.toPath().resolve("saves").resolve(world)), "NEW_OWNED_WORLD_ABSENT");
        ordinal = next; published = starter = ready = focused = false; transition(Phase.CONNECTING); nativeCall = true;
        var settings = new LevelSettings("P11 D3 isolated host " + next, GameType.SURVIVAL, false,
                Difficulty.PEACEFUL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
        try { minecraft.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(110031L + next, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), minecraft.screen); }
        finally { nativeCall = false; }
    }
    private static void connect(Minecraft minecraft) {
        require(!host() && minecraft.player == null && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null, "REMOTE_NATIVE_CLOSE_BEFORE_CONNECT");
        String address = "127.0.0.1:" + P11C4aEvidence.port();
        starter = ready = focused = false; transition(Phase.CONNECTING); nativeCall = true; P11D3ClientObservation.clear();
        try { ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("P11 D3 host", address, ServerData.Type.OTHER), false, null); }
        finally { nativeCall = false; }
    }
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        var minecraft = Minecraft.getInstance();
        try {
            var actual = event.getConnection(); var player = event.getPlayer();
            require(minecraft.isSameThread() && phase == Phase.CONNECTING && player != null && player == minecraft.player
                    && actual != null && actual != connection && actual == player.connection.getConnection() && actual.isConnected()
                    && actual.isMemoryConnection() == host() && (host() || actual.isEncrypted()), "ORIGINAL_ROLE_NATIVE_LOGIN");
            require(identity == null || identity.equals(player.getUUID()), "SAME_ACCOUNT_BOTH_LIFETIMES");
            identity = player.getUUID(); connection = actual; require(++logins == ordinal && logins <= 2, "EXACT_TWO_NATIVE_LOGINS");
            if (host()) {
                var next = minecraft.getSingleplayerServer(); require(next != null && next != retainedServer, "ACTUAL_NEW_INTEGRATED_SERVER"); retainedServer = next;
            }
            P11C4aEvidence.write(output, "login-" + ordinal + ".json", Map.of("status", "ORIGINAL_ROLE_CLIENT_LOGIN",
                    "memoryConnection", actual.isMemoryConnection(), "encrypted", actual.isEncrypted(),
                    "newConnection", true, "serverOrdinal", ordinal)); transition(Phase.PLAY);
        } catch (Exception | LinkageError primary) { fail(minecraft, P11C4aEvidence.failureCode(primary)); }
    }
    private static void play(Minecraft minecraft) throws IOException {
        if (!host() && cue("host-" + ordinal + "-leave.ready")) { transition(Phase.WAIT_STOP); stopped(minecraft); return; }
        require(connection != null && connection.isConnected() && minecraft.player != null && minecraft.level != null
                && minecraft.getConnection() == minecraft.player.connection && minecraft.getConnection().getConnection() == connection,
                "CURRENT_NATIVE_PLAY");
        if (minecraft.getOverlay() != null || minecraft.screen != null) return;
        if (host()) {
            if (!published) {
                require(retainedServer == minecraft.getSingleplayerServer()
                        && retainedServer.publishServer(GameType.SURVIVAL, false, P11C4aEvidence.port()), "ORIGINAL_LAN_PUBLICATION");
                published = true; P11C4aEvidence.cue(serverOutput, "host-" + ordinal + "-b-connect.ready");
            }
            if (cue("host-" + ordinal + "-leave.ready")) {
                transition(Phase.WAIT_STOP); nativeCall = true;
                try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }
                finally { nativeCall = false; P11D3ClientObservation.clear(); }
            }
            return;
        }
        if (!starter && cue("host-" + ordinal + "-b-starter.ready")) {
            starter = true; minecraft.getConnection().sendCommand("gramarye starter");
            P11C4aEvidence.write(output, "host-" + ordinal + "-starter.json", Map.of("status", "ORIGINAL_STARTER_COMMAND_SENT")); return;
        }
        if (starter && sends < ordinal && cue("host-" + ordinal + "-b-cast.ready")) {
            if (!focused) { GLFW.glfwFocusWindow(minecraft.getWindow().getWindow()); focused = true; return; }
            if (!ready || !minecraft.isWindowActive() || !P11D3ClientObservation.ready()) return;
            require(!armed && casts == sends && casts + 1 == ordinal, "ORIGINAL_R_ONCE_PER_REAL_SERVER");
            casts++; armed = true; long window = minecraft.getWindow().getWindow(); int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_PRESS, 0);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_RELEASE, 0);
        }
    }
    private static void stopped(Minecraft minecraft) throws IOException {
        if (connection.isConnected() || minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null) return;
        require(host() ? retainedServer.isShutdown() && minecraft.screen instanceof TitleScreen
                : minecraft.screen instanceof DisconnectedScreen, "ORIGINAL_ROLE_TERMINAL");
        if (!receipt(serverOutput, "host-" + ordinal + "-stopped.json")
                || !receipt(serverOutput, "host-" + ordinal + "-data-terminal.json")) return;
        require(host() ? casts == 0 && sends == 0 : casts == ordinal && sends == ordinal && !armed, "EXACT_SERVER_INPUT_COUNTS");
        P11C4aEvidence.write(output, "host-" + ordinal + "-terminal.json", Map.of("status", "ORIGINAL_NATIVE_STOP_CLIENT_TERMINAL",
                "closed", true, "serverOrdinal", ordinal, "casts", casts, "sends", sends,
                "actualAckReceipts", ackCount, "localHostAuthenticationClaim", false));
        P11D3ClientObservation.clear();
        if (ordinal == 1) { transition(Phase.BETWEEN); return; }
        if (host() && !receipt(output.getParent().resolve("client-b"), "host-2-terminal.json")) {
            transition(Phase.WAIT_FINAL_PEER); return;
        }
        finish(minecraft);
    }
    private static void finish(Minecraft minecraft) throws IOException {
        require(logins == 2 && receipt(serverOutput, "d3-result.json"), "TWO_LIFETIMES_AND_INDEPENDENT_SERVER_PROOF");
        P11C4aEvidence.write(output, "result.json", Map.of("status", "SAME_JVM_TWO_SERVER_CLIENT_TERMINAL", "logins", logins,
                "originalRCallbacks", casts, "originalP9Sends", sends, "actualAckReceipts", ackCount,
                "closed", true, "physicalOsInputClaim", false)); transition(Phase.TERMINAL); minecraft.stop();
    }
    static void inputReady(boolean value) { ready = value; }
    static void submitted(long sequence, int slot, int mask, boolean noHints) {
        require(!host() && phase == Phase.PLAY && armed && casts == sends + 1 && sequence == 1 && slot == 0 && mask == 0 && noHints,
                "FRESH_ORIGINAL_FIRST_WIRE_ONE");
        armed = false; sends++;
        try { P11C4aEvidence.write(output, "cast-" + sends + ".json", Map.of("status", "ORIGINAL_P9_SEND_RETURN",
                "wireSequence", sequence, "serverOrdinal", ordinal)); }
        catch (IOException primary) { throw new IllegalStateException("D3_HOST_CLIENT_RECEIPT", primary); }
    }
    static void acknowledged(long sequence, String disposition) {
        require(!host() && ordinal == 2 && phase == Phase.PLAY && sequence == 1 && disposition.equals("ACCEPTED")
                && ++ackCount == 1, "NO_OLD_ACK_ON_NEW_SERVER");
    }
    private static boolean host() { return "host".equals(role); }
    private static boolean cue(String leaf) throws IOException { return P11C4aEvidence.cuePresent(serverOutput, leaf); }
    private static boolean receipt(Path root, String leaf) throws IOException { return P11C4aEvidence.receiptPresent(root, leaf); }
    private static void transition(Phase next) { phase = next; phaseTicks = 0; }
    private static void require(boolean value, String code) { P11D3ServerHarness.require(value, "HOST_CLIENT_" + code); }
    private static void fail(Minecraft minecraft, String code) {
        if (phase == Phase.TERMINAL) return; transition(Phase.TERMINAL); P11D3ClientObservation.clear();
        try { if (output != null) P11C4aEvidence.write(output, "result.json", Map.of("status", "FAIL", "code", code,
                "serverOrdinal", ordinal, "logins", logins, "casts", casts, "sends", sends)); } catch (IOException ignored) { }
        minecraft.stop();
    }
}
