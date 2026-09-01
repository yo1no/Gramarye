package com.yo1no.gramarye.magic.runtime.mana;

import java.util.Objects;

final class ManaState {
    private final ManaAvailability availability;
    private final long balance;
    private final ManaDecodeFailure unavailableReason;

    private ManaState(
            ManaAvailability availability,
            long balance,
            ManaDecodeFailure unavailableReason) {
        this.availability = Objects.requireNonNull(availability, "availability");
        this.balance = balance;
        this.unavailableReason = unavailableReason;
    }

    static ManaState freshDefault() {
        return available(0L);
    }

    static ManaState available(long balance) {
        if (balance < 0L || balance > P6ManaBounds.MAX_MANA_VALUE) {
            throw new IllegalArgumentException("balance is outside the P6 mana bound");
        }
        return new ManaState(ManaAvailability.AVAILABLE, balance, null);
    }

    static ManaState unavailable(ManaDecodeFailure reason) {
        return new ManaState(
                ManaAvailability.UNAVAILABLE,
                ManaStateCodec.UNAVAILABLE_PERSISTENCE_BALANCE,
                Objects.requireNonNull(reason, "reason"));
    }

    ManaAvailability availability() {
        return availability;
    }

    long balance() {
        if (availability != ManaAvailability.AVAILABLE) {
            throw new IllegalStateException("unavailable mana state has no legal balance");
        }
        return balance;
    }

    ManaDecodeFailure unavailableReason() {
        if (availability != ManaAvailability.UNAVAILABLE) {
            throw new IllegalStateException("available mana state has no decode failure");
        }
        return unavailableReason;
    }

    ManaState copy() {
        return availability == ManaAvailability.AVAILABLE
                ? available(balance)
                : unavailable(unavailableReason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManaState that)) {
            return false;
        }
        return availability == that.availability
                && balance == that.balance
                && unavailableReason == that.unavailableReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(availability, balance, unavailableReason);
    }

    @Override
    public String toString() {
        return availability == ManaAvailability.AVAILABLE
                ? "ManaState[AVAILABLE,balance=" + balance + "]"
                : "ManaState[UNAVAILABLE,reason=" + unavailableReason + "]";
    }
}
