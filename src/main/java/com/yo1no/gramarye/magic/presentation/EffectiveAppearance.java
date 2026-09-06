package com.yo1no.gramarye.magic.presentation;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Immutable raw-free result of module, persisted, node, and trusted-patch resolution. */
record EffectiveAppearance(
        int primaryArgb,
        int secondaryArgb,
        int intensityMilli,
        ResolvedProfile soundProfile,
        ResolvedProfile particleProfile,
        ResolvedProfile trailProfile,
        Map<ResourceLocation, Integer> parameters) {
    EffectiveAppearance {
        if (intensityMilli < 0 || intensityMilli > PresentationLimits.MAX_INTENSITY_MILLI) {
            throw new IllegalArgumentException("effective intensity is outside the P8 bound");
        }
        soundProfile = requireChannel(soundProfile, ProfileChannel.SOUND);
        particleProfile = requireChannel(particleProfile, ProfileChannel.PARTICLE);
        trailProfile = requireChannel(trailProfile, ProfileChannel.TRAIL);
        parameters = immutableSorted(parameters);
        for (var entry : parameters.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "parameter key");
            Objects.requireNonNull(entry.getValue(), "parameter value");
            if (utf8Length(entry.getKey()) > PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES) {
                throw new IllegalArgumentException("parameter key exceeds the P8 UTF-8 bound");
            }
        }
    }

    PresentationAppearance wireAppearance() {
        return new PresentationAppearance(
                primaryArgb,
                secondaryArgb,
                intensityMilli,
                soundProfile.id(),
                particleProfile.id(),
                trailProfile.id(),
                parameters);
    }

    private static ResolvedProfile requireChannel(
            ResolvedProfile profile, ProfileChannel expected) {
        Objects.requireNonNull(profile, expected.name().toLowerCase() + "Profile");
        if (profile.channel() != expected) {
            throw new IllegalArgumentException("resolved Profile has the wrong channel");
        }
        return profile;
    }

    private static Map<ResourceLocation, Integer> immutableSorted(
            Map<ResourceLocation, Integer> source) {
        Objects.requireNonNull(source, "parameters");
        if (source.size() > PresentationLimits.MAX_EVENT_OVERRIDES) {
            throw new IllegalArgumentException("effective parameters exceed the P8 bound");
        }
        var copy = new TreeMap<ResourceLocation, Integer>(
                Comparator.comparing(ResourceLocation::toString));
        copy.putAll(source);
        return Collections.unmodifiableNavigableMap(copy);
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}

record ResolvedProfile(
        ProfileChannel channel,
        Optional<ResourceLocation> id,
        ProfileResolutionReason reason) {
    ResolvedProfile {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        if (reason.hasProfile() != id.isPresent()) {
            throw new IllegalArgumentException("Profile resolution reason and value disagree");
        }
        if (id.isPresent()
                && id.orElseThrow().toString().getBytes(StandardCharsets.UTF_8).length
                        > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalArgumentException("resolved Profile ID exceeds the P8 UTF-8 bound");
        }
    }
}

enum ProfileResolutionReason {
    SELECTED(true),
    EXPLICITLY_DISABLED(false),
    MISSING_SELECTION_USED_DEFAULT(true),
    WRONG_CHANNEL_SELECTION_USED_DEFAULT(true),
    DEFAULT_MISSING_DISABLED(false),
    DEFAULT_WRONG_CHANNEL_DISABLED(false),
    MISSING_SELECTION_DEFAULT_MISSING_DISABLED(false),
    MISSING_SELECTION_DEFAULT_WRONG_CHANNEL_DISABLED(false),
    WRONG_CHANNEL_SELECTION_DEFAULT_MISSING_DISABLED(false),
    WRONG_CHANNEL_SELECTION_DEFAULT_WRONG_CHANNEL_DISABLED(false);

    private final boolean hasProfile;

    ProfileResolutionReason(boolean hasProfile) {
        this.hasProfile = hasProfile;
    }

    boolean hasProfile() {
        return hasProfile;
    }
}

/** Event-retained Appearance value. Resolution diagnostics are deliberately stripped. */
record PresentationAppearance(
        int primaryArgb,
        int secondaryArgb,
        int intensityMilli,
        Optional<ResourceLocation> soundProfileId,
        Optional<ResourceLocation> particleProfileId,
        Optional<ResourceLocation> trailProfileId,
        Map<ResourceLocation, Integer> parameters) {
    PresentationAppearance {
        if (intensityMilli < 0 || intensityMilli > PresentationLimits.MAX_INTENSITY_MILLI) {
            throw new IllegalArgumentException("event intensity is outside the P8 bound");
        }
        soundProfileId = immutableId(soundProfileId, "soundProfileId");
        particleProfileId = immutableId(particleProfileId, "particleProfileId");
        trailProfileId = immutableId(trailProfileId, "trailProfileId");
        parameters = immutableSorted(parameters);
    }

    private static Optional<ResourceLocation> immutableId(
            Optional<ResourceLocation> source, String name) {
        Objects.requireNonNull(source, name);
        if (source.isPresent()
                && utf8Length(source.orElseThrow())
                        > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalArgumentException(name + " exceeds the P8 UTF-8 bound");
        }
        return source;
    }

    private static Map<ResourceLocation, Integer> immutableSorted(
            Map<ResourceLocation, Integer> source) {
        Objects.requireNonNull(source, "parameters");
        if (source.size() > PresentationLimits.MAX_EVENT_OVERRIDES) {
            throw new IllegalArgumentException("event parameters exceed the P8 bound");
        }
        var copy = new TreeMap<ResourceLocation, Integer>(
                Comparator.comparing(ResourceLocation::toString));
        for (var entry : source.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "parameter key");
            Objects.requireNonNull(entry.getValue(), "parameter value");
            if (utf8Length(entry.getKey()) > PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES) {
                throw new IllegalArgumentException("parameter key exceeds the P8 UTF-8 bound");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableNavigableMap(copy);
    }

    private static int utf8Length(ResourceLocation id) {
        return id.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
