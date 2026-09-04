package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.junit.jupiter.api.Test;

final class P7S2BoundaryTest {
    private static final String PACKAGE_NAME = "com.yo1no.gramarye.magic.network";
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path NETWORK_MAIN = MAIN_JAVA.resolve(
            "com/yo1no/gramarye/magic/network");
    private static final Path NETWORK_TEST = PROJECT_ROOT.resolve(
            "src/test/java/com/yo1no/gramarye/magic/network");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("(?m)^\\s*@Test\\b");
    private static final Pattern UNSUPPORTED_TEST_ANNOTATION = Pattern.compile(
            "(?m)^\\s*@(ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|Disabled\\w*|Enabled\\w*)\\b");

    private static final Set<String> S1_PRODUCTION_PATHS = Set.of(
            "AimHint.java",
            "CastInputKind.java",
            "CastIntent.java",
            "CastIntentAdmissionSemantics.java",
            "CastIntentValidation.java",
            "ConnectionEpochState.java",
            "EntityHint.java",
            "IntentAcknowledgement.java",
            "IntentSequenceState.java",
            "IntentTickBudget.java",
            "IntentTokenBucket.java",
            "P7IntentFailureReason.java",
            "P7NetworkBounds.java",
            "P7SemanticInvariantException.java",
            "P7SessionIdentity.java",
            "PendingPermitAccounting.java",
            "RateStrikeState.java");
    private static final Set<String> S1_TEST_PATHS = Set.of(
            "CastIntentAdmissionSemanticsTest.java",
            "CastIntentTest.java",
            "ConnectionEpochStateTest.java",
            "IntentAcknowledgementTest.java",
            "IntentSequenceStateTest.java",
            "IntentTickBudgetTest.java",
            "IntentTokenBucketTest.java",
            "P7NetworkBoundsTest.java",
            "P7S1BoundaryTest.java",
            "P7SessionIdentityTest.java",
            "PendingPermitAccountingTest.java",
            "RateStrikeStateTest.java");

    private static final Map<String, Class<?>> S2_PRODUCTION_TYPES = Map.ofEntries(
            Map.entry("CastIntentPayload.java", CastIntentPayload.class),
            Map.entry("CooldownSnapshotEntry.java", CooldownSnapshotEntry.class),
            Map.entry("IntentAckPayload.java", IntentAckPayload.class),
            Map.entry("P7CastIntentNetworkHandler.java", P7CastIntentNetworkHandler.class),
            Map.entry("P7ClientMirrorDispatchPort.java", P7ClientMirrorDispatchPort.class),
            Map.entry("P7ClientPayloadHandlers.java", P7ClientPayloadHandlers.class),
            Map.entry(
                    "P7ConnectionEpochSnapshotSource.java",
                    P7ConnectionEpochSnapshotSource.class),
            Map.entry("P7CooldownDispatchTask.java", P7CooldownDispatchTask.class),
            Map.entry("P7IntentAckDispatchTask.java", P7IntentAckDispatchTask.class),
            Map.entry("P7ManaDispatchTask.java", P7ManaDispatchTask.class),
            Map.entry("P7NetworkComposition.java", P7NetworkComposition.class),
            Map.entry("P7PayloadCodecSupport.java", P7PayloadCodecSupport.class),
            Map.entry("P7PayloadRegistrar.java", P7PayloadRegistrar.class),
            Map.entry("P7PendingPermit.java", P7PendingPermit.class),
            Map.entry("P7PendingPermitOwner.java", P7PendingPermitOwner.class),
            Map.entry("P7QueuedCastIntent.java", P7QueuedCastIntent.class),
            Map.entry("P7ServerDispatchTask.java", P7ServerDispatchTask.class),
            Map.entry("P7ServerIntentDispatchPort.java", P7ServerIntentDispatchPort.class),
            Map.entry("PlayerManaSnapshot.java", PlayerManaSnapshot.class),
            Map.entry("PlayerManaSyncPayload.java", PlayerManaSyncPayload.class),
            Map.entry("SkillCooldownSnapshot.java", SkillCooldownSnapshot.class),
            Map.entry("SkillCooldownSyncPayload.java", SkillCooldownSyncPayload.class));

