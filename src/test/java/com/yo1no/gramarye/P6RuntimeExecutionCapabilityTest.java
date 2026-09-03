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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P6RuntimeExecutionCapabilityTest {
    @Test
    void runtimeAdapterIsTheSoleNormalCapabilityAcquirer() throws Exception {
        var accessor = P6RuntimeExecutionCapability.class.getDeclaredMethod(
                "forRuntimeAdapter");

        assertAll(
                () -> assertSame(
                        P6RuntimeExecutionCapability.forRuntimeAdapter(),
                        P6RuntimeExecutionCapability.forRuntimeAdapter()),
                () -> assertTrue(Modifier.isStatic(accessor.getModifiers())),
                () -> assertFalse(Modifier.isPublic(accessor.getModifiers())),
                () -> assertFalse(Modifier.isProtected(accessor.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(accessor.getModifiers())),
                () -> assertEquals(
                        P6RuntimeExecutionCapability.class, accessor.getReturnType()));
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
}
