package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.store.P4E0R2QStoreJournalFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lightweight exact-arithmetic and production-framing proof for the R2Q-A fixture blueprint. */
final class P4E0ResearchR2QFixtureTest {
    @TempDir
    Path temporary;

    @Test
    void exactDirectoryShapeAndAttachmentAdmissionsAreActuallyConstructed()
            throws Exception {
        var directory = projectRoot().resolve(
                "build/p4-e0-research/matrix/r2q-a-test-"
                        + temporary.getFileName());
        var spec = P4E0ResearchR2PlanFactory.standardPlan().runs().stream()
                .filter(run -> run.matrix()
                        == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)
                .filter(run -> run.axis().equals("DIRECTORY_ENTRIES"))
                .filter(run -> run.shape().equals("PRIMARY_OLD_PAIRED"))
                .filter(run -> run.coordinate() == 4_096L)
                .filter(run -> run.heapMiB() == 1_536)
                .findFirst()
                .orElseThrow();
        var request = P4E0ResearchR2PlanFactory.plainRequest(
                spec,
                directory,
                P4E0R2QProfile.locked().researchDiskBudgetBytes());
        try {
            var observed = P4E0ResearchMatrixRunner.prepareAndRun(request);
            var admissions = P4E0R2QStoreJournalFixtures.buildExact().admissionFacts();

            assertEquals(4_096, observed.fixture().directoryEntries());
            assertEquals(2_048, observed.fixture().canonicalPrimaries());
            assertEquals(2_048, observed.fixture().canonicalOld());
            assertEquals(2_048, observed.fixture().uniqueRoutes());
            assertEquals(2_048, observed.directory().decodedRecords());
            assertEquals(0, observed.directory().projectedRoots());
            assertEquals(1_024, admissions.totalAdmissions());
            assertEquals(1, admissions.mixedFamilyAdmissions());
            assertEquals(1_023, admissions.minimalReadyAdmissions());
        } finally {
            deleteTree(directory);
        }
    }

    @Test
    void lockedBlueprintSeparatesPhysicalPlayerdataFromTypedRootProjection() {
        var plan = P4E0R2QFixturePlan.locked();
        var observed = plan.counters();
        var expected = P4E0R2QProfile.locked().candidateValues();

        assertEquals(4_096, plan.directory().totalEntries());
        assertEquals(2_048, plan.directory().selectedPrimaries());
        assertEquals(1_024, plan.directory().readyAdmissions());
        assertEquals(1, plan.directory().mixedFamilyReadyAdmissions());
        assertEquals(1_023, plan.directory().minimalReadyAdmissions());
        assertEquals(1_024, plan.directory().selectedWithoutGramaryeAttachment());
        assertTrue(plan.roots().separateFromPlayerdataNbtCounters());
        assertEquals(2_049, plan.roots().latestClaims());
        assertEquals(59_391, plan.roots().equippedClaims());
        assertEquals(61_440, plan.roots().playerClaims());
        assertEquals(4_096, plan.roots().journalClaims());
        assertEquals(65_536, plan.roots().rawClaims());
        assertEquals(65_537, plan.roots().overRawClaims());
        assertEquals(
                observed.decompressedBytesTotal(),
                P4E0R2QFixturePlan.FIXED_FRAMING_BYTES
                        + P4E0R2QFixturePlan.PAYLOAD_BYTES);
        assertEquals(
                observed.byteArrayElementsTotal(),
                P4E0R2QFixturePlan.NON_PAYLOAD_BYTE_ARRAY_ELEMENTS
                        + P4E0R2QFixturePlan.PAYLOAD_BYTES);
        for (var counter : P4E0R2QProfile.Counter.values()) {
            assertEquals(
                    expected.value(counter),
                    observed.value(counter),
                    () -> "independently derived counter differs: " + counter);
        }
        assertEquals(25, P4E0R2QProfile.Counter.values().length);
    }

