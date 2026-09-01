package com.yo1no.gramarye.magic.runtime.mana;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

final class ManaStateCodecTest {
    @Test
    void validEncodingHasExactFieldsTypesAndSchema() {
        var encoded = ManaStateCodec.encode(ManaState.available(73L));

        assertEquals(Set.of("schema_version", "balance"), encoded.getAllKeys());
        assertInstanceOf(IntTag.class, encoded.get("schema_version"));
        assertInstanceOf(LongTag.class, encoded.get("balance"));
        assertEquals(0, encoded.getInt("schema_version"));
        assertEquals(73L, encoded.getLong("balance"));
    }

    @Test
    void validEncodingIsDeterministic() {
        assertEquals(
                ManaStateCodec.encode(ManaState.available(99L)),
                ManaStateCodec.encode(ManaState.available(99L)));
    }

    @Test
    void minimumBalanceRoundTrips() {
        assertEquals(
                ManaState.available(0L),
                ManaStateCodec.decode(ManaStateCodec.encode(ManaState.available(0L))));
    }

    @Test
    void maximumBalanceRoundTrips() {
        var maximum = ManaState.available(P6ManaBounds.MAX_MANA_VALUE);
        assertEquals(maximum, ManaStateCodec.decode(ManaStateCodec.encode(maximum)));
    }

    @Test
    void nullInputIsUnavailable() {
        assertUnavailable(null, ManaDecodeFailure.NULL_INPUT);
    }

    @Test
    void wrongRootTypeIsUnavailable() {
        assertUnavailable(StringTag.valueOf("not-a-compound"), ManaDecodeFailure.WRONG_ROOT_TYPE);
    }

    @Test
    void missingSchemaVersionIsUnavailable() {
        var input = new CompoundTag();
        input.putLong("balance", 1L);
        assertUnavailable(input, ManaDecodeFailure.MISSING_SCHEMA_VERSION);
    }

    @Test
    void missingBalanceIsUnavailable() {
        var input = new CompoundTag();
        input.putInt("schema_version", 0);
        assertUnavailable(input, ManaDecodeFailure.MISSING_BALANCE);
    }

    @Test
    void extraFieldIsUnavailable() {
        var input = validTag(1L);
        input.putInt("extra", 0);
        assertUnavailable(input, ManaDecodeFailure.EXTRA_FIELD);
    }

    @Test
    void wrongSchemaVersionNbtTypeIsUnavailable() {
        var input = validTag(1L);
        input.putLong("schema_version", 0L);
        assertUnavailable(input, ManaDecodeFailure.WRONG_SCHEMA_VERSION_TYPE);
    }

    @Test
    void wrongBalanceNbtTypeIsUnavailable() {
        var input = validTag(1L);
        input.putInt("balance", 1);
        assertUnavailable(input, ManaDecodeFailure.WRONG_BALANCE_TYPE);
    }

    @Test
    void unsupportedSchemaVersionIsUnavailable() {
        var input = validTag(1L);
        input.putInt("schema_version", 1);
        assertUnavailable(input, ManaDecodeFailure.UNSUPPORTED_SCHEMA_VERSION);
    }

    @Test
    void negativeBalanceIsUnavailable() {
        assertUnavailable(validTag(-1L), ManaDecodeFailure.BALANCE_BELOW_MINIMUM);
    }

    @Test
    void overMaximumBalanceIsUnavailable() {
        assertUnavailable(
                validTag(P6ManaBounds.MAX_MANA_VALUE + 1L),
                ManaDecodeFailure.BALANCE_ABOVE_MAXIMUM);
    }

    @Test
    void unavailableStateCannotBeReadAsBalance() {
        var unavailable = ManaStateCodec.decode(StringTag.valueOf("malformed"));
        assertThrows(IllegalStateException.class, unavailable::balance);
    }

    @Test
    void unavailablePersistenceMarkerNeverBecomesLegalZero() {
        var unavailable = ManaState.unavailable(ManaDecodeFailure.EXTRA_FIELD);
        var encoded = ManaStateCodec.encode(unavailable);

        assertEquals(Set.of("schema_version", "balance"), encoded.getAllKeys());
        assertInstanceOf(IntTag.class, encoded.get("schema_version"));
        assertInstanceOf(LongTag.class, encoded.get("balance"));
        assertNotEquals(0L, encoded.getLong("balance"));
        assertEquals(-1L, encoded.getLong("balance"));
        assertEquals(
                ManaAvailability.UNAVAILABLE,
                ManaStateCodec.decode(encoded).availability());
    }

    @Test
    void absentDefaultAndMalformedPresentStateRemainDistinct() {
        var absentDefault = ManaState.freshDefault();
        var malformedPresent = ManaStateCodec.decode(StringTag.valueOf("malformed"));

        assertEquals(ManaAvailability.AVAILABLE, absentDefault.availability());
        assertEquals(0L, absentDefault.balance());
        assertEquals(ManaAvailability.UNAVAILABLE, malformedPresent.availability());
        assertNotEquals(absentDefault, malformedPresent);
    }

    private static CompoundTag validTag(long balance) {
        var input = new CompoundTag();
        input.putInt("schema_version", 0);
        input.putLong("balance", balance);
        return input;
    }

    private static void assertUnavailable(Tag input, ManaDecodeFailure expectedFailure) {
        var decoded = ManaStateCodec.decode(input);
        assertEquals(ManaAvailability.UNAVAILABLE, decoded.availability());
        assertEquals(expectedFailure, decoded.unavailableReason());
    }
}
