package com.yo1no.gramarye;

import com.yo1no.gramarye.client.presentation.api.ClientProfileFactory;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Actual 1.21.1 Minecraft sound/particle adapter; loaded only from the client mod. */
enum MinecraftP8ClientPresentationBackend implements P8ClientPresentationBackend {
    INSTANCE;

    @Override
    public boolean matchesDimension(ResourceLocation dimension) {
        Objects.requireNonNull(dimension, "dimension");
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        return minecraft.level != null
                && minecraft.level.dimension().location().equals(dimension);
    }

    @Override
    public P8ClientRecipientContext recipientContext(PresentationEventPayload payload) {
        Objects.requireNonNull(payload, "payload");
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        var player = minecraft.player;
        if (player == null) {
            return new P8ClientRecipientContext(
                    PresentationOrdering.RecipientCategory.ORDINARY,
                    PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED);
        }
        var source = payload.sourceSummary();
        var category = source.targetEntityId().isPresent()
                        && source.targetEntityId().orElseThrow() == player.getId()
                ? PresentationOrdering.RecipientCategory.TARGET_SELF
                : source.sourceEntityId().isPresent()
                                && source.sourceEntityId().orElseThrow() == player.getId()
                        ? PresentationOrdering.RecipientCategory.SOURCE
                        : PresentationOrdering.RecipientCategory.ORDINARY;
        var position = payload.position();
        var dx = player.getX() - position.x();
        var dy = player.getY() - position.y();
        var dz = player.getZ() - position.z();
        var squaredDistance = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(squaredDistance)) {
            squaredDistance = PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED;
        }
        return new P8ClientRecipientContext(
                category,
                Math.min(
                        squaredDistance,
                        PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED));
    }

    @Override
    public boolean soundExists(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        var sounds = minecraft.getSoundManager().getSoundEvent(id);
        return sounds != null && sounds.getWeight() > 0;
    }

    @Override
    public boolean particleExists(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        var sprites = spriteSet(minecraft.particleEngine, id);
        return BuiltInRegistries.PARTICLE_TYPE.containsKey(id)
                && sprites != null
                && usable(sprites);
    }

    @Override
    public boolean resourceExists(
            ResourceLocation id, P8ClientResourceIndex resources) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resources, "resources");
        requireClientThread(Minecraft.getInstance());
        return resources.contains(id);
    }

    @Override
    public P8ClientParticlePreference particlePreference() {
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        return switch (minecraft.options.particles().get()) {
            case ALL -> P8ClientParticlePreference.FULL;
            case DECREASED -> P8ClientParticlePreference.REDUCED;
            case MINIMAL -> P8ClientParticlePreference.MINIMAL;
        };
    }

    @Override
    public float soundVolume() {
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        var master = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
        var players = minecraft.options.getSoundSourceVolume(SoundSource.PLAYERS);
        return Math.max(0.0F, Math.min(1.0F, master * players));
    }

    @Override
    public P8ClientSoundHandle prepareSound(
            ClientProfileFactory.Sound command, long visualSeed) {
        Objects.requireNonNull(command, "command");
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        return soundHandle(
                command,
                visualSeed,
                NeoForge.EVENT_BUS,
                MinecraftSoundControl.INSTANCE);
    }

    static P8ClientSoundHandle soundHandle(
            ClientProfileFactory.Sound command,
            long visualSeed,
            IEventBus eventBus,
            P8SoundControl control) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(eventBus, "eventBus");
        Objects.requireNonNull(control, "control");
        var instance = soundInstance(command, visualSeed);
        return new MinecraftSoundHandle(
                instance,
                soundInstance(command, visualSeed),
                command,
                eventBus,
                control);
    }

    private static P8SoundInstance soundInstance(
            ClientProfileFactory.Sound command, long visualSeed) {
        return new P8SoundInstance(
                command.soundEventId(),
                SoundSource.PLAYERS,
                command.volume(),
                command.pitch(),
                RandomSource.create(visualSeed),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                command.x(),
                command.y(),
                command.z(),
                false);
    }

    @Override
    public P8ClientPreparedParticle prepareParticle(ClientProfileFactory.Particle command) {
        Objects.requireNonNull(command, "command");
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        var level = minecraft.level;
        var sprites = spriteSet(minecraft.particleEngine, command.particleTypeId());
        if (level == null || sprites == null || !usable(sprites)) {
            return null;
        }
        var particle = new P8TextureSheetParticle(level, command, sprites);
        return new PreparedMinecraftParticle(minecraft.particleEngine, particle);
    }

    @Override
    public Optional<P8ClientPosition> trackedPosition(int entityId) {
        var minecraft = Minecraft.getInstance();
        requireClientThread(minecraft);
        if (entityId < 1 || minecraft.level == null) {
            return Optional.empty();
        }
        var entity = minecraft.level.getEntity(entityId);
        return entity == null
                ? Optional.empty()
                : Optional.of(new P8ClientPosition(
                        entity.getX(), entity.getY(), entity.getZ()));
    }

    private static void requireClientThread(Minecraft minecraft) {
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("P8 platform work requires the client main thread");
        }
    }

    private static SpriteSet spriteSet(ParticleEngine engine, ResourceLocation id) {
        Object rawSpriteSets = engine.spriteSets;
        if (!(rawSpriteSets instanceof Map<?, ?> spriteSets)) {
            return null;
        }
        var candidate = spriteSets.get(id);
        return candidate instanceof SpriteSet sprites ? sprites : null;
    }

    private static boolean usable(SpriteSet sprites) {
        try {
            return sprites.get(0, 1) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static Sound boundedSoundAsset(Sound selected) {
        Objects.requireNonNull(selected, "selected");
        return new Sound(
                selected.getLocation(),
                ConstantFloat.of(1.0F),
                ConstantFloat.of(1.0F),
                selected.getWeight(),
                selected.getType(),
                selected.shouldStream(),
                selected.shouldPreload(),
                Math.toIntExact(PresentationLimits.MAX_RECIPIENT_RANGE_BLOCKS));
    }

    private record PreparedMinecraftParticle(
            ParticleEngine engine, P8TextureSheetParticle particle)
            implements P8ClientPreparedParticle {
        private PreparedMinecraftParticle {
            Objects.requireNonNull(engine, "engine");
            Objects.requireNonNull(particle, "particle");
        }

        @Override
        public void start() {
            engine.add(particle);
        }
    }

    private static final class P8TextureSheetParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        private P8TextureSheetParticle(
                ClientLevel level,
                ClientProfileFactory.Particle command,
                SpriteSet sprites) {
            super(level, command.x(), command.y(), command.z());
            this.sprites = Objects.requireNonNull(sprites, "sprites");
            setParticleSpeed(
                    command.velocityX(), command.velocityY(), command.velocityZ());
            setColor(
                    ((command.argb() >>> 16) & 0xff) / 255.0F,
                    ((command.argb() >>> 8) & 0xff) / 255.0F,
                    (command.argb() & 0xff) / 255.0F);
            setAlpha(((command.argb() >>> 24) & 0xff) / 255.0F);
            setLifetime(command.lifetimeTicks());
            quadSize = command.size() * 0.5F;
            setSprite(sprites.get(0, command.lifetimeTicks()));
        }

        @Override
        public void tick() {
            super.tick();
            setSpriteFromAge(sprites);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    abstract static class P8SoundControl {
        abstract void play(SoundInstance sound);

        abstract boolean active(SoundInstance sound);

        abstract void stop(SoundInstance sound);
    }

    private static final class MinecraftSoundControl extends P8SoundControl {
        private static final MinecraftSoundControl INSTANCE =
                new MinecraftSoundControl();

        @Override
        void play(SoundInstance sound) {
            var minecraft = Minecraft.getInstance();
            requireClientThread(minecraft);
            minecraft.getSoundManager().play(sound);
        }

        @Override
        boolean active(SoundInstance sound) {
            var minecraft = Minecraft.getInstance();
            requireClientThread(minecraft);
            return minecraft.getSoundManager().isActive(sound);
        }

        @Override
        void stop(SoundInstance sound) {
            var minecraft = Minecraft.getInstance();
            requireClientThread(minecraft);
            minecraft.getSoundManager().stop(sound);
        }
    }

    private static final class MinecraftSoundHandle implements P8ClientSoundHandle {
        private final SoundInstance originalSound;
        private final SoundInstance boundedReplacement;
        private final ClientProfileFactory.Sound command;
        private final IEventBus eventBus;
        private final P8SoundControl control;
        private final Consumer<PlaySoundEvent> finalSoundObserver =
                this::observeFinalSound;
        private SoundInstance effectiveSound;
        private boolean startAttempted;
        private boolean stopped;

        private MinecraftSoundHandle(
                SoundInstance originalSound,
                SoundInstance boundedReplacement,
                ClientProfileFactory.Sound command,
                IEventBus eventBus,
                P8SoundControl control) {
            this.originalSound = Objects.requireNonNull(
                    originalSound, "originalSound");
            this.boundedReplacement = Objects.requireNonNull(
                    boundedReplacement, "boundedReplacement");
            this.command = Objects.requireNonNull(command, "command");
            this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
            this.control = Objects.requireNonNull(control, "control");
        }

        @Override
        public void start() {
            if (startAttempted) {
                throw new IllegalStateException("sound handle already started");
            }
            startAttempted = true;
            try {
                eventBus.addListener(
                        EventPriority.LOWEST,
                        PlaySoundEvent.class,
                        finalSoundObserver);
                control.play(originalSound);
                eventBus.unregister(finalSoundObserver);
            } catch (RuntimeException | Error failure) {
                unregisterObserverSuppressingFailures();
                throw failure;
            }
        }

        @Override
        public boolean active() {
            return startAttempted
                    && effectiveSound != null
                    && control.active(effectiveSound);
        }

        @Override
        public void stop() {
            if (startAttempted && !stopped) {
                if (effectiveSound != null) {
                    control.stop(effectiveSound);
                }
                stopped = true;
            }
        }

        private void observeFinalSound(PlaySoundEvent event) {
            if (event.getOriginalSound() != originalSound) {
                return;
            }
            var selected = event.getSound();
            if (selected == null) {
                effectiveSound = null;
                return;
            }
            if (selected == originalSound) {
                effectiveSound = originalSound;
                return;
            }
            if (!command.soundEventId().equals(selected.getLocation())) {
                event.setSound(null);
                effectiveSound = null;
                return;
            }
            effectiveSound = boundedReplacement;
            event.setSound(effectiveSound);
        }

        private void unregisterObserverSuppressingFailures() {
            try {
                eventBus.unregister(finalSoundObserver);
            } catch (RuntimeException | Error cleanupFailure) {
                // Preserve the exact primary platform failure without history.
            }
        }
    }

    /** Resolves the selected asset normally while fixing P8's audible range to 64 blocks. */
    private static final class P8SoundInstance extends SimpleSoundInstance {
        private final float boundedVolume;
        private final float boundedPitch;

        private P8SoundInstance(
                ResourceLocation id,
                SoundSource source,
                float volume,
                float pitch,
                RandomSource random,
                boolean looping,
                int delay,
                SoundInstance.Attenuation attenuation,
                double x,
                double y,
                double z,
                boolean relative) {
            super(
                    id,
                    source,
                    volume,
                    pitch,
                    random,
                    looping,
                    delay,
                    attenuation,
                    x,
                    y,
                    z,
                    relative);
            boundedVolume = volume;
            boundedPitch = pitch;
        }

        @Override
        public float getVolume() {
            return boundedVolume;
        }

        @Override
        public float getPitch() {
            return boundedPitch;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            var resolved = super.resolve(manager);
            var selected = getSound();
            if (selected != SoundManager.EMPTY_SOUND
                    && selected != SoundManager.INTENTIONALLY_EMPTY_SOUND) {
                sound = boundedSoundAsset(selected);
            }
            return resolved;
        }
    }
}
