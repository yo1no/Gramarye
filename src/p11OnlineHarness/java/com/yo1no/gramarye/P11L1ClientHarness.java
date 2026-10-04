package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Same authenticated JVM reconnects through original ConnectScreen; no session/account reads. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11L1ClientHarness {
    private enum Phase { BOOTSTRAP, WAIT_CONNECT, CONNECTING, PLAY, WAIT_RECONNECT, TERMINAL }
    private static Phase phase = Phase.BOOTSTRAP;
    private static Path output, serverOutput;
    private static String role;
    private static UUID identity;
    private static Connection connection;
    private static boolean onboarding, nativeCall, starter, ready, armedInput, resourceReload, lifecycleArmed, terminalCloseReturned, capacitySwing, capacityFocusRequested;
    private static int ticks, phaseTicks, logins, casts, sends;
    private static final Delivery[] delivery = {new Delivery(), new Delivery(), new Delivery(), new Delivery()};
    private P11L1ClientHarness() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ClientTickEvent.Post ignored) {
        if (!P11L1ServerHarness.enabled() || nativeCall || phase == Phase.TERMINAL) { return; }
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread() && ++ticks <= 30_000 && ++phaseTicks <=
                    (phase == Phase.BOOTSTRAP || phase == Phase.WAIT_CONNECT ? 24_000 : 2400), "CLIENT_DEADLINE_OR_THREAD");
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case WAIT_CONNECT -> { if (cue(role + "-connect.ready")) { connect(minecraft); } }
                case CONNECTING -> { P11L1ContextRefusalClientProbe.tick(minecraft); }
                case PLAY -> play(minecraft);
                case WAIT_RECONNECT -> {
                    if (minecraft.player == null && minecraft.getConnection() == null && cue("a-reconnect-" + logins + ".ready")) {
                        connect(minecraft);
                    }
                }
                case TERMINAL -> { }
            }
        } catch (Exception | LinkageError failure) { fail(minecraft, P11C4aEvidence.failureCode(failure)); }
    }

    private static void bootstrap(Minecraft minecraft) throws IOException {
        if (output == null) {
            role = P11C4aEvidence.property("role");
            require(role.equals("a") || role.equals("b"), "CLIENT_ROLE");
            output = P11C4aEvidence.reserve("client-" + role);
            serverOutput = P11C4aEvidence.root().resolve("server");
            minecraft.getWindow().setTitle("Gramarye P11 L1 " + P11C4aEvidence.property("runId") + " " + role);
            P11C4aEvidence.write(output, "bootstrap.json", Map.of("status", "INITIAL_CLIENT_TICK_NOT_AUTH_PROOF", "productionJarSha256", P11OnlineInputs.verifyFrozenJar()));
        }
        require(minecraft.player == null && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null, "FRESH_CLIENT_REQUIRED");
        if (minecraft.getOverlay() != null) { return; }
        if (!onboarding && minecraft.screen != null && minecraft.screen.getClass() == AccessibilityOnboardingScreen.class) {
            onboarding = true; nativeCall = true;
            try { minecraft.screen.onClose(); } finally { nativeCall = false; }
            P11C4aEvidence.write(output, "onboarding-continued.json", Map.of("status", "ORIGINAL_ONBOARDING_RETURN")); return;
        }
        if (!(minecraft.screen instanceof TitleScreen)) { return; }
        minecraft.options.pauseOnLostFocus = false;
        P11C4aEvidence.write(output, "inputs.json", Map.of("status", "ARMED_NOT_AUTH_PROOF", "role", role,
                "runId", P11C4aEvidence.property("runId"), "case", P11C4aEvidence.property("case")));
        transition(Phase.WAIT_CONNECT);
    }

    private static void connect(Minecraft minecraft) {
        require(minecraft.player == null && minecraft.getConnection() == null && !nativeCall, "RECONNECT_MUST_BE_NATIVE_CLOSED");
        if (P11L1ContextRefusalProbe.selected() && role.equals("a") && logins == 1) {
            P11L1ContextRefusalClientProbe.arm(minecraft, connection, output);
        }
        String address = "127.0.0.1:" + P11C4aEvidence.port();
        transition(Phase.CONNECTING); ready = false; nativeCall = true;
        try { ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("P11 L1 isolated", address, ServerData.Type.OTHER), false, null); }
        finally { nativeCall = false; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!P11L1ServerHarness.enabled() || phase == Phase.TERMINAL) { return; }
        try {
            var minecraft = Minecraft.getInstance(); var actual = event.getConnection(); var player = event.getPlayer();
            if (P11L1ServerHarness.lifecycle() && lifecycleArmed && phase == Phase.PLAY
                    && P11C4aEvidence.property("case").equals("l1-work-config")) {
                require(role.equals("a") && logins == 1 && actual == connection && player == minecraft.player
                        && player.getUUID().equals(identity) && actual.isConnected(), "SAME_CONNECTION_CONFIG_LOGIN");
                logins++;
                P11C4aEvidence.write(output, "login-2.json", Map.of("status", "ACTUAL_SAME_CONNECTION_CONFIG_LOGIN_NOT_NEW_AUTH",
                        "ordinal", logins, "samePhysicalConnection", true)); return;
            }
            require(phase == Phase.CONNECTING && minecraft.isSameThread() && actual != null && player != null
                    && player == minecraft.player && actual == player.connection.getConnection() && actual.isEncrypted()
                    && !actual.isMemoryConnection() && actual != connection && logins < (role.equals("a") ? 4 : 1), "CLIENT_LOGIN_IDENTITY");
            require(identity == null || identity.equals(player.getUUID()), "RECONNECT_CHANGED_ACCOUNT");
            identity = player.getUUID(); connection = actual; logins++;
            P11C4aEvidence.write(output, "login-" + logins + ".json", Map.of("status", "ACTUAL_CLIENT_LOGIN_EVENT_NOT_HANDLER_TAIL",
                    "identityPseudonym", P11C4aEvidence.pseudonym(identity), "ordinal", logins, "encrypted", true, "memoryConnection", false));
            transition(Phase.PLAY);
        } catch (Exception | LinkageError failure) { fail(Minecraft.getInstance(), P11C4aEvidence.failureCode(failure)); }
    }

    private static void play(Minecraft minecraft) throws IOException {
        P11L1ContextRefusalClientProbe.tick(minecraft);
        if (lifecycleArmed && !P11L1LifecycleClientProbe.tick(minecraft)) { return; }
        if (P11L1ServerHarness.naturalUnload() && role.equals("a") && !terminalCloseReturned
                && connection.isConnected() && cue("a-unload-close.ready")) {
            require(logins == 1 && casts == 1 && sends == 1 && minecraft.level != null
                    && minecraft.player != null && minecraft.getConnection() == minecraft.player.connection
                    && minecraft.getConnection().getConnection() == connection, "UNLOAD_ORIGINAL_CLIENT_LEAVE_CONTEXT");
            nativeCall = true;
            try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }
            finally { nativeCall = false; }
            terminalCloseReturned = true;
            P11C4aEvidence.write(output, "natural-unload-close.json", Map.of("status", "ORIGINAL_CLIENT_LEAVE_RETURN_NOT_SERVER_UNLOAD_PROOF",
                    "originalLeaveReturns", 1, "closed", !connection.isConnected()));
        }
        if (P11L1ServerHarness.terminalBoundary() && role.equals("a") && !terminalCloseReturned
                && connection.isConnected() && cue("a-terminal-close.ready")) {
            require(logins == 1 && casts == 1 && sends == 1 && minecraft.level != null
                    && minecraft.player != null && minecraft.getConnection() == minecraft.player.connection
                    && minecraft.getConnection().getConnection() == connection, "TERMINAL_ORIGINAL_CLIENT_LEAVE_CONTEXT");
            nativeCall = true;
            try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }
            finally { nativeCall = false; }
            terminalCloseReturned = true;
            P11C4aEvidence.write(output, "terminal-close.json", Map.of("status", "ORIGINAL_CLIENT_LEAVE_RETURN_NOT_SERVER_LOGOUT_PROOF",
                    "originalLeaveReturns", 1, "closed", !connection.isConnected()));
        }
        if (!connection.isConnected()) {
            if (P11L1RestartProbe.writeSelected() && (role.equals("b") || logins == 2)) {
                require(role.equals("a") ? logins == 2 && casts == 2 && sends == 2
                        : logins == 1 && casts == 0 && sends == 0, "RESTART_WRITE_NATIVE_INPUT_COUNTS");
                if (minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null
                        || !P11C4aEvidence.receiptPresent(serverOutput, "l1-restart-expected.json")) { return; }
                var result = new java.util.LinkedHashMap<String, Object>(Map.of(
                        "status", "ORIGINAL_RESTART_WRITE_CLIENT_TERMINAL_NOT_RESTART_PROOF", "logins", logins,
                        "originalKeyCallbackClicks", casts, "originalP9Sends", sends, "closed", true,
                        "playerAbsent", true, "levelAbsent", true, "currentListenerAbsent", true,
                        "physicalOsInputClaim", false));
                result.put("nativeDelivery", java.util.Arrays.stream(delivery).limit(logins).map(Delivery::facts).toList());
                P11C4aEvidence.write(output, "result.json", result);
                transition(Phase.TERMINAL); minecraft.stop(); return;
            }
            if (P11L1ServerHarness.naturalUnload()) {
                require(role.equals("a") && logins == 1 && sends == 1 && casts == 1 && terminalCloseReturned,
                        "UNLOAD_CASE_ORIGINAL_LEAVE_AND_INPUT_COUNTS");
                if (minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null
                        || !P11C4aEvidence.receiptPresent(serverOutput, "natural-unload-result.json")) { return; }
                P11C4aEvidence.write(output, "result.json", Map.of("status", "ORIGINAL_CLIENT_LEAVE_TERMINAL_NOT_SERVER_UNLOAD_PROOF",
                        "logins", logins, "originalKeyCallbackClicks", casts, "originalP9Sends", sends,
                        "originalClientLeaveReturned", true, "closed", true,
                        "playerAbsent", true, "levelAbsent", true, "currentListenerAbsent", true, "physicalOsInputClaim", false));
                transition(Phase.TERMINAL); minecraft.stop(); return;
            }
            if (P11L1ServerHarness.terminalBoundary()) {
                require(logins == 1 && (role.equals("b") ? sends == 0 && casts == 0 : sends == 1 && casts == 1),
                        "TERMINAL_CASE_ACTUAL_INPUT_COUNTS");
                if (minecraft.player != null || minecraft.level != null || minecraft.getConnection() != null
                        || !P11C4aEvidence.receiptPresent(serverOutput, "terminal-boundary-result.json")) { return; }
                P11C4aEvidence.write(output, "result.json", Map.of("status", "ORIGINAL_NATIVE_TERMINAL_CLIENT_ONLY_NOT_SERVER_PROOF",
                        "logins", logins, "originalKeyCallbackClicks", casts, "originalP9Sends", sends,
                        "originalClientLeaveReturned", terminalCloseReturned, "closed", true,
                        "playerAbsent", true, "levelAbsent", true, "currentListenerAbsent", true, "physicalOsInputClaim", false));
                transition(Phase.TERMINAL); minecraft.stop(); return;
            }
            if (P11L1SupplementalHarness.ack()) {
                require(role.equals("a") && logins == 1 && casts == 1 && sends == 1, "ACK_FAULT_ORIGINAL_CLIENT_INPUT");
                if (minecraft.player != null || minecraft.getConnection() != null
                        || !P11C4aEvidence.receiptPresent(serverOutput, "supplemental-result.json")) { return; }
                P11C4aEvidence.write(output, "result.json", Map.of("status", "ORIGINAL_ACK_FAULT_CLIENT_CLOSE_ONLY",
                        "logins", logins, "originalKeyCallbackClicks", casts, "originalP9Sends", sends,
                        "closed", true, "playerAbsent", true, "levelAbsent", minecraft.level == null,
                        "physicalOsInputClaim", false));
                transition(Phase.TERMINAL); minecraft.stop(); return;
            }
            if (twoWorkStop()) {
                require(logins == 1 && (role.equals("b") || sends == 2 && casts == 2), "TWO_WORK_STOP_CLIENT_INPUTS");
                if (minecraft.player != null || minecraft.getConnection() != null
                        || !P11C4aEvidence.receiptPresent(serverOutput, "two-work-result.json")) { return; }
                P11C4aEvidence.write(output, "result.json", Map.of("status", "ORIGINAL_SERVER_STOP_CLIENT_TERMINAL_NOT_SERVER_PROOF",
                        "logins", logins, "originalKeyCallbackClicks", casts, "originalP9Sends", sends,
                        "closed", true, "playerAbsent", true, "levelAbsent", minecraft.level == null,
                        "physicalOsInputClaim", false));
                transition(Phase.TERMINAL); minecraft.stop(); return;
            }
            require(role.equals("a") && logins <= 3 && sends == logins, "UNEXPECTED_NATIVE_CLOSE");
            transition(Phase.WAIT_RECONNECT); return;
        }
        require(minecraft.player != null && minecraft.player.getUUID().equals(identity)
                && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getConnection().getConnection() == connection, "CURRENT_PLAY_IDENTITY");
        if (cue(role + "-finish.ready")) {
            if (!initialDelivered()) { return; }
            if (P11L1RestartProbe.readSelected()) {
                require(logins == 1 && sends == 0 && casts == 0 && !starter, "RESTART_READ_NO_NEW_STARTER_OR_R");
                if (role.equals("a") && (!delivery[0].selectedReward || !delivery[0].bread)) { return; }
            }
            if (P11L1ServerHarness.singleNatural() && role.equals("a")
                    && (!delivery[logins - 1].selectedReward || !delivery[logins - 1].bread)) { return; }
            if (P11L1ServerHarness.multiWorkReward() && role.equals("b")
                    && !delivery[logins - 1].selectedReward) { return; }
            require(P11L1RestartProbe.readSelected() ? logins == 1 && sends == 0 && casts == 0 && !starter
                    : P11L1ServerHarness.capacityWork() ? logins == 1 && sends == 1 && casts == 1 && (role.equals("a") || capacitySwing)
                    : role.equals("b") || (twoWork() ? logins == 1 && sends == 2 && casts == 2
                    : P11L1SupplementalHarness.selected() ? logins == 1 && sends == castLimit() && casts == castLimit()
                    : P11L1ServerHarness.lifecycle() ? logins == (P11C4aEvidence.property("case").equals("l1-work-config") ? 2 : 1) && sends == 1 && casts == 1
                    : P11L1ServerHarness.singleNatural() ? logins == 2 && sends == 1 && casts == 1
                    : P11L1ServerHarness.tracking() ? logins == 1 && sends == 1 && casts == 1
                    : P11L1ServerHarness.onlinePeer() ? logins == 1 && sends == 1 && casts == 1
                    : logins == 4 && sends == 3 && casts == 3), "CLIENT_EPISODES_NOT_COMPLETE");
            nativeCall = true;
            try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }
            finally { nativeCall = false; }
            var result = new java.util.LinkedHashMap<String, Object>(Map.of("status", "NATIVE_L1_CLIENT_FLOW_ONLY", "logins", logins,
                    "originalKeyCallbackClicks", casts, "originalP9Sends", sends, "physicalOsInputClaim", false,
                    "closed", !connection.isConnected(), "playerAbsent", minecraft.player == null, "levelAbsent", minecraft.level == null));
            result.put("nativeDelivery", java.util.Arrays.stream(delivery).limit(logins).map(Delivery::facts).toList());
            P11C4aEvidence.write(output, "result.json", result);
            transition(Phase.TERMINAL); minecraft.stop(); return;
        }
        if (P11L1RestartProbe.readSelected()) {
            require(!starter && casts == 0 && sends == 0 && !cue(role + "-starter.ready")
                    && !cue(role + "-cast-1.ready"), "RESTART_READ_NO_STARTER_OR_CAST_CUE");
            return;
        }
        if (role.equals("a") && P11L1SupplementalHarness.revision()) {
            if (!resourceReload && cue("a-resource-reload.ready") && minecraft.getOverlay() == null) {
                resourceReload = true; P11L1ClientResourceProbe.begin(minecraft, output);
            }
            if (resourceReload && !P11L1ClientResourceProbe.tick(minecraft)) { return; }
        }
        if ((!role.equals("a") && !P11L1ServerHarness.capacityWork()) || minecraft.getOverlay() != null || minecraft.screen != null) { return; }
        if (P11L1ServerHarness.capacityWork() && role.equals("b") && !capacitySwing && cue("b-capacity-swing.ready")) {
            require(logins == 1 && casts == 1 && sends == 1 && initialDelivered(), "CAPACITY_NATIVE_SWING_AFTER_INPUT");
            minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); capacitySwing = true;
            P11C4aEvidence.write(output, "capacity-swing.json", Map.of("status", "ORIGINAL_LOCAL_SWING_RETURN_NOT_SERVER_RECEIPT", "originalSwingReturns", 1));
        }
        if (!starter && cue(role + "-starter.ready")) {
            starter = true; minecraft.getConnection().sendCommand("gramarye starter"); return;
        }
        // A native inactive window keeps P9's flush pending; request focus before waiting for that flush.
        if (P11L1ServerHarness.capacityWork() && starter && initialDelivered() && !capacityFocusRequested
                && cue(role + "-cast-1.ready")) {
            capacityFocusRequested = true;
            GLFW.glfwFocusWindow(minecraft.getWindow().getWindow());
            P11C4aEvidence.write(output, "capacity-focus-request.json", Map.of("status", "OWNED_GLFW_FOCUS_REQUEST_NOT_FOCUS_PROOF"));
            return;
        }
        if (starter && casts < castLimit() && ready && initialDelivered() && minecraft.isWindowActive() && cue(role + "-cast-" + (casts + 1) + ".ready")) {
            require(!armedInput && sends == casts, "P9_INPUT_COUNT_BEFORE_CLICK");
            if (P11L1ServerHarness.lifecycle()) {
                require(!lifecycleArmed, "ONE_LIFECYCLE_CAST");
                P11L1LifecycleClientProbe.start(minecraft, output); lifecycleArmed = true;
            }
            armedInput = true; casts++;
            long window = minecraft.getWindow().getWindow(); int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_PRESS, 0);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_RELEASE, 0);
        }
    }

    private static boolean twoWork() { return P11C4aEvidence.property("case").equals("l1-two-work-reload") || twoWorkStop(); }
    private static boolean twoWorkStop() { return P11C4aEvidence.property("case").equals("l1-two-work-stop"); }
    private static int castLimit() { return P11L1RestartProbe.readSelected() ? 0
            : P11L1RestartProbe.writeSelected() || twoWork() || P11L1SupplementalHarness.revision() ? 2
            : P11L1SupplementalHarness.selected() || P11L1ServerHarness.lifecycle()
                    || P11L1ServerHarness.singleNatural() || P11L1ServerHarness.terminalBoundary()
                    || P11L1ServerHarness.naturalUnload() || P11L1ServerHarness.onlinePeer()
                    || P11L1ServerHarness.tracking() || P11L1ServerHarness.capacityWork() ? 1 : 3; }

    public static void inputReady(boolean observed) { if (P11L1ServerHarness.enabled()) { ready = observed; } }
    public static void loginReturned(net.minecraft.client.multiplayer.ClientPacketListener listener) {
        P11L1LifecycleClientProbe.loginReturned(listener);
        P11L1ContextRefusalClientProbe.loginReturned(listener);
        if (observes(listener)) { delivery[logins - 1].logins++; }
    }
    public static void advancements(net.minecraft.client.multiplayer.ClientPacketListener listener,
            net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket packet) {
        if (!observes(listener)) { return; }
        var observed = delivery[logins - 1];
        if (packet.shouldReset()) { observed.resets++; }
        for (String name : java.util.List.of("l1_first_kill", "l1_late_kill", "l1_partial_kill", "l1_qctx_kill", "l1_qctx_peer")) {
            var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", name);
            var progress = packet.getProgress().get(id);
            if (progress != null && progress.isDone() && listener.getAdvancements().get(id) != null) {
                if (name.equals("l1_first_kill")) { observed.firstReward = true; }
                if (name.equals("l1_late_kill")) { observed.lateReward = true; }
                String selected = P11C4aEvidence.property("case");
                String expected = selected.equals("l1-partial-reward-function") ? "l1_partial_kill"
                        : P11L1ServerHarness.multiWorkReward() ? role.equals("a") ? "l1_qctx_kill" : "l1_qctx_peer" : "l1_first_kill";
                if (name.equals(expected)) { observed.selectedReward = true; }
            }
        }
    }
    public static void recipes(net.minecraft.client.multiplayer.ClientPacketListener listener,
            net.minecraft.network.protocol.game.ClientboundRecipePacket packet) {
        if (!observes(listener)) { return; }
        var observed = delivery[logins - 1];
        if (packet.getState() == net.minecraft.network.protocol.game.ClientboundRecipePacket.State.INIT) { observed.inits++; }
        if (packet.getRecipes().contains(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"))
                && Minecraft.getInstance().player.getRecipeBook().contains(net.minecraft.resources.ResourceLocation.withDefaultNamespace("bread"))) {
            observed.bread = true;
        }
    }
    public static void metadata(boolean mana, long sequence) {
        if (!P11L1ServerHarness.enabled() || phase != Phase.PLAY || logins == 0) { return; }
        if (mana) { delivery[logins - 1].mana = sequence; } else { delivery[logins - 1].cooldown = sequence; }
    }
    private static boolean observes(net.minecraft.client.multiplayer.ClientPacketListener listener) {
        if (!P11L1ServerHarness.enabled() || phase != Phase.PLAY || logins == 0) { return false; }
        var minecraft = Minecraft.getInstance();
        require(minecraft.isSameThread() && listener == minecraft.getConnection() && listener.getConnection() == connection,
                "EXACT_ORIGINAL_DELIVERY_LISTENER"); return true;
    }
    private static boolean initialDelivered() {
        var value = delivery[logins - 1];
        return value.logins == 1 && value.resets > 0 && value.inits > 0 && value.mana > 0 && value.cooldown > 0;
    }
    public static void submitted(long sequence, int slot, int mask, boolean hintsAbsent) {
        if (!P11L1ServerHarness.enabled() || phase == Phase.TERMINAL) { return; }
        require(phase == Phase.PLAY && (role.equals("a") || P11L1ServerHarness.capacityWork() && role.equals("b")) && armedInput && sends + 1 == casts
                && slot == 0 && mask == 0 && hintsAbsent && sequence > 0, "ORIGINAL_R_SLOT_ZERO_SEND");
        sends++; armedInput = false;
        try { P11C4aEvidence.write(output, "cast-" + sends + ".json", Map.of("status", "ORIGINAL_P9_SEND_RETURN", "sequence", sequence,
                "slot", slot, "hintMask", mask, "hintsAbsent", true, "nativeCallbackNotPhysicalOSInput", true)); }
        catch (IOException failure) { fail(Minecraft.getInstance(), "EVIDENCE_IO_FAILURE"); }
    }
    private static boolean cue(String leaf) throws IOException { return P11C4aEvidence.cuePresent(serverOutput, leaf); }
    private static void transition(Phase next) { phase = next; phaseTicks = 0; }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "L1_" + code); }
    private static void fail(Minecraft minecraft, String code) {
        if (phase == Phase.TERMINAL) { return; } transition(Phase.TERMINAL);
        try { if (output != null) { P11C4aEvidence.write(output, "result.json", Map.of("status", "FAIL", "code", code, "logins", logins, "originalP9Sends", sends)); } }
        catch (IOException ignored) { }
        minecraft.stop();
    }
    private static final class Delivery {
        int logins, resets, inits; long mana, cooldown; boolean firstReward, lateReward, selectedReward, bread;
        Map<String, Object> facts() { return Map.of("originalLoginReturns", logins, "originalAdvancementResets", resets,
                "originalRecipeInits", inits, "appliedManaSequence", mana, "appliedCooldownSequence", cooldown,
                "firstRewardProgressApplied", firstReward, "lateRewardProgressApplied", lateReward,
                "selectedRewardProgressApplied", selectedReward, "breadApplied", bread); }
    }
}
