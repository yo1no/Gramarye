package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Two original P7/P5 attempts; unmanaged-source refusal is not a managed dirty-gate observation. */
public final class P11L1CapacityWorkProbe {
    public interface RuntimeAccess { Object p11$l1CapacitySlot(MinecraftServer server); }
    private static Run run;
    private P11L1CapacityWorkProbe() {}
    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(run == null && server.isSameThread() && actor != peer, "ARM");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.nativeRecipient(actor);
        require(body != null && owner.canCopy(body) && owner.nativeRecipient(peer) == null
                && owner.diagnostics(peer.getUUID()).sourceFault().equals("UNMANAGED")
                && owner.diagnostics(actor.getUUID()).resources().retainedUuids() == 1, "REAL_T_FULL_MANAGED_AND_UNMANAGED");
        run = new Run(server, actor, peer, output, owner, body); current(run);
        P11C4aEvidence.write(output, "capacity-work-armed.json", facts(run, "ARMED_NOT_QUALIFIED"));
        P11C4aEvidence.cue(output, "a-starter.ready"); P11C4aEvidence.cue(output, "b-starter.ready");
    }
    public static void starterReturned(CommandSourceStack source, int result) {
        var r = run; if (r == null || r.complete || source.getServer() != r.server) { return; }
        if (source.getEntity() != r.actor && source.getEntity() != r.peer) { return; }
        observe(r, () -> {
            current(r); require(source.source == source.getEntity() && result == 1, "ORIGINAL_STARTER_SUCCESS_REQUIRED");
            if (source.getEntity() == r.actor) { require(++r.actorStarter == 1, "ONE_A_STARTER"); }
            else { require(++r.peerStarter == 1, "ONE_B_STARTER"); }
        });
    }
    static void instanceCreated(ServerPlayer actor, Object value) {
        var r = run; if (r == null || r.complete || actor != r.actor && actor != r.peer) { return; }
        observe(r, () -> {
            current(r); require(value instanceof ServerSlot.InstanceState, "ORIGINAL_INSTANCE");
            var instance = (ServerSlot.InstanceState) value;
            require(instance.hasP9AuthenticatedActorWitness(actor) && instance.work == null, "ORIGINAL_PROSPECTIVE_WITNESS");
            if (actor == r.peer) { require(r.peerInstance == null && r.peerCue, "ONE_B_INSTANCE"); r.peerInstance = instance; }
            else { require(r.actorInstance == null && r.actorCue && r.peerRefusals == 1, "ONE_A_INSTANCE_AFTER_B"); r.actorInstance = instance; }
        });
    }
    public static void managedGateReturned(Object accountOwner, boolean result) {
        var r = run; if (r == null || r.complete) { return; }
        observe(r, () -> {
            require(r.server.isSameThread() && r.actorCue && r.peerRefusals == 1
                    && accountOwner == r.body.account.resource && !result, "EXACT_A_MANAGED_GATE_FALSE");
            var counts = r.owner.diagnostics(r.actor.getUUID()).resources();
            require(counts.retainedUuids() == 1 && counts.dirtyUuids() == 1, "REAL_DIRTY_ONE_GATE");
            require(++r.managedGateReturns == 1, "ONE_MANAGED_GATE"); r.gateDirty = counts.dirtyUuids();
        });
    }
    static void admitted(Object runtime, MinecraftServer server, ServerPlayer actor, Object result) {
        var r = run; if (r == null || r.complete || actor != r.actor && actor != r.peer) { return; }
        observe(r, () -> {
            current(r); require(server == r.server && result instanceof RuntimeAdmissionResult.OwnerInstanceUnavailable,
                    "ACTUAL_P5_OWNER_INSTANCE_REFUSAL");
            var instance = actor == r.actor ? r.actorInstance : r.peerInstance;
            require(instance != null && instance.work == null && instance.lease.pin.isClosed()
                    && instance.activeProjectileContinuation == null, "PROVISIONAL_PIN_CLOSED_WITHOUT_W");
            require(runtime instanceof RuntimeAccess && ((RuntimeAccess) runtime).p11$l1CapacitySlot(server) instanceof ServerSlot,
                    "ACTUAL_RUNTIME_SLOT");
            var slot = (ServerSlot) ((RuntimeAccess) runtime).p11$l1CapacitySlot(server);
            require(slot.state == ServerSlot.State.RUNNING && slot.queue.isEmpty() && slot.eventIndex.isEmpty()
                    && slot.instances.isEmpty() && slot.activeProjectileContinuations.isEmpty() && slot.leases.isEmpty()
                    && slot.committedPending == 0 && slot.reservedPending == 0 && slot.currentReservationCount == 0
                    && slot.currentEvent == null && slot.eventSequenceHighWater == 0 && slot.skillInstanceSequenceHighWater == 0,
                    "NO_PUBLISHED_ROOT_EVENT_OR_RESERVATION");
            if (actor == r.peer) {
                require(r.peerCue && ++r.peerRefusals == 1 && r.managedGateReturns == 0, "UNMANAGED_REFUSAL_BEFORE_MANAGED_GATE");
            } else { require(r.actorCue && ++r.actorRefusals == 1 && r.managedGateReturns == 1, "A_ACTUAL_MANAGED_DIRTY_REFUSAL"); }
        });
    }
    static void transferred(Object disposition) { unexpected(disposition == RuntimePermitTransferDisposition.TRANSFERRED, "UNEXPECTED_TRANSFER"); }
    static void claimed(Object disposition) { unexpected(disposition == RuntimePermitClaimDisposition.QUEUED, "UNEXPECTED_CLAIM"); }
    static void damageEntering(net.minecraft.world.damagesource.DamageSource source) {
        var r = run; if (r != null && !r.complete && (source.getEntity() == r.actor || source.getEntity() == r.peer)) {
            r.failure = "UNEXPECTED_ACTOR_DAMAGE";
        }
    }
    public static void animate(ServerGamePacketListenerImpl listener) {
        var r = run; if (r == null || r.complete || listener != r.peer.connection) { return; }
        observe(r, () -> { current(r); require(r.swingCue && r.actorRefusals == 1 && ++r.swings == 1, "ONE_ORIGINAL_B_SWING_RETURN"); });
    }
    static void tick() throws IOException {
        var r = run; if (r == null || r.complete) { return; }
        require(r.failure.equals("NONE") && ++r.ticks <= 2400, "OBSERVER_OR_DEADLINE"); current(r);
        if (r.actorStarter != 1 || r.peerStarter != 1) { return; }
        if (!r.peerCue) { r.peerCue = true; P11C4aEvidence.cue(r.output, "b-cast-1.ready"); return; }
        if (r.peerRefusals != 1) { return; }
        if (!r.actorCue) {
            P11C4aEvidence.write(r.output, "capacity-work-b-refused.json",
                    facts(r, "ACTUAL_UNMANAGED_P5_REFUSAL_BEFORE_MANAGED_ATTEMPT"));
            r.actorCue = true; P11C4aEvidence.cue(r.output, "a-cast-1.ready"); return;
        }
        if (r.actorRefusals != 1) { return; }
        if (!r.swingCue) { r.swingCue = true; P11C4aEvidence.cue(r.output, "b-capacity-swing.ready"); return; }
        if (r.swings != 1 || !P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve("client-b"), "capacity-swing.json")) { return; }
        require(r.managedGateReturns == 1 && r.gateDirty == 1 && workCount(r) == 0, "BOTH_DISTINCT_REFUSALS");
        r.complete = true;
        P11C4aEvidence.write(r.output, "capacity-work-result.json", facts(r, "ACTUAL_UNMANAGED_AND_MANAGED_DIRTY_NEW_WORK_REFUSALS"));
    }
    static boolean complete() { return run != null && run.complete && run.failure.equals("NONE"); }
    static void release() { run = null; }
    private static void unexpected(boolean condition, String code) {
        var r = run; if (r != null && !r.complete && condition) { r.failure = code; }
    }
    private static void observe(Run r, Runnable body) {
        try { body.run(); } catch (RuntimeException | Error ignored) { r.failure = "CAPACITY_OBSERVER_FAILURE"; }
    }
    private static long workCount(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static void current(Run r) {
        require(r.server.isSameThread() && r.actor.isAlive() && r.peer.isAlive() && !r.actor.isRemoved() && !r.peer.isRemoved()
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer
                && r.actor.connection.getConnection() == r.actorConnection && r.actorConnection.isConnected()
                && r.peer.connection.getConnection() == r.peerConnection && r.peerConnection.isConnected(), "CURRENT_AUTHENTICATED_ACTORS");
        require(P11NativeStorageBoundary.nativeSourceOwner(r.actor) == r.owner && r.owner.nativeRecipient(r.actor) == r.body
                && r.body.account.current == r.body && r.body.source.epoch() == r.epoch && r.body.source.version() >= r.lastVersion
                && r.body.advancements == r.advancements && r.body.stats == r.stats && r.owner.canCopy(r.body)
                && r.owner.nativeRecipient(r.peer) == null && r.owner.diagnostics(r.peer.getUUID()).sourceFault().equals("UNMANAGED")
                && r.owner.diagnostics(r.actor.getUUID()).resources().retainedUuids() == 1 && workCount(r) == 0,
                "EXACT_RETAINED_A_AND_UNMANAGED_B_UNCHANGED");
        r.lastVersion = r.body.source.version();
    }
    private static LinkedHashMap<String,Object> facts(Run r, String status) {
        var f = new LinkedHashMap<String,Object>(); f.put("status", status); f.put("failure", r.failure);
        f.put("actualStarterAReturns", r.actorStarter); f.put("actualStarterBReturns", r.peerStarter);
        f.put("unmanagedP5Refusals", r.peerRefusals); f.put("managedDirtyP5Refusals", r.actorRefusals);
        f.put("actualManagedMayAdmitWorkFalseReturns", r.managedGateReturns); f.put("dirtyAtManagedGate", r.gateDirty);
        f.put("originalPeerSwingReturns", r.swings); f.put("retainedUuids", 1); f.put("workRoots", workCount(r));
        f.put("initialSourceVersion", r.initialVersion); f.put("lastObservedSourceVersion", r.lastVersion);
        f.put("sameRetainedAccountBodyEpochPAStats", true); f.put("peerStillUnmanaged", true);
        f.put("noPublishedRootEventOrReservation", r.actorRefusals == 1 && r.peerRefusals == 1);
        f.put("unmanagedRefusalIsManagedWatermarkClaim", false); return f;
    }
    private static void require(boolean condition, String code) { if (!condition) { throw new IllegalStateException("L1_CAPACITY_" + code); } }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final Path output;
        final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body body;
        final Object advancements, stats; final long epoch, initialVersion;
        long lastVersion;
        final net.minecraft.network.Connection actorConnection, peerConnection;
        ServerSlot.InstanceState actorInstance, peerInstance;
        int ticks, actorStarter, peerStarter, actorRefusals, peerRefusals, managedGateReturns, gateDirty, swings;
        boolean peerCue, actorCue, swingCue, complete; String failure = "NONE";
        Run(MinecraftServer s, ServerPlayer a, ServerPlayer p, Path out, P11QualifiedSourceOwner o, P11QualifiedSourceOwner.Body b) {
            server=s; actor=a; peer=p; output=out; owner=o; body=b; epoch=b.source.epoch(); initialVersion=b.source.version(); lastVersion=initialVersion;
            advancements=b.advancements; stats=b.stats; actorConnection=a.connection.getConnection(); peerConnection=p.connection.getConnection();
        }
    }
}
