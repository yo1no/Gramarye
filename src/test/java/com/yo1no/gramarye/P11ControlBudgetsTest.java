package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11ControlBudgets.RateResult.ACCEPTED;
import static com.yo1no.gramarye.P11ControlBudgets.RateResult.CLOCK_UNAVAILABLE;
import static com.yo1no.gramarye.P11ControlBudgets.RateResult.RATE_LIMITED;
import static com.yo1no.gramarye.P11ControlBudgets.Root.COMMAND_CONTEXT;
import static com.yo1no.gramarye.P11ControlBudgets.Root.NATIVE_CREDIT;
import static com.yo1no.gramarye.P11ControlBudgets.Root.OPERATION;
import static com.yo1no.gramarye.P11ControlBudgets.Root.TRANSITION;
import static com.yo1no.gramarye.P11ControlBudgets.Root.WORK;
import static com.yo1no.gramarye.P11ControlBudgets.Root.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Direct scalar production tests; these are not native, writer, or offline integration evidence. */
final class P11ControlBudgetsTest {
    @Test
    void globalWakeupCoalescesPacketsAndRetainsArrivalAcrossEmptyPoll() {
        var wake = new P11ControlBudgets.MainWakeup();
        assertTrue(wake.request());
        for (int i = 0; i < 1_000; i++) { assertFalse(wake.request()); }
        assertTrue(wake.beginQueued());
        assertFalse(wake.beginQueued());
        assertFalse(wake.beginTick(false));
        assertFalse(wake.request());
        assertTrue(wake.complete(true), "arrival during service reserves one successor wakeup");
        assertFalse(wake.request());
        assertTrue(wake.beginQueued());
        assertFalse(wake.complete(true));
        assertTrue(wake.request());
    }

    @Test
    void globalWakeupBudgetExhaustionWaitsForRealTickAndKeepsQueuedReservation() {
        var wake = new P11ControlBudgets.MainWakeup();
        assertTrue(wake.request());
        assertTrue(wake.beginTick(true));
        assertFalse(wake.request());
        assertFalse(wake.complete(false));
        assertFalse(wake.request());
        // The existing native task may still execute, but cannot replenish the dispatcher.
        assertTrue(wake.beginQueued());
        assertFalse(wake.request());
        assertFalse(wake.complete(false));
        assertFalse(wake.request());
        assertTrue(wake.beginTick(true));
        assertFalse(wake.complete(true));
        assertTrue(wake.request());
        wake.retire();
        assertFalse(wake.beginQueued());
        assertFalse(wake.request());
        assertFalse(wake.beginTick(true));
    }

    @Test
    void dispatcherQueuedAndTickPumpsShareExactQuantaWithoutSyntheticTicks() {
        var dispatcher = new P11ControlBudgets.FairDispatcher(1, 2);
        var member = dispatcher.register(1).orElseThrow();
        assertTrue(dispatcher.eligible(member));
        for (int i = 0; i < 2; i++) {
            assertTrue(dispatcher.budgetRemaining(30));
            assertTrue(dispatcher.complete(dispatcher.poll(30).orElseThrow(), true));
        }
        assertFalse(dispatcher.budgetRemaining(30));
        assertTrue(dispatcher.poll(30).isEmpty());
        assertTrue(dispatcher.budgetRemaining(31));
        assertTrue(dispatcher.complete(dispatcher.poll(31).orElseThrow(), false));
        assertThrows(IllegalArgumentException.class, () -> dispatcher.budgetRemaining(30));
    }

    @Test
    void separateBucketsPreserveFractionalRefillAndNeverShareTryOrStatusCredit() {
        var tries = new P11ControlBudgets.TokenBucket(2, 2, 0);
        var statuses = new P11ControlBudgets.TokenBucket(4, 4, 0);
        assertEquals(ACCEPTED, tries.take(0));
        assertEquals(ACCEPTED, tries.take(0));
        assertEquals(RATE_LIMITED, tries.take(0));
        assertEquals(RATE_LIMITED, tries.take(249));
        assertEquals(RATE_LIMITED, tries.take(499));
        assertEquals(ACCEPTED, tries.take(500));
        assertEquals(RATE_LIMITED, tries.take(500));
        for (int index = 0; index < 4; index++) {
            assertEquals(ACCEPTED, statuses.take(0));
        }
        assertEquals(RATE_LIMITED, statuses.take(0));
        assertEquals(ACCEPTED, statuses.take(250));
    }

