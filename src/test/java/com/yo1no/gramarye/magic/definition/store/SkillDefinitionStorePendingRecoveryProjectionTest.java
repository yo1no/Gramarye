package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SkillDefinitionStorePendingRecoveryProjectionTest {
    private static final SkillOwnerId OWNER = owner(0x101);
    private static final SkillOwnerId OTHER_OWNER = owner(0x202);
    private static final SkillId SKILL_A = skill(0x10);
    private static final SkillId SKILL_B = skill(0x20);
    private static final SkillId SKILL_C = skill(0x30);
    private static final SkillReference A0 = reference(SKILL_A, 0);
    private static final SkillReference B0 = reference(SKILL_B, 0);
    private static final SkillReference B1 = reference(SKILL_B, 1);
    private static final SkillReference C0 = reference(SKILL_C, 0);

    @Test
    void projectionFiltersOwnerBuildsCanonicalContinuousImmutableChains() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(SKILL_C, OTHER_OWNER, 0),
                StoreTestFixtures.history(SKILL_B, OWNER, 0, 1),
                StoreTestFixtures.history(SKILL_A, OWNER, 0)));
        var journal = journal(
                entry(OTHER_OWNER, SKILL_C, 4, 5, Optional.empty(), C0),
                entry(OWNER, SKILL_B, 8, 9, Optional.empty(), B0),
                entry(OWNER, SKILL_A, 2, 3, Optional.empty(), A0),
                entry(OWNER, SKILL_B, 9, 10, Optional.of(B0), B1));
        var port = new SkillDefinitionStoreService().submissionPort();

        var available = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available.class,
                port.observePendingRecoveryCore(installed(store, journal), OWNER));

        assertEquals(List.of(SKILL_A, SKILL_B),
                available.chains().stream()
                        .map(SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain::skillId)
                        .toList());
        assertEquals(
                List.of(new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                        Optional.empty(), 2, A0, 3)),
                available.chains().get(0).steps());
        assertEquals(
                List.of(
                        new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                                Optional.empty(), 8, B0, 9),
                        new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                                Optional.of(B0), 9, B1, 10)),
                available.chains().get(1).steps());
        assertThrows(
                UnsupportedOperationException.class,
                () -> available.chains().add(available.chains().get(0)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> available.chains().get(1).steps().clear());
        assertTrue(available.chains().stream()
                .flatMap(chain -> chain.steps().stream())
                .noneMatch(step -> step.targetPointer().equals(C0)));
        assertEquals(
                Set.of("skillId", "steps"),
                Arrays.stream(SkillDefinitionStoreSubmissionPort
                                .PendingSkillRecoveryChain.class.getRecordComponents())
                        .map(component -> component.getName())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void noOwnerEntriesReturnsEmptyWithoutInvokingStoreAudit() {
        var journal = journal(entry(OTHER_OWNER, SKILL_C, 0, 1, Optional.empty(), C0));
        var auditCalls = new AtomicInteger();
        var port = new SkillDefinitionStoreService().submissionPort();

        var available = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available.class,
                port.observePendingRecoveryCore(
                        installed(new SkillDefinitionStore(), journal),
                        OWNER,
                        (store, ownerJournal) -> {
                            auditCalls.incrementAndGet();
                            return new JournalTargetAuditResult.Audited(
                                    new JournalTargetAuditProof.AuditedExisting(ownerJournal));
                        }));

        assertEquals(List.of(), available.chains());
        assertEquals(0, auditCalls.get());
    }

    @Test
    void canonicalZeroJournalProjectsAvailableEmpty() {
        var port = new SkillDefinitionStoreService().submissionPort();

        var available = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available.class,
                port.observePendingRecoveryCore(
                        installed(new SkillDefinitionStore(), PendingAttachmentJournal.empty()),
                        OWNER));

        assertEquals(List.of(), available.chains());
    }

    @Test
    void allOwnerStepsUseOneBoundedAuditInvocation() {
        var journal = journal(
                entry(OWNER, SKILL_A, 0, 1, Optional.empty(), A0),
                entry(OWNER, SKILL_B, 5, 6, Optional.empty(), B0),
                entry(OWNER, SKILL_B, 6, 7, Optional.of(B0), B1),
                entry(OTHER_OWNER, SKILL_C, 0, 1, Optional.empty(), C0));
        var auditCalls = new AtomicInteger();
        var port = new SkillDefinitionStoreService().submissionPort();

        var available = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available.class,
                port.observePendingRecoveryCore(
                        installed(new SkillDefinitionStore(), journal),
                        OWNER,
                        (store, ownerJournal) -> {
                            assertEquals(3, ownerJournal.entryCount());
                            assertEquals(Set.of(SKILL_A, SKILL_B),
                                    ownerJournal.entries().stream()
                                            .map(PendingAttachmentJournalEntry::skillId)
                                            .collect(java.util.stream.Collectors.toSet()));
                            assertTrue(ownerJournal.entries().stream()
                                    .allMatch(entry -> entry.owner().equals(OWNER)));
                            auditCalls.incrementAndGet();
                            return new JournalTargetAuditResult.Audited(
                                    new JournalTargetAuditProof.AuditedExisting(ownerJournal));
                        }));

        assertEquals(1, auditCalls.get());
        assertEquals(2, available.chains().size());
        assertEquals(3, available.chains().stream()
                .mapToInt(chain -> chain.steps().size())
                .sum());
    }

    @Test
    void liveMissingTargetReturnsNoPartialProjection() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(SKILL_A, OWNER, 0),
                StoreTestFixtures.history(SKILL_B, OWNER, 0)));
        var missingB1 = reference(SKILL_B, 1);
        var journal = journal(
                entry(OWNER, SKILL_A, 0, 1, Optional.empty(), A0),
                entry(OWNER, SKILL_B, 0, 1, Optional.empty(), missingB1));
        var port = new SkillDefinitionStoreService().submissionPort();

        var invalid = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.TargetInvalid.class,
                port.observePendingRecoveryCore(installed(store, journal), OWNER));

        assertEquals(SKILL_B, invalid.skillId());
        assertEquals(missingB1, invalid.target());
        assertEquals(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryTargetFailure.MISSING,
                invalid.reason());
    }

    @Test
    void liveOwnerMismatchReturnsBoundedTargetFailure() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(SKILL_A, OTHER_OWNER, 0)));
        var journal = journal(entry(OWNER, SKILL_A, 0, 1, Optional.empty(), A0));
        var port = new SkillDefinitionStoreService().submissionPort();

        var invalid = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.TargetInvalid.class,
                port.observePendingRecoveryCore(installed(store, journal), OWNER));

        assertEquals(SKILL_A, invalid.skillId());
        assertEquals(A0, invalid.target());
        assertEquals(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryTargetFailure.OWNER_MISMATCH,
                invalid.reason());
    }

    @Test
    void foreignOwnerDriftIsNeitherAuditedNorLeaked() {
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(SKILL_A, OWNER, 0)));
        var journal = journal(
                entry(OWNER, SKILL_A, 0, 1, Optional.empty(), A0),
                entry(OTHER_OWNER, SKILL_C, 0, 1, Optional.empty(), C0));
        var port = new SkillDefinitionStoreService().submissionPort();

        var available = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available.class,
                port.observePendingRecoveryCore(installed(store, journal), OWNER));

        assertEquals(1, available.chains().size());
        assertEquals(SKILL_A, available.chains().get(0).skillId());
    }

    @Test
    void lifecycleUnavailabilityMapsWithoutAuditing() {
        var empty = PendingAttachmentJournal.empty();
        var uninitialized = uninitialized(new SkillDefinitionStore(), empty);
        var unavailableJournal = installedUnavailable(new SkillDefinitionStore(), empty);
        var unavailableStore = installed(new SkillDefinitionStore(), empty);
        var current = unavailableStore.state();
        unavailableStore.publishState(
                current,
                new SkillSavedDataState.Unavailable(
                        SkillSavedDataRuntimeFailure.submissionPostCommitInvariant()));
        var auditCalls = new AtomicInteger();
        SkillDefinitionStoreSubmissionPort.RecoveryTargetAuditor auditor =
                (store, journal) -> {
                    auditCalls.incrementAndGet();
                    return new JournalTargetAuditResult.Audited(
                            new JournalTargetAuditProof.AuditedExisting(journal));
                };
        var port = new SkillDefinitionStoreService().submissionPort();

        assertUnavailable(
                port.observePendingRecoveryCore(uninitialized, OWNER, auditor),
                SkillDefinitionStoreSubmissionPort.PendingRecoveryUnavailableReason
                        .JOURNAL_NOT_BOOTSTRAPPED);
        assertUnavailable(
                port.observePendingRecoveryCore(unavailableJournal, OWNER, auditor),
                SkillDefinitionStoreSubmissionPort.PendingRecoveryUnavailableReason
                        .JOURNAL_UNAVAILABLE);
        assertUnavailable(
                port.observePendingRecoveryCore(unavailableStore, OWNER, auditor),
                SkillDefinitionStoreSubmissionPort.PendingRecoveryUnavailableReason
                        .STORE_UNAVAILABLE);
        assertEquals(0, auditCalls.get());
    }

    @Test
    void publicProjectionValuesDefensivelyCopyAndRejectBrokenChains() {
        var mutableSteps = new ArrayList<SkillDefinitionStoreSubmissionPort.PendingRecoveryStep>();
        mutableSteps.add(new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                Optional.empty(), 0, A0, 1));
        var chain = new SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain(
                SKILL_A, mutableSteps);
        mutableSteps.clear();
        assertEquals(1, chain.steps().size());

        var mutableChains = new ArrayList<SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain>();
        mutableChains.add(chain);
        var projection = new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available(
                mutableChains);
        mutableChains.clear();
        assertEquals(1, projection.chains().size());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillDefinitionStoreSubmissionPort.PendingSkillRecoveryChain(
                        SKILL_A, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillDefinitionStoreSubmissionPort.PendingRecoveryStep(
                        Optional.empty(), 1, A0, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.Available(
                        List.of(chain, chain)));
    }

    private static void assertUnavailable(
            SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection projection,
            SkillDefinitionStoreSubmissionPort.PendingRecoveryUnavailableReason reason) {
        assertEquals(
                reason,
                assertInstanceOf(
                        SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection
                                .Unavailable.class,
                        projection).reason());
    }

    private static GramaryeSkillSavedData installed(
            SkillDefinitionStore store, PendingAttachmentJournal journal) {
        var adapter = uninitialized(store, journal);
        var current = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var encoded = new EncodedPendingAttachmentJournal(
                current.innerCarrier().pending(), journal.entryCount());
        var journalReady = new PendingAttachmentJournalState.Ready(
                journal,
                encoded,
                current.innerCarrier().pending(),
                false,
                new JournalTargetAuditProof.AuditedExisting(journal));
        adapter.publishState(
                current,
                current.withJournalLifecycle(
                        new PendingAttachmentJournalLifecycle.Installed(journalReady)));
        return adapter;
    }

    private static GramaryeSkillSavedData installedUnavailable(
            SkillDefinitionStore store, PendingAttachmentJournal journal) {
        var adapter = uninitialized(store, journal);
        var current = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var unavailable = new PendingAttachmentJournalState.Unavailable(
                new PendingAttachmentJournalOperationalFailure.Persistence(
                        PendingAttachmentJournalFailure.simple(
                                PendingAttachmentJournalFailure.Code.JOURNAL_UNAVAILABLE)));
        adapter.publishState(
                current,
                current.withJournalLifecycle(
                        new PendingAttachmentJournalLifecycle.Installed(unavailable)));
        return adapter;
    }

    private static GramaryeSkillSavedData uninitialized(
            SkillDefinitionStore store, PendingAttachmentJournal journal) {
        var carrier = assertInstanceOf(
                CarrierBuildResult.Success.class,
                SkillStoreCarrierBuilder.rebuild(store)).carrier();
        var encoded = assertInstanceOf(
                PendingAttachmentJournalFraming.JournalEncodingResult.Encoded.class,
                PendingAttachmentJournalFraming.encode(journal)).journal();
        var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                encoded.pending(),
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(carrier.storeByteCount(), encoded.byteCount())));
        return GramaryeSkillSavedData.ready(
                SkillSavedDataReadyCandidate.afterCarrierRebuild(
                        store,
                        inner,
                        new PipelineFactReport(List.of(), false),
                        false));
    }

    private static PendingAttachmentJournal journal(
            PendingAttachmentJournalEntryPhysicalV0... entries) {
        return assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, List.of(entries))))
                .journal();
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

    private static SkillOwnerId owner(long lowBits) {
        return new SkillOwnerId(new UUID(0, lowBits));
    }

    private static SkillId skill(long lowBits) {
        return new SkillId(new UUID(0, lowBits));
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, StoreTestFixtures.revision(revision));
    }
}
