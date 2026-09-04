package com.yo1no.gramarye.magic.network;

import java.util.Optional;

final class PendingPermitAccounting {
    enum AcquireOutcome {
        GRANTED,
        SERVER_BUSY
    }

    enum ReleaseOutcome {
        RELEASED,
        REJECTED
    }

    static final class Permit {
        private final boolean released;

        private Permit(boolean released) {
            this.released = released;
        }

        boolean released() {
            return released;
        }

        private Permit markReleased() {
            return new Permit(true);
        }
    }

    static final class AcquireDecision {
        private final AcquireOutcome outcome;
        private final PendingPermitAccounting nextState;
        private final Permit permit;

        private AcquireDecision(
                AcquireOutcome outcome, PendingPermitAccounting nextState, Permit permit) {
            this.outcome = outcome;
            this.nextState = nextState;
            this.permit = permit;
        }

        AcquireOutcome outcome() {
            return outcome;
        }

        PendingPermitAccounting nextState() {
            return nextState;
        }

        Optional<Permit> permit() {
            return Optional.ofNullable(permit);
        }
    }

    static final class ReleaseDecision {
        private final ReleaseOutcome outcome;
        private final PendingPermitAccounting nextState;
        private final Permit nextPermit;

        private ReleaseDecision(
                ReleaseOutcome outcome,
                PendingPermitAccounting nextState,
                Permit nextPermit) {
            this.outcome = outcome;
            this.nextState = nextState;
            this.nextPermit = nextPermit;
        }

        ReleaseOutcome outcome() {
            return outcome;
        }

        PendingPermitAccounting nextState() {
            return nextState;
        }

        Permit nextPermit() {
            return nextPermit;
        }
    }

    private final int playerPending;
    private final int serverPending;

    PendingPermitAccounting(int playerPending, int serverPending) {
        if (playerPending < 0
                || playerPending > P7NetworkBounds.MAX_PENDING_INTENTS_PER_PLAYER
                || serverPending < 0
                || serverPending > P7NetworkBounds.MAX_PENDING_INTENTS_PER_SERVER
                || playerPending > serverPending) {
            throw new P7SemanticInvariantException("pending permit counts are invalid");
        }
        this.playerPending = playerPending;
        this.serverPending = serverPending;
    }

    static PendingPermitAccounting empty() {
        return new PendingPermitAccounting(0, 0);
    }

    AcquireDecision acquire() {
        if (playerPending == P7NetworkBounds.MAX_PENDING_INTENTS_PER_PLAYER
                || serverPending == P7NetworkBounds.MAX_PENDING_INTENTS_PER_SERVER) {
            return new AcquireDecision(AcquireOutcome.SERVER_BUSY, this, null);
        }
        return new AcquireDecision(
                AcquireOutcome.GRANTED,
                new PendingPermitAccounting(playerPending + 1, serverPending + 1),
                new Permit(false));
    }

    ReleaseDecision release(Permit permit) {
        if (permit == null) {
            throw new P7SemanticInvariantException("permit is absent");
        }
        if (permit.released || playerPending == 0 || serverPending == 0) {
            return new ReleaseDecision(ReleaseOutcome.REJECTED, this, permit);
        }
        return new ReleaseDecision(
                ReleaseOutcome.RELEASED,
                new PendingPermitAccounting(playerPending - 1, serverPending - 1),
                permit.markReleased());
    }

    int playerPending() {
        return playerPending;
    }

    int serverPending() {
        return serverPending;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof PendingPermitAccounting that
                        && playerPending == that.playerPending
                        && serverPending == that.serverPending;
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(playerPending) + Integer.hashCode(serverPending);
    }
}
