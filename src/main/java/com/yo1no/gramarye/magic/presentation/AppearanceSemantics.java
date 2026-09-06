package com.yo1no.gramarye.magic.presentation;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Pure four-layer Appearance resolution and whole-patch admission. */
final class AppearanceSemantics {
    static final ResourceLocation DEFAULT_SOUND_PROFILE =
            ResourceLocation.fromNamespaceAndPath("gramarye", "default_sound");
    static final ResourceLocation DEFAULT_PARTICLE_PROFILE =
            ResourceLocation.fromNamespaceAndPath("gramarye", "default_particle");
    static final ResourceLocation DEFAULT_TRAIL_PROFILE =
            ResourceLocation.fromNamespaceAndPath("gramarye", "default_trail");

    private static final int DEFAULT_ARGB = 0xFFFFFFFF;
    private static final int DEFAULT_INTENSITY_MILLI = 1_000;

    private AppearanceSemantics() {}

    static AppearanceResolution resolve(
            RuntimeNeutralAppearance skillAppearance,
            RuntimeNeutralAppearanceOverride nodeAppearance,
            Optional<AppearanceEventPatch> eventPatch,
            AppearanceParameterPolicy actionPolicy,
            PresentationProfileView profiles) {
        Objects.requireNonNull(skillAppearance, "skillAppearance");
        Objects.requireNonNull(nodeAppearance, "nodeAppearance");
        Objects.requireNonNull(eventPatch, "eventPatch");
        Objects.requireNonNull(actionPolicy, "actionPolicy");
        Objects.requireNonNull(profiles, "profiles");

        var persisted = moduleDefaults();
        if (skillAppearance instanceof RuntimeNeutralAppearance.Typed typed) {
            persisted = apply(persisted, typed.definition());
        }
        if (nodeAppearance instanceof RuntimeNeutralAppearanceOverride.Typed typed) {
            persisted = apply(persisted, typed.override());
        }

        var base = resolveProfiles(persisted, Map.of(), profiles);
        if (eventPatch.isEmpty()) {
            return new AppearanceResolution(base, AppearancePatchOutcome.NO_PATCH);
        }

        var patch = eventPatch.orElseThrow();
        if (patch.overCapacity()) {
            return ignored(base, AppearancePatchOutcome.IGNORED_PARAMETER_COUNT);
        }
        if (patch.isEmpty()) {
            return new AppearanceResolution(base, AppearancePatchOutcome.EMPTY_NO_OP);
        }

        var eventOverride = patch.override();
        var selectionFailure = firstSelectionFailure(eventOverride, profiles);
        if (selectionFailure.isPresent()) {
            return ignored(base, selectionFailure.orElseThrow());
        }

        var patched = apply(persisted, eventOverride);
        var effectiveWithoutParameters = resolveProfiles(patched, Map.of(), profiles);
        var parameterFailure = firstParameterFailure(
                patch.parameters(), actionPolicy, effectiveWithoutParameters, profiles);
        if (parameterFailure.isPresent()) {
            return ignored(base, parameterFailure.orElseThrow());
        }

        var effective = new EffectiveAppearance(
                effectiveWithoutParameters.primaryArgb(),
                effectiveWithoutParameters.secondaryArgb(),
                effectiveWithoutParameters.intensityMilli(),
                effectiveWithoutParameters.soundProfile(),
                effectiveWithoutParameters.particleProfile(),
                effectiveWithoutParameters.trailProfile(),
                patch.parameters());
        return new AppearanceResolution(effective, AppearancePatchOutcome.APPLIED);
    }

    private static AppearanceResolution ignored(
            EffectiveAppearance base, AppearancePatchOutcome outcome) {
        return new AppearanceResolution(base, outcome);
    }

    private static MergedAppearance moduleDefaults() {
        return new MergedAppearance(
                DEFAULT_ARGB,
                DEFAULT_ARGB,
                new ProfileSelection.Specified(DEFAULT_SOUND_PROFILE),
                new ProfileSelection.Specified(DEFAULT_PARTICLE_PROFILE),
                new ProfileSelection.Specified(DEFAULT_TRAIL_PROFILE),
                DEFAULT_INTENSITY_MILLI);
    }

    private static MergedAppearance apply(MergedAppearance base, AppearanceDefinition overlay) {
        return new MergedAppearance(
                scalar(overlay.primaryArgb(), base.primaryArgb()),
                scalar(overlay.secondaryArgb(), base.secondaryArgb()),
                selection(overlay.soundProfile(), base.soundProfile()),
                selection(overlay.particleProfile(), base.particleProfile()),
                selection(overlay.trailProfile(), base.trailProfile()),
                scalar(overlay.intensityMilli(), base.intensityMilli()));
    }

