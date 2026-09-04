package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P7SessionIdentityTest {
    private static final UUID PLAYER_ID = UUID.fromString("3c8667e1-3f97-49f4-9bf3-f34ac98d6e8f");

    @Test
    void retainsOnlyAuthenticatedUuidAndPositiveEpoch() {
        var identity = new P7SessionIdentity(PLAYER_ID, 1L);

        assertEquals(PLAYER_ID, identity.authenticatedPlayerId());
        assertEquals(1L, identity.connectionEpoch());
    }

    @Test
    void acceptsMaximumPositiveEpoch() {
        assertEquals(
                Long.MAX_VALUE,
                new P7SessionIdentity(PLAYER_ID, Long.MAX_VALUE).connectionEpoch());
    }

    @Test
    void rejectsNullPlayerAndNonpositiveEpoch() {
        assertThrows(P7SemanticInvariantException.class, () -> new P7SessionIdentity(null, 1L));
        assertThrows(
                P7SemanticInvariantException.class,
                () -> new P7SessionIdentity(PLAYER_ID, 0L));
        assertThrows(
                P7SemanticInvariantException.class,
                () -> new P7SessionIdentity(PLAYER_ID, -1L));
    }

    @Test
    void equalityUsesBothUuidAndEpoch() {
        var same = new P7SessionIdentity(PLAYER_ID, 7L);

        assertEquals(same, new P7SessionIdentity(PLAYER_ID, 7L));
        assertEquals(same.hashCode(), new P7SessionIdentity(PLAYER_ID, 7L).hashCode());
        assertNotEquals(same, new P7SessionIdentity(PLAYER_ID, 8L));
        assertNotEquals(
                same,
                new P7SessionIdentity(
                        UUID.fromString("a527e901-fd78-459c-9303-a4b210873c49"), 7L));
    }
}
