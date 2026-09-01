package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;

final class ManaStateCodec {
    static final String SCHEMA_VERSION_FIELD = "schema_version";
    static final String BALANCE_FIELD = "balance";
    static final int CURRENT_SCHEMA_VERSION = 0;
    static final long UNAVAILABLE_PERSISTENCE_BALANCE = -1L;

    private static final Set<String> EXACT_FIELDS =
            Set.of(SCHEMA_VERSION_FIELD, BALANCE_FIELD);

    private ManaStateCodec() {}

    static ManaState decode(Tag input) {
        if (input == null) {
            return ManaState.unavailable(ManaDecodeFailure.NULL_INPUT);
        }
        if (!(input instanceof CompoundTag root)) {
            return ManaState.unavailable(ManaDecodeFailure.WRONG_ROOT_TYPE);
        }

        Tag schemaVersion = root.get(SCHEMA_VERSION_FIELD);
        if (schemaVersion == null) {
            return ManaState.unavailable(ManaDecodeFailure.MISSING_SCHEMA_VERSION);
        }
        Tag balanceTag = root.get(BALANCE_FIELD);
        if (balanceTag == null) {
            return ManaState.unavailable(ManaDecodeFailure.MISSING_BALANCE);
        }
        if (!root.getAllKeys().equals(EXACT_FIELDS)) {
            return ManaState.unavailable(ManaDecodeFailure.EXTRA_FIELD);
        }
        if (!(schemaVersion instanceof IntTag exactVersion)) {
            return ManaState.unavailable(ManaDecodeFailure.WRONG_SCHEMA_VERSION_TYPE);
        }
        if (!(balanceTag instanceof LongTag exactBalance)) {
            return ManaState.unavailable(ManaDecodeFailure.WRONG_BALANCE_TYPE);
        }
        if (exactVersion.getAsInt() != CURRENT_SCHEMA_VERSION) {
            return ManaState.unavailable(ManaDecodeFailure.UNSUPPORTED_SCHEMA_VERSION);
        }

        long balance = exactBalance.getAsLong();
        if (balance < 0L) {
            return ManaState.unavailable(ManaDecodeFailure.BALANCE_BELOW_MINIMUM);
        }
        if (balance > P6ManaBounds.MAX_MANA_VALUE) {
            return ManaState.unavailable(ManaDecodeFailure.BALANCE_ABOVE_MAXIMUM);
        }
        return ManaState.available(balance);
    }

    static CompoundTag encode(ManaState state) {
        Objects.requireNonNull(state, "state");
        var output = new CompoundTag();
        output.putInt(SCHEMA_VERSION_FIELD, CURRENT_SCHEMA_VERSION);
        output.putLong(
                BALANCE_FIELD,
                state.availability() == ManaAvailability.AVAILABLE
                        ? state.balance()
                        : UNAVAILABLE_PERSISTENCE_BALANCE);
        return output;
    }
}
