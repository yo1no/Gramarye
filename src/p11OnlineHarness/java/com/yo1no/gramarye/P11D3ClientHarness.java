package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.network.P11D3ClientObservation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Original native connect, starter, discrete R, death button and leave routes. No OS-input claim. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11D3ClientHarness {
    private enum Phase { BOOT, WAIT_CONNECT, CONNECTING, PLAY, WAIT_RECONNECT, FINISHING, TERMINAL }
    private static Phase phase = Phase.BOOT;
    private static Path output, serverOutput;
    private static String role;
    private static Connection connection;
    private static UUID playerId;
    private static LocalPlayer firstPlayer;
    private static int ticks, phaseTicks, logins, casts, sends, focusedCast, acknowledgements, respawns;
    private static long lastSequence;
    private static boolean nativeCall, onboarding, starter, inputReady, inputArmed, replayed, deathClicked, respawnSealed;
    private P11D3ClientHarness() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ClientTickEvent.Post ignored) {
        if (P11D3HostProbe.selected()) { P11D3HostClientProbe.tick(Minecraft.getInstance()); return; }
        if (!P11D3ServerHarness.selected() || nativeCall || phase == Phase.TERMINAL) return;
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread() && ++ticks <= 24_000 && ++phaseTicks <= 12_000, "FINITE_CLIENT_PROGRESS");
            if (serverOutput != null && cue("abort.ready")) { fail(minecraft, "D3_OWNED_SUPERVISOR_ABORT"); return; }
            switch (phase) {
                case BOOT -> bootstrap(minecraft);
                case WAIT_CONNECT -> { if (cue(role + "-connect.ready")) connect(minecraft); }
                case CONNECTING -> { }
                case WAIT_RECONNECT -> {
                    if (closed(minecraft) && cue("a-reconnect.ready")) connect(minecraft);
                }
                case FINISHING -> { if (closed(minecraft)) finish(minecraft); }
                case PLAY -> play(minecraft);
                case TERMINAL -> { }
            }
        } catch (Exception | LinkageError primary) { fail(minecraft, P11C4aEvidence.failureCode(primary)); }
    }
    private static void bootstrap(Minecraft minecraft) throws IOException {
        if (output == null) {
            role = P11C4aEvidence.property("role"); require(role.equals("a") || role.equals("b"), "EXACT_ROLE");
            output = P11C4aEvidence.reserve("client-" + role); serverOutput = P11C4aEvidence.root().resolve("server");
            minecraft.getWindow().setTitle("Gramarye D3 " + P11C4aEvidence.property("runId") + " " + role);
            P11C4aEvidence.write(output, "bootstrap.json", Map.of("status", "CLIENT_BOOTSTRAP_NOT_AUTH_PROOF",
                    "productionJarSha256", P11OnlineInputs.verifyFrozenJar()));
        }
        require(minecraft.player == null && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null, "FRESH_CLIENT");
        if (minecraft.getOverlay() != null) return;
        if (!onboarding && minecraft.screen instanceof AccessibilityOnboardingScreen) {
            onboarding = true; nativeCall = true; try { minecraft.screen.onClose(); } finally { nativeCall = false; } return;
        }
        if (!(minecraft.screen instanceof TitleScreen)) return;
        minecraft.options.pauseOnLostFocus = false;
        P11C4aEvidence.write(output, "inputs.json", Map.of("status", "ARMED_NOT_AUTH_PROOF", "role", role,
                "case", P11C4aEvidence.property("case"), "runId", P11C4aEvidence.property("runId")));
        transition(Phase.WAIT_CONNECT);
    }
    private static void connect(Minecraft minecraft) {
        require(minecraft.player == null && minecraft.getConnection() == null, "ORIGINAL_CLOSE_BEFORE_CONNECT");
        String address = "127.0.0.1:" + P11C4aEvidence.port();
        transition(Phase.CONNECTING); inputReady = false; nativeCall = true; P11D3ClientObservation.clear();
        try { ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("P11 D3", address, ServerData.Type.OTHER), false, null); }
        finally { nativeCall = false; }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        if (P11D3HostProbe.selected()) { P11D3HostClientProbe.login(event); return; }
        if (!P11D3ServerHarness.selected() || phase == Phase.TERMINAL) return;
        var minecraft = Minecraft.getInstance();
        try {
            var actual = event.getConnection(); var player = event.getPlayer();
            require(phase == Phase.CONNECTING && minecraft.isSameThread() && player != null && player == minecraft.player
                    && actual == player.connection.getConnection() && actual != connection && actual.isConnected()
                    && actual.isEncrypted() && !actual.isMemoryConnection(), "ORIGINAL_AUTHENTICATED_LOGIN");
            require(playerId == null || playerId.equals(player.getUUID()), "EXACT_ACCOUNT_RECONNECT");
            playerId = player.getUUID(); connection = actual; logins++; if (firstPlayer == null) firstPlayer = player;
            require(logins <= (lateCase() && role.equals("a") ? 2 : 1), "BOUNDED_NATIVE_LOGINS");
            P11C4aEvidence.write(output, "login-" + logins + ".json", Map.of("status", "ORIGINAL_CLIENT_LOGIN_EVENT",
                    "encrypted", true, "newPhysicalConnection", true, "ordinal", logins)); transition(Phase.PLAY);
        } catch (Exception | LinkageError primary) { fail(minecraft, P11C4aEvidence.failureCode(primary)); }
    }
    private static void play(Minecraft minecraft) throws IOException {
        require(connection != null && connection.isConnected() && minecraft.getConnection() != null
                && minecraft.getConnection().getConnection() == connection, "CURRENT_NATIVE_CONNECTION");
        if (role.equals("a") && scenario().equals("d3-respawn") && receipt("d3-before-death.json") && !respawnSealed) {
            if (!deathClicked && minecraft.getOverlay() == null && minecraft.screen instanceof DeathScreen screen
                    && clickRespawn(minecraft, screen)) deathClicked = true;
            var transition = P11ClientTransitions.view();
            if (deathClicked && respawns == 1 && minecraft.player != null && minecraft.player != firstPlayer
                    && minecraft.player.getUUID().equals(playerId) && transition != null
                    && transition.kind() == P11TransitionProtocol.Kind.DEATH
                    && transition.outcome() == P11TransitionProtocol.Outcome.COMPLETED
                    && minecraft.screen == null && minecraft.getOverlay() == null) {
                P11C4aEvidence.write(output, "respawn.json", Map.of("status", "ORIGINAL_DEATH_BUTTON_RESPAWN_RETURN_AND_COMPLETED",
                        "sameConnection", true, "handlerReturns", respawns, "originalButtonClicks", 1)); respawnSealed = true;
            }
            if (!respawnSealed) return;
        }
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || minecraft.getOverlay() != null) return;
        require(minecraft.player.isAlive() && minecraft.player.connection == minecraft.getConnection(), "CURRENT_LIVING_PLAYER");
        if (!starter && cue(role + "-starter.ready")) {
            starter = true; minecraft.getConnection().sendCommand("gramarye starter");
            P11C4aEvidence.write(output, "starter.json", Map.of("status", "ORIGINAL_STARTER_COMMAND_SENT")); return;
        }
        if (role.equals("a") && !replayed && cue("a-replay.ready")) {
            require(sends == 1 && logins == 1 && lastSequence == 1, "EXACT_PRIOR_SEND_FOR_REPLAY");
            P11D3ClientObservation.replay(); replayed = true;
            P11C4aEvidence.write(output, "replayed.json", Map.of("status", "ENGINEERING_IDENTICAL_PAYLOAD_ORIGINAL_WIRE_SEND",
                    "originalRCount", casts, "replayCount", 1, "sequence", lastSequence)); return;
        }
        if (role.equals("a") && logins == 1 && lateCase() && cue("a-leave.ready")) {
            transition(Phase.WAIT_RECONNECT); leave(minecraft);
            require(closed(minecraft), "ORIGINAL_LEAVE_TERMINAL");
            P11C4aEvidence.write(output, "left.json", Map.of("status", "ORIGINAL_NATIVE_LEAVE", "closed", true)); return;
        }
        if (cue(role + "-finish.ready")) {
            require(receipt("d3-result.json"), "SERVER_PROOF_BEFORE_ORDINARY_TEARDOWN");
            transition(Phase.FINISHING); leave(minecraft); return;
        }
        int next = casts + 1;
        if (starter && cue(role + "-cast-" + next + ".ready")) {
            if (focusedCast != next) { GLFW.glfwFocusWindow(minecraft.getWindow().getWindow()); focusedCast = next; return; }
            if (!inputReady || !minecraft.isWindowActive() || !P11D3ClientObservation.ready()) return;
            require(!inputArmed && sends == casts && casts < 2, "ONE_ORIGINAL_R_PER_CUE");
            inputArmed = true; casts++;
            long window = minecraft.getWindow().getWindow(); int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_PRESS, 0);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_RELEASE, 0);
        }
    }
    public static void inputReady(boolean actual) {
        if (P11D3HostProbe.selected()) { P11D3HostClientProbe.inputReady(actual); return; }
        if (P11D3ServerHarness.selected()) inputReady = actual;
    }
    public static void submitted(long sequence, int slot, int mask, boolean noHints) {
        if (P11D3HostProbe.selected()) { P11D3HostClientProbe.submitted(sequence, slot, mask, noHints); return; }
        if (!P11D3ServerHarness.selected()) return;
        require(Minecraft.getInstance().isSameThread() && phase == Phase.PLAY && inputArmed && sends + 1 == casts
                && sequence == (logins == 2 ? 1 : casts) && slot == 0 && mask == 0 && noHints, "EXACT_ORIGINAL_R_BODY_AND_SEQUENCE");
        inputArmed = false; sends++; lastSequence = sequence;
        try { P11C4aEvidence.write(output, "cast-" + sends + ".json", Map.of("status", "ORIGINAL_P9_SEND_RETURN",
                "wireSequence", sequence, "slot", slot, "hintsAbsent", noHints, "loginOrdinal", logins)); }
        catch (IOException failure) { throw new IllegalStateException("D3_CLIENT_RECEIPT", failure); }
    }
    public static void acknowledged(long sequence, String disposition) {
        if (P11D3HostProbe.selected()) { P11D3HostClientProbe.acknowledged(sequence, disposition); return; }
        if (!P11D3ServerHarness.selected()) return;
        require(Minecraft.getInstance().isSameThread() && phase == Phase.PLAY && ++acknowledgements <= 4, "BOUNDED_CURRENT_ACK");
        try { P11C4aEvidence.write(output, "ack-" + acknowledgements + ".json", Map.of("status", "ACTUAL_CURRENT_MIRROR_ACK",
                "sequence", sequence, "disposition", disposition, "loginOrdinal", logins)); }
        catch (IOException failure) { throw new IllegalStateException("D3_CLIENT_ACK_RECEIPT", failure); }
    }
    public static void respawnReturned(ClientPacketListener listener) {
        if (!P11D3ServerHarness.selected()) return;
        var minecraft = Minecraft.getInstance();
        require(scenario().equals("d3-respawn") && minecraft.isSameThread() && listener == minecraft.getConnection()
                && listener.getConnection() == connection && ++respawns == 1, "EXACT_NATIVE_RESPAWN_RETURN");
    }
    private static boolean clickRespawn(Minecraft minecraft, DeathScreen screen) {
        Button found = null;
        for (var item : screen.children()) if (item instanceof Button button && button.active && button.visible
                && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals("deathScreen.respawn")) {
            require(found == null, "UNIQUE_ORIGINAL_RESPAWN_BUTTON"); found = button;
        }
        if (found == null) return false;
        require(minecraft.mouseHandler instanceof P11C4aClientInputProbe.MouseInput, "ORIGINAL_MOUSE_INVOKER");
        var input = (P11C4aClientInputProbe.MouseInput) minecraft.mouseHandler; var window = minecraft.getWindow();
        input.p11$move(window.getWindow(), (found.getX() + found.getWidth() / 2.0) * window.getScreenWidth() / window.getGuiScaledWidth(),
                (found.getY() + found.getHeight() / 2.0) * window.getScreenHeight() / window.getGuiScaledHeight());
        input.p11$press(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        input.p11$press(window.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0); return true;
    }
    private static void leave(Minecraft minecraft) {
        nativeCall = true; try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }
        finally { nativeCall = false; P11D3ClientObservation.clear(); }
    }
    private static boolean closed(Minecraft minecraft) { return minecraft.player == null && minecraft.level == null
            && minecraft.getConnection() == null && connection != null && !connection.isConnected(); }
    private static void finish(Minecraft minecraft) throws IOException {
        int expected = role.equals("b") ? scenario().equals("d3-dual") ? 1 : 0
                : lateCase() || scenario().equals("d3-respawn") ? 2 : 1;
        require(casts == expected && sends == expected && !inputArmed && logins == (role.equals("a") && lateCase() ? 2 : 1), "FINAL_EXACT_INPUT_COUNTS");
        int expectedAcks = role.equals("b") ? scenario().equals("d3-dual") ? 1 : 0
                : scenario().equals("d3-replay") || scenario().equals("d3-respawn") ? 2 : 1;
        require(acknowledgements == expectedAcks, "EXACT_CURRENT_CLIENT_ACK_COUNTS");
        P11C4aEvidence.write(output, "result.json", Map.of("status", "ORIGINAL_NATIVE_CLIENT_TERMINAL", "closed", true,
                "logins", logins, "originalKeyCallbackClicks", casts, "originalP9Sends", sends,
                "engineeringReplays", replayed ? 1 : 0, "actualCurrentAckReceipts", acknowledgements,
                "physicalOsInputClaim", false)); transition(Phase.TERMINAL); minecraft.stop();
    }
    private static boolean lateCase() { return ListHolder.LATE.contains(scenario()); }
    private static final class ListHolder { private static final java.util.Set<String> LATE = java.util.Set.of("d3-old-source", "d3-late-task", "d3-old-result"); }
    private static String scenario() { return P11C4aEvidence.property("case"); }
    private static boolean cue(String leaf) throws IOException { return P11C4aEvidence.cuePresent(serverOutput, leaf); }
    private static boolean receipt(String leaf) throws IOException { return P11C4aEvidence.receiptPresent(serverOutput, leaf); }
    private static void transition(Phase next) { phase = next; phaseTicks = 0; }
    private static void require(boolean value, String code) { P11D3ServerHarness.require(value, "CLIENT_" + code); }
    private static void fail(Minecraft minecraft, String code) {
        if (phase == Phase.TERMINAL) return;
        transition(Phase.TERMINAL); P11D3ClientObservation.clear();
        try { if (output != null) P11C4aEvidence.write(output, "result.json", Map.of("status", "FAIL", "code", code,
                "logins", logins, "casts", casts, "sends", sends)); } catch (IOException ignored) { }
        minecraft.stop();
    }
}
