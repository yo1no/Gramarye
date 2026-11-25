package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Excluded local dedicated-server client driver. Authentication belongs entirely to the
 * original connector/login implementation; this observer never reads User or account data.
 * Empty server cue files coordinate the owned processes and are not acceptance evidence.
 */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11OnlineClientHarness {
    private static final String PREFIX = "gramarye.p11.online.";
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath(
            "gramarye_p11_engineering", "delivery_root");
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static final int PHASE_DEADLINE_TICKS = 2_400;
    // Only before the first connection: allow the other local account holder's authorization.
    // No native PLAY/configuration operation or gameplay deadline is extended by this wait.
    private static final int CONNECT_BARRIER_DEADLINE_TICKS = 24_000;
    private static final Observation FIRST = new Observation();
    private static final Observation SECOND = new Observation();
    private static Phase phase = Phase.BOOTSTRAP;
    private static Path output;
    private static Path serverOutput;
    private static String role;
    private static String caseName;
    private static String runId;
    private static String productionJarSha256;
    private static String pseudonym;
    private static int port;
    private static int ticks;
    private static int logins;
    private static int logouts;
    private static int nullLogoutEvents;
    private static UUID playerId;
    private static LocalPlayer currentPlayer;
    private static ClientPacketListener currentListener;
    private static Connection currentConnection;
    private static ClientPacketListener retiredListener;
    private static Connection retiredConnection;
    private static boolean nativeCall;
    private static boolean terminal;
    private static boolean newConnectionObserved;
    private static boolean newListenerObserved;
    private static boolean onboardingContinueStarted;
    private static boolean onboardingContinueReturned;
    private static String observationFailure;
    private static boolean capacityClickStarted;
    private static boolean capacityClickReturned;
    private static boolean capacityRespawnComplete;
    private static int capacityCloneEvents;
    private static int capacityFrameReturns;
    private static long capacityScene;
    private static long capacityEpoch;
    private static long capacityOriginalGeneration;
    private static boolean u0TraceFlushAttempted;
    private static boolean u0TraceFlushSucceeded;
    private static boolean u0ExitGameRequested;
    private static boolean u0LogoutPauseOpened, u0LogoutButtonIssued;
    private static boolean u0LifecycleFirstTryIssued, u0LifecycleRetryIssued;
    private static int u0LifecycleCloneCount, u0LifecycleRespawnReturns;

    private P11OnlineClientHarness() {}

    private static boolean u0LateFinishEligible(boolean terminalScreen, boolean detached, int actualLogouts,
            boolean attempted, boolean finishCue) {
        return terminalScreen && detached && actualLogouts > 0 && !attempted && finishCue;
    }

    private static void u0FlushTrace() throws IOException, URISyntaxException {
        if (u0TraceFlushAttempted || output == null) return;
        u0TraceFlushAttempted = true;
        Throwable primary = null;
        try {
            P11C4aEvidence.write(output, "loaded-sources-terminal.json", P11OnlineServerHarness.u0LoadedSources());
        } catch (IOException | URISyntaxException | RuntimeException | Error observationFailure) {
            primary = observationFailure;
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error unavailableRecorder) { }
            throw observationFailure;
        } finally {
            try { U0OrdinaryTrace.write(output); }
            catch (IOException | RuntimeException | Error traceFailure) {
                if (primary == null) { throw traceFailure; }
            }
        }
        u0TraceFlushSucceeded = true; // Both original sinks returned; not an observer/row acceptance claim.
    }

    private static boolean u0ExitGameEligible(boolean terminalScreen, boolean detached, int actualLogouts,
            boolean flushSucceeded, boolean requested, boolean exitCue) {
        return terminalScreen && detached && actualLogouts > 0 && flushSucceeded && !requested && exitCue;
    }

    private static boolean u0PrepareExitGame(boolean terminalScreen, boolean detached) throws IOException {
        if (!terminalScreen || !detached || logouts <= 0 || !u0TraceFlushSucceeded || u0ExitGameRequested) return false;
        if (!u0ExitGameEligible(terminalScreen, detached, logouts, u0TraceFlushSucceeded, u0ExitGameRequested,
                P11C4aEvidence.cuePresent(serverOutput, role + "-exit-game.ready"))) return false;
        u0ExitGameRequested = true; // Before exclusive output/native stop; failures cannot retry this request.
        P11C4aEvidence.write(output, "native-exit-request.json", Map.of("format", "U0_NATIVE_EXIT_REQUEST_V1",
                "status", "ORIGINAL_STOP_REQUESTED_NOT_PROCESS_EXIT_PROOF", "role", role,
                "matchedNativeLogouts", logouts, "nativeTerminalScreen", terminalScreen,
                "fullyDetached", detached, "traceFlushCompleted", true, "nativeProcessExitProved", false));
        return true;
    }

    /** Closed qualification choice, joined only to this client's already-observed public player identity. */
    private static boolean u0LogoutSelected(boolean requireCurrent) throws IOException {
        if (playerId == null || requireCurrent && (currentPlayer == null || currentConnection == null
                || currentPlayer.getUUID() != playerId && !currentPlayer.getUUID().equals(playerId))) return false;
        var file = P11C4aEvidence.root().resolve("scenario/logout-selected.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) < 1 || Files.size(file) > 2048) throw new IOException("U0_LOGOUT_SELECTION_BOUND");
        var value = JsonParser.parseString(Files.readString(file, StandardCharsets.US_ASCII)).getAsJsonObject();
        if (!value.keySet().equals(java.util.Set.of("format", "pseudonym", "mode"))
                || !value.get("format").getAsString().equals("U0_LOGOUT_SELECTED_V1")
                || !java.util.Set.of("NORMAL", "CLEANUP_FAULT").contains(value.get("mode").getAsString()))
            throw new IOException("U0_LOGOUT_SELECTION_SCHEMA");
        var pseudonym = value.get("pseudonym").getAsString();
        if (!pseudonym.matches("[0-9a-f]{64}")) throw new IOException("U0_LOGOUT_SELECTION_DOMAIN");
        return pseudonym.equals(P11C4aEvidence.pseudonym(playerId));
    }

    private static Button u0PrepareLogoutButton(Minecraft minecraft) throws IOException {
        if (u0LogoutButtonIssued || minecraft.getOverlay() != null || !(minecraft.screen instanceof PauseScreen)
                || currentPlayer == null || minecraft.player != currentPlayer || currentConnection == null
                || !currentConnection.isConnected() || minecraft.getConnection() != currentListener
                || currentListener == null || currentListener.getConnection() != currentConnection
                || currentConnection.getPacketListener() != currentListener || !u0LogoutSelected(true)) return null;
        Button selected = null;
        for (var child : minecraft.screen.children()) {
            if (child instanceof Button button && button.active && button.visible
                    && button.getMessage().getContents() instanceof TranslatableContents key
                    && key.getKey().equals("menu.disconnect")) {
                if (selected != null) throw new IllegalStateException("U0_LOGOUT_AMBIGUOUS_NATIVE_BUTTON");
                selected = button;
            }
        }
        if (selected == null) return null;
        u0LogoutButtonIssued = true;
        P11C4aEvidence.write(output, "logout-native-gui-request.json", Map.of("format", "U0_LOGOUT_GUI_REQUEST_V1",
                "originalPauseScreen", true, "originalButtonKey", "menu.disconnect", "physicalOsInputClaim", false,
                "actualServerLogoutProof", false));
        return selected; // Original callback executes outside the observer catch, including native report dialogs.
    }

    /** Read only this run's bounded closed selection record; never account/profile/authentication data. */
    private static boolean u0LifecycleSelected(Minecraft minecraft) throws IOException {
        if (currentPlayer == null || minecraft.player != currentPlayer || currentConnection == null
                || currentListener == null || minecraft.getConnection() != currentListener
                || currentPlayer.connection != currentListener || currentListener.getConnection() != currentConnection
                || currentConnection.getPacketListener() != currentListener || !currentConnection.isConnected()
                || currentConnection.isMemoryConnection() || !currentConnection.isEncrypted()) return false;
        var directory = P11C4aEvidence.root().resolve("scenario");
        if (!P11C4aEvidence.cuePresent(directory, "lifecycle-respawn.ready")) return false;
        var file = directory.resolve("lifecycle-selected.json");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) < 1 || Files.size(file) > 2048) throw new IOException("U0_LIFECYCLE_SELECTION_BOUND");
        var selected = JsonParser.parseString(Files.readString(file, StandardCharsets.US_ASCII)).getAsJsonObject();
        if (!selected.keySet().equals(java.util.Set.of("format", "pseudonym"))
                || !selected.get("format").getAsString().equals("U0_LIFECYCLE_SELECTED_V1"))
            throw new IOException("U0_LIFECYCLE_SELECTION_SCHEMA");
        String who = selected.get("pseudonym").getAsString();
        if (!who.matches("[0-9a-f]{64}")) throw new IOException("U0_LIFECYCLE_SELECTION_DOMAIN");
        return who.equals(P11C4aEvidence.pseudonym(currentPlayer.getUUID()));
    }

    private static Button u0PrepareLifecycleButton(Minecraft minecraft) throws IOException {
        if (minecraft.getOverlay() != null || !u0LifecycleSelected(minecraft)) return null;
        var view = P11ClientTransitions.view();
        String key;
        boolean retry;
        if (!u0LifecycleFirstTryIssued && minecraft.screen instanceof DeathScreen && currentPlayer.isDeadOrDying()
                && view != null && view.kind() == P11TransitionProtocol.Kind.DEATH
                && view.scope() == P11TransitionProtocol.Scope.PLAY
                && view.outcome() == P11TransitionProtocol.Outcome.BINDING) {
            key = "deathScreen.respawn"; retry = false;
        } else if (u0LifecycleFirstTryIssued && !u0LifecycleRetryIssued
                && minecraft.screen instanceof P11ClientTransitionScreen && view != null
                && view.kind() == P11TransitionProtocol.Kind.DEATH && P11ClientTransitions.retryArmed()) {
            key = "screen.gramarye.transition.retry"; retry = true;
        } else { return null; }
        Button selected = null;
        for (var child : minecraft.screen.children()) {
            if (child instanceof Button button && button.visible && button.active
                    && button.getMessage().getContents() instanceof TranslatableContents text && text.getKey().equals(key)) {
                if (selected != null) throw new IllegalStateException("U0_LIFECYCLE_AMBIGUOUS_NATIVE_BUTTON");
                selected = button;
            }
        }
        if (selected == null) return null;
        if (retry) u0LifecycleRetryIssued = true; else u0LifecycleFirstTryIssued = true;
        P11C4aEvidence.write(output, retry ? "lifecycle-retry-request.json" : "lifecycle-first-try-request.json",
                Map.of("format", "U0_LIFECYCLE_GUI_CALLBACK_REQUEST_V1", "callbackKey", key,
                        "nativeControllerOutcomeBefore", view.outcome().name(), "nativeControllerReasonBefore", view.reason().name(),
                        "physicalOsInputClaim", false, "actualTryPermitOrRespawnProved", false));
        return selected; // Invoke the original Button.onPress only after leaving every observation catch.
    }

    private static boolean enabled() {
        return System.getProperty(PREFIX + "output") != null && P11OnlineInputs.onlineContextCase();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ClientTickEvent.Post ignored) {
        if ("u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) {
            int nativeConnect = 0;
            boolean nativeExit = false;
            Button nativeLifecycleButton = null;
            Button nativeLogoutButton = null;
            boolean nativeLogoutPause = false;
            var minecraft = Minecraft.getInstance();
            try {
                if (output == null) {
                    role = P11C4aEvidence.property("role");
                    if (!(role.equals("a") || role.equals("b"))) { throw new IllegalStateException("U0_ROLE"); }
                    port = P11C4aEvidence.port();
                    output = P11C4aEvidence.reserve("client-" + role);
                    serverOutput = P11C4aEvidence.root().resolve("server");
                    P11C4aEvidence.write(output, "loaded-sources-ready.json", P11OnlineServerHarness.u0LoadedSources());
                    P11C4aEvidence.write(output, "inputs.json", Map.of("status", "ORDINARY_CLIENT_PREPARED_NOT_AUTH_PROOF",
                            "role", role, "runId", P11C4aEvidence.property("runId")));
                }
                if (minecraft.level != null) { U0OrdinaryTrace.nativeTick(minecraft.level.getGameTime()); }
                boolean terminalScreen = minecraft.screen instanceof TitleScreen
                        || minecraft.screen instanceof DisconnectedScreen;
                boolean detached = !nativeCall && minecraft.getOverlay() == null
                        && minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null
                        && minecraft.getSingleplayerServer() == null;
                if (u0LateFinishEligible(terminalScreen, detached, logouts, u0TraceFlushAttempted,
                        logouts > 0 && !u0TraceFlushAttempted
                                && P11C4aEvidence.cuePresent(serverOutput, role + "-finish.ready"))) {
                    u0FlushTrace();
                }
                if (!nativeCall && minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen
                        && minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null
                        && minecraft.getSingleplayerServer() == null) {
                    if (!u0TraceFlushAttempted && logins == 0 && phase == Phase.BOOTSTRAP
                            && P11C4aEvidence.cuePresent(serverOutput, role + "-connect.ready")) {
                        nativeConnect = 1;
                    } else if (!u0TraceFlushAttempted && logins == 1 && logouts == 1 && phase == Phase.FIRST_DISCONNECTED
                            && P11C4aEvidence.cuePresent(serverOutput, role + "-reconnect.ready")) {
                        nativeConnect = 2;
                    }
                }
                // Reconnect only this selected, already-logged-out genuine account, using original connector.
                if (nativeConnect == 0 && !nativeExit && detached && !u0TraceFlushAttempted
                        && logins == 1 && logouts == 1 && phase == Phase.FIRST_DISCONNECTED
                        && (terminalScreen || minecraft.screen instanceof JoinMultiplayerScreen)
                        && u0LogoutSelected(false)
                        && P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("scenario"), "logout-reconnect.ready")) {
                    nativeConnect = 2;
                }
                nativeExit = u0PrepareExitGame(terminalScreen, detached);
                if (!nativeExit && nativeConnect == 0) {
                    if (u0LogoutSelected(true)
                            && P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("scenario"), "logout-disconnect.ready")) {
                        if (!u0LogoutPauseOpened && minecraft.screen == null && minecraft.getOverlay() == null) {
                            u0LogoutPauseOpened = true; nativeLogoutPause = true;
                        } else { nativeLogoutButton = u0PrepareLogoutButton(minecraft); }
                    }
                    if (nativeLogoutButton == null && !nativeLogoutPause) nativeLifecycleButton = u0PrepareLifecycleButton(minecraft);
                }
            } catch (Exception | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
                catch (RuntimeException | Error unavailableRecorder) { }
            }
            // This is a real native UI operation, not an observer: preserve its original Throwable.
            if (nativeExit) { minecraft.stop(); return; }
            if (nativeLogoutPause) { minecraft.setScreen(new PauseScreen(true)); return; }
            if (nativeLogoutButton != null) { nativeLogoutButton.onPress(); return; }
            if (nativeLifecycleButton != null) {
                nativeLifecycleButton.onPress(); // Original native callback Throwable remains outside the observer shield.
                try {
                    var actual = P11ClientTransitions.view();
                    if (actual == null) throw new IllegalStateException("U0_LIFECYCLE_CALLBACK_STATE_MISSING");
                    P11C4aEvidence.write(output, u0LifecycleRetryIssued
                            ? "lifecycle-retry-return.json" : "lifecycle-first-try-return.json",
                            Map.of("format", "U0_LIFECYCLE_GUI_CALLBACK_RETURN_V1",
                                    "status", "CALLBACK_RETURN_AND_CLIENT_STATE_NOT_SERVER_PERMIT",
                                    "connectionEpoch", actual.connectionEpoch(), "sceneSerial", actual.sceneSerial(),
                                    "requestSeq", actual.requestSeq(), "actorGeneration", actual.actorGeneration(),
                                    "outcome", actual.outcome().name(), "reason", actual.reason().name(),
                                    "physicalOsInputClaim", false));
                } catch (Exception | Error observationFailure) {
                    try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
                    catch (RuntimeException | Error unavailableRecorder) { }
                }
                return;
            }
            if (nativeConnect != 0) { connect(minecraft, nativeConnect == 2); }
            return;
        }
        if (!enabled() || terminal || nativeCall) { return; }
        var minecraft = Minecraft.getInstance();
        try {
            check(minecraft.isSameThread(), "NOT_CLIENT_THREAD");
            check(observationFailure == null, observationFailure == null ? "OBSERVATION_FAILURE" : observationFailure);
            int deadline = phase == Phase.WAIT_CONNECT ? CONNECT_BARRIER_DEADLINE_TICKS : PHASE_DEADLINE_TICKS;
            check(++ticks <= deadline, "PHASE_DEADLINE");
            if (phase != Phase.BOOTSTRAP && minecraft.screen instanceof DisconnectedScreen) {
                throw new ObservationFault("UNEXPECTED_NATIVE_DISCONNECT");
            }
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case WAIT_CONNECT -> { if (cue("connect")) { connect(minecraft, false); } }
                case FIRST_CONNECT, SECOND_CONNECT -> { }
                case FIRST_PLAY -> firstPlay(minecraft);
                case FIRST_DISCONNECTED -> reconnectAfterServerRemoval(minecraft);
                case SECOND_PLAY -> secondPlay(minecraft);
                case FINAL_DISCONNECTED -> complete(minecraft);
                case TERMINAL -> { }
            }
        } catch (ObservationFault failure) {
            fail(minecraft, failure.code);
        } catch (RuntimeException | Error failure) {
            // Do not serialize arbitrary exceptions, screen text, launch arguments or profiles.
            fail(minecraft, "UNEXPECTED_CLIENT_HARNESS_FAILURE");
        }
    }

    private static void bootstrap(Minecraft minecraft) {
        if (output == null) { initializeBootstrap(minecraft); }
        check(minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null
                && minecraft.getSingleplayerServer() == null, "CLIENT_NOT_FRESH");
        if (minecraft.getOverlay() != null) { return; }
        if (minecraft.screen != null && minecraft.screen.getClass() == AccessibilityOnboardingScreen.class) {
            if (onboardingContinueStarted) { return; }
            var before = screenObservation(minecraft);
            onboardingContinueStarted = true;
            nativeCall = true;
            try {
                // The native Continue button calls this same method. Preserve its original
                // options update, narrator cleanup and remaining initial-screen continuation.
                ((AccessibilityOnboardingScreen) minecraft.screen).onClose();
                onboardingContinueReturned = true;
            } finally { nativeCall = false; }
            var continued = summary("NATIVE_ACCESSIBILITY_ONBOARDING_CONTINUE_RETURNED");
            continued.add("before", before);
            continued.add("after", screenObservation(minecraft));
            write("onboarding-continued.json", continued);
            return;
        }
        // Notices, eligibility screens and all unknown screens remain under native control.
        if (!(minecraft.screen instanceof TitleScreen)) { return; }
        transition(Phase.WAIT_CONNECT);
        var inputs = summary("ARMED");
        inputs.addProperty("endpoint", "LOOPBACK_DEDICATED_TCP");
        inputs.addProperty("authentication", "ORIGINAL_CONNECTOR_ONLY_SERVER_PROVENANCE_REQUIRED");
        inputs.addProperty("cueFilesAreEvidence", false);
        inputs.addProperty("productionJarSha256", productionJarSha256);
        inputs.addProperty("connectBarrierDeadlineTicks", CONNECT_BARRIER_DEADLINE_TICKS);
        inputs.addProperty("postConnectPhaseDeadlineTicks", PHASE_DEADLINE_TICKS);
        write("inputs.json", inputs);
    }

    private static void initializeBootstrap(Minecraft minecraft) {
        role = property("role");
        caseName = property("case");
        runId = property("runId");
        check(role.equals("single") || role.equals("a") || role.equals("b"), "INVALID_ROLE");
        check(P11OnlineInputs.onlineContextCase(), "INVALID_CASE");
        check(P11OnlineInputs.semanticOnlineCase().equals("single") == role.equals("single"), "CASE_ROLE_MISMATCH");
        check(runId.matches("[A-Za-z0-9_-]{1,80}"), "INVALID_RUN_ID");
        check(property("host").equals("127.0.0.1"), "NON_LOOPBACK_ENDPOINT");
        var portText = property("port");
        check(portText.matches("[1-9][0-9]{0,4}"), "INVALID_PORT");
        port = Integer.parseInt(portText);
        check(port <= 65535, "INVALID_PORT");
        try {
            var root = Path.of(property("output")).toAbsolutePath().normalize();
            check(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root), "INVALID_OUTPUT_ROOT");
            root = root.toRealPath();
            check(!root.equals(minecraft.gameDirectory.toPath().toRealPath()), "OUTPUT_IS_GAME_DIRECTORY");
            serverOutput = root.resolve("server");
            check(Files.isDirectory(serverOutput, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isSymbolicLink(serverOutput), "INVALID_SERVER_OUTPUT");
            var reserved = root.resolve("client-" + role);
            Files.createDirectory(reserved);
            output = reserved;
        } catch (IOException failure) { throw new ObservationFault("OUTPUT_RESERVATION_FAILED"); }
        try { productionJarSha256 = P11OnlineInputs.verifyFrozenJar(); }
        catch (IOException failure) { throw new ObservationFault("FROZEN_JAR_INPUT_FAILED"); }
        var bootstrap = summary("INITIAL_CLIENT_TICK_OBSERVED_NOT_ARMED");
        bootstrap.addProperty("productionJarSha256", productionJarSha256);
        bootstrap.addProperty("bootstrapDeadlineTicks", PHASE_DEADLINE_TICKS);
        bootstrap.add("screen", screenObservation(minecraft));
        write("bootstrap.json", bootstrap);
    }

    private static JsonObject screenObservation(Minecraft minecraft) {
        var json = new JsonObject();
        var screen = minecraft.screen;
        var code = screen == null ? "NONE"
                : screen.getClass() == AccessibilityOnboardingScreen.class ? "ACCESSIBILITY_ONBOARDING"
                : screen instanceof TitleScreen ? "TITLE" : "OTHER_NATIVE_OR_MOD_SCREEN";
        json.addProperty("code", code);
        json.addProperty("class", screen == null ? null : screen.getClass().getName());
        json.addProperty("overlayPresent", minecraft.getOverlay() != null);
        return json;
    }

    private static void connect(Minecraft minecraft, boolean second) {
        check(minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null
                && minecraft.getSingleplayerServer() == null, "CONNECT_WITH_LIVE_CLIENT_STATE");
        transition(second ? Phase.SECOND_CONNECT : Phase.FIRST_CONNECT);
        var address = "127.0.0.1:" + port;
        var data = new ServerData("P11 owned online qualification", address, ServerData.Type.OTHER);
        nativeCall = true;
        try {
            ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address), data, false, null);
        } finally { nativeCall = false; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if ("u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) {
            try {
                var minecraft = Minecraft.getInstance(); var player = event.getPlayer(); var connection = event.getConnection();
                boolean exact = minecraft.isSameThread() && minecraft.getSingleplayerServer() == null
                        && player != null && connection != null && !connection.isMemoryConnection()
                        && connection.isConnected() && connection.isEncrypted() && minecraft.player == player
                        && minecraft.getConnection() == player.connection && player.connection.getConnection() == connection
                        && connection.getPacketListener() == player.connection
                        && (playerId == null || playerId.equals(player.getUUID()));
                if (exact) {
                    playerId = player.getUUID(); currentPlayer = player;
                    currentListener = player.connection; currentConnection = connection; logins++;
                    phase = Phase.FIRST_PLAY;
                }
                U0OrdinaryTrace.note(exact ? "CLIENT_NATIVE_LOGIN" : "OBSERVATION_FAILURE",
                        logins, 0, 0, 0, 0, 0, exact ? 1 : 0, 0, 0,
                        player == null ? "" : P11C4aEvidence.pseudonym(player.getUUID()));
            } catch (RuntimeException | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
                catch (RuntimeException | Error unavailableRecorder) { }
            }
            return;
        }
        if (!enabled() || terminal) { return; }
        try {
            var minecraft = Minecraft.getInstance();
            var player = event.getPlayer();
            var connection = event.getConnection();
            check(minecraft.isSameThread() && minecraft.getSingleplayerServer() == null
                    && player != null && connection != null && !connection.isMemoryConnection()
                    && connection.isConnected() && minecraft.player == player
                    && minecraft.getConnection() == player.connection
                    && player.connection.getConnection() == connection
                    && connection.getPacketListener() == player.connection,
                    "LOGIN_NOT_EXACT_REMOTE_PLAY");
            boolean second = phase == Phase.SECOND_CONNECT;
            check((phase == Phase.FIRST_CONNECT || second) && currentPlayer == null
                    && logins == logouts && logins == (second ? 1 : 0), "UNEXPECTED_LOGIN");
            if (second) {
                check(player.getUUID().equals(playerId) && retiredConnection != null
                        && !retiredConnection.isConnected() && connection != retiredConnection
                        && player.connection != retiredListener, "RECONNECT_IDENTITY_MISMATCH");
                newConnectionObserved = true;
                newListenerObserved = true;
                retiredConnection = null;
                retiredListener = null;
            } else {
                playerId = player.getUUID();
                pseudonym = pseudonym(runId, playerId);
            }
            currentPlayer = player;
            currentListener = player.connection;
            currentConnection = connection;
            logins++;
            transition(second ? Phase.SECOND_PLAY : Phase.FIRST_PLAY);
            var login = summary("NATIVE_PLAY_LOGIN_EVENT");
            login.addProperty("memoryConnection", false);
            login.addProperty("encryptedConnection", connection.isEncrypted());
            login.addProperty("loginEventIsFullHandlerCompletion", false);
            write(second ? "login-2.json" : "login-1.json", login);
        } catch (ObservationFault failure) { recordFailure(failure.code); }
        catch (RuntimeException | Error failure) { recordFailure("LOGIN_OBSERVATION_FAILURE"); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void cloned(ClientPlayerNetworkEvent.Clone event) {
        if ("u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) {
            if (!u0LifecycleFirstTryIssued) return;
            try {
                var minecraft = Minecraft.getInstance();
                boolean exact = event.getOldPlayer() == currentPlayer && event.getNewPlayer() != currentPlayer
                        && event.getConnection() == currentConnection && event.getNewPlayer() == minecraft.player
                        && event.getNewPlayer().connection == currentListener && currentListener != null
                        && currentListener.getConnection() == currentConnection && currentConnection.isConnected()
                        && currentConnection.getPacketListener() == currentListener
                        && event.getNewPlayer().getUUID().equals(playerId) && u0LifecycleCloneCount < 2;
                if (!exact) throw new IllegalStateException("U0_LIFECYCLE_CLIENT_CLONE_TUPLE");
                currentPlayer = event.getNewPlayer(); u0LifecycleCloneCount++;
                U0OrdinaryTrace.note("CLIENT_NATIVE_RESPAWN_CLONE", u0LifecycleCloneCount, 0, 0, 0, 0, 0,
                        1, 1, 0, P11C4aEvidence.pseudonym(currentPlayer.getUUID()));
            } catch (RuntimeException | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
                catch (RuntimeException | Error unavailableRecorder) { }
            }
            return;
        }
        if (!enabled() || terminal || !capacityRespawnSelected()) { return; }
        try {
            var minecraft = Minecraft.getInstance();
            check(minecraft.isSameThread() && phase == Phase.FIRST_PLAY && capacityClickStarted
                    && !capacityRespawnComplete && capacityCloneEvents == 0
                    && event.getOldPlayer() == currentPlayer && event.getNewPlayer() != currentPlayer
                    && event.getConnection() == currentConnection && event.getNewPlayer() == minecraft.player
                    && event.getNewPlayer().connection == currentListener
                    && currentListener.getConnection() == currentConnection
                    && currentConnection.isConnected() && currentConnection.getPacketListener() == currentListener
                    && event.getNewPlayer().getUUID().equals(playerId), "CAPACITY_NATIVE_CLONE_IDENTITY");
            currentPlayer = event.getNewPlayer();
            capacityCloneEvents++;
        } catch (ObservationFault failure) { recordFailure(failure.code); }
        catch (RuntimeException | Error failure) { recordFailure("CAPACITY_CLONE_OBSERVATION_FAILURE"); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if ("u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) {
            try {
                if (event.getPlayer() == null && event.getConnection() == null) {
                    U0OrdinaryTrace.note("CLIENT_NULL_LOGOUT", 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
                    return;
                }
                boolean exact = event.getPlayer() == currentPlayer && event.getConnection() == currentConnection
                        && currentPlayer != null && currentConnection != null;
                U0OrdinaryTrace.note(exact ? "CLIENT_NATIVE_LOGOUT" : "OBSERVATION_FAILURE",
                        logins, logouts, 0, 0, 0, 0, exact ? 1 : 0, 0, 0, "");
                if (exact) { logouts++; retiredConnection = currentConnection; }
                currentPlayer = null; currentListener = null; currentConnection = null; phase = Phase.FIRST_DISCONNECTED;
                if (exact && output != null && P11C4aEvidence.cuePresent(serverOutput, role + "-finish.ready")) {
                    u0FlushTrace();
                }
            } catch (Exception | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
                catch (RuntimeException | Error unavailableRecorder) { }
            }
            return;
        }
        if (!enabled() || terminal) { return; }
        try {
            if (event.getPlayer() == null && event.getConnection() == null) {
                nullLogoutEvents = Math.incrementExact(nullLogoutEvents);
                return;
            }
            check(Minecraft.getInstance().isSameThread() && nativeCall
                    && (phase == Phase.FIRST_DISCONNECTED || phase == Phase.FINAL_DISCONNECTED)
                    && event.getPlayer() == currentPlayer && event.getConnection() == currentConnection
                    && currentPlayer != null && currentPlayer.connection == currentListener
                    && currentListener.getConnection() == currentConnection && logins == logouts + 1,
                    "UNEXPECTED_LOGOUT");
            logouts++;
            currentPlayer = null;
            currentListener = null;
            currentConnection = null;
        } catch (ObservationFault failure) { recordFailure(failure.code); }
        catch (RuntimeException | Error failure) { recordFailure("LOGOUT_OBSERVATION_FAILURE"); }
    }

    private static void firstPlay(Minecraft minecraft) {
        exactCurrent(minecraft, currentListener);
        if (capacityRespawnSelected() && !capacityRespawnComplete) {
            capacityRespawn(minecraft);
            return;
        }
        if (!FIRST.initialObserved() || !FIRST.fixtureDone || !minecraft.player.getRecipeBook().contains(BREAD)
                || !cue("reconnect")) { return; }
        var report = summary("FIRST_NATIVE_STATE_OBSERVED");
        report.add("first", FIRST.json());
        write("first-play.json", report);
        disconnect(minecraft, false);
        write("first-disconnected.json", summary("EXACT_FIRST_CLIENT_DISCONNECTED"));
    }

    private static boolean capacityRespawnSelected() {
        return ("c4a-capacity".equals(caseName) || "l1-capacity".equals(caseName)) && "b".equals(role);
    }

    private static void capacityRespawn(Minecraft minecraft) {
        if (!FIRST.initialObserved() || !cue("capacity-death")) { return; }
        var view = P11ClientTransitions.view();
        if (!capacityClickStarted) {
            if (minecraft.getOverlay() != null || !(minecraft.screen instanceof DeathScreen screen)) { return; }
            if (view == null || view.kind() != P11TransitionProtocol.Kind.DEATH
                    || view.scope() != P11TransitionProtocol.Scope.PLAY
                    || view.outcome() != P11TransitionProtocol.Outcome.BINDING) { return; }
            Button selected = null;
            for (var child : screen.children()) {
                if (child instanceof Button button && button.visible && button.active
                        && button.getMessage().getContents() instanceof TranslatableContents text
                        && text.getKey().equals("deathScreen.respawn")) {
                    check(selected == null, "CAPACITY_AMBIGUOUS_RESPAWN_BUTTON");
                    selected = button;
                }
            }
            if (selected == null) { return; }
            check(minecraft.mouseHandler instanceof P11C4aClientInputProbe.MouseInput,
                    "CAPACITY_NATIVE_MOUSE_HOOK_MISSING");
            var mouse = (P11C4aClientInputProbe.MouseInput) minecraft.mouseHandler;
            var window = minecraft.getWindow();
            double x = (selected.getX() + selected.getWidth() / 2.0)
                    * window.getScreenWidth() / window.getGuiScaledWidth();
            double y = (selected.getY() + selected.getHeight() / 2.0)
                    * window.getScreenHeight() / window.getGuiScaledHeight();
            capacityScene = view.sceneSerial();
            capacityEpoch = view.connectionEpoch();
            capacityOriginalGeneration = view.actorGeneration();
            capacityClickStarted = true;
            mouse.p11$move(window.getWindow(), x, y);
            mouse.p11$press(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,
                    org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
            mouse.p11$press(window.getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,
                    org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
            capacityClickReturned = true;
            return;
        }
        if (capacityCloneEvents != 1 || capacityFrameReturns != 1 || view == null
                || view.outcome() != P11TransitionProtocol.Outcome.COMPLETED) { return; }
        check(capacityClickReturned && view.kind() == P11TransitionProtocol.Kind.DEATH
                && view.scope() == P11TransitionProtocol.Scope.PLAY
                && view.connectionEpoch() == capacityEpoch && view.sceneSerial() == capacityScene
                && view.actorGeneration() == capacityOriginalGeneration && view.requestSeq() > 0
                && view.targetActorGeneration() > capacityOriginalGeneration
                && minecraft.player.isAlive() && minecraft.player.totalExperience == 0
                && P11ClientTransitions.allowActorFunctions(currentListener), "CAPACITY_DEATH_COMPLETED_CORRELATION");
        var facts = summary("PASS_UNMANAGED_NATIVE_DEATH_RESPAWN_CLIENT_ONLY");
        facts.addProperty("originalMouseCallbackInjectionNotPhysicalOSInput", true);
        facts.addProperty("nativeButtonClicks", 1);
        facts.addProperty("nativeCloneEvents", capacityCloneEvents);
        facts.addProperty("nativeRespawnHandlerReturns", capacityFrameReturns);
        facts.addProperty("sameOriginalConnectionAndListener", true);
        facts.addProperty("sceneSerial", capacityScene);
        facts.addProperty("connectionEpoch", capacityEpoch);
        facts.addProperty("requestSeq", view.requestSeq());
        facts.addProperty("originalActorGeneration", capacityOriginalGeneration);
        facts.addProperty("targetActorGeneration", view.targetActorGeneration());
        facts.addProperty("managedSourceProofRequiredSeparately", true);
        write("capacity-respawn.json", facts);
        capacityRespawnComplete = true;
    }

    public static void afterRespawn(ClientPacketListener listener, ClientboundRespawnPacket packet) {
        if ("u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) {
            if (!u0LifecycleFirstTryIssued) return;
            try {
                var minecraft = Minecraft.getInstance();
                boolean exact = listener == currentListener && minecraft.player == currentPlayer && currentPlayer != null
                        && currentPlayer.connection == listener && listener.getConnection() == currentConnection
                        && currentConnection != null && currentConnection.getPacketListener() == listener
                        && u0LifecycleRespawnReturns < 2;
                if (!exact) throw new IllegalStateException("U0_LIFECYCLE_NATIVE_RESPAWN_RETURN");
                u0LifecycleRespawnReturns++;
                U0OrdinaryTrace.note("CLIENT_RESPAWN_HANDLER_RETURN", u0LifecycleRespawnReturns, u0LifecycleCloneCount,
                        0, 0, 0, 0, packet.shouldKeep(ClientboundRespawnPacket.KEEP_ALL_DATA) ? 1 : 0, 1, 0, "");
            } catch (RuntimeException | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
                catch (RuntimeException | Error unavailableRecorder) { }
            }
            return;
        }
        if (!enabled() || terminal || !capacityRespawnSelected()) { return; }
        try {
            exactCurrent(Minecraft.getInstance(), listener);
            check(phase == Phase.FIRST_PLAY && capacityClickStarted && capacityCloneEvents == 1
                    && capacityFrameReturns == 0 && !capacityRespawnComplete
                    && !packet.shouldKeep(ClientboundRespawnPacket.KEEP_ALL_DATA),
                    "CAPACITY_NATIVE_RESPAWN_HANDLER_ORDER");
            capacityFrameReturns++;
        } catch (ObservationFault failure) { recordFailure(failure.code); }
        catch (RuntimeException | Error failure) { recordFailure("CAPACITY_RESPAWN_OBSERVATION_FAILURE"); }
    }

    private static void reconnectAfterServerRemoval(Minecraft minecraft) {
        check(logins == 1 && logouts == 1 && currentPlayer == null && minecraft.player == null
                && minecraft.level == null && minecraft.getConnection() == null
                && retiredConnection != null && !retiredConnection.isConnected(), "FIRST_DISCONNECT_INCOMPLETE");
        if (cue("logout")) { connect(minecraft, true); }
    }

    private static void secondPlay(Minecraft minecraft) {
        exactCurrent(minecraft, currentListener);
        if (!SECOND.initialObserved() || !SECOND.fixtureDone || !SECOND.fixtureDoneInReset || !SECOND.breadKnownInInit) { return; }
        check(minecraft.player.getRecipeBook().contains(BREAD)
                && currentListener.getAdvancements().get(ROOT) != null, "RECONNECTED_CLIENT_STATE_MISSING");
        if (!cue("finish")) { return; }
        check(SECOND.breadAddPackets == 0, "RECONNECT_RECIPE_ADD_INSTEAD_OF_INIT");
        var report = summary("SECOND_NATIVE_FULL_STATE_OBSERVED");
        report.add("second", SECOND.json());
        write("second-play.json", report);
        disconnect(minecraft, true);
    }

    private static void disconnect(Minecraft minecraft, boolean last) {
        exactCurrent(minecraft, currentListener);
        retiredConnection = currentConnection;
        retiredListener = currentListener;
        transition(last ? Phase.FINAL_DISCONNECTED : Phase.FIRST_DISCONNECTED);
        nativeCall = true;
        try {
            minecraft.level.disconnect();
            minecraft.disconnect(new TitleScreen());
        } finally { nativeCall = false; }
        check(observationFailure == null && currentPlayer == null && !retiredConnection.isConnected()
                && minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null,
                "NATIVE_DISCONNECT_NOT_COMPLETED");
    }

    /** Exact RETURN observer, after PacketUtils dispatch and native ClientAdvancements.update. */
    public static void afterAdvancements(ClientPacketListener listener, ClientboundUpdateAdvancementsPacket packet) {
        if (!enabled() || terminal) { return; }
        try {
            var observation = currentObservation(listener);
            observation.advancementHandlerReturns++;
            if (packet.shouldReset()) {
                observation.resetHandlerReturns++;
                observation.resetAdded += packet.getAdded().size();
                observation.resetRemoved += packet.getRemoved().size();
                observation.resetProgress += packet.getProgress().size();
                observation.fixtureDone = false;
                observation.fixtureDoneInReset = false;
            }
            var progress = packet.getProgress().get(ROOT);
            if (packet.getRemoved().contains(ROOT)) { observation.fixtureDone = false; }
            if (progress != null) {
                observation.fixtureDone = progress.isDone() && listener.getAdvancements().get(ROOT) != null;
                if (packet.shouldReset()) { observation.fixtureDoneInReset = observation.fixtureDone; }
            }
        } catch (ObservationFault failure) { recordFailure(failure.code); }
        catch (RuntimeException | Error failure) { recordFailure("ADVANCEMENT_OBSERVATION_FAILURE"); }
    }

    /** Exact RETURN observer, after native recipe INIT/ADD and ClientRecipeBook application. */
    public static void afterRecipes(ClientPacketListener listener, ClientboundRecipePacket packet) {
        if (!enabled() || terminal) { return; }
        try {
            var observation = currentObservation(listener);
            observation.recipeHandlerReturns++;
            if (packet.getState() == ClientboundRecipePacket.State.INIT) {
                observation.recipeInitHandlerReturns++;
                observation.initRecipes += packet.getRecipes().size();
                observation.initHighlights += packet.getHighlights().size();
                if (packet.getRecipes().contains(BREAD) && Minecraft.getInstance().player.getRecipeBook().contains(BREAD)) {
                    observation.breadKnownInInit = true;
                }
            } else if (packet.getState() == ClientboundRecipePacket.State.ADD && packet.getRecipes().contains(BREAD)) {
                observation.breadAddPackets++;
            }
        } catch (ObservationFault failure) { recordFailure(failure.code); }
        catch (RuntimeException | Error failure) { recordFailure("RECIPE_OBSERVATION_FAILURE"); }
    }

    private static Observation currentObservation(ClientPacketListener listener) {
        exactCurrent(Minecraft.getInstance(), listener);
        check(phase == Phase.FIRST_PLAY || phase == Phase.SECOND_PLAY, "HANDLER_OUTSIDE_PLAY_SCOPE");
        return phase == Phase.FIRST_PLAY ? FIRST : SECOND;
    }

    private static void exactCurrent(Minecraft minecraft, ClientPacketListener listener) {
        check(minecraft.isSameThread() && currentPlayer != null && currentConnection != null
                && minecraft.getSingleplayerServer() == null && minecraft.player == currentPlayer
                && minecraft.getConnection() == listener && listener == currentListener
                && listener.getConnection() == currentConnection && currentConnection.isConnected()
                && !currentConnection.isMemoryConnection() && currentConnection.getPacketListener() == listener
                && currentPlayer.getUUID().equals(playerId), "NOT_EXACT_CURRENT_REMOTE_PLAY");
    }

    private static boolean cue(String stage) {
        var file = serverOutput.resolve(role + '-' + stage + ".ready");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) { return false; }
        try {
            check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                    && Files.size(file) == 0, "INVALID_COORDINATION_CUE");
            return true;
        } catch (IOException failure) { throw new ObservationFault("COORDINATION_CUE_IO_FAILED"); }
    }

    private static void complete(Minecraft minecraft) {
        check(logins == 2 && logouts == 2 && observationFailure == null && newConnectionObserved
                && newListenerObserved && retiredConnection != null && !retiredConnection.isConnected()
                && minecraft.player == null && minecraft.getConnection() == null, "FINAL_DISCONNECT_INCOMPLETE");
        var result = summary("PASS_CLIENT_NATIVE_DELIVERY_ONLY");
        result.add("first", FIRST.json());
        result.add("second", SECOND.json());
        result.addProperty("authenticationClaim", "SERVER_ORIGINAL_ONLINE_AUTH_PROVENANCE_REQUIRED");
        result.addProperty("rewardReplayClaim", "SERVER_NATIVE_EVENT_COUNTERS_REQUIRED");
        result.addProperty("clientPacketHistoryRetained", false);
        write("result.json", result);
        release();
        terminal = true;
        transition(Phase.TERMINAL);
        minecraft.stop();
    }

    private static void fail(Minecraft minecraft, String code) {
        var result = summary("FAIL");
        result.addProperty("failure", code);
        result.add("screen", screenObservation(minecraft));
        result.add("first", FIRST.json());
        result.add("second", SECOND.json());
        terminal = true;
        try {
            try {
                if (output != null) { write("result.json", result); }
            } catch (ObservationFault evidenceFailure) {
                // A failed exclusive write remains failed; do not overwrite or manufacture evidence.
            }
            try {
                if (minecraft.level != null) { minecraft.level.disconnect(); }
                minecraft.disconnect(new TitleScreen());
            } finally { release(); }
        } finally {
            transition(Phase.TERMINAL);
            minecraft.stop();
        }
    }

    private static JsonObject summary(String status) {
        var json = new JsonObject();
        json.addProperty("format", "P11_ONLINE_CLIENT_V1");
        json.addProperty("status", status);
        json.addProperty("runId", runId);
        json.addProperty("case", caseName);
        json.addProperty("role", role);
        json.addProperty("identityPseudonym", pseudonym);
        json.addProperty("phase", phase.name());
        json.addProperty("phaseTicks", ticks);
        json.addProperty("nativePlayLoginEvents", logins);
        json.addProperty("nativePlayLogoutEvents", logouts);
        json.addProperty("nullWorldCleanupEvents", nullLogoutEvents);
        json.addProperty("newConnectionObserved", newConnectionObserved);
        json.addProperty("newListenerObserved", newListenerObserved);
        json.addProperty("onboardingContinueStarted", onboardingContinueStarted);
        json.addProperty("onboardingContinueReturned", onboardingContinueReturned);
        return json;
    }

    private static void write(String name, JsonObject value) {
        try {
            Files.writeString(output.resolve(name), new GsonBuilder().setPrettyPrinting().create().toJson(value) + '\n',
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException failure) { throw new ObservationFault("EVIDENCE_WRITE_FAILED"); }
    }

    private static String pseudonym(String domain, UUID id) {
        try {
            var bytes = (domain + '\n' + id).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) { throw new ObservationFault("HASH_UNAVAILABLE"); }
    }

    private static String property(String name) {
        var value = System.getProperty(PREFIX + name);
        check(value != null && !value.isBlank(), "MISSING_HARNESS_PROPERTY");
        return value;
    }

    private static void transition(Phase next) { phase = next; ticks = 0; }
    private static void recordFailure(String code) { if (observationFailure == null) { observationFailure = code; } }
    private static void check(boolean condition, String code) { if (!condition) { throw new ObservationFault(code); } }
    private static void release() {
        currentPlayer = null;
        currentListener = null;
        currentConnection = null;
        retiredListener = null;
        retiredConnection = null;
        playerId = null;
    }

    private enum Phase { BOOTSTRAP, WAIT_CONNECT, FIRST_CONNECT, FIRST_PLAY, FIRST_DISCONNECTED, SECOND_CONNECT, SECOND_PLAY, FINAL_DISCONNECTED, TERMINAL }
    private static final class ObservationFault extends RuntimeException {
        private final String code;
        private ObservationFault(String code) { super(code); this.code = code; }
    }
    private static final class Observation {
        private long advancementHandlerReturns, resetHandlerReturns, resetAdded, resetRemoved, resetProgress;
        private long recipeHandlerReturns, recipeInitHandlerReturns, initRecipes, initHighlights, breadAddPackets;
        private boolean fixtureDone, fixtureDoneInReset, breadKnownInInit;
        private boolean initialObserved() { return resetHandlerReturns > 0 && recipeInitHandlerReturns > 0; }
        private JsonObject json() {
            var json = new JsonObject();
            json.addProperty("advancementHandlerReturns", advancementHandlerReturns);
            json.addProperty("resetHandlerReturns", resetHandlerReturns);
            json.addProperty("resetAdded", resetAdded);
            json.addProperty("resetRemoved", resetRemoved);
            json.addProperty("resetProgress", resetProgress);
            json.addProperty("recipeHandlerReturns", recipeHandlerReturns);
            json.addProperty("recipeInitHandlerReturns", recipeInitHandlerReturns);
            json.addProperty("initRecipes", initRecipes);
            json.addProperty("initHighlights", initHighlights);
            json.addProperty("breadAddPackets", breadAddPackets);
            json.addProperty("fixtureDoneAfterNativeHandler", fixtureDone);
            json.addProperty("fixtureDoneInNativeReset", fixtureDoneInReset);
            json.addProperty("breadKnownInNativeInit", breadKnownInInit);
            return json;
        }
    }
}
