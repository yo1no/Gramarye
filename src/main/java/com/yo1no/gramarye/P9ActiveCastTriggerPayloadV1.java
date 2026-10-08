package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;

/** Current typed payload; descriptor validation owns the semantic 0..600 bound. */
record P9ActiveCastTriggerPayloadV1(int cooldownTicks) implements TriggerPayload {
}
