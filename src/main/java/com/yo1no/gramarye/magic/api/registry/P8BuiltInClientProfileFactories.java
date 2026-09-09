package com.yo1no.gramarye.magic.api.registry;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactories;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactory;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactoryRegistration;
import java.util.Objects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Client-dist startup owner for the three mandatory built-in factory bindings. */
@EventBusSubscriber(
        modid = Gramarye.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
final class P8BuiltInClientProfileFactories {
    static final ClientProfileFactory<SoundProfileConfiguration> SOUND =
            new SoundClientProfileFactory();
    static final ClientProfileFactory<ParticleProfileConfiguration> PARTICLE =
            new ParticleClientProfileFactory();
    static final ClientProfileFactory<TrailProfileConfiguration> TRAIL =
            new TrailClientProfileFactory();

    private P8BuiltInClientProfileFactories() {
        throw new AssertionError("no instances");
    }

    @SubscribeEvent
    static void registerFactories(RegisterEvent event) {
        Objects.requireNonNull(event, "event");
        event.register(ClientProfileFactories.REGISTRY_KEY, helper -> {
            helper.register(
                    BuiltInProfileTypes.SOUND_TYPE_ID,
                    new ClientProfileFactoryRegistration<>(
                            BuiltInProfileTypes.SOUND_FACTORY_KEY, SOUND));
            helper.register(
                    BuiltInProfileTypes.PARTICLE_TYPE_ID,
                    new ClientProfileFactoryRegistration<>(
                            BuiltInProfileTypes.PARTICLE_FACTORY_KEY, PARTICLE));
            helper.register(
                    BuiltInProfileTypes.TRAIL_TYPE_ID,
                    new ClientProfileFactoryRegistration<>(
                            BuiltInProfileTypes.TRAIL_FACTORY_KEY, TRAIL));
        });
    }

    private static final class SoundClientProfileFactory
            implements ClientProfileFactory<SoundProfileConfiguration> {
        @Override
        public Availability availability(
                SoundProfileConfiguration configuration, AssetView assets) {
            Objects.requireNonNull(configuration, "configuration");
            return Objects.requireNonNull(assets, "assets").soundExists(configuration.sound())
                    ? Availability.AVAILABLE
                    : Availability.UNAVAILABLE;
        }

        @Override
        public Result present(
                SoundProfileConfiguration configuration, Input input, Output output) {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(output, "output");
            var attenuation = Math.min(input.intensity(), 1_000) / 1_000.0F;
            var volume = configuration.volumeMilli() / 1_000.0F * attenuation;
            if (volume > 0.0F) {
                output.sound(new Sound(
                        configuration.sound(),
                        input.x(),
                        input.y(),
                        input.z(),
                        volume,
                        configuration.pitchMilli() / 1_000.0F));
            }
            return Result.PRESENTED;
        }
    }

    private static final class ParticleClientProfileFactory
            implements ClientProfileFactory<ParticleProfileConfiguration> {
        @Override
        public Availability availability(
                ParticleProfileConfiguration configuration, AssetView assets) {
            Objects.requireNonNull(configuration, "configuration");
            return Objects.requireNonNull(assets, "assets")
                            .particleExists(configuration.particle())
                    ? Availability.AVAILABLE
                    : Availability.UNAVAILABLE;
        }

        @Override
        public Result present(
                ParticleProfileConfiguration configuration, Input input, Output output) {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(output, "output");
            var attenuation = Math.min(input.intensity(), 1_000);
            var count = Math.toIntExact(
                    Math.multiplyExact((long) configuration.count(), attenuation) / 1_000L);
            var speed = configuration.speedMilliBlocks() / 1_000.0D;
            for (var index = 0; index < count; index++) {
                var xJitter = signedUnit(mix(input.visualSeed(), index * 3L));
                var yJitter = signedUnit(mix(input.visualSeed(), index * 3L + 1L));
                var zJitter = signedUnit(mix(input.visualSeed(), index * 3L + 2L));
                output.particle(new Particle(
                        configuration.particle(),
                        input.x(),
                        input.y(),
                        input.z(),
                        clampVelocity(input.directionX() * speed + xJitter * speed * 0.125D),
                        clampVelocity(input.directionY() * speed + yJitter * speed * 0.125D),
                        clampVelocity(input.directionZ() * speed + zJitter * speed * 0.125D),
                        input.primaryArgb(),
                        configuration.sizeMilliBlocks() / 1_000.0F,
                        configuration.lifetimeTicks()));
            }
            return Result.PRESENTED;
        }

        private static long mix(long seed, long salt) {
            var value = seed + 0x9e3779b97f4a7c15L * (salt + 1L);
            value = (value ^ value >>> 30) * 0xbf58476d1ce4e5b9L;
            value = (value ^ value >>> 27) * 0x94d049bb133111ebL;
            return value ^ value >>> 31;
        }

        private static double signedUnit(long bits) {
            return ((bits >>> 11) * 0x1.0p-53D) * 2.0D - 1.0D;
        }

        private static double clampVelocity(double value) {
            return Math.max(-16.0D, Math.min(16.0D, value));
        }
    }

    private static final class TrailClientProfileFactory
            implements ClientProfileFactory<TrailProfileConfiguration> {
        @Override
        public Availability availability(
                TrailProfileConfiguration configuration, AssetView assets) {
            Objects.requireNonNull(configuration, "configuration");
            return Objects.requireNonNull(assets, "assets")
                            .particleExists(configuration.particle())
                    ? Availability.AVAILABLE
                    : Availability.UNAVAILABLE;
        }

        @Override
        public Result present(
                TrailProfileConfiguration configuration, Input input, Output output) {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(output, "output");
            if (input.intensity() == 0) {
                return Result.PRESENTED;
            }
            var tracked = input.targetEntityId().isPresent()
                    ? input.targetEntityId()
                    : input.sourceEntityId();
            output.trail(new Trail(
                    configuration.particle(),
                    tracked,
                    input.x(),
                    input.y(),
                    input.z(),
                    blendArgb(input.primaryArgb(), input.secondaryArgb()),
                    configuration.sizeMilliBlocks() / 1_000.0F,
                    configuration.lifetimeTicks(),
                    configuration.sampleIntervalTicks()));
            return Result.PRESENTED;
        }

        private static int blendArgb(int primary, int secondary) {
            var alpha = (((primary >>> 24) & 0xff) + ((secondary >>> 24) & 0xff)) >>> 1;
            var red = (((primary >>> 16) & 0xff) + ((secondary >>> 16) & 0xff)) >>> 1;
            var green = (((primary >>> 8) & 0xff) + ((secondary >>> 8) & 0xff)) >>> 1;
            var blue = ((primary & 0xff) + (secondary & 0xff)) >>> 1;
            return alpha << 24 | red << 16 | green << 8 | blue;
        }
    }
}
