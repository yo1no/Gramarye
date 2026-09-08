package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class PresentationCoalescingTest {
    @Test
    void exactIdentityRetainsEarliestSequenceAndItsSeedRegardlessOfArgumentOrder() {
        var identity = identity(builder -> {});
        var earlier = PresentationCoalescing.Value.create(identity, 1L);
        var later = PresentationCoalescing.Value.create(identity, 2L);

        var forward = PresentationCoalescing.decide(earlier, later);
        var reverse = PresentationCoalescing.decide(later, earlier);

        assertEquals(
                PresentationCoalescing.Outcome.EXACT_IDENTITY_COALESCED,
                forward.outcome());
        assertEquals(List.of(earlier), forward.retained());
        assertEquals(forward, reverse);
        assertEquals(PresentationSequence.visualSeed(1L), forward.retained().getFirst().visualSeed());
    }

    @Test
    void everyAuthorityIdentityFieldParticipatesInCoalescing() {
        var base = identity(builder -> {});
        var variants = List.of(
                identity(builder -> builder.authoritativeTick = 8L),
                identity(builder -> builder.sourceEventId = 12L),
                identity(builder -> builder.appliedStepIndex = 2),
                identity(builder -> builder.catalogGeneration = 4L),
                identity(builder -> builder.kindCode = 3),
                identity(builder -> builder.sourceEntityId = OptionalInt.of(12)),
                identity(builder -> builder.targetEntityId = OptionalInt.of(13)),
                identity(builder -> builder.dimension = id("gramarye", "other_dimension")),
                identity(builder -> builder.x = 1.5D),
                identity(builder -> builder.y = 2.5D),
                identity(builder -> builder.z = 3.5D),
                identity(builder -> builder.directionXQ15 = 4),
                identity(builder -> builder.directionYQ15 = 5),
                identity(builder -> builder.directionZQ15 = 6),
                identity(builder -> builder.primaryArgb = 0x01020304),
                identity(builder -> builder.secondaryArgb = 0x05060708),
                identity(builder -> builder.soundProfile = Optional.empty()),
                identity(builder -> builder.particleProfile = Optional.empty()),
                identity(builder -> builder.trailProfile = Optional.empty()),
                identity(builder -> builder.overrides.put(id("gramarye", "scale"), 99)),
                identity(builder -> builder.intensityMilli = 999),
                identity(builder -> builder.recipients = List.of(new UUID(9L, 9L))));

        for (var variant : variants) {
            var decision = PresentationCoalescing.decide(
                    PresentationCoalescing.Value.create(base, 1L),
                    PresentationCoalescing.Value.create(variant, 2L));
            assertEquals(PresentationCoalescing.Outcome.DISTINCT, decision.outcome());
            assertEquals(2, decision.retained().size());
            assertEquals(1L, decision.retained().get(0).sequence());
            assertEquals(2L, decision.retained().get(1).sequence());
        }
    }

    @Test
    void identityDefensivelyCopiesAndCanonicalizesCollections() {
        var overrides = new TreeMap<ResourceLocation, Integer>();
        overrides.put(id("gramarye", "size"), 7);
        var lowUnsigned = new UUID(0L, 2L);
        var highUnsigned = new UUID(-1L, 1L);
        var recipients = new ArrayList<>(List.of(highUnsigned, lowUnsigned));
        var builder = new IdentityBuilder();
        builder.overrides = overrides;
        builder.recipients = recipients;

        var identity = builder.build();
        overrides.put(id("gramarye", "later"), 8);
        recipients.clear();

        assertEquals(1, identity.overrides().size());
        assertEquals(List.of(lowUnsigned, highUnsigned), identity.recipients());
        assertThrows(
                UnsupportedOperationException.class,
                () -> identity.overrides().put(id("gramarye", "blocked"), 1));
        assertThrows(
                UnsupportedOperationException.class,
                () -> identity.recipients().add(new UUID(3L, 3L)));

        var reverseBuilder = new IdentityBuilder();
        reverseBuilder.overrides = new TreeMap<>(identity.overrides());
        reverseBuilder.recipients = List.of(highUnsigned, lowUnsigned);
        assertEquals(identity, reverseBuilder.build());
    }

    @Test
    void coalescingRejectsSeedMismatchAndImpossibleSequenceReuse() {
        var identity = identity(builder -> {});
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationCoalescing.Value(identity, 1L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationCoalescing.Value.create(identity, 0L));

        var distinct = identity(builder -> builder.x = 4.0D);
        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationCoalescing.decide(
                        PresentationCoalescing.Value.create(identity, 1L),
                        PresentationCoalescing.Value.create(distinct, 1L)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PresentationCoalescing.decide(
                        PresentationCoalescing.Value.create(identity, 1L),
                        PresentationCoalescing.Value.create(identity, 1L)));
    }

    @Test
    void identityEnforcesBoundedExactValueShape() {
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> builder.appliedStepIndex = 8));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> builder.catalogGeneration = 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> builder.directionXQ15 = -32_768));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> {
                    builder.directionXQ15 = 0;
                    builder.directionYQ15 = 0;
                    builder.directionZQ15 = 0;
                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> builder.intensityMilli = 10_001));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> {
                    builder.overrides.clear();
                    for (var index = 0; index < 9; index++) {
                        builder.overrides.put(id("gramarye", "parameter_" + index), index);
                    }
                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> {
                    var recipients = new ArrayList<UUID>();
                    for (var index = 0; index < 33; index++) {
                        recipients.add(new UUID(0L, index));
                    }
                    builder.recipients = recipients;
                }));
        assertThrows(
                IllegalArgumentException.class,
                () -> identity(builder -> builder.recipients = List.of(
                        new UUID(1L, 1L), new UUID(1L, 1L))));
    }

    private static PresentationCoalescing.Identity identity(Consumer<IdentityBuilder> mutation) {
        var builder = new IdentityBuilder();
        mutation.accept(builder);
        return builder.build();
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static final class IdentityBuilder {
        private long authoritativeTick = 7L;
        private long sourceEventId = 11L;
        private int appliedStepIndex = 1;
        private long catalogGeneration = 3L;
        private int kindCode = 1;
        private OptionalInt sourceEntityId = OptionalInt.of(10);
        private OptionalInt targetEntityId = OptionalInt.of(20);
        private ResourceLocation dimension = id("minecraft", "overworld");
        private double x = 1.0D;
        private double y = 2.0D;
        private double z = 3.0D;
        private int directionXQ15 = 1;
        private int directionYQ15 = 2;
        private int directionZQ15 = 3;
        private int primaryArgb = 0x11223344;
        private int secondaryArgb = 0x55667788;
        private Optional<ResourceLocation> soundProfile = Optional.of(
                id("gramarye", "default_sound"));
        private Optional<ResourceLocation> particleProfile = Optional.of(
                id("gramarye", "default_particle"));
        private Optional<ResourceLocation> trailProfile = Optional.of(
                id("gramarye", "default_trail"));
        private TreeMap<ResourceLocation, Integer> overrides = overrides();
        private int intensityMilli = 1_000;
        private List<UUID> recipients = List.of(new UUID(1L, 2L), new UUID(3L, 4L));

        private PresentationCoalescing.Identity build() {
            return new PresentationCoalescing.Identity(
                    authoritativeTick,
                    sourceEventId,
                    appliedStepIndex,
                    catalogGeneration,
                    kindCode,
                    sourceEntityId,
                    targetEntityId,
                    dimension,
                    x,
                    y,
                    z,
                    directionXQ15,
                    directionYQ15,
                    directionZQ15,
                    primaryArgb,
                    secondaryArgb,
                    soundProfile,
                    particleProfile,
                    trailProfile,
                    overrides,
                    intensityMilli,
                    recipients);
        }

        private static TreeMap<ResourceLocation, Integer> overrides() {
            var overrides = new TreeMap<ResourceLocation, Integer>();
            overrides.put(id("gramarye", "scale"), 5);
            return overrides;
        }
    }
}
