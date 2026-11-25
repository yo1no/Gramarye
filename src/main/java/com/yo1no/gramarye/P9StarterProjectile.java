package com.yo1no.gramarye;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** One bounded, nonpersistent server-authoritative starter projectile. */
final class P9StarterProjectile extends ThrowableItemProjectile {
    private final RuntimeProjectileContinuationPermit continuationPermit;
    private final transient ServerPlayer authenticatedCasterIdentity;
    private final ResourceLocation dimension;
    private final SkillRuntimeService.V0CarrierBinding v0Binding;
    private Vec3 v0ExpectedPosition;
    private Vec3 v0ExpectedVelocity;
    private boolean v0InsertionArmed;
    private boolean v0Inserted;
    private boolean v0Closed;
    private int v0Age;
    private double v0Range;
    private long v0LastSweep = Long.MIN_VALUE;
    private boolean locallyClaimedOrTerminal;
    private double accumulatedTravelDistance;
    private ProjectileHitCandidateV0 observedHit;
    private long observedHitRuntimeTick = -1;
    private int observedTargetEntityId = -1;

    P9StarterProjectile(
            EntityType<? extends ThrowableItemProjectile> type,
            Level level) {
        super(Objects.requireNonNull(type, "type"), Objects.requireNonNull(level, "level"));
        if (!level.isClientSide()) {
            throw new IllegalStateException(
                    "the platform projectile factory is client-side only");
        }
        this.continuationPermit = new RuntimeProjectileContinuationPermit();
        this.authenticatedCasterIdentity = null;
        this.dimension = level.dimension().location();
        this.v0Binding = null;
    }

    P9StarterProjectile(
            EntityType<? extends ThrowableItemProjectile> type,
            ServerLevel level,
            ServerPlayer actor,
            RuntimeProjectileContinuationOpenResult.Opened openedContinuation,
            CastGeometryExecutionDataV0 geometry) {
        super(Objects.requireNonNull(type, "type"), Objects.requireNonNull(level, "level"));
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(openedContinuation, "openedContinuation");
        Objects.requireNonNull(geometry, "geometry");
        var permit = openedContinuation.permit();
        this.continuationPermit = permit;
        this.authenticatedCasterIdentity = actor;
        this.dimension = geometry.dimension();
        this.v0Binding = null;
        setUUID(openedContinuation.plannedProjectileId());
        var origin = BlockPos.containing(
                geometry.originX(), geometry.originY(), geometry.originZ());
        if (actor.serverLevel() != level
                || actor.getServer() != level.getServer()
                || !geometry.dimension().equals(level.dimension().location())
                || !level.isInWorldBounds(origin)
                || !level.isLoaded(origin)
                || !level.getWorldBorder().isWithinBounds(
                        geometry.originX(), geometry.originZ())
                || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || permit.state != RuntimeProjectileContinuationPermit.State.RESERVED
                || !permit.dimension.equals(geometry.dimension())
                || !permit.plannedProjectileId.equals(
                        openedContinuation.plannedProjectileId())
                || permit.qualifiedActor(level.getServer(), this) != actor) {
            throw new IllegalArgumentException("invalid authoritative projectile construction");
        }

        setOwner(actor);
        setPos(geometry.originX(), geometry.originY(), geometry.originZ());

        var decoded = new Vec3(
                geometry.directionXQ15() / 32_767.0,
                geometry.directionYQ15() / 32_767.0,
                geometry.directionZQ15() / 32_767.0);
        var length = decoded.length();
        if (!Double.isFinite(length) || length <= 0.0) {
            throw new IllegalArgumentException("invalid authoritative projectile direction");
        }
        var initialMovement = decoded.scale(1.5 / length);
        if (!finite(initialMovement)) {
            throw new IllegalArgumentException("invalid authoritative projectile motion");
        }
        setDeltaMovement(initialMovement);
    }

