package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.CommitDisposition;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.WorldCommitPort;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Call-scoped root handoff from the pure P6 transaction to the platform world. */
final class P9WorldEffectHandoff implements WorldCommitPort {
    private final MinecraftServer server;
    private final ServerPlayer actor;
    private final RuntimeExecutionData executionData;
    private final Optional<RuntimeProjectileContinuationOpenResult.Opened>
            openedContinuation;

    P9WorldEffectHandoff(
            MinecraftServer server,
            ServerPlayer actor,
            RuntimeExecutionData executionData,
            Optional<RuntimeProjectileContinuationOpenResult.Opened> openedContinuation) {
        this.server = Objects.requireNonNull(server, "server");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.executionData = Objects.requireNonNull(executionData, "executionData");
        this.openedContinuation = Objects.requireNonNull(
                openedContinuation, "openedContinuation");
        var validSpawnPair = executionData instanceof CastGeometryExecutionDataV0
                && openedContinuation.isPresent()
                && openedContinuation.orElseThrow().permit().mode
                        == RuntimeProjectileContinuationPermit.Mode.REAL
                && openedContinuation.orElseThrow().permit().state
                        == RuntimeProjectileContinuationPermit.State.RESERVED;
        var validDamagePair = executionData instanceof ProjectileHitExecutionDataV0
                && openedContinuation.isEmpty();
        if (!validSpawnPair && !validDamagePair) {
            throw new IllegalArgumentException("invalid P9 execution-data/continuation pairing");
        }
    }

    @Override
    public CommitDisposition commitSpawn(SpawnCommit command) {
        Objects.requireNonNull(command, "command");
        if (!(executionData instanceof CastGeometryExecutionDataV0 geometry)
                || openedContinuation.isEmpty()) {
            throw new IllegalStateException("spawn commit used with non-spawn handoff");
        }
        var opened = openedContinuation.orElseThrow();
        if (!matchesSpawn(command, geometry)
                || !currentActor(geometry.dimension())
                || !liveSpawnOrigin(geometry)) {
            closeReservation(opened, ProjectileClosureReason.SPAWN_NOT_APPLIED);
            return CommitDisposition.NOT_APPLIED;
        }

        P9StarterProjectile projectile = null;
        boolean runtimeFaultContainment = false;
        try {
            var level = actor.serverLevel();
            projectile = new P9StarterProjectile(
                    P9StarterProjectileRegistration.type(),
                    level,
                    actor,
                    opened,
                    geometry);
            if (!liveSpawnOrigin(geometry)) {
                closeReservation(opened, ProjectileClosureReason.SPAWN_NOT_APPLIED);
                projectile.discard();
                return CommitDisposition.NOT_APPLIED;
            }
            if (!level.addFreshEntity(projectile)) {
                closeReservation(opened, ProjectileClosureReason.SPAWN_NOT_APPLIED);
                projectile.discard();
                return CommitDisposition.NOT_APPLIED;
            }
            var transfer = opened.transferAfterAppliedSpawn(server, projectile);
            if (transfer != RuntimePermitTransferDisposition.TRANSFERRED) {
                runtimeFaultContainment = true;
                throw new IllegalStateException("applied projectile transfer was rejected");
            }
            return CommitDisposition.APPLIED;
        } catch (RuntimeException failure) {
            if (opened.permit().state
                    != RuntimeProjectileContinuationPermit.State.OPEN) {
                bestEffortClose(
                        opened,
                        runtimeFaultContainment
                                ? ProjectileClosureReason.RUNTIME_FAULT
                                : ProjectileClosureReason.SPAWN_NOT_APPLIED);
                bestEffortDiscard(projectile);
            }
            throw failure;
        } catch (Error failure) {
            throw failure;
        }
    }

