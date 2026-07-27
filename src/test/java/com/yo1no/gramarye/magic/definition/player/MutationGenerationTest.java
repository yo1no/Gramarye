package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class MutationGenerationTest {
    @Test
    void successorIsCheckedAndOwnsTheOnlyPlayerAttachmentIncrement() {
        assertEquals(1, MutationGeneration.successor(0).orElseThrow());
        assertEquals(Integer.MAX_VALUE,
                MutationGeneration.successor(Integer.MAX_VALUE - 1).orElseThrow());
        assertFalse(MutationGeneration.successor(Integer.MAX_VALUE).isPresent());
        assertThrows(IllegalArgumentException.class, () -> MutationGeneration.successor(-1));
    }
}
