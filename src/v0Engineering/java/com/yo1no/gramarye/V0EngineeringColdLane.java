package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0EngineeringCatalogAssembly;
import com.yo1no.gramarye.magic.definition.scoped.V0EngineeringCatalogAssembly.FixturePublication;
import com.yo1no.gramarye.magic.definition.scoped.V0EngineeringCatalogAssembly.FixtureRevision;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;

/** Exactly one actor-free cold owner for both catalog writers in the private fixture. */
final class V0EngineeringColdLane {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final V0CatalogAuthority authority;
    private final V0ColdMailbox mailbox;
    private final V0RuntimeCatalog catalog;
    private final SkillOwnerId first;
    private final SkillOwnerId second;
    private final Thread thread;
    private final AtomicBoolean update = new AtomicBoolean();
    private volatile boolean ready;
    private volatile boolean finished;

    V0EngineeringColdLane(V0CatalogAuthority authority, V0ColdMailbox mailbox, V0RuntimeCatalog catalog,
            SkillOwnerId first, SkillOwnerId second) {
        this.authority = authority;
        this.mailbox = mailbox;
        this.catalog = catalog;
        this.first = first;
        this.second = second;
        thread = new Thread(this::run, "Gramarye V0 catalog");
        thread.setDaemon(false);
    }

    void start() {
        thread.start();
    }

    void wake() {
        LockSupport.unpark(thread);
    }

    void requestUpdate() {
        update.set(true);
        wake();
    }

    boolean ready() {
        return ready;
    }

    boolean finished() {
        return finished;
    }

    private void run() {
        try {
            ready = publish(first, FixtureRevision.BASE, "A") && publish(second, FixtureRevision.BASE, "B");
            while (true) {
                if (mailbox.retirementForColdLane()) {
                    retire();
                    return;
                }
                var recovery = mailbox.recoveryForColdLane().orElse(null);
                if (recovery != null) {
                    ready = catalog.recoverAndReopen(authority, recovery);
                    if (ready) {
                        mailbox.recoveredByColdLane(recovery);
                    }
                    continue;
                }
                if (ready) {
                    var request = mailbox.takeRequest().orElse(null);
                    if (request != null) {
                        prepare(request);
                        continue;
                    }
                    if (update.compareAndSet(true, false)) {
                        ready = publish(first, FixtureRevision.UPDATED_CHILD, "A")
                                && publish(second, FixtureRevision.UPDATED_CHILD, "B");
                        continue;
                    }
                }
                LockSupport.parkNanos(this, 2_000_000L);
            }
        } catch (RuntimeException | Error primary) {
            ready = false;
            try {
                catalog.suspend(authority);
                LOGGER.error("V0_ENGINEERING_COLD outcome=UNEXPECTED_FAILURE");
                while (!mailbox.retirementForColdLane()) {
                    LockSupport.parkNanos(this, 2_000_000L);
                }
                retire();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != primary) {
                    primary.addSuppressed(cleanupFailure);
                }
            }
            throw primary;
        } finally {
            ready = false;
        }
    }

    private void retire() {
        ready = false;
        var result = catalog.retire(authority);
        LOGGER.info("V0_ENGINEERING_RETIRE disposition={}", result);
        finished = result == V0RuntimeCatalog.Disposition.RELEASED
                || result == V0RuntimeCatalog.Disposition.STALE_OR_RELEASED;
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

    private boolean publish(SkillOwnerId owner, FixtureRevision revision, String role) {
        var result = V0EngineeringCatalogAssembly.publishFixture(catalog, authority, owner, revision);
        LOGGER.info("V0_ENGINEERING_PUBLICATION role={} phase={} result={}", role, revision, result);
        return result == FixturePublication.PUBLISHED;
    }
}
