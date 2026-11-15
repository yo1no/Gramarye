package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog.Suspension;
import java.util.Objects;
import java.util.Optional;

/** Actor-free, single-flight transfer between P5 and the owned engineering cold lane. */
final class V0ColdMailbox {
    private final V0CatalogAuthority exactOwner;
    private boolean fenced;
    private long runningTicket;
    private long lastSubmittedTicket;
    private long coldThreadId;
    private V0CaptureRequest request;
    private V0CaptureCompletion completion;
    private Suspension recovery;
    private Suspension recoveryTaken;
    private Suspension recovered;
    private boolean retirementReady;
    private boolean retirementTaken;

    V0ColdMailbox(V0CatalogAuthority exactOwner) {
        this.exactOwner = Objects.requireNonNull(exactOwner, "exactOwner");
    }

    synchronized boolean submit(V0CaptureRequest supplied) {
        Objects.requireNonNull(supplied, "request");
        if (!exactOwner.isOwnerThread() || supplied.authority() != exactOwner || fenced
                || request != null || runningTicket != 0 || completion != null
                || supplied.ticket() <= lastSubmittedTicket) {
            return false;
        }
        lastSubmittedTicket = supplied.ticket();
        request = supplied;
        return true;
    }

    synchronized Optional<V0CaptureRequest> takeRequest() {
        var currentThread = Thread.currentThread().threadId();
        if (fenced || recovery != null || request == null || !claimColdThread(currentThread)) {
            return Optional.empty();
        }
        var result = Optional.of(request);
        runningTicket = request.ticket();
        request = null;
        return result;
    }

    synchronized boolean complete(V0CaptureCompletion supplied) {
        Objects.requireNonNull(supplied, "completion");
        if (supplied.authority() != exactOwner || runningTicket != supplied.ticket()
                || coldThreadId != Thread.currentThread().threadId()) {
            return false;
        }
        runningTicket = 0;
        if (fenced) {
            // Ownership did not transfer. The original worker still settles any candidate.
            return false;
        }
        completion = supplied;
        return true;
    }

    synchronized Optional<V0CaptureCompletion> takeCompletion() {
        if (!exactOwner.isOwnerThread() || completion == null) {
            return Optional.empty();
        }
        var result = Optional.of(completion);
        completion = null;
        return result;
    }

    synchronized void fence() {
        requireOwner();
        fenced = true;
        request = null;
        recovery = null;
        recoveryTaken = null;
        recovered = null;
    }

    synchronized void handoffRecovery(Suspension suspension) {
        requireOwner();
        Objects.requireNonNull(suspension, "suspension");
        if (!fenced && recovery != suspension && recovered != suspension) {
            request = null;
            recovered = null;
            recoveryTaken = null;
            recovery = suspension;
        }
    }

    synchronized Optional<Suspension> recoveryForColdLane() {
        if (fenced || runningTicket != 0 || completion != null || recovery == null || recoveryTaken == recovery
                || !claimColdThread(Thread.currentThread().threadId())) {
            return Optional.empty();
        }
        var result = Optional.of(recovery);
        recoveryTaken = recovery;
        return result;
    }

    synchronized void recoveredByColdLane(Suspension suspension) {
        if (!fenced && suspension != null && recoveryTaken == suspension && recovery == suspension
                && coldThreadId == Thread.currentThread().threadId()) {
            recovered = suspension;
            recovery = null;
        }
    }

    synchronized boolean takeRecoveryAcknowledgment(Suspension suspension) {
        requireOwner();
        if (suspension == null || recovered != suspension) {
            return false;
        }
        recovered = null;
        return true;
    }

    synchronized void handoffRetirement() {
        requireOwner();
        if (!fenced) {
            throw new IllegalStateException("V0 mailbox retirement requires its terminal fence");
        }
        retirementReady = true;
    }

    synchronized boolean retirementForColdLane() {
        if (!retirementReady || retirementTaken || runningTicket != 0 || completion != null
                || !claimColdThread(Thread.currentThread().threadId())) {
            return false;
        }
        retirementTaken = true;
        return true;
    }

    private boolean claimColdThread(long currentThread) {
        if (exactOwner.isOwnerThread() || (coldThreadId != 0 && coldThreadId != currentThread)) {
            return false;
        }
        coldThreadId = currentThread;
        return true;
    }

    private void requireOwner() {
        if (!exactOwner.isOwnerThread()) {
            throw new IllegalStateException("Only P5 can settle its V0 mailbox");
        }
    }
}
