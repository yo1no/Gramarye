package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.EventId;
import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.capability.TriggerEventKind;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.store.ControlledSkillPin;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.definition.validation.ValidatedSkillDefinition;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Sole synchronous owner of transient P5 server runtime work. */
final class SkillRuntimeService {
    static final Comparator<RuntimeEvent> EVENT_ORDER = SkillRuntimeService::compareEvents;

    private final SkillDefinitionStoreService storeService;
    private final SkillSubmissionPolicyProvider policyProvider;
    private final P5RuntimeProjector projector;
    private final RuntimeReferenceResolver referenceResolver;
    private final RuntimeExecutionPort executionPort;
    private final AtomicBoolean p9ReloadCloseRequested = new AtomicBoolean();
    private final IdentityHashMap<MinecraftServer, ServerSlot> slots = new IdentityHashMap<>(1);
    private long serverTokenHighWater;

    SkillRuntimeService(
            SkillDefinitionStoreService storeService,
            SkillSubmissionPolicyProvider policyProvider,
            P5RuntimeProjector projector,
            RuntimeReferenceResolver referenceResolver,
            RuntimeExecutionPort executionPort) {
        this.storeService = Objects.requireNonNull(storeService, "storeService");
        this.policyProvider = Objects.requireNonNull(policyProvider, "policyProvider");
        this.projector = Objects.requireNonNull(projector, "projector");
        this.referenceResolver = Objects.requireNonNull(referenceResolver, "referenceResolver");
        this.executionPort = Objects.requireNonNull(executionPort, "executionPort");
    }

    static SkillRuntimeService create(
            IEventBus gameBus,
            SkillDefinitionStoreService storeService,
            SkillSubmissionPolicyProvider policyProvider,
            ProfileAvailabilityView profiles,
            P6RuntimeExecutionCapability capability,
            P8ServerPresentationService presentationService) {
        Objects.requireNonNull(gameBus, "gameBus");
        Objects.requireNonNull(profiles, "profiles");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(presentationService, "presentationService");
        var service = new SkillRuntimeService(
                storeService,
                policyProvider,
                new P5RuntimeProjector(profiles),
                new P5LoadedReferenceResolver(),
                new P6RuntimeExecutionPortAdapter(capability, presentationService));
        gameBus.addListener(EventPriority.LOWEST, service::handleRuntimePost);
        gameBus.addListener(service::handleRuntimeStopping);
        gameBus.addListener(service::handleRuntimeStopped);
        return service;
    }

    ServerSlot newRunningSlot(RuntimeServerToken token, P5RuntimeLimits limits) {
        return new ServerSlot(token, limits);
    }

    Optional<RuntimeAdmissionResult> persistenceFailure(RuntimeScheduleSpec schedule) {
        Objects.requireNonNull(schedule, "schedule");
        return schedule.persistence() == RuntimeSchedulePersistence.PERSISTENT
                ? Optional.of(new RuntimeAdmissionResult.PersistentScheduleUnsupported())
                : Optional.empty();
    }

