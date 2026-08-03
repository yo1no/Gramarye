package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.P4E0R2QStoreJournalFixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Isolated proof that every R2Q counter negative is a derived physical mutation plan. */
final class P4E0R2QNegativeFixtureTest {
    private static final long MICRO_WIRE_LIMIT = 33_554_432L;

    @TempDir
    Path temporary;

    @Test
    void everyCounterNegativeDerivesItsOnlyOverrunFromTypedDeltasAndCompensation() {
        var plan = P4E0R2QCasePlan.standard();
        var baseline = P4E0R2QFixturePlan.locked().counters();
        var maxima = P4E0R2QProfile.locked().candidateValues();
        var seen = EnumSet.noneOf(P4E0R2QProfile.Counter.class);

        for (var spec : plan.cases()) {
            if (spec.kind() != P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
                continue;
            }
            var target = spec.targetCounter().orElseThrow();
            seen.add(target);
            var fixture = P4E0R2QFixturePlan.negativeFixture(spec);
            var derivation = fixture.derivation();
            var physicalByCounter = deltasByCounter(derivation);
            var compensationByCounter = compensationsByCounter(derivation);
            var coupled = EnumSet.copyOf(physicalByCounter.keySet());
            coupled.remove(target);

            assertAll(
                    () -> assertEquals(target, derivation.targetCounter()),
                    () -> assertEquals(spec.mutationKind(), derivation.mutationKind()),
                    () -> assertNotNull(derivation.mechanism()),
                    () -> assertEquals(1L, physicalByCounter.get(target).amount()),
                    () -> assertEquals(coupled, spec.coupledCounters()),
                    () -> assertEquals(coupled, fixture.proof().coupledCounters()),
                    () -> assertEquals(coupled.size(), compensationByCounter.size()),
                    () -> assertEquals(
                            Math.addExact(maxima.value(target), 1L),
                            fixture.observedCounters().value(target)),
                    () -> assertEquals(
                            target,
                            plan.preflightNegative(spec, fixture).targetCounter()));

            for (var counter : coupled) {
                var physical = physicalByCounter.get(counter);
                var compensation = compensationByCounter.get(counter);
                assertAll(
                        () -> assertEquals(
                                Math.negateExact(physical.amount()),
                                compensation.amount(),
                                () -> "compensation amount: " + target + " / " + counter),
                        () -> assertEquals(
                                physical.amount() > 0
                                        ? P4E0R2QFixturePlan.CompensationPlacement.BEFORE_TARGET
                                        : P4E0R2QFixturePlan.CompensationPlacement.AFTER_TARGET,
                                compensation.placement(),
                                () -> "compensation order: " + target + " / " + counter));
            }
            for (var counter : P4E0R2QProfile.Counter.values()) {
                var expected = counter == target
                        ? Math.addExact(baseline.value(counter), 1L)
                        : baseline.value(counter);
                assertEquals(
                        expected,
                        fixture.observedCounters().value(counter),
                        () -> "derived final counter: " + target + " / " + counter);
                if (counter != target) {
                    assertTrue(
                            derivation.beforeTargetCounters().value(counter)
                                    <= maxima.value(counter),
                            () -> "before-target second overrun: " + target + " / " + counter);
                    assertTrue(
                            derivation.afterTargetCounters().value(counter)
                                    <= maxima.value(counter),
                            () -> "after-target second overrun: " + target + " / " + counter);
                }
            }
        }
        assertEquals(EnumSet.allOf(P4E0R2QProfile.Counter.class), seen);
    }

