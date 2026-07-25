package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class SkillSavedDataCarrierPersistenceBridgeTest {
    @Test
    void canonicalEmptyStoreRunsA2AndA3OnceAndBuildsMatchingReadyCandidate() {
        var store = SkillSavedDataTestSupport.canonicalEmptyStoreBlob();
        var pending = new byte[] {8, 6, 7, 5, 3, 0, 9};
        var root = SkillSavedDataTestSupport.canonicalWholeRoot(store, pending);
        var a2Calls = new AtomicInteger();
        var a3Calls = new AtomicInteger();

        var result = SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                new ByteArrayInputStream(root),
                Optional.empty(),
                (blob, provider) -> {
                    a2Calls.incrementAndGet();
                    return SkillDefinitionStorePersistenceBridge.loadStoreBlob(blob, provider);
                },
                loaded -> {
                    a3Calls.incrementAndGet();
                    return SkillStoreCarrierBuilder.rebuild(loaded);
                });
        var candidate = assertInstanceOf(
                SkillSavedDataCarrierLoadResult.Ready.class, result).candidate();

        assertEquals(1, a2Calls.get());
        assertEquals(1, a3Calls.get());
        assertFalse(candidate.rewriteRequired());
        assertTrue(candidate.facts().facts().isEmpty());
        assertTrue(candidate.carrier().storeCarrier().matchesStoreBlob(
                ImmutableStoreBlob.copyOf(store)));
        assertArrayEquals(pending, candidate.carrier().pending().copyBytes());
        assertEquals(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES
                        + store.length + pending.length,
                candidate.carrier().encodedByteCount());
        assertEquals(
                java.util.Set.of(
                        SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                        SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                        SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD),
                candidate.carrier().createDataTag().getAllKeys());
    }

    @Test
    void outerFramingFailureInvokesNeitherA2NorA3AndReturnsNoReady() {
        var a2Calls = new AtomicInteger();
        var a3Calls = new AtomicInteger();
        var result = SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                new ByteArrayInputStream(new byte[] {1}),
                Optional.empty(),
                (blob, provider) -> {
                    a2Calls.incrementAndGet();
                    return SkillDefinitionStorePersistenceBridge.loadStoreBlob(blob, provider);
                },
                store -> {
                    a3Calls.incrementAndGet();
                    return SkillStoreCarrierBuilder.rebuild(store);
                });

        assertInstanceOf(SkillSavedDataCarrierLoadResult.Failure.class, result);
        assertEquals(0, a2Calls.get());
        assertEquals(0, a3Calls.get());
    }

    @Test
    void nestedStoreFailureKeepsA2ClassificationAndDoesNotRunA3() {
        var root = SkillSavedDataTestSupport.canonicalWholeRoot(
                new byte[] {1}, new byte[0]);
        var a3Calls = new AtomicInteger();

        var failed = assertInstanceOf(
                SkillSavedDataCarrierLoadResult.Failure.class,
                SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                        new ByteArrayInputStream(root),
                        Optional.empty(),
                        SkillDefinitionStorePersistenceBridge::loadStoreBlob,
                        store -> {
                            a3Calls.incrementAndGet();
                            return SkillStoreCarrierBuilder.rebuild(store);
                        }));
        var nested = assertInstanceOf(
                SkillSavedDataCarrierFailure.StoreLoadFailed.class,
                failed.failure());

        assertInstanceOf(
                StorePersistenceFailure.MalformedStoreEnvelope.class,
                nested.failure());
        assertEquals(0, a3Calls.get());
    }

    @Test
    void typedA3FailureReturnsNoReadyAndPreservesA2Facts() {
        var root = SkillSavedDataTestSupport.canonicalWholeRoot(
                SkillSavedDataTestSupport.canonicalEmptyStoreBlob(), new byte[0]);

        var failed = assertInstanceOf(
                SkillSavedDataCarrierLoadResult.Failure.class,
                SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                        new ByteArrayInputStream(root),
                        Optional.empty(),
                        SkillDefinitionStorePersistenceBridge::loadStoreBlob,
                        store -> new CarrierBuildResult.Failure(
                                StorePersistenceFailure.EncodeFailed.INSTANCE)));

        assertInstanceOf(
                SkillSavedDataCarrierFailure.CarrierRebuildFailed.class,
                failed.failure());
        assertTrue(failed.factReport().facts().isEmpty());
    }

    @Test
    void futureOuterSchemaStopsBeforeA2AndA3() {
        var root = SkillSavedDataTestSupport.canonicalWholeRoot(
                1, SkillSavedDataTestSupport.canonicalEmptyStoreBlob(), new byte[0]);
        var a2Calls = new AtomicInteger();
        var a3Calls = new AtomicInteger();

        var failed = assertInstanceOf(
                SkillSavedDataCarrierLoadResult.Failure.class,
                SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                        new ByteArrayInputStream(root),
                        Optional.empty(),
                        (blob, provider) -> {
                            a2Calls.incrementAndGet();
                            return SkillDefinitionStorePersistenceBridge.loadStoreBlob(
                                    blob, provider);
                        },
                        store -> {
                            a3Calls.incrementAndGet();
                            return SkillStoreCarrierBuilder.rebuild(store);
                        }));

        assertInstanceOf(
                SkillSavedDataCarrierFailure.UnsupportedSavedDataSchema.class,
                failed.failure());
        assertEquals(0, a2Calls.get());
        assertEquals(0, a3Calls.get());
    }

    @Test
    void a2RewriteSignalIsIncludedWithoutDerivingFromFacts() {
        var storeBytes = SkillSavedDataTestSupport.canonicalEmptyStoreBlob();
        var root = SkillSavedDataTestSupport.canonicalWholeRoot(storeBytes, new byte[0]);
        var domainStore = new SkillDefinitionStore();
        var visibleFact = new PipelineFactReport(List.of(), true);

        var ready = assertInstanceOf(
                SkillSavedDataCarrierLoadResult.Ready.class,
                SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                        new ByteArrayInputStream(root),
                        Optional.empty(),
                        (blob, provider) -> new StorePersistenceLoadResult.Loaded(
                                domainStore, visibleFact, true),
                        SkillStoreCarrierBuilder::rebuild)).candidate();

        assertTrue(ready.rewriteRequired());
        assertTrue(ready.facts().truncated());
    }

    @Test
    void currentNoncanonicalStoreBlobLoadsButRequiresCanonicalRewrite()
            throws Exception {
        var noncanonical = reverseOrderedEmptyStoreBlob();
        var canonical = SkillSavedDataTestSupport.canonicalEmptyStoreBlob();
        assertFalse(java.util.Arrays.equals(canonical, noncanonical));

        var ready = assertInstanceOf(
                SkillSavedDataCarrierLoadResult.Ready.class,
                SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                        new ByteArrayInputStream(
                                SkillSavedDataTestSupport.canonicalWholeRoot(
                                        noncanonical, new byte[0])),
                        Optional.empty())).candidate();

        assertTrue(ready.rewriteRequired());
        assertTrue(ready.carrier().storeCarrier().matchesStoreBlob(
                ImmutableStoreBlob.copyOf(canonical)));
        assertFalse(ready.carrier().storeCarrier().matchesStoreBlob(
                ImmutableStoreBlob.copyOf(noncanonical)));
    }

    private static byte[] reverseOrderedEmptyStoreBlob() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeByte(Tag.TAG_LIST);
            output.writeUTF("history_entries");
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeInt(0);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("store_schema_version");
            output.writeInt(0);
            output.writeByte(Tag.TAG_END);
        }
        return bytes.toByteArray();
    }
}
