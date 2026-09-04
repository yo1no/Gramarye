package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

final class P7S1BoundaryTest {
    private static final String PACKAGE_NAME = "com.yo1no.gramarye.magic.network";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path NETWORK_MAIN =
            MAIN_JAVA.resolve("com/yo1no/gramarye/magic/network");
    private static final Path NETWORK_TEST =
            PROJECT_ROOT.resolve("src/test/java/com/yo1no/gramarye/magic/network");
    private static final Map<String, Class<?>> PRODUCT_TYPES_BY_PATH = Map.ofEntries(
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/P7NetworkBounds.java",
                    P7NetworkBounds.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/"
                            + "P7SemanticInvariantException.java",
                    P7SemanticInvariantException.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/CastInputKind.java",
                    CastInputKind.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/AimHint.java",
                    AimHint.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/EntityHint.java",
                    EntityHint.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/CastIntent.java",
                    CastIntent.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/CastIntentValidation.java",
                    CastIntentValidation.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/P7IntentFailureReason.java",
                    P7IntentFailureReason.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/IntentAcknowledgement.java",
                    IntentAcknowledgement.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/ConnectionEpochState.java",
                    ConnectionEpochState.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/P7SessionIdentity.java",
                    P7SessionIdentity.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/IntentSequenceState.java",
                    IntentSequenceState.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/IntentTokenBucket.java",
                    IntentTokenBucket.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/IntentTickBudget.java",
                    IntentTickBudget.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/RateStrikeState.java",
                    RateStrikeState.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/PendingPermitAccounting.java",
                    PendingPermitAccounting.class),
            Map.entry(
                    "src/main/java/com/yo1no/gramarye/magic/network/"
                            + "CastIntentAdmissionSemantics.java",
                    CastIntentAdmissionSemantics.class));
    private static final Map<String, Class<?>> TEST_TYPES_BY_PATH = Map.ofEntries(
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/P7NetworkBoundsTest.java",
                    P7NetworkBoundsTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/CastIntentTest.java",
                    CastIntentTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/"
                            + "IntentAcknowledgementTest.java",
                    IntentAcknowledgementTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/ConnectionEpochStateTest.java",
                    ConnectionEpochStateTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/P7SessionIdentityTest.java",
                    P7SessionIdentityTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/IntentSequenceStateTest.java",
                    IntentSequenceStateTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/IntentTokenBucketTest.java",
                    IntentTokenBucketTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/IntentTickBudgetTest.java",
                    IntentTickBudgetTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/RateStrikeStateTest.java",
                    RateStrikeStateTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/"
                            + "PendingPermitAccountingTest.java",
                    PendingPermitAccountingTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/"
                            + "CastIntentAdmissionSemanticsTest.java",
                    CastIntentAdmissionSemanticsTest.class),
            Map.entry(
                    "src/test/java/com/yo1no/gramarye/magic/network/P7S1BoundaryTest.java",
                    P7S1BoundaryTest.class));

    private static final Set<String> ALLOWED_IMPORTS = Set.of(
            "java.util.Objects",
            "java.util.Optional",
            "java.util.OptionalLong",
            "java.util.UUID");
    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^package\\s+([^;]+);\\s*$");
    private static final Pattern IMPORT_DECLARATION =
            Pattern.compile("(?m)^import\\s+([^;]+);\\s*$");
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:(?:final|sealed|non-sealed|abstract)\\s+)*"
                    + "(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");
    private static final String ANNOTATION_PREFIX = Character.toString('@');
    private static final Pattern ORDINARY_TEST_ANNOTATION = Pattern.compile(
            "(?m)^\\s*" + Pattern.quote(ANNOTATION_PREFIX) + "Test\\b");
    private static final Pattern UNSUPPORTED_TEST_ANNOTATION = Pattern.compile(
            "(?m)^\\s*"
                    + Pattern.quote(ANNOTATION_PREFIX)
                    + "(?:ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|"
                    + "Disabled[A-Za-z0-9_]*|Enabled[A-Za-z0-9_]*)\\b");
    private static final Pattern PLATFORM_GAME_TEST_SURFACE = Pattern.compile(
            "(?m)^\\s*(?:import\\s+[^;]*\\.gametest\\.[^;]+;|"
                    + Pattern.quote(ANNOTATION_PREFIX)
                    + "Game"
                    + "Test(?:Holder)?\\b)");

    private static final Map<String, Pattern> FORBIDDEN_GENERIC_SOURCE_PATTERNS = Map.ofEntries(
            Map.entry(
                    "unchecked or raw-types suppression",
                    Pattern.compile(
                            Pattern.quote(ANNOTATION_PREFIX)
                                    + "SuppressWarnings\\s*\\([^)]*(?:unchecked|rawtypes)")),
            Map.entry(
                    "raw generic declaration",
                    Pattern.compile(
                            "\\b(?:Optional|List|Set|Map|Collection|Queue|Deque|Iterable|"
                                    + "Iterator|Stream|Class)\\s+[A-Za-z_$][A-Za-z0-9_$]*"
                                    + "\\s*(?:[=;,)\\[])")),
            Map.entry(
                    "raw generic construction",
                    Pattern.compile(
                            "\\bnew\\s+(?:ArrayList|LinkedList|HashSet|TreeSet|HashMap|"
                                    + "TreeMap|ArrayDeque|Optional)\\s*\\(")),
            Map.entry(
                    "raw generic cast",
                    Pattern.compile(
                            "\\(\\s*(?:Optional|List|Set|Map|Collection|Queue|Deque|"
                                    + "Iterable|Iterator|Stream|Class)\\s*\\)")),
            Map.entry(
                    "parameterized unchecked cast",
                    Pattern.compile(
                            "\\(\\s*[A-Za-z_$][A-Za-z0-9_$.]*\\s*<[^;()]+>\\s*\\)")));

    private static final Map<String, Pattern> FORBIDDEN_SOURCE_PATTERNS = Map.ofEntries(
            Map.entry(
                    "Minecraft, NeoForge, Netty, or Mojang platform reference",
                    Pattern.compile(
                            "(?:net\\.minecraft|net\\.neoforged|io\\.netty|"
                                    + "com\\.mojang)\\.")),
            Map.entry(
                    "payload, codec, handler, or transport reference",
                    Pattern.compile(
                            "\\b(?:CustomPacketPayload|StreamCodec|Codec|ByteBuf|"
                                    + "FriendlyByteBuf|RegistryFriendlyByteBuf|IPayloadContext|"
                                    + "PayloadRegistrar|PacketDistributor|ServerPlayer|"
                                    + "MinecraftServer|Entity|Level|ResourceLocation|"
                                    + "ResourceKey)\\b")),
            Map.entry(
                    "P5 or P6 dependency",
                    Pattern.compile(
                            "\\bP(?:5(?!_(?:ADMISSION_REJECTED|UNAVAILABLE)\\b)|6)"
                                    + "[A-Za-z0-9_$]*\\b")),
            Map.entry("GameTest reference", Pattern.compile("\\bGameTest\\b")),
            Map.entry(
                    "thread, future, executor, callback, or parallel work",
                    Pattern.compile(
                            "\\b(?:Thread|ThreadLocal|Future|FutureTask|CompletableFuture|"
                                    + "Executor|ExecutorService|ForkJoinPool|ForkJoinTask|"
                                    + "Runnable|Callable|synchronized|volatile)\\b|"
                                    + "java\\.util\\.concurrent|\\.parallelStream\\s*\\(|"
                                    + "\\.parallel\\s*\\(|\\b(?:sleep|poll)\\s*\\(")),
            Map.entry(
                    "randomness",
                    Pattern.compile(
                            "\\b(?:Random|SecureRandom|SplittableRandom|ThreadLocalRandom)\\b|"
                                    + "Math\\.random\\s*\\(|UUID\\.randomUUID\\s*\\(")),
            Map.entry(
                    "wall clock",
                    Pattern.compile(
                            "System\\.(?:currentTimeMillis|nanoTime)\\s*\\(|"
                                    + "\\b(?:Clock|Instant|LocalDateTime|OffsetDateTime|"
                                    + "ZonedDateTime|Date)\\s*\\.now\\s*\\(")),
            Map.entry(
                    "reflection or method-handle access",
                    Pattern.compile(
                            "java\\.lang\\.reflect|java\\.lang\\.invoke|"
                                    + "\\b(?:MethodHandle|MethodHandles|VarHandle)\\b|"
                                    + "Class\\.forName\\s*\\(|\\.getDeclared(?:Field|Fields|"
                                    + "Method|Methods|Constructor|Constructors)\\s*\\(|"
                                    + "\\.getMethod\\s*\\(")),
            Map.entry(
                    "I/O or socket access",
                    Pattern.compile(
                            "(?:java\\.io|java\\.nio|java\\.net)\\.|"
                                    + "\\b(?:Socket|ServerSocket|DatagramSocket|"
                                    + "URLConnection)\\b")),
            Map.entry(
                    "registration or service loading",
                    Pattern.compile(
                            "\\b(?:register|registered|registration|registrar|ServiceLoader|"
                                    + "RegisterPayloadHandlersEvent|IEventBus|"
                                    + "EventBusSubscriber|SubscribeEvent)\\b",
                            Pattern.CASE_INSENSITIVE)),
            Map.entry(
                    "second scheduler or timer",
                    Pattern.compile(
                            "\\b(?:scheduler|scheduledexecutorservice|timer|timertask)\\b|"
                                    + "\\.schedule(?:AtFixedRate|WithFixedDelay)?\\s*\\(",
                            Pattern.CASE_INSENSITIVE)));

    private static final Set<String> LIVE_OBJECT_SIMPLE_NAMES = Set.of(
            "ServerPlayer",
            "Entity",
            "Level",
            "MinecraftServer",
            "ByteBuf",
            "IPayloadContext",
            "RuntimeEvent",
            "SkillDefinition",
            "ManaState");

    @Test
    void productionPathTypeAndPackageInventoryIsExact() throws IOException {
        Set<String> actualPaths;
        try (var paths = Files.walk(NETWORK_MAIN)) {
            actualPaths = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(PROJECT_ROOT::relativize)
                    .map(P7S1BoundaryTest::portablePath)
                    .collect(Collectors.toUnmodifiableSet());
        }

        assertEquals(PRODUCT_TYPES_BY_PATH.keySet(), actualPaths);
        assertEquals(17, actualPaths.size());
        PRODUCT_TYPES_BY_PATH.forEach((relativePath, type) -> {
            var source = read(PROJECT_ROOT.resolve(relativePath));
            var packages = PACKAGE_DECLARATION.matcher(source).results()
                    .map(result -> result.group(1))
                    .toList();
            var topLevelTypes = TOP_LEVEL_TYPE.matcher(source).results()
                    .map(result -> result.group(1))
                    .toList();
            assertEquals(List.of(PACKAGE_NAME), packages, relativePath);
            assertEquals(List.of(type.getSimpleName()), topLevelTypes, relativePath);
            assertEquals(
                    type.getSimpleName() + ".java",
                    Path.of(relativePath).getFileName().toString(),
                    relativePath);
        });
    }

    @Test
    void productTypesAndLanguageGeneratedMembersExposeNoExternalApi() {
        var allTypes = allProductTypes();
        var externallyVisibleTypes = allTypes.stream()
                .filter(P7S1BoundaryTest::isPublicOrProtected)
                .map(Class::getName)
                .sorted()
                .toList();
        var unexpectedVisibleFields = allTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> isPublicOrProtected(field.getModifiers()))
                .filter(field -> !field.isEnumConstant())
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var unexpectedVisibleConstructors = allTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredConstructors()))
                .filter(constructor -> isPublicOrProtected(constructor.getModifiers()))
                .map(Object::toString)
                .sorted()
                .toList();
        var unexpectedVisibleMethods = allTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> isPublicOrProtected(method.getModifiers()))
                .filter(method -> !isLanguageRequiredSurface(method))
                .map(method -> method.getDeclaringClass().getName() + "#" + method.getName())
                .sorted()
                .toList();

        assertEquals(17, PRODUCT_TYPES_BY_PATH.size());
        assertTrue(PRODUCT_TYPES_BY_PATH.values().stream()
                .allMatch(type -> type.getEnclosingClass() == null));
        assertTrue(PRODUCT_TYPES_BY_PATH.values().stream()
                .allMatch(type -> type.getPackageName().equals(PACKAGE_NAME)));
        assertEquals(List.of(), externallyVisibleTypes);
        assertEquals(List.of(), unexpectedVisibleFields);
        assertEquals(List.of(), unexpectedVisibleConstructors);
        assertEquals(List.of(), unexpectedVisibleMethods);
        assertOutsidePackageCannotNameProductTypes();
    }

    @Test
    void productionImportsAndSourceRemainPureAndTransportFree() {
        var sources = PRODUCT_TYPES_BY_PATH.keySet().stream()
                .map(PROJECT_ROOT::resolve)
                .map(P7S1BoundaryTest::read)
                .toList();
        var combined = String.join("\n", sources);
        var imports = sources.stream()
                .flatMap(source -> IMPORT_DECLARATION.matcher(source).results())
                .map(result -> result.group(1))
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(ALLOWED_IMPORTS, imports);
        FORBIDDEN_SOURCE_PATTERNS.forEach((description, pattern) -> assertFalse(
                pattern.matcher(combined).find(),
                () -> "P7-S1 production contains forbidden " + description));
        FORBIDDEN_GENERIC_SOURCE_PATTERNS.forEach((description, pattern) -> assertFalse(
                pattern.matcher(combined).find(),
                () -> "P7-S1 production contains forbidden " + description));
    }

    @Test
    void productStateHasNoMutableStaticLiveObjectThrowableOrUnboundedRetention() {
        var allTypes = allProductTypes();
        var fields = allTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> !field.isSynthetic())
                .toList();

        var mutableStatic = fields.stream()
                .filter(field -> java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .filter(field -> !java.lang.reflect.Modifier.isFinal(field.getModifiers()))
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var nonPrivateFinalInstanceState = fields.stream()
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .filter(field -> !java.lang.reflect.Modifier.isPrivate(field.getModifiers())
                        || !java.lang.reflect.Modifier.isFinal(field.getModifiers()))
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var liveObjects = fields.stream()
                .filter(field -> isLiveObjectType(field.getType()))
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var throwableRetention = fields.stream()
                .filter(field -> Throwable.class.isAssignableFrom(field.getType())
                        || field.getGenericType().getTypeName().contains("java.lang.Throwable"))
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var objectPayloadFields = fields.stream()
                .filter(field -> mentionsObject(field.getGenericType().getTypeName()))
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var unboundedState = fields.stream()
                .filter(field -> isUnboundedContainer(field.getType()))
                .map(field -> field.getDeclaringClass().getName() + "#" + field.getName())
                .sorted()
                .toList();
        var objectPayloadCallSurface = allTypes.stream()
                .flatMap(type -> Stream.concat(
                        Arrays.stream(type.getDeclaredConstructors())
                                .filter(constructor -> Arrays.stream(
                                                constructor.getGenericParameterTypes())
                                        .anyMatch(parameter -> mentionsObject(
                                                parameter.getTypeName())))
                                .map(Object::toString),
                        Arrays.stream(type.getDeclaredMethods())
                                .filter(method -> !isObjectEqualsOverride(method))
                                .filter(method -> mentionsObject(
                                                method.getGenericReturnType().getTypeName())
                                        || Arrays.stream(method.getGenericParameterTypes())
                                                .anyMatch(parameter -> mentionsObject(
                                                        parameter.getTypeName())))
                                .map(Object::toString)))
                .sorted()
                .toList();

        assertEquals(List.of(), mutableStatic);
        assertEquals(List.of(), nonPrivateFinalInstanceState);
        assertEquals(List.of(), liveObjects);
        assertEquals(List.of(), throwableRetention);
        assertEquals(List.of(), objectPayloadFields);
        assertEquals(List.of(), unboundedState);
        assertEquals(List.of(), objectPayloadCallSurface);
    }

    @Test
    void invariantExceptionAndUuidIdentityAreTheOnlyAuthorizedSpecialScalars() {
        var throwableProductTypes = allProductTypes().stream()
                .filter(Throwable.class::isAssignableFrom)
                .collect(Collectors.toUnmodifiableSet());
        var uuidFields = allProductTypes().stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> field.getType() == UUID.class)
                .toList();

        assertEquals(Set.of(P7SemanticInvariantException.class), throwableProductTypes);
        assertEquals(IllegalStateException.class, P7SemanticInvariantException.class.getSuperclass());
        assertEquals(0, P7SemanticInvariantException.class.getDeclaredFields().length);
        assertEquals(1, uuidFields.size());
        assertEquals(P7SessionIdentity.class, uuidFields.getFirst().getDeclaringClass());
        assertEquals("authenticatedPlayerId", uuidFields.getFirst().getName());
        assertTrue(java.lang.reflect.Modifier.isPrivate(uuidFields.getFirst().getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(uuidFields.getFirst().getModifiers()));
        assertEquals(Set.of("P7SessionIdentity.java"), PRODUCT_TYPES_BY_PATH.keySet().stream()
                .filter(path -> read(PROJECT_ROOT.resolve(path)).contains("java.util.UUID"))
                .map(path -> Path.of(path).getFileName().toString())
                .collect(Collectors.toUnmodifiableSet()));
    }

    @Test
    void exactTestInventoryUsesOnlyEnabledOrdinaryZeroArgumentTestsAndNoGameTests()
            throws IOException {
        Set<String> actualTestPaths;
        try (var paths = Files.walk(NETWORK_TEST)) {
            actualTestPaths = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(PROJECT_ROOT::relativize)
                    .map(P7S1BoundaryTest::portablePath)
                    .collect(Collectors.toUnmodifiableSet());
        }

        assertEquals(TEST_TYPES_BY_PATH.keySet(), actualTestPaths);
        assertEquals(12, actualTestPaths.size());
        TEST_TYPES_BY_PATH.forEach((relativePath, type) -> {
            var source = read(PROJECT_ROOT.resolve(relativePath));
            var packages = PACKAGE_DECLARATION.matcher(source).results()
                    .map(result -> result.group(1))
                    .toList();
            var topLevelTypes = TOP_LEVEL_TYPE.matcher(source).results()
                    .map(result -> result.group(1))
                    .toList();
            var ordinarySourceIds = ORDINARY_TEST_ANNOTATION.matcher(source).results().count();
            var ordinaryRuntimeIds = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Test.class))
                    .toList();

            assertEquals(List.of(PACKAGE_NAME), packages, relativePath);
            assertEquals(List.of(type.getSimpleName()), topLevelTypes, relativePath);
            assertFalse(ordinaryRuntimeIds.isEmpty(), relativePath);
            assertEquals(ordinarySourceIds, ordinaryRuntimeIds.size(), relativePath);
            assertTrue(
                    ordinaryRuntimeIds.stream()
                            .allMatch(method -> method.getParameterCount() == 0),
                    relativePath);
            assertTrue(
                    ordinaryRuntimeIds.stream()
                            .allMatch(method -> method.getReturnType() == void.class),
                    relativePath);
            assertFalse(UNSUPPORTED_TEST_ANNOTATION.matcher(source).find(), relativePath);
            assertFalse(PLATFORM_GAME_TEST_SURFACE.matcher(source).find(), relativePath);
        });

        var p7Source = Stream.concat(
                        PRODUCT_TYPES_BY_PATH.keySet().stream(),
                        TEST_TYPES_BY_PATH.keySet().stream())
                .map(PROJECT_ROOT::resolve)
                .map(P7S1BoundaryTest::read)
                .collect(Collectors.joining("\n"));
        String allProduction;
        try (var paths = Files.walk(MAIN_JAVA)) {
            allProduction = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(P7S1BoundaryTest::read)
                    .collect(Collectors.joining("\n"));
        }
        var gameTestMarker = ANNOTATION_PREFIX + "Game" + "Test(";

        assertEquals(0, occurrences(p7Source, gameTestMarker));
        assertEquals(19, occurrences(allProduction, gameTestMarker));
        assertFalse(PLATFORM_GAME_TEST_SURFACE.matcher(p7Source).find());
    }

    private static void assertOutsidePackageCannotNameProductTypes() {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required for the external API boundary probe");
        var declarations = PRODUCT_TYPES_BY_PATH.values().stream()
                .map(type -> "    " + type.getName() + " value" + type.getSimpleName() + ";")
                .sorted()
                .collect(Collectors.joining("\n"));
        var probeSource = "package com.yo1no.gramarye.magic.network.externalprobe;\n"
                + "final class P7ExternalAccessProbe {\n"
                + declarations
                + "\n}\n";
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject probe = new SimpleJavaFileObject(
                URI.create("string:///com/yo1no/gramarye/magic/network/externalprobe/"
                        + "P7ExternalAccessProbe.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return probeSource;
            }
        };
        var options = List.of(
                "-classpath", System.getProperty("java.class.path"), "-proc:none", "-Xlint:none");
        var compiled = compiler.getTask(
                        null, null, diagnostics, options, null, List.of(probe))
                .call();
        var errorText = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        assertFalse(Boolean.TRUE.equals(compiled), "outside package unexpectedly named P7 types");
        PRODUCT_TYPES_BY_PATH.values().forEach(type -> assertTrue(
                errorText.contains(type.getSimpleName()),
                () -> "external compiler did not reject " + type.getName() + ": " + errorText));
    }

    private static List<Class<?>> allProductTypes() {
        return PRODUCT_TYPES_BY_PATH.values().stream()
                .flatMap(P7S1BoundaryTest::typeAndNestedTypes)
                .distinct()
                .toList();
    }

    private static Stream<Class<?>> typeAndNestedTypes(Class<?> type) {
        return Stream.concat(
                Stream.of(type),
                Arrays.stream(type.getDeclaredClasses())
                        .flatMap(P7S1BoundaryTest::typeAndNestedTypes));
    }

    private static boolean isPublicOrProtected(Class<?> type) {
        return isPublicOrProtected(type.getModifiers());
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return java.lang.reflect.Modifier.isPublic(modifiers)
                || java.lang.reflect.Modifier.isProtected(modifiers);
    }

    private static boolean isLanguageRequiredSurface(java.lang.reflect.Method method) {
        if (isObjectEqualsOverride(method)
                || method.getName().equals("hashCode")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == int.class
                || method.getName().equals("toString")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == String.class) {
            return true;
        }
        if (method.getDeclaringClass().isEnum()) {
            return method.getName().equals("values") && method.getParameterCount() == 0
                    || method.getName().equals("valueOf")
                            && Arrays.equals(method.getParameterTypes(), new Class<?>[] {String.class});
        }
        if (!method.getDeclaringClass().isRecord() || method.getParameterCount() != 0) {
            return false;
        }
        return Arrays.stream(method.getDeclaringClass().getRecordComponents())
                .anyMatch(component -> component.getName().equals(method.getName())
                        && component.getType() == method.getReturnType());
    }

    private static boolean isObjectEqualsOverride(java.lang.reflect.Method method) {
        return method.getName().equals("equals")
                && method.getReturnType() == boolean.class
                && Arrays.equals(method.getParameterTypes(), new Class<?>[] {Object.class});
    }

    private static boolean isLiveObjectType(Class<?> type) {
        var name = type.getName();
        return name.startsWith("net.minecraft.")
                || name.startsWith("net.neoforged.")
                || name.startsWith("io.netty.")
                || LIVE_OBJECT_SIMPLE_NAMES.contains(type.getSimpleName());
    }

    private static boolean isUnboundedContainer(Class<?> type) {
        return type.isArray()
                || Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
                || Queue.class.isAssignableFrom(type)
                || Iterable.class.isAssignableFrom(type)
                || java.util.Iterator.class.isAssignableFrom(type)
                || java.util.stream.BaseStream.class.isAssignableFrom(type);
    }

    private static boolean mentionsObject(String typeName) {
        return typeName.equals("java.lang.Object")
                || typeName.contains("java.lang.Object<")
                || typeName.contains("<java.lang.Object")
                || typeName.contains("java.lang.Object[");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("unable to inspect " + path, exception);
        }
    }

    private static String portablePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
