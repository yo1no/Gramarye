package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.definition.submission.SubmissionPlanTestFactory;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class SkillDefinitionStoreSubmissionPortBootstrapTest {
    private static final SkillId SKILL_ID = StoreTestFixtures.skillId(811);
    private static final SkillOwnerId OWNER = StoreTestFixtures.ownerId(91);
    private static final SkillOwnerId FOREIGN_OWNER = StoreTestFixtures.ownerId(92);
    private static final SkillReference REVISION_ZERO =
            new SkillReference(SKILL_ID, StoreTestFixtures.revision(0));

    @Test
    void nonCanonicalBootstrapPublishesReplacementBeforeDirty() throws Exception {
        var source = PendingAttachmentJournalTestSupport.rootBytes(List.of());
        var adapter = ready(new SkillDefinitionStore(), source);
        var base = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var calls = new int[1];
        var port = new SkillDefinitionStoreService().submissionPort();

        var result = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready.class,
                port.bootstrapJournalCore(
                        adapter,
                        PendingAttachmentJournalFraming::load,
                        (expected, replacement, markDirty) -> {
                            calls[0]++;
                            assertSame(base, expected);
                            assertSame(base, adapter.state());
                            assertFalse(adapter.isDirty());
                            assertTrue(markDirty);
                            adapter.publishState(expected, replacement);
                            assertSame(replacement, adapter.state());
                            assertFalse(adapter.isDirty(),
                                    "publication must precede dirty marking");
                            adapter.setDirty();
                        }));

        var published = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var installed = assertInstanceOf(
                PendingAttachmentJournalLifecycle.Installed.class,
                published.journalLifecycle());
        var journalReady = assertInstanceOf(
                PendingAttachmentJournalState.Ready.class, installed.state());
        assertEquals(1, calls[0]);
        assertEquals(0, result.entryCount());
        assertTrue(result.rewritePublished());
        assertNotSame(base, published);
        assertFalse(published.rewriteRequired());
        assertSame(OpaquePendingAttachmentUpdatesBlob.empty(), published.innerCarrier().pending());
        assertSame(published.innerCarrier().pending(), journalReady.sourcePending());
        assertTrue(adapter.isDirty());
        assertArrayEquals(new byte[0], savedPending(adapter));
    }

    @Test
    void malformedAndMigrationFailuresInstallUnavailableWithoutChangingCarrierOrDirty()
            throws Exception {
        assertPersistenceFailure(
                new byte[] {Tag.TAG_COMPOUND},
                PendingAttachmentJournalFraming::load,
                PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                false);

        var migrationFailure = PendingAttachmentJournalFailure.simple(
                PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL);
        assertPersistenceFailure(
                PendingAttachmentJournalTestSupport.rootBytes(List.of()),
                source -> new PendingAttachmentJournalLoadResult.Rejected(migrationFailure),
                PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL,
                true);
    }

    @Test
    void targetAuditFailuresInstallUnavailableWithoutChangingCarrierOrDirty()
            throws Exception {
        var missingEntry = PendingAttachmentJournalTestSupport.physicalEntry(
                OWNER.value().getLeastSignificantBits(),
                SKILL_ID.value().getLeastSignificantBits(),
                0,
                1,
                Optional.empty(),
                0);
        assertTargetAuditFailure(
                new SkillDefinitionStore(),
                PendingAttachmentJournalTestSupport.rootBytes(List.of(missingEntry)),
                PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                false);

        var ownedStore = storeWithRevisionZero();
        var foreignEntry = PendingAttachmentJournalTestSupport.physicalEntry(
                FOREIGN_OWNER.value().getLeastSignificantBits(),
                SKILL_ID.value().getLeastSignificantBits(),
                0,
                1,
                Optional.empty(),
                0);
        assertTargetAuditFailure(
                ownedStore,
                PendingAttachmentJournalTestSupport.rootBytes(List.of(foreignEntry)),
                PendingAttachmentJournalFailure.Code.TARGET_OWNER_MISMATCH,
                true);
    }

    @Test
    void journalUnavailableLeavesStoreReadsAndPinsAvailableWhileD1OperationsFailClosed()
            throws Exception {
        var foreignEntry = PendingAttachmentJournalTestSupport.physicalEntry(
                FOREIGN_OWNER.value().getLeastSignificantBits(),
                SKILL_ID.value().getLeastSignificantBits(),
                0,
                1,
                Optional.empty(),
                0);
        var adapter = ready(
                storeWithRevisionZero(),
                PendingAttachmentJournalTestSupport.rootBytes(List.of(foreignEntry)));
        var port = new SkillDefinitionStoreService().submissionPort();
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.BootstrapResult.Unavailable.class,
                port.bootstrapJournalCore(adapter));

        assertEquals(
                Optional.of(StoreTestFixtures.document(SKILL_ID, 0)),
                available(adapter.find(REVISION_ZERO)));
        assertEquals(Optional.of(REVISION_ZERO), available(adapter.latestReference(SKILL_ID)));
        var pin = available(adapter.pin(REVISION_ZERO)).orElseThrow();
        pin.close();
        assertTrue(pin.isClosed());

        assertUnavailable(
                port.observeSubmissionAuthorityCore(adapter, SKILL_ID, OWNER),
                SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE);
        assertUnavailable(
                port.journalStatusCore(adapter),
                SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE);
        assertUnavailable(
                port.journalRootsCore(adapter),
                SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE);

        var plan = SubmissionPlanTestFactory.existingPlan(
                SKILL_ID, OWNER, StoreTestFixtures.revision(0));
        var target = new SkillReference(SKILL_ID, StoreTestFixtures.revision(1));
        var transition = SkillDefinitionStoreSubmissionPort.TransitionView.capture(
                this,
                OWNER,
                SKILL_ID,
                Optional.of(REVISION_ZERO),
                0,
                Optional.of(target),
                1,
                false);
        assertUnavailable(
                port.prepareSubmissionCommitCore(
                        this,
                        adapter,
                        plan,
                        SkillQuota.Unlimited.INSTANCE,
                        transition),
                SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE);
        assertUnavailable(
                port.prepareJournalPrefixClearCore(
                        this, adapter, OWNER, SKILL_ID, 1, REVISION_ZERO),
                SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE);
        assertFalse(adapter.isDirty());
    }

    @Test
    void repeatBootstrapFailsFastWithoutRepublishing() {
        var adapter = ready(new SkillDefinitionStore(), new byte[0]);
        var port = new SkillDefinitionStoreService().submissionPort();
        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready.class,
                port.bootstrapJournalCore(adapter));
        var installed = adapter.state();

        var thrown = assertThrows(
                SkillSubsystemLifecycleException.class,
                () -> port.bootstrapJournalCore(adapter));

        assertEquals(
                SkillSubsystemLifecycleException.Code.JOURNAL_BOOTSTRAP_ALREADY_INSTALLED,
                thrown.code());
        assertSame(installed, adapter.state());
        assertFalse(adapter.isDirty());
    }

    private static void assertPersistenceFailure(
            byte[] source,
            SkillDefinitionStoreSubmissionPort.BootstrapJournalLoader loader,
            PendingAttachmentJournalFailure.Code expectedCode,
            boolean initiallyDirty) {
        var adapter = ready(new SkillDefinitionStore(), source);
        if (initiallyDirty) {
            adapter.setDirty();
        }
        var base = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var originalInner = base.innerCarrier();
        var originalPending = originalInner.pending();
        var port = new SkillDefinitionStoreService().submissionPort();

        var result = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.BootstrapResult.Unavailable.class,
                port.bootstrapJournalCore(
                        adapter,
                        pending -> {
                            assertSame(originalPending, pending);
                            return loader.load(pending);
                        },
                        productionPublisher(adapter)));

        assertEquals(
                SkillDefinitionStoreSubmissionPort.UnavailableReason.JOURNAL_UNAVAILABLE,
                result.reason());
        var published = assertUnavailableInstallation(
                adapter,
                base,
                originalInner,
                originalPending,
                PendingAttachmentJournalOperationalFailure.Persistence.class,
                initiallyDirty,
                source);
        var operational = assertInstanceOf(
                PendingAttachmentJournalOperationalFailure.Persistence.class,
                assertInstanceOf(
                        PendingAttachmentJournalState.Unavailable.class,
                        assertInstanceOf(
                                PendingAttachmentJournalLifecycle.Installed.class,
                                published.journalLifecycle()).state()).failure());
        assertEquals(expectedCode, operational.failure().code());
    }

    private static void assertTargetAuditFailure(
            SkillDefinitionStore store,
            byte[] source,
            PendingAttachmentJournalFailure.Code expectedCode,
            boolean initiallyDirty) {
        var adapter = ready(store, source);
        if (initiallyDirty) {
            adapter.setDirty();
        }
        var base = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var originalInner = base.innerCarrier();
        var originalPending = originalInner.pending();
        var port = new SkillDefinitionStoreService().submissionPort();

        assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.BootstrapResult.Unavailable.class,
                port.bootstrapJournalCore(adapter));

        var published = assertUnavailableInstallation(
                adapter,
                base,
                originalInner,
                originalPending,
                PendingAttachmentJournalOperationalFailure.TargetAudit.class,
                initiallyDirty,
                source);
        var operational = assertInstanceOf(
                PendingAttachmentJournalOperationalFailure.TargetAudit.class,
                assertInstanceOf(
                        PendingAttachmentJournalState.Unavailable.class,
                        assertInstanceOf(
                                PendingAttachmentJournalLifecycle.Installed.class,
                                published.journalLifecycle()).state()).failure());
        assertEquals(expectedCode, operational.failure().code());
    }

    private static SkillSavedDataState.Ready assertUnavailableInstallation(
            GramaryeSkillSavedData adapter,
            SkillSavedDataState.Ready base,
            SkillSavedDataInnerCarrier originalInner,
            OpaquePendingAttachmentUpdatesBlob originalPending,
            Class<? extends PendingAttachmentJournalOperationalFailure> failureType,
            boolean expectedDirty,
            byte[] source) {
        var published = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        assertNotSame(base, published);
        assertSame(originalInner, published.innerCarrier());
        assertSame(originalPending, published.innerCarrier().pending());
        var installed = assertInstanceOf(
                PendingAttachmentJournalLifecycle.Installed.class,
                published.journalLifecycle());
        var unavailable = assertInstanceOf(
                PendingAttachmentJournalState.Unavailable.class, installed.state());
        assertInstanceOf(failureType, unavailable.failure());
        assertEquals(expectedDirty, adapter.isDirty());
        assertArrayEquals(source, savedPending(adapter));
        return published;
    }

    private static SkillDefinitionStoreSubmissionPort.BootstrapPublisher productionPublisher(
            GramaryeSkillSavedData adapter) {
        return (expected, replacement, markDirty) -> {
            adapter.publishState(expected, replacement);
            if (markDirty) {
                adapter.setDirty();
            }
        };
    }

    private static GramaryeSkillSavedData ready(
            SkillDefinitionStore store, byte[] pendingBytes) {
        var carrier = assertInstanceOf(
                CarrierBuildResult.Success.class,
                SkillStoreCarrierBuilder.rebuild(store)).carrier();
        var pending = OpaquePendingAttachmentUpdatesBlob.capture(pendingBytes);
        var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                pending,
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(carrier.storeByteCount(), pending.byteCount())));
        return GramaryeSkillSavedData.ready(
                SkillSavedDataReadyCandidate.afterCarrierRebuild(
                        store,
                        inner,
                        new PipelineFactReport(List.of(), false),
                        false));
    }

    private static SkillDefinitionStore storeWithRevisionZero() {
        return StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(SKILL_ID, OWNER, 0)));
    }

    private static byte[] savedPending(GramaryeSkillSavedData adapter) {
        return adapter.save(new CompoundTag(), null).getByteArray(
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD);
    }

    private static <T> T available(SkillSubsystemResult<T> result) {
        return switch (result) {
            case SkillSubsystemResult.Available<T> available -> available.value();
            case SkillSubsystemResult.Unavailable<T> unavailable ->
                    throw new AssertionError(
                            "expected available result, got " + unavailable.reason());
        };
    }

    private static void assertUnavailable(
            Object result,
            SkillDefinitionStoreSubmissionPort.UnavailableReason expectedReason) {
        var reason = switch (result) {
            case SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Unavailable unavailable ->
                    unavailable.reason();
            case SkillDefinitionStoreSubmissionPort.JournalStatus.Unavailable unavailable ->
                    unavailable.reason();
            case SkillDefinitionStoreSubmissionPort.JournalRootProjection.Unavailable unavailable ->
                    unavailable.reason();
            case SkillDefinitionStoreSubmissionPort.SubmissionPreparationResult.Unavailable unavailable ->
                    unavailable.reason();
            case SkillDefinitionStoreSubmissionPort.JournalClearPreparationResult.Unavailable unavailable ->
                    unavailable.reason();
            default -> throw new AssertionError("expected unavailable result, got " + result);
        };
        assertEquals(expectedReason, reason);
    }
}
