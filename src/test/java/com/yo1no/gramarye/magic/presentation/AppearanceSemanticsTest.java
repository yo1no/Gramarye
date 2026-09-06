package com.yo1no.gramarye.magic.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.validation.AppearanceFallbackReason;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AppearanceSemanticsTest {
    private static final AppearanceParameterPolicy NO_PARAMETERS =
            AppearanceParameterPolicy.none();

    @Test
    void mergesAllFourLayersFieldwiseAndPreservesExplicitDisable() {
        var skillSound = PresentationTestFixtures.id("skill_sound");
        var skillTrail = PresentationTestFixtures.id("skill_trail");
        var eventParticle = PresentationTestFixtures.id("event_particle");
        var profiles = withDefaults(
                PresentationTestFixtures.descriptor(skillSound, ProfileChannel.SOUND, Map.of()),
                PresentationTestFixtures.descriptor(skillTrail, ProfileChannel.TRAIL, Map.of()),
                PresentationTestFixtures.descriptor(
                        eventParticle, ProfileChannel.PARTICLE, Map.of()));
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.of(0xFF102030),
                OptionalInt.empty(),
                specified(skillSound),
                ProfileSelection.Inherit.INSTANCE,
                specified(skillTrail),
                OptionalInt.of(2_000)));
        var node = new RuntimeNeutralAppearanceOverride.Typed(new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.of(0xFF405060),
                ProfileSelection.Disabled.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty()));
        var patch = new AppearanceEventPatch(new AppearanceOverride(
                OptionalInt.of(0xFF708090),
                OptionalInt.empty(),
                ProfileSelection.Inherit.INSTANCE,
                specified(eventParticle),
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.of(3_000)), Map.of());

        var result = AppearanceSemantics.resolve(
                skill, node, Optional.of(patch), NO_PARAMETERS, profiles);

        assertEquals(AppearancePatchOutcome.APPLIED, result.patchOutcome());
        assertEquals(0xFF708090, result.appearance().primaryArgb());
        assertEquals(0xFF405060, result.appearance().secondaryArgb());
        assertEquals(3_000, result.appearance().intensityMilli());
        assertEquals(Optional.empty(), result.appearance().soundProfile().id());
        assertEquals(
                ProfileResolutionReason.EXPLICITLY_DISABLED,
                result.appearance().soundProfile().reason());
        assertEquals(Optional.of(eventParticle), result.appearance().particleProfile().id());
        assertEquals(Optional.of(skillTrail), result.appearance().trailProfile().id());
    }

    @Test
    void fallbackLayersContributeNothingWhileAdjacentTypedAndEventDisableLayersApply() {
        var skillSound = PresentationTestFixtures.id("fallback_neighbor_sound");
        var nodeParticle = PresentationTestFixtures.id("fallback_neighbor_particle");
        var profiles = withDefaults(
                PresentationTestFixtures.descriptor(skillSound, ProfileChannel.SOUND, Map.of()),
                PresentationTestFixtures.descriptor(
                        nodeParticle, ProfileChannel.PARTICLE, Map.of()));
        var skillDefinition = new AppearanceDefinition(
                OptionalInt.of(0x01020304),
                OptionalInt.empty(),
                specified(skillSound),
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty());
        var skillBesideFallback = resolve(
                new RuntimeNeutralAppearance.Typed(skillDefinition),
                new RuntimeNeutralAppearanceOverride.Fallback(AppearanceFallbackReason.UNPARSED),
                Optional.empty(),
                profiles).appearance();

        assertEquals(0x01020304, skillBesideFallback.primaryArgb());
        assertEquals(Optional.of(skillSound), skillBesideFallback.soundProfile().id());
        assertEquals(skillDefinition, new RuntimeNeutralAppearance.Typed(skillDefinition).definition());

        var nodeOverride = new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.of(0x05060708),
                ProfileSelection.Inherit.INSTANCE,
                specified(nodeParticle),
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty());
        var eventOverride = new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.empty(),
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Disabled.INSTANCE,
                OptionalInt.of(0));
        var nodeBesideFallback = AppearanceSemantics.resolve(
                new RuntimeNeutralAppearance.Fallback(AppearanceFallbackReason.REJECTED_NODE_LIMIT),
                new RuntimeNeutralAppearanceOverride.Typed(nodeOverride),
                Optional.of(new AppearanceEventPatch(eventOverride, Map.of())),
                NO_PARAMETERS,
                profiles);

        assertEquals(AppearancePatchOutcome.APPLIED, nodeBesideFallback.patchOutcome());
        assertEquals(0xFFFFFFFF, nodeBesideFallback.appearance().primaryArgb());
        assertEquals(0x05060708, nodeBesideFallback.appearance().secondaryArgb());
        assertEquals(Optional.of(nodeParticle), nodeBesideFallback.appearance().particleProfile().id());
        assertEquals(Optional.empty(), nodeBesideFallback.appearance().trailProfile().id());
        assertEquals(0, nodeBesideFallback.appearance().intensityMilli());
        assertEquals(ProfileSelection.Disabled.INSTANCE, eventOverride.trailProfile());
    }

    @Test
    void defaultFallbackStatesSupplyNoPersistedOverrides() {
        var defaultResult = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                PresentationTestFixtures.defaults());

        for (var topFallback : AppearanceFallbackReason.values()) {
            for (var nodeFallback : AppearanceFallbackReason.values()) {
                var result = resolve(
                        new RuntimeNeutralAppearance.Fallback(topFallback),
                        new RuntimeNeutralAppearanceOverride.Fallback(nodeFallback),
                        Optional.empty(),
                        PresentationTestFixtures.defaults());
                assertEquals(defaultResult.appearance(), result.appearance());
                assertEquals(AppearancePatchOutcome.NO_PATCH, result.patchOutcome());
            }
        }

        assertEquals(0xFFFFFFFF, defaultResult.appearance().primaryArgb());
        assertEquals(0xFFFFFFFF, defaultResult.appearance().secondaryArgb());
        assertEquals(1_000, defaultResult.appearance().intensityMilli());
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("gramarye", "default_sound"),
                AppearanceSemantics.DEFAULT_SOUND_PROFILE);
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("gramarye", "default_particle"),
                AppearanceSemantics.DEFAULT_PARTICLE_PROFILE);
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("gramarye", "default_trail"),
                AppearanceSemantics.DEFAULT_TRAIL_PROFILE);
    }

    @Test
    void profileFallbackIsIndependentAndMissingDefaultDisablesOnlyItsChannel() {
        var missing = PresentationTestFixtures.id("missing_sound");
        var wrongChannel = PresentationTestFixtures.id("wrong_trail_channel");
        var profiles = PresentationTestFixtures.view(
                PresentationTestFixtures.descriptor(
                        AppearanceSemantics.DEFAULT_SOUND_PROFILE,
                        ProfileChannel.SOUND,
                        Map.of()),
                PresentationTestFixtures.descriptor(
                        AppearanceSemantics.DEFAULT_PARTICLE_PROFILE,
                        ProfileChannel.PARTICLE,
                        Map.of()),
                PresentationTestFixtures.descriptor(
                        wrongChannel,
                        ProfileChannel.SOUND,
                        Map.of()));
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                specified(missing),
                ProfileSelection.Inherit.INSTANCE,
                specified(wrongChannel),
                OptionalInt.empty()));

        var appearance = resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                profiles).appearance();

        assertEquals(
                Optional.of(AppearanceSemantics.DEFAULT_SOUND_PROFILE),
                appearance.soundProfile().id());
        assertEquals(
                ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT,
                appearance.soundProfile().reason());
        assertEquals(
                Optional.of(AppearanceSemantics.DEFAULT_PARTICLE_PROFILE),
                appearance.particleProfile().id());
        assertEquals(Optional.empty(), appearance.trailProfile().id());
        assertEquals(
                ProfileResolutionReason.WRONG_CHANNEL_SELECTION_DEFAULT_MISSING_DISABLED,
                appearance.trailProfile().reason());
    }

    @Test
    void missingAndWrongChannelModuleDefaultsHaveDistinctDisabledReasons() {
        var wrongSoundDefault = PresentationTestFixtures.descriptor(
                AppearanceSemantics.DEFAULT_SOUND_PROFILE,
                ProfileChannel.PARTICLE,
                Map.of());
        var wrongResult = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                PresentationTestFixtures.view(wrongSoundDefault));
        var missingResult = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                PresentationTestFixtures.view());

        assertEquals(
                ProfileResolutionReason.DEFAULT_WRONG_CHANNEL_DISABLED,
                wrongResult.appearance().soundProfile().reason());
        assertEquals(
                ProfileResolutionReason.DEFAULT_MISSING_DISABLED,
                missingResult.appearance().soundProfile().reason());
        assertFalse(wrongResult.appearance().soundProfile().id().isPresent());
        assertFalse(missingResult.appearance().soundProfile().id().isPresent());
    }

    @Test
    void validPatchUsesActionAndEveryApplicableProfileRangeIntersection() {
        var key = PresentationTestFixtures.id("density");
        var sound = PresentationTestFixtures.id("adjustable_sound");
        var particle = PresentationTestFixtures.id("adjustable_particle");
        var profiles = withDefaults(
                PresentationTestFixtures.descriptor(
                        sound,
                        ProfileChannel.SOUND,
                        Map.of(key, new AppearanceParameterPolicy.IntRange(10, 80))),
                PresentationTestFixtures.descriptor(
                        particle,
                        ProfileChannel.PARTICLE,
                        Map.of(key, new AppearanceParameterPolicy.IntRange(20, 60))));
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                specified(sound),
                specified(particle),
                ProfileSelection.Disabled.INSTANCE,
                OptionalInt.empty()));
        var mutable = new LinkedHashMap<ResourceLocation, Integer>();
        mutable.put(key, 50);
        var patch = new AppearanceEventPatch(AppearanceOverride.empty(), mutable);
        mutable.put(key, 90);

        var result = AppearanceSemantics.resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(patch),
                new AppearanceParameterPolicy(
                        Map.of(key, new AppearanceParameterPolicy.IntRange(0, 100))),
                profiles);

        assertEquals(AppearancePatchOutcome.APPLIED, result.patchOutcome());
        assertEquals(Map.of(key, 50), result.appearance().parameters());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.appearance().parameters().put(key, 51));
    }

    @Test
    void invalidParameterRejectsWholePatchWithoutPartialScalarApplication() {
        var key = PresentationTestFixtures.id("density");
        var sound = PresentationTestFixtures.id("adjustable_sound");
        var profiles = withDefaults(PresentationTestFixtures.descriptor(
                sound,
                ProfileChannel.SOUND,
                Map.of(key, new AppearanceParameterPolicy.IntRange(20, 60))));
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.of(0xFF010203),
                OptionalInt.empty(),
                specified(sound),
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty()));
        var base = resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                profiles).appearance();
        var invalidPatch = new AppearanceEventPatch(new AppearanceOverride(
                OptionalInt.of(0xFFABCDEF),
                OptionalInt.empty(),
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty()), Map.of(key, 70));

        var result = AppearanceSemantics.resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(invalidPatch),
                new AppearanceParameterPolicy(
                        Map.of(key, new AppearanceParameterPolicy.IntRange(0, 100))),
                profiles);

        assertEquals(AppearancePatchOutcome.IGNORED_PARAMETER_OUT_OF_RANGE, result.patchOutcome());
        assertEquals(base, result.appearance());
        assertEquals(0xFF010203, result.appearance().primaryArgb());
        assertTrue(result.appearance().parameters().isEmpty());

        var disjointParticle = PresentationTestFixtures.id("disjoint_particle");
        var disjointProfiles = withDefaults(
                PresentationTestFixtures.descriptor(
                        sound,
                        ProfileChannel.SOUND,
                        Map.of(key, new AppearanceParameterPolicy.IntRange(0, 10))),
                PresentationTestFixtures.descriptor(
                        disjointParticle,
                        ProfileChannel.PARTICLE,
                        Map.of(key, new AppearanceParameterPolicy.IntRange(20, 30))));
        var disjointSkill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                specified(sound),
                specified(disjointParticle),
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty()));
        var disjoint = AppearanceSemantics.resolve(
                disjointSkill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(new AppearanceEventPatch(AppearanceOverride.empty(), Map.of(key, 5))),
                new AppearanceParameterPolicy(
                        Map.of(key, new AppearanceParameterPolicy.IntRange(0, 30))),
                disjointProfiles);
        assertEquals(
                AppearancePatchOutcome.IGNORED_PARAMETER_OUT_OF_RANGE,
                disjoint.patchOutcome());
        assertTrue(disjoint.appearance().parameters().isEmpty());
    }

    @Test
    void patchRejectsWrongChannelButAcceptsMissingSelectionThroughDefault() {
        var wrong = PresentationTestFixtures.id("particle_used_as_sound");
        var missing = PresentationTestFixtures.id("missing_sound");
        var profiles = withDefaults(PresentationTestFixtures.descriptor(
                wrong, ProfileChannel.PARTICLE, Map.of()));
        var wrongPatch = patchWithSound(wrong);
        var missingPatch = patchWithSound(missing);

        var wrongResult = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(wrongPatch),
                profiles);
        var missingResult = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(missingPatch),
                profiles);

        assertEquals(AppearancePatchOutcome.IGNORED_WRONG_CHANNEL, wrongResult.patchOutcome());
        assertEquals(AppearancePatchOutcome.APPLIED, missingResult.patchOutcome());
        assertEquals(
                Optional.of(AppearanceSemantics.DEFAULT_SOUND_PROFILE),
                missingResult.appearance().soundProfile().id());
        assertEquals(
                ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT,
                missingResult.appearance().soundProfile().reason());
    }

    @Test
    void patchBoundsNullsUnknownAndAbsentCapabilityAreClosedOutcomes() {
        var key = PresentationTestFixtures.id("known");
        var unknown = PresentationTestFixtures.id("unknown");
        var noCapabilityProfiles = PresentationTestFixtures.defaults();
        var policy = new AppearanceParameterPolicy(
                Map.of(key, new AppearanceParameterPolicy.IntRange(0, 10)));

        assertRejected(
                entries(9),
                policy,
                noCapabilityProfiles,
                AppearancePatchOutcome.IGNORED_PARAMETER_COUNT);
        assertRejected(
                Map.of(unknown, 1),
                policy,
                noCapabilityProfiles,
                AppearancePatchOutcome.IGNORED_UNKNOWN_PARAMETER);
        assertRejected(
                Map.of(key, 1),
                policy,
                noCapabilityProfiles,
                AppearancePatchOutcome.IGNORED_PARAMETER_WITHOUT_PROFILE_CAPABILITY);

        var withNull = new HashMap<ResourceLocation, Integer>();
        withNull.put(key, null);
        assertRejected(
                withNull,
                policy,
                noCapabilityProfiles,
                AppearancePatchOutcome.IGNORED_NULL_PARAMETER);
        var withNullKey = new HashMap<ResourceLocation, Integer>();
        withNullKey.put(null, 1);
        assertRejected(
                withNullKey,
                policy,
                noCapabilityProfiles,
                AppearancePatchOutcome.IGNORED_NULL_PARAMETER);

        var longKey = ResourceLocation.fromNamespaceAndPath("x", "k".repeat(31));
        assertRejected(
                Map.of(longKey, 1),
                new AppearanceParameterPolicy(
                        Map.of(longKey, new AppearanceParameterPolicy.IntRange(0, 10))),
                noCapabilityProfiles,
                AppearancePatchOutcome.IGNORED_PARAMETER_KEY_TOO_LONG);

        var longProfile = ResourceLocation.fromNamespaceAndPath("x", "p".repeat(127));
        var longProfileResult = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(patchWithSound(longProfile)),
                PresentationTestFixtures.defaults());
        assertEquals(
                AppearancePatchOutcome.IGNORED_PROFILE_ID_TOO_LONG,
                longProfileResult.patchOutcome());
    }

    @Test
    void absentAndValidEmptyPatchHaveDistinctNoMutationOutcomes() {
        var absent = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                PresentationTestFixtures.defaults());
        var empty = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(new AppearanceEventPatch(AppearanceOverride.empty(), Map.of())),
                PresentationTestFixtures.defaults());

        assertEquals(absent.appearance(), empty.appearance());
        assertEquals(AppearancePatchOutcome.NO_PATCH, absent.patchOutcome());
        assertEquals(AppearancePatchOutcome.EMPTY_NO_OP, empty.patchOutcome());
    }

    @Test
    void overCapacityValuesRejectBeforeTraversingOrCopyingTheirInput() {
        var overCapacity = overCapacityMap();
        var patch = new AppearanceEventPatch(AppearanceOverride.empty(), overCapacity);

        assertTrue(patch.overCapacity());
        assertTrue(patch.parameters().isEmpty());

        var defaults = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                PresentationTestFixtures.defaults()).appearance();
        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectiveAppearance(
                        defaults.primaryArgb(),
                        defaults.secondaryArgb(),
                        defaults.intensityMilli(),
                        defaults.soundProfile(),
                        defaults.particleProfile(),
                        defaults.trailProfile(),
                        overCapacity));
    }

    private static AppearanceResolution resolve(
            RuntimeNeutralAppearance skill,
            RuntimeNeutralAppearanceOverride node,
            Optional<AppearanceEventPatch> patch,
            PresentationProfileView profiles) {
        return AppearanceSemantics.resolve(skill, node, patch, NO_PARAMETERS, profiles);
    }

    private static AppearanceEventPatch patchWithSound(ResourceLocation id) {
        return new AppearanceEventPatch(new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.empty(),
                specified(id),
                ProfileSelection.Inherit.INSTANCE,
                ProfileSelection.Inherit.INSTANCE,
                OptionalInt.empty()), Map.of());
    }

    private static void assertRejected(
            Map<ResourceLocation, Integer> parameters,
            AppearanceParameterPolicy policy,
            PresentationProfileView profiles,
            AppearancePatchOutcome expected) {
        var base = resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                profiles).appearance();
        var result = AppearanceSemantics.resolve(
                RuntimeNeutralAppearance.Default.INSTANCE,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(new AppearanceEventPatch(AppearanceOverride.empty(), parameters)),
                policy,
                profiles);
        assertEquals(expected, result.patchOutcome());
        assertEquals(base, result.appearance());
    }

    private static Map<ResourceLocation, Integer> entries(int count) {
        var result = new LinkedHashMap<ResourceLocation, Integer>();
        for (var index = 0; index < count; index++) {
            result.put(PresentationTestFixtures.id("key_" + index), index);
        }
        return result;
    }

    private static Map<ResourceLocation, Integer> overCapacityMap() {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<ResourceLocation, Integer>> entrySet() {
                return new AbstractSet<>() {
                    @Override
                    public java.util.Iterator<Entry<ResourceLocation, Integer>> iterator() {
                        throw new AssertionError("over-cap input was traversed");
                    }

                    @Override
                    public int size() {
                        return 9;
                    }
                };
            }

            @Override
            public int size() {
                return 9;
            }
        };
    }

    private static ProfileSelection specified(ResourceLocation id) {
        return new ProfileSelection.Specified(id);
    }

    private static PresentationProfileView withDefaults(
            PresentationProfileDescriptor... additions) {
        var descriptors = new java.util.ArrayList<PresentationProfileDescriptor>();
        descriptors.add(PresentationTestFixtures.descriptor(
                AppearanceSemantics.DEFAULT_SOUND_PROFILE, ProfileChannel.SOUND, Map.of()));
        descriptors.add(PresentationTestFixtures.descriptor(
                AppearanceSemantics.DEFAULT_PARTICLE_PROFILE, ProfileChannel.PARTICLE, Map.of()));
        descriptors.add(PresentationTestFixtures.descriptor(
                AppearanceSemantics.DEFAULT_TRAIL_PROFILE, ProfileChannel.TRAIL, Map.of()));
        descriptors.addAll(List.of(additions));
        return PresentationTestFixtures.view(
                descriptors.toArray(PresentationProfileDescriptor[]::new));
    }
}
