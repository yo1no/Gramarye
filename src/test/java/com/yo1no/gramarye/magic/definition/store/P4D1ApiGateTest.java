package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact API, source-ownership, and later-phase absence gate for engineering P4-D1. */
final class P4D1ApiGateTest {
    private static final String STORE_PACKAGE =
            "com.yo1no.gramarye.magic.definition.store.";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Pattern TOP_LEVEL = Pattern.compile(
            "(?m)^(?:(?:public|abstract|final|sealed|non-sealed)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    @Test
    void exactD1SourcesDeclareOnlyTheReviewedTopLevelsAndOneNewPublicType()
            throws Exception {
        var sources = P4DPhaseTypes.NEW_STORE_SOURCE_FILE_NAMES.stream()
                .map(STORE_ROOT::resolve)
                .sorted()
                .toList();
        assertTrue(sources.stream().allMatch(Files::isRegularFile));
        var declarations = sources.stream()
                .flatMap(path -> TOP_LEVEL.matcher(withoutCommentsAndLiterals(read(path)))
                        .results().map(match -> match.group(1)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var loaded = P4DPhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES.stream()
                .map(P4D1ApiGateTest::load)
                .toList();
        var publicTypes = loaded.stream()
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());

        assertEquals(P4DPhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES, declarations);
        assertEquals(P4DPhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES,
                loaded.stream().map(Class::getSimpleName).collect(Collectors.toSet()));
        assertEquals(Set.of(P4DPhaseTypes.ONLY_NEW_PUBLIC_TOP_LEVEL), publicTypes);
    }

    @Test
    void publicPortAndServiceExposeExactlyTheApprovedNarrowOperations() {
        assertTrue(Modifier.isPublic(SkillDefinitionStoreSubmissionPort.class.getModifiers()));
        assertTrue(Modifier.isFinal(SkillDefinitionStoreSubmissionPort.class.getModifiers()));
        assertTrue(Arrays.stream(SkillDefinitionStoreSubmissionPort.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertEquals(Set.of(
                        "bootstrapJournal",
                        "commitPreparedJournalClear",
                        "commitPreparedSubmission",
                        "journalRoots",
                        "journalStatus",
                        "observePendingRecovery",
                        "observeSubmissionAuthority",
                        "prepareJournalPrefixClear",
                        "prepareSubmissionCommit"),
                publicMethodNames(SkillDefinitionStoreSubmissionPort.class));
        assertEquals(Set.of(
                        "AuthoritySnapshot",
                        "BootstrapResult",
                        "JournalClearCommitResult",
                        "JournalClearFailure",
                        "JournalClearPreparationResult",
                        "JournalRootProjection",
                        "JournalStatus",
                        "PendingRecoveryProjection",
                        "PendingRecoveryStep",
                        "PendingRecoveryTargetFailure",
                        "PendingRecoveryUnavailableReason",
                        "PendingSkillRecoveryChain",
                        "PostCommitFailureCode",
                        "PreparationFailure",
                        "PreparedBaseMismatchCode",
                        "PreparedJournalPrefixClear",
                        "PreparedStoreSubmissionCommit",
                        "SubmissionCommitResult",
                        "SubmissionPreparationResult",
                        "UnavailableReason"),
                Arrays.stream(SkillDefinitionStoreSubmissionPort.class.getDeclaredClasses())
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
        assertEquals(Set.of(
                        "committedSkillCount", "find", "latestReference", "ownerOf", "pin",
                        "reclaim", "registerOn", "submissionPort"),
                publicMethodNames(SkillDefinitionStoreService.class));
        assertEquals(Set.of(
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .TRANSITION_SERVER_MISMATCH,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .NORMAL_SUBMISSION_NO_OP,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .PLAN_TRANSITION_PAIRING_FAILURE,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .AUTHORITY_PRECONDITION_MISMATCH,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .DOCUMENT_BLOB_CAPACITY_REJECTED,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .REVISION_BLOB_CAPACITY_REJECTED,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .HISTORY_BLOB_CAPACITY_REJECTED,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .STORE_BLOB_CAPACITY_REJECTED,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .JOURNAL_ENTRY_COUNT_REJECTED,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .JOURNAL_ENCODED_CAPACITY_REJECTED,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .STORE_CARRIER_INVARIANT_FAILURE,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .JOURNAL_CHAIN_INVARIANT_FAILURE,
                        SkillDefinitionStoreSubmissionPort.PreparationFailure
                                .SAVED_DATA_CARRIER_INVARIANT_FAILURE),
                Set.of(SkillDefinitionStoreSubmissionPort.PreparationFailure.values()));
    }

    @Test
    void opaqueHandlesAndPackagePrivateJournalTypesLeakNoPersistenceTruth() {
        assertEquals(
                Set.of(
                        JournalTargetAuditProof.AuditedExisting.class,
                        JournalTargetAuditProof.ConditionalOnExactCommit.class),
                Set.of(JournalTargetAuditProof.class.getPermittedSubclasses()));
        for (var handle : List.of(
                SkillDefinitionStoreSubmissionPort.PreparedStoreSubmissionCommit.class,
                SkillDefinitionStoreSubmissionPort.PreparedJournalPrefixClear.class)) {
            assertTrue(Arrays.stream(handle.getDeclaredConstructors())
                    .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                            || Modifier.isProtected(constructor.getModifiers())));
            assertTrue(publicMethodNames(handle).isEmpty());
            assertTrue(Arrays.stream(handle.getDeclaredFields())
                    .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
            assertFalse(handle.isRecord());
        }
        for (var typeName : P4DPhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES) {
            var type = load(typeName);
            if (type == SkillDefinitionStoreSubmissionPort.class) {
                continue;
            }
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
        }
    }

    @Test
    void ceilingsCommitAuthorityAndLaterPhaseAbsenceStayExact() throws Exception {
        assertEquals(4_096, MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES);
        assertEquals(
                Set.of(
                        "PendingAttachmentJournalSchema.java",
                        "SkillSubmissionRecoveryService.java",
                        "MagicSafetyCeilings.java"),
                relativeFilesContaining("MAX_PENDING_ATTACHMENT_UPDATES"));
        assertEquals(
                Set.of("SkillDefinitionStoreSubmissionPort.java"),
                storeFilesMatching(Pattern.compile("\\.\\s*commit\\s*\\(")));
        assertEquals(
                Set.of("GramaryeSkillSavedData.java", "SkillDefinitionStoreService.java"),
                storeFilesMatching(Pattern.compile("\\.\\s*reclaim\\s*\\(")));

        var playerService = read(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java"));
        assertTrue(playerService.contains("isChangedGenerationSuccessor("));
        assertTrue(playerService.contains("boolean isBoundTo(MinecraftServer server)"));
        var allMain = javaSources(MAIN_JAVA).stream()
                .map(P4D1ApiGateTest::read)
                .collect(Collectors.joining("\n"));
        assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                relativeFilesContaining("PlayerLoggedInEvent"));
        for (var forbidden : List.of(
                "RootCollector",
                "OfflineRoot",
                "Reconciliation",
                "CustomPacketPayload",
                "PayloadRegistrar")) {
            assertFalse(allMain.contains(forbidden), forbidden);
        }
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        assertFalse(build.contains("p4D"));
        assertFalse(workflow.contains("P4-D memory gates"));
    }

    private static Set<String> publicMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isSynthetic() && !method.isBridge())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
    }

    private static Set<String> relativeFilesContaining(String fragment) throws Exception {
        return javaSources(MAIN_JAVA).stream()
                .filter(path -> read(path).contains(fragment))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> storeFilesMatching(Pattern pattern) throws Exception {
        return javaSources(STORE_ROOT).stream()
                .filter(path -> pattern.matcher(withoutCommentsAndLiterals(read(path))).find())
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static List<Path> javaSources(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted().toList();
        }
    }

    private static Class<?> load(String simpleName) {
        try {
            return Class.forName(STORE_PACKAGE + simpleName, false,
                    P4D1ApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("missing reviewed D1 type " + simpleName, exception);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
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

    private static String withoutCommentsAndLiterals(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("(?s)\"(?:\\\\.|[^\"\\\\])*\"", " ")
                .replaceAll("(?s)'(?:\\\\.|[^'\\\\])*'", " ");
    }
}
