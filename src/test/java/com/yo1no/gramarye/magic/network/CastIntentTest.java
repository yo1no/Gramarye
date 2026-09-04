package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CastIntentTest {
    private static final int AIM_MASK = 1 << P7NetworkBounds.AIM_PRESENT_BIT;
    private static final int ENTITY_MASK = 1 << P7NetworkBounds.ENTITY_HINT_PRESENT_BIT;

    @Test
    void castInputKindIsTheExactClosedCodeZeroVocabulary() {
        assertArrayEquals(new CastInputKind[] {CastInputKind.CAST}, CastInputKind.values());
        assertEquals(0, CastInputKind.CAST.semanticCode());
        assertTrue(CastInputKind.isKnownCode(0));
        assertSame(CastInputKind.CAST, CastInputKind.fromValidatedCode(0));

        for (var raw : new int[] {
            Integer.MIN_VALUE, -1, 1, 2, 255, Integer.MAX_VALUE
        }) {
            assertFalse(CastInputKind.isKnownCode(raw), Integer.toString(raw));
            assertThrows(
                    P7SemanticInvariantException.class,
                    () -> CastInputKind.fromValidatedCode(raw),
                    Integer.toString(raw));
        }
        assertFalse(Arrays.stream(CastInputKind.values())
                .map(Enum::name)
                .anyMatch(name -> Set.of("PRESS", "HOLD", "RELEASE", "REPEAT")
                        .contains(name)));
    }

    @Test
    void allFourPresenceCombinationsProduceCanonicalImmutableIntents() {
        var none = valid(1, 0, 0, null, null, null, null);
        var aim = valid(2, 1, AIM_MASK, 1, -1, 2, null);
        var entity = valid(3, 62, ENTITY_MASK, null, null, null, 1);
        var both = valid(4, 63, AIM_MASK | ENTITY_MASK, -3, 4, 5, 99);

        assertFalse(none.aimHint().isPresent());
        assertFalse(none.entityHint().isPresent());
        assertTrue(aim.aimHint().isPresent());
        assertFalse(aim.entityHint().isPresent());
        assertFalse(entity.aimHint().isPresent());
        assertTrue(entity.entityHint().isPresent());
        assertTrue(both.aimHint().isPresent());
        assertTrue(both.entityHint().isPresent());
        assertEquals(List.of(0, AIM_MASK, ENTITY_MASK, AIM_MASK | ENTITY_MASK),
                List.of(
                        none.presenceMask(),
                        aim.presenceMask(),
                        entity.presenceMask(),
                        both.presenceMask()));
    }

    @Test
    void everyStructuralInvalidityReturnsTypedMalformedWithoutLeakingAnException() {
        var invalid = List.of(
                CastIntentValidation.validate(1, 0, -1, 0,
                        null, null, null, null),
                CastIntentValidation.validate(1, 0, 1, 0,
                        null, null, null, null),
                CastIntentValidation.validate(1, -1, 0, 0,
                        null, null, null, null),
                CastIntentValidation.validate(1, 64, 0, 0,
                        null, null, null, null),
                CastIntentValidation.validate(1, 0, 0, 1 << 2,
                        null, null, null, null),
                CastIntentValidation.validate(1, 0, 0, -1,
                        null, null, null, null),
                CastIntentValidation.validate(1, 0, 0, AIM_MASK,
                        null, null, null, null),
                CastIntentValidation.validate(1, 0, 0, AIM_MASK,
                        1, null, 2, null),
                CastIntentValidation.validate(1, 0, 0, 0,
                        1, 2, 3, null),
                CastIntentValidation.validate(1, 0, 0, ENTITY_MASK,
                        null, null, null, null),
                CastIntentValidation.validate(1, 0, 0, 0,
                        null, null, null, 1),
                CastIntentValidation.validate(1, 0, 0, AIM_MASK,
                        0, 0, 0, null),
                CastIntentValidation.validate(1, 0, 0, AIM_MASK,
                        P7NetworkBounds.Q15_RESERVED, 1, 1, null),
                CastIntentValidation.validate(1, 0, 0, AIM_MASK,
                        P7NetworkBounds.Q15_MAX + 1, 1, 1, null),
                CastIntentValidation.validate(1, 0, 0, ENTITY_MASK,
                        null, null, null, 0),
                CastIntentValidation.validate(1, 0, 0, ENTITY_MASK,
                        null, null, null, -1));

        for (var result : invalid) {
            assertEquals(CastIntentValidation.Outcome.INVALID, result.outcome());
            assertFalse(result.valid());
            assertTrue(result.intent().isEmpty());
            assertEquals(
                    P7IntentFailureReason.MALFORMED_PAYLOAD,
                    result.failureReason().orElseThrow());
        }
    }

    @Test
    void aimHintAcceptsEveryComponentEdgeAndRejectsReservedOrZeroVectors() {
        var validVectors = new int[][] {
            {P7NetworkBounds.Q15_MIN, 1, 1},
            {P7NetworkBounds.Q15_MAX, 1, 1},
            {1, P7NetworkBounds.Q15_MIN, 1},
            {1, P7NetworkBounds.Q15_MAX, 1},
            {1, 1, P7NetworkBounds.Q15_MIN},
            {1, 1, P7NetworkBounds.Q15_MAX},
            {-17, 0, 23}
        };
        for (var vector : validVectors) {
            assertTrue(AimHint.componentsValid(vector[0], vector[1], vector[2]));
            var hint = new AimHint(vector[0], vector[1], vector[2]);
            assertArrayEquals(vector, new int[] {hint.x(), hint.y(), hint.z()});
        }

        var invalidVectors = new int[][] {
            {0, 0, 0},
            {P7NetworkBounds.Q15_RESERVED, 1, 1},
            {1, P7NetworkBounds.Q15_RESERVED, 1},
            {1, 1, P7NetworkBounds.Q15_RESERVED},
            {P7NetworkBounds.Q15_MAX + 1, 1, 1},
            {1, P7NetworkBounds.Q15_MAX + 1, 1},
            {1, 1, P7NetworkBounds.Q15_MAX + 1}
        };
        for (var vector : invalidVectors) {
            assertFalse(AimHint.componentsValid(vector[0], vector[1], vector[2]));
            assertThrows(P7SemanticInvariantException.class,
                    () -> new AimHint(vector[0], vector[1], vector[2]));
        }
    }

    @Test
    void aimHintEqualityIsDeterministicAndItsRepresentationUsesOnlyIntScalars() {
        var first = new AimHint(-17, 0, 23);
        var equal = new AimHint(-17, 0, 23);
        var different = new AimHint(-17, 1, 23);
        var fields = Arrays.stream(AimHint.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(first, first);
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, null);
        assertNotEquals(first, "-17,0,23");
        assertEquals(List.of("x", "y", "z"),
                fields.stream().map(field -> field.getName()).toList());
        assertTrue(fields.stream().allMatch(field -> field.getType() == int.class));
        assertTrue(fields.stream().allMatch(field -> Modifier.isPrivate(field.getModifiers())
                && Modifier.isFinal(field.getModifiers())));
        assertTrue(Arrays.stream(AimHint.class.getDeclaredMethods())
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())))
                .noneMatch(type -> type == float.class || type == double.class));
    }

    @Test
    void entityHintAcceptsExactPositiveRangeAndComputesAllVarIntSizes() {
        assertTrue(EntityHint.valueValid(1));
        assertTrue(EntityHint.valueValid(Integer.MAX_VALUE));
        assertFalse(EntityHint.valueValid(0));
        assertFalse(EntityHint.valueValid(-1));
        assertFalse(EntityHint.valueValid(Integer.MIN_VALUE));
        assertThrows(P7SemanticInvariantException.class, () -> new EntityHint(0));

        var sizeCases = new int[][] {
            {1, 1}, {0x7f, 1},
            {0x80, 2}, {0x3fff, 2},
            {0x4000, 3}, {0x1f_ffff, 3},
            {0x20_0000, 4}, {0x0fff_ffff, 4},
            {0x1000_0000, 5}, {Integer.MAX_VALUE, 5}
        };
        for (var sizeCase : sizeCases) {
            var hint = new EntityHint(sizeCase[0]);
            assertEquals(sizeCase[0], hint.networkId());
            assertEquals(sizeCase[1], hint.positiveVarIntEncodedSize());
        }
    }

    @Test
    void entityHintHasValueEqualityAndNoUuidOrCollectionCarrier() {
        var first = new EntityHint(42);
        var equal = new EntityHint(42);
        var different = new EntityHint(43);
        var fields = Arrays.stream(EntityHint.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
        assertEquals(1, fields.size());
        assertEquals(int.class, fields.getFirst().getType());
        assertTrue(fields.stream().noneMatch(field -> field.getType() == UUID.class
                || Collection.class.isAssignableFrom(field.getType())
                || Map.class.isAssignableFrom(field.getType())
                || field.getType().isArray()));
    }

    @Test
    void fixedLongSequenceAlwaysValidatesStructurallyButReportsProductValidity() {
        var one = valid(1, 0, 0, null, null, null, null);
        var maximum = valid(Long.MAX_VALUE, 0, 0, null, null, null, null);
        var zero = valid(0, 0, 0, null, null, null, null);
        var negative = valid(-1, 0, 0, null, null, null, null);
        var minimum = valid(Long.MIN_VALUE, 0, 0, null, null, null, null);

        assertTrue(one.hasProductValidSequence());
        assertTrue(maximum.hasProductValidSequence());
        assertFalse(zero.hasProductValidSequence());
        assertFalse(negative.hasProductValidSequence());
        assertFalse(minimum.hasProductValidSequence());
        assertEquals(
                List.of(1L, Long.MAX_VALUE, 0L, -1L, Long.MIN_VALUE),
                List.of(
                        one.sequence(),
                        maximum.sequence(),
                        zero.sequence(),
                        negative.sequence(),
                        minimum.sequence()));
    }

    @Test
    void slotBoundsAreExactForValidationAndInternalConstruction() {
        assertEquals(0, valid(1, 0, 0, null, null, null, null).slot());
        assertEquals(63, valid(1, 63, 0, null, null, null, null).slot());
        assertMalformed(CastIntentValidation.validate(
                1, -1, 0, 0, null, null, null, null));
        assertMalformed(CastIntentValidation.validate(
                1, 64, 0, 0, null, null, null, null));
        assertThrows(P7SemanticInvariantException.class,
                () -> new CastIntent(1, -1, CastInputKind.CAST, 0, null, null));
        assertThrows(P7SemanticInvariantException.class,
                () -> new CastIntent(1, 64, CastInputKind.CAST, 0, null, null));
        assertThrows(P7SemanticInvariantException.class,
                () -> new CastIntent(1, 0, null, 0, null, null));
    }

    @Test
    void castIntentFieldInventoryIsExactPrivateFinalAndNonPlural() {
        var fields = Arrays.stream(CastIntent.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(
                List.of(
                        "sequence", "slot", "inputKind", "presenceMask", "aimHint",
                        "entityHint"),
                fields.stream().map(field -> field.getName()).toList());
        assertEquals(
                List.of(
                        long.class, int.class, CastInputKind.class, int.class,
                        AimHint.class, EntityHint.class),
                fields.stream().map(field -> field.getType()).toList());
        assertTrue(fields.stream().allMatch(field -> Modifier.isPrivate(field.getModifiers())
                && Modifier.isFinal(field.getModifiers())));
        assertEquals(1, fields.stream()
                .filter(field -> field.getType() == EntityHint.class)
                .count());
        assertTrue(fields.stream().noneMatch(field -> field.getType().isArray()
                || Collection.class.isAssignableFrom(field.getType())
                || Map.class.isAssignableFrom(field.getType())
                || field.getType() == String.class));
    }

    @Test
    void encodedSizesCoverEveryCanonicalOptionalShapeAndVarIntWidth() {
        assertEquals(11, valid(1, 0, 0, null, null, null, null).encodedBodySize());
        assertEquals(17, valid(1, 0, AIM_MASK, 1, 2, 3, null).encodedBodySize());

        var entityIds = new int[] {1, 0x80, 0x4000, 0x20_0000, 0x1000_0000};
        for (var index = 0; index < entityIds.length; index++) {
            var entityOnly = valid(
                    1, 0, ENTITY_MASK, null, null, null, entityIds[index]);
            var both = valid(
                    1, 0, AIM_MASK | ENTITY_MASK, 1, 2, 3, entityIds[index]);
            assertEquals(12 + index, entityOnly.encodedBodySize());
            assertEquals(18 + index, both.encodedBodySize());
        }

        var maximum = valid(
                1, 63, AIM_MASK | ENTITY_MASK, 1, -1, 1, Integer.MAX_VALUE);
        assertEquals(P7NetworkBounds.ACTUAL_MAX_CAST_INTENT_BODY_BYTES,
                maximum.encodedBodySize());
        assertTrue(maximum.encodedBodySize() <= P7NetworkBounds.MAX_C2S_INTENT_BYTES);
    }

    @Test
    void castIntentEqualityAndAccessorsPreserveImmutableValues() {
        var first = valid(7, 3, AIM_MASK | ENTITY_MASK, 1, -2, 3, 127);
        var equal = valid(7, 3, AIM_MASK | ENTITY_MASK, 1, -2, 3, 127);
        var different = valid(8, 3, AIM_MASK | ENTITY_MASK, 1, -2, 3, 127);

        assertNotSame(first, equal);
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
        assertSame(CastInputKind.CAST, first.inputKind());
        assertEquals(new AimHint(1, -2, 3), first.aimHint().orElseThrow());
        assertEquals(new EntityHint(127), first.entityHint().orElseThrow());
    }

    @Test
    void validationVocabularyAndResultCarriersAreClosedAndConsistent() {
        assertArrayEquals(
                new CastIntentValidation.Outcome[] {
                    CastIntentValidation.Outcome.VALID,
                    CastIntentValidation.Outcome.INVALID
                },
                CastIntentValidation.Outcome.values());
        var accepted = CastIntentValidation.validate(
                1, 0, 0, 0, null, null, null, null);
        var rejected = CastIntentValidation.validate(
                1, 0, 99, 0, null, null, null, null);

        assertTrue(accepted.valid());
        assertTrue(accepted.intent().isPresent());
        assertTrue(accepted.failureReason().isEmpty());
        assertFalse(rejected.valid());
        assertTrue(rejected.intent().isEmpty());
        assertEquals(P7IntentFailureReason.MALFORMED_PAYLOAD,
                rejected.failureReason().orElseThrow());
        assertTrue(Arrays.stream(CastIntentValidation.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
    }

    private static CastIntent valid(
            long sequence,
            int slot,
            int mask,
            Integer aimX,
            Integer aimY,
            Integer aimZ,
            Integer entityId) {
        var validation = CastIntentValidation.validate(
                sequence,
                slot,
                P7NetworkBounds.CAST_INPUT_KIND_CODE,
                mask,
                aimX,
                aimY,
                aimZ,
                entityId);
        assertEquals(CastIntentValidation.Outcome.VALID, validation.outcome());
        assertTrue(validation.failureReason().isEmpty());
        return validation.intent().orElseThrow();
    }

    private static void assertMalformed(CastIntentValidation validation) {
        assertEquals(CastIntentValidation.Outcome.INVALID, validation.outcome());
        assertFalse(validation.valid());
        assertTrue(validation.intent().isEmpty());
        assertEquals(P7IntentFailureReason.MALFORMED_PAYLOAD,
                validation.failureReason().orElseThrow());
    }

}
