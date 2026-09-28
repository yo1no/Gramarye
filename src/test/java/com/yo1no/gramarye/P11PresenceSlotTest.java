package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class P11PresenceSlotTest {
    @Test void onlyNativeCursorAndFourCraftingWritesAreTransient() {
        for (int index = -1; index <= 600; index++) {
            assertEquals(index >= 499 && index <= 503, P11NativePresence.transientSlot(index));
        }
        assertFalse(P11NativePresence.transientSlot(Integer.MIN_VALUE));
        assertFalse(P11NativePresence.transientSlot(Integer.MAX_VALUE));
    }
}
