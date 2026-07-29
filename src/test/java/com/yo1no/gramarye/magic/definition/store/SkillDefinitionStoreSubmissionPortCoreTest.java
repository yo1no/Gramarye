package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

final class SkillDefinitionStoreSubmissionPortCoreTest {
    private static final SkillId SKILL_ID = new SkillId(
            UUID.fromString("07fd81ec-dc3a-4778-a509-c5361be5a19e"));
    private static final SkillOwnerId OWNER = new SkillOwnerId(
            UUID.fromString("6ca19576-98af-47e9-847b-ed3e2a526d6e"));

    @Test
    void prepareDoesNoMutationAndCommitInvokesStoreOnceThenPublishesDirty() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var baseState = fixture.adapter().state();
        var quota = new SkillQuota.Limited(17);
        var prepared = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Prepared.class,
                fixture.port().prepareSubmissionCommitCore(
                        fixture.serverIdentity(),
                        fixture.adapter(),
                        plan,
                        quota,
                        changedView(
                                fixture.serverIdentity(), Optional.empty(), 0, target, 1)));

        assertSame(baseState, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertEquals(Optional.empty(), fixture.store().latestReference(SKILL_ID));

        var invocations = new AtomicInteger();
        var committed = fixture.port().commitPreparedSubmissionCore(
                fixture.serverIdentity(),
                prepared.handle(),
                fixture::adapter,
                (store, candidatePlan, candidateQuota) -> {
                    invocations.incrementAndGet();
                    assertSame(quota, candidateQuota);
                    return store.commit(candidatePlan, candidateQuota);
                });

        assertEquals(1, invocations.get());
        assertEquals(
                target,
                assertInstanceOf(
                        SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                        committed).reference());
        assertTrue(fixture.adapter().isDirty());
        assertEquals(Optional.of(target), fixture.store().latestReference(SKILL_ID));
        var journalReady = currentJournalReady(fixture.adapter());
        assertEquals(1, journalReady.journal().entryCount());
        assertTrue(journalReady.encoded().byteCount() > 0);
        assertInstanceOf(
                JournalTargetAuditProof.ConditionalOnExactCommit.class,
                journalReady.targetAuditProof());
        assertSame(
                journalReady.encoded().pending(),
                assertInstanceOf(SkillSavedDataState.Ready.class, fixture.adapter().state())
                        .innerCarrier().pending());
        assertPayloadReleased(prepared.handle());
        assertThrows(
                IllegalStateException.class,
                () -> fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        prepared.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));
    }

    @Test
    void typedStoreFailureConsumesHandleWithoutPublicationOrDirtyDelta() {
        var fixture = fixture();
        fixture.adapter().setDirty();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var prepared = prepare(fixture, plan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, referenceOf(plan), 1));
        var base = fixture.adapter().state();
        var invocations = new AtomicInteger();

        var result = fixture.port().commitPreparedSubmissionCore(
                fixture.serverIdentity(),
                prepared.handle(),
                fixture::adapter,
                (store, ignoredPlan, ignoredQuota) -> {
                    invocations.incrementAndGet();
                    return new SkillStoreCommitResult.OwnerRejected(SKILL_ID);
                });

        assertEquals(1, invocations.get());
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.DomainRejected.class,
                result);
        assertSame(base, fixture.adapter().state());
        assertTrue(fixture.adapter().isDirty());
        assertEquals(Optional.empty(), fixture.store().latestReference(SKILL_ID));
        assertPayloadReleased(prepared.handle());
        assertThrows(
                IllegalStateException.class,
                () -> fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        prepared.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));
    }

    @Test
    void everyP3DTypedFailureIsReturnedExactlyWithoutPublication() {
        java.util.List<Supplier<SkillStoreCommitResult>> failures = java.util.List.of(
                () -> new SkillStoreCommitResult.Conflict(
                        new SkillStoreCommitConflict.ExpectedAbsentButPresent(SKILL_ID)),
                () -> new SkillStoreCommitResult.QuotaRejected(SKILL_ID, 0, 0),
                () -> new SkillStoreCommitResult.CapacityRejected(
                        SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                        SkillStoreCapacityScope.OWNER_SKILL_HISTORIES.canonicalMaximum(),
                        SkillStoreCapacityScope.OWNER_SKILL_HISTORIES.canonicalMaximum()),
                () -> new SkillStoreCommitResult.OwnerRejected(SKILL_ID));

        for (var supplied : failures) {
            var fixture = fixture();
            var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
            var prepared = prepare(fixture, plan, changedView(
                    fixture.serverIdentity(),
                    Optional.empty(),
                    0,
                    referenceOf(plan),
                    1));
            var base = fixture.adapter().state();
            var failure = supplied.get();

            var rejected = assertInstanceOf(
                    SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                            .DomainRejected.class,
                    fixture.port().commitPreparedSubmissionCore(
                            fixture.serverIdentity(),
                            prepared.handle(),
                            fixture::adapter,
                            (store, ignoredPlan, ignoredQuota) -> failure));

            assertSame(failure, rejected.result());
            assertSame(base, fixture.adapter().state());
            assertFalse(fixture.adapter().isDirty());
            assertEquals(Optional.empty(), fixture.store().latestReference(SKILL_ID));
            assertPayloadReleased(prepared.handle());
        }
    }

    @Test
    void baseDriftConsumesHandleBeforeStoreInvocation() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var prepared = prepare(fixture, plan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, referenceOf(plan), 1));
        var base = assertInstanceOf(SkillSavedDataState.Ready.class, fixture.adapter().state());
        var replacement = base.withJournalLifecycle(base.journalLifecycle());
        fixture.adapter().publishState(base, replacement);
        var invocations = new AtomicInteger();

        var result = fixture.port().commitPreparedSubmissionCore(
                fixture.serverIdentity(),
                prepared.handle(),
                fixture::adapter,
                (store, ignoredPlan, ignoredQuota) -> {
                    invocations.incrementAndGet();
                    return store.commit(ignoredPlan, ignoredQuota);
                });

        assertEquals(0, invocations.get());
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.PreparedBaseMismatch.class,
                result);
        assertSame(replacement, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertPayloadReleased(prepared.handle());
    }

    @Test
    void wrongServerAndWrongPortRejectWithoutConsumingTheHandle() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var prepared = prepare(fixture, plan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, target, 1));
        var invocations = new AtomicInteger();
        SkillDefinitionStoreSubmissionPort.StoreCommitInvoker invoker =
                (store, candidatePlan, quota) -> {
                    invocations.incrementAndGet();
                    return store.commit(candidatePlan, quota);
                };

        assertThrows(
                IllegalStateException.class,
                () -> fixture.port().commitPreparedSubmissionCore(
                        new Object(),
                        prepared.handle(),
                        fixture::adapter,
                        invoker));
        var foreignPort = new SkillDefinitionStoreService().submissionPort();
        assertThrows(
                IllegalStateException.class,
                () -> foreignPort.commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        prepared.handle(),
                        fixture::adapter,
                        invoker));
        assertEquals(0, invocations.get());
        assertPayloadRetained(prepared.handle());

        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        prepared.handle(),
                        fixture::adapter,
                        invoker));
        assertEquals(1, invocations.get());
        assertPayloadReleased(prepared.handle());
    }

    @Test
    void committedReferenceMismatchPublishesPrebuiltUnavailableAndClearsDirty() {
        var fixture = fixture();
        fixture.adapter().setDirty();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var prepared = prepare(fixture, plan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, target, 1));
        var fakeReference = new SkillReference(SKILL_ID, new SkillRevision(1));

        var result = fixture.port().commitPreparedSubmissionCore(
                fixture.serverIdentity(),
                prepared.handle(),
                fixture::adapter,
                (store, candidatePlan, quota) -> {
                    assertInstanceOf(
                            SkillStoreCommitResult.Committed.class,
                            store.commit(candidatePlan, quota));
                    return new SkillStoreCommitResult.Committed(fakeReference);
                });

        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult
                        .PostCommitInvariantFailure.class,
                result);
        assertInstanceOf(SkillSavedDataState.Unavailable.class, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertEquals(Optional.of(target), fixture.store().latestReference(SKILL_ID));
        assertPayloadReleased(prepared.handle());
    }

    @Test
    void prefixClearPreparesWithoutMutationThenPublishesZeroAndKeepsStore() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var prepared = prepare(fixture, plan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, target, 1));
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        prepared.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));
        fixture.adapter().setDirty(false);
        var beforeClear = fixture.adapter().state();

        var clearPrepared = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Prepared.class,
                fixture.port().prepareJournalPrefixClearCore(
                        fixture.serverIdentity(),
                        fixture.adapter(),
                        OWNER,
                        SKILL_ID,
                        1,
                        target));
        assertSame(beforeClear, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());

        var cleared = fixture.port().commitPreparedJournalClearCore(
                fixture.serverIdentity(), clearPrepared.handle(), fixture::adapter);

        assertEquals(
                1,
                assertInstanceOf(
                        SkillDefinitionStoreSubmissionPort.JournalClearCommitResult.Cleared.class,
                        cleared).entriesRemoved());
        assertTrue(fixture.adapter().isDirty());
        assertEquals(0, currentJournalReady(fixture.adapter()).journal().entryCount());
        assertTrue(currentJournalReady(fixture.adapter()).encoded().zero());
        assertEquals(Optional.of(target), fixture.store().latestReference(SKILL_ID));
        assertPayloadReleased(clearPrepared.handle());
        assertThrows(
                IllegalStateException.class,
                () -> fixture.port().commitPreparedJournalClearCore(
                        fixture.serverIdentity(), clearPrepared.handle(), fixture::adapter));
    }

    @Test
    void clearNoChainAndMismatchDoNotPublishOrDirty() {
        var fixture = fixture();
        var target = new SkillReference(SKILL_ID, new SkillRevision(0));
        var base = fixture.adapter().state();

        assertSame(
                SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.NoOp.INSTANCE,
                fixture.port().prepareJournalPrefixClearCore(
                        fixture.serverIdentity(),
                        fixture.adapter(),
                        OWNER,
                        SKILL_ID,
                        1,
                        target));
        assertSame(base, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
    }

    @Test
    void continuousAppendAndPrefixClearRetainTheCanonicalSuffixRoot() {
        var fixture = fixture();
        var firstPlan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var firstTarget = referenceOf(firstPlan);
        var first = prepare(fixture, firstPlan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, firstTarget, 1));
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        first.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));

        var secondPlan = SubmissionPlanTestFactory.existingPlan(
                SKILL_ID, OWNER, new SkillRevision(0));
        var secondTarget = referenceOf(secondPlan);
        var second = prepare(fixture, secondPlan, changedView(
                fixture.serverIdentity(), Optional.of(firstTarget), 1, secondTarget, 2));
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        second.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));
        assertEquals(
                java.util.List.of(firstTarget, secondTarget),
                currentJournalReady(fixture.adapter()).journal().targetReferences());
        fixture.adapter().setDirty(false);

        var clearFirst = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Prepared.class,
                fixture.port().prepareJournalPrefixClearCore(
                        fixture.serverIdentity(),
                        fixture.adapter(),
                        OWNER,
                        SKILL_ID,
                        1,
                        firstTarget));
        var cleared = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.JournalClearCommitResult.Cleared.class,
                fixture.port().commitPreparedJournalClearCore(
                        fixture.serverIdentity(), clearFirst.handle(), fixture::adapter));

        assertEquals(1, cleared.entriesRemoved());
        assertEquals(
                java.util.List.of(secondTarget),
                currentJournalReady(fixture.adapter()).journal().targetReferences());
        assertTrue(fixture.adapter().isDirty());
        assertEquals(Optional.of(secondTarget), fixture.store().latestReference(SKILL_ID));
    }

    @Test
    void clearBaseDriftConsumesHandleWithoutPublicationOrDirtyDelta() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var commit = prepare(fixture, plan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, target, 1));
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        commit.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));
        fixture.adapter().setDirty(false);
        var clear = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Prepared.class,
                fixture.port().prepareJournalPrefixClearCore(
                        fixture.serverIdentity(),
                        fixture.adapter(),
                        OWNER,
                        SKILL_ID,
                        1,
                        target));
        var base = assertInstanceOf(SkillSavedDataState.Ready.class, fixture.adapter().state());
        var drifted = base.withJournalLifecycle(base.journalLifecycle());
        fixture.adapter().publishState(base, drifted);

        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.JournalClearCommitResult
                        .PreparedBaseMismatch.class,
                fixture.port().commitPreparedJournalClearCore(
                        fixture.serverIdentity(), clear.handle(), fixture::adapter));
        assertSame(drifted, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertPayloadReleased(clear.handle());
        assertThrows(
                IllegalStateException.class,
                () -> fixture.port().commitPreparedJournalClearCore(
                        fixture.serverIdentity(), clear.handle(), fixture::adapter));
    }

    @Test
    void preparationRejectsWrongServerNoOpAndInvalidGenerationWithoutMutation() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var base = fixture.adapter().state();

        assertRejected(fixture.port().prepareSubmissionCommitCore(
                new Object(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                changedView(fixture.serverIdentity(), Optional.empty(), 0, target, 1)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure
                        .TRANSITION_SERVER_MISMATCH);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                SkillDefinitionStoreSubmissionPort.TransitionView.capture(
                        fixture.serverIdentity(),
                        OWNER,
                        SKILL_ID,
                        Optional.empty(),
                        0,
                        Optional.empty(),
                        0,
                        true)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.TRANSITION_NO_OP);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                changedView(fixture.serverIdentity(), Optional.empty(), 0, target, 2)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.GENERATION_INVALID);
        assertSame(base, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertEquals(Optional.empty(), fixture.store().latestReference(SKILL_ID));
    }

    @Test
    void preparationRejectsEveryStaticPairingAuthorityAndCarrierFailureWithoutMutation() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var otherOwner = new SkillOwnerId(
                UUID.fromString("935f506b-363e-4f61-a91d-4a7883728c0f"));
        var otherSkill = new SkillId(
                UUID.fromString("9360262d-7633-4c35-8d9c-cf834baf5a02"));
        var base = fixture.adapter().state();

        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                SkillDefinitionStoreSubmissionPort.TransitionView.capture(
                        fixture.serverIdentity(), OWNER, SKILL_ID,
                        Optional.empty(), 0, Optional.empty(), 1, false)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.TARGET_POINTER_MISSING);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                SkillDefinitionStoreSubmissionPort.TransitionView.capture(
                        fixture.serverIdentity(), otherOwner, SKILL_ID,
                        Optional.empty(), 0, Optional.of(target), 1, false)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.PLAN_OWNER_MISMATCH);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                SkillDefinitionStoreSubmissionPort.TransitionView.capture(
                        fixture.serverIdentity(), OWNER, otherSkill,
                        Optional.empty(), 0, Optional.of(target), 1, false)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.PLAN_SKILL_ID_MISMATCH);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                changedView(
                        fixture.serverIdentity(),
                        Optional.empty(),
                        0,
                        new SkillReference(SKILL_ID, new SkillRevision(1)),
                        1)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.PLAN_REFERENCE_MISMATCH);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                plan,
                SkillQuota.Unlimited.INSTANCE,
                changedView(
                        fixture.serverIdentity(), Optional.of(target), 0, target, 1)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.PRECONDITION_MISMATCH);

        var existingPlan = SubmissionPlanTestFactory.existingPlan(
                SKILL_ID, OWNER, new SkillRevision(0));
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                existingPlan,
                SkillQuota.Unlimited.INSTANCE,
                changedView(
                        fixture.serverIdentity(),
                        Optional.of(new SkillReference(SKILL_ID, new SkillRevision(0))),
                        0,
                        referenceOf(existingPlan),
                        1)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.AUTHORITY_MISMATCH);

        var oversized = SubmissionPlanTestFactory.oversizedDocumentPlan(
                SKILL_ID, OWNER, 5, 120_000);
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                oversized,
                SkillQuota.Unlimited.INSTANCE,
                changedView(
                        fixture.serverIdentity(),
                        Optional.empty(),
                        0,
                        referenceOf(oversized),
                        1)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.STORE_CARRIER_REJECTED);

        assertSame(base, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertEquals(Optional.empty(), fixture.store().latestReference(SKILL_ID));
    }

    @Test
    void preparationRejectsAnAppendThatDoesNotContinueTheRouteFinal() {
        var fixture = fixture();
        var firstPlan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var firstTarget = referenceOf(firstPlan);
        var first = prepare(fixture, firstPlan, changedView(
                fixture.serverIdentity(), Optional.empty(), 0, firstTarget, 1));
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionCommitResult.Committed.class,
                fixture.port().commitPreparedSubmissionCore(
                        fixture.serverIdentity(),
                        first.handle(),
                        fixture::adapter,
                        SkillDefinitionStore::commit));
        fixture.adapter().setDirty(false);
        var base = fixture.adapter().state();

        var secondPlan = SubmissionPlanTestFactory.existingPlan(
                SKILL_ID, OWNER, new SkillRevision(0));
        assertRejected(fixture.port().prepareSubmissionCommitCore(
                fixture.serverIdentity(),
                fixture.adapter(),
                secondPlan,
                SkillQuota.Unlimited.INSTANCE,
                changedView(
                        fixture.serverIdentity(),
                        Optional.of(firstTarget),
                        0,
                        referenceOf(secondPlan),
                        1)),
                SkillDefinitionStoreSubmissionPort.PreparationFailure.JOURNAL_REJECTED);

        assertSame(base, fixture.adapter().state());
        assertFalse(fixture.adapter().isDirty());
        assertEquals(Optional.of(firstTarget), fixture.store().latestReference(SKILL_ID));
    }

    @Test
    void publicationProofHasExactConditionalProvenanceWithoutBulkyRetention() {
        var fixture = fixture();
        var plan = SubmissionPlanTestFactory.newPlan(SKILL_ID, OWNER);
        var target = referenceOf(plan);
        var baseReady = assertInstanceOf(
                SkillSavedDataState.Ready.class, fixture.adapter().state());
        var baseJournal = currentJournalReady(fixture.adapter());
        var carrierUpdate = assertInstanceOf(
                CarrierUpdateResult.Prepared.class,
                SkillStoreCarrierBuilder.prepareProspectiveUpdate(
                        baseReady.storeCarrier(), plan)).update();
        var prospectiveJournal = assertInstanceOf(
                PendingAttachmentJournal.DomainMutation.Updated.class,
                baseJournal.journal().append(new PendingAttachmentJournalEntry(
                        OWNER,
                        SKILL_ID,
                        0,
                        1,
                        Optional.empty(),
                        target))).journal();
        var first = new JournalTargetAuditProof.ConditionalOnExactCommit(
                baseJournal,
                carrierUpdate,
                prospectiveJournal,
                OWNER,
                SKILL_ID,
                target,
                target);
        var second = new JournalTargetAuditProof.ConditionalOnExactCommit(
                baseJournal,
                carrierUpdate,
                prospectiveJournal,
                OWNER,
                SKILL_ID,
                target,
                target);
        assertEquals(
                Set.of(
                        JournalTargetAuditProof.AuditedExisting.class,
                        JournalTargetAuditProof.ConditionalOnExactCommit.class),
                Set.of(JournalTargetAuditProof.class.getPermittedSubclasses()));
        assertFalse(first.satisfy(
                baseJournal,
                carrierUpdate,
                OWNER,
                SKILL_ID,
                target,
                target,
                second));
        assertFalse(first.isSatisfied());
        assertTrue(first.satisfy(
                baseJournal,
                carrierUpdate,
                OWNER,
                SKILL_ID,
                target,
                target,
                first));
        assertTrue(first.isSatisfied());
        assertTrue(first.isFor(prospectiveJournal));
        assertEquals(
                prospectiveJournal,
                readField(first, "journal"));
        for (var field : List.of(
                "baseJournalState",
                "preparedCarrierUpdate",
                "planOwner",
                "skillId",
                "targetReference",
                "expectedCommittedReference")) {
            assertEquals(null, readField(first, field), field);
        }
    }

    private static Object readField(Object target, String name) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("unable to inspect conditional proof field " + name, exception);
        }
    }

    private static void assertPayloadReleased(Object handle) {
        assertEquals(null, readField(handle, "payload"));
    }

    private static void assertPayloadRetained(Object handle) {
        assertTrue(readField(handle, "payload") != null);
    }

    private static Fixture fixture() {
        var candidate = SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent();
        var adapter = GramaryeSkillSavedData.ready(candidate);
        var initial = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var journal = PendingAttachmentJournal.empty();
        var encoded = assertInstanceOf(
                PendingAttachmentJournalFraming.JournalEncodingResult.Encoded.class,
                PendingAttachmentJournalFraming.encode(journal)).journal();
        var journalReady = new PendingAttachmentJournalState.Ready(
                journal,
                encoded,
                initial.innerCarrier().pending(),
                false,
                new JournalTargetAuditProof.AuditedExisting(journal));
        var installed = initial.withJournalLifecycle(
                new PendingAttachmentJournalLifecycle.Installed(journalReady));
        adapter.publishState(initial, installed);
        var service = new SkillDefinitionStoreService();
        return new Fixture(
                new Object(), service.submissionPort(), adapter, installed.store());
    }

    private static SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Prepared prepare(
            Fixture fixture,
            SkillSubmissionPlan plan,
            SkillDefinitionStoreSubmissionPort.TransitionView transition) {
        return assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Prepared.class,
                fixture.port().prepareSubmissionCommitCore(
                        fixture.serverIdentity(),
                        fixture.adapter(),
                        plan,
                        SkillQuota.Unlimited.INSTANCE,
                        transition));
    }

    private static SkillDefinitionStoreSubmissionPort.TransitionView changedView(
            Object serverIdentity,
            Optional<SkillReference> expectedPointer,
            int expectedGeneration,
            SkillReference target,
            int targetGeneration) {
        return SkillDefinitionStoreSubmissionPort.TransitionView.capture(
                serverIdentity,
                OWNER,
                SKILL_ID,
                expectedPointer,
                expectedGeneration,
                Optional.of(target),
                targetGeneration,
                false);
    }

    private static SkillReference referenceOf(SkillSubmissionPlan plan) {
        return new SkillReference(
                plan.proposedDocument().skillId(), plan.proposedDocument().revision());
    }

    private static PendingAttachmentJournalState.Ready currentJournalReady(
            GramaryeSkillSavedData adapter) {
        var savedDataReady = assertInstanceOf(
                SkillSavedDataState.Ready.class, adapter.state());
        var lifecycle = assertInstanceOf(
                PendingAttachmentJournalLifecycle.Installed.class,
                savedDataReady.journalLifecycle());
        return assertInstanceOf(PendingAttachmentJournalState.Ready.class, lifecycle.state());
    }

    private static void assertRejected(
            SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult result,
            SkillDefinitionStoreSubmissionPort.PreparationFailure expected) {
        assertEquals(
                expected,
                assertInstanceOf(
                        SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Rejected.class,
                        result).failure());
    }

    private record Fixture(
            Object serverIdentity,
            SkillDefinitionStoreSubmissionPort port,
            GramaryeSkillSavedData adapter,
            SkillDefinitionStore store) {
    }
}
