package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact production-surface and phase-absence gate for P4-E1-A. */
final class P4E1AApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path PLAYER_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player");

    @Test
    void reviewedE1ATypesAreExactWithOneSealedPublicCapability() throws Exception {
        assertEquals(P4EPhaseTypes.STORE_TYPE_NAMES, p4EStoreTypeNames());
        for (var simpleName : P4EPhaseTypes.STORE_TYPE_NAMES) {
            assertEquals(
                    P4EPhaseTypes.PUBLIC_STORE_TYPE_NAMES.contains(simpleName),
                    Modifier.isPublic(Class.forName(
                            "com.yo1no.gramarye.magic.definition.store." + simpleName)
                            .getModifiers()),
                    simpleName);
        }
        for (var simpleName : P4EPhaseTypes.PLAYER_TYPE_NAMES) {
            assertFalse(Modifier.isPublic(Class.forName(
                    "com.yo1no.gramarye.magic.definition.player." + simpleName)
                    .getModifiers()), simpleName);
        }
    }

    @Test
    void exactTwentyFiveCounterAuthorityHasOneProductionConsumer() throws Exception {
        assertEquals(25, P4E1AuditCounter.values().length);
        var expected = List.of(
                "MAX_PLAYERDATA_DIRECTORY_ENTRIES = 4_096",
                "MAX_PLAYERDATA_RELEVANT_RECORDS = 2_048",
                "MAX_PLAYERDATA_COMPRESSED_BYTES_PER_FILE = 33_559_514",
                "MAX_PLAYERDATA_DECOMPRESSED_BYTES_PER_FILE = 268_435_456",
                "MAX_PLAYERDATA_CONTAINER_DEPTH_PER_FILE = 512",
                "MAX_PLAYERDATA_COMPOUND_CONTAINERS_PER_FILE = 1_024",
                "MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_PER_FILE = 65_537",
                "MAX_PLAYERDATA_LIST_ELEMENTS_PER_FILE = 65_536",
                "MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_PER_FILE = 268_435_384",
                "MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_PER_FILE = 65_536",
                "MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_PER_FILE = 65_536",
                "MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_PER_FILE = 67_107_692",
                "MAX_PLAYERDATA_SCALAR_TAGS_PER_FILE = 65_537",
                "MAX_PLAYERDATA_COMPRESSED_BYTES_TOTAL = 268_440_533",
                "MAX_PLAYERDATA_DECOMPRESSED_BYTES_TOTAL = 536_870_912",
                "MAX_PLAYERDATA_COMPOUND_CONTAINERS_TOTAL = 131_072",
                "MAX_PLAYERDATA_COMPOUND_FIELD_ENTRIES_TOTAL = 524_288",
                "MAX_PLAYERDATA_LIST_ELEMENTS_TOTAL = 131_072",
                "MAX_PLAYERDATA_BYTE_ARRAY_ELEMENTS_TOTAL = 456_524_705",
                "MAX_PLAYERDATA_INT_ARRAY_ELEMENTS_TOTAL = 131_072",
                "MAX_PLAYERDATA_LONG_ARRAY_ELEMENTS_TOTAL = 131_072",
                "MAX_PLAYERDATA_MODIFIED_UTF8_BYTES_TOTAL = 75_497_472",
                "MAX_PLAYERDATA_SCALAR_TAGS_TOTAL = 458_752",
                "MAX_PLAYERDATA_ATTACHMENT_ADMISSIONS = 1_024",
                "MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES = 1_610_612_736L");
        var ceilings = Files.readString(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java"));
        for (var marker : expected) {
            assertEquals(1, occurrences(ceilings, marker), marker);
        }

        var budgetSource = Files.readString(STORE_ROOT.resolve("P4E1AuditBudget.java"));
        var production = javaSources(MAIN_JAVA);
        for (var field : MagicSafetyCeilings.class.getDeclaredFields()) {
            if (!field.getName().startsWith("MAX_PLAYERDATA_")) {
                continue;
            }
            assertEquals(2, occurrences(production, field.getName()), field.getName());
            assertEquals(1, occurrences(budgetSource, field.getName()), field.getName());
        }
        assertEquals(MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM,
                P4E1TestBudgets.create().maximum(P4E1AuditCounter.RAW_ROOT_CLAIMS));
    }

    @Test
    void effectiveHotSpotHeapCoordinateIsTheOnlyFloorAuthorityAndPrecedesBudget()
            throws Exception {
        var production = javaSources(MAIN_JAVA);
        var observer = Files.readString(STORE_ROOT.resolve("P4E1HeapFloorObservation.java"));
        var budget = Files.readString(STORE_ROOT.resolve("P4E1AuditBudget.java"));
        var preflight = Files.readString(
                STORE_ROOT.resolve("P4E1SourceAdmissionPreflight.java"));
        var probe = Files.readString(PROJECT_ROOT.resolve(
                "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E1HeapFloorProbeMain.java"));

        assertAll(
                () -> assertEquals(2, occurrences(
                        production, "MIN_P4_E_ROOT_AUDIT_MAX_HEAP_SIZE_BYTES")),
                () -> assertEquals(1, occurrences(observer,
                        "ManagementFactory.getPlatformMXBean(")),
                () -> assertEquals(1, occurrences(observer,
                        "diagnostic.getVMOption(\"MaxHeapSize\")")),
                () -> assertEquals(1, occurrences(
                        production, "Runtime.getRuntime().maxMemory()")),
                () -> assertFalse(observer.contains("getInputArguments(")),
                () -> assertFalse(observer.contains("Math.min(")),
                () -> assertFalse(observer.contains("Math.max(")),
                () -> assertFalse(observer.contains("PrintFlagsFinal")),
                () -> assertFalse(observer.contains("jcmd")),
                () -> assertTrue(preflight.indexOf("P4E1HeapFloorObservation.observe()")
                        < preflight.indexOf("new P4E1AuditBudget(new QualifiedPermit())")),
                () -> assertEquals(1, occurrences(
                        production, "new P4E1AuditBudget(new QualifiedPermit())")),
                () -> assertEquals(1, occurrences(
                        preflight, "new P4E1AuditBudget(new QualifiedPermit())")),
                () -> assertTrue(preflight.contains("private QualifiedPermit()")),
                () -> assertEquals(1, occurrences(
                        production.replace(preflight, ""),
                        "P4E1SourceAdmissionPreflight.evaluate(")),
                () -> assertTrue(budget.contains(
                        "P4E1AuditBudget(P4E1SourceAdmissionPreflight.QualifiedPermit permit)")),
                () -> assertFalse(budget.contains("static P4E1AuditBudget create(")),
                () -> assertTrue(preflight.contains(
                        "case HEAP_FLOOR_NOT_MET -> new Incomplete(")),
                () -> assertTrue(preflight.contains(
                        "case HEAP_FLOOR_UNVERIFIABLE -> new Incomplete(")),
                () -> assertTrue(probe.contains("source_work_calls=")),
                () -> assertTrue(probe.contains("observedBudgetedSourceWork(preflight)")),
                () -> assertFalse(probe.contains("sourceWorkCalls = 0")),
                () -> assertFalse(probe.contains("System.out")));
        for (var sourceWorkType : List.of(
                "P4E1PlayerDataDirectorySnapshot",
                "P4E1FileSystemAccess",
                "P4E1PlayerDataFileReader",
                "P4E1PlayerDataNbtScanner",
                "P4E1IntegratedSnapshotTraversal",
                "PlayerSkillAttachmentAdmission",
                "PlayerSkillAttachmentSourceObservation",
                "PendingSkillSubmissionJournal",
                "SkillRetentionRootSnapshot",
                "SkillDefinitionStore",
                ".reclaim(")) {
            assertFalse(preflight.contains(sourceWorkType), sourceWorkType);
        }
    }

    @Test
    void gzipAndNbtGrammarHaveOneReviewedProductionOwner() throws Exception {
        var production = javaSources(MAIN_JAVA);
        var scanner = Files.readString(STORE_ROOT.resolve("P4E1PlayerDataNbtScanner.java"));
        var directory = Files.readString(
                STORE_ROOT.resolve("P4E1PlayerDataDirectorySnapshot.java"));
        var gzip = Files.readString(STORE_ROOT.resolve("StrictSingleMemberGzipInput.java"));
        assertAll(
                () -> assertEquals(1,
                        occurrences(production, "new GzipCompressorInputStream(")),
                () -> assertTrue(gzip.contains("GzipCompressorInputStream(bufferedCompressed, false)")),
                () -> assertTrue(gzip.contains("verifyMemberCompletion()")),
                () -> assertTrue(scanner.contains("new ArrayDeque<>()")),
                () -> assertTrue(scanner.contains("duplicate raw Compound field")),
                () -> assertTrue(scanner.contains("CURRENT_DATA_VERSION = 3_955")),
                () -> assertFalse(scanner.contains("attachmentOuterWrongType")),
                () -> assertTrue(directory.contains("RecordSelection selectRecords(")),
                () -> assertTrue(directory.contains(
                        "excludedIntegratedOwner.filter(record.playerId()::equals)")),
                () -> assertFalse(scanner.contains("NbtIo.")),
                () -> assertFalse(scanner.contains("CompoundTag.copy(")),
                () -> assertFalse(scanner.contains("readAllBytes")),
                () -> assertFalse(scanner.contains("unlimitedHeap")));
    }

    @Test
    void noPublicRawSignatureOrLaterPhaseCompositionExists() throws Exception {
        var e1Sources = p4ESources();
        for (var forbidden : P4EPhaseTypes.FORBIDDEN_LATER_PHASE_TOKENS) {
            var reviewedSources = forbidden.equals("Reconciliation")
                    ? p4ESourcesExcludingGroupedStoreAudit()
                    : e1Sources;
            assertFalse(reviewedSources.contains(forbidden), forbidden);
        }
        for (var forbidden : List.of(
                "java.util.zip.GZIPInputStream",
                "Files.readAllBytes",
                "NbtAccounter.unlimitedHeap",
                "java.lang.reflect",
                "setAccessible(",
                "sun.misc.Unsafe",
                "Thread.sleep(",
                "System.gc(",
                "Executors.",
                "ExecutorService",
                "new Thread(",
                "Future<",
                "RuntimeMXBean",
                "getInputArguments(",
                "PrintFlagsFinal",
                "jcmd",
                "src.p4E0Research")) {
            assertFalse(e1Sources.contains(forbidden), forbidden);
        }

        for (var simpleName : P4EPhaseTypes.STORE_TYPE_NAMES) {
            var type = Class.forName("com.yo1no.gramarye.magic.definition.store." + simpleName);
            assertEquals(
                    P4EPhaseTypes.PUBLIC_STORE_TYPE_NAMES.contains(simpleName),
                    Modifier.isPublic(type.getModifiers()));
        }
        assertEquals(1, occurrences(e1Sources, "public sealed abstract class"));
        assertFalse(e1Sources.contains("public interface"));
    }

    @Test
    void buildWorkflowAndProductionResourcesRemainOutsideE1A() throws Exception {
        var build = Files.readString(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = Files.readString(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var verifierPath = PROJECT_ROOT.resolve("scripts/verify-p4-e1-configuration.sh");
        var verifier = Files.readString(verifierPath);
        assertAll(
                () -> assertFalse(build.contains("p4E1")),
                () -> assertFalse(build.contains("P4E1")),
                () -> assertFalse(workflow.contains("p4-e1")),
                () -> assertFalse(workflow.contains("P4-E1")),
                () -> assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4E1Probe"))),
                () -> assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4E1GameTest"))),
                () -> assertTrue(Files.isExecutable(verifierPath)),
                () -> assertTrue(verifier.contains("is_allowed_changed_path()")),
                () -> assertTrue(verifier.contains(
                        "diff --cached --quiet --no-ext-diff --")),
                () -> assertTrue(verifier.contains(
                        "--name-only --no-renames --no-ext-diff -z HEAD --")),
                () -> assertTrue(verifier.contains(
                        "--others --exclude-standard -z --")),
                () -> assertTrue(verifier.contains(
                        "changed path is outside the exact E1-A allowlist")),
                () -> assertTrue(verifier.contains(
                        "allowed E1-A path is missing or not a regular file")),
                () -> assertTrue(verifier.contains(
                        "allowed E1-A path is a symlink")),
                () -> assertTrue(verifier.contains(
                        "allowed E1-A verifier is not executable")),
                () -> assertTrue(verifier.contains(
                        "self-test confused a Git tool error with an empty changed set")));
    }

    private static Set<String> p4EStoreTypeNames() throws Exception {
        try (var stream = Files.list(STORE_ROOT)) {
            return stream
                    .filter(path -> path.getFileName().toString().startsWith("P4E1")
                            || path.getFileName().toString().equals(
                                    "PlayerSkillAttachmentAdmissionSource.java"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replace(".java", ""))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static String p4ESources() throws Exception {
        return p4ESources(null);
    }

    private static String p4ESourcesExcludingGroupedStoreAudit() throws Exception {
        return p4ESources(STORE_ROOT.resolve("P4E1GroupedStoreAudit.java"));
    }

    private static String p4ESources(Path excluded) throws Exception {
        var paths = new ArrayList<Path>();
        try (var stream = Files.list(STORE_ROOT)) {
            stream.filter(path -> path.getFileName().toString().startsWith("P4E1")
                            || path.getFileName().toString().equals(
                                    "PlayerSkillAttachmentAdmissionSource.java"))
                    .filter(path -> excluded == null
                            || !path.toAbsolutePath().normalize().equals(
                                    excluded.toAbsolutePath().normalize()))
                    .forEach(paths::add);
        }
        for (var name : P4EPhaseTypes.PLAYER_TYPE_NAMES) {
            paths.add(PLAYER_ROOT.resolve(name + ".java"));
        }
        var text = new StringBuilder();
        for (var path : paths) {
            text.append(Files.readString(path)).append('\n');
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
