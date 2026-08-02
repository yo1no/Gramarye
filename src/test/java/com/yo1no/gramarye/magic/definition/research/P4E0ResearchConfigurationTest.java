package com.yo1no.gramarye.magic.definition.research;

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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact source-set, task, phase-boundary, and portable-verifier gate for P4-E0-R1. */
final class P4E0ResearchConfigurationTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path RESEARCH_ROOT =
            PROJECT_ROOT.resolve("src/p4E0Research/java");
    private static final Path RESEARCH_RESOURCE_ROOT =
            PROJECT_ROOT.resolve("src/p4E0Research/resources");
    private static final Path GAME_TEST_ROOT =
            PROJECT_ROOT.resolve("src/p4E0ResearchGameTest/java");
    private static final Path TEST_ROOT = PROJECT_ROOT.resolve("src/test/java");
    private static final Path MAIN_ROOT = PROJECT_ROOT.resolve("src/main");

    @Test
    void exactResearchSourcesResourcesAndPublicTypesStayClosed() throws Exception {
        var publicTypes = P4E0ResearchPhaseTypes.RESEARCH_SOURCE_PATHS.stream()
                .map(P4E0ResearchConfigurationTest::className)
                .map(P4E0ResearchConfigurationTest::load)
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.RESEARCH_SOURCE_PATHS,
                        relativeRegularFiles(RESEARCH_ROOT, ".java")),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.GAME_TEST_SOURCE_PATHS,
                        relativeRegularFiles(GAME_TEST_ROOT, ".java")),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.RESOURCE_PATHS,
                        relativeRegularFiles(RESEARCH_RESOURCE_ROOT, "")),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.UNIT_SOURCE_PATHS,
                        relativeP4E0UnitSources()),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.PUBLIC_RESEARCH_TYPE_NAMES,
                        publicTypes));
    }

    @Test
    void buildOwnsTwoResearchSourceSetsAndOneIsolatedDedicatedRuntime()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var runtime = bracedBlock(build, "        p4E0ResearchHarness {");
        var run = bracedBlock(build, "        p4E0ResearchDedicatedSmoke {");

        for (var required : List.of(
                "sourceSets.create('p4E0Research')",
                "sourceSets.create('p4E0ResearchGameTest')",
                "addModdingDependenciesTo(p4E0ResearchSourceSet)",
                "addModdingDependenciesTo(p4E0ResearchGameTestSourceSet)",
                "p4E0ResearchHarness",
                "sourceSet(sourceSets.main)",
                "sourceSet(p4A3ProbeSourceSet)",
                "sourceSet(p4B2ProbeSourceSet)",
                "sourceSet(p4C2ProbeSourceSet)",
                "sourceSet(p4D3ProbeSourceSet)",
                "sourceSet(p4E0ResearchSourceSet)",
                "sourceSet(p4E0ResearchGameTestSourceSet)",
                "add(p4E0ResearchSourceSet.implementationConfigurationName, "
                        + "sourceSets.main.output)",
                "add(p4E0ResearchSourceSet.implementationConfigurationName, "
                        + "p4A3ProbeSourceSet.output)",
                "add(p4E0ResearchSourceSet.implementationConfigurationName, "
                        + "p4B2ProbeSourceSet.output)",
                "add(p4E0ResearchSourceSet.implementationConfigurationName, "
                        + "p4C2ProbeSourceSet.output)",
                "add(p4E0ResearchSourceSet.implementationConfigurationName, "
                        + "p4D3ProbeSourceSet.output)",
                "add(p4E0ResearchSourceSet.implementationConfigurationName, "
                        + "commonsCompressCoordinate)",
                "add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, "
                        + "p4E0ResearchSourceSet.output)",
                "testImplementation p4E0ResearchSourceSet.output")) {
            assertTrue(build.contains(required), () -> "missing R1 build marker: " + required);
        }

        assertAll(
                () -> assertEquals(2, occurrences(
                        build, "sourceSets.create('p4E0Research")),
                () -> assertEquals(7, occurrences(runtime, "sourceSet(")),
                () -> assertEquals(1, occurrences(runtime, "sourceSet(sourceSets.main)")),
                () -> assertEquals(1, occurrences(
                        runtime, "sourceSet(p4A3ProbeSourceSet)")),
                () -> assertEquals(1, occurrences(
                        runtime, "sourceSet(p4B2ProbeSourceSet)")),
                () -> assertEquals(1, occurrences(
                        runtime, "sourceSet(p4C2ProbeSourceSet)")),
                () -> assertEquals(1, occurrences(
                        runtime, "sourceSet(p4D3ProbeSourceSet)")),
                () -> assertEquals(1, occurrences(
                        runtime, "sourceSet(p4E0ResearchSourceSet)")),
                () -> assertEquals(1, occurrences(
                        runtime, "sourceSet(p4E0ResearchGameTestSourceSet)")),
                () -> assertFalse(runtime.contains("sourceSets.test")),
                () -> assertFalse(runtime.contains("GameTestSourceSet.output")),
                () -> assertFalse(build.contains(
                        "testImplementation p4E0ResearchGameTestSourceSet.output")),
                () -> assertFalse(build.contains("runtimeElements.extendsFrom p4E0")),
                () -> assertTrue(run.contains("sourceSet = p4E0ResearchGameTestSourceSet")),
                () -> assertTrue(run.contains(
                        "systemProperty 'neoforge.enabledGameTestNamespaces'")),
                () -> assertTrue(run.contains("'gramarye_p4_e0_research'")),
                () -> assertTrue(run.contains(
                        "systemProperty 'gramarye.p4e0.research.runMode', "
                                + "'dedicated-smoke'")),
                () -> assertTrue(run.contains(
                        "jvmArguments.addAll(p4E0ResearchFixedHeapJvmArgs)")));
    }

    @Test
    void exactTaskChainUsesJava21FixedHeapAndBuildOnlyOutputs() throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        for (var task : P4E0ResearchPhaseTypes.TASK_NAMES) {
            if (task.startsWith("compileP4E0Research")) {
                continue;
            }
            assertTrue(build.contains(task), () -> "missing R1 task: " + task);
        }

        assertAll(
                () -> assertTrue(build.contains(
                        "tasks.named(p4E0ResearchSourceSet.compileJavaTaskName")),
                () -> assertTrue(build.contains(
                        "tasks.named(p4E0ResearchGameTestSourceSet.compileJavaTaskName")),
                () -> assertTrue(build.contains("'-Xms512m'")),
                () -> assertTrue(build.contains(
                        "def p4E0ResearchDefaultHeapMiB = '1024'")),
                () -> assertTrue(build.contains(
                        ".systemProperty('gramarye.p4e0.research.heapMiB')")),
                () -> assertTrue(build.contains(
                        "\"-Xmx${p4E0ResearchHeapMiB}m\"")),
                () -> assertTrue(build.contains("'-XX:+ExitOnOutOfMemoryError'")),
                () -> assertTrue(build.contains("p4E0ResearchOverrideProperties")),
                () -> assertTrue(build.contains(
                        "providers.systemProperty(propertyName)")),
                () -> assertTrue(build.contains(
                        "'gramarye.p4e0.research.scenario',")),
                () -> assertFalse(build.contains(
                        "'gramarye.p4e0.research.scenarioCase',")),
                () -> assertTrue(build.contains(
                        "def p4E0ResearchChildTimeoutSeconds = 540")),
                () -> assertTrue(build.contains(
                        "def p4E0ResearchChildTerminationBudgetSeconds = 30")),
                () -> assertTrue(build.contains(
                        "def p4E0ResearchSupervisorTimeoutSeconds = 600")),
                () -> assertTrue(build.contains(
                        "<= p4E0ResearchChildTimeoutSeconds "
                                + "+ p4E0ResearchChildTerminationBudgetSeconds")),
                () -> assertEquals(6, occurrences(
                        build, "p4E0ResearchSupervisorTimeoutSeconds")),
                () -> assertTrue(build.contains("javaLauncher.set(p4A3JavaLauncher)")),
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('p4-e0-research/smoke')")),
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('reports/p4-e0-research')")),
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('p4-e0-research/command-work')")),
                () -> assertTrue(build.contains(
                        "workingDir(p4E0ResearchCommandWorkingDirectory)")),
                () -> assertTrue(build.contains(
                        "p4E0ResearchCommandWorkingDirectory.get().asFile.mkdirs()")),
                () -> assertTrue(build.contains(
                        "'cleanP4E0ResearchLauncherLogs', Delete")),
                () -> assertTrue(build.contains(
                        "'cleanP4E0ResearchPostRunLogs', Delete")),
                () -> assertTrue(build.contains(
                        "delete(layout.projectDirectory.dir('logs'))")),
                () -> assertEquals(2, occurrences(
                        build, "delete(layout.projectDirectory.dir('logs'))")),
                () -> assertTrue(build.contains(
                        "dependsOn(cleanP4E0ResearchLauncherLogs)")),
                () -> assertTrue(build.contains(
                        "dependsOn(verifyP4E0ResearchConfiguration)")),
                () -> assertTrue(build.contains(
                        "dependsOn(prepareP4E0ResearchSmoke)")),
                () -> assertTrue(build.contains("dependsOn(runP4E0ResearchSmoke)")),
                () -> assertTrue(build.contains(
                        "dependsOn(runP4E0ResearchDedicatedSmoke)")),
                () -> assertTrue(build.contains("ignoreExitValue = true")),
                () -> assertTrue(build.contains(
                        "executionResult.get().exitValue")),
                () -> assertTrue(build.contains(
                        "finalizedBy(classifyP4E0ResearchDedicatedSmoke)")),
                () -> assertTrue(build.contains(
                        "finalizedBy(cleanP4E0ResearchPostRunLogs)")),
                () -> assertTrue(build.contains(
                        "mustRunAfter(classifyP4E0ResearchDedicatedSmoke)")),
                () -> assertTrue(build.contains("dedicated-child.json")),
                () -> assertTrue(build.contains("dedicated-exit-code.txt")),
                () -> assertTrue(build.contains("dedicated-running-v0.txt")),
                () -> assertTrue(build.contains(
                        "dependsOn(verifyP4E0ResearchSmokeOutput)")),
                () -> assertFalse(build.contains("p4E0ResearchSmokeDirectory = "
                        + "layout.projectDirectory")));
    }

    @Test
    void dedicatedTimeoutNeedsAnExactRunningMarkerAndActualExitRemainsAuthoritative()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var main = read(RESEARCH_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/P4E0ResearchMain.java"));
        var coordinator = read(GAME_TEST_ROOT.resolve(
                "com/yo1no/gramarye/magic/definition/research/"
                        + "P4E0ResearchDedicatedCoordinator.java"));

        assertAll(
                () -> assertTrue(coordinator.contains(
                        "P4E0ResearchMain.markDedicatedRunning(reportRoot);")),
                () -> assertTrue(main.contains(
                        "DEDICATED_RUNNING_CONTENT = \"P4_E0_RESEARCH_RUNNING_V0\\n\"")),
                () -> assertTrue(main.contains(
                        "classifyDedicatedMissingExit(exactRunningMarker)")),
                () -> assertTrue(main.contains(
                        "input.readNBytes(expectedBytes.length + 1)")),
                () -> assertTrue(build.contains(
                        "executionResult.get().exitValue")),
                () -> assertTrue(build.contains(
                        "p4E0ResearchDedicatedRunningMarker,")));
    }

    @Test
    void researchSourcesContainNoProductionOrUnsafeEscape() throws Exception {
        var research = sources(RESEARCH_ROOT);
        var dedicated = sources(GAME_TEST_ROOT);
        var combined = research + '\n' + dedicated;
        var production = sources(MAIN_ROOT);

        for (var forbidden : List.of(
                "org.junit",
                "System.gc(",
                "Thread.sleep(",
                "Files.readAllBytes",
                "NbtAccounter.unlimitedHeap",
                "java.lang.reflect",
                "setAccessible(",
                "sun.misc.Unsafe",
                ".reclaim(",
                "ServerStartingEvent",
                "PlayerLoggedInEvent",
                "RootIndex",
                "Reconciliation",
                "CustomPacketPayload",
                "net.minecraft.client")) {
            assertFalse(combined.contains(forbidden),
                    () -> "R1 research surface contains forbidden token: " + forbidden);
        }
        assertFalse(combined.matches("(?s).*catch\\s*\\([^)]*OutOfMemoryError.*"));

        assertAll(
                () -> assertFalse(production.contains("P4E0Research")),
                () -> assertFalse(production.contains("p4-e0-research")),
                () -> assertFalse(read(PROJECT_ROOT.resolve(
                                "src/main/java/com/yo1no/gramarye/magic/limits/"
                                        + "MagicSafetyCeilings.java"))
                        .contains("MAX_PLAYERDATA_")),
                () -> assertFalse(read(PROJECT_ROOT.resolve(
                                ".github/workflows/build.yml"))
                        .contains("p4-e0-research")));
    }

    @Test
    void portableVerifierIsExecutableSelfTestingAndDeveloperToolFree()
            throws Exception {
        var scriptPath = PROJECT_ROOT.resolve(
                "scripts/verify-p4-e0-r-configuration.sh");
        var script = read(scriptPath);

        assertAll(
                () -> assertTrue(Files.isRegularFile(scriptPath)),
                () -> assertFalse(Files.isSymbolicLink(scriptPath)),
                () -> assertTrue(Files.isExecutable(scriptPath)),
                () -> assertTrue(script.startsWith("#!/usr/bin/env bash\nset -euo pipefail\n")),
                () -> assertTrue(script.contains("PATH='/usr/bin:/bin'")),
                () -> assertTrue(script.contains("${BASH_SOURCE[0]}")),
                () -> assertTrue(script.contains("trap cleanup EXIT HUP INT TERM")),
                () -> assertTrue(script.contains("EXPECTED_MISSING")),
                () -> assertTrue(script.contains("EXPECTED_FORBIDDEN")),
                () -> assertTrue(script.contains("EXPECTED_COUNT")),
                () -> assertTrue(script.contains("grep failed while checking")),
                () -> assertFalse(script.contains("is_transient_launcher_log_path")),
                () -> assertFalse(script.contains("logs/debug")),
                () -> assertFalse(script.contains("logs/latest")),
                () -> assertFalse(script.matches(
                        "(?s).*(^|[;&|()<>`\\s])([^;&|()<>`\\s]*/)?r[g]"
                                + "([;&|()<>`\\s]|$).*")));
    }

    private static Set<String> relativeP4E0UnitSources() throws IOException {
        try (var paths = Files.walk(TEST_ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .startsWith("P4E0Research"))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(TEST_ROOT::relativize)
                    .map(Path::toString)
                    .map(P4E0ResearchConfigurationTest::portable)
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> relativeRegularFiles(Path root, String suffix)
            throws IOException {
        if (!Files.isDirectory(root)) {
            return Set.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> suffix.isEmpty()
                            || path.getFileName().toString().endsWith(suffix))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(P4E0ResearchConfigurationTest::portable)
                    .collect(Collectors.toSet());
        }
    }

    private static String className(String sourcePath) {
        return sourcePath.substring(0, sourcePath.length() - ".java".length())
                .replace('/', '.');
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(
                    className, false, P4E0ResearchConfigurationTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Unable to load reviewed R1 type " + className, exception);
        }
    }

    private static String sources(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return "";
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(P4E0ResearchConfigurationTest::read)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String bracedBlock(String source, String marker) {
        var markerIndex = source.indexOf(marker);
        if (markerIndex < 0) {
            throw new AssertionError("block marker not found: " + marker);
        }
        var open = source.indexOf('{', markerIndex);
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("unclosed block: " + marker);
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static String portable(String path) {
        return path.replace(java.io.File.separatorChar, '/');
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
