package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.action.type.ActionPayload;

/** V0 canonical projectile-spawn configuration. */
record P9SpawnProjectileActionPayloadV0(int profileCode, long manaCost)
        implements ActionPayload {
}
