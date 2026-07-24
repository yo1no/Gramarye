package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SkillStoreCarrierTest {
    @Test
    void emptySingleMultipleAndSparseStoresProduceCanonicalMetadata() {
        var empty = carrier(new SkillDefinitionStore());
        var single = carrier(store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(3), StoreTestFixtures.ownerId(3), 7)));
        var multiple = carrier(store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(30), StoreTestFixtures.ownerId(2), 8, 1),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(10), StoreTestFixtures.ownerId(1), 9),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(20), StoreTestFixtures.ownerId(1), 0, 4)));

        assertAll(
                () -> assertEquals(0, empty.historyCount()),
                () -> assertEquals(0, empty.revisionCount()),
                () -> assertEquals(1, single.historyCount()),
                () -> assertEquals(1, single.revisionCount()),
                () -> assertEquals(List.of(
                                StoreTestFixtures.skillId(10),
                                StoreTestFixtures.skillId(20),
                                StoreTestFixtures.skillId(30)),
                        multiple.histories().stream().map(EncodedHistoryIndex::skillId).toList()),
                () -> assertEquals(List.of(0, 4), multiple.histories().get(1).revisions().stream()
                        .map(index -> index.reference().revision().value()).toList()),
                () -> assertEquals(List.of(1, 8), multiple.histories().get(2).revisions().stream()
                        .map(index -> index.reference().revision().value()).toList()),
                () -> assertEquals(5, multiple.revisionCount()),
                () -> assertEquals(
                        multiple.histories().stream().mapToLong(EncodedHistoryIndex::byteLength).sum(),
                        multiple.totalHistoryBlobBytes()),
                () -> assertEquals(
                        multiple.histories().stream()
                                .flatMap(history -> history.revisions().stream())
                                .mapToLong(EncodedRevisionIndex::byteLength)
                                .sum(),
                        multiple.totalRevisionBlobBytes()));
    }

    @Test
    void canonicalRebuildIsDeterministicAcrossSnapshotInputOrder() {
        var first = store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(3), StoreTestFixtures.ownerId(2), 9, 1),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 5),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(1), 4, 0));
        var second = store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(1), 0, 4),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 5),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(3), StoreTestFixtures.ownerId(2), 1, 9));

        var firstCarrier = carrier(first);
        var secondCarrier = carrier(second);

        assertArrayEquals(bytes(firstCarrier), bytes(secondCarrier));
        assertEquals(
                firstCarrier.histories().stream().map(EncodedHistoryIndex::skillId).toList(),
                secondCarrier.histories().stream().map(EncodedHistoryIndex::skillId).toList());
    }

    @Test
    void indexRangesSelectCompleteDecodableHistoryAndRevisionBlobs() {
        var carrier = carrier(store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(7), 0, 3),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(8), 2)));

        long historyBytes = 0;
        long revisionBytes = 0;
        for (var history : carrier.histories()) {
            assertTrue(history.payloadOffset() >= 0);
            assertTrue((long) history.payloadOffset() + history.byteLength()
                    <= carrier.storeByteCount());
            var historyCopy = copy(carrier.historySlice(history));
            var decodedHistory = StoreNbtFraming.decodeHistory(
                    ImmutableHistoryBlob.takeOwnership(historyCopy)).successValue().orElseThrow();
            assertEquals(history.skillId(), decodedHistory.skillId());
            assertEquals(history.owner(), decodedHistory.owner());
            historyBytes += history.byteLength();

            for (var revision : history.revisions()) {
                assertTrue(revision.payloadOffset() >= history.payloadOffset());
                assertTrue((long) revision.payloadOffset() + revision.byteLength()
                        <= (long) history.payloadOffset() + history.byteLength());
                var revisionCopy = copy(carrier.revisionSlice(revision));
                var decodedRevision = StoreNbtFraming.decodeRevision(
                                ImmutableRevisionBlob.takeOwnership(revisionCopy))
                        .successValue().orElseThrow();
                assertEquals(revision.reference().revision(), decodedRevision.revision());
                revisionBytes += revision.byteLength();
            }
        }

        assertEquals(historyBytes, carrier.totalHistoryBlobBytes());
        assertEquals(revisionBytes, carrier.totalRevisionBlobBytes());
    }

    @Test
    void saveCopySeamIsOffsetBoundedAndDoesNotExposeCarrierBytes() {
        var store = store(StoreTestFixtures.history(
                StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 0, 5));
        var carrier = carrier(store);
        var canonical = assertSuccess(
                SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(store)).blob().copyBytes();
        var destination = new byte[carrier.storeByteCount() + 4];
        Arrays.fill(destination, (byte) 0x6a);

        carrier.copyStoreBlobInto(destination, 2);

        assertAll(
                () -> assertEquals((byte) 0x6a, destination[0]),
                () -> assertEquals((byte) 0x6a, destination[1]),
                () -> assertEquals((byte) 0x6a, destination[destination.length - 1]),
                () -> assertEquals((byte) 0x6a, destination[destination.length - 2]),
                () -> assertArrayEquals(canonical,
                        Arrays.copyOfRange(destination, 2, destination.length - 2)),
                () -> assertThrows(IndexOutOfBoundsException.class,
                        () -> carrier.copyStoreBlobInto(new byte[carrier.storeByteCount()], 1)),
                () -> assertTrue(Arrays.stream(EncodedSkillStoreCarrier.class.getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType() == byte[].class)));

        destination[2] ^= 0x7f;
        var second = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(second, 0);
        assertArrayEquals(canonical, second);
    }

    @Test
    void carrierAndIndexesHaveTheReviewedExactFieldShape() {
        var carrierFields = fieldNames(EncodedSkillStoreCarrier.class);
        var historyFields = fieldNames(EncodedHistoryIndex.class);
        var revisionFields = fieldNames(EncodedRevisionIndex.class);

        assertAll(
                () -> assertEquals(Set.of(
                                "storeBlob", "histories", "historyCount", "revisionCount",
                                "totalHistoryBlobBytes", "totalRevisionBlobBytes"),
                        carrierFields),
                () -> assertEquals(Set.of(
                                "skillId", "owner", "payloadOffset", "byteLength", "revisions"),
                        historyFields),
                () -> assertEquals(Set.of("reference", "payloadOffset", "byteLength"),
                        revisionFields),
                () -> assertEquals(1, Arrays.stream(
                                EncodedSkillStoreCarrier.class.getDeclaredFields())
                        .filter(field -> field.getType() == ImmutableStoreBlob.class).count()),
                () -> assertTrue(Arrays.stream(EncodedSkillStoreCarrier.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == byte[].class)),
                () -> assertTrue(Arrays.stream(EncodedHistoryIndex.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == byte[].class)),
                () -> assertTrue(Arrays.stream(EncodedRevisionIndex.class.getDeclaredFields())
                        .noneMatch(field -> field.getType() == byte[].class)),
                () -> assertFalse(declaresMethod(EncodedSkillStoreCarrier.class, "equals")),
                () -> assertFalse(declaresMethod(EncodedSkillStoreCarrier.class, "hashCode")));
    }

    @Test
    void metadataCollectionsAreImmutableAndToStringIsBounded() {
        var carrier = carrier(store(StoreTestFixtures.history(
                StoreTestFixtures.skillId(123456), StoreTestFixtures.ownerId(654321), 0, 4)));
        var history = carrier.histories().getFirst();
        var text = carrier.toString();

        assertAll(
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> carrier.histories().clear()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> history.revisions().clear()),
                () -> assertTrue(text.length() < 160),
                () -> assertTrue(text.contains("storeByteCount=")),
                () -> assertTrue(text.contains("historyCount=1")),
                () -> assertTrue(text.contains("revisionCount=2")),
                () -> assertFalse(text.contains(history.skillId().value().toString())),
                () -> assertFalse(text.contains(history.owner().value().toString())));
    }

    @Test
    void malformedOrderContainmentAndForeignIndexesAreRejected() {
        var store = store(
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(1), 0),
                StoreTestFixtures.history(
                        StoreTestFixtures.skillId(2), StoreTestFixtures.ownerId(2), 0));
        var layout = assertLayout(store);
        var first = layout.histories().get(0);
        var second = layout.histories().get(1);
        var firstRevision = first.revisions().getFirst();
        var overlappingHistory = new EncodedHistoryIndex(
                second.skillId(),
                second.owner(),
                first.payloadOffset(),
                first.byteLength(),
                List.of(new EncodedRevisionIndex(
                        new SkillReference(
                                second.skillId(), firstRevision.reference().revision()),
                        firstRevision.payloadOffset(),
                        firstRevision.byteLength())));

        assertAll(
                () -> assertCarrierConstructionRejected(
                        layout.blob(), List.of(second, first)),
                () -> assertCarrierConstructionRejected(
                        layout.blob(), List.of(first, overlappingHistory)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EncodedHistoryIndex(
                                first.skillId(), first.owner(), first.payloadOffset(),
                                first.byteLength(), List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EncodedHistoryIndex(
                                first.skillId(),
                                first.owner(),
                                first.payloadOffset(),
                                first.byteLength(),
                                List.of(
                                        firstRevision,
                                        new EncodedRevisionIndex(
                                                new SkillReference(
                                                        first.skillId(),
                                                        StoreTestFixtures.revision(1)),
                                                firstRevision.payloadOffset(),
                                                firstRevision.byteLength())))),
                () -> assertThrows(ArithmeticException.class,
                        () -> new EncodedRevisionIndex(
                                firstRevision.reference(), Integer.MAX_VALUE, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new EncodedHistoryIndex(
                                first.skillId(), first.owner(), first.payloadOffset(),
                                first.byteLength(), List.of(new EncodedRevisionIndex(
                                        new SkillReference(
                                                StoreTestFixtures.skillId(99),
                                                firstRevision.reference().revision()),
                                        firstRevision.payloadOffset(),
                                        firstRevision.byteLength())))),
                () -> assertCarrierConstructionRejected(
                        layout.blob(), List.of(new EncodedHistoryIndex(
                                        first.skillId(), first.owner(), layout.blob().byteCount(),
                                        first.byteLength(), List.of(new EncodedRevisionIndex(
                                                firstRevision.reference(),
                                                layout.blob().byteCount(),
                                                firstRevision.byteLength()))))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> carrier(store).historySlice(new EncodedHistoryIndex(
                                first.skillId(), first.owner(), first.payloadOffset(),
                                first.byteLength(), first.revisions()))));
    }

    @Test
    void routeBoundWriterRejectsSameShapeRouteSubstitutionAndNoncanonicalHistoryOrder() {
        var firstSkillId = StoreTestFixtures.skillId(1);
        var secondSkillId = StoreTestFixtures.skillId(2);
        var firstDocument = StoreTestFixtures.document(firstSkillId, 0);
        var secondDocument = StoreTestFixtures.document(secondSkillId, 0);
        var firstRevision = SkillDefinitionStorePersistenceBridge
                .encodeCurrentRevision(firstDocument).successValue().orElseThrow();
        var secondRevision = SkillDefinitionStorePersistenceBridge
                .encodeCurrentRevision(secondDocument).successValue().orElseThrow();
        var firstHistory = StoreNbtFraming.encodeHistoryWithLayout(
                        firstSkillId, StoreTestFixtures.ownerId(1), List.of(firstRevision))
                .successValue().orElseThrow();
        var secondHistory = StoreNbtFraming.encodeHistoryWithLayout(
                        secondSkillId, StoreTestFixtures.ownerId(2), List.of(secondRevision))
                .successValue().orElseThrow();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> StoreNbtFraming.encodeRevisionWithRoute(
                                firstRevision.reference(),
                                new RevisionPersistentEnvelopeV0(
                                        StoreTestFixtures.revision(1),
                                        StorePersistenceSchema.DOCUMENT_ENCODING,
                                        EncodedSkillDocument.copyOf(new byte[] {0})))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> StoreNbtFraming.encodeHistoryWithLayout(
                                secondSkillId,
                                StoreTestFixtures.ownerId(2),
                                List.of(firstRevision))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> StoreNbtFraming.encodeStoreWithLayout(
                                StorePersistenceSchema.CURRENT_SCHEMA_VERSION,
                                List.of(secondHistory, firstHistory))),
                () -> assertEquals(firstSkillId,
                        firstHistory.references().getFirst().skillId()),
                () -> assertEquals(StoreTestFixtures.revision(0),
                        firstHistory.references().getFirst().revision()));
    }

    private static void assertCarrierConstructionRejected(
            ImmutableStoreBlob blob,
            List<EncodedHistoryIndex> histories) {
        try {
            var constructor = EncodedSkillStoreCarrier.class.getDeclaredConstructor(
                    ImmutableStoreBlob.class, List.class);
            constructor.setAccessible(true);
            var failure = assertThrows(
                    InvocationTargetException.class,
                    () -> constructor.newInstance(blob, histories));
            assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private static StoreEncodingLayout assertLayout(SkillDefinitionStore store) {
        return ((StoreLayoutEncodeResult.Success)
                SkillDefinitionStorePersistenceBridge.encodeCurrentStoreLayout(store.snapshot()))
                .layout();
    }

    private static CarrierBuildResult.Success assertCarrierSuccess(CarrierBuildResult result) {
        return (CarrierBuildResult.Success) result;
    }

    private static StorePersistenceEncodeResult.Success assertSuccess(
            StorePersistenceEncodeResult result) {
        return (StorePersistenceEncodeResult.Success) result;
    }

    private static EncodedSkillStoreCarrier carrier(SkillDefinitionStore store) {
        return assertCarrierSuccess(SkillStoreCarrierBuilder.rebuild(store)).carrier();
    }

    private static SkillDefinitionStore store(SkillHistorySnapshot... histories) {
        return StoreTestFixtures.restore(StoreTestFixtures.snapshot(histories));
    }

    private static byte[] bytes(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
        return bytes;
    }

    private static byte[] copy(HistoryBlobSource source) {
        var bytes = new byte[source.byteCount()];
        source.copyInto(bytes, 0);
        return bytes;
    }

    private static byte[] copy(RevisionBlobSource source) {
        var bytes = new byte[source.byteCount()];
        source.copyInto(bytes, 0);
        return bytes;
    }

    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .map(field -> field.getName())
                .collect(Collectors.toSet());
    }

    private static boolean declaresMethod(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !Modifier.isStatic(method.getModifiers()))
                .anyMatch(method -> method.getName().equals(name));
    }
}
