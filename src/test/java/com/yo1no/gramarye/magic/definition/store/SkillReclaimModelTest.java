package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillReclaimModelTest {
    private static final int ROOT_MAX = MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM;

    @Test
    void failureVocabularyAndSingletonsAreExact() {
        assertEquals(
                Set.of(
                        SkillReclaimFailure.IncompleteRootSnapshot.class,
                        SkillReclaimFailure.TruncatedRootSnapshot.class,
                        SkillReclaimFailure.RootCapacityExceeded.class,
                        SkillReclaimFailure.MissingExternalRoot.class),
                Set.of(SkillReclaimFailure.class.getPermittedSubclasses()));
        assertArrayEquals(
                new SkillReclaimFailure.IncompleteRootSnapshot[] {
                    SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE
                },
                SkillReclaimFailure.IncompleteRootSnapshot.values());
        assertArrayEquals(
                new SkillReclaimFailure.TruncatedRootSnapshot[] {
                    SkillReclaimFailure.TruncatedRootSnapshot.INSTANCE
                },
                SkillReclaimFailure.TruncatedRootSnapshot.values());
    }

    @Test
    void failureMetadataIsTypedBoundedAndCanonical() {
        var reference = reference(1, 2);
        assertEquals(
                reference,
                new SkillReclaimFailure.MissingExternalRoot(reference).reference());
        assertThrows(
                NullPointerException.class,
                () -> new SkillReclaimFailure.MissingExternalRoot(null));
        assertEquals(
                new SkillReclaimFailure.RootCapacityExceeded(ROOT_MAX + 1, ROOT_MAX),
                new SkillReclaimFailure.RootCapacityExceeded(ROOT_MAX + 1, ROOT_MAX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillReclaimFailure.RootCapacityExceeded(ROOT_MAX, ROOT_MAX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillReclaimFailure.RootCapacityExceeded(-1, ROOT_MAX));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillReclaimFailure.RootCapacityExceeded(ROOT_MAX + 1, ROOT_MAX - 1));
    }

    @Test
    void resultVocabularyRequiresOnlyItsTypedCore() {
        assertEquals(
                Set.of(SkillReclaimResult.Completed.class, SkillReclaimResult.Rejected.class),
                Set.of(SkillReclaimResult.class.getPermittedSubclasses()));
        var report = new SkillReclaimReport(1, 2, 1, 1);
        assertEquals(report, new SkillReclaimResult.Completed(report).report());
        assertEquals(
                SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE,
                new SkillReclaimResult.Rejected(
                                SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE)
                        .failure());
        assertThrows(NullPointerException.class, () -> new SkillReclaimResult.Completed(null));
        assertThrows(NullPointerException.class, () -> new SkillReclaimResult.Rejected(null));
    }

    @Test
    void reportAcceptsZeroAndMaximumConsistentCounts() {
        assertEquals(new SkillReclaimReport(0, 0, 0, 0), new SkillReclaimReport(0, 0, 0, 0));
        assertEquals(
                new SkillReclaimReport(
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL,
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL,
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL,
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL
                                - MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL),
                new SkillReclaimReport(4_096, 32_768, 4_096, 28_672));
    }

    @Test
    void reportRejectsEveryInconsistentRelationship() {
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(-1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(0, 0, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 1, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 2, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 3, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(2, 4, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 2, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 2, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillReclaimReport(1, 2, 1, 2));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillReclaimReport(
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL + 1,
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL + 1,
                        0,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillReclaimReport(
                        1,
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL + 1,
                        0,
                        0));
    }

    private static SkillReference reference(long skillId, int revision) {
        return new SkillReference(
                StoreTestFixtures.skillId(skillId), StoreTestFixtures.revision(revision));
    }
}
