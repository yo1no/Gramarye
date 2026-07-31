package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P4D3FixtureTest {
    @Test
    void exactStoreAndMaximumJournalUseProductionFramingAndDomainAdmission() {
        var proof = buildExactFixtureProof();
        var loaded = SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                proof.source(),
                java.util.Optional.of(net.minecraft.core.RegistryAccess.EMPTY));
        assertTrue(loaded instanceof StorePersistenceLoadResult.Loaded);
        var rebuilt = SkillStoreCarrierBuilder.rebuild(
                ((StorePersistenceLoadResult.Loaded) loaded).store());
        assertTrue(rebuilt instanceof CarrierBuildResult.Success);
        var rebuiltCarrier = ((CarrierBuildResult.Success) rebuilt).carrier();
        assertTrue(rebuiltCarrier.matchesStoreBlob(proof.source()));
        assertEquals(proof.checksum(), P4D3Hashing.sha256(rebuiltCarrier));
    }

    private static ExactFixtureProof buildExactFixtureProof() {
        var fixture = P4D3StoreJournalFixture.build();
        var carrier = fixture.carrier();

        assertEquals(P4D3StoreJournalFixture.STORE_BYTES, carrier.storeByteCount());
        assertEquals(P4D3StoreJournalFixture.HISTORY_COUNT, carrier.historyCount());
        assertEquals(P4D3StoreJournalFixture.REVISION_COUNT, carrier.revisionCount());
        assertEquals(2, fixture.store().committedSkillCount(
                P4D3StoreJournalFixture.selectedOwner()));
        assertEquals(0, fixture.store().committedSkillCount(
                P4D3StoreJournalFixture.submissionOwner()));
        for (var ownerIndex = 1; ownerIndex <= 7; ownerIndex++) {
            assertEquals(256, countOwnerIndex(fixture, ownerIndex));
        }
        assertEquals(254, countOwnerIndex(fixture, 8));

        assertEquals(P4D3StoreJournalFixture.CURRENT_JOURNAL_ENTRIES,
                fixture.currentJournal().entryCount());
        assertEquals(P4D3StoreJournalFixture.CURRENT_JOURNAL_BYTES,
                fixture.encodedCurrent().byteCount());
        assertEquals(P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES,
                fixture.prospectiveJournal().entryCount());
        assertEquals(P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES,
                fixture.encodedProspective().byteCount());
        assertTrue(fixture.encodedProspective().byteCount()
                <= MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES);
        assertTrue(fixture.store().auditJournalTargets(fixture.currentJournal())
                instanceof JournalTargetAuditResult.Audited);

        var keys = new HashSet<String>();
        fixture.prospectiveJournal().entries().forEach(entry -> assertTrue(keys.add(
                entry.owner().value() + ":" + entry.skillId().value() + ":"
                        + entry.targetAttachmentGeneration())));
        var selected = fixture.currentJournal().entries().stream()
                .filter(entry -> entry.owner().equals(
                        P4D3StoreJournalFixture.selectedOwner()))
                .toList();
        assertEquals(4, selected.size());
        assertEquals(P4D3StoreJournalFixture.skillId(0), selected.get(0).skillId());
        assertEquals(1, selected.get(0).targetAttachmentGeneration());
        assertEquals(P4D3StoreJournalFixture.skillId(0), selected.get(1).skillId());
        assertEquals(2, selected.get(1).targetAttachmentGeneration());
        assertEquals(P4D3StoreJournalFixture.skillId(1), selected.get(2).skillId());
        assertEquals(P4D3StoreJournalFixture.skillId(1), selected.get(3).skillId());

        var source = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(source, 0);
        return new ExactFixtureProof(
                ImmutableStoreBlob.takeOwnership(source), P4D3Hashing.sha256(carrier));

    }

    @Test
    void manifestIsExactBoundedAndRejectsExtraFields(@TempDir java.nio.file.Path directory)
            throws Exception {
        var checksum = "0".repeat(64);
        var manifest = new P4D3FixtureManifest(
                P4D3ProbeCase.D, "prepared", "EXPECTED", checksum,
                P4D3FixtureManifest.NONE, checksum, 1, 1, 1, 1, checksum,
                1, 1, 1, checksum, checksum, checksum, 1,
                P4D3FixtureManifest.NONE, P4D3FixtureManifest.NONE, 0,
                "PREPARED", 0, 0, 0, 0, 0);
        manifest.write(directory);
        assertTrue(Files.size(directory.resolve(P4D3FixtureManifest.FILE_NAME))
                <= P4D3FixtureManifest.MAX_BYTES);
        assertEquals(manifest, P4D3FixtureManifest.read(directory));

        Files.writeString(directory.resolve(P4D3FixtureManifest.FILE_NAME),
                Files.readString(directory.resolve(P4D3FixtureManifest.FILE_NAME))
                        + "unexpected=value\n");
        assertThrows(IllegalArgumentException.class,
                () -> P4D3FixtureManifest.read(directory));

        Files.write(
                directory.resolve(P4D3FixtureManifest.FILE_NAME),
                new byte[Math.toIntExact(P4D3FixtureManifest.MAX_BYTES + 1)]);
        assertThrows(IllegalArgumentException.class,
                () -> P4D3FixtureManifest.read(directory));
    }

    @Test
    void j2DefensiveReauditReturnsTargetInvalidWithoutMutatingLiveState() {
        var owner = P4D3StoreJournalFixture.selectedOwner();
        var skillId = P4D3StoreJournalFixture.skillId(0);
        var target = P4D3StoreJournalFixture.target(0, 0);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0)));
        var journal = P4D3StoreJournalFixture.singleJournal();
        assertInstanceOf(JournalTargetAuditResult.Audited.class,
                store.auditJournalTargets(journal));
        var adapter = installed(store, journal);
        var beforeState = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var beforeLifecycle = assertInstanceOf(
                PendingAttachmentJournalLifecycle.Installed.class,
                beforeState.journalLifecycle());
        var beforeJournal = assertInstanceOf(
                PendingAttachmentJournalState.Ready.class,
                beforeLifecycle.state()).journal();
        var auditCalls = new AtomicInteger();
        var port = new SkillDefinitionStoreService().submissionPort();

        var invalid = assertInstanceOf(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.TargetInvalid.class,
                port.observePendingRecoveryCore(
                        adapter,
                        owner,
                        (liveStore, ownerJournal) -> {
                            assertSame(store, liveStore);
                            assertEquals(1, ownerJournal.entryCount());
                            assertEquals(skillId, ownerJournal.entries().get(0).skillId());
                            assertEquals(target, ownerJournal.entries().get(0).targetPointer());
                            auditCalls.incrementAndGet();
                            return new JournalTargetAuditResult.Rejected(
                                    PendingAttachmentJournalFailure.entry(
                                            PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                                            0,
                                            skillId,
                                            target));
                        }));

        assertEquals(1, auditCalls.get());
        assertEquals(skillId, invalid.skillId());
        assertEquals(target, invalid.target());
        assertEquals(
                SkillDefinitionStoreSubmissionPort.PendingRecoveryTargetFailure.MISSING,
                invalid.reason());
        assertSame(beforeState, adapter.state());
        var afterLifecycle = assertInstanceOf(
                PendingAttachmentJournalLifecycle.Installed.class,
                ((SkillSavedDataState.Ready) adapter.state()).journalLifecycle());
        assertSame(beforeJournal, assertInstanceOf(
                PendingAttachmentJournalState.Ready.class,
                afterLifecycle.state()).journal());
        assertFalse(adapter.isDirty());
    }

    @Test
    void runModesAreAnExactSixteenValuePairedMatrix() {
        assertEquals(16, P4D3RunMode.values().length);
        for (var probeCase : P4D3ProbeCase.values()) {
            var matching = java.util.Arrays.stream(P4D3RunMode.values())
                    .filter(mode -> mode.probeCase() == probeCase)
                    .toList();
            assertEquals(2, matching.size());
            assertTrue(matching.stream().anyMatch(P4D3RunMode::restart));
            assertTrue(matching.stream().anyMatch(mode -> !mode.restart()));
            assertFalse(matching.get(0).token().equals(matching.get(1).token()));
        }
    }

    private static int countOwnerIndex(
            P4D3StoreJournalFixture.Fixture fixture, int ownerIndex) {
        var owner = new com.yo1no.gramarye.magic.api.id.SkillOwnerId(
                new java.util.UUID(0x5034_4433_0000_0001L, ownerIndex));
        return fixture.store().committedSkillCount(owner);
    }

    private static GramaryeSkillSavedData installed(
            SkillDefinitionStore store, PendingAttachmentJournal journal) {
        var carrier = assertInstanceOf(
                CarrierBuildResult.Success.class,
                SkillStoreCarrierBuilder.rebuild(store)).carrier();
        var encoded = P4D3StoreJournalFixture.requireEncoded(journal);
        var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                encoded.pending(),
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(carrier.storeByteCount(), encoded.byteCount())));
        var adapter = GramaryeSkillSavedData.ready(
                SkillSavedDataReadyCandidate.afterCarrierRebuild(
                        store,
                        inner,
                        new PipelineFactReport(List.of(), false),
                        false));
        var current = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var journalReady = new PendingAttachmentJournalState.Ready(
                journal,
                encoded,
                inner.pending(),
                false,
                new JournalTargetAuditProof.AuditedExisting(journal));
        adapter.publishState(
                current,
                current.withJournalLifecycle(
                        new PendingAttachmentJournalLifecycle.Installed(journalReady)));
        return adapter;
    }

    private record ExactFixtureProof(ImmutableStoreBlob source, String checksum) {
    }

}
