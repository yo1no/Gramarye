package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/** Direct §62 aggregate accounting boundaries without changing the relocated S1 inventory. */
final class P8StoredCostAccountingTest {
    @Test
    void builtInAggregateProductsMeetExactLiveTrailAndSoundBoundaries() {
        var sixteenParticleProfiles = exactProduct(256L, 16L);
        var seventeenParticleProfiles = exactProduct(256L, 17L);
        var thirtyTwoTrails = exactProduct(48L, 32L);
        var thirtyThreeTrails = exactProduct(48L, 33L);
        var liveParticles = PresentationBudget.initial(
                PresentationBudget.Scope.LIVE_PARTICLE_CREDITS);
        var trailSegments = PresentationBudget.initial(
                PresentationBudget.Scope.TOTAL_TRAIL_SEGMENTS);
        var soundStarts = PresentationBudget.initial(PresentationBudget.Scope.SOUND_STARTS);

        assertEquals(PresentationCost.count(4_096L), sixteenParticleProfiles);
        assertEquals(PresentationCost.count(4_352L), seventeenParticleProfiles);
        assertEquals(PresentationCost.count(1_536L), thirtyTwoTrails);
        assertEquals(PresentationCost.count(1_584L), thirtyThreeTrails);
        assertEquals(
                PresentationBudget.Outcome.ACCEPTED,
                liveParticles.consume(sixteenParticleProfiles).outcome());
        assertRejectedUnchanged(
                liveParticles,
                seventeenParticleProfiles,
                PresentationBudget.Outcome.LIMIT_EXCEEDED);
        assertEquals(
                PresentationBudget.Outcome.ACCEPTED,
                trailSegments.consume(thirtyTwoTrails).outcome());
        assertRejectedUnchanged(
                trailSegments,
                thirtyThreeTrails,
                PresentationBudget.Outcome.LIMIT_EXCEEDED);
        assertEquals(
                PresentationBudget.Outcome.ACCEPTED,
                soundStarts.consume(PresentationCost.count(8L)).outcome());
        assertRejectedUnchanged(
                soundStarts,
                PresentationCost.count(9L),
                PresentationBudget.Outcome.LIMIT_EXCEEDED);
    }

    private static PresentationCost exactProduct(long count, long factor) {
        return assertInstanceOf(
                        PresentationCost.Arithmetic.Exact.class,
                        PresentationCost.count(count).multiply(factor))
                .value();
    }

    private static void assertRejectedUnchanged(
            PresentationBudget budget,
            PresentationCost cost,
            PresentationBudget.Outcome outcome) {
        var decision = budget.consume(cost);
        assertEquals(outcome, decision.outcome());
        assertSame(budget, decision.nextState());
    }
}
