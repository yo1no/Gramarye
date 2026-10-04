package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;

/** Excluded controlled native tracking-state regression; not an actual chunk-unload claim. */
public final class P11L1TrackingBoundaryProbe {
    public interface LevelAccess {
        PersistentEntitySectionManager<Entity> p11$l1EntityManager();
    }
    private static Run run;
    private P11L1TrackingBoundaryProbe() {}

    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, Path output) throws IOException {
        require(run == null && server.isSameThread() && actor != peer && actor.getServer() == server
                && peer.getServer() == server && actor.serverLevel() == peer.serverLevel(), "ARM_IDENTITY");
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canCopy(body) && owner.canSerialize(body)
                && body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] == 0, "FRESH_SOURCE");
        var r = new Run(server, actor, peer, output, body);
        current(r); run = r;
        P11C4aEvidence.write(output, "tracking-retirement-armed.json", facts(r, "ARMED_NOT_QUALIFIED"));
        P11C4aEvidence.cue(output, "a-cast-1.ready");
    }

    static void instanceCreated(ServerPlayer actor, Object value) {
        var r = run; if (r == null || actor != r.actor || r.complete) { return; }
        observe(r, () -> {
            require(r.instance == null && value instanceof ServerSlot.InstanceState, "ONE_INSTANCE");
            r.instance = (ServerSlot.InstanceState) value;
            require(r.instance.hasP9AuthenticatedActorWitness(actor) && r.instance.work == null, "ORIGINAL_WITNESS");
        });
    }

    static void accepted(MinecraftServer server, ServerPlayer actor, Object value) {
        var r = run; if (r == null || actor != r.actor || r.complete) { return; }
        observe(r, () -> {
            require(server == r.server && !r.accepted && value instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                    "ONE_ACTUAL_ACCEPTANCE");
            var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) value;
            require(r.instance != null && r.instance.id.equals(accepted.eventToken().skillInstanceId())
                    && r.instance.work != null && r.instance.work.qualifies(actor) && count(r) == 1, "EXACT_ACCEPTED_WORK");
            r.work = r.instance.work; r.accepted = true;
        });
    }

    static void transferred(Object value, Object disposition) {
        var r = run; if (r == null || r.complete || disposition != RuntimePermitTransferDisposition.TRANSFERRED) { return; }
        observe(r, () -> {
            require(r.accepted && r.projectile == null && value instanceof P9StarterProjectile, "ONE_REAL_TRANSFER");
            r.projectile = (P9StarterProjectile) value; r.permit = r.instance.activeProjectileContinuation;
            require(r.permit != null && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && r.permit.qualifiedActor(r.server, r.projectile) == r.actor
                    && r.level.getEntity(r.projectile.getUUID()) == r.projectile, "EXACT_LOADED_OPEN");
        });
    }

    static void claimed(Object disposition) {
        var r = run; if (r != null && !r.complete && disposition == RuntimePermitClaimDisposition.QUEUED) {
            r.claims++; r.failure = "UNEXPECTED_CHILD";
        }
    }

    static void damageEntering(DamageSource source) {
        var r = run; if (r != null && source.getDirectEntity() == r.projectile) {
            r.hurts++; r.failure = "UNEXPECTED_HURT";
        }
    }

    /** Called at normal server Post, after the actual natural projectile has flown. */
    static void tick() throws IOException {
        var r = run; if (r == null || r.complete) { return; }
        require(r.server.isSameThread() && r.failure.equals("NONE") && ++r.ticks <= 2400, "OBSERVER_OR_DEADLINE");
        current(r);
        if (!r.accepted || r.projectile == null) { return; }
        require(r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN && count(r) == 1
                && r.instance.work == r.work && r.work.qualifies(r.actor) && !r.projectile.isRemoved(), "WORK_BEFORE_TRACKING");
        var chunk = r.projectile.chunkPosition();
        // Never demote the real actors' chunks. Original movement alone reaches this neighboring chunk.
        if (r.projectile.tickCount == 0 || chunk.equals(r.actor.chunkPosition()) || chunk.equals(r.peer.chunkPosition())) { return; }
        var manager = ((LevelAccess) r.level).p11$l1EntityManager();
        require(r.projectile.tickCount < 100 && manager.canPositionTick(chunk)
                && r.level.getEntity(r.projectile.getUUID()) == r.projectile, "ORIGINAL_TICKING_P9_CHUNK");
        require(manager.isLoaded(r.projectile.getUUID()) && manager.getEntityGetter().get(r.projectile.getUUID()) == r.projectile,
                "ACTUAL_MANAGER_OWNS_ORIGINAL_ENTITY");
        r.ageBefore = r.projectile.tickCount; r.requested = true;
        P11C4aEvidence.write(r.output, "tracking-retirement-before.json", facts(r, "REAL_OPEN_BEFORE_CONTROLLED_NATIVE_API"));
        Throwable primary = null;
        try {
            manager.updateChunkStatus(chunk, Visibility.HIDDEN); r.hiddenReturns++;
            require(r.projectile.isRemoved() && !manager.isLoaded(r.projectile.getUUID())
                    && manager.getEntityGetter().get(r.projectile.getUUID()) == null, "HIDDEN_RETURN_MUST_REMOVE_NATIVE_OWNERSHIP");
            manager.updateChunkStatus(chunk, Visibility.TRACKED); r.trackedReturns++;
            require(!manager.canPositionTick(chunk) && !manager.isLoaded(r.projectile.getUUID())
                    && manager.getEntityGetter().get(r.projectile.getUUID()) == null
                    && r.projectile.tickCount == r.ageBefore, "NON_TICKING_RETRACK_CANNOT_RETAIN_OR_REOPEN");
        } catch (RuntimeException | Error failure) {
            primary = failure; throw failure;
        } finally {
            // Restore this controlled fixture's initial ticking status via the same original API.
            // This is not a ticket/unload fixture and never fabricates ChunkEvent.Unload.
            try { manager.updateChunkStatus(chunk, Visibility.TICKING); r.restoreReturns++; }
            catch (RuntimeException | Error restoreFailure) {
                r.failure = "ORIGINAL_STATUS_RESTORE_FAILED";
                if (primary == null) { throw restoreFailure; }
            }
        }
        require(r.permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                && r.instance.activeProjectileContinuation == null && r.instance.work == null
                && r.instance.lease.pin.isClosed() && r.instance.p9ActorWitness() == null && !r.work.qualifies(r.actor)
                && count(r) == 0 && r.claims == 0 && r.hurts == 0
                && r.instance.p9Diagnostic != null
                && r.instance.p9Diagnostic.terminalReason == ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED,
                "EXACT_WORK_AND_WITNESS_CUSTODY_TERMINAL");
        require(r.actor.totalExperience == r.initialExperience && r.actor.getScore() == r.initialScore
                && r.projectile.getOwner() == null && !manager.isLoaded(r.projectile.getUUID()), "NO_REWARD_OR_REVIVAL");
        current(r); r.complete = true;
        P11C4aEvidence.write(r.output, "tracking-retirement-result.json",
                facts(r, "NATIVE_CONTROLLED_HIDDEN_TRACKED_RETIREMENT_NOT_PHYSICAL_UNLOAD"));
    }

    static boolean complete() { return run != null && run.complete && run.failure.equals("NONE"); }
    static String failureCode() { return run == null ? "NONE" : run.failure; }
    static void release() { run = null; }
    private static long count(Run r) { return r.body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()]; }
    private static void current(Run r) {
        require(r.actor.getServer() == r.server && r.actor.serverLevel() == r.level && !r.actor.isRemoved()
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor && r.actor.connection == r.connection
                && r.connection.getConnection().isConnected() && !r.peer.isRemoved()
                && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer
                && r.peer.connection.getConnection().isConnected(), "EXACT_LIVE_ACTORS");
    }
    private static void observe(Run r, Runnable observation) {
        try { observation.run(); } catch (RuntimeException | Error ignored) { r.failure = "TRACKING_OBSERVER_FAILURE"; }
    }
    private static LinkedHashMap<String,Object> facts(Run r, String status) {
        var f = new LinkedHashMap<String,Object>();
        f.put("status", status); f.put("failure", r.failure); f.put("accepted", r.accepted);
        f.put("workCount", count(r)); f.put("controlledNativeVisibilityCalls", true);
        f.put("physicalChunkUnloadedClaim", false); f.put("nativeHeapGcClaim", false);
        f.put("hiddenNormalReturns", r.hiddenReturns); f.put("trackedNormalReturns", r.trackedReturns);
        f.put("restoreNormalReturns", r.restoreReturns); f.put("ageBefore", r.ageBefore);
        f.put("ageAfter", r.projectile == null ? -1 : r.projectile.tickCount);
        f.put("removed", r.projectile != null && r.projectile.isRemoved());
        f.put("permit", r.permit == null ? "ABSENT" : r.permit.state.name());
        f.put("claims", r.claims); f.put("hurts", r.hurts);
        return f;
    }
    private static void require(boolean condition, String code) { if (!condition) { throw new IllegalStateException("L1_TRACKING_" + code); } }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final ServerLevel level;
        final net.minecraft.server.network.ServerGamePacketListenerImpl connection;
        final Path output; final P11QualifiedSourceOwner.Body body; final int initialExperience, initialScore;
        ServerSlot.InstanceState instance; P11QualifiedSourceOwner.WorkReservation work;
        RuntimeProjectileContinuationPermit permit; P9StarterProjectile projectile;
        boolean accepted, requested, complete; int ticks, claims, hurts, ageBefore, hiddenReturns, trackedReturns, restoreReturns;
        String failure = "NONE";
        Run(MinecraftServer s,ServerPlayer a,ServerPlayer p,Path o,P11QualifiedSourceOwner.Body b) {
            server=s;actor=a;peer=p;output=o;body=b;level=a.serverLevel();connection=a.connection;
            initialExperience=a.totalExperience;initialScore=a.getScore();
        }
    }
}
