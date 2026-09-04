package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillDraftCreationService;
import com.yo1no.gramarye.magic.definition.submission.SkillIdSource;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPolicyProvider;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.junit.jupiter.api.Test;

/** Exact facade, composition-root, ownership, and phase boundary Gate for P4-D2-B. */
final class P4D2BApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path SERVICE_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/submission/"
                    + "SkillDefinitionSubmissionService.java");
    private static final Path SUBMISSION_GAME_TEST_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/submission/"
                    + "SkillDefinitionSubmissionGameTests.java");
    private static final Path GRAMARYE_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/Gramarye.java");
    private static final Path MANA_GAME_TEST_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java");
    private static final Pattern STORE_COMMIT_CALL = Pattern.compile(
            "\\.\\s*commit\\s*\\(");
    private static final Pattern SET_DATA_CALL = Pattern.compile(
            "\\.\\s*setData\\s*\\(");
    private static final Pattern RECLAIM_CALL = Pattern.compile(
            "\\.\\s*reclaim\\s*\\(");
    private static final Pattern PREPARE_JOURNAL_CLEAR_CALL = Pattern.compile(
            "\\.\\s*prepareJournalPrefixClear\\s*\\(");
    private static final Pattern COMMIT_JOURNAL_CLEAR_CALL = Pattern.compile(
            "\\.\\s*commitPreparedJournalClear\\s*\\(");

    @Test
    void facadeHasOneAuthenticatedDomainEntryAndNoPublicPartialDependencySurface()
            throws Exception {
        var type = SkillDefinitionSubmissionService.class;
        var submit = type.getDeclaredMethod("submit", ServerPlayer.class, SkillId.class);
        var production = type.getDeclaredMethod(
                "production",
                PlayerSkillAttachmentService.class,
                SkillDefinitionStoreSubmissionPort.class,
                SkillSubmissionPolicyProvider.class);

        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertTrue(Modifier.isPublic(submit.getModifiers()));
        assertFalse(Modifier.isStatic(submit.getModifiers()));
        assertEquals(SkillSubmissionCompositionOutcome.class, submit.getReturnType());
        assertTrue(Modifier.isPublic(production.getModifiers()));
        assertTrue(Modifier.isStatic(production.getModifiers()));
        assertEquals(type, production.getReturnType());
        assertEquals(Set.of("production", "submit"), publicDeclaredMethodNames(type));
        assertTrue(Arrays.stream(type.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                        || Modifier.isProtected(constructor.getModifiers())));
        assertTrue(Arrays.stream(type.getDeclaredClasses())
                .noneMatch(nested -> Modifier.isPublic(nested.getModifiers())
                        || Modifier.isProtected(nested.getModifiers())));
        assertEquals(1, Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .count());
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && field.getType().getSimpleName().equals("Dependencies")));
    }

    @Test
    void facadeOwnsOneStrictFirstOrderOrchestrationWithoutDirectStateMutation() {
        var source = withoutCommentsAndLiterals(read(SERVICE_SOURCE));
        var core = methodBody(source, "submitCore");
        var orderedCalls = List.of(
                "dependencies.findDraft(",
                "dependencies.precheck(",
                "dependencies.observeSubmissionAuthority(",
                "dependencies.checkAuthority(",
                "dependencies.snapshotPolicy(",
                "dependencies.prepareAndMap(",
                "dependencies.prepareLatestTransitionToCurrent(",
                "dependencies.prepareSubmissionCommit(",
                "dependencies.checkPreparedTransitionCurrent(",
                "dependencies.commitPreparedSubmission(",
                "dependencies.publishPreparedTransition(");
        var previous = -1;
        for (var call : orderedCalls) {
            assertEquals(1, occurrences(core, call), call);
            var current = core.indexOf(call);
            assertTrue(previous < current, () -> "out-of-order orchestration call " + call);
            previous = current;
        }
        assertEquals(2, occurrences(core, "catch (RuntimeException exception)"));
        assertFalse(core.contains("catch (Error"));
        assertFalse(core.contains("catch (Throwable"));
        assertFalse(source.contains(".setData("));
        assertFalse(STORE_COMMIT_CALL.matcher(source).find());
        assertFalse(source.contains("SkillDefinitionStore "));
        assertFalse(source.contains("PlayerSkillAttachmentState"));
        assertFalse(source.contains("@SuppressWarnings"));
    }

    @Test
    void compositionRootOwnsOneProductionDependencyGraphWithoutPublicLocator() {
        assertPrivateFinalFieldCount(PlayerSkillAttachmentService.class, 1);
        assertPrivateFinalFieldCount(SkillDefinitionStoreService.class, 1);
        assertPrivateFinalFieldCount(SkillIdSource.class, 1);
        assertPrivateFinalFieldCount(SkillDraftCreationService.class, 1);
        assertPrivateFinalFieldCount(SkillSubmissionPolicyProvider.class, 1);
        assertPrivateFinalFieldCount(SkillDefinitionSubmissionService.class, 1);

        var source = withoutCommentsAndLiterals(read(GRAMARYE_SOURCE));
        assertEquals(1, occurrences(source, "SkillDraftCreationService.randomUuidSkillIdSource()"));
        assertEquals(1, occurrences(source, "new SkillDraftCreationService("));
        assertEquals(1, occurrences(source, "SkillSubmissionPolicyProvider.defaults()"));
        assertEquals(1, occurrences(source, "SkillDefinitionSubmissionService.production("));
        assertTrue(Arrays.stream(Gramarye.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .noneMatch(method -> Set.of(
                                SkillIdSource.class,
                                SkillDraftCreationService.class,
                                SkillSubmissionPolicyProvider.class,
                                SkillDefinitionSubmissionService.class)
                        .contains(method.getReturnType())));
    }

    @Test
    void twoSubmissionGameTestsRaiseTheNormalRequiredTotalToNine() throws Exception {
        var playerHolder = Class.forName(
                "com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests",
                false,
                P4D2BApiGateTest.class.getClassLoader());
        var holder = Class.forName(
                "com.yo1no.gramarye.magic.definition.submission."
                        + "SkillDefinitionSubmissionGameTests",
                false,
                P4D2BApiGateTest.class.getClassLoader());
        var methods = Arrays.stream(holder.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GameTest.class))
                .toList();
        var holderAnnotation = holder.getAnnotation(GameTestHolder.class);
        var allMain = javaSources(MAIN_JAVA).stream()
                .map(P4D2BApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var manaGameTests = read(MANA_GAME_TEST_SOURCE);
        var totalGameTestCount = occurrences(allMain, "@GameTest(");
        var manaGameTestCount = occurrences(manaGameTests, "@GameTest(");
        assertTrue(Modifier.isPublic(holder.getModifiers()));
        assertTrue(Modifier.isFinal(holder.getModifiers()));
        assertTrue(Arrays.stream(holder.getDeclaredClasses())
                .noneMatch(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers())));
        assertEquals(2, methods.size());
        assertTrue(methods.stream().allMatch(method ->
                Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers())));
        assertTrue(holderAnnotation != null
                && holderAnnotation.value().equals(Gramarye.MOD_ID));
        assertEquals(Set.of(
                        "newServiceForSubmissionGameTests",
                        "registeredAttachmentPersistsThroughActualPlayerdataSaveAndReload",
                        "registeredQuarantineAndCopyLifecycleRemainTotal"),
                publicDeclaredMethodNames(playerHolder));
        var bridge = playerHolder.getDeclaredMethod("newServiceForSubmissionGameTests");
        assertTrue(Modifier.isPublic(bridge.getModifiers()));
        assertTrue(Modifier.isStatic(bridge.getModifiers()));
        assertEquals(PlayerSkillAttachmentService.class, bridge.getReturnType());
        assertEquals(Set.of(
                        "fullSubmissionCommitsStoreJournalThenAttachmentExactlyOnce",
                        "postCommitAttachmentDriftReturnsPendingRecovery"),
                methods.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(2, occurrences(read(SUBMISSION_GAME_TEST_SOURCE), "@GameTest("));
        assertEquals(12, totalGameTestCount - manaGameTestCount);
        assertEquals(7, manaGameTestCount);
        assertEquals(19, totalGameTestCount);
    }

    @Test
    void uniqueMutationOwnersAndLaterPhaseAbsenceRemainClosed() throws Exception {
        assertEquals(Set.of("SkillDefinitionStoreSubmissionPort.java"),
                relativeSourcesMatching(STORE_COMMIT_CALL));
        assertEquals(Set.of("PlayerSkillAttachmentService.java", "ManaAttachments.java"),
                relativeSourcesMatching(SET_DATA_CALL));
        assertEquals(Set.of(
                        "GramaryeSkillSavedData.java",
                        "SkillDefinitionStoreService.java"),
                relativeSourcesMatching(RECLAIM_CALL));
        assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                relativeSourcesMatching(PREPARE_JOURNAL_CLEAR_CALL));
        assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                relativeSourcesMatching(COMMIT_JOURNAL_CLEAR_CALL));
        assertEquals(Set.of("RandomUuidSkillIdSource.java"),
                relativeSourcesContaining("UUID.randomUUID()"));
        assertEquals(Set.of("DefaultSkillSubmissionPolicyProvider.java"),
                relativeSourcesContaining("SkillQuota.Unlimited.INSTANCE"));
        assertEquals(Set.of("DefaultSkillSubmissionPolicyProvider.java"),
                relativeSourcesContaining(
                        "new ValidationContext(MagicPolicyLimits.DEFAULTS)"));

        var allMain = javaSources(MAIN_JAVA).stream()
                .map(P4D2BApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var mainWithoutReviewedReconciliationOwners = javaSources(MAIN_JAVA).stream()
                .filter(path -> !Set.of(
                                MAIN_JAVA.resolve("com/yo1no/gramarye/magic/definition/store/"
                                                + "P4E1GroupedStoreAudit.java")
                                        .toAbsolutePath().normalize(),
                                MAIN_JAVA.resolve("com/yo1no/gramarye/magic/definition/store/"
                                                + "SkillRetentionRootAuditResult.java")
                                        .toAbsolutePath().normalize(),
                                MAIN_JAVA.resolve("com/yo1no/gramarye/magic/definition/store/"
                                                + "SkillRetentionRootAuditService.java")
                                        .toAbsolutePath().normalize())
                        .contains(path.toAbsolutePath().normalize())
                        && !P4DPhaseTypes.E2_RECONCILIATION_PRODUCTION_SOURCE_PATHS.contains(
                                MAIN_JAVA.relativize(path).toString()))
                .map(P4D2BApiGateTest::read)
                .collect(Collectors.joining("\n"));
        assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                relativeSourcesContaining("PlayerLoggedInEvent"));
        for (var forbidden : List.of(
                "PlayerLoggedOutEvent",
                "OfflineRoot",
                "RootCollector",
                "RootIndex",
                "PacketDistributor")) {
            assertFalse(allMain.contains(forbidden), forbidden);
        }
        assertEquals(
                Set.of(
                        "com/yo1no/gramarye/magic/network/CastIntentPayload.java",
                        "com/yo1no/gramarye/magic/network/IntentAckPayload.java",
                        "com/yo1no/gramarye/magic/network/PlayerManaSyncPayload.java",
                        "com/yo1no/gramarye/magic/network/SkillCooldownSyncPayload.java"),
                relativeProductionPathsContaining("CustomPacketPayload"));
        assertEquals(
                Set.of("com/yo1no/gramarye/magic/network/P7PayloadRegistrar.java"),
                relativeProductionPathsContaining("PayloadRegistrar"));
        assertFalse(mainWithoutReviewedReconciliationOwners.contains("Reconciliation"),
                "reconciliation escaped the exact E1/E2 owners");
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        assertEquals(2, occurrences(build, "sourceSets.create('p4D3"));
        assertTrue(build.contains("sourceSets.create('p4D3Probe')"));
        assertTrue(build.contains("sourceSets.create('p4D3GameTest')"));
        assertTrue(workflow.contains("  p4-d-memory-gates:"));
        assertTrue(workflow.contains("    name: P4-D memory gates"));
    }

    private static void assertPrivateFinalFieldCount(Class<?> fieldType, long expected) {
        assertEquals(expected, Arrays.stream(Gramarye.class.getDeclaredFields())
                .filter(field -> field.getType() == fieldType)
                .filter(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers()))
                .count(), fieldType.getName());
    }

    private static Set<String> publicDeclaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
    }

    private static Set<String> relativeSourcesContaining(String fragment) throws Exception {
        return javaSources(MAIN_JAVA).stream()
                .filter(path -> read(path).contains(fragment))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static Set<String> relativeProductionPathsContaining(String fragment)
            throws Exception {
        return javaSources(MAIN_JAVA).stream()
                .filter(path -> read(path).contains(fragment))
                .map(MAIN_JAVA::relativize)
                .map(path -> path.toString().replace('\\', '/'))
                .collect(Collectors.toSet());
    }

    private static Set<String> relativeSourcesMatching(Pattern pattern) throws Exception {
        return javaSources(MAIN_JAVA).stream()
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
