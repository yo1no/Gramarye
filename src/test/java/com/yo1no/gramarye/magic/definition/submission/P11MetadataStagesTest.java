package com.yo1no.gramarye.magic.definition.submission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataInitialStage;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataStages;
import org.junit.jupiter.api.Test;

/** Direct finite-stage owner tests; native lease/Attachment identity is a separate integration layer. */
final class P11MetadataStagesTest {
    @Test
    void completedRecoveryMayContinueOnlyTheUnstartedE2Stage() {
        var stages = recovered();
        assertFalse(stages.resumable());
        stages.finishAttempt(false);
        assertTrue(stages.resumable());
        assertThrows(IllegalStateException.class, stages::recoveryStarted);
        stages.reconciliationStarted();
        assertFalse(stages.resumable());
        stages.reconciliationCompleted(true);
        assertTrue(stages.resumable());
        assertThrows(IllegalStateException.class, stages::reconciliationStarted);
    }

    @Test
    void consumedOrPossiblyPublishedE2IsUnknownNotNotStarted() {
        var stages = recovered();
        stages.reconciliationStarted();
        stages.finishAttempt(false);
        assertFalse(stages.resumable());
        assertThrows(IllegalStateException.class, stages::reconciliationStarted);
        assertThrows(IllegalStateException.class, () -> stages.reconciliationCompleted(true));
    }

    @Test
    void openedSessionCannotBeReopenedButMissingInitialFamiliesCanFinish() {
        var stages = session();
        stages.finishAttempt(false);
        assertTrue(stages.resumable());
        assertThrows(IllegalStateException.class, stages::sessionStarted);
        stages.initialSync(MetadataInitialStage.MANA_STARTED);
        stages.initialSync(MetadataInitialStage.MANA_SUBMITTED);
        assertTrue(stages.resumable());
        stages.initialSync(MetadataInitialStage.COOLDOWN_STARTED);
        stages.initialSync(MetadataInitialStage.COOLDOWN_SUBMITTED);
        assertTrue(stages.complete());
        assertFalse(stages.resumable());
    }

    @Test
    void unknownSessionAndUnknownSubmissionNeverBecomeResumable() {
        var opening = recovered();
        opening.reconciliationStarted();
        opening.reconciliationCompleted(true);
        opening.sessionStarted();
        opening.finishAttempt(false);
        assertFalse(opening.resumable());
        assertThrows(IllegalStateException.class, opening::sessionOpened);

        for (var failure : new MetadataInitialStage[] {
                MetadataInitialStage.MANA_FAILED, MetadataInitialStage.COOLDOWN_FAILED}) {
            var stages = session();
            stages.initialSync(MetadataInitialStage.MANA_STARTED);
            if (failure == MetadataInitialStage.COOLDOWN_FAILED) {
                stages.initialSync(MetadataInitialStage.MANA_SUBMITTED);
                stages.initialSync(MetadataInitialStage.COOLDOWN_STARTED);
            }
            stages.initialSync(failure);
            assertFalse(stages.resumable());
            stages.finishAttempt(true);
            assertFalse(stages.resumable());
        }
    }

    @Test
    void rejectedReconciliationAndDuplicateSubmissionDoNotGrantReadiness() {
        var rejected = recovered();
        rejected.reconciliationStarted();
        rejected.reconciliationCompleted(false);
        rejected.finishAttempt(false);
        assertFalse(rejected.resumable());
        var duplicate = session();
        duplicate.initialSync(MetadataInitialStage.MANA_STARTED);
        duplicate.initialSync(MetadataInitialStage.MANA_SUBMITTED);
        duplicate.initialSync(MetadataInitialStage.MANA_SUBMITTED);
        duplicate.finishAttempt(false);
        assertFalse(duplicate.resumable());
    }

    private static MetadataStages recovered() {
        var stages = new MetadataStages();
        stages.recoveryStarted();
        stages.recoveryCompleted();
        return stages;
    }

    private static MetadataStages session() {
        var stages = recovered();
        stages.reconciliationStarted();
        stages.reconciliationCompleted(true);
        stages.sessionStarted();
        stages.sessionOpened();
        return stages;
    }
}
