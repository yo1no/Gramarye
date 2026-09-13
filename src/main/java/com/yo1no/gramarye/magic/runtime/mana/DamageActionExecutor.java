package com.yo1no.gramarye.magic.runtime.mana;

/** Pure deterministic projection from one typed action input to one effect request. */
final class DamageActionExecutor implements ActionExecutor {
    @Override
    public ActionExecutorOutcome execute(ActionInvocation input) {
        if (!(input instanceof DamageActionInvocation damage)
                || !damage.actionRegistryKey().equals(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "gramarye", "damage"))
                || damage.magnitude() <= 0
                || damage.magnitude() > P6EffectBounds.MAX_EFFECT_MAGNITUDE
                || damage.manaCost() < 0
                || damage.manaCost() > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            return NoActionRequest.INSTANCE;
        }
        return new ProducedActionRequest(new DamageEffectRequest(
                damage.requestId(),
                damage.sourceEventId(),
                damage.target(),
                damage.magnitude(),
                damage.manaCost(),
                damage.compensationPolicy()));
    }
}
