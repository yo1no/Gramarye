package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.network.P11D3Observation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Closed D3 experiments. All casts use the actual starter and original R sender, except
 * the explicitly labelled identical-body replay. No test creates a P7 session or permit. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11D3ServerHarness {
    private enum Stage { CONNECT, STARTER, FIRST, REPLAY, RECONNECT, FRESH, DEATH, PEER, FINISH }
    private static final Map<Connection, UUID> AUTH = new IdentityHashMap<>();
    private static volatile MinecraftServer server;
    private static ServerSlot slot;
    private static ServerPlayer actor, peer, firstActor;
    private static Connection firstConnection;
    private static Path output;
    private static Stage stage = Stage.CONNECT;
    private static P11D3Observation.Snapshot firstIdentity, peerBefore;
    private static int ticks, logins, logouts, admissions, attempts, transfers, claims;
    private static boolean aCue, bCue, sealed;
    private static volatile boolean authenticationFault;
    private static String failure;
    private P11D3ServerHarness() { }

    public static boolean selected() {
        return P11D3HostProbe.selected() || dedicatedSelected();
    }
    private static boolean dedicatedSelected() {
        return List.of("d3-replay", "d3-old-source", "d3-late-task", "d3-old-result", "d3-respawn", "d3-dual")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    private static String scenario() { return P11C4aEvidence.property("case"); }
    public static void require(boolean value, String code) { P11C4aEvidence.require(value, "D3_" + code); }

    public static void runtimeStarted(MinecraftServer actual, Object actualSlot) {
        if (P11D3HostProbe.selected()) { P11D3HostProbe.runtimeStarted(actual, actualSlot); return; }
        if (!selected()) return;
        require(slot == null && actual.isSameThread() && actualSlot instanceof ServerSlot, "ORIGINAL_RUNTIME_START");
        slot = (ServerSlot) actualSlot;
        require(slot.instances.isEmpty() && slot.activeProjectileContinuations.isEmpty(), "FRESH_RUNTIME");
    }
    public static synchronized void authenticated(MinecraftServer actual, Connection connection, UUID player) {
        if (P11D3HostProbe.selected()) { P11D3HostProbe.authenticated(actual, connection, player); return; }
        if (!dedicatedSelected()) return;
        if (server != actual || connection == null || player == null || AUTH.size() >= 3 || AUTH.containsKey(connection)) {
            authenticationFault = true; return;
        }
        AUTH.put(connection, player);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        if (!dedicatedSelected()) return;
        try {
            server = event.getServer();
            require(server.isDedicatedServer() && server.usesAuthentication() && !server.isSingleplayer() && slot != null,
                    "DEDICATED_AUTHENTICATED_TOPOLOGY");
            output = P11C4aEvidence.reserve("server"); P11D3Observation.begin(server);
            P11C4aEvidence.write(output, "ready.json", Map.of("status", "ONLINE_DEDICATED_READY_NO_AUTH_CLAIM",
                    "case", scenario(), "runId", P11C4aEvidence.property("runId"), "productionJarSha256", P11OnlineInputs.verifyFrozenJar(),
                    "configurationSha256", P11C4aLoadedConfiguration.hash(), "onlineMode", true,
                    "integrated", false, "expectedPlayers", 2));
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!dedicatedSelected() || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) return;
        try {
            var connection = player.connection.getConnection(); UUID authenticated;
            synchronized (P11D3ServerHarness.class) { authenticated = AUTH.remove(connection); }
            current(player);
            require(player.getUUID().equals(authenticated) && connection.isEncrypted() && !connection.isMemoryConnection(), "AUTH_TO_EXACT_PLAY");
            String role;
            if (firstActor == null) {
                actor = firstActor = player; firstConnection = connection; logins = 1; role = "a";
            } else if (firstActor.getUUID().equals(player.getUUID())) {
                require(stage == Stage.RECONNECT && logins == 1 && logouts == 1 && player != firstActor
                        && connection != firstConnection && firstActor.isRemoved() && !firstConnection.isConnected(), "NEW_AUTHENTICATED_BINDING");
                actor = player; logins++; role = "a";
            } else { require(peer == null && logins == 1, "DISTINCT_AUTHENTICATED_PEER"); peer = player; role = "b"; }
            P11C4aEvidence.write(output, role + "-auth-" + (role.equals("a") ? logins : 1) + ".json",
                    Map.of("status", "ORIGINAL_HAS_JOINED_TO_EXACT_PLAY", "encrypted", true,
                            "identityPseudonym", P11C4aEvidence.pseudonym(player.getUUID())));
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!dedicatedSelected() || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) return;
        if (stage == Stage.RECONNECT && player == firstActor && logouts == 0
                || stage == Stage.FINISH && (player == actor || player == peer)) logouts++;
        else if (failure == null) failure = "D3_UNEXPECTED_NATIVE_LOGOUT";
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        if (!dedicatedSelected() || server != event.getServer() || sealed) return;
        try {
            require(server.isSameThread() && ++ticks <= 12_000 && !authenticationFault && failure == null, "FINITE_ORIGINAL_SERVER_PROGRESS");
            if (!aCue && receipt("client-a", "inputs.json")) { aCue = true; cue("a-connect.ready"); }
            if (actor != null && !bCue && receipt("client-b", "inputs.json")) { bCue = true; cue("b-connect.ready"); }
            if (stage == Stage.FINISH) {
                if (logouts == (logins == 2 ? 3 : 2) && server.getPlayerList().getPlayers().isEmpty()
                        && receipt("client-a", "result.json") && receipt("client-b", "result.json")) {
                    sealed = true; server.halt(false);
                }
                return;
            }
            if (actor == null || peer == null) return;
            current(peer);
            if (stage != Stage.RECONNECT && stage != Stage.DEATH) current(actor);
            switch (stage) {
                case CONNECT -> {
                    if (!P11D3Observation.initialDelivered(actor) || !P11D3Observation.initialDelivered(peer)) return;
                    arena(actor, 0); arena(peer, 8); cue("a-starter.ready"); cue("b-starter.ready"); stage = Stage.STARTER;
                }
                case STARTER -> {
                    if (!receipt("client-a", "starter.json") || !receipt("client-b", "starter.json")
                            || !equipped(actor) || !equipped(peer)) return;
                    firstIdentity = P11D3Observation.snapshot(actor); peerBefore = P11D3Observation.snapshot(peer);
                    require(firstIdentity.expectedNext() == 1 && peerBefore.expectedNext() == 1, "UNTOUCHED_FIRST_LEGAL_SEQUENCE");
                    var hold = switch (scenario()) {
                        case "d3-old-source" -> P11D3Observation.Hold.SOURCE;
                        case "d3-late-task" -> P11D3Observation.Hold.TASK;
                        case "d3-old-result" -> P11D3Observation.Hold.RESULT;
                        default -> P11D3Observation.Hold.NONE;
                    };
                    if (hold != P11D3Observation.Hold.NONE) P11D3Observation.arm(actor, hold);
                    stage = Stage.FIRST; cue("a-cast-1.ready");
                }
                case FIRST -> {
                    if (scenario().equals("d3-old-source") || scenario().equals("d3-late-task") || scenario().equals("d3-old-result")) {
                        if (!P11D3Observation.held() || !idle()) return;
                        int expected = scenario().equals("d3-old-result") ? 1 : 0;
                        require(admissions == expected && attempts == expected && transfers == expected && claims == 0,
                                "EXACT_HELD_PREFIX");
                        int pending = scenario().equals("d3-late-task") ? 1 : 0;
                        require(P11D3Observation.pending() == pending && P11D3Observation.pending(actor) == pending,
                                "EXACT_REAL_HELD_PERMIT_ACCOUNTING");
                        P11C4aEvidence.write(output, "d3-held.json", P11D3Observation.counters());
                        stage = Stage.RECONNECT; cue("a-leave.ready"); return;
                    }
                    if (admissions != 1 || !idle() || P11D3Observation.pending() != 0) return;
                    require(attempts == 1 && transfers == 1 && claims == 0, "FIRST_NATIVE_D0_RELEASE_ONCE");
                    require(P11D3Observation.sameAdmission(peerBefore, peer), "A_CAST_DID_NOT_MUTATE_PEER_ADMISSION");
                    if (scenario().equals("d3-replay")) { stage = Stage.REPLAY; cue("a-replay.ready"); }
                    else if (scenario().equals("d3-respawn")) {
                        firstIdentity = P11D3Observation.snapshot(actor); stage = Stage.DEATH;
                        P11C4aEvidence.write(output, "d3-before-death.json", firstIdentity.facts()); actor.kill();
                    } else { stage = Stage.PEER; cue("b-cast-1.ready"); }
                }
                case REPLAY -> {
                    if (!receipt("client-a", "replayed.json") || !P11D3Observation.lastResult().equals("DUPLICATE_SEQUENCE")) return;
                    require(P11D3Observation.lastResultSequence() == 1 && admissions == 1 && attempts == 1
                            && transfers == 1 && claims == 0 && P11D3Observation.snapshot(actor).expectedNext() == 2,
                            "DUPLICATE_HAS_NO_NEW_WORLD_ADMISSION");
                    local("SAME_SESSION_IDENTICAL_BODY_REPLAY_REJECTED"); finish();
                }
                case RECONNECT -> {
                    if (logins == 1) {
                        if (logouts == 1 && server.getPlayerList().getPlayer(firstActor.getUUID()) == null
                                && !firstConnection.isConnected() && receipt("client-a", "left.json")) cueOnce("a-reconnect.ready");
                        return;
                    }
                    current(actor); if (!P11D3Observation.initialDelivered(actor)) return;
                    var fresh = P11D3Observation.snapshot(actor);
                    require(fresh.epoch() > firstIdentity.epoch() && fresh.generation() == firstIdentity.generation()
                            && fresh.expectedNext() == 1 && P11D3Observation.pending() == 0, "NEW_C_FRESH_INITIAL_STATE");
                    var peerState = P11D3Observation.snapshot(peer);
                    var source = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
                    int beforeAttempts = attempts, beforeAdmissions = admissions, beforeTransfers = transfers;
                    P11D3Observation.release();
                    require(!scenario().equals("d3-old-source") || P11D3Observation.lateSourceRejected(), "DIRECT_OLD_SOURCE_CAPTURE_REJECTION");
                    require(P11D3Observation.unchanged(fresh, actor) && P11D3Observation.unchanged(peerState, peer)
                            && sameSource(source, P11NativeStorageBoundary.diagnostics(server, actor.getUUID()))
                            && attempts == beforeAttempts && admissions == beforeAdmissions && transfers == beforeTransfers,
                            "LATE_ORIGINAL_NO_CURRENT_STATE_OR_ACK_OR_WORLD_EFFECT");
                    P11C4aEvidence.write(output, "d3-late-release.json", Map.of("status", "ENGINEERING_ORIGINAL_LATE_CONTINUATION_NO_EFFECT",
                            "newSession", fresh.facts(), "peerUnchanged", true, "sourceUnchanged", true,
                            "originalContinuationCalls", 1, "counters", P11D3Observation.counters()));
                    arena(actor, 0); stage = Stage.FRESH; cue("a-cast-2.ready");
                }
                case FRESH -> {
                    int expected = scenario().equals("d3-old-result") ? 2 : 1;
                    if (admissions != expected || !idle() || P11D3Observation.pending() != 0) return;
                    require(attempts == expected && transfers == expected && claims == 0
                            && P11D3Observation.snapshot(actor).expectedNext() == 2, "FRESH_FIRST_LEGAL_D0_ACCEPTED");
                    local("NEW_AUTHENTICATED_SESSION_FIRST_WIRE_ONE_ACCEPTED"); finish();
                }
                case DEATH -> {
                    var replacement = server.getPlayerList().getPlayer(firstActor.getUUID());
                    if (replacement == null || replacement == firstActor || !receipt("client-a", "respawn.json")) return;
                    require(replacement.connection.getConnection() == firstConnection && firstActor.isRemoved(), "NATIVE_SAME_C_REPLACEMENT");
                    actor = replacement;
                    if (!P11D3Observation.initialDelivered(actor)) return;
                    require(P11D3Observation.sameAdmission(firstIdentity, actor)
                            && P11D3Observation.snapshot(actor).expectedNext() == firstIdentity.expectedNext(), "SAME_C_IDENTITY_SEQUENCE_RETAINED");
                    stage = Stage.PEER; cue("a-cast-2.ready");
                }
                case PEER -> {
                    if (admissions != 2 || !idle() || P11D3Observation.pending() != 0) return;
                    require(attempts == 2 && transfers == 2 && claims == 0, "TWO_ORIGINAL_RELEASES");
                    require(P11D3Observation.sameIdentity(peerBefore, peer), "PEER_IDENTITY_RETAINED");
                    require(P11D3Observation.snapshot(peer).expectedNext() == (scenario().equals("d3-dual") ? 2 : 1), "PEER_SEQUENCE_ISOLATION");
                    require(P11D3Observation.snapshot(actor).expectedNext() == (scenario().equals("d3-dual") ? 2 : 3), "ACTOR_SEQUENCE_EXACT");
                    local(scenario().equals("d3-dual") ? "TWO_AUTHENTICATED_INDEPENDENT_SESSIONS" : "SAME_CONNECTION_NATIVE_RESPAWN_SEQUENCE_RETAINED"); finish();
                }
                default -> throw new IllegalStateException("D3_STAGE");
            }
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    public static void admitted(MinecraftServer actual, ServerPlayer player, SkillReference ref, Object result) {
        if (P11D3HostProbe.selected()) { P11D3HostProbe.admitted(actual, player, result); return; }
        if (!selected() || server != actual) return;
        require(server.isSameThread() && (player == actor || player == peer), "ACTUAL_P5_CALLER");
        attempts++; require(result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly, "EXPECTED_ORIGINAL_D0_ADMISSION"); admissions++;
    }
    public static void transferred(Object disposition) {
        if (P11D3HostProbe.selected()) { P11D3HostProbe.transferred(disposition); return; }
        if (selected() && disposition == RuntimePermitTransferDisposition.TRANSFERRED) transfers++;
    }
    public static void claimed(Object disposition) {
        if (P11D3HostProbe.selected()) { P11D3HostProbe.claimed(disposition); return; }
        if (selected() && disposition == RuntimePermitClaimDisposition.QUEUED) claims++;
    }
    private static boolean idle() { return slot.instances.isEmpty() && slot.activeProjectileContinuations.isEmpty(); }
    private static boolean equipped(ServerPlayer player) {
        var value = P11NativeStorageBoundary.diagnostics(server, player.getUUID()).equippedSlot0();
        return !value.equals("ABSENT") && !value.equals("UNAVAILABLE");
    }
    private static boolean sameSource(P11QualifiedSourceOwner.Diagnostics before, P11QualifiedSourceOwner.Diagnostics after) {
        return before.active() == after.active() && before.sourceEpoch() == after.sourceEpoch()
                && before.sourceVersion() == after.sourceVersion() && before.bodyComplete() == after.bodyComplete()
                && before.candidatePresent() == after.candidatePresent() && before.sourceFault().equals(after.sourceFault())
                && before.sourceInput().equals(after.sourceInput()) && before.equippedSlot0().equals(after.equippedSlot0())
                && before.writers().equals(after.writers()) && before.resources().equals(after.resources())
                && before.serializations() == after.serializations() && before.writes() == after.writes()
                && before.saveProgress().kinds().stream().map(kind -> java.util.List.of(kind.kind(), kind.dirtySources(),
                    kind.successfulPhysicalWrites(), kind.counterSaturated())).toList().equals(after.saveProgress().kinds().stream()
                    .map(kind -> java.util.List.of(kind.kind(), kind.dirtySources(), kind.successfulPhysicalWrites(), kind.counterSaturated())).toList())
                && before.nativeResponsibilities().liveCanonicalHolders() == after.nativeResponsibilities().liveCanonicalHolders()
                && before.nativeResponsibilities().detachedCanonicalHolders() == after.nativeResponsibilities().detachedCanonicalHolders()
                && before.nativeResponsibilities().partialHolders() == after.nativeResponsibilities().partialHolders()
                && before.nativeResponsibilities().roots().stream().map(root -> java.util.List.of(root.kind(), root.count(), root.accountPeakSum())).toList()
                    .equals(after.nativeResponsibilities().roots().stream().map(root -> java.util.List.of(root.kind(), root.count(), root.accountPeakSum())).toList());
    }
    private static void current(ServerPlayer player) {
        require(server.isSameThread() && player.getServer() == server && !player.isRemoved() && player.isAlive()
                && player.connection.isAcceptingMessages() && server.getPlayerList().getPlayer(player.getUUID()) == player, "EXACT_CURRENT_PLAYER");
    }
    private static void arena(ServerPlayer player, int x) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            server.overworld().setBlockAndUpdate(new BlockPos(x + dx, 99, dz), Blocks.STONE.defaultBlockState());
        player.teleportTo(server.overworld(), x + .5, 100, .5, java.util.Set.of(), 0, -70);
    }
    private static boolean receipt(String role, String leaf) throws IOException { return P11C4aEvidence.receiptPresent(output.getParent().resolve(role), leaf); }
    private static void cue(String leaf) throws IOException { P11C4aEvidence.cue(output, leaf); }
    private static void cueOnce(String leaf) throws IOException { if (!P11C4aEvidence.cuePresent(output, leaf)) cue(leaf); }
    private static void finish() throws IOException { stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready"); }
    private static void local(String status) throws IOException {
        int expectedAcks = scenario().equals("d3-old-source") || scenario().equals("d3-late-task") || scenario().equals("d3-old-result") ? 1 : 2;
        require(P11D3Observation.ackCount() == expectedAcks, "EXACT_ORIGINAL_ACK_SUBMISSIONS");
        var facts = new LinkedHashMap<String, Object>(); facts.put("status", status); facts.put("case", scenario());
        facts.put("initialWireSequence", 1); facts.put("admissions", admissions); facts.put("nativeTransfers", transfers);
        facts.put("queuedHitChildren", claims); facts.put("actualFirstActorLogins", logins); facts.put("runtimeEmpty", idle());
        facts.put("actor", P11D3Observation.snapshot(actor).facts()); facts.put("peer", P11D3Observation.snapshot(peer).facts());
        facts.put("observations", P11D3Observation.counters()); facts.put("physicalOsInputClaim", false);
        facts.put("sameJvmRestartClaim", false); P11C4aEvidence.write(output, "d3-result.json", facts);
    }
    public static void rootRetired(MinecraftServer actual) {
        if (P11D3HostProbe.selected()) { P11D3HostProbe.rootRetired(actual); return; }
        if (!selected() || actual != server || output == null) return;
        try {
            var data = P11NativeStorageBoundary.terminalDiagnostics(); require(data != null, "ACTUAL_TERMINAL_DIAGNOSTICS");
            var roots = data.nativeResponsibilities().roots();
            boolean clean = data.nativeStopNormal() && data.failures() == 0 && data.resources().dirtyUuids() == 0
                    && roots.size() == 5 && roots.stream().allMatch(root -> root.count() == 0);
            P11C4aEvidence.write(output, "data-terminal.json", Map.of("status", clean && sealed && failure == null
                    ? "NORMAL_NATIVE_STOP_AND_ROOTS_ZERO" : "TERMINAL_NOT_QUALIFIED", "nativeStopNormal", data.nativeStopNormal(),
                    "sourceFailures", data.failures(), "dirtyUuids", data.resources().dirtyUuids(), "originalWrites", data.writes(),
                    "allRootCounts", roots.stream().map(root -> Map.of("kind", root.kind(), "count", root.count())).toList()));
        } catch (Exception | LinkageError primary) { fail(primary); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void stopped(ServerStoppedEvent event) {
        if (!dedicatedSelected() || server != event.getServer()) return;
        try {
            if (output != null) P11C4aEvidence.write(output, "d3-cost.json", P11D3Observation.costs());
            if (output != null) P11C4aEvidence.write(output, "stopped.json", Map.of("status", sealed && failure == null
                    ? "ORIGINAL_SERVER_STOPPED" : "STOP_AFTER_FAILURE", "failure", failure == null ? "NONE" : failure,
                    "actualLogoutEvents", logouts, "remainingPlayers", server.getPlayerList().getPlayers().size(),
                    "p7", P11D3Observation.counters()));
        } catch (IOException ignored) { }
        finally { P11D3Observation.clear(); synchronized (P11D3ServerHarness.class) { AUTH.clear(); } actor = peer = firstActor = null; firstConnection = null; slot = null; server = null; }
    }
    private static void fail(Throwable primary) {
        if (failure == null) {
            failure = P11C4aEvidence.failureCode(primary);
            try { if (output != null) P11C4aEvidence.write(output, "failure.json", Map.of("status", "FAIL", "failure", failure,
                    "stage", stage.name(), "admissions", admissions, "attempts", attempts, "nativeTransfers", transfers)); }
            catch (IOException ignored) { }
        }
        P11D3Observation.clear();
        if (server != null && server.isSameThread()) { sealed = true; server.halt(false); }
    }
}
