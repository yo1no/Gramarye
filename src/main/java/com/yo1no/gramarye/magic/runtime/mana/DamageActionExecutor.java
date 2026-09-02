package com.yo1no.gramarye.magic.runtime.mana;

/** Pure deterministic projection from one typed action input to one effect request. */
final class DamageActionExecutor implements ActionExecutor {
    @Override
    public ActionExecutorOutcome execute(DamageActionInvocation input) {
        if (input == null
                || input.magnitude() <= 0
                || input.magnitude() > P6EffectBounds.MAX_EFFECT_MAGNITUDE
                || input.manaCost() < 0
                || input.manaCost() > P6EffectBounds.MAX_MANA_OPERATION_AMOUNT) {
            return NoActionRequest.INSTANCE;
        }
        return new ProducedActionRequest(new DamageEffectRequest(
                input.requestId(),
                input.sourceEventId(),
                input.target(),
                input.magnitude(),
                input.manaCost(),
                input.compensationPolicy()));
    }
}