    @Test
    void everyCounterNegativeExecutesItsBoundWriterAndInverseCompensations()
            throws Exception {
        var seen = EnumSet.noneOf(P4E0R2QProfile.Counter.class);
        var maxima = P4E0R2QProfile.locked().candidateValues();
        for (var spec : P4E0R2QCasePlan.standard().cases()) {
            if (spec.kind() != P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
                continue;
            }
            var target = spec.targetCounter().orElseThrow();
            var fixture = P4E0R2QFixturePlan.negativeFixture(spec);
            var execution = executePhysical(
                    fixture,
                    temporary.resolve(String.format(
                            "%02d-%s", seen.size(), target.slug())));
            seen.add(target);

            assertAll(
                    () -> assertEquals(target, execution.targetCounter()),
                    () -> assertEquals(
                            fixture.physicalBinding().fixtureKind(), execution.fixtureKind()),
                    () -> assertEquals(
                            fixture.physicalBinding().mutationPlacement(),
                            execution.mutationPlacement()),
                    () -> assertEquals(1L, execution.forwardDeltas().get(target)),
                    () -> assertTrue(execution.physicalArtifacts() > 0),
                    () -> assertEquals(
                            Math.addExact(maxima.value(target), 1L),
                            fixture.observedCounters().value(target)));

            var physicalDeltas = deltasByCounter(fixture.derivation());
            for (var delta : physicalDeltas.values()) {
                assertEquals(
                        delta.amount(),
                        execution.forwardDeltas().get(delta.counter()),
                        () -> "forward writer delta: " + target + " / " + delta.counter());
            }
            for (var binding : fixture.physicalBinding().compensations()) {
                assertEquals(
                        binding.expectedDelta(),
                        execution.compensationDeltas().get(binding.counter()),
                        () -> "inverse writer delta: " + target + " / " + binding.counter());
            }
            for (var counter : P4E0R2QProfile.Counter.values()) {
                if (counter != target) {
                    assertTrue(
                            fixture.observedCounters().value(counter) <= maxima.value(counter),
                            () -> "second profile overrun: " + target + " / " + counter);
                }
            }
        }
        assertEquals(EnumSet.allOf(P4E0R2QProfile.Counter.class), seen);
    }

    @Test
    void canonicalSelectedRecordSideEffectsComeFromARealWireWitness() throws Exception {
        var root = new CompoundTag();
        root.putInt("DataVersion", 3_955);
        var observed = observe(root);
        var derivation = negativeFor(P4E0R2QProfile.Counter.RELEVANT_RECORDS).derivation();
        var deltas = deltasByCounter(derivation);

        assertAll(
                () -> assertEquals(
                        P4E0R2QFixturePlan.MutationMechanism
                                .RESELECT_IRRELEVANT_AS_CANONICAL_PRIMARY,
                        derivation.mechanism()),
                () -> assertEquals(
                        observed.wire().physicalBytes(),
                        deltas.get(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL).amount()),
                () -> assertEquals(
                        observed.wire().decompressedBytes(),
                        deltas.get(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL).amount()),
                () -> assertEquals(
                        observed.metrics().compoundCount(),
                        deltas.get(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL).amount()),
                () -> assertEquals(
                        observed.metrics().compoundEntryCount(),
                        deltas.get(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL).amount()),
                () -> assertEquals(
                        observed.metrics().modifiedUtf8Bytes(),
                        deltas.get(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL).amount()),
                () -> assertEquals(
                        observed.metrics().scalarTagCount(),
                        deltas.get(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL).amount()));
    }

    @Test
    void structuralMutationDeltasHaveReducedPhysicalNbtAndGzipWitnesses() throws Exception {
        var byteValue = rootWithByte("v");
        var shortValue = new CompoundTag();
        shortValue.putShort("v", (short) 1);
        assertWireAndMetricDelta(byteValue, shortValue, 1L, Metric.NONE, 0L);

        var empty = new CompoundTag();
        var nested = new CompoundTag();
        nested.put("", new CompoundTag());
        assertWireAndMetricDelta(empty, nested, 4L, Metric.COMPOUNDS, 1L);
        assertEquals(1L, observe(nested).metrics().compoundEntryCount());
        assertEquals(2L, observe(nested).metrics().maxContainerDepth());

        var field = new CompoundTag();
        field.putByteArray("x", new byte[0]);
        assertWireAndMetricDelta(empty, field, 8L, Metric.FIELDS, 1L);
        assertEquals(1L, observe(field).metrics().modifiedUtf8Bytes());

        assertWireAndMetricDelta(
                rootWithByteList(1), rootWithByteList(2), 1L, Metric.LIST_ELEMENTS, 1L);
        assertWireAndMetricDelta(
                rootWithByteArray(1), rootWithByteArray(2), 1L, Metric.BYTE_ARRAY_ELEMENTS, 1L);
        assertWireAndMetricDelta(
                rootWithIntArray(1), rootWithIntArray(2), 4L, Metric.INT_ARRAY_ELEMENTS, 1L);
        assertWireAndMetricDelta(
                rootWithLongArray(1), rootWithLongArray(2), 8L, Metric.LONG_ARRAY_ELEMENTS, 1L);

        var shortUtf = new CompoundTag();
        shortUtf.putString("x", "a");
        var longUtf = new CompoundTag();
        longUtf.putString("x", "aa");
        assertWireAndMetricDelta(shortUtf, longUtf, 1L, Metric.MODIFIED_UTF8, 1L);

        var zeroArray = new CompoundTag();
        zeroArray.putByteArray("x", new byte[0]);
        var scalar = rootWithByte("x");
        assertWireAndMetricDelta(zeroArray, scalar, -3L, Metric.SCALARS, 1L);

        var headerOne = measureHeader(1);
        var headerTwo = measureHeader(2);
        assertAll(
                () -> assertEquals(1L, headerTwo.physicalBytes() - headerOne.physicalBytes()),
                () -> assertEquals(headerOne.decompressedBytes(), headerTwo.decompressedBytes()));
    }

