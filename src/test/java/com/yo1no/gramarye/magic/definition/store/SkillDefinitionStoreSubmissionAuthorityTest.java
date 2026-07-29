package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import java.util.UUID;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SkillDefinitionStoreSubmissionAuthorityTest {
    private static final SkillId SKILL_ID = new SkillId(
            UUID.fromString("71d28632-409e-47a5-a9cb-1b2573c7d11c"));
    private static final SkillOwnerId OWNER = new SkillOwnerId(
            UUID.fromString("c24034d6-4915-47d2-91a0-970314203ef2"));
    private static final SkillOwnerId FOREIGN = new SkillOwnerId(
            UUID.fromString("0d273068-b7f6-41c2-b4bb-f4fa799a1ba5"));

    @Test
    void observationDistinguishesAbsentOwnedAndPrivateForeignState() {
        var store = new SkillDefinitionStore();
        assertEquals(
                new StoreSubmissionAuthorityObservation.Absent(SKILL_ID),
                store.observeSubmissionAuthority(SKILL_ID, OWNER));

        var committed = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER),
                        SkillQuota.Unlimited.INSTANCE));
        assertEquals(
                new StoreSubmissionAuthorityObservation.Owned(committed.committed()),
                store.observeSubmissionAuthority(SKILL_ID, OWNER));
        assertEquals(
                new StoreSubmissionAuthorityObservation.ForeignOwned(SKILL_ID),
                store.observeSubmissionAuthority(SKILL_ID, FOREIGN));
    }

    @Test
    void ownedObservationUsesTheMaximumRetainedRevision() {
        var store = new SkillDefinitionStore();
        store.commit(
                SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER),
                SkillQuota.Unlimited.INSTANCE);
        store.commit(
                SubmissionPlanTestFactory.existingPlan(
                        SKILL_ID, OWNER, new SkillRevision(0)),
                SkillQuota.Unlimited.INSTANCE);

        var owned = assertInstanceOf(
                StoreSubmissionAuthorityObservation.Owned.class,
                store.observeSubmissionAuthority(SKILL_ID, OWNER));
        assertEquals(new SkillReference(SKILL_ID, new SkillRevision(1)), owned.latest());
    }

    @Test
    void journalTargetAuditRequiresExactTargetAndOwnerWithoutMutatingStore() {
        var store = new SkillDefinitionStore();
        var target = assertInstanceOf(
                SkillStoreCommitResult.Committed.class,
                store.commit(
                        SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER),
                        SkillQuota.Unlimited.INSTANCE)).committed();
        var journal = admittedJournal(OWNER, target);

        var audited = assertInstanceOf(
                JournalTargetAuditResult.Audited.class,
                store.auditJournalTargets(journal));
        assertTrue(assertInstanceOf(
                JournalTargetAuditProof.AuditedExisting.class,
                audited.proof()).isFor(journal));
        assertEquals(Optional.of(target), store.latestReference(SKILL_ID));

        var foreign = assertInstanceOf(
                JournalTargetAuditResult.Rejected.class,
                store.auditJournalTargets(admittedJournal(FOREIGN, target)));
        assertEquals(
                PendingAttachmentJournalFailure.Code.TARGET_OWNER_MISMATCH,
                foreign.failure().code());

        var missingReference = new SkillReference(SKILL_ID, new SkillRevision(1));
        var missing = assertInstanceOf(
                JournalTargetAuditResult.Rejected.class,
                store.auditJournalTargets(admittedJournal(OWNER, missingReference)));
        assertEquals(PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                missing.failure().code());
        assertEquals(Optional.of(missingReference), missing.failure().reference());
    }

    @Test
    void emptyJournalAuditSucceedsWithoutAStoreTarget() {
        var store = new SkillDefinitionStore();
        var journal = PendingAttachmentJournal.empty();
        var audited = assertInstanceOf(
                JournalTargetAuditResult.Audited.class,
                store.auditJournalTargets(journal));
        assertTrue(assertInstanceOf(
                JournalTargetAuditProof.AuditedExisting.class,
                audited.proof()).isFor(journal));
    }

    private static PendingAttachmentJournal admittedJournal(
            SkillOwnerId owner, SkillReference target) {
        var physical = new PendingAttachmentJournalPhysicalV0(
                0,
                List.of(new PendingAttachmentJournalEntryPhysicalV0(
                        owner,
                        target.skillId(),
                        0,
                        1,
                        Optional.empty(),
                        target)));
        return assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(physical)).journal();
    }
}
