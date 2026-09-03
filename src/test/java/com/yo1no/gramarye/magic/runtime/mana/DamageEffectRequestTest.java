package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DamageEffectRequestTest {
    @Test
    void acceptsInclusiveMagnitudeAndManaBounds() {
        assertEquals(1L, EffectTestFixtures.request(1L, 0L).magnitude());
        assertEquals(
                P6EffectBounds.MAX_EFFECT_MAGNITUDE,
                EffectTestFixtures.request(
                        P6EffectBounds.MAX_EFFECT_MAGNITUDE,
                        P6EffectBounds.MAX_MANA_OPERATION_AMOUNT).magnitude());
        assertEquals(0L, EffectTestFixtures.request(1L, 0L).manaCost());
        assertEquals(
                P6EffectBounds.MAX_MANA_OPERATION_AMOUNT,
                EffectTestFixtures.request(
                        1L, P6EffectBounds.MAX_MANA_OPERATION_AMOUNT).manaCost());
    }

    @Test
    void rejectsMagnitudeOutsideInclusiveBound() {
        assertThrows(IllegalArgumentException.class, () -> EffectTestFixtures.request(0L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectTestFixtures.request(
                        P6EffectBounds.MAX_EFFECT_MAGNITUDE + 1L, 0L));
    }

    @Test
    void rejectsManaOutsideInclusiveBound() {
        assertThrows(IllegalArgumentException.class, () -> EffectTestFixtures.request(1L, -1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> EffectTestFixtures.request(
                        1L, P6EffectBounds.MAX_MANA_OPERATION_AMOUNT + 1L));
    }

    @Test
    void rejectsNullIdentitySourceTargetAndCompensationPolicy() {
        DamageEffectRequest valid = EffectTestFixtures.request();
        assertThrows(NullPointerException.class, () -> new DamageEffectRequest(
                null,
                valid.sourceEventId(),
                valid.target(),
                valid.magnitude(),
                valid.manaCost(),
                valid.compensationPolicy()));
        assertThrows(NullPointerException.class, () -> new DamageEffectRequest(
                valid.requestId(),
                null,
                valid.target(),
                valid.magnitude(),
                valid.manaCost(),
                valid.compensationPolicy()));
        assertThrows(NullPointerException.class, () -> new DamageEffectRequest(
                valid.requestId(),
                valid.sourceEventId(),
                null,
                valid.magnitude(),
                valid.manaCost(),
                valid.compensationPolicy()));
        assertThrows(NullPointerException.class, () -> new DamageEffectRequest(
                valid.requestId(),
                valid.sourceEventId(),
                valid.target(),
                valid.magnitude(),
                valid.manaCost(),
                null));
    }

    @Test
    void identityValuesRejectInvalidScalarShape() {
        assertThrows(IllegalArgumentException.class, () -> new EffectRequestId(0L));
        assertThrows(IllegalArgumentException.class, () -> new SourceEventId(-1L));
        assertThrows(NullPointerException.class, () -> new DamageTargetReference(null));
    }

    @Test
    void storesExactlyOneStableTargetWithDeterministicValueEquality() {
        DamageEffectRequest first = EffectTestFixtures.request();
        DamageEffectRequest second = EffectTestFixtures.request();
        assertNotNull(first.target());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1L, P6EffectBounds.MAX_TARGETS_PER_REQUEST);
    }

    @Test
    void damageTargetCardinalityIsStructurallyExactlyOne() {
        var components = DamageEffectRequest.class.getRecordComponents();
        var instanceFields = Arrays.stream(DamageEffectRequest.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var constructors = Arrays.asList(DamageEffectRequest.class.getDeclaredConstructors());
        var factories = Arrays.stream(DamageEffectRequest.class.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> DamageEffectRequest.class.isAssignableFrom(
                        method.getReturnType()))
                .toList();
        long targetComponents = Arrays.stream(components)
                .filter(component -> component.getType() == DamageTargetReference.class)
                .count();
        long targetFields = instanceFields.stream()
                .filter(field -> field.getType() == DamageTargetReference.class)
                .count();
        boolean pluralTargetCarrier = Arrays.stream(components)
                .anyMatch(component -> isSecondaryCarrier(
                        component.getGenericType(), DamageTargetReference.class))
                || instanceFields.stream().anyMatch(field -> isSecondaryCarrier(
                        field.getGenericType(), DamageTargetReference.class))
                || constructors.stream().anyMatch(constructor ->
                        constructor.isVarArgs()
                                || Arrays.stream(constructor.getGenericParameterTypes())
                                        .anyMatch(type -> isSecondaryCarrier(
                                                type, DamageTargetReference.class)))
                || factories.stream().anyMatch(factory ->
                        factory.isVarArgs()
                                || Arrays.stream(factory.getGenericParameterTypes())
                                        .anyMatch(type -> isSecondaryCarrier(
                                                type, DamageTargetReference.class)));
        long maximumConstructorTargets = constructors.stream()
                .mapToLong(constructor -> Arrays.stream(constructor.getParameterTypes())
                        .filter(DamageTargetReference.class::equals)
                        .count())
                .max()
                .orElse(0L);
        long maximumFactoryTargets = factories.stream()
                .mapToLong(factory -> Arrays.stream(factory.getParameterTypes())
                        .filter(DamageTargetReference.class::equals)
                        .count())
                .max()
                .orElse(0L);
        var stepComponents = DamageEffectStep.class.getRecordComponents();
        var stepFields = Arrays.stream(DamageEffectStep.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var stepConstructors = Arrays.asList(DamageEffectStep.class.getDeclaredConstructors());
        var stepFactories = Arrays.stream(DamageEffectStep.class.getDeclaredMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> DamageEffectStep.class.isAssignableFrom(method.getReturnType()))
                .toList();
        long stepTargetComponents = Arrays.stream(stepComponents)
                .filter(component -> component.getType() == DamageTargetReference.class)
                .count();
        long stepTargetFields = stepFields.stream()
                .filter(field -> field.getType() == DamageTargetReference.class)
                .count();
        boolean pluralStepTargetCarrier = Arrays.stream(stepComponents)
                .anyMatch(component -> isSecondaryCarrier(
                        component.getGenericType(), DamageTargetReference.class))
                || stepFields.stream().anyMatch(field -> isSecondaryCarrier(
                        field.getGenericType(), DamageTargetReference.class))
                || stepConstructors.stream().anyMatch(constructor ->
                        constructor.isVarArgs()
                                || Arrays.stream(constructor.getGenericParameterTypes())
                                        .anyMatch(type -> isSecondaryCarrier(
                                                type, DamageTargetReference.class)))
                || stepFactories.stream().anyMatch(factory ->
                        factory.isVarArgs()
                                || Arrays.stream(factory.getGenericParameterTypes())
                                        .anyMatch(type -> isSecondaryCarrier(
                                                type, DamageTargetReference.class)));

        DamageTargetReference target = new DamageTargetReference(
                java.util.UUID.fromString("70000000-0000-4000-8000-000000000099"));
        DamageEffectRequest request = new DamageEffectRequest(
                new EffectRequestId(901),
                new SourceEventId(902),
                target,
                25,
                0,
                CompensationPolicy.REFUND_IF_NO_PRIMARY_MUTATION);
        EffectResolution resolution = new DamageEffectResolver().resolve(request, 0);
        assertTrue(resolution instanceof AcceptedEffectResolution);
        EffectCommitPlan plan = ((AcceptedEffectResolution) resolution).plan();
        var targetIdentities = Collections.newSetFromMap(
                new IdentityHashMap<DamageTargetReference, Boolean>());
        targetIdentities.add(request.target());
        for (EffectStep step : plan.steps()) {
            assertTrue(step instanceof DamageEffectStep);
            DamageEffectStep damage = (DamageEffectStep) step;
            assertSame(target, damage.target());
            targetIdentities.add(damage.target());
        }

        assertAll(
                () -> assertTrue(DamageEffectRequest.class.isRecord()),
                () -> assertEquals("target", Arrays.stream(components)
                        .filter(component -> component.getType()
                                == DamageTargetReference.class)
                        .findFirst()
                        .orElseThrow()
                        .getName()),
                () -> assertEquals(1L, targetComponents),
                () -> assertEquals(1L, targetFields),
                () -> assertFalse(pluralTargetCarrier),
                () -> assertEquals(1L, maximumConstructorTargets),
                () -> assertTrue(maximumFactoryTargets <= 1L),
                () -> assertEquals(0, factories.size()),
                () -> assertEquals(1, constructors.size()),
                () -> assertFalse(constructors.getFirst().isVarArgs()),
                () -> assertThrows(
                        NoSuchMethodException.class,
                        () -> DamageEffectRequest.class.getDeclaredConstructor(
                                EffectRequestId.class,
                                SourceEventId.class,
                                DamageTargetReference.class,
                                DamageTargetReference.class,
                                long.class,
                                long.class,
                                CompensationPolicy.class),
                        "STRUCTURALLY_UNREPRESENTABLE_ONE_OVER"),
                () -> assertEquals(1L, stepTargetComponents),
                () -> assertEquals(1L, stepTargetFields),
                () -> assertFalse(pluralStepTargetCarrier),
                () -> assertEquals(1, stepConstructors.size()),
                () -> assertEquals(0, stepFactories.size()),
                () -> assertEquals(1, plan.steps().size()),
                () -> assertEquals(1, targetIdentities.size()),
                () -> assertSame(target, request.target()),
                () -> assertEquals(1L, P6EffectBounds.MAX_TARGETS_PER_REQUEST),
                () -> assertEquals(
                        "STRUCTURALLY_UNREPRESENTABLE_ONE_OVER",
                        targetComponents == 1
                                        && targetFields == 1
                                        && !pluralTargetCarrier
                                        && maximumConstructorTargets <= 1
                                        && maximumFactoryTargets <= 1
                                ? "STRUCTURALLY_UNREPRESENTABLE_ONE_OVER"
                                : "REPRESENTABLE_ONE_OVER"));
    }

    @Test
    void requestFieldsContainNoLiveOrGenericPayloadType() {
        Set<String> forbidden = Set.of(
                "java.lang.Object",
                "net.minecraft.world.entity.Entity",
                "net.minecraft.world.level.Level",
                "net.minecraft.server.MinecraftServer");
        for (Field field : DamageEffectRequest.class.getDeclaredFields()) {
            if (forbidden.contains(field.getType().getName())) {
                throw new AssertionError(field.getType().getName());
            }
        }
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
