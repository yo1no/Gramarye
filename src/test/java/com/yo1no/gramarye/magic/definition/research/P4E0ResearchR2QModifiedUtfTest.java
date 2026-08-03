package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prefix checkpoint, Java modified-UTF compatibility, and scanner-path tests for R2Q-A. */
final class P4E0ResearchR2QModifiedUtfTest {
    private static final P4E0ResearchWireNbt.CheckpointLimits EXACT_CHECKPOINTS =
            new P4E0ResearchWireNbt.CheckpointLimits(
                    2,
                    1L,
                    6L,
                    2L,
                    2L,
                    2L,
                    2L,
                    13L,
                    4L);

    @Test
    void readerMatchesDataOutputModifiedUtfForEveryRequiredEncodingShape() throws Exception {
        for (var value : List.of(
                "ASCII",
                "\0",
                "\u0080\u07ff",
                "\u0800\uffff",
                "\ud83d\ude00",
                "A\0\u0080\u0800\ud83d\ude00")) {
            var observedLengths = new ArrayList<Integer>();
            var decoded = P4E0R2QModifiedUtf.read(
                    new DataInputStream(new ByteArrayInputStream(encoded(value))),
                    observedLengths::add);
            assertAll(
                    () -> assertEquals(value, decoded),
                    () -> assertEquals(
                            List.of(Math.toIntExact(
                                    P4E0ResearchNbtMetrics.modifiedUtf8Length(value))),
                            observedLengths));
        }
    }

    @Test
    void malformedAndTruncatedEncodingsRemainParserFailures() {
        assertAll(
                () -> assertThrows(
                        UTFDataFormatException.class,
                        () -> read(new byte[] {0, 1, (byte) 0x80})),
                () -> assertThrows(
                        UTFDataFormatException.class,
                        () -> read(new byte[] {0, 2, (byte) 0xc2, 0x20})),
                () -> assertThrows(
                        UTFDataFormatException.class,
                        () -> read(new byte[] {0, 1, (byte) 0xc2})),
                () -> assertThrows(
                        EOFException.class,
                        () -> read(new byte[] {0, 2, (byte) 0xc2})));
    }

    @Test
    void exactPrefixIsAcceptedAndMaximumPlusOneRejectsBeforePayloadReadOrAllocation()
            throws Exception {
        var aggregate = new P4E0R2QModifiedUtf.AggregateBudget(3L);
        var exact = new P4E0R2QModifiedUtf.Budget(3L, aggregate);
        assertEquals(
                "abc",
                P4E0R2QModifiedUtf.read(
                        new DataInputStream(new ByteArrayInputStream(encoded("abc"))), exact));
        assertAll(
                () -> assertEquals(3L, exact.perFileObserved()),
                () -> assertEquals(3L, aggregate.observed()));

        var perFileFailure = assertThrows(
                P4E0R2QModifiedUtf.CapacityException.class,
                () -> P4E0R2QModifiedUtf.read(
                        new DataInputStream(new ByteArrayInputStream(new byte[] {0, 4})),
                        new P4E0R2QModifiedUtf.Budget(
                                3L, new P4E0R2QModifiedUtf.AggregateBudget(10L))));
        assertAll(
                () -> assertEquals(P4E0R2QModifiedUtf.Scope.PER_FILE, perFileFailure.scope()),
                () -> assertEquals(4L, perFileFailure.observedAtLeast()),
                () -> assertEquals(3L, perFileFailure.maximum()));
    }

    @Test
    void aggregateFailureIsDistinctAndDoesNotPublishPartialPerFileCount() {
        var aggregate = new P4E0R2QModifiedUtf.AggregateBudget(2L);
        var budget = new P4E0R2QModifiedUtf.Budget(10L, aggregate);
        var failure = assertThrows(
                P4E0R2QModifiedUtf.CapacityException.class,
                () -> P4E0R2QModifiedUtf.read(
                        new DataInputStream(new ByteArrayInputStream(new byte[] {0, 3})),
                        budget));
        assertAll(
                () -> assertEquals(P4E0R2QModifiedUtf.Scope.AGGREGATE, failure.scope()),
                () -> assertEquals(3L, failure.observedAtLeast()),
                () -> assertEquals(2L, failure.maximum()),
                () -> assertEquals(0L, budget.perFileObserved()),
                () -> assertEquals(0L, aggregate.observed()));
    }