    private static final Set<String> S2_TEST_PATHS = Set.of(
            "CastIntentPayloadCodecTest.java",
            "IntentAckPayloadCodecTest.java",
            "P7CastIntentNetworkHandlerTest.java",
            "P7ClientPayloadHandlersTest.java",
            "P7PayloadRegistrarTest.java",
            "P7PendingPermitOwnerTest.java",
            "P7QueuedTaskRetentionTest.java",
            "P7RecordingPayloadContext.java",
            "P7S2BoundaryTest.java",
            "P7S2CodecTestSupport.java",
            "P7S2DedicatedRegistrationTest.java",
            "PlayerManaSyncPayloadCodecTest.java",
            "SkillCooldownSyncPayloadCodecTest.java");
    private static final List<Class<?>> S2_TEST_CLASSES = List.of(
            CastIntentPayloadCodecTest.class,
            IntentAckPayloadCodecTest.class,
            P7CastIntentNetworkHandlerTest.class,
            P7ClientPayloadHandlersTest.class,
            P7PayloadRegistrarTest.class,
            P7PendingPermitOwnerTest.class,
            P7QueuedTaskRetentionTest.class,
            P7S2BoundaryTest.class,
            P7S2DedicatedRegistrationTest.class,
            PlayerManaSyncPayloadCodecTest.class,
            SkillCooldownSyncPayloadCodecTest.class);

    @Test
    void exactS2ProductionPathAndTopLevelTypeInventoryIsClosed() throws IOException {
        var allNetworkPaths = javaFileNames(NETWORK_MAIN);
        var actualS2Paths = allNetworkPaths.stream()
                .filter(path -> !S1_PRODUCTION_PATHS.contains(path))
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(17, S1_PRODUCTION_PATHS.size());
        assertEquals(22, S2_PRODUCTION_TYPES.size());
        assertEquals(S2_PRODUCTION_TYPES.keySet(), actualS2Paths);
        assertTrue(allNetworkPaths.containsAll(S1_PRODUCTION_PATHS));
        S2_PRODUCTION_TYPES.forEach((path, type) -> {
            assertEquals(PACKAGE_NAME, type.getPackageName(), path);
            assertEquals(type.getSimpleName() + ".java", path);
            assertFalse(java.lang.reflect.Modifier.isPublic(type.getModifiers()), path);
            assertFalse(java.lang.reflect.Modifier.isProtected(type.getModifiers()), path);
            assertTrue(type.isInterface()
                    || java.lang.reflect.Modifier.isFinal(type.getModifiers()), path);
        });
    }

