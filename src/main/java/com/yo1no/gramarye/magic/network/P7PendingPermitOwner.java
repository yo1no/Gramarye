package com.yo1no.gramarye.magic.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class P7PendingPermitOwner {
    enum AcquireOutcome {
        GRANTED,
        SERVER_BUSY
    }

    static final class AcquireResult {
        private final AcquireOutcome outcome;
        private final P7PendingPermit permit;

        private AcquireResult(AcquireOutcome outcome, P7PendingPermit permit) {
            this.outcome = outcome;
            this.permit = permit;
        }

        AcquireOutcome outcome() {
            return outcome;
        }

        Optional<P7PendingPermit> permit() {
            return Optional.ofNullable(permit);
        }
    }

    private final Object monitor = new Object();
    private final Map<UUID, Integer> perPlayerPending = new HashMap<>();
    private int serverPending;

    AcquireResult acquire(UUID authenticatedPlayerId, long connectionEpoch) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException("connection epoch is invalid");
        }
        synchronized (monitor) {
            var playerPending = perPlayerPending.getOrDefault(authenticatedPlayerId, 0);
            var accounting = new PendingPermitAccounting(playerPending, serverPending);
            var decision = accounting.acquire();
            if (decision.outcome() == PendingPermitAccounting.AcquireOutcome.SERVER_BUSY) {
                return new AcquireResult(AcquireOutcome.SERVER_BUSY, null);
            }
            var nextState = decision.nextState();
            perPlayerPending.put(authenticatedPlayerId, nextState.playerPending());
            serverPending = nextState.serverPending();
            var permit = new P7PendingPermit(
                    this,
                    authenticatedPlayerId,
                    connectionEpoch,
                    decision.permit().orElseThrow());
            return new AcquireResult(AcquireOutcome.GRANTED, permit);
        }
    }

    void release(P7PendingPermit permit) {
        Objects.requireNonNull(permit, "permit");
        synchronized (monitor) {
            if (permit.owner() != this
                    || permit.accountingPermitUnderOwnerLock().released()) {
                throw new P7SemanticInvariantException("pending permit was released twice");
            }
            var playerId = permit.authenticatedPlayerId();
            var playerPending = perPlayerPending.getOrDefault(playerId, 0);
            var accounting = new PendingPermitAccounting(playerPending, serverPending);
            var decision = accounting.release(
                    permit.accountingPermitUnderOwnerLock());
            if (decision.outcome() != PendingPermitAccounting.ReleaseOutcome.RELEASED) {
                throw new P7SemanticInvariantException("pending permit accounting mismatch");
            }
            var nextState = decision.nextState();
            if (nextState.playerPending() == 0) {
                perPlayerPending.remove(playerId);
            } else {
                perPlayerPending.put(playerId, nextState.playerPending());
            }
            serverPending = nextState.serverPending();
            permit.markReleasedUnderOwnerLock(decision.nextPermit());
        }
    }

    boolean isReleased(P7PendingPermit permit) {
        Objects.requireNonNull(permit, "permit");
        synchronized (monitor) {
            if (permit.owner() != this) {
                throw new P7SemanticInvariantException("pending permit owner mismatch");
            }
            return permit.accountingPermitUnderOwnerLock().released();
        }
    }

    int playerPending(UUID authenticatedPlayerId) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        synchronized (monitor) {
            return perPlayerPending.getOrDefault(authenticatedPlayerId, 0);
        }
    }

    int serverPending() {
        synchronized (monitor) {
            return serverPending;
        }
    }

    int trackedPlayerCount() {
        synchronized (monitor) {
            return perPlayerPending.size();
        }
    }
}
