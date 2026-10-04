package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryComponent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Excluded two-authentication negative: B joins normally before the separately faulty A. */
public final class P11C4aRequiredProbe {
    static final String ORIGINAL_VERSION = "gramarye-p11-transition-v1";
    static final String WRONG_VERSION = "gramarye-p11-transition-v1-fixture-incompatible";
    static final Set<ResourceLocation> P11_IDS = Set.of(id("transition_request"), id("transition_state"));
    private static volatile Run active;
    private P11C4aRequiredProbe() { }

    static boolean selected() {
        return P11C4aScenario.MODE == P11C4aScenario.Mode.REQUIRED_CHANNEL_MISSING
                || P11C4aScenario.MODE == P11C4aScenario.Mode.REQUIRED_VERSION_MISMATCH;
    }
    static boolean missing() { return P11C4aScenario.MODE == P11C4aScenario.Mode.REQUIRED_CHANNEL_MISSING; }
    static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gramarye", path); }
    static boolean expectedIds(Set<ResourceLocation> ids) { return ids.equals(P11_IDS); }
    static boolean incompatible(Component reason) {
        return reason.getContents() instanceof TranslatableContents value
                && value.getKey().equals("multiplayer.disconnect.incompatible");
    }
    static boolean queryShape(Map<ConnectionProtocol, Set<ModdedNetworkQueryComponent>> channels, boolean faultyA) {
        for (var protocol : new ConnectionProtocol[] {ConnectionProtocol.CONFIGURATION, ConnectionProtocol.PLAY}) {
            var rows = channels.get(protocol);
            if (rows == null) { return false; }
            for (var id : P11_IDS) {
                var found = rows.stream().filter(value -> value.id().equals(id)).toList();
                if (faultyA && missing()) { if (!found.isEmpty()) { return false; } }
                else if (found.size() != 1 || found.getFirst().optional()
                        || !found.getFirst().version().equals(faultyA ? WRONG_VERSION : ORIGINAL_VERSION)) { return false; }
            }
        }
        var play = channels.get(ConnectionProtocol.PLAY);
        for (var name : new String[] {"cast_intent", "intent_ack", "player_mana_sync", "skill_cooldown_sync", "profile_catalog", "presentation_event"}) {
            var found = play.stream().filter(value -> value.id().equals(id(name))).toList();
            String version = name.equals("profile_catalog") || name.equals("presentation_event") ? "gramarye-p8-v0" : "gramarye-p7-v0";
            if (found.size() != 1 || found.getFirst().optional() || !found.getFirst().version().equals(version)) { return false; }
        }
        return true;
    }

