package com.yo1no.gramarye.magic.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final Map<UUID, Set<P7PendingPermit>> activePermitsByPlayer =
            new HashMap<>();
    private int serverPending;
    private long serverGeneration = 1L;

    AcquireResult acquire(UUID authenticatedPlayerId, long connectionEpoch) {
        return acquire(
                authenticatedPlayerId,
                connectionEpoch,
                captureServerGeneration());
    }

    AcquireResult acquire(
            UUID authenticatedPlayerId,
            long connectionEpoch,
            long expectedServerGeneration) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException("connection epoch is invalid");
        }
        if (expectedServerGeneration <= 0) {
            throw new P7SemanticInvariantException("server generation is invalid");
        }
        synchronized (monitor) {
            if (expectedServerGeneration != serverGeneration) {
                return new AcquireResult(AcquireOutcome.SERVER_BUSY, null);
            }
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
                    serverGeneration,
                    decision.permit().orElseThrow());
            var playerPermits = activePermitsByPlayer.computeIfAbsent(
                    authenticatedPlayerId,
                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>()));
            if (!playerPermits.add(permit)) {
                throw new P7SemanticInvariantException(
                        "pending permit identity was already active");
            }
            return new AcquireResult(AcquireOutcome.GRANTED, permit);
        }
    }

    void release(P7PendingPermit permit) {
        Objects.requireNonNull(permit, "permit");
        synchronized (monitor) {
            requireOwned(permit);
            if (permit.lifecycleStateUnderOwnerLock()
                    != P7PendingPermit.LifecycleState.ACTIVE) {
                throw new P7SemanticInvariantException("pending permit was released twice");
            }
            releaseActive(
                    permit, P7PendingPermit.LifecycleState.EXPLICITLY_RELEASED);
        }
    }

    void releaseAfterEnqueueFailure(P7PendingPermit permit) {
        Objects.requireNonNull(permit, "permit");
        synchronized (monitor) {
            requireOwned(permit);
            if (permit.lifecycleStateUnderOwnerLock()
                    == P7PendingPermit.LifecycleState.LIFECYCLE_TERMINATED) {
                return;
            }
            if (permit.lifecycleStateUnderOwnerLock()
                    != P7PendingPermit.LifecycleState.ACTIVE) {
                throw new P7SemanticInvariantException("enqueue failure no longer owns pending permit");
            }
            releaseActive(
                    permit, P7PendingPermit.LifecycleState.EXPLICITLY_RELEASED);
        }
    }

    void releaseAfterTask(P7PendingPermit permit) {
        Objects.requireNonNull(permit, "permit");
        synchronized (monitor) {
            requireOwned(permit);
            if (permit.lifecycleStateUnderOwnerLock()
                    == P7PendingPermit.LifecycleState.LIFECYCLE_TERMINATED) {
                return;
            }
            if (permit.lifecycleStateUnderOwnerLock()
                    != P7PendingPermit.LifecycleState.TASK_STARTED) {
                throw new P7SemanticInvariantException("pending permit was released twice");
            }
            releaseActive(
                    permit, P7PendingPermit.LifecycleState.EXPLICITLY_RELEASED);
        }
    }

    boolean tryStartTask(P7PendingPermit permit) {
        Objects.requireNonNull(permit, "permit");
        synchronized (monitor) {
            requireOwned(permit);
            if (permit.lifecycleStateUnderOwnerLock()
                    == P7PendingPermit.LifecycleState.LIFECYCLE_TERMINATED) {
                return false;
            }
            if (permit.lifecycleStateUnderOwnerLock()
                            != P7PendingPermit.LifecycleState.ACTIVE
                    || permit.serverGeneration() != serverGeneration
                    || !containsActive(permit)) {
                throw new P7SemanticInvariantException(
                        "pending permit task was started twice");
            }
            permit.markTaskStartedUnderOwnerLock();
            return true;
        }
    }

    int invalidateSession(UUID authenticatedPlayerId, long connectionEpoch) {
        Objects.requireNonNull(authenticatedPlayerId, "authenticatedPlayerId");
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException("connection epoch is invalid");
        }
        synchronized (monitor) {
            var invalidated = 0;
            var playerPermits = activePermitsByPlayer.get(authenticatedPlayerId);
            if (playerPermits == null) {
                return 0;
            }
            for (var permit : List.copyOf(playerPermits)) {
                if (permit.connectionEpoch() == connectionEpoch) {
                    releaseActive(
                            permit,
                            P7PendingPermit.LifecycleState.LIFECYCLE_TERMINATED);
                    invalidated++;
                }
            }
            return invalidated;
        }
    }

    int stopAll() {
        synchronized (monitor) {
            if (serverGeneration == Long.MAX_VALUE) {
                throw new P7SemanticInvariantException(
                        "pending permit server generation is exhausted");
            }
            var activePermits = new ArrayList<P7PendingPermit>(serverPending);
            activePermitsByPlayer.values().forEach(activePermits::addAll);
            var stopped = activePermits.size();
            for (var permit : activePermits) {
                releaseActive(
                        permit,
                        P7PendingPermit.LifecycleState.LIFECYCLE_TERMINATED);
            }
            if (serverPending != 0
                    || !perPlayerPending.isEmpty()
                    || !activePermitsByPlayer.isEmpty()) {
                throw new P7SemanticInvariantException(
                        "pending permit stop cleanup is incomplete");
            }
            serverGeneration++;
            return stopped;
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

    long captureServerGeneration() {
        synchronized (monitor) {
            return serverGeneration;
        }
    }

    private void requireOwned(P7PendingPermit permit) {
        if (permit.owner() != this) {
            throw new P7SemanticInvariantException("pending permit owner mismatch");
        }
    }

    private void releaseActive(
            P7PendingPermit permit,
            P7PendingPermit.LifecycleState terminalState) {
        if ((permit.lifecycleStateUnderOwnerLock()
                                != P7PendingPermit.LifecycleState.ACTIVE
                        && permit.lifecycleStateUnderOwnerLock()
                                != P7PendingPermit.LifecycleState.TASK_STARTED)
                || permit.accountingPermitUnderOwnerLock().released()
                || permit.serverGeneration() != serverGeneration
                || !removeActive(permit)) {
            throw new P7SemanticInvariantException("pending permit accounting mismatch");
        }
        var playerId = permit.authenticatedPlayerId();
        var playerPending = perPlayerPending.getOrDefault(playerId, 0);
        var accounting = new PendingPermitAccounting(playerPending, serverPending);
        var decision = accounting.release(permit.accountingPermitUnderOwnerLock());
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
        permit.markReleasedUnderOwnerLock(decision.nextPermit(), terminalState);
    }

    private boolean containsActive(P7PendingPermit permit) {
        var playerPermits = activePermitsByPlayer.get(
                permit.authenticatedPlayerId());
        return playerPermits != null && playerPermits.contains(permit);
    }

    private boolean removeActive(P7PendingPermit permit) {
        var playerId = permit.authenticatedPlayerId();
        var playerPermits = activePermitsByPlayer.get(playerId);
        if (playerPermits == null || !playerPermits.remove(permit)) {
            return false;
        }
        if (playerPermits.isEmpty()) {
            activePermitsByPlayer.remove(playerId);
        }
        return true;
    }
}
