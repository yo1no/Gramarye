package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.store.SkillQuota;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import org.junit.jupiter.api.Test;

final class SkillSubmissionPolicyProviderTest {
    @Test
    void defaultProviderReusesOneExactImmutableV0SnapshotWithoutRetainingServer() {
        var provider = assertInstanceOf(
                DefaultSkillSubmissionPolicyProvider.class,
                SkillSubmissionPolicyProvider.defaults());
        var first = provider.snapshotForServerIdentity(new Object());
        var second = provider.snapshotForServerIdentity(new Object());

        assertSame(first, second);
        assertSame(SkillQuota.Unlimited.INSTANCE, first.quota());
        assertSame(MagicPolicyLimits.DEFAULTS, first.validationContext().policyLimits());
        assertNotSame(
                SkillSubmissionPolicyProvider.defaults(),
                SkillSubmissionPolicyProvider.defaults());
        assertThrows(
                NullPointerException.class,
                () -> provider.snapshotForServerIdentity(null));
        assertThrows(NullPointerException.class, () -> provider.snapshot(null));
    }

    @Test
    void snapshotRejectsNullComponents() {
        assertThrows(
                NullPointerException.class,
                () -> new SkillSubmissionPolicySnapshot(
                        null,
                        new ValidationContext(MagicPolicyLimits.DEFAULTS)));
        assertThrows(
                NullPointerException.class,
                () -> new SkillSubmissionPolicySnapshot(
                        SkillQuota.Unlimited.INSTANCE,
                        null));
    }
}
