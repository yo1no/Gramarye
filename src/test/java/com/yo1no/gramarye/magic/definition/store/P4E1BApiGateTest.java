package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Final exact type, verifier, and phase-boundary Gate for the complete P4-E1-B surface. */
final class P4E1BApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");

    @Test
    void exactE1TypeInventoryHasOnlyTwoReviewedPublicTopLevels() throws Exception {
        assertEquals(P4EPhaseTypes.STORE_TYPE_NAMES, currentStoreTypeNames());
        for (var name : P4EPhaseTypes.STORE_TYPE_NAMES) {
            var type = Class.forName(
                    "com.yo1no.gramarye.magic.definition.store." + name);
            assertEquals(
                    P4EPhaseTypes.PUBLIC_STORE_TYPE_NAMES.contains(name),
                    Modifier.isPublic(type.getModifiers()),
                    name);
        }
        assertEquals(
                Set.of(
                        "PlayerSkillAttachmentAdmissionSource",
                        "SkillRetentionRootAuditResult"),
                P4EPhaseTypes.PUBLIC_STORE_TYPE_NAMES);
    }

    @Test
    void exactB2BTestsAndPortableVerifierCoverageExist() throws Exception {
        var testRoot = PROJECT_ROOT.resolve(
                "src/test/java/com/yo1no/gramarye/magic/definition/store");
        for (var test : Set.of(
                "P4E1B2BFinalFreshnessTest.java",
                "P4E1B2BIndexLifecycleTest.java",
                "P4E1B2BCompleteHandoffTest.java",
                "P4E1B2BApiGateTest.java",
                "P4E1BApiGateTest.java")) {
            var path = testRoot.resolve(test);
            assertTrue(Files.isRegularFile(path), test);
            assertFalse(Files.isSymbolicLink(path), test);
        }

        var exactVerifiers = Set.of(
                "verify-p4-c2-b-configuration.sh",
                "verify-p4-d1-configuration.sh",
                "verify-p4-d2-configuration.sh",
                "verify-p4-d3-a-configuration.sh",
                "verify-p4-d3-configuration.sh",
                "verify-p4-e0-r-configuration.sh",
                "verify-p4-e0-r2q-configuration.sh",
                "verify-p4-e1-configuration.sh");
        for (var verifier : exactVerifiers) {
            var path = PROJECT_ROOT.resolve("scripts").resolve(verifier);
            assertTrue(Files.isRegularFile(path), verifier);
            assertFalse(Files.isSymbolicLink(path), verifier);
            assertTrue(Files.isExecutable(path), verifier);
        }

        var e1Verifier = Files.readString(
                PROJECT_ROOT.resolve("scripts/verify-p4-e1-configuration.sh"));
        for (var exactPath : Set.of(
                "P4E1CompleteRootHandoff.java",
                "P4E1FinalFreshness.java",
                "SkillRetentionRootAuditResult.java",
                "SkillRetentionRootAuditService.java",
                "P4E1B2BFinalFreshnessTest.java",
                "P4E1B2BIndexLifecycleTest.java",
                "P4E1B2BCompleteHandoffTest.java",
                "P4E1B2BApiGateTest.java",
                "P4E1BApiGateTest.java")) {
            assertTrue(e1Verifier.contains(exactPath), exactPath);
        }
        assertFalse(e1Verifier.contains("SkillRetentionRootAuditService*.java"));
        assertFalse(e1Verifier.contains("P4E1B2B*.java"));
        assertFalse(e1Verifier.contains("Audit*.java"));
    }

    @Test
    void buildWorkflowResourcesAndNormalGameTestInventoryRemainUnchanged()
            throws Exception {
        var build = Files.readString(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = Files.readString(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var allProduction = javaSources(MAIN_JAVA);
        assertFalse(build.contains("p4E1"));
        assertFalse(build.contains("P4E1"));
        assertFalse(workflow.contains("p4-e1"));
        assertFalse(workflow.contains("P4-E1"));
        assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4E1Probe")));
        assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4E1GameTest")));
        assertFalse(build.contains("p4E1FixedHeapGate"));
        assertEquals(12, occurrences(allProduction, "@GameTest("));
    }

    @Test
    void laterPhaseOwnersAndProductionCompositionRemainAbsent() throws Exception {
        var e1 = e1Sources();
        for (var forbidden : P4EPhaseTypes.FORBIDDEN_LATER_PHASE_TOKENS) {
            assertFalse(e1.contains(forbidden), forbidden);
        }
        for (var forbiddenFile : Set.of(
                "P4E1GlobalInventory.java",
                "P4E1RootIndex.java",
                "P4E1RootHandoff.java",
                "P4E1Reconciliation.java",
                "OfflineRootIndex.java")) {
            assertFalse(Files.exists(STORE_ROOT.resolve(forbiddenFile)), forbiddenFile);
        }
        var allProduction = javaSources(MAIN_JAVA);
        assertEquals(0, occurrences(
                allProduction, "SkillRetentionRootSnapshot.fromCompleteRoots"));
    }

    private static Set<String> currentStoreTypeNames() throws Exception {
        try (var stream = Files.list(STORE_ROOT)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith("P4E1")
                            || path.getFileName().toString().startsWith(
                                    "SkillRetentionRootAudit")
                            || path.getFileName().toString().equals(
                                    "PlayerSkillAttachmentAdmissionSource.java"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static String e1Sources() throws Exception {
        var text = new StringBuilder();
        try (var stream = Files.list(STORE_ROOT)) {
            for (var path : stream
                    .filter(candidate -> candidate.getFileName().toString().startsWith("P4E1")
                            || candidate.getFileName().toString().startsWith(
                                    "SkillRetentionRootAudit")
                            || candidate.getFileName().toString().equals(
                                    "PlayerSkillAttachmentAdmissionSource.java"))
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .toList()) {
                text.append(Files.readString(path)).append('\n');
            }
        }
        return text.toString();
    }

    private static String javaSources(Path root) throws Exception {
        var text = new StringBuilder();
        try (var stream = Files.walk(root)) {
            for (var path : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                text.append(Files.readString(path)).append('\n');
            }
        }
        return text.toString();
    }

    private static int occurrences(String text, String token) {
        var count = 0;
        var offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }
}
