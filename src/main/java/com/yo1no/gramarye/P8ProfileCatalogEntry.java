package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** One immutable, bounded P8 Profile-catalog wire entry. */
final class P8ProfileCatalogEntry {
    private final ResourceLocation profileId;
    private final ResourceLocation typeId;
    private final ProfileChannel channel;
    private final ResourceLocation clientFactoryId;
    private final int configurationVersion;
    private final Optional<String> canonicalConfigurationJson;
    private final Optional<ProfileConfiguration> decodedConfiguration;
    private final Optional<P8DecodedProfileConfiguration<?>> decodedProfile;
    private final int envelopeBytes;
    private final int wireBodyBytes;

    P8ProfileCatalogEntry(
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel channel,
            ResourceLocation clientFactoryId,
            int configurationVersion,
            String canonicalConfigurationJson) {
        this(
                profileId,
                typeId,
                channel,
                clientFactoryId,
                configurationVersion,
                Optional.of(Objects.requireNonNull(
                        canonicalConfigurationJson, "canonicalConfigurationJson")),
                Optional.empty(),
                Optional.empty(),
                0,
                false);
    }

    P8ProfileCatalogEntry(
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel channel,
            ResourceLocation clientFactoryId,
            int configurationVersion,
            String canonicalConfigurationJson,
            ProfileConfiguration decodedConfiguration) {
        this(
                profileId,
                typeId,
                channel,
                clientFactoryId,
                configurationVersion,
                Optional.of(Objects.requireNonNull(
                        canonicalConfigurationJson, "canonicalConfigurationJson")),
                Optional.of(Objects.requireNonNull(
                        decodedConfiguration, "decodedConfiguration")),
                Optional.empty(),
                0,
                false);
    }

    static P8ProfileCatalogEntry incoming(
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel channel,
            ResourceLocation clientFactoryId,
            int configurationVersion,
            int envelopeBytes,
            Optional<String> canonicalConfigurationJson,
            Optional<P8DecodedProfileConfiguration<?>> decodedProfile) {
        return new P8ProfileCatalogEntry(
                profileId,
                typeId,
                channel,
                clientFactoryId,
                configurationVersion,
                canonicalConfigurationJson,
                Objects.requireNonNull(decodedProfile, "decodedProfile")
                        .map(value -> value.configuration()),
                decodedProfile,
                envelopeBytes,
                true);
    }

    private P8ProfileCatalogEntry(
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel channel,
            ResourceLocation clientFactoryId,
            int configurationVersion,
            Optional<String> canonicalConfigurationJson,
            Optional<ProfileConfiguration> decodedConfiguration,
            Optional<P8DecodedProfileConfiguration<?>> decodedProfile,
            int incomingEnvelopeBytes,
            boolean canonicalJsonAlreadyChecked) {
        this.profileId = requireId(profileId, "profileId");
        this.typeId = requireId(typeId, "typeId");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.clientFactoryId = requireId(clientFactoryId, "clientFactoryId");
        if (configurationVersion < 0
                || configurationVersion > PresentationLimits.MAX_PROFILE_CONFIGURATION_VERSION) {
            throw new IllegalArgumentException(
                    "configurationVersion is outside the P8 bound");
        }
        this.configurationVersion = configurationVersion;
        this.canonicalConfigurationJson = Objects.requireNonNull(
                canonicalConfigurationJson, "canonicalConfigurationJson");
        this.canonicalConfigurationJson.ifPresent(source -> {
            if (!canonicalJsonAlreadyChecked) {
                P8PayloadCodecSupport.requireCanonicalConfigurationJson(source);
            }
        });
        this.decodedConfiguration = Objects.requireNonNull(
                decodedConfiguration, "decodedConfiguration");
        this.decodedProfile = Objects.requireNonNull(decodedProfile, "decodedProfile");
        if (this.decodedProfile.isPresent()
                && (this.decodedConfiguration.isEmpty()
                        || this.decodedProfile.orElseThrow().configuration()
                                != this.decodedConfiguration.orElseThrow())) {
            throw new IllegalArgumentException(
                    "typed and erased decoded configurations must share identity");
        }
        if (this.canonicalConfigurationJson.isEmpty()
                && this.decodedConfiguration.isPresent()) {
            throw new IllegalArgumentException(
                    "decoded configuration requires retained canonical configuration");
        }

        var retainedEnvelopeBytes = this.canonicalConfigurationJson
                .map(source -> Math.addExact(
                        P8PayloadCodecSupport.canonicalVarIntSize(configurationVersion),
                        source.getBytes(StandardCharsets.UTF_8).length))
                .orElse(incomingEnvelopeBytes);
        if (incomingEnvelopeBytes != 0
                && this.canonicalConfigurationJson.isPresent()
                && incomingEnvelopeBytes != retainedEnvelopeBytes) {
            throw new IllegalArgumentException(
                    "retained configuration disagrees with its envelope length");
        }
        envelopeBytes = retainedEnvelopeBytes;
        if (envelopeBytes < PresentationLimits.MIN_PROFILE_ENVELOPE_BYTES
                || envelopeBytes > PresentationLimits.MAX_PROFILE_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Profile envelope is outside the P8 bound");
        }
        wireBodyBytes = P8PayloadCodecSupport.profileCatalogEntryBodySize(this);
    }

