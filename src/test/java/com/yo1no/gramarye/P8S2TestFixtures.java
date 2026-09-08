package com.yo1no.gramarye;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

/** Typed in-memory registry and source fixtures for the package-private P8-S2 core. */
final class P8S2TestFixtures {
    static final ResourceLocation SOUND_TYPE_ID = id("sound");
    static final ResourceLocation PARTICLE_TYPE_ID = id("particle");
    static final ResourceLocation TRAIL_TYPE_ID = id("trail");
    static final ResourceLocation TREE_TYPE_ID = id("test_tree");
    static final ResourceLocation STRING_TYPE_ID = id("test_string");
    static final ResourceLocation ALTERNATE_PARTICLE_TYPE_ID = id("test_particle");
    static final ResourceLocation OVER_COST_TYPE_ID = id("test_over_cost");
    static final ResourceLocation UNEXPECTED_CODEC_TYPE_ID = id("test_unexpected_codec");
    static final ResourceLocation UNEXPECTED_VALIDATION_TYPE_ID =
            id("test_unexpected_validation");
    static final ResourceLocation UNEXPECTED_ESTIMATE_TYPE_ID =
            id("test_unexpected_estimate");
    static final ResourceLocation SNAPSHOT_SOUND_TYPE_ID = id("snapshot_sound");
    static final ResourceLocation SNAPSHOT_PARTICLE_TYPE_ID = id("snapshot_particle");
    static final ResourceLocation SNAPSHOT_TRAIL_TYPE_ID = id("snapshot_trail");

    static final ResourceLocation DEFAULT_SOUND_ID = id("default_sound");
    static final ResourceLocation DEFAULT_PARTICLE_ID = id("default_particle");
    static final ResourceLocation DEFAULT_TRAIL_ID = id("default_trail");