    @Test
    void attachmentAdmissionUsesAProductionReadyWitnessAndAccountsForItsRoots()
            throws Exception {
        var fixture = P4E0ResearchAttachmentFixtures.readyRootMax(false);
        var tag = fixture.serializedTag();
        var observed = observe(tag);
        var derivation = negativeFor(P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS)
                .derivation();
        var deltas = deltasByCounter(derivation);

        assertAll(
                () -> assertEquals(
                        P4E0R2QFixturePlan.MutationMechanism.ADMIT_PRODUCTION_READY_ATTACHMENT,
                        derivation.mechanism()),
                () -> assertEquals(1L,
                        deltas.get(P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS).amount()),
                () -> assertEquals(
                        observed.wire().physicalBytes(),
                        deltas.get(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL).amount()),
                () -> assertEquals(
                        observed.wire().decompressedBytes(),
                        deltas.get(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL).amount()),
                () -> assertEquals(
                        observed.metrics().compoundCount(),
                        deltas.get(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL).amount()),
                () -> assertEquals(
                        observed.metrics().listElementCount(),
                        deltas.get(P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL).amount()),
                () -> assertEquals(
                        fixture.projectedRoots().orElseThrow().size(),
                        deltas.get(P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS).amount()));
    }

    private static P4E0R2QFixturePlan.NegativeFixture negativeFor(
            P4E0R2QProfile.Counter target) {
        var spec = P4E0R2QCasePlan.standard().cases().stream()
                .filter(candidate -> candidate.targetCounter().orElse(null) == target)
                .findFirst()
                .orElseThrow();
        return P4E0R2QFixturePlan.negativeFixture(spec);
    }

    private static PhysicalExecution executePhysical(
            P4E0R2QFixturePlan.NegativeFixture fixture, Path root) throws Exception {
        Files.createDirectories(root);
        var binding = fixture.physicalBinding();
        return switch (binding.fixtureKind()) {
            case FILESYSTEM_DIRECTORY -> executeDirectory(fixture, root);
            case FILESYSTEM_SOURCE_SELECTION -> executeSourceSelection(fixture, root);
            case STRICT_SINGLE_MEMBER_GZIP -> executeHeaderPair(fixture, root);
            case STREAMING_UNNAMED_COMPOUND -> executeTagPair(fixture, root);
            case PRODUCTION_READY_ADMISSION -> executeAdmission(fixture, root);
            case PRODUCTION_ROOT_PROJECTION -> executeRootProjection(fixture);
        };
    }

    private static PhysicalExecution executeDirectory(
            P4E0R2QFixturePlan.NegativeFixture fixture, Path root) throws Exception {
        Files.createFile(root.resolve("baseline-entry"));
        var before = directoryEntries(root);
        Files.createFile(root.resolve("added-irrelevant-entry"));
        var after = directoryEntries(root);
        return execution(fixture, deltas(
                P4E0R2QProfile.Counter.DIRECTORY_ENTRIES, after - before), Map.of(), 2);
    }

