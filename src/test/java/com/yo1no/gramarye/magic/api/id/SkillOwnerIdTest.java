package com.yo1no.gramarye.magic.api.id;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class SkillOwnerIdTest {
    @Test
    void rejectsNullAndUsesOnlyTheUuidForValueEquality() {
        var firstValue = UUID.fromString("9864990c-d024-48ea-9b68-bd10f0f82b52");
        var secondValue = UUID.fromString("fb879e1e-35ec-4334-b202-45702e6743e8");
        var first = new SkillOwnerId(firstValue);
        var equal = new SkillOwnerId(firstValue);
        var different = new SkillOwnerId(secondValue);

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new SkillOwnerId(null)),
                () -> assertEquals(first, equal),
                () -> assertEquals(first.hashCode(), equal.hashCode()),
                () -> assertNotEquals(first, different),
                () -> assertEquals(firstValue, first.value()));
    }

    @Test
    void canonicalCodecRoundTripsAndMatchesSkillIdPhysicalRepresentation() {
        var value = UUID.fromString("9864990c-d024-48ea-9b68-bd10f0f82b52");
        var owner = new SkillOwnerId(value);
        var skill = new SkillId(value);
        var ownerJson = SkillOwnerId.CODEC.encodeStart(JsonOps.INSTANCE, owner).getOrThrow();
        var ownerNbt = SkillOwnerId.CODEC.encodeStart(NbtOps.INSTANCE, owner).getOrThrow();

        assertAll(
                () -> assertEquals(
                        SkillId.CODEC.encodeStart(JsonOps.INSTANCE, skill).getOrThrow(),
                        ownerJson),
                () -> assertEquals(
                        SkillId.CODEC.encodeStart(NbtOps.INSTANCE, skill).getOrThrow(),
                        ownerNbt),
                () -> assertEquals(owner, SkillOwnerId.CODEC.parse(JsonOps.INSTANCE, ownerJson).getOrThrow()),
                () -> assertEquals(owner, SkillOwnerId.CODEC.parse(NbtOps.INSTANCE, ownerNbt).getOrThrow()));
    }

    @Test
    void malformedSerializedOwnerIsACompleteCodecError() {
        var result = SkillOwnerId.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("not-a-uuid"));

        assertAll(
                () -> assertTrue(result.error().isPresent()),
                () -> assertTrue(result.result().isEmpty()),
                () -> assertTrue(result.error().orElseThrow().partialValue().isEmpty()));
    }

    @Test
    void p4APhaseLocalShapeHasCanonicalCodecButNoNetworkOrPlayerDependency() {
        var fields = Arrays.stream(SkillOwnerId.class.getDeclaredFields()).toList();
        var methods = Arrays.stream(SkillOwnerId.class.getDeclaredMethods()).toList();

        assertAll(
                () -> assertEquals(UUID.class, SkillOwnerId.class.getRecordComponents()[0].getType()),
                () -> assertEquals(Codec.class, SkillOwnerId.class.getDeclaredField("CODEC").getType()),
                () -> assertEquals(1, fields.stream().filter(field ->
                        field.getType().getName().contains("Codec")).count()),
                () -> assertFalse(fields.stream().anyMatch(field ->
                        field.getType().getName().contains("StreamCodec"))),
                () -> assertFalse(methods.stream().anyMatch(method ->
                        method.getReturnType().getName().contains("StreamCodec")
                                || method.getName().toLowerCase().contains("random")
                                || method.getName().toLowerCase().contains("generate"))),
                () -> assertFalse(Arrays.stream(SkillOwnerId.class.getRecordComponents())
                        .anyMatch(component -> component.getType().getName().contains("Player"))));
    }
}
