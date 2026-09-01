package com.yo1no.gramarye.magic.runtime.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
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
}