    @Test
    void fieldNameAndStringPayloadUseTheSamePrefixAwareScannerPath() throws Exception {
        var directory = Files.createTempDirectory("gramarye-p4-e0-r2q-utf-");
        var file = directory.resolve("utf.dat");
        var fieldName = "field\0\u0080";
        var value = "value\u0800\ud83d\ude00";
        try {
            var written = P4E0ResearchWireNbt.write(
                    file,
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    Deflater.DEFAULT_COMPRESSION,
                    16_384L,
                    16_384L,
                    output -> {
                        P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                        output.writeByte(Tag.TAG_STRING);
                        output.writeUTF(fieldName);
                        output.writeUTF(value);
                        output.writeByte(Tag.TAG_END);
                    });
            var expectedUtfBytes = Math.addExact(
                    P4E0ResearchNbtMetrics.modifiedUtf8Length(fieldName),
                    P4E0ResearchNbtMetrics.modifiedUtf8Length(value));
            var aggregate = new P4E0R2QModifiedUtf.AggregateBudget(expectedUtfBytes);
            var scanned = P4E0ResearchWireNbt.scan(
                    file,
                    new P4E0ResearchWireNbt.ScanLimits(
                            written.physicalBytes(),
                            written.decompressedBytes(),
                            4L,
                            2,
                            1L,
                            expectedUtfBytes),
                    aggregate);
            assertAll(
                    () -> assertEquals(expectedUtfBytes, scanned.nbt().modifiedUtf8Bytes()),
                    () -> assertEquals(expectedUtfBytes, aggregate.observed()),
                    () -> assertEquals(1L, scanned.nbt().compoundEntryCount()),
                    () -> assertEquals(1L, scanned.nbt().stringCount()));
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void qualificationCheckpointsAcceptExactPerFileAndAggregateValues(
            @TempDir Path directory) throws Exception {
        var fixture = writeCheckpointFixture(directory.resolve("exact.dat"));
        var aggregate = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                EXACT_CHECKPOINTS);
        var scanned = scanCheckpointFixture(fixture, EXACT_CHECKPOINTS, aggregate);
        var expected = new P4E0ResearchWireNbt.CheckpointFacts(
                2,
                1L,
                6L,
                2L,
                2L,
                2L,
                2L,
                13L,
                4L);
        assertAll(
                () -> assertEquals(expected, aggregate.observed()),
                () -> assertEquals(2L, scanned.nbt().maxContainerDepth()),
                () -> assertEquals(1L, scanned.nbt().compoundCount()),
                () -> assertEquals(6L, scanned.nbt().compoundEntryCount()),
                () -> assertEquals(2L, scanned.nbt().listElementCount()),
                () -> assertEquals(2L, scanned.nbt().byteArrayElements()),
                () -> assertEquals(2L, scanned.nbt().intArrayElements()),
                () -> assertEquals(2L, scanned.nbt().longArrayElements()),
                () -> assertEquals(13L, scanned.nbt().modifiedUtf8Bytes()),
                () -> assertEquals(4L, scanned.nbt().scalarTagCount()));
    }

    @Test
    void everyPerFileCheckpointRejectsMaximumPlusOne(@TempDir Path directory)
            throws Exception {
        var fixture = writeCheckpointFixture(directory.resolve("per-file.dat"));
        for (var coordinate : checkpointCoordinates()) {
            var lowered = lower(EXACT_CHECKPOINTS, coordinate);
            var aggregate = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                    EXACT_CHECKPOINTS);
            var failure = assertThrows(
                    P4E0ResearchWireNbt.ResearchLimitException.class,
                    () -> scanCheckpointFixture(fixture, lowered, aggregate),
                    coordinate);
            assertEquals(perFileCoordinate(coordinate), failure.coordinate(), coordinate);
        }
    }