    private static PhysicalExecution executeSourceSelection(
            P4E0R2QFixturePlan.NegativeFixture fixture, Path root) throws Exception {
        var playerId = new UUID(0x5034_4530_5232_5100L, 1L);
        var irrelevant = root.resolve("ignored-fixture.dat");
        var selected = root.resolve(playerId + ".dat");
        var tag = canonicalDataVersion();
        write(irrelevant, tag, P4E0ResearchWireNbt.HeaderOptions.canonical());
        var afterFacts = scan(irrelevant);
        Files.move(irrelevant, selected);
        var selection = P4E0ResearchFixtureFactory.selectResearchSource(root, playerId);
        assertEquals(P4E0ResearchFixtureFactory.ResearchPhysicalSource.PRIMARY,
                selection.source());
        var selectedFacts = scan(selected);
        assertEquals(afterFacts, selectedFacts);
        var forward = absoluteSelectedRecord(selectedFacts);
        Files.delete(selected);
        assertTrue(Files.notExists(selected));
        return execution(fixture, forward, inverse(forward, fixture), 2);
    }

    private static PhysicalExecution executeHeaderPair(
            P4E0R2QFixturePlan.NegativeFixture fixture, Path root) throws Exception {
        var tag = canonicalDataVersion();
        return executePair(
                fixture,
                root,
                tag,
                tag,
                P4E0ResearchWireNbt.HeaderOptions.fileName(1),
                P4E0ResearchWireNbt.HeaderOptions.fileName(2));
    }

    private static PhysicalExecution executeTagPair(
            P4E0R2QFixturePlan.NegativeFixture fixture, Path root) throws Exception {
        var pair = tagPair(fixture.proof().targetCounter());
        return executePair(
                fixture,
                root,
                pair.before(),
                pair.after(),
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                P4E0ResearchWireNbt.HeaderOptions.canonical());
    }

    private static PhysicalExecution executePair(
            P4E0R2QFixturePlan.NegativeFixture fixture,
            Path root,
            CompoundTag before,
            CompoundTag after,
            P4E0ResearchWireNbt.HeaderOptions beforeHeader,
            P4E0ResearchWireNbt.HeaderOptions afterHeader) throws Exception {
        var beforeFacts = writeAndScan(root.resolve("target-before.dat"), before, beforeHeader);
        var afterFacts = writeAndScan(root.resolve("target-after.dat"), after, afterHeader);
        var compensationSource = writeAndScan(
                root.resolve("compensation-source.dat"), after, afterHeader);
        var compensationResult = writeAndScan(
                root.resolve("compensation-result.dat"), before, beforeHeader);
        assertEquals(afterFacts, compensationSource);
        assertEquals(beforeFacts, compensationResult);
        var forward = coordinateDeltas(beforeFacts, afterFacts);
        var compensation = coordinateDeltas(compensationSource, compensationResult);
        return execution(fixture, forward, compensation, 4);
    }

    private static PhysicalExecution executeAdmission(
            P4E0R2QFixturePlan.NegativeFixture fixture, Path root) throws Exception {
        var admitted = P4E0ResearchAttachmentFixtures.readyRootMax(false);
        var tag = admitted.serializedTag();
        var facts = writeAndScan(
                root.resolve("admitted-ready.dat"),
                (CompoundTag) tag,
                P4E0ResearchWireNbt.HeaderOptions.canonical());
        var compensationFacts = writeAndScan(
                root.resolve("compensation-ready.dat"),
                (CompoundTag) tag.copy(),
                P4E0ResearchWireNbt.HeaderOptions.canonical());
        assertEquals(facts, compensationFacts);
        var forward = absoluteCoordinates(facts);
        forward.put(P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS, 1L);
        forward.put(
                P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS,
                (long) admitted.projectedRoots().orElseThrow().size());
        Files.delete(root.resolve("compensation-ready.dat"));
        return execution(fixture, forward, inverse(forward, fixture), 2);
    }

    private static PhysicalExecution executeRootProjection(
            P4E0R2QFixturePlan.NegativeFixture fixture) {
        var facts = P4E0R2QStoreJournalFixtures.buildExact().facts();
        var forward = deltas(
                P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS,
                Math.subtractExact(facts.overRawRoots(), facts.exactRawRoots()));
        assertTrue(facts.exactRootsComplete());
        assertTrue(facts.overRootsRejected());
        return execution(fixture, forward, Map.of(), 2);
    }