    P9StarterProjectile(EntityType<? extends ThrowableItemProjectile> type, ServerLevel level,
            SkillRuntimeService.V0CarrierBinding binding, V0SpawnGeometry geometry) {
        super(Objects.requireNonNull(type, "type"), Objects.requireNonNull(level, "level"));
        continuationPermit = null;
        authenticatedCasterIdentity = null;
        dimension = level.dimension().location();
        v0Binding = Objects.requireNonNull(binding, "binding");
        if (geometry.level() != level || !finite(geometry.origin())) {
            throw new IllegalArgumentException("Invalid exact V0 projectile geometry");
        }
        var velocity = v0InitialVelocity(geometry.directionXQ15(), geometry.directionYQ15(),
                geometry.directionZQ15(), geometry.launchStepMilli());
        if (velocity == null) {
            throw new IllegalArgumentException("Invalid V0 projectile wire velocity");
        }
        v0ExpectedPosition = geometry.origin();
        v0ExpectedVelocity = velocity;
        setPos(v0ExpectedPosition);
        setNoGravity(true);
        // Native old-pose and motion initialization match the client add/data/motion bundle.
        lerpMotion(velocity.x, velocity.y, velocity.z);
    }

    static Vec3 v0InitialVelocity(int directionXQ15, int directionYQ15, int directionZQ15,
            int launchStepMilli) {
        if (directionXQ15 < -32_767 || directionXQ15 > 32_767
                || directionYQ15 < -32_767 || directionYQ15 > 32_767
                || directionZQ15 < -32_767 || directionZQ15 > 32_767
                || launchStepMilli < 1 || launchStepMilli > 1_000) {
            return null;
        }
        var direction = new Vec3(directionXQ15 / 32_767.0, directionYQ15 / 32_767.0,
                directionZQ15 / 32_767.0);
        double length = direction.length();
        if (length == 0.0) {
            return null;
        }
        var raw = direction.scale((launchStepMilli / 1_000.0) / length);
        var velocity = new Vec3(v0WireComponent(raw.x), v0WireComponent(raw.y), v0WireComponent(raw.z));
        return finite(velocity) && velocity.lengthSqr() > 0.0 && velocity.lengthSqr() <= 1.0
                ? velocity : null;
    }

    private static double v0WireComponent(double value) {
        int bin = (int) (value * 8_000.0);
        double decoded = bin / 8_000.0;
        if ((int) (decoded * 8_000.0) != bin) {
            bin -= Integer.signum(bin);
            decoded = bin / 8_000.0;
        }
        return (int) (decoded * 8_000.0) == bin ? decoded : Double.NaN;
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
            return;
        }
        if (v0Binding != null) {
            tickV0();
            return;
        }
        if (continuationPermit.isPreparedSpawn()) {
            return;
        }
        if (locallyClaimedOrTerminal) {
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            terminate(ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED);
            return;
        }
        if (tickCount > 100) {
            terminate(ProjectileClosureReason.AGE_EXHAUSTED);
            return;
        }
        if (observedHit != null) {
            if (continuationPermit.qualification(serverLevel.getServer(), this)
                    == SkillRuntimeService.WorkQualification.INVALID) {
                terminate(ProjectileClosureReason.OWNER_INVALIDATED);
            }
            return;
        }
        if (SkillRuntimeService.awaitingNativeCleanup(
                continuationPermit.qualification(serverLevel.getServer(), this))) {
            return;
        }
        if (!validServerState(serverLevel)) {
            if (SkillRuntimeService.awaitingNativeCleanup(
                    continuationPermit.qualification(serverLevel.getServer(), this))) {
                return;
            }
            terminate(ProjectileClosureReason.OWNER_INVALIDATED);
            return;
        }

