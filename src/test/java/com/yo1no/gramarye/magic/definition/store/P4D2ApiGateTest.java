package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillDraftCreationService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicySnapshot;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact P4-D2-A phase-local API, ownership, and later-phase absence gate. */
final class P4D2ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path SUBMISSION_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/submission");
    private static final Path PLAYER_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java");
    private static final Path PREPARATION_PIPELINE = SUBMISSION_ROOT.resolve(
            "SkillSubmissionPreparationPipeline.java");
    private static final Pattern TOP_LEVEL = Pattern.compile(
            "(?m)^(?:(?:public|abstract|final|sealed|non-sealed)\\s+)*"
                    + "(?:class|record|interface|enum)\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    @Test
    void exactD2ASourcesAndVisibilityArePhaseLocal() throws Exception {
        var sources = P4DPhaseTypes.D2A_SUBMISSION_SOURCE_FILE_NAMES.stream()
                .map(SUBMISSION_ROOT::resolve)
                .sorted()
                .toList();
        assertTrue(sources.stream().allMatch(Files::isRegularFile));
        var declarations = sources.stream()
                .flatMap(path -> TOP_LEVEL.matcher(withoutCommentsAndLiterals(read(path)))
                        .results().map(match -> match.group(1)))
                .collect(Collectors.toSet());
        var loaded = P4DPhaseTypes.D2A_SUBMISSION_TOP_LEVEL_TYPE_NAMES.stream()
                .map(P4D2ApiGateTest::loadSubmission)
                .toList();

        assertEquals(P4DPhaseTypes.D2A_SUBMISSION_TOP_LEVEL_TYPE_NAMES, declarations);
        assertEquals(P4DPhaseTypes.D2A_SUBMISSION_TOP_LEVEL_TYPE_NAMES,
                loaded.stream().map(Class::getSimpleName).collect(Collectors.toSet()));
        assertEquals(P4DPhaseTypes.D2A_PUBLIC_TOP_LEVEL_TYPE_NAMES,
                loaded.stream()
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .map(Class::getSimpleName)
                        .collect(Collectors.toSet()));
        assertTrue(P4DPhaseTypes.D2A_MODIFIED_SOURCE_PATHS.stream()
                .map(MAIN_JAVA::resolve)
                .allMatch(Files::isRegularFile));
    }

    @Test
    void policyDraftAndOutcomePublicShapesStayNarrowAndImmutable() {
        assertTrue(Modifier.isPublic(SkillSubmissionPolicyProvider.class.getModifiers()));
        assertTrue(SkillSubmissionPolicyProvider.class.isInterface());
        assertEquals(Set.of("defaults", "snapshot"), publicDeclaredMethodNames(
                SkillSubmissionPolicyProvider.class));
        assertTrue(SkillSubmissionPolicySnapshot.class.isRecord());
        assertEquals(List.of("quota", "validationContext"),
                Arrays.stream(SkillSubmissionPolicySnapshot.class.getRecordComponents())
                        .map(component -> component.getName()).toList());

        assertTrue(Modifier.isPublic(SkillDraftCreationService.class.getModifiers()));
        assertTrue(Modifier.isFinal(SkillDraftCreationService.class.getModifiers()));
        assertEquals(Set.of("createDraft", "randomUuidSkillIdSource"),
                publicDeclaredMethodNames(SkillDraftCreationService.class));
        assertEquals(Set.of("Created", "CreationRejectionCode", "CreationResult", "Rejected",
                        "Unavailable"),
                Arrays.stream(SkillDraftCreationService.class.getDeclaredClasses())
                        .filter(type -> Modifier.isPublic(type.getModifiers()))
                        .map(Class::getSimpleName).collect(Collectors.toSet()));

        assertTrue(SkillSubmissionCompositionOutcome.class.isSealed());
        assertEquals(11, SkillSubmissionCompositionOutcome.class.getPermittedSubclasses().length);
        assertEquals(Set.of(
                        "ATTACHMENT_ENCODED",
                        "DOCUMENT_BLOB",
                        "REVISION_BLOB",
                        "HISTORY_BLOB",
                        "STORE_BLOB",
                        "JOURNAL_ENTRY_COUNT",
                        "JOURNAL_ENCODED_BYTES"),
                Arrays.stream(SkillSubmissionCompositionOutcome.PersistenceCapacityScope.values())
                        .map(Enum::name).collect(Collectors.toSet()));
        assertEquals(List.of("skillId"), recordComponentNames(
                SkillSubmissionCompositionOutcome.DraftUnavailable.class));
        assertEquals(List.of("skillId", "failure"), recordComponentNames(
                SkillSubmissionCompositionOutcome.SubsystemUnavailableBeforePreparation.class));
        assertEquals(Set.of(
                        "STATE_CHANGED",
                        "ATTACHMENT_QUARANTINED",
                        "UNEXPECTED_NO_OP",
                        "RUNTIME_EXCEPTION"),
                Arrays.stream(SkillSubmissionCompositionOutcome
                                .AttachmentPublicationFailureCode.values())
                        .map(Enum::name).collect(Collectors.toSet()));
        var forbiddenComponentFragments = Set.of(
                "SkillSubmissionPlan",
                "SkillDocument",
                "Carrier",
                "Journal",
                "PlayerSkillAttachmentState",
                "Throwable");
        var outcomeComponents = Arrays.stream(
                        SkillSubmissionCompositionOutcome.class.getDeclaredClasses())
                .filter(Class::isRecord)
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .toList();
        assertTrue(outcomeComponents.stream().noneMatch(component ->
                component.getType() == Object.class
                        || component.getType() == byte[].class
                        || component.getType().getName().equals("net.minecraft.nbt.Tag")
                        || forbiddenComponentFragments.stream()
                                .anyMatch(component.getType().getName()::contains)));
        assertTrue(outcomeComponents.stream()
                .map(component -> component.getName().toLowerCase(java.util.Locale.ROOT))
                .noneMatch(name -> List.of(
                                "raw", "plan", "document", "carrier", "journal", "transition")
                        .stream().anyMatch(name::contains)));
    }

    @Test
    void preparationPipelineRemainsPackagePrivateWithOnePackagePrivateTestingSeam() {
        var pipeline = loadSubmission("SkillSubmissionPreparationPipeline");
        var source = withoutCommentsAndLiterals(read(PREPARATION_PIPELINE));
        assertFalse(Modifier.isPublic(pipeline.getModifiers()));
        assertTrue(Modifier.isFinal(pipeline.getModifiers()));
        assertEquals(Set.of("checkAuthority", "map", "precheck", "prepareAndMap"),
                Arrays.stream(pipeline.getDeclaredMethods())
                        .filter(method -> !method.isSynthetic())
                        .filter(Predicate.not(method -> Modifier.isPrivate(method.getModifiers())))
                        .map(method -> method.getName()).collect(Collectors.toSet()));
        assertTrue(Arrays.stream(pipeline.getDeclaredMethods())
                .noneMatch(method -> Modifier.isPublic(method.getModifiers())
                        || Modifier.isProtected(method.getModifiers())));
        assertEquals(Set.of("Stages"),
                Arrays.stream(pipeline.getDeclaredClasses())
                        .map(Class::getSimpleName).collect(Collectors.toSet()));
        assertTrue(Arrays.stream(pipeline.getDeclaredClasses())
                .noneMatch(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers())));
        assertEquals(1, occurrences(source, "SkillSubmissionInput.direct("));
        for (var forbidden : List.of(
                "SkillDraftReader", "SkillDraftWriter", "resolveFromRaw")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void p4CPrepareAndCurrentnessUseSingleObservationAndOneSharedValidator() {
        var source = read(PLAYER_SERVICE);
        var prepareCurrent = methodBody(source, "prepareLatestTransitionToCurrent");
        var currentness = methodBody(source, "checkPreparedTransitionCurrent");
        var publish = methodBody(source, "publishPreparedTransition");
        var validator = methodBody(
                source,
                "private static PreparedTransitionValidation validatePreparedTransition");

        assertEquals(1, occurrences(prepareCurrent, "observeChecked(player)"));
        assertFalse(prepareCurrent.contains("findLatestState("));
        assertFalse(prepareCurrent.contains("prepareLatestTransition("));
        assertEquals(1, occurrences(currentness, "validatePreparedTransition("));
        assertEquals(1, occurrences(publish, "validatePreparedTransition("));
        assertEquals(1, occurrences(validator, "observeChecked(player)"));
        assertEquals(1, occurrences(source, "transition.original.matches(observed)"));
        var serverCheck = validator.indexOf("server != transition.server");
        var playerCheck = validator.indexOf("player.getUUID().equals(transition.playerId)");
        var observation = validator.indexOf("observeChecked(player)");
        assertTrue(serverCheck >= 0 && serverCheck < observation);
        assertTrue(playerCheck >= 0 && playerCheck < observation);
        assertTrue(validator.substring(serverCheck, observation)
                .contains("MutationRejectionCode.WRONG_SERVER"));
        assertTrue(validator.substring(playerCheck, observation)
                .contains("MutationRejectionCode.WRONG_PLAYER"));
        assertFalse(currentness.contains("setData("));
        assertFalse(currentness.contains("publishReplacement("));
        assertFalse(currentness.contains("rebuildReady("));

        assertEquals(Set.of(
                        "checkPreparedTransitionCurrent",
                        "prepareLatestTransitionToCurrent"),
                publicDeclaredMethodNames(PlayerSkillAttachmentService.class).stream()
                        .filter(name -> name.endsWith("Current")
                                || name.endsWith("Currentness"))
                        .collect(Collectors.toSet()));
        assertEquals(Set.of("CURRENT", "STATE_CHANGED"),
                Arrays.stream(PlayerSkillAttachmentService.TransitionCurrentness.values())
                        .map(Enum::name).collect(Collectors.toSet()));
    }

    @Test
    void typedTaxonomyAndStaticOwnershipHaveNoStringInferenceOrDuplicateOwners()
            throws Exception {
        var port = read(MAIN_JAVA.resolve(
                "com/yo1no/gramarye/magic/definition/store/"
                        + "SkillDefinitionStoreSubmissionPort.java"));
        var storeMapping = methodBody(port, "mapStoreCarrierFailure");
        var journalMapping = methodBody(port, "mapJournalFailure");
        assertFalse(storeMapping.contains("default"));
        assertFalse(journalMapping.contains("default"));
        for (var forbidden : List.of("getMessage(", "getClass(", ".name(", "toString(")) {
            assertFalse(storeMapping.contains(forbidden), forbidden);
            assertFalse(journalMapping.contains(forbidden), forbidden);
        }

        var innerMaximum = Math.addExact(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                Math.addExact(
                        MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES,
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES));
        assertEquals(68_157_531, innerMaximum);
        assertTrue(innerMaximum
                <= MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES);

        assertEquals(Set.of("RandomUuidSkillIdSource.java"),
                relativeSourcesContaining("UUID.randomUUID()"));
        assertEquals(Set.of("DefaultSkillSubmissionPolicyProvider.java"),
                relativeSourcesContaining("SkillQuota.Unlimited.INSTANCE"));
        assertEquals(Set.of("DefaultSkillSubmissionPolicyProvider.java"),
                relativeSourcesContaining(
                        "new ValidationContext(MagicPolicyLimits.DEFAULTS)"));
        assertEquals(Set.of("SkillDefinitionStoreSubmissionPort.java"),
                relativeStoreSourcesMatching(Pattern.compile("\\.\\s*commit\\s*\\(")));
        assertEquals(Set.of("PlayerSkillAttachmentService.java"),
                relativeSourcesMatching(Pattern.compile("\\.\\s*setData\\s*\\(")));
        var draftService = withoutCommentsAndLiterals(read(
                SUBMISSION_ROOT.resolve("SkillDraftCreationService.java")));
        assertEquals(1, occurrences(
                draftService, "attachmentService.ownerId((ServerPlayer) player)"));
    }

    @Test
    void d2BAndLaterSurfacesRemainAbsentAndBuildConfigurationIsUnchanged()
            throws Exception {
        var allMain = javaSources(MAIN_JAVA).stream()
                .map(P4D2ApiGateTest::read)
                .collect(Collectors.joining("\n"));
        for (var forbidden : List.of(
                "SkillDefinitionSubmissionService",
                "PlayerLoggedInEvent",
                "PlayerLoggedOutEvent",
                "OfflineRoot",
                "Reconciliation",
                "CustomPacketPayload",
                "PayloadRegistrar")) {
            assertFalse(allMain.contains(forbidden), forbidden);
        }
        assertFalse(read(PROJECT_ROOT.resolve("build.gradle")).contains("p4D"));
        assertFalse(read(PROJECT_ROOT.resolve(".github/workflows/build.yml"))
                .contains("P4-D memory gates"));
    }

    private static Set<String> publicDeclaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
    }

    private static List<String> recordComponentNames(Class<?> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }

    private static Set<String> relativeSourcesContaining(String fragment) throws Exception {
        return javaSources(MAIN_JAVA).stream()
                .filter(path -> read(path).contains(fragment))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> relativeSourcesMatching(Pattern pattern) throws Exception {
        return javaSources(MAIN_JAVA).stream()
                .filter(path -> pattern.matcher(withoutCommentsAndLiterals(read(path))).find())
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> relativeStoreSourcesMatching(Pattern pattern) throws Exception {
        var root = MAIN_JAVA.resolve("com/yo1no/gramarye/magic/definition/store");
        return javaSources(root).stream()
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

    private static Class<?> loadSubmission(String simpleName) {
        try {
            return Class.forName(
                    "com.yo1no.gramarye.magic.definition.submission." + simpleName,
                    false,
                    P4D2ApiGateTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("missing reviewed D2-A type " + simpleName, exception);
        }
    }

    private static String methodBody(String source, String methodName) {
        var signature = source.indexOf(methodName + "(");
        if (signature < 0) {
            throw new AssertionError("method not found: " + methodName);
        }
        var open = source.indexOf('{', signature);
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("method body did not close: " + methodName);
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment);
                index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
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
