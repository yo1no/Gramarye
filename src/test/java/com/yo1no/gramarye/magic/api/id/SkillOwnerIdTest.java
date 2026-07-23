package com.yo1no.gramarye.magic.api.id;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.UUID;
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
    void p3CPhaseLocalShapeHasNoCodecNetworkOrMinecraftPlayerDependency() {
        var fields = Arrays.stream(SkillOwnerId.class.getDeclaredFields()).toList();
        var methods = Arrays.stream(SkillOwnerId.class.getDeclaredMethods()).toList();

        assertAll(
                () -> assertEquals(UUID.class, SkillOwnerId.class.getRecordComponents()[0].getType()),
                () -> assertFalse(fields.stream().anyMatch(field ->
                        field.getType().getName().contains("Codec"))),
                () -> assertFalse(methods.stream().anyMatch(method ->
                        method.getReturnType().getName().contains("Codec")
                                || method.getName().toLowerCase().contains("random")
                                || method.getName().toLowerCase().contains("generate"))),
                () -> assertFalse(Arrays.stream(SkillOwnerId.class.getRecordComponents())
                        .anyMatch(component -> component.getType().getName().contains("Player"))));
    }
}
