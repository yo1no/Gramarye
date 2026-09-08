package com.yo1no.gramarye.magic.api.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Package-private startup definitions for the three mandatory common Profile types. */
final class BuiltInProfileTypes {
    static final ResourceLocation SOUND_TYPE_ID = id("sound");
    static final ResourceLocation PARTICLE_TYPE_ID = id("particle");
    static final ResourceLocation TRAIL_TYPE_ID = id("trail");

    static final ClientFactoryKey<SoundProfileConfiguration> SOUND_FACTORY_KEY =
            new ClientFactoryKey<>(SOUND_TYPE_ID);
    static final ClientFactoryKey<ParticleProfileConfiguration> PARTICLE_FACTORY_KEY =
            new ClientFactoryKey<>(PARTICLE_TYPE_ID);
    static final ClientFactoryKey<TrailProfileConfiguration> TRAIL_FACTORY_KEY =
            new ClientFactoryKey<>(TRAIL_TYPE_ID);

    static final ProfileType<SoundProfileConfiguration> SOUND = new SoundProfileType();
    static final ProfileType<ParticleProfileConfiguration> PARTICLE =
            new ParticleProfileType();
    static final ProfileType<TrailProfileConfiguration> TRAIL = new TrailProfileType();

    private BuiltInProfileTypes() {}

    static void register(DeferredRegister<ProfileType<?>> types) {
        Objects.requireNonNull(types, "types");
        types.register(SOUND_TYPE_ID.getPath(), () -> SOUND);
        types.register(PARTICLE_TYPE_ID.getPath(), () -> PARTICLE);
        types.register(TRAIL_TYPE_ID.getPath(), () -> TRAIL);
    }

    static void validateRegistry(Registry<ProfileType<?>> registry) {
        Objects.requireNonNull(registry, "registry");
        if (registry.size() > 64) {
            throw new IllegalStateException("Profile type registry exceeds 64 entries");
        }

        requireBuiltIn(registry, SOUND_TYPE_ID, SOUND);
        requireBuiltIn(registry, PARTICLE_TYPE_ID, PARTICLE);
        requireBuiltIn(registry, TRAIL_TYPE_ID, TRAIL);

        var typeObjects = Collections.newSetFromMap(
                new IdentityHashMap<ProfileType<?>, Boolean>());
        var factoryIds = new HashSet<ResourceLocation>();
        for (var entry : registry.entrySet()) {
            var typeId = entry.getKey().location();
            var type = Objects.requireNonNull(entry.getValue(), "registered Profile type");
            requireUtf8(typeId, 128, "Profile type ID");
            if (!typeObjects.add(type)) {
                throw new IllegalStateException("Profile type object is registered more than once");
            }

            var version = type.currentConfigurationVersion();
            if (version < 0 || version > 65_535) {
                throw new IllegalStateException(
                        "Profile configuration version must be in [0,65535]");
            }
            Objects.requireNonNull(type.channel(), "Profile channel");
            Objects.requireNonNull(type.configurationCodec(), "Profile configuration codec");
            var factoryKey = Objects.requireNonNull(
                    type.clientFactoryKey(), "Profile client factory key");
            requireUtf8(factoryKey.id(), 128, "Profile client factory ID");
            if (!factoryIds.add(factoryKey.id())) {
                throw new IllegalStateException("Duplicate Profile client factory ID");
            }

            var capabilities = Objects.requireNonNull(
                    type.capabilities(), "Profile type capabilities");
            var adjustable = Objects.requireNonNull(
                    capabilities.adjustableParameters(),
                    "Profile adjustable parameter policy");
            if (adjustable.integerRanges().size() > 16) {
                throw new IllegalStateException(
                        "Profile adjustable parameter policy exceeds 16 entries");
            }
            for (var parameterId : adjustable.integerRanges().keySet()) {
                requireUtf8(parameterId, 32, "Profile adjustable parameter ID");
            }
        }
    }

