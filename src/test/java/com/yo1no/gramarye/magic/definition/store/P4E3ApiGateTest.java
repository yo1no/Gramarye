package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.P4E2QualificationFacade;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;

/** Closed public surface, owner, callsite, listener, and isolation Gate for P4-E3. */
final class P4E3ApiGateTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path STORE_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java");
    private static final Path ROOT_AUDIT_SERVICE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java");
    private static final Path HANDOFF = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java");
    private static final Path FACADE = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/P4E2QualificationFacade.java");
    private static final Path TEST_ACCESS = PROJECT_ROOT.resolve(
            "src/p4E3GameTest/java/com/yo1no/gramarye/"
                    + "P4E3StartupObservationTestAccess.java");

    private static final Set<String> EXACT_PRODUCTION_PATHS = Set.of(
            "src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java",
            "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E1CompleteRootHandoff.java",
            "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                    + "SkillDefinitionStoreService.java",
            "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                    + "SkillRetentionRootAuditService.java");
    private static final Set<String> EXACT_EXISTING_TEST_PATHS = Set.of(
            "src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java",
            "src/test/java/com/yo1no/gramarye/"
                    + "P4E2QualificationFacadeVisibilityCompileTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E1B2BCompleteHandoffTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E1BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E1B2BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E2ApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4B2BApiGateTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E2LifecycleOrderingTest.java");
    private static final Set<String> EXACT_NEW_TEST_PATHS = Set.of(
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3StartupLifecycleTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3LeaseTerminalTest.java",
            "src/test/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3ApiGateTest.java");
    private static final Set<String> EXACT_PROBE_PATHS = Set.of(
            "src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3FixtureBuilder.java",
            "src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3FixtureManifest.java",
            "src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3ProbeMain.java",
            "src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3FileVerifier.java",
            "src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/"
                    + "P4E3PlayerDataFixture.java");
    private static final Set<String> EXACT_GAME_TEST_PATHS = Set.of(
            "src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/"
                    + "P4E3StartupMemoryGameTests.java",
            "src/p4E3GameTest/java/com/yo1no/gramarye/"
                    + "P4E3StartupObservationTestAccess.java");
    private static final Set<String> EXACT_MODIFIED_EXISTING_VERIFIER_PATHS = Set.of(
            "scripts/verify-p4-b2-b-configuration.sh",
            "scripts/verify-p4-e0-r-configuration.sh",
            "scripts/verify-p4-e0-r2q-configuration.sh",
            "scripts/verify-p4-e1-configuration.sh",
            "scripts/verify-p4-e2-configuration.sh");
    private static final Set<String> EXACT_NEW_VERIFIER_PATHS = Set.of(
            "scripts/verify-p4-e3-configuration.sh");
    private static final Set<String> EXACT_PORTABLE_VERIFIER_PATHS = Set.of(
            "scripts/verify-p4-b2-b-configuration.sh",
            "scripts/verify-p4-c2-a-configuration.sh",
            "scripts/verify-p4-c2-b-configuration.sh",
            "scripts/verify-p4-d1-configuration.sh",
            "scripts/verify-p4-d2-configuration.sh",
            "scripts/verify-p4-d3-a-configuration.sh",
            "scripts/verify-p4-d3-configuration.sh",
            "scripts/verify-p4-e0-r-configuration.sh",
            "scripts/verify-p4-e0-r2q-configuration.sh",
            "scripts/verify-p4-e1-configuration.sh",
            "scripts/verify-p4-e2-configuration.sh",
            "scripts/verify-p4-e3-configuration.sh");

    @Test
    void exactThirtyPathInventoryExistsWithoutPrefixExpansion() throws Exception {
        var exactPaths = new java.util.HashSet<String>();
        exactPaths.addAll(EXACT_PRODUCTION_PATHS);
        exactPaths.addAll(EXACT_EXISTING_TEST_PATHS);
        exactPaths.addAll(EXACT_NEW_TEST_PATHS);
        exactPaths.addAll(EXACT_PROBE_PATHS);
        exactPaths.addAll(EXACT_GAME_TEST_PATHS);
        exactPaths.addAll(EXACT_MODIFIED_EXISTING_VERIFIER_PATHS);
        exactPaths.addAll(EXACT_NEW_VERIFIER_PATHS);
        exactPaths.add("build.gradle");
        exactPaths.add(".github/workflows/build.yml");

        assertEquals(8, EXACT_EXISTING_TEST_PATHS.size());
        assertEquals(5, EXACT_MODIFIED_EXISTING_VERIFIER_PATHS.size());
        assertEquals(1, EXACT_NEW_VERIFIER_PATHS.size());
        assertEquals(30, exactPaths.size());
        for (var relative : exactPaths) {
            var path = PROJECT_ROOT.resolve(relative);
            assertTrue(Files.isRegularFile(path), relative);
            assertFalse(Files.isSymbolicLink(path), relative);
        }
        assertEquals(12, EXACT_PORTABLE_VERIFIER_PATHS.size());
        assertEquals(36, EXACT_PORTABLE_VERIFIER_PATHS.size() * 3);
        for (var relative : EXACT_PORTABLE_VERIFIER_PATHS) {
            var path = PROJECT_ROOT.resolve(relative);
            assertTrue(Files.isRegularFile(path), relative);
            assertFalse(Files.isSymbolicLink(path), relative);
            assertTrue(Files.isExecutable(path), relative);
        }
        assertEquals(EXACT_PROBE_PATHS, relativeJavaSources("src/p4E3Probe/java"));
        assertEquals(EXACT_GAME_TEST_PATHS, relativeJavaSources("src/p4E3GameTest/java"));
        assertEquals(EXACT_NEW_TEST_PATHS, javaSources(PROJECT_ROOT.resolve(
                        "src/test/java/com/yo1no/gramarye/magic/definition/store"))
                .stream()
                .map(PROJECT_ROOT::relativize)
                .map(Path::toString)
                .filter(path -> Path.of(path).getFileName().toString().startsWith("P4E3"))
                .collect(Collectors.toSet()));
    }

    @Test
    void nestedFacadeSurfaceIsExactSealedAndBounded() throws Exception {
        var facade = P4E2QualificationFacade.class;
        var view = P4E2QualificationFacade.E3StartupView.class;
        var implementation = nested(facade, "E3StartupViewImpl");
        var publicE3Types = Arrays.stream(facade.getDeclaredClasses())
                .filter(type -> type.getSimpleName().startsWith("E3"))
                .filter(type -> Modifier.isPublic(type.getModifiers()))
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(Set.of(
                                P4E2QualificationFacade.E3StartupView.class,
                                P4E2QualificationFacade.E3AuditVariant.class,
                                P4E2QualificationFacade.E3SnapshotVariant.class,
                                P4E2QualificationFacade.E3ReclaimVariant.class,
                                P4E2QualificationFacade.E3IndexTerminal.class),
                        publicE3Types),
                () -> assertTrue(view.isSealed()),
                () -> assertTrue(Modifier.isPublic(view.getModifiers())),
                () -> assertTrue(Modifier.isStatic(view.getModifiers())),
                () -> assertTrue(Modifier.isAbstract(view.getModifiers())),
                () -> assertEquals(Set.of(implementation),
                        Set.of(view.getPermittedSubclasses())),
                () -> assertTrue(Modifier.isPrivate(implementation.getModifiers())),
                () -> assertTrue(Modifier.isStatic(implementation.getModifiers())),
                () -> assertTrue(Modifier.isFinal(implementation.getModifiers())),
                () -> assertEquals(view, implementation.getSuperclass()),
                () -> assertTrue(Arrays.stream(view.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())
                                && Arrays.equals(
                                        constructor.getParameterTypes(),
                                        new Class<?>[] {facade}))),
                () -> assertTrue(Arrays.stream(implementation.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())
                                && Arrays.equals(
                                        constructor.getParameterTypes(),
                                        new Class<?>[] {facade}))));
    }

    @Test
    void fourEnumFamiliesContainExactlyFifteenReviewedValues() {
        assertAll(
                () -> assertEquals(Set.of(
                                "COMPLETE", "INCOMPLETE", "OVER_LIMIT",
                                "RECONCILIATION_REQUIRED", "GENERATION_EXHAUSTED"),
                        enumNames(P4E2QualificationFacade.E3AuditVariant.class)),
                () -> assertEquals(Set.of(
                                "COMPLETE", "INCOMPLETE", "TRUNCATED", "OVER_LIMIT"),
                        enumNames(P4E2QualificationFacade.E3SnapshotVariant.class)),
                () -> assertEquals(Set.of(
                                "COMPLETED_ZERO", "COMPLETED_POSITIVE", "REJECTED",
                                "UNAVAILABLE"),
                        enumNames(P4E2QualificationFacade.E3ReclaimVariant.class)),
                () -> assertEquals(Set.of("COMPLETE_INDEX", "INCOMPLETE"),
                        enumNames(P4E2QualificationFacade.E3IndexTerminal.class)),
                () -> assertEquals(15,
                        P4E2QualificationFacade.E3AuditVariant.values().length
                                + P4E2QualificationFacade.E3SnapshotVariant.values().length
                                + P4E2QualificationFacade.E3ReclaimVariant.values().length
                                + P4E2QualificationFacade.E3IndexTerminal.values().length));
    }

    @Test
    void thirteenPublicFinalRecordingOperationsHaveExactDescriptors() {
        var server = MinecraftServer.class;
        var expected = new LinkedHashMap<String, List<Class<?>>>(Map.ofEntries(
                Map.entry("beginRecording", List.of(server)),
                Map.entry("recordAuditInvocation", List.of(server)),
                Map.entry("recordAuditResult", List.of(
                        server, P4E2QualificationFacade.E3AuditVariant.class, long.class)),
                Map.entry("recordCompleteConsumeInvocation", List.of(server)),
                Map.entry("recordSnapshotInvocation", List.of(server)),
                Map.entry("recordSnapshotResult", List.of(
                        server, P4E2QualificationFacade.E3SnapshotVariant.class, int.class)),
                Map.entry("recordReclaimInvocation", List.of(server, boolean.class)),
                Map.entry("recordReclaimResult", List.of(
                        server, P4E2QualificationFacade.E3ReclaimVariant.class,
                        int.class, int.class, int.class, int.class)),
                Map.entry("recordDirtyAfter", List.of(server, boolean.class)),
                Map.entry("recordIndexTerminal", List.of(
                        server, P4E2QualificationFacade.E3IndexTerminal.class, long.class)),
                Map.entry("completeRecording", List.of(server)),
                Map.entry("abortRecording", List.of(server)),
                Map.entry("clearOnServerStopped", List.of(server))));
        var declaredPublic = Arrays.stream(
                        P4E2QualificationFacade.E3StartupView.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isBridge() && !method.isSynthetic())
                .toList();

        assertEquals(13, declaredPublic.size());
        assertEquals(expected.keySet(), declaredPublic.stream()
                .map(Method::getName)
                .collect(Collectors.toSet()));
        for (var method : declaredPublic) {
            assertTrue(Modifier.isFinal(method.getModifiers()), method.toString());
            assertFalse(Modifier.isStatic(method.getModifiers()), method.toString());
            assertEquals(expected.get(method.getName()),
                    List.of(method.getParameterTypes()), method.toString());
            assertEquals(method.getName().equals("beginRecording")
                            ? boolean.class : void.class,
                    method.getReturnType(), method.toString());
            assertEquals(0, method.getExceptionTypes().length, method.toString());
        }
    }

    @Test
    void accessorSessionAndCompletedRecordStayClosedAndExact() throws Exception {
        var accessor = P4E2QualificationFacade.StoreView.class.getDeclaredMethod(
                "e3StartupView");
        var session = nested(P4E2QualificationFacade.class, "E3StartupSession");
        var snapshot = nested(P4E2QualificationFacade.class, "E3StartupSnapshot");
        var expectedComponents = List.of(
                "sessionToken", "auditInvocations", "auditVariant", "auditGeneration",
                "completeConsumeInvocations", "snapshotInvocations", "snapshotVariant",
                "completeRootCount", "reclaimInvocations", "reclaimVariant",
                "historiesScanned", "revisionsScanned", "historiesChanged",
                "revisionsReclaimed", "dirtyBefore", "dirtyAfter",
                "indexTerminalObservations", "indexTerminal", "indexGeneration");

        assertAll(
                () -> assertTrue(Modifier.isPublic(accessor.getModifiers())),
                () -> assertTrue(Modifier.isFinal(accessor.getModifiers())),
                () -> assertFalse(Modifier.isStatic(accessor.getModifiers())),
                () -> assertEquals(0, accessor.getParameterCount()),
                () -> assertEquals(P4E2QualificationFacade.E3StartupView.class,
                        accessor.getReturnType()),
                () -> assertPackagePrivate(session.getModifiers(), "E3StartupSession"),
                () -> assertTrue(Modifier.isFinal(session.getModifiers())),
                () -> assertEquals(Map.of(
                                "owner", P4E2QualificationFacade.class,
                                "token", long.class),
                        Arrays.stream(session.getDeclaredFields())
                                .filter(field -> !field.isSynthetic())
                                .collect(Collectors.toMap(
                                        field -> field.getName(),
                                        field -> field.getType()))),
                () -> assertTrue(Arrays.stream(session.getDeclaredFields())
                        .filter(field -> !field.isSynthetic())
                        .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                                && Modifier.isFinal(field.getModifiers()))),
                () -> assertTrue(Arrays.stream(session.getDeclaredConstructors())
                        .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())
                                && Arrays.equals(constructor.getParameterTypes(),
                                        new Class<?>[] {
                                            P4E2QualificationFacade.class, long.class
                                        }))),
                () -> assertPackagePrivate(snapshot.getModifiers(), "E3StartupSnapshot"),
                () -> assertTrue(snapshot.isRecord()),
                () -> assertEquals(expectedComponents,
                        Arrays.stream(snapshot.getRecordComponents())
                                .map(component -> component.getName())
                                .toList()),
                () -> assertTrue(Arrays.stream(snapshot.getRecordComponents())
                        .allMatch(component -> component.getType().isPrimitive()
                                || component.getType().isEnum())),
                () -> assertTrue(Arrays.stream(snapshot.getDeclaredConstructors())
                        .allMatch(constructor -> isPackagePrivate(constructor.getModifiers()))));
    }

    @Test
    void javapJdepsAndCompletionBytecodeLockTheClosedArtifact() {
        var mainClasses = PROJECT_ROOT.resolve("build/classes/java/main");
        var facadeClass = mainClasses.resolve(
                "com/yo1no/gramarye/P4E2QualificationFacade.class");
        assertTrue(Files.isRegularFile(facadeClass), facadeClass.toString());

        var publicView = runJdkTool(
                "javap",
                "-classpath", mainClasses.toString(),
                "-public", "-s",
                "com.yo1no.gramarye.P4E2QualificationFacade$E3StartupView");
        assertAll(
                () -> assertEquals(0, publicView.returnCode(), publicView.diagnostics()),
                () -> assertEquals(13, occurrences(publicView.output(), "descriptor:")),
                () -> assertFalse(publicView.output().contains("armE3Startup")),
                () -> assertFalse(publicView.output().contains("claimE3Startup")),
                () -> assertFalse(publicView.output().contains("consumeE3Startup")));

        var fullFacade = runJdkTool(
                "javap",
                "-classpath", mainClasses.toString(),
                "-p", "-s", "-c", "-v",
                "com.yo1no.gramarye.P4E2QualificationFacade");
        assertEquals(0, fullFacade.returnCode(), fullFacade.diagnostics());
        var completion = exclusiveSlice(
                fullFacade.output(),
                "  private void completeE3StartupRecording(",
                "  private void abortE3StartupRecording(");
        var allocation = completion.indexOf(
                "// class com/yo1no/gramarye/P4E2QualificationFacade$E3StartupSnapshot");
        var recordPublication = completion.indexOf("// Field e3CompletedSnapshot:");
        var coordinateClear = completion.indexOf("// Method resetE3Coordinates:()V");
        var witnessPublication = completion.indexOf("// Field e3SessionServer:");
        var completedCommit = completion.lastIndexOf("// Field e3State:");
        var immediateReturn = completion.indexOf(": return", completedCommit);
        assertAll(
                () -> assertEquals(1, occurrences(completion,
                        "// class com/yo1no/gramarye/"
                                + "P4E2QualificationFacade$E3StartupSnapshot")),
                () -> assertTrue(allOrdered(
                        allocation,
                        recordPublication,
                        coordinateClear,
                        witnessPublication,
                        completedCommit,
                        immediateReturn)),
                () -> assertFalse(completion.substring(completedCommit, immediateReturn)
                        .contains("invoke")),
                () -> assertFalse(completion.substring(completedCommit, immediateReturn)
                        .contains("new           ")));

        var dependencies = runJdkTool(
                "jdeps",
                "--ignore-missing-deps",
                "-verbose:class",
                "-cp", mainClasses.toString(),
                facadeClass.toString());
        assertAll(
                () -> assertEquals(0, dependencies.returnCode(),
                        dependencies.diagnostics()),
                () -> assertTrue(dependencies.output().contains("java.base")),
                () -> assertFalse(dependencies.output().contains("p4E3Probe")),
                () -> assertFalse(dependencies.output().contains("p4E3GameTest")),
                () -> assertFalse(dependencies.output().contains("org.junit")),
                () -> assertFalse(dependencies.output().contains("org.hamcrest")));
    }

    @Test
    void packagePrivateControlsAndProductionOwnersRemainExact() throws Exception {
        var facade = P4E2QualificationFacade.class;
        var session = nested(facade, "E3StartupSession");
        var controls = List.of(
                facade.getDeclaredMethod("armE3Startup", MinecraftServer.class),
                facade.getDeclaredMethod("claimE3Startup", MinecraftServer.class),
                facade.getDeclaredMethod(
                        "consumeE3Startup", MinecraftServer.class, session),
                facade.getDeclaredMethod(
                        "abortE3Startup", MinecraftServer.class, session));
        assertTrue(controls.stream().allMatch(method -> isPackagePrivate(method.getModifiers())));
        assertEquals(List.of(void.class, session,
                        nested(facade, "E3StartupSnapshot"), void.class),
                controls.stream().map(Method::getReturnType).toList());

        var serviceSource = withoutCommentsAndLiterals(read(STORE_SERVICE));
        var start = methodBody(serviceSource, "onServerStarting");
        var startup = methodBody(serviceSource, "runP4E3StartupReclaim");
        var stop = methodBody(serviceSource, "onServerStopped");
        var allProduction = javaSources(MAIN_JAVA).stream()
                .map(P4E3ApiGateTest::read)
                .map(P4E3ApiGateTest::withoutCommentsAndLiterals)
                .collect(Collectors.joining("\n"));

        assertAll(
                () -> assertEquals(1, occurrences(start, "runP4E3StartupReclaim(server)")),
                () -> assertOrdered(start,
                        "install(server)",
                        "submissionPort.bootstrapJournal(server)",
                        "runP4E3StartupReclaim(server)"),
                () -> assertEquals(1, invocationCount(startup, "audit")),
                () -> assertEquals(1, invocationCount(startup, "consumeComplete")),
                () -> assertEquals(1, occurrences(
                        startup, "SkillRetentionRootSnapshot.fromCompleteRoots(handoff)")),
                () -> assertEquals(1, invocationCount(startup, "reclaim")),
                () -> assertEquals(1, invocationCount(startup, "observeP4E3IndexTerminal")),
                () -> assertEquals(1, occurrences(allProduction,
                        "gameBus.addListener(this::onServerStarting)")),
                () -> assertEquals(1, occurrences(allProduction,
                        "gameBus.addListener(this::onServerStopped)")),
                () -> assertOrdered(stop,
                        "qualificationStoreView.clearOnServerStopped()",
                        "qualificationStoreView.e3StartupView().clearOnServerStopped(server)",
                        "rootAuditService.removeServer(server)",
                        "uninstall(server)"),
                () -> assertFalse(startup.contains("onlineReconciliationDependency")),
                () -> assertFalse(startup.contains("bootstrapJournal(")),
                () -> assertFalse(startup.contains("PlayerLoggedInEvent")),
                () -> assertFalse(startup.contains("Executor")),
                () -> assertFalse(startup.contains("CompletableFuture")),
                () -> assertFalse(startup.contains("parallelStream(")),
                () -> assertFalse(startup.contains(".post(")),
                () -> assertFalse(read(HANDOFF).contains("public void markStoreSourceUnchanged")),
                () -> assertTrue(read(ROOT_AUDIT_SERVICE).contains(
                        "P4E3IndexTerminalObservation observeP4E3IndexTerminal(")));
    }

    @Test
    void testOnlyArmRouteAndProductionJarBoundaryAreSourceLocked() throws Exception {
        var access = withoutCommentsAndLiterals(read(TEST_ACCESS));
        var build = read(PROJECT_ROOT.resolve("build.gradle"));
        var workflow = read(PROJECT_ROOT.resolve(".github/workflows/build.yml"));
        var productionJarBlock = methodBodyLike(
                build, "tasks.named('jar', Jar).configure {");

        assertAll(
                () -> assertTrue(read(TEST_ACCESS).contains(
                        "@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)")),
                () -> assertTrue(read(TEST_ACCESS).contains(
                        "@SubscribeEvent(priority = EventPriority.HIGHEST)")),
                () -> assertEquals(1, occurrences(access, "ServerAboutToStartEvent event")),
                () -> assertEquals(0, occurrences(access, "ServerStoppedEvent")),
                () -> assertEquals(1, occurrences(access, ".armE3Startup(event.getServer())")),
                () -> assertTrue(access.contains("ModList.get()")),
                () -> assertTrue(access.contains("getModContainerById(Gramarye.MOD_ID)")),
                () -> assertTrue(access.contains(
                        "getCustomExtension(P4E2QualificationFacade.class)")),
                () -> assertFalse(declaresStaticFacadeField(access)),
                () -> assertFalse(access.contains("ThreadLocal")),
                () -> assertFalse(access.contains("System.getProperty(")),
                () -> assertEquals(2, occurrences(build, "sourceSets.create('p4E3")),
                () -> assertTrue(build.contains("sourceSets.create('p4E3Probe')")),
                () -> assertTrue(build.contains("sourceSets.create('p4E3GameTest')")),
                () -> assertEquals(1, occurrences(
                        build,
                        "tasks.named(p4E3GameTestSourceSet.processResourcesTaskName, "
                                + "ProcessResources).configure {")),
                () -> assertEquals(1, occurrences(
                        build, "layout.buildDirectory.dir('generated/p4E3GameTestResources')")),
                () -> assertEquals(1, occurrences(
                        build, "p4E3GameTestSourceSet.output.resourcesDir =")),
                () -> assertEquals(1, occurrences(
                        build,
                        "destinationDir = p4E3GameTestGeneratedResourcesDirectory.get().asFile")),
                () -> assertEquals(1, occurrences(
                        build, "'data/gramarye_p4_e3/structure/p4_e3_probe.nbt'")),
                () -> assertFalse(build.contains(
                        "tasks.register('generateP4E3GameTestResources'")),
                () -> assertEquals(1, occurrences(
                        build, "layout.buildDirectory.dir('p4-e3/command-work')")),
                () -> assertEquals(3, occurrences(
                        build, "workingDir(p4E3CommandDirectory.get().asFile)")),
                () -> assertFalse(productionJarBlock.contains("p4E3Probe")),
                () -> assertFalse(productionJarBlock.contains("p4E3GameTest")),
                () -> assertEquals(1, occurrences(workflow, "  p4-e-memory-gates:")),
                () -> assertEquals(1, occurrences(workflow, "    name: P4-E memory gates")));
    }

    @Test
    void lexicalMaskingAndLiteralInvocationScanRemainLinearAndTextBlockAware() {
        var escapedTextBlockQuotes = "\\" + "\"\"\"";
        var textBlockContinuation = "\\" + "\r\n";
        var source = String.join("",
                "owner . audit (\r\n",
                "// .consumeComplete(\rowner . reclaim (\n",
                "/* .reclaim(\n */\r",
                "var ordinary = \".observeP4E3IndexTerminal(\";\n",
                "var quote = '\\'';\r\n",
                "var slash = '\\\\';\n",
                "var text = \"\"\" \t\f\r",
                ".consumeComplete(\r\n",
                escapedTextBlockQuotes,
                "\r\n",
                textBlockContinuation,
                ".reclaim(\n",
                "\"\"\";\r\n",
                "owner . observeP4E3IndexTerminal (");
        var masked = withoutCommentsAndLiterals(source);

        assertAll(
                () -> assertFalse(source.endsWith("\n")),
                () -> assertEquals(source.length(), masked.length()),
                () -> assertLineEndingsPreserved(source, masked),
                () -> assertEquals(1, invocationCount(masked, "audit")),
                () -> assertEquals(0, invocationCount(masked, "consumeComplete")),
                () -> assertEquals(1, invocationCount(masked, "reclaim")),
                () -> assertEquals(1,
                        invocationCount(masked, "observeP4E3IndexTerminal")),
                () -> assertEquals(1, invocationCount("..audit(", "audit")),
                () -> assertEquals(0, invocationCount("audit(", "audit")),
                () -> assertEquals(0, invocationCount(".Audit(", "audit")),
                () -> assertEquals(0, invocationCount(".audited(", "audit")),
                () -> assertEquals(0,
                        invocationCount(".\u00a0audit(", "audit")));
    }

    private static Set<String> enumNames(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(Enum::name)
                .collect(Collectors.toSet());
    }

    private static Class<?> nested(Class<?> owner, String simpleName) {
        return Arrays.stream(owner.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals(simpleName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing nested type " + simpleName));
    }

    private static void assertPackagePrivate(int modifiers, String label) {
        assertTrue(isPackagePrivate(modifiers), label);
    }

    private static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPublic(modifiers)
                && !Modifier.isProtected(modifiers)
                && !Modifier.isPrivate(modifiers);
    }

    private static boolean declaresStaticFacadeField(String source) {
        var typeName = "P4E2QualificationFacade";
        for (var offset = source.indexOf(typeName);
                offset >= 0;
                offset = source.indexOf(typeName, offset + typeName.length())) {
            var declarationStart = Math.max(
                    Math.max(source.lastIndexOf(';', offset), source.lastIndexOf('{', offset)),
                    source.lastIndexOf('}', offset)) + 1;
            if (!containsAsciiWord(source, declarationStart, offset, "static")) {
                continue;
            }
            var cursor = offset + typeName.length();
            while (cursor < source.length()
                    && isAsciiRegexWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            while (cursor < source.length()
                    && Character.isJavaIdentifierPart(source.charAt(cursor))) {
                cursor++;
            }
            while (cursor < source.length()
                    && isAsciiRegexWhitespace(source.charAt(cursor))) {
                cursor++;
            }
            if (cursor >= source.length() || source.charAt(cursor) != '(') {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAsciiWord(
            String source, int start, int end, String word) {
        for (var offset = source.indexOf(word, start);
                offset >= 0 && offset < end;
                offset = source.indexOf(word, offset + word.length())) {
            var before = offset == start || !Character.isJavaIdentifierPart(source.charAt(offset - 1));
            var afterOffset = offset + word.length();
            var after = afterOffset >= end
                    || !Character.isJavaIdentifierPart(source.charAt(afterOffset));
            if (before && after) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> relativeJavaSources(String relativeRoot) throws IOException {
        return javaSources(PROJECT_ROOT.resolve(relativeRoot)).stream()
                .map(PROJECT_ROOT::relativize)
                .map(Path::toString)
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

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
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

    private static boolean allOrdered(int... coordinates) {
        var previous = -1;
        for (var coordinate : coordinates) {
            if (coordinate <= previous) {
                return false;
            }
            previous = coordinate;
        }
        return true;
    }

    private static String exclusiveSlice(String source, String begin, String end) {
        var beginIndex = source.indexOf(begin);
        var endIndex = source.indexOf(end, beginIndex + begin.length());
        if (beginIndex < 0 || endIndex <= beginIndex) {
            throw new AssertionError("unable to isolate bytecode slice: " + begin);
        }
        return source.substring(beginIndex, endIndex);
    }

    private static ToolResult runJdkTool(String name, String... arguments) {
        var tool = ToolProvider.findFirst(name)
                .orElseThrow(() -> new AssertionError("JDK tool is unavailable: " + name));
        var output = new StringWriter();
        var diagnostics = new StringWriter();
        int returnCode;
        try (var outputWriter = new PrintWriter(output);
                var diagnosticWriter = new PrintWriter(diagnostics)) {
            returnCode = tool.run(outputWriter, diagnosticWriter, arguments);
        }
        return new ToolResult(returnCode, output.toString(), diagnostics.toString());
    }

    private static int invocationCount(String source, String methodName) {
        var count = 0;
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
                count++;
            }
        }
        return count;
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

    private static void assertOrdered(String source, String... fragments) {
        var previous = -1;
        for (var fragment : fragments) {
            var current = source.indexOf(fragment, previous + 1);
            assertTrue(current >= 0, "missing ordered fragment: " + fragment);
            assertTrue(current > previous, "out-of-order fragment: " + fragment);
            previous = current;
        }
    }

    private static String methodBody(String source, String methodName) {
        var signature = source.indexOf(methodName + "(");
        if (signature < 0) {
            throw new AssertionError("method not found: " + methodName);
        }
        var open = source.indexOf('{', signature);
        return bracedBody(source, open, methodName);
    }

    private static String methodBodyLike(String source, String opening) {
        var signature = source.indexOf(opening);
        if (signature < 0) {
            throw new AssertionError("block not found: " + opening);
        }
        var open = source.indexOf('{', signature);
        return bracedBody(source, open, opening);
    }

    private static String bracedBody(String source, int open, String label) {
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            var character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("block did not close: " + label);
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
        for (var cursor = index + 3; cursor < source.length(); cursor++) {
            var character = source.charAt(cursor);
            if (character == '\r' || character == '\n') {
                return true;
            }
            if (character != ' ' && character != '\t' && character != '\f') {
                return false;
            }
        }
        return false;
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

    private static void assertLineEndingsPreserved(String original, String masked) {
        for (var index = 0; index < original.length(); index++) {
            if (original.charAt(index) == '\r' || original.charAt(index) == '\n'
                    || masked.charAt(index) == '\r' || masked.charAt(index) == '\n') {
                assertEquals(original.charAt(index), masked.charAt(index),
                        "line terminator changed at " + index);
            }
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    private enum LexicalState {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING,
        CHARACTER,
        TEXT_BLOCK
    }

    private record ToolResult(int returnCode, String output, String diagnostics) {
    }
}
