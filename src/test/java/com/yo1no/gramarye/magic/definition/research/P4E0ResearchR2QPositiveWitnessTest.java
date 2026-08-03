package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Physical, streaming proof for the approved per-file peaks and their shared aggregate budget. */
final class P4E0ResearchR2QPositiveWitnessTest {
    @TempDir
    Path temporary;

    @Test
    void allPerFilePeaksArePhysicallyMaterializedAndPrefixScanned() throws Exception {
        var result = P4E0R2QPositiveWitnesses.materializeAndScan(temporary.resolve("peaks"));
        var witnesses = result.witnesses();
        var profile = P4E0R2QProfile.locked();

        assertEquals(8, witnesses.size());
        assertEquals(0, result.dfuInvocations());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.HCA)
                        .decompressedBytes());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.HCA)
                        .nbt().byteArrayElements());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.DEPTH)
                        .nbt().maxContainerDepth());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.COMPOUND_CONTAINERS)
                        .nbt().compoundCount());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.FIELDS_AND_SCALARS)
                        .nbt().compoundEntryCount());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.FIELDS_AND_SCALARS)
                        .nbt().scalarTagCount());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.LIST)
                        .nbt().listElementCount());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.INT_ARRAY)
                        .nbt().intArrayElements());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.LONG_ARRAY)
                        .nbt().longArrayElements());
        assertEquals(
                profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE),
                witnesses.get(P4E0R2QPositiveWitnesses.WitnessKind.MODIFIED_UTF)
                        .nbt().modifiedUtf8Bytes());

        try (var paths = Files.list(temporary.resolve("peaks"))) {
            assertEquals(8L, paths.filter(Files::isRegularFile).count());
        }
        for (var entry : witnesses.values()) {
            assertEquals(3_955, entry.dataVersion().intValue());
        }
    }

    @Test
    void physicalPeaksCoexistBelowEveryAggregateCoordinate() throws Exception {
        var result = P4E0R2QPositiveWitnesses.materializeAndScan(
                temporary.resolve("aggregate"));
        var aggregate = result.aggregate();
        var profile = P4E0R2QProfile.locked();
        var structural = aggregate.structural();

        assertTrue(aggregate.compressedBytes()
                <= profile.maximum(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL));
        assertTrue(aggregate.decompressedBytes()
                <= profile.maximum(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL));
        assertTrue(structural.compoundContainers()
                <= profile.maximum(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL));
        assertTrue(structural.compoundFieldEntries()
                <= profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL));
        assertTrue(structural.listElements()
                <= profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL));
        assertTrue(structural.byteArrayElements()
                <= profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL));
        assertTrue(structural.intArrayElements()
                <= profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL));
        assertTrue(structural.longArrayElements()
                <= profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL));
        assertTrue(structural.modifiedUtf8Bytes()
                <= profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL));
        assertTrue(structural.scalarTags()
                <= profile.maximum(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL));
    }
}
