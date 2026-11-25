package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
final class V0ProvisionMailboxTest {
    private static final V0StartupLimits LIMITS = new V0StartupLimits(1, 2, 1, 4, 16, 16, 24, 8, 1L);
    private static final SkillOwnerId A = new SkillOwnerId(new UUID(0, 903));
    private static final SkillOwnerId B = new SkillOwnerId(new UUID(0, 904));
    @TempDir Path directory;

    @Test
    void provisionAndCaptureShareOneCapacityAndOneMonotoneTicketStream() throws Exception {
        var authority = V0CatalogAuthority.open(1, LIMITS);
        var wrong = V0CatalogAuthority.open(1, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var request = new V0ProvisionRequest(authority, 1, A);
        var result = refusal(authority);
        assertFalse(mailbox.submitProvision(new V0ProvisionRequest(wrong, 1, A)));
        assertTrue(mailbox.submitProvision(request));
        assertFalse(mailbox.submit(capture(authority, 2)));
        assertTrue(mailbox.takeProvisionRequest().isEmpty());
        var taken = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        var worker = new FutureTask<>(() -> {
            assertSame(request, mailbox.takeProvisionRequest().orElseThrow());
            assertTrue(mailbox.takeRequest().isEmpty());
            taken.countDown();
            assertTrue(finish.await(2, TimeUnit.SECONDS));
            assertFalse(mailbox.completeProvision(new V0ProvisionCompletion(wrong, 1, A, result)));
            assertFalse(mailbox.completeProvision(new V0ProvisionCompletion(authority, 2, A, result)));
            assertFalse(mailbox.completeProvision(new V0ProvisionCompletion(authority, 1, B, result)));
            assertTrue(mailbox.completeProvision(new V0ProvisionCompletion(authority, 1, A, result)));
            assertFalse(mailbox.completeProvision(new V0ProvisionCompletion(authority, 1, A, result)));
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        assertTrue(taken.await(2, TimeUnit.SECONDS));
        assertFalse(mailbox.submit(capture(authority, 2)));
        finish.countDown();
        worker.get(2, TimeUnit.SECONDS);
        assertFalse(mailbox.submit(capture(authority, 2)));
        var completion = mailbox.takeProvisionCompletion().orElseThrow();
        assertEquals(A, completion.owner());
        assertSame(result, completion.result());
        assertTrue(mailbox.takeProvisionCompletion().isEmpty());
        assertFalse(mailbox.submitProvision(request));
        assertTrue(mailbox.submit(capture(authority, 2)));
        assertFalse(mailbox.submitProvision(new V0ProvisionRequest(authority, 3, B)));
    }

    @Test
    void terminalFenceDropsQueuedProvisionAndWaitsForRunningOriginalWorker() throws Exception {
        var authority = V0CatalogAuthority.open(1, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var request = new V0ProvisionRequest(authority, 1, A);
        var result = refusal(authority);
        var taken = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        assertTrue(mailbox.submitProvision(request));
        var worker = new FutureTask<>(() -> {
            assertSame(request, mailbox.takeProvisionRequest().orElseThrow());
            taken.countDown();
            assertTrue(finish.await(2, TimeUnit.SECONDS));
            assertFalse(mailbox.retirementForColdLane());
            assertFalse(mailbox.completeProvision(new V0ProvisionCompletion(authority, 1, A, result)));
            assertTrue(mailbox.retirementForColdLane());
            assertFalse(mailbox.retirementForColdLane());
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        assertTrue(taken.await(2, TimeUnit.SECONDS));
        mailbox.fence();
        mailbox.handoffRetirement();
        finish.countDown();
        worker.get(2, TimeUnit.SECONDS);
        assertTrue(mailbox.takeProvisionCompletion().isEmpty());
        assertFalse(mailbox.submitProvision(new V0ProvisionRequest(authority, 2, B)));
    }

    @Test
    void completedProvisionRetainsOneOwnerConsumerAcrossStop() throws Exception {
        var authority = V0CatalogAuthority.open(1, LIMITS);
        var mailbox = new V0ColdMailbox(authority);
        var request = new V0ProvisionRequest(authority, 1, A);
        var result = refusal(authority);
        assertTrue(mailbox.submitProvision(request));
        var worker = new FutureTask<>(() -> {
            assertSame(request, mailbox.takeProvisionRequest().orElseThrow());
            assertTrue(mailbox.completeProvision(new V0ProvisionCompletion(authority, 1, A, result)));
            return null;
        });
        Thread.ofPlatform().daemon().start(worker);
        worker.get(2, TimeUnit.SECONDS);
        mailbox.fence();
        assertSame(result, mailbox.takeProvisionCompletion().orElseThrow().result());
        assertTrue(mailbox.takeProvisionCompletion().isEmpty());
    }

    private V0RuntimeCatalog.BootstrapReadiness refusal(V0CatalogAuthority authority) {
        // A real main-thread rejection is enough for transport; no opaque readiness is fabricated.
        return V0RuntimeCatalog.openNormal(authority, directory.resolve("gramarye/spell_catalog"))
                .ensureNormalBootstrap(authority, A);
    }

    private static V0CaptureRequest capture(V0CatalogAuthority authority, long ticket) {
        return new V0CaptureRequest(authority, ticket, A, V0RuntimeCatalog.Selection.standalone(1), 9, 4);
    }
}
