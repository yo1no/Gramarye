package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.network.P11D3Observation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** A second real integrated server in the same JVM, not a cross-process restart substitute.
 * One original remote task survives only as a bounded excluded engineering holder. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11D3HostProbe {
    private static final Map<Connection, UUID> AUTH = new IdentityHashMap<>();
    private static volatile MinecraftServer server;
    private static ServerSlot slot;
    private static ServerPlayer host, peer;
    private static UUID hostId, peerId;
    private static Path output;
    private static P11D3Observation.Snapshot oldIdentity;
    private static int generation, stage, ticks, admissions, transfers, logoutEvents;
    private static boolean stoppedFirst, secondIsDifferent, localProof, authFault, readyPublished;
    private static String failure;
    private P11D3HostProbe() { }
    public static boolean selected() { return "d3-host-lan".equals(System.getProperty("gramarye.p11.online.case", "")); }
    public static void runtimeStarted(MinecraftServer actual, Object value) {
        require(actual.isSameThread() && value instanceof ServerSlot && slot == null, "ACTUAL_NEW_RUNTIME");
        require(server == null || server != actual && stoppedFirst, "NEW_ACTUAL_SERVER_OBJECT");
        if (server != null) secondIsDifferent = server != actual;
        slot = (ServerSlot) value;
        require(slot.instances.isEmpty() && slot.activeProjectileContinuations.isEmpty(), "NO_RUNTIME_RESTORATION");
    }
    public static synchronized void authenticated(MinecraftServer actual, Connection connection, UUID id) {
        if (actual != server || id == null || connection == null || AUTH.size() >= 1 || AUTH.containsKey(connection)) { authFault = true; return; }
        AUTH.put(connection, id);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        if (!selected()) return;
        try {
            var actual = event.getServer(); require(!actual.isDedicatedServer() && actual.isSingleplayer()
                    && actual.usesAuthentication() && slot != null, "INTEGRATED_TOPOLOGY");
            server = actual; require(++generation <= 2 && (generation == 1 || stoppedFirst && secondIsDifferent), "TWO_REAL_SERVER_STARTS");
            if (generation == 1) { output = P11C4aEvidence.reserve("server"); P11D3Observation.begin(server); }
            else P11D3Observation.beginNextServer(server);
            host = peer = null; stage = ticks = admissions = transfers = logoutEvents = 0; localProof = false;
            P11C4aEvidence.write(output, "host-" + generation + "-ready.json", Map.of("status", "ACTUAL_INTEGRATED_SERVER_STARTED",
                    "generationOrdinal", generation, "newActualServerObject", generation == 2 && secondIsDifferent,
                    "sameJvm", true, "emptyRuntime", true, "productionJarSha256", P11OnlineInputs.verifyFrozenJar(),
                    "configurationSha256", P11C4aLoadedConfiguration.hash(), "localHostIsAuthenticationProof", false));
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!selected() || !(event.getEntity() instanceof ServerPlayer actor) || actor.getServer() != server) return;
        try {
            var connection = actor.connection.getConnection(); String role;
            if (connection.isMemoryConnection()) {
                require(host == null && server.isSingleplayerOwner(actor.getGameProfile()) && (hostId == null || hostId.equals(actor.getUUID())), "EXACT_LOCAL_HOST");
                host = actor; hostId = actor.getUUID(); role = "host";
            } else {
                UUID authenticated; synchronized (P11D3HostProbe.class) { authenticated = AUTH.remove(connection); }
                require(host != null && peer == null && actor.getUUID().equals(authenticated) && connection.isEncrypted()
                        && !actor.getUUID().equals(hostId) && (peerId == null || peerId.equals(actor.getUUID())), "EXACT_AUTHENTICATED_REMOTE_PEER");
                peer = actor; peerId = actor.getUUID(); role = "b";
            }
            P11C4aEvidence.write(output, "host-" + generation + "-" + role + "-login.json", Map.of("status", role.equals("host")
                    ? "ORIGINAL_LOCAL_HOST_LOGIN_NOT_AUTH_PROOF" : "ORIGINAL_HAS_JOINED_TO_EXACT_REMOTE_PLAY",
                    "encrypted", connection.isEncrypted(), "memoryConnection", connection.isMemoryConnection(),
                    "identityPseudonym", P11C4aEvidence.pseudonym(actor.getUUID())));
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!selected() || !(event.getEntity() instanceof ServerPlayer actor) || actor.getServer() != server) return;
        if ((actor == host || actor == peer) && stage == 4) logoutEvents++;
        else if (failure == null) failure = "D3_HOST_UNPLANNED_LOGOUT";
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        if (!selected() || server != event.getServer()) return;
        try {
            require(server.isSameThread() && failure == null && !authFault && ++ticks <= 12_000, "FINITE_ACTUAL_SERVER_PROGRESS");
            if (generation == 1 && !readyPublished && server.isPublished()) {
                require(server.getPort() == P11C4aEvidence.port(), "ORIGINAL_PUBLISHED_NATIVE_PORT"); readyPublished = true;
                P11C4aEvidence.write(output, "ready.json", Map.of("status", "ONLINE_INTEGRATED_PUBLISHED_NO_PARTNER_AUTH_CLAIM",
                        "case", "d3-host-lan", "runId", P11C4aEvidence.property("runId"),
                        "productionJarSha256", P11OnlineInputs.verifyFrozenJar(), "configurationSha256", P11C4aLoadedConfiguration.hash(),
                        "onlineMode", true, "integrated", true, "expectedPlayers", 2));
            }
            if (host == null || peer == null || stage == 4) return;
            if (!P11D3Observation.initialDelivered(host) || !P11D3Observation.initialDelivered(peer)) return;
            if (stage == 0) {
                require(P11D3Observation.snapshot(host).expectedNext() == 1 && P11D3Observation.snapshot(peer).expectedNext() == 1,
                        "FRESH_NATIVE_SESSIONS");
                cue("host-" + generation + "-b-starter.ready"); stage = 1; return;
            }
            if (stage == 1) {
                if (!receipt("client-b", "host-" + generation + "-starter.json")) return;
                String ref = P11NativeStorageBoundary.diagnostics(server, peerId).equippedSlot0();
                if (ref.equals("ABSENT") || ref.equals("UNAVAILABLE")) return;
                // Only ordinary native positioning, before any authentic R. The projectile misses all actors.
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                    server.overworld().setBlockAndUpdate(new BlockPos(8 + dx, 99, dz), Blocks.STONE.defaultBlockState());
                peer.teleportTo(server.overworld(), 8.5, 100, .5, java.util.Set.of(), 0, -70);
                if (generation == 1) {
                    oldIdentity = P11D3Observation.snapshot(peer); P11D3Observation.arm(peer, P11D3Observation.Hold.TASK);
                } else {
                    var fresh = P11D3Observation.snapshot(peer); var local = P11D3Observation.snapshot(host);
                    require(fresh.generation() > oldIdentity.generation() && fresh.expectedNext() == 1,
                            "SOLE_OWNER_NEW_GENERATION");
                    P11D3Observation.release();
                    require(P11D3Observation.unchanged(fresh, peer) && P11D3Observation.unchanged(local, host)
                            && admissions == 0 && transfers == 0 && slot.instances.isEmpty() && P11D3Observation.pending() == 0,
                            "OLD_SERVER_TASK_NO_NEW_SERVER_EFFECT");
                    P11C4aEvidence.write(output, "host-generation-late-task.json", Map.of("status", "ENGINEERING_OLD_TASK_REJECTED_BY_NEW_ACTUAL_SERVER",
                            "old", oldIdentity.facts(), "new", fresh.facts(), "hostUnchanged", true,
                            "oldAckNotSubmittedToNewSession", true, "actualServerObjectChanged", secondIsDifferent));
                }
                cue("host-" + generation + "-b-cast.ready"); stage = 2; return;
            }
            if (stage == 2 && generation == 1) {
                if (!P11D3Observation.held()) return;
                require(P11D3Observation.pending() == 1 && admissions == 0 && transfers == 0 && slot.instances.isEmpty(), "REAL_PENDING_TASK_BEFORE_NATIVE_HALT");
                P11C4aEvidence.write(output, "host-1-held.json", P11D3Observation.counters());
                localProof = true; stage = 4; cue("host-1-leave.ready"); return;
            }
            if (stage == 2 && generation == 2 && admissions == 1 && transfers == 1
                    && slot.instances.isEmpty() && slot.activeProjectileContinuations.isEmpty() && P11D3Observation.pending() == 0) {
                require(P11D3Observation.snapshot(peer).expectedNext() == 2 && P11D3Observation.snapshot(host).expectedNext() == 1,
                        "NEW_SERVER_FIRST_WIRE_ONE_D0_ACCEPTED_AND_LOCAL_ISOLATION");
                P11C4aEvidence.write(output, "d3-result.json", Map.of("status", "SAME_JVM_HALT_NEW_SERVER_OLD_TASK_DROP_AND_FRESH_D0_ACCEPT",
                        "serverStarts", generation, "sameJvm", true, "actualServerObjectChanged", secondIsDifferent,
                        "newRemoteAdmissions", admissions, "newNativeTransfers", transfers,
                        "peerAuthenticationPerServer", true, "localHostAuthenticationClaim", false,
                        "observations", P11D3Observation.counters()));
                localProof = true; stage = 4; cue("host-2-leave.ready");
            }
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    public static void admitted(MinecraftServer actual, ServerPlayer actor, Object result) {
        require(server == actual && server.isSameThread() && generation == 2 && actor == peer
                && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly && ++admissions == 1, "ORIGINAL_FRESH_D0_ACCEPTANCE");
    }
    public static void transferred(Object disposition) {
        if (disposition == RuntimePermitTransferDisposition.TRANSFERRED) require(generation == 2 && ++transfers == 1, "ORIGINAL_NEW_NATIVE_RELEASE");
    }
    public static void claimed(Object disposition) { require(disposition != RuntimePermitClaimDisposition.QUEUED, "NO_UNPLANNED_HIT"); }
    public static void rootRetired(MinecraftServer actual) {
        if (actual != server || output == null) return;
        try {
            var data = P11NativeStorageBoundary.terminalDiagnostics(); require(data != null, "ACTUAL_DATA_TERMINAL");
            var roots = data.nativeResponsibilities().roots();
            boolean clean = data.nativeStopNormal() && data.failures() == 0 && data.resources().dirtyUuids() == 0
                    && roots.size() == 5 && roots.stream().allMatch(root -> root.count() == 0);
            P11C4aEvidence.write(output, "host-" + generation + "-data-terminal.json", Map.of("status", clean && localProof && failure == null
                    ? "NORMAL_NATIVE_STOP_AND_ROOTS_ZERO" : "TERMINAL_NOT_QUALIFIED", "nativeStopNormal", data.nativeStopNormal(),
                    "sourceFailures", data.failures(), "dirtyUuids", data.resources().dirtyUuids(), "originalWrites", data.writes(),
                    "allRootCounts", roots.stream().map(root -> Map.of("kind", root.kind(), "count", root.count())).toList()));
            require(clean, "CLEAN_ORIGINAL_STOP");
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void stopped(ServerStoppedEvent event) {
        if (!selected() || event.getServer() != server) return;
        try {
            require(localProof && failure == null && P11D3Observation.pending() == 0
                    && P11D3Observation.activeSessions() == 0 && server.getPlayerList().getPlayers().isEmpty(), "ORIGINAL_STOP_INVALIDATES_ALL_SESSIONS_PERMITS");
            P11C4aEvidence.write(output, "host-" + generation + "-stopped.json", Map.of("status", "ORIGINAL_INTEGRATED_SERVER_STOPPED",
                    "actualLogoutEvents", logoutEvents, "remainingPlayers", 0, "serverPending", 0, "activeSessions", 0));
            P11C4aEvidence.write(output, "host-" + generation + "-cost.json", P11D3Observation.costs());
            if (generation == 1) stoppedFirst = true; else P11D3Observation.clear();
        } catch (Exception | LinkageError primary) { fail(primary); }
        finally { host = peer = null; slot = null; synchronized (P11D3HostProbe.class) { AUTH.clear(); } }
    }
    private static boolean receipt(String role, String leaf) throws IOException { return P11C4aEvidence.receiptPresent(output.getParent().resolve(role), leaf); }
    private static void cue(String leaf) throws IOException { P11C4aEvidence.cue(output, leaf); }
    private static void require(boolean value, String code) { P11D3ServerHarness.require(value, "HOST_" + code); }
    private static void fail(Throwable primary) {
        if (failure == null) {
            failure = P11C4aEvidence.failureCode(primary);
            try { if (output != null) P11C4aEvidence.write(output, "failure.json", Map.of("status", "FAIL", "failure", failure,
                    "serverOrdinal", generation, "stage", stage)); } catch (IOException ignored) { }
        }
        P11D3Observation.clear(); if (server != null && server.isSameThread()) server.halt(false);
    }
}
