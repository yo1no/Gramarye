package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Arrays;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillStoreCommitResultTest {
    @Test
    void resultVocabularyHasExactlyFiveVariants() {
        assertEquals(Set.of(
                        SkillStoreCommitResult.Committed.class,
                        SkillStoreCommitResult.Conflict.class,
                        SkillStoreCommitResult.QuotaRejected.class,
                        SkillStoreCommitResult.CapacityRejected.class,
                        SkillStoreCommitResult.OwnerRejected.class),
                Set.of(SkillStoreCommitResult.class.getPermittedSubclasses()));
    }

    @Test
    void committedAndConflictRequireTypedNonNullValuesWithoutPartialCoreAccessors() {
        var committedReference = reference(1, 0);
        var conflictValue = new SkillStoreCommitConflict.ExpectedAbsentButPresent(
                committedReference.skillId());
        var committed = new SkillStoreCommitResult.Committed(committedReference);
        var conflict = new SkillStoreCommitResult.Conflict(conflictValue);
        var forbiddenMethods = Set.of("document", "plan", "definition", "report");

        assertAll(
                () -> assertSame(committedReference, committed.committed()),
                () -> assertSame(conflictValue, conflict.conflict()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitResult.Committed(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitResult.Conflict(null)),
                () -> assertFalse(Arrays.stream(SkillStoreCommitResult.Committed.class.getMethods())
                        .anyMatch(method -> forbiddenMethods.contains(method.getName()))));
    }

    @Test
    void quotaRejectedAcceptsAtOrOverPolicyAndRejectsInvalidMetadata() {
        var skillId = StoreTestFixtures.skillId(2);
        var atZero = new SkillStoreCommitResult.QuotaRejected(skillId, 0, 0);
        var atMaximum = new SkillStoreCommitResult.QuotaRejected(
                skillId,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER,
                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER);
        var overPolicy = new SkillStoreCommitResult.QuotaRejected(skillId, 10, 7);

        assertAll(
                () -> assertSame(skillId, atZero.skillId()),
                () -> assertEquals(0, atZero.maximum()),
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER,
                        atMaximum.maximum()),
                () -> assertEquals(10, overPolicy.current()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitResult.QuotaRejected(null, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillStoreCommitResult.QuotaRejected(skillId, -1, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillStoreCommitResult.QuotaRejected(skillId, 0, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillStoreCommitResult.QuotaRejected(skillId, 6, 7)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillStoreCommitResult.QuotaRejected(
                                skillId,
                                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1,
                                MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER + 1)));
    }

    @Test
    void capacityRejectedUsesEachCanonicalMaximumAndAllowsAtOrOverCapacity() {
        for (var scope : SkillStoreCapacityScope.values()) {
            var maximum = scope.canonicalMaximum();
            assertAll(
                    () -> assertEquals(maximum,
                            new SkillStoreCommitResult.CapacityRejected(
                                    scope, maximum, maximum).maximum()),
                    () -> assertEquals(maximum + 1,
                            new SkillStoreCommitResult.CapacityRejected(
                                    scope, maximum + 1, maximum).current()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new SkillStoreCommitResult.CapacityRejected(
                                    scope, maximum - 1, maximum)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new SkillStoreCommitResult.CapacityRejected(
                                    scope, -1, maximum)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new SkillStoreCommitResult.CapacityRejected(
                                    scope, maximum + 1, maximum - 1)));
        }

        assertThrows(NullPointerException.class,
                () -> new SkillStoreCommitResult.CapacityRejected(null, 0, 0));
    }

    @Test
    void ownerRejectedIsOpaqueAboutActualOwnerAndLatest() {
        var skillId = StoreTestFixtures.skillId(4);
        var rejected = new SkillStoreCommitResult.OwnerRejected(skillId);
        var forbiddenMethods = Set.of("owner", "actualOwner", "latest", "observed");

        assertAll(
                () -> assertSame(skillId, rejected.skillId()),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillStoreCommitResult.OwnerRejected(null)),
                () -> assertFalse(Arrays.stream(rejected.getClass().getMethods())
                        .anyMatch(method -> forbiddenMethods.contains(method.getName()))));
    }

    private static SkillReference reference(long skillId, int revision) {
        return new SkillReference(
                StoreTestFixtures.skillId(skillId),
                StoreTestFixtures.revision(revision));
    }
}