    private static void requireBuiltIn(
            Registry<ProfileType<?>> registry,
            ResourceLocation id,
            ProfileType<?> expected) {
        var actual = registry.getOptional(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing built-in Profile type: " + id));
        if (actual != expected) {
            throw new IllegalStateException("Built-in Profile type identity mismatch: " + id);
        }
    }

    private static void requireUtf8(ResourceLocation id, int maximum, String name) {
        Objects.requireNonNull(id, name);
        if (id.toString().getBytes(StandardCharsets.UTF_8).length > maximum) {
            throw new IllegalStateException(name + " exceeds " + maximum + " UTF-8 bytes");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    static <C> MapCodec<C> rejectUnknownMembers(
            Set<String> allowedMembers, MapCodec<C> delegate) {
        var allowed = Set.copyOf(Objects.requireNonNull(allowedMembers, "allowedMembers"));
        Objects.requireNonNull(delegate, "delegate");
        return new MapCodec<>() {
            @Override
            public <T> DataResult<C> decode(DynamicOps<T> ops, MapLike<T> input) {
                var iterator = input.entries().iterator();
                while (iterator.hasNext()) {
                    var key = ops.getStringValue(iterator.next().getFirst()).result();
                    if (key.isEmpty() || !allowed.contains(key.orElseThrow())) {
                        return DataResult.error(
                                () -> "Unknown built-in Profile configuration member");
                    }
                }
                return delegate.decode(ops, input);
            }

            @Override
            public <T> RecordBuilder<T> encode(
                    C input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return delegate.encode(input, ops, prefix);
            }

            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return delegate.keys(ops);
            }

            @Override
            public String toString() {
                return "Strict[" + delegate + "]";
            }
        };
    }
}

record SoundProfileConfiguration(
        ResourceLocation sound,
        int volumeMilli,
        int pitchMilli) implements ProfileConfiguration {
    SoundProfileConfiguration {
        BuiltInProfileValidation.requireAssetId(sound, "sound");
        BuiltInProfileValidation.requireRange(volumeMilli, 0, 1_000, "volumeMilli");
        BuiltInProfileValidation.requireRange(pitchMilli, 500, 2_000, "pitchMilli");
    }
}

record ParticleProfileConfiguration(
        ResourceLocation particle,
        int count,
        int speedMilliBlocks,
        int sizeMilliBlocks,
        int lifetimeTicks) implements ProfileConfiguration {
    ParticleProfileConfiguration {
        BuiltInProfileValidation.requireAssetId(particle, "particle");
        BuiltInProfileValidation.requireRange(count, 0, 256, "count");
        BuiltInProfileValidation.requireRange(
                speedMilliBlocks, 0, 16_000, "speedMilliBlocks");
        BuiltInProfileValidation.requireRange(
                sizeMilliBlocks, 1, 16_000, "sizeMilliBlocks");
        BuiltInProfileValidation.requireRange(lifetimeTicks, 1, 120, "lifetimeTicks");
    }
}

record TrailProfileConfiguration(
        ResourceLocation particle,
        int segments,
        int sampleIntervalTicks,
        int sizeMilliBlocks,
        int lifetimeTicks) implements ProfileConfiguration {
    TrailProfileConfiguration {
        BuiltInProfileValidation.requireAssetId(particle, "particle");
        BuiltInProfileValidation.requireRange(segments, 1, 48, "segments");
        BuiltInProfileValidation.requireRange(
                sampleIntervalTicks, 1, 120, "sampleIntervalTicks");
        BuiltInProfileValidation.requireRange(
                sizeMilliBlocks, 1, 16_000, "sizeMilliBlocks");
        BuiltInProfileValidation.requireRange(lifetimeTicks, 1, 120, "lifetimeTicks");
    }
}

final class SoundProfileType implements ProfileType<SoundProfileConfiguration> {
    private static final MapCodec<SoundProfileConfiguration> CODEC =
            BuiltInProfileTypes.rejectUnknownMembers(
                    Set.of("sound", "volume_milli", "pitch_milli"),
                    RecordCodecBuilder.mapCodec(instance -> instance.group(
                                    BuiltInProfileValidation.ASSET_ID_CODEC.fieldOf("sound")
                                            .forGetter(SoundProfileConfiguration::sound),
                                    BuiltInProfileValidation.exactIntegerRange(0, 1_000)
                                            .fieldOf("volume_milli")
                                            .forGetter(SoundProfileConfiguration::volumeMilli),
                                    BuiltInProfileValidation.exactIntegerRange(500, 2_000)
                                            .fieldOf("pitch_milli")
                                            .forGetter(SoundProfileConfiguration::pitchMilli))
                            .apply(instance, SoundProfileConfiguration::new)));
    private static final ProfileTypeCapabilities CAPABILITIES = new ProfileTypeCapabilities(
            false, false, false, false, AppearanceParameterPolicy.none());

    @Override
    public int currentConfigurationVersion() {
        return 0;
    }

    @Override
    public ProfileChannel channel() {
        return ProfileChannel.SOUND;
    }

    @Override
    public MapCodec<SoundProfileConfiguration> configurationCodec() {
        return CODEC;
    }

    @Override
    public ClientFactoryKey<SoundProfileConfiguration> clientFactoryKey() {
        return BuiltInProfileTypes.SOUND_FACTORY_KEY;
    }

    @Override
    public ProfileTypeCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProfileCost estimateCost(SoundProfileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new ProfileCost(0, 1, 0, 0, 1);
    }

    @Override
    public ValidationResult validate(
            SoundProfileConfiguration configuration, ValidationContext context) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(context, "context");
        return ValidationResult.valid();
    }
}

final class ParticleProfileType implements ProfileType<ParticleProfileConfiguration> {
    private static final MapCodec<ParticleProfileConfiguration> CODEC =
            BuiltInProfileTypes.rejectUnknownMembers(
                    Set.of(
                            "particle",
                            "count",
                            "speed_milli_blocks",
                            "size_milli_blocks",
                            "lifetime_ticks"),
                    RecordCodecBuilder.mapCodec(instance -> instance.group(
                                    BuiltInProfileValidation.ASSET_ID_CODEC.fieldOf("particle")
                                            .forGetter(ParticleProfileConfiguration::particle),
                                    BuiltInProfileValidation.exactIntegerRange(0, 256)
                                            .fieldOf("count")
                                            .forGetter(ParticleProfileConfiguration::count),
                                    BuiltInProfileValidation.exactIntegerRange(0, 16_000)
                                            .fieldOf("speed_milli_blocks")
                                            .forGetter(
                                                    ParticleProfileConfiguration::speedMilliBlocks),
                                    BuiltInProfileValidation.exactIntegerRange(1, 16_000)
                                            .fieldOf("size_milli_blocks")
                                            .forGetter(
                                                    ParticleProfileConfiguration::sizeMilliBlocks),
                                    BuiltInProfileValidation.exactIntegerRange(1, 120)
                                            .fieldOf("lifetime_ticks")
                                            .forGetter(ParticleProfileConfiguration::lifetimeTicks))
                            .apply(instance, ParticleProfileConfiguration::new)));
    private static final ProfileTypeCapabilities CAPABILITIES = new ProfileTypeCapabilities(
            true, false, true, false, AppearanceParameterPolicy.none());