    @Override
    public CommitDisposition commitDamage(DamageCommit command) {
        Objects.requireNonNull(command, "command");
        if (!(executionData instanceof ProjectileHitExecutionDataV0 hit)
                || openedContinuation.isPresent()) {
            throw new IllegalStateException("damage commit used with non-damage handoff");
        }
        if (command.requestId() != command.sourceEventId()
                || command.requestId() <= 0L
                || !command.targetId().equals(hit.targetId())
                || command.magnitude() != 4_000L
                || command.magnitude() % 1_000L != 0L
                || command.manaCost() != 0L) {
            return CommitDisposition.NOT_APPLIED;
        }
        var convertedDamage = (float) (command.magnitude() / 1_000.0D);
        if (!Float.isFinite(convertedDamage)
                || convertedDamage <= 0.0F
                || convertedDamage != 4.0F
                || !currentActor(hit.dimension())) {
            return CommitDisposition.NOT_APPLIED;
        }

        var serverLevel = actor.serverLevel();
        var hitPosition = BlockPos.containing(hit.hitX(), hit.hitY(), hit.hitZ());
        var projectileEntity = serverLevel.getEntity(hit.projectileId());
        var targetEntity = serverLevel.getEntity(hit.targetId());
        if (!(projectileEntity instanceof P9StarterProjectile projectile)
                || !(targetEntity instanceof LivingEntity target)
                || projectile.getUUID().equals(target.getUUID())
                || projectile.level() != serverLevel
                || !projectile.isAddedToLevel()
                || projectile.isRemoved()
                || !projectile.isAlive()
                || projectile.getOwner() != actor
                || !projectile.hasAuthenticatedCasterIdentity(actor)
                || target == actor
                || target.level() != serverLevel
                || !target.isAddedToLevel()
                || target.isRemoved()
                || !target.isAlive()
                || !serverLevel.isInWorldBounds(hitPosition)
                || !serverLevel.isLoaded(hitPosition)
                || !serverLevel.getWorldBorder().isWithinBounds(hit.hitX(), hit.hitZ())
                || !serverLevel.isLoaded(target.blockPosition())) {
            return CommitDisposition.NOT_APPLIED;
        }

        return target.hurt(
                        serverLevel.damageSources().indirectMagic(projectile, actor), 4.0F)
                ? CommitDisposition.APPLIED
                : CommitDisposition.NOT_APPLIED;
    }

    private boolean currentActor(net.minecraft.resources.ResourceLocation dimension) {
        return server.isSameThread()
                && server.isRunning()
                && !server.isStopped()
                && actor.getServer() == server
                && actor.serverLevel().getServer() == server
                && actor.serverLevel().dimension().location().equals(dimension)
                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                && actor.isAddedToLevel()
                && !actor.isRemoved()
                && actor.isAlive()
                && actor.connection != null
                && actor.connection.isAcceptingMessages();
    }

    private boolean liveSpawnOrigin(CastGeometryExecutionDataV0 geometry) {
        var level = actor.serverLevel();
        var origin = BlockPos.containing(
                geometry.originX(), geometry.originY(), geometry.originZ());
        return level.dimension().location().equals(geometry.dimension())
                && level.isInWorldBounds(origin)
                && level.isLoaded(origin)
                && level.getWorldBorder().isWithinBounds(
                        geometry.originX(), geometry.originZ());
    }

    private static boolean matchesSpawn(
            SpawnCommit command, CastGeometryExecutionDataV0 geometry) {
        return command.requestId() == command.sourceEventId()
                && command.requestId() > 0L
                && command.dimension().equals(geometry.dimension())
                && sameDouble(command.originX(), geometry.originX())
                && sameDouble(command.originY(), geometry.originY())
                && sameDouble(command.originZ(), geometry.originZ())
                && command.directionXQ15() == geometry.directionXQ15()
                && command.directionYQ15() == geometry.directionYQ15()
                && command.directionZQ15() == geometry.directionZQ15()
                && command.profileCode() == geometry.profileCode()
                && command.manaCost() == 0L;
    }

    private static boolean sameDouble(double left, double right) {
        return Double.doubleToLongBits(left) == Double.doubleToLongBits(right);
    }

    private void closeReservation(
            RuntimeProjectileContinuationOpenResult.Opened opened,
            ProjectileClosureReason reason) {
        var disposition = opened.permit().closeWithoutHit(server, reason);
        if (disposition != RuntimePermitCloseDisposition.CLOSED
                && disposition != RuntimePermitCloseDisposition.ALREADY_CLOSED) {
            throw new IllegalStateException("P9 reservation close was rejected");
        }
    }

    private void bestEffortClose(
            RuntimeProjectileContinuationOpenResult.Opened opened,
            ProjectileClosureReason reason) {
        try {
            opened.permit().closeWithoutHit(server, reason);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Preserve the already-caught primary platform/runtime failure.
        }
    }

    private static void bestEffortDiscard(P9StarterProjectile projectile) {
        if (projectile == null) {
            return;
        }
        try {
            projectile.discard();
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Preserve the already-caught primary platform/runtime failure.
        }
    }
}
