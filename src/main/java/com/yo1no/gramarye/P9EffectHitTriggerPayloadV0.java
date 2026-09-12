package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;

/** V0 typed source selector for the canonical projectile-hit continuation. */
record P9EffectHitTriggerPayloadV0(
        int sourceNodeIndex,
        int sourceOutputOrdinal,
        boolean includeDerived) implements TriggerPayload {
}
