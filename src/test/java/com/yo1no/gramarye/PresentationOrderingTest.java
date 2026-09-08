package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.PresentationOrdering.ClientPresentationCandidate;
import com.yo1no.gramarye.PresentationOrdering.DeliveryCandidate;
import com.yo1no.gramarye.PresentationOrdering.RecipientCategory;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class PresentationOrderingTest {
    @Test
    void deliveryRetentionAndReverseDropUseEveryExactServerTieBreak() {
        var target = delivery(RecipientCategory.TARGET_SELF, 4_096.0D, 9L, 9L, 5L);
        var source = delivery(RecipientCategory.SOURCE, 0.0D, 9L, 9L, 4L);
        var ordinaryLowUuid = delivery(RecipientCategory.ORDINARY, 1.0D, 0L, 2L, 3L);
        var ordinaryHighUuidEarly = delivery(RecipientCategory.ORDINARY, 1.0D, -1L, 1L, 1L);
        var ordinaryHighUuidLate = delivery(RecipientCategory.ORDINARY, 1.0D, -1L, 1L, 2L);

        var selection = PresentationOrdering.selectDeliveries(
                List.of(
                        ordinaryHighUuidLate,
                        ordinaryLowUuid,
                        source,
                        ordinaryHighUuidEarly,
                        target),
                2);

        assertEquals(List.of(target, source), selection.retained());
        assertEquals(
                List.of(ordinaryHighUuidLate, ordinaryHighUuidEarly, ordinaryLowUuid),
                selection.dropped());
        assertThrows(
                UnsupportedOperationException.class,
                () -> selection.retained().add(ordinaryLowUuid));
    }

    @Test
    void clientRetentionDropsNewestLowerPriorityAndResolvesAllFinalTies() {
        var target = active(RecipientCategory.TARGET_SELF, 64.0D, 9L, ProfileChannel.TRAIL, "z");
        var source = active(RecipientCategory.SOURCE, 0.0D, 8L, ProfileChannel.TRAIL, "z");
        var ordinaryOldSound = active(
                RecipientCategory.ORDINARY, 1.0D, 1L, ProfileChannel.SOUND, "z");
        var ordinaryOldParticle = active(
                RecipientCategory.ORDINARY, 1.0D, 1L, ProfileChannel.PARTICLE, "a");
        var ordinaryNewest = active(
                RecipientCategory.ORDINARY, 1.0D, 2L, ProfileChannel.SOUND, "a");

        var selection = PresentationOrdering.selectActivePresentations(
                List.of(
                        ordinaryNewest,
                        ordinaryOldParticle,
                        source,
                        ordinaryOldSound,
                        target),
                3);

        assertEquals(List.of(target, source, ordinaryOldSound), selection.retained());
        assertEquals(List.of(ordinaryNewest, ordinaryOldParticle), selection.dropped());
    }

    @Test
    void expiryAndTrailTruncationOrderIsOldestThenChannelThenProfileId() {
        var soundB = active(RecipientCategory.ORDINARY, 4.0D, 1L, ProfileChannel.SOUND, "b");
        var soundA = active(RecipientCategory.TARGET_SELF, 64.0D, 1L, ProfileChannel.SOUND, "a");
        var particle = active(
                RecipientCategory.SOURCE, 0.0D, 1L, ProfileChannel.PARTICLE, "a");
        var newer = active(RecipientCategory.TARGET_SELF, 0.0D, 2L, ProfileChannel.SOUND, "a");

        assertEquals(
                List.of(soundA, soundB, particle, newer),
                PresentationOrdering.oldestFirst(List.of(newer, particle, soundB, soundA)));
    }

    @Test
    void orderingInputsAreBoundedBeforeTraversalAndRejectDuplicateOrMalformedValues() {
        var exactMaximum = new ArrayList<DeliveryCandidate>();
        for (var index = 0; index < 4_096; index++) {
            exactMaximum.add(delivery(
                    RecipientCategory.ORDINARY,
                    1.0D,
                    0L,
                    index,
                    index + 1L));
        }
        assertEquals(
                4_096,
                PresentationOrdering.selectDeliveries(exactMaximum, 4_096).retained().size());

        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationOrdering.selectDeliveries(failingList(4_097), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationOrdering.selectActivePresentations(failingActiveList(130), 0));
        var duplicate = delivery(RecipientCategory.ORDINARY, 0.0D, 0L, 1L, 1L);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationOrdering.selectDeliveries(List.of(duplicate, duplicate), 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> delivery(RecipientCategory.ORDINARY, 4_096.0D + Math.ulp(4_096.0D), 0L, 1L, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> active(RecipientCategory.ORDINARY, 0.0D, 0L, ProfileChannel.SOUND, "a"));
    }

    private static DeliveryCandidate delivery(
            RecipientCategory category,
            double squaredDistance,
            long uuidMost,
            long uuidLeast,
            long sequence) {
        return new DeliveryCandidate(
                category, squaredDistance, new UUID(uuidMost, uuidLeast), sequence);
    }

    private static ClientPresentationCandidate active(
            RecipientCategory priority,
            double squaredDistance,
            long sequence,
            ProfileChannel channel,
            String profilePath) {
        return new ClientPresentationCandidate(
                priority,
                squaredDistance,
                sequence,
                channel,
                ResourceLocation.fromNamespaceAndPath("gramarye", profilePath));
    }

    private static List<DeliveryCandidate> failingList(int size) {
        return new AbstractList<>() {
            @Override
            public DeliveryCandidate get(int index) {
                throw new AssertionError("over-cap input was traversed");
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    private static List<ClientPresentationCandidate> failingActiveList(int size) {
        return new AbstractList<>() {
            @Override
            public ClientPresentationCandidate get(int index) {
                throw new AssertionError("over-cap input was traversed");
            }

            @Override
            public int size() {
                return size;
            }
        };
    }
}
