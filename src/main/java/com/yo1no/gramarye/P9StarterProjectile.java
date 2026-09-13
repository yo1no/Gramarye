package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/** One bounded, nonpersistent server-authoritative starter projectile. */
final class P9StarterProjectile extends ThrowableItemProjectile {
    private final RuntimeProjectileContinuationPermit continuationPermit;
    private final transient ServerPlayer authenticatedCasterIdentity;
    private final ResourceLocation dimension;
    private boolean locallyClaimedOrTerminal;
    private double accumulatedTravelDistance;

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
        var origin = BlockPos.containing(
                geometry.originX(), geometry.originY(), geometry.originZ());
        if (actor.serverLevel() != level
                || actor.getServer() != level.getServer()
                || actor.isRemoved()
                || !actor.isAlive()
                || !geometry.dimension().equals(level.dimension().location())
                || !level.isInWorldBounds(origin)
                || !level.isLoaded(origin)
                || !level.getWorldBorder().isWithinBounds(
                        geometry.originX(), geometry.originZ())
                || permit.mode != RuntimeProjectileContinuationPermit.Mode.REAL
                || permit.state != RuntimeProjectileContinuationPermit.State.RESERVED
                || !permit.dimension.equals(geometry.dimension())
                || !permit.plannedProjectileId.equals(
                        openedContinuation.plannedProjectileId())) {
            throw new IllegalArgumentException("invalid authoritative projectile construction");
        }

        this.continuationPermit = permit;
        this.authenticatedCasterIdentity = actor;
        this.dimension = geometry.dimension();
        setUUID(openedContinuation.plannedProjectileId());
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

    @Override
    public void tick() {
        if (level().isClientSide()) {
            super.tick();
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
        if (!validServerState(serverLevel)) {
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

            checkInsideBlocks();
            updateRotation();
            var drag = isInWater() ? 0.8 : 0.99;
            setDeltaMovement(movement.scale(drag));
            applyGravity();
            setPos(endpoint);
        } catch (RuntimeException failure) {
            locallyClaimedOrTerminal = true;
            bestEffortClose(serverLevel, ProjectileClosureReason.RUNTIME_FAULT);
            bestEffortDiscard();
            throw failure;
        } catch (Error failure) {
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
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide() || locallyClaimedOrTerminal) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel)) {
            terminate(ProjectileClosureReason.ENTITY_OR_LEVEL_REMOVED);
            return;
        }

        Entity target = Objects.requireNonNull(hit, "hit").getEntity();
        if (!(target instanceof LivingEntity living)
                || target == authenticatedCasterIdentity
                || target.isRemoved()
                || !target.isAddedToLevel()
                || !living.isAlive()
                || target.level() != serverLevel
                || !serverLevel.dimension().location().equals(dimension)
                || !validServerState(serverLevel)) {
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

        locallyClaimedOrTerminal = true;
        RuntimePermitClaimDisposition disposition;
        try {
            disposition = continuationPermit.claimLoadedEntityHit(
                    serverLevel.getServer(),
                    new ProjectileHitCandidateV0(
                            getUUID(),
                            target.getUUID(),
                            dimension,
                            hitPosition.x,
                            hitPosition.y,
                            hitPosition.z,
                            direction[0],
                            direction[1],
                            direction[2]));
        } catch (RuntimeException failure) {
            bestEffortClose(serverLevel, ProjectileClosureReason.RUNTIME_FAULT);
            bestEffortDiscard();
            throw failure;
        } catch (Error failure) {
            throw failure;
        }
        if (disposition == RuntimePermitClaimDisposition.QUEUED) {
            try {
                setPos(hitPosition);
                setDeltaMovement(Vec3.ZERO);
                setNoGravity(true);
            } catch (RuntimeException failure) {
                bestEffortClose(serverLevel, ProjectileClosureReason.RUNTIME_FAULT);
                bestEffortDiscard();
                throw failure;
            } catch (Error failure) {
                throw failure;
            }
        } else {
            closeAndDiscard(serverLevel, ProjectileClosureReason.CLAIM_REJECTED);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        Objects.requireNonNull(hit, "hit");
        if (!level().isClientSide() && !locallyClaimedOrTerminal) {
            terminate(ProjectileClosureReason.BLOCK_OR_INVALID_HIT);
        }
    }

    @Override
    public void onRemovedFromLevel() {
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

    boolean hasAuthenticatedCasterIdentity(ServerPlayer candidate) {
        return authenticatedCasterIdentity == candidate;
    }

    boolean hasContinuationPermitIdentity(RuntimeProjectileContinuationPermit candidate) {
        return continuationPermit == candidate;
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
                && server.getPlayerList().getPlayer(authenticatedCasterIdentity.getUUID())
                        == authenticatedCasterIdentity
                && !authenticatedCasterIdentity.isRemoved()
                && authenticatedCasterIdentity.isAlive()
                && authenticatedCasterIdentity.connection != null
                && authenticatedCasterIdentity.connection.isAcceptingMessages()
                && continuationPermit.mode
                        == RuntimeProjectileContinuationPermit.Mode.REAL
                && continuationPermit.state
                        == RuntimeProjectileContinuationPermit.State.OPEN
                && getOwner() == authenticatedCasterIdentity;
    }

    private void terminate(ProjectileClosureReason reason) {
        if (locallyClaimedOrTerminal || level().isClientSide()) {
            return;
        }
        locallyClaimedOrTerminal = true;
        if (level() instanceof ServerLevel serverLevel) {
            closeAndDiscard(serverLevel, reason);
        } else {
            bestEffortDiscard();
        }
    }

    private void closeAndDiscard(ServerLevel serverLevel, ProjectileClosureReason reason) {
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
