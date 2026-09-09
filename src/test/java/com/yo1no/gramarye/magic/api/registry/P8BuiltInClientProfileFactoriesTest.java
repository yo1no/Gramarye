package com.yo1no.gramarye.magic.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactory;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.junit.jupiter.api.Test;

final class P8BuiltInClientProfileFactoriesTest {
    private static final ResourceLocation SOUND = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "entity.experience_orb.pickup");
    private static final ResourceLocation PARTICLE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "enchant");
    private static final ResourceLocation PARAMETER =
            ResourceLocation.fromNamespaceAndPath("gramarye", "test_parameter");

    @Test
    void startupOwnerIsClientOnlyAndPairsAllBuiltInsByExactKeyIdentity() throws Exception {
        var subscriber = P8BuiltInClientProfileFactories.class.getAnnotation(
                EventBusSubscriber.class);
        var registration = P8BuiltInClientProfileFactories.class.getDeclaredMethod(
                "registerFactories", RegisterEvent.class);

        assertFalse(Modifier.isPublic(P8BuiltInClientProfileFactories.class.getModifiers()));
        assertTrue(Modifier.isFinal(P8BuiltInClientProfileFactories.class.getModifiers()));
        assertNotNull(subscriber);
        assertEquals(Gramarye.MOD_ID, subscriber.modid());
        assertEquals(List.of(Dist.CLIENT), List.of(subscriber.value()));
        assertEquals(EventBusSubscriber.Bus.MOD, subscriber.bus());
        assertTrue(Modifier.isStatic(registration.getModifiers()));
        assertFalse(Modifier.isPublic(registration.getModifiers()));
        assertEquals(void.class, registration.getReturnType());
        assertNotNull(registration.getAnnotation(SubscribeEvent.class));
        assertSame(
                BuiltInProfileTypes.SOUND_FACTORY_KEY,
                BuiltInProfileTypes.SOUND.clientFactoryKey());
        assertSame(
                BuiltInProfileTypes.PARTICLE_FACTORY_KEY,
                BuiltInProfileTypes.PARTICLE.clientFactoryKey());
        assertSame(
                BuiltInProfileTypes.TRAIL_FACTORY_KEY,
                BuiltInProfileTypes.TRAIL.clientFactoryKey());
        assertEquals(
                BuiltInProfileTypes.SOUND_TYPE_ID,
                BuiltInProfileTypes.SOUND_FACTORY_KEY.id());
        assertEquals(
                BuiltInProfileTypes.PARTICLE_TYPE_ID,
                BuiltInProfileTypes.PARTICLE_FACTORY_KEY.id());
        assertEquals(
                BuiltInProfileTypes.TRAIL_TYPE_ID,
                BuiltInProfileTypes.TRAIL_FACTORY_KEY.id());
    }

    @Test
    void soundFactoryUsesTypedConfigurationAssetProbeAndAttenuatedCommand() {
        var configuration = new SoundProfileConfiguration(SOUND, 800, 1_250);
        var available = new TestAssets(Set.of(SOUND), Set.of(), Set.of());
        var unavailable = new TestAssets(Set.of(), Set.of(), Set.of());
        assertEquals(
                ClientProfileFactory.Availability.AVAILABLE,
                P8BuiltInClientProfileFactories.SOUND.availability(configuration, available));
        assertEquals(
                ClientProfileFactory.Availability.UNAVAILABLE,
                P8BuiltInClientProfileFactories.SOUND.availability(configuration, unavailable));

        var input = input(500, 0x123456789abcdef0L, OptionalInt.of(7), OptionalInt.of(9));
        var output = new RecordingOutput();
        assertEquals(
                ClientProfileFactory.Result.PRESENTED,
                P8BuiltInClientProfileFactories.SOUND.present(configuration, input, output));

        assertEquals(1, output.sounds.size());
        var command = output.sounds.get(0);
        assertSame(SOUND, command.soundEventId());
        assertEquals(input.x(), command.x());
        assertEquals(input.y(), command.y());
        assertEquals(input.z(), command.z());
        assertEquals(0.4F, command.volume(), 0.000001F);
        assertEquals(1.25F, command.pitch(), 0.000001F);
        assertTrue(output.particles.isEmpty());
        assertTrue(output.trails.isEmpty());
    }

    @Test
    void particleFactoryIsSeedDeterministicAndUsesDirectionColorAndIntensity() {
        var configuration = new ParticleProfileConfiguration(PARTICLE, 4, 800, 250, 20);
        var assets = new TestAssets(Set.of(), Set.of(PARTICLE), Set.of());
        assertEquals(
                ClientProfileFactory.Availability.AVAILABLE,
                P8BuiltInClientProfileFactories.PARTICLE.availability(configuration, assets));
        assertEquals(
                ClientProfileFactory.Availability.UNAVAILABLE,
                P8BuiltInClientProfileFactories.PARTICLE.availability(
                        configuration, new TestAssets(Set.of(), Set.of(), Set.of())));

        var input = input(750, 0x1020304050607080L, OptionalInt.of(7), OptionalInt.of(9));
        var first = new RecordingOutput();
        var second = new RecordingOutput();
        var differentSeed = new RecordingOutput();
        assertEquals(
                ClientProfileFactory.Result.PRESENTED,
                P8BuiltInClientProfileFactories.PARTICLE.present(configuration, input, first));
        P8BuiltInClientProfileFactories.PARTICLE.present(configuration, input, second);
        P8BuiltInClientProfileFactories.PARTICLE.present(
                configuration,
                input(750, input.visualSeed() + 1L, OptionalInt.of(7), OptionalInt.of(9)),
                differentSeed);

        assertEquals(3, first.particles.size());
        assertEquals(first.particles, second.particles);
        assertNotEquals(first.particles, differentSeed.particles);
        for (var command : first.particles) {
            assertSame(PARTICLE, command.particleTypeId());
            assertEquals(input.x(), command.x());
            assertEquals(input.y(), command.y());
            assertEquals(input.z(), command.z());
            assertEquals(input.primaryArgb(), command.argb());
            assertEquals(0.25F, command.size(), 0.000001F);
            assertEquals(20, command.lifetimeTicks());
            assertTrue(command.velocityX() >= 0.38D && command.velocityX() <= 0.58D);
            assertTrue(command.velocityY() >= 0.54D && command.velocityY() <= 0.74D);
            assertTrue(command.velocityZ() >= -0.1D && command.velocityZ() <= 0.1D);
        }
        assertTrue(first.sounds.isEmpty());
        assertTrue(first.trails.isEmpty());
    }

    @Test
    void trailFactoryUsesTargetThenSourceAndBlendsColorsExactly() {
        var configuration = new TrailProfileConfiguration(PARTICLE, 8, 2, 200, 16);
        assertEquals(
                ClientProfileFactory.Availability.AVAILABLE,
                P8BuiltInClientProfileFactories.TRAIL.availability(
                        configuration,
                        new TestAssets(Set.of(), Set.of(PARTICLE), Set.of())));
        assertEquals(
                ClientProfileFactory.Availability.UNAVAILABLE,
                P8BuiltInClientProfileFactories.TRAIL.availability(
                        configuration,
                        new TestAssets(Set.of(), Set.of(), Set.of())));

        var targetInput = input(
                1_000, 17L, OptionalInt.of(7), OptionalInt.of(9));
        var targetOutput = new RecordingOutput();
        assertEquals(
                ClientProfileFactory.Result.PRESENTED,
                P8BuiltInClientProfileFactories.TRAIL.present(
                        configuration, targetInput, targetOutput));
        assertEquals(1, targetOutput.trails.size());
        var target = targetOutput.trails.get(0);
        assertEquals(OptionalInt.of(9), target.trackedEntityId());
        assertSame(PARTICLE, target.particleTypeId());
        assertEquals(0xff6080a0, target.argb());
        assertEquals(0.2F, target.size(), 0.000001F);
        assertEquals(16, target.lifetimeTicks());
        assertEquals(2, target.sampleIntervalTicks());

        var sourceOutput = new RecordingOutput();
        P8BuiltInClientProfileFactories.TRAIL.present(
                configuration,
                input(1_000, 17L, OptionalInt.of(7), OptionalInt.empty()),
                sourceOutput);
        assertEquals(OptionalInt.of(7), sourceOutput.trails.get(0).trackedEntityId());
    }

    @Test
    void zeroIntensityRemainsPresentedWithoutEmittingAnyBuiltInCommand() {
        var input = input(0, 1L, OptionalInt.of(7), OptionalInt.empty());
        var soundOutput = new RecordingOutput();
        var particleOutput = new RecordingOutput();
        var trailOutput = new RecordingOutput();

        assertEquals(
                ClientProfileFactory.Result.PRESENTED,
                P8BuiltInClientProfileFactories.SOUND.present(
                        new SoundProfileConfiguration(SOUND, 800, 1_000),
                        input,
                        soundOutput));
        assertEquals(
                ClientProfileFactory.Result.PRESENTED,
                P8BuiltInClientProfileFactories.PARTICLE.present(
                        new ParticleProfileConfiguration(PARTICLE, 8, 50, 250, 20),
                        input,
                        particleOutput));
        assertEquals(
                ClientProfileFactory.Result.PRESENTED,
                P8BuiltInClientProfileFactories.TRAIL.present(
                        new TrailProfileConfiguration(PARTICLE, 8, 2, 200, 16),
                        input,
                        trailOutput));

        assertEquals(0, soundOutput.commandCount());
        assertEquals(0, particleOutput.commandCount());
        assertEquals(0, trailOutput.commandCount());
    }

    private static TestInput input(
            int intensity,
            long visualSeed,
            OptionalInt sourceEntityId,
            OptionalInt targetEntityId) {
        return new TestInput(
                1,
                sourceEntityId,
                targetEntityId,
                1.25D,
                2.5D,
                3.75D,
                0.6F,
                0.8F,
                0.0F,
                0xff204060,
                0xffa0c0e0,
                intensity,
                Map.of(PARAMETER, 37),
                visualSeed,
                9L);
    }

    private record TestAssets(
            Set<ResourceLocation> sounds,
            Set<ResourceLocation> particles,
            Set<ResourceLocation> resources) implements ClientProfileFactory.AssetView {
        @Override
        public boolean soundExists(ResourceLocation id) {
            return sounds.contains(id);
        }

        @Override
        public boolean particleExists(ResourceLocation id) {
            return particles.contains(id);
        }

        @Override
        public boolean resourceExists(ResourceLocation id) {
            return resources.contains(id);
        }
    }

    private record TestInput(
            int eventKindCode,
            OptionalInt sourceEntityId,
            OptionalInt targetEntityId,
            double x,
            double y,
            double z,
            float directionX,
            float directionY,
            float directionZ,
            int primaryArgb,
            int secondaryArgb,
            int intensity,
            Map<ResourceLocation, Integer> overrides,
            long visualSeed,
            long sequence) implements ClientProfileFactory.Input {
        @Override
        public OptionalInt override(ResourceLocation key) {
            var value = overrides.get(key);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }

    private static final class RecordingOutput implements ClientProfileFactory.Output {
        private final ArrayList<ClientProfileFactory.Particle> particles = new ArrayList<>();
        private final ArrayList<ClientProfileFactory.Sound> sounds = new ArrayList<>();
        private final ArrayList<ClientProfileFactory.Trail> trails = new ArrayList<>();

        @Override
        public boolean particle(ClientProfileFactory.Particle command) {
            particles.add(command);
            return true;
        }

        @Override
        public boolean sound(ClientProfileFactory.Sound command) {
            sounds.add(command);
            return true;
        }

        @Override
        public boolean trail(ClientProfileFactory.Trail command) {
            trails.add(command);
            return true;
        }

        private int commandCount() {
            return particles.size() + sounds.size() + trails.size();
        }
    }
}
