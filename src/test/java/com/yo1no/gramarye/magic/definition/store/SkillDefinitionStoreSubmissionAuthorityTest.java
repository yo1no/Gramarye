package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SkillDefinitionStoreSubmissionAuthorityTest {
    private static final SkillId SKILL_ID = new SkillId(
            UUID.fromString("71d28632-409e-47a5-a9cb-1b2573c7d11c"));
    private static final SkillOwnerId OWNER = new SkillOwnerId(
            UUID.fromString("c24034d6-4915-47d2-91a0-970314203ef2"));
    private static final SkillOwnerId FOREIGN = new SkillOwnerId(
            UUID.fromString("0d273068-b7f6-41c2-b4bb-f4fa799a1ba5"));
    private static final SkillId AUDIT_SKILL_A = StoreTestFixtures.skillId(0x101);
    private static final SkillId AUDIT_SKILL_B = StoreTestFixtures.skillId(0x202);

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

    @Test
    void journalAuditLooksUpEachEncounteredDistinctRouteOnceAndClearsObservations() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(AUDIT_SKILL_A, OWNER, 0, 1),
                StoreTestFixtures.history(AUDIT_SKILL_B, OWNER, 0)));
        var a0 = reference(AUDIT_SKILL_A, 0);
        var a1 = reference(AUDIT_SKILL_A, 1);
        var b0 = reference(AUDIT_SKILL_B, 0);
        var journal = admittedJournal(
                entry(OWNER, AUDIT_SKILL_A, 0, 1, Optional.empty(), a0),
                entry(OWNER, AUDIT_SKILL_A, 1, 2, Optional.of(a0), a1),
                entry(OWNER, AUDIT_SKILL_B, 0, 1, Optional.empty(), b0));
        var lookups = new ArrayList<SkillId>();
        var presentObservations = new ArrayList<P4E1StoreHistoryObservation.Present>();

        var audited = assertInstanceOf(
                JournalTargetAuditResult.Audited.class,
                store.auditJournalTargets(journal, skillId -> {
                    lookups.add(skillId);
                    var observation = store.observeExactHistoryForRootAudit(skillId);
                    if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                        presentObservations.add(present);
                    }
                    return observation;
                }));

        assertEquals(List.of(AUDIT_SKILL_A, AUDIT_SKILL_B), lookups);
        assertTrue(audited.proof().isFor(journal));
        assertEquals(Optional.of(a1), store.latestReference(AUDIT_SKILL_A));
        assertEquals(Optional.of(b0), store.latestReference(AUDIT_SKILL_B));
        assertEquals(2, store.committedSkillCount(OWNER));
        assertEquals(2, presentObservations.size());
        for (var present : presentObservations) {
            assertThrows(IllegalStateException.class, () -> present.ownerMatches(OWNER));
        }
    }

    @Test
    void ownerMismatchPrecedesMissingRevisionAndStopsBeforeLaterRoutes() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(AUDIT_SKILL_A, OWNER, 0),
                StoreTestFixtures.history(AUDIT_SKILL_B, FOREIGN, 0)));
        var missingA = reference(AUDIT_SKILL_A, 99);
        var journal = admittedJournal(
                entry(FOREIGN, AUDIT_SKILL_A, 0, 1, Optional.empty(), missingA),
                entry(FOREIGN, AUDIT_SKILL_B, 0, 1, Optional.empty(),
                        reference(AUDIT_SKILL_B, 0)));
        var lookups = new ArrayList<SkillId>();
        var presentObservations = new ArrayList<P4E1StoreHistoryObservation.Present>();

        var rejected = assertInstanceOf(
                JournalTargetAuditResult.Rejected.class,
                store.auditJournalTargets(journal, skillId -> {
                    lookups.add(skillId);
                    var observation = store.observeExactHistoryForRootAudit(skillId);
                    if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                        presentObservations.add(present);
                    }
                    return observation;
                }));

        assertEquals(List.of(AUDIT_SKILL_A), lookups);
        assertEquals(PendingAttachmentJournalFailure.Code.TARGET_OWNER_MISMATCH,
                rejected.failure().code());
        assertEquals(PendingAttachmentJournalFailure.Stage.TARGET_AUDIT,
                rejected.failure().stage());
        assertEquals(PendingAttachmentJournalFailure.Field.TARGET_POINTER,
                rejected.failure().field());
        assertEquals(0, rejected.failure().entryIndex());
        assertEquals(Optional.of(AUDIT_SKILL_A), rejected.failure().skillId());
        assertEquals(Optional.of(missingA), rejected.failure().reference());
        assertEquals(Optional.empty(), rejected.failure().exceptionClassName());
        assertEquals(1, presentObservations.size());
        assertThrows(IllegalStateException.class,
                () -> presentObservations.get(0).contains(missingA));
    }

    @Test
    void absentAndMissingRevisionTerminateWithoutLookingUpLaterRoutes() {
        var b0 = reference(AUDIT_SKILL_B, 0);
        var journal = admittedJournal(
                entry(OWNER, AUDIT_SKILL_A, 0, 1, Optional.empty(),
                        reference(AUDIT_SKILL_A, 0)),
                entry(OWNER, AUDIT_SKILL_B, 0, 1, Optional.empty(), b0));
        var onlyB = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(AUDIT_SKILL_B, OWNER, 0)));
        var absentLookups = new ArrayList<SkillId>();

        var absent = assertInstanceOf(
                JournalTargetAuditResult.Rejected.class,
                onlyB.auditJournalTargets(journal, skillId -> {
                    absentLookups.add(skillId);
                    return onlyB.observeExactHistoryForRootAudit(skillId);
                }));
        assertEquals(List.of(AUDIT_SKILL_A), absentLookups);
        assertEquals(PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                absent.failure().code());

        var both = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(AUDIT_SKILL_A, OWNER, 0),
                StoreTestFixtures.history(AUDIT_SKILL_B, OWNER, 0)));
        var missingReference = reference(AUDIT_SKILL_A, 1);
        var missingJournal = admittedJournal(
                entry(OWNER, AUDIT_SKILL_A, 0, 1, Optional.empty(), missingReference),
                entry(OWNER, AUDIT_SKILL_B, 0, 1, Optional.empty(), b0));
        var missingLookups = new ArrayList<SkillId>();
        var missingPresent = new ArrayList<P4E1StoreHistoryObservation.Present>();

        var missing = assertInstanceOf(
                JournalTargetAuditResult.Rejected.class,
                both.auditJournalTargets(missingJournal, skillId -> {
                    missingLookups.add(skillId);
                    var observation = both.observeExactHistoryForRootAudit(skillId);
                    if (observation instanceof P4E1StoreHistoryObservation.Present present) {
                        missingPresent.add(present);
                    }
                    return observation;
                }));
        assertEquals(List.of(AUDIT_SKILL_A), missingLookups);
        assertEquals(PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                missing.failure().code());
        assertEquals(Optional.of(missingReference), missing.failure().reference());
        assertEquals(1, missingPresent.size());
        assertThrows(IllegalStateException.class,
                () -> missingPresent.get(0).contains(missingReference));
    }

    @Test
    void runtimeExceptionAndErrorClearPriorObservationsAndPropagateUnchanged() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(AUDIT_SKILL_A, OWNER, 0),
                StoreTestFixtures.history(AUDIT_SKILL_B, OWNER, 0)));
        var journal = admittedJournal(
                entry(OWNER, AUDIT_SKILL_A, 0, 1, Optional.empty(),
                        reference(AUDIT_SKILL_A, 0)),
                entry(OWNER, AUDIT_SKILL_B, 0, 1, Optional.empty(),
                        reference(AUDIT_SKILL_B, 0)));

        var runtime = new IllegalStateException("injected-runtime");
        var runtimeLookups = new ArrayList<SkillId>();
        var runtimePresent = new ArrayList<P4E1StoreHistoryObservation.Present>();
        assertSame(runtime, assertThrows(IllegalStateException.class,
                () -> store.auditJournalTargets(journal, skillId -> {
                    runtimeLookups.add(skillId);
                    if (skillId.equals(AUDIT_SKILL_B)) {
                        throw runtime;
                    }
                    var observation = store.observeExactHistoryForRootAudit(skillId);
                    runtimePresent.add((P4E1StoreHistoryObservation.Present) observation);
                    return observation;
                })));
        assertEquals(List.of(AUDIT_SKILL_A, AUDIT_SKILL_B), runtimeLookups);
        assertThrows(IllegalStateException.class,
                () -> runtimePresent.get(0).ownerMatches(OWNER));

        var error = new AssertionError("injected-error");
        var errorLookups = new ArrayList<SkillId>();
        var errorPresent = new ArrayList<P4E1StoreHistoryObservation.Present>();
        assertSame(error, assertThrows(AssertionError.class,
                () -> store.auditJournalTargets(journal, skillId -> {
                    errorLookups.add(skillId);
                    if (skillId.equals(AUDIT_SKILL_B)) {
                        throw error;
                    }
                    var observation = store.observeExactHistoryForRootAudit(skillId);
                    errorPresent.add((P4E1StoreHistoryObservation.Present) observation);
                    return observation;
                })));
        assertEquals(List.of(AUDIT_SKILL_A, AUDIT_SKILL_B), errorLookups);
        assertThrows(IllegalStateException.class,
                () -> errorPresent.get(0).ownerMatches(OWNER));
    }

    private static PendingAttachmentJournal admittedJournal(
            SkillOwnerId owner, SkillReference target) {
        return admittedJournal(entry(
                owner, target.skillId(), 0, 1, Optional.empty(), target));
    }

    private static PendingAttachmentJournal admittedJournal(
            PendingAttachmentJournalEntryPhysicalV0... entries) {
        var physical = new PendingAttachmentJournalPhysicalV0(0, List.of(entries));
        return assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(physical)).journal();
    }

    private static PendingAttachmentJournalEntryPhysicalV0 entry(
            SkillOwnerId owner,
            SkillId skillId,
            int expectedGeneration,
            int targetGeneration,
            Optional<SkillReference> expectedPointer,
            SkillReference targetPointer) {
        return new PendingAttachmentJournalEntryPhysicalV0(
                owner,
                skillId,
                expectedGeneration,
                targetGeneration,
                expectedPointer,
                targetPointer);
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, StoreTestFixtures.revision(revision));
    }
}
