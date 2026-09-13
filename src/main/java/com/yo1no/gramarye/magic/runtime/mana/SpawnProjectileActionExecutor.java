package com.yo1no.gramarye.magic.runtime.mana;

/** Pure deterministic projection from the typed P9 spawn action to one effect request. */
final class SpawnProjectileActionExecutor implements ActionExecutor {
    @Override
    public ActionExecutorOutcome execute(ActionInvocation input) {
        if (!(input instanceof SpawnProjectileActionInvocation spawn)
                || !spawn.actionRegistryKey().equals(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "gramarye", "spawn_projectile"))) {
            return NoActionRequest.INSTANCE;
        }
        return new ProducedActionRequest(new SpawnProjectileRequest(
                spawn.requestId(),
                spawn.sourceEventId(),
                spawn.dimension(),
                spawn.originX(),
                spawn.originY(),
                spawn.originZ(),
                spawn.directionXQ15(),
                spawn.directionYQ15(),
                spawn.directionZQ15(),
                spawn.profileCode(),
                spawn.manaCost(),
                spawn.compensationPolicy()));
    }
}