    @Override
    public int currentConfigurationVersion() {
        return 0;
    }

    @Override
    public ProfileChannel channel() {
        return ProfileChannel.PARTICLE;
    }

    @Override
    public MapCodec<ParticleProfileConfiguration> configurationCodec() {
        return CODEC;
    }

    @Override
    public ClientFactoryKey<ParticleProfileConfiguration> clientFactoryKey() {
        return BuiltInProfileTypes.PARTICLE_FACTORY_KEY;
    }

    @Override
    public ProfileTypeCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProfileCost estimateCost(ParticleProfileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new ProfileCost(
                configuration.count(), 0, 0, 0, configuration.lifetimeTicks());
    }

    @Override
    public ValidationResult validate(
            ParticleProfileConfiguration configuration, ValidationContext context) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(context, "context");
        return ValidationResult.valid();
    }
}

final class TrailProfileType implements ProfileType<TrailProfileConfiguration> {
    private static final MapCodec<TrailProfileConfiguration> CODEC =
            BuiltInProfileTypes.rejectUnknownMembers(
                    Set.of(
                            "particle",
                            "segments",
                            "sample_interval_ticks",
                            "size_milli_blocks",
                            "lifetime_ticks"),
                    RecordCodecBuilder.mapCodec(instance -> instance.group(
                                    BuiltInProfileValidation.ASSET_ID_CODEC.fieldOf("particle")
                                            .forGetter(TrailProfileConfiguration::particle),
                                    BuiltInProfileValidation.exactIntegerRange(1, 48)
                                            .fieldOf("segments")
                                            .forGetter(TrailProfileConfiguration::segments),
                                    BuiltInProfileValidation.exactIntegerRange(1, 120)
                                            .fieldOf("sample_interval_ticks")
                                            .forGetter(
                                                    TrailProfileConfiguration::sampleIntervalTicks),
                                    BuiltInProfileValidation.exactIntegerRange(1, 16_000)
                                            .fieldOf("size_milli_blocks")
                                            .forGetter(TrailProfileConfiguration::sizeMilliBlocks),
                                    BuiltInProfileValidation.exactIntegerRange(1, 120)
                                            .fieldOf("lifetime_ticks")
                                            .forGetter(TrailProfileConfiguration::lifetimeTicks))
                            .apply(instance, TrailProfileConfiguration::new)));
    private static final ProfileTypeCapabilities CAPABILITIES = new ProfileTypeCapabilities(
            true, true, true, true, AppearanceParameterPolicy.none());