    static void start(MinecraftServer server, Path output) {
        require(selected() && active == null && server.isSameThread() && server.isDedicatedServer()
                && server.usesAuthentication() && !server.isSingleplayer(), "REQUIRED_SERVER_SCOPE");
        active = new Run(server, output);
    }
    /** Called only by the existing exact original ProfileResult observer, never by UUID lookup. */
    static void authenticated(MinecraftServer server, Connection connection, UUID id) {
        var run = active;
        if (run == null) { return; }
        synchronized (run) {
            if (server != run.server || connection == null || id == null || connection.isMemoryConnection()
                    || !connection.isEncrypted() || !connection.isConnected()) { run.failure = "REQUIRED_AUTH_IDENTITY"; return; }
            if (run.b == null && run.bCue && !run.aCue) { run.b = connection; run.bId = id; }
            else if (run.a == null && run.aCue && run.b != connection && !run.bId.equals(id)) { run.a = connection; run.aId = id; }
            else { run.failure = "REQUIRED_AUTH_ORDER_OR_DUPLICATE"; }
        }
    }
    static void login(ServerPlayer player) {
        var run = active;
        require(run != null && run.server.isSameThread() && player.getServer() == run.server
                && !player.isFakePlayer() && player.connection.getConnection() == run.b
                && player.getUUID().equals(run.bId) && run.actor == null, "REQUIRED_UNEXPECTED_PLAY_OR_ACTOR");
        run.actor = player;
    }
    public static void query(ServerConfigurationPacketListener listener,
            Map<ConnectionProtocol, Set<ModdedNetworkQueryComponent>> channels) {
        var run = active; if (run == null) { return; }
        synchronized (run) {
            Connection connection = listener.getConnection();
            if (connection.getPacketListener() != listener || connection != run.a && connection != run.b
                    || !queryShape(channels, connection == run.a)) { run.failure = "REQUIRED_QUERY_SHAPE_OR_OWNER"; return; }
            if (connection == run.a) { run.aQueries++; } else { run.bQueries++; }
        }
    }
    public static void failedPayload(ServerConfigurationPacketListener listener, Set<ResourceLocation> ids) {
        var run = active; if (run == null) { return; }
        synchronized (run) {
            if (listener.getConnection() != run.a || !expectedIds(ids)) { run.failure = "REQUIRED_FAILURE_CHANNEL_SET"; return; }
            run.failPackets++;
        }
    }
    public static void disconnected(ServerConfigurationPacketListener listener, Component reason) {
        var run = active; if (run == null) { return; }
        synchronized (run) {
            if (listener.getConnection() != run.a || !incompatible(reason)) { run.failure = "REQUIRED_NATIVE_CLOSE_REASON"; return; }
            run.disconnects++;
        }
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var run = active; if (run == null || !run.server.isSameThread()) { return; }
        if (listener.getConnection() == run.b && listener.player == run.actor && run.actionCue) { run.swings++; }
    }

