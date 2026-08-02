package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Lightweight taxonomy, reuse, deterministic-fixture, and strict-ingress gate for P4-E0-R1. */
final class P4E0ResearchFixtureTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path RESEARCH_ROOT =
            PROJECT_ROOT.resolve("src/p4E0Research/java");
    private static final Path PLAYER_ADAPTER = RESEARCH_ROOT.resolve(
            "com/yo1no/gramarye/magic/definition/player/"
                    + "P4E0ResearchAttachmentFixtures.java");
    private static final Path STORE_ADAPTER = RESEARCH_ROOT.resolve(
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchStoreJournalFixtures.java");
    private static final Path GZIP_ADAPTER = RESEARCH_ROOT.resolve(
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchGzipAdapter.java");

    @Test
    void caseTaxonomyAndSupportedScenariosAreExactAndResearchOnly() throws Exception {
        var fixtureCase = load(
                "com.yo1no.gramarye.magic.definition.research.P4E0ResearchCase");
        var scenario = load(
                "com.yo1no.gramarye.magic.definition.research.P4E0ResearchScenario");
        assertTrue(fixtureCase.isEnum());
        assertTrue(scenario.isEnum());
        var caseNames = Arrays.stream(fixtureCase.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .collect(Collectors.toSet());
        var scenarioNames = Arrays.stream(scenario.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
                .collect(Collectors.toSet());
        assertEquals(P4E0ResearchPhaseTypes.CASE_NAMES, caseNames);
        assertEquals(P4E0ResearchPhaseTypes.SCENARIO_NAMES, scenarioNames);

        var combined = sources(RESEARCH_ROOT).toLowerCase(java.util.Locale.ROOT);
        assertAll(
                () -> assertTrue(combined.contains("research-only")),
                () -> assertTrue(combined.contains("non-authoritative")),
                () -> assertFalse(combined.contains("normative ceiling")),
                () -> assertFalse(combined.contains("safe upper bound")));
    }

    @Test
    void bothScenariosRunTheFullTaxonomyAndSelectDifferentReadyCoordinates()
            throws Exception {
        var correctness = P4E0ResearchAttachmentFixtures.readyRootMax(
                P4E0ResearchScenario.CORRECTNESS_SMOKE.includeExistingMixedDraft());
        var playerdataCoordinate = P4E0ResearchAttachmentFixtures.readyRootMax(
                P4E0ResearchScenario.PLAYERDATA_COORDINATE_SMOKE
                        .includeExistingMixedDraft());
        var factory = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchFixtureFactory.java"));

        assertAll(
                () -> assertEquals(1, correctness.draftCount()),
                () -> assertEquals(0, playerdataCoordinate.draftCount()),
                () -> assertTrue(correctness.serializedWriteAnyTagBytes()
                        > playerdataCoordinate.serializedWriteAnyTagBytes()),
                () -> assertFalse(
                        correctness.serializedTag().equals(playerdataCoordinate.serializedTag())),
                () -> assertEquals(
                        correctness.latestCount(), playerdataCoordinate.latestCount()),
                () -> assertEquals(
                        correctness.equippedCount(), playerdataCoordinate.equippedCount()),
                () -> assertEquals(
                        correctness.projectedRoots(), playerdataCoordinate.projectedRoots()),
                () -> assertEquals(1, occurrences(
                        factory, "parameters.scenario().includeExistingMixedDraft()")));
        for (var fixtureName : P4E0ResearchPhaseTypes.CASE_NAMES) {
            assertTrue(factory.contains("P4E0ResearchCase." + fixtureName),
                    () -> "scenario-independent fixture case is missing: " + fixtureName);
        }
    }

    @Test
    void researchPhysicalSourceSelectorDistinguishesPrimaryAndOldOnFilesystem()
            throws Exception {
        var directory = PROJECT_ROOT.resolve(
                "build/p4-e0-research/selection-shape-test");
        var pairedId = UUID.fromString("50344530-0000-0000-8000-000000000001");
        var fallbackId = UUID.fromString("50344530-0000-0000-8000-000000000002");
        var pairedPrimary = directory.resolve(pairedId + ".dat");
        var pairedOld = directory.resolve(pairedId + ".dat_old");
        var fallbackPrimary = directory.resolve(fallbackId + ".dat");
        var fallbackOld = directory.resolve(fallbackId + ".dat_old");

        Files.createDirectories(directory);
        for (var path : List.of(
                pairedPrimary, pairedOld, fallbackPrimary, fallbackOld)) {
            Files.deleteIfExists(path);
        }
        try {
            var primaryRoot = new CompoundTag();
            primaryRoot.putInt("DataVersion", 3_955);
            primaryRoot.putString("research_source", "primary");
            NbtIo.writeCompressed(primaryRoot, pairedPrimary);
            Files.writeString(pairedOld, "arbitrary-malformed-old-not-selected\n");

            var fallbackRoot = new CompoundTag();
            fallbackRoot.putInt("DataVersion", 3_955);
            fallbackRoot.putString("research_source", "old");
            NbtIo.writeCompressed(fallbackRoot, fallbackOld);

            var primarySelection = P4E0ResearchFixtureFactory.selectResearchSource(
                    directory, pairedId);
            var oldSelection = P4E0ResearchFixtureFactory.selectResearchSource(
                    directory, fallbackId);

            assertAll(
                    () -> assertEquals(
                            P4E0ResearchFixtureFactory.ResearchPhysicalSource.PRIMARY,
                            primarySelection.source()),
                    () -> assertEquals(
                            pairedPrimary.toAbsolutePath().normalize(),
                            primarySelection.selectedPath()),
                    () -> assertTrue(primarySelection.primaryPresent()),
                    () -> assertTrue(primarySelection.oldPresent()),
                    () -> assertNotEquals(
                            P4E0ResearchHashing.sha256(pairedPrimary),
                            P4E0ResearchHashing.sha256(pairedOld)),
                    () -> assertEquals(
                            3_955,
                            NbtIo.readCompressed(
                                    primarySelection.selectedPath(),
                                    NbtAccounter.create(4_096L)).getInt("DataVersion")),
                    () -> assertThrows(
                            IOException.class,
                            () -> NbtIo.readCompressed(
                                    pairedOld, NbtAccounter.create(4_096L))),
                    () -> assertEquals(
                            P4E0ResearchFixtureFactory.ResearchPhysicalSource.OLD,
                            oldSelection.source()),
                    () -> assertEquals(
                            fallbackOld.toAbsolutePath().normalize(),
                            oldSelection.selectedPath()),
                    () -> assertFalse(oldSelection.primaryPresent()),
                    () -> assertTrue(oldSelection.oldPresent()),
                    () -> assertFalse(Files.exists(fallbackPrimary)),
                    () -> assertEquals(
                            3_955,
                            NbtIo.readCompressed(
                                    oldSelection.selectedPath(),
                                    NbtAccounter.create(4_096L)).getInt("DataVersion")));
        } finally {
            for (var path : List.of(
                    pairedPrimary, pairedOld, fallbackPrimary, fallbackOld)) {
                Files.deleteIfExists(path);
            }
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void coPackageAdaptersReuseReviewedBuildersWithoutOpeningProductionVisibility()
            throws Exception {
        var attachment = read(PLAYER_ADAPTER);
        var store = read(STORE_ADAPTER);

        assertAll(
                () -> assertTrue(attachment.contains("P4C2FixtureBuilder")),
                () -> assertTrue(store.contains("P4D3StoreJournalFixture")),
                () -> assertFalse(attachment.contains("PlayerSkillAttachmentSerializer(")),
                () -> assertFalse(store.contains("new SkillDefinitionStoreCarrier")),
                () -> assertTrue(Modifier.isPublic(load(
                                "com.yo1no.gramarye.magic.definition.player."
                                        + "P4E0ResearchAttachmentFixtures")
                        .getModifiers())),
                () -> assertTrue(Modifier.isPublic(load(
                                "com.yo1no.gramarye.magic.definition.store."
                                        + "P4E0ResearchStoreJournalFixtures")
                        .getModifiers())));
    }

    @Test
    void gzipAdapterUsesReviewedStreamingPrimitivesAndFiniteAccounting()
            throws Exception {
        var gzip = read(GZIP_ADAPTER);
        for (var required : List.of(
                "FileChannel",
                "BoundedChannelInputStream",
                "GzipHeaderVerifier",
                "BufferedInputStream",
                "GzipCompressorInputStream",
                "NbtAccounter.create(")) {
            assertTrue(gzip.contains(required), () -> "missing strict gzip marker: " + required);
        }
        for (var forbidden : List.of(
                "java.util.zip.GZIPInputStream",
                "Files.readAllBytes",
                "NbtAccounter.unlimitedHeap",
                ".available(",
                "getCompressedCount(",
                "concatenated=true",
                "concatenated = true")) {
            assertFalse(gzip.contains(forbidden), () -> "unsafe gzip marker: " + forbidden);
        }
        assertTrue(Modifier.isPublic(load(
                        "com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter")
                .getModifiers()));
    }

    @Test
    void smokeFixtureContractContainsEveryRequiredBoundaryAndDeterminismInput()
            throws Exception {
        var source = sources(RESEARCH_ROOT);
        for (var fixtureCase : P4E0ResearchPhaseTypes.CASE_NAMES) {
            assertTrue(source.contains(fixtureCase),
                    () -> "missing research fixture case " + fixtureCase);
        }
        for (var scenario : P4E0ResearchPhaseTypes.SCENARIO_NAMES) {
            assertTrue(source.contains(scenario),
                    () -> "missing supported research scenario " + scenario);
        }
        for (var marker : List.of(
                "3_955",
                "16_777_216",
                "65_536",
                "rootClaims",
                "hashSyntheticTree",
                "hashDecodedChecksums",
                "artifact_alias",
                "mixed-directory-summary",
                "reduced-combined-envelope",
                "seed",
                "SHA-256",
                "build/p4-e0-research",
                "build/reports/p4-e0-research")) {
            assertTrue(source.contains(marker), () -> "missing fixture marker " + marker);
        }
        assertAll(
                () -> assertFalse(source.contains("UUID.randomUUID()")),
                () -> assertFalse(source.contains("ThreadLocalRandom")),
                () -> assertFalse(source.contains("SecureRandom")),
                () -> assertFalse(source.contains("new Random(")),
                () -> assertFalse(source.contains("@TempDir")),
                () -> assertFalse(source.contains("Files.createTempDirectory")),
                () -> assertFalse(source.contains("java.io.tmpdir")),
                () -> assertFalse(source.contains("\".minecraft\"")),
                () -> assertFalse(source.contains("/.minecraft/")),
                () -> assertFalse(source.contains("/Users/")));
    }

    @Test
    void reportAliasesAndIntegrityCoverEverySyntheticArtifactWithoutUuidDisclosure()
            throws Exception {
        var factory = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchFixtureFactory.java"));
        var result = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchResult.java"));
        var resource = read(PROJECT_ROOT.resolve(
                "src/p4E0Research/resources/p4-e0-research-smoke-v0.json"));

        assertAll(
                () -> assertTrue(result.contains("\"artifact_alias\"")),
                () -> assertTrue(result.contains("\"fixture_file_count\"")),
                () -> assertTrue(result.contains("\"decoded_artifact_count\"")),
                () -> assertTrue(result.contains("json.add(\"metrics\"")),
                () -> assertTrue(factory.contains("TreeMap<String, String>")),
                () -> assertTrue(factory.contains("Files.walk(root)")),
                () -> assertTrue(factory.contains("Files::isRegularFile")),
                () -> assertTrue(factory.contains("P4E0ResearchCase.MIXED_DIRECTORY")),
                () -> assertTrue(factory.contains("P4E0ResearchCase.COMBINED_ENVELOPE")),
                () -> assertTrue(factory.contains(
                        "directory.uniqueUuidRecords() != parameters.relevantRecords()")),
                () -> assertTrue(resource.contains("\"relevant_records\": 9")),
                () -> assertFalse(result.contains("\"artifact\"")));
    }

    @Test
    void resourceConfigurationIsBoundedAndContainsOnlySyntheticParameters()
            throws Exception {
        var resource = read(PROJECT_ROOT.resolve(
                "src/p4E0Research/resources/p4-e0-research-smoke-v0.json"));
        for (var key : List.of(
                "scenario",
                "heap_mib",
                "directory_entries",
                "relevant_records",
                "compressed_target_bytes",
                "decompressed_target_bytes",
                "target_depth",
                "target_compound_entries",
                "target_list_elements",
                "target_array_elements",
                "root_claims",
                "ready_record_ratio",
                "preserved_raw_record_count",
                "seed",
                "compressed_guard_bytes",
                "decompressed_guard_bytes",
                "nbt_quota_bytes")) {
            assertTrue(resource.contains('"' + key + '"'),
                    () -> "missing research parameter " + key);
        }
        assertAll(
                () -> assertTrue(resource.length() <= 16_384),
                () -> assertFalse(resource.contains("playerName")),
                () -> assertFalse(resource.contains("worldPath")),
                () -> assertFalse(resource.contains("/Users/")),
                () -> assertFalse(resource.contains(".minecraft")));
    }

    @Test
    void researchParametersDriveTheBoundedSmokeBuilderAndChildHeap() {
        var parameters = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchParameters.java"));
        var factory = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchFixtureFactory.java"));
        var main = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchMain.java"));

        assertAll(
                () -> assertTrue(parameters.contains("withSystemPropertyOverrides()")),
                () -> assertTrue(parameters.contains(
                        "enumProperty(prefix + \"scenario\"")),
                () -> assertFalse(parameters.contains("scenarioCase")),
                () -> assertTrue(factory.contains("parameters.targetDepth()")),
                () -> assertTrue(factory.contains(
                        "requirePlatformDepthRejection(targetPath, quota)")),
                () -> assertTrue(factory.contains(
                        "requirePlatformDepthRejection(\n"
                                + "                    targetPath, parameters.nbtQuotaBytes())")),
                () -> assertTrue(factory.contains(
                        "catch (NbtAccounterException expected)")),
                () -> assertFalse(factory.contains(
                        "catch (IOException | NbtAccounterException")),
                () -> assertTrue(factory.contains("parameters.targetCompoundEntries()")),
                () -> assertTrue(factory.contains("parameters.targetListElements()")),
                () -> assertTrue(factory.contains("parameters.targetArrayElements()")),
                () -> assertTrue(factory.contains("parameters.rootClaims()")),
                () -> assertTrue(factory.contains("parameters.readyRecordRatio()")),
                () -> assertTrue(factory.contains("parameters.preservedRawRecordCount()")),
                () -> assertTrue(factory.contains("parameters.compressedTargetBytes()")),
                () -> assertTrue(factory.contains("parameters.decompressedTargetBytes()")),
                () -> assertTrue(factory.contains("parameters.directoryEntries()")),
                () -> assertTrue(factory.contains("parameters.relevantRecords()")),
                () -> assertTrue(factory.contains("parameters.outputDirectory()")),
                () -> assertTrue(main.contains("parameters.heapMiB()")));
    }

    private static String sources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(P4E0ResearchFixtureTest::read)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static int occurrences(String source, String needle) {
        var count = 0;
        var cursor = 0;
        while ((cursor = source.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, P4E0ResearchFixtureTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to load research fixture type " + name, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect " + path, exception);
        }
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
