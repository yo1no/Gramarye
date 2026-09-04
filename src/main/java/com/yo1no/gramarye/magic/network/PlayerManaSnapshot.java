package com.yo1no.gramarye.magic.network;

import java.util.Optional;

final class PlayerManaSnapshot {
    enum Availability {
        AVAILABLE(0),
        UNAVAILABLE(1);

        private final int wireCode;

        Availability(int wireCode) {
            this.wireCode = wireCode;
        }

        int wireCode() {
            return wireCode;
        }

        static Optional<Availability> fromWireCode(int rawCode) {
            return switch (rawCode) {
                case 0 -> Optional.of(AVAILABLE);
                case 1 -> Optional.of(UNAVAILABLE);
                default -> Optional.empty();
            };
        }
    }

    private static final long MAX_BALANCE = 1_000_000_000L;

    private final long syncSequence;
    private final Availability availability;
    private final long balance;

    PlayerManaSnapshot(long syncSequence, Availability availability, long balance) {
        if (syncSequence <= 0) {
            throw new P7SemanticInvariantException("mana sync sequence is invalid");
        }
        if (availability == null) {
            throw new P7SemanticInvariantException("mana availability is absent");
        }
        if (availability == Availability.AVAILABLE
                        && (balance < 0 || balance > MAX_BALANCE)
                || availability == Availability.UNAVAILABLE && balance != 0) {
            throw new P7SemanticInvariantException("mana snapshot balance is invalid");
        }
        this.syncSequence = syncSequence;
        this.availability = availability;
        this.balance = balance;
    }

    long syncSequence() {
        return syncSequence;
    }

    Availability availability() {
        return availability;
    }

    long balance() {
        return balance;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PlayerManaSnapshot that
                        && syncSequence == that.syncSequence
                        && availability == that.availability
                        && balance == that.balance;
    }

    @Override
    public int hashCode() {
        var result = Long.hashCode(syncSequence);
        result = 31 * result + availability.hashCode();
        return 31 * result + Long.hashCode(balance);
    }
}
