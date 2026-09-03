package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P6EffectVocabularyTest {
    @Test
    void boundsMatchP6A1Exactly() {
        assertArrayEquals(
                new long[] {1, 1, 8, 8, 2, 32, 1_000_000L, 1_000_000_000L,
                        1_000_000_000L, 10, 32},
                new long[] {
                    P6EffectBounds.MAX_EFFECT_REQUESTS_PER_EXECUTION,
                    P6EffectBounds.MAX_TARGETS_PER_REQUEST,
                    P6EffectBounds.MAX_COMMIT_STEPS_PER_PLAN,
                    P6EffectBounds.MAX_PRIMARY_WORLD_MUTATIONS_PER_EXECUTION,
                    P6EffectBounds.MAX_MANA_MUTATIONS_PER_EXECUTION,
                    P6EffectBounds.MAX_TRACE_ENTRIES,
                    P6EffectBounds.MAX_EFFECT_MAGNITUDE,
                    P6EffectBounds.MAX_MANA_VALUE,
                    P6EffectBounds.MAX_MANA_OPERATION_AMOUNT,
                    P6EffectBounds.MAX_DEADLINE_CHECKS_PER_EXECUTION,
                    P6EffectBounds.MAX_CHILD_INTENTS_PER_EXECUTION
                });
    }

    @Test
    void effectRequestCardinalityIsStructurallyExactlyOne() {
        Set<Class<?>> outcomeTypes = Set.copyOf(
                Arrays.asList(ActionExecutorOutcome.class.getPermittedSubclasses()));
        var producedComponents = ProducedActionRequest.class.getRecordComponents();
        var producedFields = Arrays.stream(ProducedActionRequest.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var outcomeConstructors = outcomeTypes.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredConstructors()))
                .toList();
        var outcomeFactories = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(ActionExecutorOutcome.class),
                        outcomeTypes.stream())
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> ActionExecutorOutcome.class.isAssignableFrom(
                        method.getReturnType()))
                .toList();

        long requestComponents = Arrays.stream(producedComponents)
                .filter(component -> EffectRequest.class.isAssignableFrom(component.getType()))
                .count();
        long requestFields = producedFields.stream()
                .filter(field -> EffectRequest.class.isAssignableFrom(field.getType()))
                .count();
        boolean pluralRequestCarrier = Arrays.stream(producedComponents)
                .anyMatch(component -> isSecondaryCarrier(
                        component.getGenericType(), EffectRequest.class))
                || producedFields.stream()
                        .anyMatch(field -> isSecondaryCarrier(
                                field.getGenericType(), EffectRequest.class))
                || outcomeConstructors.stream().anyMatch(constructor ->
                        constructor.isVarArgs()
                                || Arrays.stream(constructor.getGenericParameterTypes())
                                        .anyMatch(type -> isSecondaryCarrier(
                                                type, EffectRequest.class)))
                || outcomeFactories.stream().anyMatch(factory ->
                        factory.isVarArgs()
                                || Arrays.stream(factory.getGenericParameterTypes())
                                        .anyMatch(type -> isSecondaryCarrier(
                                                type, EffectRequest.class)));
        long maximumConstructorRequestParameters = outcomeConstructors.stream()
                .mapToLong(constructor -> Arrays.stream(constructor.getParameterTypes())
                        .filter(EffectRequest.class::isAssignableFrom)
                        .count())
                .max()
                .orElse(0L);
        long maximumFactoryRequestParameters = outcomeFactories.stream()
                .mapToLong(factory -> Arrays.stream(factory.getParameterTypes())
                        .filter(EffectRequest.class::isAssignableFrom)
                        .count())
                .max()
                .orElse(0L);

        AtomicInteger executorCalls = new AtomicInteger();
        ActionExecutor countingExecutor = input -> {
            assertEquals(1, executorCalls.incrementAndGet());
            return new DamageActionExecutor().execute(input);
        };
        AtomicInteger resolverCalls = new AtomicInteger();
        EffectRequest[] consumedRequest = new EffectRequest[1];
        EffectResolver resolver = (request, capacity) -> {
            assertEquals(1, resolverCalls.incrementAndGet());
            assertTrue(consumedRequest[0] == null);
            consumedRequest[0] = request;
            return new DamageEffectResolver().resolve(request, capacity);
        };
        DamageActionInvocation invocation = ActionTransactionTestFixtures.invocation();
        ActionDamageTransactionResult result = ActionTransactionTestFixtures.engine(
                        ActionTransactionTestFixtures.registry(countingExecutor), resolver)
                .execute(
                        invocation,
                        new ActionTransactionTestFixtures.RecordingManaAccount(100),
                        0,
                        ActionTransactionTestFixtures.TransactionRecordingGuard.allowing(),
                        ActionTransactionTestFixtures.TransactionRecordingPort.applyingAll());

        assertAll(
                () -> assertTrue(ActionExecutorOutcome.class.isSealed()),
                () -> assertEquals(
                        Set.of(ProducedActionRequest.class, NoActionRequest.class),
                        outcomeTypes),
                () -> assertEquals(1, producedComponents.length),
                () -> assertEquals("request", producedComponents[0].getName()),
                () -> assertEquals(DamageEffectRequest.class,
                        producedComponents[0].getType()),
                () -> assertEquals(1, producedFields.size()),
                () -> assertEquals("request", producedFields.getFirst().getName()),
                () -> assertEquals(DamageEffectRequest.class,
                        producedFields.getFirst().getType()),
                () -> assertEquals(1L, requestComponents),
                () -> assertEquals(1L, requestFields),
                () -> assertFalse(pluralRequestCarrier),
                () -> assertEquals(1L, maximumConstructorRequestParameters),
                () -> assertTrue(maximumFactoryRequestParameters <= 1L),
                () -> assertFalse(ProducedActionRequest.class
                        .getDeclaredConstructor(DamageEffectRequest.class)
                        .isVarArgs()),
                () -> assertThrows(
                        NoSuchMethodException.class,
                        () -> ProducedActionRequest.class.getDeclaredConstructor(
                                DamageEffectRequest.class,
                                DamageEffectRequest.class),
                        "STRUCTURALLY_UNREPRESENTABLE_ONE_OVER"),
                () -> assertEquals(0L,
                        Arrays.stream(NoActionRequest.class.getDeclaredFields())
                                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                                .count()),
                () -> assertEquals(1, ActionExecutor.class.getDeclaredMethods().length),
                () -> assertEquals(ActionExecutorOutcome.class,
                        ActionExecutor.class.getDeclaredMethods()[0].getReturnType()),
                () -> assertEquals(1, executorCalls.get()),
                () -> assertEquals(1, resolverCalls.get()),
                () -> assertEquals(EffectTerminalStatus.SUCCEEDED,
                        result.effectResult().status()),
                () -> assertTrue(consumedRequest[0] instanceof DamageEffectRequest),
                () -> assertSame(invocation.target(),
                        ((DamageEffectRequest) consumedRequest[0]).target()),
                () -> assertEquals(1L, P6EffectBounds.MAX_EFFECT_REQUESTS_PER_EXECUTION),
                () -> assertEquals(
                        "STRUCTURALLY_UNREPRESENTABLE_ONE_OVER",
                        requestComponents == 1
                                        && requestFields == 1
                                        && !pluralRequestCarrier
                                        && maximumConstructorRequestParameters <= 1
                                        && maximumFactoryRequestParameters <= 1
                                ? "STRUCTURALLY_UNREPRESENTABLE_ONE_OVER"
                                : "REPRESENTABLE_ONE_OVER"));
    }

    @Test
    void closedVocabulariesHaveExactValues() {
        assertArrayEquals(
                new EffectTerminalStatus[] {
                    EffectTerminalStatus.SUCCEEDED,
                    EffectTerminalStatus.REJECTED,
                    EffectTerminalStatus.FAILED,
                    EffectTerminalStatus.PARTIALLY_SUCCEEDED,
                    EffectTerminalStatus.COMPENSATED,
                    EffectTerminalStatus.COMPENSATION_FAILED
                },
                EffectTerminalStatus.values());
        assertArrayEquals(
                new EffectStepOutcomeKind[] {
                    EffectStepOutcomeKind.APPLIED,
                    EffectStepOutcomeKind.NOT_APPLIED,
                    EffectStepOutcomeKind.APPLIED_WITH_FAILURE
                },
                EffectStepOutcomeKind.values());
        assertArrayEquals(
                new EffectGuardDecision[] {
                    EffectGuardDecision.ALLOWED,
                    EffectGuardDecision.CANCELLED,
                    EffectGuardDecision.DEADLINE_EXCEEDED
                },
                EffectGuardDecision.values());
        assertEquals(1, CompensationPolicy.values().length);
        assertEquals(
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION,
                CompensationPolicy.values()[0]);
    }

    @Test
    void rejectFailureResolutionAndTraceVocabulariesAreExact() {
        assertArrayEquals(
                new EffectRejectReason[] {
                    EffectRejectReason.UNSUPPORTED_ACTION,
                    EffectRejectReason.INVALID_REQUEST,
                    EffectRejectReason.INVALID_TARGET,
                    EffectRejectReason.TARGET_UNAVAILABLE,
                    EffectRejectReason.INSUFFICIENT_MANA,
                    EffectRejectReason.MANA_STATE_UNAVAILABLE,
                    EffectRejectReason.BOUND_EXCEEDED,
                    EffectRejectReason.CANCELLED,
                    EffectRejectReason.DEADLINE_EXCEEDED,
                    EffectRejectReason.COMMIT_PORT_UNAVAILABLE
                },
                EffectRejectReason.values());
        assertArrayEquals(
                new EffectFailureReason[] {
                    EffectFailureReason.PRIMARY_STEP_NOT_APPLIED,
                    EffectFailureReason.PRIMARY_STEP_APPLIED_WITH_FAILURE,
                    EffectFailureReason.EXECUTION_CANCELLED,
                    EffectFailureReason.EXECUTION_DEADLINE_EXCEEDED,
                    EffectFailureReason.COMPENSATION_REFUND_FAILED
                },
                EffectFailureReason.values());
        assertArrayEquals(
                new EffectResolutionKind[] {
                    EffectResolutionKind.ACCEPTED,
                    EffectResolutionKind.REJECTED
                },
                EffectResolutionKind.values());
        assertArrayEquals(
                new EffectTraceStage[] {
                    EffectTraceStage.REQUEST_VALIDATED,
                    EffectTraceStage.TARGET_RESOLVED,
                    EffectTraceStage.MANA_DEBITED,
                    EffectTraceStage.STEP_APPLIED,
                    EffectTraceStage.STEP_NOT_APPLIED,
                    EffectTraceStage.STEP_APPLIED_WITH_FAILURE,
                    EffectTraceStage.REFUND_APPLIED,
                    EffectTraceStage.REFUND_FAILED,
                    EffectTraceStage.TERMINAL_REJECTED,
                    EffectTraceStage.TERMINAL_SUCCEEDED,
                    EffectTraceStage.TERMINAL_FAILED,
                    EffectTraceStage.TERMINAL_PARTIAL,
                    EffectTraceStage.TERMINAL_COMPENSATED,
                    EffectTraceStage.TERMINAL_COMPENSATION_FAILED
                },
                EffectTraceStage.values());
    }

    private static boolean isSecondaryCarrier(Type type, Class<?> elementType) {
        if (type instanceof Class<?> rawType) {
            if (elementType.isAssignableFrom(rawType)) {
                return false;
            }
            if (rawType.isArray()) {
                return containsElement(rawType.getComponentType(), elementType);
            }
            return Iterable.class.isAssignableFrom(rawType)
                    || java.util.Map.class.isAssignableFrom(rawType);
        }
        return containsElement(type, elementType);
    }

    private static boolean containsElement(Type type, Class<?> elementType) {
        if (type instanceof Class<?> rawType) {
            return elementType.isAssignableFrom(rawType)
                    || (rawType.isArray()
                            && containsElement(rawType.getComponentType(), elementType));
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return Arrays.stream(parameterizedType.getActualTypeArguments())
                    .anyMatch(argument -> containsElement(argument, elementType));
        }
        if (type instanceof GenericArrayType arrayType) {
            return containsElement(arrayType.getGenericComponentType(), elementType);
        }
        if (type instanceof WildcardType wildcardType) {
            return Arrays.stream(wildcardType.getUpperBounds())
                            .anyMatch(bound -> containsElement(bound, elementType))
                    || Arrays.stream(wildcardType.getLowerBounds())
                            .anyMatch(bound -> containsElement(bound, elementType));
        }
        if (type instanceof TypeVariable<?> variable) {
            return Arrays.stream(variable.getBounds())
                    .anyMatch(bound -> containsElement(bound, elementType));
        }
        throw new AssertionError("unsupported reflective type: " + type.getTypeName());
    }
}
