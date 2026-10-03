package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
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

    private P11OnlineClientHarness() {}

    private static boolean enabled() { return System.getProperty(PREFIX + "output") != null; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ClientTickEvent.Post ignored) {
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
        check(caseName.equals("single") || caseName.equals("qctx") || caseName.equals("capacity"), "INVALID_CASE");
        check(caseName.equals("single") == role.equals("single"), "CASE_ROLE_MISMATCH");
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
    static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
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
        if (!FIRST.initialObserved() || !FIRST.fixtureDone || !minecraft.player.getRecipeBook().contains(BREAD)
                || !cue("reconnect")) { return; }
        var report = summary("FIRST_NATIVE_STATE_OBSERVED");
        report.add("first", FIRST.json());
        write("first-play.json", report);
        disconnect(minecraft, false);
        write("first-disconnected.json", summary("EXACT_FIRST_CLIENT_DISCONNECTED"));
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
