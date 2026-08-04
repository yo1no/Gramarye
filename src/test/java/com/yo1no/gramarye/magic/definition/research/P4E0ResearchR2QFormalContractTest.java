package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Closed B1 vocabulary, task graph, runtime, and phase-boundary contract. */
final class P4E0ResearchR2QFormalContractTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path FORMAL_MAIN = research("P4E0R2QFormalMain.java");
    private static final Path FORMAL_RESULT = research("P4E0R2QFormalResult.java");
    private static final Path FORMAL_EVIDENCE = research("P4E0R2QFormalEvidence.java");
    private static final Path FORMAL_WORKLOAD = research("P4E0R2QFormalWorkload.java");
    private static final Path FORMAL_DRIVER = dedicated("P4E0R2QFormalDedicatedDriver.java");

    @TempDir
    Path temporaryDirectory;

    @Test
    void processAndQualificationTaxonomiesStaySeparateAndClosed() {
        var process = Arrays.stream(
                        P4E0R2QFormalResult.ProcessClassification.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        var qualification = Arrays.stream(
                        P4E0R2QFormalResult.QualificationResult.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(P4E0ResearchPhaseTypes.CLASSIFICATION_NAMES, process),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.R2Q_QUALIFICATION_NAMES, qualification),
                () -> assertTrue(process.stream().noneMatch(qualification::contains)),
                () -> assertFalse(process.contains("REJECTED_EXPECTED_COUNTER")),
                () -> assertFalse(qualification.contains("REJECTED_BY_RESEARCH_GUARD")));
    }

    @Test
    void parentClassificationUsesDiskFactsAndKeepsOomeAndTimeoutDistinct() {
        assertAll(
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.OOME_EXIT,
                        P4E0R2QFormalMain.classifyParentEvidence(
                                3, "TIMEOUT\n", true, true)),
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.TIMEOUT,
                        P4E0R2QFormalMain.classifyParentEvidence(
                                124, "TIMEOUT\n", false, true)),
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE,
                        P4E0R2QFormalMain.classifyParentEvidence(
                                124, "RUNNING\n", false, false)),
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                        P4E0R2QFormalMain.classifyParentEvidence(
                                0, "COMPLETED\n", true, false)),
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE,
                        P4E0R2QFormalMain.classifyParentEvidence(
                                0, "COMPLETED\n", false, false)));
    }

    @Test
    void gradleDeclaresExactlyTwentyNineDedicatedCasesAndTheFixedRuntime() {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        assertAll(
                () -> assertTrue(build.contains(
                        "def p4E0R2QFormalCaseIndices = (0..<29).toList()")),
                () -> assertTrue(build.contains("p4E0R2QFormalCaseIndices.each")),
                () -> assertTrue(build.contains(
                        "\"p4E0R2QCase${formalCaseToken}\"")),
                () -> assertTrue(build.contains("sourceSet = p4E0ResearchGameTestSourceSet")),
                () -> assertTrue(build.contains(
                        "systemProperty 'gramarye.p4e0.research.runMode', 'r2q-formal'")),
                () -> assertTrue(build.contains(
                        "systemProperty 'gramarye.p4e0.r2q.formal.caseIndex'")),
                () -> assertTrue(build.contains(
                        "systemProperty 'gramarye.p4e0.r2q.formal.childResult'")),
                () -> assertTrue(build.contains(
                        "systemProperty 'gramarye.p4e0.r2q.formal.runningMarker'")),
                () -> assertTrue(build.contains(
                        "def p4E0R2QFormalTimeoutSeconds = 900")),
                () -> assertTrue(build.contains(
                        "def p4E0R2QFormalWatchdogSeconds = 870")),
                () -> assertTrue(build.contains(
                        "def p4E0R2QFormalDiskBudgetBytes = '12884901888'")),
                () -> assertTrue(build.contains("'-Xms512m'")),
                () -> assertTrue(build.contains("'-Xmx1536m'")),
                () -> assertTrue(build.contains("'-XX:+ExitOnOutOfMemoryError'")),
                () -> assertTrue(build.contains(
                        "p4E0R2QConfiguredFormalRunNames.contains(name)")),
                () -> assertTrue(build.contains(
                        "\"captureP4E0R2QCase${formalCaseToken}ParentFailure\"")),
                () -> assertTrue(build.contains("finalizedBy(captureFailureTask)")),
                () -> assertTrue(build.contains(
                        "java.nio.file.StandardOpenOption.CREATE_NEW")),
                () -> assertTrue(build.contains("channel.force(true)")),
                () -> assertFalse(build.contains("name.startsWith('p4E0R2QCase')")));
    }

    @Test
    void formalAndSmokeOutputsArePhysicallyDisjoint() {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        assertAll(
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('reports/p4-e0-r2q')")),
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('reports/p4-e0-r2q-smoke/runtime')")),
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('reports/p4-e0-r2q-runner-smoke/runner')")),
                () -> assertFalse(build.contains(
                        "layout.buildDirectory.dir('reports/p4-e0-r2q/smoke')")),
                () -> assertTrue(build.contains("runP4E0R2QSupervisorSmoke")),
                () -> assertTrue(build.contains("runP4E0R2QRunnerDedicatedSmoke")),
                () -> assertTrue(build.contains("verifyP4E0R2QSupervisorSmoke")));
    }

    @Test
    void formalGateOwnsEveryFailClosedPreconditionBeforeCaseZero() {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var gate = bracedBlock(build, "def requireP4E0R2QFormalGate = {");
        var prepare = bracedBlock(build,
                "prepareP4E0R2QFormalStudy.configure {");
        var run = bracedBlock(build, "        runCaseTask.configure {");

        for (var marker : List.of(
                "p4E0R2QFormalMode.isPresent()",
                "p4E0R2QFormalMode.get() != 'true'",
                "p4E0R2QFormalDiskBudget.isPresent()",
                "p4E0R2QFormalDiskBudget.get() != p4E0R2QFormalDiskBudgetBytes",
                "p4E0R2QGitStatus.get().isEmpty()",
                "p4E0R2QGitUntracked.get().isEmpty()",
                "p4E0R2QGitDiff.result.get().exitValue != 0",
                "p4E0R2QGitCachedDiff.result.get().exitValue != 0",
                "p4E0R2QGitBranch.result.get().exitValue != 0",
                "p4E0R2QGitBranch.standardOutput.asText.get().trim() != 'main'",
                "head != originMain")) {
            assertTrue(gate.contains(marker), () -> "missing formal gate: " + marker);
        }
        assertAll(
                () -> assertTrue(prepare.contains("requireP4E0R2QFormalGate()")),
                () -> assertTrue(run.contains("requireP4E0R2QFormalGate()")),
                () -> assertTrue(build.contains(
                        "P4-E0-R2Q refused stale per-case process evidence")),
                () -> assertTrue(build.contains(
                        "P4-E0-R2Q formal task inventory drift")));
    }

    @Test
    void normalVerificationEntriesDoNotDependOnFormalStudyTasks() {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        for (var marker : List.of(
                "tasks.named('test', Test).configure {",
                "tasks.register('p4E0ResearchSmoke') {",
                "tasks.register('p4E0R2QSmoke') {")) {
            var block = bracedBlock(build, marker);
            assertFalse(block.contains("P4E0R2QFormal")
                            || block.contains("p4E0R2QStudy")
                            || block.contains("P4E0R2QCase"),
                    () -> "normal task owns formal dependency: " + marker);
        }
    }

    @Test
    void dedicatedEntryDispatchesOneCaseAndOwnsTheIndependentWatchdog() {
        var driver = read(FORMAL_DRIVER);
        assertAll(
                () -> assertTrue(driver.contains("870")),
                () -> assertTrue(driver.contains("Runtime.getRuntime().halt(")),
                () -> assertEquals(1, occurrences(driver, "Runtime.getRuntime().halt(")),
                () -> assertTrue(driver.contains("formal.caseIndex")),
                () -> assertFalse(driver.contains("for (var spec")),
                () -> assertFalse(driver.contains("System.gc(")),
                () -> assertFalse(driver.contains("Thread.sleep(")),
                () -> assertFalse(driver.contains("System.exit(")),
                () -> assertFalse(driver.matches(
                        "(?s).*catch\\s*\\([^)]*(Error|OutOfMemoryError).*")));
    }

    @Test
    void caseCleanupDeletesOnlyLargeFixtureAndGameTrees() throws Exception {
        var caseRoot = temporaryDirectory.resolve("case");
        var fixture = caseRoot.resolve(P4E0R2QFormalWorkload.LARGE_FIXTURE_DIRECTORY)
                .resolve("record.dat");
        var game = caseRoot.resolve(P4E0R2QFormalWorkload.GAME_DIRECTORY)
                .resolve("world/level.dat");
        var manifest = caseRoot.resolve(P4E0R2QFormalWorkload.CASE_MANIFEST);
        var result = caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT);
        Files.createDirectories(fixture.getParent());
        Files.createDirectories(game.getParent());
        Files.writeString(fixture, "large fixture stand-in");
        Files.writeString(game, "large world stand-in");
        Files.writeString(manifest, "bounded manifest");
        Files.writeString(result, "bounded result");

        P4E0R2QFormalWorkload.cleanupLargeCaseData(caseRoot);

        assertAll(
                () -> assertFalse(Files.exists(fixture)),
                () -> assertFalse(Files.exists(game)),
                () -> assertFalse(Files.exists(caseRoot.resolve(
                        P4E0R2QFormalWorkload.LARGE_FIXTURE_DIRECTORY))),
                () -> assertFalse(Files.exists(caseRoot.resolve(
                        P4E0R2QFormalWorkload.GAME_DIRECTORY))),
                () -> assertTrue(Files.isRegularFile(manifest)),
                () -> assertTrue(Files.isRegularFile(result)));
    }

    @Test
    void formalSourcesRemainResearchOnlyBoundedAndNonNormative() throws Exception {
        var formal = List.of(
                        FORMAL_MAIN, FORMAL_RESULT, FORMAL_EVIDENCE,
                        FORMAL_WORKLOAD, FORMAL_DRIVER)
                .stream()
                .map(P4E0ResearchR2QFormalContractTest::read)
                .collect(Collectors.joining("\n"));
        var production = sources(PROJECT_ROOT.resolve("src/main"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));

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
                "RootIndex",
                "Reconciliation",
                "CustomPacketPayload",
                "net.minecraft.client")) {
            assertFalse(formal.contains(forbidden),
                    () -> "formal research source opened forbidden seam: " + forbidden);
        }
        assertAll(
                () -> assertFalse(formal.matches(
                        "(?s).*catch\\s*\\([^)]*(Error|OutOfMemoryError).*")),
                () -> assertFalse(production.contains("P4E0R2QFormal")),
                () -> assertFalse(workflow.contains("p4-e0-r2q")),
                () -> assertFalse(workflow.contains("P4-E0-R2Q")),
                () -> assertEquals(
                        Set.of(
                                "P4E0R2QFormalEvidence.java",
                                "P4E0R2QFormalMain.java",
                                "P4E0R2QFormalResult.java",
                                "P4E0R2QFormalWorkload.java"),
                        formalResearchNames()));
    }

    private static Set<String> formalResearchNames() throws IOException {
        try (var paths = Files.list(FORMAL_MAIN.getParent())) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("P4E0R2QFormal"))
                    .collect(Collectors.toSet());
        }
    }

    private static String sources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(P4E0ResearchR2QFormalContractTest::read)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static Path research(String name) {
        return required(PROJECT_ROOT.resolve(
                "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                        + name));
    }

    private static Path dedicated(String name) {
        return required(PROJECT_ROOT.resolve(
                "src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/"
                        + "research/" + name));
    }

    private static Path required(Path path) {
        assertTrue(Files.isRegularFile(path) && !Files.isSymbolicLink(path),
                () -> "reviewed formal source is missing: " + path);
        return path;
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
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
