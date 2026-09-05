package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P6S3BoundaryTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path TEST_JAVA = PROJECT_ROOT.resolve("src/test/java");
    private static final Path MANA_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana");
    private static final Path MANA_TEST = TEST_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/mana");
    private static final Path EFFECT_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/effect");
    private static final Path EFFECT_TEST = TEST_JAVA.resolve(
            "com/yo1no/gramarye/magic/runtime/effect");
    private static final Pattern TEST_METHOD = Pattern.compile(
            "(?m)^\\s*@Test\\s*\\R\\s*void\\s+"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final List<String> S1_PRODUCTION_FILES = List.of(
            "DamageEffectCommitPort.java",
            "EffectCommitPlan.java",
            "EffectExecutionEngine.java",
            "EffectExecutionGuard.java",
            "EffectExecutionResult.java",
            "EffectRequest.java",
            "EffectResolution.java",
            "EffectStep.java",
            "EffectStepOutcome.java",
            "EffectTrace.java",
            "P6EffectBounds.java",
            "P6ExecutionInvariantException.java");
    private static final List<String> S1_TEST_FILES = List.of(
            "DamageEffectCommitPortTest.java",
            "DamageEffectRequestTest.java",
            "DamageEffectResolverTest.java",
            "EffectCommitPlanTest.java",
            "EffectEngineTestDoubles.java",
            "EffectExecutionEngineFailureTest.java",
            "EffectExecutionEngineSuccessTest.java",
            "EffectExecutionGuardTest.java",
            "EffectExecutionResultTest.java",
            "EffectSemanticBoundaryTest.java",
            "EffectStepOutcomeTest.java",
            "EffectTestFixtures.java",
            "EffectTraceTest.java",
            "P6EffectVocabularyTest.java");
    private static final List<String> S2_TEST_FILES = List.of(
            "ManaBoundaryTest.java",
            "ManaLifecycleTest.java",
            "ManaMutationBudgetTest.java",
            "ManaStateCodecTest.java",
            "ManaTransactionServiceTest.java",
            "P6ManaBoundsTest.java");
    private static final List<String> S3_PRODUCTION_FILES = List.of(
            "ActionDamageTransactionEngine.java",
            "ActionDamageTransactionResult.java",
            "ActionExecutor.java",
            "ActionExecutorRegistry.java",
            "DamageActionExecutor.java",
            "DamageActionInvocation.java");
    private static final List<Class<?>> S3_TYPES = List.of(
            ActionExecutor.class,
            ActionExecutorOutcome.class,
            ProducedActionRequest.class,
            NoActionRequest.class,
            ActionExecutorRegistration.class,
            ActionExecutorRegistry.class,
            DamageActionInvocation.class,
            DamageActionExecutor.class,
            ManaExecutionSummaryKind.class,
            ManaExecutionSummary.class,
            ManaReceiptSnapshot.class,
            ManaNotRequired.class,
            ManaDebitRejected.class,
            ManaDebited.class,
            ManaRefunded.class,
            ManaRefundFailed.class,
            ProvisionalEffectFailure.class,
            ActionDamageTransactionResult.class,
            ActionDamageTransactionEngine.class);
    private static final List<Class<?>> S1_PHASE_TYPES = List.of(
            EffectExecutionPreparation.class,
            PreparedEffectExecution.class,
            TerminalEffectExecution.class,
            EffectAttemptStatus.class,
            ManaTraceState.class,
            EffectExecutionAttempt.class);

    @Test
    void relocatedS1AndExistingS2InventoriesAreExact() throws IOException {
        List<Path> s1Production = paths(MANA_MAIN, S1_PRODUCTION_FILES);
        List<Path> s1Tests = paths(MANA_TEST, S1_TEST_FILES);
        List<Path> s2Tests = paths(MANA_TEST, S2_TEST_FILES);
        List<Path> s3Production = paths(MANA_MAIN, S3_PRODUCTION_FILES);

        assertEquals(12, s1Production.size());
        assertTrue(s1Production.stream().allMatch(Files::isRegularFile));
        assertEquals(14, s1Tests.size());
        assertTrue(s1Tests.stream().allMatch(Files::isRegularFile));
        assertEquals(93L, testCount(s1Tests));
        assertEquals(70L, testCount(s2Tests));
        assertEquals(6, s3Production.size());
        assertTrue(s3Production.stream().allMatch(Files::isRegularFile));
        assertTrue(!Files.exists(EFFECT_MAIN) || javaSources(EFFECT_MAIN).isEmpty());
        assertTrue(!Files.exists(EFFECT_TEST) || javaSources(EFFECT_TEST).isEmpty());
        assertTrue(s1Production.stream().allMatch(P6S3BoundaryTest::usesManaPackage));
        assertTrue(s1Tests.stream().allMatch(P6S3BoundaryTest::usesManaPackage));
    }

    @Test
    void s3AndRelocatedS1TypesAddNoPublicOrProtectedSurface() throws IOException {
        String source = source(paths(MANA_MAIN, S1_PRODUCTION_FILES))
                + source(paths(MANA_MAIN, S3_PRODUCTION_FILES));
        Pattern publicTopLevel = Pattern.compile(
                "(?m)^public\\s+(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                        + "(?:class|interface|record|enum)\\b");
        Pattern protectedMember = Pattern.compile("(?m)^\\s*protected\\s+");

        assertFalse(publicTopLevel.matcher(source).find());
        assertFalse(protectedMember.matcher(source).find());
        assertTrue(java.util.stream.Stream.concat(
                        S3_TYPES.stream(), S1_PHASE_TYPES.stream())
                .noneMatch(type -> Modifier.isPublic(type.getModifiers())
                        || Modifier.isProtected(type.getModifiers())));
        assertTrue(java.util.stream.Stream.concat(
                        S3_TYPES.stream(), S1_PHASE_TYPES.stream())
                .allMatch(P6S3BoundaryTest::hasNoProtectedMember));
    }

    @Test
    void registryDispatchUsesResourceLocationWithoutStaticOrRuntimeRegistration()
            throws Exception {
        var registrationComponents = ActionExecutorRegistration.class.getRecordComponents();
        var invocationComponents = DamageActionInvocation.class.getRecordComponents();
        var find = ActionExecutorRegistry.class.getDeclaredMethod(
                "find", ResourceLocation.class);
        var registryField = ActionExecutorRegistry.class.getDeclaredField("executors");
        Set<String> methodNames = Arrays.stream(
                        ActionExecutorRegistry.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        String source = code(MANA_MAIN.resolve("ActionExecutorRegistry.java"));

        assertEquals(ResourceLocation.class, registrationComponents[0].getType());
        assertEquals(ResourceLocation.class, invocationComponents[0].getType());
        assertEquals(ResourceLocation.class, find.getParameterTypes()[0]);
        assertEquals(Optional.class, find.getReturnType());
        assertEquals(Map.class, registryField.getType());
        assertTrue(registryField.getGenericType().getTypeName().contains(
                "java.util.Map<net.minecraft.resources.ResourceLocation, "
                        + "com.yo1no.gramarye.magic.runtime.mana.ActionExecutor>"));
        assertTrue(Modifier.isPrivate(registryField.getModifiers()));
        assertTrue(Modifier.isFinal(registryField.getModifiers()));
        assertEquals(Set.of("find", "size"), methodNames);
        assertEquals(0, Arrays.stream(ActionExecutorRegistry.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .count());
        for (String forbidden : List.of(
                "ServiceLoader", "Class.forName", ".register(", ".remove(", ".clear(")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void s3SourcesContainNoWorldDamageP5AdapterQueueOrChildPublication()
            throws IOException {
        String source = source(paths(MANA_MAIN, S3_PRODUCTION_FILES));

        for (String forbidden : List.of(
                "net.minecraft.world",
                "net.minecraft.server",
                "LivingEntity",
                "DamageSource",
                "DamageType",
                ".hurt(",
                "RuntimeExecutionPort",
                "RuntimeEvent",
                "RuntimeExecutionContext",
                "RuntimeChildPlan",
                "RuntimeQueue",
                "SkillRuntimeService",
                ".publish(",
                ".enqueue(",
                ".schedule(")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void s3SourcesContainNoBackgroundRandomReflectionRawUncheckedOrThrowableCatch()
            throws IOException {
        String source = source(paths(MANA_MAIN, S3_PRODUCTION_FILES));

        for (String forbidden : List.of(
                "java.util.concurrent",
                "java.lang.Thread",
                "new Thread(",
                "ThreadLocal",
                "ExecutorService",
                "CompletableFuture",
                "Future<",
                "ForkJoin",
                "parallelStream",
                "java.util.Random",
                "RandomGenerator",
                "Math.random",
                "java.lang.reflect",
                "ServiceLoader",
                "Class.forName",
                "Method.invoke",
                "@SuppressWarnings",
                "catch (Throwable",
                "catch (RuntimeException",
                "catch (Error")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertFalse(Pattern.compile("\\bObject\\b").matcher(source).find());
        assertFalse(Pattern.compile(
                        "\\b(?:Map|List|Optional)\\s+[A-Za-z_$][A-Za-z0-9_$]*")
                .matcher(source)
                .find());
    }

    @Test
    void transactionOwnerRetainsOnlyRegistryEngineAndManaService() {
        Map<String, Class<?>> retained = Arrays.stream(
                        ActionDamageTransactionEngine.class.getDeclaredFields())
                .collect(Collectors.toMap(
                        field -> field.getName(), field -> field.getType()));
        Map<String, Class<?>> expected = Map.of(
                "executors", ActionExecutorRegistry.class,
                "resolver", EffectResolver.class,
                "effects", EffectExecutionEngine.class,
                "manaTransactions", ManaTransactionService.class);

        assertEquals(expected, retained);
        assertTrue(Arrays.stream(ActionDamageTransactionEngine.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())
                        && !Modifier.isStatic(field.getModifiers())));
        assertEquals(0, EffectExecutionEngine.class.getDeclaredFields().length);
        assertEquals(0, DamageActionExecutor.class.getDeclaredFields().length);
    }

    @Test
    void primaryStepCommitLoopHasExactlyOneOwnerAndManaTruthRemainsUnique()
            throws IOException {
        String s1Source = source(paths(MANA_MAIN, S1_PRODUCTION_FILES));
        String manaSource = javaSources(MANA_MAIN).stream()
                .map(P6S3BoundaryTest::code)
                .collect(Collectors.joining("\n"));

        assertEquals(1, occurrences(
                s1Source, "for (EffectStep step : prepared.plan().steps())"));
        assertEquals(1, occurrences(
                s1Source, "prepared.commitPort().commitDamage(damageStep)"));
        assertEquals(1, occurrences(
                manaSource, "private static final AttachmentType<ManaState> PLAYER_MANA"));
        assertEquals(1, occurrences(manaSource, ".getData(PLAYER_MANA)"));
        assertEquals(1, occurrences(manaSource, ".setData(PLAYER_MANA"));
        assertFalse(source(paths(MANA_MAIN, S3_PRODUCTION_FILES)).contains("AttachmentType"));
    }

    @Test
    void attachmentBridgeRegistrationOwnerAndNineteenGameTestsRemainExact()
            throws Exception {
        Class<?> bridge = ManaAttachmentDefinitionBridge.class;
        Set<String> methods = Arrays.stream(bridge.getDeclaredMethods())
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        Set<String> registrationOwners = javaSources(MAIN_JAVA).stream()
                .filter(path -> code(path).contains(
                        "NeoForgeRegistries.Keys.ATTACHMENT_TYPES"))
                .map(path -> MAIN_JAVA.relativize(path).toString().replace('\\', '/'))
                .collect(Collectors.toSet());
        String allMain = javaSources(MAIN_JAVA).stream()
                .map(P6S3BoundaryTest::code)
                .collect(Collectors.joining("\n"));

        assertTrue(Modifier.isPublic(bridge.getModifiers()));
        assertTrue(Modifier.isFinal(bridge.getModifiers()));
        assertEquals(0, bridge.getDeclaredFields().length);
        assertEquals(1, bridge.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(bridge.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(Set.of("attachmentId", "attachmentType"), methods);
        assertTrue(Arrays.stream(bridge.getDeclaredMethods()).allMatch(method ->
                Modifier.isPublic(method.getModifiers())
                        && Modifier.isStatic(method.getModifiers())));
        assertEquals(
                Set.of("com/yo1no/gramarye/magic/definition/player/"
                        + "PlayerSkillAttachments.java"),
                registrationOwners);
        assertEquals(com.yo1no.gramarye.P7GameTestInventory.totalCount(), occurrences(allMain, "@GameTest("));
    }

    private static List<Path> paths(Path root, List<String> names) {
        return names.stream().map(root::resolve).toList();
    }

    private static boolean hasNoProtectedMember(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                        .noneMatch(field -> Modifier.isProtected(field.getModifiers()))
                && Arrays.stream(type.getDeclaredMethods())
                        .noneMatch(method -> Modifier.isProtected(method.getModifiers()))
                && Arrays.stream(type.getDeclaredConstructors())
                        .noneMatch(constructor -> Modifier.isProtected(
                                constructor.getModifiers()));
    }

    private static long testCount(List<Path> paths) {
        return paths.stream()
                .map(P6S3BoundaryTest::readSource)
                .mapToLong(source -> TEST_METHOD.matcher(source).results().count())
                .sum();
    }

    private static String source(List<Path> paths) {
        return paths.stream()
                .map(P6S3BoundaryTest::code)
                .collect(Collectors.joining("\n"));
    }

    private static boolean usesManaPackage(Path path) {
        return readSource(path).startsWith(
                "package com.yo1no.gramarye.magic.runtime.mana;");
    }

    private static String readSource(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static String code(Path path) {
        return readSource(path)
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ")
                .replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static int occurrences(String source, String fragment) {
        int count = 0;
        for (int index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