        try {
            if (!this.hasBeenShot) {
                this.gameEvent(GameEvent.PROJECTILE_SHOOT, this.getOwner());
                this.hasBeenShot = true;
            }

            if (!this.leftOwner) {
                this.leftOwner = this.checkLeftOwner();
            }

            baseTick();
            if (locallyClaimedOrTerminal || isRemoved()) {
                setDeltaMovement(Vec3.ZERO);
                setNoGravity(true);
                return;
            }

            var movement = getDeltaMovement();
            var segmentLength = movement.length();
            if (!finite(position())
                    || !finite(movement)
                    || !Double.isFinite(segmentLength)
                    || segmentLength <= 0.0
                    || !Double.isFinite(accumulatedTravelDistance)
                    || accumulatedTravelDistance < 0.0
                    || accumulatedTravelDistance > 64.0) {
                terminate(ProjectileClosureReason.RUNTIME_FAULT);
                return;
            }
            var remaining = 64.0 - accumulatedTravelDistance;
            if (!Double.isFinite(remaining) || remaining <= 0.0) {
                terminate(ProjectileClosureReason.RANGE_EXHAUSTED);
                return;
            }
            if (segmentLength > remaining) {
                movement = movement.scale(remaining / segmentLength);
                segmentLength = remaining;
                setDeltaMovement(movement);
            }
            var endpoint = position().add(movement);
            if (!finite(endpoint)
                    || !legalPosition(endpoint)
                    || !loadedPosition(serverLevel, position())
                    || !loadedPosition(serverLevel, endpoint)) {
                terminate(ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED);
                return;
            }

            var advanced = accumulatedTravelDistance + segmentLength;
            if (!Double.isFinite(advanced)
                    || advanced <= accumulatedTravelDistance
                    || advanced > 64.0) {
                terminate(ProjectileClosureReason.RUNTIME_FAULT);
                return;
            }
            accumulatedTravelDistance = advanced;

            var hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS
                    && !EventHooks.onProjectileImpact(this, hit)) {
                setDeltaMovement(movement);
                if (hit instanceof EntityHitResult entityHit) {
                    onHitEntity(entityHit);
                } else if (hit instanceof BlockHitResult blockHit) {
                    onHitBlock(blockHit);
                }
            }

            if (locallyClaimedOrTerminal || isRemoved()) {
                setDeltaMovement(Vec3.ZERO);
                setNoGravity(true);
                return;
            }

            if (observedHit != null) { return; }

