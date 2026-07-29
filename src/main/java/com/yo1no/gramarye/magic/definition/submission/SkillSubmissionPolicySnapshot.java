package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.util.Objects;

/** Immutable policy values captured once for one submission attempt. */
public record SkillSubmissionPolicySnapshot(
        SkillQuota quota,
        ValidationContext validationContext) {
    public SkillSubmissionPolicySnapshot {
        Objects.requireNonNull(quota, "quota");
        Objects.requireNonNull(validationContext, "validationContext");
    }
}
