package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Closed native callback experiments. No attachment, receipt, permit or queue is written here. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11CooldownFaultProbe {
    public interface ProjectileCallbacks {
        void p11$cooldownEntityHit(EntityHitResult hit);
        void p11$cooldownBlockHit(BlockHitResult hit);
    }
    /** Excluded scalar view; never invokes retire, a writer, or a source mutation. */
    public interface SourceFailures { long p11$cooldownSourceFailures(); }
    private enum Mode { PREPARED_REENTRY, ADD_FALSE, ADD_REMOVE, BEFORE_ARM_THROW, AFTER_ARM_THROW, UNARMED_STOP }
    private static Run run;
    private P11CooldownFaultProbe() { }

    public static boolean selected() {
        return List.of("cooldown-prepared-reentry", "cooldown-add-false", "cooldown-add-remove",
                "cooldown-before-arm-throw", "cooldown-after-arm-throw", "cooldown-unarmed-stop")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    public static boolean expectedNativeFault() {
        return List.of("cooldown-add-remove", "cooldown-before-arm-throw", "cooldown-after-arm-throw")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    public static boolean expectedServerStop() {
        return expectedNativeFault() || "cooldown-unarmed-stop".equals(System.getProperty("gramarye.p11.online.case", ""));
    }
    static boolean originalNativeFaultObserved() {
        return run != null && expectedNativeFault() && run.primary != null
                && run.postThrows == 1 && run.nativeCatches == 1 && run.failure == null;
    }
    static boolean originalUnarmedStopRequested() {
        return run != null && run.mode == Mode.UNARMED_STOP && run.haltCalls == 1 && run.haltReturned;
    }
    static void arm(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference ref,
            ServerSlot slot, P11FoundationService foundation, Path output) {
        require(selected() && run == null && server.isSameThread() && actor != peer
                && actor.connection.getConnection().isConnected() && peer.connection.getConnection().isConnected(), "ARM_CURRENT");
        Mode mode = switch (System.getProperty("gramarye.p11.online.case")) {
            case "cooldown-prepared-reentry" -> Mode.PREPARED_REENTRY;
            case "cooldown-add-false" -> Mode.ADD_FALSE;
            case "cooldown-add-remove" -> Mode.ADD_REMOVE;
            case "cooldown-before-arm-throw" -> Mode.BEFORE_ARM_THROW;
            case "cooldown-after-arm-throw" -> Mode.AFTER_ARM_THROW;
            case "cooldown-unarmed-stop" -> Mode.UNARMED_STOP;
            default -> throw new IllegalStateException("CLOSED_COOLDOWN_FAULT_CASE");
        };
        run = new Run(server, actor, peer, ref, slot, foundation, output, mode);
    }
    static void admitted(MinecraftServer server, ServerPlayer actor, SkillReference ref, Object instance, Object result) {
        var r = exact();
        require(server == r.server && actor == r.actor && ref.equals(r.reference) && r.instance == null
                && instance instanceof ServerSlot.InstanceState && result instanceof RuntimeAdmissionResult.AcceptedMemoryOnly,
                "ONE_REAL_ACCEPTANCE");
        r.instance = (ServerSlot.InstanceState) instance;
        var accepted = (RuntimeAdmissionResult.AcceptedMemoryOnly) result;
        r.root = r.slot.queue.stream().filter(event -> event.eventId().equals(accepted.eventToken().eventId())).findFirst().orElseThrow();
        require(r.instance.id.equals(accepted.eventToken().skillInstanceId()) && r.slot.instances.get(r.instance.id) == r.instance
                && r.instance.work != null && !r.instance.lease.pin.isClosed()
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING,
                "ACTUAL_PUBLISHED_PENDING");
        r.pending = entry(r);
        require(r.pending != null && r.pending.kind == 1 && r.pending.duration == 600
                && r.pending.attemptId.equals(r.instance.cooldownReceipt.attemptId())
                && r.pending.releaseNotAfter == r.pending.acceptedAt + r.root.deadlineRuntimeTick() - r.root.createdRuntimeTick(),
                "PENDING_EXACT_ROOT_BOUND");
        r.acceptances++;
        if (r.mode == Mode.UNARMED_STOP) {
            require(r.instance.activeProjectileContinuation == null && !r.instance.inFlight
                    && r.slot.runtimeTick == r.root.createdRuntimeTick()
                    && r.slot.eventIndex.get(r.root.eventId()) == r.root
                    && r.root.scheduledRuntimeTick() == Math.addExact(r.root.createdRuntimeTick(), 1),
                    "UNEXECUTED_ACTUAL_PENDING_ROOT");
            r.haltCalls++; r.server.halt(false); r.haltReturned = true;
        }
    }

    /** Cancellation is the real NeoForge event result, never an overwritten native add result. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void joining(EntityJoinLevelEvent event) {
        var r = run;
        if (r == null || r.mode != Mode.ADD_FALSE || !(event.getEntity() instanceof P9StarterProjectile p)
                || r.instance == null || r.instance.activeProjectileContinuation == null
                || !p.hasContinuationPermitIdentity(r.instance.activeProjectileContinuation)) { return; }
        require(r.server.isSameThread() && event.getLevel() == r.actor.serverLevel() && !event.isCanceled()
                && r.joinCancelled == 0 && !p.isAddedToLevel() && !p.isRemoved(), "ORIGINAL_JOIN_SELECTION");
        capturePrepared(r, p); event.setCanceled(true); r.joinCancelled++;
    }

    /** Same synchronous original add call, after its real onAddedToLevel callback returns. */
    public static void afterAdded(ServerLevel level, Entity entity) {
        var r = run;
        if (r == null || !(entity instanceof P9StarterProjectile p) || r.instance == null
                || r.instance.activeProjectileContinuation == null
                || !p.hasContinuationPermitIdentity(r.instance.activeProjectileContinuation)) { return; }
        require(level == r.actor.serverLevel() && r.server.isSameThread() && r.onAdded == 0
                && p.isAddedToLevel() && !p.isRemoved() && level.getEntity(p.getUUID()) == p,
                "ACTUAL_ADDED_CALLBACK");
        capturePrepared(r, p); r.onAdded++;
        if (r.mode == Mode.BEFORE_ARM_THROW) {
            require(r.primary == null, "ONE_OWNED_PRE_ARM_PRIMARY");
            r.primary = new OwnedFault(); throw r.primary;
        }
        if (r.mode == Mode.ADD_REMOVE) {
            p.discard(); r.discardReturns++;
            require(p.isRemoved() && level.getEntity(p.getUUID()) == null, "ORIGINAL_CALLBACK_DISCARD");
            return;
        }
        if (r.mode == Mode.AFTER_ARM_THROW) { return; }
        require(r.mode == Mode.PREPARED_REENTRY, "UNEXPECTED_ADDED_CALLBACK");
        var position = p.position(); var motion = p.getDeltaMovement(); int age = p.tickCount;
        boolean gravity = p.isNoGravity(); int queued = r.slot.committedPending, reserved = r.slot.reservedPending;
        int events = r.slot.eventIndex.size(); float health = r.peer.getHealth();
        p.tick(); r.reentrantTicks++;
        ((ProjectileCallbacks) (Object) p).p11$cooldownEntityHit(new EntityHitResult(r.peer)); r.entityHits++;
        ((ProjectileCallbacks) (Object) p).p11$cooldownBlockHit(new BlockHitResult(position, Direction.UP,
                BlockPos.containing(position), false)); r.blockHits++;
        var candidate = new ProjectileHitCandidateV0(p.getUUID(), r.peer.getUUID(), level.dimension().location(),
                r.peer.getX(), r.peer.getY(), r.peer.getZ(), 0, 0, 32767);
        var claim = r.permit.claimLoadedEntityHit(r.server, candidate); r.directClaims++;
        require(claim == RuntimePermitClaimDisposition.REJECTED && r.permit.isPreparedSpawn()
                && r.permit.state == RuntimeProjectileContinuationPermit.State.RESERVED
                && r.instance.activeProjectileContinuation == r.permit && r.instance.work != null
                && !r.instance.lease.pin.isClosed() && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                && p.position().equals(position) && p.getDeltaMovement().equals(motion) && p.tickCount == age
                && p.isNoGravity() == gravity && !p.isRemoved() && r.peer.getHealth() == health
                && r.slot.committedPending == queued && r.slot.reservedPending == reserved && r.slot.eventIndex.size() == events
                && r.queuedClaims == 0 && entry(r) == r.pending, "PREPARED_REENTRY_INERT_AND_CUSTODY_UNCHANGED");
        r.inertVerified = true;
    }
    private static void capturePrepared(Run r, P9StarterProjectile p) {
        require(r.instance != null && (r.projectile == null || r.projectile == p)
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                && r.instance.activeProjectileContinuation != null
                && r.instance.activeProjectileContinuation.isPreparedSpawn(), "REAL_PREPARED_BEFORE_ARM");
        r.projectile = p; r.permit = r.instance.activeProjectileContinuation;
    }
    public static void addReturned(Entity entity, boolean result, Throwable primary) {
        var r = run; if (r == null || entity != r.projectile) { return; }
        observe(r, () -> {
            if (r.mode == Mode.BEFORE_ARM_THROW) {
                require(primary == r.primary && primary != null && r.addReturns == 0 && r.addThrows == 0,
                        "ORIGINAL_ADD_ESCAPES_OWNED_PRE_ARM_PRIMARY");
                r.addThrows++; return;
            }
            require(primary == null && r.addReturns == 0, "ORIGINAL_ADD_NORMAL_RETURN");
            r.addReturns++; r.addTrue = result;
            require(result == (r.mode != Mode.ADD_FALSE), "ORIGINAL_ADD_RESULT_NOT_REPLACED");
        });
    }
    public static void transferReturned(Object projectile, Object result, Throwable primary) {
        var r = run; if (r == null || projectile != r.projectile) { return; }
        observe(r, () -> {
            if (r.mode == Mode.AFTER_ARM_THROW) {
                require(primary == r.primary && primary != null && r.armReturns == 1 && r.active != null
                        && r.transferCalls == 0 && r.transferThrows == 0,
                        "ORIGINAL_TRANSFER_ESCAPES_OWNED_POST_ARM_PRIMARY");
                r.transferThrows++; return;
            }
            require(primary == null && r.addReturns == 1 && r.addTrue && r.transferCalls == 0, "TRANSFER_AFTER_TRUE_ADD");
            r.transferCalls++;
            if (r.mode == Mode.ADD_REMOVE) {
                require(result == RuntimePermitTransferDisposition.REJECTED, "REMOVED_NATIVE_TRANSFER_REJECTED");
                r.transferRejected = true;
            } else {
                require(result == RuntimePermitTransferDisposition.TRANSFERRED && r.inertVerified
                        && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                        && r.projectile.getOwner() == r.actor && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM,
                        "ORIGINAL_TRANSFER_REAL_ARM");
                r.active = entry(r);
                require(r.active != null && r.active.kind == 0 && r.active.attemptId.equals(r.pending.attemptId)
                        && r.active.releasedAt == r.instance.cooldownReceipt.releasedAt()
                        && r.active.expiresAt == r.active.releasedAt + 600
                        && r.slot.runtimeTick < r.root.deadlineRuntimeTick(), "ACTUAL_ACTIVE_SAME_ATTEMPT");
            }
        });
    }
    /** Outside the indivisible OPEN/ARM stores: the real completeArm already returned normally. */
    public static void armReturned(Object arm) {
        var r = run; if (r == null || r.mode != Mode.AFTER_ARM_THROW) { return; }
        require(r.server.isSameThread() && arm instanceof P11CastCooldownService.ArmPreparation
                && r.addReturns == 1 && r.addTrue && r.primary == null && r.armReturns == 0
                && r.permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && r.projectile.getOwner() == r.actor && !r.projectile.isRemoved(), "EXACT_ORIGINAL_ARM_RETURN");
        r.active = entry(r);
        require(r.active != null && r.active.kind == 0 && r.active.attemptId.equals(r.pending.attemptId)
                && r.active.releasedAt == ((P11CastCooldownService.ArmPreparation) arm).releasedAt()
                && r.active.releasedAt == r.instance.cooldownReceipt.releasedAt()
                && r.active.expiresAt == r.active.releasedAt + 600, "REAL_ARM_BEFORE_OWNED_FAILURE");
        r.armReturns++; r.primary = new OwnedFault(); throw r.primary;
    }
    static void claimed(Object result) { if (run != null && result == RuntimePermitClaimDisposition.QUEUED) { run.queuedClaims++; } }
    /** The exact abnormal Fop end, before its OPERATION root is released. */
    public static void operationFailure(Object source, Object material,
            P11NativeOperationBoundary.OperationScope scope, boolean normal, boolean after, boolean returned) {
        var r = run; if (r == null || !expectedNativeFault()) { return; }
        observe(r, () -> {
            if (!after) { r.operationFailureCalls++; }
            require(r.server.isSameThread() && scope != null && !normal && faultCut(r)
                    && r.instance != null && r.instance.inFlight && r.instance.work != null
                    && r.slot.dispatching && r.slot.currentEvent == r.root
                    && r.slot.instances.get(r.instance.id) == r.instance
                    && source == r.foundation.sourceOwner(r.server)
                    && source instanceof P11QualifiedSourceOwner
                    && material instanceof P11QualifiedSourceOwner.Body,
                    "EXACT_ABNORMAL_OPERATION_IN_FLIGHT");
            var owner = (P11QualifiedSourceOwner) source;
            var body = (P11QualifiedSourceOwner.Body) material;
            require(owner.owns(r.server) && body.actor == r.actor && owner.body(r.actor) == body
                    && body.account.current == body && owner.canCopy(body)
                    && r.server.getPlayerList().getPlayer(r.actor.getUUID()) == r.actor
                    && r.actor.connection.player == r.actor
                    && r.actor.connection.getConnection().getPacketListener() == r.actor.connection
                    && body.account.nativeCounts[P11ControlBudgets.Root.OPERATION.ordinal()] == 1,
                    "EXACT_OPERATION_SOURCE_ACTOR_AND_ROOT");
            long failures = ((SourceFailures) source).p11$cooldownSourceFailures();
            if (!after) {
                r.sourceFailuresBeforeOperation = failures;
                require(r.operationFailureCalls == 1 && !returned && failures == 0,
                        "FIRST_NATIVE_OPERATION_FAILURE_FROM_ZERO");
            } else {
                r.sourceFailuresAfterOperation = failures;
                r.operationFailureReturned = returned;
                require(r.operationFailureCalls == 1 && returned
                        && r.sourceFailuresBeforeOperation == 0 && failures == 1,
                        "ORIGINAL_NATIVE_OPERATION_FAILURE_NORMAL_ZERO_TO_ONE");
                r.operationFailureDelta = failures - r.sourceFailuresBeforeOperation;
            }
        });
    }
    private static boolean faultCut(Run r) {
        return r.mode == Mode.ADD_REMOVE ? r.transferRejected
                : r.mode == Mode.BEFORE_ARM_THROW ? r.addThrows == 1 && r.transferCalls == 0
                : r.mode == Mode.AFTER_ARM_THROW && r.transferThrows == 1 && r.armReturns == 1;
    }
    private static void requireOperationFailure(Run r) {
        require(r.operationFailureCalls == 1 && r.sourceFailuresBeforeOperation == 0
                && r.sourceFailuresAfterOperation == 1 && r.operationFailureDelta == 1
                && r.operationFailureReturned, "EXACT_ORIGINAL_OPERATION_FAILURE_OBSERVED");
    }
    public static void runtimeFault(Object slot, RuntimeException primary, boolean after, RuntimeException returned) {
        var r = run; if (r == null || !expectedNativeFault()) { return; }
        observe(r, () -> {
            require(slot == r.slot && faultCut(r) && primary != null, "ORIGINAL_P5_FAULT_AFTER_EXACT_BOUNDARY");
            requireOperationFailure(r);
            if (r.primary == null) { r.primary = primary; }
            require(primary == r.primary, "SAME_ORIGINAL_PRIMARY");
            if (after) { require(returned == primary && r.slot.state == ServerSlot.State.FAULTED, "ORIGINAL_FAULT_RETURN"); r.faultReturns++; }
        });
    }
    public static void postThrew(MinecraftServer server, Throwable primary) {
        var r = run; if (r == null || !expectedNativeFault() || server != r.server) { return; }
        observe(r, () -> {
            require(primary == r.primary && r.faultReturns == 1 && terminal(r), "ORIGINAL_POST_ESCAPES_SAME_PRIMARY");
            r.postThrows++;
            if (r.mode == Mode.AFTER_ARM_THROW) { requireArmRetained(r); r.armRetainedBeforeStop = true; }
            else { requireNoRelease(r); }
            write(r, "cooldown-fault-local.json", switch (r.mode) {
                case ADD_REMOVE -> "ORIGINAL_ADD_TRUE_TRANSFER_REJECTED_NO_RELEASE";
                case BEFORE_ARM_THROW -> "ORIGINAL_ADD_THROW_NO_RELEASE";
                case AFTER_ARM_THROW -> "ORIGINAL_POST_ARM_THROW_KNOWN_ARM_RETAINED";
                default -> throw new IllegalStateException("CLOSED_FAULT_MODE");
            });
        });
    }
    public static void nativeOuter(MinecraftServer server, boolean cleanup, Throwable primary) {
        var r = run; if (r == null || !expectedNativeFault() || server != r.server) { return; }
        observe(r, () -> {
            require(!cleanup && r.postThrows == 1 && primary == r.primary && r.nativeCatches == 0,
                    "ORIGINAL_NATIVE_PRIMARY_RECEIVER"); r.nativeCatches++;
        });
    }
    static boolean tick() throws IOException {
        var r = exact(); check(r);
        if (expectedServerStop() || r.instance == null || !terminal(r)) { return false; }
        require(r.acceptances == 1 && r.addReturns == 1 && r.queuedClaims == 0, "ONE_NATIVE_ATTEMPT_TERMINAL");
        if (r.mode == Mode.ADD_FALSE) {
            require(r.joinCancelled == 1 && !r.addTrue && r.onAdded == 0 && r.transferCalls == 0, "ORIGINAL_FALSE_WITHOUT_TRANSFER");
            requireNoRelease(r);
        } else {
            require(r.inertVerified && r.transferCalls == 1 && r.active != null
                    && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                    && sameActive(entry(r), r.active) && r.server.overworld().getGameTime() < r.active.expiresAt,
                    "TERMINAL_MISS_DOES_NOT_REFUND_ARM");
        }
        if (!r.resultWritten) { write(r, "cooldown-fault-result.json", "NATIVE_CALLBACK_COOLDOWN_BOUNDARY_OBSERVED"); r.resultWritten = true; }
        return true;
    }
    static void stopped(MinecraftServer server) throws IOException {
        var r = exact(); check(r); require(server == r.server, "STOP_EXACT_SERVER");
        if (expectedNativeFault()) {
            require(r.postThrows == 1 && r.nativeCatches == 1 && r.primary != null && terminal(r), "NATIVE_FAULT_STOP_NOT_HARNESS_HALT");
            requireOperationFailure(r);
            if (r.mode == Mode.AFTER_ARM_THROW) {
                require(r.armRetainedBeforeStop && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM,
                        "KNOWN_ARM_SURVIVES_NATIVE_FAULT_STOP");
            } else { requireNoRelease(r); }
            write(r, "cooldown-fault-result.json", r.mode == Mode.AFTER_ARM_THROW
                    ? "ORIGINAL_NATIVE_FAULT_STOP_AFTER_KNOWN_ARM" : "ORIGINAL_NATIVE_FAULT_STOP_AFTER_NO_RELEASE"); r.resultWritten = true;
        } else if (r.mode == Mode.UNARMED_STOP) {
            require(r.haltCalls == 1 && r.haltReturned && r.addReturns == 0 && r.onAdded == 0 && r.projectile == null
                    && r.instance.work == null && r.instance.lease.pin.isClosed()
                    && r.instance.activeProjectileContinuation == null && r.queuedClaims == 0,
                    "ORIGINAL_UNARMED_STOP_TERMINAL");
            requireNoRelease(r);
            write(r, "cooldown-fault-result.json", "ORIGINAL_UNARMED_STOP_WITHOUT_RELEASE"); r.resultWritten = true;
        } else { require(r.resultWritten, "NORMAL_RESULT_BEFORE_STOP"); }
    }
    static boolean requiresActiveHud() { return !selected() || !"cooldown-add-false".equals(System.getProperty("gramarye.p11.online.case")); }
    private static void requireNoRelease(Run r) {
        require(r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.NO_RELEASE
                && !r.slot.instances.containsKey(r.instance.id)
                && (r.mode == Mode.UNARMED_STOP ? r.permit == null && r.slot.activeProjectileContinuations.isEmpty()
                    : r.permit != null && !r.slot.activeProjectileContinuations.containsKey(r.permit.permitId)
                        && r.permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT),
                "IRREVERSIBLE_NO_RELEASE_NOT_ADD_INFERENCE");
        // Fault stop may already have retired the whole source; material is checked before that boundary.
        if (r.foundation.sourceOwner(r.server) != null) { require(entry(r) == null, "SOLE_CELL_CLEARED_NO_RELEASE"); }
    }
    private static void requireArmRetained(Run r) {
        require(r.active != null && r.instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                && sameActive(entry(r), r.active) && r.server.overworld().getGameTime() < r.active.expiresAt,
                "KNOWN_ARM_NOT_REFUNDED_BY_RUNTIME_FAULT");
    }
    private static boolean terminal(Run r) {
        return r.instance.work == null && r.instance.lease.pin.isClosed() && r.instance.activeProjectileContinuation == null
                && (r.mode == Mode.UNARMED_STOP ? r.projectile == null : r.projectile != null && r.projectile.isRemoved());
    }
    private static P11CastCooldownData.Entry entry(Run r) {
        var owner = r.foundation.sourceOwner(r.server); var body = owner == null ? null : owner.body(r.actor);
        require(body != null && owner.canCopy(body) && body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED,
                "ACTUAL_CURRENT_SOURCE");
        return body.cooldown.data.entries.get(r.reference.skillId().value());
    }
    private static boolean sameActive(P11CastCooldownData.Entry a, P11CastCooldownData.Entry b) {
        return a != null && a.kind == 0 && a.attemptId.equals(b.attemptId) && a.acceptedAt == b.acceptedAt
                && a.releaseNotAfter == b.releaseNotAfter && a.releasedAt == b.releasedAt && a.expiresAt == b.expiresAt;
    }
    private static void write(Run r, String leaf, String status) throws IOException {
        var f = new LinkedHashMap<String, Object>();
        f.put("status", status); f.put("case", P11C4aEvidence.property("case")); f.put("accepted", r.acceptances);
        f.put("nativeJoinCancels", r.joinCancelled); f.put("onAddedReturns", r.onAdded); f.put("nativeAddReturns", r.addReturns);
        f.put("nativeAddTrue", r.addTrue); f.put("originalDiscardReturns", r.discardReturns); f.put("transferCalls", r.transferCalls);
        f.put("originalTransferRejected", r.transferRejected); f.put("preparedTickCalls", r.reentrantTicks);
        f.put("preparedEntityHitCalls", r.entityHits); f.put("preparedBlockHitCalls", r.blockHits); f.put("preparedDirectClaimCalls", r.directClaims);
        f.put("preparedInertnessVerified", r.inertVerified); f.put("queuedHitChildren", r.queuedClaims);
        f.put("receiptFact", r.instance.cooldownReceipt.fact().name()); f.put("workPinEntityTerminal", terminal(r));
        f.put("p5FaultReturns", r.faultReturns); f.put("originalPostThrows", r.postThrows); f.put("originalNativeCatches", r.nativeCatches);
        f.put("originalAddThrows", r.addThrows); f.put("originalTransferThrows", r.transferThrows);
        f.put("originalArmNormalReturnsBeforeFault", r.armReturns); f.put("knownArmRetainedBeforeStop", r.armRetainedBeforeStop);
        f.put("originalHaltCalls", r.haltCalls); f.put("originalHaltReturned", r.haltReturned);
        f.put("originalNativeOperationFailureCalls", r.operationFailureCalls);
        f.put("sourceFailuresBeforeOperation", r.sourceFailuresBeforeOperation);
        f.put("sourceFailuresAfterOperation", r.sourceFailuresAfterOperation);
        f.put("observedNativeOperationFailureDelta", r.operationFailureDelta);
        f.put("originalNativeOperationFailureReturned", r.operationFailureReturned);
        f.put("controlledCallbackNotNaturalCollision", true); f.put("nativeAddSideEffectsWereNotRolledBack", true);
        f.put("observerFailure", r.failure == null ? "NONE" : r.failure);
        if (r.active != null) { f.put("releasedAt", r.active.releasedAt); f.put("expiresAt", r.active.expiresAt); }
        P11C4aEvidence.write(r.output, leaf, f);
    }
    private static void observe(Run r, Observation operation) {
        try { operation.run(); } catch (IOException | RuntimeException | Error failure) {
            if (r.failure == null) { r.failure = "FIXED_FAULT_OBSERVER_FAILURE"; }
        }
    }
    private static void check(Run r) { require(r.failure == null, "OBSERVER_FAILED"); }
    private static Run exact() { require(run != null, "ARMED"); return run; }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_FAULT_" + code); }
    @FunctionalInterface private interface Observation { void run() throws IOException; }
    private static final class OwnedFault extends RuntimeException {
        private OwnedFault() { super("COOLDOWN_OWNED_NATIVE_BOUNDARY_FAULT"); }
    }
    private static final class Run {
        final MinecraftServer server; final ServerPlayer actor, peer; final SkillReference reference; final ServerSlot slot;
        final P11FoundationService foundation; final Path output; final Mode mode;
        ServerSlot.InstanceState instance; RuntimeEvent root; P9StarterProjectile projectile;
        RuntimeProjectileContinuationPermit permit; P11CastCooldownData.Entry pending, active; RuntimeException primary;
        int acceptances, joinCancelled, onAdded, addReturns, discardReturns, transferCalls, reentrantTicks, entityHits, blockHits,
                directClaims, queuedClaims, faultReturns, postThrows, nativeCatches, addThrows, transferThrows, armReturns, haltCalls;
        int operationFailureCalls;
        long sourceFailuresBeforeOperation = -1, sourceFailuresAfterOperation = -1, operationFailureDelta = -1;
        boolean operationFailureReturned;
        boolean addTrue, transferRejected, inertVerified, resultWritten, armRetainedBeforeStop, haltReturned; String failure;
        Run(MinecraftServer server, ServerPlayer actor, ServerPlayer peer, SkillReference reference, ServerSlot slot,
                P11FoundationService foundation, Path output, Mode mode) {
            this.server = server; this.actor = actor; this.peer = peer; this.reference = reference; this.slot = slot;
            this.foundation = foundation; this.output = output; this.mode = mode;
        }
    }
}
