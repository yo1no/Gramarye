package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Fresh native connector/host lifecycle plus controlled original window callback input. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11C4aClientHarness {
    private enum Phase { BOOTSTRAP, WAIT_CONNECT, CONNECTING, PLAY, DISCONNECTING, TERMINAL }
    private static final String WORLD = "p11-c4a-owned-host";
    private static Phase phase = Phase.BOOTSTRAP;
    private static Path output;
    private static Path serverOutput;
    private static String role;
    private static String runId;
    private static String product;
    private static boolean host;
    private static boolean onboarding;
    private static boolean published;
    private static boolean nativeCall;
    private static boolean endView;
    private static Connection connection;
    private static UUID id;
    private static int ticks;
    private static int logins;
    private static int step;
    private static int inputStage;
    private static long beforeNativeFrames;
    private static long completedRespawnFrames;
    private static boolean activeStep;
    private static long completedConfigurationScene;
    private static int reloadStep;
    private static boolean reloadActive;
    private static boolean firstTryActive;
    private static boolean firstTryComplete;
    private static int metadataHStage;
    private static int metadataHLogins;
    private static long metadataHStarts, metadataHLoginFrames, metadataHConnectionEpoch, metadataHScene;
    private static boolean metadataHActive, metadataHComplete;
    private static boolean hostLeaveActive;
    private static boolean hostExpiryActive;
    private static boolean parkingActive, parkingComplete;
    private static int parkingLogins;
    private static boolean configResetActive, configResetComplete;
    private static int configResetLogins;
    private static boolean secondEndActive, secondEndComplete;
    private static boolean portalComplete;
    private static boolean nativeErrorArmed, nativeErrorClientObserved;
    private static int nativeErrorTicks;
    private static String loginDisconnectCategory = "NOT_OBSERVED";
    private static int loginDisconnectObservations;

    private P11C4aClientHarness() {}

    /** Diagnostic only: never formats native reason text, arguments, profiles or exceptions. */
    public static void loginDisconnected(Connection exact, net.minecraft.network.chat.Component reason) {
        if (!P11C4aEvidence.enabled() || output == null || phase != Phase.CONNECTING || logins != 0) { return; }
        try {
            if (!Minecraft.getInstance().isSameThread() || exact.isMemoryConnection()) {
                loginDisconnectCategory = "UNQUALIFIED_NATIVE_DIAGNOSTIC_OWNER"; return;
            }
            if (loginDisconnectObservations != 0) {
                loginDisconnectCategory = "MULTIPLE_NATIVE_LOGIN_DISCONNECTS"; return;
            }
            loginDisconnectObservations = 1;
            loginDisconnectCategory = loginReasonCategory(reason);
        } catch (RuntimeException | Error secondary) {
            loginDisconnectCategory = "DIAGNOSTIC_UNAVAILABLE";
        }
    }

    static String loginReasonCategory(net.minecraft.network.chat.Component reason) {
        if (reason == null || !(reason.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)) {
            return "OTHER_NATIVE_REASON";
        }
        if (text.getKey().equals("disconnect.loginFailedInfo")) {
            Object[] arguments = text.getArgs();
            if (arguments.length != 1 || !(arguments[0] instanceof net.minecraft.network.chat.Component nested)
                    || !(nested.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents detail)) {
                return "OTHER_LOGIN_FAILURE";
            }
            return switch (detail.getKey()) {
                case "disconnect.loginFailedInfo.invalidSession" -> "INVALID_SESSION";
                case "disconnect.loginFailedInfo.serversUnavailable" -> "AUTH_SERVERS_UNAVAILABLE";
                case "disconnect.loginFailedInfo.insufficientPrivileges" -> "INSUFFICIENT_PRIVILEGES";
                case "disconnect.loginFailedInfo.userBanned" -> "ACCOUNT_RESTRICTED";
                default -> "OTHER_LOGIN_FAILURE";
            };
        }
        return switch (text.getKey()) {
            case "multiplayer.disconnect.unverified_username" -> "SERVER_UNVERIFIED_PROFILE";
            case "multiplayer.disconnect.authservers_down" -> "SERVER_AUTH_SERVICE_UNAVAILABLE";
            case "multiplayer.disconnect.slow_login" -> "NATIVE_LOGIN_TIMEOUT";
            case "multiplayer.disconnect.duplicate_login" -> "DUPLICATE_LOGIN";
            case "disconnect.timeout" -> "NETWORK_TIMEOUT";
            case "disconnect.endOfStream" -> "NETWORK_END_OF_STREAM";
            case "disconnect.genericReason" -> "NATIVE_GENERIC_REASON_UNREAD";
            default -> "OTHER_NATIVE_REASON";
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ClientTickEvent.Post ignored) {
        if (!P11C4aEvidence.enabled() || phase == Phase.TERMINAL || nativeCall) { return; }
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread(), "NOT_CLIENT_THREAD");
            P11C4aNativeObservations.requireHealthy();
            if (P11C4aTerminalStatusProbe.selected() && phase == Phase.PLAY) {
                nativeCall = true;
                try {
                    if (P11C4aTerminalStatusClientProbe.tick(minecraft, connection, role, output, serverOutput)) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, "TERMINAL_STATUS_NATIVE_SUBSET_ONLY"));
                        P11C4aTerminalStatusClientProbe.release(); connection = null; id = null;
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            if (P11C4aHeldProbe.selected() && phase == Phase.PLAY) {
                nativeCall = true;
                try {
                    if (P11C4aHeldClientProbe.tick(minecraft, connection, role, output, serverOutput)) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, "HELD_THREE_WINDOWS_NATIVE_SUBSET_ONLY"));
                        P11C4aHeldClientProbe.release(); connection = null; id = null;
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            if (P11C4aConfigCatchProbe.selected() && phase == Phase.PLAY) {
                nativeCall = true;
                try {
                    if (P11C4aConfigCatchClientProbe.tick(minecraft, connection, role, output, serverOutput)) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, role.equals("a")
                                ? "ORIGINAL_CONFIG_CATCH_NATIVE_CLOSE_SUBSET_ONLY" : "UNCHANGED_PEER_NORMAL_DEPARTURE"));
                        P11C4aConfigCatchClientProbe.release(); connection = null; id = null;
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            if ((P11C4aScenario.nativeErrorMode() != null || P11C4aSubmissionRejectProbe.selected()) && phase == Phase.PLAY) {
                nativeError(minecraft); return;
            }
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.C6_RATE_FAIRNESS && phase == Phase.PLAY) {
                nativeCall = true;
                try {
                    if (P11C4aRateFairClientProbe.tick(minecraft, connection, role, output, serverOutput)) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, "RATE_FAIRNESS_TERMINAL_ROWS_REQUIRE_EXPLICIT_QUALIFICATION"));
                        P11C4aRateFairClientProbe.release(); connection = null; id = null;
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            if (P11C4aScenario.c6() && phase == Phase.PLAY
                    && (role.equals("b") || step >= P11C4aServerHarness.NORMAL_STEPS.size())) {
                nativeCall = true;
                try {
                    if (P11C4aC6ClientFlow.tick(minecraft, connection, role, output, serverOutput)) {
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            require(++ticks <= (phase == Phase.WAIT_CONNECT || phase == Phase.PLAY && !activeStep ? 24_000 : 2400), "CLIENT_PHASE_DEADLINE");
            if (P11C4aRequiredProbe.selected() && (phase == Phase.CONNECTING || phase == Phase.PLAY)) {
                nativeCall = true;
                try {
                    if (P11C4aRequiredClientProbe.tick(minecraft)) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, role.equals("a")
                                ? "EXPECTED_NATIVE_NEGOTIATION_REJECTION_NOT_NATIVE_SUCCESS"
                                : "NORMAL_REQUIRED_CHANNEL_CONTROL_DEPARTURE_ONLY"));
                        P11C4aRequiredClientProbe.release(); connection = null; id = null;
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_EXPIRY && phase == Phase.PLAY) {
                hostExpiry(minecraft); return;
            }
            if (phase == Phase.PLAY && P11C4aPreplayChatProbe.selected()) { preplayChat(minecraft); return; }
            if (P11C4aMalformedWireProbe.selected() && phase == Phase.PLAY) {
                nativeCall = true;
                try {
                    if (P11C4aMalformedClientProbe.tick(minecraft, connection, role, output, serverOutput)) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, role.equals("a")
                                ? "EXPECTED_NATIVE_MALFORMED_WIRE_CLOSE_NOT_NATIVE_SUCCESS"
                                : "NORMAL_MALFORMED_WIRE_CONTROL_DEPARTURE_ONLY"));
                        P11C4aMalformedClientProbe.release(); connection = null; id = null;
                        phase = Phase.TERMINAL; minecraft.stop();
                    }
                } finally { nativeCall = false; }
                return;
            }
            if (P11C4aConfigDepartureProbe.selectedMode() != null
                    && (phase == Phase.CONNECTING && role.equals("b") || phase == Phase.PLAY)) {
                configDeparture(minecraft); return;
            }
            if (phase == Phase.PLAY && P11C4aAckRejectionProbe.selectedMode() != null) {
                ackRejection(minecraft); return;
            }
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_LEAVE && hostLeaveActive
                    && P11C4aHostLeaveClientProbe.terminalExpected()) {
                hostLeave(minecraft); return;
            }
            if (phase != Phase.BOOTSTRAP && phase != Phase.DISCONNECTING) {
                require(!(minecraft.screen instanceof DisconnectedScreen), "UNEXPECTED_NATIVE_DISCONNECT");
            }
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case WAIT_CONNECT -> { if (P11C4aEvidence.cuePresent(serverOutput, role + "-connect.ready")) { connect(minecraft); } }
                case CONNECTING -> { }
                case PLAY -> play(minecraft);
                case DISCONNECTING -> {
                    if (minecraft.getConnection() == null && minecraft.player == null && minecraft.getSingleplayerServer() == null) {
                        P11C4aEvidence.write(output, "result.json", summary(minecraft, P11C4aPreplayChatProbe.selected()
                                ? "PREPLAY_CHAT_CLIENT_ONLY_SERVER_NATIVE_VALIDATION_PROOF_REQUIRED"
                                : P11C4aConfigDepartureProbe.selectedMode() != null
                                ? "CONFIG_DEPARTURE_LIVE_PEER_ORIGINAL_NORMAL_DEPARTURE_ONLY"
                                : P11C4aAckRejectionProbe.selectedMode() != null
                                ? "ACK_REJECTION_PEER_ORIGINAL_NORMAL_DEPARTURE_ONLY"
                                : "NORMAL_PATH_CLIENT_OBSERVATIONS_ONLY_FULL_MATRIX_PENDING"));
                        connection = null; id = null; P11C4aPreplayChatClientProbe.release();
                        P11C4aNativeObservations.release(); phase = Phase.TERMINAL; minecraft.stop();
                    }
                }
                case TERMINAL -> { }
            }
        } catch (Exception | LinkageError failure) { fail(minecraft, P11C4aEvidence.failureCode(failure)); }
    }

    private static void bootstrap(Minecraft minecraft) throws IOException {
        if (output == null) {
            role = P11C4aEvidence.property("role");
            host = P11C4aEvidence.property("case").equals("c4a-host-lan") && role.equals("host");
            require(host || role.equals("a") || role.equals("b"), "INVALID_C4A_CLIENT_ROLE");
            require(!P11C4aEvidence.property("case").equals("c4a-host-lan") || host || role.equals("b"), "C4A_CASE_ROLE_MISMATCH");
            runId = P11C4aEvidence.property("runId");
            require(runId.matches("[A-Za-z0-9_-]{8,64}"), "INVALID_RUN_ID");
            minecraft.getWindow().setTitle("Gramarye P11 C4a " + runId + " " + role);
            require(P11C4aEvidence.property("host").equals("127.0.0.1"), "NON_LOOPBACK_CLIENT");
            output = P11C4aEvidence.reserve("client-" + role);
            serverOutput = P11C4aEvidence.root().resolve("server");
            product = P11OnlineInputs.verifyFrozenJar();
            P11C4aEvidence.write(output, "bootstrap.json", summary(minecraft, "INITIAL_TICK_NOT_ARMED"));
        }
        require(minecraft.player == null && minecraft.level == null && minecraft.getConnection() == null
                && minecraft.getSingleplayerServer() == null, "CLIENT_NOT_FRESH");
        if (minecraft.getOverlay() != null) { return; }
        if (minecraft.screen != null && minecraft.screen.getClass() == AccessibilityOnboardingScreen.class && !onboarding) {
            onboarding = true; nativeCall = true;
            try { minecraft.screen.onClose(); } finally { nativeCall = false; }
            P11C4aEvidence.write(output, "onboarding-continued.json", summary(minecraft, "ORIGINAL_ONBOARDING_CONTINUE_RETURNED"));
            return;
        }
        if (!(minecraft.screen instanceof TitleScreen)) { return; }
        P11C4aEvidence.write(output, "inputs.json", summary(minecraft, "ARMED_NOT_AUTHENTICATION_PROOF"));
        minecraft.options.pauseOnLostFocus = false;
        transition(host ? Phase.CONNECTING : Phase.WAIT_CONNECT);
        if (host) {
            require(!Files.exists(minecraft.gameDirectory.toPath().resolve("saves").resolve(WORLD)), "HOST_WORLD_ALREADY_EXISTS");
            var settings = new LevelSettings("P11 C4a isolated host", GameType.SURVIVAL, false,
                    Difficulty.PEACEFUL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
            nativeCall = true;
            try {
                minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(110031L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), minecraft.screen);
            } finally { nativeCall = false; }
        }
    }

    private static void connect(Minecraft minecraft) throws IOException {
        require(minecraft.player == null && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null, "CONNECT_NOT_FRESH");
        String address = "127.0.0.1:" + port();
        transition(Phase.CONNECTING);
        if (P11C4aRequiredProbe.selected()) { P11C4aRequiredClientProbe.arm(minecraft, role, output); }
        P11C4aPreplayChatClientProbe.arm(minecraft,role,output);
        if (P11C4aConfigDepartureProbe.selectedMode() != null && role.equals("b")) {
            P11C4aConfigDepartureClientProbe.arm(minecraft, role, output);
        }
        nativeCall = true;
        try { ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                new ServerData("P11 C4a isolated", address, ServerData.Type.OTHER), false, null); }
        finally { nativeCall = false; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!P11C4aEvidence.enabled() || phase == Phase.TERMINAL) { return; }
        var minecraft = Minecraft.getInstance();
        try {
            require(!P11C4aRequiredProbe.selected() || role.equals("b"), "REQUIRED_FAULTY_A_NATIVE_LOGIN");
            var next = event.getConnection();
            require(minecraft.isSameThread() && next != null && next.isConnected() && event.getPlayer() != null
                    && next.isMemoryConnection() == host && (host || next.isEncrypted()), "CLIENT_CONNECTION_IDENTITY");
            if (connection == null) { connection = next; id = event.getPlayer().getUUID(); }
            else {
                boolean normalReturn = step == 4 && logins == 1;
                boolean metadataReturn = firstTryComplete && metadataHStage == 2
                        && logins == metadataHLogins && metadataHLogins == 2;
                boolean parkingReturn = parkingActive && !parkingComplete && logins == parkingLogins;
                boolean resetReturn = P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET
                        && configResetActive && !configResetComplete && logins == configResetLogins;
                require(connection == next && id.equals(event.getPlayer().getUUID())
                        && ((!role.equals("b") && (normalReturn || metadataReturn)) || parkingReturn || resetReturn
                            || P11C4aTerminalStatusClientProbe.allowsLogin(next, role, logins)),
                        "UNEXPECTED_CLIENT_LOGIN");
            }
            logins++;
            P11C4aEvidence.write(output, "login-" + logins + ".json", summary(minecraft,
                    host ? "ORIGINAL_INTEGRATED_HOST_PLAY_NOT_HAS_JOINED_PROOF" : "ORIGINAL_REMOTE_PLAY_SERVER_AUTH_PROOF_REQUIRED"));
            transition(Phase.PLAY);
        } catch (Exception | LinkageError failure) { fail(minecraft, P11C4aEvidence.failureCode(failure)); }
    }

    private static boolean preplayChatFinished;
    private static void preplayChat(Minecraft minecraft) throws IOException {
        require(!host && connection != null && connection.isConnected(),"CHAT_CLIENT_REAL_REMOTE_OWNER");
        if (role.equals("a") && !preplayChatFinished) {
            P11C4aPreplayChatClientProbe.tick(minecraft);
            if (!parkingActive && !parkingComplete) {
                if (!P11C4aEvidence.cuePresent(serverOutput,"a-parking-arm.ready")) { return; }
                parkingLogins = logins;
                P11C4aParkingClientProbe.start(minecraft,connection,role,output,serverOutput);
                parkingActive = true;
            }
            if (parkingActive) {
                nativeCall = true;
                try {
                    if (P11C4aParkingClientProbe.tick(minecraft)) {
                        require(logins == parkingLogins + 1,"CHAT_ORIGINAL_RETURN_LOGIN_COUNT");
                        parkingActive = false; parkingComplete = true;
                    }
                } finally { nativeCall = false; }
            }
            if (parkingComplete) { preplayChatFinished = P11C4aPreplayChatClientProbe.finish(minecraft); }
        }
        if (!P11C4aEvidence.cuePresent(serverOutput,role+"-preplay-chat-finish.ready")) { return; }
        require(role.equals("b") ? logins == 1 : preplayChatFinished && logins == 2,"CHAT_FINISH_BEFORE_NATIVE_PROOF");
        transition(Phase.DISCONNECTING); nativeCall = true;
        try {
            if (minecraft.level != null) { minecraft.level.disconnect(); }
            minecraft.disconnect(new TitleScreen());
        } finally { nativeCall = false; }
    }

    private static boolean departurePeerAction;
    private static void configDeparture(Minecraft minecraft) throws IOException {
        require(!host && role.matches("[ab]"), "DEPARTURE_CLIENT_ROLES");
        if (role.equals("b")) {
            if (!P11C4aConfigDepartureClientProbe.tick(minecraft)) { return; }
            P11C4aEvidence.write(output, "result.json", summary(minecraft,
                    "EXACT_CONFIGURATION_DEPARTURE_CLIENT_ONLY_SERVER_BIND_PROOF_REQUIRED"));
            connection = null; id = null; phase = Phase.TERMINAL; minecraft.stop(); return;
        }
        require(logins == 1 && connection != null && connection.isConnected()
                && minecraft.player != null && minecraft.player.isAlive(), "DEPARTURE_PEER_CURRENT_PLAY");
        if (!departurePeerAction && P11C4aEvidence.cuePresent(serverOutput, "a-config-departure-action.ready")) {
            minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); departurePeerAction = true;
        }
        if (!P11C4aEvidence.cuePresent(serverOutput, "a-config-departure-finish.ready")) { return; }
        require(departurePeerAction, "DEPARTURE_PEER_ACTION_MISSING");
        transition(Phase.DISCONNECTING); nativeCall = true;
        try {
            if (minecraft.level != null) { minecraft.level.disconnect(); }
            minecraft.disconnect(new TitleScreen());
        } finally { nativeCall = false; }
    }

    private static boolean ackRejectionActive;
    private static void ackRejection(Minecraft minecraft) throws IOException {
        require(!host && role.matches("[ab]") && logins == 1 && connection != null,
                "ACK_CLIENT_ORCHESTRATION_REMOTE_INITIAL_PLAY");
        if (role.equals("b")) {
            require(connection.isConnected(), "ACK_PEER_UNEXPECTED_DISCONNECT");
            if (!P11C4aEvidence.cuePresent(serverOutput, "b-ack-rejection-finish.ready")) { return; }
            transition(Phase.DISCONNECTING);
            nativeCall = true;
            try {
                if (minecraft.level != null) { minecraft.level.disconnect(); }
                minecraft.disconnect(new TitleScreen());
            } finally { nativeCall = false; }
            return;
        }
        if (!ackRejectionActive) {
            require(connection.isConnected(), "ACK_CLIENT_DISCONNECTED_BEFORE_ARM");
            if (!P11C4aEvidence.cuePresent(serverOutput, "a-ack-rejection-arm.ready")) { return; }
            P11C4aAckRejectionClientProbe.start(minecraft, connection,
                    P11C4aAckRejectionProbe.selectedMode(), output);
            ackRejectionActive = true; activeStep = true; ticks = 0;
        }
        if (!P11C4aAckRejectionClientProbe.tick(minecraft)) { return; }
        P11C4aEvidence.write(output, "result.json", summary(minecraft,
                "ACTUAL_NATIVE_ACK_DISCONNECT_EXPECTED_ONLY_IN_SELECTED_TERMINAL_FIXTURE"));
        connection = null; id = null; P11C4aNativeObservations.release();
        phase = Phase.TERMINAL; minecraft.stop();
    }

    private static boolean publishHostIfNeeded(Minecraft minecraft) throws IOException {
        if (host && !published) {
            var integratedServer = minecraft.getSingleplayerServer();
            if (minecraft.player == null || minecraft.level == null || integratedServer == null) { return false; }
            require(integratedServer.publishServer(GameType.SURVIVAL, false, port()), "ORIGINAL_LAN_PUBLISH_FAILED");
            published = true;
            P11C4aEvidence.write(output, "host-published.json", summary(minecraft, "ORIGINAL_PUBLISH_SERVER_RETURNED_NO_PARTNER_AUTH_CLAIM"));
        }
        return true;
    }

    private static void play(Minecraft minecraft) throws IOException {
        require(connection != null && connection.isConnected(), "LOST_CLIENT_CONNECTION");
        if (!publishHostIfNeeded(minecraft)) { return; }
        if (P11C4aRewardContinuityProbe.selected()) {
            P11C4aRewardContinuityClientProbe.tick(minecraft, connection, role, output);
        }
        if (P11C4aEvidence.cuePresent(serverOutput, role + "-normal-finish.ready")) {
            require(role.equals("b") || step == P11C4aServerHarness.NORMAL_STEPS.size(), "NORMAL_SCENES_NOT_CONSUMED");
            boolean resetMode = P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET;
            boolean remoteEpisodes = parkingComplete && (resetMode
                    ? configResetComplete && logins == configResetLogins + 1
                    : logins == parkingLogins + 1);
            require(role.equals("b") ? (logins == 1 || remoteEpisodes)
                    : P11C4aRewardContinuityProbe.selected() ? P11C4aRewardContinuityClientProbe.complete() && logins == 2
                    : P11C4aPortalProbe.selected() ? portalComplete && logins == 2
                    : P11C4aNativeSenderProbe.selected() ? secondEndComplete && logins == 2
                    : metadataHComplete && (host ? logins == 3 : remoteEpisodes),
                    "METADATA_H_OR_EXTRA_LOGIN_NOT_COMPLETE");
            transition(Phase.DISCONNECTING);
            nativeCall = true;
            try {
                if (minecraft.level != null) { minecraft.level.disconnect(); }
                minecraft.disconnect(new TitleScreen());
            } finally { nativeCall = false; }
            return;
        }
        if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_LEAVE
                && (role.equals("b") || step >= P11C4aServerHarness.NORMAL_STEPS.size())) {
            hostLeave(minecraft); return;
        }
        if (role.equals("b")) { parking(minecraft); return; }
        if (step >= P11C4aServerHarness.NORMAL_STEPS.size()) {
            if (P11C4aRewardContinuityProbe.selected()) { return; }
            if (!P11C4aPortalProbe.selected()) { reload(minecraft); }
            return;
        }
        String scene = P11C4aServerHarness.NORMAL_STEPS.get(step);
        if (!activeStep) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + '-' + scene + ".ready")) { return; }
            activeStep = true; inputStage = 0; ticks = 0;
            // Cue arrival may trail the immediate/native frame; a pre-scene total is
            // retained from the preceding completed scene instead of sampled late.
            beforeNativeFrames = step == 4 ? 1 : completedRespawnFrames;
        }
        if (step == 3 && !endView && minecraft.level != null && minecraft.level.dimension() == Level.END
                && minecraft.player != null && minecraft.screen == null) {
            endView = true; P11C4aEvidence.cue(output, "end-view.ready");
        }
        switch (step) {
            case 0 -> { if (inputStage == 0 && input(minecraft, P11C4aClientInputProbe.Action.DEATH_RESPAWN)) { inputStage++; } }
            case 1 -> {
                if (inputStage == 0 && input(minecraft, P11C4aClientInputProbe.Action.DEATH_TITLE)) { inputStage++; }
                else if (inputStage == 1 && input(minecraft, P11C4aClientInputProbe.Action.DEATH_CONFIRM_NO)) { inputStage++; }
            }
            case 2, 4 -> { } // Native immediate respawn and server configuration sender own these paths.
            case 3 -> {
                if (P11C4aPortalProbe.selected()) {
                    if (!portalComplete) {
                        nativeCall = true;
                        try { portalComplete = P11C4aPortalClientProbe.tick(minecraft, connection, output, serverOutput); }
                        finally { nativeCall = false; }
                    }
                } else if (inputStage == 0 && input(minecraft, P11C4aClientInputProbe.Action.END_FINISH)) { inputStage++; }
            }
            default -> throw new IllegalStateException("UNKNOWN_CLIENT_SCENE");
        }
        var state = P11ClientTransitions.view();
        if (step == 4 && completedConfigurationScene == 0 && state != null
                && state.scope() == P11TransitionProtocol.Scope.CONFIG
                && state.kind() == P11TransitionProtocol.Kind.ENTER_CONFIG
                && state.outcome() == P11TransitionProtocol.Outcome.COMPLETED) {
            require(state.actorGeneration() == 0 && state.requestSeq() == 0
                    && state.targetActorGeneration() == 0 && minecraft.player == null && minecraft.level == null
                    && connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                    && P11C4aNativeObservations.count(connection,
                            P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN) == 1
                    && logins == 1, "CONFIG_TERMINAL_BEFORE_RETURN_IDENTITY");
            completedConfigurationScene = state.sceneSerial();
            P11C4aEvidence.write(output, "enter-config-terminal.json",
                    summary(minecraft, "ACTUAL_CONFIG_ENTER_COMPLETED_WITHOUT_RETURN_REQUEST"));
            P11C4aEvidence.cue(output, "enter-config-terminal.ready");
        }
        if (state == null || state.outcome() != P11TransitionProtocol.Outcome.COMPLETED
                || P11C4aNativeObservations.count(connection, frameEvent()) <= beforeNativeFrames) { return; }
        var expectedKind = step == 3 ? P11TransitionProtocol.Kind.END
                : step == 4 ? P11TransitionProtocol.Kind.RETURN_TO_WORLD : P11TransitionProtocol.Kind.DEATH;
        if (state.kind() != expectedKind) { return; }
        require(step != 3 || !P11C4aPortalProbe.selected() || portalComplete, "PORTAL_CLIENT_COMPONENTS_NOT_COMPLETE");
        require(step != 4 || completedConfigurationScene > 0 && state.sceneSerial() > completedConfigurationScene
                && state.requestSeq() == 0, "RETURN_NOT_AFTER_INDEPENDENT_CONFIG_TERMINAL");
        require(minecraft.player != null && minecraft.player.getUUID().equals(id)
                && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection,
                "CLIENT_SUCCESSOR_NOT_CURRENT");
        if (step == 3 && !host && role.equals("a")) {
            // Before this receipt allows the server to start CONFIG: no diagnostic arrival race.
            P11C4aConfigPrimaryProbe.arm(connection, output, false);
        }
        P11C4aEvidence.write(output, scene + ".json", summary(minecraft, "ACTUAL_CLIENT_FRAME_AND_COMPLETED_STATE_OBSERVED"));
        if (step == 4) { P11C4aConfigPrimaryProbe.finish(connection); }
        completedRespawnFrames = P11C4aNativeObservations.count(connection, P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN);
        step++; activeStep = false; ticks = 0;
    }

    private static boolean input(Minecraft minecraft, P11C4aClientInputProbe.Action action) {
        nativeCall = true;
        try { return P11C4aClientInputProbe.act(minecraft, action); }
        finally { nativeCall = false; }
    }

    private static void reload(Minecraft minecraft) throws IOException {
        if (P11C4aNativeSenderProbe.selected() && reloadStep == 1) {
            if (secondEndComplete) { return; }
            if (!secondEndActive) {
                if (!P11C4aEvidence.cuePresent(serverOutput, role + "-second-end-prepare.ready")) { return; }
                P11C4aNativeSenderClientProbe.startSecondEnd(minecraft);
                secondEndActive = true; activeStep = true; ticks = 0;
            }
            if (P11C4aNativeSenderClientProbe.tickSecondEnd(minecraft)) {
                secondEndComplete = true; activeStep = false; ticks = 0;
            }
            return;
        }
        if (reloadStep >= P11C4aServerHarness.RELOAD_STEPS.size()) { firstTry(minecraft); return; }
        var mode = P11C4aServerHarness.RELOAD_STEPS.get(reloadStep);
        String label = mode == P11C4aReloadBlockerProbe.Mode.FOP ? "reload-fop" : "reload-qctx";
        if (!reloadActive) {
            boolean uiCallback = mode == P11C4aReloadBlockerProbe.Mode.FOP
                    && P11C4aScenario.MODE == P11C4aScenario.Mode.UI_CALLBACK;
            boolean uiHeld = mode == P11C4aReloadBlockerProbe.Mode.FOP
                    && P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD;
            String cue = P11C4aNativeSenderProbe.selected() ? role + "-native-senders-prepare.ready"
                    : uiCallback || uiHeld ? role + "-ui-input-prepare.ready" : role + '-' + label + ".ready";
            if (!P11C4aEvidence.cuePresent(serverOutput, cue)) { return; }
            P11C4aReloadClientProbe.start(minecraft, connection, mode, output);
            if (uiCallback) { P11C4aUiInputProbe.start(minecraft, connection, output); }
            if (uiHeld) { P11C4aUiHeldInputProbe.start(minecraft, connection, output); }
            if (P11C4aNativeSenderProbe.selected()) { P11C4aNativeSenderClientProbe.start(minecraft, connection, output); }
            reloadActive = true; activeStep = true; ticks = 0;
            if (uiCallback || uiHeld) { P11C4aEvidence.cue(output, "ui-input-armed.ready"); }
            if (P11C4aNativeSenderProbe.selected()) { P11C4aEvidence.cue(output, "native-senders-armed.ready"); }
        }
        nativeCall = true;
        try {
            if (P11C4aReloadClientProbe.tick(minecraft)) {
                reloadStep++; reloadActive = false; activeStep = false; ticks = 0;
            }
        } finally { nativeCall = false; }
    }

    private static P11C4aNativeObservations.Event frameEvent() {
        return step == 4 ? P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN : P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN;
    }

    private static void firstTry(Minecraft minecraft) throws IOException {
        if (firstTryComplete) { metadataH(minecraft); return; }
        if (!firstTryActive) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-first-try.ready")) { return; }
            P11C4aFirstTryClientProbe.start(minecraft, connection, output);
            firstTryActive = true; activeStep = true; ticks = 0;
        }
        nativeCall = true;
        try {
            if (P11C4aFirstTryClientProbe.tick(minecraft)) {
                firstTryComplete = true; firstTryActive = false; activeStep = false; ticks = 0;
            }
        } finally { nativeCall = false; }
    }

    private static Map<String, Object> summary(Minecraft minecraft, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("case", System.getProperty("gramarye.p11.online.case"));
        values.put("runId", runId); values.put("role", role); values.put("phase", phase.name());
        values.put("phaseTicks", ticks); values.put("normalStep", step); values.put("nativeLogins", logins);
        values.put("completedReloadModes", reloadStep);
        values.put("uiInputCallback", P11C4aUiInputProbe.pending());
        values.put("uiInputHeld", P11C4aUiHeldInputProbe.pending());
        values.put("firstTryComplete", firstTryComplete);
        values.put("metadataHStage", metadataHStage); values.put("metadataHComplete", metadataHComplete);
        values.put("parkingActive", parkingActive); values.put("parkingComplete", parkingComplete);
        values.put("configResetActive", configResetActive); values.put("configResetComplete", configResetComplete);
        values.put("productionJarSha256", product); values.put("identityPseudonym", id == null ? null : P11C4aEvidence.pseudonym(id));
        values.put("input", P11C4aClientInputProbe.snapshot(minecraft));
        values.put("nativeAndWire", P11C4aNativeObservations.snapshot(connection));
        values.put("screenVisualAppearanceClaimed", false); values.put("inputKind", "CONTROLLED_ORIGINAL_WINDOW_CALLBACK");
        values.put("nativeLoginDisconnectObservations", loginDisconnectObservations);
        values.put("nativeLoginDisconnectCategory", loginDisconnectCategory);
        return values;
    }

    private static void hostExpiry(Minecraft minecraft) throws IOException {
        if (!hostExpiryActive) {
            if (!publishHostIfNeeded(minecraft)) { return; }
            if (!P11C4aEvidence.cuePresent(serverOutput, "host-expiry-arm.ready")) { return; }
            P11C4aHostExpiryClientProbe.start(minecraft, connection, role, output); hostExpiryActive = true;
        }
        nativeCall = true;
        try {
            if (P11C4aHostExpiryClientProbe.tick(minecraft)) {
                P11C4aEvidence.write(output, "result.json", summary(minecraft,
                        "HOST_EXPIRY_NATIVE_TERMINAL_OBSERVATIONS_K_AND_DATA_REQUIRE_SERVER_RECEIPT"));
                P11C4aHostExpiryClientProbe.release(); phase = Phase.TERMINAL; minecraft.stop();
            }
        } finally { nativeCall = false; }
    }

    private static void hostLeave(Minecraft minecraft) throws IOException {
        if (!hostLeaveActive) {
            if (!P11C4aEvidence.cuePresent(serverOutput, "host-leave-arm.ready")) { return; }
            P11C4aHostLeaveClientProbe.start(minecraft, connection, role, output);
            hostLeaveActive = true;
        }
        nativeCall = true;
        try {
            if (P11C4aHostLeaveClientProbe.tick(minecraft)) {
                P11C4aEvidence.write(output, "result.json", summary(minecraft,
                        "HOST_LEAVE_NATIVE_TERMINAL_ONLY_FULL_MATRIX_PENDING"));
                P11C4aHostLeaveClientProbe.release();
                connection = null; id = null; phase = Phase.TERMINAL; minecraft.stop();
            }
        } finally { nativeCall = false; }
    }

    private static void nativeError(Minecraft minecraft) throws IOException {
        require(!host && (role.equals("a") || role.equals("b")) && ++nativeErrorTicks <= 2400,
                "ERROR_CLIENT_ROLE_OR_DEADLINE");
        if (!nativeErrorArmed) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + (P11C4aSubmissionRejectProbe.selected() ? "-submission.ready" : "-native-error.ready"))) { return; }
            require(connection != null && connection.isConnected() && connection.isEncrypted()
                    && !connection.isMemoryConnection() && minecraft.player != null && minecraft.player.isAlive()
                    && minecraft.level != null && minecraft.getConnection() != null
                    && minecraft.getConnection().getConnection() == connection && logins == 1,
                    "ERROR_CLIENT_ORIGINAL_FIRST_PLAY");
            if (role.equals("a")) {
                if (P11C4aSubmissionRejectProbe.selected()) { P11C4aSubmissionClientProbe.start(minecraft, connection, output); }
                else { P11C4aNativeErrorClientProbe.start(minecraft, connection, output, P11C4aScenario.nativeErrorMode()); }
            } else {
                P11C4aEvidence.write(output, P11C4aSubmissionRejectProbe.selected() ? "submission-peer-armed.json" : "native-error-peer-armed.json", Map.of(
                        "status", "ACTUAL_UNMODIFIED_PEER_CONNECTION_ARMED_NOT_ACCEPTANCE", "nativeLogins", logins));
            }
            nativeErrorArmed = true;
            if (P11C4aCompleteBFaultProbe.selected()) { P11C4aCompleteBFaultClientProbe.start(minecraft, connection, output, role); }
        }
        nativeCall = true;
        try {
            if (role.equals("a") && (!P11C4aCompleteBFaultProbe.selected() || !nativeErrorClientObserved)) {
                if (P11C4aSubmissionRejectProbe.selected() ? P11C4aSubmissionClientProbe.tick(minecraft)
                        : P11C4aNativeErrorClientProbe.tick(minecraft)) { nativeErrorClientObserved = true; }
            } else if (role.equals("b") && connection.isConnected() && !P11C4aCompleteBFaultProbe.selected()) {
                if (P11C4aSubmissionRejectProbe.selected()) { P11C4aSubmissionClientProbe.peer(minecraft, connection, output, serverOutput); }
                require(minecraft.player != null && minecraft.player.isAlive() && minecraft.level != null
                        && minecraft.player.getUUID().equals(id) && minecraft.getConnection() != null
                        && minecraft.getConnection().getConnection() == connection && logins == 1
                        && P11C4aNativeObservations.count(connection,
                                P11C4aNativeObservations.Event.CLIENT_RESPAWN_RETURN) == 0,
                        "ERROR_PEER_CHANGED_BEFORE_NATIVE_SERVER_STOP");
            }
            if (P11C4aCompleteBFaultProbe.selected()
                    && !P11C4aCompleteBFaultClientProbe.tick(minecraft, nativeErrorClientObserved)) { return; }
            if (connection.isConnected() || !P11C4aCompleteBFaultProbe.selected() && !(minecraft.screen instanceof DisconnectedScreen)
                    || minecraft.player != null || minecraft.level != null
                    || !P11C4aEvidence.receiptPresent(serverOutput, "native-error-server-terminal.json")) { return; }
            require(!role.equals("a") || nativeErrorClientObserved, "ERROR_A_CLIENT_POLICY_PROOF_MISSING");
            P11C4aEvidence.write(output, "native-error-terminal.json", Map.of(
                    "status", "ACTUAL_ORIGINAL_CONNECTION_TERMINAL_SERVER_STOP_CAUSE_SEPARATE",
                    "role", role, "nativeDisconnectedScreen", minecraft.screen instanceof DisconnectedScreen, "exactConnectionClosed", true,
                    "nativePlayerPresent", false, "nativeLevelPresent", false,
                    "manualDisconnectInvoked", P11C4aCompleteBFaultClientProbe.exitInvoked(), "serverErrorCauseInferred", false));
            P11C4aEvidence.write(output, "result.json", summary(minecraft,
                    "NATIVE_ERROR_TERMINAL_OBSERVED_REQUIRES_EXACT_SERVER_POLICY_RECEIPTS"));
            P11C4aNativeErrorClientProbe.release(); P11C4aSubmissionClientProbe.release(); P11C4aCompleteBFaultClientProbe.release(); connection = null; id = null;
            phase = Phase.TERMINAL; minecraft.stop();
        } finally { nativeCall = false; }
    }

    private static void fail(Minecraft minecraft, String code) {
        P11C4aCompleteBFaultClientProbe.release();
        P11C4aSubmissionClientProbe.release();
        P11C4aConfigCatchClientProbe.release();
        P11C4aHeldClientProbe.release();
        P11C4aTerminalStatusClientProbe.release();
        P11C4aNativeErrorClientProbe.release();
        if (P11C4aMalformedWireProbe.selected()) { P11C4aMalformedClientProbe.release(); }
        P11C4aRequiredClientProbe.release();
        P11C4aPreplayChatClientProbe.release();
        P11C4aConfigDepartureProbe.failureCleanup();
        P11C4aHostLeaveProbe.failureCleanup();
        P11C4aHostExpiryProbe.failureCleanup();
        try {
            if (output != null) {
                var result = new LinkedHashMap<>(summary(minecraft, "FAIL_FIXED_CLIENT_STAGE"));
                result.put("failure", code);
                P11C4aEvidence.write(output, "result.json", result);
            }
        }
        catch (Exception | LinkageError ignored) { }
        P11C4aAckRejectionClientProbe.release();
        P11C4aReloadClientProbe.release();
        P11C4aNativeSenderClientProbe.release();
        P11C4aPortalClientProbe.abort();
        P11C4aFirstTryClientProbe.abort();
        P11C4aMetadataHClientProbe.release();
        P11C4aParkingClientProbe.release();
        P11C4aConfigResetClientProbe.release();
        P11C4aConfigParkingResetClientProbe.release();
        phase = Phase.TERMINAL;
        try {
            if (minecraft.level != null) { minecraft.level.disconnect(); }
            minecraft.disconnect(new TitleScreen());
        } finally { connection = null; id = null; minecraft.stop(); }
    }

    private static int port() { return P11C4aEvidence.port(); }

    private static void metadataH(Minecraft minecraft) throws IOException {
        if (metadataHComplete) { parking(minecraft); return; }
        if (metadataHStage == 0) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-metadata-h-enter.ready")) { return; }
            var baseline = P11ClientTransitions.view();
            require(!role.equals("b") && logins == 2 && minecraft.player != null && minecraft.level != null
                    && minecraft.getConnection() != null && minecraft.getConnection().getConnection() == connection
                    && baseline != null && baseline.outcome() == P11TransitionProtocol.Outcome.COMPLETED,
                    "METADATA_H_CLIENT_ARM_BASELINE");
            metadataHLogins = logins;
            metadataHStarts = P11C4aNativeObservations.count(connection,
                    P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN);
            metadataHLoginFrames = P11C4aNativeObservations.count(connection,
                    P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN);
            metadataHConnectionEpoch = baseline.connectionEpoch(); metadataHScene = baseline.sceneSerial();
            metadataHStage = 1; activeStep = true; ticks = 0;
            P11C4aEvidence.write(output, "metadata-h-armed.json", summary(minecraft, "SECOND_CONFIG_CLIENT_ARMED_NOT_ACCEPTANCE"));
            return;
        }
        if (metadataHStage == 1) {
            var state = P11ClientTransitions.view();
            if (state == null || state.scope() != P11TransitionProtocol.Scope.CONFIG
                    || state.kind() != P11TransitionProtocol.Kind.ENTER_CONFIG
                    || state.outcome() != P11TransitionProtocol.Outcome.COMPLETED) { return; }
            require(state.connectionEpoch() == metadataHConnectionEpoch && state.sceneSerial() > metadataHScene
                    && state.actorGeneration() == 0 && state.targetActorGeneration() == 0 && state.requestSeq() == 0
                    && connection.getPacketListener() instanceof ClientConfigurationPacketListenerImpl
                    && minecraft.player == null && minecraft.level == null && logins == metadataHLogins
                    && P11C4aNativeObservations.count(connection,
                            P11C4aNativeObservations.Event.CLIENT_START_CONFIGURATION_RETURN) == metadataHStarts + 1
                    && P11C4aNativeObservations.count(connection,
                            P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN) == metadataHLoginFrames,
                    "METADATA_H_CLIENT_INDEPENDENT_CONFIG_TERMINAL");
            P11C4aEvidence.write(output, "metadata-h-config-terminal.json",
                    summary(minecraft, "SECOND_ACTUAL_CONFIG_TERMINAL_WITHOUT_H_RETURN_REQUEST"));
            metadataHStage = 2;
            P11C4aEvidence.cue(output, "metadata-h-config-terminal.ready");
            return;
        }
        require(metadataHStage == 2, "METADATA_H_CLIENT_PHASE");
        if (!metadataHActive) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-reload-h.ready")) { return; }
            P11C4aMetadataHClientProbe.start(minecraft, connection, output);
            metadataHActive = true;
        }
        nativeCall = true;
        try {
            if (P11C4aMetadataHClientProbe.tick(minecraft)) {
                require(logins == metadataHLogins + 1 && logins == 3
                        && P11C4aNativeObservations.count(connection,
                                P11C4aNativeObservations.Event.CLIENT_LOGIN_RETURN) == metadataHLoginFrames + 1,
                        "METADATA_H_CLIENT_NOT_ONE_ADDITIONAL_LOGIN");
                metadataHComplete = true; metadataHActive = false; metadataHStage = 3;
                activeStep = false; ticks = 0;
            }
        } finally { nativeCall = false; }
    }

    private static void parking(Minecraft minecraft) throws IOException {
        if (parkingComplete) {
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET) { configReset(minecraft); }
            return;
        }
        if (!parkingActive) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-parking-arm.ready")) { return; }
            require(!host && (role.equals("b") || metadataHComplete), "PARK_CLIENT_ORCHESTRATION_OWNER");
            parkingLogins = logins;
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.PARKING_TRANSFER) {
                P11C4aParkingClientProbe.startPendingTransfer(minecraft, connection, role, output, serverOutput);
            } else if (P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_PARKING_RESET) {
                P11C4aConfigParkingResetClientProbe.start(minecraft, connection, role, output, serverOutput);
            } else { P11C4aParkingClientProbe.start(minecraft, connection, role, output, serverOutput); }
            parkingActive = true; activeStep = true; ticks = 0;
        }
        nativeCall = true;
        try {
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_PARKING_RESET
                    ? P11C4aConfigParkingResetClientProbe.tick(minecraft) : P11C4aParkingClientProbe.tick(minecraft)) {
                require(logins == parkingLogins + 1, "PARK_CLIENT_ORCHESTRATION_EXTRA_LOGIN");
                parkingComplete = true; parkingActive = false; activeStep = false; ticks = 0;
            }
        } finally { nativeCall = false; }
    }
    private static void configReset(Minecraft minecraft) throws IOException {
        if (configResetComplete) { return; }
        if (!configResetActive) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + "-config-reset-arm.ready")) { return; }
            require(!host && parkingComplete && P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET,
                    "RESET_CLIENT_ORCHESTRATION_OWNER");
            configResetLogins = logins;
            P11C4aConfigResetClientProbe.start(minecraft, connection, output);
            configResetActive = true; activeStep = true; ticks = 0;
        }
        nativeCall = true;
        try {
            if (P11C4aConfigResetClientProbe.tick(minecraft)) {
                require(logins == configResetLogins + 1, "RESET_CLIENT_ORCHESTRATION_EXTRA_LOGIN");
                configResetComplete = true; configResetActive = false; activeStep = false; ticks = 0;
            }
        } finally { nativeCall = false; }
    }
    private static void transition(Phase next) { phase = next; ticks = 0; }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
}