    @Test
    void componentMultiplicitiesDeriveEveryStructuralAggregateAndFixedEquation() {
        var structural = P4E0R2QFixturePlan.locked().structural();
        var components = structural.components();
        var hca = components.get(0);
        var lowCompression = components.get(1);
        var fillers = components.get(2);

        assertEquals(List.of(
                "HCA_WITNESS", "LOW_COMPRESSION_WITNESS", "AGGREGATE_FILLERS"),
                components.stream().map(P4E0R2QFixturePlan.ComponentFacts::code).toList());
        assertEquals(2_048, structural.recordCount());
        assertEquals(1, hca.recordCount());
        assertEquals(1, lowCompression.recordCount());
        assertEquals(2_046, fillers.recordCount());
        assertEquals(268_435_456L, hca.decompressedBytes());
        assertEquals(268_435_384L, hca.byteArrayElements());
        assertEquals(33_554_376L, lowCompression.decompressedBytes());
        assertEquals(33_554_304L, lowCompression.byteArrayElements());
        assertEquals(154_535_017L, fillers.payloadBytes());

        assertEquals(536_870_912L, structural.decompressedBytes());
        assertEquals(131_072L, structural.compoundContainers());
        assertEquals(524_288L, structural.compoundFieldEntries());
        assertEquals(131_072L, structural.listElements());
        assertEquals(456_524_705L, structural.byteArrayElements());
        assertEquals(131_072L, structural.intArrayElements());
        assertEquals(131_072L, structural.longArrayElements());
        assertEquals(75_497_472L, structural.modifiedUtf8Bytes());
        assertEquals(458_752L, structural.scalarTags());
        assertEquals(154_535_017L, structural.payloadBytes());
        assertEquals(382_335_895L, structural.fixedFramingBytes());
        assertEquals(301_989_688L, structural.nonPayloadByteArrayElements());
        assertEquals(
                structural.decompressedBytes(),
                Math.addExact(structural.fixedFramingBytes(), structural.payloadBytes()));
        assertEquals(
                structural.byteArrayElements(),
                Math.addExact(
                        structural.nonPayloadByteArrayElements(),
                        structural.payloadBytes()));
    }

    @Test
    void everyPerFilePeakHasEnoughTypedComponentCapacityForAnExactWitness() {
        var plan = P4E0R2QFixturePlan.locked();
        var peaks = plan.structural().peaks();

        assertEquals(33_559_514L, plan.compressed().maximumPhysicalBytes());
        assertEquals(268_435_456L, peaks.decompressedBytesPerFile());
        assertEquals(512L, peaks.containerDepthPerFile());
        assertEquals(1_024L, peaks.compoundContainersPerFile());
        assertEquals(65_537L, peaks.compoundFieldEntriesPerFile());
        assertEquals(65_536L, peaks.listElementsPerFile());
        assertEquals(268_435_384L, peaks.byteArrayElementsPerFile());
        assertEquals(65_536L, peaks.intArrayElementsPerFile());
        assertEquals(65_536L, peaks.longArrayElementsPerFile());
        assertEquals(67_107_692L, peaks.modifiedUtf8BytesPerFile());
        assertEquals(65_537L, peaks.scalarTagsPerFile());
    }

