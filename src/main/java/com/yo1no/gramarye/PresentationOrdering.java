package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Pure deterministic retention, reverse-drop, and oldest-first ordering. */
final class PresentationOrdering {
    enum RecipientCategory {
        TARGET_SELF,
        SOURCE,
        ORDINARY
    }

    record DeliveryCandidate(
            RecipientCategory category,
            double squaredDistance,
            UUID recipientId,
            long sequence) {
        DeliveryCandidate {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(recipientId, "recipientId");
            requireDistance(squaredDistance);
            requireSequence(sequence);
        }
    }

    record ClientPresentationCandidate(
            RecipientCategory priority,
            double squaredDistance,
            long sequence,
            ProfileChannel channel,
            ResourceLocation profileId) {
        ClientPresentationCandidate {
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(channel, "channel");
            requireDistance(squaredDistance);
            requireSequence(sequence);
            requireProfileId(profileId);
        }
    }

    static final class Selection<T> {
        private final List<T> retained;
        private final List<T> dropped;

        private Selection(List<T> retained, List<T> dropped) {
            this.retained = List.copyOf(retained);
            this.dropped = List.copyOf(dropped);
        }

        List<T> retained() {
            return retained;
        }

        List<T> dropped() {
            return dropped;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Selection<?> that
                            && retained.equals(that.retained)
                            && dropped.equals(that.dropped);
        }

        @Override
        public int hashCode() {
            return Objects.hash(retained, dropped);
        }
    }

    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
        var comparison = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return comparison != 0
                ? comparison
                : Long.compareUnsigned(
                        left.getLeastSignificantBits(), right.getLeastSignificantBits());
    };

    private static final Comparator<DeliveryCandidate> DELIVERY_RETENTION_ORDER =
            Comparator.comparingInt((DeliveryCandidate value) -> value.category().ordinal())
                    .thenComparingDouble(DeliveryCandidate::squaredDistance)
                    .thenComparing(DeliveryCandidate::recipientId, UNSIGNED_UUID_ORDER)
                    .thenComparingLong(DeliveryCandidate::sequence);

    private static final Comparator<ClientPresentationCandidate> CLIENT_RETENTION_ORDER =
            Comparator.comparingInt(
                            (ClientPresentationCandidate value) -> value.priority().ordinal())
                    .thenComparingDouble(ClientPresentationCandidate::squaredDistance)
                    .thenComparingLong(ClientPresentationCandidate::sequence)
                    .thenComparingInt(value -> value.channel().wireCode())
                    .thenComparing(value -> value.profileId().toString());

    private static final Comparator<ClientPresentationCandidate> OLDEST_FIRST_ORDER =
            Comparator.comparingLong(ClientPresentationCandidate::sequence)
                    .thenComparingInt(value -> value.channel().wireCode())
                    .thenComparing(value -> value.profileId().toString());

    private PresentationOrdering() {}

    static Selection<DeliveryCandidate> selectDeliveries(
            List<DeliveryCandidate> candidates, int maximumRetained) {
        Objects.requireNonNull(candidates, "candidates");
        requireCapacity(
                candidates.size(),
                maximumRetained,
                Math.toIntExact(PresentationLimits.MAX_CANDIDATE_DELIVERIES_PER_TICK));
        return select(candidates, maximumRetained, DELIVERY_RETENTION_ORDER);
    }

    static Selection<ClientPresentationCandidate> selectActivePresentations(
            List<ClientPresentationCandidate> candidates, int maximumRetained) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > PresentationLimits.MAX_CLIENT_ACTIVE_SELECTION_INPUT) {
            throw new IllegalArgumentException("active selection input exceeds the transition bound");
        }
        if (maximumRetained < 0
                || maximumRetained > PresentationLimits.MAX_CLIENT_ACTIVE_PRESENTATIONS) {
            throw new IllegalArgumentException("active retained capacity is outside the P8 bound");
        }
        return select(candidates, maximumRetained, CLIENT_RETENTION_ORDER);
    }

    static List<ClientPresentationCandidate> oldestFirst(
            List<ClientPresentationCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > PresentationLimits.MAX_CLIENT_ACTIVE_SELECTION_INPUT) {
            throw new IllegalArgumentException("oldest-first input exceeds the transition bound");
        }
        var copy = checkedCopy(candidates);
        copy.sort(OLDEST_FIRST_ORDER);
        rejectDuplicates(copy);
        return List.copyOf(copy);
    }

    private static <T> Selection<T> select(
            List<T> candidates, int maximumRetained, Comparator<T> retentionOrder) {
        var ordered = checkedCopy(candidates);
        ordered.sort(retentionOrder);
        rejectDuplicates(ordered);
        var retainedCount = Math.min(maximumRetained, ordered.size());
        var retained = List.copyOf(ordered.subList(0, retainedCount));
        var dropped = new ArrayList<T>(ordered.size() - retainedCount);
        for (var index = ordered.size() - 1; index >= retainedCount; index--) {
            dropped.add(ordered.get(index));
        }
        return new Selection<>(retained, dropped);
    }

    private static <T> ArrayList<T> checkedCopy(List<T> source) {
        var copy = new ArrayList<T>(source.size());
        source.forEach(value -> copy.add(Objects.requireNonNull(value, "candidate")));
        return copy;
    }

    private static void rejectDuplicates(List<?> sorted) {
        for (var index = 1; index < sorted.size(); index++) {
            if (sorted.get(index - 1).equals(sorted.get(index))) {
                throw new IllegalArgumentException("presentation candidates must be unique");
            }
        }
    }

    private static void requireCapacity(int size, int retained, int maximum) {
        if (size > maximum) {
            throw new IllegalArgumentException("candidate input exceeds the P8 bound");
        }
        if (retained < 0 || retained > maximum) {
            throw new IllegalArgumentException("retained capacity is outside the P8 bound");
        }
    }

    private static void requireDistance(double squaredDistance) {
        if (!Double.isFinite(squaredDistance)
                || squaredDistance < 0.0D
                || squaredDistance > 4_096.0D) {
            throw new IllegalArgumentException("squared distance is outside the P8 range");
        }
    }

    private static void requireSequence(long sequence) {
        if (sequence < PresentationLimits.MIN_SEQUENCE) {
            throw new IllegalArgumentException("presentation sequence must be positive");
        }
    }

    private static void requireProfileId(ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        if (profileId.toString().getBytes(StandardCharsets.UTF_8).length
                > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalArgumentException("Profile ID exceeds the P8 UTF-8 bound");
        }
    }
}
