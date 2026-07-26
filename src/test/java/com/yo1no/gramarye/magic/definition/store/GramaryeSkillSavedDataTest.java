package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class GramaryeSkillSavedDataTest {
    @Test
    void readyCallbackReturnsOnlyFreshPrebuiltInnerTagsWithoutChangingDirty() {
        var adapter = GramaryeSkillSavedData.ready(
                SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent());
        var first = adapter.save(new CompoundTag(), null);
        var originalStore = first.getByteArray(
                SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD).clone();

        first.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 99);
        first.getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD)[0] ^= 0x5a;
        first.putString("unexpected", "mutation");
        var second = adapter.save(new CompoundTag(), null);

        assertEquals(
                java.util.Set.of(
                        SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                        SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                        SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD),
                second.getAllKeys());
        assertEquals(
                SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION,
                second.getInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD));
        assertArrayEquals(
                originalStore,
                second.getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD));
        assertArrayEquals(
                new byte[0],
                second.getByteArray(
                        SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD));
        assertNotSame(first, second);
        assertFalse(adapter.isDirty());
    }

    @Test
    void quarantinedAndUnavailableCallbacksLeaveCallerOutputUntouched() {
        var quarantine = GramaryeSkillSavedData.quarantined(
                new SkillSavedDataPrimaryFailure.MalformedGzip(
                        SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID));
        var quarantineOutput = sentinelOutput();

        assertThrows(IllegalStateException.class, () -> quarantine.save(quarantineOutput, null));
        assertEquals(sentinelOutput(), quarantineOutput);
        assertFalse(quarantine.isDirty());

        var unavailable = readyWithRevisions(0, 1);
        unavailable.setDirty();
        var transitioned = unavailable.reclaim(
                complete(),
                (base, snapshot) -> {
                    throw new CarrierInvariantException(
                            CarrierInvariantException.Code.RECLAIM_SIZE_MISMATCH);
                });
        var unavailableOutput = sentinelOutput();

        var reason = assertInstanceOf(
                SkillSubsystemResult.Unavailable.class, transitioned).reason();
        assertEquals(
                SkillSubsystemUnavailableReason.State.UNAVAILABLE,
                reason.state());
        assertInstanceOf(SkillSavedDataState.Unavailable.class, unavailable.state());
        assertFalse(unavailable.isDirty());
        assertThrows(
                IllegalStateException.class,
                () -> unavailable.save(unavailableOutput, null));
        assertEquals(sentinelOutput(), unavailableOutput);
    }

    @Test
    void availableMissingIsDistinctFromBothUnavailableStates() {
        var missingReference = new com.yo1no.gramarye.magic.definition.document.SkillReference(
                StoreTestFixtures.skillId(90), StoreTestFixtures.revision(0));
        var ready = GramaryeSkillSavedData.ready(
                SkillSavedDataCarrierPersistenceBridge.createEmptyCurrent());
        var missing = assertInstanceOf(
                SkillSubsystemResult.Available.class,
                ready.find(missingReference));

        assertEquals(Optional.empty(), missing.value());

        var quarantined = GramaryeSkillSavedData.quarantined(
                SkillSavedDataPrimaryFailure.PrimaryFileIdentityUnavailable.INSTANCE);
        var unavailable = assertInstanceOf(
                SkillSubsystemResult.Unavailable.class,
                quarantined.find(missingReference));
        assertEquals(
                SkillSubsystemUnavailableReason.State.QUARANTINED,
                unavailable.reason().state());
        assertEquals(
                SkillSubsystemUnavailableReason.Code.PRIMARY_FILE_IDENTITY_UNAVAILABLE,
                unavailable.reason().code());
    }

    @Test
    void readyControlledReadsAndPinsReturnAvailableFoundAndMissingValues() {
        var skillId = StoreTestFixtures.skillId(41);
        var owner = StoreTestFixtures.ownerId(7);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(skillId, owner, 0, 1)));
        var adapter = ready(store, false);
        var latest = new com.yo1no.gramarye.magic.definition.document.SkillReference(
                skillId, StoreTestFixtures.revision(1));
        var missing = new com.yo1no.gramarye.magic.definition.document.SkillReference(
                StoreTestFixtures.skillId(99), StoreTestFixtures.revision(0));

        assertEquals(
                Optional.of(StoreTestFixtures.document(skillId, 1)),
                availableValue(adapter.find(latest)));
        assertEquals(Optional.empty(), availableValue(adapter.find(missing)));
        assertEquals(Optional.of(latest), availableValue(adapter.latestReference(skillId)));
        assertEquals(Optional.of(owner), availableValue(adapter.ownerOf(skillId)));
        assertEquals(1, availableValue(adapter.committedSkillCount(owner)));

        var presentPin = availableValue(adapter.pin(latest)).orElseThrow();
        assertEquals(latest, presentPin.reference());
        assertFalse(presentPin.isClosed());
        presentPin.close();
        presentPin.close();
        assertTrue(presentPin.isClosed());
        assertEquals(Optional.empty(), availableValue(adapter.pin(missing)));
        assertFalse(adapter.isDirty());
    }

    @Test
    void rejectedAndZeroReclaimPreserveStateAndPriorDirty() {
        var missing = new com.yo1no.gramarye.magic.definition.document.SkillReference(
                StoreTestFixtures.skillId(1), StoreTestFixtures.revision(9));
        var incompleteRoots = SkillRetentionRootSnapshot.fromCompleteRoots(List.of(missing));

        var cleanRejected = readyWithRevisions(0, 1);
        var cleanRejectedState = cleanRejected.state();
        var cleanRejectedResult = assertInstanceOf(
                SkillSubsystemResult.Available.class,
                cleanRejected.reclaim(incompleteRoots));
        assertInstanceOf(SkillReclaimResult.Rejected.class, cleanRejectedResult.value());
        assertSame(cleanRejectedState, cleanRejected.state());
        assertFalse(cleanRejected.isDirty());

        var rejected = readyWithRevisions(0, 1);
        rejected.setDirty();
        var rejectedState = rejected.state();

        var rejectedResult = assertInstanceOf(
                SkillSubsystemResult.Available.class,
                rejected.reclaim(incompleteRoots));
        assertInstanceOf(SkillReclaimResult.Rejected.class, rejectedResult.value());
        assertSame(rejectedState, rejected.state());
        assertTrue(rejected.isDirty());

        var cleanZero = readyWithRevisions(7);
        var cleanZeroState = cleanZero.state();
        var cleanZeroResult = assertInstanceOf(
                SkillSubsystemResult.Available.class,
                cleanZero.reclaim(complete()));
        assertEquals(
                0,
                assertInstanceOf(SkillReclaimResult.Completed.class, cleanZeroResult.value())
                        .report()
                        .revisionsReclaimed());
        assertSame(cleanZeroState, cleanZero.state());
        assertFalse(cleanZero.isDirty());

        var zero = readyWithRevisions(7);
        zero.setDirty();
        var zeroState = zero.state();
        var zeroResult = assertInstanceOf(
                SkillSubsystemResult.Available.class,
                zero.reclaim(complete()));
        assertEquals(
                0,
                assertInstanceOf(SkillReclaimResult.Completed.class, zeroResult.value())
                        .report()
                        .revisionsReclaimed());
        assertSame(zeroState, zero.state());
        assertTrue(zero.isDirty());
    }

    @Test
    void positiveReclaimPublishesMatchingCarrierThenMarksDirty() {
        var adapter = readyWithRevisions(0, 1, 2);
        var oldState = adapter.state();

        var result = assertInstanceOf(
                SkillSubsystemResult.Available.class,
                adapter.reclaim(complete()));
        var completed = assertInstanceOf(SkillReclaimResult.Completed.class, result.value());
        var ready = assertInstanceOf(SkillSavedDataState.Ready.class, adapter.state());
        var rebuilt = assertInstanceOf(
                CarrierBuildResult.Success.class,
                SkillStoreCarrierBuilder.rebuild(ready.store())).carrier();
        var expectedBytes = new byte[rebuilt.storeByteCount()];
        rebuilt.copyStoreBlobInto(expectedBytes, 0);

        assertEquals(2, completed.report().revisionsReclaimed());
        assertNotSame(oldState, adapter.state());
        assertTrue(adapter.isDirty());
        assertArrayEquals(
                expectedBytes,
                adapter.save(new CompoundTag(), null)
                        .getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD));
        assertTrue(adapter.isDirty(), "callback must not clear dirty");
    }

    @Test
    void existingPinCanCloseAfterAnotherHistoryTransitionsAdapterUnavailable() {
        var firstId = StoreTestFixtures.skillId(1);
        var secondId = StoreTestFixtures.skillId(2);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(firstId, StoreTestFixtures.ownerId(1), 0),
                StoreTestFixtures.history(secondId, StoreTestFixtures.ownerId(1), 0, 1)));
        var adapter = ready(store, false);
        var pinResult = adapter.pin(
                new com.yo1no.gramarye.magic.definition.document.SkillReference(
                        firstId, StoreTestFixtures.revision(0)));
        var pin = switch (pinResult) {
            case SkillSubsystemResult.Available<Optional<SkillRevisionPin>> available ->
                    available.value().orElseThrow();
            case SkillSubsystemResult.Unavailable<Optional<SkillRevisionPin>> unavailable ->
                    throw new AssertionError("Ready pin unexpectedly unavailable: "
                            + unavailable.reason().code());
        };

        assertInstanceOf(
                SkillSubsystemResult.Unavailable.class,
                adapter.reclaim(
                        complete(),
                        (base, snapshot) -> {
                            throw new CarrierInvariantException(
                                    CarrierInvariantException.Code.RECLAIM_SIZE_MISMATCH);
                        }));
        pin.close();

        assertTrue(pin.isClosed());
        assertInstanceOf(SkillSavedDataState.Unavailable.class, adapter.state());
    }

    private static GramaryeSkillSavedData readyWithRevisions(int... revisions) {
        var skillId = StoreTestFixtures.skillId(1);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(
                        skillId, StoreTestFixtures.ownerId(1), revisions)));
        return ready(store, false);
    }

    private static GramaryeSkillSavedData ready(
            SkillDefinitionStore store,
            boolean rewriteRequired) {
        var carrier = assertInstanceOf(
                CarrierBuildResult.Success.class,
                SkillStoreCarrierBuilder.rebuild(store)).carrier();
        var pending = OpaquePendingAttachmentUpdatesBlob.empty();
        var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                pending,
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES
                        + carrier.storeByteCount());
        return GramaryeSkillSavedData.ready(
                SkillSavedDataReadyCandidate.afterCarrierRebuild(
                        store,
                        inner,
                        new com.yo1no.gramarye.magic.definition.migration.PipelineFactReport(
                                List.of(), false),
                        rewriteRequired));
    }

    private static SkillRetentionRootSnapshot complete() {
        return SkillRetentionRootSnapshot.fromCompleteRoots(List.of());
    }

    private static <T> T availableValue(SkillSubsystemResult<T> result) {
        return switch (result) {
            case SkillSubsystemResult.Available<T> available -> available.value();
            case SkillSubsystemResult.Unavailable<T> unavailable ->
                    throw new AssertionError("Ready operation unexpectedly unavailable: "
                            + unavailable.reason().code());
        };
    }

    private static CompoundTag sentinelOutput() {
        var output = new CompoundTag();
        output.putString("sentinel", "untouched");
        return output;
    }
}