    @Test
    void compressedHeaderPlanHasIndependentPerFileAndAggregatePlusOneMutations()
            throws Exception {
        var blueprint = P4E0R2QFixturePlan.locked();
        var plan = blueprint.compressed();
        var observed = blueprint.counters();

        assertEquals(
                blueprint.jointRecords().canonicalPhysicalBytes().stream()
                        .mapToLong(Long::longValue).sum(),
                plan.canonicalTotal());
        assertEquals(observed.compressedBytesTotal(), plan.tunedTotal());
        assertEquals(
                observed.compressedBytesPerFile(),
                plan.files().get(plan.perFileMaximumIndex()).targetPhysicalBytes());
        assertTrue(plan.files().stream().allMatch(file ->
                file.targetPhysicalBytes() <= observed.compressedBytesPerFile()
                        && (file.fileNameBytes() == 0 || file.fileNameBytes() > 0)));
        var renderedTotal = 0L;
        for (var file : plan.files()) {
            renderedTotal = Math.addExact(
                    renderedTotal,
                    blueprint.jointRecords().measure(
                            file.fileIndex(),
                            file.headerOptions(),
                            file.targetPhysicalBytes()).physicalBytes());
        }
        assertEquals(observed.compressedBytesTotal(), renderedTotal);

        var perFileOver = plan.perFileOverrun();
        assertEquals(
                observed.compressedBytesPerFile() + 1,
                perFileOver.get(plan.perFileMaximumIndex()).targetPhysicalBytes());
        assertEquals(observed.compressedBytesTotal(), sum(perFileOver));
        assertTrue(perFileOver.stream()
                .filter(file -> file.fileIndex() != plan.perFileMaximumIndex())
                .allMatch(file -> file.targetPhysicalBytes()
                        <= observed.compressedBytesPerFile()));

        var aggregateOver = plan.aggregateOverrun();
        assertEquals(observed.compressedBytesTotal() + 1, sum(aggregateOver));
        assertTrue(aggregateOver.stream().allMatch(file ->
                file.targetPhysicalBytes() <= observed.compressedBytesPerFile()));
        assertEquals(
                observed.compressedBytesPerFile() + 1,
                blueprint.jointRecords().measure(
                        plan.perFileMaximumIndex(),
                        perFileOver.get(plan.perFileMaximumIndex()).headerOptions(),
                        perFileOver.get(plan.perFileMaximumIndex()).targetPhysicalBytes())
                        .physicalBytes());
        assertEquals(
                aggregateOver.get(plan.aggregateOverrunIndex()).targetPhysicalBytes(),
                blueprint.jointRecords().measure(
                        plan.aggregateOverrunIndex(),
                        aggregateOver.get(plan.aggregateOverrunIndex()).headerOptions(),
                        aggregateOver.get(plan.aggregateOverrunIndex()).targetPhysicalBytes())
                        .physicalBytes());
    }

    @Test
    void ownerRedistributionPreservesExactStoreAndJournalFramingAndRootBoundary() {
        var fixture = P4E0R2QStoreJournalFixtures.buildExact();
        var facts = fixture.facts();

        assertEquals(66_060_348, facts.currentStoreBytes());
        assertEquals(2_048, facts.currentHistories());
        assertEquals(4_095, facts.currentRevisions());
        assertEquals(1_024, facts.ownerCount());
        assertEquals(4_095, facts.currentJournalEntries());
        assertEquals(1_048_324, facts.currentJournalBytes());
        assertEquals(2_049, facts.prospectiveHistories());
        assertEquals(4_096, facts.prospectiveRevisions());
        assertEquals(4_096, facts.prospectiveJournalEntries());
        assertEquals(1_048_538, facts.prospectiveJournalBytes());
        assertEquals(2_049, facts.latestRoots());
        assertEquals(59_391, facts.equippedRoots());
        assertEquals(4_096, facts.journalRoots());
        assertEquals(65_536, facts.exactRawRoots());
        assertEquals(65_537, facts.overRawRoots());
        assertTrue(facts.exactRootsComplete());
        assertTrue(facts.overRootsRejected());
        assertTrue(facts.currentStoreChecksum().matches("[0-9a-f]{64}"));
        fixture.retainAtPeak();
    }

    @Test
    void fixtureSourcesDoNotCallReclaimOrHandWritePersistentBlobs() throws Exception {
        var root = projectRoot();
        var store = Files.readString(root.resolve(
                "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E0R2QStoreJournalFixtures.java"));
        var plan = Files.readString(root.resolve(
                "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0R2QFixturePlan.java"));
        var cases = Files.readString(root.resolve(
                "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0R2QCasePlan.java"));

        assertFalse(store.contains(".reclaim("));
        assertFalse(store.contains("new EncodedSkillStoreCarrier"));
        assertFalse(store.contains("new EncodedPendingAttachmentJournal"));
        assertTrue(store.contains("SkillStoreCarrierBuilder.rebuild"));
        assertTrue(store.contains("PendingAttachmentJournalFraming.encode"));
        assertTrue(plan.contains("distinct coordinates"));
        assertFalse(plan.contains("P4E0R2QProfile.locked()"));
        assertFalse(plan.contains("candidateValues()"));
        assertFalse(plan.contains("100_000"));
        assertTrue(plan.contains("P4E0ResearchWireNbt.measure"));
        assertFalse(cases.contains("boolean physicallyValid"));
    }

    private static long sum(List<P4E0R2QFixturePlan.HeaderTuning> files) {
        return files.stream().mapToLong(
                P4E0R2QFixturePlan.HeaderTuning::targetPhysicalBytes).sum();
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

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
