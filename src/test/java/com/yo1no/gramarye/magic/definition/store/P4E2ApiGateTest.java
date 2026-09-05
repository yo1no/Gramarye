package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact type, composition, and phase-boundary Gate for engineering P4-E2. */
final class P4E2ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path TEST_STORE_ROOT = PROJECT_ROOT.resolve(
            "src/test/java/com/yo1no/gramarye/magic/definition/store");
    private static final Path STORE_ROOT = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store");
    private static final Path PLAYER_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java");
    private static final Path MANA_GAME_TEST_SOURCE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java");
    private static final Path QUALIFICATION_FACADE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/P4E2QualificationFacade.java");
    private static final Path QUALIFICATION_TEST_ACCESS = PROJECT_ROOT.resolve(
            "src/p4C2GameTest/java/com/yo1no/gramarye/"
                    + "P4E2QualificationFacadeTestAccess.java");
    private static final Path RECOVERY_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/submission/"
                    + "SkillSubmissionRecoveryService.java");

    @Test
    void exactE2ProductionAndEvidenceInventoryExistsWithoutPrefixExpansion() throws Exception {
        assertEquals(P4E2PhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES,
                P4E2PhaseTypes.NEW_STORE_SOURCE_FILE_NAMES.stream()
                        .map(name -> name.substring(0, name.length() - ".java".length()))
                        .collect(Collectors.toUnmodifiableSet()));
        for (var fileName : P4E2PhaseTypes.NEW_STORE_SOURCE_FILE_NAMES) {
            var source = STORE_ROOT.resolve(fileName);
            assertTrue(Files.isRegularFile(source), fileName);
            assertFalse(Files.isSymbolicLink(source), fileName);
        }
        for (var fileName : P4E2PhaseTypes.REQUIRED_TEST_SOURCE_FILE_NAMES) {
            var source = TEST_STORE_ROOT.resolve(fileName);
            if (fileName.equals("P4E2AtomicReconciliationTest.java")) {
                source = PROJECT_ROOT.resolve(
                        "src/test/java/com/yo1no/gramarye/magic/definition/player")
                        .resolve(fileName);
            }
            assertTrue(Files.isRegularFile(source), fileName);
            assertFalse(Files.isSymbolicLink(source), fileName);
        }
        var verifier = PROJECT_ROOT.resolve("scripts/verify-p4-e2-configuration.sh");
        assertAll(
                () -> assertTrue(Files.isRegularFile(verifier)),
                () -> assertFalse(Files.isSymbolicLink(verifier)),
                () -> assertTrue(Files.isExecutable(verifier)));
    }

    @Test
    void directObservationProductionAllowlistIsExactAndConcrete() {
        assertEquals(8, P4E2PhaseTypes.DIRECT_OBSERVATION_PRODUCTION_SOURCE_PATHS.size());
        assertEquals(
                Set.of(
                        "com/yo1no/gramarye/Gramarye.java",
                        "com/yo1no/gramarye/P4E2QualificationFacade.java",
                        "com/yo1no/gramarye/magic/definition/player/"
                                + "PlayerSkillAttachmentService.java",
                        "com/yo1no/gramarye/magic/definition/store/"
                                + "P4E2BoundPlayerSkillAttachmentReconciliationCapability.java",
                        "com/yo1no/gramarye/magic/definition/store/"
                                + "P4E2OnlineReconciliationCoordinator.java",
                        "com/yo1no/gramarye/magic/definition/store/"
                                + "PlayerSkillAttachmentReconciliationCapability.java",
                        "com/yo1no/gramarye/magic/definition/store/"
                                + "SkillDefinitionStoreService.java",
                        "com/yo1no/gramarye/magic/definition/submission/"
                                + "SkillSubmissionRecoveryService.java"),
                P4E2PhaseTypes.DIRECT_OBSERVATION_PRODUCTION_SOURCE_PATHS);
        for (var path : P4E2PhaseTypes.DIRECT_OBSERVATION_PRODUCTION_SOURCE_PATHS) {
            var source = MAIN_JAVA.resolve(path);
            assertTrue(Files.isRegularFile(source), path);
            assertFalse(Files.isSymbolicLink(source), path);
        }
        assertTrue(Files.isRegularFile(QUALIFICATION_FACADE));
    }

    @Test
    void qualificationSessionAndHandleAllocateBeforeTheActiveCommit() throws Exception {
        var facade = Files.readString(QUALIFICATION_FACADE);
        var stateValidation = facade.indexOf("if (state != State.IDLE)");
        var serverValidation = facade.indexOf(
                "Objects.requireNonNull(exactServer, \"exactServer\")");
        var phaseValidation = facade.indexOf(
                "Objects.requireNonNull(expectedPhase, \"expectedPhase\")");
        var tokenValidation = facade.indexOf("Math.incrementExact(nextToken)");
        var threadCapture = facade.indexOf(
                "var validatedLogicThread = Thread.currentThread();");
        var facadeAllocation = facade.indexOf("var session = new Session(this, token);");
        var commitTokens = List.of(
                "server = validatedServer;",
                "logicThread = validatedLogicThread;",
                "playerMost = expectedPlayerMost;",
                "playerLeast = expectedPlayerLeast;",
                "caseId = boundedCaseId;",
                "phase = validatedPhase;",
                "nextToken = token;",
                "activeToken = token;");
        var facadeCommit = facade.indexOf(commitTokens.getFirst());
        var armedCommit = facade.indexOf("state = State.ARMED;");
        var facadeReturn = facade.indexOf("return session;");
        assertAll(
                () -> assertTrue(List.of(
                                stateValidation, serverValidation, phaseValidation,
                                tokenValidation, threadCapture, facadeAllocation,
                                facadeCommit, armedCommit, facadeReturn)
                        .stream().allMatch(index -> index >= 0)),
                () -> assertEquals(1, occurrences(facade,
                        "var session = new Session(this, token);")),
                () -> assertTrue(stateValidation < serverValidation),
                () -> assertTrue(serverValidation < phaseValidation),
                () -> assertTrue(phaseValidation < tokenValidation),
                () -> assertTrue(tokenValidation < threadCapture),
                () -> assertTrue(threadCapture < facadeAllocation),
                () -> assertTrue(facadeAllocation < facadeCommit),
                () -> assertTrue(commitTokens.stream().allMatch(token ->
                        facadeAllocation < facade.indexOf(token)
                                && facade.indexOf(token) < armedCommit)),
                () -> assertTrue(facadeCommit < armedCommit),
                () -> assertTrue(armedCommit < facadeReturn),
                () -> assertFalse(facade.substring(facadeCommit, facadeReturn)
                        .contains("new ")),
                () -> assertFalse(facade.contains("return new Session(")),
                () -> assertEquals(1, occurrences(facade, "state = State.ARMED;")));

        var adapter = Files.readString(QUALIFICATION_TEST_ACCESS);
        var handleAllocation = adapter.indexOf("var handle = new Handle(");
        var preArmInitialization = adapter.indexOf("handle.session = null;");
        var armCall = adapter.indexOf("var session = facade.arm(");
        var bind = adapter.indexOf("handle.session = session;");
        var handleReturn = adapter.indexOf("return handle;");
        assertAll(
                () -> assertTrue(List.of(
                                handleAllocation, preArmInitialization, armCall,
                                bind, handleReturn)
                        .stream().allMatch(index -> index >= 0)),
                () -> assertEquals(1, occurrences(adapter, "var handle = new Handle(")),
                () -> assertTrue(handleAllocation < preArmInitialization),
                () -> assertTrue(preArmInitialization < armCall),
                () -> assertTrue(armCall < bind),
                () -> assertTrue(bind < handleReturn),
                () -> assertFalse(adapter.substring(armCall, handleReturn)
                        .contains("new ")),
                () -> assertEquals(1, occurrences(adapter, "handle.session = null;")),
                () -> assertEquals(1, occurrences(adapter, "handle.session = session;")),
                () -> assertEquals(1, occurrences(adapter,
                        "private P4E2QualificationFacade.Session session;")),
                () -> assertFalse(adapter.contains("void bind(")),
                () -> assertEquals(
                        "handle.session = session;\n        return handle;",
                        adapter.substring(bind, handleReturn + "return handle;".length())));
    }

    @Test
    void exactE2TopLevelsHaveOnlyTheTwoReviewedPublicNominals() throws Exception {
        for (var simpleName : P4E2PhaseTypes.NEW_STORE_TOP_LEVEL_TYPE_NAMES) {
            var type = Class.forName(P4E2PhaseTypes.STORE_PACKAGE + simpleName);
            assertEquals(
                    P4E2PhaseTypes.PUBLIC_STORE_TOP_LEVEL_TYPE_NAMES.contains(simpleName),
                    Modifier.isPublic(type.getModifiers()),
                    simpleName);
            assertFalse(Modifier.isProtected(type.getModifiers()), simpleName);
        }

        var dependency = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "P4E2OnlineReconciliationDependency");
        var capability = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "PlayerSkillAttachmentReconciliationCapability");
        assertAll(
                () -> assertTrue(dependency.isSealed()),
                () -> assertTrue(capability.isSealed()),
                () -> assertTrue(Arrays.stream(dependency.getDeclaredConstructors())
                        .noneMatch(P4E2ApiGateTest::isPublicOrProtected)),
                () -> assertTrue(Arrays.stream(capability.getDeclaredConstructors())
                        .noneMatch(P4E2ApiGateTest::isPublicOrProtected)),
                () -> assertEquals(
                        Set.of("P4E2OnlineReconciliationCoordinator"),
                        permittedSimpleNames(dependency)),
                () -> assertEquals(
                        Set.of("P4E2BoundPlayerSkillAttachmentReconciliationCapability"),
                        permittedSimpleNames(capability)));
    }

    @Test
    void internalResultAndInvalidationTaxonomiesAreBoundedAndNonPublic() throws Exception {
        var result = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "P4E2ReconciliationResult");
        var audit = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "SkillRetentionRootAuditService");
        var invalidation = nested(audit, "InvalidationResult");

        assertAll(
                () -> assertTrue(result.isSealed()),
                () -> assertFalse(Modifier.isPublic(result.getModifiers())),
                () -> assertFalse(Modifier.isProtected(result.getModifiers())),
                () -> assertEquals(P4E2PhaseTypes.RESULT_VARIANT_NAMES,
                        permittedSimpleNames(result)),
                () -> assertFalse(Modifier.isPublic(invalidation.getModifiers())),
                () -> assertFalse(Modifier.isProtected(invalidation.getModifiers())),
                () -> assertEquals(
                        Set.of("Accepted", "GenerationExhausted"),
                        permittedSimpleNames(invalidation)));

        var accepted = nested(invalidation, "Accepted");
        var exhausted = nested(invalidation, "GenerationExhausted");
        assertAll(
                () -> assertTrue(accepted.isRecord()),
                () -> assertEquals(List.of("generation"),
                        Arrays.stream(accepted.getRecordComponents())
                                .map(component -> component.getName())
                                .toList()),
                () -> assertEquals(long.class,
                        accepted.getRecordComponents()[0].getType()),
                () -> assertTrue(exhausted.isEnum()),
                () -> assertEquals(Set.of("INSTANCE"),
                        Arrays.stream(exhausted.getEnumConstants())
                                .map(Object::toString)
                                .collect(Collectors.toSet())));

        for (var type : result.getPermittedSubclasses()) {
            assertEquals(result, type.getEnclosingClass(), type.getSimpleName());
            assertBoundedFields(type);
        }
    }

    @Test
    void recoveryContinuationIsPublicOpaqueAndPrivatelyConstructed() throws Exception {
        var recovery = Class.forName(P4E2PhaseTypes.SUBMISSION_PACKAGE
                + "SkillSubmissionRecoveryService");
        var continuation = nested(recovery, "RecoveryContinuation");
        assertAll(
                () -> assertTrue(Modifier.isPublic(continuation.getModifiers())),
                () -> assertTrue(Modifier.isStatic(continuation.getModifiers())),
                () -> assertTrue(Modifier.isFinal(continuation.getModifiers())),
                () -> assertTrue(Arrays.stream(continuation.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(
                                constructor.getModifiers()))),
                () -> assertTrue(Arrays.stream(continuation.getDeclaredFields())
                        .noneMatch(field -> Modifier.isPublic(field.getModifiers())
                                || Modifier.isProtected(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(continuation.getDeclaredMethods())
                        .noneMatch(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))));
    }

    @Test
    void exactPublicNominalAndPlayerCrossingSignaturesAreNarrow() throws Exception {
        var dependency = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "P4E2OnlineReconciliationDependency");
        var recoveryKind = nested(dependency, "RecoveryKind");
        var serverPlayer = Class.forName("net.minecraft.server.level.ServerPlayer");
        var recovery = Class.forName(P4E2PhaseTypes.SUBMISSION_PACKAGE
                + "SkillSubmissionRecoveryService");
        var recoveryContinuation = nested(recovery, "RecoveryContinuation");
        var continueAfterRecovery = dependency.getDeclaredMethod(
                "reconcileAfterRecovery",
                serverPlayer,
                recoveryContinuation,
                recoveryKind,
                int.class,
                int.class,
                Optional.class);
        var coordinator = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "P4E2OnlineReconciliationCoordinator");
        var recoveryStatus = nested(coordinator, "RecoveryStatus");
        var result = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "P4E2ReconciliationResult");
        var reconcile = coordinator.getDeclaredMethod(
                "reconcile",
                serverPlayer,
                recoveryContinuation,
                recoveryKind,
                int.class,
                int.class,
                Optional.class);
        var playerService = Class.forName(P4E2PhaseTypes.PLAYER_PACKAGE
                + "PlayerSkillAttachmentService");
        var submissionPort = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "SkillDefinitionStoreSubmissionPort");
        var create = recovery.getDeclaredMethod(
                "create", playerService, submissionPort, dependency);
        var facade = Class.forName(P4E2PhaseTypes.ROOT_PACKAGE
                + "P4E2QualificationFacade");
        var storeView = nested(facade, "StoreView");
        var playerView = nested(facade, "PlayerView");
        var storeService = Class.forName(P4E2PhaseTypes.STORE_PACKAGE
                + "SkillDefinitionStoreService");
        var gameBus = Class.forName("net.neoforged.bus.api.IEventBus");
        var loginReadyPort = Class.forName(
                "com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary$LoginReadyPort");
        var observedRegisterOn = storeService.getDeclaredMethod(
                "registerOn", gameBus, playerService, loginReadyPort, storeView, playerView);
        var unobservedRegisterOn = storeService.getDeclaredMethod(
                "registerOn", gameBus, playerService, loginReadyPort);
        assertThrows(NoSuchMethodException.class, () -> storeService.getDeclaredMethod(
                "registerOn", gameBus, playerService, storeView, playerView));
        assertThrows(NoSuchMethodException.class, () -> storeService.getDeclaredMethod(
                "registerOn", gameBus, playerService));
        assertAll(
                () -> assertTrue(Modifier.isPublic(continueAfterRecovery.getModifiers())),
                () -> assertEquals(void.class, continueAfterRecovery.getReturnType()),
                () -> assertEquals(
                        List.of(
                                serverPlayer,
                                recoveryContinuation,
                                recoveryKind,
                                int.class,
                                int.class,
                                Optional.class),
                        Arrays.asList(continueAfterRecovery.getParameterTypes())),
                () -> assertEquals(
                        "java.util.Optional<java.lang.String>",
                        continueAfterRecovery.getGenericParameterTypes()[5].getTypeName()),
                () -> assertFalse(Modifier.isPublic(reconcile.getModifiers())
                        || Modifier.isProtected(reconcile.getModifiers())),
                () -> assertEquals(result, reconcile.getReturnType()),
                () -> assertEquals(
                        "java.util.Optional<java.lang.String>",
                        reconcile.getGenericParameterTypes()[5].getTypeName()),
                () -> assertTrue(recoveryStatus.isRecord()),
                () -> assertTrue(Modifier.isPrivate(recoveryStatus.getModifiers())),
                () -> assertTrue(Modifier.isStatic(recoveryStatus.getModifiers())),
                () -> assertEquals(
                        List.of("kind", "entriesCleared", "stepsReplayed", "exceptionClass"),
                        Arrays.stream(recoveryStatus.getRecordComponents())
                                .map(component -> component.getName())
                                .toList()),
                () -> assertEquals(
                        List.of(recoveryKind, int.class, int.class, Optional.class),
                        Arrays.stream(recoveryStatus.getRecordComponents())
                                .map(component -> component.getType())
                                .toList()),
                () -> assertEquals(
                        "java.util.Optional<java.lang.String>",
                        recoveryStatus.getRecordComponents()[3]
                                .getGenericType()
                                .getTypeName()),
                () -> assertEquals(Set.of("reconcileAfterRecovery"),
                        Arrays.stream(dependency.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers())
                                        || Modifier.isProtected(method.getModifiers()))
                                .map(method -> method.getName())
                                .collect(Collectors.toSet())),
                () -> assertEquals(1L,
                        Arrays.stream(dependency.getDeclaredMethods())
                                .filter(method -> Modifier.isPublic(method.getModifiers())
                                        || Modifier.isProtected(method.getModifiers()))
                                .count()),
                () -> assertEquals(1L,
                        Arrays.stream(coordinator.getDeclaredMethods())
                                .filter(method -> method.getName().equals("reconcile"))
                                .count()),
                () -> assertEquals(
                        P4E2PhaseTypes.DEPENDENCY_PUBLIC_NESTED_TYPE_NAMES,
                        Arrays.stream(dependency.getDeclaredClasses())
                                .filter(type -> Modifier.isPublic(type.getModifiers())
                                        || Modifier.isProtected(type.getModifiers()))
                                .map(Class::getSimpleName)
                                .collect(Collectors.toSet())),
                () -> assertTrue(recoveryKind.isEnum()),
                () -> assertTrue(Modifier.isPublic(create.getModifiers())
                        && Modifier.isStatic(create.getModifiers())),
                () -> assertEquals(recovery, create.getReturnType()),
                () -> assertTrue(Modifier.isPublic(observedRegisterOn.getModifiers())
                        && Modifier.isStatic(observedRegisterOn.getModifiers())),
                () -> assertEquals(storeService, observedRegisterOn.getReturnType()),
                () -> assertTrue(Modifier.isPublic(unobservedRegisterOn.getModifiers())
                        && Modifier.isStatic(unobservedRegisterOn.getModifiers())),
                () -> assertEquals(2L, Arrays.stream(storeService.getDeclaredMethods())
                        .filter(method -> method.getName().equals("registerOn"))
                        .count()));

        var recoveryOutcome = nested(recovery, "RecoveryOutcome");
        var outcomeMethods = Arrays.stream(recoveryOutcome.getDeclaredMethods())
                .collect(Collectors.toMap(method -> method.getName(), method -> method));
        assertAll(
                () -> assertTrue(recoveryOutcome.isSealed()),
                () -> assertEquals(
                        Set.of(
                                "e2Kind",
                                "e2EntriesCleared",
                                "e2StepsReplayed",
                                "e2ExceptionClass"),
                        outcomeMethods.keySet()),
                () -> assertTrue(outcomeMethods.values().stream()
                        .allMatch(method -> method.getParameterCount() == 0)),
                () -> assertEquals(recoveryKind,
                        outcomeMethods.get("e2Kind").getReturnType()),
                () -> assertEquals(int.class,
                        outcomeMethods.get("e2EntriesCleared").getReturnType()),
                () -> assertEquals(int.class,
                        outcomeMethods.get("e2StepsReplayed").getReturnType()),
                () -> assertEquals(Optional.class,
                        outcomeMethods.get("e2ExceptionClass").getReturnType()),
                () -> assertEquals(
                        "java.util.Optional<java.lang.String>",
                        outcomeMethods.get("e2ExceptionClass")
                                .getGenericReturnType()
                                .getTypeName()));

        var e2Methods = Arrays.stream(playerService.getDeclaredMethods())
                .filter(method -> P4E2PhaseTypes.PLAYER_SERVICE_E2_PUBLIC_METHOD_NAMES
                        .contains(method.getName()))
                .toList();
        assertEquals(P4E2PhaseTypes.PLAYER_SERVICE_E2_PUBLIC_METHOD_NAMES,
                e2Methods.stream().map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(P4E2PhaseTypes.PLAYER_SERVICE_E2_PUBLIC_METHOD_NAMES.size(),
                e2Methods.size());
        assertTrue(e2Methods.stream().allMatch(method ->
                Modifier.isPublic(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())));

        var prepare = e2Methods.stream()
                .filter(method -> method.getName().equals("prepareOnlineReconciliation"))
                .findFirst()
                .orElseThrow();
        assertTrue(Arrays.stream(prepare.getGenericParameterTypes())
                .anyMatch(P4E2ApiGateTest::isExactWildcardCapability));
        for (var method : e2Methods) {
            assertNoRawPublicPersistenceType(method.getGenericReturnType().getTypeName());
            for (var parameter : method.getGenericParameterTypes()) {
                assertNoRawPublicPersistenceType(parameter.getTypeName());
            }
        }

        var nestedNames = Arrays.stream(playerService.getDeclaredClasses())
                .filter(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers()))
                .map(Class::getSimpleName)
                .filter(P4E2PhaseTypes.PLAYER_SERVICE_E2_PUBLIC_NESTED_TYPE_NAMES::contains)
                .collect(Collectors.toSet());
        assertEquals(P4E2PhaseTypes.PLAYER_SERVICE_E2_PUBLIC_NESTED_TYPE_NAMES, nestedNames);
    }

    @Test
    void compositionOwnersAndGlobalSideEffectOwnersRemainExact() throws Exception {
        var production = com.yo1no.gramarye.P7GameTestInventory.productionSource();
        var storeService = STORE_ROOT.resolve("SkillDefinitionStoreService.java");
        assertAll(
                () -> assertEquals(1, occurrences(
                        production, "new SkillRetentionRootAuditService(")),
                () -> assertEquals(
                        Set.of(relative(storeService)),
                        sourcePathsContaining("new SkillRetentionRootAuditService(")),
                () -> assertEquals(1, occurrences(
                        production, "PlayerEvent.PlayerLoggedInEvent")),
                () -> assertEquals(
                        Set.of(relative(RECOVERY_SERVICE)),
                        sourcePathsContaining("PlayerEvent.PlayerLoggedInEvent")),
                () -> assertEquals(2, occurrences(production, ".setData(")),
                () -> assertEquals(
                        Set.of(
                                "com/yo1no/gramarye/magic/definition/player/"
                                        + "PlayerSkillAttachmentService.java",
                                "com/yo1no/gramarye/magic/runtime/mana/"
                                        + "ManaAttachments.java"),
                        sourcePathsContaining(".setData(")),
                () -> assertEquals(1, occurrences(
                        Files.readString(PLAYER_SERVICE),
                        ".recordE2SetDataAttempt(")),
                () -> assertEquals(1, occurrences(
                        Files.readString(PLAYER_SERVICE),
                        ".recordE2SetDataSuccess(")),
                () -> assertEquals(1, occurrences(
                        production, "SkillRetentionRootSnapshot.fromCompleteRoots")),
                () -> assertEquals(
                        Set.of(relative(storeService)),
                        sourcePathsContaining(
                                "SkillRetentionRootSnapshot.fromCompleteRoots")),
                () -> assertEquals(
                        Set.of(
                                "com/yo1no/gramarye/magic/definition/store/"
                                        + "GramaryeSkillSavedData.java",
                                "com/yo1no/gramarye/magic/definition/store/"
                                        + "SkillDefinitionStoreService.java"),
                        sourcePathsContaining(".reclaim(")));
    }

    @Test
    void e2SourcesDoNotEscapeIntoLaterPhasesOrForbiddenAuthority() throws Exception {
        var sources = new StringBuilder();
        for (var fileName : P4E2PhaseTypes.NEW_STORE_SOURCE_FILE_NAMES) {
            sources.append(Files.readString(STORE_ROOT.resolve(fileName))).append('\n');
        }
        for (var token : P4E2PhaseTypes.FORBIDDEN_E2_SOURCE_TOKENS) {
            assertFalse(sources.toString().contains(token), token);
        }
        for (var token : List.of(
                "P4E3",
                "SkillRetentionRootSnapshot",
                "PendingSkillSubmissionJournal",
                "java.nio.file",
                "CompoundTag",
                "NbtIo.")) {
            assertFalse(sources.toString().contains(token), token);
        }
    }

    @Test
    void buildWorkflowSourceSetsAndNormalGameTestCountRemainUnchanged() throws Exception {
        var build = Files.readString(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = Files.readString(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var visibilityProbe = Files.readString(
                TEST_STORE_ROOT.resolve("P4E2VisibilityCompileTest.java"));
        var production = javaSources(MAIN_JAVA);
        var totalGameTestCount = occurrences(production, "@GameTest(");
        var manaGameTestCount = occurrences(
                Files.readString(MANA_GAME_TEST_SOURCE), "@GameTest(");
        assertAll(
                () -> assertFalse(build.contains("p4E2")),
                () -> assertFalse(build.contains("P4E2")),
                () -> assertFalse(workflow.contains("p4-e2")),
                () -> assertFalse(workflow.contains("P4-E2")),
                () -> assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4E2Probe"))),
                () -> assertFalse(Files.exists(PROJECT_ROOT.resolve("src/p4E2GameTest"))),
                () -> assertEquals(1, occurrences(visibilityProbe, "\"-proc:none\"")),
                () -> assertTrue(visibilityProbe.indexOf("\"-proc:none\"")
                        < visibilityProbe.indexOf("compiler.getTask(")),
                () -> assertEquals(12, totalGameTestCount - manaGameTestCount - com.yo1no.gramarye.P7GameTestInventory.s4Count()),
                () -> assertEquals(7, manaGameTestCount),
                () -> assertEquals(com.yo1no.gramarye.P7GameTestInventory.totalCount(), totalGameTestCount));
    }

    private static void assertBoundedFields(Class<?> type) {
        for (var field : type.getDeclaredFields()) {
            var name = field.getGenericType().getTypeName();
            for (var forbidden : List.of(
                    "Throwable", "Tag", "Path", "SkillDefinitionStore",
                    "History", "Ready", "Capability", "Iterable", "Collection", "List")) {
                assertFalse(name.contains(forbidden), type.getSimpleName() + ":" + name);
            }
        }
    }

    private static Class<?> nested(Class<?> owner, String simpleName) {
        return Arrays.stream(owner.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        owner.getName() + " is missing nested " + simpleName));
    }

    private static Set<String> permittedSimpleNames(Class<?> type) {
        return Arrays.stream(type.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isPublicOrProtected(java.lang.reflect.Constructor<?> constructor) {
        return Modifier.isPublic(constructor.getModifiers())
                || Modifier.isProtected(constructor.getModifiers());
    }

    private static boolean isExactWildcardCapability(java.lang.reflect.Type type) {
        if (!(type instanceof ParameterizedType parameterized)
                || !parameterized.getRawType().getTypeName().equals(
                        P4E2PhaseTypes.STORE_PACKAGE
                                + "PlayerSkillAttachmentReconciliationCapability")) {
            return false;
        }
        return Arrays.stream(parameterized.getActualTypeArguments())
                .allMatch(argument -> argument instanceof WildcardType);
    }

    private static void assertNoRawPublicPersistenceType(String typeName) {
        for (var forbidden : List.of(
                "net.minecraft.nbt",
                "java.nio.file",
                "SkillDefinitionStore",
                "P4E1StoreHistoryObservation",
                "Ready",
                "Iterable<",
                "Collection<",
                "List<")) {
            assertFalse(typeName.contains(forbidden), typeName);
        }
    }

    private static Set<String> sourcePathsContaining(String token) throws Exception {
        try (var stream = Files.walk(MAIN_JAVA)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !com.yo1no.gramarye.P7GameTestInventory.isS4Harness(path))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains(token);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    })
                    .map(P4E2ApiGateTest::relative)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private static String relative(Path path) {
        return MAIN_JAVA.relativize(path).toString().replace('\\', '/');
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
