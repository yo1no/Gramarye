package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Complete immutable Profile catalog carried by one P8 catalog payload. */
final class P8ProfileCatalogSnapshot {
    private static final ResourceLocation DEFAULT_SOUND = id("default_sound");
    private static final ResourceLocation DEFAULT_PARTICLE = id("default_particle");
    private static final ResourceLocation DEFAULT_TRAIL = id("default_trail");
    private static final ResourceLocation SOUND_TYPE = id("sound");
    private static final ResourceLocation PARTICLE_TYPE = id("particle");
    private static final ResourceLocation TRAIL_TYPE = id("trail");

    private static final String DEFAULT_SOUND_JSON =
            "{\"pitch_milli\":1000,\"sound\":\"minecraft:entity.experience_orb.pickup\","
                    + "\"volume_milli\":600}";
    private static final String DEFAULT_PARTICLE_JSON =
            "{\"count\":8,\"lifetime_ticks\":20,\"particle\":\"minecraft:enchant\","
                    + "\"size_milli_blocks\":250,\"speed_milli_blocks\":50}";
    private static final String DEFAULT_TRAIL_JSON =
            "{\"lifetime_ticks\":16,\"particle\":\"minecraft:enchant\","
                    + "\"sample_interval_ticks\":2,\"segments\":8,"
                    + "\"size_milli_blocks\":200}";

    private final long catalogGeneration;
    private final List<P8ProfileCatalogEntry> entries;
    private final Map<ResourceLocation, P8ProfileCatalogEntry> entriesById;
    private final int wireBodyBytes;

    P8ProfileCatalogSnapshot(
            long catalogGeneration, List<P8ProfileCatalogEntry> entries) {
        this(catalogGeneration, entries, false);
    }

    static P8ProfileCatalogSnapshot incoming(
            long catalogGeneration, List<P8ProfileCatalogEntry> entries) {
        return new P8ProfileCatalogSnapshot(catalogGeneration, entries, true);
    }

    private P8ProfileCatalogSnapshot(
            long catalogGeneration,
            List<P8ProfileCatalogEntry> entries,
            boolean allowUnavailableIncomingEntries) {
        if (catalogGeneration < 1L) {
            throw new IllegalArgumentException("catalogGeneration must be positive");
        }
        Objects.requireNonNull(entries, "entries");
        if (entries.size() < 3
                || entries.size() > PresentationLimits.MAX_PROFILE_INSTANCES) {
            throw new IllegalArgumentException("catalog entry count is outside the P8 bound");
        }

        var copiedEntries = new ArrayList<P8ProfileCatalogEntry>(entries.size());
        var indexedEntries = new LinkedHashMap<ResourceLocation, P8ProfileCatalogEntry>();
        var channelCounts = new EnumMap<ProfileChannel, Integer>(ProfileChannel.class);
        for (var channel : ProfileChannel.values()) {
            channelCounts.put(channel, 0);
        }
        ResourceLocation previousId = null;
        for (var entry : entries) {
            Objects.requireNonNull(entry, "catalog entry");
            if (!allowUnavailableIncomingEntries
                    && entry.retainedCanonicalConfigurationJson().isEmpty()) {
                throw new IllegalArgumentException(
                        "outgoing catalog entry has no canonical configuration");
            }
            if (previousId != null && previousId.compareTo(entry.profileId()) >= 0) {
                throw new IllegalArgumentException(
                        "catalog Profile IDs must be strictly ascending");
            }
            copiedEntries.add(entry);
            indexedEntries.put(entry.profileId(), entry);
            var channelCount = Math.addExact(channelCounts.get(entry.channel()), 1);
            if (channelCount > PresentationLimits.MAX_PROFILE_INSTANCES_PER_CHANNEL) {
                throw new IllegalArgumentException(
                        "catalog channel count exceeds the P8 bound");
            }
            channelCounts.put(entry.channel(), channelCount);
            previousId = entry.profileId();
        }
        requireDefault(
                indexedEntries,
                DEFAULT_SOUND,
                SOUND_TYPE,
                ProfileChannel.SOUND,
                DEFAULT_SOUND_JSON);
        requireDefault(
                indexedEntries,
                DEFAULT_PARTICLE,
                PARTICLE_TYPE,
                ProfileChannel.PARTICLE,
                DEFAULT_PARTICLE_JSON);
        requireDefault(
                indexedEntries,
                DEFAULT_TRAIL,
                TRAIL_TYPE,
                ProfileChannel.TRAIL,
                DEFAULT_TRAIL_JSON);

        this.catalogGeneration = catalogGeneration;
        this.entries = List.copyOf(copiedEntries);
        this.entriesById = Collections.unmodifiableMap(indexedEntries);
        wireBodyBytes = P8PayloadCodecSupport.profileCatalogBodySize(this.entries);
        if (wireBodyBytes > PresentationLimits.MAX_PROFILE_CATALOG_BODY_BYTES
                || wireBodyBytes
                        > PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES) {
            throw new IllegalArgumentException("catalog body exceeds the P8 bound");
        }
    }

    long catalogGeneration() {
        return catalogGeneration;
    }

    List<P8ProfileCatalogEntry> entries() {
        return entries;
    }

    P8ProfileCatalogEntry entry(ResourceLocation profileId) {
        return entriesById.get(Objects.requireNonNull(profileId, "profileId"));
    }

    int wireBodyBytes() {
        return wireBodyBytes;
    }

    void requireLocallyDecodedDefaults() {
        requireDecodedDefault(DEFAULT_SOUND);
        requireDecodedDefault(DEFAULT_PARTICLE);
        requireDecodedDefault(DEFAULT_TRAIL);
    }

    private void requireDecodedDefault(ResourceLocation profileId) {
        if (entriesById.get(profileId).decodedConfiguration().isEmpty()) {
            throw new IllegalArgumentException(
                    "required catalog default is not locally decodable");
        }
    }

    private static void requireDefault(
            Map<ResourceLocation, P8ProfileCatalogEntry> entries,
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel channel,
            String canonicalJson) {
        var entry = entries.get(profileId);
        if (entry == null
                || !entry.typeId().equals(typeId)
                || entry.channel() != channel
                || !entry.clientFactoryId().equals(typeId)
                || entry.configurationVersion() != 0
                || !entry.retainedCanonicalConfigurationJson().equals(
                        java.util.Optional.of(canonicalJson))) {
            throw new IllegalArgumentException("catalog required default is invalid");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }
}
