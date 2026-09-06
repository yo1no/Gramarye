package com.yo1no.gramarye.magic.presentation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PresentationEventTest {
    @Test
    void eventKindVocabularyIsExactAndClosed() {
        assertArrayEquals(
                new String[] {
                    "CAST_START",
                    "CAST_RELEASE",
                    "PROJECTILE_SPAWN",
                    "HIT",
                    "EXPLOSION",
                    "SPLIT",
                    "CHAIN",
                    "MARK_APPLY",
                    "MARK_TRIGGER",
                    "CONSTRUCT_SPAWN",
                    "CONSTRUCT_DESTROY"
                },
                Arrays.stream(PresentationEventKind.values()).map(Enum::name).toArray(String[]::new));
        for (var code = 0; code < 11; code++) {
            var kind = PresentationEventKind.fromWireCode(code).orElseThrow();
            assertEquals(code, kind.wireCode());
        }
        assertTrue(PresentationEventKind.fromWireCode(-1).isEmpty());
        assertTrue(PresentationEventKind.fromWireCode(11).isEmpty());
    }

    @Test
    void validEventRetainsOnlyImmutableBoundedSemanticValues() {
        var first = accepted(PresentationEvent.create(
                1L,
                PresentationEventKind.HIT.wireCode(),
                OptionalInt.of(1),
                OptionalInt.of(Integer.MAX_VALUE),
                PresentationTestFixtures.id("dimension"),
                1.25D,
                -2.5D,
                3.75D,
                1.0D,
                2.0D,
                3.0D,
                defaults(),
                Long.MIN_VALUE,
                1L));
        var second = accepted(PresentationEvent.create(
                1L,
                PresentationEventKind.HIT.wireCode(),
                OptionalInt.of(1),
                OptionalInt.of(Integer.MAX_VALUE),
                PresentationTestFixtures.id("dimension"),
                1.25D,
                -2.5D,
                3.75D,
                1.0D,
                2.0D,
                3.0D,
                defaults(),
                Long.MIN_VALUE,
                1L));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1L, first.catalogGeneration());
        assertEquals(PresentationEventKind.HIT, first.kind());
        assertEquals(Long.MIN_VALUE, first.visualSeed());
        assertEquals(1L, first.sequence());
        assertEquals(OptionalInt.of(1), first.sourceSummary().sourceEntityId());
        assertEquals(OptionalInt.of(Integer.MAX_VALUE), first.sourceSummary().targetEntityId());
        assertEquals(PresentationTestFixtures.id("dimension"), first.dimension());
        assertEquals(new PresentationPosition(1.25D, -2.5D, 3.75D), first.position());
        assertEquals(
                new PresentationDirection((short) 8_757, (short) 17_515, (short) 26_272),
                first.direction());
        assertEquals(defaults().wireAppearance(), first.appearance());
        assertTrue(first.bodySize() <= PresentationLimits.MAX_LEGAL_EVENT_BODY_BYTES);
        assertEquals(
                first.bodySize() + PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES,
                first.packetCharge());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.appearance().parameters().put(PresentationTestFixtures.id("x"), 1));
    }

    @Test
    void serverFactoryUsesTheExactPureVisualSeed() {
        var event = accepted(PresentationEvent.createServer(
                9L,
                PresentationEventKind.CAST_RELEASE.wireCode(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                PresentationTestFixtures.id("dimension"),
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                1.0D,
                0.0D,
                defaults(),
                Long.MAX_VALUE));

        assertEquals(Long.MAX_VALUE, event.sequence());
        assertEquals(PresentationSequence.visualSeed(Long.MAX_VALUE), event.visualSeed());
        assertEquals(
                PresentationEventRejection.INVALID_SEQUENCE,
                rejected(PresentationEvent.createServer(
                        9L,
                        0,
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        PresentationTestFixtures.id("dimension"),
                        0.0D,
                        0.0D,
                        0.0D,
                        0.0D,
                        1.0D,
                        0.0D,
                        defaults(),
                        0L)));
    }

    @Test
    void constructionRejectsEveryBoundedSemanticCategory() {
        assertEquals(
                PresentationEventRejection.INVALID_CATALOG_GENERATION,
                rejected(create(0L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_CATALOG_GENERATION,
                rejected(create(-1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_KIND,
                rejected(create(1L, 11, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_KIND,
                rejected(create(1L, -1, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_SOURCE_SUMMARY,
                rejected(create(1L, 0, OptionalInt.of(0), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_SOURCE_SUMMARY,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.of(0),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_SOURCE_SUMMARY,
                rejected(create(1L, 0, null, OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_SOURCE_SUMMARY,
                rejected(create(1L, 0, OptionalInt.empty(), null,
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_DIMENSION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(129), 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_DIMENSION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        null, 0, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_POSITION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 30_000_001.0D, 0, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_POSITION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, Double.NaN, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_POSITION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, -2_049.0D, 0, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_POSITION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, -30_000_001.0D, 1, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_DIRECTION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 0, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_DIRECTION,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, Double.POSITIVE_INFINITY, 0, 0, defaults(), 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_APPEARANCE,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, null, 1L)));
        assertEquals(
                PresentationEventRejection.INVALID_SEQUENCE,
                rejected(create(1L, 0, OptionalInt.empty(), OptionalInt.empty(),
                        dimension(10), 0, 0, 0, 1, 0, 0, defaults(), 0L)));

        assertEquals(
                Long.MAX_VALUE,
                accepted(create(
                                Long.MAX_VALUE,
                                0,
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                dimension(10),
                                0,
                                0,
                                0,
                                1,
                                0,
                                0,
                                defaults(),
                                Long.MAX_VALUE))
                        .catalogGeneration());
    }

    @Test
    void positionAndDirectionAcceptExactEdgesAndRejectOneOverOrInvalidQ15() {
        var edge = new PresentationPosition(
                -PresentationLimits.MAX_HORIZONTAL_POSITION,
                PresentationLimits.MAX_VERTICAL_POSITION,
                PresentationLimits.MAX_HORIZONTAL_POSITION);
        assertEquals(-30_000_000.0D, edge.x());
        assertThrows(IllegalArgumentException.class, () -> new PresentationPosition(
                0.0D, PresentationLimits.MAX_VERTICAL_POSITION + 1.0D, 0.0D));

        assertEquals(
                new PresentationDirection((short) 32_767, (short) 0, (short) 0),
                PresentationDirection.normalized(1.0D, 0.0D, 0.0D).orElseThrow());
        assertEquals(
                new PresentationDirection((short) -32_767, (short) 0, (short) 0),
                PresentationDirection.normalized(-Double.MAX_VALUE, 0.0D, 0.0D).orElseThrow());
        assertEquals(
                new PresentationDirection((short) 18_918, (short) 18_918, (short) 18_918),
                PresentationDirection.normalized(
                        Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE).orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDirection(Short.MIN_VALUE, (short) 0, (short) 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PresentationDirection((short) 0, (short) 0, (short) 0));
    }

    @Test
    void maximumLegalLayoutIsExactly897BodyAnd926Charge() {
        var dimension = dimension(PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES);
        var sound = exactLengthId("s", PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES);
        var particle = exactLengthId("p", PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES);
        var trail = exactLengthId("t", PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES);
        var parameters = maximumParameters();
        var ranges = new LinkedHashMap<ResourceLocation, AppearanceParameterPolicy.IntRange>();
        parameters.keySet().forEach(key -> ranges.put(
                key, new AppearanceParameterPolicy.IntRange(Integer.MIN_VALUE, Integer.MAX_VALUE)));
        var profiles = PresentationTestFixtures.view(
                PresentationTestFixtures.descriptor(sound, ProfileChannel.SOUND, ranges),
                PresentationTestFixtures.descriptor(particle, ProfileChannel.PARTICLE, ranges),
                PresentationTestFixtures.descriptor(trail, ProfileChannel.TRAIL, ranges));
        var patch = new AppearanceEventPatch(new AppearanceOverride(
                OptionalInt.of(Integer.MIN_VALUE),
                OptionalInt.of(Integer.MAX_VALUE),
                new ProfileSelection.Specified(sound),
                new ProfileSelection.Specified(particle),
                new ProfileSelection.Specified(trail),
                OptionalInt.of(PresentationLimits.MAX_INTENSITY_MILLI)), parameters);
        var resolved = AppearanceSemantics.resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(patch),
                new AppearanceParameterPolicy(ranges),
                profiles);
        assertEquals(AppearancePatchOutcome.APPLIED, resolved.patchOutcome());

        var event = accepted(PresentationEvent.create(
                1L,
                10,
                OptionalInt.of(Integer.MAX_VALUE),
                OptionalInt.of(Integer.MAX_VALUE),
                dimension,
                -30_000_000.0D,
                2_048.0D,
                30_000_000.0D,
                1.0D,
                1.0D,
                1.0D,
                resolved.appearance(),
                0L,
                Long.MAX_VALUE));

        assertEquals(897, event.bodySize());
        assertEquals(926, event.packetCharge());
        assertEquals(
                parameters.keySet().stream()
                        .map(ResourceLocation::toString)
                        .sorted()
                        .toList(),
                event.appearance().parameters().keySet().stream()
                        .map(ResourceLocation::toString)
                        .toList());
    }

    @Test
    void eventStripsResolutionDiagnosticsAndAllNewSemanticTypesArePackagePrivate() {
        var event = accepted(PresentationEvent.create(
                1L,
                0,
                OptionalInt.empty(),
                OptionalInt.empty(),
                dimension(10),
                0,
                0,
                0,
                1,
                0,
                0,
                defaults(),
                0L,
                1L));
        var eventFieldTypes = Arrays.stream(PresentationEvent.class.getDeclaredFields())
                .map(field -> field.getType().getName())
                .toList();

        assertTrue(eventFieldTypes.contains(PresentationAppearance.class.getName()));
        assertFalse(eventFieldTypes.contains(EffectiveAppearance.class.getName()));
        assertFalse(eventFieldTypes.contains(ResolvedProfile.class.getName()));
        assertFalse(eventFieldTypes.contains(ProfileResolutionReason.class.getName()));
        assertNotEquals(
                ProfileResolutionReason.class,
                event.appearance().getClass());

        for (var type : List.of(
                PresentationEvent.class,
                PresentationEventKind.class,
                PresentationSourceSummary.class,
                PresentationPosition.class,
                PresentationDirection.class,
                PresentationEventCreation.class,
                AcceptedPresentationEvent.class,
                RejectedPresentationEvent.class,
                PresentationEventRejection.class,
                AppearanceEventPatch.class,
                AppearanceSemantics.class,
                AppearanceResolution.class,
                AppearancePatchOutcome.class,
                EffectiveAppearance.class,
                ResolvedProfile.class,
                ProfileResolutionReason.class,
                PresentationAppearance.class,
                PresentationProfileView.class,
                PresentationProfileDescriptor.class)) {
            assertFalse(Modifier.isPublic(type.getModifiers()), type.getName());
        }
    }

    private static PresentationEventCreation create(
            long generation,
            int kind,
            OptionalInt source,
            OptionalInt target,
            ResourceLocation dimension,
            double x,
            double y,
            double z,
            double directionX,
            double directionY,
            double directionZ,
            EffectiveAppearance appearance,
            long sequence) {
        return PresentationEvent.create(
                generation,
                kind,
                source,
                target,
                dimension,
                x,
                y,
                z,
                directionX,
                directionY,
                directionZ,
                appearance,
                0L,
                sequence);
    }

    private static PresentationEvent accepted(PresentationEventCreation result) {
        return assertInstanceOf(AcceptedPresentationEvent.class, result).event();
    }

    private static PresentationEventRejection rejected(PresentationEventCreation result) {
        return assertInstanceOf(RejectedPresentationEvent.class, result).reason();
    }

    private static EffectiveAppearance defaults() {
        return AppearanceSemantics.resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                AppearanceParameterPolicy.none(),
                PresentationTestFixtures.defaults()).appearance();
    }

    private static ResourceLocation dimension(int utf8Length) {
        return exactLengthId("d", utf8Length);
    }

    private static ResourceLocation exactLengthId(String distinguishing, int utf8Length) {
        if (utf8Length < 3) {
            throw new IllegalArgumentException("fixture ID must leave room for namespace separator");
        }
        var pathLength = utf8Length - 2;
        var path = "a".repeat(pathLength - 1) + distinguishing;
        var result = ResourceLocation.fromNamespaceAndPath("x", path);
        assertEquals(utf8Length, result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        return result;
    }

    private static Map<ResourceLocation, Integer> maximumParameters() {
        var parameters = new LinkedHashMap<ResourceLocation, Integer>();
        for (var index = 0; index < PresentationLimits.MAX_EVENT_OVERRIDES; index++) {
            parameters.put(
                    exactLengthId(Integer.toString(index),
                            PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES),
                    index);
        }
        return parameters;
    }
}