    private static MergedAppearance apply(MergedAppearance base, AppearanceOverride overlay) {
        return new MergedAppearance(
                scalar(overlay.primaryArgb(), base.primaryArgb()),
                scalar(overlay.secondaryArgb(), base.secondaryArgb()),
                selection(overlay.soundProfile(), base.soundProfile()),
                selection(overlay.particleProfile(), base.particleProfile()),
                selection(overlay.trailProfile(), base.trailProfile()),
                scalar(overlay.intensityMilli(), base.intensityMilli()));
    }

    private static int scalar(OptionalInt overlay, int base) {
        return overlay.orElse(base);
    }

    private static ProfileSelection selection(
            ProfileSelection overlay, ProfileSelection base) {
        return overlay instanceof ProfileSelection.Inherit ? base : overlay;
    }

    private static Optional<AppearancePatchOutcome> firstSelectionFailure(
            AppearanceOverride patch, PresentationProfileView profiles) {
        var checks = new ArrayList<SelectionCheck>(3);
        checks.add(new SelectionCheck(patch.soundProfile(), ProfileChannel.SOUND));
        checks.add(new SelectionCheck(patch.particleProfile(), ProfileChannel.PARTICLE));
        checks.add(new SelectionCheck(patch.trailProfile(), ProfileChannel.TRAIL));
        for (var check : checks) {
            if (!(check.selection() instanceof ProfileSelection.Specified specified)) {
                continue;
            }
            if (utf8Length(specified.id()) > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
                return Optional.of(AppearancePatchOutcome.IGNORED_PROFILE_ID_TOO_LONG);
            }
            var descriptor = find(profiles, specified.id());
            if (descriptor.isPresent() && descriptor.orElseThrow().channel() != check.channel()) {
                return Optional.of(AppearancePatchOutcome.IGNORED_WRONG_CHANNEL);
            }
        }
        return Optional.empty();
    }

    private static Optional<AppearancePatchOutcome> firstParameterFailure(
            Map<ResourceLocation, Integer> parameters,
            AppearanceParameterPolicy actionPolicy,
            EffectiveAppearance effective,
            PresentationProfileView profiles) {
        var sorted = new TreeMap<ResourceLocation, Integer>(
                Comparator.nullsFirst(Comparator.comparing(ResourceLocation::toString)));
        sorted.putAll(parameters);
        for (var entry : sorted.entrySet()) {
            var key = entry.getKey();
            var value = entry.getValue();
            if (key == null || value == null) {
                return Optional.of(AppearancePatchOutcome.IGNORED_NULL_PARAMETER);
            }
            if (utf8Length(key) > PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES) {
                return Optional.of(AppearancePatchOutcome.IGNORED_PARAMETER_KEY_TOO_LONG);
            }
            var actionRange = actionPolicy.integerRanges().get(key);
            if (actionRange == null) {
                return Optional.of(AppearancePatchOutcome.IGNORED_UNKNOWN_PARAMETER);
            }

            var minimum = actionRange.minInclusive();
            var maximum = actionRange.maxInclusive();
            var applicable = false;
            for (var resolved : resolvedProfiles(effective)) {
                if (resolved.id().isEmpty()) {
                    continue;
                }
                var descriptor = find(profiles, resolved.id().orElseThrow()).orElseThrow(
                        () -> new IllegalStateException("resolved Profile disappeared from its view"));
                var range = descriptor.capabilities().adjustableParameters()
                        .integerRanges().get(key);
                if (range != null) {
                    applicable = true;
                    minimum = Math.max(minimum, range.minInclusive());
                    maximum = Math.min(maximum, range.maxInclusive());
                }
            }
            if (!applicable) {
                return Optional.of(
                        AppearancePatchOutcome.IGNORED_PARAMETER_WITHOUT_PROFILE_CAPABILITY);
            }
            if (minimum > maximum || value < minimum || value > maximum) {
                return Optional.of(AppearancePatchOutcome.IGNORED_PARAMETER_OUT_OF_RANGE);
            }
        }
        return Optional.empty();
    }

    private static java.util.List<ResolvedProfile> resolvedProfiles(
            EffectiveAppearance appearance) {
        return java.util.List.of(
                appearance.soundProfile(),
                appearance.particleProfile(),
                appearance.trailProfile());
    }

    private static EffectiveAppearance resolveProfiles(
            MergedAppearance value,
            Map<ResourceLocation, Integer> parameters,
            PresentationProfileView profiles) {
        return new EffectiveAppearance(
                value.primaryArgb(),
                value.secondaryArgb(),
                value.intensityMilli(),
                resolveProfile(
                        ProfileChannel.SOUND,
                        value.soundProfile(),
                        DEFAULT_SOUND_PROFILE,
                        profiles),
                resolveProfile(
                        ProfileChannel.PARTICLE,
                        value.particleProfile(),
                        DEFAULT_PARTICLE_PROFILE,
                        profiles),
                resolveProfile(
                        ProfileChannel.TRAIL,
                        value.trailProfile(),
                        DEFAULT_TRAIL_PROFILE,
                        profiles),
                parameters);
    }