            checkInsideBlocks();
            updateRotation();
            var drag = isInWater() ? 0.8 : 0.99;
            setDeltaMovement(movement.scale(drag));
            applyGravity();
            setPos(endpoint);
        } catch (RuntimeException failure) {
            clearObservedHit();
            locallyClaimedOrTerminal = true;
            bestEffortClose(serverLevel, ProjectileClosureReason.RUNTIME_FAULT);
            bestEffortDiscard();
            throw failure;
        } catch (Error failure) {
            clearObservedHit();
            locallyClaimedOrTerminal = true;
            throw failure;
        }

        if (locallyClaimedOrTerminal) {
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            return;
        }
        if (accumulatedTravelDistance >= 64.0) {
            terminate(ProjectileClosureReason.RANGE_EXHAUSTED);
        }
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    @Override
    public Entity getOwner() {
        if (level().isClientSide()) {
            return super.getOwner();
        }
        if (v0Binding != null) {
            return v0Closed ? null : v0Binding.qualifiedActor(this);
        }
        return level() instanceof ServerLevel serverLevel && continuationPermit != null
                ? continuationPermit.qualifiedActor(serverLevel.getServer(), this)
                : null;
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide() || v0Binding != null || continuationPermit.isPreparedSpawn()
                || locallyClaimedOrTerminal || observedHit != null) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            terminate(ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED);
            return;
        }
        Entity target = Objects.requireNonNull(hit, "hit").getEntity();
        if (!(target instanceof LivingEntity living)
                || authenticatedCasterIdentity == null
                || target.getUUID().equals(authenticatedCasterIdentity.getUUID())
                || target.isRemoved()
                || !target.isAddedToLevel()
                || !living.isAlive()
                || target.level() != serverLevel
                || !serverLevel.dimension().location().equals(dimension)
                || continuationPermit.qualification(serverLevel.getServer(), this)
                        == SkillRuntimeService.WorkQualification.INVALID) {
            terminate(ProjectileClosureReason.BLOCK_OR_INVALID_HIT);
            return;
        }

        var hitPosition = hit.getLocation();
        var movement = getDeltaMovement();
        var direction = q15(movement);
        if (!finite(hitPosition) || !legalPosition(hitPosition) || direction == null) {
            terminate(ProjectileClosureReason.BLOCK_OR_INVALID_HIT);
            return;
        }

        observedHit = new ProjectileHitCandidateV0(
                getUUID(), target.getUUID(), dimension, hitPosition.x, hitPosition.y, hitPosition.z,
                direction[0], direction[1], direction[2]);
        observedTargetEntityId = target.getId();
        submitObservedHit(serverLevel);
    }

    boolean hasObservedHit(RuntimeProjectileContinuationPermit permit, ProjectileHitCandidateV0 candidate) {
        return continuationPermit == permit && observedHit != null && observedHit == candidate
                && !locallyClaimedOrTerminal && !isRemoved();
    }

    boolean hasObservedTarget(RuntimeProjectileContinuationPermit permit,
            ProjectileHitCandidateV0 candidate, Entity target) {
        return hasObservedHit(permit, candidate) && target != null
                && target.getId() == observedTargetEntityId && target.getUUID().equals(candidate.targetId());
    }

    void clearObservedHit(RuntimeProjectileContinuationPermit permit) {
        if (continuationPermit == permit) { clearObservedHit(); }
    }

    void observeHitTick(RuntimeProjectileContinuationPermit permit, ProjectileHitCandidateV0 candidate, long tick) {
        if (!hasObservedHit(permit, candidate) || tick < 0
                || observedHitRuntimeTick > tick) {
            throw new IllegalStateException("invalid original P9 observed hit custody");
        }
        if (observedHitRuntimeTick == -1) { observedHitRuntimeTick = tick; }
    }

    void resumeObservedHit(RuntimeProjectileContinuationPermit permit) {
        if (observedHit == null || continuationPermit != permit) { return; }
        if (!(level() instanceof ServerLevel serverLevel) || !serverLevel.getServer().isSameThread()) {
            throw new IllegalStateException("invalid original P9 observed hit thread");
        }
        if (tickCount > 100) {
            terminate(ProjectileClosureReason.AGE_EXHAUSTED);
            return;
        }
        submitObservedHit(serverLevel);
    }

    private void submitObservedHit(ServerLevel serverLevel) {
        var candidate = observedHit;
        try {
            var result = continuationPermit.submitObservedHit(serverLevel.getServer(), this, candidate);
            if (result.isEmpty()) { return; }
            clearObservedHit();
            locallyClaimedOrTerminal = true;
            if (result.orElseThrow() == RuntimePermitClaimDisposition.QUEUED) {
                setPos(candidate.hitX(), candidate.hitY(), candidate.hitZ());
                setDeltaMovement(Vec3.ZERO);
                setNoGravity(true);
            } else {
                closeAndDiscard(serverLevel, ProjectileClosureReason.CLAIM_REJECTED);
            }
        } catch (RuntimeException failure) {
            clearObservedHit();
            locallyClaimedOrTerminal = true;
            bestEffortClose(serverLevel, ProjectileClosureReason.RUNTIME_FAULT);
            bestEffortDiscard();
            throw failure;
        } catch (Error failure) {
            clearObservedHit();
            locallyClaimedOrTerminal = true;
            throw failure;
        }
    }

    private void clearObservedHit() {
        observedHit = null;
        observedHitRuntimeTick = -1;
        observedTargetEntityId = -1;
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        Objects.requireNonNull(hit, "hit");
        if (v0Binding != null || continuationPermit.isPreparedSpawn()) { return; }
        if (!level().isClientSide() && !locallyClaimedOrTerminal) {
            terminate(ProjectileClosureReason.BLOCK_OR_INVALID_HIT);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        if (v0Binding != null) {
            super.onRemovedFromLevel();
            closeV0(V0TerminalReason.ENTITY_UNLOADED);
            return;
        }
        clearObservedHit();
        var serverLevel = level() instanceof ServerLevel exactLevel ? exactLevel : null;
        try {
            super.onRemovedFromLevel();
        } catch (RuntimeException failure) {
            if (serverLevel != null) {
                locallyClaimedOrTerminal = true;
                bestEffortClose(
                        serverLevel, ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED);
            }
            throw failure;
        } catch (Error failure) {
            if (serverLevel != null) {
                locallyClaimedOrTerminal = true;
            }
            throw failure;
        }
        if (serverLevel != null) {
            locallyClaimedOrTerminal = true;
            continuationPermit.closeWithoutHit(
                    serverLevel.getServer(),
                    ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED);
        }
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback callback) {
        if (v0InsertionArmed && callback != EntityInLevelCallback.NULL
                && level() instanceof ServerLevel exactLevel && exactLevel.getServer().isSameThread()) {
            v0Inserted = true;
        }
        super.setLevelCallback(callback);
    }

    void armV0InsertionWitness() {
        if (v0Binding == null || v0InsertionArmed || v0Inserted || !v0Binding.adding(this)) {
            throw new IllegalStateException("Invalid V0 native insertion witness");
        }
        // This one exact candidate can reach insertion after a synchronous eligibility fence.
        v0InsertionArmed = true;
    }

    void completeV0NativeAdd() {
        v0InsertionArmed = false;
    }

    boolean v0EverInserted() {
        return v0Inserted;
    }

    boolean guardV0PositionChunk(Level world, int chunkX, int chunkZ) {
        if (v0Binding == null || level().isClientSide()) {
            return false;
        }
        if (!v0Closed && level() instanceof ServerLevel serverLevel) {
            if (!serverLevel.getServer().isSameThread()) {
                throw new IllegalStateException("Wrong thread for V0 projectile movement");
            }
            if (world != serverLevel || !position().equals(v0ExpectedPosition)
                    || P9V0LoadedBlocks.completedChunk(serverLevel, chunkX, chunkZ) == null) {
                closeV0(V0TerminalReason.CHUNK_UNLOADED);
            }
        }
        return true;
    }

    boolean v0ClosedForTrackingDiscard() {
        return v0Binding != null && v0Closed && v0Inserted && !v0InsertionArmed && !isRemoved()
                && level() instanceof ServerLevel serverLevel && serverLevel.getServer().isSameThread();
    }

    void closeV0(V0TerminalReason reason) {
        if (v0Binding == null) {
            return;
        }
        if (v0Closed) {
            P11P9TrackingCleanup.discardClosedV0(this);
            return;
        }
        v0Closed = true;
        boolean finished = false;
        try {
            v0Binding.finish(this, V0CarrierTerminal.of(reason));
            finished = true;
        } finally {
            P9V0WorldEffectHandoff.discardAfterV0Close(this, finished);
        }
    }

    private void tickV0() {
        if (v0Closed) {
            return;
        }
        var qualification = v0Binding.qualification(this);
        if (qualification == V0WorkQualification.ADDING || qualification == V0WorkQualification.CLAIMED
                || qualification == V0WorkQualification.PENDING_NATIVE_CLEANUP) {
            return;
        }
        if (qualification != V0WorkQualification.EXECUTABLE || !(level() instanceof ServerLevel exactLevel)) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        int tick = exactLevel.getServer().getTickCount();
        if (tick == v0LastSweep) {
            return;
        }
        v0LastSweep = tick;
        P9V0WorldEffectHandoff.moveCarrier(this, exactLevel);
    }

    void stepV0(ServerLevel exactLevel) {
        if (!v0MotionCurrent() || !isAddedToLevel() || isRemoved()
                || exactLevel.getEntity(getId()) != this || exactLevel.getEntity(getUUID()) != this) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        if (v0Age >= v0Binding.maxAgeTicks()) {
            closeV0(V0TerminalReason.NATURAL_AGE);
            return;
        }
        var start = v0ExpectedPosition;
        var movement = v0ExpectedVelocity;
        var end = start.add(movement);
        double advanced = v0Range + movement.length();
        if (!Double.isFinite(advanced) || advanced <= v0Range || advanced > v0Binding.maxRangeBlocks()) {
            closeV0(V0TerminalReason.NATURAL_RANGE);
            return;
        }
        var query = getBoundingBox().expandTowards(movement).inflate(1.0);
        var blocks = P9V0LoadedBlocks.capture(exactLevel, query.inflate(1.0E-7)).orElse(null);
        if (blocks == null || !blocks.dryPath(start, end)
                || !exactLevel.getWorldBorder().isWithinBounds(end.x, end.z)) {
            closeV0(V0TerminalReason.CHUNK_UNLOADED);
            return;
        }
        var actor = v0Binding.qualifiedActor(this);
        if (actor == null) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        HitResult hit = blocks.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Predicate<Entity> target = candidate -> !candidate.getUUID().equals(actor.getUUID())
                && eligibleV0Target(exactLevel, candidate) && canHitEntity(candidate);
        EntityHitResult entityHit;
        if (v0Binding.normalPolicy()) {
            var result = U0BoundedEntityQuery.query(exactLevel, this, start, hit.getLocation(), query, target);
            if (!result.qualified) {
                closeV0(V0TerminalReason.OWNER_INVALIDATED);
                return;
            }
            entityHit = result.hit;
        } else {
            entityHit = ProjectileUtil.getEntityHitResult(exactLevel, this, start, hit.getLocation(), query,
                    target, 0.3F);
        }
        if (entityHit != null) {
            hit = entityHit;
        }
        v0Age++;
        v0Range = advanced;
        if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
            if (v0Closed || !v0MotionCurrent()) {
                closeV0(V0TerminalReason.OWNER_INVALIDATED);
                return;
            }
            if (hit instanceof EntityHitResult entityImpact) {
                observeV0Impact(exactLevel, entityImpact);
            } else {
                closeV0(v0Binding.qualification(this) == V0WorkQualification.EXECUTABLE
                        ? V0TerminalReason.NATURAL_BLOCK : V0TerminalReason.OWNER_INVALIDATED);
            }
            return;
        }
        if (v0Closed || !v0MotionCurrent()) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        if (v0Binding.qualification(this) == V0WorkQualification.PENDING_NATIVE_CLEANUP) {
            if (hit.getType() != HitResult.Type.MISS) {
                // No continuation may replay the impact hook for this canceled segment.
                closeV0(V0TerminalReason.OWNER_INVALIDATED);
            }
            return;
        }
        if (v0Binding.qualification(this) != V0WorkQualification.EXECUTABLE) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        // The same native dry, zero-gravity full step as the client. No baseTick/block callbacks.
        updateRotation();
        v0ExpectedVelocity = movement.scale((double) 0.99F);
        setDeltaMovement(v0ExpectedVelocity);
        v0ExpectedPosition = end;
        setPos(end);
        if (v0Age == v0Binding.maxAgeTicks()) {
            closeV0(V0TerminalReason.NATURAL_AGE);
        }
    }

    private void observeV0Impact(ServerLevel exactLevel, EntityHitResult hit) {
        if (!eligibleV0Target(exactLevel, hit.getEntity()) || !finite(hit.getLocation())
                || !P9V0LoadedBlocks.dryCell(exactLevel, BlockPos.containing(hit.getLocation()))) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return;
        }
        var disposition = v0Binding.observeImpact(this, hit);
        if (disposition == V0ClaimDisposition.REJECTED) {
            closeV0(V0TerminalReason.OWNER_INVALIDATED);
        } else if (disposition == V0ClaimDisposition.QUEUED) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private boolean v0MotionCurrent() {
        return !isPassenger() && !isVehicle() && isNoGravity()
                && v0ExpectedPosition.equals(position()) && v0ExpectedVelocity.equals(getDeltaMovement());
    }

    boolean normalQueryCurrent(ServerLevel exactLevel, Vec3 start, AABB query) {
        return v0Binding != null && v0Binding.normalPolicy() && !v0Closed && level() == exactLevel
                && exactLevel.getServer().isSameThread() && exactLevel.getServer().getTickCount() == v0LastSweep
                && isAddedToLevel() && !isRemoved() && exactLevel.getEntity(getId()) == this
                && exactLevel.getEntity(getUUID()) == this && v0MotionCurrent()
                && v0Binding.qualification(this) == V0WorkQualification.EXECUTABLE
                && v0Binding.qualifiedActor(this) != null && v0ExpectedPosition.equals(start)
                && getBoundingBox().expandTowards(v0ExpectedVelocity).inflate(1.0).equals(query);
    }

    static boolean eligibleV0Target(ServerLevel exactLevel, Entity candidate) {
        if (candidate == null || (candidate.getClass() != Pig.class && candidate.getClass() != ServerPlayer.class)
                || !(candidate instanceof LivingEntity living) || !living.isAlive() || candidate.isRemoved()
                || !candidate.isAddedToLevel() || candidate.level() != exactLevel || candidate.isPassenger()
                || candidate.isVehicle() || exactLevel.getEntity(candidate.getId()) != candidate
                || exactLevel.getEntity(candidate.getUUID()) != candidate
                || !P9V0LoadedBlocks.dryCell(exactLevel, candidate.blockPosition())) {
            return false;
        }
        AABB footprint = candidate.getBoundingBox();
        return P9V0LoadedBlocks.capture(exactLevel, footprint).isPresent();
    }

    boolean hasAuthenticatedCasterIdentity(ServerPlayer candidate) {
        return authenticatedCasterIdentity == candidate;
    }

    boolean hasContinuationPermitIdentity(RuntimeProjectileContinuationPermit candidate) {
        return continuationPermit == candidate;
    }

    ServerPlayer actorWitness(RuntimeProjectileContinuationPermit expected) {
        return continuationPermit == expected ? authenticatedCasterIdentity : null;
    }

    // Native tracking loss may leave this object in a non-ticking section. Only
    // an already-closed exact work may be discarded after that traversal unwinds.
    boolean closedForTrackingRemoval() {
        return level() instanceof ServerLevel serverLevel
                && serverLevel.getServer().isSameThread()
                && authenticatedCasterIdentity != null
                && authenticatedCasterIdentity.getServer() == serverLevel.getServer()
                && serverLevel.dimension().location().equals(dimension)
                && continuationPermit.mode == RuntimeProjectileContinuationPermit.Mode.REAL
                && continuationPermit.plannedProjectileId.equals(getUUID())
                && (continuationPermit.state == RuntimeProjectileContinuationPermit.State.CLOSED_NO_HIT
                        || continuationPermit.state == RuntimeProjectileContinuationPermit.State.CLOSED_AFTER_HIT)
                && !isRemoved();
    }

    private boolean validServerState(ServerLevel serverLevel) {
        var server = serverLevel.getServer();
        return server.isSameThread()
                && server.isRunning()
                && !server.isStopped()
                && serverLevel.dimension().location().equals(dimension)
                && authenticatedCasterIdentity != null
                && authenticatedCasterIdentity.getServer() == server
                && authenticatedCasterIdentity.serverLevel() == serverLevel
                && continuationPermit.mode
                        == RuntimeProjectileContinuationPermit.Mode.REAL
                && continuationPermit.state
                        == RuntimeProjectileContinuationPermit.State.OPEN
                && continuationPermit.qualifiedActor(server, this)
                        == authenticatedCasterIdentity;
    }

    private void terminate(ProjectileClosureReason reason) {
        if (locallyClaimedOrTerminal || level().isClientSide()) {
            return;
        }
        clearObservedHit();
        locallyClaimedOrTerminal = true;
        if (level() instanceof ServerLevel serverLevel) {
            closeAndDiscard(serverLevel, reason);
        } else {
            bestEffortDiscard();
        }
    }

    private void closeAndDiscard(ServerLevel serverLevel, ProjectileClosureReason reason) {
        clearObservedHit();
        try {
            continuationPermit.closeWithoutHit(serverLevel.getServer(), reason);
        } catch (RuntimeException failure) {
            bestEffortDiscard();
            throw failure;
        } catch (Error failure) {
            throw failure;
        }
        discard();
    }

    private void bestEffortClose(ServerLevel serverLevel, ProjectileClosureReason reason) {
        try {
            continuationPermit.closeWithoutHit(serverLevel.getServer(), reason);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Preserve the already-caught primary platform/runtime failure.
        }
    }

    private void bestEffortDiscard() {
        try {
            discard();
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Preserve the already-caught primary platform/runtime failure.
        }
    }

    private static boolean loadedPosition(ServerLevel level, Vec3 position) {
        var blockPosition = BlockPos.containing(position);
        return level.isInWorldBounds(blockPosition)
                && level.isLoaded(blockPosition)
                && level.getWorldBorder().isWithinBounds(position.x, position.z);
    }

    private static boolean legalPosition(Vec3 position) {
        return position.x >= -30_000_000.0
                && position.x < 30_000_000.0
                && position.y >= -20_000_000.0
                && position.y < 20_000_000.0
                && position.z >= -30_000_000.0
                && position.z < 30_000_000.0;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x)
                && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }

    private static int[] q15(Vec3 movement) {
        if (!finite(movement)) {
            return null;
        }
        var length = movement.length();
        if (!Double.isFinite(length) || length <= 0.0) {
            return null;
        }
        var normalized = movement.scale(1.0 / length);
        var x = q15Component(normalized.x);
        var y = q15Component(normalized.y);
        var z = q15Component(normalized.z);
        return x == 0 && y == 0 && z == 0 ? null : new int[] {x, y, z};
    }

    private static int q15Component(double component) {
        return (int) Math.max(
                -32_767L,
                Math.min(32_767L, (long) StrictMath.rint(component * 32_767.0)));
    }
}