    private static PhysicalExecution execution(
            P4E0R2QFixturePlan.NegativeFixture fixture,
            Map<P4E0R2QProfile.Counter, Long> forward,
            Map<P4E0R2QProfile.Counter, Long> compensation,
            int artifacts) {
        return new PhysicalExecution(
                fixture.proof().targetCounter(),
                fixture.physicalBinding().fixtureKind(),
                fixture.physicalBinding().mutationPlacement(),
                forward,
                compensation,
                artifacts);
    }

    private static Map<P4E0R2QProfile.Counter, Long> inverse(
            Map<P4E0R2QProfile.Counter, Long> forward,
            P4E0R2QFixturePlan.NegativeFixture fixture) {
        var result = new EnumMap<P4E0R2QProfile.Counter, Long>(
                P4E0R2QProfile.Counter.class);
        for (var compensation : fixture.physicalBinding().compensations()) {
            result.put(
                    compensation.counter(),
                    Math.negateExact(forward.get(compensation.counter())));
        }
        return result;
    }

    private static TagPair tagPair(P4E0R2QProfile.Counter counter) {
        return switch (counter) {
            case DECOMPRESSED_BYTES_PER_FILE, DECOMPRESSED_BYTES_TOTAL -> {
                var after = new CompoundTag();
                after.putShort("v", (short) 1);
                yield new TagPair(rootWithByte("v"), after);
            }
            case CONTAINER_DEPTH_PER_FILE -> {
                var after = new CompoundTag();
                after.put("", new CompoundTag());
                yield new TagPair(new CompoundTag(), after);
            }
            case COMPOUND_CONTAINERS_PER_FILE, COMPOUND_CONTAINERS_TOTAL -> {
                var after = new CompoundTag();
                after.put("", new CompoundTag());
                yield new TagPair(new CompoundTag(), after);
            }
            case COMPOUND_FIELD_ENTRIES_PER_FILE, COMPOUND_FIELD_ENTRIES_TOTAL -> {
                var after = new CompoundTag();
                after.putByteArray("x", new byte[0]);
                yield new TagPair(new CompoundTag(), after);
            }
            case LIST_ELEMENTS_PER_FILE, LIST_ELEMENTS_TOTAL ->
                    new TagPair(rootWithByteList(1), rootWithByteList(2));
            case BYTE_ARRAY_ELEMENTS_PER_FILE, BYTE_ARRAY_ELEMENTS_TOTAL ->
                    new TagPair(rootWithByteArray(1), rootWithByteArray(2));
            case INT_ARRAY_ELEMENTS_PER_FILE, INT_ARRAY_ELEMENTS_TOTAL ->
                    new TagPair(rootWithIntArray(1), rootWithIntArray(2));
            case LONG_ARRAY_ELEMENTS_PER_FILE, LONG_ARRAY_ELEMENTS_TOTAL ->
                    new TagPair(rootWithLongArray(1), rootWithLongArray(2));
            case MODIFIED_UTF8_BYTES_PER_FILE, MODIFIED_UTF8_BYTES_TOTAL -> {
                var before = new CompoundTag();
                before.putString("x", "a");
                var after = new CompoundTag();
                after.putString("x", "aa");
                yield new TagPair(before, after);
            }
            case SCALAR_TAGS_PER_FILE, SCALAR_TAGS_TOTAL -> {
                var before = new CompoundTag();
                before.putByteArray("x", new byte[0]);
                yield new TagPair(before, rootWithByte("x"));
            }
            default -> throw new IllegalArgumentException(
                    "counter has no streaming NBT mutation: " + counter);
        };
    }

    private static CompoundTag canonicalDataVersion() {
        var tag = new CompoundTag();
        tag.putInt("DataVersion", 3_955);
        return tag;
    }

    private static P4E0ResearchWireNbt.ScanFacts writeAndScan(
            Path path, CompoundTag tag, P4E0ResearchWireNbt.HeaderOptions header)
            throws Exception {
        write(path, tag, header);
        return scan(path);
    }

    private static void write(
            Path path, CompoundTag tag, P4E0ResearchWireNbt.HeaderOptions header)
            throws IOException {
        P4E0ResearchWireNbt.write(
                path,
                header,
                Deflater.DEFAULT_COMPRESSION,
                MICRO_WIRE_LIMIT,
                MICRO_WIRE_LIMIT,
                output -> NbtIo.writeUnnamedTag(tag, output));
    }