    private static ResolvedProfile resolveProfile(
            ProfileChannel channel,
            ProfileSelection selection,
            ResourceLocation defaultId,
            PresentationProfileView profiles) {
        if (selection instanceof ProfileSelection.Disabled) {
            return new ResolvedProfile(
                    channel, Optional.empty(), ProfileResolutionReason.EXPLICITLY_DISABLED);
        }

        var selectedId = ((ProfileSelection.Specified) selection).id();
        var selected = classify(profiles, selectedId, channel);
        if (selected == LookupStatus.MATCH) {
            return new ResolvedProfile(
                    channel, Optional.of(selectedId), ProfileResolutionReason.SELECTED);
        }

        var defaultStatus = classify(profiles, defaultId, channel);
        if (defaultStatus == LookupStatus.MATCH) {
            var reason = selected == LookupStatus.MISSING
                    ? ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT
                    : ProfileResolutionReason.WRONG_CHANNEL_SELECTION_USED_DEFAULT;
            return new ResolvedProfile(channel, Optional.of(defaultId), reason);
        }

        var selectedIsDefault = selectedId.equals(defaultId);
        var reason = disabledReason(selected, defaultStatus, selectedIsDefault);
        return new ResolvedProfile(channel, Optional.empty(), reason);
    }

    private static ProfileResolutionReason disabledReason(
            LookupStatus selected, LookupStatus defaultStatus, boolean selectedIsDefault) {
        if (selectedIsDefault) {
            return defaultStatus == LookupStatus.MISSING
                    ? ProfileResolutionReason.DEFAULT_MISSING_DISABLED
                    : ProfileResolutionReason.DEFAULT_WRONG_CHANNEL_DISABLED;
        }
        if (selected == LookupStatus.MISSING) {
            return defaultStatus == LookupStatus.MISSING
                    ? ProfileResolutionReason.MISSING_SELECTION_DEFAULT_MISSING_DISABLED
                    : ProfileResolutionReason.MISSING_SELECTION_DEFAULT_WRONG_CHANNEL_DISABLED;
        }
        return defaultStatus == LookupStatus.MISSING
                ? ProfileResolutionReason.WRONG_CHANNEL_SELECTION_DEFAULT_MISSING_DISABLED
                : ProfileResolutionReason.WRONG_CHANNEL_SELECTION_DEFAULT_WRONG_CHANNEL_DISABLED;
    }

    private static LookupStatus classify(
            PresentationProfileView profiles,
            ResourceLocation id,
            ProfileChannel expectedChannel) {
        if (utf8Length(id) > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            return LookupStatus.MISSING;
        }
        var descriptor = find(profiles, id);
        if (descriptor.isEmpty()) {
            return LookupStatus.MISSING;
        }
        return descriptor.orElseThrow().channel() == expectedChannel
                ? LookupStatus.MATCH
                : LookupStatus.WRONG_CHANNEL;
    }

    private static Optional<PresentationProfileDescriptor> find(
            PresentationProfileView profiles, ResourceLocation id) {
        var result = Objects.requireNonNull(profiles.find(id), "Profile view result");
        if (result.isPresent() && !result.orElseThrow().id().equals(id)) {
            throw new IllegalStateException("Profile view returned a descriptor for another ID");
        }
        return result;
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private enum LookupStatus {
        MATCH,
        MISSING,
        WRONG_CHANNEL
    }

    private record SelectionCheck(ProfileSelection selection, ProfileChannel channel) {}

    private record MergedAppearance(
            int primaryArgb,
            int secondaryArgb,
            ProfileSelection soundProfile,
            ProfileSelection particleProfile,
            ProfileSelection trailProfile,
            int intensityMilli) {}
}

record AppearanceResolution(
        EffectiveAppearance appearance, AppearancePatchOutcome patchOutcome) {
    AppearanceResolution {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(patchOutcome, "patchOutcome");
    }
}

enum AppearancePatchOutcome {
    NO_PATCH,
    EMPTY_NO_OP,
    APPLIED,
    IGNORED_PARAMETER_COUNT,
    IGNORED_PROFILE_ID_TOO_LONG,
    IGNORED_WRONG_CHANNEL,
    IGNORED_NULL_PARAMETER,
    IGNORED_PARAMETER_KEY_TOO_LONG,
    IGNORED_UNKNOWN_PARAMETER,
    IGNORED_PARAMETER_WITHOUT_PROFILE_CAPABILITY,
    IGNORED_PARAMETER_OUT_OF_RANGE
}
