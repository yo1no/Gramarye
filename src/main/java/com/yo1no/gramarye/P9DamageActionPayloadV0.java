package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.action.type.ActionPayload;

/** V0 canonical damage configuration in thousandths of one health point. */
record P9DamageActionPayloadV0(long magnitude, long manaCost) implements ActionPayload {
}
