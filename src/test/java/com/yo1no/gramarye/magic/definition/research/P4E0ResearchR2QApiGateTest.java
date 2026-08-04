package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Phase-local task, runtime, formal-entry, and production-isolation gate for R2Q-A. */
final class P4E0ResearchR2QApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final List<String> COUNTER_SLUGS = List.of(
            "directory_entries",
            "relevant_records",
            "compressed_bytes_per_file",
            "decompressed_bytes_per_file",
            "container_depth_per_file",
            "compound_containers_per_file",
            "compound_field_entries_per_file",
            "list_elements_per_file",
            "byte_array_elements_per_file",
            "int_array_elements_per_file",
            "long_array_elements_per_file",
            "modified_utf8_bytes_per_file",
            "scalar_tags_per_file",
            "compressed_bytes_total",
            "decompressed_bytes_total",
            "compound_containers_total",
            "compound_field_entries_total",
            "list_elements_total",
            "byte_array_elements_total",
            "int_array_elements_total",
            "long_array_elements_total",
            "modified_utf8_bytes_total",
            "scalar_tags_total",
            "attachment_admissions",
            "raw_root_claims");
    private static final List<String> R2Q_RESEARCH_PATHS = List.of(
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/player/"
                    + "P4E0ResearchAttachmentFixtures.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchWireNbt.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QAuditBudget.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QCasePlan.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QFixturePlan.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QFormalEvidence.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QFormalMain.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QFormalResult.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QFormalWorkload.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QJointRecords.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QMain.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QModifiedUtf.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QPositiveWitnesses.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QProfile.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QStudyIdentity.java",
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0R2QStoreJournalFixtures.java");
    private static final List<String> R2Q_GAME_PATHS = List.of(
            "src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/"
                    + "research/P4E0ResearchDedicatedCoordinator.java",
            "src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/"
                    + "research/P4E0R2QFormalDedicatedDriver.java",
            "src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/"
                    + "research/P4E0R2QDedicatedDriver.java");

    @Test
    void reducedSmokeIsHardOrderedAndUsesOnlyTheExistingResearchRuntime()
            throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var runtime = bracedBlock(build, "        p4E0ResearchHarness {");
        var run = bracedBlock(build, "        p4E0R2QDedicatedSmoke {");
        var dedicatedDependencies = matchingLines(
                build, "p4E0ResearchGameTestSourceSet.implementationConfigurationName");

        assertAll(
                () -> assertEquals(2, occurrences(
                        build, "sourceSets.create('p4E0Research")),
                () -> assertEquals(7, occurrences(runtime, "sourceSet(")),
                () -> assertFalse(runtime.contains("sourceSets.test")),
                () -> assertFalse(runtime.contains("GameTestSourceSet.output")),
                () -> assertTrue(run.contains(
                        "sourceSet = p4E0ResearchGameTestSourceSet")),
                () -> assertTrue(run.contains("'gramarye_p4_e0_research'")),
                () -> assertTrue(run.contains("'r2q-smoke'")),
                () -> assertTrue(run.contains(
                        "jvmArguments.addAll(p4E0R2QSmokeJvmArgs)")),
                () -> assertTrue(build.contains(
                        "def p4E0R2QSmokeTimeoutSeconds = 600")),
                () -> assertEquals(5, occurrences(build,
                        "Duration.ofSeconds(p4E0R2QSmokeTimeoutSeconds)")),
                () -> assertTrue(build.contains(
                        "p4E0R2QDedicatedGameDirectory.get().asFile.deleteDir()")),
                () -> assertTrue(build.contains(
                        "'verifyP4E0R2QPreflightTests')")),
                () -> assertEquals(7, occurrences(build,
                        "add(p4E0ResearchGameTestSourceSet."
                                + "implementationConfigurationName")),
                () -> assertEquals(7, occurrences(build,
                        "p4E0ResearchGameTestSourceSet."
                                + "implementationConfigurationName")),
                () -> assertFalse(dedicatedDependencies.toLowerCase(Locale.ROOT)
                        .contains("junit")),
                () -> assertFalse(build.contains(
                        "p4E0ResearchGameTestImplementation")),
                () -> assertFalse(build.contains(
                        "p4E0ResearchGameTestRuntimeOnly")),
                () -> assertFalse(build.contains(
                        "p4E0ResearchGameTestSourceSet.runtimeOnlyConfigurationName")),
                () -> assertFalse(build.contains(
                        "p4E0ResearchGameTestSourceSet."
                                + "runtimeClasspathConfigurationName")),
                () -> assertFalse(build.contains(
                        "p4E0ResearchGameTestRuntimeClasspath")),
                () -> assertFalse(build.contains("sourceSets.test.output")),
                () -> assertFalse(build.contains("testRuntimeClasspath")),
                () -> assertFalse(build.contains(
                        "testImplementation p4E0ResearchGameTestSourceSet.output")),
                () -> assertFalse(build.contains("runtimeElements.extendsFrom p4E0")));

        for (var dependency : List.of(
                "sourceSets.main.output",
                "p4A3ProbeSourceSet.output",
                "p4B2ProbeSourceSet.output",
                "p4C2ProbeSourceSet.output",
                "p4D3ProbeSourceSet.output",
                "p4E0ResearchSourceSet.output",
                "commonsCompressCoordinate")) {
            var marker = "add(p4E0ResearchGameTestSourceSet."
                    + "implementationConfigurationName, " + dependency + ")";
            assertEquals(1, occurrences(build, marker),
                    () -> "R2Q dedicated classpath changed: " + marker);
        }

        for (var edge : List.of(
                "verifyP4E0R2QPreflightTests = tasks.register(",
                "'verifyP4E0R2QFreshJvmDataVersion',\n"
                        + "        'verify-version-init'",
                "verifyP4E0R2QFreshJvmDataVersion.configure {\n"
                        + "    dependsOn(verifyP4E0R2QConfiguration)",
                "prepareP4E0R2Q.configure {\n"
                        + "    dependsOn(verifyP4E0R2QPreflightTests)",
                "    dependsOn(verifyP4E0R2QFreshJvmDataVersion)",
                "verifyP4E0R2QProfile.configure {\n"
                        + "    dependsOn(prepareP4E0R2Q)",
                "runP4E0R2QSmoke.configure {\n"
                        + "    dependsOn(verifyP4E0R2QProfile)",
                "runP4E0R2QDedicatedSmoke.configure {\n"
                        + "    dependsOn(runP4E0R2QSmoke)",
                "verifyP4E0R2QSmokeOutput.configure {\n"
                        + "    dependsOn(runP4E0R2QDedicatedSmoke)",
                "tasks.register('p4E0R2QSmoke') {")) {
            assertTrue(build.contains(edge), () -> "missing R2Q smoke edge: " + edge);
        }
    }

    @Test
    void exactCounterSlugsAndTwentyNineCasePlanStayClosed() {
        var actualSlugs = List.of(P4E0R2QProfile.Counter.values()).stream()
                .map(P4E0R2QProfile.Counter::slug)
                .toList();
        var cases = P4E0R2QCasePlan.standard().cases();
        var actualIds = cases.stream()
                .map(P4E0R2QCasePlan.CaseSpec::caseId)
                .toList();
        var expectedIds = new ArrayList<String>();
        expectedIds.add(P4E0R2QCasePlan.CASE_PREFIX + "exact");
        COUNTER_SLUGS.stream()
                .map(slug -> P4E0R2QCasePlan.CASE_PREFIX + "over-"
                        + slug.replace('_', '-'))
                .forEach(expectedIds::add);
        expectedIds.add(P4E0R2QCasePlan.CASE_PREFIX + "dataversion-missing");
        expectedIds.add(P4E0R2QCasePlan.CASE_PREFIX + "dataversion-wrong-type");
        expectedIds.add(P4E0R2QCasePlan.CASE_PREFIX + "dataversion-wrong-value");

        assertAll(
                () -> assertEquals(25, P4E0R2QProfile.Counter.values().length),
                () -> assertEquals(COUNTER_SLUGS, actualSlugs),
                () -> assertEquals(P4E0R2QCasePlan.CASE_COUNT, cases.size()),
                () -> assertEquals(29, cases.size()),
                () -> assertEquals(expectedIds, actualIds),
                () -> assertEquals(29, new HashSet<>(actualIds).size()),
                () -> assertEquals(1L, cases.stream()
                        .filter(spec -> spec.kind()
                                == P4E0R2QCasePlan.CaseKind.POSITIVE)
                        .count()),
                () -> assertEquals(25L, cases.stream()
                        .filter(spec -> spec.kind()
                                == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE)
                        .count()),
                () -> assertEquals(3L, cases.stream()
                        .filter(spec -> spec.kind() != P4E0R2QCasePlan.CaseKind.POSITIVE
                                && spec.kind()
                                        != P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE)
                        .count()),
                () -> assertTrue(cases.stream()
                        .allMatch(spec -> spec.expectedDfuInvocations() == 0)),
                () -> assertTrue(actualIds.stream()
                        .noneMatch(id -> id.toLowerCase(Locale.ROOT).contains("dfu"))),
                () -> assertTrue(actualIds.stream().allMatch(
                        id -> id.startsWith(P4E0R2QCasePlan.CASE_PREFIX)
                                && !id.contains("1280"))));
    }

    @Test
    void formalEntryOwnsTheExactDirectSerialDependencySpine() throws Exception {
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var formal = bracedBlock(build, "tasks.register('p4E0R2QStudy') {");

        assertAll(
                () -> assertTrue(formal.contains(
                        "dependsOn(verifyP4E0R2QFormalArtifacts)")),
                () -> assertFalse(formal.contains("doFirst")),
                () -> assertTrue(build.contains(
                        "def previousP4E0R2QFormalVerifier = "
                                + "prepareP4E0R2QFormalStudy")),
                () -> assertTrue(build.contains(
                        "prepareCaseTask.configure {\n"
                                + "            dependsOn(prerequisiteVerifier)")),
                () -> assertTrue(build.contains(
                        "runCaseTask.configure {\n"
                                + "            dependsOn(prepareCaseTask)")),
                () -> assertTrue(build.contains(
                        "verifyCaseTask.configure {\n"
                                + "            dependsOn(runCaseTask)")),
                () -> assertTrue(build.contains(
                        "aggregateP4E0R2QFormal.configure {\n"
                                + "    dependsOn(previousP4E0R2QFormalVerifier)")),
                () -> assertTrue(build.contains(
                        "verifyP4E0R2QFormalArtifacts.configure {\n"
                                + "    dependsOn(aggregateP4E0R2QFormal)")),
                () -> assertEquals(1, occurrences(build,
                        "def p4E0R2QFormalCaseIndices = (0..<29).toList()")),
                () -> assertEquals(1, occurrences(build,
                        "previousP4E0R2QFormalVerifier = verifyCaseTask")),
                () -> assertTrue(build.contains("requireP4E0R2QFormalGate()")),
                () -> assertTrue(build.contains("p4E0R2QGitStatus")),
                () -> assertTrue(build.contains("p4E0R2QGitHead")),
                () -> assertTrue(build.contains("p4E0R2QGitTree")),
                () -> assertTrue(build.contains("p4E0R2QOriginMain")),
                () -> assertTrue(build.contains(
                        "'--porcelain=v2', '--untracked-files=all'")),
                () -> assertFalse(build.contains(
                        "formal execution is reserved for R2Q-B")),
                () -> assertFalse(build.contains(
                        "mustRunAfter(previousP4E0R2QFormalVerifier)")),
                () -> assertFalse(build.contains(
                        "shouldRunAfter(previousP4E0R2QFormalVerifier)")));
    }

    @Test
    void r2qFormalInfrastructureRemainsResearchOnlyAndPublishesOnlyItsSixArtifacts()
            throws Exception {
        var production = sources(PROJECT_ROOT.resolve("src/main"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var research = exactSources(R2Q_RESEARCH_PATHS);
        var dedicated = exactSources(R2Q_GAME_PATHS);
        var combined = research + '\n' + dedicated;

        assertAll(
                () -> assertFalse(production.contains("P4E0R2Q")),
                () -> assertFalse(production.contains("p4-e0-r2q")),
                () -> assertFalse(workflow.contains("p4-e0-r2q")),
                () -> assertFalse(workflow.contains("P4-E0-R2Q")),
                () -> assertFalse(combined.contains("org.junit")),
                () -> assertFalse(combined.contains("System.gc(")),
                () -> assertFalse(combined.contains("Thread.sleep(")),
                () -> assertFalse(combined.contains("Files.readAllBytes")),
                () -> assertFalse(combined.contains("NbtAccounter.unlimitedHeap")),
                () -> assertFalse(combined.contains(".reclaim(")),
                () -> assertFalse(combined.matches(
                        "(?s).*catch\\s*\\([^)]*OutOfMemoryError.*")));

        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        assertAll(
                () -> assertTrue(build.contains(
                        "layout.buildDirectory.dir('reports/p4-e0-r2q')")),
                () -> assertTrue(combined.contains("runs.jsonl")),
                () -> assertTrue(combined.contains("r2q-profile.json")),
                () -> assertTrue(combined.contains("r2q-case-plan.json")),
                () -> assertTrue(combined.contains("summary.md")),
                () -> assertTrue(combined.contains("PROVENANCE.txt")),
                () -> assertTrue(combined.contains("SHA256SUMS.txt")),
                () -> assertFalse(combined.contains("authority approved")),
                () -> assertFalse(combined.contains("minimum safe heap")),
                () -> assertFalse(combined.contains("E0-B ready")));
    }

    private static String sources(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return "";
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .map(P4E0ResearchR2QApiGateTest::read)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String exactSources(List<String> relativePaths) {
        return relativePaths.stream()
                .map(PROJECT_ROOT::resolve)
                .peek(path -> assertTrue(
                        Files.isRegularFile(path) && !Files.isSymbolicLink(path),
                        () -> "R2Q reviewed source is missing: " + path))
                .map(P4E0ResearchR2QApiGateTest::read)
                .collect(Collectors.joining("\n"));
    }

    private static String matchingLines(String source, String fragment) {
        return source.lines()
                .filter(line -> line.contains(fragment))
                .collect(Collectors.joining("\n"));
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
