package com.yo1no.gramarye.magic.network;

import java.util.Objects;
import java.util.UUID;

final class P7PendingPermit {
    private final P7PendingPermitOwner owner;
    private final UUID authenticatedPlayerId;
    private final long connectionEpoch;
    private PendingPermitAccounting.Permit accountingPermit;

    P7PendingPermit(
            P7PendingPermitOwner owner,
            UUID authenticatedPlayerId,
            long connectionEpoch,
            PendingPermitAccounting.Permit accountingPermit) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.authenticatedPlayerId = Objects.requireNonNull(
                authenticatedPlayerId, "authenticatedPlayerId");
        if (connectionEpoch <= 0) {
            throw new P7SemanticInvariantException("connection epoch is invalid");
        }
        this.connectionEpoch = connectionEpoch;
        this.accountingPermit = Objects.requireNonNull(
                accountingPermit, "accountingPermit");
    }

    void release() {
        owner.release(this);
    }

    UUID authenticatedPlayerId() {
        return authenticatedPlayerId;
    }

    long connectionEpoch() {
        return connectionEpoch;
    }

    P7PendingPermitOwner owner() {
        return owner;
    }

    PendingPermitAccounting.Permit accountingPermitUnderOwnerLock() {
        return accountingPermit;
    }

    boolean released() {
        return owner.isReleased(this);
    }

    void markReleasedUnderOwnerLock(
            PendingPermitAccounting.Permit nextAccountingPermit) {
        accountingPermit = Objects.requireNonNull(
                nextAccountingPermit, "nextAccountingPermit");
    }
}
