package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class SkillSavedDataCarrierValuesTest {
    @Test
    void carrierMatcherComparesEveryStoreByteWithoutAByteArrayAccessor() {
        var carrier = emptyCarrier();
        var canonical = storeBytes(carrier);
        var equalSource = ImmutableStoreBlob.copyOf(canonical);
        var changed = canonical.clone();
        changed[changed.length - 1] ^= 0x5a;
        var shorter = Arrays.copyOf(canonical, canonical.length - 1);

        assertAll(
                () -> assertTrue(carrier.matchesStoreBlob(equalSource)),
                () -> assertFalse(carrier.matchesStoreBlob(ImmutableStoreBlob.copyOf(changed))),
                () -> assertFalse(carrier.matchesStoreBlob(ImmutableStoreBlob.copyOf(shorter))),
                () -> assertThrows(NullPointerException.class,
                        () -> carrier.matchesStoreBlob(null)),
                () -> assertTrue(Arrays.stream(EncodedSkillStoreCarrier.class.getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType() == byte[].class)));
    }

    @Test
    void opaquePendingBlobSnapshotsIngressAndEveryEgress() {
        var source = new byte[] {3, 5, 8, 13};
        var expected = source.clone();
        var blob = OpaquePendingAttachmentUpdatesBlob.capture(source);
        source[0] = 99;

        var first = blob.copyBytes();
        first[1] = 88;
        var destination = new byte[blob.byteCount() + 2];
        blob.copyInto(destination, 1);
        var copiedRange = Arrays.copyOfRange(destination, 1, destination.length - 1);
        destination[1] = 77;

        assertAll(
                () -> assertArrayEquals(expected, blob.copyBytes()),
                () -> assertArrayEquals(expected, copiedRange),
                () -> assertNotSame(first, blob.copyBytes()),
                () -> assertThrows(IndexOutOfBoundsException.class,
                        () -> blob.copyInto(new byte[blob.byteCount()], 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> OpaquePendingAttachmentUpdatesBlob.capture(null)));
    }

    @Test
    void pendingBlobAcceptsExactCeilingAndRejectsPlusOne() {
        var exact = OpaquePendingAttachmentUpdatesBlob.capture(
                new byte[MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES]);
        var empty = OpaquePendingAttachmentUpdatesBlob.empty();

        assertAll(
                () -> assertEquals(
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES,
                        exact.byteCount()),
                () -> assertFalse(exact.isEmpty()),
                () -> assertTrue(empty.isEmpty()),
                () -> assertEquals(0, empty.byteCount()),
                () -> assertTrue(OpaquePendingAttachmentUpdatesBlob.capture(new byte[0]).isEmpty()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> OpaquePendingAttachmentUpdatesBlob.capture(new byte[
                                MagicSafetyCeilings
                                                .MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES
                                        + 1])));
    }

    @Test
    void pendingBlobToStringIsBoundedAndDoesNotExposeContents() {
        var secret = "unique-pending-secret";
        var blob = OpaquePendingAttachmentUpdatesBlob.capture(
                secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var rendered = blob.toString();

        assertAll(
                () -> assertTrue(rendered.contains("byteCount=" + blob.byteCount())),
                () -> assertTrue(rendered.contains("empty=false")),
                () -> assertFalse(rendered.contains(secret)),
                () -> assertFalse(rendered.contains(Arrays.toString(blob.copyBytes()))),
                () -> assertTrue(rendered.length() < 128));
    }

    @Test
    void innerCarrierCreatesOnlyFreshCurrentSchemaFields() throws Exception {
        var storeCarrier = emptyCarrier();
        var pendingSource = new byte[] {21, 34, 55};
        var pending = OpaquePendingAttachmentUpdatesBlob.capture(pendingSource);
        var encodedByteCount = encodedInnerSize(storeCarrier, pending);
        var carrier = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                storeCarrier, pending, encodedByteCount);

        var first = carrier.createDataTag();
        var expectedStore = storeBytes(storeCarrier);
        assertAll(
                () -> assertEquals(Set.of(
                                SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                                SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD),
                        first.getAllKeys()),
                () -> assertEquals(
                        SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION,
                        first.getInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD)),
                () -> assertArrayEquals(
                        expectedStore,
                        first.getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD)),
                () -> assertArrayEquals(
                        pendingSource,
                        first.getByteArray(
                                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD)),
                () -> assertEquals(encodedByteCount, carrier.encodedByteCount()));

        first.getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD)[0] ^= 0x7f;
        first.getByteArray(
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD)[0] ^= 0x7f;
        first.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 99);
        pendingSource[0] = 99;

        var second = carrier.createDataTag();
        assertAll(
                () -> assertNotSame(first, second),
                () -> assertArrayEquals(
                        expectedStore,
                        second.getByteArray(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD)),
                () -> assertArrayEquals(
                        new byte[] {21, 34, 55},
                        second.getByteArray(
                                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD)),
                () -> assertEquals(
                        SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION,
                        second.getInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD)));
    }

    @Test
    void innerCarrierRequiresPrevalidatedPositiveBoundedByteCount() {
        var storeCarrier = emptyCarrier();
        var pending = OpaquePendingAttachmentUpdatesBlob.empty();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                                storeCarrier, pending, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                                storeCarrier,
                                pending,
                                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES
                                        + storeCarrier.storeByteCount() + 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                                null, pending, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                                storeCarrier, null, 1)));
    }

    @Test
    void readyCandidateKeepsTheExactTransientShapeWithoutRawAccessors() throws Exception {
        var store = new SkillDefinitionStore();
        var storeCarrier = carrier(store);
        var pending = OpaquePendingAttachmentUpdatesBlob.empty();
        var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                storeCarrier, pending, encodedInnerSize(storeCarrier, pending));
        var facts = new PipelineFactReport(List.of(), false);
        var candidate = SkillSavedDataReadyCandidate.afterCarrierRebuild(
                store, inner, facts, true);
        var fields = Arrays.stream(SkillSavedDataReadyCandidate.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toList();

        assertAll(
                () -> assertEquals(Set.of("store", "carrier", "facts", "rewriteRequired"),
                        fields.stream().map(java.lang.reflect.Field::getName)
                                .collect(java.util.stream.Collectors.toSet())),
                () -> assertTrue(fields.stream().allMatch(field ->
                        Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(SkillSavedDataReadyCandidate.class
                                .getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()))),
                () -> assertEquals(store, candidate.store()),
                () -> assertEquals(inner, candidate.carrier()),
                () -> assertEquals(facts, candidate.facts()),
                () -> assertTrue(candidate.rewriteRequired()),
                () -> assertTrue(Arrays.stream(SkillSavedDataReadyCandidate.class
                                .getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType() == byte[].class)),
                () -> assertTrue(Arrays.stream(SkillSavedDataInnerCarrier.class
                                .getDeclaredMethods())
                        .noneMatch(method -> method.getReturnType() == byte[].class)),
                () -> assertFalse(candidate.toString().contains("store=")),
                () -> assertFalse(candidate.toString().contains("facts=")),
                () -> assertTrue(candidate.toString().length() < 192));
    }

    private static int encodedInnerSize(
            EncodedSkillStoreCarrier storeCarrier,
            OpaquePendingAttachmentUpdatesBlob pending) throws Exception {
        var tag = new CompoundTag();
        tag.putInt(
                SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD,
                SkillSavedDataPersistenceSchema.CURRENT_SCHEMA_VERSION);
        tag.putByteArray(
                SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                storeBytes(storeCarrier));
        tag.putByteArray(
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD,
                pending.copyBytes());
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            NbtIo.writeUnnamedTag(tag, output);
        }
        return bytes.size();
    }

    private static EncodedSkillStoreCarrier emptyCarrier() {
        return carrier(new SkillDefinitionStore());
    }

    private static EncodedSkillStoreCarrier carrier(SkillDefinitionStore store) {
        var result = SkillStoreCarrierBuilder.rebuild(store);
        if (result instanceof CarrierBuildResult.Success success) {
            return success.carrier();
        }
        throw new AssertionError("carrier build failed");
    }

    private static byte[] storeBytes(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
        return bytes;
    }
}
