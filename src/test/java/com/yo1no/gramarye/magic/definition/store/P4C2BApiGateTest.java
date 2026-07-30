package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase-local source-set, lifecycle, task, CI, and isolation gate for P4-C2-B. */
class P4C2BApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path PROBE_ROOT = PROJECT_ROOT.resolve("src/p4C2Probe/java")
            .resolve(P4C2BPhaseTypes.PLAYER_PACKAGE_PATH);
    private static final Path STORE_PROBE_ROOT = PROJECT_ROOT.resolve("src/p4C2Probe/java")
            .resolve(P4C2BPhaseTypes.STORE_PACKAGE_PATH);
    private static final Path GAME_TEST_ROOT = PROJECT_ROOT.resolve("src/p4C2GameTest/java")
            .resolve(P4C2BPhaseTypes.PLAYER_PACKAGE_PATH);
    private static final Path P4_B2_PROBE_ROOT = PROJECT_ROOT.resolve("src/p4B2Probe/java")
            .resolve(P4C2BPhaseTypes.STORE_PACKAGE_PATH);

    @Test
    void exactReviewedSourcesStayInTestOnlyRoots() throws Exception {
        var probeMain = Class.forName(
                "com.yo1no.gramarye.magic.definition.player.P4C2ProbeMain");

        assertAll(
                () -> assertEquals(P4C2BPhaseTypes.PROBE_SOURCE_FILE_NAMES,
                        javaFiles(PROBE_ROOT)),
                () -> assertEquals(P4C2BPhaseTypes.STORE_PROBE_SOURCE_FILE_NAMES,
                        javaFiles(STORE_PROBE_ROOT)),
                () -> assertEquals(P4C2BPhaseTypes.GAME_TEST_SOURCE_FILE_NAMES,
                        javaFiles(GAME_TEST_ROOT)),
                () -> assertTrue(Modifier.isPublic(probeMain.getModifiers())),
                () -> assertEquals(Set.of("main"), Arrays.stream(probeMain.getDeclaredMethods())
                        .filter(method -> Modifier.isPublic(method.getModifiers()))
                        .map(method -> method.getName())
                        .collect(Collectors.toSet())),
                () -> assertThrows(ClassNotFoundException.class, () -> Class.forName(
                        "com.yo1no.gramarye.magic.definition.player.P4C2MemoryGameTests")));
    }

    @Test
    void buildDeclaresOnlyTheTwoReviewedSourceSetsAndDedicatedModShape() throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));

        for (var required : List.of(
                "sourceSets.create('p4C2Probe')",
                "sourceSets.create('p4C2GameTest')",
                "tasks.register('generateP4C2GameTestResources', Sync)",
                "data/gramarye_p4_c2/structure/p4_c2_probe.nbt",
                "addModdingDependenciesTo(p4C2ProbeSourceSet)",
                "addModdingDependenciesTo(p4C2GameTestSourceSet)",
                "p4C2HeapProbe",
                "sourceSet(p4C2ProbeSourceSet)",
                "sourceSet(p4C2GameTestSourceSet)",
                "add(p4C2ProbeSourceSet.implementationConfigurationName, sourceSets.main.output)",
                "add(p4C2GameTestSourceSet.implementationConfigurationName, sourceSets.main.output)",
                "add(p4C2GameTestSourceSet.implementationConfigurationName, "
                        + "p4C2ProbeSourceSet.output)",
                "testImplementation p4C2ProbeSourceSet.output")) {
            assertTrue(build.contains(required), () -> "missing C2-B build marker: " + required);
        }
        assertAll(
                () -> assertEquals(2, occurrences(build, "sourceSets.create('p4C2")),
                () -> assertFalse(build.contains("testImplementation p4C2GameTestSourceSet.output")),
                () -> assertFalse(build.contains("runtimeElements.extendsFrom p4C2")),
                () -> assertFalse(build.contains(
                        "add(p4C2ProbeSourceSet.implementationConfigurationName, "
                                + "p4A3ProbeSourceSet.output)")),
                () -> assertFalse(build.contains(
                        "add(p4C2ProbeSourceSet.implementationConfigurationName, "
                                + "p4B2ProbeSourceSet.output)")),
                () -> assertFalse(build.contains(
                        "add(p4C2GameTestSourceSet.implementationConfigurationName, "
                                + "p4A3ProbeSourceSet.output)")),
                () -> assertFalse(build.contains(
                        "add(p4C2GameTestSourceSet.implementationConfigurationName, "
                                + "p4B2ProbeSourceSet.output)")));
    }

    @Test
    void sixFixedHeapProcessesAndExternalVerifiersAreExactAndSerialized() throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var ordered = List.of(
                "runP4C2ReadyServer",
                "verifyP4C2ReadyFirst",
                "runP4C2ReadyRestartServer",
                "verifyP4C2ReadyRestart",
                "runP4C2PreservedRawServer",
                "verifyP4C2PreservedRawFirst",
                "runP4C2PreservedRawRestartServer",
                "verifyP4C2PreservedRawRestart",
                "runP4C2OversizeServer",
                "verifyP4C2OversizeFirst",
                "runP4C2OversizeRestartServer",
                "verifyP4C2OversizeRestart");

        for (var required : List.of(
                "prepareP4C2Worlds",
                "prepareP4C2PreservedStore",
                "verifyP4C2Configuration",
                "p4C2FixedHeapGate",
                "'-Xms512m'",
                "'-Xmx1024m'",
                "'-XX:+ExitOnOutOfMemoryError'",
                "Duration.ofSeconds(600)",
                "Duration.ofSeconds(180)",
                "gramarye.p4c2.runMode")) {
            assertTrue(build.contains(required), () -> "missing C2-B task marker: " + required);
        }
        ordered.forEach(name -> assertTrue(build.contains(name),
                () -> "missing serialized C2-B node: " + name));
        for (var edge : List.of(
                "dependsOn(runP4C2ReadyServer, verifyP4C2ReadyFirst)",
                "dependsOn(runP4C2ReadyRestartServer, verifyP4C2ReadyRestart)",
                "dependsOn(runP4C2PreservedRawServer, verifyP4C2PreservedRawFirst)",
                "dependsOn(runP4C2PreservedRawRestartServer, "
                        + "verifyP4C2PreservedRawRestart)",
                "dependsOn(runP4C2OversizeServer, verifyP4C2OversizeFirst)")) {
            assertTrue(build.contains(edge), () -> "missing serialized C2-B edge: " + edge);
        }
        assertAll(
                () -> assertEquals(6,
                        occurrences(build, "tasks.named('runP4C2")),
                () -> assertTrue(build.contains(
                        "tasks.register('p4C2FixedHeapGate')")));
    }

    @Test
    void preservedWorldReusesTheExistingFullStoreBuilderWithoutAParallelBuilder()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var p4B2Main = read(P4_B2_PROBE_ROOT.resolve("P4B2ProbeMain.java"));
        var p4B2Builder = read(P4_B2_PROBE_ROOT.resolve("P4B2FixtureBuilder.java"));
        var storeAdapter = read(STORE_PROBE_ROOT.resolve("P4C2StoreProbe.java"));
        var c2Sources = sources(PROBE_ROOT) + "\n" + sources(STORE_PROBE_ROOT)
                + "\n" + sources(GAME_TEST_ROOT);

        assertAll(
                () -> assertTrue(build.contains("mainClass.set("
                        + "'com.yo1no.gramarye.magic.definition.store.P4B2ProbeMain')")),
                () -> assertTrue(build.contains("'prepare-full'")),
                () -> assertTrue(build.contains("p4C2PreservedRawGameDirectory")),
                () -> assertTrue(p4B2Main.contains(
                        "P4B2FixtureBuilder.prepareFull(Path.of(arguments[1]))")),
                () -> assertTrue(storeAdapter.contains("p4-b2-manifest.properties")),
                () -> assertTrue(storeAdapter.contains(
                        "FULL_STORE_MINIMUM_BYTES = 63 * 1_024 * 1_024")),
                () -> assertTrue(storeAdapter.contains(
                        "\"full-first-load-save\".equals(required(values, \"phase\"))")),
                () -> assertTrue(storeAdapter.contains("expected_store_bytes")),
                () -> assertTrue(storeAdapter.contains("canonical_store_sha256")),
                () -> assertFalse(storeAdapter.contains("P4B2FixtureManifest")),
                () -> assertFalse(storeAdapter.contains("P4B2FixtureBuilder")),
                () -> assertFalse(storeAdapter.contains("P4B2Hashing")),
                () -> assertTrue(p4B2Builder.contains(
                        "FULL_SIZE_MINIMUM_BYTES = 63 * 1_024 * 1_024")),
                () -> assertFalse(c2Sources.contains("class P4C2StoreFixtureBuilder")),
                () -> assertFalse(c2Sources.contains("66_060_348")));
    }

    @Test
    void fixturesAndLifecycleUseExactCountsAndActualPlatformPaths() throws Exception {
        var probe = sources(PROBE_ROOT);
        var gameTest = sources(GAME_TEST_ROOT);
        var fileVerifier = read(PROBE_ROOT.resolve("P4C2FileVerifier.java"));

        for (var required : List.of(
                "16_777_211",
                "16_777_212",
                "16_777_216",
                "16_777_217",
                "ready-first",
                "ready-restart",
                "preserved-raw-first",
                "preserved-raw-restart",
                "oversize-first",
                "oversize-restart")) {
            assertTrue(probe.contains(required) || gameTest.contains(required),
                    () -> "missing exact fixture/lifecycle marker: " + required);
        }
        for (var required : List.of(
                "current.setHealth(0.0F)",
                "ServerboundClientCommandPacket.Action.PERFORM_RESPAWN",
                "current.connection.handleClientCommand",
                "current.changeDimension(new DimensionTransition",
                "Level.END",
                "current.showEndCredits()",
                "server.getPlayerList().saveAll()",
                "NbtIo.readCompressed",
                "NbtAccounter.create")) {
            assertTrue(gameTest.contains(required),
                    () -> "missing actual lifecycle/readback marker: " + required);
        }
        assertAll(
                () -> assertEquals(1, occurrences(gameTest, "@GameTest(")),
                () -> assertTrue(gameTest.contains("@GameTestHolder(\"gramarye_p4_c2\")")),
                () -> assertTrue(gameTest.contains("templateNamespace = \"gramarye_p4_c2\"")),
                () -> assertTrue(fileVerifier.indexOf("P4C2StoreProbe.verifyCanonical")
                        < fileVerifier.indexOf("manifest.afterFirstRun")),
                () -> assertFalse(gameTest.contains("PlayerSkillAttachmentSerializer.INSTANCE")));
    }

    @Test
    void workflowAddsOneRequiredP4CMemoryJobAfterAllEarlierGates() throws Exception {
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var block = yamlJob(workflow, "p4-c-memory-gates");

        for (var required : List.of(
                "  p4-c-memory-gates:",
                "    name: P4-C memory gates",
                "      - build",
                "      - p4-a3-memory-gates",
                "      - p4-b-memory-gates",
                "    timeout-minutes: 30",
                "./gradlew --no-daemon --console=plain verifyP4C2Configuration",
                "./gradlew --no-daemon --console=plain p4C2FixedHeapGate")) {
            assertTrue(block.contains(required), () -> "missing P4-C CI marker: " + required);
        }
        for (var forbidden : List.of(
                "continue-on-error", "allow-failure", "|| true", "--exclude-task", "\n    if:")) {
            assertFalse(block.contains(forbidden), () -> "P4-C CI escape present: " + forbidden);
        }
    }

    @Test
    void probeSourcesDoNotOpenLaterCompositionOrProductionSurfaces() throws Exception {
        var code = sources(PROBE_ROOT) + "\n" + sources(STORE_PROBE_ROOT)
                + "\n" + sources(GAME_TEST_ROOT);
        var production = sources(PROJECT_ROOT.resolve("src/main/java"));

        for (var forbidden : List.of(
                "PendingAttachmentJournal",
                "SkillDefinitionSubmissionService",
                "RootCollector",
                "RootIndex",
                "OfflineRoot",
                "Reconciliation",
                "CustomPacketPayload",
                "PayloadRegistrar",
                "PacketDistributor",
                "net.minecraft.client",
                "java.lang.reflect",
                "setAccessible",
                "sun.misc.Unsafe",
                ".commit(",
                ".reclaim(")) {
            assertFalse(code.contains(forbidden), () -> "later/test-bypass surface: " + forbidden);
        }
        assertAll(
                () -> assertFalse(production.contains("P4C2ProbeMain")),
                () -> assertFalse(production.contains("P4C2MemoryGameTests")),
                () -> assertFalse(production.contains("@GameTestHolder(\"gramarye_p4_c2\")")),
                () -> assertEquals(12, occurrences(production, "@GameTest(")));
    }

    private static Set<String> javaFiles(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (var paths = Files.list(root)) {
            return paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private static String sources(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return "";
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(P4C2BApiGateTest::read)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String yamlJob(String yaml, String name) {
        var output = new StringBuilder();
        var active = false;
        for (var line : yaml.split("\\R", -1)) {
            if (line.equals("  " + name + ":")) {
                active = true;
            } else if (active && line.matches("  [A-Za-z0-9_-]+:")) {
                break;
            }
            if (active) {
                output.append(line).append('\n');
            }
        }
        return output.toString();
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
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
