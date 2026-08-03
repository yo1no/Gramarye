package com.yo1no.gramarye.magic.definition.research;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.ToLongFunction;
import java.util.zip.Deflater;
import net.minecraft.nbt.Tag;

/**
 * Deterministic 2,048-record physical/counting plan joining the approved structural tuple.
 *
 * <p>Every record has one streaming wire writer. Canonical compressed baselines are measured from
 * those same writers, so gzip header tuning cannot silently target an unrelated empty-root file.
 * The plan is cached in the enclosing fixture holder and never starts a child JVM.</p>
 */
final class P4E0R2QJointRecords {
    private static final int RECORD_COUNT = 2_048;
    private static final int PEAK_RECORD_COUNT = 9;
    private static final int FILLER_COUNT = RECORD_COUNT - PEAK_RECORD_COUNT;
    private static final int DATA_VERSION = 3_955;
    private static final int COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;
    private static final long MAXIMUM_PHYSICAL_BYTES = 33_559_514L;
    private static final long MAXIMUM_DECOMPRESSED_BYTES = 268_435_456L;
    private static final int BUFFER_BYTES = 8_192;

    private P4E0R2QJointRecords() {
    }

    static Plan build(P4E0R2QFixturePlan.StructuralComposition structural) {
        Objects.requireNonNull(structural, "structural");
        try {
            return create(structural);
        } catch (IOException exception) {
            throw new IllegalStateException("R2Q joint record plan could not be measured", exception);
        }
    }

