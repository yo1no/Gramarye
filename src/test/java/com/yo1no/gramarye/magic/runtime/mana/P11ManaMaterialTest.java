package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** Direct closed-token checks only, not evidence of native load/copy/save execution. */
final class P11ManaMaterialTest {
    @Test
    void actualOutputIdentityIsRequiredNotNbtEquality() {
        var output = new CompoundTag();
        output.putLong("balance", 73L);
        var write = P11ManaMaterial.written(ManaState.available(73L), output);
        assertTrue(write.matchesOutput(output));
        assertFalse(write.matchesOutput(output.copy()));
        assertFalse(write.matchesOutput(null));
    }

    @Test
    void nestedDifferentWriteCannotProveOuterOutput() {
        var state = ManaState.available(73L);
        var outer = ManaStateCodec.encode(state);
        var nested = ManaStateCodec.encode(state);
        var nestedWrite = P11ManaMaterial.written(state, nested);
        assertFalse(nestedWrite.matchesOutput(outer));
        assertTrue(nestedWrite.matchesOutput(nested));
    }

    @Test
    void missingStateOrOutputCannotMintWriteObservation() {
        assertThrows(NullPointerException.class,
                () -> P11ManaMaterial.written(null, new CompoundTag()));
        assertThrows(NullPointerException.class,
                () -> P11ManaMaterial.written(ManaState.freshDefault(), null));
    }

    @Test
    void publicWitnessesExposeNoNativeMutableStateOrNbtAndHaveNoPublicConstructors() {
        for (var type : new Class<?>[] {P11ManaMaterial.State.class, P11ManaMaterial.Write.class,
                P11ManaMaterial.Read.class, P11ManaMaterial.Publication.class}) {
            assertTrue(Arrays.stream(type.getDeclaredConstructors())
                    .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
            assertTrue(Arrays.stream(type.getDeclaredFields())
                    .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                            && Modifier.isFinal(field.getModifiers())));
            assertFalse(Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .anyMatch(method -> Tag.class.isAssignableFrom(method.getReturnType())
                            || method.getReturnType() == ManaState.class));
        }
        assertTrue(Arrays.stream(P11ManaMaterial.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .allMatch(method -> method.getReturnType() == P11ManaMaterial.State.class));
    }
}