    private static final MapCodec<SoundConfiguration> SOUND_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("sound")
                                    .forGetter(SoundConfiguration::sound),
                            Codec.intRange(0, 1_000).fieldOf("volume_milli")
                                    .forGetter(SoundConfiguration::volumeMilli),
                            Codec.intRange(500, 2_000).fieldOf("pitch_milli")
                                    .forGetter(SoundConfiguration::pitchMilli))
                    .apply(instance, SoundConfiguration::new));
    private static final MapCodec<ParticleConfiguration> PARTICLE_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("particle")
                                    .forGetter(ParticleConfiguration::particle),
                            Codec.intRange(0, 256).fieldOf("count")
                                    .forGetter(ParticleConfiguration::count),
                            Codec.intRange(0, 16_000).fieldOf("speed_milli_blocks")
                                    .forGetter(ParticleConfiguration::speedMilliBlocks),
                            Codec.intRange(1, 16_000).fieldOf("size_milli_blocks")
                                    .forGetter(ParticleConfiguration::sizeMilliBlocks),
                            Codec.intRange(1, 120).fieldOf("lifetime_ticks")
                                    .forGetter(ParticleConfiguration::lifetimeTicks))
                    .apply(instance, ParticleConfiguration::new));
    private static final MapCodec<TrailConfiguration> TRAIL_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            ResourceLocation.CODEC.fieldOf("particle")
                                    .forGetter(TrailConfiguration::particle),
                            Codec.intRange(1, 48).fieldOf("segments")
                                    .forGetter(TrailConfiguration::segments),
                            Codec.intRange(1, 120).fieldOf("sample_interval_ticks")
                                    .forGetter(TrailConfiguration::sampleIntervalTicks),
                            Codec.intRange(1, 16_000).fieldOf("size_milli_blocks")
                                    .forGetter(TrailConfiguration::sizeMilliBlocks),
                            Codec.intRange(1, 120).fieldOf("lifetime_ticks")
                                    .forGetter(TrailConfiguration::lifetimeTicks))
                    .apply(instance, TrailConfiguration::new));
    private static final MapCodec<TreeConfiguration> TREE_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            Codec.PASSTHROUGH.fieldOf("payload")
                                    .forGetter(TreeConfiguration::payload))
                    .apply(instance, TreeConfiguration::new));
    private static final MapCodec<StringConfiguration> STRING_CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                            Codec.STRING.fieldOf("value")
                                    .forGetter(StringConfiguration::value))
                    .apply(instance, StringConfiguration::new));

    private static final ProfileTypeCapabilities SOUND_CAPABILITIES = capabilities(
            false, false, false, false, AppearanceParameterPolicy.none());
    private static final ProfileTypeCapabilities PARTICLE_CAPABILITIES = capabilities(
            true, false, true, false, AppearanceParameterPolicy.none());
    private static final ProfileTypeCapabilities TRAIL_CAPABILITIES = capabilities(
            true, true, true, true, AppearanceParameterPolicy.none());

    static final TestProfileType<SoundConfiguration> SOUND_TYPE = new TestProfileType<>(
            ProfileChannel.SOUND,
            SOUND_CODEC,
            SOUND_TYPE_ID,
            ignored -> new ProfileCost(0, 1, 0, 0, 1),
            SOUND_CAPABILITIES);
    static final TestProfileType<ParticleConfiguration> PARTICLE_TYPE =
            new TestProfileType<>(
                    ProfileChannel.PARTICLE,
                    PARTICLE_CODEC,
                    PARTICLE_TYPE_ID,
                    configuration -> new ProfileCost(
                            configuration.count(),
                            0,
                            0,
                            0,
                            configuration.lifetimeTicks()),
                    PARTICLE_CAPABILITIES);
    static final TestProfileType<TrailConfiguration> TRAIL_TYPE = new TestProfileType<>(
            ProfileChannel.TRAIL,
            TRAIL_CODEC,
            TRAIL_TYPE_ID,
            configuration -> new ProfileCost(
                    0,
                    0,
                    1,
                    configuration.segments(),
                    configuration.lifetimeTicks()),
            TRAIL_CAPABILITIES);
    static final TestProfileType<TreeConfiguration> TREE_TYPE = new TestProfileType<>(
            ProfileChannel.SOUND,
            TREE_CODEC,
            TREE_TYPE_ID,
            ignored -> new ProfileCost(0, 1, 0, 0, 1));
    static final TestProfileType<StringConfiguration> STRING_TYPE = new TestProfileType<>(
            ProfileChannel.SOUND,
            STRING_CODEC,
            STRING_TYPE_ID,
            ignored -> new ProfileCost(0, 1, 0, 0, 1));
    static final TestProfileType<SoundConfiguration> ALTERNATE_PARTICLE_TYPE =
            new TestProfileType<>(
                    ProfileChannel.PARTICLE,
                    SOUND_CODEC,
                    ALTERNATE_PARTICLE_TYPE_ID,
                    ignored -> new ProfileCost(1, 0, 0, 0, 1));
    static final TestProfileType<SoundConfiguration> OVER_COST_TYPE =
            new TestProfileType<>(
                    ProfileChannel.SOUND,
                    SOUND_CODEC,
                    OVER_COST_TYPE_ID,
                    ignored -> new ProfileCost(257, 0, 0, 0, 1));
    static final ProfileType<SoundConfiguration> UNEXPECTED_CODEC_TYPE =
            new UnexpectedFailureProfileType(
                    UNEXPECTED_CODEC_TYPE_ID, UnexpectedFailurePoint.CODEC);
    static final ProfileType<SoundConfiguration> UNEXPECTED_VALIDATION_TYPE =
            new UnexpectedFailureProfileType(
                    UNEXPECTED_VALIDATION_TYPE_ID, UnexpectedFailurePoint.VALIDATION);
    static final ProfileType<SoundConfiguration> UNEXPECTED_ESTIMATE_TYPE =
            new UnexpectedFailureProfileType(
                    UNEXPECTED_ESTIMATE_TYPE_ID, UnexpectedFailurePoint.ESTIMATE);

    private P8S2TestFixtures() {}

    static Registry<ProfileType<?>> registry() {
        ResourceKey<Registry<ProfileType<?>>> key =
                ResourceKey.createRegistryKey(id("p8_s2_test_profile_types"));
        Registry<ProfileType<?>> registry = new RegistryBuilder<ProfileType<?>>(key)
                .disableRegistrationCheck()
                .create();
        Registry.register(registry, SOUND_TYPE_ID, SOUND_TYPE);
        Registry.register(registry, PARTICLE_TYPE_ID, PARTICLE_TYPE);
        Registry.register(registry, TRAIL_TYPE_ID, TRAIL_TYPE);
        Registry.register(registry, TREE_TYPE_ID, TREE_TYPE);
        Registry.register(registry, STRING_TYPE_ID, STRING_TYPE);
        Registry.register(
                registry, ALTERNATE_PARTICLE_TYPE_ID, ALTERNATE_PARTICLE_TYPE);
        Registry.register(registry, OVER_COST_TYPE_ID, OVER_COST_TYPE);
        Registry.register(registry, UNEXPECTED_CODEC_TYPE_ID, UNEXPECTED_CODEC_TYPE);
        Registry.register(
                registry, UNEXPECTED_VALIDATION_TYPE_ID, UNEXPECTED_VALIDATION_TYPE);
        Registry.register(registry, UNEXPECTED_ESTIMATE_TYPE_ID, UNEXPECTED_ESTIMATE_TYPE);
        return registry;
    }

    static Registry<ProfileType<?>> registryWithoutRequiredType(ResourceLocation missingTypeId) {
        Objects.requireNonNull(missingTypeId, "missingTypeId");
        if (!missingTypeId.equals(SOUND_TYPE_ID)
                && !missingTypeId.equals(PARTICLE_TYPE_ID)
                && !missingTypeId.equals(TRAIL_TYPE_ID)) {
            throw new IllegalArgumentException("missingTypeId must identify a required type");
        }
        ResourceKey<Registry<ProfileType<?>>> key = ResourceKey.createRegistryKey(
                id("p8_s2_missing_" + missingTypeId.getPath() + "_profile_types"));
        Registry<ProfileType<?>> registry = new RegistryBuilder<ProfileType<?>>(key)
                .disableRegistrationCheck()
                .create();
        if (!missingTypeId.equals(SOUND_TYPE_ID)) {
            Registry.register(registry, SOUND_TYPE_ID, SOUND_TYPE);
        }
        if (!missingTypeId.equals(PARTICLE_TYPE_ID)) {
            Registry.register(registry, PARTICLE_TYPE_ID, PARTICLE_TYPE);
        }
        if (!missingTypeId.equals(TRAIL_TYPE_ID)) {
            Registry.register(registry, TRAIL_TYPE_ID, TRAIL_TYPE);
        }
        return registry;
    }

    static Registry<ProfileType<?>> snapshotRegistry(
            boolean secondGeneration, ResourceLocation parameterId) {
        Objects.requireNonNull(parameterId, "parameterId");
        ResourceKey<Registry<ProfileType<?>>> key = ResourceKey.createRegistryKey(
                id(secondGeneration
                        ? "p8_s2_snapshot_b_profile_types"
                        : "p8_s2_snapshot_a_profile_types"));
        Registry<ProfileType<?>> registry = new RegistryBuilder<ProfileType<?>>(key)
                .disableRegistrationCheck()
                .create();

        var none = AppearanceParameterPolicy.none();
        var soundCapabilities = new ProfileTypeCapabilities(
                !secondGeneration, false, false, false, none);
        var particleCapabilities = new ProfileTypeCapabilities(
                !secondGeneration,
                secondGeneration,
                !secondGeneration,
                false,
                new AppearanceParameterPolicy(Map.of(
                        parameterId,
                        new AppearanceParameterPolicy.IntRange(
                                0, secondGeneration ? 4 : 10))));
        var trailCapabilities = new ProfileTypeCapabilities(
                !secondGeneration,
                !secondGeneration,
                !secondGeneration,
                !secondGeneration,
                none);

        Registry.register(
                registry,
                SOUND_TYPE_ID,
                new TestProfileType<>(
                        ProfileChannel.SOUND,
                        SOUND_CODEC,
                        SOUND_TYPE_ID,
                        ignored -> new ProfileCost(
                                0, secondGeneration ? 2 : 1, 0, 0, 1),
                        SOUND_CAPABILITIES));
        Registry.register(
                registry,
                PARTICLE_TYPE_ID,
                new TestProfileType<>(
                        ProfileChannel.PARTICLE,
                        PARTICLE_CODEC,
                        PARTICLE_TYPE_ID,
                        configuration -> new ProfileCost(
                                configuration.count() + (secondGeneration ? 1 : 0),
                                0,
                                0,
                                0,
                                configuration.lifetimeTicks()),
                        PARTICLE_CAPABILITIES));
        Registry.register(
                registry,
                TRAIL_TYPE_ID,
                new TestProfileType<>(
                        ProfileChannel.TRAIL,
                        TRAIL_CODEC,
                        TRAIL_TYPE_ID,
                        configuration -> new ProfileCost(
                                0,
                                0,
                                1,
                                configuration.segments() + (secondGeneration ? 1 : 0),
                                configuration.lifetimeTicks()),
                        TRAIL_CAPABILITIES));
        Registry.register(
                registry,
                SNAPSHOT_SOUND_TYPE_ID,
                new TestProfileType<>(
                        ProfileChannel.SOUND,
                        SOUND_CODEC,
                        SNAPSHOT_SOUND_TYPE_ID,
                        ignored -> new ProfileCost(
                                0, secondGeneration ? 4 : 3, 0, 0, 1),
                        soundCapabilities));
        Registry.register(
                registry,
                SNAPSHOT_PARTICLE_TYPE_ID,
                new TestProfileType<>(
                        ProfileChannel.PARTICLE,
                        PARTICLE_CODEC,
                        SNAPSHOT_PARTICLE_TYPE_ID,
                        configuration -> new ProfileCost(
                                configuration.count() + (secondGeneration ? 1 : 0),
                                0,
                                0,
                                0,
                                configuration.lifetimeTicks()),
                        particleCapabilities));
        Registry.register(
                registry,
                SNAPSHOT_TRAIL_TYPE_ID,
                new TestProfileType<>(
                        ProfileChannel.TRAIL,
                        TRAIL_CODEC,
                        SNAPSHOT_TRAIL_TYPE_ID,
                        configuration -> new ProfileCost(
                                0,
                                0,
                                1,
                                configuration.segments() + (secondGeneration ? 1 : 0),
                                configuration.lifetimeTicks()),
                        trailCapabilities));
        return registry;
    }

    static byte[] profile(ResourceLocation typeId, String configurationJson) {
        return profile(typeId, 0, configurationJson);
    }

    static byte[] profile(
            ResourceLocation typeId, int configurationVersion, String configurationJson) {
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(configurationJson, "configurationJson");
        return ("{\"format\":0,\"type\":\""
                        + typeId
                        + "\",\"type_version\":"
                        + configurationVersion
                        + ",\"configuration\":"
                        + configurationJson
                        + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    static String soundConfiguration(int volumeMilli) {
        return "{\"sound\":\"minecraft:entity.experience_orb.pickup\","
                + "\"volume_milli\":"
                + volumeMilli
                + ",\"pitch_milli\":1000}";
    }

    static String particleConfiguration(int count) {
        return "{\"particle\":\"minecraft:enchant\",\"count\":"
                + count
                + ",\"speed_milli_blocks\":50,\"size_milli_blocks\":250,"
                + "\"lifetime_ticks\":20}";
    }

    static String trailConfiguration(int segments) {
        return "{\"particle\":\"minecraft:enchant\",\"segments\":"
                + segments
                + ",\"sample_interval_ticks\":2,\"size_milli_blocks\":200,"
                + "\"lifetime_ticks\":16}";
    }

    static byte[] paddedTo(byte[] source, int targetBytes) {
        if (source.length > targetBytes) {
            throw new IllegalArgumentException("source already exceeds targetBytes");
        }
        var result = new byte[targetBytes];
        System.arraycopy(source, 0, result, 0, source.length);
        java.util.Arrays.fill(result, source.length, result.length, (byte) ' ');
        return result;
    }

    static String arrayPayload(int primitiveCount) {
        var values = new ArrayList<String>(primitiveCount);
        for (var index = 0; index < primitiveCount; index++) {
            values.add("0");
        }
        return "{\"payload\":[" + String.join(",", values) + "]}";
    }

    static String nestedArrayPayload(int arrayDepth) {
        return "{\"payload\":" + "[".repeat(arrayDepth) + "0" + "]".repeat(arrayDepth) + "}";
    }

    static String stringArrayConfigurationOfBytes(int exactUtf8Bytes) {
        var prefix = "{\"payload\":[";
        var suffix = "]}";
        if (exactUtf8Bytes < prefix.length() + suffix.length() + 2) {
            throw new IllegalArgumentException("requested configuration is too short");
        }
        var result = new StringBuilder(prefix);
        var remaining = exactUtf8Bytes - prefix.length() - suffix.length();
        var first = true;
        while (remaining > 0) {
            var separatorBytes = first ? 0 : 1;
            var maximumValueBytes = Math.min(128, remaining - separatorBytes - 2);
            if (maximumValueBytes < 0) {
                throw new IllegalArgumentException("exact byte length cannot be represented");
            }
            if (!first) {
                result.append(',');
            }
            result.append('\"').append("a".repeat(maximumValueBytes)).append('\"');
            remaining -= separatorBytes + maximumValueBytes + 2;
            first = false;
        }
        result.append(suffix);
        if (result.toString().getBytes(StandardCharsets.UTF_8).length != exactUtf8Bytes) {
            throw new AssertionError("configuration byte fixture is not exact");
        }
        return result.toString();
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static ProfileTypeCapabilities capabilities(
            boolean primary,
            boolean secondary,
            boolean direction,
            boolean trail,
            AppearanceParameterPolicy parameters) {
        return new ProfileTypeCapabilities(
                primary, secondary, direction, trail, parameters);
    }

    record SoundConfiguration(
            ResourceLocation sound,
            int volumeMilli,
            int pitchMilli) implements ProfileConfiguration {}

    record ParticleConfiguration(
            ResourceLocation particle,
            int count,
            int speedMilliBlocks,
            int sizeMilliBlocks,
            int lifetimeTicks) implements ProfileConfiguration {}

    record TrailConfiguration(
            ResourceLocation particle,
            int segments,
            int sampleIntervalTicks,
            int sizeMilliBlocks,
            int lifetimeTicks) implements ProfileConfiguration {}

    record TreeConfiguration(Dynamic<?> payload) implements ProfileConfiguration {
        TreeConfiguration {
            Objects.requireNonNull(payload, "payload");
        }
    }

    record StringConfiguration(String value) implements ProfileConfiguration {
        StringConfiguration {
            Objects.requireNonNull(value, "value");
        }
    }

    static final class TestProfileType<C extends ProfileConfiguration>
            implements ProfileType<C> {
        private static final ProfileTypeCapabilities CAPABILITIES =
                new ProfileTypeCapabilities(
                        false, false, false, false, AppearanceParameterPolicy.none());

        private final ProfileChannel channel;
        private final MapCodec<C> codec;
        private final ClientFactoryKey<C> factoryKey;
        private final Function<C, ProfileCost> cost;
        private final ProfileTypeCapabilities capabilities;

        private TestProfileType(
                ProfileChannel channel,
                MapCodec<C> codec,
                ResourceLocation factoryId,
                Function<C, ProfileCost> cost) {
            this(channel, codec, factoryId, cost, CAPABILITIES);
        }

        private TestProfileType(
                ProfileChannel channel,
                MapCodec<C> codec,
                ResourceLocation factoryId,
                Function<C, ProfileCost> cost,
                ProfileTypeCapabilities capabilities) {
            this.channel = Objects.requireNonNull(channel, "channel");
            this.codec = Objects.requireNonNull(codec, "codec");
            this.factoryKey = new ClientFactoryKey<>(factoryId);
            this.cost = Objects.requireNonNull(cost, "cost");
            this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        }

        @Override
        public int currentConfigurationVersion() {
            return 0;
        }

        @Override
        public ProfileChannel channel() {
            return channel;
        }

        @Override
        public MapCodec<C> configurationCodec() {
            return codec;
        }

        @Override
        public ClientFactoryKey<C> clientFactoryKey() {
            return factoryKey;
        }

        @Override
        public ProfileTypeCapabilities capabilities() {
            return capabilities;
        }

        @Override
        public ProfileCost estimateCost(C configuration) {
            return cost.apply(Objects.requireNonNull(configuration, "configuration"));
        }

        @Override
        public ValidationResult validate(C configuration, ValidationContext context) {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(context, "context");
            return ValidationResult.valid();
        }
    }

    private enum UnexpectedFailurePoint {
        CODEC,
        VALIDATION,
        ESTIMATE
    }

    private static final class UnexpectedFailureProfileType
            implements ProfileType<SoundConfiguration> {
        private final ClientFactoryKey<SoundConfiguration> factoryKey;
        private final UnexpectedFailurePoint failurePoint;

        private UnexpectedFailureProfileType(
                ResourceLocation factoryId, UnexpectedFailurePoint failurePoint) {
            this.factoryKey = new ClientFactoryKey<>(factoryId);
            this.failurePoint = Objects.requireNonNull(failurePoint, "failurePoint");
        }

        @Override
        public int currentConfigurationVersion() {
            return 0;
        }

        @Override
        public ProfileChannel channel() {
            return ProfileChannel.SOUND;
        }

        @Override
        public MapCodec<SoundConfiguration> configurationCodec() {
            failAt(UnexpectedFailurePoint.CODEC);
            return SOUND_CODEC;
        }

        @Override
        public ClientFactoryKey<SoundConfiguration> clientFactoryKey() {
            return factoryKey;
        }

        @Override
        public ProfileTypeCapabilities capabilities() {
            return SOUND_CAPABILITIES;
        }

        @Override
        public ProfileCost estimateCost(SoundConfiguration configuration) {
            Objects.requireNonNull(configuration, "configuration");
            failAt(UnexpectedFailurePoint.ESTIMATE);
            return new ProfileCost(0, 1, 0, 0, 1);
        }

        @Override
        public ValidationResult validate(
                SoundConfiguration configuration, ValidationContext context) {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(context, "context");
            failAt(UnexpectedFailurePoint.VALIDATION);
            return ValidationResult.valid();
        }

        private void failAt(UnexpectedFailurePoint point) {
            if (failurePoint == point) {
                throw new IllegalStateException(
                        "unexpected Profile " + point.name().toLowerCase() + " failure");
            }
        }
    }
}
