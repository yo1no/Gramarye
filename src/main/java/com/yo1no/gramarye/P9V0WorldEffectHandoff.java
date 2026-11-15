package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.DamageCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.SpawnCommit;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0NativeOutcome;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0Terminal;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.V0TerminalReceipt;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** One native statement or release, owned by the exact active P5 dispatch frame. */
final class P9V0WorldEffectHandoff {
    private static final ThreadLocal<P9V0WorldEffectHandoff> IN_FLIGHT_DAMAGE = new ThreadLocal<>();
    private final V0NativeCallFrame frame;
    private final int serverTick;
    private DamageSource nativeDamage;
    private boolean attempted;
    private boolean primaryConfirmed;
    private boolean consumed;
    private boolean matchingPost;
    private boolean duplicatePost;
    private boolean finitePost;
    private boolean positivePost;

    P9V0WorldEffectHandoff(V0NativeCallFrame frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        serverTick = frame.server().getTickCount();
    }

    V0NativeOutcome commitSpawn(SpawnCommit command) {
        Objects.requireNonNull(command, "command");
        var geometry = frame.spawn();
        if (!beginAttempt(command.requestId(), command.sourceEventId()) || geometry == null
                || !matchesSpawn(command, geometry) || !loadedSpawn(geometry)) {
            return V0NativeOutcome.CONFIRMED_NOT_APPLIED;
        }
        var projectile = new P9StarterProjectile(P9StarterProjectileRegistration.type(),
                frame.level(), frame.binding(), geometry);
        try {
            if (!frame.prepare(projectile)) {
                return V0NativeOutcome.CONFIRMED_NOT_APPLIED;
            }
            projectile.setOwner(frame.actor());
            if (!frame.allowsNative() || !loadedSpawn(geometry)) {
                projectile.closeV0(V0TerminalReason.REJECTED_BEFORE_COMMIT);
                return V0NativeOutcome.CONFIRMED_NOT_APPLIED;
            }
            projectile.armV0InsertionWitness();
            boolean added;
            try {
                added = frame.level().addFreshEntity(projectile);
            } finally {
                projectile.completeV0NativeAdd();
            }
            if (!added) {
                var inserted = projectile.v0EverInserted();
                projectile.closeV0(inserted ? V0TerminalReason.FAULT : V0TerminalReason.CONFIRMED_NOT_APPLIED);
                return inserted ? V0NativeOutcome.APPLIED_WITH_FAILURE : V0NativeOutcome.CONFIRMED_NOT_APPLIED;
            }
            primaryConfirmed = true;
            if (projectile.v0EverInserted() && projectile.isAddedToLevel() && !projectile.isRemoved()
                    && projectile.level() == frame.level()
                    && frame.level().getEntity(projectile.getId()) == projectile
                    && frame.level().getEntity(projectile.getUUID()) == projectile
                    && loadedSpawn(geometry) && frame.open(projectile)) {
                return V0NativeOutcome.CONFIRMED_APPLIED;
            }
            projectile.closeV0(V0TerminalReason.OWNER_INVALIDATED);
            return V0NativeOutcome.APPLIED_WITH_FAILURE;
        } catch (RuntimeException | Error primary) {
            closeAfterNativeFault(projectile, projectile.v0EverInserted()
                    ? V0TerminalReason.FAULT : V0TerminalReason.OUTCOME_UNKNOWN);
            throw primary;
        }
    }

    static void moveCarrier(P9StarterProjectile projectile, ServerLevel level) {
        try {
            projectile.stepV0(level);
        } catch (RuntimeException | Error primary) {
            closeAfterNativeFault(projectile, V0TerminalReason.FAULT);
            throw primary;
        }
    }

    static void discardAfterV0Close(P9StarterProjectile projectile, boolean finished) {
        if (finished) {
            P11P9TrackingCleanup.discardClosedV0(projectile);
            return;
        }
        try {
            P11P9TrackingCleanup.discardClosedV0(projectile);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // The owner has already thrown; preserve its original failure.
        }
    }

    private static void closeAfterNativeFault(P9StarterProjectile projectile, V0TerminalReason reason) {
        try {
            projectile.closeV0(reason);
        } catch (RuntimeException | Error ignoredCleanupFailure) {
            // Preserve the original native exception, including Error.
        }
    }