    @Test
    void tokenRefillSaturatesBeforeAnyLongProductAndSupportsExactIntMaximum() {
        var largeRate = new P11ControlBudgets.TokenBucket(1, Integer.MAX_VALUE, 0);
        assertEquals(ACCEPTED, largeRate.take(0));
        assertEquals(RATE_LIMITED, largeRate.take(0));
        // Long.MAX_VALUE * Integer.MAX_VALUE would overflow: only bounded fill is evaluated.
        assertEquals(ACCEPTED, largeRate.take(Long.MAX_VALUE));
        assertEquals(RATE_LIMITED, largeRate.take(Long.MAX_VALUE));

        var largeBurst = new P11ControlBudgets.TokenBucket(
                Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
        assertEquals(ACCEPTED, largeBurst.take(0));
        assertEquals(ACCEPTED, largeBurst.take(Long.MAX_VALUE));
        assertEquals(ACCEPTED, largeBurst.take(Long.MAX_VALUE));
        var endOfClock = new P11ControlBudgets.TokenBucket(1, 1, Long.MAX_VALUE - 1_000L);
        assertEquals(ACCEPTED, endOfClock.take(Long.MAX_VALUE - 1_000L));
        assertEquals(ACCEPTED, endOfClock.take(Long.MAX_VALUE));
    }

    @Test
    void tokenUnknownDoesNotResetCreditsAndRollbackDoesNotCreateAFreshClock() {
        var bucket = new P11ControlBudgets.TokenBucket(1, 1, 10);
        assertEquals(ACCEPTED, bucket.take(10));
        assertEquals(CLOCK_UNAVAILABLE, bucket.take(-1));
        assertEquals(RATE_LIMITED, bucket.take(10));
        assertEquals(CLOCK_UNAVAILABLE, bucket.take(9));
        assertEquals(CLOCK_UNAVAILABLE, bucket.take(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> bucket.take(-2));
        assertThrows(IllegalArgumentException.class,
                () -> new P11ControlBudgets.TokenBucket(0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new P11ControlBudgets.TokenBucket(1, 0, 0));
    }

    @Test
    void readinessRefillsWithoutSpendingCreditOrRecoveringARegressedClock() {
        var bucket = new P11ControlBudgets.TokenBucket(1, 2, 0);
        for (int index = 0; index < 100; index++) { assertEquals(ACCEPTED, bucket.availability(0)); }
        assertEquals(ACCEPTED, bucket.take(0));
        assertEquals(RATE_LIMITED, bucket.availability(499));
        for (int index = 0; index < 100; index++) { assertEquals(ACCEPTED, bucket.availability(500)); }
        assertEquals(ACCEPTED, bucket.take(500));
        assertEquals(RATE_LIMITED, bucket.take(500));
        assertEquals(CLOCK_UNAVAILABLE, bucket.availability(499));
        assertEquals(CLOCK_UNAVAILABLE, bucket.take(Long.MAX_VALUE));
    }

    @Test
    void admissionWaitStartsOnlyOnRefusalAndRetriesOrUnknownCannotRestartIt() {
        var wait = new P11ControlBudgets.AdmissionWait(30_000L);
        assertEquals(P11ControlBudgets.WaitResult.NOT_STARTED, wait.observe(999_999L));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, wait.refused(100));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, wait.refused(20_000L));
        assertEquals(P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE, wait.observe(-1));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, wait.observe(30_099L));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, wait.observe(30_100L));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, wait.refused(30_101L));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, wait.observe(-1));
    }

    @Test
    void waitRollbackAndUnknownFirstRefusalFailClosedWithoutStartingANewInterval() {
        var wait = new P11ControlBudgets.AdmissionWait(100);
        assertEquals(P11ControlBudgets.WaitResult.WAITING, wait.refused(50));
        assertEquals(P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE, wait.observe(49));
        assertEquals(P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE, wait.refused(500));
        var unknown = new P11ControlBudgets.AdmissionWait(100);
        assertEquals(P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE, unknown.refused(-1));
        assertEquals(P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE, unknown.refused(0));
        assertEquals(P11ControlBudgets.WaitResult.CLOCK_UNAVAILABLE,
                unknown.observe(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> unknown.observe(-2));
    }

    @Test
    void waitComparesElapsedWithoutOverflowAtLongBoundary() {
        var wait = new P11ControlBudgets.AdmissionWait(10);
        assertEquals(P11ControlBudgets.WaitResult.WAITING, wait.refused(Long.MAX_VALUE - 10));
        assertEquals(P11ControlBudgets.WaitResult.WAITING, wait.observe(Long.MAX_VALUE - 1));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, wait.observe(Long.MAX_VALUE));
        var maximum = new P11ControlBudgets.AdmissionWait(Long.MAX_VALUE);
        assertEquals(P11ControlBudgets.WaitResult.WAITING, maximum.refused(0));
        assertEquals(P11ControlBudgets.WaitResult.EXPIRED, maximum.observe(Long.MAX_VALUE));
    }

    @Test
    void dispatcherRoundsRobinWithinOneGlobalTickBudgetAndAppendsArrivals() {
        var dispatcher = new P11ControlBudgets.FairDispatcher(4, 2);
        var a = dispatcher.register(1).orElseThrow();
        var b = dispatcher.register(2).orElseThrow();
        var c = dispatcher.register(3).orElseThrow();
        assertTrue(dispatcher.eligible(a));
        assertTrue(dispatcher.eligible(b));
        assertTrue(dispatcher.eligible(c));
        var order = new ArrayList<Long>();
        for (long tick = 0; tick < 3; tick++) {
            for (int quantum = 0; quantum < 2; quantum++) {
                var work = dispatcher.poll(tick).orElseThrow();
                order.add(work.connectionId());
                assertTrue(dispatcher.complete(work, true));
            }
            assertTrue(dispatcher.poll(tick).isEmpty());
        }
        assertEquals(List.of(1L, 2L, 3L, 1L, 2L, 3L), order);
        var d = dispatcher.register(Long.MAX_VALUE).orElseThrow();
        assertTrue(dispatcher.eligible(d));
        assertEquals(1L, dispatcher.poll(3).orElseThrow().connectionId());
        assertEquals(2L, dispatcher.poll(3).orElseThrow().connectionId());
        assertEquals(3L, dispatcher.poll(4).orElseThrow().connectionId());
        assertEquals(Long.MAX_VALUE, dispatcher.poll(4).orElseThrow().connectionId());
        assertEquals(4, dispatcher.members());
        assertTrue(dispatcher.register(5).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> dispatcher.poll(3));
    }

    @Test
    void dispatcherDeduplicatesEligibilityAndRejectsForeignRetiredAndStaleDispatches() {
        var dispatcher = new P11ControlBudgets.FairDispatcher(1, Integer.MAX_VALUE);
        var other = new P11ControlBudgets.FairDispatcher(1, 1);
        var member = dispatcher.register(1).orElseThrow();
        assertTrue(dispatcher.register(1).isEmpty());
        assertFalse(other.eligible(member));
        assertTrue(dispatcher.eligible(member));
        assertTrue(dispatcher.eligible(member));
        assertEquals(1, dispatcher.queued());
        var dispatch = dispatcher.poll(Long.MAX_VALUE).orElseThrow();
        assertTrue(dispatcher.poll(Long.MAX_VALUE).isEmpty());
        assertFalse(other.complete(dispatch, true));
        assertTrue(dispatcher.retire(member));
        var successor = dispatcher.register(1).orElseThrow();
        assertFalse(dispatcher.complete(dispatch, true));
        assertFalse(dispatcher.eligible(member));
        assertFalse(dispatcher.retire(member));
        assertTrue(dispatcher.eligible(successor));
        assertEquals(1, dispatcher.queued());
    }

    @Test
    void dispatcherRetainsAnArrivalBetweenPollAndCompletionWithoutCallbackUnderLock()
            throws Exception {
        var dispatcher = new P11ControlBudgets.FairDispatcher(1, 2);
        var member = dispatcher.register(1).orElseThrow();
        assertTrue(dispatcher.eligible(member));
        var dispatched = new CountDownLatch(1);
        var arrived = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var service = executor.submit(() -> {
                var dispatch = dispatcher.poll(0).orElseThrow();
                dispatched.countDown();
                assertTrue(arrived.await(5, TimeUnit.SECONDS));
                return dispatcher.complete(dispatch, false);
            });
            var ingress = executor.submit(() -> {
                assertTrue(dispatched.await(5, TimeUnit.SECONDS));
                assertTrue(dispatcher.eligible(member));
                arrived.countDown();
                return true;
            });
            assertTrue(ingress.get(5, TimeUnit.SECONDS));
            assertTrue(service.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, dispatcher.queued());
        var second = dispatcher.poll(0).orElseThrow();
        assertTrue(dispatcher.complete(second, false));
        assertFalse(dispatcher.complete(second, true));
        assertEquals(0, dispatcher.queued());
    }

    @Test
    void allRootCategoriesDeduplicateOneUuidAndExactOwnersMakeReleaseIdempotent() {
        var resources = new P11ControlBudgets.Resources(limits(1, 1, 2, 10, 1));
        var owner = resources.newAccountOwner(id(1));
        var roots = new ArrayList<P11ControlBudgets.Resources.RootReservation>();
        for (var root : List.of(WORK, NATIVE_CREDIT, OPERATION, COMMAND_CONTEXT, WRITE, TRANSITION)) {
            var reservation = resources.tryAcquireRoot(owner, root, false).orElseThrow();
            assertSame(reservation, resources.tryAcquireRoot(owner, root, false).orElseThrow());
            roots.add(reservation);
        }
        var dirty = resources.markDirty(owner, 0).orElseThrow();
        assertEquals(1, resources.counts().retainedUuids());
        assertTrue(resources.tryAcquireRoot(resources.newAccountOwner(id(1)), WORK, false).isEmpty());
        assertTrue(resources.tryAcquireRoot(resources.newAccountOwner(id(2)), WORK, false).isEmpty());
        var foreign = new P11ControlBudgets.Resources(limits(1, 1, 2, 10, 1));
        for (var root : roots) {
            assertFalse(foreign.releaseRoot(root));
            assertTrue(resources.releaseRoot(root));
            assertFalse(resources.releaseRoot(root));
            assertEquals(1, resources.counts().retainedUuids());
        }
        assertTrue(resources.releaseDirty(dirty));
        assertFalse(resources.releaseDirty(dirty));
        assertEquals(0, resources.counts().retainedUuids());
        assertTrue(resources.tryAcquireRoot(owner, WORK, false).isEmpty());
        assertTrue(resources.tryAcquireRoot(resources.newAccountOwner(id(1)), WORK, false).isPresent());
    }

    @Test
    void waitingCapacityIsIndependentFromUuidUnionAndExactOwnerChecked() {
        var resources = new P11ControlBudgets.Resources(limits(1, 2, 1, 10, 1));
        var owner = resources.newAccountOwner(id(1));
        var root = resources.tryAcquireRoot(owner, WORK, false).orElseThrow();
        var connection = resources.newConnectionOwner(1);
        var first = resources.tryAcquireWaiting(connection).orElseThrow();
        assertSame(first, resources.tryAcquireWaiting(connection).orElseThrow());
        assertTrue(resources.tryAcquireWaiting(resources.newConnectionOwner(1)).isEmpty());
        var second = resources.tryAcquireWaiting(resources.newConnectionOwner(2)).orElseThrow();
        assertTrue(resources.tryAcquireWaiting(resources.newConnectionOwner(3)).isEmpty());
        assertEquals(1, resources.counts().retainedUuids());
        assertEquals(2, resources.counts().waitingConnections());
        var foreign = new P11ControlBudgets.Resources(limits(1, 2, 1, 10, 1));
        assertFalse(foreign.releaseWaiting(first));
        assertTrue(resources.releaseWaiting(first));
        assertFalse(resources.releaseWaiting(first));
        assertTrue(resources.tryAcquireWaiting(connection).isEmpty());
        assertTrue(resources.releaseRoot(root));
        assertEquals(0, resources.counts().retainedUuids());
        assertEquals(1, resources.counts().waitingConnections());
        assertTrue(resources.releaseWaiting(second));
    }

    @Test
    void sealedReservationsCountEveryPhaseBoundBytesAndPermitOnlyOneInFlight() {
        var resources = new P11ControlBudgets.Resources(limits(1, 1, 3, 10, 1));
        var owner = resources.newAccountOwner(id(1));
        var work = resources.tryAcquireRoot(owner, WORK, false).orElseThrow();
        assertTrue(resources.tryReserveSealed(owner, 1).isEmpty());
        var write = resources.retainRoot(owner, WRITE).orElseThrow();
        var first = resources.tryReserveSealed(owner, 6).orElseThrow();
        var second = resources.tryReserveSealed(owner, 4).orElseThrow();
        assertTrue(resources.tryReserveSealed(owner, 1).isEmpty());
        assertFalse(resources.start(first));
        assertTrue(resources.queue(first));
        assertFalse(resources.queue(first));
        assertTrue(resources.queue(second));
        assertTrue(resources.start(first));
        assertFalse(resources.start(first));
        assertFalse(resources.start(second));
        assertFalse(resources.releaseRoot(write));
        assertEquals(new P11ControlBudgets.ResourceCounts(1, 0, 2, 10, 1, 0), resources.counts());
        var foreign = new P11ControlBudgets.Resources(limits(1, 1, 3, 10, 1));
        assertFalse(foreign.releaseSealed(first));
        assertTrue(resources.releaseSealed(first));
        assertFalse(resources.releaseSealed(first));
        assertTrue(resources.start(second));
        assertTrue(resources.releaseSealed(second));
        assertFalse(resources.start(second));
        assertTrue(resources.releaseRoot(write));
        assertTrue(resources.releaseRoot(work));
        assertEquals(new P11ControlBudgets.ResourceCounts(0, 0, 0, 0, 0, 0), resources.counts());
    }

    @Test
    void sealedLongMaximumBytesNeverOverflowAndSnapshotCountIsIndependent() {
        var resources = new P11ControlBudgets.Resources(limits(1, 1, 2, Long.MAX_VALUE, 1));
        var owner = resources.newAccountOwner(id(1));
        resources.tryAcquireRoot(owner, WRITE, false).orElseThrow();
        var first = resources.tryReserveSealed(owner, Long.MAX_VALUE - 1).orElseThrow();
        assertTrue(resources.tryReserveSealed(owner, 2).isEmpty());
        var second = resources.tryReserveSealed(owner, 1).orElseThrow();
        assertEquals(Long.MAX_VALUE, resources.counts().sealedBytes());
        assertTrue(resources.tryReserveSealed(owner, 1).isEmpty());
        assertTrue(resources.releaseSealed(first));
        var replacement = resources.tryReserveSealed(owner, 1).orElseThrow();
        assertEquals(2, resources.counts().sealedBytes());
        assertTrue(resources.tryReserveSealed(owner, 1).isEmpty());
        assertTrue(resources.releaseSealed(second));
        assertTrue(resources.releaseSealed(replacement));
        assertThrows(IllegalArgumentException.class, () -> resources.tryReserveSealed(owner, 0));
    }

    @Test
    void repeatedAggregateWorkCannotBypassNewWorkDirtyAdmission() {
        var resources = new P11ControlBudgets.Resources(limits(2, 1, 1, 10, 1));
        var owner = resources.newAccountOwner(id(1));
        assertTrue(resources.mayAdmitWork(owner));
        var work = resources.tryAcquireRoot(owner, WORK, true).orElseThrow();
        assertTrue(resources.mayAdmitWork(owner));
        var dirty = resources.markDirty(owner, 0).orElseThrow();
        // Idempotent root lookup is not permission to accept another P5 work.
        assertSame(work, resources.tryAcquireRoot(owner, WORK, true).orElseThrow());
        assertFalse(resources.mayAdmitWork(owner));
        assertFalse(resources.mayAdmitWork(resources.newAccountOwner(id(2))));
        assertTrue(resources.retainRoot(owner, OPERATION).isPresent());
        assertTrue(resources.retainRoot(owner, NATIVE_CREDIT).isPresent());
        assertTrue(resources.releaseDirty(dirty));
        assertTrue(resources.mayAdmitWork(owner));
        assertEquals(1, resources.counts().retainedUuids());
    }

    @Test
    void newWorkChecksExactAccountCapacityRetirementAndNeverMintsMembership() {
        var resources = new P11ControlBudgets.Resources(limits(1, 1, 1, 10, 1));
        var owner = resources.newAccountOwner(id(1));
        var foreign = new P11ControlBudgets.Resources(limits(1, 1, 1, 10, 1));
        assertFalse(resources.mayAdmitWork(null));
        assertFalse(resources.mayAdmitWork(foreign.newAccountOwner(id(1))));
        assertTrue(resources.mayAdmitWork(owner));
        assertEquals(0, resources.counts().retainedUuids());
        var work = resources.tryAcquireRoot(owner, WORK, true).orElseThrow();
        assertTrue(resources.mayAdmitWork(owner));
        assertFalse(resources.mayAdmitWork(resources.newAccountOwner(id(1))));
        assertFalse(resources.mayAdmitWork(resources.newAccountOwner(id(2))));
        assertTrue(resources.releaseRoot(work));
        assertFalse(resources.mayAdmitWork(owner));
        var next = resources.newAccountOwner(id(1));
        assertTrue(resources.mayAdmitWork(next));
        resources.retireSlot();
        assertFalse(resources.mayAdmitWork(next));
    }

    @Test
    void dirtyWatermarkRefusesOnlyNewDirtyWorkAndAgeNeverEvictsExistingObligations() {
        var resources = new P11ControlBudgets.Resources(limits(4, 3, 2, 8_388_608, 3));
        var owners = new ArrayList<P11ControlBudgets.Resources.AccountOwner>();
        var work = new ArrayList<P11ControlBudgets.Resources.RootReservation>();
        for (int index = 1; index <= 4; index++) {
            var owner = resources.newAccountOwner(id(index));
            owners.add(owner);
            work.add(resources.tryAcquireRoot(owner, WORK, true).orElseThrow());
        }
        for (int index = 0; index < 3; index++) {
            resources.markDirty(owners.get(index), index).orElseThrow();
        }
        assertTrue(resources.tryAcquireRoot(owners.get(3), OPERATION, true).isEmpty());
        assertTrue(resources.retainRoot(owners.get(3), OPERATION).isPresent());
        // This mutation belongs to already admitted work; the watermark does not discard it.
        var older = resources.markDirty(owners.get(3), 3).orElseThrow();
        var latest = resources.markDirty(owners.get(3), 5).orElseThrow();
        assertFalse(resources.releaseDirty(older));
        assertEquals(4, resources.counts().dirtyUuids());
        var age = resources.dirtyAge(10_000);
        assertEquals(10_000L, age.oldestMillis().orElseThrow());
        assertTrue(age.warning());
        assertFalse(age.clockUnavailable());
        for (var root : work) {
            assertTrue(resources.releaseRoot(root));
        }
        assertEquals(4, resources.counts().retainedUuids());
        assertTrue(resources.releaseDirty(latest));
        assertEquals(3, resources.counts().dirtyUuids());
        assertEquals(4, resources.counts().retainedUuids()); // retained OPERATION on fourth UUID
    }

    @Test
    void dirtyUnknownAndRollbackCannotBecomeAnArtificialYoungAge() {
        var resources = new P11ControlBudgets.Resources(limits(2, 1, 1, 10, 2));
        var owner = resources.newAccountOwner(id(1));
        resources.tryAcquireRoot(owner, WORK, false).orElseThrow();
        resources.markDirty(owner, Long.MAX_VALUE - 10_000).orElseThrow();
        assertTrue(resources.dirtyAge(-1).clockUnavailable());
        assertEquals(10_000L, resources.dirtyAge(Long.MAX_VALUE).oldestMillis().orElseThrow());
        assertTrue(resources.dirtyAge(Long.MAX_VALUE - 1).clockUnavailable());
        assertTrue(resources.dirtyAge(Long.MAX_VALUE).oldestMillis().isEmpty());
        var unknown = resources.newAccountOwner(id(2));
        resources.tryAcquireRoot(unknown, WORK, false).orElseThrow();
        resources.markDirty(unknown, -1).orElseThrow();
        assertEquals(2, resources.counts().dirtyUuids());
        assertTrue(resources.dirtyAge(Long.MAX_VALUE).clockUnavailable());
    }

    @Test
    void trustedTransferPreservesActualOverBudgetObligationsAndRejectsNewAdmissions() {
        var source = new P11ControlBudgets.Resources(limits(2, 2, 2, 20, 2));
        var first = source.newAccountOwner(id(1));
        var second = source.newAccountOwner(id(2));
        var firstWork = source.tryAcquireRoot(first, WORK, false).orElseThrow();
        var secondWork = source.tryAcquireRoot(second, WORK, false).orElseThrow();
        var write = source.retainRoot(first, WRITE).orElseThrow();
        var dirty = source.markDirty(first, 1).orElseThrow();
        var seal = source.tryReserveSealed(first, 20).orElseThrow();
        assertTrue(source.queue(seal));
        assertTrue(source.start(seal));
        var waitingOne = source.tryAcquireWaiting(source.newConnectionOwner(1)).orElseThrow();
        var waitingTwo = source.tryAcquireWaiting(source.newConnectionOwner(2)).orElseThrow();
        var target = source.newTransferTarget(limits(1, 1, 1, 10, 1));
        var unrelated = new P11ControlBudgets.Resources(limits(1, 1, 1, 10, 1));
        assertFalse(source.transferTo(unrelated));
        assertTrue(source.transferTo(target));
        assertEquals(new P11ControlBudgets.ResourceCounts(2, 2, 1, 20, 1, 1), target.counts());
        assertEquals(new P11ControlBudgets.ResourceCounts(0, 0, 0, 0, 0, 0), source.counts());
        assertFalse(source.releaseRoot(firstWork));
        assertFalse(source.releaseDirty(dirty));
        assertFalse(source.releaseSealed(seal));
        assertFalse(source.releaseWaiting(waitingOne));
        assertFalse(source.transferTo(target));
        assertThrows(IllegalStateException.class, () -> source.newAccountOwner(id(3)));
        assertTrue(target.tryAcquireRoot(target.newAccountOwner(id(3)), WORK, false).isEmpty());
        assertTrue(target.tryAcquireRoot(first, OPERATION, false).isEmpty());
        assertTrue(target.tryAcquireWaiting(target.newConnectionOwner(3)).isEmpty());
        assertTrue(target.tryReserveSealed(first, 1).isEmpty());
        // Exact repeated reservations remain usable; required continuation retains a new root.
        assertSame(firstWork, target.tryAcquireRoot(first, WORK, false).orElseThrow());
        var continuation = target.retainRoot(second, TRANSITION).orElseThrow();
        assertTrue(target.releaseRoot(continuation));
        assertTrue(target.releaseRoot(secondWork));
        assertTrue(target.releaseSealed(seal));
        assertTrue(target.releaseDirty(dirty));
        assertTrue(target.releaseRoot(write));
        assertTrue(target.releaseRoot(firstWork));
        assertTrue(target.releaseWaiting(waitingOne));
        assertTrue(target.releaseWaiting(waitingTwo));
        assertEquals(new P11ControlBudgets.ResourceCounts(0, 0, 0, 0, 0, 0), target.counts());
        assertTrue(target.tryAcquireRoot(target.newAccountOwner(id(3)), WORK, true).isPresent());
    }

    @Test
    void retainedRootsCannotMintANewUuidAndTransferRequiresAnEmptySameDomainTarget() {
        var source = new P11ControlBudgets.Resources(limits(2, 1, 1, 10, 1));
        var unadmitted = source.newAccountOwner(id(1));
        assertTrue(source.retainRoot(unadmitted, WORK).isEmpty());
        var target = source.newTransferTarget(limits(1, 1, 1, 10, 1));
        target.tryAcquireRoot(target.newAccountOwner(id(2)), WORK, false).orElseThrow();
        assertFalse(source.transferTo(target));
        assertFalse(source.transferTo(source));
        assertFalse(source.transferTo(null));
        assertEquals(1, target.counts().retainedUuids());
    }

    @Test
    void slotRetirementFencesAllLateResourceTokensWithoutClaimingObligationsFinished() {
        var resources = new P11ControlBudgets.Resources(limits(1, 1, 1, 10, 1));
        var owner = resources.newAccountOwner(id(1));
        var work = resources.tryAcquireRoot(owner, WORK, false).orElseThrow();
        resources.retainRoot(owner, WRITE).orElseThrow();
        var dirty = resources.markDirty(owner, 0).orElseThrow();
        var seal = resources.tryReserveSealed(owner, 10).orElseThrow();
        assertTrue(resources.queue(seal));
        var connection = resources.newConnectionOwner(1);
        var waiting = resources.tryAcquireWaiting(connection).orElseThrow();
        var target = resources.newTransferTarget(limits(1, 1, 1, 10, 1));
        var beforeStop = resources.counts();
        assertTrue(resources.retireSlot());
        assertFalse(resources.retireSlot());
        assertTrue(resources.tryAcquireRoot(owner, WORK, false).isEmpty());
        assertTrue(resources.retainRoot(owner, TRANSITION).isEmpty());
        assertTrue(resources.markDirty(owner, 1).isEmpty());
        assertTrue(resources.tryAcquireWaiting(connection).isEmpty());
        assertTrue(resources.tryReserveSealed(owner, 1).isEmpty());
        assertFalse(resources.queue(seal));
        assertFalse(resources.start(seal));
        assertFalse(resources.releaseRoot(work));
        assertFalse(resources.releaseDirty(dirty));
        assertFalse(resources.releaseSealed(seal));
        assertFalse(resources.releaseWaiting(waiting));
        assertFalse(resources.transferTo(target));
        assertThrows(IllegalStateException.class,
                () -> resources.newTransferTarget(limits(1, 1, 1, 10, 1)));
        assertEquals(beforeStop, resources.counts());
        assertTrue(resources.dirtyAge(10_000).clockUnavailable());
        assertEquals(0, target.counts().retainedUuids());
    }

    @Test
    void dispatcherSlotRetirementRejectsInflightAndQueuedOwnersAndNeverReopens() {
        var dispatcher = new P11ControlBudgets.FairDispatcher(2, 2);
        var first = dispatcher.register(1).orElseThrow();
        var second = dispatcher.register(2).orElseThrow();
        assertTrue(dispatcher.eligible(first));
        assertTrue(dispatcher.eligible(second));
        var dispatch = dispatcher.poll(0).orElseThrow();
        assertTrue(dispatcher.retireSlot());
        assertFalse(dispatcher.retireSlot());
        assertFalse(dispatcher.complete(dispatch, true));
        assertFalse(dispatcher.eligible(second));
        assertFalse(dispatcher.retire(first));
        assertTrue(dispatcher.register(1).isEmpty());
        assertTrue(dispatcher.poll(Long.MAX_VALUE).isEmpty());
        assertEquals(0, dispatcher.members());
        assertEquals(0, dispatcher.queued());
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }

    private static P11StartupLimits limits(int uuids, int waiting, int sealed, long bytes, int dirty) {
        return new P11StartupLimits(uuids, waiting, 30_000L, 2, 2, 4, 4, 2,
                sealed, bytes, dirty, 10_000L);
    }
}
