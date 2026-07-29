package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;

/** Unique immutable V0 submission-policy implementation. */
final class DefaultSkillSubmissionPolicyProvider implements SkillSubmissionPolicyProvider {
    private final SkillSubmissionPolicySnapshot snapshot = new SkillSubmissionPolicySnapshot(
            SkillQuota.Unlimited.INSTANCE,
            new ValidationContext(MagicPolicyLimits.DEFAULTS));

    @Override
    public SkillSubmissionPolicySnapshot snapshot(MinecraftServer server) {
        return snapshotForServerIdentity(Objects.requireNonNull(server, "server"));
    }

    SkillSubmissionPolicySnapshot snapshotForServerIdentity(Object serverIdentity) {
        Objects.requireNonNull(serverIdentity, "serverIdentity");
        return snapshot;
    }
}
