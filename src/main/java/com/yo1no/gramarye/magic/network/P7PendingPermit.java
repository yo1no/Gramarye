package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;

final class P7PendingPermit {
    enum LifecycleState {
        ACTIVE,
        TASK_STARTED,
        EXPLICITLY_RELEASED,
        LIFECYCLE_TERMINATED
    }

    private final P7PendingPermitOwner owner;
    private final UUID authenticatedPlayerId;
    private final long connectionEpoch;
    private final long serverGeneration;
    private PendingPermitAccounting.Permit accountingPermit;
    private LifecycleState lifecycleState;

    P7PendingPermit(
            P7PendingPermitOwner owner,
            UUID authenticatedPlayerId,
            long connectionEpoch,
            long serverGeneration,
            PendingPermitAccounting.Permit accountingPermit) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.authenticatedPlayerId = Objects.requireNonNull(
                authenticatedPlayerId, "authenticatedPlayerId");
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException("connection epoch is invalid");
        }
        this.connectionEpoch = connectionEpoch;
        if (serverGeneration <= 0) {
            throw new P7SemanticInvariantException("server generation is invalid");
        }
        this.serverGeneration = serverGeneration;
        this.accountingPermit = Objects.requireNonNull(
                accountingPermit, "accountingPermit");
        this.lifecycleState = LifecycleState.ACTIVE;
    }

    void release() {
        owner.release(this);
    }

    void releaseAfterEnqueueFailure() {
        owner.releaseAfterEnqueueFailure(this);
    }

    void releaseAfterTask() {
        owner.releaseAfterTask(this);
    }

    boolean tryStartTask() {
        return owner.tryStartTask(this);
    }

    UUID authenticatedPlayerId() {
        return authenticatedPlayerId;
    }

    long connectionEpoch() {
        return connectionEpoch;
    }

    long serverGeneration() {
        return serverGeneration;
    }

    P7PendingPermitOwner owner() {
        return owner;
    }

    PendingPermitAccounting.Permit accountingPermitUnderOwnerLock() {
        return accountingPermit;
    }

    LifecycleState lifecycleStateUnderOwnerLock() {
        return lifecycleState;
    }

    void markTaskStartedUnderOwnerLock() {
        if (lifecycleState != LifecycleState.ACTIVE) {
            throw new P7SemanticInvariantException(
                    "pending permit task transition is invalid");
        }
        lifecycleState = LifecycleState.TASK_STARTED;
    }

    boolean released() {
        return owner.isReleased(this);
    }

    void markReleasedUnderOwnerLock(
            PendingPermitAccounting.Permit nextAccountingPermit,
            LifecycleState nextLifecycleState) {
        if ((lifecycleState != LifecycleState.ACTIVE
                        && lifecycleState != LifecycleState.TASK_STARTED)
                || nextLifecycleState == LifecycleState.ACTIVE) {
            throw new P7SemanticInvariantException(
                    "pending permit lifecycle transition is invalid");
        }
        accountingPermit = Objects.requireNonNull(
                nextAccountingPermit, "nextAccountingPermit");
        lifecycleState = Objects.requireNonNull(
                nextLifecycleState, "nextLifecycleState");
    }
}
