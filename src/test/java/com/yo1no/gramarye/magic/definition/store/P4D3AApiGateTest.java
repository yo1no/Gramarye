package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.junit.jupiter.api.Test;

/** Exact public API, lifecycle ownership, and later-phase absence gate for P4-D3-A. */
final class P4D3AApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path RECOVERY_SERVICE =
            MAIN_JAVA.resolve(P4D3PhaseTypes.RECOVERY_SERVICE_PATH);
    private static final Path RECOVERY_GAME_TESTS =
            MAIN_JAVA.resolve(P4D3PhaseTypes.RECOVERY_GAME_TEST_PATH);
    private static final Path PLAYER_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java");
    private static final Path STORE_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java");
    private static final Path STORE_PORT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/"
                    + "SkillDefinitionStoreSubmissionPort.java");
    private static final Pattern STORE_COMMIT_CALL = Pattern.compile(
            "\\.\\s*commit\\s*\\(");
    private static final Pattern STORE_RECLAIM_CALL = Pattern.compile(
            "\\.\\s*reclaim\\s*\\(");
    private static final Pattern SET_DATA_CALL = Pattern.compile(
            "\\.\\s*setData\\s*\\(");
    private static final Pattern PREPARE_CLEAR_CALL = Pattern.compile(
            "\\.\\s*prepareJournalPrefixClear\\s*\\(");
    private static final Pattern COMMIT_CLEAR_CALL = Pattern.compile(
            "\\.\\s*commitPreparedJournalClear\\s*\\(");

    @Test
    void phaseOwnsExactlyTwoNewProductionSourcesAndSixReviewedIntegrations() {
        assertAll(
                () -> assertTrue(P4D3PhaseTypes.NEW_PRODUCTION_SOURCE_PATHS.stream()
                        .map(MAIN_JAVA::resolve)
                        .allMatch(Files::isRegularFile)),
                () -> assertTrue(P4D3PhaseTypes.MODIFIED_PRODUCTION_SOURCE_PATHS.stream()
                        .map(MAIN_JAVA::resolve)
                        .allMatch(Files::isRegularFile)),
                () -> assertTrue(Modifier.isPublic(
                        SkillSubmissionRecoveryService.class.getModifiers())),
                () -> assertTrue(Modifier.isFinal(
                        SkillSubmissionRecoveryService.class.getModifiers())));
    }

    @Test
    void recoveryServiceExposesOnlyFactoryAndSingleRegistrationEntry() throws Exception {
        var create = SkillSubmissionRecoveryService.class.getDeclaredMethod(
                "create",
                PlayerSkillAttachmentService.class,
                SkillDefinitionStoreSubmissionPort.class);
        var register = SkillSubmissionRecoveryService.class.getDeclaredMethod(
                "registerOn", IEventBus.class);
        var source = withoutCommentsAndLiterals(read(RECOVERY_SERVICE));

        assertAll(
                () -> assertEquals(
                        P4D3PhaseTypes.RECOVERY_SERVICE_PUBLIC_METHOD_NAMES,
                        publicDeclaredMethodNames(SkillSubmissionRecoveryService.class)),
                () -> assertTrue(Modifier.isPublic(create.getModifiers())
                        && Modifier.isStatic(create.getModifiers())),
                () -> assertEquals(SkillSubmissionRecoveryService.class, create.getReturnType()),
                () -> assertTrue(Modifier.isPublic(register.getModifiers())
                        && !Modifier.isStatic(register.getModifiers())),
                () -> assertEquals(void.class, register.getReturnType()),
                () -> assertTrue(Arrays.stream(
                                SkillSubmissionRecoveryService.class.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())
                                || Modifier.isProtected(constructor.getModifiers()))),
                () -> assertTrue(Arrays.stream(
                                SkillSubmissionRecoveryService.class.getDeclaredClasses())
                        .noneMatch(type -> Modifier.isPublic(type.getModifiers())
                                || Modifier.isProtected(type.getModifiers()))),
                () -> assertEquals(1, occurrences(source, "PlayerEvent.PlayerLoggedInEvent")),
                () -> assertEquals(1, occurrences(source, "addListener(this::onPlayerLoggedIn)")),
                () -> assertEquals(1, occurrences(source, "recoverPersistedPlayer(player)")),
                () -> assertFalse(source.contains("PlayerLoggedOutEvent")),
                () -> assertFalse(source.contains("PlayerEvent.Clone")),
                () -> assertFalse(source.contains("SkillDefinitionSubmissionService")),
                () -> assertFalse(source.contains(".reclaim(")),
                () -> assertFalse(source.contains(".sync(")));
    }

    @Test
    void storePortAddsOneOwnerScopedProjectionWithoutLeakingJournalTruth()
            throws Exception {
        var projection = SkillDefinitionStoreSubmissionPort.class.getDeclaredMethod(
                "observePendingRecovery", MinecraftServer.class,
                com.yo1no.gramarye.magic.api.id.SkillOwnerId.class);
        var recoveryTypes = Arrays.stream(
                        SkillDefinitionStoreSubmissionPort.class.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .map(Class::getSimpleName)
                .filter(name -> name.startsWith("PendingRecovery")
                        || name.equals("PendingSkillRecoveryChain"))
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(P4D3PhaseTypes.PORT_PUBLIC_METHOD_NAMES,
                        publicDeclaredMethodNames(SkillDefinitionStoreSubmissionPort.class)),
                () -> assertTrue(Modifier.isPublic(projection.getModifiers())),
                () -> assertEquals(
                        SkillDefinitionStoreSubmissionPort.PendingRecoveryProjection.class,
                        projection.getReturnType()),
                () -> assertEquals(
                        P4D3PhaseTypes.PORT_RECOVERY_PUBLIC_NESTED_TYPE_NAMES,
                        recoveryTypes),
                () -> assertEquals(
                        Set.of("Available", "TargetInvalid", "Unavailable"),
                        Arrays.stream(SkillDefinitionStoreSubmissionPort
                                        .PendingRecoveryProjection.class
                                        .getPermittedSubclasses())
                                .map(Class::getSimpleName)
                                .collect(Collectors.toSet())),
                () -> assertEquals(List.of("skillId", "steps"),
                        recordComponentNames(SkillDefinitionStoreSubmissionPort
                                .PendingSkillRecoveryChain.class)),
                () -> assertEquals(
                        List.of(
                                "expectedPointer",
                                "expectedGeneration",
                                "targetPointer",
                                "targetGeneration"),
                        recordComponentNames(SkillDefinitionStoreSubmissionPort
                                .PendingRecoveryStep.class)),
                () -> assertEquals(
                        Set.of("MISSING", "OWNER_MISMATCH"),
                        Arrays.stream(SkillDefinitionStoreSubmissionPort
                                        .PendingRecoveryTargetFailure.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet())),
                () -> assertEquals(
                        Set.of(
                                "JOURNAL_NOT_BOOTSTRAPPED",
                                "JOURNAL_UNAVAILABLE",
                                "STORE_UNAVAILABLE",
                                "AUTHORITY_UNAVAILABLE"),
                        Arrays.stream(SkillDefinitionStoreSubmissionPort
                                        .PendingRecoveryUnavailableReason.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet())),
                () -> assertTrue(Arrays.stream(projection.getGenericParameterTypes())
                        .noneMatch(type -> exposesRawPersistenceTruth(type.getTypeName()))),
                () -> assertFalse(exposesRawPersistenceTruth(
                        projection.getGenericReturnType().getTypeName())));
    }

    @Test
    void playerServiceAddsOneNonInstallingCanonicalBatchObservation() throws Exception {
        var observation = PlayerSkillAttachmentService.class.getDeclaredMethod(
                "observeLatestStates", ServerPlayer.class);
        var latestView = PlayerSkillAttachmentService.LatestStateView.class;
        var source = withoutCommentsAndLiterals(read(PLAYER_SERVICE));
        var body = methodBody(source, "observeLatestStates");

        assertAll(
                () -> assertTrue(Modifier.isPublic(observation.getModifiers())),
                () -> assertEquals(List.of("skillId", "pointer", "mutationGeneration"),
                        recordComponentNames(latestView)),
                () -> assertEquals(SkillId.class,
                        latestView.getRecordComponents()[0].getType()),
                () -> assertEquals(Optional.class,
                        latestView.getRecordComponents()[1].getType()),
                () -> assertEquals(int.class,
                        latestView.getRecordComponents()[2].getType()),
                () -> assertEquals(1, occurrences(body, "observeChecked(player)")),
                () -> assertFalse(body.contains("getData(")),
                () -> assertFalse(body.contains("setData(")),
                () -> assertFalse(body.contains("SkillDefinitionStore")));
    }

    @Test
    void recoveryOutcomeTaxonomyIsBoundedPackagePrivateAndExact() throws Exception {
        var outcome = recoveryNested("RecoveryOutcome");
        var conflict = recoveryNested("Conflict");
        var targetInvalid = recoveryNested("TargetInvalid");
        var unavailable = recoveryNested("Unavailable");
        var conflictCode = recoveryNested("RecoveryConflictCode");
        var unavailableReason = recoveryNested("RecoveryUnavailableReason");

        assertAll(
                () -> assertTrue(outcome.isSealed()),
                () -> assertEquals(
                        Set.of(
                                "NoPending",
                                "Cleared",
                                "Replayed",
                                "ClearedAndReplayed",
                                "Conflict",
                                "TargetInvalid",
                                "Unavailable"),
                        Arrays.stream(outcome.getPermittedSubclasses())
                                .map(Class::getSimpleName)
                                .collect(Collectors.toSet())),
                () -> assertEquals(
                        List.of(
                                "skillId",
                                "code",
                                "entriesClearedBeforeFailure",
                                "stepsReplayedBeforeFailure"),
                        recordComponentNames(conflict)),
                () -> assertEquals(
                        List.of(
                                "skillId",
                                "target",
                                "reason",
                                "entriesClearedBeforeFailure",
                                "stepsReplayedBeforeFailure"),
                        recordComponentNames(targetInvalid)),
                () -> assertEquals(
                        List.of(
                                "reason",
                                "entriesClearedBeforeFailure",
                                "stepsReplayedBeforeFailure",
                                "exceptionClass"),
                        recordComponentNames(unavailable)),
                () -> assertEquals(
                        Set.of(
                                "THIRD_STATE",
                                "CLEAR_PREPARATION_REJECTED",
                                "CLEAR_COMMIT_REJECTED",
                                "REPLAY_PREPARATION_REJECTED",
                                "REPLAY_CURRENTNESS_CHANGED",
                                "REPLAY_PUBLICATION_REJECTED",
                                "REPLAY_UNEXPECTED_NO_OP"),
                        enumNames(conflictCode)),
                () -> assertEquals(
                        Set.of(
                                "JOURNAL_NOT_BOOTSTRAPPED",
                                "JOURNAL_UNAVAILABLE",
                                "STORE_UNAVAILABLE",
                                "AUTHORITY_UNAVAILABLE",
                                "ATTACHMENT_PRESERVED_RAW_QUARANTINE",
                                "ATTACHMENT_OVERSIZE_QUARANTINE",
                                "RUNTIME_EXCEPTION"),
                        enumNames(unavailableReason)),
                () -> assertTrue(List.of(
                                outcome,
                                conflict,
                                targetInvalid,
                                unavailable,
                                conflictCode,
                                unavailableReason)
                        .stream()
                        .noneMatch(type -> Modifier.isPublic(type.getModifiers())
                                || Modifier.isProtected(type.getModifiers()))),
                () -> assertTrue(List.of(conflict, targetInvalid, unavailable).stream()
                        .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                        .noneMatch(component -> exposesRawPersistenceTruth(
                                component.getGenericType().getTypeName()))));
    }

    @Test
    void productionBootstrapAndCompositionOwnOneExplicitLifecycleOrder() throws Exception {
        var storeSource = withoutCommentsAndLiterals(read(STORE_SERVICE));
        var callback = methodBody(storeSource, "onServerStarting");
        var root = withoutCommentsAndLiterals(read(
                MAIN_JAVA.resolve("com/yo1no/gramarye/Gramarye.java")));

        assertAll(
                () -> assertOrdered(callback, "install(server)",
                        "submissionPort.bootstrapJournal(server)"),
                () -> assertEquals(1, occurrences(callback, "install(server)")),
                () -> assertEquals(1,
                        occurrences(callback, "submissionPort.bootstrapJournal(server)")),
                () -> assertFalse(methodBody(storeSource, "install")
                        .contains("bootstrapJournal(")),
                () -> assertEquals(1, occurrences(root,
                        "SkillSubmissionRecoveryService.create(")),
                () -> assertEquals(1, occurrences(root,
                        "skillSubmissionRecoveryService.registerOn(NeoForge.EVENT_BUS)")));
    }

    @Test
    void threeNormalRecoveryGameTestsRaiseTheExactRequiredTotalToTwelve()
            throws Exception {
        var holder = Class.forName(
                "com.yo1no.gramarye.magic.definition.store."
                        + "SkillSubmissionRecoveryGameTests",
                false,
                P4D3AApiGateTest.class.getClassLoader());
        var methods = Arrays.stream(holder.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(GameTest.class))
                .toList();
        var allProduction = javaSources(MAIN_JAVA).stream()
                .map(P4D3AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var holderAnnotation = holder.getAnnotation(GameTestHolder.class);

        assertAll(
                () -> assertTrue(Modifier.isPublic(holder.getModifiers())),
                () -> assertTrue(Modifier.isFinal(holder.getModifiers())),
                () -> assertEquals(3, methods.size()),
                () -> assertTrue(methods.stream().allMatch(method ->
                        Modifier.isPublic(method.getModifiers())
                                && Modifier.isStatic(method.getModifiers()))),
                () -> assertEquals(P4D3PhaseTypes.RECOVERY_GAME_TEST_METHOD_NAMES,
                        methods.stream().map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertTrue(holderAnnotation != null
                        && holderAnnotation.value().equals(Gramarye.MOD_ID)),
                () -> assertEquals(3, occurrences(read(RECOVERY_GAME_TESTS), "@GameTest(")),
                () -> assertEquals(12, occurrences(allProduction, "@GameTest(")));
    }

    @Test
    void mutationOwnersRemainClosedAndD3BTestSurfacesStayIsolated() throws Exception {
        assertAll(
                () -> assertEquals(Set.of("SkillDefinitionStoreSubmissionPort.java"),
                        relativeSourcesMatching(STORE_COMMIT_CALL)),
                () -> assertEquals(Set.of(
                                "GramaryeSkillSavedData.java",
                                "SkillDefinitionStoreService.java"),
                        relativeSourcesMatching(STORE_RECLAIM_CALL)),
                () -> assertEquals(Set.of("PlayerSkillAttachmentService.java"),
                        relativeSourcesMatching(SET_DATA_CALL)),
                () -> assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                        relativeSourcesMatching(PREPARE_CLEAR_CALL)),
                () -> assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                        relativeSourcesMatching(COMMIT_CLEAR_CALL)),
                () -> assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                        relativeSourcesContaining("PlayerLoggedInEvent")));

        var allProduction = javaSources(MAIN_JAVA).stream()
                .map(P4D3AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var productionWithoutReviewedReconciliationOwners = javaSources(MAIN_JAVA).stream()
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
                        .contains(path.toAbsolutePath().normalize()))
                .map(P4D3AApiGateTest::read)
                .collect(Collectors.joining("\n"));
        var allUnitTests = javaSources(PROJECT_ROOT.resolve("src/test/java")).stream()
                .map(P4D3AApiGateTest::read)
                .map(P4D3AApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));
        for (var forbidden : List.of(
                "PlayerLoggedOutEvent",
                "OfflineRoot",
                "RootCollector",
                "RootIndex",
                "CustomPacketPayload",
                "PayloadRegistrar",
                "PacketDistributor")) {
            assertFalse(allProduction.contains(forbidden), forbidden);
        }
        assertFalse(productionWithoutReviewedReconciliationOwners.contains("Reconciliation"),
                "reconciliation escaped the exact B2-A/B2-B owners");
        assertAll(
                () -> assertFalse(allProduction.contains("Runtime.getRuntime()" + ".halt")),
                () -> assertFalse(allUnitTests.contains("Runtime.getRuntime()" + ".halt")),
                () -> assertTrue(Files.isDirectory(PROJECT_ROOT.resolve("src/p4D3Probe"))),
                () -> assertTrue(Files.isDirectory(PROJECT_ROOT.resolve("src/p4D3GameTest"))),
                () -> assertEquals(2, occurrences(
                        read(PROJECT_ROOT.resolve("build.gradle")),
                        "sourceSets.create('p4D3")),
                () -> assertTrue(read(PROJECT_ROOT.resolve("build.gradle"))
                        .contains("sourceSets.create('p4D3Probe')")),
                () -> assertTrue(read(PROJECT_ROOT.resolve("build.gradle"))
                        .contains("sourceSets.create('p4D3GameTest')")),
                () -> assertTrue(read(PROJECT_ROOT.resolve(".github/workflows/build.yml"))
                        .contains("    name: P4-D memory gates")));
    }

    private static boolean exposesRawPersistenceTruth(String typeName) {
        return typeName.contains("PendingAttachmentJournal")
                || typeName.contains("GramaryeSkillSavedData")
                || typeName.matches(".*(?:^|[.$])SkillDefinitionStore(?:[<>, ]|$).*")
                || typeName.contains("Carrier")
                || typeName.contains("net.minecraft.nbt")
                || typeName.contains("java.nio.file.Path")
                || typeName.equals(byte[].class.getTypeName());
    }

    private static Set<String> publicDeclaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
    }

    private static Class<?> recoveryNested(String simpleName) throws ClassNotFoundException {
        return Class.forName(
                SkillSubmissionRecoveryService.class.getName() + "$" + simpleName,
                false,
                P4D3AApiGateTest.class.getClassLoader());
    }

    private static Set<String> enumNames(Class<?> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(value -> ((Enum<?>) value).name())
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

    private static List<Path> javaSources(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static void assertOrdered(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var current = source.indexOf(fragment, previous + 1);
            assertTrue(current >= 0, "Missing ordered fragment: " + fragment);
            assertTrue(current > previous, "Out-of-order fragment: " + fragment);
            previous = current;
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

    private static String withoutCommentsAndLiterals(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("(?s)\"(?:\\\\.|[^\"\\\\])*\"", " ")
                .replaceAll("(?s)'(?:\\\\.|[^'\\\\])*'", " ");
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
