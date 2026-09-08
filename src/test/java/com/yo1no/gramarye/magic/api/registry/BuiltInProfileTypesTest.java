package com.yo1no.gramarye.magic.api.registry;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.junit.jupiter.api.Test;

class BuiltInProfileTypesTest {
    private static final ResourceLocation DEFAULT_SOUND_ASSET =
            ResourceLocation.withDefaultNamespace("entity.experience_orb.pickup");
    private static final ResourceLocation DEFAULT_PARTICLE_ASSET =
            ResourceLocation.withDefaultNamespace("enchant");
    private static final ValidationContext VALIDATION_CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    @Test
    void defaultConfigurationsHaveExactStrictCodecShapes() {
        var sound = new SoundProfileConfiguration(DEFAULT_SOUND_ASSET, 600, 1_000);
        var particle = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 8, 50, 250, 20);
        var trail = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 8, 2, 200, 16);

        assertAll(
                () -> assertCodecRoundTrip(
                        BuiltInProfileTypes.SOUND,
                        sound,
                        soundJson("minecraft:entity.experience_orb.pickup", 600, 1_000)),
                () -> assertCodecRoundTrip(
                        BuiltInProfileTypes.PARTICLE,
                        particle,
                        particleJson("minecraft:enchant", 8, 50, 250, 20)),
                () -> assertCodecRoundTrip(
                        BuiltInProfileTypes.TRAIL,
                        trail,
                        trailJson("minecraft:enchant", 8, 2, 200, 16)));

        var soundWithUnknownMember = soundJson(
                "minecraft:entity.experience_orb.pickup", 600, 1_000);
        soundWithUnknownMember.addProperty("unknown", true);
        var particleWithUnknownMember = particleJson(
                "minecraft:enchant", 8, 50, 250, 20);
        particleWithUnknownMember.addProperty("unknown", true);
        var trailWithUnknownMember = trailJson("minecraft:enchant", 8, 2, 200, 16);
        trailWithUnknownMember.addProperty("unknown", true);

        assertAll(
                () -> assertCodecRejects(BuiltInProfileTypes.SOUND, soundWithUnknownMember),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE, particleWithUnknownMember),
                () -> assertCodecRejects(BuiltInProfileTypes.TRAIL, trailWithUnknownMember));
    }

    @Test
    void everyLegalDomainBoundaryValidatesWithoutPolicyNarrowing() {
        var soundMinimum = new SoundProfileConfiguration(DEFAULT_SOUND_ASSET, 0, 500);
        var soundMaximum = new SoundProfileConfiguration(DEFAULT_SOUND_ASSET, 1_000, 2_000);
        var particleMinimum = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 0, 0, 1, 1);
        var particleMaximum = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 256, 16_000, 16_000, 120);
        var trailMinimum = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 1, 1, 1, 1);
        var trailMaximum = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 48, 120, 16_000, 120);

        assertAll(
                () -> assertTrue(BuiltInProfileTypes.SOUND
                        .validate(soundMinimum, VALIDATION_CONTEXT)
                        .isValid()),
                () -> assertTrue(BuiltInProfileTypes.SOUND
                        .validate(soundMaximum, VALIDATION_CONTEXT)
                        .isValid()),
                () -> assertTrue(BuiltInProfileTypes.PARTICLE
                        .validate(particleMinimum, VALIDATION_CONTEXT)
                        .isValid()),
                () -> assertTrue(BuiltInProfileTypes.PARTICLE
                        .validate(particleMaximum, VALIDATION_CONTEXT)
                        .isValid()),
                () -> assertTrue(BuiltInProfileTypes.TRAIL
                        .validate(trailMinimum, VALIDATION_CONTEXT)
                        .isValid()),
                () -> assertTrue(BuiltInProfileTypes.TRAIL
                        .validate(trailMaximum, VALIDATION_CONTEXT)
                        .isValid()),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> BuiltInProfileTypes.SOUND.validate(null, VALIDATION_CONTEXT)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> BuiltInProfileTypes.PARTICLE.validate(particleMinimum, null)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> BuiltInProfileTypes.TRAIL.estimateCost(null)));
    }

    @Test
    void constructorsAndCodecsRejectEveryAdjacentScalarDomain() {
        var asset = DEFAULT_PARTICLE_ASSET;
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SoundProfileConfiguration(asset, -1, 500)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SoundProfileConfiguration(asset, 1_001, 500)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SoundProfileConfiguration(asset, 0, 499)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SoundProfileConfiguration(asset, 0, 2_001)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, -1, 0, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 257, 0, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 0, -1, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 0, 16_001, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 0, 0, 0, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 0, 0, 16_001, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 0, 0, 1, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ParticleProfileConfiguration(asset, 0, 0, 1, 121)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 0, 1, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 49, 1, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 1, 0, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 1, 121, 1, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 1, 1, 0, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 1, 1, 16_001, 1)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 1, 1, 1, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TrailProfileConfiguration(asset, 1, 1, 1, 121)));

        assertAll(
                () -> assertCodecRejects(
                        BuiltInProfileTypes.SOUND,
                        soundJson("minecraft:enchant", -1, 500)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.SOUND,
                        soundJson("minecraft:enchant", 1_001, 500)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.SOUND,
                        soundJson("minecraft:enchant", 0, 499)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.SOUND,
                        soundJson("minecraft:enchant", 0, 2_001)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", -1, 0, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 257, 0, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 0, -1, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 0, 16_001, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 0, 0, 0, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 0, 0, 16_001, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 0, 0, 1, 0)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson("minecraft:enchant", 0, 0, 1, 121)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 0, 1, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 49, 1, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 1, 0, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 1, 121, 1, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 1, 1, 0, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 1, 1, 16_001, 1)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 1, 1, 1, 121)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson("minecraft:enchant", 1, 1, 1, 0)));
    }

    @Test
    void codecsRejectFractionalAndWrappingOverflowForEveryScalar() {
        var sound = soundJson("minecraft:entity.experience_orb.pickup", 600, 1_000);
        var particle = particleJson("minecraft:enchant", 8, 50, 250, 20);
        var trail = trailJson("minecraft:enchant", 8, 2, 200, 16);

        assertAll(
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.SOUND, sound, "volume_milli", 600),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.SOUND, sound, "pitch_milli", 1_000),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.PARTICLE, particle, "count", 8),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.PARTICLE, particle, "speed_milli_blocks", 50),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.PARTICLE, particle, "size_milli_blocks", 250),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.PARTICLE, particle, "lifetime_ticks", 20),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.TRAIL, trail, "segments", 8),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.TRAIL, trail, "sample_interval_ticks", 2),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.TRAIL, trail, "size_milli_blocks", 200),
                () -> assertNonExactIntegerRejected(
                        BuiltInProfileTypes.TRAIL, trail, "lifetime_ticks", 16));
    }

    @Test
    void codecsRejectEveryMissingRequiredMember() {
        assertEveryMissingMemberRejected(
                BuiltInProfileTypes.SOUND,
                soundJson("minecraft:entity.experience_orb.pickup", 600, 1_000),
                "sound",
                "volume_milli",
                "pitch_milli");
        assertEveryMissingMemberRejected(
                BuiltInProfileTypes.PARTICLE,
                particleJson("minecraft:enchant", 8, 50, 250, 20),
                "particle",
                "count",
                "speed_milli_blocks",
                "size_milli_blocks",
                "lifetime_ticks");
        assertEveryMissingMemberRejected(
                BuiltInProfileTypes.TRAIL,
                trailJson("minecraft:enchant", 8, 2, 200, 16),
                "particle",
                "segments",
                "sample_interval_ticks",
                "size_milli_blocks",
                "lifetime_ticks");
    }

    @Test
    void exactAssetBoundaryIsAcceptedAndOneOverIsAnOrdinaryCodecError() {
        var exact = ResourceLocation.fromNamespaceAndPath("g", "a".repeat(126));
        var oneOver = ResourceLocation.fromNamespaceAndPath("g", "a".repeat(127));
        assertEquals(128, exact.toString().getBytes(StandardCharsets.UTF_8).length);
        assertEquals(129, oneOver.toString().getBytes(StandardCharsets.UTF_8).length);

        assertAll(
                () -> assertDoesNotThrow(
                        () -> new SoundProfileConfiguration(exact, 600, 1_000)),
                () -> assertDoesNotThrow(
                        () -> new ParticleProfileConfiguration(exact, 8, 50, 250, 20)),
                () -> assertDoesNotThrow(
                        () -> new TrailProfileConfiguration(exact, 8, 2, 200, 16)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new SoundProfileConfiguration(oneOver, 600, 1_000)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.SOUND,
                        soundJson(oneOver.toString(), 600, 1_000)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.PARTICLE,
                        particleJson(oneOver.toString(), 8, 50, 250, 20)),
                () -> assertCodecRejects(
                        BuiltInProfileTypes.TRAIL,
                        trailJson(oneOver.toString(), 8, 2, 200, 16)));
    }

    @Test
    void idsFactoryWitnessesAndCapabilityTuplesAreExact() {
        assertAll(
                () -> assertEquals(id("sound"), BuiltInProfileTypes.SOUND_TYPE_ID),
                () -> assertEquals(id("particle"), BuiltInProfileTypes.PARTICLE_TYPE_ID),
                () -> assertEquals(id("trail"), BuiltInProfileTypes.TRAIL_TYPE_ID),
                () -> assertSame(
                        BuiltInProfileTypes.SOUND_FACTORY_KEY,
                        BuiltInProfileTypes.SOUND.clientFactoryKey()),
                () -> assertSame(
                        BuiltInProfileTypes.PARTICLE_FACTORY_KEY,
                        BuiltInProfileTypes.PARTICLE.clientFactoryKey()),
                () -> assertSame(
                        BuiltInProfileTypes.TRAIL_FACTORY_KEY,
                        BuiltInProfileTypes.TRAIL.clientFactoryKey()),
                () -> assertEquals(
                        BuiltInProfileTypes.SOUND_TYPE_ID,
                        BuiltInProfileTypes.SOUND_FACTORY_KEY.id()),
                () -> assertEquals(
                        BuiltInProfileTypes.PARTICLE_TYPE_ID,
                        BuiltInProfileTypes.PARTICLE_FACTORY_KEY.id()),
                () -> assertEquals(
                        BuiltInProfileTypes.TRAIL_TYPE_ID,
                        BuiltInProfileTypes.TRAIL_FACTORY_KEY.id()),
                () -> assertEquals(0, BuiltInProfileTypes.SOUND.currentConfigurationVersion()),
                () -> assertEquals(0, BuiltInProfileTypes.PARTICLE.currentConfigurationVersion()),
                () -> assertEquals(0, BuiltInProfileTypes.TRAIL.currentConfigurationVersion()),
                () -> assertEquals(
                        ProfileChannel.SOUND,
                        BuiltInProfileTypes.SOUND.channel()),
                () -> assertEquals(
                        ProfileChannel.PARTICLE,
                        BuiltInProfileTypes.PARTICLE.channel()),
                () -> assertEquals(
                        ProfileChannel.TRAIL,
                        BuiltInProfileTypes.TRAIL.channel()),
                () -> assertEquals(
                        capabilities(false, false, false, false),
                        BuiltInProfileTypes.SOUND.capabilities()),
                () -> assertEquals(
                        capabilities(true, false, true, false),
                        BuiltInProfileTypes.PARTICLE.capabilities()),
                () -> assertEquals(
                        capabilities(true, true, true, true),
                        BuiltInProfileTypes.TRAIL.capabilities()),
                () -> assertTrue(BuiltInProfileTypes.SOUND
                        .capabilities()
                        .adjustableParameters()
                        .integerRanges()
                        .isEmpty()),
                () -> assertTrue(BuiltInProfileTypes.PARTICLE
                        .capabilities()
                        .adjustableParameters()
                        .integerRanges()
                        .isEmpty()),
                () -> assertTrue(BuiltInProfileTypes.TRAIL
                        .capabilities()
                        .adjustableParameters()
                        .integerRanges()
                        .isEmpty()));
    }

    @Test
    void costFormulaeCoverDefaultsMinimaMaximaAndIrrelevantScalars() {
        var soundDefault = new SoundProfileConfiguration(DEFAULT_SOUND_ASSET, 600, 1_000);
        var soundMinimum = new SoundProfileConfiguration(DEFAULT_SOUND_ASSET, 0, 500);
        var soundMaximum = new SoundProfileConfiguration(DEFAULT_SOUND_ASSET, 1_000, 2_000);
        var particleDefault = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 8, 50, 250, 20);
        var particleMinimum = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 0, 0, 1, 1);
        var particleMaximum = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 256, 16_000, 16_000, 120);
        var particleInterior = new ParticleProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 23, 100, 200, 67);
        var trailDefault = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 8, 2, 200, 16);
        var trailMinimum = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 1, 1, 1, 1);
        var trailMaximumScalars = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 48, 120, 16_000, 120);
        var trailMaximumFrequency = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 48, 1, 1, 120);

        assertAll(
                () -> assertEquals(
                        new ProfileCost(0, 1, 0, 0, 1),
                        BuiltInProfileTypes.SOUND.estimateCost(soundDefault)),
                () -> assertEquals(
                        new ProfileCost(0, 1, 0, 0, 1),
                        BuiltInProfileTypes.SOUND.estimateCost(soundMinimum)),
                () -> assertEquals(
                        new ProfileCost(0, 1, 0, 0, 1),
                        BuiltInProfileTypes.SOUND.estimateCost(soundMaximum)),
                () -> assertEquals(
                        new ProfileCost(8, 0, 0, 0, 20),
                        BuiltInProfileTypes.PARTICLE.estimateCost(particleDefault)),
                () -> assertEquals(
                        new ProfileCost(0, 0, 0, 0, 1),
                        BuiltInProfileTypes.PARTICLE.estimateCost(particleMinimum)),
                () -> assertEquals(
                        new ProfileCost(256, 0, 0, 0, 120),
                        BuiltInProfileTypes.PARTICLE.estimateCost(particleMaximum)),
                () -> assertEquals(
                        new ProfileCost(23, 0, 0, 0, 67),
                        BuiltInProfileTypes.PARTICLE.estimateCost(particleInterior)),
                () -> assertEquals(
                        new ProfileCost(0, 0, 1, 8, 16),
                        BuiltInProfileTypes.TRAIL.estimateCost(trailDefault)),
                () -> assertEquals(
                        new ProfileCost(0, 0, 1, 1, 1),
                        BuiltInProfileTypes.TRAIL.estimateCost(trailMinimum)),
                () -> assertEquals(
                        new ProfileCost(0, 0, 1, 48, 120),
                        BuiltInProfileTypes.TRAIL.estimateCost(trailMaximumScalars)),
                () -> assertEquals(
                        new ProfileCost(0, 0, 1, 48, 120),
                        BuiltInProfileTypes.TRAIL.estimateCost(trailMaximumFrequency)));
    }

    @Test
    void trailOpportunityRoundingIsExactMonotoneAndRejectsIllegalIntervals() {
        var trailDefault = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 8, 2, 200, 16);
        var trailMinimum = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 1, 1, 1, 1);
        var trailMaximumScalars = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 48, 120, 16_000, 120);
        var trailMaximumFrequency = new TrailProfileConfiguration(
                DEFAULT_PARTICLE_ASSET, 48, 1, 16_000, 120);

        assertAll(
                () -> assertEquals(
                        8,
                        BuiltInProfileValidation.trailSamplingOpportunities(
                                trailDefault, trailDefault.sampleIntervalTicks())),
                () -> assertEquals(
                        1,
                        BuiltInProfileValidation.trailSamplingOpportunities(
                                trailMinimum, trailMinimum.sampleIntervalTicks())),
                () -> assertEquals(
                        1,
                        BuiltInProfileValidation.trailSamplingOpportunities(
                                trailMaximumScalars,
                                trailMaximumScalars.sampleIntervalTicks())),
                () -> assertEquals(
                        120,
                        BuiltInProfileValidation.trailSamplingOpportunities(
                                trailMaximumFrequency,
                                trailMaximumFrequency.sampleIntervalTicks())),
                () -> assertEquals(
                        4,
                        BuiltInProfileValidation.trailSamplingOpportunities(trailDefault, 4)),
                () -> assertTrue(
                        BuiltInProfileValidation.trailSamplingOpportunities(trailDefault, 4)
                                <= BuiltInProfileValidation.trailSamplingOpportunities(
                                        trailDefault, 2)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BuiltInProfileValidation.trailSamplingOpportunities(
                                trailDefault, 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BuiltInProfileValidation.trailSamplingOpportunities(
                                trailDefault, 121)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> BuiltInProfileValidation.trailSamplingOpportunities(
                                trailDefault, 1)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> BuiltInProfileValidation.trailSamplingOpportunities(null, 1)));
    }

    @Test
    void isolatedRegistryBuilderBakesOnlyWithTheExactBuiltIns() {
        var registry = profileTypeRegistry("valid_profile_types");
        registerBuiltIns(registry);

        assertAll(
                () -> assertEquals(63, registry.getMaxId()),
                () -> assertFalse(registry.doesSync()),
                () -> assertDoesNotThrow(registry::freeze),
                () -> assertSame(
                        BuiltInProfileTypes.SOUND,
                        registry.get(BuiltInProfileTypes.SOUND_TYPE_ID)),
                () -> assertSame(
                        BuiltInProfileTypes.PARTICLE,
                        registry.get(BuiltInProfileTypes.PARTICLE_TYPE_ID)),
                () -> assertSame(
                        BuiltInProfileTypes.TRAIL,
                        registry.get(BuiltInProfileTypes.TRAIL_TYPE_ID)));

        var incompleteRegistry = profileTypeRegistry("incomplete_profile_types");
        Registry.register(
                incompleteRegistry,
                BuiltInProfileTypes.SOUND_TYPE_ID,
                BuiltInProfileTypes.SOUND);
        assertThrows(IllegalStateException.class, incompleteRegistry::freeze);
    }

    @Test
    void registryRejectsDuplicateTypeIdentityAndBakeRejectsInvalidDefinitions() {
        var duplicateType = profileTypeRegistry("duplicate_type_identity");
        registerBuiltIns(duplicateType);
        assertThrows(
                IllegalStateException.class,
                () -> Registry.register(
                        duplicateType,
                        id("duplicate_type"),
                        BuiltInProfileTypes.SOUND));

        var duplicateFactory = profileTypeRegistry("duplicate_factory_identity");
        registerBuiltIns(duplicateFactory);
        Registry.register(
                duplicateFactory,
                id("duplicate_factory"),
                soundLike(0, BuiltInProfileTypes.SOUND_FACTORY_KEY));

        var invalidVersion = profileTypeRegistry("invalid_configuration_version");
        registerBuiltIns(invalidVersion);
        Registry.register(
                invalidVersion,
                id("invalid_version"),
                soundLike(-1, new ClientFactoryKey<>(id("invalid_version_factory"))));

        var versionOneOver = profileTypeRegistry("configuration_version_one_over");
        registerBuiltIns(versionOneOver);
        Registry.register(
                versionOneOver,
                id("version_one_over"),
                soundLike(65_536, new ClientFactoryKey<>(id("version_one_over_factory"))));

        assertAll(
                () -> assertEquals(3, duplicateType.size()),
                () -> assertDoesNotThrow(duplicateType::freeze),
                () -> assertThrows(IllegalStateException.class, duplicateFactory::freeze),
                () -> assertThrows(IllegalStateException.class, invalidVersion::freeze),
                () -> assertThrows(IllegalStateException.class, versionOneOver::freeze));
    }

    @Test
    void registryAcceptsExactCapacityAndVersionMaximumButRejectsOneOverAndLateRegistration() {
        var registry = profileTypeRegistry("exact_capacity_profile_types");
        registerBuiltIns(registry);
        for (var index = 0; index < 61; index++) {
            var suffix = "%02d".formatted(index);
            Registry.register(
                    registry,
                    id("capacity_type_" + suffix),
                    soundLike(
                            index == 0 ? 65_535 : 0,
                            new ClientFactoryKey<>(id("capacity_factory_" + suffix))));
        }

        assertEquals(64, registry.size());
        assertThrows(
                IllegalStateException.class,
                () -> Registry.register(
                        registry,
                        id("capacity_type_61"),
                        soundLike(
                                0,
                                new ClientFactoryKey<>(id("capacity_factory_61")))));
        assertEquals(64, registry.size());
        assertDoesNotThrow(registry::freeze);
        assertThrows(
                IllegalStateException.class,
                () -> Registry.register(
                        registry,
                        id("late_type"),
                        soundLike(0, new ClientFactoryKey<>(id("late_factory")))));
        assertEquals(64, registry.size());
    }

    private static <C extends ProfileConfiguration> void assertCodecRoundTrip(
            ProfileType<C> type, C configuration, JsonObject expectedJson) {
        var encoded = type.configurationCodec()
                .codec()
                .encodeStart(JsonOps.INSTANCE, configuration)
                .getOrThrow();
        assertEquals(expectedJson, encoded);
        assertEquals(
                configuration,
                type.configurationCodec()
                        .codec()
                        .parse(JsonOps.INSTANCE, encoded)
                        .getOrThrow());
    }

    private static void assertCodecRejects(ProfileType<?> type, JsonElement input) {
        assertTrue(type.configurationCodec()
                .codec()
                .parse(JsonOps.INSTANCE, input)
                .error()
                .isPresent());
    }

    private static void assertNonExactIntegerRejected(
            ProfileType<?> type, JsonObject valid, String member, int legalValue) {
        var fractional = valid.deepCopy();
        fractional.addProperty(member, legalValue + 0.5D);
        var wrappingOverflow = valid.deepCopy();
        wrappingOverflow.addProperty(member, 4_294_967_296L + legalValue);

        assertAll(
                () -> assertCodecRejects(type, fractional),
                () -> assertCodecRejects(type, wrappingOverflow));
    }

    private static void assertEveryMissingMemberRejected(
            ProfileType<?> type, JsonObject valid, String... requiredMembers) {
        for (var requiredMember : requiredMembers) {
            var missing = valid.deepCopy();
            missing.remove(requiredMember);
            assertCodecRejects(type, missing);
        }
    }

    private static ProfileTypeCapabilities capabilities(
            boolean primaryColor,
            boolean secondaryColor,
            boolean direction,
            boolean trail) {
        return new ProfileTypeCapabilities(
                primaryColor,
                secondaryColor,
                direction,
                trail,
                AppearanceParameterPolicy.none());
    }

    private static Registry<ProfileType<?>> profileTypeRegistry(String path) {
        ResourceKey<Registry<ProfileType<?>>> registryKey =
                ResourceKey.createRegistryKey(id(path));
        return new RegistryBuilder<ProfileType<?>>(registryKey)
                .maxId(63)
                .sync(false)
                .onBake(BuiltInProfileTypes::validateRegistry)
                .disableRegistrationCheck()
                .create();
    }

    private static ProfileType<SoundProfileConfiguration> soundLike(
            int version, ClientFactoryKey<SoundProfileConfiguration> factoryKey) {
        return new ProfileType<>() {
            @Override
            public int currentConfigurationVersion() {
                return version;
            }

            @Override
            public ProfileChannel channel() {
                return ProfileChannel.SOUND;
            }

            @Override
            public MapCodec<SoundProfileConfiguration> configurationCodec() {
                return BuiltInProfileTypes.SOUND.configurationCodec();
            }

            @Override
            public ClientFactoryKey<SoundProfileConfiguration> clientFactoryKey() {
                return factoryKey;
            }

            @Override
            public ProfileTypeCapabilities capabilities() {
                return BuiltInProfileTypes.SOUND.capabilities();
            }

            @Override
            public ProfileCost estimateCost(SoundProfileConfiguration configuration) {
                return BuiltInProfileTypes.SOUND.estimateCost(configuration);
            }

            @Override
            public ValidationResult validate(
                    SoundProfileConfiguration configuration, ValidationContext context) {
                return BuiltInProfileTypes.SOUND.validate(configuration, context);
            }
        };
    }

    private static void registerBuiltIns(Registry<ProfileType<?>> registry) {
        Registry.register(
                registry,
                BuiltInProfileTypes.SOUND_TYPE_ID,
                BuiltInProfileTypes.SOUND);
        Registry.register(
                registry,
                BuiltInProfileTypes.PARTICLE_TYPE_ID,
                BuiltInProfileTypes.PARTICLE);
        Registry.register(
                registry,
                BuiltInProfileTypes.TRAIL_TYPE_ID,
                BuiltInProfileTypes.TRAIL);
    }

    private static JsonObject soundJson(String sound, int volumeMilli, int pitchMilli) {
        var json = new JsonObject();
        json.addProperty("sound", sound);
        json.addProperty("volume_milli", volumeMilli);
        json.addProperty("pitch_milli", pitchMilli);
        return json;
    }

    private static JsonObject particleJson(
            String particle,
            int count,
            int speedMilliBlocks,
            int sizeMilliBlocks,
            int lifetimeTicks) {
        var json = new JsonObject();
        json.addProperty("particle", particle);
        json.addProperty("count", count);
        json.addProperty("speed_milli_blocks", speedMilliBlocks);
        json.addProperty("size_milli_blocks", sizeMilliBlocks);
        json.addProperty("lifetime_ticks", lifetimeTicks);
        return json;
    }

    private static JsonObject trailJson(
            String particle,
            int segments,
            int sampleIntervalTicks,
            int sizeMilliBlocks,
            int lifetimeTicks) {
        var json = new JsonObject();
        json.addProperty("particle", particle);
        json.addProperty("segments", segments);
        json.addProperty("sample_interval_ticks", sampleIntervalTicks);
        json.addProperty("size_milli_blocks", sizeMilliBlocks);
        json.addProperty("lifetime_ticks", lifetimeTicks);
        return json;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }
}
