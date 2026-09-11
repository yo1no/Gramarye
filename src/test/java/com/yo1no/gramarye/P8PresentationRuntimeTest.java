package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Direct bounded-transition tests for the actual P8-S3 server runtime owners. */
final class P8PresentationRuntimeTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void logicalSkillSourceAndServerRejectionsAreAtomicAndResetOnTheNextTick() {
        var source = new UUID(0L, 0x8301L);
        var limitedSkill = skill(1L);
        var skillScratch = P8TickState.empty().scratch(10L);

        for (var index = 0; index < 8; index++) {
            assertTrue(skillScratch.admitLogical(limitedSkill, Optional.of(source)));
        }
        assertFalse(skillScratch.admitLogical(limitedSkill, Optional.of(source)));
        for (var index = 0; index < 120; index++) {
            assertTrue(skillScratch.admitLogical(skill(index + 2L), Optional.empty()));
        }
        assertFalse(skillScratch.admitLogical(skill(1_000L), Optional.empty()));

        var skillState = skillScratch.freeze();
        assertEquals(10L, skillState.runtimeTick());
        assertThrows(IllegalArgumentException.class, () -> skillState.scratch(9L));
        assertTrue(skillState.scratch(11L)
                .admitLogical(limitedSkill, Optional.of(source)));

