package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Excluded deterministic exact-C native-close interleave. Never substitutes a binding result. */
public final class P11C4aConfigDepartureProbe {
    public enum Mode { INITIAL_JOIN, ENTER_CONFIG_ACK }
    public record Snapshot(int waiting, int entries, boolean targetEntry, boolean targetStarting,
            boolean initialReservation, boolean stopping) { }
    public interface ServiceView {
        Snapshot c4a$departureSnapshot(Connection connection);
        boolean c4a$departureEntriesHeld();
    }
    public interface EntryView { Connection c4a$departureConnection(); }
    public interface LockView { boolean c4a$departureLockHeld(); }
    private static volatile Run active;
    private static final ThreadLocal<Run> RETIRING = new ThreadLocal<>();
    private P11C4aConfigDepartureProbe() { }

    static Mode selectedMode() {
        return switch (P11C4aScenario.MODE) {
            case CONFIG_JOIN_CLOSE -> Mode.INITIAL_JOIN;
            case CONFIG_ACK_CLOSE -> Mode.ENTER_CONFIG_ACK;
            default -> null;
        };
    }

    static boolean ready(ServerPlayer actor) {
        var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.nativeRecipient(actor);
        return body != null && body.complete && source.canCopy(body) && body.account.metadata == null
                && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.COMMAND_CONTEXT.ordinal()] == 0
                && body.account.nativeCounts[P11ControlBudgets.Root.TRANSITION.ordinal()] == 0;
    }

    /** Existing parent arms only after actual A auth->PLAY, before its original b-connect cue. */
    static void start(MinecraftServer server, ServerPlayer peer, Path output) throws IOException {
        require(P11C4aEvidence.enabled() && selectedMode() != null && active == null && server.isSameThread()
                && server.isDedicatedServer() && server.usesAuthentication() && !peer.isFakePlayer()
                && server.getPlayerList().getPlayer(peer.getUUID()) == peer
                && P11NativeStorageBoundary.nativeDeliveryEligible(peer), "DEPARTURE_REAL_PEER_REQUIRED");
        require(output.equals(P11C4aEvidence.root().resolve("server")), "DEPARTURE_OUTPUT");
        active = new Run(server, peer, selectedMode(), output);
        protectRealPeer(active);
        P11C4aEvidence.write(output, "config-departure-armed.json", report(active, "ARMED_REAL_PEER_NOT_ACCEPTANCE"));
    }

    /** Existing fixture shape, but only actual A exists before the initial B JOIN. No fake second actor. */
    private static void protectRealPeer(Run run) throws IOException {
        var peer = run.peer; var level = peer.serverLevel();
        require(peer.isAlive() && peer.fallDistance == 0.0F, "DEPARTURE_PEER_ALREADY_UNSAFE");
        int floorY = Math.max(64, peer.blockPosition().getY() + 4);
        var minimum = new BlockPos(peer.blockPosition().getX() + 16, floorY, peer.blockPosition().getZ() + 16);
        var maximum = minimum.offset(6, 4, 6);
        require(floorY > level.getMinBuildHeight() && maximum.getY() < level.getMaxBuildHeight()
                && level.getWorldBorder().isWithinBounds(minimum) && level.getWorldBorder().isWithinBounds(maximum),
                "DEPARTURE_PEER_ENCLOSURE_BOUNDS");
        require(level.getEntities((Entity) null, new AABB(minimum.getX(), minimum.getY(), minimum.getZ(),
                maximum.getX() + 1.0, maximum.getY() + 1.0, maximum.getZ() + 1.0)).isEmpty(),
                "DEPARTURE_PEER_ENCLOSURE_EXISTING_ENTITY");
        for (var position : BlockPos.betweenClosed(minimum, maximum)) {
            require(level.getBlockState(position).isAir() && level.getBlockEntity(position) == null,
                    "DEPARTURE_PEER_ENCLOSURE_NOT_EMPTY");
        }
        float healthBefore = peer.getHealth(); int placed = 0;
        for (int x = 0; x < 7; x++) { for (int y = 0; y < 5; y++) { for (int z = 0; z < 7; z++) {
            if (x != 0 && x != 6 && y != 0 && y != 4 && z != 0 && z != 6) { continue; }
            var block = x == 3 && y == 4 && z == 3 ? Blocks.SEA_LANTERN : Blocks.GLASS;
            require(level.setBlock(minimum.offset(x, y, z), block.defaultBlockState(), 3), "DEPARTURE_PEER_ENCLOSURE_WRITE");
            placed++;
        } } }
        double x = minimum.getX() + 3.5, y = floorY + 1.0, z = minimum.getZ() + 3.5;
        peer.connection.teleport(x, y, z, peer.getYRot(), peer.getXRot());
        require(placed == 170 && peer.getX() == x && peer.getY() == y && peer.getZ() == z
                && peer.getHealth() == healthBefore, "DEPARTURE_PEER_ENCLOSURE_TELEPORT_RESULT");
        unchangedPeer(run);
        P11C4aEvidence.write(run.output, "config-departure-peer-safety.json", Map.of(
                "status", "ORDINARY_REAL_A_ENCLOSURE_NATIVE_TELEPORT_NOT_IMMUNITY", "placedBlocks", placed,
                "glassBlocks", placed - 1, "seaLanternBlocks", 1, "interiorAirBlocks", 75,
                "healthBefore", healthBefore, "healthAfter", peer.getHealth(), "sameActorConnectionSource", true));
    }

    /** Called after the parent's original successful ProfileResult receipt, never from a setting. */
    static void authenticated(MinecraftServer server, Connection connection, UUID id) {
        var run = active;
        if (run == null || run.server != server || connection == run.peerC) { return; }
        if (run.targetC != null || id == null || id.equals(run.peer.getUUID()) || !connection.isConnected()
                || !connection.isEncrypted() || connection.isMemoryConnection()) {
            fail(run, "DEPARTURE_AUTH_IDENTITY"); return;
        }
        run.targetId = id; run.authReceipts = 1; run.targetC = connection;
    }

    static boolean tick(ServerPlayer actualTarget) throws IOException {
        var run = active;
        require(run != null && run.server.isSameThread() && run.failure == null && ++run.ticks <= 1200,
                "DEPARTURE_OWNER_OR_DEADLINE");
        unchangedPeer(run);
        if (run.mode == Mode.ENTER_CONFIG_ACK && !run.closeArmed) {
            if (actualTarget == null || !P11NativeStorageBoundary.nativeDeliveryEligible(actualTarget) || !ready(actualTarget)
                    || !P11C4aEvidence.receiptPresent(run.output.getParent().resolve("client-b"), "config-departure-play.json")) { return false; }
            require(run.authReceipts == 1 && actualTarget.getUUID().equals(run.targetId)
                    && actualTarget.connection.getConnection() == run.targetC && actualTarget.isAlive(),
                    "DEPARTURE_ACK_REAL_TARGET");
            run.target = actualTarget;
            run.targetSource = P11NativeStorageBoundary.nativeSourceOwner(actualTarget);
            run.targetBody = run.targetSource == null ? null : run.targetSource.nativeRecipient(actualTarget);
            require(run.targetBody != null && run.targetBody.complete, "DEPARTURE_ACK_SOURCE");
            baseline(run);
            run.closeArmed = true;
            run.switched = true;
            actualTarget.connection.switchToConfig(); // One original native sender, no returnToWorld call.
            return false;
        }
        if (run.returned == 0) { return false; }
        require(run.targetC != null && !run.targetC.isConnected() && run.authReceipts == 1
                && run.bindCalls == 1 && run.emptyReturns == 1 && run.closeCalls == 1 && run.closeReturns == 1
                && run.returned == 1 && run.constructed == 0 && run.closeUnlocked,
                "DEPARTURE_NOT_ONE_ORIGINAL_CLOSED_BIND");
        Snapshot live = ((ServiceView) run.service).c4a$departureSnapshot(run.targetC);
        if (live.targetStarting()) { return false; } // Existing newcomer sweep owns a possible FALSE marker.
        if (run.server.getTickCount() <= run.returnTick + 3) { return false; }
        require(!live.stopping() && !live.targetEntry() && live.waiting() == run.before.waiting() - 1
                && live.entries() == run.before.entries() - (run.mode == Mode.ENTER_CONFIG_ACK ? 1 : 0)
                && run.server.getPlayerList().getPlayer(run.targetId) == null, "DEPARTURE_RETIREMENT_OR_LIVENESS");
        unchangedNativeCounts(run);
        require(run.identities.retainedDataAccounts() == run.dataAccountsBefore, "DEPARTURE_DATA_ACCOUNT_DISCHARGED");
        if (run.targetBody != null) {
            require(run.targetSource.body(run.target) == run.targetBody && run.targetBody.complete
                    && run.targetBody.source == run.materialSource && run.targetBody.source.epoch() == run.sourceEpoch
                    && run.targetBody.envelope == run.envelope && run.envelope != null,
                    "DEPARTURE_DETACHED_SOURCE_OR_ROOTS_CHANGED");
        }
        if (!run.peerCue) {
            run.peerCue = true; P11C4aEvidence.cue(run.output, "a-config-departure-action.ready"); return false;
        }
        if (run.peerActions != 1 || !P11C4aEvidence.receiptPresent(run.output.getParent().resolve("client-b"), "config-departure-terminal.json")) { return false; }
        if (run.mode == Mode.ENTER_CONFIG_ACK) {
            require(run.retireCalls == 1 && run.retireReturns == 1 && run.dispatchRetired == 1
                    && run.identityRetired == 1 && run.targetLogouts == 1, "DEPARTURE_ACK_NATIVE_RETIREMENT");
        } else {
            require(run.retireCalls == 0 && run.retireReturns == 0 && run.identityRetired == 0
                    && run.targetLogouts == 0 && run.before.initialReservation() && !run.before.targetEntry(),
                    "DEPARTURE_JOIN_RESERVATION_ONLY");
        }
        if (!run.qualified) {
            run.qualified = true;
            P11C4aEvidence.write(run.output, "config-departure.json", report(run, "ACTUAL_EXACT_C_CLOSE_BIND_EMPTY_NORMAL_RETURN_AND_LIVE_PEER"));
        }
        return true;
    }

    /** Exact native binding invocation only. No catch/retry/Optional construction or native lock wait. */
    public static Optional<?> bind(Object service, Object owner, ServerConfigurationPacketListenerImpl listener,
            Mode point, Operation<Optional<?>> original) {
        var run = active;
        if (run == null || run.mode != point || !run.closeArmed || listener.getConnection() != run.targetC) {
            return original.call(owner, listener);
        }
        require(run.server.isSameThread() && run.authReceipts == 1 && run.bindCalls == 0
                && listener.getMainThreadEventLoop() == run.server && run.targetC.getPacketListener() == listener
                && run.targetC.isConnected() && listener.getOwner().getId().equals(run.targetId),
                "DEPARTURE_EXACT_AUTHENTICATED_CONFIG");
        unchangedPeer(run);
        var view = (ServiceView) service;
        var guard = ((P11KeepAliveBoundary.ConnectionAccess) run.targetC).p11$keepAliveGuard();
        run.closeUnlocked = !view.c4a$departureEntriesHeld() && !Thread.holdsLock(owner)
                && !((LockView) (Object) guard).c4a$departureLockHeld();
        require(run.closeUnlocked, "DEPARTURE_NATIVE_CLOSE_UNDER_LOCK");
        run.service = service; run.identities = (P11IdentityOwner) owner;
        run.before = view.c4a$departureSnapshot(run.targetC);
        require(run.before.waiting() > 0 && !run.before.stopping()
                && (point == Mode.INITIAL_JOIN ? run.before.initialReservation() && !run.before.targetEntry()
                        : run.before.targetEntry()), "DEPARTURE_ORIGINAL_K_OWNERSHIP");
        run.dataAccountsBefore = run.identities.retainedDataAccounts();
        if (run.mode == Mode.INITIAL_JOIN) { baseline(run); }
        else {
            run.materialSource = run.targetBody.source; run.sourceEpoch = run.materialSource.epoch();
            run.envelope = run.targetBody.envelope; run.roots = run.targetBody.account.nativeCounts.clone();
            require(run.targetBody.logoutAttempted && !run.targetBody.logoutActive && run.envelope != null,
                    "DEPARTURE_ACK_BEFORE_ORIGINAL_LOGOUT_ENVELOPE");
        }
        run.bindCalls++;
        run.closeCalls++;
        run.targetC.disconnect(Component.literal("P11 owned exact configuration departure fixture"));
        run.closeReturns++;
        require(!run.targetC.isConnected(), "DEPARTURE_NATIVE_CLOSE_NOT_COMPLETE");
        Optional<?> result = original.call(owner, listener);
        if (result.isEmpty()) { run.emptyReturns++; }
        return result;
    }

    public static void returned(Object service, Connection connection, Mode point) {
        var run = active;
        if (run == null || run.mode != point || connection != run.targetC || run.service != service || run.bindCalls == 0) { return; }
        run.returned++; run.returnTick = run.server.getTickCount();
        run.after = ((ServiceView) service).c4a$departureSnapshot(connection);
        if (run.after.targetEntry() || run.after.waiting() != run.before.waiting() - 1) { fail(run, "DEPARTURE_NORMAL_RETURN_K_OR_ENTRY"); }
        if (run.targetBody != null) {
            run.rootsAtReturn = run.targetBody.account.nativeCounts.clone();
            if (!Arrays.equals(run.roots, run.rootsAtReturn)) { fail(run, "DEPARTURE_RETIRE_DISCHARGED_DATA_ROOT"); }
        }
    }

    public static boolean beginRetire(Object entry) {
        var run = active;
        if (run == null || !(entry instanceof EntryView view) || view.c4a$departureConnection() != run.targetC || run.bindCalls == 0) { return false; }
        if (RETIRING.get() != null) { fail(run, "DEPARTURE_NESTED_RETIRE"); return false; }
        run.retireCalls++; RETIRING.set(run); return true;
    }
    public static void endRetire(boolean selected, boolean normal) {
        if (!selected) { return; }
        var run = RETIRING.get(); RETIRING.remove();
        if (run != null) { if (normal) { run.retireReturns++; } else { fail(run, "DEPARTURE_ORIGINAL_RETIRE_THROW"); } }
    }
    public static void retired(boolean identity, Object capture, boolean returned) {
        var run = RETIRING.get();
        if (run == null) { return; }
        if (!returned) { fail(run, "DEPARTURE_ORIGINAL_RETIRE_FALSE"); return; }
        if (identity) {
            var actual = (P11IdentityOwner.CapturedIdentity) capture;
            if (!actual.uuid().equals(run.targetId) || actual.currentBinding()) { fail(run, "DEPARTURE_IDENTITY_NOT_RETIRED"); }
            run.identityRetired++;
        } else { run.dispatchRetired++; }
    }
    public static void constructed(ServerPlayer actor) {
        var run = active;
        if (run != null && run.closeArmed && run.targetId != null && actor.getServer() == run.server
                && actor.getUUID().equals(run.targetId)) { run.constructed++; }
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var run = active;
        if (run != null && run.peerCue && listener == run.peer.connection) { run.peerActions++; }
    }
    static void logout(ServerPlayer actor) {
        var run = active;
        if (run != null && run.target != null && actor == run.target) { run.targetLogouts++; }
    }
    static boolean qualified() { var run = active; return run != null && run.qualified; }
    static void failureCleanup() { var run = active; if (run != null) { run.cleanup = true; fail(run, "DEPARTURE_ENGINEERING_FAILURE_CLEANUP"); } }

    static void stopped(MinecraftServer server) throws IOException {
        var run = active;
        if (run == null || run.server != server) { return; }
        var terminal = P11NativeStorageBoundary.terminalDiagnostics();
        boolean complete = run.qualified && !run.cleanup && terminal != null && terminal.nativeStopNormal()
                && terminal.failures() == 0 && terminal.resources().inFlight() == 0
                && terminal.resources().sealedSnapshots() == 0 && terminal.resources().dirtyUuids() == 0;
        if (!complete) { fail(run, "DEPARTURE_ORIGINAL_DATA_STOP_NOT_COMPLETE"); }
        var values = report(run, complete ? "ORIGINAL_NATIVE_STOP_AFTER_SEALED_DEPARTURE_WITH_NO_DIRTY_DATA"
                : "FAILED_ORIGINAL_DATA_STOP_EVIDENCE");
        values.put("terminalDiagnosticsPresent", terminal != null);
        if (terminal != null) {
            values.put("nativeStopNormal", terminal.nativeStopNormal()); values.put("nativeWrites", terminal.writes());
            values.put("nativeSerializations", terminal.serializations()); values.put("nativeFailures", terminal.failures());
            values.put("remainingDirtyUuids", terminal.resources().dirtyUuids());
            values.put("physicalWriterSuccesses", terminal.saveProgress().kinds().stream().map(kind -> Map.of(
                    "kind", kind.kind().name(), "successfulPhysicalWrites", kind.successfulPhysicalWrites(), "dirtySources", kind.dirtySources())).toList());
        }
        P11C4aEvidence.write(run.output, "config-departure-stopped.json", values);
    }

    private static void baseline(Run run) {
        run.loginFrames = count(run, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED);
        run.respawnFrames = count(run, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED);
        run.factories = count(run, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED);
        run.bodies = count(run, P11C4aNativeObservations.Event.CONFIG_CALL_ENTER);
    }
    private static void unchangedNativeCounts(Run run) {
        require(count(run, P11C4aNativeObservations.Event.LOGIN_FRAME_PRODUCED) == run.loginFrames
                && count(run, P11C4aNativeObservations.Event.RESPAWN_FRAME_PRODUCED) == run.respawnFrames
                && count(run, P11C4aNativeObservations.Event.FACTORY_TICKET_CHECKED) == run.factories
                && count(run, P11C4aNativeObservations.Event.CONFIG_CALL_ENTER) == run.bodies,
                "DEPARTURE_NEW_BODY_FACTORY_OR_NATIVE_FRAME");
    }
    private static long count(Run run, P11C4aNativeObservations.Event event) { return P11C4aNativeObservations.count(run.targetC, event); }
    private static void unchangedPeer(Run run) {
        require(run.server.isRunning() && run.peerC.isConnected() && run.peer.isAlive()
                && run.peerC.getPacketListener() == run.peer.connection
                && run.server.getPlayerList().getPlayer(run.peer.getUUID()) == run.peer
                && run.peer.getStats() == run.peerBody.stats && run.peer.getAdvancements() == run.peerBody.advancements
                && P11NativeStorageBoundary.nativeSourceOwner(run.peer) == run.peerSource
                && run.peerSource.nativeRecipient(run.peer) == run.peerBody, "DEPARTURE_LIVE_PEER_CHANGED");
    }
    private static Map<String, Object> report(Run run, String status) {
        var values = new LinkedHashMap<String, Object>();
        values.put("status", status); values.put("mode", run.mode.name());
        values.put("actualTargetProfileResultReceipts", run.authReceipts); values.put("bindingCalls", run.bindCalls);
        values.put("actualOriginalOptionalEmptyReturns", run.emptyReturns); values.put("nativeCloseCalls", run.closeCalls);
        values.put("nativeCloseReturns", run.closeReturns); values.put("originalMethodNormalReturns", run.returned);
        values.put("closeOutsideEntriesAndKeepAliveLock", run.closeUnlocked); values.put("before", run.before); values.put("after", run.after);
        values.put("newTargetConstructors", run.constructed); values.put("retireCalls", run.retireCalls);
        values.put("retireReturns", run.retireReturns); values.put("originalDispatcherRetireTrue", run.dispatchRetired);
        values.put("originalIdentityRetireTrue", run.identityRetired); values.put("targetOriginalLogouts", run.targetLogouts);
        values.put("retainedDataAccountsAtBind", run.dataAccountsBefore); values.put("peerActualAnimateReturns", run.peerActions);
        values.put("nativeDataRootsImmediatelyBeforeBind", run.roots);
        values.put("nativeDataRootsAtOriginalMethodReturn", run.rootsAtReturn);
        values.put("peerSameActorConnectionSourceCanonicalObjects", run.peerBody != null);
        values.put("deterministicNativeCloseIntervention", true); values.put("cohort16CauseClaimed", false);
        values.put("fullC4aAcceptance", false); values.put("failure", run.failure == null ? "NONE" : run.failure);
        return values;
    }
    private static void fail(Run run, String code) { if (run.failure == null) { run.failure = code; } }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, code); }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer peer; final Connection peerC; final Mode mode; final Path output;
        final P11QualifiedSourceOwner peerSource; final P11QualifiedSourceOwner.Body peerBody;
        volatile Connection targetC; volatile UUID targetId; volatile int authReceipts; volatile String failure;
        ServerPlayer target; P11QualifiedSourceOwner targetSource; P11QualifiedSourceOwner.Body targetBody;
        P11IdentityOwner identities; P11ReceiptLedger.Source materialSource; Object envelope, service;
        long sourceEpoch; long[] roots, rootsAtReturn; Snapshot before, after;
        boolean closeArmed, switched, closeUnlocked, peerCue, qualified, cleanup;
        int ticks, bindCalls, emptyReturns, closeCalls, closeReturns, returned, returnTick, constructed;
        int retireCalls, retireReturns, dispatchRetired, identityRetired, targetLogouts, dataAccountsBefore, peerActions;
        long loginFrames, respawnFrames, factories, bodies;
        Run(MinecraftServer server, ServerPlayer peer, Mode mode, Path output) {
            this.server = server; this.peer = peer; this.mode = mode; this.output = output;
            peerC = peer.connection.getConnection(); peerSource = P11NativeStorageBoundary.nativeSourceOwner(peer);
            peerBody = peerSource == null ? null : peerSource.nativeRecipient(peer);
            require(peerBody != null && peerC.isEncrypted() && !peerC.isMemoryConnection(), "DEPARTURE_PEER_SOURCE");
            closeArmed = mode == Mode.INITIAL_JOIN;
        }
    }
}
