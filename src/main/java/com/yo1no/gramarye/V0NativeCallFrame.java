package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Call-local exact P5 custody. Native references leave scope with the enclosing dispatch. */
final class V0NativeCallFrame {
    private final V0SlotRuntime slot;
    private final V0SlotRuntime.DispatchClaim claim;
    private final SkillRuntimeService.V0CarrierBinding binding;
    private final P11QualifiedSourceOwner.WorkReservation work;
    private final ServerPlayer actor;
    private final V0SpawnGeometry trajectory;
    private final LivingEntity target;
    private final Vec3 impactPosition;
    private final V0RuntimeCatalog.Unit unit;
    private final P6RuntimeExecutionBridge.Invocation invocation;
    private final int statementOrdinal;
    private final long runtimeTick;

    V0NativeCallFrame(V0SlotRuntime slot, V0SlotRuntime.DispatchClaim claim,
            SkillRuntimeService.V0CarrierBinding binding, P11QualifiedSourceOwner.WorkReservation work,
            ServerPlayer actor, V0SpawnGeometry trajectory, LivingEntity target, Vec3 impactPosition,
            V0RuntimeCatalog.Unit unit, P6RuntimeExecutionBridge.Invocation invocation,
            int statementOrdinal, long runtimeTick) {
        this.slot = slot;
        this.claim = claim;
        this.binding = binding;
        this.work = work;
        this.actor = actor;
        this.trajectory = trajectory;
        this.target = target;
        this.impactPosition = impactPosition;
        this.unit = unit;
        this.invocation = invocation;
        this.statementOrdinal = statementOrdinal;
        this.runtimeTick = runtimeTick;
    }

    ServerPlayer actor() { return actor; }
    P11QualifiedSourceOwner.WorkReservation work() { return work; }
    ServerLevel level() { return trajectory.level(); }
    MinecraftServer server() { return level().getServer(); }
    V0CatalogAuthority authority() { return slot.authority(); }
    SkillRuntimeService.V0CarrierBinding binding() { return binding; }
    long requestId() {
        return invocation instanceof P6RuntimeExecutionBridge.SpawnProjectileInvocation spawn
                ? spawn.requestId() : ((P6RuntimeExecutionBridge.DamageInvocation) invocation).requestId();
    }

    long sourceEventId() {
        return invocation instanceof P6RuntimeExecutionBridge.SpawnProjectileInvocation spawn
                ? spawn.sourceEventId() : ((P6RuntimeExecutionBridge.DamageInvocation) invocation).sourceEventId();
    }
    long runtimeTick() { return runtimeTick; }
    int statementOrdinal() { return statementOrdinal; }
    int offerLimit() { return slot.presentationOfferLimit(); }
    int recipientLimit() { return slot.presentationRecipientLimit(); }
    V0SpellInstanceId instanceId() { return claim.instance; }
    V0SpawnGeometry spawn() { return target == null ? trajectory : null; }
    LivingEntity target() { return target; }
    Vec3 impactPosition() { return impactPosition; }
    int impactDirectionXQ15() { return trajectory.directionXQ15(); }
    int impactDirectionYQ15() { return trajectory.directionYQ15(); }
    int impactDirectionZQ15() { return trajectory.directionZQ15(); }
    Optional<V0RuntimeCatalog.Profile> profile() { return unit.profile(); }
    P9StarterProjectile carrier() { return binding.carrier(); }

    ServerPlayer onlineActor() {
        return slot.onlineActor(claim, actor);
    }

    boolean matchesInvocation(ServerPlayer candidate, P6RuntimeExecutionBridge.Invocation supplied) {
        return candidate == actor && invocation.equals(supplied);
    }

    P6RuntimeExecutionBridge.GuardDecision check(P6RuntimeExecutionBridge.GuardPoint point, int stepIndex) {
        if (runtimeTick > claim.deadline) {
            return P6RuntimeExecutionBridge.GuardDecision.DEADLINE_EXCEEDED;
        }
        return allowsNative() ? P6RuntimeExecutionBridge.GuardDecision.ALLOWED
                : P6RuntimeExecutionBridge.GuardDecision.CANCELLED;
    }

    boolean allowsDebit(ServerPlayer exactA) {
        return exactA == actor && onlineActor() == exactA && allowsNative();
    }

    boolean presentationContextCurrent() { return slot.presentationContextCurrent(claim, runtimeTick); }

    boolean allowsNative() {
        return slot.frameActive(claim, binding, runtimeTick, actor)
                && (target == null || eligibleTarget(target));
    }

    boolean eligibleTarget(LivingEntity candidate) {
        return candidate != null && candidate == target && slot.eligibleTarget(binding, candidate);
    }

    boolean prepare(P9StarterProjectile projectile) {
        return target == null && allowsNative() && binding.prepare(projectile);
    }

    boolean open(P9StarterProjectile projectile) {
        return target == null && allowsNative() && binding.open(projectile);
    }
}