        var sourceScratch = P8TickState.empty().scratch(20L);
        for (var index = 0; index < 32; index++) {
            assertTrue(sourceScratch.admitLogical(skill(index + 1L), Optional.of(source)));
        }
        assertFalse(sourceScratch.admitLogical(skill(33L), Optional.of(source)));
        for (var index = 0; index < 96; index++) {
            assertTrue(sourceScratch.admitLogical(skill(index + 100L), Optional.empty()));
        }
        assertFalse(sourceScratch.admitLogical(skill(1_001L), Optional.empty()));
    }

    @Test
    void bufferCoalescingRequiresTheSameCurrentConnectionIdentity() {
        var playerId = new UUID(0L, 0x8302L);
        var scratch = P8TickState.empty().scratch(30L);
        var first = buffered(30L, 1L, 7L, 0, playerId, 1L);
        var reconnected = buffered(30L, 2L, 7L, 0, playerId, 2L);
        var sameConnection = buffered(30L, 3L, 7L, 0, playerId, 1L);

        assertEquals(
                PresentationDegradation.Outcome.UNCHANGED,
                scratch.admitBuffered(first).outcome());
        assertEquals(
                PresentationDegradation.Outcome.UNCHANGED,
                scratch.admitBuffered(reconnected).outcome());
        assertEquals(
                PresentationDegradation.Outcome.COALESCED,
                scratch.admitBuffered(sameConnection).outcome());
    }

    @Test
    void coalescingCanonicalizesTheSameRecipientEpochSetAcrossSelectionOrder() {
        var firstPlayer = new UUID(0L, 0x8305L);
        var secondPlayer = new UUID(0L, 0x8306L);
        var firstIdentity = new P8RecipientIdentity(firstPlayer, 3L);
        var secondIdentity = new P8RecipientIdentity(secondPlayer, 7L);
        var firstOrder = List.of(
                recipient(firstIdentity, 4.0D),
                recipient(secondIdentity, 9.0D));
        var reversedOrder = List.of(
                recipient(secondIdentity, 1.0D),
                recipient(firstIdentity, 16.0D));
        var scratch = P8TickState.empty().scratch(31L);

        assertEquals(
                PresentationDegradation.Outcome.UNCHANGED,
                scratch.admitBuffered(buffered(31L, 1L, 9L, 0, firstOrder)).outcome());
        assertEquals(
                PresentationDegradation.Outcome.COALESCED,
                scratch.admitBuffered(buffered(31L, 2L, 9L, 0, reversedOrder)).outcome());
        assertEquals(1, scratch.freeze().buffer().size());
    }

    @Test
    void logicalCoalescingAndBufferCapacityUseIndependentBoundedLedgers() {
        var source = new UUID(0L, 0x8307L);
        var skill = skill(0x8307L);
        var coalescing = P8TickState.empty().scratch(32L);
        for (var sequence = 1L; sequence <= 8L; sequence++) {
            assertTrue(coalescing.admitLogical(skill, Optional.of(source)));
            assertEquals(
                    sequence == 1L
                            ? PresentationDegradation.Outcome.UNCHANGED
                            : PresentationDegradation.Outcome.COALESCED,
                    coalescing.admitBuffered(
                                    buffered(32L, sequence, 10L, 0, source, 1L))
                            .outcome());
        }
        assertFalse(coalescing.admitLogical(skill, Optional.of(source)));
        assertEquals(1, coalescing.freeze().buffer().size());

        var capacity = P8TickState.empty().scratch(33L);
        for (var sequence = 1L; sequence <= 128L; sequence++) {
            assertEquals(
                    PresentationDegradation.Outcome.UNCHANGED,
                    capacity.admitBuffered(
                                    buffered(33L, sequence, sequence, 0, source, 1L))
                            .outcome());
        }
        assertEquals(
                PresentationDegradation.Outcome.DROPPED,
                capacity.admitBuffered(buffered(33L, 129L, 129L, 0, source, 1L)).outcome());
        assertEquals(128, capacity.freeze().buffer().size());
    }

    @Test
    void recipientEvaluationAndSequenceOwnersRejectExcessWithoutWrap() {
        var evaluations = P8TickState.empty().scratch(34L);
        assertTrue(evaluations.chargeRecipientEvaluations(65_536));
        assertFalse(evaluations.chargeRecipientEvaluations(1));
        assertFalse(evaluations.chargeRecipientEvaluations(1));
        assertTrue(evaluations.freeze().scratch(35L).chargeRecipientEvaluations(1));

        var sequence = PresentationSequence.expecting(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE - 1L, sequence.allocatedHighWater());
        var maximum = sequence.allocate();
        assertEquals(Long.MAX_VALUE, maximum.sequence().orElseThrow());
        assertEquals(Long.MAX_VALUE, maximum.nextState().allocatedHighWater());
        assertTrue(maximum.nextState().allocate().sequence().isEmpty());
        assertEquals(Long.MAX_VALUE, maximum.nextState().allocate().nextState()
                .allocatedHighWater());
        assertEquals(0L, PresentationSequence.initial().allocatedHighWater());
    }

    @Test
    void recipientSelectorClosesPlayerListAndCandidateAdmissionAtFiveHundredTwelve() {
        assertTrue(P8RecipientSelector.withinOnlineScanBound(512));
        assertFalse(P8RecipientSelector.withinOnlineScanBound(513));
        assertThrows(
                IllegalArgumentException.class,
                () -> P8RecipientSelector.withinOnlineScanBound(-1));

        var candidates = new TreeMap<UUID, Long>();
        for (var index = 1L; index <= 512L; index++) {
            assertTrue(P8RecipientSelector.admitCandidate(
                    candidates, new UUID(0L, index), index));
        }
        assertEquals(512, candidates.size());
        assertTrue(P8RecipientSelector.admitCandidate(
                candidates, new UUID(0L, 1L), Long.MAX_VALUE));
        assertEquals(1L, candidates.get(new UUID(0L, 1L)));
        assertFalse(P8RecipientSelector.admitCandidate(
                candidates, new UUID(0L, 513L), 513L));
        assertEquals(512, candidates.size());
    }

    @Test
    void recipientSelectorRetainsTheDeterministicBestThirtyTwoFromThirtyThree() {
        var exact = java.util.stream.LongStream.rangeClosed(1L, 32L)
                .map(index -> 33L - index)
                .mapToObj(index -> recipient(
                        new P8RecipientIdentity(new UUID(0L, index), 1L), 1.0D))
                .toList();
        var expected = java.util.stream.LongStream.rangeClosed(1L, 32L)
                .mapToObj(index -> new UUID(0L, index))
                .toList();

        var exactSelection = P8RecipientSelector.retainEligible(exact, 32, 1L);

        assertEquals(32, exactSelection.evaluations());
        assertEquals(expected, exactSelection.recipients().stream()
                .map(value -> value.identity().playerId())
                .toList());

        var oneOver = java.util.stream.LongStream.rangeClosed(1L, 33L)
                .map(index -> 34L - index)
                .mapToObj(index -> recipient(
                        new P8RecipientIdentity(new UUID(0L, index), 1L), 1.0D))
                .toList();

        var trimmed = P8RecipientSelector.retainEligible(oneOver, 33, 2L);

        assertEquals(33, trimmed.evaluations());
        assertEquals(32, trimmed.recipients().size());
        assertEquals(expected, trimmed.recipients().stream()
                .map(value -> value.identity().playerId())
                .toList());
    }

    @Test
    void activationStyleBufferClearPreservesAlreadyChargedLogicalWork() {
        var skill = skill(0x8303L);
        var source = new UUID(0L, 0x8303L);
        var scratch = P8TickState.empty().scratch(40L);
        for (var index = 0; index < 8; index++) {
            assertTrue(scratch.admitLogical(skill, Optional.of(source)));
        }
        assertEquals(
                PresentationDegradation.Outcome.UNCHANGED,
                scratch.admitBuffered(buffered(40L, 1L, 8L, 0, source, 1L)).outcome());

        var cleared = scratch.freeze().clearBufferPreservingLogicalWork();

        assertTrue(cleared.buffer().isEmpty());
        assertFalse(cleared.scratch(40L).admitLogical(skill, Optional.of(source)));
        assertTrue(cleared.scratch(41L).admitLogical(skill, Optional.of(source)));
    }

    @Test
    void deliveryAdmissionEnforcesPerPlayerAndServerCapsInDeterministicOrder() {
        var onePlayer = new UUID(0L, 0x8304L);
        var seventeen = java.util.stream.LongStream.rangeClosed(1L, 17L)
                .mapToObj(sequence -> delivery(sequence, onePlayer))
                .toList();

        var perPlayer = P8DeliveryAdmission.admit(seventeen);

        assertEquals(16, perPlayer.size());
        assertEquals(
                java.util.stream.LongStream.rangeClosed(1L, 16L).boxed().toList(),
                perPlayer.stream()
                        .map(value -> value.buffered().event().sequence())
                        .toList());

        var fiveHundredThirteen = java.util.stream.LongStream.rangeClosed(1L, 513L)
                .mapToObj(sequence -> delivery(sequence, new UUID(0L, sequence)))
                .toList();
        var server = P8DeliveryAdmission.admit(fiveHundredThirteen);

        assertEquals(512, server.size());
        assertEquals(new UUID(0L, 1L), server.getFirst().identity().playerId());
        assertEquals(new UUID(0L, 512L), server.getLast().identity().playerId());
        assertThrows(
                IllegalArgumentException.class,
                () -> P8DeliveryAdmission.admit(Collections.nCopies(4_097, null)));
    }

    @Test
    void currentTickRetentionContainsOnlyCopiedValuesAndStableIds() {
        var prohibitedNames = List.of(
                "MinecraftServer",
                "ServerPlayer",
                "ServerLevel",
                "world.entity.Entity",
                "RuntimeEvent",
                "RuntimeExecutionContext",
                "AppliedFact",
                "AppearanceResolutionSnapshot",
                "Throwable");

        for (Class<?> owner : List.of(
                P8EventMaterial.class,
                P8BufferedPresentation.class,
                P8Delivery.class,
                P8TickState.class)) {
            for (var field : owner.getDeclaredFields()) {
                String type = field.getGenericType().getTypeName();
                for (String prohibited : prohibitedNames) {
                    assertFalse(type.contains(prohibited), owner.getName() + " retains " + type);
                }
            }
        }
    }

    private static SkillInstanceId skill(long value) {
        return new SkillInstanceId(new UUID(0x8300000000000000L, value));
    }

    private static P8Delivery delivery(long sequence, UUID playerId) {
        P8BufferedPresentation buffered = buffered(
                50L, sequence, sequence, 0, playerId, 1L);
        P8SelectedRecipient selected = buffered.recipients().getFirst();
        return new P8Delivery(
                new PresentationOrdering.DeliveryCandidate(
                        selected.category(),
                        selected.squaredDistance(),
                        playerId,
                        sequence),
                selected.identity(),
                buffered);
    }

    private static P8BufferedPresentation buffered(
            long tick,
            long sequence,
            long sourceEventId,
            int appliedStepIndex,
            UUID playerId,
            long connectionEpoch) {
        return buffered(
                tick,
                sequence,
                sourceEventId,
                appliedStepIndex,
                List.of(recipient(
                        new P8RecipientIdentity(playerId, connectionEpoch), 0.0D)));
    }

    private static P8BufferedPresentation buffered(
            long tick,
            long sequence,
            long sourceEventId,
            int appliedStepIndex,
            List<P8SelectedRecipient> recipients) {
        EffectiveAppearance appearance = disabledAppearance();
        PresentationEvent event = ((AcceptedPresentationEvent) PresentationEvent.createServer(
                        1L,
                        PresentationEventKind.CAST_RELEASE.wireCode(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OVERWORLD,
                        0.0D,
                        64.0D,
                        0.0D,
                        1.0D,
                        0.0D,
                        0.0D,
                        appearance,
                        sequence))
                .event();
        var direction = event.direction();
        var wireAppearance = event.appearance();
        var identity = new PresentationCoalescing.Identity(
                tick,
                sourceEventId,
                appliedStepIndex,
                event.catalogGeneration(),
                event.kind().wireCode(),
                event.sourceSummary().sourceEntityId(),
                event.sourceSummary().targetEntityId(),
                event.dimension(),
                event.position().x(),
                event.position().y(),
                event.position().z(),
                direction.xQ15(),
                direction.yQ15(),
                direction.zQ15(),
                wireAppearance.primaryArgb(),
                wireAppearance.secondaryArgb(),
                wireAppearance.soundProfileId(),
                wireAppearance.particleProfileId(),
                wireAppearance.trailProfileId(),
                new TreeMap<>(wireAppearance.parameters()),
                wireAppearance.intensityMilli(),
                recipients.stream()
                        .map(value -> value.identity().playerId())
                        .toList());
        return new P8BufferedPresentation(
                tick,
                sourceEventId,
                appliedStepIndex,
                event,
                PresentationCoalescing.Value.create(identity, sequence),
                recipients,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static P8SelectedRecipient recipient(
            P8RecipientIdentity identity, double squaredDistance) {
        return new P8SelectedRecipient(
                identity,
                PresentationOrdering.RecipientCategory.ORDINARY,
                squaredDistance);
    }

    private static EffectiveAppearance disabledAppearance() {
        return new EffectiveAppearance(
                0xff_ffffff,
                0xff_ffffff,
                1_000,
                new ResolvedProfile(
                        ProfileChannel.SOUND,
                        Optional.empty(),
                        ProfileResolutionReason.EXPLICITLY_DISABLED),
                new ResolvedProfile(
                        ProfileChannel.PARTICLE,
                        Optional.empty(),
                        ProfileResolutionReason.EXPLICITLY_DISABLED),
                new ResolvedProfile(
                        ProfileChannel.TRAIL,
                        Optional.empty(),
                        ProfileResolutionReason.EXPLICITLY_DISABLED),
                Map.of());
    }
}
