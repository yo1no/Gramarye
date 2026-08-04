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

    /** Builds one complete 2,048-record physical plan with the named MAX+1 mutation. */
    static Plan buildNegative(P4E0R2QProfile.Counter target) {
        Objects.requireNonNull(target, "target");
        try {
            return createNegative(target);
        } catch (IOException exception) {
            throw new IllegalStateException("R2Q negative joint plan could not be measured", exception);
        }
    }

    /** Keeps 2,048 primary writers while reserving one tiny 2,049th selected record. */
    static Plan buildRelevantCompensation() {
        try {
            var positive = P4E0R2QFixturePlan.locked().jointRecords();
            var plans = new ArrayList<UnmeasuredRecord>(RECORD_COUNT);
            for (var index = 0; index < PEAK_RECORD_COUNT; index++) {
                var source = positive.records().get(index);
                plans.add(new UnmeasuredRecord(
                        source.code(), source.facts(), source.writer()));
            }
            var witness = new RecordFacts(22L, 1, 1, 1, 0, 0, 0, 0, 11, 1);
            var desired = subtractFacts(positive.aggregate(), witness);
            appendFillers(
                    plans,
                    subtractAggregate(desired, plans),
                    RECORD_COUNT - plans.size(),
                    "RELEVANT_COMPENSATION_FILLER_");
            requireExactAggregate(desired, plans, null);
            return measurePlans(plans);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "R2Q relevant-record compensation could not be measured", exception);
        }
    }

    private static Plan createNegative(P4E0R2QProfile.Counter target) throws IOException {
        return measurePlans(negativePlans(target), target);
    }

    static void requireNegativeShape(P4E0R2QProfile.Counter target) {
        try {
            negativePlans(Objects.requireNonNull(target, "target"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "R2Q negative shape is infeasible: " + target.name(), exception);
        }
    }

    /** Proves the corrected case-04 writer reaches its exact target at the old guard boundary. */
    static ConstructionDiagnostic diagnoseDecompressedPerFileConstruction()
            throws IOException {
        var target = P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE;
        var expected = Math.addExact(
                P4E0R2QProfile.locked().maximum(target), 1L);
        try {
            P4E0ResearchWireNbt.measure(
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    COMPRESSION_LEVEL,
                    MAXIMUM_PHYSICAL_BYTES,
                    MAXIMUM_DECOMPRESSED_BYTES,
                    targetWriter(target));
        } catch (P4E0ResearchWireNbt.ResearchLimitException exception) {
            if (!exception.coordinate().equals("decompressed_bytes")) {
                throw exception;
            }
            var output = exception.requireOutputDiagnostic();
            return new ConstructionDiagnostic(
                    ConstructionDiagnosticCode.COMPLETE_TARGET_REACHED_AT_MAX_PLUS_ONE,
                    output.countBeforeWrite(),
                    output.requestedWriteWidth(),
                    output.measurementCeiling(),
                    expected,
                    output.projectedCountAfterWrite());
        }
        throw new IOException("R2Q case-04 qualification ceiling was not reached");
    }

    private static List<UnmeasuredRecord> negativePlans(P4E0R2QProfile.Counter target) {
        var positive = P4E0R2QFixturePlan.locked().jointRecords();
        if (!isStructuralTarget(target)) {
            return positive.records().stream()
                    .map(record -> new UnmeasuredRecord(
                            record.code(), record.facts(), record.writer()))
                    .toList();
        }
        var plans = new ArrayList<UnmeasuredRecord>(RECORD_COUNT);
        var targetRecord = isPerFileTarget(target) ? targetRecord(target) : null;
        var replacementIndex = targetRecord == null ? -1 : targetReplacementIndex(target);
        for (var index = 0; index < PEAK_RECORD_COUNT; index++) {
            var source = positive.records().get(index);
            if (targetRecord != null && index == replacementIndex) {
                plans.add(targetRecord);
            } else {
                plans.add(new UnmeasuredRecord(
                        source.code(), source.facts(), source.writer()));
            }
        }
        var desired = desiredAggregate(positive.aggregate(), target);
        var remaining = subtractAggregate(desired, plans);
        var fillerCount = RECORD_COUNT - plans.size();
        if (target == P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE) {
            appendSparseArrayFillers(
                    plans, remaining, fillerCount, "NEGATIVE_FILLER_");
        } else {
            appendFillers(plans, remaining, fillerCount, "NEGATIVE_FILLER_");
        }
        requireExactAggregate(desired, plans, target);
        return List.copyOf(plans);
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
        appendFillers(plans, remaining, FILLER_COUNT, "FILLER_");

        requireExactAggregate(structural, plans);
        return measurePlans(plans);
    }

    private static Plan measurePlans(List<UnmeasuredRecord> plans) throws IOException {
        return measurePlans(plans, null);
    }

    private static Plan measurePlans(
            List<UnmeasuredRecord> plans, P4E0R2QProfile.Counter target)
            throws IOException {
        var measured = new ArrayList<MeasuredRecord>(RECORD_COUNT);
        for (var index = 0; index < plans.size(); index++) {
            var plan = plans.get(index);
            var constructionDecompressedCeiling =
                    target == P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE
                                    && index == targetReplacementIndex(target)
                            ? Math.addExact(MAXIMUM_DECOMPRESSED_BYTES, 1L)
                            : MAXIMUM_DECOMPRESSED_BYTES;
            var wire = P4E0ResearchWireNbt.measure(
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    COMPRESSION_LEVEL,
                    MAXIMUM_PHYSICAL_BYTES,
                    constructionDecompressedCeiling,
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
                    wire.physicalBytes(),
                    constructionDecompressedCeiling));
        }
        return new Plan(measured, aggregate(plans));
    }

    private static void appendFillers(
            List<UnmeasuredRecord> plans,
            RecordFacts remaining,
            int fillerCount,
            String prefix) {
        if (fillerCount <= 0) {
            throw new IllegalArgumentException("R2Q negative filler count is invalid");
        }
        var decompressed = distribute(remaining.decompressedBytes(), fillerCount);
        var containers = distribute(remaining.compoundContainers(), fillerCount);
        var fields = distribute(remaining.compoundFieldEntries(), fillerCount);
        var lists = distribute(remaining.listElements(), fillerCount);
        var bytes = distribute(remaining.byteArrayElements(), fillerCount);
        var ints = distribute(remaining.intArrayElements(), fillerCount);
        var longs = distribute(remaining.longArrayElements(), fillerCount);
        var utf = distribute(remaining.modifiedUtf8Bytes(), fillerCount);
        var scalars = distribute(remaining.scalarTags(), fillerCount);
        for (var index = 0; index < fillerCount; index++) {
            var facts = new RecordFacts(
                    decompressed[index], 2, containers[index], fields[index], lists[index],
                    bytes[index], ints[index], longs[index], utf[index], scalars[index]);
            var encoding = FillerEncoding.create(facts);
            plans.add(new UnmeasuredRecord(
                    prefix + String.format(Locale.ROOT, "%04d", index),
                    facts,
                    encoding::write));
        }
    }

    /**
     * Places the three aggregate array coordinates only on records that carry elements.
     *
     * <p>The isolated list-per-file witness deliberately spends nearly the entire list budget on
     * non-scalar nested elements. Its compensating scalar population therefore needs every
     * remaining compound-field slot. Emitting three named zero-length arrays in each of 2,039
     * fillers would invent 6,114 unnecessary fields and make the otherwise valid aggregate shape
     * impossible. This allocator preserves the exact aggregate vector while keeping each array
     * population below its independently locked per-file maximum.</p>
     */
    private static void appendSparseArrayFillers(
            List<UnmeasuredRecord> plans,
            RecordFacts remaining,
            int fillerCount,
            String prefix) {
        if (fillerCount <= 0) {
            throw new IllegalArgumentException("R2Q sparse filler count is invalid");
        }
        var profile = P4E0R2QProfile.locked();
        var containers = distribute(remaining.compoundContainers(), fillerCount);
        var lists = distribute(remaining.listElements(), fillerCount);
        var bytes = distributeSparse(
                remaining.byteArrayElements(),
                fillerCount,
                profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE));
        var ints = distributeSparse(
                remaining.intArrayElements(),
                fillerCount,
                profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE));
        var longs = distributeSparse(
                remaining.longArrayElements(),
                fillerCount,
                profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE));
        var utf = distribute(remaining.modifiedUtf8Bytes(), fillerCount);
        var scalars = distribute(remaining.scalarTags(), fillerCount);
        var fields = new long[fillerCount];
        var minimumBytes = new long[fillerCount];
        var maximumBytes = new long[fillerCount];
        var fieldTotal = 0L;
        for (var index = 0; index < fillerCount; index++) {
            var arrayFields = (bytes[index] > 0 ? 1L : 0L)
                    + (ints[index] > 0 ? 1L : 0L)
                    + (longs[index] > 0 ? 1L : 0L);
            if (lists[index] <= 0 || scalars[index] <= lists[index]) {
                throw new IllegalArgumentException("R2Q sparse filler scalar/list plan changed");
            }
            fields[index] = Math.addExact(
                    Math.addExact(containers[index], scalars[index] - lists[index]),
                    arrayFields);
            fieldTotal = Math.addExact(fieldTotal, fields[index]);
        }
        var extraFields = remaining.compoundFieldEntries() - fieldTotal;
        var maximumFieldsPerRecord = profile.maximum(
                P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE);
        if (extraFields < 0) {
            throw new IllegalArgumentException("R2Q sparse filler fields are insufficient");
        }
        var zeroArrayFields = distribute(extraFields, fillerCount);
        for (var index = 0; index < fillerCount; index++) {
            fields[index] = Math.addExact(fields[index], zeroArrayFields[index]);
            if (fields[index] > maximumFieldsPerRecord) {
                throw new IllegalArgumentException(
                        "R2Q sparse filler fields exceed record capacity");
            }
        }
        var minimumTotal = 0L;
        var maximumTotal = 0L;
        for (var index = 0; index < fillerCount; index++) {
            var facts = new RecordFacts(
                    0L, 2, containers[index], fields[index], lists[index],
                    bytes[index], ints[index], longs[index], utf[index], scalars[index]);
            minimumBytes[index] = FillerEncoding.minimumDecompressedBytes(facts);
            maximumBytes[index] = Math.addExact(
                    minimumBytes[index], FillerEncoding.maximumScalarPaddingBytes(facts));
            minimumTotal = Math.addExact(minimumTotal, minimumBytes[index]);
            maximumTotal = Math.addExact(maximumTotal, maximumBytes[index]);
        }
        if (remaining.decompressedBytes() < minimumTotal
                || remaining.decompressedBytes() > maximumTotal) {
            throw new IllegalArgumentException(
                    "R2Q sparse filler aggregate is infeasible: decompressed="
                            + remaining.decompressedBytes()
                            + "/[" + minimumTotal + "," + maximumTotal + "]");
        }
        var decompressed = minimumBytes.clone();
        var padding = remaining.decompressedBytes() - minimumTotal;
        for (var index = 0; index < fillerCount && padding > 0; index++) {
            var available = maximumBytes[index] - decompressed[index];
            var addition = Math.min(padding, available);
            decompressed[index] = Math.addExact(decompressed[index], addition);
            padding -= addition;
        }
        if (padding != 0L) {
            throw new IllegalArgumentException("R2Q sparse filler padding was not consumed");
        }
        for (var index = 0; index < fillerCount; index++) {
            var facts = new RecordFacts(
                    decompressed[index], 2, containers[index], fields[index], lists[index],
                    bytes[index], ints[index], longs[index], utf[index], scalars[index]);
            var encoding = FillerEncoding.create(facts);
            plans.add(new UnmeasuredRecord(
                    prefix + String.format(Locale.ROOT, "%04d", index),
                    facts,
                    encoding::write));
        }
    }

    private static boolean isStructuralTarget(P4E0R2QProfile.Counter target) {
        return switch (target) {
            case DECOMPRESSED_BYTES_PER_FILE,
                    CONTAINER_DEPTH_PER_FILE,
                    COMPOUND_CONTAINERS_PER_FILE,
                    COMPOUND_FIELD_ENTRIES_PER_FILE,
                    LIST_ELEMENTS_PER_FILE,
                    BYTE_ARRAY_ELEMENTS_PER_FILE,
                    INT_ARRAY_ELEMENTS_PER_FILE,
                    LONG_ARRAY_ELEMENTS_PER_FILE,
                    MODIFIED_UTF8_BYTES_PER_FILE,
                    SCALAR_TAGS_PER_FILE,
                    DECOMPRESSED_BYTES_TOTAL,
                    COMPOUND_CONTAINERS_TOTAL,
                    COMPOUND_FIELD_ENTRIES_TOTAL,
                    LIST_ELEMENTS_TOTAL,
                    BYTE_ARRAY_ELEMENTS_TOTAL,
                    INT_ARRAY_ELEMENTS_TOTAL,
                    LONG_ARRAY_ELEMENTS_TOTAL,
                    MODIFIED_UTF8_BYTES_TOTAL,
                    SCALAR_TAGS_TOTAL -> true;
            default -> false;
        };
    }

    private static boolean isPerFileTarget(P4E0R2QProfile.Counter target) {
        return switch (target) {
            case DECOMPRESSED_BYTES_PER_FILE,
                    CONTAINER_DEPTH_PER_FILE,
                    COMPOUND_CONTAINERS_PER_FILE,
                    COMPOUND_FIELD_ENTRIES_PER_FILE,
                    LIST_ELEMENTS_PER_FILE,
                    BYTE_ARRAY_ELEMENTS_PER_FILE,
                    INT_ARRAY_ELEMENTS_PER_FILE,
                    LONG_ARRAY_ELEMENTS_PER_FILE,
                    MODIFIED_UTF8_BYTES_PER_FILE,
                    SCALAR_TAGS_PER_FILE -> true;
            default -> false;
        };
    }

    private static RecordFacts desiredAggregate(
            RecordFacts baseline, P4E0R2QProfile.Counter target) {
        return new RecordFacts(
                baseline.decompressedBytes()
                        + (target == P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL ? 1 : 0),
                baseline.containerDepth(),
                baseline.compoundContainers()
                        + (target == P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL ? 1 : 0),
                baseline.compoundFieldEntries()
                        + (target == P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL ? 1 : 0),
                baseline.listElements()
                        + (target == P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL ? 1 : 0),
                baseline.byteArrayElements()
                        + (target == P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL ? 1 : 0),
                baseline.intArrayElements()
                        + (target == P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL ? 1 : 0),
                baseline.longArrayElements()
                        + (target == P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL ? 1 : 0),
                baseline.modifiedUtf8Bytes()
                        + (target == P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL ? 1 : 0),
                baseline.scalarTags()
                        + (target == P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL ? 1 : 0));
    }

    private static RecordFacts subtractFacts(RecordFacts left, RecordFacts right) {
        return new RecordFacts(
                Math.subtractExact(left.decompressedBytes(), right.decompressedBytes()),
                left.containerDepth(),
                Math.subtractExact(left.compoundContainers(), right.compoundContainers()),
                Math.subtractExact(left.compoundFieldEntries(), right.compoundFieldEntries()),
                Math.subtractExact(left.listElements(), right.listElements()),
                Math.subtractExact(left.byteArrayElements(), right.byteArrayElements()),
                Math.subtractExact(left.intArrayElements(), right.intArrayElements()),
                Math.subtractExact(left.longArrayElements(), right.longArrayElements()),
                Math.subtractExact(left.modifiedUtf8Bytes(), right.modifiedUtf8Bytes()),
                Math.subtractExact(left.scalarTags(), right.scalarTags()));
    }

    private static RecordFacts subtractAggregate(
            RecordFacts desired, List<UnmeasuredRecord> fixed) {
        var used = aggregate(fixed);
        return new RecordFacts(
                Math.subtractExact(desired.decompressedBytes(), used.decompressedBytes()),
                2,
                Math.subtractExact(desired.compoundContainers(), used.compoundContainers()),
                Math.subtractExact(
                        desired.compoundFieldEntries(), used.compoundFieldEntries()),
                Math.subtractExact(desired.listElements(), used.listElements()),
                Math.subtractExact(desired.byteArrayElements(), used.byteArrayElements()),
                Math.subtractExact(desired.intArrayElements(), used.intArrayElements()),
                Math.subtractExact(desired.longArrayElements(), used.longArrayElements()),
                Math.subtractExact(desired.modifiedUtf8Bytes(), used.modifiedUtf8Bytes()),
                Math.subtractExact(desired.scalarTags(), used.scalarTags()));
    }

    private static UnmeasuredRecord targetRecord(P4E0R2QProfile.Counter target) {
        return new UnmeasuredRecord(
                "NEGATIVE_TARGET_" + target.name(), targetFacts(target), targetWriter(target));
    }

    private static int targetReplacementIndex(P4E0R2QProfile.Counter target) {
        return switch (target) {
            case DECOMPRESSED_BYTES_PER_FILE, BYTE_ARRAY_ELEMENTS_PER_FILE -> 0;
            case CONTAINER_DEPTH_PER_FILE -> 2;
            case COMPOUND_CONTAINERS_PER_FILE -> 3;
            case COMPOUND_FIELD_ENTRIES_PER_FILE -> 4;
            case LIST_ELEMENTS_PER_FILE, SCALAR_TAGS_PER_FILE -> 5;
            case INT_ARRAY_ELEMENTS_PER_FILE -> 6;
            case LONG_ARRAY_ELEMENTS_PER_FILE -> 7;
            case MODIFIED_UTF8_BYTES_PER_FILE -> 8;
            default -> throw new IllegalArgumentException("counter has no peak replacement");
        };
    }

    private static RecordFacts targetFacts(P4E0R2QProfile.Counter target) {
        return switch (target) {
            case DECOMPRESSED_BYTES_PER_FILE -> new RecordFacts(
                    268_435_457L, 1, 1, 4, 0, 268_435_384L, 4, 0, 27, 2);
            case CONTAINER_DEPTH_PER_FILE -> new RecordFacts(
                    2_581L, 513, 513, 513, 0, 0, 0, 0, 522, 1);
            case COMPOUND_CONTAINERS_PER_FILE -> new RecordFacts(
                    1_058L, 3, 1_025, 3, 1_023, 0, 0, 0, 12, 1);
            case COMPOUND_FIELD_ENTRIES_PER_FILE -> new RecordFacts(
                    655_390L, 1, 1, 65_538, 0, 0, 0, 0, 393_228L, 65_537);
            case LIST_ELEMENTS_PER_FILE -> new RecordFacts(
                    323_625L, 5, 1_024, 3, 65_537, 0, 0, 0, 13, 3);
            case BYTE_ARRAY_ELEMENTS_PER_FILE -> new RecordFacts(
                    268_435_456L, 1, 1, 3, 0, 268_435_385L, 4, 0, 30, 1);
            case INT_ARRAY_ELEMENTS_PER_FILE -> new RecordFacts(
                    262_178L, 1, 1, 2, 0, 0, 65_537, 0, 12, 1);
            case LONG_ARRAY_ELEMENTS_PER_FILE -> new RecordFacts(
                    524_326L, 1, 1, 2, 0, 0, 0, 65_537, 12, 1);
            case MODIFIED_UTF8_BYTES_PER_FILE -> new RecordFacts(
                    67_112_824L, 1, 1, 1_025, 0, 0, 0, 0, 67_107_693L, 1_025);
            case SCALAR_TAGS_PER_FILE -> new RecordFacts(
                    65_572L, 2, 1, 3, 65_536, 0, 0, 0, 13, 65_538);
            default -> throw new IllegalArgumentException("counter has no target record");
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter targetWriter(
            P4E0R2QProfile.Counter target) {
        return switch (target) {
            case DECOMPRESSED_BYTES_PER_FILE -> output -> {
                startAndDataVersion(output);
                writeUuid(output);
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF("research_pay");
                output.writeInt(268_435_384);
                writeRepeated(output, 268_435_384, 0x5a);
                output.writeByte(Tag.TAG_SHORT);
                output.writeUTF("");
                output.writeShort(1);
                output.writeByte(Tag.TAG_END);
            };
            case CONTAINER_DEPTH_PER_FILE -> output -> {
                startAndDataVersion(output);
                for (var level = 1; level < 512; level++) {
                    output.writeByte(Tag.TAG_COMPOUND);
                    output.writeUTF("d");
                }
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("");
                for (var level = 0; level < 513; level++) {
                    output.writeByte(Tag.TAG_END);
                }
            };
            case COMPOUND_CONTAINERS_PER_FILE -> output -> {
                startAndDataVersion(output);
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF("c");
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeInt(1_023);
                for (var index = 0; index < 1_023; index++) {
                    output.writeByte(Tag.TAG_END);
                }
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("");
                output.writeByte(Tag.TAG_END);
                output.writeByte(Tag.TAG_END);
            };
            case COMPOUND_FIELD_ENTRIES_PER_FILE -> output -> {
                startAndDataVersion(output);
                for (var index = 0; index < 65_536; index++) {
                    output.writeByte(Tag.TAG_BYTE);
                    output.writeUTF(String.format(Locale.ROOT, "f%05d", index));
                    output.writeByte(index);
                }
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF("x");
                output.writeInt(0);
                output.writeByte(Tag.TAG_END);
            };
            case LIST_ELEMENTS_PER_FILE -> output -> {
                startAndDataVersion(output);
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF("c");
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeInt(1_023);
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF("l");
                output.writeByte(Tag.TAG_LIST);
                output.writeInt(64_512);
                output.writeByte(Tag.TAG_BYTE);
                output.writeInt(2);
                output.writeByte(0x51);
                output.writeByte(0x52);
                for (var index = 1; index < 64_512; index++) {
                    output.writeByte(Tag.TAG_END);
                    output.writeInt(0);
                }
                output.writeByte(Tag.TAG_END);
                for (var index = 1; index < 1_023; index++) {
                    output.writeByte(Tag.TAG_END);
                }
                output.writeByte(Tag.TAG_END);
            };
            case BYTE_ARRAY_ELEMENTS_PER_FILE -> output -> {
                startAndDataVersion(output);
                writeUuid(output);
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF("research_payloa");
                output.writeInt(268_435_385);
                writeRepeated(output, 268_435_385, 0x5a);
                output.writeByte(Tag.TAG_END);
            };
            case INT_ARRAY_ELEMENTS_PER_FILE -> output -> {
                startAndDataVersion(output);
                output.writeByte(Tag.TAG_INT_ARRAY);
                output.writeUTF("i");
                output.writeInt(65_537);
                for (var index = 0; index < 65_537; index++) {
                    output.writeInt(index);
                }
                output.writeByte(Tag.TAG_END);
            };
            case LONG_ARRAY_ELEMENTS_PER_FILE -> output -> {
                startAndDataVersion(output);
                output.writeByte(Tag.TAG_LONG_ARRAY);
                output.writeUTF("g");
                output.writeInt(65_537);
                for (var index = 0; index < 65_537; index++) {
                    output.writeLong(0x5034_4530_5232_5100L ^ index);
                }
                output.writeByte(Tag.TAG_END);
            };
            case MODIFIED_UTF8_BYTES_PER_FILE -> output -> {
                startAndDataVersion(output);
                var full = "u".repeat(65_535);
                var tail = "u".repeat(59_233);
                for (var index = 0; index < 1_024; index++) {
                    output.writeByte(Tag.TAG_STRING);
                    output.writeUTF(String.format(Locale.ROOT, "u%05d", index));
                    output.writeUTF(index == 1_023 ? tail : full);
                }
                output.writeByte(Tag.TAG_END);
            };
            case SCALAR_TAGS_PER_FILE -> output -> {
                startAndDataVersion(output);
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF("l");
                output.writeByte(Tag.TAG_BYTE);
                output.writeInt(65_536);
                writeRepeated(output, 65_536, 0x5a);
                output.writeByte(Tag.TAG_BYTE);
                output.writeUTF("x");
                output.writeByte(1);
                output.writeByte(Tag.TAG_END);
            };
            default -> throw new IllegalArgumentException("counter has no target writer");
        };
    }

    private static void startAndDataVersion(DataOutput output) throws IOException {
        P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
        writeDataVersion(output);
    }

    private static void writeUuid(DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF("UUID");
        output.writeInt(4);
        output.writeInt(0x5034_4530);
        output.writeInt(0x5232_5100);
        output.writeInt(0);
        output.writeInt(1);
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

    private static long[] distributeSparse(long total, int records, long maximumPerRecord) {
        if (total < 0 || records <= 0 || maximumPerRecord <= 0) {
            throw new IllegalArgumentException("invalid R2Q sparse distribution");
        }
        var values = new long[records];
        var remaining = total;
        for (var index = 0; index < records && remaining > 0; index++) {
            values[index] = Math.min(remaining, maximumPerRecord);
            remaining -= values[index];
        }
        if (remaining != 0L) {
            throw new IllegalArgumentException("R2Q sparse distribution exceeds record capacity");
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

    private static void requireExactAggregate(
            RecordFacts desired,
            List<UnmeasuredRecord> records,
            P4E0R2QProfile.Counter target) {
        if (records.size() != RECORD_COUNT) {
            throw new IllegalArgumentException("R2Q negative joint record count changed");
        }
        var actual = aggregate(records);
        if (actual.decompressedBytes() != desired.decompressedBytes()
                || actual.compoundContainers() != desired.compoundContainers()
                || actual.compoundFieldEntries() != desired.compoundFieldEntries()
                || actual.listElements() != desired.listElements()
                || actual.byteArrayElements() != desired.byteArrayElements()
                || actual.intArrayElements() != desired.intArrayElements()
                || actual.longArrayElements() != desired.longArrayElements()
                || actual.modifiedUtf8Bytes() != desired.modifiedUtf8Bytes()
                || actual.scalarTags() != desired.scalarTags()) {
            throw new IllegalArgumentException("R2Q negative structural aggregate changed");
        }
        var profile = P4E0R2QProfile.locked();
        for (var counter : List.of(
                P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE,
                P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE,
                P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE,
                P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE,
                P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE)) {
            var observed = records.stream().map(UnmeasuredRecord::facts)
                    .mapToLong(facts -> fact(facts, counter)).max().orElseThrow();
            var expected = profile.maximum(counter) + (target == counter ? 1L : 0L);
            if (observed != expected) {
                throw new IllegalArgumentException(
                        "R2Q negative per-file witness changed: " + counter.slug());
            }
        }
    }

    private static long fact(RecordFacts facts, P4E0R2QProfile.Counter counter) {
        return switch (counter) {
            case DECOMPRESSED_BYTES_PER_FILE -> facts.decompressedBytes();
            case CONTAINER_DEPTH_PER_FILE -> facts.containerDepth();
            case COMPOUND_CONTAINERS_PER_FILE -> facts.compoundContainers();
            case COMPOUND_FIELD_ENTRIES_PER_FILE -> facts.compoundFieldEntries();
            case LIST_ELEMENTS_PER_FILE -> facts.listElements();
            case BYTE_ARRAY_ELEMENTS_PER_FILE -> facts.byteArrayElements();
            case INT_ARRAY_ELEMENTS_PER_FILE -> facts.intArrayElements();
            case LONG_ARRAY_ELEMENTS_PER_FILE -> facts.longArrayElements();
            case MODIFIED_UTF8_BYTES_PER_FILE -> facts.modifiedUtf8Bytes();
            case SCALAR_TAGS_PER_FILE -> facts.scalarTags();
            default -> throw new IllegalArgumentException("counter is not per-file structural");
        };
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
            var relaxed = records.stream()
                    .filter(record -> record.constructionDecompressedCeiling()
                            == MAXIMUM_DECOMPRESSED_BYTES + 1L)
                    .count();
            if (relaxed > 1L) {
                throw new IllegalArgumentException(
                        "R2Q joint plan relaxed more than one construction record");
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
                    record.constructionDecompressedCeiling(),
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
                    record.constructionDecompressedCeiling(),
                    record.writer());
        }
    }

    record MeasuredRecord(
            int index,
            String code,
            RecordFacts facts,
            P4E0ResearchWireNbt.PayloadWriter writer,
            long canonicalPhysicalBytes,
            long constructionDecompressedCeiling) {
        MeasuredRecord {
            if (index < 0 || code == null || code.isBlank()
                    || facts == null || writer == null
                    || canonicalPhysicalBytes <= 0
                    || canonicalPhysicalBytes > MAXIMUM_PHYSICAL_BYTES
                    || (constructionDecompressedCeiling != MAXIMUM_DECOMPRESSED_BYTES
                            && constructionDecompressedCeiling
                                    != MAXIMUM_DECOMPRESSED_BYTES + 1L)
                    || (constructionDecompressedCeiling
                                    == MAXIMUM_DECOMPRESSED_BYTES + 1L
                            && facts.decompressedBytes()
                                    != MAXIMUM_DECOMPRESSED_BYTES + 1L)) {
                throw new IllegalArgumentException("invalid measured R2Q joint record");
            }
        }
    }

    enum ConstructionDiagnosticCode {
        COMPLETE_TARGET_REACHED_AT_MAX_PLUS_ONE
    }

    record ConstructionDiagnostic(
            ConstructionDiagnosticCode code,
            long countBeforeWrite,
            long requestedWriteWidth,
            long measurementCeiling,
            long expectedObservedValue,
            long projectedCountAfterWrite) {
        ConstructionDiagnostic {
            Objects.requireNonNull(code, "code");
            if (countBeforeWrite < 0L
                    || requestedWriteWidth <= 0L
                    || measurementCeiling != MAXIMUM_DECOMPRESSED_BYTES
                    || expectedObservedValue != MAXIMUM_DECOMPRESSED_BYTES + 1L
                    || projectedCountAfterWrite != expectedObservedValue
                    || projectedCountAfterWrite
                            != Math.addExact(countBeforeWrite, requestedWriteWidth)) {
                throw new IllegalArgumentException("invalid R2Q construction diagnostic");
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
            var arrayFields = (facts.byteArrayElements() > 0 ? 1 : 0)
                    + (facts.intArrayElements() > 0 ? 1 : 0)
                    + (facts.longArrayElements() > 0 ? 1 : 0);
            var usedFields = Math.addExact(
                    1,
                    Math.addExact(
                            emptyCompounds,
                            Math.addExact(
                                    facts.listElements() > 0 ? 1 : 0,
                                    Math.addExact(arrayFields, scalarFields))));
            var zeroArrays = Math.toIntExact(facts.compoundFieldEntries() - usedFields);
            if (emptyCompounds < 0 || scalarFields < 0 || zeroArrays < 0) {
                throw new IllegalArgumentException("R2Q filler structural counts are infeasible");
            }
            var baseBytes = minimumDecompressedBytes(facts);
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

        private static long minimumDecompressedBytes(RecordFacts facts) {
            var emptyCompounds = Math.toIntExact(facts.compoundContainers() - 1L);
            var scalarFields = Math.toIntExact(
                    facts.scalarTags() - 1L - facts.listElements());
            var arrayFields = (facts.byteArrayElements() > 0 ? 1 : 0)
                    + (facts.intArrayElements() > 0 ? 1 : 0)
                    + (facts.longArrayElements() > 0 ? 1 : 0);
            var usedFields = Math.addExact(
                    1,
                    Math.addExact(
                            emptyCompounds,
                            Math.addExact(
                                    facts.listElements() > 0 ? 1 : 0,
                                    Math.addExact(arrayFields, scalarFields))));
            var zeroArrays = Math.toIntExact(facts.compoundFieldEntries() - usedFields);
            if (emptyCompounds < 0 || scalarFields < 0 || zeroArrays < 0) {
                throw new IllegalArgumentException("R2Q filler structural counts are infeasible");
            }
            var nameCount = Math.toIntExact(facts.compoundFieldEntries() - 1L);
            var encodedNameBytes = Math.subtractExact(facts.modifiedUtf8Bytes(), 11L);
            if (nameCount <= 0
                    || encodedNameBytes < Math.multiplyExact(6L, nameCount)
                    || encodedNameBytes > Math.multiplyExact(65_535L, nameCount)) {
                throw new IllegalArgumentException("R2Q filler modified-UTF target is infeasible");
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
            if (facts.byteArrayElements() > 0) {
                baseBytes = Math.addExact(baseBytes, 4L + facts.byteArrayElements());
            }
            if (facts.intArrayElements() > 0) {
                baseBytes = Math.addExact(
                        baseBytes, 4L + Math.multiplyExact(4L, facts.intArrayElements()));
            }
            if (facts.longArrayElements() > 0) {
                baseBytes = Math.addExact(
                        baseBytes, 4L + Math.multiplyExact(8L, facts.longArrayElements()));
            }
            baseBytes = Math.addExact(baseBytes, scalarFields);
            return Math.addExact(baseBytes, Math.multiplyExact(4L, zeroArrays));
        }

        private static long maximumScalarPaddingBytes(RecordFacts facts) {
            var scalarFields = Math.subtractExact(
                    Math.subtractExact(facts.scalarTags(), 1L), facts.listElements());
            if (scalarFields < 0) {
                throw new IllegalArgumentException("R2Q filler scalar padding is infeasible");
            }
            return Math.multiplyExact(7L, scalarFields);
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
            if (facts.byteArrayElements() > 0) {
                output.writeByte(Tag.TAG_BYTE_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(Math.toIntExact(facts.byteArrayElements()));
                writeRepeated(output, Math.toIntExact(facts.byteArrayElements()), 0x5a);
            }
            if (facts.intArrayElements() > 0) {
                output.writeByte(Tag.TAG_INT_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(Math.toIntExact(facts.intArrayElements()));
                for (var index = 0; index < facts.intArrayElements(); index++) {
                    output.writeInt(index);
                }
            }
            if (facts.longArrayElements() > 0) {
                output.writeByte(Tag.TAG_LONG_ARRAY);
                output.writeUTF(names.get(cursor++));
                output.writeInt(Math.toIntExact(facts.longArrayElements()));
                for (var index = 0; index < facts.longArrayElements(); index++) {
                    output.writeLong(0x5034_4530_5232_5100L ^ index);
                }
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
