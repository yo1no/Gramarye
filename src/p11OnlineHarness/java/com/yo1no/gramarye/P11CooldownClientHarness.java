package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
import net.minecraft.network.Connection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Excluded original keyboard R callbacks and same-client original reconnect; no intent fabrication. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11CooldownClientHarness {
    private enum Phase { BOOT, WAIT_CONNECT, CONNECTING, PLAY, WAIT_RECONNECT, TERMINAL }
    private static Phase phase = Phase.BOOT;
    private static Path output, serverOutput;
    private static String role, expectedReference, mirroredReference;
    private static UUID identity;
    private static Connection connection;
    private static boolean nativeCall, onboarding, starter, starterCommandIssued, inputReady, inputArmed;
    private static int focusedCast, focusAt, stalledCast;
    private static boolean activeApplied, readyApplied, returnedAfterClose;
    private static boolean readyDrawn, activeDrawn, hudFailed, readyDrawReported, activeDrawReported;
    private static int mirrorClears;
    private static long readyDrawGeneration, readyDrawSequence, activeDrawGeneration, activeDrawSequence;
    private static int activeDrawRemaining;
    private static int ticks, phaseTicks, logins, casts, sends, snapshotChanges;
    private static long latestSequence, latestGeneration;
    private static String latestState = "NONE";
    private P11CooldownClientHarness() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ClientTickEvent.Post ignored) {
        if (!P11CooldownServerHarness.selected() || nativeCall || phase == Phase.TERMINAL) return;
        var minecraft = Minecraft.getInstance();
        try {
            if (serverOutput != null && P11C4aEvidence.cuePresent(serverOutput, "abort.ready")) {
                fail(minecraft, "OWNED_SUPERVISOR_ABORT"); return;
            }
            require(minecraft.isSameThread() && ++ticks <= 24_000 && ++phaseTicks <= 12_000, "FINITE_CLIENT_BOUND");
            require(!hudFailed, "HUD_OR_DISCONNECT_OBSERVER");
            reportDraws();
            switch (phase) {
                case BOOT -> bootstrap(minecraft);
                case WAIT_CONNECT -> { if (cue(role + "-connect.ready")) connect(minecraft); }
                case CONNECTING -> { }
                case WAIT_RECONNECT -> {
                    if (minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null
                            && !connection.isConnected() && cue("a-reconnect.ready")) connect(minecraft);
                }
                case PLAY -> play(minecraft);
                case TERMINAL -> { }
            }
        } catch (Exception | LinkageError problem) { fail(minecraft, P11C4aEvidence.failureCode(problem)); }
    }
    private static void bootstrap(Minecraft minecraft) throws IOException {
        if (output == null) {
            role = P11C4aEvidence.property("role"); require(role.equals("a") || role.equals("b"), "ROLE");
            output = P11C4aEvidence.reserve("client-" + role); serverOutput = P11C4aEvidence.root().resolve("server");
            minecraft.getWindow().setTitle("Gramarye cooldown " + P11C4aEvidence.property("runId") + " " + role);
            P11C4aEvidence.write(output, "bootstrap.json", Map.of("status", "CLIENT_BOOTSTRAP_NOT_AUTH_PROOF",
                    "productionJarSha256", P11OnlineInputs.verifyFrozenJar()));
        }
        require(minecraft.player == null && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null,
                "FRESH_CLIENT");
        if (minecraft.getOverlay() != null) return;
        if (!onboarding && minecraft.screen != null && minecraft.screen.getClass() == AccessibilityOnboardingScreen.class) {
            onboarding = true; nativeCall = true;
            try { minecraft.screen.onClose(); } finally { nativeCall = false; } return;
        }
        if (!(minecraft.screen instanceof TitleScreen)) return;
        minecraft.options.pauseOnLostFocus = false;
        if (P11CooldownRestartProbe.readSelected()) starter = true;
        P11C4aEvidence.write(output, "inputs.json", Map.of("status", "ARMED_NOT_AUTH_PROOF", "role", role,
                "case", P11C4aEvidence.property("case"), "runId", P11C4aEvidence.property("runId")));
        transition(Phase.WAIT_CONNECT);
    }
    private static void connect(Minecraft minecraft) {
        require(minecraft.player == null && minecraft.getConnection() == null, "CONNECT_ONLY_AFTER_NATIVE_CLOSE");
        String address = "127.0.0.1:" + P11C4aEvidence.port();
        transition(Phase.CONNECTING); inputReady = false; mirroredReference = null; latestSequence = 0;
        latestState = "NONE"; nativeCall = true;
        try { ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("P11 cooldown", address, ServerData.Type.OTHER), false, null); }
        finally { nativeCall = false; }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!P11CooldownServerHarness.selected() || phase == Phase.TERMINAL) return;
        var minecraft = Minecraft.getInstance();
        try {
            var actual = event.getConnection(); var player = event.getPlayer();
            require(phase == Phase.CONNECTING && minecraft.isSameThread() && actual != null && player != null
                    && player == minecraft.player && actual == player.connection.getConnection() && actual != connection
                    && actual.isConnected() && actual.isEncrypted() && !actual.isMemoryConnection(), "ORIGINAL_CLIENT_LOGIN");
            require(identity == null || identity.equals(player.getUUID()), "SAME_ACCOUNT_RECONNECT");
            identity = player.getUUID(); connection = actual; logins++;
            require(logins <= (role.equals("a") ? expectedLogins() : 1), "LOGIN_BOUND");
            P11C4aEvidence.write(output, "login-" + logins + ".json", Map.of("status", "ORIGINAL_CLIENT_LOGIN_EVENT",
                    "encrypted", true, "newConnection", true, "sameAccount", true, "ordinal", logins));
            transition(Phase.PLAY);
        } catch (Exception | LinkageError problem) { fail(minecraft, P11C4aEvidence.failureCode(problem)); }
    }
    private static void play(Minecraft minecraft) throws IOException {
        if (P11CooldownFaultProbe.expectedServerStop() && connection != null && !connection.isConnected()) {
            faultStopped(minecraft); return;
        }
        if (P11CooldownRestartProbe.writeSelected() && connection != null && !connection.isConnected()) {
            stoppedByServer(minecraft); return;
        }
        if (P11CooldownCloneProbe.selected() && role.equals("a")) {
            nativeCall = true;
            try { if (!P11CooldownCloneClientProbe.tick(minecraft, connection, output, serverOutput)) return; }
            finally { nativeCall = false; }
        }
        require(connection != null && connection.isConnected() && minecraft.player != null && minecraft.level != null
                && minecraft.player.getUUID().equals(identity) && minecraft.getConnection() == minecraft.player.connection
                && minecraft.getConnection().getConnection() == connection, "CURRENT_NATIVE_PLAY");
        if (P11CooldownDurabilityProbe.selected() && role.equals("a")) {
            P11CooldownDurabilityClientProbe.tick(minecraft, connection, output, serverOutput);
        }
        if (cue(role + "-finish.ready")) {
            require(P11CooldownDualProbe.selected() ? logins == 1 && casts == 2 && sends == 2
                        && starterCommandIssued && readyApplied && readyDrawn && activeApplied && activeDrawn
                    : role.equals("b") ? logins == 1 && casts == 0 && sends == 0
                    : casts == expectedCasts() && sends == casts && logins == expectedLogins()
                        && readyApplied && readyDrawn && (P11CooldownServerHarness.duration() == 1
                            || P11CooldownFaultProbe.selected() && (!P11CooldownFaultProbe.requiresActiveHud() || activeApplied && activeDrawn)
                            || P11CooldownCloneProbe.selected() && P11CooldownCloneClientProbe.complete() && activeApplied && activeDrawn
                            || P11CooldownDurabilityProbe.selected() && P11CooldownDurabilityClientProbe.complete()
                                && (P11CooldownDurabilityProbe.clearSelected() || activeApplied && activeDrawn)
                            || activeApplied && activeDrawn && (P11CooldownRestartProbe.readSelected()
                                || returnedAfterClose && mirrorClears == 1)),
                    "FINAL_INPUT_AND_APPLIED_METADATA");
            leave(minecraft);
            require(!connection.isConnected() && minecraft.player == null && minecraft.level == null
                    && minecraft.getConnection() == null && !hudFailed
                    && mirrorClears == logins, "ACTUAL_CLIENT_TERMINAL");
            P11CooldownCostProbe.sealClient(output);
            P11C4aEvidence.write(output, "hud-result.json", Map.of("status", "ORIGINAL_HUD_DRAW_RETURN_AND_MIRROR_CLEAR",
                    "readyDrawReturned", readyDrawn, "activeDrawReturned", activeDrawn,
                    "originalDisconnectClears", mirrorClears, "pixelOrPhysicalOSClaim", false));
            P11C4aEvidence.write(output, "result.json", Map.of("status", "ORIGINAL_CLIENT_FLOW_MIRROR_AND_NATIVE_HUD_DRAW",
                    "logins", logins, "originalRCallbacks", casts, "originalP9Sends", sends,
                    "readySnapshotApplied", readyApplied, "activeSnapshotApplied", activeApplied,
                    "closed", true, "nativeDrawReturnObserved", castingRole() && readyDrawn,
                    "physicalOsInputClaim", false, "starterCommandIssued", starterCommandIssued));
            transition(Phase.TERMINAL); minecraft.stop(); return;
        }
        if (!castingRole()) return;
        if (cue("a-close.ready") && !returnedAfterClose) {
            if (!activeDrawn) return;
            require(logins == 1 && casts == 2 && sends == 2 && activeApplied && activeDrawn, "ACTIVE_BEFORE_RECONNECT");
            leave(minecraft); returnedAfterClose = true;
            require(!connection.isConnected() && minecraft.player == null && minecraft.level == null
                    && !hudFailed && mirrorClears == 1, "ORIGINAL_LEAVE_RETURN");
            P11C4aEvidence.write(output, "close.json", Map.of("status", "ORIGINAL_CLIENT_LEAVE_RETURN", "closed", true));
            transition(Phase.WAIT_RECONNECT); return;
        }
        if (minecraft.getOverlay() != null || minecraft.screen != null) return;
        if (!starter && cue(castRole() + "-starter.ready")) {
            starter = true; starterCommandIssued = true;
            minecraft.getConnection().sendCommand("gramarye starter"); return;
        }
        String formalLeaf = P11CooldownDualProbe.selected() ? "formal-submission-" + role + ".json" : "formal-submission.json";
        if (expectedReference == null && P11C4aEvidence.receiptPresent(serverOutput, formalLeaf)) {
            var path = serverOutput.resolve(formalLeaf);
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                    && Files.size(path) <= 16_384, "PUBLIC_FORMAL_RECEIPT_BOUND");
            expectedReference = JsonParser.parseString(Files.readString(path)).getAsJsonObject().get("reference").getAsString();
        }
        if (starter && expectedReference != null && casts < expectedCasts()
                && focusedCast < casts + 1 && cue(castRole() + "-cast-" + (casts + 1) + ".ready")) {
            focusedCast = casts + 1; focusAt = phaseTicks;
            P11C4aEvidence.write(output, "input-focus-" + focusedCast + ".json", inputFacts(minecraft,
                    "ACTUAL_INPUT_GATES_BEFORE_OWNED_FOCUS_REQUEST"));
            GLFW.glfwFocusWindow(minecraft.getWindow().getWindow()); return;
        }
        if (casts < focusedCast && phaseTicks - focusAt >= 100 && stalledCast < focusedCast) {
            stalledCast = focusedCast;
            P11C4aEvidence.write(output, "input-stall-" + focusedCast + ".json", inputFacts(minecraft,
                    "ACTUAL_INPUT_GATES_STILL_WAITING_NOT_CAUSAL_ATTRIBUTION"));
        }
        if (starter && inputReady && minecraft.isWindowActive() && expectedReference != null
                && expectedReference.equals(mirroredReference) && cue(castRole() + "-cast-" + (casts + 1) + ".ready")) {
            if (casts == 0 && !(P11CooldownRestartProbe.readSelected() ? activeDrawn : readyDrawn)) return;
            if (P11CooldownServerHarness.reconnectActiveControlSelected() && casts == 2) {
                require(logins == 2 && latestState.equals("ACTIVE"), "NEW_CONNECTION_ACTIVE_BEFORE_ORIGINAL_R");
            }
            require(!inputArmed && sends == casts && casts < expectedCasts(), "EXACT_R_COUNT");
            inputArmed = true; casts++;
            long window = minecraft.getWindow().getWindow(); int scan = GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_R);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_PRESS, 0);
            minecraft.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_R, scan, GLFW.GLFW_RELEASE, 0);
        }
    }
    private static void stoppedByServer(Minecraft minecraft) throws IOException {
        if (!(minecraft.screen instanceof DisconnectedScreen) || minecraft.player != null || minecraft.level != null
                || minecraft.getConnection() != null) return;
        require(!hudFailed && logins == 1 && mirrorClears == 1 && !connection.isConnected()
                && (role.equals("b") ? casts == 0 && sends == 0
                    : casts == 1 && sends == 1 && readyApplied && activeApplied && readyDrawn && activeDrawn && starterCommandIssued),
                "ORIGINAL_STOP_NATIVE_CLIENT_TERMINAL");
        if (!P11C4aEvidence.receiptPresent(serverOutput, "cooldown-restart-expected.json")) return;
        P11CooldownCostProbe.sealClient(output);
        P11C4aEvidence.write(output, "hud-result.json", Map.of("status", "ORIGINAL_HUD_DRAW_RETURN_AND_MIRROR_CLEAR",
                "readyDrawReturned", readyDrawn, "activeDrawReturned", activeDrawn,
                "originalDisconnectClears", mirrorClears, "pixelOrPhysicalOSClaim", false));
        P11C4aEvidence.write(output, "result.json", Map.ofEntries(
                Map.entry("status", "ORIGINAL_SERVER_STOP_CLIENT_TERMINAL"), Map.entry("logins", logins),
                Map.entry("originalKeyCallbackClicks", casts), Map.entry("originalP9Sends", sends),
                Map.entry("closed", true), Map.entry("originalDisconnectedScreen", true),
                Map.entry("playerAbsent", true), Map.entry("levelAbsent", true), Map.entry("currentListenerAbsent", true),
                Map.entry("serverProofIsSeparate", true), Map.entry("physicalOsInputClaim", false)));
        transition(Phase.TERMINAL); minecraft.stop();
    }
    private static int expectedCasts() {
        if (P11CooldownDualProbe.selected()) return 2;
        if (P11CooldownFaultProbe.selected() || P11CooldownDurabilityProbe.selected()) return 1;
        if (P11CooldownCloneProbe.selected()) return 2;
        if (P11CooldownServerHarness.reconnectActiveControlSelected()) return 4;
        return P11CooldownRestartProbe.writeSelected() ? 1
                : P11CooldownRestartProbe.readSelected() || P11CooldownServerHarness.duration() == 1 ? 2 : 3;
    }
    private static Map<String, Object> inputFacts(Minecraft minecraft, String status) {
        return Map.ofEntries(Map.entry("status", status), Map.entry("castCue", focusedCast),
                Map.entry("logins", logins), Map.entry("casts", casts), Map.entry("sends", sends),
                Map.entry("originalInputReady", inputReady), Map.entry("windowActive", minecraft.isWindowActive()),
                Map.entry("screenAbsent", minecraft.screen == null), Map.entry("overlayAbsent", minecraft.getOverlay() == null),
                Map.entry("expectedReferenceMatchesMirror", expectedReference != null && expectedReference.equals(mirroredReference)),
                Map.entry("readyDrawReturned", readyDrawn), Map.entry("phaseTicks", phaseTicks));
    }
    private static int expectedLogins() {
        if (P11CooldownDualProbe.selected()) return 1;
        if (P11CooldownFaultProbe.selected() || P11CooldownCloneProbe.selected() || P11CooldownDurabilityProbe.selected()) return 1;
        return P11CooldownRestartProbe.writeSelected() || P11CooldownRestartProbe.readSelected()
                || P11CooldownServerHarness.duration() == 1 ? 1 : 2;
    }

    private static void faultStopped(Minecraft minecraft) throws IOException {
        if (!(minecraft.screen instanceof DisconnectedScreen) || minecraft.player != null || minecraft.level != null
                || minecraft.getConnection() != null) return;
        require(!hudFailed && logins == 1 && mirrorClears == 1 && !connection.isConnected()
                && (role.equals("b") ? casts == 0 && sends == 0
                    : casts == 1 && sends == 1 && readyApplied && readyDrawn && starterCommandIssued),
                "ORIGINAL_FAULT_STOP_CLIENT_TERMINAL");
        if (!P11C4aEvidence.receiptPresent(serverOutput, "cooldown-fault-result.json")) return;
        P11CooldownCostProbe.sealClient(output);
        P11C4aEvidence.write(output, "result.json", Map.of("status", P11CooldownFaultProbe.expectedNativeFault()
                ? "ORIGINAL_NATIVE_FAULT_CLIENT_TERMINAL_NOT_SERVER_PROOF" : "ORIGINAL_UNARMED_STOP_CLIENT_TERMINAL_NOT_SERVER_PROOF",
                "logins", logins, "originalRCallbacks", casts, "originalP9Sends", sends, "closed", true,
                "originalDisconnectedScreen", true, "playerAbsent", true, "levelAbsent", true,
                "currentListenerAbsent", true, "physicalOsInputClaim", false));
        transition(Phase.TERMINAL); minecraft.stop();
    }
    public static void inputReady(boolean ready) {
        if (P11CooldownServerHarness.selected()) inputReady = ready;
    }
    /** Scalar-only inside the renderer; receipts are written from the ordinary client Post. */
    public static void hudDrawn(boolean exactLabel, long generation, long sequence, String reference,
            String state, int remaining) {
        if (!P11CooldownServerHarness.selected() || phase != Phase.PLAY || !castingRole()
                || expectedReference == null || !expectedReference.equals(reference)) return;
        if (!exactLabel || generation != latestGeneration || sequence != latestSequence || remaining < 0) {
            hudFailed = true; return;
        }
        P11CooldownCostProbe.clientDrawReturned(generation, sequence);
        if (P11CooldownDurabilityProbe.selected()) {
            P11CooldownDurabilityClientProbe.hudDrawn(exactLabel, generation, sequence, reference, state, remaining);
        }
        if (state.equals("READY") && !readyDrawn) {
            readyDrawn = true; readyDrawGeneration = generation; readyDrawSequence = sequence;
        }
        if (state.equals("ACTIVE") && remaining > 0 && !activeDrawn) {
            activeDrawn = true; activeDrawGeneration = generation; activeDrawSequence = sequence;
            activeDrawRemaining = remaining;
        }
    }
    public static void hudObservationFailed() { if (P11CooldownServerHarness.selected()) hudFailed = true; }
    public static void mirrorDisconnected(long generation, boolean actuallyEmpty) {
        if (!P11CooldownServerHarness.selected() || phase != Phase.PLAY || connection == null) return;
        if (!actuallyEmpty || generation == latestGeneration || mirrorClears >= logins) { hudFailed = true; return; }
        mirrorClears++;
    }
    private static void reportDraws() throws IOException {
        if (output == null) return;
        if (readyDrawn && !readyDrawReported) {
            readyDrawReported = true;
            P11C4aEvidence.write(output, "hud-ready.json", Map.of("status", "ORIGINAL_HUD_DRAW_RETURN",
                    "state", "READY", "generation", readyDrawGeneration, "sequence", readyDrawSequence,
                    "pixelOrPhysicalOSClaim", false));
        }
        if (activeDrawn && !activeDrawReported) {
            activeDrawReported = true;
            P11C4aEvidence.write(output, "hud-active.json", Map.of("status", "ORIGINAL_HUD_DRAW_RETURN",
                    "state", "ACTIVE", "generation", activeDrawGeneration, "sequence", activeDrawSequence,
                    "remainingTicks", activeDrawRemaining, "pixelOrPhysicalOSClaim", false));
        }
    }
    public static void submitted(long sequence, int slot, int mask, boolean hintsAbsent) {
        if (!P11CooldownServerHarness.selected() || phase == Phase.TERMINAL) return;
        try {
            require(phase == Phase.PLAY && castingRole() && inputArmed && sends + 1 == casts
                    && sequence > 0 && slot == 0 && mask == 0 && hintsAbsent, "ORIGINAL_R_SLOT_ZERO_PAYLOAD");
            if (P11CooldownServerHarness.reconnectActiveControlSelected()) {
                require(logins == (sends < 2 ? 1 : 2) && sequence == sends % 2 + 1,
                        "ORIGINAL_NEW_CONNECTION_SEQUENCE_ONE_THEN_TWO");
            }
            sends++; inputArmed = false;
            P11C4aEvidence.write(output, "cast-" + sends + ".json", Map.of("status", "ORIGINAL_P9_SEND_RETURN",
                    "sequence", sequence, "slot", slot, "hintsAbsent", true, "mirrorStateAtSend", latestState,
                    "loginOrdinal", logins, "nativeCallbackNotPhysicalOSInput", true));
        } catch (Exception | LinkageError problem) { fail(Minecraft.getInstance(), P11C4aEvidence.failureCode(problem)); }
    }
    /** Called only after the exact real P7 mirror accepted this same snapshot/generation. */
    public static void snapshot(long generation, long sequence, long epoch, long version, String sourceState,
            String sourceReason, String reference, String state, String reason, int remaining) {
        if (!P11CooldownServerHarness.selected() || phase != Phase.PLAY || !castingRole()) return;
        try {
            require(Minecraft.getInstance().isSameThread() && generation > 0 && sequence > latestSequence,
                    "ACTUAL_CURRENT_MIRROR_SEQUENCE");
            latestSequence = sequence; latestGeneration = generation; mirroredReference = reference;
            P11CooldownCostProbe.clientApplied(generation, sequence);
            if (P11CooldownDurabilityProbe.selected()) {
                P11CooldownDurabilityClientProbe.snapshot(generation, sequence, epoch, version, sourceState,
                        sourceReason, reference, state, reason, remaining);
            }
            if (P11CooldownCloneProbe.selected()) {
                P11CooldownCloneClientProbe.snapshot(generation, sequence, epoch, version, sourceState, reference, state, remaining);
            }
            if (!state.equals(latestState) && reference != null) {
                require(++snapshotChanges <= 16, "BOUNDED_METADATA_TRANSITIONS");
                latestState = state;
                if (sourceState.equals("AVAILABLE") && state.equals("READY")) readyApplied = true;
                if (sourceState.equals("AVAILABLE") && state.equals("ACTIVE")) activeApplied = true;
                P11C4aEvidence.write(output, "cooldown-mirror-" + snapshotChanges + ".json", Map.of(
                        "status", "ORIGINAL_MIRROR_APPLIED_NOT_RENDER", "generation", latestGeneration,
                        "sequence", sequence, "sourceEpoch", epoch, "sourceVersion", version,
                        "sourceState", sourceState, "sourceReason", sourceReason,
                        "entry", Map.of("reference", reference, "state", state, "reason", reason, "remainingTicks", remaining),
                        "loginOrdinal", logins, "renderClaim", false));
            }
        } catch (Exception | LinkageError problem) { fail(Minecraft.getInstance(), P11C4aEvidence.failureCode(problem)); }
    }
    private static boolean castingRole() {
        return "a".equals(role) || P11CooldownDualProbe.selected() && "b".equals(role);
    }
    private static String castRole() { return P11CooldownDualProbe.selected() ? role : "a"; }
    private static void leave(Minecraft minecraft) {
        nativeCall = true;
        try { minecraft.level.disconnect(); minecraft.disconnect(new TitleScreen()); }
        finally { nativeCall = false; }
    }
    private static boolean cue(String leaf) throws IOException { return P11C4aEvidence.cuePresent(serverOutput, leaf); }
    private static void transition(Phase next) { phase = next; phaseTicks = 0; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_CLIENT_" + code); }
    private static void fail(Minecraft minecraft, String code) {
        if (phase == Phase.TERMINAL) return;
        transition(Phase.TERMINAL);
        try { if (output != null) P11C4aEvidence.write(output, "result.json", Map.of("status", "FAIL", "code", code,
                "logins", logins, "casts", casts, "sends", sends)); } catch (IOException ignored) { }
        minecraft.stop();
    }
}
