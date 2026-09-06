package com.yo1no.gramarye.magic.presentation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Exact-identity, same-tick coalescing without a buffer or runtime owner. */
final class PresentationCoalescing {
    private static final Comparator<UUID> UNSIGNED_UUID_ORDER = (left, right) -> {
        var comparison = Long.compareUnsigned(
                left.getMostSignificantBits(), right.getMostSignificantBits());
        return comparison != 0
                ? comparison
                : Long.compareUnsigned(
                        left.getLeastSignificantBits(), right.getLeastSignificantBits());
    };

    enum Outcome {
        DISTINCT,
        EXACT_IDENTITY_COALESCED
    }

    record Identity(
            long authoritativeTick,
            long sourceEventId,
            int appliedStepIndex,
            long catalogGeneration,
            int kindCode,
            OptionalInt sourceEntityId,
            OptionalInt targetEntityId,
            ResourceLocation dimension,
            double x,
            double y,
            double z,
            int directionXQ15,
            int directionYQ15,
            int directionZQ15,
            int primaryArgb,
            int secondaryArgb,
            Optional<ResourceLocation> soundProfile,
            Optional<ResourceLocation> particleProfile,
            Optional<ResourceLocation> trailProfile,
            SortedMap<ResourceLocation, Integer> overrides,
            int intensityMilli,
            List<UUID> recipients) {
        Identity {
            if (sourceEventId < PresentationLimits.MIN_SEQUENCE) {
                throw new IllegalArgumentException("source event id must be positive");
            }
            if (appliedStepIndex < 0 || appliedStepIndex > 7) {
                throw new IllegalArgumentException("applied step index must be in [0,7]");
            }
            if (catalogGeneration < PresentationLimits.MIN_SEQUENCE) {
                throw new IllegalArgumentException("catalog generation must be positive");
            }
            if (kindCode < 0 || kindCode > 10) {
                throw new IllegalArgumentException("presentation kind code must be in [0,10]");
            }
            sourceEntityId = requireEntityId(sourceEntityId, "sourceEntityId");
            targetEntityId = requireEntityId(targetEntityId, "targetEntityId");
            dimension = requireResourceLocation(
                    dimension,
                    PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                    "dimension");
            requirePosition(x, PresentationLimits.MAX_HORIZONTAL_POSITION, "x");
            requirePosition(y, PresentationLimits.MAX_VERTICAL_POSITION, "y");
            requirePosition(z, PresentationLimits.MAX_HORIZONTAL_POSITION, "z");
            requireDirection(directionXQ15, "directionXQ15");
            requireDirection(directionYQ15, "directionYQ15");
            requireDirection(directionZQ15, "directionZQ15");
            if (directionXQ15 == 0 && directionYQ15 == 0 && directionZQ15 == 0) {
                throw new IllegalArgumentException("presentation direction cannot be all zero");
            }
            soundProfile = requireProfile(soundProfile, "soundProfile");
            particleProfile = requireProfile(particleProfile, "particleProfile");
            trailProfile = requireProfile(trailProfile, "trailProfile");
            overrides = immutableOverrides(overrides);
            if (intensityMilli < 0 || intensityMilli > PresentationLimits.MAX_INTENSITY_MILLI) {
                throw new IllegalArgumentException("presentation intensity is outside the P8 bound");
            }
            recipients = immutableRecipients(recipients);
        }
    }

    record Value(Identity identity, long sequence, long visualSeed) {
        Value {
            Objects.requireNonNull(identity, "identity");
            if (sequence < PresentationLimits.MIN_SEQUENCE) {
                throw new IllegalArgumentException("presentation sequence must be positive");
            }
            if (visualSeed != PresentationSequence.visualSeed(sequence)) {
                throw new IllegalArgumentException("visual seed does not match its sequence");
            }
        }

        static Value create(Identity identity, long sequence) {
            return new Value(identity, sequence, PresentationSequence.visualSeed(sequence));
        }
    }

    static final class Decision {
        private final Outcome outcome;
        private final List<Value> retained;

