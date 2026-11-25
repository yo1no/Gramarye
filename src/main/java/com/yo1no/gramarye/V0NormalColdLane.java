package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;

/** One actor-free owner of normal catalog IO, C work, recovery and writer retirement. */
final class V0NormalColdLane {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final V0CatalogAuthority authority;
    private final V0ColdMailbox mailbox;
    private final V0RuntimeCatalog catalog;
    private final Thread thread;
    private boolean startAttempted;
    private boolean aborted;
    private boolean retirementTaken;
    private volatile boolean entered;
    private volatile boolean finished;

    V0NormalColdLane(V0CatalogAuthority authority, V0ColdMailbox mailbox, V0RuntimeCatalog catalog) {
        this.authority = authority;
        this.mailbox = mailbox;
        this.catalog = catalog;
        thread = new Thread(this::run, "Gramarye normal catalog");
        thread.setDaemon(false);
    }

    void start() {
        requireOwner(authority);
        if (startAttempted || aborted) {
            throw new IllegalStateException("Normal catalog worker cannot be restarted");
        }
        V0NormalComposition.claimStartup(this);
        startAttempted = true;
        thread.start();
    }

    void abortUnpublished(V0CatalogAuthority exact) {
        requireOwner(exact);
        if (aborted) { return; }
        aborted = true;
        // Before root publication no request can reach this mailbox. No catalog IO has been issued.
        mailbox.fence();
        mailbox.handoffRetirement();
        if (!startAttempted || !entered && thread.getState() == Thread.State.NEW) {
            finished = true;
            V0NormalComposition.releaseUnstarted(this);
        }
        wake();
    }

    private void requireOwner(V0CatalogAuthority exact) {
        if (exact != authority || !authority.isOwnerThread()) {
            throw new IllegalStateException("Normal catalog startup requires its original owner");
        }
    }

    void wake() { LockSupport.unpark(thread); }

    boolean finished() { return finished; }

    private void run() {
        entered = true;
        try {
            while (true) {
                if (mailbox.retirementForColdLane()) {
                    retirementTaken = true;
                    retire();
                    return;
                }
                var recovery = mailbox.recoveryForColdLane().orElse(null);
                if (recovery != null) {
                    if (catalog.recoverAndReopen(authority, recovery)) {
                        mailbox.recoveredByColdLane(recovery);
                    }
                    continue;
                }
                var cleanup = mailbox.takeBootstrapCleanupRequest().orElse(null);
                if (cleanup != null) {
                    cleanup(cleanup);
                    continue;
                }
                var provision = mailbox.takeProvisionRequest().orElse(null);
                if (provision != null) {
                    provision(provision);
                    continue;
                }
                var request = mailbox.takeRequest().orElse(null);
                if (request != null) {
                    prepare(request);
                    continue;
                }
                LockSupport.parkNanos(this, 2_000_000L);
            }
        } catch (RuntimeException | Error primary) {
            try {
                if (!retirementTaken) {
                    catalog.suspend(authority);
                    LOGGER.error("V0_NORMAL_COLD outcome=UNEXPECTED_FAILURE");
                    while (!mailbox.retirementForColdLane()) {
                        LockSupport.parkNanos(this, 2_000_000L);
                    }
                    retirementTaken = true;
                    retire();
                }
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != primary) {
                    try {
                        primary.addSuppressed(cleanupFailure);
                    } catch (RuntimeException | Error suppressionFailure) {
                        // Preserve the original failure even if suppression cannot allocate its bookkeeping.
                    }
                }
            }
            throw primary;
        }
    }

    private void provision(V0ProvisionRequest request) {
        boolean transferred = false;
        try {
            var result = catalog.ensureNormalBootstrap(request.authority(), request.owner());
            transferred = mailbox.completeProvision(new V0ProvisionCompletion(request.authority(), request.ticket(),
                    request.owner(), result));
        } finally {
            if (!transferred) {
                mailbox.coldTerminated(request);
            }
        }
    }

    private void cleanup(V0BootstrapCleanupRequest request) {
        boolean transferred = false;
        try {
            V0BootstrapCleanupCompletion result;
            if (request.receipts() != null) {
                var proof = catalog.prepareBootstrapSeedRetirement(request.authority(), request.receipts()).orElse(null);
                result = new V0BootstrapCleanupCompletion(request.authority(), request.ticket(), request.owner(),
                        proof == null ? V0RuntimeCatalog.BootstrapCleanup.REFUSED
                                : V0RuntimeCatalog.BootstrapCleanup.NO_MATCH, null, proof);
            } else {
                var report = catalog.cleanupNormalBootstrap(request.authority(), request.owner(), request.permit(), request.roster());
                result = new V0BootstrapCleanupCompletion(request.authority(), request.ticket(), request.owner(),
                        report.result(), report.receipt().orElse(null), null);
            }
            transferred = mailbox.completeBootstrapCleanup(result);
        } finally {
            if (!transferred) {
                mailbox.coldTerminated(request);
            }
        }
    }

    private void prepare(V0CaptureRequest request) {
        boolean transferred = false;
        try {
            var result = catalog.prepare(request.authority(), request.ticket(), request.owner(),
                    request.selection(), request.maxManifestEntries());
            transferred = mailbox.complete(new V0CaptureCompletion(request.authority(), request.ticket(), result));
        } finally {
            if (!transferred) {
                mailbox.coldTerminated(request);
            }
        }
    }

    private void retire() {
        var result = catalog.retire(authority);
        finished = result == V0RuntimeCatalog.Disposition.RELEASED
                || result == V0RuntimeCatalog.Disposition.STALE_OR_RELEASED;
        LOGGER.info("V0_NORMAL_RETIRE disposition={}", result);
    }
}
