package com.yo1no.gramarye;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Excluded controlled transport close after one ORIGINAL uncancelled natural impact. */
public final class P11L1ImpactCustodyProbe {
    private static Run run;
    private P11L1ImpactCustodyProbe() {}
    static boolean selected() { return "l1-impact-close-custody".equals(System.getProperty("gramarye.p11.online.case", "")); }

    static void arm(Object runtime, MinecraftServer server, ServerPlayer actor, ServerPlayer peer,
            ServerSlot.InstanceState instance, LivingEntity target) {
        if (!selected()) { return; }
        require(run == null && server.isSameThread() && actor != peer && target != actor && target != peer,
                "ARM_EXACT_ACTUAL_ACTORS");
        require(runtime instanceof P11L1CapacityWorkProbe.RuntimeAccess, "EXISTING_READONLY_SLOT_ACCESS");
        var slot = (ServerSlot) ((P11L1CapacityWorkProbe.RuntimeAccess) runtime).p11$l1CapacitySlot(server);
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor); var body = owner.nativeRecipient(actor);
        require(slot != null && slot.instances.get(instance.id) == instance && instance.work != null
                && instance.hasP9AuthenticatedActorWitness(actor) && !instance.lease.pin.isClosed()
                && body != null && owner.canCopy(body), "ACTUAL_ACCEPTED_WORK_SOURCE_PIN");
        run = new Run(runtime, server, actor, peer, instance, target, slot, owner, body);
    }

    /** Called only after EventHooks.onProjectileImpact returned its original boolean. */
    public static void impactReturned(Object value, HitResult hit, boolean cancelled) {
        var r = run; if (r == null || !(value instanceof P9StarterProjectile projectile)
                || r.instance.activeProjectileContinuation == null
                || !projectile.hasContinuationPermitIdentity(r.instance.activeProjectileContinuation)) { return; }
        require(!cancelled && hit instanceof EntityHitResult entity && entity.getEntity() == r.target,
                "ONE_ORIGINAL_UNCANCELLED_SELECTED_ENTITY_IMPACT");
        current(r); require(++r.impacts == 1 && r.projectile == null && r.connection.isConnected()
                && r.listener.isAcceptingMessages() && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                && !r.actor.isRemoved() && !r.actor.hasDisconnected(), "LIVE_BEFORE_ORIGINAL_CLOSE");
        r.projectile = projectile; r.permit = r.instance.activeProjectileContinuation;
        require(r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && projectile.getOwner() == r.actor && r.slot.eventIndex.get(r.permit.heldChildEventId) == null
                && r.instance.reservedPending == 1 && r.slot.reservedPending == 1
                && r.instance.committedPending == 0 && r.slot.committedPending == 0,
                "REAL_OPEN_BEFORE_CLOSE");
        r.impactRuntimeTick = r.slot.runtimeTick; r.impactServerTick = r.server.getTickCount();
        r.deadline = r.permit.deadlineRuntimeTick; r.lifeBefore = r.instance.lifetimeEvents;
        r.positionAtImpact = projectile.position(); r.directionAtImpact = projectile.getDeltaMovement();
        r.hitPosition = hit.getLocation(); r.ageAtImpact = projectile.tickCount;
        require(root(r, "WORK") == 1 && root(r, "NATIVE_CREDIT") == 0 && root(r, "OPERATION") == 0,
                "NO_EARLY_WORLD_ROOTS");
        P11L1ServerHarness.impactCloseStarting(r.actor, projectile, r.target);
        r.closeCalls++;
        r.connection.disconnect(Component.literal("P11 L1 controlled original-impact connection close"));
        r.closeReturns++;
        require(!r.connection.isConnected() && r.connection.getPacketListener() == r.listener
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                && r.instance.logoutState == SkillRuntimeService.LogoutState.ONLINE
                && r.permit.qualification(r.server, projectile) == SkillRuntimeService.WorkQualification.NATIVE_CLEANUP_PENDING,
                "NATIVE_CLOSE_NOT_CLEANUP_OR_PERMISSION");
        unchangedOpen(r);
    }

    public static void claimReturned(Object runtime, MinecraftServer server, Object permitValue,
            Object projectileValue, Object candidateValue, Optional<?> result) {
        var r = run; if (r == null || projectileValue != r.projectile) { return; }
        current(r); require(runtime == r.runtime && server == r.server && permitValue == r.permit
                && candidateValue instanceof ProjectileHitCandidateV0, "EXACT_ORIGINAL_CLAIM_INPUT");
        var candidate = (ProjectileHitCandidateV0) candidateValue;
        if (r.candidate == null) {
            r.candidate = candidate; r.candidateCaptures++;
            require(candidate.projectileId().equals(r.projectile.getUUID()) && candidate.targetId().equals(r.target.getUUID())
                    && new Vec3(candidate.hitX(), candidate.hitY(), candidate.hitZ()).equals(r.hitPosition), "REAL_ORIGINAL_HIT_VALUE");
        } else { require(r.candidate == candidate, "SAME_ORIGINAL_CANDIDATE_NOT_REBUILT"); }
        if (result.isEmpty()) {
            require(++r.holds == 1 && r.queued == 0 && r.logoutEntries == 0 && r.logoutReturns == 0
                    && r.instance.logoutState == SkillRuntimeService.LogoutState.ONLINE
                    && r.slot.runtimeTick == r.impactRuntimeTick && r.projectile.hasObservedHit(r.permit, candidate)
                    && r.projectile.hasObservedTarget(r.permit, candidate, r.target), "ACTUAL_EMPTY_HOLD_BEFORE_NATIVE_CLEANUP");
            unchangedOpen(r); return;
        }
        require(result.orElseThrow() == RuntimePermitClaimDisposition.QUEUED && ++r.queued == 1
                && r.holds == 1 && r.logoutEntries == 1 && r.logoutReturns == 1 && r.logoutNormal
                && r.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                && r.projectile.getOwner() == r.actor && !r.projectile.hasObservedHit(r.permit, candidate),
                "ONLY_AFTER_PROVED_WHOLE_LOGOUT");
        r.claimRuntimeTick = r.slot.runtimeTick; r.claimServerTick = r.server.getTickCount();
        var child = r.slot.eventIndex.get(r.permit.heldChildEventId);
        require(child != null && child.eventId().equals(r.permit.heldChildEventId)
                && child.createdRuntimeTick() == r.claimRuntimeTick && child.scheduledRuntimeTick() == r.claimRuntimeTick
                && child.deadlineRuntimeTick() == r.deadline && child.skillReference().equals(r.instance.lease.reference)
                && r.claimRuntimeTick == r.impactRuntimeTick && r.claimServerTick == r.impactServerTick
                && r.permit.state == RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE
                && r.instance.reservedPending == 0 && r.slot.reservedPending == 0
                && r.instance.committedPending == 1 && r.slot.committedPending == 1
                && r.instance.lifetimeEvents == r.lifeBefore + 1 && r.hurts == 0,
                "ORIGINAL_SAME_TICK_CHILD_PUBLICATION_AND_CONSERVATION");
        r.childCreated = child.createdRuntimeTick(); r.childScheduled = child.scheduledRuntimeTick();
    }

    static void hitReturned(Object projectile, Object target) {
        var r = run; require(r != null && projectile == r.projectile && target == r.target
                && ++r.entityHitReturns == 1 && r.holds == 1 && r.queued == 0 && r.hurts == 0,
                "ORIGINAL_ENTITY_HANDLER_RETURN_HELD_NOT_CLAIMED");
        require(r.projectile.position().equals(r.positionAtImpact)
                && r.projectile.getDeltaMovement().equals(r.directionAtImpact), "NO_ENDPOINT_PASS_THROUGH");
    }

    public static void tickReturned(Object projectile) {
        var r = run; if (r == null || projectile != r.projectile || r.heldTickReturns != 0) { return; }
        require(++r.heldTickReturns == 1 && r.entityHitReturns == 1 && r.holds == 1 && r.queued == 0
                && r.logoutEntries == 0 && r.projectile.hasObservedHit(r.permit, r.candidate)
                && r.projectile.position().equals(r.positionAtImpact)
                && r.projectile.getDeltaMovement().equals(r.directionAtImpact)
                && r.projectile.tickCount == r.ageAtImpact, "WHOLE_NATIVE_TICK_NO_ENDPOINT_OR_CALLBACK_REPLAY");
        unchangedOpen(r);
    }

    public static boolean logoutEntering(ServerGamePacketListenerImpl listener) {
        var r = run; if (r == null || listener != r.listener) { return false; }
        current(r); require(++r.logoutEntries == 1 && r.closeReturns == 1 && r.entityHitReturns == 1
                && r.heldTickReturns == 1 && r.holds == 1 && r.queued == 0 && r.hurts == 0 && r.slot.runtimeTick == r.impactRuntimeTick,
                "ORIGINAL_NETWORK_CLEANUP_AFTER_HELD_ENTITY_RETURN");
        unchangedOpen(r); return true;
    }

    public static void logoutFinished(boolean selected, Throwable primary) {
        var r = run; if (!selected || r == null) { return; }
        if (primary != null) { r.logoutFailed = true; return; } // Never replace an original native primary.
        current(r); require(++r.logoutReturns == 1 && r.instance.logoutState == SkillRuntimeService.LogoutState.COMPLETE
                && r.instance.logoutScope == null && r.body.envelope != null && r.body.logoutAttempted
                && !r.body.logoutActive && r.actor.isRemoved() && r.actor.hasDisconnected()
                && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == null
                && r.permit.qualifiedActor(r.server, r.projectile) == r.actor,
                "REAL_COMPLETE_ENVELOPE_AND_EXACT_DETACHED_OWNER");
        r.logoutNormal = true; unchangedOpen(r);
        P11L1ServerHarness.impactLogoutCompleted(r.actor);
    }

    static boolean takeReconnectCue() {
        var r = run; if (r == null || !r.logoutNormal || r.reconnectCued) { return false; }
        r.reconnectCued = true; return true;
    }
    static void hurtEntering(LivingEntity target, DamageSource source) {
        var r = run; if (r == null || target != r.target) { return; }
        current(r); require(r.queued == 1 && r.logoutNormal && !r.logoutFailed && ++r.hurts == 1
                && source.getEntity() == r.actor && source.getDirectEntity() == r.projectile
                && root(r, "WORK") == 1 && root(r, "OPERATION") > 0 && root(r, "NATIVE_CREDIT") == 0,
                "ORIGINAL_HURT_AFTER_CUSTODY_TO_W_FOP");
    }
    static Map<String, Object> finish(ServerPlayer successor) {
        var r = run; require(r != null && r.impacts == 1 && r.closeCalls == 1 && r.closeReturns == 1
                && r.candidateCaptures == 1 && r.entityHitReturns == 1 && r.heldTickReturns == 1 && r.holds == 1 && r.queued == 1
                && r.hurts == 1 && r.logoutNormal && !r.logoutFailed && r.reconnectCued
                && successor != r.actor && successor.getUUID().equals(r.actor.getUUID())
                && r.instance.work == null && r.instance.lease.pin.isClosed() && r.instance.activeProjectileContinuation == null,
                "FINAL_ONCE_COUNTS_AND_RECONNECTED_TERMINAL");
        var f = new LinkedHashMap<String, Object>();
        f.put("status", "ACTUAL_CONTROLLED_IMPACT_CLOSE_CUSTODY_NAMED_ROW");
        f.put("originalImpactReturns", r.impacts); f.put("originalEntityHitReturns", r.entityHitReturns);
        f.put("originalHeldTickReturns", r.heldTickReturns);
        f.put("nativeConnectionCloseCalls", r.closeCalls); f.put("nativeConnectionCloseReturns", r.closeReturns);
        f.put("manualHandleDisconnectionCalls", 0); f.put("originalCandidateCaptures", r.candidateCaptures);
        f.put("actualEmptyHoldReturns", r.holds); f.put("actualQueuedReturns", r.queued); f.put("originalHurtEntries", r.hurts);
        f.put("wholeLogoutEntries", r.logoutEntries); f.put("wholeLogoutNormalReturns", r.logoutReturns);
        f.put("impactRuntimeTick", r.impactRuntimeTick); f.put("claimRuntimeTick", r.claimRuntimeTick);
        f.put("impactServerTick", r.impactServerTick); f.put("claimServerTick", r.claimServerTick);
        f.put("originalChildCreatedTick", r.childCreated); f.put("originalChildScheduledTick", r.childScheduled);
        f.put("unchangedDeadline", r.deadline); f.put("heldChildEventId", r.permit.heldChildEventId.value());
        f.put("projectileAgeAtImpact", r.ageAtImpact); f.put("sameWUntilHurt", true); f.put("exactOldActorNativeOrigin", true);
        f.put("sameCandidateIdentity", true); f.put("noEarlyWorldOrImpactReplay", true); f.put("sameTickPublicationObserved", true);
        f.put("crossPostRuntimeClaim", false); f.put("newAuthenticatedSuccessor", true);
        return Map.copyOf(f);
    }
    static void release() { run = null; }
    private static void current(Run r) {
        require(r.server.isSameThread() && r.slot.instances.get(r.instance.id) == r.instance
                && r.instance.work == r.work && !r.instance.lease.pin.isClosed()
                && r.owner.body(r.actor) == r.body && r.body.account.current == r.body
                && r.owner.canCopy(r.body) && r.actor.connection == r.listener && r.listener.player == r.actor
                && r.peer.connection.getConnection().isConnected()
                && r.server.getPlayerList().getPlayer(r.peer.getUUID()) == r.peer, "UNCHANGED_EXACT_CUSTODY_AND_PEER");
    }
    private static void unchangedOpen(Run r) {
        require(r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && r.instance.activeProjectileContinuation == r.permit
                && r.slot.activeProjectileContinuations.get(r.permit.permitId) == r.permit
                && r.slot.eventIndex.get(r.permit.heldChildEventId) == null
                && r.instance.reservedPending == 1 && r.slot.reservedPending == 1
                && r.instance.committedPending == 0 && r.slot.committedPending == 0
                && r.instance.lifetimeEvents == r.lifeBefore && r.permit.deadlineRuntimeTick == r.deadline
                && r.hurts == 0 && root(r, "WORK") == 1 && root(r, "NATIVE_CREDIT") == 0,
                "PENDING_DOES_NOT_CLAIM_OR_DROP_EXISTING_WORK");
    }
    private static long root(Run r, String kind) { return P11NativeStorageBoundary.diagnostics(r.server, r.actor.getUUID())
            .nativeResponsibilities().roots().stream().filter(v -> kind.equals(v.kind())).mapToLong(v -> v.count()).sum(); }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "L1_IMPACT_" + code); }
    private static final class Run {
        final Object runtime, work; final MinecraftServer server; final ServerPlayer actor, peer; final LivingEntity target;
        final Connection connection; final ServerGamePacketListenerImpl listener; final ServerSlot slot;
        final ServerSlot.InstanceState instance; final P11QualifiedSourceOwner owner; final P11QualifiedSourceOwner.Body body;
        P9StarterProjectile projectile; RuntimeProjectileContinuationPermit permit; ProjectileHitCandidateV0 candidate;
        Vec3 positionAtImpact, directionAtImpact, hitPosition;
        int impacts, closeCalls, closeReturns, candidateCaptures, holds, queued, entityHitReturns, heldTickReturns, logoutEntries, logoutReturns, hurts;
        int impactServerTick, claimServerTick, ageAtImpact, lifeBefore;
        long impactRuntimeTick, claimRuntimeTick, deadline, childCreated, childScheduled;
        boolean logoutNormal, logoutFailed, reconnectCued;
        Run(Object runtime, MinecraftServer server, ServerPlayer actor, ServerPlayer peer, ServerSlot.InstanceState instance,
                LivingEntity target, ServerSlot slot, P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body) {
            this.runtime = runtime; this.server = server; this.actor = actor; this.peer = peer; this.instance = instance;
            this.target = target; this.slot = slot; this.owner = owner; this.body = body; work = instance.work;
            listener = actor.connection; connection = listener.getConnection();
        }
    }
}