        private Decision(Outcome outcome, List<Value> retained) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(retained, "retained");
            var expectedSize = outcome == Outcome.EXACT_IDENTITY_COALESCED ? 1 : 2;
            if (retained.size() != expectedSize) {
                throw new IllegalArgumentException("coalescing result cardinality is invalid");
            }
            this.retained = List.copyOf(retained);
            for (var index = 1; index < this.retained.size(); index++) {
                if (this.retained.get(index - 1).sequence()
                        >= this.retained.get(index).sequence()) {
                    throw new IllegalArgumentException(
                            "retained values must have unique ascending sequence");
                }
            }
        }

        Outcome outcome() {
            return outcome;
        }

        List<Value> retained() {
            return retained;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof Decision that
                            && outcome == that.outcome
                            && retained.equals(that.retained);
        }

        @Override
        public int hashCode() {
            return Objects.hash(outcome, retained);
        }
    }

    private PresentationCoalescing() {}

    static Decision decide(Value left, Value right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.sequence() == right.sequence()) {
            throw new IllegalArgumentException("one sequence cannot identify two offers");
        }

        var earlier = left.sequence() < right.sequence() ? left : right;
        var later = earlier == left ? right : left;
        if (earlier.identity().equals(later.identity())) {
            return new Decision(
                    Outcome.EXACT_IDENTITY_COALESCED, List.of(earlier));
        }
        return new Decision(Outcome.DISTINCT, List.of(earlier, later));
    }

    private static OptionalInt requireEntityId(OptionalInt value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isPresent()
                && (value.orElseThrow() < 1
                        || value.orElseThrow() > PresentationLimits.MAX_ENTITY_ID)) {
            throw new IllegalArgumentException(name + " must contain a positive entity id");
        }
        return value;
    }

    private static Optional<ResourceLocation> requireProfile(
            Optional<ResourceLocation> value, String name) {
        Objects.requireNonNull(value, name);
        value.ifPresent(profile -> requireResourceLocation(
                profile, PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES, name));
        return value;
    }

    private static ResourceLocation requireResourceLocation(
            ResourceLocation value, int maximumUtf8Bytes, String name) {
        Objects.requireNonNull(value, name);
        if (value.toString().getBytes(StandardCharsets.UTF_8).length > maximumUtf8Bytes) {
            throw new IllegalArgumentException(name + " exceeds its UTF-8 bound");
        }
        return value;
    }

    private static void requirePosition(double value, double absoluteMaximum, String name) {
        if (!Double.isFinite(value) || Math.abs(value) > absoluteMaximum) {
            throw new IllegalArgumentException(name + " is outside the finite position bound");
        }
    }

    private static void requireDirection(int value, String name) {
        if (value < -PresentationLimits.MAX_DIRECTION_Q15
                || value > PresentationLimits.MAX_DIRECTION_Q15) {
            throw new IllegalArgumentException(name + " is outside the Q15 bound");
        }
    }

    private static SortedMap<ResourceLocation, Integer> immutableOverrides(
            SortedMap<ResourceLocation, Integer> input) {
        Objects.requireNonNull(input, "overrides");
        if (input.size() > PresentationLimits.MAX_EVENT_OVERRIDES) {
            throw new IllegalArgumentException("event overrides exceed the P8 bound");
        }
        var copy = new TreeMap<ResourceLocation, Integer>();
        input.forEach((key, value) -> {
            requireResourceLocation(
                    key, PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES, "override key");
            copy.put(key, Objects.requireNonNull(value, "override value"));
        });
        return Collections.unmodifiableSortedMap(copy);
    }

    private static List<UUID> immutableRecipients(List<UUID> input) {
        Objects.requireNonNull(input, "recipients");
        if (input.size() > PresentationLimits.MAX_SELECTED_RECIPIENTS) {
            throw new IllegalArgumentException("recipients exceed the P8 bound");
        }
        var copy = new ArrayList<UUID>(input.size());
        input.forEach(recipient -> copy.add(Objects.requireNonNull(recipient, "recipient")));
        copy.sort(UNSIGNED_UUID_ORDER);
        for (var index = 1; index < copy.size(); index++) {
            if (copy.get(index - 1).equals(copy.get(index))) {
                throw new IllegalArgumentException("recipients must be unique");
            }
        }
        return List.copyOf(copy);
    }
}
