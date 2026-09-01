package com.yo1no.gramarye;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Loaded-only resolver for the generic stable references retained by runtime events. */
final class P5LoadedReferenceResolver implements RuntimeReferenceResolver {
    @Override
    public RuntimeReferenceResolutionOutcome resolve(
            MinecraftServer server,
            RuntimeEvent event) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(event, "event");

        return resolveLoadedReferences(
                server,
                event.cancellationToken().serverSlotToken(),
                event.origin(),
                event.target());
    }

    static RuntimeReferenceResolutionOutcome resolveLoadedReferences(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            RuntimeOrigin origin,
            Optional<RuntimeTarget> target) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(serverToken, "serverToken");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(target, "target");

        var originResolution = resolveOrigin(server, serverToken, origin);
        if (originResolution instanceof OriginResolution.Missing missing) {
            return classifySourceFailure(missing.reason());
        }
        if (originResolution instanceof OriginResolution.Invalid invalid) {
            return classifySourceFailure(invalid.reason());
        }

        var targetResolution = target.<TargetResolution>map(
                        value -> resolveTarget(server, serverToken, value))
                .orElseGet(() -> new TargetResolution.Resolved(
                        NoResolvedRuntimeTarget.INSTANCE));
        if (targetResolution instanceof TargetResolution.Missing missing) {
            return classifyTargetFailure(missing.reason());
        }
        if (targetResolution instanceof TargetResolution.Invalid invalid) {
            return classifyTargetFailure(invalid.reason());
        }

        return new RuntimeReferenceResolutionOutcome.Resolved(
                new ResolvedRuntimeReferenceContext(
                        ((OriginResolution.Resolved) originResolution).origin(),
                        ((TargetResolution.Resolved) targetResolution).target()));
    }

    static RuntimeReferenceResolutionOutcome classifySourceFailure(
            RuntimeReferenceFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        return switch (reason) {
            case MISSING, MISSING_OR_UNLOADED, UNLOADED ->
                    new RuntimeReferenceResolutionOutcome.SourceMissing(reason);
            case WRONG_SERVER, DIMENSION_UNAVAILABLE, WRONG_DIMENSION, TYPE_MISMATCH ->
                    new RuntimeReferenceResolutionOutcome.InvalidRuntimeReference(reason);
        };
    }

    static RuntimeReferenceResolutionOutcome classifyTargetFailure(
            RuntimeReferenceFailureReason reason) {
        Objects.requireNonNull(reason, "reason");
        return switch (reason) {
            case MISSING, MISSING_OR_UNLOADED, UNLOADED ->
                    new RuntimeReferenceResolutionOutcome.TargetMissing(reason);
            case WRONG_SERVER, DIMENSION_UNAVAILABLE, WRONG_DIMENSION, TYPE_MISMATCH ->
                    new RuntimeReferenceResolutionOutcome.InvalidRuntimeReference(reason);
        };
    }

    private static OriginResolution resolveOrigin(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            RuntimeOrigin origin) {
        return switch (origin) {
            case ServerOrigin value -> value.server().equals(serverToken)
                    ? new OriginResolution.Resolved(new ResolvedServerOrigin(server))
                    : wrongServerOrigin();
            case PlayerOrigin value -> resolvePlayerOrigin(server, serverToken, value);
            case EntityOrigin value -> resolveEntityOrigin(server, serverToken, value);
            case BlockOrigin value -> resolveBlockOrigin(server, serverToken, value);
        };
    }

    private static TargetResolution resolveTarget(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            RuntimeTarget target) {
        return switch (target) {
            case PlayerTarget value -> resolvePlayerTarget(server, serverToken, value);
            case EntityTarget value -> resolveEntityTarget(server, serverToken, value);
            case BlockTarget value -> resolveBlockTarget(server, serverToken, value);
        };
    }

    private static OriginResolution resolvePlayerOrigin(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            PlayerOrigin reference) {
        if (!reference.server().equals(serverToken)) {
            return wrongServerOrigin();
        }
        var levelResolution = resolveLevel(server, reference.dimension());
        if (levelResolution instanceof LevelResolution.Invalid invalid) {
            return new OriginResolution.Invalid(invalid.reason());
        }
        var player = Optional.ofNullable(
                server.getPlayerList().getPlayer(reference.player().value()));
        if (player.isEmpty() || player.orElseThrow().isRemoved()) {
            return new OriginResolution.Missing(RuntimeReferenceFailureReason.MISSING);
        }
        var resolved = player.orElseThrow();
        var liveValidation = validatePlayer(server, reference.dimension(), resolved);
        if (liveValidation.isPresent()) {
            return new OriginResolution.Invalid(liveValidation.orElseThrow());
        }
        return new OriginResolution.Resolved(new ResolvedPlayerOrigin(resolved));
    }

    private static TargetResolution resolvePlayerTarget(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            PlayerTarget reference) {
        if (!reference.server().equals(serverToken)) {
            return wrongServerTarget();
        }
        var levelResolution = resolveLevel(server, reference.dimension());
        if (levelResolution instanceof LevelResolution.Invalid invalid) {
            return new TargetResolution.Invalid(invalid.reason());
        }
        var player = Optional.ofNullable(
                server.getPlayerList().getPlayer(reference.player().value()));
        if (player.isEmpty() || player.orElseThrow().isRemoved()) {
            return new TargetResolution.Missing(RuntimeReferenceFailureReason.MISSING);
        }
        var resolved = player.orElseThrow();
        var liveValidation = validatePlayer(server, reference.dimension(), resolved);
        if (liveValidation.isPresent()) {
            return new TargetResolution.Invalid(liveValidation.orElseThrow());
        }
        return new TargetResolution.Resolved(new ResolvedPlayerTarget(resolved));
    }

    private static OriginResolution resolveEntityOrigin(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            EntityOrigin reference) {
        if (!reference.server().equals(serverToken)) {
            return wrongServerOrigin();
        }
        var levelResolution = resolveLevel(server, reference.dimension());
        if (levelResolution instanceof LevelResolution.Invalid invalid) {
            return new OriginResolution.Invalid(invalid.reason());
        }
        var level = ((LevelResolution.Resolved) levelResolution).level();
        var entity = Optional.ofNullable(level.getEntity(reference.entity().value()));
        if (entity.isEmpty()) {
            return findLoadedEntityInAnotherDimension(
                            server, reference.dimension(), reference.entity())
                    .<OriginResolution>map(ignored -> new OriginResolution.Invalid(
                            RuntimeReferenceFailureReason.WRONG_DIMENSION))
                    .orElseGet(() -> new OriginResolution.Missing(
                            RuntimeReferenceFailureReason.MISSING_OR_UNLOADED));
        }
        var resolved = entity.orElseThrow();
        if (resolved.isRemoved()) {
            return new OriginResolution.Missing(RuntimeReferenceFailureReason.MISSING);
        }
        var invalid = validateEntity(
                server, reference.dimension(), reference.expectedKind(), resolved);
        if (invalid.isPresent()) {
            return new OriginResolution.Invalid(invalid.orElseThrow());
        }
        return new OriginResolution.Resolved(new ResolvedEntityOrigin(resolved));
    }

    private static TargetResolution resolveEntityTarget(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            EntityTarget reference) {
        if (!reference.server().equals(serverToken)) {
            return wrongServerTarget();
        }
        var levelResolution = resolveLevel(server, reference.dimension());
        if (levelResolution instanceof LevelResolution.Invalid invalid) {
            return new TargetResolution.Invalid(invalid.reason());
        }
        var level = ((LevelResolution.Resolved) levelResolution).level();
        var entity = Optional.ofNullable(level.getEntity(reference.entity().value()));
        if (entity.isEmpty()) {
            return findLoadedEntityInAnotherDimension(
                            server, reference.dimension(), reference.entity())
                    .<TargetResolution>map(ignored -> new TargetResolution.Invalid(
                            RuntimeReferenceFailureReason.WRONG_DIMENSION))
                    .orElseGet(() -> new TargetResolution.Missing(
                            RuntimeReferenceFailureReason.MISSING_OR_UNLOADED));
        }
        var resolved = entity.orElseThrow();
        if (resolved.isRemoved()) {
            return new TargetResolution.Missing(RuntimeReferenceFailureReason.MISSING);
        }
        var invalid = validateEntity(
                server, reference.dimension(), reference.expectedKind(), resolved);
        if (invalid.isPresent()) {
            return new TargetResolution.Invalid(invalid.orElseThrow());
        }
        return new TargetResolution.Resolved(new ResolvedEntityTarget(resolved));
    }

    private static OriginResolution resolveBlockOrigin(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            BlockOrigin reference) {
        if (!reference.server().equals(serverToken)) {
            return wrongServerOrigin();
        }
        var levelResolution = resolveLevel(server, reference.dimension());
        if (levelResolution instanceof LevelResolution.Invalid invalid) {
            return new OriginResolution.Invalid(invalid.reason());
        }
        var level = ((LevelResolution.Resolved) levelResolution).level();
        var missing = validateLoadedBlock(level, reference.position());
        if (missing.isPresent()) {
            return new OriginResolution.Missing(missing.orElseThrow());
        }
        return new OriginResolution.Resolved(
                new ResolvedBlockOrigin(level, reference.position()));
    }

    private static TargetResolution resolveBlockTarget(
            MinecraftServer server,
            RuntimeServerToken serverToken,
            BlockTarget reference) {
        if (!reference.server().equals(serverToken)) {
            return wrongServerTarget();
        }
        var levelResolution = resolveLevel(server, reference.dimension());
        if (levelResolution instanceof LevelResolution.Invalid invalid) {
            return new TargetResolution.Invalid(invalid.reason());
        }
        var level = ((LevelResolution.Resolved) levelResolution).level();
        var missing = validateLoadedBlock(level, reference.position());
        if (missing.isPresent()) {
            return new TargetResolution.Missing(missing.orElseThrow());
        }
        return new TargetResolution.Resolved(
                new ResolvedBlockTarget(level, reference.position()));
    }

    private static LevelResolution resolveLevel(
            MinecraftServer server,
            ResourceKey<Level> dimension) {
        var level = Optional.ofNullable(server.getLevel(dimension));
        if (level.isEmpty()) {
            return new LevelResolution.Invalid(
                    RuntimeReferenceFailureReason.DIMENSION_UNAVAILABLE);
        }
        var resolved = level.orElseThrow();
        if (resolved.getServer() != server) {
            return new LevelResolution.Invalid(RuntimeReferenceFailureReason.WRONG_SERVER);
        }
        if (!resolved.dimension().equals(dimension)) {
            return new LevelResolution.Invalid(RuntimeReferenceFailureReason.WRONG_DIMENSION);
        }
        return new LevelResolution.Resolved(resolved);
    }

    private static Optional<RuntimeReferenceFailureReason> validatePlayer(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            ServerPlayer player) {
        if (player.getServer() != server || player.serverLevel().getServer() != server) {
            return Optional.of(RuntimeReferenceFailureReason.WRONG_SERVER);
        }
        if (!player.serverLevel().dimension().equals(dimension)) {
            return Optional.of(RuntimeReferenceFailureReason.WRONG_DIMENSION);
        }
        return Optional.empty();
    }

    private static Optional<RuntimeReferenceFailureReason> validateEntity(
            MinecraftServer server,
            ResourceKey<Level> dimension,
            RuntimeEntityKind expectedKind,
            Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || level.getServer() != server) {
            return Optional.of(RuntimeReferenceFailureReason.WRONG_SERVER);
        }
        if (!level.dimension().equals(dimension)) {
            return Optional.of(RuntimeReferenceFailureReason.WRONG_DIMENSION);
        }
        if (expectedKind == RuntimeEntityKind.LIVING_ENTITY
                && !(entity instanceof LivingEntity)) {
            return Optional.of(RuntimeReferenceFailureReason.TYPE_MISMATCH);
        }
        return Optional.empty();
    }

    private static Optional<RuntimeReferenceFailureReason> validateLoadedBlock(
            ServerLevel level,
            BlockPos position) {
        if (level.isOutsideBuildHeight(position)) {
            return Optional.of(RuntimeReferenceFailureReason.MISSING);
        }
        if (!level.isLoaded(position)) {
            return Optional.of(RuntimeReferenceFailureReason.UNLOADED);
        }
        return Optional.empty();
    }

    private static Optional<Entity> findLoadedEntityInAnotherDimension(
            MinecraftServer server,
            ResourceKey<Level> expectedDimension,
            RuntimeEntityId entityId) {
        for (var level : server.getAllLevels()) {
            if (level.dimension().equals(expectedDimension)) {
                continue;
            }
            var entity = level.getEntity(entityId.value());
            if (entity != null && !entity.isRemoved()) {
                return Optional.of(entity);
            }
        }
        return Optional.empty();
    }

    private static OriginResolution.Invalid wrongServerOrigin() {
        return new OriginResolution.Invalid(RuntimeReferenceFailureReason.WRONG_SERVER);
    }

    private static TargetResolution.Invalid wrongServerTarget() {
        return new TargetResolution.Invalid(RuntimeReferenceFailureReason.WRONG_SERVER);
    }

    private sealed interface OriginResolution
            permits OriginResolution.Resolved,
                    OriginResolution.Missing,
                    OriginResolution.Invalid {
        record Resolved(ResolvedRuntimeOrigin origin) implements OriginResolution {
            public Resolved {
                Objects.requireNonNull(origin, "origin");
            }
        }

        record Missing(RuntimeReferenceFailureReason reason) implements OriginResolution {
            public Missing {
                Objects.requireNonNull(reason, "reason");
            }
        }

        record Invalid(RuntimeReferenceFailureReason reason) implements OriginResolution {
            public Invalid {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    private sealed interface TargetResolution
            permits TargetResolution.Resolved,
                    TargetResolution.Missing,
                    TargetResolution.Invalid {
        record Resolved(ResolvedRuntimeTarget target) implements TargetResolution {
            public Resolved {
                Objects.requireNonNull(target, "target");
            }
        }

        record Missing(RuntimeReferenceFailureReason reason) implements TargetResolution {
            public Missing {
                Objects.requireNonNull(reason, "reason");
            }
        }

        record Invalid(RuntimeReferenceFailureReason reason) implements TargetResolution {
            public Invalid {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    private sealed interface LevelResolution
            permits LevelResolution.Resolved, LevelResolution.Invalid {
        record Resolved(ServerLevel level) implements LevelResolution {
            public Resolved {
                Objects.requireNonNull(level, "level");
            }
        }

        record Invalid(RuntimeReferenceFailureReason reason) implements LevelResolution {
            public Invalid {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
