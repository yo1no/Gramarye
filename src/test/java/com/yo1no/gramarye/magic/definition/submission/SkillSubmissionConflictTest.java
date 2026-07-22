package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import org.junit.jupiter.api.Test;

class SkillSubmissionConflictTest {
    @Test
    void variantsExposeOnlyTheirTypedConflictData() {
        var supplied = new SkillRevision(3);
        var latest = latest(5);
        var baseForNew = new SkillSubmissionConflict.BaseRevisionForNew(
                SubmissionAuthorityTestFixtures.SKILL_ID, supplied);
        var missing = new SkillSubmissionConflict.MissingBaseForExisting(latest);
        var stale = new SkillSubmissionConflict.StaleBase(supplied, latest);
        var future = new SkillSubmissionConflict.FutureBase(new SkillRevision(7), latest);

        assertAll(
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID, baseForNew.skillId()),
                () -> assertSame(supplied, baseForNew.suppliedBase()),
                () -> assertSame(latest, missing.latest()),
                () -> assertSame(SubmissionAuthorityTestFixtures.SKILL_ID, missing.skillId()),
                () -> assertSame(latest, stale.latest()),
                () -> assertSame(latest, future.latest()));
    }

    @Test
    void staleAndFutureConstructorsEnforceTheirRevisionDirection() {
        var latest = latest(5);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionConflict.StaleBase(
                                new SkillRevision(5), latest)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionConflict.StaleBase(
                                new SkillRevision(6), latest)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionConflict.FutureBase(
                                new SkillRevision(5), latest)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new SkillSubmissionConflict.FutureBase(
                                new SkillRevision(4), latest)));
    }

    @Test
    void everyConflictVariantRejectsNullComponents() {
        var revision = new SkillRevision(1);
        var latest = latest(2);

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.BaseRevisionForNew(null, revision)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.BaseRevisionForNew(
                                SubmissionAuthorityTestFixtures.SKILL_ID, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.MissingBaseForExisting(null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.StaleBase(null, latest)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.StaleBase(revision, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.FutureBase(null, latest)),
                () -> assertThrows(NullPointerException.class,
                        () -> new SkillSubmissionConflict.FutureBase(revision, null)));
    }

    private static SkillReference latest(int revision) {
        return new SkillReference(
                SubmissionAuthorityTestFixtures.SKILL_ID,
                new SkillRevision(revision));
    }
}