    void handleRuntimeStarted(ServerStartedEvent event, P5RuntimeLimits limits) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(limits, "limits");
        var server = event.getServer();
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        if (!server.isRunning() || server.isStopped()) {
            throw kernel(RuntimeKernelException.Code.STOPPED_SERVER_INSTALL);
        }
        if (slots.containsKey(server)) {
            throw kernel(RuntimeKernelException.Code.DUPLICATE_SERVER_INSTALL);
        }
        if (!slots.isEmpty()) {
            throw kernel(RuntimeKernelException.Code.SECOND_ACTIVE_SERVER);
        }
        var nextToken = checkedPositiveSuccessor(serverTokenHighWater);
        if (nextToken.isEmpty()) {
            throw kernel(RuntimeKernelException.Code.SERVER_SLOT_TOKEN_EXHAUSTED);
        }
        var token = new RuntimeServerToken(nextToken.orElseThrow());
        var slot = newRunningSlot(token, limits);
        if (!slot.queue.isEmpty()
                || !slot.instances.isEmpty()
                || !slot.activeProjectileContinuations.isEmpty()) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        slots.put(server, slot);
        serverTokenHighWater = token.value();
        p9ReloadCloseRequested.set(false);
    }

    RuntimeAdmissionResult admitAuthenticatedPlayerCast(
            MinecraftServer server,
            ServerPlayer actor,
            SkillReference exactReference,
            CastGeometryExecutionDataV0 geometry) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(exactReference, "exactReference");
        Objects.requireNonNull(geometry, "geometry");
        if (!server.isSameThread()) {
            return new RuntimeAdmissionResult.WrongThread();
        }
        var slot = slots.get(server);
        if (slot == null) {
            return new RuntimeAdmissionResult.ServerNotRunning();
        }
        if (p9ReloadCloseRequested.get()
                || !server.isRunning()
                || server.isStopped()) {
            return new RuntimeAdmissionResult.ServerStopping();
        }
        var actorId = actor.getUUID();
        var actorLevel = actor.serverLevel();
        if (actor.getServer() != server || actorLevel.getServer() != server) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    RuntimeReferenceFailureReason.WRONG_SERVER);
        }
        if (server.getPlayerList().getPlayer(actorId) != actor
                || actor.isRemoved()
                || !actor.isAlive()
                || actor.connection == null
                || !actor.connection.isAcceptingMessages()) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    RuntimeReferenceFailureReason.MISSING);
        }
        if (!geometry.dimension().equals(actorLevel.dimension().location())) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    RuntimeReferenceFailureReason.WRONG_DIMENSION);
        }
        var playerId = new RuntimePlayerId(actorId);
        return admitRootWithP9Actor(
                server,
                new RuntimeRootEventSpec(
                        exactReference,
                        0,
                        new RuntimeScheduleSpec(
                                0,
                                100,
                                RuntimeSchedulePersistence.MEMORY_ONLY),
                        new PlayerRuntimeBudgetAttribution(slot.token, playerId),
                        new PlayerOrigin(slot.token, actorLevel.dimension(), playerId),
                        Optional.empty(),
                        new RootTriggerCause(new TriggerEventKind(
                                ResourceLocation.fromNamespaceAndPath(
                                        Gramarye.MOD_ID, "active_cast"))),
                        geometry),
                actor);
    }

    void requestP9ReloadInvalidation() {
        p9ReloadCloseRequested.set(true);
    }

    void completeP9Reload(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        var slot = slots.get(server);
        if (slot == null) {
            return;
        }
        invalidateP9WorkPreservingPrimary(
                server, slot, ProjectileClosureReason.RELOAD_INVALIDATED);
        p9ReloadCloseRequested.set(false);
    }

    void armP9TerminalDiagnosticFailureForTesting(
            MinecraftServer server,
            SkillInstanceId skillInstanceId,
            boolean throwError) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        var slot = slots.get(server);
        var instance = slot == null ? null : slot.instances.get(skillInstanceId);
        if (instance == null || instance.p9Diagnostic == null) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.p9Diagnostic.armTerminalFailureForTesting(throwError);
    }

    ServerSlot.P9TerminalDiagnostic p9TerminalDiagnosticForTesting(
            MinecraftServer server, SkillInstanceId skillInstanceId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        var slot = slots.get(server);
        if (slot == null) {
            return null;
        }
        ServerSlot.P9TerminalDiagnostic matched = null;
        for (var diagnostic : slot.p9TerminalRing) {
            if (diagnostic != null
                    && diagnostic.skillInstanceId().equals(skillInstanceId)) {
                if (matched != null) {
                    throw kernel(
                            RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                matched = diagnostic;
            }
        }
        return matched;
    }

    ServerSlot.P9ActiveDiagnostic p9ErrorDeferredDiagnosticForTesting(
            MinecraftServer server, SkillInstanceId skillInstanceId) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        var slot = slots.get(server);
        if (slot == null || slot.state != ServerSlot.State.FAULTED) {
            return null;
        }
        var instance = slot.instances.get(skillInstanceId);
        var diagnostic = instance == null ? null : instance.p9Diagnostic;
        return diagnostic != null
                        && diagnostic.skillInstanceId.equals(skillInstanceId)
                        && diagnostic.terminalReason
                                == ProjectileClosureReason.RUNTIME_FAULT
                        && diagnostic.cleanupDisposition
                                == P9RuntimeCleanupDisposition.ERROR_DEFERRED
                        && !diagnostic.terminalPublished
                ? diagnostic
                : null;
    }

    RuntimeProjectileContinuationOpenResult openProjectileContinuation(
            MinecraftServer server,
            ServerSlot slot,
            RuntimeEvent sourceEvent,
            ChildReservation reservation,
            ActionOutputKind outputKind,
            int outputOrdinal) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(sourceEvent, "sourceEvent");
        Objects.requireNonNull(reservation, "reservation");
        if (outputKind != ActionOutputKind.PROJECTILE || outputOrdinal != 0) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        if (!server.isSameThread()
                || slots.get(server) != slot
                || slot.state != ServerSlot.State.RUNNING
                || !server.isRunning()
                || server.isStopped()
                || !slot.dispatching
                || slot.currentEvent != sourceEvent
                || p9ReloadCloseRequested.get()) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.LIFECYCLE_UNAVAILABLE);
        }
        var instance = slot.instances.get(sourceEvent.skillInstanceId());
        if (instance == null
                || !instance.inFlight
                || instance.cancellationRequested
                || instance.terminal
                || instance.lease.pin.isClosed()
                || !instance.lease.reference.equals(sourceEvent.skillReference())) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.LIFECYCLE_UNAVAILABLE);
        }
        if (sourceEvent.nodeIndex() != 0
                || sourceEvent.parentEventId().isPresent()
                || sourceEvent.depth() != 0
                || sourceEvent.childSequence() != 0
                || !(sourceEvent.executionData() instanceof CastGeometryExecutionDataV0 geometry)
                || !P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                        instance.lease.definition)) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        if (slot.runtimeTick >= sourceEvent.deadlineRuntimeTick()) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.LIFECYCLE_UNAVAILABLE);
        }
        if (reservation.capacity() < 1
                || reservation.budget().zeroDelayChildCapacity() < 1
                || instance.activeProjectileContinuation != null
                || slot.activeProjectileContinuations.size() >= 128
                || activeContinuationsForAttribution(slot, instance.attribution) >= 16) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.CAPACITY_UNAVAILABLE);
        }
        var attribution = requireAttribution(slot, instance.attribution);
        if (reservation.detachedPermit() != null
                || slot.currentReservationCount < 1
                || !instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending < slot.currentReservationCount
                || attribution.reservedPending < slot.currentReservationCount
                || slot.reservedPending < slot.currentReservationCount) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }

        final EventId heldChildEventId;
        try {
            heldChildEventId = new EventId(Math.addExact(reservation.eventIdStart(), 1L));
        } catch (ArithmeticException | IllegalArgumentException ignored) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        if (heldChildEventId.value() > slot.eventSequenceHighWater
                || slot.eventIndex.containsKey(heldChildEventId)) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        var permitId = new UUID(slot.token.value(), heldChildEventId.value());
        var plannedProjectileId = new UUID(~slot.token.value(), heldChildEventId.value());
        if (permitId.equals(plannedProjectileId)
                || zeroUuid(permitId)
                || zeroUuid(plannedProjectileId)
                || slot.activeProjectileContinuations.containsKey(permitId)
                || loadedEntityUuidExists(server, plannedProjectileId)) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        if (!(instance.attribution instanceof PlayerRuntimeBudgetAttribution playerAttribution)) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        var actorCandidate = server.getPlayerList().getPlayer(
                playerAttribution.playerId().value());
        if (!isCurrentP9AuthenticatedActor(
                server, instance, actorCandidate, geometry.dimension())) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.LIFECYCLE_UNAVAILABLE);
        }
        var permit = new RuntimeProjectileContinuationPermit(
                this,
                slot.token,
                instance.id,
                instance.attribution,
                sourceEvent.skillReference(),
                geometry.dimension(),
                new SourceFamilyKey(instance.id, sourceEvent.eventId(), 0, 0),
                0,
                heldChildEventId,
                sourceEvent.deadlineRuntimeTick(),
                permitId,
                plannedProjectileId);
        if (slot.activeProjectileContinuations.putIfAbsent(permitId, permit) != null) {
            return continuationRejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        instance.activeProjectileContinuation = permit;
        reservation.attach(permit);
        slot.currentReservationCount--;
        if (slot.currentReservationCount == 0) {
            slot.currentReservationOwner = null;
        }
        recordP9Stage(slot, instance, P9RuntimeDiagnosticStage.CONTINUATION_OPENED);
        return new RuntimeProjectileContinuationOpenResult.Opened(
                permit, plannedProjectileId, this);
    }

    RuntimePermitTransferDisposition transferSpawnedProjectile(
            MinecraftServer server,
            RuntimeProjectileContinuationPermit permit,
            UUID plannedProjectileId,
            P9StarterProjectile projectile) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(plannedProjectileId, "plannedProjectileId");
        Objects.requireNonNull(projectile, "projectile");
        if (!server.isSameThread()) {
            return RuntimePermitTransferDisposition.REJECTED;
        }
        var slot = slots.get(server);
        if (slot == null
                || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || !permit.serverSlotToken.equals(slot.token)
                || !permit.plannedProjectileId.equals(plannedProjectileId)
                || slot.activeProjectileContinuations.get(permit.permitId) != permit) {
            return RuntimePermitTransferDisposition.REJECTED;
        }
        var instance = slot.instances.get(permit.skillInstanceId);
        var level = levelForDimension(server, permit.dimension);
        if (instance == null
                || instance.activeProjectileContinuation != permit
                || level == null
                || level.getEntity(plannedProjectileId) != projectile
                || !projectile.isAddedToLevel()
                || !projectile.getUUID().equals(plannedProjectileId)
                || !projectile.hasContinuationPermitIdentity(permit)) {
            return RuntimePermitTransferDisposition.REJECTED;
        }
        var attribution = slot.attributions.get(permit.budgetAttribution);
        var detachedAndCurrentReservationCount = slot.currentReservationCount + 1;
        var actor = currentP9Actor(server, instance, permit.dimension);
        if (slot.state != ServerSlot.State.RUNNING
                || !server.isRunning()
                || server.isStopped()
                || p9ReloadCloseRequested.get()
                || !slot.dispatching
                || slot.currentEvent == null
                || permit.state != RuntimeProjectileContinuationPermit.State.RESERVED
                || attribution == null
                || instance.terminal
                || instance.cancellationRequested
                || !instance.inFlight
                || !slot.currentEvent.skillInstanceId().equals(instance.id)
                || !slot.currentEvent.eventId().equals(permit.sourceFamily.sourceEventId())
                || slot.currentEvent.nodeIndex() != 0
                || !(slot.currentEvent.executionData()
                        instanceof CastGeometryExecutionDataV0 geometry)
                || !geometry.dimension().equals(permit.dimension)
                || !slot.currentEvent.skillReference().equals(permit.exactReference)
                || slot.currentEvent.deadlineRuntimeTick() != permit.deadlineRuntimeTick
                || instance.lease.pin.isClosed()
                || !instance.lease.reference.equals(permit.exactReference)
                || slot.currentReservationCount < 0
                || (slot.currentReservationCount == 0
                        ? slot.currentReservationOwner != null
                        : !instance.id.equals(slot.currentReservationOwner))
                || instance.reservedPending != detachedAndCurrentReservationCount
                || attribution.reservedPending < detachedAndCurrentReservationCount
                || slot.reservedPending < detachedAndCurrentReservationCount
                || slot.runtimeTick >= permit.deadlineRuntimeTick
                || actor == null
                || projectile.level() != level
                || projectile.isRemoved()
                || !projectile.isAlive()
                || projectile.getOwner() != actor
                || !projectile.hasAuthenticatedCasterIdentity(actor)) {
            recordP9SpawnResult(
                    slot, instance, RuntimePermitTransferDisposition.REJECTED);
            return RuntimePermitTransferDisposition.REJECTED;
        }

        // The entity already owns the exact actor witness.  Publish OPEN before
        // clearing P5 so there is never a stable zero-custodian interval.
        permit.state = RuntimeProjectileContinuationPermit.State.OPEN;
        instance.clearP9AuthenticatedActorWitness();
        recordP9SpawnResult(
                slot, instance, RuntimePermitTransferDisposition.TRANSFERRED);
        return RuntimePermitTransferDisposition.TRANSFERRED;
    }

    RuntimePermitClaimDisposition claimProjectileHit(
            MinecraftServer server,
            RuntimeProjectileContinuationPermit permit,
            ProjectileHitCandidateV0 candidate) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(candidate, "candidate");
        if (!server.isSameThread()) {
            return RuntimePermitClaimDisposition.REJECTED;
        }
        var slot = slots.get(server);
        if (slot == null) {
            return RuntimePermitClaimDisposition.REJECTED;
        }
        try {
            return claimProjectileHitInSlot(server, slot, permit, candidate);
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            slot.p9ErrorCleanup.prepare(server);
            throw preserveErrorFault(slot, primary);
        }
    }

    private RuntimePermitClaimDisposition claimProjectileHitInSlot(
            MinecraftServer server,
            ServerSlot slot,
            RuntimeProjectileContinuationPermit permit,
            ProjectileHitCandidateV0 candidate) {
        var instance = slot.instances.get(permit.skillInstanceId);
        if (slot.state != ServerSlot.State.RUNNING
                || !server.isRunning()
                || server.isStopped()
                || p9ReloadCloseRequested.get()
                || slot.dispatching
                || slot.currentEvent != null
                || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || permit.state != RuntimeProjectileContinuationPermit.State.OPEN
                || !permit.serverSlotToken.equals(slot.token)
                || slot.activeProjectileContinuations.get(permit.permitId) != permit) {
            return rejectClaimAndClose(server, slot, instance, permit, candidate);
        }
        var attribution = slot.attributions.get(permit.budgetAttribution);
        if (instance == null
                || attribution == null
                || instance.activeProjectileContinuation != permit
                || instance.terminal
                || instance.cancellationRequested
                || instance.inFlight
                || instance.lease.pin.isClosed()
                || !instance.lease.reference.equals(permit.exactReference)
                || instance.reservedPending != 1
                || attribution.reservedPending < 1
                || slot.reservedPending < 1
                || slot.runtimeTick >= permit.deadlineRuntimeTick
                || instance.lifetimeEvents >= slot.limits.eventsPerSkillInstance()
                || slot.eventIndex.containsKey(permit.heldChildEventId)
                || permit.heldChildEventId.value() > slot.eventSequenceHighWater
                || !permit.sourceFamily.skillInstanceId().equals(instance.id)
                || permit.sourceFamily.producerNodeIndex() != 0
                || permit.sourceFamily.outputOrdinal() != 0
                || permit.sourceDerivationDepth != 0) {
            return rejectClaimAndClose(server, slot, instance, permit, candidate);
        }
        var level = levelForDimension(server, permit.dimension);
        var loadedProjectile = level == null
                ? null
                : level.getEntity(permit.plannedProjectileId);
        var target = level == null ? null : level.getEntity(candidate.targetId());
        var hitPosition = BlockPos.containing(
                candidate.hitX(), candidate.hitY(), candidate.hitZ());
        var actor = currentP9EntityActor(server, instance, loadedProjectile, permit);
        if (!(loadedProjectile instanceof P9StarterProjectile projectile)
                || !(target instanceof LivingEntity livingTarget)
                || actor == null
                || candidate.projectileId().equals(candidate.targetId())
                || !candidate.projectileId().equals(permit.plannedProjectileId)
                || !candidate.dimension().equals(permit.dimension)
                || projectile.level() != level
                || projectile.isRemoved()
                || !projectile.isAlive()
                || !projectile.hasContinuationPermitIdentity(permit)
                || livingTarget == actor
                || livingTarget.level() != level
                || livingTarget.isRemoved()
                || !livingTarget.isAlive()
                || !level.isInWorldBounds(hitPosition)
                || !level.isLoaded(hitPosition)
                || !level.getWorldBorder().isWithinBounds(
                        candidate.hitX(), candidate.hitZ())) {
            return rejectClaimAndClose(server, slot, instance, permit, candidate);
        }

        var hitData = new ProjectileHitExecutionDataV0(
                permit.permitId,
                permit.plannedProjectileId,
                permit.sourceFamily,
                permit.sourceDerivationDepth,
                permit.dimension,
                candidate.targetId(),
                candidate.hitX(),
                candidate.hitY(),
                candidate.hitZ(),
                candidate.directionXQ15(),
                candidate.directionYQ15(),
                candidate.directionZQ15());
        var childSpec = new RuntimeChildSpec(
                1,
                0,
                0,
                new PlayerOrigin(
                        slot.token,
                        level.dimension(),
                        ((PlayerRuntimeBudgetAttribution) instance.attribution).playerId()),
                Optional.of(new EntityTarget(
                        slot.token,
                        level.dimension(),
                        new RuntimeEntityId(candidate.targetId()),
                        RuntimeEntityKind.LIVING_ENTITY)),
                new ChildTriggerCause(new TriggerEventKind(P9StarterSkillContent.EFFECT_HIT_ID)),
                hitData);
        if (validateChildShape(instance.lease.definition, instance.attribution, childSpec)
                .isPresent()) {
            return rejectClaimAndClose(server, slot, instance, permit, candidate);
        }
        var child = new RuntimeEvent(
                permit.heldChildEventId,
                instance.id,
                instance.sequence,
                new RuntimeCancellationToken(slot.token, instance.id),
                Optional.of(permit.sourceFamily.sourceEventId()),
                permit.exactReference,
                1,
                slot.runtimeTick,
                slot.runtimeTick,
                permit.deadlineRuntimeTick,
                1,
                1,
                RuntimeSchedulePersistence.MEMORY_ONLY,
                instance.attribution,
                childSpec.origin(),
                childSpec.target(),
                childSpec.triggerCause(),
                hitData);

        addCommittedEvent(slot, instance, attribution, child);
        instance.reservedPending--;
        attribution.reservedPending--;
        slot.reservedPending--;
        instance.lifetimeEvents++;
        permit.state = RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;
        recordP9HitClaimResult(
                slot, instance, permit, candidate, RuntimePermitClaimDisposition.QUEUED);
        return RuntimePermitClaimDisposition.QUEUED;
    }

    private RuntimePermitClaimDisposition rejectClaimAndClose(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            RuntimeProjectileContinuationPermit permit,
            ProjectileHitCandidateV0 candidate) {
        recordP9HitClaimResult(
                slot, instance, permit, candidate, RuntimePermitClaimDisposition.REJECTED);
        var close = closeProjectileContinuation(
                server,
                permit,
                permit.serverSlotToken,
                permit.skillInstanceId,
                permit.budgetAttribution,
                permit.permitId,
                ProjectileClosureReason.CLAIM_REJECTED);
        if (close != RuntimePermitCloseDisposition.CLOSED
                && close != RuntimePermitCloseDisposition.ALREADY_CLOSED) {
            return RuntimePermitClaimDisposition.REJECTED;
        }
        return RuntimePermitClaimDisposition.REJECTED;
    }

    RuntimePermitCloseDisposition closeProjectileContinuation(
            MinecraftServer server,
            RuntimeProjectileContinuationPermit permit,
            RuntimeServerToken serverSlotToken,
            SkillInstanceId skillInstanceId,
            RuntimeBudgetAttribution budgetAttribution,
            UUID permitId,
            ProjectileClosureReason reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        Objects.requireNonNull(budgetAttribution, "budgetAttribution");
        Objects.requireNonNull(permitId, "permitId");
        Objects.requireNonNull(reason, "reason");
        return closeProjectileContinuationOnObservedThread(
                server.isSameThread(),
                server,
                permit,
                serverSlotToken,
                skillInstanceId,
                budgetAttribution,
                permitId,
                reason);
    }

    RuntimePermitCloseDisposition rejectProjectileContinuationCloseForGameTest(
            MinecraftServer server,
            RuntimeProjectileContinuationPermit permit,
            RuntimeServerToken serverSlotToken,
            SkillInstanceId skillInstanceId,
            RuntimeBudgetAttribution budgetAttribution,
            UUID permitId,
            ProjectileClosureReason reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        Objects.requireNonNull(budgetAttribution, "budgetAttribution");
        Objects.requireNonNull(permitId, "permitId");
        Objects.requireNonNull(reason, "reason");
        return closeProjectileContinuationOnObservedThread(
                false,
                server,
                permit,
                serverSlotToken,
                skillInstanceId,
                budgetAttribution,
                permitId,
                reason);
    }

    private RuntimePermitCloseDisposition closeProjectileContinuationOnObservedThread(
            boolean observedSameThread,
            MinecraftServer server,
            RuntimeProjectileContinuationPermit permit,
            RuntimeServerToken serverSlotToken,
            SkillInstanceId skillInstanceId,
            RuntimeBudgetAttribution budgetAttribution,
            UUID permitId,
            ProjectileClosureReason reason) {
        if (!observedSameThread) {
            return RuntimePermitCloseDisposition.REJECTED;
        }
        Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        Objects.requireNonNull(budgetAttribution, "budgetAttribution");
        Objects.requireNonNull(permitId, "permitId");
        var slot = slots.get(server);
        if (slot == null || !slot.token.equals(serverSlotToken)) {
            return RuntimePermitCloseDisposition.REJECTED;
        }
        if (permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || !permit.serverSlotToken.equals(serverSlotToken)
                || !permit.skillInstanceId.equals(skillInstanceId)
                || !permit.budgetAttribution.equals(budgetAttribution)
                || !permit.permitId.equals(permitId)) {
            return RuntimePermitCloseDisposition.REJECTED;
        }
        if (permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                || permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT) {
            return RuntimePermitCloseDisposition.ALREADY_CLOSED;
        }
        var instance = slot.instances.get(skillInstanceId);
        var indexed = slot.p9BatchContinuationCloseInProgress
                ? permit
                : slot.activeProjectileContinuations.get(permitId);
        var errorDeindexedRecovery = indexed == null
                && slot.p9ActiveIndexInvalidatedAfterError
                && slot.activeProjectileContinuations.isEmpty()
                && slot.state == ServerSlot.State.STOPPING
                && reason == ProjectileClosureReason.SERVER_STOPPED
                && instance != null
                && instance.activeProjectileContinuation == permit;
        if (indexed == null && !errorDeindexedRecovery) {
            return RuntimePermitCloseDisposition.ALREADY_CLOSED;
        }
        if (!errorDeindexedRecovery && indexed != permit) {
            return RuntimePermitCloseDisposition.REJECTED;
        }
        var attribution = slot.attributions.get(budgetAttribution);
        var claimed = permit.state
                == RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE;
        if (instance == null
                || attribution == null
                || instance.activeProjectileContinuation != permit
                || !instance.attribution.equals(budgetAttribution)
                || !claimed && (instance.reservedPending <= 0
                        || attribution.reservedPending <= 0
                        || !errorDeindexedRecovery && slot.reservedPending <= 0)) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (!claimed
                && permit.state == RuntimeProjectileContinuationPermit.State.RESERVED
                && reason == ProjectileClosureReason.SPAWN_NOT_APPLIED) {
            recordP9SpawnResult(
                    slot, instance, RuntimePermitTransferDisposition.REJECTED);
        }
        if (claimed) {
            var child = slot.eventIndex.get(permit.heldChildEventId);
            if (child != null && slot.currentEvent != child) {
                if (!removeExactQueuedOrDeferred(slot, child)) {
                    throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
                }
                removeCommittedEvent(slot, child);
            }
        }
        if (!slot.p9BatchContinuationCloseInProgress
                && !errorDeindexedRecovery
                && !slot.activeProjectileContinuations.remove(permitId, permit)) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.activeProjectileContinuation = null;
        if (claimed) {
            permit.state = RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT;
        } else {
            instance.reservedPending--;
            attribution.reservedPending--;
            if (!errorDeindexedRecovery) {
                slot.reservedPending--;
            }
            permit.state = RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT;
        }
        instance.clearP9AuthenticatedActorWitness();
        if (!errorDeindexedRecovery
                && !slot.p9BatchContinuationCloseInProgress) {
            maybeRemoveInstance(slot, instance.id);
        }
        recordP9Terminal(slot, instance, reason, P9RuntimeCleanupDisposition.RELEASED);
        return RuntimePermitCloseDisposition.CLOSED;
    }

    private static RuntimeProjectileContinuationOpenResult continuationRejected(
            RuntimeProjectileContinuationOpenRejectionReason reason) {
        return new RuntimeProjectileContinuationOpenResult.Rejected(reason);
    }

    boolean ownsOpenedContinuation(
            RuntimeProjectileContinuationPermit permit, UUID plannedProjectileId) {
        Objects.requireNonNull(permit, "permit");
        Objects.requireNonNull(plannedProjectileId, "plannedProjectileId");
        if (permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || !permit.plannedProjectileId.equals(plannedProjectileId)) {
            return false;
        }
        for (var slot : slots.values()) {
            if (slot.activeProjectileContinuations.get(permit.permitId) == permit) {
                return true;
            }
        }
        return false;
    }

    boolean isOwningPermitServerThread(
            MinecraftServer server, RuntimeProjectileContinuationPermit permit) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(permit, "permit");
        var slot = slots.get(server);
        return server.isSameThread()
                && slot != null
                && slot.token.equals(permit.serverSlotToken);
    }

    private static void closeActiveP9ContinuationAndDiscard(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ProjectileClosureReason reason) {
        var permit = instance.activeProjectileContinuation;
        if (permit == null) {
            return;
        }
        var projectile = loadedProjectile(server, permit);
        var close = permit.closeWithoutHit(server, reason);
        if (close != RuntimePermitCloseDisposition.CLOSED
                && close != RuntimePermitCloseDisposition.ALREADY_CLOSED) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (projectile != null && !projectile.isRemoved()) {
            projectile.discard();
        }
    }

    private static P9StarterProjectile loadedProjectile(
            MinecraftServer server, RuntimeProjectileContinuationPermit permit) {
        var level = levelForDimension(server, permit.dimension);
        if (level == null) {
            return null;
        }
        var loaded = level.getEntity(permit.plannedProjectileId);
        return loaded instanceof P9StarterProjectile projectile ? projectile : null;
    }

    private static ProjectileClosureReason terminalReasonForOutcome(
            RuntimeExecutionOutcome outcome) {
        if (outcome instanceof RuntimeExecutionOutcome.DeadlineExpired
                || outcome instanceof RuntimeExecutionOutcome.ScheduleRejected) {
            return ProjectileClosureReason.DEADLINE_REACHED;
        }
        if (outcome instanceof RuntimeExecutionOutcome.Cancelled
                || outcome instanceof RuntimeExecutionOutcome.OwnerInstanceUnavailable
                || outcome instanceof RuntimeExecutionOutcome.SourceMissing
                || outcome instanceof RuntimeExecutionOutcome.TargetMissing
                || outcome instanceof RuntimeExecutionOutcome.SkillRevisionUnavailable) {
            return ProjectileClosureReason.OWNER_INVALIDATED;
        }
        return ProjectileClosureReason.RUNTIME_FAULT;
    }

    private static int activeContinuationsForAttribution(
            ServerSlot slot, RuntimeBudgetAttribution attribution) {
        var count = 0;
        for (var instance : slot.instances.values()) {
            if (instance.attribution.equals(attribution)
                    && instance.activeProjectileContinuation != null) {
                count = Math.incrementExact(count);
            }
        }
        return count;
    }

    private static boolean loadedEntityUuidExists(MinecraftServer server, UUID uuid) {
        for (var level : server.getAllLevels()) {
            if (level.getEntity(uuid) != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean zeroUuid(UUID value) {
        return value.getMostSignificantBits() == 0L
                && value.getLeastSignificantBits() == 0L;
    }

    private static boolean isCurrentP9AuthenticatedActor(
            MinecraftServer server,
            ServerSlot.InstanceState instance,
            ServerPlayer candidate,
            ResourceLocation dimension) {
        if (!server.isSameThread()
                || !server.isRunning()
                || server.isStopped()
                || candidate == null
                || !(instance.attribution instanceof PlayerRuntimeBudgetAttribution player)) {
            return false;
        }
        var playerId = player.playerId().value();
        if (!candidate.getUUID().equals(playerId)
                || server.getPlayerList().getPlayer(candidate.getUUID()) != candidate
                || !instance.hasP9AuthenticatedActorWitness(candidate)
                || candidate.getServer() != server) {
            return false;
        }
        var level = candidate.serverLevel();
        return level.getServer() == server
                && !candidate.isRemoved()
                && candidate.isAlive()
                && level.dimension().location().equals(dimension)
                && candidate.connection != null
                && candidate.connection.isAcceptingMessages();
    }

    private static ServerPlayer currentP9Actor(
            MinecraftServer server,
            ServerSlot.InstanceState instance,
            ResourceLocation dimension) {
        if (!(instance.attribution instanceof PlayerRuntimeBudgetAttribution player)) {
            return null;
        }
        var candidate = server.getPlayerList().getPlayer(player.playerId().value());
        return isCurrentP9AuthenticatedActor(server, instance, candidate, dimension)
                ? candidate
                : null;
    }

    private static ServerPlayer currentP9EntityActor(
            MinecraftServer server,
            ServerSlot.InstanceState instance,
            Entity loadedProjectile,
            RuntimeProjectileContinuationPermit permit) {
        if (!(loadedProjectile instanceof P9StarterProjectile projectile)
                || !(instance.attribution instanceof PlayerRuntimeBudgetAttribution player)) {
            return null;
        }
        var candidate = server.getPlayerList().getPlayer(player.playerId().value());
        if (!server.isSameThread()
                || !server.isRunning()
                || server.isStopped()
                || candidate == null
                || !candidate.getUUID().equals(player.playerId().value())
                || server.getPlayerList().getPlayer(candidate.getUUID()) != candidate
                || candidate.getServer() != server
                || candidate.serverLevel().getServer() != server
                || !candidate.serverLevel().dimension().location().equals(permit.dimension)
                || candidate.isRemoved()
                || !candidate.isAlive()
                || candidate.connection == null
                || !candidate.connection.isAcceptingMessages()
                || projectile.getOwner() != candidate
                || projectile.level() != candidate.serverLevel()
                || !projectile.hasAuthenticatedCasterIdentity(candidate)
                || !projectile.hasContinuationPermitIdentity(permit)) {
            return null;
        }
        return candidate;
    }

    private static net.minecraft.server.level.ServerLevel levelForDimension(
            MinecraftServer server, ResourceLocation dimension) {
        for (var level : server.getAllLevels()) {
            if (level.dimension().location().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static ServerPlayer currentP9ChildActor(
            MinecraftServer server, RuntimeEvent event) {
        if (!(event.origin() instanceof PlayerOrigin origin)) {
            return null;
        }
        return server.getPlayerList().getPlayer(origin.player().value());
    }

    private static Entity currentP9ChildTarget(
            MinecraftServer server, RuntimeEvent event) {
        if (!(event.executionData() instanceof ProjectileHitExecutionDataV0 hit)) {
            return null;
        }
        var level = levelForDimension(server, hit.dimension());
        return level == null ? null : level.getEntity(hit.targetId());
    }

    private static boolean validClaimedP9Child(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            RuntimeEvent event) {
        if (!(event.executionData() instanceof ProjectileHitExecutionDataV0 hit)
                || event.nodeIndex() != 1
                || event.depth() != 1
                || event.childSequence() != 1
                || event.parentEventId().isEmpty()
                || !event.parentEventId().orElseThrow().equals(
                        hit.sourceFamily().sourceEventId())
                || event.scheduledRuntimeTick() != event.createdRuntimeTick()
                || event.deadlineRuntimeTick() < event.scheduledRuntimeTick()
                || !event.skillReference().equals(instance.lease.reference)
                || !event.skillInstanceId().equals(instance.id)
                || !hit.sourceFamily().skillInstanceId().equals(instance.id)
                || hit.sourceDerivationDepth() != 0) {
            return false;
        }
        var permit = slot.activeProjectileContinuations.get(hit.permitId());
        if (permit == null
                || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || permit.state
                        != RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE
                || instance.activeProjectileContinuation != permit
                || !permit.serverSlotToken.equals(slot.token)
                || !permit.skillInstanceId.equals(instance.id)
                || !permit.exactReference.equals(event.skillReference())
                || !permit.heldChildEventId.equals(event.eventId())
                || !permit.plannedProjectileId.equals(hit.projectileId())
                || !permit.sourceFamily.matches(
                        hit.sourceFamily(),
                        permit.exactReference,
                        event.skillReference(),
                        hit.sourceDerivationDepth(),
                        false)
                || permit.deadlineRuntimeTick != event.deadlineRuntimeTick()
                || !permit.dimension.equals(hit.dimension())
                || instance.lease.pin.isClosed()
                || slot.runtimeTick > permit.deadlineRuntimeTick) {
            return false;
        }
        var level = levelForDimension(server, permit.dimension);
        var loadedProjectile = level == null ? null : level.getEntity(hit.projectileId());
        var target = level == null ? null : level.getEntity(hit.targetId());
        var actor = currentP9EntityActor(server, instance, loadedProjectile, permit);
        return loadedProjectile instanceof P9StarterProjectile projectile
                && target instanceof LivingEntity livingTarget
                && actor != null
                && projectile.level() == level
                && !projectile.isRemoved()
                && projectile.isAlive()
                && projectile.hasContinuationPermitIdentity(permit)
                && livingTarget != actor
                && livingTarget.level() == level
                && !livingTarget.isRemoved()
                && livingTarget.isAlive();
    }

    RuntimeAdmissionResult admitRoot(MinecraftServer server, RuntimeRootEventSpec spec) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(spec, "spec");
        if (spec.executionData() instanceof CastGeometryExecutionDataV0) {
            return new RuntimeAdmissionResult.InvalidEvent(
                    InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        return admitRootWithP9Actor(server, spec, null);
    }

    private RuntimeAdmissionResult admitRootWithP9Actor(
            MinecraftServer server,
            RuntimeRootEventSpec spec,
            ServerPlayer p9AuthenticatedActorWitness) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(spec, "spec");
        if (spec.executionData() instanceof CastGeometryExecutionDataV0
                && p9AuthenticatedActorWitness == null) {
            return new RuntimeAdmissionResult.InvalidEvent(
                    InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        if (!(spec.executionData() instanceof CastGeometryExecutionDataV0)
                && p9AuthenticatedActorWitness != null) {
            throw new IllegalArgumentException("actor witness requires cast geometry");
        }
        var slot = slots.get(server);
        if (slot == null) {
            return new RuntimeAdmissionResult.ServerNotRunning();
        }
        if (!server.isSameThread()) {
            return new RuntimeAdmissionResult.WrongThread();
        }
        if (isP9ExecutionData(spec.executionData()) && p9ReloadCloseRequested.get()) {
            return new RuntimeAdmissionResult.ServerStopping();
        }
        if (slot.state == ServerSlot.State.FAULTED) {
            return new RuntimeAdmissionResult.KernelFaulted();
        }
        if (slot.state == ServerSlot.State.EXHAUSTED) {
            return new RuntimeAdmissionResult.TickExhausted();
        }
        if (slot.state != ServerSlot.State.RUNNING || !server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeAdmissionResult.ServerStopping();
        }
        if (slot.rootAdmissionsThisTick == slot.limits.rootAdmissionsPerTick()) {
            return new RuntimeAdmissionResult.RootAdmissionBudgetExceeded(
                    slot.limits.rootAdmissionsPerTick());
        }
        slot.rootAdmissionsThisTick++;
        slot.diagnostics.rootAdmissionAttemptsThisTick++;

        var schedule = spec.schedule();
        var persistenceFailure = persistenceFailure(schedule);
        if (persistenceFailure.isPresent()) {
            return persistenceFailure.orElseThrow();
        }
        if (schedule.delayTicks() < 0
                || schedule.delayTicks() > slot.limits.maximumDelayTicks()) {
            return new RuntimeAdmissionResult.DelayOutOfRange(
                    schedule.delayTicks(), slot.limits.maximumDelayTicks());
        }
        if (schedule.deadlineHorizonTicks() < 0
                || schedule.deadlineHorizonTicks()
                        > slot.limits.maximumDeadlineHorizonTicks()) {
            return new RuntimeAdmissionResult.DeadlineOutOfRange(
                    schedule.deadlineHorizonTicks(),
                    slot.limits.maximumDeadlineHorizonTicks());
        }

        final long baseTick;
        final long scheduledTick;
        final long deadlineTick;
        try {
            baseTick = Math.addExact(slot.runtimeTick, 1L);
        } catch (ArithmeticException ignored) {
            return new RuntimeAdmissionResult.TickExhausted();
        }
        try {
            scheduledTick = Math.addExact(baseTick, schedule.delayTicks());
        } catch (ArithmeticException ignored) {
            return new RuntimeAdmissionResult.DelayOverflow();
        }
        try {
            deadlineTick = Math.addExact(baseTick, schedule.deadlineHorizonTicks());
        } catch (ArithmeticException ignored) {
            return new RuntimeAdmissionResult.DeadlineOverflow();
        }
        if (scheduledTick > deadlineTick) {
            return new RuntimeAdmissionResult.DeadlineBeforeScheduledTick(
                    scheduledTick, deadlineTick);
        }

        var invalid = validateRootStableShape(slot, spec);
        if (invalid.isPresent()) {
            return new RuntimeAdmissionResult.InvalidEvent(invalid.orElseThrow());
        }
        if (!stableTokensMatch(slot.token, spec.origin(), spec.target())) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    RuntimeReferenceFailureReason.WRONG_SERVER);
        }
        var rootResolution = P5LoadedReferenceResolver.resolveLoadedReferences(
                server, slot.token, spec.origin(), spec.target());
        if (slot.state != ServerSlot.State.RUNNING) {
            return admissionForClosedSlot(slot);
        }
        if (!server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeAdmissionResult.ServerStopping();
        }
        if (!(rootResolution instanceof RuntimeReferenceResolutionOutcome.Resolved)) {
            return new RuntimeAdmissionResult.InvalidRuntimeReference(
                    referenceFailure(rootResolution));
        }
        ServerPlayer resolvedP9Actor = null;
        if (spec.executionData() instanceof CastGeometryExecutionDataV0) {
            var resolvedContext = ((RuntimeReferenceResolutionOutcome.Resolved) rootResolution)
                    .context();
            if (!(resolvedContext.origin() instanceof ResolvedPlayerOrigin playerOrigin)) {
                return new RuntimeAdmissionResult.InvalidRuntimeReference(
                        RuntimeReferenceFailureReason.TYPE_MISMATCH);
            }
            resolvedP9Actor = playerOrigin.player();
        }
        if (slot.instances.size() == slot.limits.activeSkillInstancesPerServer()) {
            return new RuntimeAdmissionResult.ActiveLineageCapacityExceeded(
                    slot.instances.size(), slot.limits.activeSkillInstancesPerServer());
        }
        var attribution = slot.attributions.get(spec.budgetAttribution());
        var activeAttribution = attribution == null ? 0 : attribution.activeInstances;
        if (activeAttribution == slot.limits.activeSkillInstancesPerAttribution()) {
            return new RuntimeAdmissionResult.ActiveBudgetAttributionCapacityExceeded(
                    activeAttribution, slot.limits.activeSkillInstancesPerAttribution());
        }
        var nextInstanceSequence = checkedPositiveSuccessor(slot.skillInstanceSequenceHighWater);
        if (nextInstanceSequence.isEmpty()) {
            return new RuntimeAdmissionResult.SequenceExhausted(
                    RuntimeSequenceKind.SKILL_INSTANCE_SEQUENCE);
        }
        var nextEventSequence = checkedPositiveSuccessor(slot.eventSequenceHighWater);
        if (nextEventSequence.isEmpty()) {
            return new RuntimeAdmissionResult.SequenceExhausted(RuntimeSequenceKind.EVENT_SEQUENCE);
        }

        var prospectiveSequence = nextInstanceSequence.orElseThrow();
        var prospectiveId = new SkillInstanceId(new UUID(slot.token.value(), prospectiveSequence));
        if (slot.instances.containsKey(prospectiveId)) {
            return new RuntimeAdmissionResult.InvalidEvent(
                    InvalidEventReason.DUPLICATE_LIVE_SKILL_INSTANCE_ID);
        }
        var attributionPending = attribution == null
                ? 0
                : attribution.committedPending + attribution.reservedPending;
        if (attributionPending == slot.limits.pendingEventsPerAttribution()) {
            var reason = spec.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution
                    ? RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED
                    : RuntimeCircuitBreakReason.NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED;
            var summary = new RuntimeCircuitBreakerSummary(
                    reason,
                    attributionPending,
                    1,
                    slot.limits.pendingEventsPerAttribution(),
                    0,
                    false);
            observeBreaker(
                    slot,
                    prospectiveId,
                    Optional.empty(),
                    Optional.empty(),
                    playerId(spec.budgetAttribution()),
                    summary);
            return new RuntimeAdmissionResult.CircuitBroken(summary);
        }
        if (slot.committedPending + slot.reservedPending
                == slot.limits.pendingEventsPerServer()) {
            var summary = new RuntimeCircuitBreakerSummary(
                    RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                    slot.committedPending + slot.reservedPending,
                    1,
                    slot.limits.pendingEventsPerServer(),
                    0,
                    false);
            observeBreaker(
                    slot,
                    prospectiveId,
                    Optional.empty(),
                    Optional.empty(),
                    playerId(spec.budgetAttribution()),
                    summary);
            return new RuntimeAdmissionResult.CircuitBroken(summary);
        }

        var prospectiveEventId = new EventId(nextEventSequence.orElseThrow());
        var cancellationToken = new RuntimeCancellationToken(slot.token, prospectiveId);
        var accepted = new RuntimeAdmissionResult.AcceptedMemoryOnly(
                new RuntimeEventToken(slot.token, prospectiveId, prospectiveEventId),
                cancellationToken);

        try {
            return acquireAndPublishRoot(
                    server,
                    slot,
                    spec,
                    attribution,
                    prospectiveSequence,
                    prospectiveId,
                    prospectiveEventId,
                    cancellationToken,
                    accepted,
                    scheduledTick,
                    deadlineTick,
                    p9AuthenticatedActorWitness,
                    resolvedP9Actor);
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            slot.p9ErrorCleanup.prepare(server);
            throw preserveErrorFault(slot, primary);
        }
    }

    private RuntimeAdmissionResult acquireAndPublishRoot(
            MinecraftServer server,
            ServerSlot slot,
            RuntimeRootEventSpec spec,
            ServerSlot.AttributionState attribution,
            long prospectiveSequence,
            SkillInstanceId prospectiveId,
            EventId prospectiveEventId,
            RuntimeCancellationToken cancellationToken,
            RuntimeAdmissionResult.AcceptedMemoryOnly accepted,
            long scheduledTick,
            long deadlineTick,
            ServerPlayer p9AuthenticatedActorWitness,
            ServerPlayer resolvedP9Actor) {
        var leaseAcquisition = acquireLease(server, slot, spec.skillReference());
        try {
            if (slot.state != ServerSlot.State.RUNNING) {
                releaseProvisionalLease(slot, leaseAcquisition);
                return admissionForClosedSlot(slot);
            }
            if (!server.isRunning() || server.isStopped()) {
                stopSlot(slot);
                releaseProvisionalLease(slot, leaseAcquisition);
                return new RuntimeAdmissionResult.ServerStopping();
            }
            if (leaseAcquisition instanceof LeaseAcquisition.Unavailable unavailable) {
                return new RuntimeAdmissionResult.SkillRevisionUnavailable(unavailable.reason());
            }
            var lease = ((LeaseAcquisition.Available) leaseAcquisition).lease();
            var nodeFailure = validateRootDefinitionShape(lease.definition, spec);
            if (nodeFailure.isPresent()) {
                releaseProvisionalLease(slot, leaseAcquisition);
                return new RuntimeAdmissionResult.InvalidEvent(nodeFailure.orElseThrow());
            }
            var prospectiveEvent = new RuntimeEvent(
                    prospectiveEventId,
                    prospectiveId,
                    new RuntimeSkillInstanceSequence(prospectiveSequence),
                    cancellationToken,
                    Optional.empty(),
                    spec.skillReference(),
                    spec.nodeIndex(),
                    slot.runtimeTick,
                    scheduledTick,
                    deadlineTick,
                    0,
                    0,
                    RuntimeSchedulePersistence.MEMORY_ONLY,
                    spec.budgetAttribution(),
                    spec.origin(),
                    spec.target(),
                    spec.triggerCause(),
                    spec.executionData());
            var instance = new ServerSlot.InstanceState(
                    prospectiveEvent.skillInstanceId(),
                    prospectiveEvent.skillInstanceSequence(),
                    prospectiveEvent.budgetAttribution(),
                    lease,
                    p9AuthenticatedActorWitness);
            if (prospectiveEvent.executionData() instanceof CastGeometryExecutionDataV0 geometry
                    && !isCurrentP9AuthenticatedActor(
                            server, instance, resolvedP9Actor, geometry.dimension())) {
                releaseProvisionalLease(slot, leaseAcquisition);
                return new RuntimeAdmissionResult.InvalidRuntimeReference(
                        RuntimeReferenceFailureReason.MISSING);
            }
            publishRoot(
                    slot,
                    prospectiveEvent,
                    lease,
                    leaseAcquisition,
                    attribution,
                    instance);
            return accepted;
        } catch (RuntimeException | Error primary) {
            closeProvisionalAfterRootFault(leaseAcquisition);
            throw primary;
        }
    }

    RuntimeCancellationResult cancel(
            MinecraftServer server, RuntimeCancellationHandle handle) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(handle, "handle");
        var slot = slots.get(server);
        if (slot == null) {
            return new RuntimeCancellationResult.ServerNotRunning();
        }
        if (!server.isSameThread()) {
            return new RuntimeCancellationResult.WrongThread();
        }
        if (slot.state != ServerSlot.State.RUNNING || !server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeCancellationResult.ServerStopping();
        }
        return cancelInSlot(server, slot, handle);
    }

    private RuntimeCancellationResult cancelInSlot(
            MinecraftServer server,
            ServerSlot slot,
            RuntimeCancellationHandle handle) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(handle, "handle");
        if (!slot.token.equals(serverToken(handle))) {
            return new RuntimeCancellationResult.WrongServer();
        }
        if (!cancellationBudgetAvailable(
                slot.cancellationsThisTick, slot.limits.cancellationsPerTick())) {
            return new RuntimeCancellationResult.CancellationBudgetExceeded(
                    slot.limits.cancellationsPerTick());
        }
        slot.cancellationsThisTick++;
        try {
            if (handle instanceof RuntimeCancellationToken token) {
                return cancelInstance(server, slot, token.skillInstanceId());
            }
            var token = (RuntimeEventToken) handle;
            var indexed = slot.eventIndex.get(token.eventId());
            if (indexed == null) {
                return new RuntimeCancellationResult.NotPending();
            }
            if (!indexed.skillInstanceId().equals(token.skillInstanceId())) {
                return new RuntimeCancellationResult.CancellationTokenInvalid(
                        RuntimeCancellationTokenInvalidReason.EVENT_OWNER_MISMATCH);
            }
            if (slot.currentEvent == indexed) {
                return new RuntimeCancellationResult.InFlight();
            }
            if (!removeExactQueuedOrDeferred(slot, indexed)) {
                throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
            }
            var owner = slot.instances.get(indexed.skillInstanceId());
            removeCommittedEvent(slot, indexed);
            if (owner != null && isP9ExecutionData(indexed.executionData())) {
                closeActiveP9ContinuationAndDiscard(
                        server, slot, owner, ProjectileClosureReason.OWNER_INVALIDATED);
                owner.clearP9AuthenticatedActorWitness();
            }
            maybeRemoveInstance(slot, token.skillInstanceId());
            if (owner != null && isP9ExecutionData(indexed.executionData())) {
                recordP9Terminal(
                        slot,
                        owner,
                        ProjectileClosureReason.OWNER_INVALIDATED,
                        P9RuntimeCleanupDisposition.RELEASED);
            }
            return new RuntimeCancellationResult.CancelledEvent();
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            slot.p9ErrorCleanup.prepare(server);
            throw preserveErrorFault(slot, primary);
        }
    }

    boolean cancellationBudgetAvailable(
            int cancellationsThisTick, int cancellationLimit) {
        if (cancellationsThisTick < 0
                || cancellationLimit <= 0
                || cancellationsThisTick > cancellationLimit) {
            throw new IllegalArgumentException("invalid cancellation boundary state");
        }
        return cancellationsThisTick < cancellationLimit;
    }

    void handleRuntimePost(ServerTickEvent.Post event) {
        var server = event.getServer();
        var slot = slots.get(server);
        if (slot == null) {
            throw kernel(RuntimeKernelException.Code.TICK_BEFORE_INSTALL);
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_DRAIN);
        }
        if (slot.state != ServerSlot.State.RUNNING) {
            return;
        }
        if (!server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return;
        }
        if (slot.dispatching) {
            throw kernel(RuntimeKernelException.Code.NESTED_DRAIN);
        }
        if (p9ReloadCloseRequested.get()) {
            invalidateP9WorkPreservingPrimary(
                    server, slot, ProjectileClosureReason.RELOAD_INVALIDATED);
        }
        if (advanceRuntimeTick(slot) == RuntimeTickAdvanceResult.EXHAUSTED) {
            slot.state = ServerSlot.State.EXHAUSTED;
            clearSlotNormal(server, slot);
            return;
        }
        resetTickState(slot);
        drain(server, slot);
        if (slot.state == ServerSlot.State.RUNNING) {
            try {
                sweepActiveProjectileContinuations(server, slot);
            } catch (RuntimeException primary) {
                throw preserveRuntimeFault(slot, primary);
            } catch (Error primary) {
                slot.p9ErrorCleanup.prepare(server);
                throw preserveErrorFault(slot, primary);
            }
        }
    }

    private static void sweepActiveProjectileContinuations(
            MinecraftServer server, ServerSlot slot) {
        if (slot.dispatching || slot.p9BatchContinuationCloseInProgress) {
            throw kernel(RuntimeKernelException.Code.NESTED_DRAIN);
        }
        var workUnits = slot.activeProjectileContinuations.size();
        if (workUnits > 128) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        var observed = 0;
        var permits = slot.activeProjectileContinuations.entrySet().iterator();
        while (permits.hasNext()) {
            var indexed = permits.next();
            observed++;
            var permit = indexed.getValue();
            if (!indexed.getKey().equals(permit.permitId)
                    || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                    || !permit.serverSlotToken.equals(slot.token)) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            if (permit.state == RuntimeProjectileContinuationPermit.State.RESERVED) {
                continue;
            }
            if (permit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                    || permit.state
                            == RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            var instance = slot.instances.get(permit.skillInstanceId);
            if (instance == null || instance.activeProjectileContinuation != permit) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            var projectile = loadedProjectile(server, permit);
            var actor = currentP9EntityActor(server, instance, projectile, permit);
            var expired = permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    ? slot.runtimeTick >= permit.deadlineRuntimeTick
                    : slot.runtimeTick > permit.deadlineRuntimeTick;
            var claimedChildMissing = permit.state
                            == RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE
                    && slot.eventIndex.get(permit.heldChildEventId) == null;
            if (!expired && projectile != null && actor != null && !claimedChildMissing) {
                continue;
            }
            var reason = expired
                    ? ProjectileClosureReason.DEADLINE_REACHED
                    : projectile == null
                            ? ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED
                            : actor == null
                                    ? ProjectileClosureReason.OWNER_INVALIDATED
                                    : ProjectileClosureReason.RUNTIME_FAULT;
            slot.p9BatchContinuationCloseInProgress = true;
            try {
                var close = permit.closeWithoutHit(server, reason);
                if (close != RuntimePermitCloseDisposition.CLOSED) {
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                permits.remove();
            } finally {
                slot.p9BatchContinuationCloseInProgress = false;
            }
            if (projectile != null && !projectile.isRemoved()) {
                projectile.discard();
            }
            maybeRemoveInstance(slot, instance.id);
        }
        if (observed != workUnits) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
    }

    private void handleRuntimeStopping(ServerStoppingEvent event) {
        var server = event.getServer();
        var slot = slots.get(server);
        if (slot == null) {
            return;
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        stopSlot(slot);
    }

    int enterStoppingForTesting(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        var slot = slots.get(server);
        if (slot == null) {
            return 0;
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        return enterStopping(server, slot);
    }

    void handleRuntimeStopped(ServerStoppedEvent event) {
        var server = event.getServer();
        var slot = slots.get(server);
        if (slot == null) {
            return;
        }
        if (!server.isSameThread()) {
            throw kernel(RuntimeKernelException.Code.WRONG_THREAD_LIFECYCLE);
        }
        slot.state = ServerSlot.State.STOPPING;
        clearSlotNormal(server, slot);
        slot.state = ServerSlot.State.REMOVED;
        slots.remove(server);
    }

    private void invalidateP9Work(
            MinecraftServer server,
            ServerSlot slot,
            ProjectileClosureReason reason) {
        if (slot.dispatching) {
            throw kernel(RuntimeKernelException.Code.NESTED_DRAIN);
        }
        var scratchCount = 0;
        while (!slot.queue.isEmpty()) {
            var queued = slot.queue.poll();
            if (isP9ExecutionData(queued.executionData())) {
                var instance = slot.instances.get(queued.skillInstanceId());
                removeCommittedEvent(slot, queued);
                if (instance != null) {
                    instance.clearP9AuthenticatedActorWitness();
                }
                maybeRemoveInstance(slot, queued.skillInstanceId());
                if (instance != null) {
                    recordP9Terminal(
                            slot, instance, reason, P9RuntimeCleanupDisposition.RELEASED);
                }
            } else {
                if (scratchCount == slot.cleanupScratch.length) {
                    throw kernel(RuntimeKernelException.Code.BREAKER_SCRATCH_OVERFLOW);
                }
                slot.cleanupScratch[scratchCount++] = queued;
            }
        }
        for (var index = 0; index < scratchCount; index++) {
            slot.queue.add(slot.cleanupScratch[index]);
            slot.cleanupScratch[index] = null;
        }
        var write = 0;
        for (var read = 0; read < slot.deferredCount; read++) {
            var deferred = slot.deferred[read];
            if (isP9ExecutionData(deferred.executionData())) {
                var instance = slot.instances.get(deferred.skillInstanceId());
                removeCommittedEvent(slot, deferred);
                if (instance != null) {
                    instance.clearP9AuthenticatedActorWitness();
                }
                maybeRemoveInstance(slot, deferred.skillInstanceId());
                if (instance != null) {
                    recordP9Terminal(
                            slot, instance, reason, P9RuntimeCleanupDisposition.RELEASED);
                }
            } else {
                slot.deferred[write++] = deferred;
            }
        }
        Arrays.fill(slot.deferred, write, slot.deferredCount, null);
        slot.deferredCount = write;

        closeAllIndexedContinuations(
                server, slot, reason, P9RuntimeCleanupDisposition.RELEASED);
        removeEmptyInstances(slot);
    }

    private void invalidateP9WorkPreservingPrimary(
            MinecraftServer server,
            ServerSlot slot,
            ProjectileClosureReason reason) {
        try {
            invalidateP9Work(server, slot, reason);
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            slot.p9ErrorCleanup.prepare(server);
            throw preserveErrorFault(slot, primary);
        }
    }

    private static void removeEmptyInstances(ServerSlot slot) {
        var iterator = slot.instances.entrySet().iterator();
        while (iterator.hasNext()) {
            var instance = iterator.next().getValue();
            if (instance.committedPending != 0
                    || instance.reservedPending != 0
                    || instance.inFlight
                    || instance.activeProjectileContinuation != null) {
                continue;
            }
            instance.clearP9AuthenticatedActorWitness();
            iterator.remove();
            var attribution = requireAttribution(slot, instance.attribution);
            if (attribution.activeInstances <= 0) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            attribution.activeInstances--;
            if (instance.lease.release()) {
                var removed = slot.leases.remove(instance.lease.reference);
                if (removed != instance.lease) {
                    throw kernel(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
                }
            }
        }
    }

    private void drain(MinecraftServer server, ServerSlot slot) {
        if (slot.dispatching) {
            throw kernel(RuntimeKernelException.Code.NESTED_DRAIN);
        }
        slot.dispatching = true;
        try {
            while (true) {
                if (slot.executionsThisTick == slot.limits.executionsPerServerPerTick()) {
                    reintegrateDeferred(slot);
                    observeDrainStop(
                            slot, RuntimeDrainStopReason.SERVER_EXECUTION_LIMIT_REACHED);
                    return;
                }
                var event = slot.queue.peek();
                if (event == null || event.scheduledRuntimeTick() > slot.runtimeTick) {
                    reintegrateDeferred(slot);
                    return;
                }
                event = slot.queue.poll();
                verifyQueuedIdentity(slot, event);
                var instance = slot.instances.get(event.skillInstanceId());
                if (instance == null) {
                    removeStaleOwnerEvent(slot, event);
                    observeOutcome(slot, new RuntimeExecutionOutcome.OwnerInstanceUnavailable());
                    continue;
                }
                verifyQueuedOwnerIdentity(instance, event);
                if (instance.terminal) {
                    removeCommittedEvent(slot, event);
                    if (isP9ExecutionData(event.executionData())) {
                        closeActiveP9ContinuationAndDiscard(
                                server,
                                slot,
                                instance,
                                ProjectileClosureReason.OWNER_INVALIDATED);
                        instance.clearP9AuthenticatedActorWitness();
                    }
                    maybeRemoveInstance(slot, instance.id);
                    observeOutcome(slot, new RuntimeExecutionOutcome.OwnerInstanceUnavailable());
                    continue;
                }
                if (instance.cancellationRequested) {
                    removeCommittedEvent(slot, event);
                    if (isP9ExecutionData(event.executionData())) {
                        closeActiveP9ContinuationAndDiscard(
                                server,
                                slot,
                                instance,
                                ProjectileClosureReason.OWNER_INVALIDATED);
                        instance.clearP9AuthenticatedActorWitness();
                    }
                    maybeRemoveInstance(slot, instance.id);
                    observeOutcome(slot, new RuntimeExecutionOutcome.Cancelled());
                    continue;
                }
                if (deadlineExpired(slot, event)) {
                    observeExpired(slot, event);
                    var p9DeadlineOwner = isP9ExecutionData(event.executionData())
                            ? instance
                            : null;
                    removeCommittedEvent(slot, event);
                    if (p9DeadlineOwner != null) {
                        closeActiveP9ContinuationAndDiscard(
                                server,
                                slot,
                                p9DeadlineOwner,
                                ProjectileClosureReason.DEADLINE_REACHED);
                        p9DeadlineOwner.clearP9AuthenticatedActorWitness();
                    }
                    maybeRemoveInstance(slot, instance.id);
                    if (p9DeadlineOwner != null) {
                        recordP9Terminal(
                                slot,
                                p9DeadlineOwner,
                                ProjectileClosureReason.DEADLINE_REACHED,
                                P9RuntimeCleanupDisposition.RELEASED);
                    }
                    observeOutcome(
                            slot,
                            new RuntimeExecutionOutcome.DeadlineExpired(
                                    event.deadlineRuntimeTick(), slot.runtimeTick));
                    continue;
                }
                var attribution = requireAttribution(slot, instance.attribution);
                resetExecutionEpoch(instance, slot.runtimeTick);
                resetExecutionEpoch(attribution, slot.runtimeTick);
                var decision = executionDecision(slot, instance, attribution);
                if (decision != RuntimeBudgetDecision.EXECUTE) {
                    defer(slot, event, instance, attribution, decision);
                    continue;
                }
                claim(slot, event, instance, attribution);
                dispatchClaimed(server, slot, instance, attribution, event);
                if (slot.state != ServerSlot.State.RUNNING) {
                    reintegrateDeferred(slot);
                    return;
                }
            }
        } catch (RuntimeException primary) {
            throw preserveRuntimeFault(slot, primary);
        } catch (Error primary) {
            slot.p9ErrorCleanup.prepare(server);
            throw preserveErrorFault(slot, primary);
        } finally {
            slot.dispatching = false;
            if (slot.state == ServerSlot.State.RUNNING) {
                reintegrateDeferred(slot);
            }
        }
    }

    private void dispatchClaimed(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event) {
        RuntimeExecutionOutcome outcome;
        var lease = instance.lease;
        if (lease.pin.isClosed()) {
            outcome = new RuntimeExecutionOutcome.SkillRevisionUnavailable();
        } else {
            if (event.executionData() instanceof CastGeometryExecutionDataV0
                    && event.nodeIndex() == 0
                    && P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                            lease.definition)) {
                recordP9Stage(slot, instance, P9RuntimeDiagnosticStage.NODE0_MATCHED);
            }
            var invocation = invokeRuntimeBoundary(
                    server, slot, instance, attribution, event, lease);
            if (invocation instanceof AbortedInvocation) {
                return;
            }
            outcome = switch (invocation) {
                case TerminalInvocation terminal -> terminal.outcome();
                case PortInvocation returned -> finishPort(
                        server, slot, instance, attribution, event, returned);
                case NullBatchInvocation returned -> finishPort(
                        server, slot, instance, attribution, event, returned);
                case AbortedInvocation ignored ->
                        throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            };
        }
        if (slot.state != ServerSlot.State.RUNNING) {
            return;
        }
        if (slot.currentEvent != event) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        if (event.executionData() instanceof ProjectileHitExecutionDataV0) {
            closeActiveP9ContinuationAndDiscard(
                    server, slot, instance, ProjectileClosureReason.DAMAGE_TERMINAL);
        }
        if (isP9ExecutionData(event.executionData())
                && instance.activeProjectileContinuation == null) {
            var terminalReason = terminalReasonForOutcome(outcome);
            var breakerPlayer = playerId(instance.attribution);
            instance.clearP9AuthenticatedActorWitness();
            terminalizeCurrent(slot, instance, event);
            recordP9Terminal(
                    slot, instance, terminalReason, P9RuntimeCleanupDisposition.RELEASED);
            observeOutcome(slot, outcome);
            if (outcome instanceof RuntimeExecutionOutcome.CircuitBroken broken) {
                observeBreaker(
                        slot,
                        instance.id,
                        Optional.of(instance.sequence),
                        Optional.of(event.eventId()),
                        breakerPlayer,
                        broken.summary());
            }
            return;
        }
        var breakerPlayer = playerId(instance.attribution);
        terminalizeCurrent(slot, instance, event);
        observeOutcome(slot, outcome);
        if (outcome instanceof RuntimeExecutionOutcome.CircuitBroken broken) {
            observeBreaker(
                    slot,
                    instance.id,
                    Optional.of(instance.sequence),
                    Optional.of(event.eventId()),
                    breakerPlayer,
                    broken.summary());
        }
    }

    private DetachedInvocation invokeRuntimeBoundary(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event,
            RuntimeRevisionLease lease) {
        RuntimeReferenceResolutionOutcome resolution = null;
        ResolvedRuntimeReferenceContext resolvedReferences = null;
        RuntimeExecutionContext context = null;
        try {
            resolution = referenceResolver.resolve(server, event);
            if (slot.state != ServerSlot.State.RUNNING) {
                return AbortedInvocation.INSTANCE;
            }
            if (!server.isRunning() || server.isStopped()) {
                stopSlot(slot);
                return AbortedInvocation.INSTANCE;
            }
            if (instance.cancellationRequested) {
                return new TerminalInvocation(
                        new RuntimeExecutionOutcome.Cancelled());
            }
            if (p9ReloadCloseRequested.get() && isP9ExecutionData(event.executionData())) {
                return new TerminalInvocation(
                        new RuntimeExecutionOutcome.Cancelled());
            }
            if (!(resolution instanceof RuntimeReferenceResolutionOutcome.Resolved)) {
                return new TerminalInvocation(referenceFailureOutcome(resolution));
            }
            resolvedReferences = ((RuntimeReferenceResolutionOutcome.Resolved) resolution).context();
            ServerPlayer guardedP9Actor = null;
            ResourceLocation guardedP9Dimension = null;
            if (event.executionData() instanceof CastGeometryExecutionDataV0 geometry) {
                if (!(resolvedReferences.origin() instanceof ResolvedPlayerOrigin playerOrigin)
                        || !isCurrentP9AuthenticatedActor(
                                server,
                                instance,
                                playerOrigin.player(),
                                geometry.dimension())) {
                    return new TerminalInvocation(
                            new RuntimeExecutionOutcome.OwnerInstanceUnavailable());
                }
                guardedP9Actor = playerOrigin.player();
                guardedP9Dimension = geometry.dimension();
            } else if (event.executionData() instanceof ProjectileHitExecutionDataV0
                    && (!validClaimedP9Child(server, slot, instance, event)
                            || !(resolvedReferences.origin()
                                    instanceof ResolvedPlayerOrigin playerOrigin)
                            || !(resolvedReferences.target()
                                    instanceof ResolvedEntityTarget entityTarget)
                            || playerOrigin.player() != currentP9ChildActor(server, event)
                            || entityTarget.entity() != currentP9ChildTarget(server, event))) {
                return new TerminalInvocation(
                        new RuntimeExecutionOutcome.OwnerInstanceUnavailable());
            }
            var reservation = reserveForPort(slot, instance, attribution, event);
            var node = lease.definition.nodes().get(event.nodeIndex());
            var executionGuard = new RuntimeExecutionGuardState(
                    this,
                    server,
                    slot,
                    instance,
                    event,
                    guardedP9Actor,
                    guardedP9Dimension);
            context = new RuntimeExecutionContext(
                    server,
                    lease.definition,
                    node,
                    slot.runtimeTick,
                    slot.token,
                    resolvedReferences,
                    reservation.budget(),
                    executionGuard,
                    new RuntimeProjectileContinuationOpener(
                            this, server, slot, event, reservation));
            slot.diagnostics.portInvocationsThisTick++;
            var batch = executionPort.execute(event, context);
            return batch == null
                    ? new NullBatchInvocation(reservation)
                    : new PortInvocation(reservation, batch);
        } finally {
            context = null;
            resolvedReferences = null;
            resolution = null;
        }
    }

    private RuntimeExecutionOutcome finishPort(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event,
            DetachedInvocation invocation) {
        var reservation = switch (invocation) {
            case PortInvocation returned -> returned.reservation();
            case NullBatchInvocation returned -> returned.reservation();
            case AbortedInvocation ignored ->
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            case TerminalInvocation ignored ->
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        };
        if (slot.state != ServerSlot.State.RUNNING) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.ServerStopping();
        }
        if (!server.isRunning() || server.isStopped()) {
            stopSlot(slot);
            return new RuntimeExecutionOutcome.ServerStopping();
        }
        if (instance.cancellationRequested) {
            closeDetachedContinuation(
                    server, reservation, ProjectileClosureReason.OWNER_INVALIDATED);
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.Cancelled();
        }
        if (p9ReloadCloseRequested.get() && isP9ExecutionData(event.executionData())) {
            closeDetachedContinuation(
                    server, reservation, ProjectileClosureReason.RELOAD_INVALIDATED);
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.Cancelled();
        }
        if (invocation instanceof NullBatchInvocation) {
            throw kernel(RuntimeKernelException.Code.NULL_EXECUTION_BATCH);
        }
        var batch = ((PortInvocation) invocation).batch();
        if (batch.outcome() instanceof RuntimePortOutcome.Rejected rejected) {
            closeDetachedContinuation(
                    server, reservation, ProjectileClosureReason.RUNTIME_FAULT);
            releaseCurrentReservation(slot, instance, attribution);
            return portRejectionOutcome(rejected);
        }
        return processCompletedPlan(
                server, slot, instance, attribution, event, reservation, batch.children());
    }

    static RuntimeExecutionOutcome referenceFailureOutcome(
            RuntimeReferenceResolutionOutcome resolution) {
        Objects.requireNonNull(resolution, "resolution");
        return switch (resolution) {
            case RuntimeReferenceResolutionOutcome.SourceMissing missing ->
                    new RuntimeExecutionOutcome.SourceMissing(missing.reason());
            case RuntimeReferenceResolutionOutcome.TargetMissing missing ->
                    new RuntimeExecutionOutcome.TargetMissing(missing.reason());
            case RuntimeReferenceResolutionOutcome.InvalidRuntimeReference invalid ->
                    new RuntimeExecutionOutcome.InvalidRuntimeReference(invalid.reason());
            case RuntimeReferenceResolutionOutcome.Resolved ignored ->
                    throw new IllegalArgumentException("resolved reference has no failure outcome");
        };
    }

    static RuntimeExecutionOutcome portRejectionOutcome(RuntimePortOutcome.Rejected rejection) {
        Objects.requireNonNull(rejection, "rejection");
        return new RuntimeExecutionOutcome.RejectedByExecutionPort(rejection.reason());
    }

    private static void closeDetachedContinuation(
            MinecraftServer server,
            ChildReservation reservation,
            ProjectileClosureReason reason) {
        var permit = reservation.detachedPermit();
        if (permit == null) {
            return;
        }
        var disposition = permit.closeWithoutHit(server, reason);
        if (disposition != RuntimePermitCloseDisposition.CLOSED
                && disposition != RuntimePermitCloseDisposition.ALREADY_CLOSED) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
    }

    private RuntimeExecutionOutcome processCompletedPlan(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent parent,
            ChildReservation reservation,
            RuntimeChildPlan plan) {
        if (reservation.detachedPermit() != null && !plan.children().isEmpty()) {
            closeDetachedContinuation(
                    server, reservation, ProjectileClosureReason.RUNTIME_FAULT);
            releaseCurrentReservation(slot, instance, attribution);
            throw kernel(RuntimeKernelException.Code.INVALID_CHILD_PLAN_INVARIANT);
        }
        var children = canonicalize(plan.children());
        var childCount = children.size();
        if (childCount == 0) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.Completed();
        }
        if (childCount > slot.limits.directChildrenPerEvent()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.DIRECT_CHILD_LIMIT_EXCEEDED);
        }
        var zeroDelayCount = 0;
        for (var child : children) {
            if (child.delayTicks() == 0) {
                zeroDelayCount++;
            }
        }
        if (zeroDelayCount > slot.limits.zeroDelayChildrenPerEvent()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.ZERO_DELAY_CHILD_LIMIT_EXCEEDED);
        }
        for (var child : children) {
            if (!stableTokensMatch(slot.token, child.origin(), child.target())) {
                releaseCurrentReservation(slot, instance, attribution);
                throw kernel(RuntimeKernelException.Code.INVALID_CHILD_PLAN_INVARIANT);
            }
        }
        for (var child : children) {
            var structural = validateChildShape(
                    instance.lease.definition, instance.attribution, child);
            if (structural.isPresent()) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.InvalidEvent(structural.orElseThrow());
            }
        }
        if (parent.depth() == slot.limits.maximumDepth()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.DEPTH_LIMIT_EXCEEDED);
        }
        for (var child : children) {
            if (child.delayTicks() < 0
                    || child.delayTicks() > slot.limits.maximumDelayTicks()) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DELAY_OUT_OF_RANGE);
            }
        }
        for (var child : children) {
            if (child.deadlineHorizonTicks() < 0
                    || child.deadlineHorizonTicks()
                            > slot.limits.maximumDeadlineHorizonTicks()) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DEADLINE_OUT_OF_RANGE);
            }
        }
        var scheduledTicks = new long[childCount];
        for (var index = 0; index < childCount; index++) {
            try {
                scheduledTicks[index] = Math.addExact(
                        slot.runtimeTick, children.get(index).delayTicks());
            } catch (ArithmeticException ignored) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DELAY_OVERFLOW);
            }
        }
        var requestedDeadlines = new long[childCount];
        for (var index = 0; index < childCount; index++) {
            try {
                requestedDeadlines[index] = Math.addExact(
                        slot.runtimeTick, children.get(index).deadlineHorizonTicks());
            } catch (ArithmeticException ignored) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DEADLINE_OVERFLOW);
            }
        }
        for (var index = 0; index < childCount; index++) {
            if (scheduledTicks[index]
                    > Math.min(parent.deadlineRuntimeTick(), requestedDeadlines[index])) {
                releaseCurrentReservation(slot, instance, attribution);
                return new RuntimeExecutionOutcome.ScheduleRejected(
                        RuntimeScheduleRejectionReason.DEADLINE_BEFORE_SCHEDULED_TICK);
            }
        }
        if (instance.lifetimeEvents + childCount > slot.limits.eventsPerSkillInstance()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.LINEAGE_EVENT_LIMIT_EXCEEDED);
        }

        var pendingBreak = pendingBreak(slot, instance, attribution, childCount);
        if (pendingBreak != null) {
            releaseCurrentReservation(slot, instance, attribution);
            var selection = pendingBreak;
            instance.terminal = true;
            var removed = removeInstanceQueuedAndDeferred(slot, instance.id);
            var summary = new RuntimeCircuitBreakerSummary(
                    selection.reason,
                    selection.pendingBefore,
                    childCount,
                    selection.maximum,
                    removed,
                    true);
            return new RuntimeExecutionOutcome.CircuitBroken(summary);
        }
        if (childCount > reservation.capacity()) {
            releaseCurrentReservation(slot, instance, attribution);
            return new RuntimeExecutionOutcome.BudgetRejected(
                    RuntimeBudgetRejectionReason.EVENT_SEQUENCE_CAPACITY_EXCEEDED);
        }

        var published = new RuntimeEvent[childCount];
        for (var index = 0; index < childCount; index++) {
            var child = children.get(index);
            published[index] = new RuntimeEvent(
                    new EventId(Math.addExact(reservation.eventIdStart(), index + 1L)),
                    instance.id,
                    instance.sequence,
                    new RuntimeCancellationToken(slot.token, instance.id),
                    Optional.of(parent.eventId()),
                    parent.skillReference(),
                    child.nodeIndex(),
                    slot.runtimeTick,
                    scheduledTicks[index],
                    Math.min(parent.deadlineRuntimeTick(), requestedDeadlines[index]),
                    parent.depth() + 1,
                    index + 1,
                    parent.persistence(),
                    parent.budgetAttribution(),
                    child.origin(),
                    child.target(),
                    child.triggerCause(),
                    child.executionData());
        }
        for (var child : published) {
            convertReservedChildToCommitted(slot, instance, attribution, child);
        }
        releaseCurrentReservation(slot, instance, attribution);
        instance.lifetimeEvents += childCount;
        return new RuntimeExecutionOutcome.CompletedWithChildren(childCount);
    }

    private ChildReservation reserveForPort(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event) {
        var remainingLineage = Math.subtractExact(
                slot.limits.eventsPerSkillInstance(),
                Math.addExact(instance.lifetimeEvents, instance.reservedPending));
        if (remainingLineage < 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        var remainingDepth = slot.limits.maximumDepth() - event.depth();
        var instanceHeadroom = slot.limits.pendingEventsPerSkillInstance()
                - instance.committedPending - instance.reservedPending;
        var attributionHeadroom = slot.limits.pendingEventsPerAttribution()
                - attribution.committedPending - attribution.reservedPending;
        var serverHeadroom = slot.limits.pendingEventsPerServer()
                - slot.committedPending - slot.reservedPending;
        var eventIdHeadroom = Long.MAX_VALUE - slot.eventSequenceHighWater;
        var capacity = minimum(
                slot.limits.directChildrenPerEvent(),
                remainingLineage,
                remainingDepth == 0 ? 0 : RuntimeChildPlan.PHYSICAL_MAXIMUM,
                instanceHeadroom,
                attributionHeadroom,
                serverHeadroom,
                eventIdHeadroom > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) eventIdHeadroom);
        var zeroCapacity = Math.min(slot.limits.zeroDelayChildrenPerEvent(), capacity);
        var budget = new RuntimeExecutionBudget(
                capacity,
                zeroCapacity,
                remainingLineage,
                remainingDepth,
                slot.limits.maximumDelayTicks(),
                slot.limits.maximumDeadlineHorizonTicks(),
                instanceHeadroom,
                attributionHeadroom,
                serverHeadroom);
        var eventIdStart = slot.eventSequenceHighWater;
        if (capacity > 0) {
            slot.eventSequenceHighWater = Math.addExact(slot.eventSequenceHighWater, capacity);
            instance.reservedPending += capacity;
            attribution.reservedPending += capacity;
            slot.reservedPending += capacity;
            slot.currentReservationCount = capacity;
            slot.currentReservationOwner = instance.id;
        }
        return new ChildReservation(
                capacity,
                eventIdStart,
                budget);
    }

    private void releaseCurrentReservation(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        var count = slot.currentReservationCount;
        if (count == 0) {
            return;
        }
        if (!instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending < count
                || attribution.reservedPending < count
                || slot.reservedPending < count) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.reservedPending -= count;
        attribution.reservedPending -= count;
        slot.reservedPending -= count;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
    }

    private PendingBreak pendingBreak(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            int requested) {
        return selectPendingBreak(
                slot.limits,
                instance.committedPending,
                attribution.committedPending,
                slot.committedPending,
                instance.attribution,
                requested);
    }

    PendingBreak selectPendingBreak(
            P5RuntimeLimits limits,
            int instancePending,
            int attributionPending,
            int serverPending,
            RuntimeBudgetAttribution attribution,
            int requested) {
        return pendingBreak(
                limits,
                instancePending,
                attributionPending,
                serverPending,
                attribution,
                requested);
    }

    static PendingBreak pendingBreak(
            P5RuntimeLimits limits,
            int instancePending,
            int attributionPending,
            int serverPending,
            RuntimeBudgetAttribution attribution,
            int requested) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(attribution, "attribution");
        if (instancePending < 0
                || instancePending > limits.pendingEventsPerSkillInstance()
                || attributionPending < 0
                || attributionPending > limits.pendingEventsPerAttribution()
                || serverPending < 0
                || serverPending > limits.pendingEventsPerServer()
                || requested <= 0
                || requested > RuntimeChildPlan.PHYSICAL_MAXIMUM) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (instancePending + requested > limits.pendingEventsPerSkillInstance()) {
            return new PendingBreak(
                    RuntimeCircuitBreakReason.SKILL_INSTANCE_PENDING_EVENTS_EXCEEDED,
                    instancePending,
                    limits.pendingEventsPerSkillInstance());
        }
        if (attributionPending + requested > limits.pendingEventsPerAttribution()) {
            return new PendingBreak(
                    attribution instanceof PlayerRuntimeBudgetAttribution
                            ? RuntimeCircuitBreakReason.PLAYER_PENDING_EVENTS_EXCEEDED
                            : RuntimeCircuitBreakReason.NON_PLAYER_DOMAIN_PENDING_EVENTS_EXCEEDED,
                    attributionPending,
                    limits.pendingEventsPerAttribution());
        }
        if (serverPending + requested > limits.pendingEventsPerServer()) {
            return new PendingBreak(
                    RuntimeCircuitBreakReason.SERVER_PENDING_EVENTS_EXCEEDED,
                    serverPending,
                    limits.pendingEventsPerServer());
        }
        return null;
    }

    private static int compareEvents(RuntimeEvent left, RuntimeEvent right) {
        var compared = Long.compare(left.scheduledRuntimeTick(), right.scheduledRuntimeTick());
        if (compared != 0) {
            return compared;
        }
        compared = Long.compare(left.eventId().value(), right.eventId().value());
        if (compared != 0) {
            return compared;
        }
        compared = Long.compare(
                left.skillInstanceSequence().value(), right.skillInstanceSequence().value());
        if (compared != 0) {
            return compared;
        }
        compared = Integer.compare(left.nodeIndex(), right.nodeIndex());
        return compared != 0
                ? compared
                : Integer.compare(left.childSequence(), right.childSequence());
    }

    static List<RuntimeChildSpec> canonicalize(List<RuntimeChildSpec> children) {
        record IndexedChild(RuntimeChildSpec child, int originalOrdinal) {}
        var indexed = new java.util.ArrayList<IndexedChild>(children.size());
        for (var index = 0; index < children.size(); index++) {
            indexed.add(new IndexedChild(children.get(index), index));
        }
        indexed.sort((left, right) -> {
            var compared = Integer.compare(left.child().delayTicks(), right.child().delayTicks());
            if (compared != 0) {
                return compared;
            }
            compared = Integer.compare(left.child().nodeIndex(), right.child().nodeIndex());
            return compared != 0
                    ? compared
                    : Integer.compare(left.originalOrdinal(), right.originalOrdinal());
        });
        var ordered = new java.util.ArrayList<RuntimeChildSpec>(children.size());
        for (var value : indexed) {
            ordered.add(value.child());
        }
        return List.copyOf(ordered);
    }

    private LeaseAcquisition acquireLease(
            MinecraftServer server, ServerSlot slot, SkillReference reference) {
        var existing = slot.leases.get(reference);
        if (existing != null) {
            return new LeaseAcquisition.Available(existing, false);
        }
        var found = storeService.find(server, reference);
        if (found instanceof SkillSubsystemResult.Unavailable<Optional<SkillDocument>> unavailable) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.DefinitionSubsystemUnavailable(
                            unavailable.reason()));
        }
        var document = ((SkillSubsystemResult.Available<Optional<SkillDocument>>) found).value();
        if (document.isEmpty()) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.ExactRevisionMissing());
        }
        var context = policyProvider.snapshot(server).validationContext();
        var projection = projector.project(reference, document.orElseThrow(), context);
        if (projection instanceof P5RuntimeProjector.Projection.Unavailable) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.RuntimeProjectionUnavailable());
        }
        var definition = ((P5RuntimeProjector.Projection.Available) projection).definition();
        var pinned = storeService.pin(server, reference);
        if (pinned instanceof SkillSubsystemResult.Unavailable<Optional<ControlledSkillPin>> unavailable) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.DefinitionSubsystemUnavailable(
                            unavailable.reason()));
        }
        var pin = ((SkillSubsystemResult.Available<Optional<ControlledSkillPin>>) pinned).value();
        if (pin.isEmpty()) {
            return new LeaseAcquisition.Unavailable(
                    new SkillRevisionUnavailableReason.TransientPinUnavailable());
        }
        var exactPin = pin.orElseThrow();
        try {
            var lease = new RuntimeRevisionLease(reference, exactPin, definition);
            return new LeaseAcquisition.Available(lease, true);
        } catch (RuntimeException primary) {
            closePinAfterLeaseConstructionFailure(exactPin);
            throw primary;
        } catch (Error primary) {
            closePinAfterLeaseConstructionFailure(exactPin);
            throw primary;
        }
    }

    private static void closePinAfterLeaseConstructionFailure(ControlledSkillPin pin) {
        try {
            pin.close();
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // The lease-construction primary remains authoritative.
        }
    }

    private static void publishRoot(
            ServerSlot slot,
            RuntimeEvent event,
            RuntimeRevisionLease lease,
            LeaseAcquisition acquisition,
            ServerSlot.AttributionState existingAttribution,
            ServerSlot.InstanceState instance) {
        if (acquisition instanceof LeaseAcquisition.Available available && available.provisional()) {
            slot.leases.put(lease.reference, lease);
        }
        lease.retain();
        var attribution = existingAttribution;
        if (attribution == null) {
            if (slot.attributions.size() == slot.limits.runtimeBudgetAttributionStatesPerServer()) {
                throw kernel(RuntimeKernelException.Code.ATTRIBUTION_STATE_CAPACITY_INVARIANT);
            }
            attribution = new ServerSlot.AttributionState(event.budgetAttribution());
            slot.attributions.put(event.budgetAttribution(), attribution);
        }
        attribution.activeInstances++;
        var publicationCompleted = false;
        try {
            slot.instances.put(instance.id, instance);
            slot.skillInstanceSequenceHighWater = instance.sequence.value();
            slot.eventSequenceHighWater = event.eventId().value();
            addCommittedEvent(slot, instance, attribution, event);
            instance.lifetimeEvents = 1;
            if (event.executionData() instanceof CastGeometryExecutionDataV0 geometry) {
                instance.p9Diagnostic = new ServerSlot.P9ActiveDiagnostic(event, geometry);
                recordP9Stage(slot, instance, P9RuntimeDiagnosticStage.CAST_ACCEPTED);
                recordP9Stage(slot, instance, P9RuntimeDiagnosticStage.INSTANCE_PINNED);
            }
            publicationCompleted = true;
        } finally {
            if (!publicationCompleted) {
                instance.clearP9AuthenticatedActorWitness();
            }
        }
    }

    private static void releaseProvisionalLease(
            ServerSlot slot, LeaseAcquisition acquisition) {
        if (acquisition instanceof LeaseAcquisition.Available available
                && available.provisional()) {
            available.lease().close();
        }
    }

    private static void closeProvisionalAfterRootFault(
            LeaseAcquisition acquisition) {
        if (acquisition instanceof LeaseAcquisition.Available available
                && available.provisional()) {
            try {
                available.lease().close();
            } catch (RuntimeException | Error ignoredCleanupFailure) {
                // Publication's already-caught primary remains authoritative.
            }
        }
    }

    private static RuntimeCancellationResult cancelInstance(
            MinecraftServer server, ServerSlot slot, SkillInstanceId instanceId) {
        var instance = slot.instances.get(instanceId);
        if (instance == null || instance.terminal) {
            return new RuntimeCancellationResult.NotPending();
        }
        if (instance.cancellationRequested) {
            return new RuntimeCancellationResult.AlreadyCancelled();
        }
        instance.cancellationRequested = true;
        var removedContinuationWork = 0;
        if (instance.activeProjectileContinuation != null) {
            var permit = instance.activeProjectileContinuation;
            var projectile = loadedProjectile(server, permit);
            var disposition = permit.closeWithoutHit(
                    server, ProjectileClosureReason.OWNER_INVALIDATED);
            if (disposition != RuntimePermitCloseDisposition.CLOSED) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            removedContinuationWork = 1;
            if (projectile != null && !projectile.isRemoved()) {
                projectile.discard();
            }
            if (slot.instances.get(instanceId) != instance) {
                return new RuntimeCancellationResult.CancelledSkillInstance(
                        removedContinuationWork);
            }
        }
        var inFlight = slot.currentEvent != null
                && slot.currentEvent.skillInstanceId().equals(instanceId);
        var attribution = requireAttribution(slot, instance.attribution);
        if (inFlight) {
            releaseCurrentReservationStatic(slot, instance, attribution);
        }
        var removed = removeInstanceQueuedAndDeferred(slot, instanceId);
        if (inFlight) {
            return new RuntimeCancellationResult.CancellationRequested(
                    Math.addExact(removed, removedContinuationWork));
        }
        removed = Math.addExact(removed, removedContinuationWork);
        instance.terminal = true;
        instance.clearP9AuthenticatedActorWitness();
        maybeRemoveInstance(slot, instanceId);
        recordP9Terminal(
                slot,
                instance,
                ProjectileClosureReason.OWNER_INVALIDATED,
                P9RuntimeCleanupDisposition.RELEASED);
        return new RuntimeCancellationResult.CancelledSkillInstance(removed);
    }

    private static int removeInstanceQueuedAndDeferred(
            ServerSlot slot, SkillInstanceId instanceId) {
        var scratchCount = 0;
        var removed = 0;
        while (!slot.queue.isEmpty()) {
            var event = slot.queue.poll();
            if (event.skillInstanceId().equals(instanceId)) {
                removeCommittedEvent(slot, event);
                removed++;
            } else {
                if (scratchCount == slot.cleanupScratch.length) {
                    throw kernel(RuntimeKernelException.Code.BREAKER_SCRATCH_OVERFLOW);
                }
                slot.cleanupScratch[scratchCount++] = event;
            }
        }
        for (var index = 0; index < scratchCount; index++) {
            slot.queue.add(slot.cleanupScratch[index]);
            slot.cleanupScratch[index] = null;
        }
        var write = 0;
        for (var read = 0; read < slot.deferredCount; read++) {
            var event = slot.deferred[read];
            if (event.skillInstanceId().equals(instanceId)) {
                removeCommittedEvent(slot, event);
                removed++;
            } else {
                slot.deferred[write++] = event;
            }
        }
        Arrays.fill(slot.deferred, write, slot.deferredCount, null);
        slot.deferredCount = write;
        return removed;
    }

    static boolean removeExactQueuedOrDeferred(ServerSlot slot, RuntimeEvent event) {
        if (slot.queue.remove(event)) {
            return true;
        }
        for (var index = 0; index < slot.deferredCount; index++) {
            if (slot.deferred[index] == event) {
                var move = slot.deferredCount - index - 1;
                if (move > 0) {
                    System.arraycopy(slot.deferred, index + 1, slot.deferred, index, move);
                }
                slot.deferred[--slot.deferredCount] = null;
                return true;
            }
        }
        return false;
    }

    private static void addCommittedEvent(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent event) {
        if (slot.committedPending == MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        slot.queue.add(event);
        var previous = slot.eventIndex.put(event.eventId(), event);
        if (previous != null) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        instance.committedPending++;
        attribution.committedPending++;
        slot.committedPending++;
    }

    private static void convertReservedChildToCommitted(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeEvent child) {
        if (slot.currentReservationCount <= 0
                || !instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending <= 0
                || attribution.reservedPending <= 0
                || slot.reservedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.reservedPending--;
        attribution.reservedPending--;
        slot.reservedPending--;
        slot.currentReservationCount--;
        if (slot.currentReservationCount == 0) {
            slot.currentReservationOwner = null;
        }
        addCommittedEvent(slot, instance, attribution, child);
    }

    private static void removeCommittedEvent(ServerSlot slot, RuntimeEvent event) {
        var indexed = slot.eventIndex.remove(event.eventId());
        if (indexed != event) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var instance = slot.instances.get(event.skillInstanceId());
        if (instance == null || instance.committedPending <= 0 || slot.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        var attribution = requireAttribution(slot, instance.attribution);
        if (attribution.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.committedPending--;
        attribution.committedPending--;
        slot.committedPending--;
    }

    private static void removeStaleOwnerEvent(ServerSlot slot, RuntimeEvent event) {
        var indexed = slot.eventIndex.remove(event.eventId());
        if (indexed != event || slot.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var attribution = requireAttribution(slot, event.budgetAttribution());
        if (attribution.committedPending <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        attribution.committedPending--;
        slot.committedPending--;
    }

    private RuntimeBudgetDecision executionDecision(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        return decideExecution(
                slot.limits,
                instance.executionsThisTick,
                attribution.executionsThisTick,
                instance.attribution);
    }

    RuntimeBudgetDecision decideExecution(
            P5RuntimeLimits limits,
            int instanceExecutions,
            int attributionExecutions,
            RuntimeBudgetAttribution attribution) {
        return executionDecision(
                limits, instanceExecutions, attributionExecutions, attribution);
    }

    static RuntimeBudgetDecision executionDecision(
            P5RuntimeLimits limits,
            int instanceExecutions,
            int attributionExecutions,
            RuntimeBudgetAttribution attribution) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(attribution, "attribution");
        if (instanceExecutions < 0
                || instanceExecutions > limits.executionsPerSkillInstancePerTick()
                || attributionExecutions < 0
                || attributionExecutions > limits.executionsPerAttributionPerTick()) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (instanceExecutions == limits.executionsPerSkillInstancePerTick()) {
            return RuntimeBudgetDecision.DEFER_SKILL_INSTANCE_TICK_LIMIT;
        }
        if (attributionExecutions == limits.executionsPerAttributionPerTick()) {
            return attribution instanceof PlayerRuntimeBudgetAttribution
                    ? RuntimeBudgetDecision.DEFER_PLAYER_TICK_LIMIT
                    : RuntimeBudgetDecision.DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT;
        }
        return RuntimeBudgetDecision.EXECUTE;
    }

    private static void claim(
            ServerSlot slot,
            RuntimeEvent event,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        slot.currentEvent = event;
        instance.inFlight = true;
        instance.executionsThisTick++;
        attribution.executionsThisTick++;
        slot.executionsThisTick++;
        slot.diagnostics.executionAttemptsThisTick++;
        var lag = schedulingLag(slot.runtimeTick, event);
        slot.diagnostics.maximumLagTicksThisTick = Math.max(
                slot.diagnostics.maximumLagTicksThisTick, lag);
        if (attribution.smallestContributingSequenceThisTick == null
                || instance.sequence.value()
                        < attribution.smallestContributingSequenceThisTick.value()) {
            attribution.smallestContributingSequenceThisTick = instance.sequence;
        }
        slot.diagnostics.observeInstanceOffender(instance);
        slot.diagnostics.observeAttributionOffender(attribution);
    }

    private static void terminalizeCurrent(
            ServerSlot slot, ServerSlot.InstanceState instance, RuntimeEvent event) {
        if (slot.currentEvent != event || !instance.inFlight) {
            throw kernel(RuntimeKernelException.Code.EVENT_INDEX_INVARIANT);
        }
        var attribution = requireAttribution(slot, instance.attribution);
        releaseCurrentReservationStatic(slot, instance, attribution);
        removeCommittedEvent(slot, event);
        instance.inFlight = false;
        slot.currentEvent = null;
        maybeRemoveInstance(slot, instance.id);
    }

    private static void releaseCurrentReservationStatic(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution) {
        var count = slot.currentReservationCount;
        if (count == 0) {
            return;
        }
        if (!instance.id.equals(slot.currentReservationOwner)
                || instance.reservedPending < count
                || attribution.reservedPending < count
                || slot.reservedPending < count) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        instance.reservedPending -= count;
        attribution.reservedPending -= count;
        slot.reservedPending -= count;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
    }

    private static void maybeRemoveInstance(ServerSlot slot, SkillInstanceId instanceId) {
        var instance = slot.instances.get(instanceId);
        if (instance == null
                || instance.committedPending != 0
                || instance.reservedPending != 0
                || instance.inFlight
                || instance.activeProjectileContinuation != null) {
            return;
        }
        instance.clearP9AuthenticatedActorWitness();
        slot.instances.remove(instanceId);
        var attribution = requireAttribution(slot, instance.attribution);
        if (attribution.activeInstances <= 0) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        attribution.activeInstances--;
        if (instance.lease.release()) {
            var removed = slot.leases.remove(instance.lease.reference);
            if (removed != instance.lease) {
                throw kernel(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
            }
        }
    }

    static void verifyQueuedIdentity(ServerSlot slot, RuntimeEvent event) {
        if (slot.eventIndex.get(event.eventId()) != event
                || !slot.token.equals(event.cancellationToken().serverSlotToken())
                || !event.skillInstanceId().equals(event.cancellationToken().skillInstanceId())) {
            throw kernel(RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
    }

    private static void verifyQueuedOwnerIdentity(
            ServerSlot.InstanceState instance, RuntimeEvent event) {
        if (!instance.id.equals(event.skillInstanceId())
                || !instance.sequence.equals(event.skillInstanceSequence())
                || !instance.attribution.equals(event.budgetAttribution())
                || !instance.lease.reference.equals(event.skillReference())) {
            throw kernel(RuntimeKernelException.Code.QUEUED_EVENT_IDENTITY_INVARIANT);
        }
    }

    private static void defer(
            ServerSlot slot,
            RuntimeEvent event,
            ServerSlot.InstanceState instance,
            ServerSlot.AttributionState attribution,
            RuntimeBudgetDecision decision) {
        if (slot.deferredCount == slot.deferred.length) {
            throw kernel(RuntimeKernelException.Code.DEFERRED_BUFFER_OVERFLOW);
        }
        slot.deferred[slot.deferredCount++] = event;
        var lag = schedulingLag(slot.runtimeTick, event);
        slot.diagnostics.maximumLagTicksThisTick = Math.max(
                slot.diagnostics.maximumLagTicksThisTick, lag);
        switch (decision) {
            case EXECUTE -> throw new IllegalArgumentException("execute cannot be deferred");
            case DEFER_SKILL_INSTANCE_TICK_LIMIT -> {
                slot.diagnostics.instanceDeferralsThisTick++;
            }
            case DEFER_PLAYER_TICK_LIMIT -> {
                slot.diagnostics.playerDeferralsThisTick++;
            }
            case DEFER_NON_PLAYER_DOMAIN_TICK_LIMIT -> {
                slot.diagnostics.nonPlayerDeferralsThisTick++;
            }
        }
    }

    static void finishDeferred(ServerSlot slot) {
        for (var index = 0; index < slot.deferredCount; index++) {
            var event = slot.deferred[index];
            if (event != null) {
                slot.queue.add(event);
                slot.deferred[index] = null;
            }
        }
        slot.deferredCount = 0;
    }

    void reintegrateDeferred(ServerSlot slot) {
        finishDeferred(Objects.requireNonNull(slot, "slot"));
    }

    static void resetTickState(ServerSlot slot) {
        slot.rootAdmissionsThisTick = 0;
        slot.executionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.diagnostics.resetCurrentTick();
        slot.attributions.entrySet().removeIf(entry -> {
            var state = entry.getValue();
            return state.activeInstances == 0
                    && state.committedPending == 0
                    && state.reservedPending == 0
                    && state.executionEpoch < slot.runtimeTick;
        });
    }

    static RuntimeTickAdvanceResult advanceRuntimeTick(ServerSlot slot) {
        if (slot.runtimeTick == Long.MAX_VALUE) {
            return RuntimeTickAdvanceResult.EXHAUSTED;
        }
        slot.runtimeTick = Math.incrementExact(slot.runtimeTick);
        return RuntimeTickAdvanceResult.ADVANCED;
    }

    static OptionalLong checkedPositiveSuccessor(long highWater) {
        return highWater >= 0 && highWater < Long.MAX_VALUE
                ? OptionalLong.of(highWater + 1L)
                : OptionalLong.empty();
    }

    static boolean preferOffender(
            long candidateCount,
            long candidateSequence,
            long incumbentCount,
            long incumbentSequence) {
        return candidateCount > incumbentCount
                || candidateCount == incumbentCount && candidateSequence < incumbentSequence;
    }

    static long schedulingLag(long observedRuntimeTick, RuntimeEvent event) {
        Objects.requireNonNull(event, "event");
        if (observedRuntimeTick < event.scheduledRuntimeTick()) {
            throw new IllegalArgumentException("event has not reached its scheduled runtime tick");
        }
        return Math.subtractExact(observedRuntimeTick, event.scheduledRuntimeTick());
    }

    static void observeDrainStop(
            ServerSlot slot, RuntimeDrainStopReason reason) {
        switch (reason) {
            case SERVER_EXECUTION_LIMIT_REACHED -> {
                if (!slot.diagnostics.serverExecutionLimitReachedThisTick) {
                    slot.diagnostics.serverExecutionLimitReachedThisTick = true;
                    slot.diagnostics.serverExecutionLimitReachedTickTotal = saturatingIncrement(
                            slot.diagnostics.serverExecutionLimitReachedTickTotal);
                }
            }
        }
    }

    private static void resetExecutionEpoch(
            ServerSlot.InstanceState instance, long runtimeTick) {
        if (instance.executionEpoch != runtimeTick) {
            instance.executionEpoch = runtimeTick;
            instance.executionsThisTick = 0;
        }
    }

    private static void resetExecutionEpoch(
            ServerSlot.AttributionState attribution, long runtimeTick) {
        if (attribution.executionEpoch != runtimeTick) {
            attribution.executionEpoch = runtimeTick;
            attribution.executionsThisTick = 0;
            attribution.smallestContributingSequenceThisTick = null;
        }
    }

    static void observeExpiry(ServerSlot slot, RuntimeEvent event) {
        slot.diagnostics.deadlineExpiredEventsThisTick++;
        slot.diagnostics.deadlineExpiredEventTotal = saturatingIncrement(
                slot.diagnostics.deadlineExpiredEventTotal);
        slot.diagnostics.maximumDeadlineLatenessTicksThisTick = Math.max(
                slot.diagnostics.maximumDeadlineLatenessTicksThisTick,
                slot.runtimeTick - event.deadlineRuntimeTick());
        slot.diagnostics.maximumLagTicksThisTick = Math.max(
                slot.diagnostics.maximumLagTicksThisTick,
                schedulingLag(slot.runtimeTick, event));
    }

    static boolean deadlineExpired(ServerSlot slot, RuntimeEvent event) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(event, "event");
        return slot.runtimeTick > event.deadlineRuntimeTick();
    }

    static RuntimeExecutionGuardDecision runtimeExecutionGuardDecision(
            ServerSlot slot, ServerSlot.InstanceState instance, RuntimeEvent event) {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(event, "event");
        if (slot.state != ServerSlot.State.RUNNING || instance.cancellationRequested) {
            return RuntimeExecutionGuardDecision.CANCELLED;
        }
        return deadlineExpired(slot, event)
                ? RuntimeExecutionGuardDecision.DEADLINE_EXCEEDED
                : RuntimeExecutionGuardDecision.ALLOWED;
    }

    void observeExpired(ServerSlot slot, RuntimeEvent event) {
        observeExpiry(
                Objects.requireNonNull(slot, "slot"),
                Objects.requireNonNull(event, "event"));
    }

    private static void observeOutcome(ServerSlot slot, RuntimeExecutionOutcome outcome) {
        slot.diagnostics.typedOutcomesThisTick++;
    }

    private static void recordP9Stage(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            P9RuntimeDiagnosticStage stage) {
        var trace = instance.p9Diagnostic;
        if (trace == null || trace.terminalPublished) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        trace.record(stage, slot.runtimeTick);
    }

    void reportP9S4Stage(
            MinecraftServer server,
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            RuntimeEvent event,
            P9RuntimeDiagnosticStage stage) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(stage, "stage");
        if (!server.isSameThread()
                || slots.get(server) != slot
                || slot.state != ServerSlot.State.RUNNING
                || !server.isRunning()
                || server.isStopped()
                || !slot.dispatching
                || slot.currentEvent != event
                || slot.instances.get(event.skillInstanceId()) != instance
                || !instance.inFlight
                || instance.terminal
                || instance.lease.pin.isClosed()
                || !instance.lease.reference.equals(event.skillReference())
                || !P9StarterSkillContent.hasCanonicalGameplayFingerprint(
                        instance.lease.definition)) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }

        var trace = instance.p9Diagnostic;
        if (trace == null || trace.terminalPublished) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        var expectedPredecessor = switch (stage) {
            case CAST_PRESENTATION_OFFERED -> {
                if (!(event.executionData() instanceof CastGeometryExecutionDataV0)
                        || event.nodeIndex() != 0
                        || instance.activeProjectileContinuation == null
                        || instance.activeProjectileContinuation.state
                                != RuntimeProjectileContinuationPermit.State.OPEN
                        || slot.activeProjectileContinuations.get(
                                        instance.activeProjectileContinuation.permitId)
                                != instance.activeProjectileContinuation) {
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                yield P9RuntimeDiagnosticStage.PROJECTILE_ACTIVE;
            }
            case NODE1_MATCHED,
                    DAMAGE_RESOLVED,
                    DAMAGE_COMMIT_RESULT,
                    HIT_PRESENTATION_OFFERED -> {
                if (!hasP9S4DiagnosticCustody(slot, instance, event)) {
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                yield switch (stage) {
                    case NODE1_MATCHED -> P9RuntimeDiagnosticStage.NODE1_QUEUED;
                    case DAMAGE_RESOLVED -> P9RuntimeDiagnosticStage.NODE1_MATCHED;
                    case DAMAGE_COMMIT_RESULT -> P9RuntimeDiagnosticStage.DAMAGE_RESOLVED;
                    case HIT_PRESENTATION_OFFERED ->
                            P9RuntimeDiagnosticStage.DAMAGE_COMMIT_RESULT;
                    default -> throw new AssertionError("unreachable P9-S4 diagnostic stage");
                };
            }
            default -> throw kernel(
                    RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        };

        if (trace.lastStageOrdinal != expectedPredecessor.ordinal()) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        recordP9Stage(slot, instance, stage);
    }

    private static boolean hasP9S4DiagnosticCustody(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            RuntimeEvent event) {
        if (!(event.executionData() instanceof ProjectileHitExecutionDataV0 hit)
                || event.nodeIndex() != 1
                || event.depth() != 1
                || event.childSequence() != 1
                || event.parentEventId().isEmpty()
                || !event.parentEventId().orElseThrow().equals(
                        hit.sourceFamily().sourceEventId())
                || event.scheduledRuntimeTick() != event.createdRuntimeTick()
                || event.deadlineRuntimeTick() < event.scheduledRuntimeTick()
                || !event.skillInstanceId().equals(instance.id)
                || !event.skillReference().equals(instance.lease.reference)
                || !hit.sourceFamily().skillInstanceId().equals(instance.id)
                || hit.sourceDerivationDepth() != 0) {
            return false;
        }
        var permit = slot.activeProjectileContinuations.get(hit.permitId());
        return permit != null
                && permit.mode == RuntimeProjectileContinuationPermit.Mode.REAL
                && permit.state
                        == RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE
                && instance.activeProjectileContinuation == permit
                && permit.serverSlotToken.equals(slot.token)
                && permit.skillInstanceId.equals(instance.id)
                && permit.exactReference.equals(event.skillReference())
                && permit.heldChildEventId.equals(event.eventId())
                && permit.plannedProjectileId.equals(hit.projectileId())
                && permit.sourceFamily.matches(
                        hit.sourceFamily(),
                        permit.exactReference,
                        event.skillReference(),
                        hit.sourceDerivationDepth(),
                        false)
                && permit.deadlineRuntimeTick == event.deadlineRuntimeTick()
                && permit.dimension.equals(hit.dimension())
                && slot.runtimeTick <= permit.deadlineRuntimeTick;
    }

    private static void recordP9SpawnResult(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            RuntimePermitTransferDisposition result) {
        var trace = instance.p9Diagnostic;
        if (trace == null || trace.terminalPublished) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        trace.recordSpawnResult(result, slot.runtimeTick);
    }

    private static void recordP9HitClaimResult(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            RuntimeProjectileContinuationPermit permit,
            ProjectileHitCandidateV0 candidate,
            RuntimePermitClaimDisposition result) {
        if (instance == null
                || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || !permit.serverSlotToken.equals(slot.token)
                || slot.activeProjectileContinuations.get(permit.permitId) != permit
                || instance.activeProjectileContinuation != permit) {
            return;
        }
        var trace = instance.p9Diagnostic;
        if (trace == null || trace.terminalPublished) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        trace.recordHitClaimResult(
                permit.permitId.getMostSignificantBits(),
                permit.permitId.getLeastSignificantBits(),
                candidate,
                result,
                slot.runtimeTick);
    }

    private static void recordP9Terminal(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ProjectileClosureReason reason,
            P9RuntimeCleanupDisposition disposition) {
        try {
            materializeP9Terminal(slot, instance, reason, disposition);
        } catch (RuntimeException | Error ignoredDiagnosticFailure) {
            // Optional terminal evidence never controls authoritative state cleanup.
        }
    }

    private static void materializeP9Terminal(
            ServerSlot slot,
            ServerSlot.InstanceState instance,
            ProjectileClosureReason reason,
            P9RuntimeCleanupDisposition disposition) {
        var trace = instance.p9Diagnostic;
        if (trace == null || trace.terminalPublished) {
            return;
        }
        trace.throwTerminalFailureForTesting();
        trace.terminalReason = Objects.requireNonNull(reason, "reason");
        trace.cleanupDisposition = Objects.requireNonNull(disposition, "disposition");
        trace.record(P9RuntimeDiagnosticStage.TERMINAL_CAUSE, slot.runtimeTick);
        trace.record(P9RuntimeDiagnosticStage.CLEANUP_DISPOSITION, slot.runtimeTick);
        trace.terminalPublished = true;
        slot.p9TerminalRing[slot.p9TerminalWriteIndex] = new ServerSlot.P9TerminalDiagnostic(
                trace.skillInstanceId,
                trace.sequence,
                trace.rootEventId,
                trace.exactReference,
                trace.dimension,
                trace.originX,
                trace.originY,
                trace.originZ,
                trace.directionXQ15,
                trace.directionYQ15,
                trace.directionZQ15,
                trace.profileCode,
                trace.spawnCommitResultCode,
                trace.hitPermitIdMostSignificantBits,
                trace.hitPermitIdLeastSignificantBits,
                trace.hitProjectileIdMostSignificantBits,
                trace.hitProjectileIdLeastSignificantBits,
                trace.hitTargetIdMostSignificantBits,
                trace.hitTargetIdLeastSignificantBits,
                trace.hitXBits,
                trace.hitYBits,
                trace.hitZBits,
                trace.hitDirectionXQ15,
                trace.hitDirectionYQ15,
                trace.hitDirectionZQ15,
                trace.hitClaimResultCode,
                trace.stageCount,
                Arrays.copyOf(trace.stageCodes, trace.stageCodes.length),
                Arrays.copyOf(trace.stageTicks, trace.stageTicks.length),
                reason,
                disposition);
        slot.p9TerminalWriteIndex = (slot.p9TerminalWriteIndex + 1) % slot.p9TerminalRing.length;
        if (slot.p9TerminalCount < slot.p9TerminalRing.length) {
            slot.p9TerminalCount++;
        }
    }

    static void recordBreaker(
            ServerSlot slot,
            SkillInstanceId instanceId,
            Optional<RuntimeSkillInstanceSequence> sequence,
            Optional<EventId> eventId,
            Optional<RuntimePlayerId> player,
            RuntimeCircuitBreakerSummary summary) {
        slot.diagnostics.breakerRing[slot.diagnostics.breakerWriteIndex] =
                new ServerSlot.BreakerDiagnostic(
                        summary.reason(),
                        slot.runtimeTick,
                        instanceId,
                        sequence,
                        eventId,
                        player,
                        summary.pendingBefore(),
                        summary.requestedAdditionalCount(),
                        summary.maximum(),
                        summary.removedQueuedAndDeferredCount(),
                        summary.eventInFlight());
        slot.diagnostics.breakerWriteIndex =
                (slot.diagnostics.breakerWriteIndex + 1)
                        % MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER;
        var reasonIndex = summary.reason().ordinal();
        slot.diagnostics.breakerTotals[reasonIndex] = saturatingIncrement(
                slot.diagnostics.breakerTotals[reasonIndex]);
        slot.diagnostics.breakerTripsThisTick++;
        slot.diagnostics.breakerRemovalsThisTick += summary.removedQueuedAndDeferredCount();
    }

    void observeBreaker(
            ServerSlot slot,
            SkillInstanceId instanceId,
            Optional<RuntimeSkillInstanceSequence> sequence,
            Optional<EventId> eventId,
            Optional<RuntimePlayerId> player,
            RuntimeCircuitBreakerSummary summary) {
        recordBreaker(slot, instanceId, sequence, eventId, player, summary);
    }

    private static Optional<RuntimePlayerId> playerId(
            RuntimeBudgetAttribution attribution) {
        return attribution instanceof PlayerRuntimeBudgetAttribution player
                ? Optional.of(player.playerId())
                : Optional.empty();
    }

    private static Optional<InvalidEventReason> validateRootStableShape(
            ServerSlot slot, RuntimeRootEventSpec spec) {
        if (spec.nodeIndex() < 0 || spec.nodeIndex() >= 256) {
            return Optional.of(InvalidEventReason.INVALID_NODE_COORDINATE);
        }
        if (spec.executionData() instanceof CastGeometryExecutionDataV0 geometry) {
            if (spec.nodeIndex() != 0
                    || spec.schedule().delayTicks() != 0
                    || spec.schedule().deadlineHorizonTicks() != 100
                    || spec.schedule().persistence()
                            != RuntimeSchedulePersistence.MEMORY_ONLY
                    || !(spec.origin() instanceof PlayerOrigin playerOrigin)
                    || spec.target().isPresent()
                    || !spec.triggerCause().eventKind().key().equals(
                            P9StarterSkillContent.ACTIVE_CAST_ID)
                    || !geometry.dimension().equals(playerOrigin.dimension().location())
                    || !validCastGeometry(geometry)) {
                return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
            }
        } else if (!(spec.executionData() instanceof NoRuntimeExecutionData)) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        if (spec.origin() instanceof PlayerOrigin playerOrigin) {
            if (!(spec.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution attribution)) {
                return Optional.of(InvalidEventReason.INVALID_BUDGET_ATTRIBUTION);
            }
            if (!playerOrigin.player().equals(attribution.playerId())) {
                return Optional.of(InvalidEventReason.BUDGET_ATTRIBUTION_MISMATCH);
            }
        }
        if (!(spec.budgetAttribution() instanceof PlayerRuntimeBudgetAttribution)
                && !(spec.budgetAttribution() instanceof NonPlayerRuntimeBudgetAttribution)) {
            return Optional.of(InvalidEventReason.INVALID_BUDGET_ATTRIBUTION);
        }
        if (!slot.token.equals(spec.budgetAttribution().server())) {
            return Optional.of(InvalidEventReason.INVALID_BUDGET_ATTRIBUTION);
        }
        return Optional.empty();
    }

    private static Optional<InvalidEventReason> validateRootDefinitionShape(
            ValidatedSkillDefinition definition, RuntimeRootEventSpec spec) {
        if (!definition.reference().equals(spec.skillReference())
                || spec.nodeIndex() < 0
                || spec.nodeIndex() >= definition.nodes().size()) {
            return Optional.of(InvalidEventReason.INVALID_NODE_COORDINATE);
        }
        var capabilities = definition.nodes().get(spec.nodeIndex())
                .trigger().descriptor().capabilities();
        var canonicalP9 = P9StarterSkillContent.hasCanonicalGameplayFingerprint(definition);
        if (spec.executionData() instanceof CastGeometryExecutionDataV0) {
            if (spec.nodeIndex() != 0 || !canonicalP9) {
                return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
            }
        } else if (canonicalP9 && spec.nodeIndex() == 0) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        if (!capabilities.eventKinds().contains(spec.triggerCause().eventKind())) {
            return Optional.of(InvalidEventReason.INVALID_TRIGGER_CAUSE);
        }
        if (capabilities.sourceRequirement() != SourceRequirement.NONE) {
            return Optional.of(InvalidEventReason.INVALID_REFERENCE_SHAPE);
        }
        if (capabilities.targetRequirement() == TargetRequirement.NONE
                && spec.target().isPresent()
                || capabilities.targetRequirement() == TargetRequirement.REQUIRED
                        && spec.target().isEmpty()) {
            return Optional.of(InvalidEventReason.INVALID_REFERENCE_SHAPE);
        }
        if (capabilities.requiresContinuationState()
                && spec.executionData() == NoRuntimeExecutionData.INSTANCE) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        return Optional.empty();
    }

    private static Optional<InvalidEventReason> validateChildShape(
            ValidatedSkillDefinition definition,
            RuntimeBudgetAttribution inheritedAttribution,
            RuntimeChildSpec child) {
        var p9Hit = child.executionData() instanceof ProjectileHitExecutionDataV0;
        if (!(child.executionData() instanceof NoRuntimeExecutionData) && !p9Hit) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        if (child.nodeIndex() < 0 || child.nodeIndex() >= definition.nodes().size()) {
            return Optional.of(InvalidEventReason.INVALID_NODE_COORDINATE);
        }
        if (child.origin() instanceof PlayerOrigin playerOrigin
                && (!(inheritedAttribution instanceof PlayerRuntimeBudgetAttribution playerAttribution)
                        || !playerOrigin.player().equals(playerAttribution.playerId()))) {
            return Optional.of(InvalidEventReason.INVALID_REFERENCE_SHAPE);
        }
        var capabilities = definition.nodes().get(child.nodeIndex())
                .trigger().descriptor().capabilities();
        if (p9Hit) {
            var hit = (ProjectileHitExecutionDataV0) child.executionData();
            if (child.nodeIndex() != 1
                    || !P9StarterSkillContent.hasCanonicalGameplayFingerprint(definition)
                    || !(child.origin() instanceof PlayerOrigin playerOrigin)
                    || !(child.target().orElse(null) instanceof EntityTarget entityTarget)
                    || entityTarget.expectedKind() != RuntimeEntityKind.LIVING_ENTITY
                    || !child.triggerCause().eventKind().key().equals(
                            P9StarterSkillContent.EFFECT_HIT_ID)
                    || !playerOrigin.dimension().location().equals(hit.dimension())
                    || !entityTarget.dimension().location().equals(hit.dimension())
                    || !entityTarget.entity().value().equals(hit.targetId())
                    || hit.sourceDerivationDepth() != 0) {
                return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
            }
        }
        if (!capabilities.eventKinds().contains(child.triggerCause().eventKind())) {
            return Optional.of(InvalidEventReason.INVALID_TRIGGER_CAUSE);
        }
        if (capabilities.sourceRequirement() != SourceRequirement.PRIOR_NODE) {
            return Optional.of(InvalidEventReason.INVALID_NODE_CAPABILITY);
        }
        if (capabilities.targetRequirement() == TargetRequirement.NONE
                && child.target().isPresent()
                || capabilities.targetRequirement() == TargetRequirement.REQUIRED
                        && child.target().isEmpty()) {
            return Optional.of(InvalidEventReason.INVALID_NODE_CAPABILITY);
        }
        if (capabilities.requiresContinuationState()
                && child.executionData() == NoRuntimeExecutionData.INSTANCE) {
            return Optional.of(InvalidEventReason.INVALID_EXECUTION_DATA);
        }
        return Optional.empty();
    }

    private static boolean validCastGeometry(CastGeometryExecutionDataV0 geometry) {
        return geometry.dimension() != null
                && Double.isFinite(geometry.originX())
                && Double.isFinite(geometry.originY())
                && Double.isFinite(geometry.originZ())
                && geometry.originX() >= -30_000_000.0
                && geometry.originX() < 30_000_000.0
                && geometry.originY() >= -20_000_000.0
                && geometry.originY() < 20_000_000.0
                && geometry.originZ() >= -30_000_000.0
                && geometry.originZ() < 30_000_000.0
                && geometry.directionXQ15() >= -32_767
                && geometry.directionXQ15() <= 32_767
                && geometry.directionYQ15() >= -32_767
                && geometry.directionYQ15() <= 32_767
                && geometry.directionZQ15() >= -32_767
                && geometry.directionZQ15() <= 32_767
                && (geometry.directionXQ15() != 0
                        || geometry.directionYQ15() != 0
                        || geometry.directionZQ15() != 0)
                && geometry.profileCode() == 0;
    }

    private static boolean isP9ExecutionData(RuntimeExecutionData executionData) {
        return executionData instanceof CastGeometryExecutionDataV0
                || executionData instanceof ProjectileHitExecutionDataV0;
    }

    private static boolean stableTokensMatch(
            RuntimeServerToken token,
            RuntimeOrigin origin,
            Optional<RuntimeTarget> target) {
        if (!token.equals(serverOf(origin))) {
            return false;
        }
        return target.isEmpty() || token.equals(serverOf(target.orElseThrow()));
    }

    private static RuntimeServerToken serverOf(RuntimeOrigin origin) {
        return switch (origin) {
            case ServerOrigin value -> value.server();
            case PlayerOrigin value -> value.server();
            case EntityOrigin value -> value.server();
            case BlockOrigin value -> value.server();
        };
    }

    private static RuntimeServerToken serverOf(RuntimeTarget target) {
        return switch (target) {
            case PlayerTarget value -> value.server();
            case EntityTarget value -> value.server();
            case BlockTarget value -> value.server();
        };
    }

    private static RuntimeReferenceFailureReason referenceFailure(
            RuntimeReferenceResolutionOutcome outcome) {
        return switch (outcome) {
            case RuntimeReferenceResolutionOutcome.Resolved ignored ->
                    throw new IllegalArgumentException("resolved outcome has no failure");
            case RuntimeReferenceResolutionOutcome.SourceMissing failure -> failure.reason();
            case RuntimeReferenceResolutionOutcome.TargetMissing failure -> failure.reason();
            case RuntimeReferenceResolutionOutcome.InvalidRuntimeReference failure -> failure.reason();
        };
    }

    private static RuntimeServerToken serverToken(RuntimeCancellationHandle handle) {
        return switch (handle) {
            case RuntimeCancellationToken value -> value.serverSlotToken();
            case RuntimeEventToken value -> value.serverSlotToken();
        };
    }

    private static RuntimeAdmissionResult admissionForClosedSlot(ServerSlot slot) {
        return switch (slot.state) {
            case FAULTED -> new RuntimeAdmissionResult.KernelFaulted();
            case EXHAUSTED -> new RuntimeAdmissionResult.TickExhausted();
            case STOPPING -> new RuntimeAdmissionResult.ServerStopping();
            case REMOVED -> new RuntimeAdmissionResult.ServerNotRunning();
            case RUNNING -> throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        };
    }

    private static ServerSlot.AttributionState requireAttribution(
            ServerSlot slot, RuntimeBudgetAttribution key) {
        var attribution = slot.attributions.get(key);
        if (attribution == null) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        return attribution;
    }

    static void enterStopping(ServerSlot slot) {
        enterStopping(null, slot);
    }

    private static int enterStopping(MinecraftServer server, ServerSlot slot) {
        if (slot.state == ServerSlot.State.REMOVED || slot.state == ServerSlot.State.STOPPING) {
            return 0;
        }
        slot.state = ServerSlot.State.STOPPING;
        return clearSlotNormal(server, slot);
    }

    void stopSlot(ServerSlot slot) {
        var exactSlot = Objects.requireNonNull(slot, "slot");
        enterStopping(serverForSlot(exactSlot), exactSlot);
    }

    private MinecraftServer serverForSlot(ServerSlot slot) {
        for (var entry : slots.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        if (!slot.activeProjectileContinuations.isEmpty()) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        return null;
    }

    RuntimeException preserveRuntimeFault(ServerSlot slot, RuntimeException primary) {
        Objects.requireNonNull(primary, "primary");
        var exactSlot = Objects.requireNonNull(slot, "slot");
        enterFaultAfterRuntimeException(serverForSlot(exactSlot), exactSlot);
        return primary;
    }

    Error preserveErrorFault(ServerSlot slot, Error primary) {
        Objects.requireNonNull(primary, "primary");
        enterFaultAfterError(Objects.requireNonNull(slot, "slot"));
        return primary;
    }

    static void enterFaultAfterRuntimeException(ServerSlot slot) {
        enterFaultAfterRuntimeException(null, slot);
    }

    private static void enterFaultAfterRuntimeException(
            MinecraftServer server, ServerSlot slot) {
        slot.state = ServerSlot.State.FAULTED;
        try {
            clearSlotAfterRuntimeException(server, slot);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // The already-caught primary is authoritative and is never masked or retained.
        }
    }

    private static void enterFaultAfterError(ServerSlot slot) {
        slot.state = ServerSlot.State.FAULTED;
        try {
            clearSlotAfterError(slot);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Error cleanup is best effort and never masks or retains the primary identity.
        }
    }

    private static int closeAllIndexedContinuations(
            MinecraftServer server,
            ServerSlot slot,
            ProjectileClosureReason reason,
            P9RuntimeCleanupDisposition disposition) {
        var workUnits = slot.activeProjectileContinuations.size();
        if (workUnits > 128 || workUnits > slot.instances.size()) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (workUnits != 0 && server == null) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        if (slot.p9BatchContinuationCloseInProgress) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        var permits = slot.activeProjectileContinuations.entrySet().iterator();
        var closedWorkUnits = 0;
        while (permits.hasNext()) {
            var indexed = permits.next();
            if (slot.p9BatchContinuationCloseInProgress) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            slot.p9BatchContinuationCloseInProgress = true;
            try {
                var permit = indexed.getValue();
                var projectile = loadedProjectile(server, permit);
                var close = permit.closeWithoutHit(server, reason);
                if (close != RuntimePermitCloseDisposition.CLOSED) {
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                permits.remove();
                if (projectile != null && !projectile.isRemoved()) {
                    projectile.discard();
                }
                closedWorkUnits++;
            } finally {
                slot.p9BatchContinuationCloseInProgress = false;
            }
        }
        if (closedWorkUnits != workUnits
                || !slot.activeProjectileContinuations.isEmpty()
                || slot.p9BatchContinuationCloseInProgress) {
            throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
        }
        return closedWorkUnits;
    }

    private static void terminalizeRemainingP9(
            MinecraftServer server,
            ServerSlot slot,
            ProjectileClosureReason reason,
            P9RuntimeCleanupDisposition disposition) {
        for (var instance : slot.instances.values()) {
            var errorRetainedPermit = instance.activeProjectileContinuation;
            if (errorRetainedPermit != null) {
                var projectile = server == null
                        ? null
                        : loadedProjectile(server, errorRetainedPermit);
                if (server == null
                        || errorRetainedPermit.closeWithoutHit(server, reason)
                                != RuntimePermitCloseDisposition.CLOSED) {
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                if (projectile != null && !projectile.isRemoved()) {
                    projectile.discard();
                }
            }
            instance.clearP9AuthenticatedActorWitness();
            if (instance.p9Diagnostic != null) {
                recordP9Terminal(slot, instance, reason, disposition);
            }
        }
    }

    private static int clearSlotNormal(MinecraftServer server, ServerSlot slot) {
        var closedWorkUnits = closeAllIndexedContinuations(
                server,
                slot,
                ProjectileClosureReason.SERVER_STOPPED,
                P9RuntimeCleanupDisposition.RELEASED);
        terminalizeRemainingP9(
                server,
                slot,
                ProjectileClosureReason.SERVER_STOPPED,
                P9RuntimeCleanupDisposition.RELEASED);
        slot.queue.clear();
        slot.eventIndex.clear();
        Arrays.fill(slot.deferred, null);
        Arrays.fill(slot.cleanupScratch, null);
        slot.deferredCount = 0;
        slot.currentEvent = null;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
        slot.committedPending = 0;
        slot.reservedPending = 0;
        slot.rootAdmissionsThisTick = 0;
        slot.executionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.instances.clear();
        slot.attributions.clear();
        for (var lease : slot.leases.values()) {
            lease.close();
        }
        slot.leases.clear();
        slot.diagnostics.clear();
        Arrays.fill(slot.p9TerminalRing, null);
        slot.p9TerminalWriteIndex = 0;
        slot.p9TerminalCount = 0;
        slot.p9ActiveIndexInvalidatedAfterError = false;
        return closedWorkUnits;
    }

    private static void clearSlotAfterRuntimeException(
            MinecraftServer server, ServerSlot slot) {
        closeAllIndexedContinuations(
                server,
                slot,
                ProjectileClosureReason.RUNTIME_FAULT,
                P9RuntimeCleanupDisposition.RELEASED);
        terminalizeRemainingP9(
                server,
                slot,
                ProjectileClosureReason.RUNTIME_FAULT,
                P9RuntimeCleanupDisposition.RELEASED);
        slot.queue.clear();
        slot.eventIndex.clear();
        Arrays.fill(slot.deferred, null);
        Arrays.fill(slot.cleanupScratch, null);
        slot.deferredCount = 0;
        slot.currentEvent = null;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
        slot.committedPending = 0;
        slot.reservedPending = 0;
        slot.rootAdmissionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.instances.clear();
        slot.attributions.clear();
        slot.diagnostics.clearAfterFaultPreservingStartedCounters();
        for (var lease : slot.leases.values()) {
            try {
                lease.close();
            } catch (RuntimeException | Error ignoredCleanupFailure) {
                // Continue the bounded close pass; the primary must keep its identity.
            }
        }
        slot.leases.clear();
    }

    static void clearSlotAfterError(ServerSlot slot) {
        var currentInstance = slot.currentEvent == null
                ? null
                : slot.instances.get(slot.currentEvent.skillInstanceId());
        if (currentInstance != null) {
            currentInstance.clearP9AuthenticatedActorWitness();
            if (currentInstance.p9Diagnostic != null) {
                try {
                    currentInstance.p9Diagnostic.terminalReason =
                            ProjectileClosureReason.RUNTIME_FAULT;
                    currentInstance.p9Diagnostic.recordErrorDeferredBestEffort(
                            slot.runtimeTick);
                } catch (RuntimeException | Error ignoredDiagnosticFailure) {
                    // Optional Error-deferred evidence never controls primitive cleanup.
                }
            }
        }
        try {
            slot.activeProjectileContinuations.forEach(slot.p9ErrorCleanup);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // The preallocated best effort must not mask the already-caught Error.
        } finally {
            slot.p9ErrorCleanup.clear();
        }
        slot.instances.forEach(slot.p9InstanceErrorCleanup);
        slot.queue.clear();
        slot.eventIndex.clear();
        slot.p9ActiveIndexInvalidatedAfterError = true;
        slot.p9BatchContinuationCloseInProgress = false;
        slot.activeProjectileContinuations.clear();
        for (var index = 0; index < slot.deferred.length; index++) {
            slot.deferred[index] = null;
        }
        for (var index = 0; index < slot.cleanupScratch.length; index++) {
            slot.cleanupScratch[index] = null;
        }
        slot.deferredCount = 0;
        slot.currentEvent = null;
        slot.currentReservationCount = 0;
        slot.currentReservationOwner = null;
        slot.committedPending = 0;
        slot.reservedPending = 0;
        slot.rootAdmissionsThisTick = 0;
        slot.cancellationsThisTick = 0;
        slot.diagnostics.clearAfterFaultPreservingStartedCounters();
    }

    private static int minimum(int first, int... remaining) {
        var minimum = first;
        for (var value : remaining) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static RuntimeKernelException kernel(RuntimeKernelException.Code code) {
        return new RuntimeKernelException(code);
    }

    private sealed interface LeaseAcquisition
            permits LeaseAcquisition.Available, LeaseAcquisition.Unavailable {
        record Available(RuntimeRevisionLease lease, boolean provisional)
                implements LeaseAcquisition {
            public Available {
                Objects.requireNonNull(lease, "lease");
            }
        }

        record Unavailable(SkillRevisionUnavailableReason reason)
                implements LeaseAcquisition {
            public Unavailable {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    private sealed interface DetachedInvocation
            permits AbortedInvocation,
                    TerminalInvocation,
                    PortInvocation,
                    NullBatchInvocation {}

    private enum AbortedInvocation implements DetachedInvocation {
        INSTANCE
    }

    private record TerminalInvocation(RuntimeExecutionOutcome outcome)
            implements DetachedInvocation {
        TerminalInvocation {
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    private record PortInvocation(
            ChildReservation reservation, RuntimeExecutionBatch batch)
            implements DetachedInvocation {
        PortInvocation {
            Objects.requireNonNull(reservation, "reservation");
            Objects.requireNonNull(batch, "batch");
        }
    }

    private record NullBatchInvocation(ChildReservation reservation)
            implements DetachedInvocation {
        NullBatchInvocation {
            Objects.requireNonNull(reservation, "reservation");
        }
    }

    /** Preallocated, non-retaining best-effort visitor for the outer Error path. */
    static final class P9ErrorCleanup
            implements BiConsumer<UUID, RuntimeProjectileContinuationPermit> {
        private final ServerSlot slot;
        private transient MinecraftServer server;

        P9ErrorCleanup(ServerSlot slot) {
            this.slot = Objects.requireNonNull(slot, "slot");
        }

        void prepare(MinecraftServer server) {
            this.server = server;
        }

        void clear() {
            server = null;
        }

        @Override
        public void accept(UUID permitId, RuntimeProjectileContinuationPermit permit) {
            if (permit == null
                    || permitId == null
                    || !permitId.equals(permit.permitId)) {
                return;
            }
            P9StarterProjectile projectile = null;
            try {
                if (server != null) {
                    projectile = loadedProjectile(server, permit);
                }
            } catch (RuntimeException | Error ignoredLookupFailure) {
                // Primitive permit invalidation below is independent of entity lookup.
            }
            try {
                permit.state = permit.state
                                == RuntimeProjectileContinuationPermit.State.CLAIMED_PENDING_DAMAGE
                        ? RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT
                        : RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT;
                var instance = slot.instances.get(permit.skillInstanceId);
                if (instance != null && instance.activeProjectileContinuation == permit) {
                    instance.activeProjectileContinuation = null;
                    instance.clearP9AuthenticatedActorWitness();
                }
            } catch (RuntimeException | Error ignoredPrimitiveFailure) {
                // Continue to the bounded entity discard without masking its primary.
            }
            try {
                if (projectile != null && !projectile.isRemoved()) {
                    projectile.discard();
                }
            } catch (RuntimeException | Error ignoredDiscardFailure) {
                // The already-caught Error remains authoritative.
            }
        }
    }

    /** Preallocated primitive witness clearer for every bounded retained instance. */
    static final class P9InstanceErrorCleanup
            implements BiConsumer<SkillInstanceId, ServerSlot.InstanceState> {
        @Override
        public void accept(
                SkillInstanceId ignoredInstanceId,
                ServerSlot.InstanceState instance) {
            if (instance != null) {
                instance.clearP9AuthenticatedActorWitness();
            }
        }
    }

    /** Existing call-scoped P5 guard with a root-package-only diagnostic projection. */
    static final class RuntimeExecutionGuardState implements RuntimeExecutionGuard {
        private final SkillRuntimeService owner;
        private final MinecraftServer server;
        private final ServerSlot slot;
        private final ServerSlot.InstanceState instance;
        private final RuntimeEvent event;
        private final ServerPlayer p9Actor;
        private final ResourceLocation p9Dimension;
        private boolean p9DamageCommitEntered;
        private boolean p9DamageCommitFinished;
        private boolean p9AppliedObservationArmed;

        RuntimeExecutionGuardState(
                SkillRuntimeService owner,
                MinecraftServer server,
                ServerSlot slot,
                ServerSlot.InstanceState instance,
                RuntimeEvent event,
                ServerPlayer p9Actor,
                ResourceLocation p9Dimension) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.server = Objects.requireNonNull(server, "server");
            this.slot = Objects.requireNonNull(slot, "slot");
            this.instance = Objects.requireNonNull(instance, "instance");
            this.event = Objects.requireNonNull(event, "event");
            this.p9Actor = p9Actor;
            this.p9Dimension = p9Dimension;
            if ((p9Actor == null) != (p9Dimension == null)) {
                throw new IllegalArgumentException(
                        "P9 guard actor and dimension must be jointly present");
            }
        }

        @Override
        public RuntimeExecutionGuardDecision check() {
            if (owner.p9ReloadCloseRequested.get()
                    && isP9ExecutionData(event.executionData())) {
                return RuntimeExecutionGuardDecision.CANCELLED;
            }
            if (p9Actor != null
                    && !isCurrentP9AuthenticatedActor(
                            server, instance, p9Actor, p9Dimension)) {
                return RuntimeExecutionGuardDecision.CANCELLED;
            }
            if (event.executionData() instanceof ProjectileHitExecutionDataV0
                    && !validClaimedP9Child(server, slot, instance, event)) {
                return RuntimeExecutionGuardDecision.CANCELLED;
            }
            return owner.runtimeExecutionGuardDecision(slot, instance, event);
        }

        void reportP9S4Stage(P9RuntimeDiagnosticStage stage) {
            owner.reportP9S4Stage(server, slot, instance, event, stage);
        }

        void enterP9DamageCommit() {
            if (p9DamageCommitEntered
                    || p9DamageCommitFinished
                    || !(event.executionData() instanceof ProjectileHitExecutionDataV0)) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            var trace = instance.p9Diagnostic;
            if (trace == null
                    || trace.lastStageOrdinal
                            != P9RuntimeDiagnosticStage.DAMAGE_RESOLVED.ordinal()) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            p9DamageCommitEntered = true;
        }

        void armP9AppliedObservation() {
            if (p9AppliedObservationArmed) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            p9AppliedObservationArmed = true;
        }

        void reportP9AppliedFactIfArmed() {
            if (!p9AppliedObservationArmed) {
                return;
            }
            p9AppliedObservationArmed = false;
            if (event.executionData() instanceof CastGeometryExecutionDataV0) {
                reportP9S4Stage(P9RuntimeDiagnosticStage.CAST_PRESENTATION_OFFERED);
                return;
            }
            if (event.executionData() instanceof ProjectileHitExecutionDataV0) {
                if (!p9DamageCommitEntered || p9DamageCommitFinished) {
                    throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
                }
                reportP9S4Stage(P9RuntimeDiagnosticStage.DAMAGE_COMMIT_RESULT);
                reportP9S4Stage(P9RuntimeDiagnosticStage.HIT_PRESENTATION_OFFERED);
                p9DamageCommitFinished = true;
            }
        }

        void finishP9DamageCommit() {
            if (!p9DamageCommitEntered) {
                throw kernel(RuntimeKernelException.Code.RESERVATION_ACCOUNTING_INVARIANT);
            }
            if (p9DamageCommitFinished) {
                return;
            }
            reportP9S4Stage(P9RuntimeDiagnosticStage.DAMAGE_COMMIT_RESULT);
            p9DamageCommitFinished = true;
        }
    }

    record PendingBreak(
            RuntimeCircuitBreakReason reason, int pendingBefore, int maximum) {}
}

/** Mutable call-scoped view of one current firm child reservation. */
final class ChildReservation {
    private final int capacity;
    private final long eventIdStart;
    private final RuntimeExecutionBudget budget;
    private RuntimeProjectileContinuationPermit detachedPermit;

    ChildReservation(int capacity, long eventIdStart, RuntimeExecutionBudget budget) {
        if (capacity < 0 || eventIdStart < 0L) {
            throw new IllegalArgumentException("invalid child reservation coordinates");
        }
        this.capacity = capacity;
        this.eventIdStart = eventIdStart;
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    int capacity() {
        return capacity;
    }

    long eventIdStart() {
        return eventIdStart;
    }

    RuntimeExecutionBudget budget() {
        return budget;
    }

    RuntimeProjectileContinuationPermit detachedPermit() {
        return detachedPermit;
    }

    void attach(RuntimeProjectileContinuationPermit permit) {
        if (detachedPermit != null) {
            throw new IllegalStateException("child reservation already detached");
        }
        detachedPermit = Objects.requireNonNull(permit, "permit");
    }
}

/** One-shot, call-scoped access to P5's current firm child reservation. */
final class RuntimeProjectileContinuationOpener {
    private final SkillRuntimeService owner;
    private final MinecraftServer server;
    private final ServerSlot slot;
    private final RuntimeEvent sourceEvent;
    private final ChildReservation reservation;
    private boolean consumed;

    RuntimeProjectileContinuationOpener(
            SkillRuntimeService owner,
            MinecraftServer server,
            ServerSlot slot,
            RuntimeEvent sourceEvent,
            ChildReservation reservation) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.server = Objects.requireNonNull(server, "server");
        this.slot = Objects.requireNonNull(slot, "slot");
        this.sourceEvent = Objects.requireNonNull(sourceEvent, "sourceEvent");
        this.reservation = Objects.requireNonNull(reservation, "reservation");
    }

    RuntimeProjectileContinuationOpenResult openProjectileContinuation(
            ActionOutputKind outputKind,
            int outputOrdinal) {
        if (consumed) {
            return new RuntimeProjectileContinuationOpenResult.Rejected(
                    RuntimeProjectileContinuationOpenRejectionReason.INVARIANT_REJECTED);
        }
        consumed = true;
        return owner.openProjectileContinuation(
                server,
                slot,
                sourceEvent,
                reservation,
                outputKind,
                outputOrdinal);
    }
}

/** Closed result of the one-shot continuation opener. */
sealed abstract class RuntimeProjectileContinuationOpenResult
        permits RuntimeProjectileContinuationOpenResult.Opened,
                RuntimeProjectileContinuationOpenResult.Rejected {
    private RuntimeProjectileContinuationOpenResult() {}

    static final class Opened extends RuntimeProjectileContinuationOpenResult {
        private final RuntimeProjectileContinuationPermit permit;
        private final UUID plannedProjectileId;
        private final SkillRuntimeService owner;
        private boolean transferConsumed;

        Opened(
                RuntimeProjectileContinuationPermit permit,
                UUID plannedProjectileId,
                SkillRuntimeService owner) {
            this.permit = Objects.requireNonNull(permit, "permit");
            this.plannedProjectileId = Objects.requireNonNull(
                    plannedProjectileId, "plannedProjectileId");
            this.owner = Objects.requireNonNull(owner, "owner");
            if (plannedProjectileId.getMostSignificantBits() == 0L
                    && plannedProjectileId.getLeastSignificantBits() == 0L) {
                throw new IllegalArgumentException("planned projectile identity must be nonzero");
            }
            if (!owner.ownsOpenedContinuation(permit, plannedProjectileId)) {
                throw new IllegalArgumentException("opened continuation is not owned by this service");
            }
        }

        RuntimeProjectileContinuationPermit permit() {
            return permit;
        }

        UUID plannedProjectileId() {
            return plannedProjectileId;
        }

        RuntimePermitTransferDisposition transferAfterAppliedSpawn(
                MinecraftServer server,
                P9StarterProjectile projectile) {
            Objects.requireNonNull(server, "server");
            Objects.requireNonNull(projectile, "projectile");
            if (transferConsumed) {
                return RuntimePermitTransferDisposition.ALREADY_TRANSFERRED;
            }
            transferConsumed = true;
            return owner.transferSpawnedProjectile(
                    server, permit, plannedProjectileId, projectile);
        }
    }

    static final class Rejected extends RuntimeProjectileContinuationOpenResult {
        private final RuntimeProjectileContinuationOpenRejectionReason reason;

        Rejected(RuntimeProjectileContinuationOpenRejectionReason reason) {
            this.reason = Objects.requireNonNull(reason, "reason");
        }

        RuntimeProjectileContinuationOpenRejectionReason reason() {
            return reason;
        }
    }
}

enum RuntimeProjectileContinuationOpenRejectionReason {
    CAPACITY_UNAVAILABLE,
    LIFECYCLE_UNAVAILABLE,
    INVARIANT_REJECTED
}

/** P5-owned capability for one held future projectile continuation. */
final class RuntimeProjectileContinuationPermit {
    private final SkillRuntimeService owner;
    final Mode mode;
    final RuntimeServerToken serverSlotToken;
    final SkillInstanceId skillInstanceId;
    final RuntimeBudgetAttribution budgetAttribution;
    final SkillReference exactReference;
    final ResourceLocation dimension;
    final SourceFamilyKey sourceFamily;
    final int sourceDerivationDepth;
    final EventId heldChildEventId;
    final long deadlineRuntimeTick;
    final UUID permitId;
    final UUID plannedProjectileId;
    State state;

    RuntimeProjectileContinuationPermit() {
        owner = null;
        mode = Mode.UNAVAILABLE;
        serverSlotToken = null;
        skillInstanceId = null;
        budgetAttribution = null;
        exactReference = null;
        dimension = null;
        sourceFamily = null;
        sourceDerivationDepth = 0;
        heldChildEventId = null;
        deadlineRuntimeTick = 0L;
        permitId = null;
        plannedProjectileId = null;
        state = State.CLOSED_NO_HIT;
    }

    RuntimeProjectileContinuationPermit(
            SkillRuntimeService owner,
            RuntimeServerToken serverSlotToken,
            SkillInstanceId skillInstanceId,
            RuntimeBudgetAttribution budgetAttribution,
            SkillReference exactReference,
            ResourceLocation dimension,
            SourceFamilyKey sourceFamily,
            int sourceDerivationDepth,
            EventId heldChildEventId,
            long deadlineRuntimeTick,
            UUID permitId,
            UUID plannedProjectileId) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.mode = Mode.REAL;
        this.serverSlotToken = Objects.requireNonNull(serverSlotToken, "serverSlotToken");
        this.skillInstanceId = Objects.requireNonNull(skillInstanceId, "skillInstanceId");
        this.budgetAttribution = Objects.requireNonNull(
                budgetAttribution, "budgetAttribution");
        this.exactReference = Objects.requireNonNull(exactReference, "exactReference");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.sourceFamily = Objects.requireNonNull(sourceFamily, "sourceFamily");
        this.sourceDerivationDepth = sourceDerivationDepth;
        this.heldChildEventId = Objects.requireNonNull(
                heldChildEventId, "heldChildEventId");
        this.deadlineRuntimeTick = deadlineRuntimeTick;
        this.permitId = Objects.requireNonNull(permitId, "permitId");
        this.plannedProjectileId = Objects.requireNonNull(
                plannedProjectileId, "plannedProjectileId");
        this.state = State.RESERVED;
        if (serverSlotToken.value() <= 0L
                || !sourceFamily.skillInstanceId().equals(skillInstanceId)
                || sourceDerivationDepth != 0
                || heldChildEventId.value() <= 0L
                || deadlineRuntimeTick < 0L
                || !permitId.equals(new UUID(
                        serverSlotToken.value(), heldChildEventId.value()))
                || !plannedProjectileId.equals(new UUID(
                        ~serverSlotToken.value(), heldChildEventId.value()))
                || permitId.equals(plannedProjectileId)) {
            throw new IllegalArgumentException("invalid P9 continuation coordinates");
        }
    }

    RuntimePermitClaimDisposition claimLoadedEntityHit(
            MinecraftServer server,
            ProjectileHitCandidateV0 candidate) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(candidate, "candidate");
        if (mode != Mode.REAL) {
            return RuntimePermitClaimDisposition.REJECTED;
        }
        if (!owner.isOwningPermitServerThread(server, this)) {
            return RuntimePermitClaimDisposition.REJECTED;
        }
        if (state == State.CLAIMED_PENDING_DAMAGE
                || state == State.CLOSED_NO_HIT
                || state == State.CLOSED_AFTER_HIT) {
            return RuntimePermitClaimDisposition.DUPLICATE_OR_LATE;
        }
        if (state != State.OPEN) {
            return RuntimePermitClaimDisposition.REJECTED;
        }
        return owner.claimProjectileHit(server, this, candidate);
    }

    RuntimePermitCloseDisposition closeWithoutHit(
            MinecraftServer server,
            ProjectileClosureReason reason) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(reason, "reason");
        if (mode != Mode.REAL) {
            return RuntimePermitCloseDisposition.REJECTED;
        }
        var disposition = owner.closeProjectileContinuation(
                server,
                this,
                serverSlotToken,
                skillInstanceId,
                budgetAttribution,
                permitId,
                reason);
        return disposition;
    }

    enum Mode {
        REAL,
        UNAVAILABLE
    }

    enum State {
        RESERVED,
        OPEN,
        CLAIMED_PENDING_DAMAGE,
        CLOSED_NO_HIT,
        CLOSED_AFTER_HIT
    }
}

enum RuntimePermitTransferDisposition {
    TRANSFERRED,
    REJECTED,
    ALREADY_TRANSFERRED
}

enum RuntimePermitClaimDisposition {
    QUEUED,
    REJECTED,
    DUPLICATE_OR_LATE
}

enum ProjectileClosureReason {
    SPAWN_NOT_APPLIED,
    BLOCK_OR_INVALID_HIT,
    RANGE_EXHAUSTED,
    AGE_EXHAUSTED,
    DEADLINE_REACHED,
    OWNER_INVALIDATED,
    ENTITY_OR_LEVEL_REMOVED,
    RELOAD_INVALIDATED,
    SERVER_STOPPED,
    RUNTIME_FAULT,
    CLAIM_REJECTED,
    DAMAGE_TERMINAL
}

enum RuntimePermitCloseDisposition {
    CLOSED,
    ALREADY_CLOSED,
    REJECTED
}

enum P9RuntimeDiagnosticStage {
    CAST_ACCEPTED,
    INSTANCE_PINNED,
    NODE0_MATCHED,
    CONTINUATION_OPENED,
    SPAWN_RESOLVED,
    SPAWN_COMMIT_RESULT,
    PROJECTILE_ACTIVE,
    CAST_PRESENTATION_OFFERED,
    HIT_CLAIM_RESULT,
    NODE1_QUEUED,
    NODE1_MATCHED,
    DAMAGE_RESOLVED,
    DAMAGE_COMMIT_RESULT,
    HIT_PRESENTATION_OFFERED,
    TERMINAL_CAUSE,
    CLEANUP_DISPOSITION
}

enum P9RuntimeCleanupDisposition {
    RELEASED,
    ERROR_DEFERRED
}

/** One exact server-slot state graph owned exclusively by {@link SkillRuntimeService}. */
final class ServerSlot {
    enum State {
        RUNNING,
        STOPPING,
        REMOVED,
        EXHAUSTED,
        FAULTED
    }

    final RuntimeServerToken token;
    final P5RuntimeLimits limits;
    final PriorityQueue<RuntimeEvent> queue = new PriorityQueue<>(
            MagicSafetyCeilings.MAX_PENDING_EVENTS_PER_SERVER,
            SkillRuntimeService.EVENT_ORDER);
    final Map<EventId, RuntimeEvent> eventIndex = new HashMap<>(5_462);
    final Map<SkillInstanceId, InstanceState> instances = new HashMap<>(171);
    final Map<UUID, RuntimeProjectileContinuationPermit> activeProjectileContinuations =
            new HashMap<>(171);
    final SkillRuntimeService.P9ErrorCleanup p9ErrorCleanup =
            new SkillRuntimeService.P9ErrorCleanup(this);
    final SkillRuntimeService.P9InstanceErrorCleanup p9InstanceErrorCleanup =
            new SkillRuntimeService.P9InstanceErrorCleanup();
    final Map<RuntimeBudgetAttribution, AttributionState> attributions = new HashMap<>(256);
    final Map<SkillReference, RuntimeRevisionLease> leases = new HashMap<>(171);
    final RuntimeEvent[] deferred =
            new RuntimeEvent[MagicSafetyCeilings.MAX_BUDGET_DEFERRED_EVENTS];
    final RuntimeEvent[] cleanupScratch =
            new RuntimeEvent[MagicSafetyCeilings.MAX_BREAKER_CLEANUP_SCRATCH_EVENTS];
    final Diagnostics diagnostics = new Diagnostics();
    final P9TerminalDiagnostic[] p9TerminalRing = new P9TerminalDiagnostic[256];
    State state = State.RUNNING;
    long runtimeTick;
    long eventSequenceHighWater;
    long skillInstanceSequenceHighWater;
    int rootAdmissionsThisTick;
    int executionsThisTick;
    int cancellationsThisTick;
    int committedPending;
    int reservedPending;
    int deferredCount;
    int currentReservationCount;
    SkillInstanceId currentReservationOwner;
    RuntimeEvent currentEvent;
    boolean dispatching;
    boolean p9BatchContinuationCloseInProgress;
    boolean p9ActiveIndexInvalidatedAfterError;
    int p9TerminalWriteIndex;
    int p9TerminalCount;

    ServerSlot(RuntimeServerToken token, P5RuntimeLimits limits) {
        this.token = Objects.requireNonNull(token, "token");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    static final class InstanceState {
        final SkillInstanceId id;
        final RuntimeSkillInstanceSequence sequence;
        final RuntimeBudgetAttribution attribution;
        final RuntimeRevisionLease lease;
        private ServerPlayer p9AuthenticatedActorWitness;
        int committedPending;
        int reservedPending;
        int lifetimeEvents;
        long executionEpoch = -1L;
        int executionsThisTick;
        boolean inFlight;
        boolean cancellationRequested;
        boolean terminal;
        RuntimeProjectileContinuationPermit activeProjectileContinuation;
        P9ActiveDiagnostic p9Diagnostic;

        InstanceState(
                SkillInstanceId id,
                RuntimeSkillInstanceSequence sequence,
                RuntimeBudgetAttribution attribution,
                RuntimeRevisionLease lease,
                ServerPlayer p9AuthenticatedActorWitness) {
            this.id = Objects.requireNonNull(id, "id");
            this.sequence = Objects.requireNonNull(sequence, "sequence");
            this.attribution = Objects.requireNonNull(attribution, "attribution");
            this.lease = Objects.requireNonNull(lease, "lease");
            this.p9AuthenticatedActorWitness = p9AuthenticatedActorWitness;
        }

        boolean hasP9AuthenticatedActorWitness(ServerPlayer candidate) {
            return candidate != null && p9AuthenticatedActorWitness == candidate;
        }

        void clearP9AuthenticatedActorWitness() {
            p9AuthenticatedActorWitness = null;
        }
    }

    static final class P9ActiveDiagnostic {
        final SkillInstanceId skillInstanceId;
        final RuntimeSkillInstanceSequence sequence;
        final EventId rootEventId;
        final SkillReference exactReference;
        final ResourceLocation dimension;
        final double originX;
        final double originY;
        final double originZ;
        final int directionXQ15;
        final int directionYQ15;
        final int directionZQ15;
        final int profileCode;
        final int[] stageCodes = new int[16];
        final long[] stageTicks = new long[16];
        int spawnCommitResultCode;
        long hitPermitIdMostSignificantBits;
        long hitPermitIdLeastSignificantBits;
        long hitProjectileIdMostSignificantBits;
        long hitProjectileIdLeastSignificantBits;
        long hitTargetIdMostSignificantBits;
        long hitTargetIdLeastSignificantBits;
        long hitXBits;
        long hitYBits;
        long hitZBits;
        int hitDirectionXQ15;
        int hitDirectionYQ15;
        int hitDirectionZQ15;
        int hitClaimResultCode;
        int stageCount;
        int lastStageOrdinal = -1;
        ProjectileClosureReason terminalReason;
        P9RuntimeCleanupDisposition cleanupDisposition;
        boolean terminalPublished;
        private int terminalFailureForTesting;

        P9ActiveDiagnostic(RuntimeEvent event, CastGeometryExecutionDataV0 geometry) {
            skillInstanceId = event.skillInstanceId();
            sequence = event.skillInstanceSequence();
            rootEventId = event.eventId();
            exactReference = event.skillReference();
            dimension = geometry.dimension();
            originX = geometry.originX();
            originY = geometry.originY();
            originZ = geometry.originZ();
            directionXQ15 = geometry.directionXQ15();
            directionYQ15 = geometry.directionYQ15();
            directionZQ15 = geometry.directionZQ15();
            profileCode = geometry.profileCode();
        }

        void recordSpawnResult(
                RuntimePermitTransferDisposition result, long runtimeTick) {
            Objects.requireNonNull(result, "result");
            if (result == RuntimePermitTransferDisposition.ALREADY_TRANSFERRED) {
                throw new IllegalArgumentException(
                        "duplicate transfer is not a spawn commit result");
            }
            var recordsProjectileActive = result
                    == RuntimePermitTransferDisposition.TRANSFERRED;
            var requiredSlots = recordsProjectileActive ? 3 : 2;
            var spawnResolvedOrdinal = P9RuntimeDiagnosticStage.SPAWN_RESOLVED.ordinal();
            if (spawnCommitResultCode != 0
                    || stageCount > stageCodes.length - requiredSlots
                    || spawnResolvedOrdinal <= lastStageOrdinal) {
                throw new IllegalStateException("invalid P9 spawn diagnostic transition");
            }
            // Closed scalar coding of P6 CommitDisposition: 1 APPLIED, 2 NOT_APPLIED.
            spawnCommitResultCode = recordsProjectileActive ? 1 : 2;
            record(P9RuntimeDiagnosticStage.SPAWN_RESOLVED, runtimeTick);
            record(P9RuntimeDiagnosticStage.SPAWN_COMMIT_RESULT, runtimeTick);
            if (recordsProjectileActive) {
                record(P9RuntimeDiagnosticStage.PROJECTILE_ACTIVE, runtimeTick);
            }
        }

        void recordHitClaimResult(
                long permitIdMostSignificantBits,
                long permitIdLeastSignificantBits,
                ProjectileHitCandidateV0 candidate,
                RuntimePermitClaimDisposition result,
                long runtimeTick) {
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(result, "result");
            if ((permitIdMostSignificantBits == 0L
                            && permitIdLeastSignificantBits == 0L)
                    || result == RuntimePermitClaimDisposition.DUPLICATE_OR_LATE) {
                throw new IllegalArgumentException(
                        "invalid authoritative hit diagnostic identity or result");
            }
            var recordsQueuedChild = result == RuntimePermitClaimDisposition.QUEUED;
            var requiredSlots = recordsQueuedChild ? 2 : 1;
            var hitClaimOrdinal = P9RuntimeDiagnosticStage.HIT_CLAIM_RESULT.ordinal();
            if (hitClaimResultCode != 0
                    || stageCount > stageCodes.length - requiredSlots
                    || hitClaimOrdinal <= lastStageOrdinal) {
                throw new IllegalStateException("invalid P9 hit diagnostic transition");
            }
            hitPermitIdMostSignificantBits = permitIdMostSignificantBits;
            hitPermitIdLeastSignificantBits = permitIdLeastSignificantBits;
            hitProjectileIdMostSignificantBits =
                    candidate.projectileId().getMostSignificantBits();
            hitProjectileIdLeastSignificantBits =
                    candidate.projectileId().getLeastSignificantBits();
            hitTargetIdMostSignificantBits = candidate.targetId().getMostSignificantBits();
            hitTargetIdLeastSignificantBits = candidate.targetId().getLeastSignificantBits();
            hitXBits = Double.doubleToRawLongBits(candidate.hitX());
            hitYBits = Double.doubleToRawLongBits(candidate.hitY());
            hitZBits = Double.doubleToRawLongBits(candidate.hitZ());
            hitDirectionXQ15 = candidate.directionXQ15();
            hitDirectionYQ15 = candidate.directionYQ15();
            hitDirectionZQ15 = candidate.directionZQ15();
            hitClaimResultCode = result.ordinal() + 1;
            record(P9RuntimeDiagnosticStage.HIT_CLAIM_RESULT, runtimeTick);
            if (recordsQueuedChild) {
                record(P9RuntimeDiagnosticStage.NODE1_QUEUED, runtimeTick);
            }
        }

        void record(P9RuntimeDiagnosticStage stage, long runtimeTick) {
            var ordinal = stage.ordinal();
            if (stageCount == stageCodes.length
                    || ordinal <= lastStageOrdinal) {
                throw new IllegalStateException("invalid P9 diagnostic stage transition");
            }
            stageCodes[stageCount] = ordinal + 1;
            stageTicks[stageCount] = runtimeTick;
            stageCount++;
            lastStageOrdinal = ordinal;
        }

        void recordErrorDeferredBestEffort(long runtimeTick) {
            throwTerminalFailureForTesting();
            var terminalOrdinal = P9RuntimeDiagnosticStage.TERMINAL_CAUSE.ordinal();
            if (stageCount < stageCodes.length && terminalOrdinal > lastStageOrdinal) {
                stageCodes[stageCount] = terminalOrdinal + 1;
                stageTicks[stageCount] = runtimeTick;
                stageCount++;
                lastStageOrdinal = terminalOrdinal;
            }
            var cleanupOrdinal = P9RuntimeDiagnosticStage.CLEANUP_DISPOSITION.ordinal();
            if (stageCount < stageCodes.length && cleanupOrdinal > lastStageOrdinal) {
                stageCodes[stageCount] = cleanupOrdinal + 1;
                stageTicks[stageCount] = runtimeTick;
                stageCount++;
                lastStageOrdinal = cleanupOrdinal;
                cleanupDisposition = P9RuntimeCleanupDisposition.ERROR_DEFERRED;
            }
        }

        void armTerminalFailureForTesting(boolean throwError) {
            if (terminalPublished || terminalFailureForTesting != 0) {
                throw new IllegalStateException("P9 terminal diagnostic failure already armed");
            }
            terminalFailureForTesting = throwError ? 2 : 1;
        }

        void throwTerminalFailureForTesting() {
            var failure = terminalFailureForTesting;
            terminalFailureForTesting = 0;
            if (failure == 1) {
                throw new IllegalStateException("P9_TEST_TERMINAL_DIAGNOSTIC_RUNTIME");
            }
            if (failure == 2) {
                throw new AssertionError("P9_TEST_TERMINAL_DIAGNOSTIC_ERROR");
            }
        }

    }

    record P9TerminalDiagnostic(
            SkillInstanceId skillInstanceId,
            RuntimeSkillInstanceSequence sequence,
            EventId rootEventId,
            SkillReference exactReference,
            ResourceLocation dimension,
            double originX,
            double originY,
            double originZ,
            int directionXQ15,
            int directionYQ15,
            int directionZQ15,
            int profileCode,
            int spawnCommitResultCode,
            long hitPermitIdMostSignificantBits,
            long hitPermitIdLeastSignificantBits,
            long hitProjectileIdMostSignificantBits,
            long hitProjectileIdLeastSignificantBits,
            long hitTargetIdMostSignificantBits,
            long hitTargetIdLeastSignificantBits,
            long hitXBits,
            long hitYBits,
            long hitZBits,
            int hitDirectionXQ15,
            int hitDirectionYQ15,
            int hitDirectionZQ15,
            int hitClaimResultCode,
            int stageCount,
            int[] stageCodes,
            long[] stageTicks,
            ProjectileClosureReason terminalReason,
            P9RuntimeCleanupDisposition cleanupDisposition) {}

    static final class AttributionState {
        final RuntimeBudgetAttribution attribution;
        int activeInstances;
        int committedPending;
        int reservedPending;
        long executionEpoch = -1L;
        int executionsThisTick;
        RuntimeSkillInstanceSequence smallestContributingSequenceThisTick;

        AttributionState(RuntimeBudgetAttribution attribution) {
            this.attribution = Objects.requireNonNull(attribution, "attribution");
        }
    }

    record BreakerDiagnostic(
            RuntimeCircuitBreakReason reason,
            long runtimeTick,
            SkillInstanceId instanceId,
            Optional<RuntimeSkillInstanceSequence> sequence,
            Optional<EventId> eventId,
            Optional<RuntimePlayerId> playerId,
            int pendingBefore,
            int requested,
            int maximum,
            int removed,
            boolean eventInFlight) {
        BreakerDiagnostic {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(sequence, "sequence");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }

    static final class Diagnostics {
        final BreakerDiagnostic[] breakerRing = new BreakerDiagnostic[
                MagicSafetyCeilings.MAX_BREAKER_DIAGNOSTIC_RECORDS_PER_SERVER];
        final long[] breakerTotals = new long[4];
        int breakerWriteIndex;
        long rootAdmissionAttemptsThisTick;
        long executionAttemptsThisTick;
        long portInvocationsThisTick;
        long typedOutcomesThisTick;
        long breakerTripsThisTick;
        long breakerRemovalsThisTick;
        long instanceDeferralsThisTick;
        long playerDeferralsThisTick;
        long nonPlayerDeferralsThisTick;
        long maximumLagTicksThisTick;
        long deadlineExpiredEventsThisTick;
        long maximumDeadlineLatenessTicksThisTick;
        long deadlineExpiredEventTotal;
        boolean serverExecutionLimitReachedThisTick;
        long serverExecutionLimitReachedTickTotal;
        InstanceOffender instanceOffender;
        AttributionOffender attributionOffender;

        void resetCurrentTick() {
            rootAdmissionAttemptsThisTick = 0;
            executionAttemptsThisTick = 0;
            portInvocationsThisTick = 0;
            typedOutcomesThisTick = 0;
            breakerTripsThisTick = 0;
            breakerRemovalsThisTick = 0;
            instanceDeferralsThisTick = 0;
            playerDeferralsThisTick = 0;
            nonPlayerDeferralsThisTick = 0;
            maximumLagTicksThisTick = 0;
            deadlineExpiredEventsThisTick = 0;
            maximumDeadlineLatenessTicksThisTick = 0;
            serverExecutionLimitReachedThisTick = false;
            instanceOffender = null;
            attributionOffender = null;
        }

        void observeInstanceOffender(InstanceState instance) {
            var candidate = new InstanceOffender(instance.id, instance.sequence, instance.executionsThisTick);
            if (instanceOffender == null
                    || SkillRuntimeService.preferOffender(
                            candidate.count,
                            candidate.sequence.value(),
                            instanceOffender.count,
                            instanceOffender.sequence.value())) {
                instanceOffender = candidate;
            }
        }

        void observeAttributionOffender(AttributionState attribution) {
            var sequence = Objects.requireNonNull(
                    attribution.smallestContributingSequenceThisTick,
                    "smallestContributingSequenceThisTick");
            var candidate = new AttributionOffender(
                    attribution.attribution, sequence, attribution.executionsThisTick);
            if (attributionOffender == null
                    || SkillRuntimeService.preferOffender(
                            candidate.count,
                            candidate.sequence.value(),
                            attributionOffender.count,
                            attributionOffender.sequence.value())) {
                attributionOffender = candidate;
            }
        }

        void clear() {
            Arrays.fill(breakerRing, null);
            Arrays.fill(breakerTotals, 0L);
            breakerWriteIndex = 0;
            deadlineExpiredEventTotal = 0;
            serverExecutionLimitReachedTickTotal = 0;
            resetCurrentTick();
        }

        void clearAfterFaultPreservingStartedCounters() {
            var startedExecutions = executionAttemptsThisTick;
            var enteredPorts = portInvocationsThisTick;
            clear();
            executionAttemptsThisTick = startedExecutions;
            portInvocationsThisTick = enteredPorts;
        }
    }

    private record InstanceOffender(
            SkillInstanceId id, RuntimeSkillInstanceSequence sequence, int count) {}

    private record AttributionOffender(
            RuntimeBudgetAttribution attribution,
            RuntimeSkillInstanceSequence sequence,
            int count) {}
}

/** One shared exact-revision transient pin and immutable runtime projection. */
final class RuntimeRevisionLease {
    final SkillReference reference;
    final ControlledSkillPin pin;
    final ValidatedSkillDefinition definition;
    private int instanceReferences;
    private boolean closed;

    RuntimeRevisionLease(
            SkillReference reference,
            ControlledSkillPin pin,
            ValidatedSkillDefinition definition) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.pin = Objects.requireNonNull(pin, "pin");
        this.definition = Objects.requireNonNull(definition, "definition");
        if (!reference.equals(definition.reference()) || !reference.equals(pin.reference())) {
            throw new IllegalArgumentException("runtime revision lease reference mismatch");
        }
    }

    void retain() {
        if (closed || instanceReferences == Integer.MAX_VALUE) {
            throw new RuntimeKernelException(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
        }
        instanceReferences++;
    }

    boolean release() {
        if (closed || instanceReferences <= 0) {
            throw new RuntimeKernelException(RuntimeKernelException.Code.LEASE_ACCOUNTING_INVARIANT);
        }
        instanceReferences--;
        if (instanceReferences == 0) {
            close();
            return true;
        }
        return false;
    }

    void close() {
        if (!closed) {
            pin.close();
            closed = true;
            instanceReferences = 0;
        }
    }
}