    @Override
    public int currentConfigurationVersion() {
        return 0;
    }

    @Override
    public ProfileChannel channel() {
        return ProfileChannel.TRAIL;
    }

    @Override
    public MapCodec<TrailProfileConfiguration> configurationCodec() {
        return CODEC;
    }

    @Override
    public ClientFactoryKey<TrailProfileConfiguration> clientFactoryKey() {
        return BuiltInProfileTypes.TRAIL_FACTORY_KEY;
    }

    @Override
    public ProfileTypeCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public ProfileCost estimateCost(TrailProfileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new ProfileCost(
                0, 0, 1, configuration.segments(), configuration.lifetimeTicks());
    }

    @Override
    public ValidationResult validate(
            TrailProfileConfiguration configuration, ValidationContext context) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(context, "context");
        return ValidationResult.valid();
    }
}

final class BuiltInProfileValidation {
    private static final int MAX_ASSET_ID_UTF8_BYTES = 128;
    static final Codec<ResourceLocation> ASSET_ID_CODEC =
            ResourceLocation.CODEC.validate(BuiltInProfileValidation::validateAssetId);

    private BuiltInProfileValidation() {}

    static Codec<Integer> exactIntegerRange(int minimum, int maximum) {
        if (minimum > maximum) {
            throw new IllegalArgumentException("minimum cannot exceed maximum");
        }
        return new PrimitiveCodec<>() {
            @Override
            public <T> DataResult<Integer> read(DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input).flatMap(number -> {
                    final int exactValue;
                    try {
                        exactValue = new BigDecimal(number.toString()).intValueExact();
                    } catch (ArithmeticException | NumberFormatException exception) {
                        return rangeError();
                    }
                    if (exactValue < minimum || exactValue > maximum) {
                        return rangeError();
                    }
                    return DataResult.success(exactValue);
                });
            }

            @Override
            public <T> T write(DynamicOps<T> ops, Integer value) {
                return ops.createInt(value);
            }

            private DataResult<Integer> rangeError() {
                return DataResult.error(
                        () -> "Expected exact integer in [" + minimum + "," + maximum + "]");
            }

            @Override
            public String toString() {
                return "ExactInt[" + minimum + "," + maximum + "]";
            }
        };
    }

    static void requireAssetId(ResourceLocation id, String name) {
        Objects.requireNonNull(id, name);
        if (assetIdUtf8Bytes(id) > MAX_ASSET_ID_UTF8_BYTES) {
            throw new IllegalArgumentException(name + " exceeds 128 UTF-8 bytes");
        }
    }

    static int trailSamplingOpportunities(
            TrailProfileConfiguration configuration, int effectiveIntervalTicks) {
        Objects.requireNonNull(configuration, "configuration");
        requireRange(effectiveIntervalTicks, 1, 120, "effectiveIntervalTicks");
        if (effectiveIntervalTicks < configuration.sampleIntervalTicks()) {
            throw new IllegalArgumentException(
                    "effectiveIntervalTicks cannot reduce the configured interval");
        }
        return 1 + (configuration.lifetimeTicks() - 1) / effectiveIntervalTicks;
    }

    static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be in [" + minimum + "," + maximum + "]");
        }
    }

    private static DataResult<ResourceLocation> validateAssetId(ResourceLocation id) {
        if (assetIdUtf8Bytes(id) > MAX_ASSET_ID_UTF8_BYTES) {
            return DataResult.error(
                    () -> "built-in Profile asset ID exceeds 128 UTF-8 bytes");
        }
        return DataResult.success(id);
    }

    private static int assetIdUtf8Bytes(ResourceLocation id) {
        return Objects.requireNonNull(id, "asset ID")
                .toString()
                .getBytes(StandardCharsets.UTF_8)
                .length;
    }
}
