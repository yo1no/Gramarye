package com.yo1no.gramarye.magic.runtime.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class EffectSemanticBoundaryTest {
    private static final Path PRODUCTION_ROOT = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/runtime/effect");
    private static final List<Class<?>> PRODUCTION_TYPES = List.of(
            P6EffectBounds.class,
            EffectRequest.class,
            EffectRequestId.class,
            SourceEventId.class,
            DamageTargetReference.class,
            CompensationPolicy.class,
            DamageEffectRequest.class,
            EffectStep.class,
            EffectStepKind.class,
            DamageEffectStep.class,
            EffectCommitPlan.class,
            EffectStepOutcomeKind.class,
            EffectStepOutcome.class,
            EffectTraceStage.class,
            EffectTraceEntry.class,
            EffectTrace.class,
            EffectTerminalStatus.class,
            EffectRejectReason.class,
            EffectFailureReason.class,
            EffectExecutionResult.class,
            EffectResolutionKind.class,
            EffectResolution.class,
            AcceptedEffectResolution.class,
            RejectedEffectResolution.class,
            EffectResolver.class,
            DamageEffectResolver.class,
            EffectGuardDecision.class,
            EffectGuardPointKind.class,
            EffectGuardPoint.class,
            EffectExecutionGuard.class,
            DamageEffectCommitPort.class,
            P6ExecutionInvariantCode.class,
            P6ExecutionInvariantException.class,
            EffectExecutionEngine.class,
            EffectMutationAccumulator.class);

    @Test
    void productionHasNoPublicTopLevelOrProtectedIntegrationSurface() {
        for (Class<?> type : PRODUCTION_TYPES) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
            Stream.concat(
                            Stream.concat(
                                    Stream.of(type.getDeclaredFields()),
                                    Stream.of(type.getDeclaredMethods())),
                            Stream.of(type.getDeclaredConstructors()))
                    .forEach(member -> assertFalse(
                            Modifier.isProtected(member.getModifiers()), member.toString()));
        }
    }

    @Test
    void productionSourceDiscoveryFindsNoPublicTopLevelOrProtectedDeclaration()
            throws IOException {
        String source = allProductionSource();
        assertFalse(java.util.regex.Pattern.compile(
                        "(?m)^public\\s+(?:(?:final|sealed|non-sealed)\\s+)?"
                                + "(?:class|interface|record|enum)\\s+")
                .matcher(source)
                .find());
        assertFalse(java.util.regex.Pattern.compile("\\bprotected\\b")
                .matcher(source)
                .find());
    }

    @Test
    void productionSourceHasNoPlatformConcurrencyReflectionRandomIoOrNetworkReference()
            throws IOException {
        String source = allProductionSource();
        for (String forbidden : List.of(
                "net.minecraft",
                "net.neoforged",
                "java.util.concurrent",
                "java.lang.reflect",
                "java.io.",
                "java.nio.file",
                "java.net.",
                "Thread",
                "Executor",
                "Future",
                "CompletableFuture",
                "ForkJoin",
                "parallelStream",
                "ThreadLocal",
                "synchronized",
                "System.currentTimeMillis",
                "System.nanoTime",
                "Random",
                "RandomSource",
                "Math.random",
                "ServiceLoader",
                "Class.forName",
                "Method.invoke")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        for (String forbiddenCall : List.of("wait", "notify", "sleep")) {
            assertFalse(java.util.regex.Pattern.compile(
                            "\\b" + forbiddenCall + "\\s*\\(")
                    .matcher(source)
                    .find(), forbiddenCall);
        }
        assertFalse(
                java.util.regex.Pattern.compile("\\bObject\\b").matcher(source).find(),
                "generic Object type");
    }

    @Test
    void productionFieldsRetainNoLiveObjectThrowableOrMutableStaticState() {
        Set<String> forbiddenFieldTypes = Set.of(
                "java.lang.Object",
                "net.minecraft.world.entity.Entity",
                "net.minecraft.world.level.Level",
                "net.minecraft.server.MinecraftServer");
        for (Class<?> type : PRODUCTION_TYPES) {
            Stream.of(type.getDeclaredFields()).forEach(field -> {
                assertFalse(forbiddenFieldTypes.contains(field.getType().getName()), field.toString());
                assertFalse(Throwable.class.isAssignableFrom(field.getType()), field.toString());
                if (Modifier.isStatic(field.getModifiers())) {
                    assertTrue(Modifier.isFinal(field.getModifiers()), field.toString());
                }
            });
        }
        Set<Class<?>> retainedEngineTypes = Stream.of(
                        EffectExecutionEngine.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertEquals(
                Set.of(EffectResolver.class, EffectExecutionGuard.class, DamageEffectCommitPort.class),
                retainedEngineTypes);
    }

    @Test
    void productionSourceHasNoRawUncheckedGenericPayloadOrUnorderedMapApi()
            throws IOException {
        String source = allProductionSource();
        for (String forbidden : List.of(
                "@SuppressWarnings",
                "Map<",
                "HashMap",
                "Map.of",
                "Object payload",
                "Object>",
                "<Object",
                "Runnable",
                "Consumer<")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }

    @Test
    void productionImportsMatchPureJdkAllowlist() throws IOException {
        Set<String> allowedImports = Set.of(
                "import java.util.ArrayList;",
                "import java.util.List;",
                "import java.util.Objects;",
                "import java.util.Optional;",
                "import java.util.UUID;");
        for (Path path : productionPaths()) {
            for (String line : Files.readAllLines(path)) {
                if (line.startsWith("import ")) {
                    assertTrue(allowedImports.contains(line), path + ": " + line);
                }
            }
        }
    }

    @Test
    void productionDeclaresExactlyOneCommitPortAndNoImplementation() throws IOException {
        String source = allProductionSource();
        assertEquals(1, occurrences(source, "interface DamageEffectCommitPort"));
        assertEquals(0, occurrences(source, "implements DamageEffectCommitPort"));
    }

    private static String allProductionSource() throws IOException {
        StringBuilder source = new StringBuilder();
        for (Path path : productionPaths()) {
            source.append(Files.readString(path)).append('\n');
        }
        return source.toString();
    }

    private static List<Path> productionPaths() throws IOException {
        try (Stream<Path> paths = Files.list(PRODUCTION_ROOT)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private static Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }
}