    private static P4E0ResearchWireNbt.ScanFacts scan(Path path) throws IOException {
        return P4E0ResearchWireNbt.scan(
                path,
                new P4E0ResearchWireNbt.ScanLimits(
                        MICRO_WIRE_LIMIT,
                        MICRO_WIRE_LIMIT,
                        1_000_000L,
                        1_024,
                        1_000_000L,
                        MICRO_WIRE_LIMIT));
    }

    private static long directoryEntries(Path root) throws IOException {
        try (var entries = Files.list(root)) {
            return entries.count();
        }
    }

    private static EnumMap<P4E0R2QProfile.Counter, Long> coordinateDeltas(
            P4E0ResearchWireNbt.ScanFacts before,
            P4E0ResearchWireNbt.ScanFacts after) {
        var beforeCoordinates = absoluteCoordinates(before);
        var afterCoordinates = absoluteCoordinates(after);
        var result = new EnumMap<P4E0R2QProfile.Counter, Long>(
                P4E0R2QProfile.Counter.class);
        for (var counter : afterCoordinates.keySet()) {
            result.put(counter, Math.subtractExact(
                    afterCoordinates.get(counter), beforeCoordinates.get(counter)));
        }
        return result;
    }

    private static EnumMap<P4E0R2QProfile.Counter, Long> absoluteSelectedRecord(
            P4E0ResearchWireNbt.ScanFacts facts) {
        var result = absoluteCoordinates(facts);
        result.put(P4E0R2QProfile.Counter.RELEVANT_RECORDS, 1L);
        return result;
    }

    private static EnumMap<P4E0R2QProfile.Counter, Long> absoluteCoordinates(
            P4E0ResearchWireNbt.ScanFacts facts) {
        var result = new EnumMap<P4E0R2QProfile.Counter, Long>(
                P4E0R2QProfile.Counter.class);
        result.put(P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE, facts.physicalBytes());
        result.put(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL, facts.physicalBytes());
        result.put(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE, facts.decompressedBytes());
        result.put(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL, facts.decompressedBytes());
        var metrics = facts.nbt();
        putPair(result, P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE,
                P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL,
                metrics.compoundCount());
        putPair(result, P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL,
                metrics.compoundEntryCount());
        result.put(P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE,
                (long) metrics.maxContainerDepth());
        putPair(result, P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL,
                metrics.listElementCount());
        putPair(result, P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL,
                metrics.byteArrayElements());
        putPair(result, P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL,
                metrics.intArrayElements());
        putPair(result, P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE,
                P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL,
                metrics.longArrayElements());
        putPair(result, P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE,
                P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL,
                metrics.modifiedUtf8Bytes());
        putPair(result, P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE,
                P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL,
                metrics.scalarTagCount());
        return result;
    }

    private static void putPair(
            EnumMap<P4E0R2QProfile.Counter, Long> result,
            P4E0R2QProfile.Counter perFile,
            P4E0R2QProfile.Counter aggregate,
            long value) {
        result.put(perFile, value);
        result.put(aggregate, value);
    }

    private static EnumMap<P4E0R2QProfile.Counter, Long> deltas(
            P4E0R2QProfile.Counter counter, long value) {
        var result = new EnumMap<P4E0R2QProfile.Counter, Long>(
                P4E0R2QProfile.Counter.class);
        result.put(counter, value);
        return result;
    }

    private static EnumMap<P4E0R2QProfile.Counter, P4E0R2QFixturePlan.CounterDelta>
            deltasByCounter(P4E0R2QFixturePlan.MutationDerivation derivation) {
        var result = new EnumMap<
                P4E0R2QProfile.Counter, P4E0R2QFixturePlan.CounterDelta>(
                P4E0R2QProfile.Counter.class);
        for (var delta : derivation.physicalDeltas()) {
            assertEquals(null, result.put(delta.counter(), delta));
        }
        return result;
    }

    private static EnumMap<P4E0R2QProfile.Counter, P4E0R2QFixturePlan.CompensationDelta>
            compensationsByCounter(P4E0R2QFixturePlan.MutationDerivation derivation) {
        var result = new EnumMap<
                P4E0R2QProfile.Counter, P4E0R2QFixturePlan.CompensationDelta>(
                P4E0R2QProfile.Counter.class);
        for (var delta : derivation.compensations()) {
            assertEquals(null, result.put(delta.counter(), delta));
        }
        return result;
    }

