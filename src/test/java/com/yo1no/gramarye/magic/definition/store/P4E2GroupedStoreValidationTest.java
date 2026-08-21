package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class P4E2GroupedStoreValidationTest {
    @Test
    void terminalSummaryPreservesEveryBoundedDirectObservationCoordinate() {
        var summary = new P4E2ReconciliationResult.Summary(
                1,
                2,
                2,
                1,
                1,
                1,
                2,
                1,
                OptionalLong.of(7L));

        assertEquals(1, summary.recoveryEntriesCleared());
        assertEquals(2, summary.recoveryStepsReplayed());
        assertEquals(2, summary.staleLatestObserved());
        assertEquals(1, summary.staleLatestPruned());
        assertEquals(1, summary.staleEquippedObserved());
        assertEquals(1, summary.staleEquippedPruned());
        assertEquals(2, summary.missingCount());
        assertEquals(1, summary.ownerMismatchCount());
        assertEquals(OptionalLong.of(7L), summary.acceptedGeneration());
    }

    @Test
    void storeWitnessRejectsEveryExactIdentityDrift() {
        assertTrue(P4E2GroupedStoreValidation.StoreReadyWitness.identitiesCurrent(
                storeFactsExcept("none")));

        for (var coordinate : java.util.List.of(
                "service",
                "server",
                "adapter",
                "ready-state",
                "store",
                "audit",
                "coordinator")) {
            assertFalse(P4E2GroupedStoreValidation.StoreReadyWitness.identitiesCurrent(
                    storeFactsExcept(coordinate)), coordinate);
        }
    }

    @Test
    void distinctLookupOccursOnceAcrossLatestEquippedDuplicatesAndKeepsNonlatestValid() {
        var owner = StoreTestFixtures.ownerId(0xE201);
        var first = StoreTestFixtures.skillId(0xE211);
        var second = StoreTestFixtures.skillId(0xE212);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(first, owner, 0, 2),
                StoreTestFixtures.history(second, owner, 4)));
        var lookups = new HashMap<SkillId, Integer>();
        var validation = new P4E2GroupedStoreValidation(owner);

        validation.latest(0, first, Optional.of(reference(first, 0)), 7);
        validation.latest(1, second, Optional.empty(), 8);
        validation.equipped(0, 3, reference(first, 0));
        validation.equipped(1, 4, reference(first, 2));
        var result = validation.validate(skillId -> {
            lookups.merge(skillId, 1, Math::addExact);
            return store.observeExactHistoryForRootAudit(skillId);
        });

        assertArrayEquals(new int[0], result.staleLatestOrdinals());
        assertArrayEquals(new int[0], result.staleEquippedOrdinals());
        assertEquals(0, result.missingCount());
        assertEquals(0, result.ownerMismatchCount());
        assertEquals(1, result.distinctSkillIdCount());
        assertEquals(1, lookups.get(first));
        assertFalse(lookups.containsKey(second),
                "an explicit empty latest route performs no Store lookup");
        assertFalse(result.hasStaleRoutes());
        assertThrows(IllegalStateException.class,
                () -> validation.validate(store::observeExactHistoryForRootAudit));
        assertThrows(IllegalStateException.class,
                () -> validation.latest(2, second, Optional.empty(), 9));
    }

    @Test
    void missingHistoryMissingRevisionAndForeignOwnerPreserveEveryRoleCoordinate() {
        var owner = StoreTestFixtures.ownerId(0xE221);
        var foreign = StoreTestFixtures.ownerId(0xE222);
        var absent = StoreTestFixtures.skillId(0xE231);
        var missingRevision = StoreTestFixtures.skillId(0xE232);
        var foreignSkill = StoreTestFixtures.skillId(0xE233);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(missingRevision, owner, 0),
                StoreTestFixtures.history(foreignSkill, foreign, 0)));
        var lookups = new HashMap<SkillId, Integer>();
        var validation = new P4E2GroupedStoreValidation(owner);

        validation.latest(0, absent, Optional.of(reference(absent, 0)), 0);
        validation.latest(1, missingRevision,
                Optional.of(reference(missingRevision, 1)), 1);
        validation.latest(2, foreignSkill, Optional.of(reference(foreignSkill, 0)), 2);
        validation.equipped(0, 5, reference(foreignSkill, 0));
        validation.equipped(1, 6, reference(absent, 0));
        var result = validation.validate(skillId -> {
            lookups.merge(skillId, 1, Math::addExact);
            return store.observeExactHistoryForRootAudit(skillId);
        });

        assertArrayEquals(new int[] {0, 1, 2}, result.staleLatestOrdinals());
        assertArrayEquals(new int[] {0, 1}, result.staleEquippedOrdinals());
        assertEquals(3, result.missingCount());
        assertEquals(2, result.ownerMismatchCount());
        assertEquals(3, result.distinctSkillIdCount());
        assertEquals(1, lookups.get(absent));
        assertEquals(1, lookups.get(missingRevision));
        assertEquals(1, lookups.get(foreignSkill));
        assertTrue(result.hasStaleRoutes());
    }

    @Test
    void ownerMismatchIsClassifiedBeforeRevisionAndNeverExposesTheActualOwner() {
        var owner = StoreTestFixtures.ownerId(0xE241);
        var foreign = StoreTestFixtures.ownerId(0xE242);
        var routeSkill = StoreTestFixtures.skillId(0xE251);
        var mismatchedObservationSkill = StoreTestFixtures.skillId(0xE252);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(mismatchedObservationSkill, foreign, 0)));
        var mismatched = (P4E1StoreHistoryObservation.Present)
                store.observeExactHistoryForRootAudit(mismatchedObservationSkill);
        var validation = new P4E2GroupedStoreValidation(owner);
        validation.latest(0, routeSkill, Optional.of(reference(routeSkill, 99)), 0);

        var result = validation.validate(ignored -> mismatched);

        assertEquals(0, result.missingCount());
        assertEquals(1, result.ownerMismatchCount());
        assertArrayEquals(new int[] {0}, result.staleLatestOrdinals());
        assertThrows(IllegalStateException.class, () -> mismatched.ownerMatches(owner),
                "the owner-bearing observation must be cleared before the result escapes");
    }

    @Test
    void runtimeFailureClearsAllPreviouslyAcquiredHistoryObservations() {
        assertLookupFailureClearsPriorObservation(
                new IllegalStateException("runtime sentinel"));
    }

    @Test
    void errorFailurePropagatesByIdentityAndClearsPriorObservation() {
        assertLookupFailureClearsPriorObservation(new AssertionError("error sentinel"));
    }

    @Test
    void oomeFailurePropagatesByIdentityAndClearsPriorObservation() {
        assertLookupFailureClearsPriorObservation(new OutOfMemoryError("oome sentinel"));
    }

    @Test
    void projectionOrderAndRoleCoordinatesAreStrictAndBounded() {
        var owner = StoreTestFixtures.ownerId(0xE261);
        var skill = StoreTestFixtures.skillId(0xE262);
        var latest = new P4E2GroupedStoreValidation(owner);
        assertThrows(IllegalStateException.class,
                () -> latest.latest(1, skill, Optional.empty(), 0));

        var equipped = new P4E2GroupedStoreValidation(owner);
        assertThrows(IllegalStateException.class,
                () -> equipped.equipped(1, 0, reference(skill, 0)));

        var negativeGeneration = new P4E2GroupedStoreValidation(owner);
        assertThrows(IllegalStateException.class,
                () -> negativeGeneration.latest(0, skill, Optional.empty(), -1));

        var negativeSlot = new P4E2GroupedStoreValidation(owner);
        assertThrows(IllegalStateException.class,
                () -> negativeSlot.equipped(0, -1, reference(skill, 0)));
    }

    private static void assertLookupFailureClearsPriorObservation(Throwable failure) {
        var owner = StoreTestFixtures.ownerId(0xE271);
        var first = StoreTestFixtures.skillId(0xE272);
        var second = StoreTestFixtures.skillId(0xE273);
        var store = StoreTestFixtures.restore(StoreTestFixtures.snapshot(
                StoreTestFixtures.history(first, owner, 0)));
        var retained = (P4E1StoreHistoryObservation.Present)
                store.observeExactHistoryForRootAudit(first);
        var calls = new AtomicInteger();
        var validation = new P4E2GroupedStoreValidation(owner);
        validation.latest(0, first, Optional.of(reference(first, 0)), 0);
        validation.latest(1, second, Optional.of(reference(second, 0)), 0);

        try {
            validation.validate(skillId -> {
                if (calls.getAndIncrement() == 0) {
                    assertEquals(first, skillId);
                    return retained;
                }
                assertEquals(second, skillId);
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw new AssertionError("unexpected failure type", failure);
            });
            fail("lookup failure did not escape");
        } catch (Throwable observed) {
            assertSame(failure, observed);
        }

        assertEquals(2, calls.get());
        assertThrows(IllegalStateException.class, () -> retained.ownerMatches(owner));
        assertThrows(IllegalStateException.class,
                () -> validation.validate(store::observeExactHistoryForRootAudit));
    }

    private static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, StoreTestFixtures.revision(revision));
    }

    private static P4E2GroupedStoreValidation.StoreReadyWitness.StoreCurrentnessFacts
            storeFactsExcept(String drift) {
        return new P4E2GroupedStoreValidation.StoreReadyWitness.StoreCurrentnessFacts(
                !drift.equals("service"),
                !drift.equals("server"),
                !drift.equals("adapter"),
                !drift.equals("ready-state"),
                !drift.equals("store"),
                !drift.equals("audit"),
                !drift.equals("coordinator"));
    }
}
