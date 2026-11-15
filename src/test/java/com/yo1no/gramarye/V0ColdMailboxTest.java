package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
final class V0ColdMailboxTest {
    private static final V0StartupLimits LIMITS = new V0StartupLimits(1, 2, 1, 4, 16, 16, 24, 8, 1L);
    private static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(1L, 2L));

    @Test
    void exactOwnedTransferPreservesTheSingleFlightUntilItsCompletionIsTaken() throws Exception {
        var authority = V0CatalogAuthority.open(1L, LIMITS);
        var successor = V0CatalogAuthority.open(1L, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var first = request(authority, 1L);
        var second = request(authority, 2L);
        var result = new V0CaptureCompletion(authority, 1L, rejected());
        assertFalse(mailbox.submit(request(successor, 1L)));
        assertTrue(mailbox.submit(first));
        assertFalse(mailbox.submit(second));
        assertTrue(mailbox.takeRequest().isEmpty());

        var taken = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var worker = new FutureTask<>(() -> {
            assertSame(first, mailbox.takeRequest().orElseThrow());
            taken.countDown();
            assertTrue(finish.await(2L, TimeUnit.SECONDS));
            assertFalse(mailbox.complete(new V0CaptureCompletion(successor, 1L, result.result())));
            assertFalse(mailbox.complete(new V0CaptureCompletion(authority, 2L, result.result())));
            assertTrue(mailbox.complete(result));
            assertFalse(mailbox.complete(result));
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        assertTrue(taken.await(2L, TimeUnit.SECONDS));
        assertFalse(mailbox.submit(second));
        assertTrue(mailbox.takeCompletion().isEmpty());
        finish.countDown();
        worker.get(2L, TimeUnit.SECONDS);
        assertFalse(mailbox.submit(second));
        assertSame(result, mailbox.takeCompletion().orElseThrow());
        assertFalse(mailbox.submit(first));
        assertTrue(mailbox.submit(second));
    }

    @Test
    void fencingRetainsTheRunningTicketUntilItsOriginalWorkerReturns() throws Exception {
        var authority = V0CatalogAuthority.open(1L, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var first = request(authority, 1L);
        var result = new V0CaptureCompletion(authority, 1L, rejected());
        var taken = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        assertTrue(mailbox.submit(first));
        var worker = new FutureTask<>(() -> {
            assertSame(first, mailbox.takeRequest().orElseThrow());
            taken.countDown();
            assertTrue(finish.await(2L, TimeUnit.SECONDS));
            assertFalse(mailbox.retirementForColdLane());
            assertFalse(mailbox.complete(result));
            assertTrue(mailbox.retirementForColdLane());
            assertFalse(mailbox.retirementForColdLane());
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        assertTrue(taken.await(2L, TimeUnit.SECONDS));
        mailbox.fence();
        mailbox.handoffRetirement();
        assertFalse(mailbox.submit(request(authority, 2L)));
        finish.countDown();
        worker.get(2L, TimeUnit.SECONDS);
        assertTrue(mailbox.takeCompletion().isEmpty());
    }

    @Test
    void stopAfterCompletionLeavesOneExactMainThreadCleanupConsumer() throws Exception {
        var authority = V0CatalogAuthority.open(1L, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var first = request(authority, 1L);
        var result = new V0CaptureCompletion(authority, 1L, rejected());
        assertTrue(mailbox.submit(first));
        var worker = new FutureTask<>(() -> {
            assertSame(first, mailbox.takeRequest().orElseThrow());
            assertTrue(mailbox.complete(result));
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        worker.get(2L, TimeUnit.SECONDS);
        mailbox.fence();
        assertSame(result, mailbox.takeCompletion().orElseThrow());
        assertTrue(mailbox.takeCompletion().isEmpty());
        assertFalse(mailbox.submit(request(authority, 2L)));
    }

    @Test
    void newerReloadKeepsItsExactHandoffWhenThePreviousRecoveryReturnsLate() throws Exception {
        var authority = V0CatalogAuthority.open(1L, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var original = suspension();
        var successor = suspension();
        var taken = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        mailbox.handoffRecovery(original);
        var worker = new FutureTask<>(() -> {
            assertSame(original, mailbox.recoveryForColdLane().orElseThrow());
            assertTrue(mailbox.recoveryForColdLane().isEmpty());
            taken.countDown();
            assertTrue(finish.await(2L, TimeUnit.SECONDS));
            mailbox.recoveredByColdLane(original);
            assertSame(successor, mailbox.recoveryForColdLane().orElseThrow());
            mailbox.recoveredByColdLane(successor);
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        assertTrue(taken.await(2L, TimeUnit.SECONDS));
        mailbox.handoffRecovery(successor);
        finish.countDown();
        worker.get(2L, TimeUnit.SECONDS);
        assertFalse(mailbox.takeRecoveryAcknowledgment(original));
        assertTrue(mailbox.takeRecoveryAcknowledgment(successor));
        assertFalse(mailbox.takeRecoveryAcknowledgment(successor));
    }

    private static V0RuntimeCatalog.Suspension suspension() throws Exception {
        // This fixture exercises identity transport. It grants no catalog or server authority.
        var constructor = V0RuntimeCatalog.Suspension.class.getDeclaredConstructor(Object.class);
        constructor.setAccessible(true);
        return constructor.newInstance(new Object());
    }

    private static V0CaptureRequest request(V0CatalogAuthority authority, long ticket) {
        return new V0CaptureRequest(authority, ticket, OWNER, V0RuntimeCatalog.Selection.standalone(1L), 9L, 4);
    }

    private static V0RuntimeCatalog.Rejected rejected() throws Exception {
        // A transport-only fixture. Catalog tests separately prove the native refusal origin.
        var constructor = V0RuntimeCatalog.Rejected.class.getDeclaredConstructor(V0RuntimeCatalog.Refusal.class);
        constructor.setAccessible(true);
        var rejection = constructor.newInstance(V0RuntimeCatalog.Refusal.HEAD_STALE);
        assertEquals(V0RuntimeCatalog.Refusal.HEAD_STALE, rejection.reason());
        return rejection;
    }
}