    @Test
    void everyAggregateCheckpointRejectsMaximumPlusOne(@TempDir Path directory)
            throws Exception {
        var fixture = writeCheckpointFixture(directory.resolve("aggregate.dat"));
        for (var coordinate : checkpointCoordinates()) {
            var aggregate = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                    lower(EXACT_CHECKPOINTS, coordinate));
            var failure = assertThrows(
                    P4E0ResearchWireNbt.ResearchLimitException.class,
                    () -> scanCheckpointFixture(fixture, EXACT_CHECKPOINTS, aggregate),
                    coordinate);
            assertEquals(aggregateCoordinate(coordinate), failure.coordinate(), coordinate);
        }
    }

    @Test
    void additiveAggregateCountersAccumulateAcrossFiles(@TempDir Path directory)
            throws Exception {
        var first = writeCheckpointFixture(directory.resolve("first.dat"));
        var second = writeCheckpointFixture(directory.resolve("second.dat"));
        var aggregate = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                doubledAggregateCheckpoints());
        scanCheckpointFixture(first, EXACT_CHECKPOINTS, aggregate);
        scanCheckpointFixture(second, EXACT_CHECKPOINTS, aggregate);
        assertEquals(
                new P4E0ResearchWireNbt.CheckpointFacts(
                        2,
                        2L,
                        12L,
                        4L,
                        4L,
                        4L,
                        4L,
                        26L,
                        8L),
                aggregate.observed());
    }

    @Test
    void structuralCheckpointsPrecedeNamesAndPayloadReads(@TempDir Path directory)
            throws Exception {
        var field = writeMalformed(
                directory.resolve("field.dat"),
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                });
        assertCheckpointFailure(
                field,
                generousCheckpointsWith("compound_field_entries", 0L),
                "compound_field_entries_per_file");

        var scalar = writeMalformed(
                directory.resolve("scalar.dat"),
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("x");
                });
        assertCheckpointFailure(
                scalar,
                generousCheckpointsWith("scalar_tags", 0L),
                "scalar_tags_per_file");

        var byteArray = writeMalformed(
                directory.resolve("byte-array.dat"),
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_BYTE_ARRAY);
                    output.writeUTF("x");
                    output.writeInt(2);
                });
        assertCheckpointFailure(
                byteArray,
                generousCheckpointsWith("byte_array_elements", 1L),
                "byte_array_elements_per_file");

        var list = writeMalformed(
                directory.resolve("list.dat"),
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_LIST);
                    output.writeUTF("x");
                    output.writeByte(Tag.TAG_INT);
                    output.writeInt(2);
                });
        assertCheckpointFailure(
                list,
                generousCheckpointsWith("list_elements", 1L),
                "list_elements_per_file");
    }

    @Test
    void bothModifiedUtfPrefixesAreCheckedBeforeTheirEncodedBytes(
            @TempDir Path directory) throws Exception {
        var fieldName = writeMalformed(
                directory.resolve("field-name.dat"),
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeShort(1);
                });
        assertCheckpointFailure(
                fieldName,
                generousCheckpointsWith("modified_utf8_bytes", 0L),
                "modified_utf8_bytes_per_file");

        var stringPayload = writeMalformed(
                directory.resolve("string-payload.dat"),
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_STRING);
                    output.writeUTF("x");
                    output.writeShort(1);
                });
        assertCheckpointFailure(
                stringPayload,
                generousCheckpointsWith("modified_utf8_bytes", 1L),
                "modified_utf8_bytes_per_file");
    }

    @Test
    void malformedUtfIsNotReclassifiedAsCapacityWhenPrefixIsInBound() {
        var aggregate = new P4E0R2QModifiedUtf.AggregateBudget(10L);
        var budget = new P4E0R2QModifiedUtf.Budget(10L, aggregate);
        assertThrows(
                UTFDataFormatException.class,
                () -> P4E0R2QModifiedUtf.read(
                        new DataInputStream(new ByteArrayInputStream(
                                new byte[] {0, 2, (byte) 0xc2, 0x20})),
                        budget));
        assertAll(
                () -> assertEquals(2L, budget.perFileObserved()),
                () -> assertEquals(2L, aggregate.observed()));
    }

    @Test
    void prefixCheckpointPrecedesDecodeAllocationAndBothScannerPathsUseIt() throws Exception {
        var root = projectRoot();
        var utility = Files.readString(root.resolve(
                "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0R2QModifiedUtf.java"));
        var scanner = Files.readString(root.resolve(
                "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchWireNbt.java"));
        var checkpoint = utility.indexOf("observer.observe(encodedBytes);");
        var byteAllocation = utility.indexOf("new byte[encodedBytes]", checkpoint);
        var characterAllocation = utility.indexOf("new char[encodedBytes]", checkpoint);
        var stringAllocation = utility.indexOf(
                "new String(characters, 0, characterIndex)", checkpoint);
        assertAll(
                () -> assertTrue(checkpoint >= 0),
                () -> assertTrue(checkpoint < byteAllocation),
                () -> assertTrue(checkpoint < characterAllocation),
                () -> assertTrue(checkpoint < stringAllocation),
                () -> assertEquals(
                        2,
                        scanner.split("P4E0R2QModifiedUtf\\.read\\(", -1).length - 1),
                () -> assertTrue(!scanner.contains("input.readUTF()")));
    }

    private static byte[] encoded(String value) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeUTF(value);
        }
        return bytes.toByteArray();
    }

    private static String read(byte[] bytes) throws Exception {
        return P4E0R2QModifiedUtf.read(
                new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    private static CheckpointFixture writeCheckpointFixture(Path path) throws Exception {
        var facts = P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                16_384L,
                16_384L,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_BYTE);
                    output.writeUTF("b");
                    output.writeByte(1);
                    output.writeByte(Tag.TAG_STRING);
                    output.writeUTF("s");
                    output.writeUTF("v");
                    output.writeByte(Tag.TAG_BYTE_ARRAY);
                    output.writeUTF("ba");
                    output.writeInt(2);
                    output.write(new byte[] {1, 2});
                    output.writeByte(Tag.TAG_INT_ARRAY);
                    output.writeUTF("ia");
                    output.writeInt(2);
                    output.writeInt(1);
                    output.writeInt(2);
                    output.writeByte(Tag.TAG_LONG_ARRAY);
                    output.writeUTF("la");
                    output.writeInt(2);
                    output.writeLong(1L);
                    output.writeLong(2L);
                    output.writeByte(Tag.TAG_LIST);
                    output.writeUTF("list");
                    output.writeByte(Tag.TAG_INT);
                    output.writeInt(2);
                    output.writeInt(1);
                    output.writeInt(2);
                    output.writeByte(Tag.TAG_END);
                });
        return new CheckpointFixture(path, facts);
    }

    private static CheckpointFixture writeMalformed(
            Path path, P4E0ResearchWireNbt.PayloadWriter payload) throws Exception {
        var facts = P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                16_384L,
                16_384L,
                payload);
        return new CheckpointFixture(path, facts);
    }

    private static P4E0ResearchWireNbt.ScanFacts scanCheckpointFixture(
            CheckpointFixture fixture,
            P4E0ResearchWireNbt.CheckpointLimits perFile,
            P4E0ResearchWireNbt.AggregateCheckpointBudget aggregate)
            throws Exception {
        return P4E0ResearchWireNbt.scan(
                fixture.path(),
                new P4E0ResearchWireNbt.ScanLimits(
                        fixture.facts().physicalBytes(),
                        fixture.facts().decompressedBytes(),
                        32L,
                        8,
                        32L,
                        64L),
                perFile,
                aggregate);
    }

    private static void assertCheckpointFailure(
            CheckpointFixture fixture,
            P4E0ResearchWireNbt.CheckpointLimits perFile,
            String coordinate) {
        var aggregate = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                generousCheckpointsWith("none", 0L));
        var failure = assertThrows(
                P4E0ResearchWireNbt.ResearchLimitException.class,
                () -> scanCheckpointFixture(fixture, perFile, aggregate));
        assertEquals(coordinate, failure.coordinate());
    }

    private static List<String> checkpointCoordinates() {
        return List.of(
                "container_depth",
                "compound_containers",
                "compound_field_entries",
                "list_elements",
                "byte_array_elements",
                "int_array_elements",
                "long_array_elements",
                "modified_utf8_bytes",
                "scalar_tags");
    }

    private static P4E0ResearchWireNbt.CheckpointLimits lower(
            P4E0ResearchWireNbt.CheckpointLimits limits, String coordinate) {
        return new P4E0ResearchWireNbt.CheckpointLimits(
                coordinate.equals("container_depth")
                        ? limits.containerDepth() - 1 : limits.containerDepth(),
                coordinate.equals("compound_containers")
                        ? limits.compoundContainers() - 1L : limits.compoundContainers(),
                coordinate.equals("compound_field_entries")
                        ? limits.compoundFieldEntries() - 1L : limits.compoundFieldEntries(),
                coordinate.equals("list_elements")
                        ? limits.listElements() - 1L : limits.listElements(),
                coordinate.equals("byte_array_elements")
                        ? limits.byteArrayElements() - 1L : limits.byteArrayElements(),
                coordinate.equals("int_array_elements")
                        ? limits.intArrayElements() - 1L : limits.intArrayElements(),
                coordinate.equals("long_array_elements")
                        ? limits.longArrayElements() - 1L : limits.longArrayElements(),
                coordinate.equals("modified_utf8_bytes")
                        ? limits.modifiedUtf8Bytes() - 1L : limits.modifiedUtf8Bytes(),
                coordinate.equals("scalar_tags")
                        ? limits.scalarTags() - 1L : limits.scalarTags());
    }

    private static P4E0ResearchWireNbt.CheckpointLimits doubledAggregateCheckpoints() {
        return new P4E0ResearchWireNbt.CheckpointLimits(
                EXACT_CHECKPOINTS.containerDepth(),
                EXACT_CHECKPOINTS.compoundContainers() * 2L,
                EXACT_CHECKPOINTS.compoundFieldEntries() * 2L,
                EXACT_CHECKPOINTS.listElements() * 2L,
                EXACT_CHECKPOINTS.byteArrayElements() * 2L,
                EXACT_CHECKPOINTS.intArrayElements() * 2L,
                EXACT_CHECKPOINTS.longArrayElements() * 2L,
                EXACT_CHECKPOINTS.modifiedUtf8Bytes() * 2L,
                EXACT_CHECKPOINTS.scalarTags() * 2L);
    }

    private static P4E0ResearchWireNbt.CheckpointLimits generousCheckpointsWith(
            String coordinate, long value) {
        var generous = new P4E0ResearchWireNbt.CheckpointLimits(
                8, 32L, 32L, 32L, 32L, 32L, 32L, 64L, 32L);
        return switch (coordinate) {
            case "compound_field_entries" -> new P4E0ResearchWireNbt.CheckpointLimits(
                    generous.containerDepth(),
                    generous.compoundContainers(),
                    value,
                    generous.listElements(),
                    generous.byteArrayElements(),
                    generous.intArrayElements(),
                    generous.longArrayElements(),
                    generous.modifiedUtf8Bytes(),
                    generous.scalarTags());
            case "list_elements" -> new P4E0ResearchWireNbt.CheckpointLimits(
                    generous.containerDepth(),
                    generous.compoundContainers(),
                    generous.compoundFieldEntries(),
                    value,
                    generous.byteArrayElements(),
                    generous.intArrayElements(),
                    generous.longArrayElements(),
                    generous.modifiedUtf8Bytes(),
                    generous.scalarTags());
            case "byte_array_elements" -> new P4E0ResearchWireNbt.CheckpointLimits(
                    generous.containerDepth(),
                    generous.compoundContainers(),
                    generous.compoundFieldEntries(),
                    generous.listElements(),
                    value,
                    generous.intArrayElements(),
                    generous.longArrayElements(),
                    generous.modifiedUtf8Bytes(),
                    generous.scalarTags());
            case "modified_utf8_bytes" -> new P4E0ResearchWireNbt.CheckpointLimits(
                    generous.containerDepth(),
                    generous.compoundContainers(),
                    generous.compoundFieldEntries(),
                    generous.listElements(),
                    generous.byteArrayElements(),
                    generous.intArrayElements(),
                    generous.longArrayElements(),
                    value,
                    generous.scalarTags());
            case "scalar_tags" -> new P4E0ResearchWireNbt.CheckpointLimits(
                    generous.containerDepth(),
                    generous.compoundContainers(),
                    generous.compoundFieldEntries(),
                    generous.listElements(),
                    generous.byteArrayElements(),
                    generous.intArrayElements(),
                    generous.longArrayElements(),
                    generous.modifiedUtf8Bytes(),
                    value);
            default -> generous;
        };
    }

    private static String perFileCoordinate(String coordinate) {
        return coordinate + "_per_file";
    }

    private static String aggregateCoordinate(String coordinate) {
        return coordinate.equals("container_depth")
                ? "container_depth_aggregate"
                : coordinate + "_total";
    }

    private record CheckpointFixture(
            Path path, P4E0ResearchWireNbt.WriteFacts facts) {
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root not found");
    }
}
