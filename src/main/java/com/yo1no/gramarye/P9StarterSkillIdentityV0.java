package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Pure deterministic identity projection for one player's canonical starter skill. */
final class P9StarterSkillIdentityV0 {
    private static final String DOMAIN = "gramarye:starter_bolt_v0";

    private P9StarterSkillIdentityV0() {
        throw new AssertionError("no instances");
    }

    static SkillId forPlayer(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return new SkillId(UUID.nameUUIDFromBytes(
                (DOMAIN + "\0" + playerUuid).getBytes(StandardCharsets.UTF_8)));
    }
}