    @Test
    void exactS2TestInventoryUsesOnlyOrdinaryEnabledZeroArgumentTests()
            throws IOException {
        var allNetworkTests = javaFileNames(NETWORK_TEST);
        var actualS2Tests = allNetworkTests.stream()
                .filter(path -> !S1_TEST_PATHS.contains(path))
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(12, S1_TEST_PATHS.size());
        assertEquals(S2_TEST_PATHS, actualS2Tests);
        assertEquals(11, S2_TEST_CLASSES.size());
        for (var type : S2_TEST_CLASSES) {
            var source = read(NETWORK_TEST.resolve(type.getSimpleName() + ".java"));
            var methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Test.class))
                    .toList();
            assertFalse(methods.isEmpty(), type.getSimpleName());
            assertEquals(TEST_ANNOTATION.matcher(source).results().count(), methods.size());
            assertTrue(methods.stream().allMatch(method ->
                    method.getParameterCount() == 0 && method.getReturnType() == void.class));
            assertFalse(UNSUPPORTED_TEST_ANNOTATION.matcher(source).find());
            assertFalse(source.contains("@Game" + "Test"));
        }
        assertEquals(0, TEST_ANNOTATION.matcher(
                read(NETWORK_TEST.resolve("P7RecordingPayloadContext.java"))).results().count());
        assertEquals(0, TEST_ANNOTATION.matcher(
                read(NETWORK_TEST.resolve("P7S2CodecTestSupport.java"))).results().count());
    }

    @Test
    void packagePrivateS2TopLevelsExposeNoEffectiveExternalApi() {
        assertTrue(S2_PRODUCTION_TYPES.values().stream().allMatch(type ->
                !java.lang.reflect.Modifier.isPublic(type.getModifiers())
                        && !java.lang.reflect.Modifier.isProtected(type.getModifiers())));
        assertOutsidePackageCannotNameS2Types();
    }

    @Test
    void fourPayloadsAndFourCodecsAreTheOnlyS2PayloadSurface() {
        var payloadTypes = S2_PRODUCTION_TYPES.values().stream()
                .filter(CustomPacketPayload.class::isAssignableFrom)
                .collect(Collectors.toUnmodifiableSet());
        var codecFields = S2_PRODUCTION_TYPES.values().stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> StreamCodec.class.isAssignableFrom(field.getType()))
                .toList();
        var typeFields = payloadTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(field -> CustomPacketPayload.Type.class.isAssignableFrom(
                        field.getType()))
                .toList();

        assertEquals(Set.of(
                CastIntentPayload.class,
                IntentAckPayload.class,
                PlayerManaSyncPayload.class,
                SkillCooldownSyncPayload.class), payloadTypes);
        assertEquals(4, codecFields.size());
        assertEquals(4, typeFields.size());
        assertTrue(Stream.concat(codecFields.stream(), typeFields.stream()).allMatch(field ->
                java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        && java.lang.reflect.Modifier.isFinal(field.getModifiers())
                        && !java.lang.reflect.Modifier.isPublic(field.getModifiers())
                        && !java.lang.reflect.Modifier.isProtected(field.getModifiers())));
        payloadTypes.forEach(type -> {
            var valueFields = Arrays.stream(type.getDeclaredFields())
                    .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                    .toList();
            assertEquals(1, valueFields.size(), type.getSimpleName());
            assertTrue(valueFields.stream().allMatch(field ->
                    java.lang.reflect.Modifier.isPrivate(field.getModifiers())
                            && java.lang.reflect.Modifier.isFinal(field.getModifiers())));
            assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
                    !java.lang.reflect.Modifier.isPublic(constructor.getModifiers())
                            && !java.lang.reflect.Modifier.isProtected(
                                    constructor.getModifiers())));
        });
    }

    @Test
    void compositionHasOnlyFourPrivateFinalDependenciesAndOneStagedSingleton() {
        var instanceFields = Arrays.stream(P7NetworkComposition.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .toList();
        var production = P7NetworkComposition.production();
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000701");

        assertEquals(Set.of(
                P7ConnectionEpochSnapshotSource.class,
                P7PendingPermitOwner.class,
                P7ServerIntentDispatchPort.class,
                P7ClientMirrorDispatchPort.class), instanceFields.stream()
                .map(java.lang.reflect.Field::getType)
                .collect(Collectors.toSet()));
        assertTrue(instanceFields.stream().allMatch(field ->
                java.lang.reflect.Modifier.isPrivate(field.getModifiers())
                        && java.lang.reflect.Modifier.isFinal(field.getModifiers())));
        assertSame(production, P7NetworkComposition.production());
        assertTrue(production.connectionEpochSource().currentEpoch(playerId).isEmpty());
        assertEquals(0, production.pendingPermitOwner().serverPending());
        production.serverIntentDispatchPort().dispatch(new P7QueuedCastIntent(
                playerId, 1, validIntent()));
        production.clientMirrorDispatchPort().onIntentAcknowledgement(
                new IntentAcknowledgement(
                        1,
                        IntentAcknowledgement.Disposition.ACCEPTED,
                        IntentAcknowledgement.SEQUENCE_CONSUMED,
                        null));
        production.clientMirrorDispatchPort().onPlayerManaSnapshot(
                new PlayerManaSnapshot(
                        1, PlayerManaSnapshot.Availability.UNAVAILABLE, 0));
        production.clientMirrorDispatchPort().onSkillCooldownSnapshot(
                new SkillCooldownSnapshot(1, List.of()));
        assertEquals(0, production.pendingPermitOwner().serverPending());
    }

    @Test
    void queuedAndDispatchTypesRetainOnlyReviewedTypedState() {
        assertFieldTypes(P7QueuedCastIntent.class,
                Set.of(UUID.class, long.class, CastIntent.class));
        assertFieldTypes(P7ServerDispatchTask.class,
                Set.of(
                        P7QueuedCastIntent.class,
                        P7ServerIntentDispatchPort.class,
                        P7PendingPermit.class));
        assertFieldTypes(P7IntentAckDispatchTask.class,
                Set.of(IntentAcknowledgement.class, P7ClientMirrorDispatchPort.class));
        assertFieldTypes(P7ManaDispatchTask.class,
                Set.of(PlayerManaSnapshot.class, P7ClientMirrorDispatchPort.class));
        assertFieldTypes(P7CooldownDispatchTask.class,
                Set.of(SkillCooldownSnapshot.class, P7ClientMirrorDispatchPort.class));

        var retainedTypes = List.of(
                        P7QueuedCastIntent.class,
                        P7ServerDispatchTask.class,
                        P7IntentAckDispatchTask.class,
                        P7ManaDispatchTask.class,
                        P7CooldownDispatchTask.class)
                .stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(field -> field.getGenericType().getTypeName())
                .toList();
        for (var forbidden : List.of(
                "IPayloadContext",
                "ServerPlayer",
                "ByteBuf",
                "Connection",
                "Entity",
                "Level",
                "Throwable")) {
            assertTrue(retainedTypes.stream().noneMatch(name -> name.contains(forbidden)),
                    forbidden);
        }
    }

    @Test
    void productionSourceHasNoS3SendClientWorkerOrUnsafeGenericSurface() {
        var combined = S2_PRODUCTION_TYPES.keySet().stream()
                .map(NETWORK_MAIN::resolve)
                .map(P7S2BoundaryTest::read)
                .collect(Collectors.joining("\n"));
        var c2sHandler = read(NETWORK_MAIN.resolve("P7CastIntentNetworkHandler.java"));

        for (var forbidden : List.of(
                "net.minecraft.client",
                "PacketDistributor",
                "PayloadPacket",
                "sendToPlayer(",
                "sendToServer(",
                "sendToAllPlayers(",
                ".send(",
                ".reply(",
                "SkillRuntimeService",
                "P6RuntimeExecutionBridge",
                "P7ManaSnapshotBridge",
                "ServiceLoader",
                "java.lang.reflect",
                "java.lang.invoke",
                "MethodHandle",
                "ThreadLocal",
                "ExecutorService",
                "CompletableFuture",
                "parallelStream(",
                ".parallel(",
                "new Thread(",
                "System.currentTimeMillis(",
                "System.nanoTime(",
                "Math.random(",
                "UUID.randomUUID(")) {
            assertFalse(combined.contains(forbidden), forbidden);
        }
        for (var forbidden : List.of(
                "IntentSequenceState",
                "IntentTokenBucket",
                "IntentTickBudget",
                "RateStrikeState",
                "CastIntentAdmissionSemantics",
                "SkillRuntimeService",
                "P6RuntimeExecutionBridge")) {
            assertFalse(c2sHandler.contains(forbidden), forbidden);
        }
    }

    @Test
    void S1SourcesRemainPlatformFreeAndGameTestCountRemainsNineteen()
            throws IOException {
        var s1Source = S1_PRODUCTION_PATHS.stream()
                .map(NETWORK_MAIN::resolve)
                .map(P7S2BoundaryTest::read)
                .collect(Collectors.joining("\n"));
        String allProduction;
        try (var paths = Files.walk(MAIN_JAVA)) {
            allProduction = paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(P7S2BoundaryTest::read)
                    .collect(Collectors.joining("\n"));
        }

        assertFalse(s1Source.contains("net.minecraft."));
        assertFalse(s1Source.contains("net.neoforged."));
        assertFalse(s1Source.contains("CustomPacketPayload"));
        assertFalse(s1Source.contains("StreamCodec"));
        assertFalse(s1Source.contains("PayloadRegistrar"));
        assertEquals(19, occurrences(allProduction, "@Game" + "Test("));
    }

    private static CastIntent validIntent() {
        return CastIntentValidation.validate(1, 0, 0, 0, null, null, null, null)
                .intent()
                .orElseThrow();
    }

    private static void assertFieldTypes(Class<?> type, Set<Class<?>> expected) {
        var fields = Arrays.stream(type.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .toList();
        assertEquals(expected, fields.stream()
                .map(java.lang.reflect.Field::getType)
                .collect(Collectors.toSet()), type.getSimpleName());
        assertEquals(expected.size(), fields.size(), type.getSimpleName());
        assertTrue(fields.stream().allMatch(field ->
                java.lang.reflect.Modifier.isPrivate(field.getModifiers())
                        && java.lang.reflect.Modifier.isFinal(field.getModifiers())));
    }

    private static void assertOutsidePackageCannotNameS2Types() {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var declarations = S2_PRODUCTION_TYPES.values().stream()
                .map(type -> "    " + type.getName() + " value" + type.getSimpleName() + ";")
                .sorted()
                .collect(Collectors.joining("\n"));
        var source = "package com.yo1no.gramarye.magic.network.externalprobe;\n"
                + "final class P7S2ExternalAccessProbe {\n"
                + declarations
                + "\n}\n";
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        JavaFileObject probe = new SimpleJavaFileObject(
                URI.create("string:///com/yo1no/gramarye/magic/network/externalprobe/"
                        + "P7S2ExternalAccessProbe.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        var options = List.of(
                "-classpath", System.getProperty("java.class.path"), "-proc:none", "-Xlint:none");
        var compiled = compiler.getTask(
                        null, null, diagnostics, options, null, List.of(probe))
                .call();
        var errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(Object::toString)
                .collect(Collectors.joining("\n"));

        assertFalse(Boolean.TRUE.equals(compiled));
        S2_PRODUCTION_TYPES.values().forEach(type -> assertTrue(
                errors.contains(type.getSimpleName()),
                () -> "external compiler did not reject " + type.getName() + ": " + errors));
    }

    private static Set<String> javaFileNames(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
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
