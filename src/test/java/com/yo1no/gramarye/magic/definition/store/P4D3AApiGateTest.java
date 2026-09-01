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
    private static final String STORE_COMMIT_METHOD = "commit";
    private static final String STORE_RECLAIM_METHOD = "reclaim";
    private static final String SET_DATA_METHOD = "setData";
    private static final String PREPARE_CLEAR_METHOD = "prepareJournalPrefixClear";
    private static final String COMMIT_CLEAR_METHOD = "commitPreparedJournalClear";

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
                SkillDefinitionStoreSubmissionPort.class,
                P4E2OnlineReconciliationDependency.class);
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
                () -> assertEquals(
                        P4D3PhaseTypes.E2_RECOVERY_SERVICE_PUBLIC_NESTED_TYPE_NAMES,
                        Arrays.stream(SkillSubmissionRecoveryService.class.getDeclaredClasses())
                                .filter(type -> Modifier.isPublic(type.getModifiers())
                                        || Modifier.isProtected(type.getModifiers()))
                                .map(Class::getSimpleName)
                                .collect(Collectors.toSet())),
                () -> assertTrue(Arrays.stream(
                                SkillSubmissionRecoveryService.RecoveryContinuation.class
                                        .getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(
                                constructor.getModifiers()))),
                () -> assertTrue(Modifier.isPublic(
                        SkillSubmissionRecoveryService.RecoveryContinuation.class
                                .getModifiers())),
                () -> assertTrue(Modifier.isStatic(
                        SkillSubmissionRecoveryService.RecoveryContinuation.class
                                .getModifiers())),
                () -> assertTrue(Modifier.isFinal(
                        SkillSubmissionRecoveryService.RecoveryContinuation.class
                                .getModifiers())),
                () -> assertTrue(Arrays.stream(
                                SkillSubmissionRecoveryService.RecoveryContinuation.class
                                        .getDeclaredMethods())
                        .noneMatch(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))),
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
                () -> assertEquals(19, occurrences(allProduction, "@GameTest(")));
    }

    @Test
    void mutationOwnersRemainClosedAndD3BTestSurfacesStayIsolated() throws Exception {
        assertAll(
                () -> assertEquals(Set.of("SkillDefinitionStoreSubmissionPort.java"),
                        relativeSourcesInvoking(STORE_COMMIT_METHOD)),
                () -> assertEquals(Set.of(
                                "GramaryeSkillSavedData.java",
                                "SkillDefinitionStoreService.java"),
                        relativeSourcesInvoking(STORE_RECLAIM_METHOD)),
                () -> assertEquals(Set.of(
                                "PlayerSkillAttachmentService.java", "ManaAttachments.java"),
                        relativeSourcesInvoking(SET_DATA_METHOD)),
                () -> assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                        relativeSourcesInvoking(PREPARE_CLEAR_METHOD)),
                () -> assertEquals(Set.of("SkillSubmissionRecoveryService.java"),
                        relativeSourcesInvoking(COMMIT_CLEAR_METHOD)),
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
                        .contains(path.toAbsolutePath().normalize())
                        && !P4DPhaseTypes.E2_RECONCILIATION_PRODUCTION_SOURCE_PATHS.contains(
                                MAIN_JAVA.relativize(path).toString()))
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
                "reconciliation escaped the exact E1/E2 owners");
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

    @Test
    void lexicalSourceScanningIsLengthStableAndInvocationEquivalent() {
        var escapedTextBlockQuotes = "\\" + "\"\"\"";
        var textBlockContinuation = "\\" + "\r\n";
        var source = String.join("",
                "owner . commit (\r\n",
                "// .reclaim(\r\n",
                "/* .prepareJournalPrefixClear(\n */\r",
                "var ordinary = \".prepareJournalPrefixClear(\";\n",
                "var quote = '\\'';\r\n",
                "var slash = '\\\\';\n",
                "var text = \"\"\" \t\f\r\n",
                "var embedded = \".commitPreparedJournalClear(\";\r\n",
                ".commitPreparedJournalClear(\r\n",
                escapedTextBlockQuotes,
                "\r\n",
                textBlockContinuation,
                ".reclaim(\n",
                "\"\"\";\r\n",
                "owner . setData (\r\n");
        var masked = withoutCommentsAndLiterals(source);
        var asciiWhitespace = " \t\n" + (char) 0x0B + "\f\r";

        assertAll(
                () -> assertEquals(source.length(), masked.length()),
                () -> {
                    for (var index = 0; index < source.length(); index++) {
                        var original = source.charAt(index);
                        var sanitized = masked.charAt(index);
                        if (original == '\r' || original == '\n'
                                || sanitized == '\r' || sanitized == '\n') {
                            assertEquals(original, sanitized,
                                    "line terminator changed at " + index);
                        }
                    }
                },
                () -> assertTrue(containsInvocation(masked, "commit")),
                () -> assertTrue(containsInvocation(masked, "setData")),
                () -> assertFalse(containsInvocation(masked, "reclaim")),
                () -> assertFalse(containsInvocation(
                        masked, "prepareJournalPrefixClear")),
                () -> assertFalse(containsInvocation(
                        masked, "commitPreparedJournalClear")),
                () -> assertTrue(containsInvocation(
                        "." + asciiWhitespace + "commit" + asciiWhitespace + "(",
                        "commit")),
                () -> assertTrue(containsInvocation("..commit(", "commit")),
                () -> assertFalse(containsInvocation("commit(", "commit")),
                () -> assertFalse(containsInvocation(".Commit(", "commit")),
                () -> assertFalse(containsInvocation(".commitment(", "commit")),
                () -> assertFalse(containsInvocation(
                        "." + (char) 0x00A0 + "commit(", "commit")),
                () -> assertFalse(containsInvocation(
                        ".commit" + (char) 0x2028 + "(", "commit")));
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

    private static Set<String> relativeSourcesInvoking(String methodName) throws Exception {
        return javaSources(MAIN_JAVA).stream()
                .filter(path -> containsInvocation(
                        withoutCommentsAndLiterals(read(path)), methodName))
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet());
    }

    private static boolean containsInvocation(String source, String methodName) {
        for (var index = 0; index < source.length(); index++) {
            if (source.charAt(index) != '.') {
                continue;
            }
            var cursor = skipAsciiRegexWhitespace(source, index + 1);
            if (!source.startsWith(methodName, cursor)) {
                continue;
            }
            cursor = skipAsciiRegexWhitespace(source, cursor + methodName.length());
            if (cursor < source.length() && source.charAt(cursor) == '(') {
                return true;
            }
        }
        return false;
    }

    private static int skipAsciiRegexWhitespace(String source, int start) {
        var cursor = start;
        while (cursor < source.length()
                && isAsciiRegexWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isAsciiRegexWhitespace(char character) {
        return character == ' '
                || character == '\t'
                || character == '\n'
                || character == '\u000B'
                || character == '\f'
                || character == '\r';
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
        var masked = new StringBuilder(source.length());
        var state = LexicalState.CODE;
        for (var index = 0; index < source.length(); index++) {
            var current = source.charAt(index);
            var hasNext = index + 1 < source.length();
            var next = hasNext ? source.charAt(index + 1) : '\0';
            switch (state) {
                case CODE -> {
                    if (current == '/' && next == '/') {
                        masked.append("  ");
                        index++;
                        state = LexicalState.LINE_COMMENT;
                    } else if (current == '/' && next == '*') {
                        masked.append("  ");
                        index++;
                        state = LexicalState.BLOCK_COMMENT;
                    } else if (isTextBlockOpeningDelimiterAt(source, index)) {
                        masked.append("   ");
                        index += 2;
                        state = LexicalState.TEXT_BLOCK;
                    } else if (current == '"') {
                        masked.append(' ');
                        state = LexicalState.STRING;
                    } else if (current == '\'') {
                        masked.append(' ');
                        state = LexicalState.CHARACTER;
                    } else {
                        masked.append(current);
                    }
                }
                case LINE_COMMENT -> {
                    appendMasked(masked, current);
                    if (current == '\r' || current == '\n') {
                        state = LexicalState.CODE;
                    }
                }
                case BLOCK_COMMENT -> {
                    if (current == '*' && next == '/') {
                        masked.append("  ");
                        index++;
                        state = LexicalState.CODE;
                    } else {
                        appendMasked(masked, current);
                    }
                }
                case STRING, CHARACTER -> {
                    appendMasked(masked, current);
                    if (current == '\\' && hasNext) {
                        appendMasked(masked, next);
                        index++;
                    } else if ((state == LexicalState.STRING && current == '"')
                            || (state == LexicalState.CHARACTER && current == '\'')) {
                        state = LexicalState.CODE;
                    }
                }
                case TEXT_BLOCK -> {
                    if (isTripleQuoteAt(source, index)) {
                        masked.append("   ");
                        index += 2;
                        state = LexicalState.CODE;
                    } else if (current == '\\' && hasNext) {
                        appendMasked(masked, current);
                        appendMasked(masked, next);
                        index++;
                        if (next == '\r'
                                && index + 1 < source.length()
                                && source.charAt(index + 1) == '\n') {
                            appendMasked(masked, '\n');
                            index++;
                        }
                    } else {
                        appendMasked(masked, current);
                    }
                }
            }
        }
        if (masked.length() != source.length()) {
            throw new AssertionError("lexical masker changed source length");
        }
        return masked.toString();
    }

    private static boolean isTextBlockOpeningDelimiterAt(String source, int index) {
        if (!isTripleQuoteAt(source, index)) {
            return false;
        }
        var cursor = index + 3;
        while (cursor < source.length()
                && isTextBlockOpeningWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor < source.length()
                && (source.charAt(cursor) == '\r' || source.charAt(cursor) == '\n');
    }

    private static boolean isTextBlockOpeningWhitespace(char character) {
        return character == ' ' || character == '\t' || character == '\f';
    }

    private static boolean isTripleQuoteAt(String source, int index) {
        return index + 2 < source.length()
                && source.charAt(index) == '"'
                && source.charAt(index + 1) == '"'
                && source.charAt(index + 2) == '"';
    }

    private static void appendMasked(StringBuilder masked, char character) {
        masked.append(character == '\r' || character == '\n' ? character : ' ');
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

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }
}