    private static CompoundTag rootWithByte(String name) {
        var root = new CompoundTag();
        root.putByte(name, (byte) 1);
        return root;
    }

    private static CompoundTag rootWithByteList(int size) {
        var list = new ListTag();
        for (var index = 0; index < size; index++) {
            list.add(ByteTag.valueOf((byte) index));
        }
        var root = new CompoundTag();
        root.put("x", list);
        return root;
    }

    private static CompoundTag rootWithByteArray(int size) {
        var root = new CompoundTag();
        root.putByteArray("x", new byte[size]);
        return root;
    }

    private static CompoundTag rootWithIntArray(int size) {
        var root = new CompoundTag();
        root.putIntArray("x", new int[size]);
        return root;
    }

    private static CompoundTag rootWithLongArray(int size) {
        var root = new CompoundTag();
        root.putLongArray("x", new long[size]);
        return root;
    }

    private static void assertWireAndMetricDelta(
            CompoundTag before,
            CompoundTag after,
            long decompressedDelta,
            Metric metric,
            long metricDelta) throws Exception {
        var beforeObserved = observe(before);
        var afterObserved = observe(after);
        assertAll(
                () -> assertEquals(
                        decompressedDelta,
                        afterObserved.wire().decompressedBytes()
                                - beforeObserved.wire().decompressedBytes()),
                () -> assertEquals(
                        metricDelta,
                        metric.value(afterObserved.metrics())
                                - metric.value(beforeObserved.metrics())));
    }

    private static Observed observe(net.minecraft.nbt.Tag tag) throws IOException {
        var wire = P4E0ResearchWireNbt.measure(
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                16_777_216L,
                16_777_216L,
                output -> NbtIo.writeUnnamedTag(tag, output));
        return new Observed(wire, P4E0ResearchNbtMetrics.measure(tag));
    }

    private static P4E0ResearchWireNbt.WriteFacts measureHeader(int fileNameBytes)
            throws IOException {
        var root = new CompoundTag();
        root.putInt("DataVersion", 3_955);
        return P4E0ResearchWireNbt.measure(
                P4E0ResearchWireNbt.HeaderOptions.fileName(fileNameBytes),
                Deflater.DEFAULT_COMPRESSION,
                1_024L,
                64L,
                output -> NbtIo.writeUnnamedTag(root, output));
    }

    private record Observed(
            P4E0ResearchWireNbt.WriteFacts wire,
            P4E0ResearchNbtMetrics metrics) {
    }

    private record TagPair(CompoundTag before, CompoundTag after) {
        TagPair {
            assertNotNull(before);
            assertNotNull(after);
        }
    }

    private record PhysicalExecution(
            P4E0R2QProfile.Counter targetCounter,
            P4E0R2QFixturePlan.PhysicalFixtureKind fixtureKind,
            P4E0R2QFixturePlan.MutationPlacement mutationPlacement,
            Map<P4E0R2QProfile.Counter, Long> forwardDeltas,
            Map<P4E0R2QProfile.Counter, Long> compensationDeltas,
            int physicalArtifacts) {
        PhysicalExecution {
            assertNotNull(targetCounter);
            assertNotNull(fixtureKind);
            assertNotNull(mutationPlacement);
            forwardDeltas = Map.copyOf(forwardDeltas);
            compensationDeltas = Map.copyOf(compensationDeltas);
            assertTrue(physicalArtifacts > 0);
        }
    }

    private enum Metric {
        NONE {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return 0;
            }
        },
        COMPOUNDS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.compoundCount();
            }
        },
        FIELDS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.compoundEntryCount();
            }
        },
        LIST_ELEMENTS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.listElementCount();
            }
        },
        BYTE_ARRAY_ELEMENTS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.byteArrayElements();
            }
        },
        INT_ARRAY_ELEMENTS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.intArrayElements();
            }
        },
        LONG_ARRAY_ELEMENTS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.longArrayElements();
            }
        },
        MODIFIED_UTF8 {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.modifiedUtf8Bytes();
            }
        },
        SCALARS {
            @Override
            long value(P4E0ResearchNbtMetrics metrics) {
                return metrics.scalarTagCount();
            }
        };

        abstract long value(P4E0ResearchNbtMetrics metrics);
    }
}