    ResourceLocation profileId() {
        return profileId;
    }

    ResourceLocation typeId() {
        return typeId;
    }

    ProfileChannel channel() {
        return channel;
    }

    ResourceLocation clientFactoryId() {
        return clientFactoryId;
    }

    int configurationVersion() {
        return configurationVersion;
    }

    Optional<String> retainedCanonicalConfigurationJson() {
        return canonicalConfigurationJson;
    }

    String canonicalConfigurationJsonForEncoding() {
        return canonicalConfigurationJson.orElseThrow(() ->
                new IllegalStateException("unavailable incoming Profile has no retained JSON"));
    }

    Optional<ProfileConfiguration> decodedConfiguration() {
        return decodedConfiguration;
    }

    Optional<P8DecodedProfileConfiguration<?>> decodedProfile() {
        return decodedProfile;
    }

    int envelopeBytes() {
        return envelopeBytes;
    }

    int wireBodyBytes() {
        return wireBodyBytes;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof P8ProfileCatalogEntry that
                        && configurationVersion == that.configurationVersion
                        && profileId.equals(that.profileId)
                        && typeId.equals(that.typeId)
                        && channel == that.channel
                        && clientFactoryId.equals(that.clientFactoryId)
                        && canonicalConfigurationJson.equals(that.canonicalConfigurationJson)
                        && decodedConfiguration.equals(that.decodedConfiguration)
                        && decodedProfile.equals(that.decodedProfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                profileId,
                typeId,
                channel,
                clientFactoryId,
                configurationVersion,
                canonicalConfigurationJson,
                decodedConfiguration,
                decodedProfile);
    }

    private static ResourceLocation requireId(ResourceLocation id, String name) {
        Objects.requireNonNull(id, name);
        if (id.toString().getBytes(StandardCharsets.UTF_8).length
                > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalArgumentException(name + " exceeds the P8 UTF-8 bound");
        }
        return id;
    }
}

/** Client-decode-only generic witness retained without erasing the validated value. */
final class P8DecodedProfileConfiguration<C extends ProfileConfiguration> {
    private final ProfileType<C> type;
    private final C configuration;
    private final ProfileCost estimatedCost;

    P8DecodedProfileConfiguration(
            ProfileType<C> type, C configuration, ProfileCost estimatedCost) {
        this.type = Objects.requireNonNull(type, "type");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost");
    }

    ProfileType<C> type() {
        return type;
    }

    C configuration() {
        return configuration;
    }

    ProfileCost estimatedCost() {
        return estimatedCost;
    }
}
