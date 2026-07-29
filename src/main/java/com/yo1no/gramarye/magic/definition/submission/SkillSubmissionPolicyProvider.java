package com.yo1no.gramarye.magic.definition.submission;

import net.minecraft.server.MinecraftServer;

/** Server-side source of one immutable policy snapshot per submission attempt. */
public interface SkillSubmissionPolicyProvider {
    SkillSubmissionPolicySnapshot snapshot(MinecraftServer server);

    /** Returns the immutable V0 policy provider. */
    static SkillSubmissionPolicyProvider defaults() {
        return new DefaultSkillSubmissionPolicyProvider();
    }
}
