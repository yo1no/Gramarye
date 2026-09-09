package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class P8ServerConnectionAuthorityTest {
    @Test
    void catalogAttemptsAreBoundedAndBudgetDeferralDoesNotConsumeOne() {
        var owner = new P8ServerConnectionAuthority();
        var player = new UUID(0L, 4L);
        var identity = owner.open(player, 7L, 20L).orElseThrow();
        var key = owner.due(20L).getFirst();

        assertEquals(0, owner.attempts(player));
        assertTrue(owner.isCurrent(key));
        assertTrue(owner.beginAttempt(key, 20L));
        owner.failed(key, 20L);
        assertEquals(1, owner.attempts(player));
        assertTrue(owner.due(20L).isEmpty());
        assertEquals(List.of(key), owner.due(21L));

        assertTrue(owner.beginAttempt(key, 21L));
        owner.failed(key, 21L);
        assertEquals(2, owner.attempts(player));
        assertTrue(owner.due(22L).isEmpty());
        assertTrue(owner.captureReady(player, 7L).isEmpty());
        assertEquals(player, identity.playerId());
    }

    @Test
    void localSubmissionReadinessIsExactToEpochAndCatalogGeneration() {
        var owner = new P8ServerConnectionAuthority();
        var player = new UUID(0L, 8L);
        var first = owner.open(player, 1L, 0L).orElseThrow();
        var firstKey = owner.due(0L).getFirst();
        assertTrue(owner.beginAttempt(firstKey, 0L));
        owner.submitted(firstKey);
        assertEquals(first, owner.captureReady(player, 1L).orElseThrow());

        owner.scheduleAll(2L, 1L);
        assertTrue(owner.captureReady(player, 1L).isEmpty());
        assertFalse(owner.isCurrent(firstKey));
        var secondGeneration = owner.due(1L).getFirst();
        assertTrue(owner.beginAttempt(secondGeneration, 1L));
        owner.submitted(secondGeneration);
        assertTrue(owner.isCurrent(first, 2L));

        var reconnect = owner.open(player, 2L, 2L).orElseThrow();
        assertTrue(reconnect.connectionEpoch() > first.connectionEpoch());
        assertFalse(owner.isCurrent(first, 2L));
        assertTrue(owner.captureReady(player, 2L).isEmpty());
    }

    @Test
    void ownerKeepsOnlyFiveHundredTwelveCurrentScalarRecords() {
        var owner = new P8ServerConnectionAuthority();
        var retained = new ArrayList<UUID>();
        for (var index = 0;
                index < PresentationLimits.MAX_CATALOG_READINESS_RECORDS;
                index++) {
            var player = new UUID(0L, index + 1L);
            retained.add(player);
            assertTrue(owner.open(player, 1L, 0L).isPresent());
        }
        assertEquals(PresentationLimits.MAX_CATALOG_READINESS_RECORDS, owner.size());
        assertTrue(owner.open(new UUID(0L, 10_000L), 1L, 0L).isEmpty());

        owner.retainOnly(retained.subList(1, retained.size()));
        assertEquals(PresentationLimits.MAX_CATALOG_READINESS_RECORDS - 1, owner.size());
        assertTrue(owner.open(new UUID(0L, 10_000L), 1L, 0L).isPresent());
        assertEquals(PresentationLimits.MAX_CATALOG_READINESS_RECORDS, owner.size());
    }
}