    static boolean tick() throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.failure == null && ++run.ticks <= 24000,
                "REQUIRED_SERVER_DEADLINE_OR_OBSERVER");
        if (run.loggedOut) { return true; }
        // The cue deliberately permits the peer's original disconnect; await its real logout.
        // A transport-close-to-main-logout interval is not an unexpected control failure.
        if (run.qualified) { return false; }
        Path root = P11C4aEvidence.root();
        if (!run.bCue) {
            if (!P11C4aEvidence.receiptPresent(root.resolve("client-a"), "inputs.json")
                    || !P11C4aEvidence.receiptPresent(root.resolve("client-b"), "inputs.json")) { return false; }
            run.bCue = true; P11C4aEvidence.cue(run.output, "b-connect.ready"); return false;
        }
        if (run.actor == null) { return false; }
        require(run.b.isConnected() && run.server.getPlayerList().getPlayer(run.bId) == run.actor
                && run.actor.connection.getConnection() == run.b && run.actor.isAlive(), "REQUIRED_CONTROL_DISTURBED");
        if (!P11NativeStorageBoundary.nativeDeliveryEligible(run.actor)) { return false; }
        if (!run.aCue) {
            if (!P11C4aEvidence.receiptPresent(root.resolve("client-b"), "required-control-play.json")) { return false; }
            var source = P11NativeStorageBoundary.nativeSourceOwner(run.actor);
            var body = source == null ? null : source.nativeRecipient(run.actor);
            require(body != null && run.bQueries == 1, "REQUIRED_CONTROL_SOURCE_OR_QUERY");
            for (var channel : P11_IDS) { require(NetworkRegistry.hasChannel(run.b, ConnectionProtocol.PLAY, channel), "REQUIRED_CONTROL_P11_CHANNEL"); }
            run.epoch = P11NativeStorageBoundary.diagnostics(run.server, run.bId).sourceEpoch();
            run.stats = run.actor.getStats(); run.advancements = run.actor.getAdvancements();
            P11C4aEvidence.write(run.output, "b-required-auth-play.json", Map.of(
                    "status", "ORIGINAL_ENCRYPTED_HAS_JOINED_TO_PLAY_BEFORE_FAULTY_A", "role", "b",
                    "identityPseudonym", P11C4aEvidence.pseudonym(run.bId), "sourceEpoch", run.epoch,
                    "queryP7P8P11Unchanged", true, "nativeAndWire", P11C4aNativeObservations.snapshot(run.b)));
            run.aCue = true; P11C4aEvidence.cue(run.output, "a-connect.ready"); return false;
        }
        if (run.a == null || run.a.isConnected()
                || !P11C4aEvidence.receiptPresent(root.resolve("client-a"), "required-rejection.json")) { return false; }
        require(run.aQueries == 1 && run.failPackets == 1 && run.disconnects == 1
                && run.server.getPlayerList().getPlayer(run.aId) == null, "REQUIRED_REJECTION_MISSING");
        for (var event : new P11C4aNativeObservations.Event[] {P11C4aNativeObservations.Event.CONFIG_CALL_ENTER,
                P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED,
                P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER, P11C4aNativeObservations.Event.SERVER_INGRESS}) {
            require(P11C4aNativeObservations.count(run.a, event) == 0, "REQUIRED_REJECTED_DOWNSTREAM");
        }
        if (!run.actionCue) {
            run.actionCue = true; P11C4aEvidence.cue(run.output, "b-required-action.ready"); return false;
        }
        if (run.swings == 0) { return false; }
        if (!run.qualified) {
            require(run.swings == 1 && run.actor.getStats() == run.stats && run.actor.getAdvancements() == run.advancements
                    && P11NativeStorageBoundary.diagnostics(run.server, run.bId).sourceEpoch() == run.epoch
                    && P11C4aNativeObservations.count(run.b, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == 1
                    && P11C4aNativeObservations.count(run.b, P11C4aNativeObservations.Event.RESPAWN_CALL_ENTER) == 0
                    && P11C4aNativeObservations.tries(run.b) == 0, "REQUIRED_CONTROL_CHANGED");
            run.server.saveEverything(false, true, true);
            P11C4aEvidence.write(run.output, "required-result.json", Map.of(
                    "status", "REQUIRED_NEGOTIATION_NATIVE_SUBSET_OBSERVED", "mode", P11C4aScenario.MODE.name(),
                    "aIdentityPseudonym", P11C4aEvidence.pseudonym(run.aId), "bIdentityPseudonym", P11C4aEvidence.pseudonym(run.bId),
                    "aNativeAndWire", P11C4aNativeObservations.snapshot(run.a), "bNativeAndWire", P11C4aNativeObservations.snapshot(run.b),
                    "nativeFailurePacket", run.failPackets, "nativeIncompatibleDisconnectReturn", run.disconnects,
                    "controlOriginalAnimateReturns", run.swings, "fullC4aAcceptance", false));
            run.qualified = true; P11C4aEvidence.cue(run.output, "b-required-finish.ready");
        }
        return run.loggedOut;
    }
    static void logout(ServerPlayer player) {
        var run = active;
        require(run != null && run.qualified && player == run.actor && player.connection.getConnection() == run.b,
                "REQUIRED_EARLY_OR_UNKNOWN_LOGOUT");
        run.loggedOut = true;
    }
    static void stopped(MinecraftServer server) throws IOException {
        var run = active; if (run == null) { return; }
        if (server == run.server && run.qualified && run.loggedOut && run.failure == null) {
            P11C4aEvidence.write(run.output, "required-stopped.json", Map.of(
                    "status", "ORIGINAL_SERVER_STOPPED_AFTER_REQUIRED_SUBSET_AND_CONTROL_LOGOUT", "fullC4aAcceptance", false));
        }
        active = null;
    }
    static void abort() { active = null; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final MinecraftServer server; final Path output;
        volatile Connection a, b; volatile UUID aId, bId; volatile String failure;
        volatile boolean aCue, bCue; volatile int aQueries, bQueries, failPackets, disconnects;
        ServerPlayer actor; Object stats, advancements; long epoch; int ticks, swings;
        boolean actionCue, qualified, loggedOut;
        Run(MinecraftServer server, Path output) { this.server = server; this.output = output; }
    }
}