    V0NativeOutcome commitDamage(DamageCommit command) {
        Objects.requireNonNull(command, "command");
        var target = frame.target();
        var carrier = frame.carrier();
        var actor = frame.actor();
        if (!beginAttempt(command.requestId(), command.sourceEventId()) || target == null || carrier == null
                || !target.getUUID().equals(command.targetId()) || command.manaCost() != 0L
                || command.magnitude() < 1_000L || command.magnitude() > 4_000L
                || command.magnitude() % 1_000L != 0L || target.getUUID().equals(actor.getUUID())
                || carrier.level() != frame.level() || !carrier.isAddedToLevel()
                || carrier.isRemoved() || carrier.getOwner() != actor
                || frame.level().getEntity(carrier.getId()) != carrier
                || frame.level().getEntity(carrier.getUUID()) != carrier
                || !P9V0LoadedBlocks.dryCell(frame.level(), BlockPos.containing(frame.impactPosition()))) {
            return V0NativeOutcome.CONFIRMED_NOT_APPLIED;
        }
        if (IN_FLIGHT_DAMAGE.get() != null) {
            throw new IllegalStateException("Nested V0 native damage");
        }
        nativeDamage = frame.level().damageSources().indirectMagic(carrier, actor);
        IN_FLIGHT_DAMAGE.set(this);
        boolean applied;
        try {
            applied = target.hurt(nativeDamage, (float) (command.magnitude() / 1_000.0));
        } finally {
            IN_FLIGHT_DAMAGE.remove();
            nativeDamage = null;
        }
        if (positivePost) {
            primaryConfirmed = !duplicatePost;
            return applied && !duplicatePost ? V0NativeOutcome.CONFIRMED_APPLIED : V0NativeOutcome.APPLIED_WITH_FAILURE;
        }
        if (duplicatePost || (matchingPost && !finitePost) || (applied && !matchingPost)) {
            return V0NativeOutcome.OUTCOME_UNKNOWN;
        }
        return V0NativeOutcome.CONFIRMED_NOT_APPLIED;
    }

    static void observeV0DamagePost(LivingDamageEvent.Post event) {
        var current = IN_FLIGHT_DAMAGE.get();
        if (current == null || current.nativeDamage != event.getSource()
                || current.frame.target() != event.getEntity()
                || !current.frame.server().isSameThread() || event.getEntity().level() != current.frame.level()) {
            return;
        }
        if (current.matchingPost) {
            current.duplicatePost = true;
            return;
        }
        current.matchingPost = true;
        float damage = event.getNewDamage();
        float absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        current.finitePost = Float.isFinite(damage) && Float.isFinite(absorption)
                && damage >= 0.0F && absorption >= 0.0F;
        current.positivePost = current.finitePost && (damage > 0.0F || absorption > 0.0F);
    }

    Optional<V0PresentationMaterial> consumeApplied(V0TerminalReceipt receipt) {
        if (consumed) {
            return Optional.empty();
        }
        consumed = true;
        if (!primaryConfirmed || receipt == null || !frame.server().isSameThread()
                || !frame.presentationContextCurrent()
                || frame.server().getTickCount() != serverTick || receipt.requestId() != frame.requestId()
                || receipt.sourceEventId() != frame.sourceEventId()
                || receipt.statementOrdinal() != frame.statementOrdinal()
                || (receipt.terminal() != V0Terminal.CONFIRMED_APPLIED
                        && receipt.terminal() != V0Terminal.CONFIRMED_APPLIED_WITH_FAILURE)) {
            return Optional.empty();
        }
        return Optional.of(new V0PresentationMaterial(frame, serverTick, eventMaterial(), appearance()));
    }

    private boolean beginAttempt(long requestId, long sourceEventId) {
        if (attempted) {
            throw new IllegalStateException("Reused exact V0 native call");
        }
        attempted = true;
        return requestId == frame.requestId() && sourceEventId == frame.sourceEventId() && frame.allowsNative();
    }

    private static boolean matchesSpawn(SpawnCommit command, V0SpawnGeometry geometry) {
        var origin = geometry.origin();
        return command.dimension().equals(geometry.level().dimension().location())
                && Double.doubleToLongBits(command.originX()) == Double.doubleToLongBits(origin.x)
                && Double.doubleToLongBits(command.originY()) == Double.doubleToLongBits(origin.y)
                && Double.doubleToLongBits(command.originZ()) == Double.doubleToLongBits(origin.z)
                && command.directionXQ15() == geometry.directionXQ15()
                && command.directionYQ15() == geometry.directionYQ15()
                && command.directionZQ15() == geometry.directionZQ15() && command.profileCode() == 0;
    }

