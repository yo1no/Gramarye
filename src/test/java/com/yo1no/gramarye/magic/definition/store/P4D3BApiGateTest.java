package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact test-only source, task, CI, interruption, and later-phase gate for P4-D3-B. */
final class P4D3BApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path PROBE_ROOT = PROJECT_ROOT.resolve("src/p4D3Probe/java");
    private static final Path GAME_TEST_ROOT = PROJECT_ROOT.resolve("src/p4D3GameTest/java");
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final String HALT_CALL = "Runtime.getRuntime()." + "halt(0)";

    @Test
    void exactReviewedSourcesStayInTheTwoIsolatedD3Roots() throws Exception {
        var publicProbeTypes = P4D3PhaseTypes.D3_PROBE_SOURCE_PATHS.stream()
                .map(P4D3BApiGateTest::className)
                .map(P4D3BApiGateTest::load)
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getName)
                .collect(Collectors.toSet());
        assertAll(
                () -> assertEquals(
                        P4D3PhaseTypes.D3_PROBE_SOURCE_PATHS,
                        relativeJavaPaths(PROBE_ROOT)),
                () -> assertEquals(
                        P4D3PhaseTypes.D3_GAME_TEST_SOURCE_PATHS,
                        relativeJavaPaths(GAME_TEST_ROOT)),
                () -> assertEquals(2, P4D3PhaseTypes.D3_GAME_TEST_SOURCE_PATHS.size()),
                () -> assertEquals(
                        P4D3PhaseTypes.D3_PUBLIC_PROBE_TYPE_NAMES,
                        publicProbeTypes),
                () -> assertEquals(16, P4D3PhaseTypes.D3_SERVER_RUN_TASK_NAMES.size()),
                () -> assertEquals(16, P4D3PhaseTypes.D3_RUN_MODE_TOKENS.size()),
                () -> assertEquals(16, P4D3PhaseTypes.D3_VERIFIER_TASK_NAMES.size()),
                () -> assertEquals(32, P4D3PhaseTypes.D3_SERIAL_TASK_NAMES.size()));
    }

    @Test
    void buildDeclaresOnlyTheReviewedSourceSetsAndDedicatedRuntimeShape()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var runtimeMarker = "        p4D3HeapProbe {";
        var runtime = bracedBlock(
                build, build.indexOf('{', build.indexOf(runtimeMarker)), "p4D3HeapProbe");

        for (var required : List.of(
                "sourceSets.create('p4D3Probe')",
                "sourceSets.create('p4D3GameTest')",
                "tasks.register('generateP4D3GameTestResources', Sync)",
                "data/gramarye_p4_d3/structure/p4_d3_probe.nbt",
                "addModdingDependenciesTo(p4D3ProbeSourceSet)",
                "addModdingDependenciesTo(p4D3GameTestSourceSet)",
                "p4D3HeapProbe",
                "sourceSet(p4D3ProbeSourceSet)",
                "sourceSet(p4D3GameTestSourceSet)",
                "add(p4D3ProbeSourceSet.implementationConfigurationName, sourceSets.main.output)",
                "add(p4D3ProbeSourceSet.implementationConfigurationName, "
                        + "p4A3ProbeSourceSet.output)",
                "add(p4D3ProbeSourceSet.implementationConfigurationName, "
                        + "p4B2ProbeSourceSet.output)",
                "add(p4D3ProbeSourceSet.implementationConfigurationName, "
                        + "p4C2ProbeSourceSet.output)",
                "add(p4D3GameTestSourceSet.implementationConfigurationName, "
                        + "sourceSets.main.output)",
                "add(p4D3GameTestSourceSet.implementationConfigurationName, "
                        + "p4A3ProbeSourceSet.output)",
                "add(p4D3GameTestSourceSet.implementationConfigurationName, "
                        + "p4B2ProbeSourceSet.output)",
                "add(p4D3GameTestSourceSet.implementationConfigurationName, "
                        + "p4C2ProbeSourceSet.output)",
                "add(p4D3GameTestSourceSet.implementationConfigurationName, "
                        + "p4D3ProbeSourceSet.output)",
                "testImplementation p4D3ProbeSourceSet.output")) {
            assertTrue(build.contains(required), () -> "missing D3-B build marker: " + required);
        }

        assertAll(
                () -> assertEquals(2, occurrences(build, "sourceSets.create('p4D3")),
                () -> assertEquals(3, occurrences(runtime, "sourceSet(")),
                () -> assertEquals(1, occurrences(runtime, "sourceSet(sourceSets.main)")),
                () -> assertEquals(1, occurrences(runtime,
                        "sourceSet(p4D3ProbeSourceSet)")),
                () -> assertEquals(1, occurrences(runtime,
                        "sourceSet(p4D3GameTestSourceSet)")),
                () -> assertFalse(runtime.contains("sourceSets.test")),
                () -> assertFalse(runtime.contains("p4A3GameTestSourceSet")),
                () -> assertFalse(runtime.contains("p4B2GameTestSourceSet")),
                () -> assertFalse(runtime.contains("p4C2GameTestSourceSet")),
                () -> assertFalse(runtime.contains("p4A3ProbeSourceSet")),
                () -> assertFalse(runtime.contains("p4B2ProbeSourceSet")),
                () -> assertFalse(runtime.contains("p4C2ProbeSourceSet")),
                () -> assertFalse(build.contains("testImplementation p4D3GameTestSourceSet.output")),
                () -> assertFalse(build.contains("runtimeElements.extendsFrom p4D3")),
                () -> assertFalse(build.contains(
                        "sourceSet(p4A3GameTestSourceSet)\n"
                                + "            sourceSet(p4D3")),
                () -> assertFalse(build.contains(
                        "sourceSet(p4B2GameTestSourceSet)\n"
                                + "            sourceSet(p4D3")),
                () -> assertFalse(build.contains(
                        "sourceSet(p4C2GameTestSourceSet)\n"
                                + "            sourceSet(p4D3")));
    }

    @Test
    void sixteenFixedHeapServersAndTheirRunModesAreExact() throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));

        for (var index = 0; index < P4D3PhaseTypes.D3_SERVER_RUN_TASK_NAMES.size(); index++) {
            var runTask = P4D3PhaseTypes.D3_SERVER_RUN_TASK_NAMES.get(index);
            var runConfiguration = Character.toLowerCase(runTask.charAt(3))
                    + runTask.substring(4);
            var block = namedRunConfiguration(build, runConfiguration);
            assertTrue(!block.isEmpty(),
                    () -> "missing D3-B run configuration: " + runConfiguration);
            assertTrue(build.contains("tasks.named('" + runTask + "', JavaExec)"),
                    () -> "missing D3-B server task binding: " + runTask);
            var runMode = P4D3PhaseTypes.D3_RUN_MODE_TOKENS.get(index);
            assertTrue(block.contains("systemProperty 'gramarye.p4d3.runMode', '"
                            + runMode + "'"),
                    () -> runConfiguration + " lost exact mode " + runMode);
        }
        for (var verifier : P4D3PhaseTypes.D3_VERIFIER_TASK_NAMES) {
            assertTrue(build.contains("'" + verifier + "'"),
                    () -> "missing D3-B verifier task: " + verifier);
        }

        assertAll(
                () -> assertEquals(16,
                        occurrences(build, "jvmArguments.addAll(p4D3FixedHeapJvmArgs)")),
                () -> assertEquals(16,
                        occurrences(build, "sourceSet = p4D3GameTestSourceSet")),
                () -> assertEquals(16,
                        occurrences(build, "systemProperty 'gramarye.p4d3.runMode'")),
                () -> assertEquals(16,
                        occurrences(build, "tasks.named('runP4D3")),
                () -> assertEquals(16,
                        occurrences(build, "taskBefore(tasks.named("
                                + "p4D3GameTestSourceSet.classesTaskName))")),
                () -> assertTrue(Pattern.compile(
                                "def\\s+p4D3FixedHeapJvmArgs\\s*=\\s*\\[\\s*"
                                        + "'-Xms512m',\\s*'-Xmx1024m',\\s*"
                                        + "'-XX:\\+ExitOnOutOfMemoryError',?\\s*]",
                                Pattern.DOTALL)
                        .matcher(build)
                        .find()),
                () -> assertTrue(build.contains("Duration.ofSeconds(600)")),
                () -> assertTrue(build.contains("Duration.ofSeconds(300)")));
    }

    @Test
    void taskGraphOwnsOneImmediateDependencyChainAndOneTerminalAggregate()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));

        assertTrue(build.contains("dependsOn(prepareP4D3Worlds)"));
        for (var index = 1; index < P4D3PhaseTypes.D3_SERIAL_TASK_NAMES.size(); index++) {
            var current = P4D3PhaseTypes.D3_SERIAL_TASK_NAMES.get(index);
            var previous = P4D3PhaseTypes.D3_SERIAL_TASK_NAMES.get(index - 1);
            var block = namedTaskConfiguration(build, current);
            assertTrue(block.contains("dependsOn(" + previous + ")"),
                    () -> current + " must depend directly on " + previous);
        }

        var aggregate = registeredTaskBlock(build, "p4D3FixedHeapGate");
        assertAll(
                () -> assertTrue(build.contains("'verifyP4D3Configuration', Exec")),
                () -> assertTrue(aggregate.contains(
                        "dependsOn(verifyP4D3CombinedHeapRestart)")),
                () -> assertEquals(1, occurrences(aggregate, "dependsOn(")),
                () -> assertFalse(aggregate.contains("runP4D3")));
    }

    @Test
    void crashMatrixFixtureAndHardHaltContractsAreExact() throws Exception {
        var probe = sources(PROBE_ROOT);
        var gameTest = sources(GAME_TEST_ROOT);
        var allD3 = probe + "\n" + gameTest;
        var manifest = read(PROBE_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/P4D3FixtureManifest.java"));
        var verifier = read(PROBE_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/store/P4D3FileVerifier.java"));

        for (var required : List.of(
                "66_060_348",
                "2_048",
                "4_095",
                "4_096",
                "1_048_538",
                "CRASH_D_RESTART",
                "CRASH_E_RESTART",
                "CRASH_F_RESTART",
                "CRASH_G_RESTART",
                "CRASH_H_RESTART",
                "CRASH_I_RESTART",
                "CRASH_J1_RESTART",
                "COMBINED_RESTART",
                "G_REPLAY_APPLIED_PLAYERDATA_NOT_SAVED",
                "H_CLEAR_IN_MEMORY_NOT_SAVED")) {
            assertTrue(allD3.contains(required),
                    () -> "missing D3-B fixture/lifecycle marker: " + required);
        }

        assertAll(
                () -> assertEquals(2,
                        occurrences(gameTest, HALT_CALL)),
                () -> assertEquals(2,
                        occurrences(allD3, HALT_CALL)),
                () -> assertFalse(probe.contains(HALT_CALL)),
                () -> assertEquals(1, occurrences(gameTest, "@GameTest(")),
                () -> assertTrue(gameTest.contains("@GameTestHolder(\"gramarye_p4_d3\")")),
                () -> assertTrue(gameTest.contains("templateNamespace = \"gramarye_p4_d3\"")),
                () -> assertTrue(manifest.contains("static final long MAX_BYTES = 4_096")),
                () -> assertTrue(manifest.contains("channel.force(true)")),
                () -> assertTrue(manifest.contains("values.stringPropertyNames().equals(KEYS)")),
                () -> assertTrue(verifier.contains("SkillSavedDataPrimaryIngress.load(")),
                () -> assertTrue(verifier.contains("PendingAttachmentJournalFraming.load(")),
                () -> assertTrue(verifier.contains("sourcePending.contentEquals(")),
                () -> assertTrue(verifier.contains("auditJournalTargets(journal)")),
                () -> assertTrue(verifier.contains("P4D3PlayerProbe.readPlayerdata(")),
                () -> assertTrue(verifier.contains("requireManifestMatches(manifest, actual)")));
    }

    @Test
    void workflowAddsOneRequiredP4DMemoryJobAfterEveryEarlierGate() throws Exception {
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var block = yamlJob(workflow, "p4-d-memory-gates");

        for (var required : List.of(
                "  p4-d-memory-gates:",
                "    name: P4-D memory gates",
                "      - build",
                "      - p4-a3-memory-gates",
                "      - p4-b-memory-gates",
                "      - p4-c-memory-gates",
                "    timeout-minutes: 45",
                "./gradlew --no-daemon --console=plain verifyP4D3Configuration",
                "./gradlew --no-daemon --console=plain p4D3FixedHeapGate")) {
            assertTrue(block.contains(required), () -> "missing P4-D CI marker: " + required);
        }
        for (var forbidden : List.of(
                "continue-on-error", "allow-failure", "|| true", "--exclude-task", "\n    if:")) {
            assertFalse(block.contains(forbidden), () -> "P4-D CI escape present: " + forbidden);
        }
    }

    @Test
    void dedicatedSourcesDoNotLeakJUnitOrOpenP4EProductionSurfaces() throws Exception {
        var dedicated = sources(PROBE_ROOT) + "\n" + sources(GAME_TEST_ROOT);
        var production = sources(MAIN_JAVA);

        for (var forbidden : List.of(
                "org.junit",
                "Thread.sleep(",
                "System.gc(",
                "java.lang.reflect",
                "setAccessible(",
                "sun.misc.Unsafe",
                "@SuppressWarnings",
                ".reclaim(",
                "OfflineRoot",
                "RootCollector",
                "RootIndex",
                "Reconciliation",
                "CustomPacketPayload",
                "PayloadRegistrar",
                "PacketDistributor",
                "net.minecraft.client")) {
            assertFalse(dedicated.contains(forbidden), () -> "D3-B bypass/later surface: " + forbidden);
        }

        assertAll(
                () -> assertFalse(production.contains(HALT_CALL)),
                () -> assertFalse(production.contains("P4D3ProbeMain")),
                () -> assertFalse(production.contains("@GameTestHolder(\"gramarye_p4_d3\")")),
                () -> assertEquals(19, occurrences(production, "@GameTest(")));
    }

    private static String namedTaskConfiguration(String build, String taskName) {
        var marker = taskName + ".configure";
        var markerIndex = build.indexOf(marker);
        if (markerIndex < 0) {
            throw new AssertionError("task configuration not found: " + taskName);
        }
        return bracedBlock(build, build.indexOf('{', markerIndex + marker.length()), taskName);
    }

    private static String namedRunConfiguration(String build, String runConfiguration) {
        var marker = "        " + runConfiguration + " {";
        var markerIndex = build.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        return bracedBlock(build, build.indexOf('{', markerIndex), runConfiguration);
    }

    private static String registeredTaskBlock(String source, String taskName) {
        var marker = "tasks.register('" + taskName + "')";
        var markerIndex = source.indexOf(marker);
        if (markerIndex < 0) {
            throw new AssertionError("task registration not found: " + taskName);
        }
        return bracedBlock(
                source,
                source.indexOf('{', markerIndex + marker.length()),
                taskName);
    }

    private static String bracedBlock(String source, int open, String description) {
        if (open < 0) {
            throw new AssertionError("block did not open: " + description);
        }
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("block did not close: " + description);
    }

    private static Set<String> relativeJavaPaths(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".java"))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace(java.io.File.separatorChar, '/'))
                    .collect(Collectors.toSet());
        }
    }

    private static String className(String sourcePath) {
        return sourcePath.substring(0, sourcePath.length() - ".java".length())
                .replace('/', '.');
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, P4D3BApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to load reviewed D3-B probe " + className, exception);
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
                    .map(P4D3BApiGateTest::read)
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
