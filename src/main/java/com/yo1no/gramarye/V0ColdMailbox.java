package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog.Suspension;
import java.util.Objects;
import java.util.Optional;

/** Actor-free, single-flight transfer between P5 and its one owned cold lane. */
final class V0ColdMailbox {
    private final V0CatalogAuthority exactOwner;
    private boolean fenced;
    private long runningTicket;
    private long lastSubmittedTicket;
    private long coldThreadId;
    private V0CaptureRequest request;
    private V0CaptureCompletion completion;
    private V0ProvisionRequest provision;
    private V0ProvisionRequest runningProvision;
    private V0ProvisionCompletion provisionCompletion;
    private V0BootstrapCleanupRequest cleanup;
    private V0BootstrapCleanupRequest runningCleanup;
    private V0BootstrapCleanupCompletion cleanupCompletion;
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
                || request != null || provision != null || runningTicket != 0
                || completion != null || provisionCompletion != null || cleanup != null || cleanupCompletion != null
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
                || runningProvision != null || runningCleanup != null
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

    synchronized void coldTerminated(V0CaptureRequest exact) {
        if (exact.authority() == exactOwner && runningTicket == exact.ticket()
                && runningProvision == null && runningCleanup == null
                && coldThreadId == Thread.currentThread().threadId()) {
            runningTicket = 0;
        }
    }

    synchronized Optional<V0CaptureCompletion> takeCompletion() {
        if (!exactOwner.isOwnerThread() || completion == null) {
            return Optional.empty();
        }
        var result = Optional.of(completion);
        completion = null;
        return result;
    }

    synchronized boolean submitProvision(V0ProvisionRequest supplied) {
        Objects.requireNonNull(supplied, "request");
        if (!exactOwner.isOwnerThread() || supplied.authority() != exactOwner || fenced
                || request != null || provision != null || runningTicket != 0
                || completion != null || provisionCompletion != null || cleanup != null || cleanupCompletion != null
                || supplied.ticket() <= lastSubmittedTicket) {
            return false;
        }
        lastSubmittedTicket = supplied.ticket();
        provision = supplied;
        return true;
    }

    synchronized Optional<V0ProvisionRequest> takeProvisionRequest() {
        if (fenced || recovery != null || provision == null || !claimColdThread(Thread.currentThread().threadId())) {
            return Optional.empty();
        }
        runningProvision = provision;
        runningTicket = provision.ticket();
        provision = null;
        return Optional.of(runningProvision);
    }

    synchronized boolean completeProvision(V0ProvisionCompletion supplied) {
        Objects.requireNonNull(supplied, "completion");
        if (supplied.authority() != exactOwner || runningProvision == null || runningTicket != supplied.ticket()
                || !runningProvision.owner().equals(supplied.owner())
                || coldThreadId != Thread.currentThread().threadId()) {
            return false;
        }
        runningTicket = 0;
        runningProvision = null;
        if (fenced) {
            return false;
        }
        provisionCompletion = supplied;
        return true;
    }

    synchronized void coldTerminated(V0ProvisionRequest exact) {
        if (exact == runningProvision && coldThreadId == Thread.currentThread().threadId()) {
            runningTicket = 0;
            runningProvision = null;
        }
    }

    synchronized Optional<V0ProvisionCompletion> takeProvisionCompletion() {
        if (!exactOwner.isOwnerThread() || provisionCompletion == null) {
            return Optional.empty();
        }
        var result = Optional.of(provisionCompletion);
        provisionCompletion = null;
        return result;
    }

    synchronized boolean submitBootstrapCleanup(V0BootstrapCleanupRequest supplied) {
        Objects.requireNonNull(supplied, "request");
        if (supplied.authority() != exactOwner || !idleForBootstrapRetirement()
                || supplied.ticket() <= lastSubmittedTicket) {
            return false;
        }
        lastSubmittedTicket = supplied.ticket();
        cleanup = supplied;
        return true;
    }

    synchronized Optional<V0BootstrapCleanupRequest> takeBootstrapCleanupRequest() {
        if (fenced || recovery != null || cleanup == null || !claimColdThread(Thread.currentThread().threadId())) {
            return Optional.empty();
        }
        runningCleanup = cleanup;
        runningTicket = cleanup.ticket();
        cleanup = null;
        return Optional.of(runningCleanup);
    }

    synchronized boolean completeBootstrapCleanup(V0BootstrapCleanupCompletion supplied) {
        Objects.requireNonNull(supplied, "completion");
        if (supplied.authority() != exactOwner || runningCleanup == null || runningTicket != supplied.ticket()
                || !runningCleanup.owner().equals(supplied.owner())
                || coldThreadId != Thread.currentThread().threadId()) {
            return false;
        }
        runningTicket = 0;
        runningCleanup = null;
        if (fenced) {
            return false;
        }
        cleanupCompletion = supplied;
        return true;
    }

    synchronized void coldTerminated(V0BootstrapCleanupRequest exact) {
        if (exact == runningCleanup && coldThreadId == Thread.currentThread().threadId()) {
            runningTicket = 0;
            runningCleanup = null;
        }
    }

    synchronized Optional<V0BootstrapCleanupCompletion> takeBootstrapCleanupCompletion() {
        if (!exactOwner.isOwnerThread() || cleanupCompletion == null) {
            return Optional.empty();
        }
        var result = Optional.of(cleanupCompletion);
        cleanupCompletion = null;
        return result;
    }

    synchronized boolean idleForBootstrapRetirement() {
        return exactOwner.isOwnerThread() && !fenced && request == null && provision == null && cleanup == null
                && runningTicket == 0 && completion == null && provisionCompletion == null && cleanupCompletion == null
                && recovery == null && recovered == null && !retirementReady;
    }

    synchronized void fence() {
        requireOwner();
        fenced = true;
        request = null;
        provision = null;
        cleanup = null;
        recovery = null;
        recoveryTaken = null;
        recovered = null;
    }

    synchronized void handoffRecovery(Suspension suspension) {
        requireOwner();
        Objects.requireNonNull(suspension, "suspension");
        if (!fenced && recovery != suspension && recovered != suspension) {
            request = null;
            provision = null;
            cleanup = null;
            recovered = null;
            recoveryTaken = null;
            recovery = suspension;
        }
    }

    synchronized Optional<Suspension> recoveryForColdLane() {
        if (fenced || runningTicket != 0 || completion != null || provisionCompletion != null || cleanupCompletion != null
                || recovery == null || recoveryTaken == recovery
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
        if (!retirementReady || retirementTaken || runningTicket != 0 || completion != null || provisionCompletion != null
                || cleanupCompletion != null
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