    private static boolean loadedSpawn(V0SpawnGeometry geometry) {
        Vec3 origin = geometry.origin();
        return origin.y >= geometry.level().getMinBuildHeight() + 64.0
                && geometry.level().getWorldBorder().isWithinBounds(origin.x, origin.z)
                && P9V0LoadedBlocks.dryCell(geometry.level(), BlockPos.containing(origin));
    }

    private P8EventMaterial eventMaterial() {
        var spawn = frame.spawn();
        var target = frame.target();
        var source = frame.onlineActor();
        var position = spawn == null ? frame.impactPosition() : spawn.origin();
        return new P8EventMaterial(spawn == null ? PresentationEventKind.HIT : PresentationEventKind.CAST_RELEASE,
                frame.level().dimension().location(), position.x, position.y, position.z,
                spawn == null ? frame.impactDirectionXQ15() : spawn.directionXQ15(),
                spawn == null ? frame.impactDirectionYQ15() : spawn.directionYQ15(),
                spawn == null ? frame.impactDirectionZQ15() : spawn.directionZQ15(),
                entityId(source), entityId(target), playerId(source), playerId(target),
                target == null ? Optional.empty() : Optional.of(target.getUUID()));
    }

    private static OptionalInt entityId(Entity entity) {
        return entity == null ? OptionalInt.empty() : OptionalInt.of(entity.getId());
    }

    private static Optional<UUID> playerId(Entity entity) {
        return entity instanceof ServerPlayer player ? Optional.of(player.getUUID()) : Optional.empty();
    }

    private RuntimeNeutralAppearance appearance() {
        var profile = frame.profile().orElse(null);
        if (profile == null) {
            return RuntimeNeutralAppearance.Default.INSTANCE;
        }
        var sound = ResourceLocation.tryParse(profile.soundId());
        var particle = ResourceLocation.tryParse(profile.particleId());
        var trail = ResourceLocation.tryParse(profile.trailId());
        if (sound == null || particle == null || trail == null) {
            return RuntimeNeutralAppearance.Default.INSTANCE;
        }
        return new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(OptionalInt.empty(), OptionalInt.empty(),
                ProfileSelection.specified(sound), ProfileSelection.specified(particle), ProfileSelection.specified(trail),
                OptionalInt.empty()));
    }

    static final class V0PresentationMaterial {
        private final V0CatalogAuthority authority;
        private final long runtimeTick;
        private final int serverTick;
        private final long requestId;
        private final long sourceEventId;
        private final int statementOrdinal;
        private final V0SpellInstanceId instanceId;
        private final UUID attribution;
        private final P8EventMaterial eventMaterial;
        private final RuntimeNeutralAppearance appearance;
        private final int offerLimit;
        private final int recipientLimit;

        private V0PresentationMaterial(V0NativeCallFrame frame, int serverTick,
                P8EventMaterial eventMaterial, RuntimeNeutralAppearance appearance) {
            authority = frame.authority();
            runtimeTick = frame.runtimeTick();
            this.serverTick = serverTick;
            requestId = frame.requestId();
            sourceEventId = frame.sourceEventId();
            statementOrdinal = frame.statementOrdinal();
            instanceId = frame.instanceId();
            attribution = frame.actor().getUUID();
            this.eventMaterial = eventMaterial;
            this.appearance = appearance;
            offerLimit = frame.offerLimit();
            recipientLimit = frame.recipientLimit();
        }

        V0CatalogAuthority authority() { return authority; }
        long runtimeTick() { return runtimeTick; }
        int serverTick() { return serverTick; }
        long requestId() { return requestId; }
        long sourceEventId() { return sourceEventId; }
        int statementOrdinal() { return statementOrdinal; }
        V0SpellInstanceId instanceId() { return instanceId; }
        UUID attribution() { return attribution; }
        P8EventMaterial eventMaterial() { return eventMaterial; }
        RuntimeNeutralAppearance appearance() { return appearance; }
        int offerLimit() { return offerLimit; }
        int recipientLimit() { return recipientLimit; }
    }
}
