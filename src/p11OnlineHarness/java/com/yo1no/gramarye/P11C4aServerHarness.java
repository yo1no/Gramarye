package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated genuine server causes; this companion never grants a transition attempt or replays a handler. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11C4aServerHarness {
    static final List<String> NORMAL_STEPS = List.of("death-button", "death-negative-confirm",
            "death-immediate", "end", "enter-config");
    static final List<P11C4aReloadBlockerProbe.Mode> RELOAD_STEPS = List.of(
            P11C4aReloadBlockerProbe.Mode.FOP, P11C4aReloadBlockerProbe.Mode.QCTX);
    private static final Map<Connection, UUID> AUTH = new IdentityHashMap<>();
    private static final Map<String, Actor> ACTORS = new LinkedHashMap<>();
    private static volatile MinecraftServer server;
    private static volatile boolean authFault;
    private static Path output;
    private static boolean integrated;
    private static boolean ready;
    private static boolean firstCue;
    private static boolean secondCue;
    private static boolean terminal;
    private static int ticks;
    private static int step = -1;
    private static int stepTicks;
    private static ServerPlayer original;
    private static long beforeRespawns;
    private static long beforeTries;
    private static long beforeFrames;
    private static Portal portal;
    private static int endStage;
    private static int normalReports;
    private static int finalLogouts;
    private static boolean returnRequested;
    private static int reloadStep;
    private static boolean reloadStarted;
    private static boolean hostLeaveStarted;
    private static boolean hostExpiryStarted;
    private static boolean departureStarted, departureFinished;
    private static boolean uiInputPrepareSent;
    private static int uiInputPrepareTicks;
    private static boolean nativeErrorStarted;
    private static int nativeErrorLogouts;
    private static String nativeErrorCleanupCause = "NO_HARNESS_HALT_REQUEST";

    private P11C4aServerHarness() {}

    public static synchronized void authenticated(MinecraftServer exact, Connection connection, UUID id) {
        if (!P11C4aEvidence.enabled()) { return; }
        if (exact != server || connection == null || id == null || AUTH.size() >= 4 || AUTH.containsKey(connection)) {
            authFault = true; return;
        }
        if (P11C4aRequiredProbe.selected()) { P11C4aRequiredProbe.authenticated(exact, connection, id); return; }
        AUTH.put(connection, id);
        P11C4aConfigDepartureProbe.authenticated(exact, connection, id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        if (!P11C4aEvidence.enabled()) { return; }
        try {
            server = event.getServer();
            integrated = P11C4aEvidence.property("case").equals("c4a-host-lan");
            require(server.usesAuthentication() && integrated == server.isSingleplayer()
                    && integrated != server.isDedicatedServer(), "WRONG_C4A_SERVER_TOPOLOGY");
            output = P11C4aEvidence.reserve("server");
            P11OnlineInputs.verifyFrozenJar();
            if (!integrated) { ready(); }
        } catch (Exception | LinkageError failure) { fail("SERVER_STARTUP_" + P11C4aEvidence.failureCode(failure)); }
    }

    private static void ready() throws IOException {
        require(!ready && (!integrated || server.isPublished()), "SERVER_NOT_PUBLISHED");
        require(server.getPort() == P11C4aEvidence.port(), "READY_NATIVE_PORT_MISMATCH");
        ready = true;
        P11C4aEvidence.write(output, "ready.json", Map.of(
                "status", integrated ? "ONLINE_INTEGRATED_PUBLISHED_NO_PARTNER_AUTH_CLAIM" : "ONLINE_DEDICATED_READY_NO_AUTH_CLAIM",
                "case", P11C4aEvidence.property("case"), "runId", P11C4aEvidence.property("runId"),
                "productionJarSha256", P11OnlineInputs.verifyFrozenJar(),
                "onlineMode", true, "integrated", integrated, "expectedPlayers", 2,
                "configurationSha256", P11C4aLoadedConfiguration.hash()));
        if (P11C4aRequiredProbe.selected()) { P11C4aRequiredProbe.start(server, output); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (server == null || terminal || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) { return; }
        try {
            require(server.isSameThread() && !player.isFakePlayer(), "INVALID_C4A_NATIVE_ACTOR");
            if (P11C4aRequiredProbe.selected()) { P11C4aRequiredProbe.login(player); return; }
            var connection = player.connection.getConnection();
            var existing = ACTORS.values().stream().filter(value -> value.connection == connection).findFirst().orElse(null);
            if (existing != null) {
                boolean normalReturn = step == 4 && existing.logins == 1;
                boolean metadataReturn = firstTryComplete && metadataHStage == 3
                        && existing.logins == metadataHLogins && metadataHLogins == 2;
                boolean parkingReturn = parkingStarted && !parkingComplete
                        && existing.role.equals(integrated ? "b" : "a") && existing.logins == parkingLogins;
                boolean resetReturn = P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET
                        && configResetStarted && !configResetComplete
                        && existing.role.equals(integrated ? "b" : "a") && existing.logins == configResetLogins;
                require(((existing.role.equals(integrated ? "host" : "a") && (normalReturn || metadataReturn))
                        || parkingReturn || resetReturn || P11C4aTerminalStatusProbe.allowsLogin(connection, existing.role, existing.logins))
                        && existing.id.equals(player.getUUID()) && existing.current != player,
                        "UNEXPECTED_SAME_CONNECTION_LOGIN");
                existing.current = player; existing.logins++; return;
            }
            boolean host = integrated && connection.isMemoryConnection() && server.isSingleplayerOwner(player.getGameProfile());
            if (!host) {
                UUID authenticated;
                synchronized (P11C4aServerHarness.class) { authenticated = AUTH.remove(connection); }
                require(authenticated != null && authenticated.equals(player.getUUID()) && connection.isEncrypted()
                        && !connection.isMemoryConnection(), "MISSING_EXACT_HAS_JOINED_RECEIPT");
            }
            require(ACTORS.size() < 2 && ACTORS.values().stream().noneMatch(value -> value.id.equals(player.getUUID())),
                    "DUPLICATE_OR_EXTRA_C4A_ACTOR");
            String role = host ? "host" : ACTORS.isEmpty() ? "a" : "b";
            require(!integrated || host || ACTORS.containsKey("host"), "PARTNER_BEFORE_REAL_HOST");
            var actor = new Actor(role, player);
            ACTORS.put(role, actor);
            P11C4aEvidence.write(output, role + "-auth-1.json", Map.of(
                    "status", host ? "ORIGINAL_INTEGRATED_OWNER_NOT_ONLINE_HAS_JOINED_PROOF" : "ORIGINAL_ONLINE_HAS_JOINED_TO_EXACT_PLAY",
                    "identityPseudonym", P11C4aEvidence.pseudonym(actor.id), "memoryConnection", connection.isMemoryConnection(),
                    "encrypted", connection.isEncrypted(), "fakePlayer", false,
                    "observer", "ORIGINAL_PROFILE_RESULT_RETURN_THEN_EXACT_NATIVE_LOGIN"));
        } catch (Exception | LinkageError failure) { fail("NATIVE_LOGIN_" + P11C4aEvidence.failureCode(failure)); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (server == null || terminal || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) { return; }
        for (var actor : ACTORS.values()) {
            if (actor.id.equals(player.getUUID()) && actor.connection == player.connection.getConnection()) {
                actor.current = player; actor.respawns++; actor.lastEnd = event.isEndConquered(); return;
            }
        }
        fail("UNKNOWN_NATIVE_RESPAWN");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (P11L1HostStopProbe.selected() && event.getEntity() instanceof ServerPlayer actor) {
        P11L1HostStopProbe.logout(actor); return;
    }
        if (P11C4aTerminalStatusProbe.selected() && P11C4aTerminalStatusProbe.started()
                && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            try { P11C4aTerminalStatusProbe.logout(actor); }
            catch (Exception | LinkageError failure) { fail("TERMINAL_STATUS_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (P11C4aHeldProbe.selected() && P11C4aHeldProbe.started() && event.getEntity() instanceof ServerPlayer actor) {
            try { P11C4aHeldProbe.logout(actor); }
            catch (Exception | LinkageError failure) { fail("HELD_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (P11C4aConfigCatchProbe.selected() && P11C4aConfigCatchProbe.started()
                && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            try { P11C4aConfigCatchProbe.logout(actor); }
            catch (RuntimeException | LinkageError failure) { fail("CONFIG_CATCH_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (nativeErrorStarted && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            // Deliberately partial FRAME_SEND does not promise the normal respawn/logout shape.
            if (nativeErrorLogouts < 4) { nativeErrorLogouts++; }
            return;
        }
        if (P11C4aMalformedWireProbe.selected() && P11C4aMalformedServerProbe.started()
                && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            try { P11C4aMalformedServerProbe.logout(actor); }
            catch (RuntimeException | LinkageError failure) { fail("MALFORMED_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (P11C4aRequiredProbe.selected() && server != null && event.getEntity() instanceof ServerPlayer actor
                && actor.getServer() == server) {
            try { P11C4aRequiredProbe.logout(actor); }
            catch (RuntimeException | LinkageError failure) { fail("REQUIRED_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (server != null && preplayChatStarted && P11C4aPreplayChatProbe.selected()
                && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            try {
                if (!preplayChatFinished) {
                    require(actor == ACTORS.get("a").current && !preplayChatParkingComplete, "CHAT_EARLY_PEER_OR_SUCCESSOR_DEPARTURE");
                    return; // Existing parking's one original ENTER_CONFIG logout; no completion claim.
                }
                require(++preplayChatLogouts <= 2 && actor == ACTORS.get(preplayChatLogouts == 1 ? "b" : "a").current,
                        "CHAT_FINAL_ORIGINAL_LOGOUT_ORDER");
                if (preplayChatLogouts == 1) { P11C4aEvidence.cue(output,"a-preplay-chat-finish.ready"); }
                else { terminal = true; server.halt(false); }
            } catch (Exception | LinkageError failure) { fail("CHAT_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (P11C4aScenario.MODE == P11C4aScenario.Mode.C6_RATE_FAIRNESS && P11C4aRateFairProbe.started()
                && event.getEntity() instanceof ServerPlayer actor) {
            P11C4aRateFairProbe.logout(actor); return;
        }
        if (server != null && departureStarted && P11C4aConfigDepartureProbe.selectedMode() != null
                && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            try {
                if (ACTORS.containsKey("a") && actor == ACTORS.get("a").current) {
                    require(departureFinished && P11C4aConfigDepartureProbe.qualified(), "DEPARTURE_PEER_EARLY_LOGOUT");
                    terminal = true; server.halt(false);
                } else { P11C4aConfigDepartureProbe.logout(actor); }
            } catch (RuntimeException | LinkageError failure) { fail("DEPARTURE_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_EXPIRY && hostExpiryStarted
                && event.getEntity() instanceof ServerPlayer actor) {
            P11C4aHostExpiryProbe.logout(actor); return;
        }
        if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_LEAVE && hostLeaveStarted
                && event.getEntity() instanceof ServerPlayer actor) {
            P11C4aHostLeaveProbe.logout(actor); return;
        }
        if (server != null && P11C4aAckRejectionProbe.selectedMode() != null
                && event.getEntity() instanceof ServerPlayer actor && actor.getServer() == server) {
            try {
                if (ACTORS.containsKey("a") && actor == ACTORS.get("a").current) {
                    P11C4aAckRejectionProbe.loggedOut(actor);
                } else {
                    require(ackRejectionFinished && ACTORS.containsKey("b") && actor == ACTORS.get("b").current,
                            "ACK_UNEXPECTED_PEER_LOGOUT");
                    terminal = true; server.halt(false);
                }
            } catch (RuntimeException | LinkageError failure) { fail("ACK_LOGOUT_" + P11C4aEvidence.failureCode(failure)); }
            return;
        }
        if (server == null || !terminal || normalReports != NORMAL_STEPS.size()
                || event.getEntity().getServer() != server) { return; }
        finalLogouts++;
        if (finalLogouts == 1) {
            require(event.getEntity().getUUID().equals(ACTORS.get("b").id), "NORMAL_SHUTDOWN_PEER_ORDER");
            try { P11C4aEvidence.cue(output, (integrated ? "host" : "a") + "-normal-finish.ready"); }
            catch (IOException failure) { fail("NORMAL_SHUTDOWN_CUE"); }
        }
        if (finalLogouts == 2 && !integrated) { server.halt(false); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        if (server == null || terminal || event.getServer() != server) { return; }
        try {
            require(!authFault && ++ticks <= 24_000, "C4A_SERVER_AUTH_OR_TOTAL_DEADLINE");
            P11C4aNativeObservations.requireHealthy();
            P11C4aPeerSafetyProbe.requireHealthy();
            if (integrated && !ready && server.isPublished()) { ready(); }
            if (!ready) { return; }
            if (Boolean.parseBoolean(System.getProperty("gramarye.p11.online.readyOnly", "false"))) {
                P11C4aEvidence.write(output, "readiness-result.json", Map.of(
                        "status", "READY_ONLY_NO_AUTH_OR_C4A_ACCEPTANCE", "integrated", integrated));
                terminal = true; server.halt(false); return;
            }
            if (P11C4aRequiredProbe.selected()) {
                if (P11C4aRequiredProbe.tick()) { terminal = true; server.halt(false); }
                return;
            }
            var evidence = P11C4aEvidence.root();
            if (!firstCue && !integrated && P11C4aEvidence.receiptPresent(evidence.resolve("client-a"), "inputs.json")
                    && P11C4aEvidence.receiptPresent(evidence.resolve("client-b"), "inputs.json")) {
                firstCue = true; P11C4aEvidence.cue(output, "a-connect.ready");
            }
            var first = ACTORS.get(integrated ? "host" : "a");
            if (!secondCue && first != null && materialReady(first.current)
                    && P11C4aEvidence.receiptPresent(evidence.resolve("client-b"), "inputs.json")) {
                if (P11C4aConfigDepartureProbe.selectedMode() != null) {
                    if (!P11C4aConfigDepartureProbe.ready(first.current)) { return; }
                    P11C4aConfigDepartureProbe.start(server, first.current, output); departureStarted = true;
                }
                secondCue = true; P11C4aEvidence.cue(output, "b-connect.ready");
            }
            if (P11C4aConfigDepartureProbe.selectedMode() != null) {
                if (departureStarted) { progressConfigDeparture(); }
                return;
            }
if (ACTORS.size() != 2) { return; }
if (P11L1HostStopProbe.selected()) {
    require(integrated, "L1_HOST_STOP_TOPOLOGY");
    var hostActor = ACTORS.get("host"); var peerActor = ACTORS.get("b");
    if (!P11L1HostStopProbe.started()) {
        if (!materialReady(hostActor.current) || !materialReady(peerActor.current)) { return; }
        require(hostActor.logins == 1 && peerActor.logins == 1 && hostActor.respawns == 0
                && peerActor.respawns == 0, "L1_HOST_INITIAL_ACTORS");
        P11L1HostStopProbe.start(server, hostActor.current, peerActor.current, output);
    }
    P11L1HostStopProbe.tick(); return;
}
            if (P11C4aTerminalStatusProbe.selected()) {
                require(!integrated, "TERMINAL_STATUS_DEDICATED_ONLY");
                if (!P11C4aTerminalStatusProbe.started()) {
                    var peer = ACTORS.get("b").current;
                    if (!materialReady(first.current) || !materialReady(peer)) { return; }
                    P11C4aPeerSafetyProbe.install(server, first.current, peer, output);
                    P11C4aTerminalStatusProbe.start(server, first.current, peer, output);
                }
                if (P11C4aTerminalStatusProbe.tick(first.current)) { terminal = true; server.halt(false); }
                return;
            }
            if (P11C4aHeldProbe.selected()) {
                require(!integrated, "HELD_DEDICATED_ONLY");
                if (!P11C4aHeldProbe.started()) {
                    if (ACTORS.values().stream().anyMatch(actor -> !materialReady(actor.current))) { return; }
                    P11C4aPeerSafetyProbe.install(server, first.current, ACTORS.get("b").current, output);
                    P11C4aHeldProbe.start(server, first.current, ACTORS.get("b").current, output);
                }
                if (P11C4aHeldProbe.tick(first.current)) { terminal = true; server.halt(false); }
                return;
            }
            if (P11C4aConfigCatchProbe.selected()) {
                require(!integrated, "CONFIG_CATCH_DEDICATED_ONLY");
                if (!P11C4aConfigCatchProbe.started()) {
                    var peer = ACTORS.get("b").current;
                    if (!materialReady(first.current) || !materialReady(peer)) { return; }
                    P11C4aPeerSafetyProbe.install(server, first.current, peer, output);
                    P11C4aConfigCatchProbe.start(server, first.current, peer, output);
                }
                if (P11C4aConfigCatchProbe.tick()) { terminal = true; server.halt(false); }
                return;
            }
            if (P11C4aScenario.nativeErrorMode() != null || P11C4aSubmissionRejectProbe.selected()) { progressNativeError(first); return; }
            if (P11C4aMalformedWireProbe.selected()) {
                require(!integrated, "MALFORMED_DEDICATED_ONLY");
                if (!P11C4aMalformedServerProbe.started()) {
                    var peer = ACTORS.get("b").current;
                    if (!materialReady(first.current) || !materialReady(peer)) { return; }
                    P11C4aPeerSafetyProbe.install(server, first.current, peer, output);
                    P11C4aMalformedServerProbe.start(server, first.current, peer, output);
                }
                if (P11C4aMalformedServerProbe.tick()) { terminal = true; server.halt(false); }
                return;
            }
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_EXPIRY) { progressHostExpiry(first); return; }
            if (P11C4aPreplayChatProbe.selected()) { progressPreplayChat(); return; }
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.C6_RATE_FAIRNESS) {
                require(!integrated, "RF_DEDICATED_ONLY");
                if (!P11C4aRateFairProbe.started()) {
                    var peer = ACTORS.get("b").current;
                    if (!P11C4aRateFairProbe.ready(first.current, peer)) { return; }
                    P11C4aPeerSafetyProbe.install(server, first.current, peer, output);
                    first.current.connection.teleport(peer.getX(), peer.getY(), peer.getZ(),
                            first.current.getYRot(), first.current.getXRot());
                }
                if (P11C4aRateFairProbe.tick(server, first.current, ACTORS.get("b").current, output)) {
                    terminal = true; server.halt(false);
                }
                return;
            }
            if (P11C4aAckRejectionProbe.selectedMode() != null) { progressAckRejection(); return; }
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_LEAVE
                    && hostLeaveStarted && P11C4aHostLeaveProbe.terminalIntent()) {
                P11C4aHostLeaveProbe.tick(); return;
            }
            require(ACTORS.values().stream().allMatch(actor -> actor.connection.isConnected()
                    || P11C4aScenario.c6() && (P11C4aC6Coordinator.capacityCloseExpected(actor.connection)
                        || P11C4aC6Coordinator.expiryExpected(actor.connection))),
                    "UNEXPECTED_NATIVE_CONNECTION_CLOSED");
            if (step == NORMAL_STEPS.size()) {
                if (P11C4aRewardContinuityProbe.selected()) { P11C4aRewardContinuityProbe.finish(); terminal = true; }
                else if (P11C4aScenario.MODE == P11C4aScenario.Mode.HOST_LEAVE) { progressHostLeave(first); }
                else { progressReload(first); }
                return;
            }
            if (step < 0) {
                if (ACTORS.values().stream().anyMatch(actor -> !materialReady(actor.current))) { return; }
                P11C4aPeerSafetyProbe.install(server, first.current, ACTORS.get("b").current, output);
                for (var actor : ACTORS.values()) {
                    require(P11C4aNativeObservations.count(actor.connection, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == 1,
                            "JOIN_NATIVE_FRAME_COUNT");
                    require(P11C4aNativeObservations.tries(actor.connection) == 0, "NORMAL_JOIN_UNEXPECTED_TRY");
                    P11C4aEvidence.write(output, actor.role + "-first-native.json", observation(actor, "JOIN_NATIVE_OBSERVED"));
                }
                step = 0; start(first); return;
            }
            require(++stepTicks <= 2400, "C4A_NATIVE_STEP_DEADLINE");
            if (step == 3) { progressEnd(first); }
            if (step == 4) { progressConfiguration(first); }
            boolean returned = step == 4 ? first.logins == 2 : first.respawns == beforeRespawns + 1;
            if (!returned || !materialReady(first.current)) { return; }
            require(P11C4aNativeObservations.tries(first.connection) == beforeTries
                    + (step == 4 ? 0 : step == 3 && P11C4aPortalProbe.selected() ? 2 : 1),
                    "NORMAL_SCENE_NOT_EXACTLY_ONE_ORIGINAL_TRY");
            require(P11C4aNativeObservations.count(first.connection, step == 4
                    ? P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED
                    : P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED) == beforeFrames + 1,
                    "NORMAL_SCENE_NATIVE_FRAME_PRODUCER_COUNT");
            require(first.current != original && first.current.connection.getConnection() == first.connection
                    && server.getPlayerList().getPlayer(first.id) == first.current
                    && first.current.getAdvancements() == original.getAdvancements()
                    && first.current.getStats() == original.getStats(), "NORMAL_NATIVE_SUCCESSOR_CUSTODY");
            if (step == 3) { require(first.lastEnd && first.current.seenCredits && !first.current.wonGame, "END_NATIVE_RETURN_FLAGS"); }
            if (P11C4aRewardContinuityProbe.selected()) {
                if (step == 3) { P11C4aRewardContinuityProbe.endReturned(first.current); }
                if (step == 4 && !P11C4aRewardContinuityProbe.configurationReturned(first.current)) { return; }
            }
            var peer = ACTORS.get("b");
            require(peer.current == peer.first && peer.respawns == 0 && peer.logins == 1
                    && peer.connection.isConnected() && server.getPlayerList().getPlayer(peer.id) == peer.current,
                    "SECOND_ACCOUNT_OR_HOST_SERVER_DISTURBED");
            String scene = NORMAL_STEPS.get(step);
            if (!P11C4aEvidence.receiptPresent(evidence.resolve("client-" + first.role), scene + ".json")) { return; }
            if (step == 3 && P11C4aPortalProbe.selected()) { P11C4aPortalProbe.finish(first.current); }
            if (step == 4 && P11C4aBossProducerProbe.selected()
                    && !P11C4aBossProducerProbe.finish(first.current)) { return; }
            P11C4aEvidence.write(output, scene + ".json", observation(first, "NORMAL_NATIVE_SCENE_OBSERVED_NOT_FULL_MATRIX"));
            if (step == 4) { P11C4aConfigPrimaryProbe.finish(first.connection); }
            normalReports++;
            original = null;
            if (++step < NORMAL_STEPS.size()) { start(first); }
            else {
                P11C4aNativeObservations.requireHealthy();
                server.saveEverything(false, true, true);
                P11C4aEvidence.write(output, "normal-path-result.json", Map.of(
                        "status", "NORMAL_PATH_SUBSET_COMPLETE_FULL_C4A_MATRIX_PENDING", "normalScenes", normalReports,
                        "fullC4aAcceptance", false, "hostIsOnlineHasJoinedProof", false,
                        "twoDistinctActualUuids", true, "peerStillSameActorAndConnection", true));
                // The normal subset is sealed before the separate real reload probes.
                // Those probes never retroactively rewrite this subset's observations.
            }
        } catch (Exception | LinkageError failure) { fail("NATIVE_SCENE_" + P11C4aEvidence.failureCode(failure)); }
    }

    private static boolean preplayChatStarted, preplayChatParkingComplete, preplayChatFinished;
    private static int preplayChatLogouts, preplayChatTicks;
    private static void progressPreplayChat() throws IOException {
        require(!integrated && ++preplayChatTicks <= 2400,"CHAT_ORCHESTRATION_OWNER_OR_DEADLINE");
        var actor = ACTORS.get("a"); var peer = ACTORS.get("b");
        var client = P11C4aEvidence.root().resolve("client-a");
        if (!preplayChatStarted) {
            if (!materialReady(actor.current) || !materialReady(peer.current)
                    || !P11C4aEvidence.receiptPresent(client,"preplay-chat-ready.json")) { return; }
            require(actor.logins == 1 && peer.logins == 1
                    && P11C4aNativeObservations.tries(actor.connection) == 0
                    && P11C4aNativeObservations.tries(peer.connection) == 0,"CHAT_EXACT_INITIAL_NATIVE_JOINS");
            require(!parkingStarted && !parkingComplete, "CHAT_PARKING_ALREADY_STARTED");
            P11C4aPeerSafetyProbe.install(server,actor.current,peer.current,output);
            P11C4aPreplayChatProbe.start(server,actor.current,peer.current,output);
            parkingLogins = actor.logins; parkingStarted = true;
            P11C4aParkingProbe.start(server,actor.current,"a",output);
            preplayChatStarted = true; return;
        }
        if (preplayChatFinished) { return; }
        if (!preplayChatParkingComplete) {
            if (!P11C4aParkingProbe.tick(server.getPlayerList().getPlayer(actor.id),client)) { return; }
            require(actor.logins == parkingLogins + 1, "CHAT_PARKING_ORIGINAL_RETURN_LOGIN_COUNT");
            parkingComplete = true; preplayChatParkingComplete = true;
        }
        if (!P11C4aPreplayChatProbe.finish(actor.current)) { return; }
        require(actor.logins == 2 && peer.logins == 1,"CHAT_EXACT_RETURN_LOGIN_COUNT");
        preplayChatFinished = true;
        P11C4aEvidence.cue(output,"b-preplay-chat-finish.ready");
    }

    private static void progressConfigDeparture() throws IOException {
        require(!integrated && server.isDedicatedServer(), "DEPARTURE_DEDICATED_ONLY");
        if (departureFinished) { return; }
        var target = ACTORS.get("b");
        if (!P11C4aConfigDepartureProbe.tick(target == null ? null : target.current)) { return; }
        departureFinished = true;
        P11C4aEvidence.cue(output, "a-config-departure-finish.ready");
    }

    private static boolean ackRejectionStarted, ackRejectionFinished;
    private static int ackRejectionTicks;
    private static void progressAckRejection() throws IOException {
        require(!integrated && server.isDedicatedServer() && ++ackRejectionTicks <= 1200,
                "ACK_ORCHESTRATION_TOPOLOGY_OR_DEADLINE");
        var actor = ACTORS.get("a"); var peer = ACTORS.get("b");
        require(actor != null && peer != null && actor.logins == 1 && peer.logins == 1
                && actor.current == actor.first && peer.current == peer.first
                && actor.respawns == 0 && peer.respawns == 0
                && P11C4aNativeObservations.tries(actor.connection) == 0
                && P11C4aNativeObservations.tries(peer.connection) == 0,
                "ACK_ORCHESTRATION_NOT_ORIGINAL_AUTHENTICATED_PLAY");
        if (ackRejectionFinished) { return; }
        if (!ackRejectionStarted) {
            require(actor.connection.isConnected() && peer.connection.isConnected(), "ACK_EARLY_NATIVE_DISCONNECT");
            if (!materialReady(actor.current) || !materialReady(peer.current)) { return; }
            for (var participant : List.of(actor, peer)) {
                var source = P11NativeStorageBoundary.nativeSourceOwner(participant.current);
                var body = source == null ? null : source.nativeRecipient(participant.current);
                require(body != null, "ACK_ORCHESTRATION_SOURCE_MISSING");
                if (body.account.metadata != null
                        || body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] != 0
                        || body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] != 0
                        || body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] != 0) { return; }
                require(nativeCount(participant, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == 1,
                        "ACK_ORCHESTRATION_INITIAL_LOGIN_FRAME");
            }
            P11C4aPeerSafetyProbe.install(server, actor.current, peer.current, output);
            require(actor.current.isAlive(), "ACK_TARGET_ALREADY_UNSAFE");
            float healthBefore = actor.current.getHealth();
            // Both real actors stay in the ordinary owned glass enclosure; no immunity or health repair.
            actor.current.connection.teleport(peer.current.getX() + 1.0, peer.current.getY(), peer.current.getZ(),
                    actor.current.getYRot(), actor.current.getXRot());
            require(actor.current.getHealth() == healthBefore, "ACK_SAFETY_TELEPORT_CHANGED_HEALTH");
            P11C4aEvidence.write(output, "ack-target-safety.json", Map.of(
                    "status", "ORIGINAL_SAME_WORLD_TELEPORT_IN_OWNED_ENCLOSURE_NOT_IMMUNITY",
                    "healthBefore", healthBefore, "healthAfter", actor.current.getHealth()));
            P11C4aAckRejectionProbe.start(server, actor.current, peer.current,
                    P11C4aAckRejectionProbe.selectedMode(), output);
            ackRejectionStarted = true;
            return;
        }
        if (!P11C4aAckRejectionProbe.tick(P11C4aEvidence.root().resolve("client-a"))) { return; }
        ackRejectionFinished = true;
        P11C4aEvidence.cue(output, "b-ack-rejection-finish.ready");
    }

    private static void start(Actor actor) throws IOException {
        original = actor.current; beforeRespawns = actor.respawns; stepTicks = 0;
        beforeTries = P11C4aNativeObservations.tries(actor.connection);
        beforeFrames = P11C4aNativeObservations.count(actor.connection, step == 4
                ? P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED : P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED);
        String scene = NORMAL_STEPS.get(step);
        P11C4aEvidence.cue(output, actor.role + '-' + scene + ".ready");
        server.getGameRules().getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(step == 2, server);
        switch (step) {
            case 0, 1, 2 -> original.kill();
            case 3 -> { require(!original.seenCredits && !original.wonGame, "END_REQUIRES_NATIVE_FRESH_CREDITS"); endStage = 0; portal = portal(original); }
            case 4 -> {
                if (P11C4aBossProducerProbe.selected()) {
                    P11C4aBossProducerProbe.arm(server, original, ACTORS.get("b").current, output);
                }
                if (!integrated) {
                    P11C4aConfigPrimaryProbe.arm(original.connection.getConnection(), output, true);
                }
                if (P11C4aRewardContinuityProbe.selected()) { P11C4aRewardContinuityProbe.beforeConfiguration(original); }
                original.connection.switchToConfig();
                if (P11C4aRewardContinuityProbe.selected()) { P11C4aRewardContinuityProbe.configurationSwitched(); }
            }
            default -> throw new IllegalStateException("UNKNOWN_C4A_NORMAL_STEP");
        }
    }

    private static void progressEnd(Actor actor) throws IOException {
        if (endStage == 0 && original.level().dimension() == Level.END && !original.wonGame) {
            restorePortal(); endStage = 1;
        }
        if (endStage == 1 && P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("client-" + actor.role), "end-view.ready")) {
            if (P11C4aRewardContinuityProbe.selected()) {
                P11C4aRewardContinuityProbe.beforeEndExit(original, ACTORS.get("b").current, output);
            }
            if (P11C4aPortalProbe.selected()) {
                if (!P11C4aPortalProbe.prepareExit(original, output)) { return; }
                endStage = 2;
                return;
            }
            portal = portal(original); endStage = 2;
        }
        if (endStage == 2 && original.wonGame) { restorePortal(); endStage = 3; }
    }

    private static void progressConfiguration(Actor actor) throws IOException {
        if (returnRequested || !P11C4aEvidence.cuePresent(P11C4aEvidence.root()
                .resolve("client-" + actor.role), "enter-config-terminal.ready")) { return; }
        require(actor.connection.isConnected()
                && actor.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl,
                "RETURN_REQUIRES_ACTUAL_CONFIGURATION_LISTENER");
        var listener = (ServerConfigurationPacketListenerImpl) actor.connection.getPacketListener();
        require(listener.getConnection() == actor.connection && listener.getMainThreadEventLoop() == server
                && server.getPlayerList().getPlayer(actor.id) == null && actor.logins == 1
                && P11C4aNativeObservations.count(actor.connection,
                        P11C4aNativeObservations.Event.SWITCH_CALL_RETURN) == 1
                && P11C4aNativeObservations.count(actor.connection,
                        P11C4aNativeObservations.Event.START_CONFIGURATION_SEND_RETURN) == 1
                && P11C4aNativeObservations.count(actor.connection,
                        P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED) == 1
                && P11C4aNativeObservations.count(actor.connection,
                        P11C4aNativeObservations.Event.CONFIG_CALL_RETURN) == 1
                && P11C4aNativeObservations.count(actor.connection,
                        P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == beforeFrames,
                "RETURN_BEFORE_INDEPENDENT_CONFIG_TERMINAL");
        P11C4aEvidence.write(output, "enter-config-terminal.json",
                observation(actor, "ACTUAL_CONFIG_WITH_CLIENT_TERMINAL_OBSERVED_BEFORE_RETURN_REQUEST"));
        // ACK installs CONFIG but intentionally does not request return. This separate
        // original server API is invoked only after the client observed its terminal.
        returnRequested = true;
        listener.returnToWorld();
    }

    private static void progressHostExpiry(Actor host) throws IOException {
        require(integrated, "HOST_EXPIRY_REQUIRES_ACTUAL_HOST");
        var peer = ACTORS.get("b");
        if (!hostExpiryStarted) {
            if (!materialReady(host.current) || !materialReady(peer.current)
                    || !P11C4aHostExpiryProbe.ready(host.current) || !P11C4aHostExpiryProbe.ready(peer.current)) { return; }
            require(host.logins == 1 && peer.logins == 1 && host.respawns == 0 && peer.respawns == 0
                    && P11C4aNativeObservations.tries(host.connection) == 0
                    && P11C4aNativeObservations.tries(peer.connection) == 0, "HOST_EXPIRY_INITIAL_REAL_JOIN_COUNTS");
            P11C4aPeerSafetyProbe.install(server, host.current, peer.current, output);
            P11C4aHostExpiryProbe.start(server, host.current, peer.current, output);
            hostExpiryStarted = true;
        }
        P11C4aHostExpiryProbe.tick();
    }

    private static void progressHostLeave(Actor host) throws IOException {
        require(integrated && normalReports == NORMAL_STEPS.size(), "HOST_LEAVE_REQUIRES_NORMAL_HOST_FIVE");
        if (!hostLeaveStarted) {
            if (!materialReady(host.current) || !materialReady(ACTORS.get("b").current)
                    || !P11C4aHostLeaveProbe.ready(host.current, ACTORS.get("b").current)) { return; }
            P11C4aHostLeaveProbe.start(server, host.current, ACTORS.get("b").current, output);
            hostLeaveStarted = true;
        }
        P11C4aHostLeaveProbe.tick();
    }

    private static void progressReload(Actor actor) throws IOException {
        var peer = ACTORS.get("b");
        if (P11C4aPortalProbe.selected()) {
            require(!integrated && normalReports == NORMAL_STEPS.size()
                    && P11C4aEvidence.receiptPresent(output, "portal-server.json")
                    && peer.current == peer.first && peer.respawns == 0 && peer.logins == 1
                    && peer.connection.isConnected(), "PORTAL_TERMINAL_NORMAL_PEER");
            P11C4aEvidence.cue(output, "b-normal-finish.ready");
            terminal = true;
            return;
        }
        if (P11C4aScenario.c6()) {
            if (P11C4aC6Coordinator.tick(server, actor.current, peer.current, output)) {
                terminal = true; server.halt(false);
            }
            return;
        }
        require(peer.connection.isConnected() && (integrated && parkingStarted
                || peer.current == peer.first && peer.respawns == 0 && peer.logins == 1
                    && server.getPlayerList().getPlayer(peer.id) == peer.current),
                "RELOAD_DISTURBED_OTHER_AUTHENTICATED_PLAYER");
        if (P11C4aNativeSenderProbe.selected() && reloadStep == 1) {
            if (P11C4aNativeSenderProbe.secondEnd(actor.current,
                    P11C4aEvidence.root().resolve("client-" + actor.role))) {
                P11C4aEvidence.write(output, "native-senders-subset-result.json", Map.of(
                        "status", "IMMEDIATE_HIDDEN_F0_AND_SECOND_END_DIMENSION_SUBSET_ONLY",
                        "peerStillSameActorAndConnection", true, "fullC4aAcceptance", false));
                P11C4aEvidence.cue(output, "b-normal-finish.ready");
                terminal = true;
            }
            return;
        }
        if (reloadStep == RELOAD_STEPS.size()) { progressFirstTry(actor); return; }
        if (!reloadStarted) {
            if (P11C4aNativeSenderProbe.selected()) {
                require(!integrated && reloadStep == 0 && ++uiInputPrepareTicks <= 2400,
                        "SENDERS_DEDICATED_PREARM_DEADLINE");
                if (!uiInputPrepareSent) {
                    uiInputPrepareSent = true;
                    P11C4aEvidence.cue(output, actor.role + "-native-senders-prepare.ready");
                    return;
                }
                if (!P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("client-" + actor.role),
                        "native-senders-armed.ready")) { return; }
                reloadStarted = true;
                P11C4aNativeSenderProbe.armImmediate(server, actor.current, actor.role, output);
                return;
            }
            if (RELOAD_STEPS.get(reloadStep) == P11C4aReloadBlockerProbe.Mode.FOP
                    && (P11C4aScenario.MODE == P11C4aScenario.Mode.UI_CALLBACK
                        || P11C4aScenario.MODE == P11C4aScenario.Mode.UI_HELD)) {
                require(++uiInputPrepareTicks <= 2400, "UI_INPUT_CLIENT_PREARM_DEADLINE");
                if (!uiInputPrepareSent) {
                    uiInputPrepareSent = true;
                    P11C4aReloadBlockerProbe.prepareUiClient(server, actor.current, actor.role, output);
                    return;
                }
                if (!P11C4aEvidence.cuePresent(P11C4aEvidence.root().resolve("client-" + actor.role),
                        "ui-input-armed.ready")) { return; }
            }
            reloadStarted = true;
            P11C4aReloadBlockerProbe.start(server, actor.current, RELOAD_STEPS.get(reloadStep), actor.role, output);
            return;
        }
        if (P11C4aNativeSenderProbe.selected() && !P11C4aNativeSenderProbe.advanceReload()) { return; }
        if (!P11C4aReloadBlockerProbe.finish(actor.current,
                P11C4aEvidence.root().resolve("client-" + actor.role))) { return; }
        if (P11C4aNativeSenderProbe.selected()) { P11C4aNativeSenderProbe.immediateFinished(); }
        reloadStarted = false;
        if (++reloadStep < RELOAD_STEPS.size()) { return; }
        P11C4aEvidence.write(output, "reload-subset-result.json", Map.of(
                "status", "ACTUAL_FOP_QCTX_RELOAD_SUBSET_COMPLETE_FULL_MATRIX_PENDING",
                "modes", RELOAD_STEPS.stream().map(Enum::name).toList(), "fullC4aAcceptance", false,
                "peerStillSameActorAndConnection", true));
    }

    private static boolean firstTryStarted;
    private static boolean firstTryComplete;
    private static long metadataHNotBefore;
    private static int metadataHStage;
    private static int metadataHTicks;
    private static int metadataHLogins;
    private static long metadataHConfigReturns, metadataHSwitchReturns, metadataHStartSends;
    private static long metadataHFactories, metadataHLoginFrames;
    private static void progressFirstTry(Actor actor) throws IOException {
        if (firstTryComplete) { progressMetadataH(actor); return; }
        if (!firstTryStarted) {
            firstTryStarted = true;
            P11C4aFirstTryProbe.start(server, actor.current, actor.role, output);
            return;
        }
        P11C4aFirstTryProbe.tick(actor.current);
        if (P11C4aFirstTryProbe.finish(actor.current,
                P11C4aEvidence.root().resolve("client-" + actor.role))) {
            firstTryComplete = true;
            // Fixture pacing only: let the actual sender's TRY bucket refill after 42.
            // Never seed sequence numbers, mutate a bucket, or auto-replay any request.
            metadataHNotBefore = System.nanoTime() + 1_000_000_000L;
        }
    }

    private static void progressMetadataH(Actor actor) throws IOException {
        if (metadataHStage == 4) { progressParking(); return; }
        require(++metadataHTicks <= 2400 && actor.role.equals(integrated ? "host" : "a")
                && actor.connection.isConnected(), "METADATA_H_ORCHESTRATION_DEADLINE_OR_OWNER");
        var clientOutput = P11C4aEvidence.root().resolve("client-" + actor.role);
        if (metadataHStage == 0) {
            if (System.nanoTime() - metadataHNotBefore < 0) { return; }
            var source = P11NativeStorageBoundary.nativeSourceOwner(actor.current);
            var body = source == null ? null : source.body(actor.current);
            require(materialReady(actor.current) && actor.logins == 2 && body != null,
                    "METADATA_H_ORCHESTRATION_ACTUAL_BASELINE");
            // A real pending initial sync is allowed to finish itself. Do not release or refresh it.
            if (body.account.metadata != null
                    || body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] != 0
                    || body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] != 0
                    || body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] != 0) { return; }
            metadataHLogins = actor.logins;
            metadataHConfigReturns = nativeCount(actor, P11C4aNativeObservations.Event.CONFIG_CALL_RETURN);
            metadataHSwitchReturns = nativeCount(actor, P11C4aNativeObservations.Event.SWITCH_CALL_RETURN);
            metadataHStartSends = nativeCount(actor, P11C4aNativeObservations.Event.START_CONFIGURATION_SEND_RETURN);
            metadataHFactories = nativeCount(actor, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED);
            metadataHLoginFrames = nativeCount(actor, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED);
            P11C4aMetadataHProbe.arm(server, actor.current, actor.role, output);
            metadataHStage = 1;
            P11C4aEvidence.cue(output, actor.role + "-metadata-h-enter.ready");
            return;
        }
        if (metadataHStage == 1) {
            if (!P11C4aEvidence.receiptPresent(clientOutput, "metadata-h-armed.json")) { return; }
            require(materialReady(actor.current) && actor.logins == metadataHLogins,
                    "METADATA_H_SWITCH_ACTOR_CHANGED");
            metadataHStage = 2;
            actor.current.connection.switchToConfig();
            return;
        }
        if (metadataHStage == 2) {
            if (!P11C4aEvidence.cuePresent(clientOutput, "metadata-h-config-terminal.ready")) { return; }
            require(actor.connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl
                    && server.getPlayerList().getPlayer(actor.id) == null
                    && actor.logins == metadataHLogins
                    && nativeCount(actor, P11C4aNativeObservations.Event.SWITCH_CALL_RETURN) == metadataHSwitchReturns + 1
                    && nativeCount(actor, P11C4aNativeObservations.Event.START_CONFIGURATION_SEND_RETURN) == metadataHStartSends + 1
                    && nativeCount(actor, P11C4aNativeObservations.Event.CONFIG_CALL_RETURN) == metadataHConfigReturns
                    && nativeCount(actor, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED) == metadataHFactories
                    && nativeCount(actor, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == metadataHLoginFrames,
                    "METADATA_H_RETURN_BEFORE_INDEPENDENT_CONFIG_TERMINAL");
            var listener = (ServerConfigurationPacketListenerImpl) actor.connection.getPacketListener();
            require(listener.getConnection() == actor.connection && listener.getMainThreadEventLoop() == server,
                    "METADATA_H_CONFIG_EXACT_OWNER");
            P11C4aEvidence.write(output, "metadata-h-config-terminal.json",
                    observation(actor, "SECOND_ACTUAL_CONFIG_TERMINAL_BEFORE_H_RETURN_REQUEST"));
            metadataHStage = 3;
            listener.returnToWorld();
            return;
        }
        require(metadataHStage == 3, "METADATA_H_ORCHESTRATION_REENTERED_TERMINAL");
        if (!P11C4aMetadataHProbe.finish(actor.current, clientOutput)) { return; }
        require(actor.logins == metadataHLogins + 1 && actor.logins == 3,
                "METADATA_H_NOT_ONE_ADDITIONAL_LOGIN");
        metadataHStage = 4;
        parkingNotBefore = System.nanoTime() + 1_000_000_000L;
    }

    private static boolean parkingStarted, parkingComplete;
    private static int parkingLogins;
    private static long parkingNotBefore;
    private static void progressParking() throws IOException {
        if (parkingComplete && P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET) {
            progressConfigReset(); return;
        }
        var actor = ACTORS.get(integrated ? "b" : "a");
        require(actor != null && actor.connection.isConnected(), "PARK_ORCHESTRATION_OWNER");
        if (!parkingStarted) {
            if (System.nanoTime() - parkingNotBefore < 0 || !materialReady(actor.current)) { return; }
            parkingLogins = actor.logins;
            parkingStarted = true;
            if (P11C4aScenario.MODE == P11C4aScenario.Mode.PARKING_TRANSFER) {
                P11C4aParkingProbe.startPendingTransfer(server, actor.current, actor.role, output);
            } else if (P11C4aScenario.MODE == P11C4aScenario.Mode.PARKING_OLD_TICK) {
                P11C4aParkingProbe.startOldTick(server, actor.current, actor.role, output);
            } else if (P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_PARKING_RESET) {
                require(!integrated && actor.role.equals("a"), "CP_RESET_DEDICATED_TWO_ACTOR_FIXTURE");
                P11C4aConfigParkingResetProbe.start(server, actor.current, ACTORS.get("b").current, actor.role, output);
            } else { P11C4aParkingProbe.start(server, actor.current, actor.role, output); }
            return;
        }
        boolean parkedComplete = P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_PARKING_RESET
                ? P11C4aConfigParkingResetProbe.tick(server.getPlayerList().getPlayer(actor.id),
                        P11C4aEvidence.root().resolve("client-" + actor.role))
                : P11C4aParkingProbe.tick(server.getPlayerList().getPlayer(actor.id),
                        P11C4aEvidence.root().resolve("client-" + actor.role));
        if (!parkedComplete) { return; }
        require(actor.logins == parkingLogins + 1, "PARK_ORCHESTRATION_EXTRA_LOGIN");
        parkingComplete = true;
        if (P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET) { return; }
        // The remote peer leaves before the main actor/host; never claim full C4a.
        P11C4aEvidence.cue(output, "b-normal-finish.ready");
        terminal = true;
    }

    private static boolean configResetStarted, configResetComplete;
    private static int configResetLogins, configResetTicks;
    private static void progressConfigReset() throws IOException {
        require(P11C4aScenario.MODE == P11C4aScenario.Mode.CONFIG_RESET && parkingComplete
                && ++configResetTicks <= 2400, "RESET_ORCHESTRATION_MODE_OR_DEADLINE");
        var actor = ACTORS.get(integrated ? "b" : "a");
        require(actor != null && actor.connection.isConnected(), "RESET_ORCHESTRATION_OWNER");
        if (!configResetStarted) {
            if (!materialReady(actor.current)) { return; }
            var source = P11NativeStorageBoundary.nativeSourceOwner(actor.current);
            var body = source == null ? null : source.body(actor.current);
            require(body != null, "RESET_ORCHESTRATION_BODY");
            if (body.account.metadata != null
                    || body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] != 0
                    || body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] != 0
                    || body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] != 0) { return; }
            configResetLogins = actor.logins;
            P11C4aConfigResetProbe.start(server, actor.current, actor.role, output);
            configResetStarted = true;
            return;
        }
        if (!P11C4aConfigResetProbe.tick(server.getPlayerList().getPlayer(actor.id),
                P11C4aEvidence.root().resolve("client-" + actor.role))) { return; }
        require(actor.logins == configResetLogins + 1, "RESET_ORCHESTRATION_EXTRA_LOGIN");
        configResetComplete = true;
        P11C4aEvidence.cue(output, "b-normal-finish.ready");
        terminal = true;
    }

    private static long nativeCount(Actor actor, P11C4aNativeObservations.Event event) {
        return P11C4aNativeObservations.count(actor.connection, event);
    }

    private static Portal portal(ServerPlayer actor) {
        var level = actor.serverLevel();
        var position = actor.blockPosition().above(4).offset(4, 0, 0);
        var positions = List.of(position.below(), position, position.above(), position.above(2));
        var previous = new ArrayList<BlockState>();
        var placed = List.of(Blocks.OBSIDIAN.defaultBlockState(), Blocks.END_PORTAL.defaultBlockState(),
                Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
        require(position.getY() > level.getMinBuildHeight() && position.getY() + 2 < level.getMaxBuildHeight(), "PORTAL_WORLD_BOUND");
        for (var pos : positions) { require(level.getBlockEntity(pos) == null, "PORTAL_BLOCK_ENTITY"); previous.add(level.getBlockState(pos)); }
        for (int i = 0; i < positions.size(); i++) { level.setBlock(positions.get(i), placed.get(i), 3); }
        actor.connection.teleport(position.getX() + .5, position.getY() + .25, position.getZ() + .5, actor.getYRot(), actor.getXRot());
        return new Portal(level, positions, previous, placed);
    }

    private static void restorePortal() {
        if (portal == null) { return; }
        for (int i = 3; i >= 0; i--) {
            require(portal.level.getBlockState(portal.positions.get(i)).equals(portal.placed.get(i)), "PORTAL_CHANGED_EXTERNALLY");
            portal.level.setBlock(portal.positions.get(i), portal.previous.get(i), 3);
        }
        portal = null;
    }

    private static Map<String, Object> observation(Actor actor, String status) {
        return Map.of("status", status, "role", actor.role, "identityPseudonym", P11C4aEvidence.pseudonym(actor.id),
                "nativeLogins", actor.logins, "nativeRespawns", actor.respawns,
                "connectionStillOpen", actor.connection.isConnected(),
                "source", P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(server, actor.id)),
                "nativeAndWire", P11C4aNativeObservations.snapshot(actor.connection));
    }

    private static boolean materialReady(ServerPlayer actor) {
        return actor.connection.getConnection().isConnected()
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && P11NativeStorageBoundary.nativeDeliveryEligible(actor);
    }

    private static void progressNativeError(Actor first) throws IOException {
        require(!integrated, "ERROR_DEDICATED_ONLY");
        if (!nativeErrorStarted) {
            var peer = ACTORS.get("b").current;
            if (!materialReady(first.current) || !materialReady(peer)) { return; }
            for (var actor : ACTORS.values()) {
                require(actor.logins == 1 && actor.respawns == 0
                        && P11C4aNativeObservations.tries(actor.connection) == 0
                        && P11C4aNativeObservations.count(actor.connection,
                                P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == 1,
                        "ERROR_INITIAL_AUTHENTICATED_PLAY_ONLY");
            }
            P11C4aPeerSafetyProbe.install(server, first.current, peer, output);
            if (P11C4aSubmissionRejectProbe.selected()) { P11C4aSubmissionRejectProbe.start(server, first.current, peer, output); }
            else { P11C4aNativeErrorProbe.start(server, first.current, peer, output, P11C4aScenario.nativeErrorMode()); }
            nativeErrorStarted = true;
            if (!P11C4aSubmissionRejectProbe.selected()) { P11C4aEvidence.cue(output, "b-native-error.ready"); }
        }
        if (!P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-b"), P11C4aSubmissionRejectProbe.selected() ? "submission-peer-armed.json" : "native-error-peer-armed.json")) { return; }
        if (P11C4aSubmissionRejectProbe.selected() ? P11C4aSubmissionRejectProbe.tick() : P11C4aNativeErrorProbe.tick()) {
            if (P11C4aCompleteBFaultProbe.selected()) {
                if (!P11C4aCompleteBFaultProbe.afterClientProof()) { return; }
                nativeErrorCleanupCause = "HARNESS_AFTER_ORIGINAL_CONNECTED_CATCH_AND_BOTH_CLIENT_TERMINALS";
                P11C4aEvidence.write(output, "native-error-cleanup.json", Map.of(
                        "status", nativeErrorCleanupCause, "originalHaltWillBeInvoked", true,
                        "nativeErrorStoppedServerClaimed", false, "extraLogoutOrRespawnInvoked", false));
                terminal = true; server.halt(false);
                return; // The runServer route never reaches this ordinary post-proof cleanup.
            }
            // The original queued-task catch can continue. This halt is explicitly engineering cleanup,
            // not evidence that the original error policy stopped the server.
            nativeErrorCleanupCause = "HARNESS_AFTER_ORIGINAL_POLICY_AND_CLIENT_PROOF";
            P11C4aEvidence.write(output, "native-error-cleanup.json", Map.of(
                    "status", nativeErrorCleanupCause, "originalHaltWillBeInvoked", true,
                    "nativeErrorStoppedServerClaimed", false, "extraLogoutOrRespawnInvoked", false));
            terminal = true; server.halt(false);
        }
    }

    private static void fail(String code) {
        P11C4aCompleteBFaultProbe.abort();
        try { P11C4aBossProducerProbe.abort(); }
        catch (RuntimeException | Error secondary) { /* Preserve the recorded primary and original stop. */ }
        try { P11C4aConfigCatchProbe.abort(); }
        catch (RuntimeException | Error secondary) { /* Preserve the fixed primary and original stop. */ }
        try { P11C4aHeldProbe.abort(); }
        catch (RuntimeException | Error secondary) { /* Preserve original fixed failure and normal stop. */ }
        try { P11C4aTerminalStatusProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the original failure and owned stop. */ }
        if (nativeErrorStarted) { nativeErrorCleanupCause = "HARNESS_FAILURE_CLEANUP_NOT_ACCEPTANCE"; }
        if (P11C4aMalformedWireProbe.selected()) {
            try { P11C4aMalformedServerProbe.abort(); }
            catch (RuntimeException | Error ignored) { /* Keep the fixed primary; original stop below still runs. */ }
        }
        P11C4aRequiredProbe.abort();
        P11C4aPreplayChatProbe.abort();
        P11C4aConfigDepartureProbe.failureCleanup();
        P11C4aHostLeaveProbe.failureCleanup();
        P11C4aHostExpiryProbe.failureCleanup();
        terminal = true;
        try { if (output != null) { P11C4aEvidence.write(output, "failure.json", Map.of("status", "FAIL", "code", code,
                "step", step, "stepTicks", stepTicks, "reload", P11C4aReloadBlockerProbe.pending(),
                "firstTry", P11C4aFirstTryProbe.pending(),
                "metadataH", P11C4aMetadataHProbe.pending(), "metadataHStage", metadataHStage,
                "parking", Map.of("baseline", P11C4aParkingProbe.pending(), "configReset", P11C4aConfigResetProbe.pending()),
                "nativeConnections", ACTORS.values().stream().map(actor -> Map.of(
                        "role", actor.role, "connected", actor.connection.isConnected(),
                        "nativeAndWire", P11C4aNativeObservations.snapshot(actor.connection))).toList())); } }
        catch (IOException ignored) { }
        try { P11C4aReloadBlockerProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the recorded original failure; stop below. */ }
        try { P11C4aNativeSenderProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the recorded original failure and owned stop. */ }
        try { P11C4aPortalProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the recorded primary and owned stop. */ }
        try { P11C4aFirstTryProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the original failure and owned stop. */ }
        try { P11C4aMetadataHProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the original failure and owned stop. */ }
        try { P11C4aParkingProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve the original failure and owned stop. */ }
        P11C4aConfigResetProbe.abort();
        try { P11C4aConfigParkingResetProbe.abort(); }
        catch (RuntimeException | Error cleanupFailure) { /* Preserve recorded primary and owned stop. */ }
        P11C4aAckRejectionProbe.abort();
        P11C4aRewardContinuityProbe.release();
        if (server != null) { server.halt(false); }
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        if (event.getServer() != server) { return; }
        try {
            if (nativeErrorStarted) {
                if (P11C4aCompleteBFaultProbe.selected()) { P11C4aCompleteBFaultProbe.stopped(event.getServer()); }
                if (P11C4aSubmissionRejectProbe.selected()) { P11C4aSubmissionRejectProbe.stopped(event.getServer()); }
                else { P11C4aNativeErrorProbe.stopped(event.getServer()); }
                P11C4aEvidence.write(output, "native-error-server-terminal.json", Map.of(
                        "status", "ORIGINAL_SERVER_STOPPED_CAUSE_REQUIRES_SEPARATE_POLICY_RECEIPT",
                        "harnessCleanupCause", nativeErrorCleanupCause,
                        "nativeLogoutEventsObservedBounded", nativeErrorLogouts,
                        "normalRespawnOrLogoutShapeRequired", false, "nativeErrorCauseInferredFromStop", false,
                        "fullC4aAcceptance", false));
            }
            if (P11C4aConfigCatchProbe.selected()) { P11C4aConfigCatchProbe.stopped(event.getServer()); }
            if (P11C4aHeldProbe.selected()) { P11C4aHeldProbe.stopped(event.getServer()); }
            if (P11C4aTerminalStatusProbe.selected()) { P11C4aTerminalStatusProbe.stopped(event.getServer()); }
            if (P11C4aRequiredProbe.selected()) { P11C4aRequiredProbe.stopped(event.getServer()); }
            if (P11C4aMalformedWireProbe.selected()) { P11C4aMalformedServerProbe.stopped(event.getServer()); }
            if (departureStarted) { P11C4aConfigDepartureProbe.stopped(event.getServer()); }
if (P11L1HostStopProbe.selected()) { P11L1HostStopProbe.stopped(event.getServer()); }
if (hostLeaveStarted) { P11C4aHostLeaveProbe.stopped(event.getServer()); }
            if (hostExpiryStarted) { P11C4aHostExpiryProbe.stopped(event.getServer()); }
            if (output != null) { P11C4aEvidence.write(output, "stopped.json", Map.of("status", "ORIGINAL_SERVER_STOPPED", "normalReports", normalReports)); }
        }
        catch (IOException ignored) { }
        synchronized (P11C4aServerHarness.class) { AUTH.clear(); }
        P11C4aConfigResetProbe.abort();
        P11C4aPeerSafetyProbe.release();
        P11C4aPreplayChatProbe.abort();
        P11C4aAckRejectionProbe.abort();
        ACTORS.clear(); original = null; portal = null; server = null;
        if (!integrated) { P11C4aNativeObservations.release(); }
    }

    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, code); }
    private record Portal(ServerLevel level, List<BlockPos> positions, List<BlockState> previous, List<BlockState> placed) {}
    private static final class Actor {
        private final String role;
        private final UUID id;
        private final ServerPlayer first;
        private final Connection connection;
        private ServerPlayer current;
        private long respawns;
        private int logins = 1;
        private boolean lastEnd;
        private Actor(String role, ServerPlayer actor) {
            this.role = role; id = actor.getUUID(); first = actor; current = actor; connection = actor.connection.getConnection();
        }
    }
}
