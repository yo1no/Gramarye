package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import java.util.Objects;

public record ValidationContext(MagicPolicyLimits policyLimits) {
    public ValidationContext {
        Objects.requireNonNull(policyLimits, "policyLimits");
    }
}
