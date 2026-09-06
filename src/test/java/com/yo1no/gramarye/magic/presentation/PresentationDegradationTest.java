package com.yo1no.gramarye.magic.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class PresentationDegradationTest {
    @Test
    void stageOrderIsExactAndFinite() {
        assertArrayEquals(
                new PresentationDegradation.Stage[] {
                    PresentationDegradation.Stage.PARTICLE_REDUCTION,
                    PresentationDegradation.Stage.FREQUENCY_OR_SAMPLE_REDUCTION,
                    PresentationDegradation.Stage.SECONDARY_TRAIL_REMOVAL,
                    PresentationDegradation.Stage.EXACT_IDENTITY_COALESCING,
                    PresentationDegradation.Stage.DROP
                },
                PresentationDegradation.Stage.values());
    }

    @Test
    void particleMinimumIntervalIncreaseAndSecondaryRemovalUseFixedOrder() {
        var input = new PresentationDegradation.Candidate(256, 1, true, true);
        var constraints = new PresentationDegradation.Constraints(7L, false, 120, true, false);

        var decision = PresentationDegradation.decide(input, constraints, Optional.empty());

        assertEquals(PresentationDegradation.Outcome.DEGRADED, decision.outcome());
        assertEquals(
                List.of(
                        PresentationDegradation.Stage.PARTICLE_REDUCTION,
                        PresentationDegradation.Stage.FREQUENCY_OR_SAMPLE_REDUCTION,
                        PresentationDegradation.Stage.SECONDARY_TRAIL_REMOVAL),
                decision.appliedStages());
        assertEquals(
                new PresentationDegradation.Candidate(7, 120, true, false),
                decision.candidate().orElseThrow());
        assertEquals(3, decision.appliedStages().size());
    }

    @Test
    void coalescingPrecedesAndPreventsDropOfTheLaterExactIdentity() {
        var decision = PresentationDegradation.decide(
                new PresentationDegradation.Candidate(10, 2, true, true),
                new PresentationDegradation.Constraints(5L, false, 3, true, true),
                Optional.of(exactCoalescingDecision()));

        assertEquals(PresentationDegradation.Outcome.COALESCED, decision.outcome());
        assertEquals(
                List.of(
                        PresentationDegradation.Stage.PARTICLE_REDUCTION,
                        PresentationDegradation.Stage.FREQUENCY_OR_SAMPLE_REDUCTION,
                        PresentationDegradation.Stage.SECONDARY_TRAIL_REMOVAL,
                        PresentationDegradation.Stage.EXACT_IDENTITY_COALESCING),
                decision.appliedStages());
        assertTrue(decision.candidate().isEmpty());
        assertFalse(decision.appliedStages().contains(PresentationDegradation.Stage.DROP));
    }

    @Test
    void dropIsLastAndUnpressuredCandidateIsUnchanged() {
        var input = new PresentationDegradation.Candidate(8, 2, true, false);
        var unchanged = PresentationDegradation.decide(
                input,
                new PresentationDegradation.Constraints(8L, false, 1, false, false),
                Optional.empty());
        assertEquals(PresentationDegradation.Outcome.UNCHANGED, unchanged.outcome());
        assertEquals(input, unchanged.candidate().orElseThrow());
        assertTrue(unchanged.appliedStages().isEmpty());

        var dropped = PresentationDegradation.decide(
                input,
                new PresentationDegradation.Constraints(0L, false, 3, false, true),
                Optional.empty());
        assertEquals(PresentationDegradation.Outcome.DROPPED, dropped.outcome());
        assertEquals(
                List.of(
                        PresentationDegradation.Stage.PARTICLE_REDUCTION,
                        PresentationDegradation.Stage.FREQUENCY_OR_SAMPLE_REDUCTION,
                        PresentationDegradation.Stage.DROP),
                dropped.appliedStages());
        assertTrue(dropped.candidate().isEmpty());
        assertTrue(dropped.appliedStages().size() <= PresentationDegradation.Stage.values().length);
    }

    @Test
    void candidateAndConstraintBoundsRejectNegativeAndOneOver() {
        assertEquals(
                new PresentationDegradation.Candidate(0, 1, false, false),
                new PresentationDegradation.Candidate(0, 1, false, false));
        assertEquals(
                new PresentationDegradation.Candidate(256, 120, true, true),
                new PresentationDegradation.Candidate(256, 120, true, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Candidate(-1, 1, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Candidate(257, 1, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Candidate(0, 0, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Candidate(0, 121, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Candidate(0, 1, false, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Constraints(-1L, false, 1, false, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDegradation.Constraints(513L, false, 1, false, false));
    }

    @Test
    void inapplicableFrequencyStageAndUnpressuredCoalescingAreSkipped() {
        var empty = new PresentationDegradation.Candidate(0, 1, false, false);
        var noWork = PresentationDegradation.decide(
                empty,
                new PresentationDegradation.Constraints(0L, true, 120, false, false),
                Optional.of(exactCoalescingDecision()));

        assertEquals(PresentationDegradation.Outcome.UNCHANGED, noWork.outcome());
        assertTrue(noWork.appliedStages().isEmpty());
        assertEquals(empty, noWork.candidate().orElseThrow());
    }

    @Test
    void particleOnlyFrequencySampleCanSuppressTheCurrentEmission() {
        var input = new PresentationDegradation.Candidate(8, 1, false, false);

        var decision = PresentationDegradation.decide(
                input,
                new PresentationDegradation.Constraints(8L, true, 120, false, false),
                Optional.empty());

        assertEquals(PresentationDegradation.Outcome.DEGRADED, decision.outcome());
        assertEquals(
                List.of(PresentationDegradation.Stage.FREQUENCY_OR_SAMPLE_REDUCTION),
                decision.appliedStages());
        assertEquals(
                new PresentationDegradation.Candidate(0, 1, false, false),
                decision.candidate().orElseThrow());
    }

    @Test
    void trailOnlySampleReductionIncreasesTheApplicableInterval() {
        var input = new PresentationDegradation.Candidate(0, 1, true, false);

        var decision = PresentationDegradation.decide(
                input,
                new PresentationDegradation.Constraints(0L, false, 120, false, false),
                Optional.empty());

        assertEquals(PresentationDegradation.Outcome.DEGRADED, decision.outcome());
        assertEquals(
                List.of(PresentationDegradation.Stage.FREQUENCY_OR_SAMPLE_REDUCTION),
                decision.appliedStages());
        assertEquals(
                new PresentationDegradation.Candidate(0, 120, true, false),
                decision.candidate().orElseThrow());
    }

    @Test
    void particleOnlyCandidateSkipsTheStageWithoutSuppression() {
        var input = new PresentationDegradation.Candidate(8, 1, false, false);

        var decision = PresentationDegradation.decide(
                input,
                new PresentationDegradation.Constraints(8L, false, 120, false, false),
                Optional.empty());

        assertEquals(PresentationDegradation.Outcome.UNCHANGED, decision.outcome());
        assertTrue(decision.appliedStages().isEmpty());
        assertEquals(input, decision.candidate().orElseThrow());
    }

    private static PresentationCoalescing.Decision exactCoalescingDecision() {
        var identity = new PresentationCoalescing.Identity(
                1L,
                1L,
                0,
                1L,
                0,
                OptionalInt.empty(),
                OptionalInt.empty(),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                0.0D,
                0.0D,
                0.0D,
                32_767,
                0,
                0,
                -1,
                -1,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                new TreeMap<>(),
                1_000,
                List.of(new UUID(0L, 1L)));
        return PresentationCoalescing.decide(
                PresentationCoalescing.Value.create(identity, 1L),
                PresentationCoalescing.Value.create(identity, 2L));
    }
}
