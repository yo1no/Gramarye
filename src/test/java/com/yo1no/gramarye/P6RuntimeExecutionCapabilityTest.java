package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge;
import com.yo1no.gramarye.magic.runtime.mana.P6RuntimeExecutionBridge.GuardDecision;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class P6RuntimeExecutionCapabilityTest {
    private static final Path MAIN_JAVA = projectRoot().resolve("src/main/java");
    private static final Pattern CAPABILITY_ACQUISITION = Pattern.compile(
            "\\bP6RuntimeExecutionCapability\\s*\\.\\s*forRuntimeAdapter\\s*\\(");

    @Test
    void runtimeAdapterAndRootBootstrapAreTheExactCapabilityAcquirers() throws Exception {
        var accessor = P6RuntimeExecutionCapability.class.getDeclaredMethod(
                "forRuntimeAdapter");
        var callers = new HashSet<String>();
        var callCount = 0L;
        try (var paths = Files.walk(MAIN_JAVA)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".java"))
                    .filter(candidate -> !candidate.equals(MAIN_JAVA.resolve(
                            "com/yo1no/gramarye/P7S4LoginManaGameTests.java")))
                    .toList()) {
                var matches = CAPABILITY_ACQUISITION.matcher(Files.readString(path))
                        .results()
                        .count();
                if (matches > 0) {
                    callers.add(MAIN_JAVA.relativize(path).toString());
                    callCount += matches;
                }
            }
        }
        var exactCallCount = callCount;

        assertAll(
                () -> assertSame(
                        P6RuntimeExecutionCapability.forRuntimeAdapter(),
                        P6RuntimeExecutionCapability.forRuntimeAdapter()),
                () -> assertTrue(Modifier.isStatic(accessor.getModifiers())),
                () -> assertFalse(Modifier.isPublic(accessor.getModifiers())),
                () -> assertFalse(Modifier.isProtected(accessor.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(accessor.getModifiers())),
                () -> assertEquals(
                        P6RuntimeExecutionCapability.class, accessor.getReturnType()),
                () -> assertEquals(
                        Set.of(
                                "com/yo1no/gramarye/Gramarye.java",
                                "com/yo1no/gramarye/P6RuntimeExecutionPortAdapter.java"),
                        callers),
                () -> assertEquals(2, exactCallCount));
    }

    @Test
    void capabilityHasNoPublicConstructorMethodFieldOrInstanceState() {
        var type = P6RuntimeExecutionCapability.class;
        var constructors = type.getDeclaredConstructors();
        var fields = type.getDeclaredFields();
        var methods = type.getDeclaredMethods();

        assertAll(
                () -> assertTrue(Modifier.isPublic(type.getModifiers())),
                () -> assertTrue(Modifier.isFinal(type.getModifiers())),
                () -> assertEquals(1, constructors.length),
                () -> assertTrue(Modifier.isPrivate(constructors[0].getModifiers())),
                () -> assertEquals(1, fields.length),
                () -> assertTrue(Modifier.isPrivate(fields[0].getModifiers())),
                () -> assertTrue(Modifier.isStatic(fields[0].getModifiers())),
                () -> assertTrue(Modifier.isFinal(fields[0].getModifiers())),
                () -> assertEquals(0, Arrays.stream(fields)
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .count()),
                () -> assertEquals(0, Arrays.stream(methods)
                        .filter(method -> Modifier.isPublic(method.getModifiers())
                                || Modifier.isProtected(method.getModifiers()))
                        .count()));
    }

    @Test
    void nullCapabilityFailsBeforeEveryOtherInputAndGuard() {
        var guardCalls = new AtomicInteger();
        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                P6RuntimeExecutionBridge.execute(
                        null,
                        null,
                        null,
                        Long.MIN_VALUE,
                        Long.MIN_VALUE,
                        null,
                        Long.MIN_VALUE,
                        Long.MIN_VALUE,
                        (point, stepIndex) -> {
                            guardCalls.incrementAndGet();
                            return GuardDecision.ALLOWED;
                        }));

        assertAll(
                () -> assertEquals(
                        "com.yo1no.gramarye.magic.runtime.mana."
                                + "P6ExecutionInvariantException",
                        failure.getClass().getName()),
                () -> assertEquals(0, guardCalls.get()));
    }

    @Test
    void validCapabilityRejectsNullActorBeforeGuardOrCoreWork() {
        var guardCalls = new AtomicInteger();
        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                P6RuntimeExecutionBridge.execute(
                        P6RuntimeExecutionCapability.forRuntimeAdapter(),
                        null,
                        null,
                        0,
                        0,
                        null,
                        0,
                        -1,
                        (point, stepIndex) -> {
                            guardCalls.incrementAndGet();
                            return GuardDecision.ALLOWED;
                        }));

        assertAll(
                () -> assertEquals(
                        "P6ExecutionInvariantException",
                        failure.getClass().getSimpleName()),
                () -> assertEquals(0, guardCalls.get()));
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