    private static Plan create(P4E0R2QFixturePlan.StructuralComposition structural)
            throws IOException {
        var plans = new ArrayList<UnmeasuredRecord>(RECORD_COUNT);
        plans.add(peak(
                "HCA",
                new RecordFacts(
                        268_435_456L, 1, 1, 3, 0,
                        268_435_384L, 4, 0, 31, 1),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.HCA)));
        plans.add(peak(
                "LOW_COMPRESSION_COORDINATE",
                new RecordFacts(
                        33_554_376L, 1, 1, 3, 0,
                        33_554_304L, 4, 0, 31, 1),
                repeatedArray(33_554_304)));
        plans.add(peak(
                "DEPTH",
                new RecordFacts(2_577L, 512, 512, 512, 0, 0, 0, 0, 522, 1),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.DEPTH)));
        plans.add(peak(
                "COMPOUND_CONTAINERS",
                new RecordFacts(1_054L, 3, 1_024, 2, 1_023, 0, 0, 0, 12, 1),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.COMPOUND_CONTAINERS)));
        plans.add(peak(
                "FIELDS_AND_SCALARS",
                new RecordFacts(
                        655_382L, 1, 1, 65_537, 0, 0, 0, 0, 393_227L, 65_537),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.FIELDS_AND_SCALARS)));
        plans.add(peak(
                "LIST",
                new RecordFacts(65_567L, 2, 1, 2, 65_536, 0, 0, 0, 12, 65_537),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.LIST)));
        plans.add(peak(
                "INT_ARRAY",
                new RecordFacts(262_174L, 1, 1, 2, 0, 0, 65_536, 0, 12, 1),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.INT_ARRAY)));
        plans.add(peak(
                "LONG_ARRAY",
                new RecordFacts(524_318L, 1, 1, 2, 0, 0, 0, 65_536, 12, 1),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.LONG_ARRAY)));
        plans.add(peak(
                "MODIFIED_UTF",
                new RecordFacts(
                        67_112_823L, 1, 1, 1_025, 0, 0, 0, 0,
                        67_107_692L, 1_025),
                P4E0R2QPositiveWitnesses.payload(
                        P4E0R2QPositiveWitnesses.WitnessKind.MODIFIED_UTF)));

        var remaining = subtract(structural, plans);
        var decompressed = distribute(remaining.decompressedBytes(), FILLER_COUNT);
        var containers = distribute(remaining.compoundContainers(), FILLER_COUNT);
        var fields = distribute(remaining.compoundFieldEntries(), FILLER_COUNT);
        var lists = distribute(remaining.listElements(), FILLER_COUNT);
        var bytes = distribute(remaining.byteArrayElements(), FILLER_COUNT);
        var ints = distribute(remaining.intArrayElements(), FILLER_COUNT);
        var longs = distribute(remaining.longArrayElements(), FILLER_COUNT);
        var utf = distribute(remaining.modifiedUtf8Bytes(), FILLER_COUNT);
        var scalars = distribute(remaining.scalarTags(), FILLER_COUNT);
        for (var index = 0; index < FILLER_COUNT; index++) {
            var facts = new RecordFacts(
                    decompressed[index],
                    2,
                    containers[index],
                    fields[index],
                    lists[index],
                    bytes[index],
                    ints[index],
                    longs[index],
                    utf[index],
                    scalars[index]);
            var encoding = FillerEncoding.create(facts);
            plans.add(new UnmeasuredRecord(
                    "FILLER_" + String.format(Locale.ROOT, "%04d", index),
                    facts,
                    encoding::write));
        }

        requireExactAggregate(structural, plans);
        var measured = new ArrayList<MeasuredRecord>(RECORD_COUNT);
        for (var index = 0; index < plans.size(); index++) {
            var plan = plans.get(index);
            var wire = P4E0ResearchWireNbt.measure(
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    COMPRESSION_LEVEL,
                    MAXIMUM_PHYSICAL_BYTES,
                    MAXIMUM_DECOMPRESSED_BYTES,
                    plan.writer());
            if (wire.decompressedBytes() != plan.facts().decompressedBytes()) {
                throw new IOException(
                        "R2Q joint writer decompressed count changed at index " + index);
            }
            measured.add(new MeasuredRecord(
                    index,
                    plan.code(),
                    plan.facts(),
                    plan.writer(),
                    wire.physicalBytes()));
        }
        return new Plan(measured, aggregate(plans));
    }

    private static UnmeasuredRecord peak(
            String code,
            RecordFacts facts,
            P4E0ResearchWireNbt.PayloadWriter writer) {
        return new UnmeasuredRecord(code, facts, writer);
    }

    private static P4E0ResearchWireNbt.PayloadWriter repeatedArray(int length) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeDataVersion(output);
            output.writeByte(Tag.TAG_INT_ARRAY);
            output.writeUTF("UUID");
            output.writeInt(4);
            output.writeInt(0x5034_4530);
            output.writeInt(0x5232_5100);
            output.writeInt(0);
            output.writeInt(2);
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("research_payload");
            output.writeInt(length);
            writeRepeated(output, length, 0x5a);
            output.writeByte(Tag.TAG_END);
        };
    }

    private static RecordFacts subtract(
            P4E0R2QFixturePlan.StructuralComposition structural,
            List<UnmeasuredRecord> peaks) {
        var used = aggregate(peaks);
        return new RecordFacts(
                Math.subtractExact(structural.decompressedBytes(), used.decompressedBytes()),
                2,
                Math.subtractExact(structural.compoundContainers(), used.compoundContainers()),
                Math.subtractExact(structural.compoundFieldEntries(), used.compoundFieldEntries()),
                Math.subtractExact(structural.listElements(), used.listElements()),
                Math.subtractExact(structural.byteArrayElements(), used.byteArrayElements()),
                Math.subtractExact(structural.intArrayElements(), used.intArrayElements()),
                Math.subtractExact(structural.longArrayElements(), used.longArrayElements()),
                Math.subtractExact(structural.modifiedUtf8Bytes(), used.modifiedUtf8Bytes()),
                Math.subtractExact(structural.scalarTags(), used.scalarTags()));
    }

    private static long[] distribute(long total, int records) {
        if (total < 0 || records <= 0) {
            throw new IllegalArgumentException("invalid R2Q joint distribution");
        }
        var values = new long[records];
        var base = total / records;
        var remainder = total % records;
        for (var index = 0; index < records; index++) {
            values[index] = base + (index < remainder ? 1L : 0L);
        }
        return values;
    }

    private static RecordFacts aggregate(List<UnmeasuredRecord> records) {
        return new RecordFacts(
                sum(records, facts -> facts.decompressedBytes()),
                records.stream().mapToInt(record -> record.facts().containerDepth()).max()
                        .orElse(0),
                sum(records, facts -> facts.compoundContainers()),
                sum(records, facts -> facts.compoundFieldEntries()),
                sum(records, facts -> facts.listElements()),
                sum(records, facts -> facts.byteArrayElements()),
                sum(records, facts -> facts.intArrayElements()),
                sum(records, facts -> facts.longArrayElements()),
                sum(records, facts -> facts.modifiedUtf8Bytes()),
                sum(records, facts -> facts.scalarTags()));
    }

    private static long sum(
            List<UnmeasuredRecord> records, ToLongFunction<RecordFacts> coordinate) {
        var total = 0L;
        for (var record : records) {
            total = Math.addExact(total, coordinate.applyAsLong(record.facts()));
        }
        return total;
    }

    private static void requireExactAggregate(
            P4E0R2QFixturePlan.StructuralComposition structural,
            List<UnmeasuredRecord> records) {
        if (records.size() != RECORD_COUNT) {
            throw new IllegalArgumentException("R2Q joint record count changed");
        }
        var actual = aggregate(records);
        if (actual.decompressedBytes() != structural.decompressedBytes()
                || actual.containerDepth() != structural.peaks().containerDepthPerFile()
                || actual.compoundContainers() != structural.compoundContainers()
                || actual.compoundFieldEntries() != structural.compoundFieldEntries()
                || actual.listElements() != structural.listElements()
                || actual.byteArrayElements() != structural.byteArrayElements()
                || actual.intArrayElements() != structural.intArrayElements()
                || actual.longArrayElements() != structural.longArrayElements()
                || actual.modifiedUtf8Bytes() != structural.modifiedUtf8Bytes()
                || actual.scalarTags() != structural.scalarTags()) {
            throw new IllegalArgumentException("R2Q joint structural aggregate changed");
        }
        var peaks = structural.peaks();
        for (var record : records) {
            var facts = record.facts();
            if (facts.decompressedBytes() > peaks.decompressedBytesPerFile()
                    || facts.containerDepth() > peaks.containerDepthPerFile()
                    || facts.compoundContainers() > peaks.compoundContainersPerFile()
                    || facts.compoundFieldEntries() > peaks.compoundFieldEntriesPerFile()
                    || facts.listElements() > peaks.listElementsPerFile()
                    || facts.byteArrayElements() > peaks.byteArrayElementsPerFile()
                    || facts.intArrayElements() > peaks.intArrayElementsPerFile()
                    || facts.longArrayElements() > peaks.longArrayElementsPerFile()
                    || facts.modifiedUtf8Bytes() > peaks.modifiedUtf8BytesPerFile()
                    || facts.scalarTags() > peaks.scalarTagsPerFile()) {
                throw new IllegalArgumentException("R2Q joint record exceeds a per-file peak");
            }
        }
    }

    private static void writeDataVersion(DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_INT);
        output.writeUTF("DataVersion");
        output.writeInt(DATA_VERSION);
    }

    private static void writeRepeated(DataOutput output, int length, int value)
            throws IOException {
        var bytes = new byte[BUFFER_BYTES];
        java.util.Arrays.fill(bytes, (byte) value);
        var remaining = length;
        while (remaining > 0) {
            var count = Math.min(remaining, bytes.length);
            output.write(bytes, 0, count);
            remaining -= count;
        }
    }

    record Plan(List<MeasuredRecord> records, RecordFacts aggregate) {
        Plan {
            records = List.copyOf(Objects.requireNonNull(records, "records"));
            Objects.requireNonNull(aggregate, "aggregate");
            if (records.size() != RECORD_COUNT) {
                throw new IllegalArgumentException("R2Q joint measured record count changed");
            }
        }

        List<Long> canonicalPhysicalBytes() {
            return records.stream().map(MeasuredRecord::canonicalPhysicalBytes).toList();
        }

        P4E0ResearchWireNbt.WriteFacts measure(
                int index,
                P4E0ResearchWireNbt.HeaderOptions header,
                long maximumPhysicalBytes) throws IOException {
            var record = records.get(index);
            return P4E0ResearchWireNbt.measure(
                    header,
                    COMPRESSION_LEVEL,
                    maximumPhysicalBytes,
                    MAXIMUM_DECOMPRESSED_BYTES,
                    record.writer());
        }

        /**
         * Writes one tuned member for strict, sequential test traversal.
         *
         * <p>The caller owns the path lifecycle and must delete each member before writing the
         * next one. This keeps R2Q-A from publishing or retaining the complete 2,048-file formal
         * fixture while proving that the same writers used for counting produce valid wire
         * members.</p>
         */
        P4E0ResearchWireNbt.WriteFacts write(
                int index,
                Path path,
                P4E0ResearchWireNbt.HeaderOptions header,
                long maximumPhysicalBytes) throws IOException {
            Objects.requireNonNull(path, "path");
            var record = records.get(index);
            return P4E0ResearchWireNbt.write(
                    path,
                    header,
                    COMPRESSION_LEVEL,
                    maximumPhysicalBytes,
                    MAXIMUM_DECOMPRESSED_BYTES,
                    record.writer());
        }
    }

    record MeasuredRecord(
            int index,
            String code,
            RecordFacts facts,
            P4E0ResearchWireNbt.PayloadWriter writer,
            long canonicalPhysicalBytes) {
        MeasuredRecord {
            if (index < 0 || code == null || code.isBlank()
                    || facts == null || writer == null
                    || canonicalPhysicalBytes <= 0
                    || canonicalPhysicalBytes > MAXIMUM_PHYSICAL_BYTES) {
                throw new IllegalArgumentException("invalid measured R2Q joint record");
            }
        }
    }

    private record UnmeasuredRecord(
            String code,
            RecordFacts facts,
            P4E0ResearchWireNbt.PayloadWriter writer) {
        private UnmeasuredRecord {
            if (code == null || code.isBlank() || facts == null || writer == null) {
                throw new IllegalArgumentException("invalid unmeasured R2Q joint record");
            }
        }
    }

    record RecordFacts(
            long decompressedBytes,
            int containerDepth,
            long compoundContainers,
            long compoundFieldEntries,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long modifiedUtf8Bytes,
            long scalarTags) {
        RecordFacts {
            if (decompressedBytes < 0 || containerDepth < 0
                    || compoundContainers < 0 || compoundFieldEntries < 0
                    || listElements < 0 || byteArrayElements < 0
                    || intArrayElements < 0 || longArrayElements < 0
                    || modifiedUtf8Bytes < 0 || scalarTags < 0) {
                throw new IllegalArgumentException("negative R2Q joint record fact");
            }
        }
    }

    private record FillerEncoding(
            RecordFacts facts,
            int emptyCompounds,
            int scalarFields,
            int zeroArrays,
            int wideLongs,
            int wideInts,
            int wideShorts) {
        private static FillerEncoding create(RecordFacts facts) {
            var emptyCompounds = Math.toIntExact(facts.compoundContainers() - 1L);
            var scalarFields = Math.toIntExact(
                    facts.scalarTags() - 1L - facts.listElements());
            var usedFields = Math.addExact(
                    1,
                    Math.addExact(
                            emptyCompounds,
                            Math.addExact(
                                    facts.listElements() > 0 ? 1 : 0,
                                    Math.addExact(3, scalarFields))));
            var zeroArrays = Math.toIntExact(facts.compoundFieldEntries() - usedFields);
            if (emptyCompounds < 0 || scalarFields < 0 || zeroArrays < 0) {
                throw new IllegalArgumentException("R2Q filler structural counts are infeasible");
            }
            var baseBytes = Math.addExact(
                    4L,
                    Math.addExact(
                            Math.multiplyExact(3L, facts.compoundFieldEntries()),
                            facts.modifiedUtf8Bytes()));
            baseBytes = Math.addExact(baseBytes, 4L); // DataVersion payload.
            baseBytes = Math.addExact(baseBytes, emptyCompounds);
            if (facts.listElements() > 0) {
                baseBytes = Math.addExact(baseBytes, 5L + facts.listElements());
            }
            baseBytes = Math.addExact(baseBytes, 4L + facts.byteArrayElements());
            baseBytes = Math.addExact(
                    baseBytes, 4L + Math.multiplyExact(4L, facts.intArrayElements()));
            baseBytes = Math.addExact(
                    baseBytes, 4L + Math.multiplyExact(8L, facts.longArrayElements()));
            baseBytes = Math.addExact(baseBytes, scalarFields);
            baseBytes = Math.addExact(baseBytes, Math.multiplyExact(4L, zeroArrays));
            var extra = Math.subtractExact(facts.decompressedBytes(), baseBytes);
            if (extra < 0 || extra > Math.multiplyExact(7L, scalarFields)) {
                throw new IllegalArgumentException("R2Q filler decompressed padding is infeasible");
            }
            var wideLongs = Math.toIntExact(extra / 7L);
            var remainder = Math.toIntExact(extra % 7L);
            var wideInts = remainder / 3;
            var wideShorts = remainder % 3;
            if (wideLongs + wideInts + wideShorts > scalarFields) {
                throw new IllegalArgumentException("R2Q filler scalar padding changed");
            }
            return new FillerEncoding(
                    facts,
                    emptyCompounds,
                    scalarFields,
                    zeroArrays,
                    wideLongs,
                    wideInts,
                    wideShorts);
        }

        private void write(DataOutput output) throws IOException {
            var names = fieldNames(
                    Math.toIntExact(facts.compoundFieldEntries() - 1L),
                    Math.subtractExact(facts.modifiedUtf8Bytes(), 11L));
            var cursor = 0;
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeDataVersion(output);
            for (var index = 0; index < emptyCompounds; index++) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF(names.get(cursor++));
                output.writeByte(Tag.TAG_END);
            }
            if (facts.listElements() > 0) {
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF(names.get(cursor++));
                output.writeByte(Tag.TAG_BYTE);
                output.writeInt(Math.toIntExact(facts.listElements()));
                writeRepeated(output, Math.toIntExact(facts.listElements()), 0x5a);
            }
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF(names.get(cursor++));
            output.writeInt(Math.toIntExact(facts.byteArrayElements()));
            writeRepeated(output, Math.toIntExact(facts.byteArrayElements()), 0x5a);
            output.writeByte(Tag.TAG_INT_ARRAY);
            output.writeUTF(names.get(cursor++));
            output.writeInt(Math.toIntExact(facts.intArrayElements()));
            for (var index = 0; index < facts.intArrayElements(); index++) {
                output.writeInt(index);
            }
            output.writeByte(Tag.TAG_LONG_ARRAY);
            output.writeUTF(names.get(cursor++));
            output.writeInt(Math.toIntExact(facts.longArrayElements()));
            for (var index = 0; index < facts.longArrayElements(); index++) {
                output.writeLong(0x5034_4530_5232_5100L ^ index);
            }
            for (var index = 0; index < scalarFields; index++) {
                if (index < wideLongs) {
                    output.writeByte(Tag.TAG_LONG);
                    output.writeUTF(names.get(cursor++));
                    output.writeLong(index);
                } else if (index < wideLongs + wideInts) {
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF(names.get(cursor++));
                    output.writeInt(index);
                } else if (index < wideLongs + wideInts + wideShorts) {
                    output.writeByte(Tag.TAG_SHORT);
                    output.writeUTF(names.get(cursor++));
                    output.writeShort(index);
                } else {
                    output.writeByte(Tag.TAG_BYTE);
                    output.writeUTF(names.get(cursor++));
                    output.writeByte(index);
                }
            }
            for (var index = 0; index < zeroArrays; index++) {
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(0);
            }
            if (cursor != names.size()) {
                throw new IOException("R2Q filler field-name plan was not consumed exactly");
            }
            output.writeByte(Tag.TAG_END);
        }

        private static List<String> fieldNames(int count, long encodedBytes) {
            if (count <= 0 || encodedBytes < 0) {
                throw new IllegalArgumentException("invalid R2Q filler field-name target");
            }
            var bases = new ArrayList<String>(count);
            var baseBytes = 0L;
            for (var index = 0; index < count; index++) {
                var base = String.format(Locale.ROOT, "n%05d", index);
                bases.add(base);
                baseBytes = Math.addExact(baseBytes, base.length());
            }
            var remaining = Math.subtractExact(encodedBytes, baseBytes);
            var names = new ArrayList<String>(count);
            for (var base : bases) {
                var addition = Math.toIntExact(Math.min(remaining, 65_535L - base.length()));
                names.add(base + "x".repeat(addition));
                remaining -= addition;
            }
            if (remaining != 0) {
                throw new IllegalArgumentException("R2Q filler modified-UTF target is infeasible");
            }
            return List.copyOf(names);
        }
    }
}
