package com.yo1no.gramarye.client.presentation.api;

import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.resources.ResourceLocation;

/** Client-only startup-frozen factory contract for one typed presentation Profile. */
public interface ClientProfileFactory<C extends ProfileConfiguration> {
    Availability availability(C configuration, AssetView assets);

    Result present(C configuration, Input input, Output output);

    enum Availability {
        AVAILABLE,
        UNAVAILABLE
    }

    enum Result {
        PRESENTED,
        UNAVAILABLE
    }

    interface AssetView {
        boolean soundExists(ResourceLocation id);

        boolean particleExists(ResourceLocation id);

        boolean resourceExists(ResourceLocation id);
    }

    interface Input {
        int eventKindCode();

        OptionalInt sourceEntityId();

        OptionalInt targetEntityId();

        double x();

        double y();

        double z();

        float directionX();

        float directionY();

        float directionZ();

        int primaryArgb();

        int secondaryArgb();

        int intensity();

        OptionalInt override(ResourceLocation key);

        long visualSeed();

        long sequence();
    }

    interface Output {
        boolean particle(Particle command);

        boolean sound(Sound command);

        boolean trail(Trail command);
    }

    record Particle(
            ResourceLocation particleTypeId,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            int argb,
            float size,
            int lifetimeTicks) {
        public Particle {
            Objects.requireNonNull(particleTypeId, "particleTypeId");
        }
    }

    record Sound(
            ResourceLocation soundEventId,
            double x,
            double y,
            double z,
            float volume,
            float pitch) {
        public Sound {
            Objects.requireNonNull(soundEventId, "soundEventId");
        }
    }

    record Trail(
            ResourceLocation particleTypeId,
            OptionalInt trackedEntityId,
            double x,
            double y,
            double z,
            int argb,
            float size,
            int lifetimeTicks,
            int sampleIntervalTicks) {
        public Trail {
            Objects.requireNonNull(particleTypeId, "particleTypeId");
            Objects.requireNonNull(trackedEntityId, "trackedEntityId");
        }
    }
}
