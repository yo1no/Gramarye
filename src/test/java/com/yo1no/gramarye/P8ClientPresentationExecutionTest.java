package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.client.presentation.api.ClientProfileFactory;
import com.yo1no.gramarye.client.presentation.api.ClientProfileFactoryRegistration;
import com.yo1no.gramarye.magic.presentation.api.ClientFactoryKey;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.junit.jupiter.api.Test;

final class P8ClientPresentationExecutionTest {
    private static final long CONNECTION_GENERATION = 11L;
    private static final long WORLD_GENERATION = 22L;
    private static final long RESOURCE_GENERATION = 33L;
    private static final long CATALOG_GENERATION = 1L;

    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final ResourceLocation SOUND_ASSET = ResourceLocation.fromNamespaceAndPath(
            "minecraft", "entity.experience_orb.pickup");
    private static final ResourceLocation PARTICLE_ASSET =
            ResourceLocation.fromNamespaceAndPath("minecraft", "enchant");
    private static final ResourceLocation PARAMETER_ID = id("test_parameter");
    private static final ResourceLocation SELECTED_SOUND_ID = id("selected_sound");
    private static final ResourceLocation SELECTED_PARTICLE_ID = id("selected_particle");
    private static final ResourceLocation SELECTED_TRAIL_ID = id("selected_trail");
    private static final ResourceLocation MIDDLE_PARTICLE_ID = id("middle_particle");
    private static final ResourceLocation LAST_PARTICLE_ID = id("zz_selected_particle");

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

    private static final P8S2TestFixtures.SoundConfiguration DEFAULT_SOUND =
            new P8S2TestFixtures.SoundConfiguration(SOUND_ASSET, 600, 1_000);
    private static final P8S2TestFixtures.ParticleConfiguration DEFAULT_PARTICLE =
            new P8S2TestFixtures.ParticleConfiguration(PARTICLE_ASSET, 8, 50, 250, 20);
    private static final P8S2TestFixtures.TrailConfiguration DEFAULT_TRAIL =
            new P8S2TestFixtures.TrailConfiguration(PARTICLE_ASSET, 8, 2, 200, 16);

    @Test
    void executionOwnerHasNoPublicOrProtectedDeclaredSurface() {
        assertFalse(java.lang.reflect.Modifier.isPublic(
                P8ClientPresentationExecution.class.getModifiers()));
        assertTrue(Arrays.stream(P8ClientPresentationExecution.class.getDeclaredConstructors())
                .noneMatch(constructor -> isPublicOrProtected(constructor.getModifiers())));
        assertTrue(Arrays.stream(P8ClientPresentationExecution.class.getDeclaredFields())
                .noneMatch(field -> isPublicOrProtected(field.getModifiers())));
        assertTrue(Arrays.stream(P8ClientPresentationExecution.class.getDeclaredMethods())
                .noneMatch(method -> isPublicOrProtected(method.getModifiers())));
    }

    @Test
    void soundAssetBindingForcesExactP8RangeWithoutAssetVolumeAmplification() {
        var selected = new Sound(
                SOUND_ASSET,
                ConstantFloat.of(4.0F),
                ConstantFloat.of(4.0F),
                7,
                Sound.Type.FILE,
                true,
                true,
                900);

        var bounded = MinecraftP8ClientPresentationBackend.boundedSoundAsset(selected);

        assertEquals(SOUND_ASSET, bounded.getLocation());
        assertEquals(1.0F, bounded.getVolume().sample(RandomSource.create(1L)));
        assertEquals(1.0F, bounded.getPitch().sample(RandomSource.create(2L)));
        assertEquals(7, bounded.getWeight());
        assertEquals(Sound.Type.FILE, bounded.getType());
        assertTrue(bounded.shouldStream());
        assertTrue(bounded.shouldPreload());
        assertEquals(64, bounded.getAttenuationDistance());
    }

    @Test
    void finalSoundReplacementIsReboundedAndOwnedByTheExactHandle() {
        var bus = BusBuilder.builder().build();
        var replacement = simpleSound(
                SOUND_ASSET, SoundSource.MUSIC, 4.0F, 0.25F, 90.0D);
        bus.addListener(
                EventPriority.LOWEST,
                PlaySoundEvent.class,
                event -> event.setSound(replacement));
        var unrelated = simpleSound(
                SOUND_ASSET, SoundSource.AMBIENT, 0.2F, 0.8F, 3.0D);
        var control = new DirectSoundControl(bus, unrelated);
        var command = new ClientProfileFactory.Sound(
                SOUND_ASSET, 1.0D, 2.0D, 3.0D, 0.6F, 1.25F);
        var handle = MinecraftP8ClientPresentationBackend.soundHandle(
                command, 17L, bus, control);

        handle.start();

        assertSame(replacement, control.unrelatedFinal);
        assertFalse(control.played == replacement);
        assertEquals(SOUND_ASSET, control.played.getLocation());
        assertEquals(SoundSource.PLAYERS, control.played.getSource());
        assertEquals(0.6F, control.played.getVolume());
        assertEquals(1.25F, control.played.getPitch());
        assertEquals(1.0D, control.played.getX());
        assertEquals(2.0D, control.played.getY());
        assertEquals(3.0D, control.played.getZ());
        assertFalse(control.played.isLooping());
        assertFalse(control.played.isRelative());
        assertEquals(0, control.played.getDelay());
        assertEquals(SoundInstance.Attenuation.LINEAR, control.played.getAttenuation());
        assertTrue(handle.active());

        handle.stop();

        assertSame(control.played, control.stopped);
        assertFalse(handle.active());
        var after = bus.post(new PlaySoundEvent(null, control.original));
        assertSame(replacement, after.getSound());
    }

    @Test
    void finalSoundSuppressionAndForeignAssetBothFailClosed() {
        var command = new ClientProfileFactory.Sound(
                SOUND_ASSET, 1.0D, 2.0D, 3.0D, 0.6F, 1.25F);
        var suppressedBus = BusBuilder.builder().build();
        suppressedBus.addListener(
                EventPriority.LOWEST,
                PlaySoundEvent.class,
                event -> event.setSound(null));
        var suppressedControl = new DirectSoundControl(suppressedBus, null);
        var suppressed = MinecraftP8ClientPresentationBackend.soundHandle(
                command, 18L, suppressedBus, suppressedControl);

        suppressed.start();

        assertTrue(suppressedControl.played == null);
        assertFalse(suppressed.active());
        suppressed.stop();
        assertEquals(0, suppressedControl.stopCalls);

        var foreignBus = BusBuilder.builder().build();
        var foreign = simpleSound(
                ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.bell"),
                SoundSource.PLAYERS,
                0.5F,
                1.0F,
                1.0D);
        foreignBus.addListener(
                EventPriority.LOWEST,
                PlaySoundEvent.class,
                event -> event.setSound(foreign));
        var foreignControl = new DirectSoundControl(foreignBus, null);
        var rejected = MinecraftP8ClientPresentationBackend.soundHandle(
                command, 19L, foreignBus, foreignControl);

        rejected.start();

        assertTrue(foreignControl.played == null);
        assertFalse(rejected.active());
        rejected.stop();
        assertEquals(0, foreignControl.stopCalls);
    }

    @Test
    void soundObserverUnregistersAndPreservesPrimaryErrorIdentity() {
        var bus = BusBuilder.builder().build();
        var replacement = simpleSound(
                SOUND_ASSET, SoundSource.MUSIC, 4.0F, 0.25F, 90.0D);
        bus.addListener(
                EventPriority.LOWEST,
                PlaySoundEvent.class,
                event -> event.setSound(replacement));
        var control = new DirectSoundControl(bus, null);
        var primary = new AssertionError("sound play");
        control.playError = primary;
        var handle = MinecraftP8ClientPresentationBackend.soundHandle(
                new ClientProfileFactory.Sound(
                        SOUND_ASSET, 1.0D, 2.0D, 3.0D, 0.6F, 1.25F),
                20L,
                bus,
                control);

        var thrown = assertThrows(AssertionError.class, handle::start);

        assertSame(primary, thrown);
        assertEquals(0, primary.getSuppressed().length);
        handle.stop();
        assertSame(control.played, control.stopped);
        var after = bus.post(new PlaySoundEvent(null, control.original));
        assertSame(replacement, after.getSound());
    }

    @Test
    void soundObserverCleanupCoversPartialRegistrationFailure() {
        var delegate = BusBuilder.builder().build();
        var primary = new AssertionError("partial listener registration");
        var addCalls = new int[1];
        var unregisterCalls = new int[1];
        var bus = faultingEventBus(
                delegate, "addListener", true, primary, addCalls, unregisterCalls);
        var control = new DirectSoundControl(bus, null);
        var handle = MinecraftP8ClientPresentationBackend.soundHandle(
                new ClientProfileFactory.Sound(
                        SOUND_ASSET, 1.0D, 2.0D, 3.0D, 0.6F, 1.25F),
                21L,
                bus,
                control);

        var thrown = assertThrows(AssertionError.class, handle::start);

        assertSame(primary, thrown);
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(1, addCalls[0]);
        assertEquals(1, unregisterCalls[0]);
        assertTrue(control.played == null);
    }

    @Test
    void soundObserverCleanupCoversNormalUnregisterFailure() {
        var delegate = BusBuilder.builder().build();
        var primary = new AssertionError("first listener unregister");
        var addCalls = new int[1];
        var unregisterCalls = new int[1];
        var bus = faultingEventBus(
                delegate, "unregister", false, primary, addCalls, unregisterCalls);
        var control = new DirectSoundControl(bus, null);
        var handle = MinecraftP8ClientPresentationBackend.soundHandle(
                new ClientProfileFactory.Sound(
                        SOUND_ASSET, 1.0D, 2.0D, 3.0D, 0.6F, 1.25F),
                22L,
                bus,
                control);

        var thrown = assertThrows(AssertionError.class, handle::start);

        assertSame(primary, thrown);
        assertEquals(1, addCalls[0]);
        assertEquals(2, unregisterCalls[0]);
        assertSame(control.played, control.active);
    }

    @Test
    void typedCatalogHandoffInvokesAllFactoriesAndActualBackendSeams() {
        var soundFactory = soundFactory((configuration, input, output) -> {
            output.sound(sound(input, 0.75F, 1.25F));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var particleFactory = particleFactory((configuration, input, output) -> {
            output.particle(particle(input, 0xff123456, 5));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var trailFactory = trailFactory((configuration, input, output) -> {
            output.trail(new ClientProfileFactory.Trail(
                    configuration.particle(),
                    input.targetEntityId(),
                    input.x(),
                    input.y(),
                    input.z(),
                    0xffabcdef,
                    0.25F,
                    7,
                    2));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.trackedPositions.put(22, new P8ClientPosition(9.0D, 8.0D, 7.0D));
        var execution = execution(soundFactory, particleFactory, trailFactory, backend);
        var catalog = catalog();

        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var payload = event(
                Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                Optional.of(P8S2TestFixtures.DEFAULT_TRAIL_ID),
                1L);
        var result = execution.present(
                payload,
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.PRESENTED, result);
        assertEquals(3L, execution.factoryCallsThisTick());
        assertSame(DEFAULT_SOUND, soundFactory.presentedConfigurations.get(0));
        assertSame(DEFAULT_PARTICLE, particleFactory.presentedConfigurations.get(0));
        assertSame(DEFAULT_TRAIL, trailFactory.presentedConfigurations.get(0));
        assertSame(soundFactory.presentedInputs.get(0), particleFactory.presentedInputs.get(0));
        assertSame(soundFactory.presentedInputs.get(0), trailFactory.presentedInputs.get(0));
        assertEquals(List.of(payload), backend.recipientPayloads);

        var input = soundFactory.presentedInputs.get(0);
        assertEquals(PresentationEventKind.CAST_RELEASE.wireCode(), input.eventKindCode());
        assertEquals(OptionalInt.of(11), input.sourceEntityId());
        assertEquals(OptionalInt.of(22), input.targetEntityId());
        assertEquals(1.0D, input.x());
        assertEquals(2.0D, input.y());
        assertEquals(3.0D, input.z());
        assertEquals(1.0F, input.directionX());
        assertEquals(0.0F, input.directionY());
        assertEquals(0.0F, input.directionZ());
        assertEquals(0xff010203, input.primaryArgb());
        assertEquals(0xffa0b0c0, input.secondaryArgb());
        assertEquals(1_000, input.intensity());
        assertEquals(OptionalInt.of(37), input.override(PARAMETER_ID));
        assertEquals(0x1020304050607080L, input.visualSeed());
        assertEquals(1L, input.sequence());

        assertEquals(
                List.of(new StartedSound(sound(input, 0.75F, 1.25F), input.visualSeed())),
                backend.sounds);
        assertEquals(2, backend.particles.size());
        assertEquals(particle(input, 0xff123456, 5), backend.particles.get(0));
        assertEquals(9.0D, backend.particles.get(1).x());
        assertEquals(8.0D, backend.particles.get(1).y());
        assertEquals(7.0D, backend.particles.get(1).z());
        assertEquals(0xffabcdef, backend.particles.get(1).argb());
        assertEquals(1L, execution.activeSoundHandles());
        assertEquals(1L, execution.activeTrails());
        assertEquals(1L, execution.totalTrailSegments());
        assertEquals(2L, execution.liveParticleCredits());
        assertEquals(3, execution.activePresentationCount());

        execution.clearActive();
        execution.clearActive();

        assertEquals(1, backend.handles.get(0).stopCalls);
        assertFalse(backend.handles.get(0).active);
        assertEquals(0L, execution.activeSoundHandles());
        assertEquals(0L, execution.activeTrails());
        assertEquals(0L, execution.totalTrailSegments());
        assertEquals(0L, execution.liveParticleCredits());
        assertEquals(0, execution.activePresentationCount());
    }

    @Test
    void exactFactoryKeyIdentityIsCheckedBeforeTypedFactoryInvocation() {
        var soundFactory = soundFactory((configuration, input, output) ->
                ClientProfileFactory.Result.PRESENTED);
        var particleFactory = idleParticleFactory();
        var trailFactory = idleTrailFactory();
        var registries = new FakeRegistryView();
        registries.registerMismatched(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.SOUND_TYPE,
                soundFactory);
        registries.register(
                P8S2TestFixtures.PARTICLE_TYPE_ID,
                P8S2TestFixtures.PARTICLE_TYPE,
                particleFactory);
        registries.register(
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                trailFactory);
        var execution = new P8ClientPresentationExecution(registries, new FakeBackend());
        var catalog = catalog();

        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var result = execution.present(
                event(
                        Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                        Optional.empty(),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.UNAVAILABLE, result);
        assertEquals(0, soundFactory.availabilityConfigurations.size());
        assertEquals(0, soundFactory.presentedConfigurations.size());
        assertEquals(0L, execution.factoryCallsThisTick());
        assertTrue(execution.diagnosticCount() >= 1);
    }

    @Test
    void selectedRuntimeExceptionFallsBackOnceAndChargesBothCalls() {
        var selected = new P8S2TestFixtures.SoundConfiguration(SOUND_ASSET, 321, 1_000);
        var expectedFailure = new IllegalStateException("selected");
        var soundFactory = soundFactory((configuration, input, output) -> {
            if (configuration == selected) {
                throw expectedFailure;
            }
            output.sound(sound(input, 0.6F, 1.0F));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        var execution = execution(
                soundFactory, idleParticleFactory(), idleTrailFactory(), backend);
        var catalog = catalog(entry(
                SELECTED_SOUND_ID,
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.SOUND_TYPE,
                selected,
                soundJson(321)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var result = execution.present(
                event(Optional.of(SELECTED_SOUND_ID), Optional.empty(), Optional.empty(), 1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.PRESENTED, result);
        assertEquals(2L, execution.factoryCallsThisTick());
        assertEquals(2, soundFactory.presentedConfigurations.size());
        assertSame(selected, soundFactory.presentedConfigurations.get(0));
        assertSame(DEFAULT_SOUND, soundFactory.presentedConfigurations.get(1));
        assertEquals(1, backend.sounds.size());
        assertEquals(1, execution.diagnosticCount());
    }

    @Test
    void failedDefaultStopsFallbackWithoutRecursionOrRetry() {
        var selected = new P8S2TestFixtures.SoundConfiguration(SOUND_ASSET, 321, 1_000);
        var soundFactory = soundFactory((configuration, input, output) ->
                ClientProfileFactory.Result.UNAVAILABLE);
        var execution = execution(
                soundFactory, idleParticleFactory(), idleTrailFactory(), new FakeBackend());
        var catalog = catalog(entry(
                SELECTED_SOUND_ID,
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.SOUND_TYPE,
                selected,
                soundJson(321)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var result = execution.present(
                event(Optional.of(SELECTED_SOUND_ID), Optional.empty(), Optional.empty(), 1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.UNAVAILABLE, result);
        assertEquals(2L, execution.factoryCallsThisTick());
        assertEquals(2, soundFactory.presentedConfigurations.size());
        assertSame(selected, soundFactory.presentedConfigurations.get(0));
        assertSame(DEFAULT_SOUND, soundFactory.presentedConfigurations.get(1));
    }

    @Test
    void partialParticleWorkSurvivesFactoryRuntimeExceptionUntilBoundedCleanup() {
        var expectedFailure = new IllegalArgumentException("factory");
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertTrue(output.particle(particle(input, 0xff000001, 3)));
            throw expectedFailure;
        });
        var backend = new FakeBackend();
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var result = execution.present(
                event(
                        Optional.empty(),
                        Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.PARTIALLY_PRESENTED, result);
        assertEquals(1, backend.particles.size());
        assertEquals(1L, execution.liveParticleCredits());
        assertEquals(1, execution.activePresentationCount());
        assertEquals(1L, execution.factoryCallsThisTick());

        execution.clearActive();
        assertEquals(0L, execution.liveParticleCredits());
        assertEquals(0, execution.activePresentationCount());
    }

    @Test
    void partialParticleWorkSurvivesBackendRuntimeExceptionUntilBoundedCleanup() {
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertTrue(output.particle(particle(input, 0xff000001, 3)));
            assertFalse(output.particle(particle(input, 0xff000002, 3)));
            assertFalse(output.particle(particle(input, 0xff000003, 3)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.particleRuntimeFailureAtAttempt = 2;
        backend.particleRuntimeFailure = new IllegalStateException("backend");
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var result = execution.present(
                event(
                        Optional.empty(),
                        Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.PARTIALLY_PRESENTED, result);
        assertEquals(2, backend.particlePrepareCalls);
        assertEquals(1, backend.particles.size());
        assertEquals(2L, execution.particleStartsThisTick());
        assertEquals(2L, execution.liveParticleCredits());
        assertEquals(1, execution.activePresentationCount());
        assertEquals(1, execution.diagnosticCount());

        execution.clearAll();
        assertEquals(0L, execution.liveParticleCredits());
        assertEquals(0, execution.activePresentationCount());
    }

    @Test
    void backendRuntimeDropsSelectedInstanceWithoutFactoryFallback() {
        var selected = new P8S2TestFixtures.ParticleConfiguration(
                PARTICLE_ASSET, 1, 0, 250, 3);
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertFalse(output.particle(particle(input, 0xff000001, 3)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.particleRuntimeFailureAtAttempt = 1;
        backend.particleRuntimeFailure = new IllegalStateException("backend");
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), backend);
        var catalog = catalog(entry(
                SELECTED_PARTICLE_ID,
                P8S2TestFixtures.PARTICLE_TYPE_ID,
                P8S2TestFixtures.PARTICLE_TYPE,
                selected,
                particleJson(1, 3)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var result = execution.present(
                event(
                        Optional.empty(),
                        Optional.of(SELECTED_PARTICLE_ID),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.PARTIALLY_PRESENTED, result);
        assertEquals(1L, execution.factoryCallsThisTick());
        assertEquals(List.of(selected), particleFactory.presentedConfigurations);
        assertEquals(1, backend.particlePrepareCalls);
        assertEquals(1L, execution.liveParticleCredits());
        execution.clearAll();
    }

    @Test
    void backendRuntimeCannotBeReclassifiedByALaterFactoryRuntime() {
        var selected = new P8S2TestFixtures.ParticleConfiguration(
                PARTICLE_ASSET, 1, 0, 250, 3);
        var factoryFailure = new IllegalArgumentException("factory-after-backend");
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertFalse(output.particle(particle(input, 0xff000001, 3)));
            throw factoryFailure;
        });
        var backend = new FakeBackend();
        backend.particleRuntimeFailureAtAttempt = 1;
        backend.particleRuntimeFailure = new IllegalStateException("backend");
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), backend);
        var catalog = catalog(entry(
                SELECTED_PARTICLE_ID,
                P8S2TestFixtures.PARTICLE_TYPE_ID,
                P8S2TestFixtures.PARTICLE_TYPE,
                selected,
                particleJson(1, 3)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var result = execution.present(
                event(
                        Optional.empty(),
                        Optional.of(SELECTED_PARTICLE_ID),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(P8ClientPresentationHandoffResult.PARTIALLY_PRESENTED, result);
        assertEquals(1L, execution.factoryCallsThisTick());
        assertEquals(List.of(selected), particleFactory.presentedConfigurations);
        assertEquals(1, backend.particlePrepareCalls);
        assertEquals(1L, execution.liveParticleCredits());
        assertEquals(1, execution.diagnosticCount());
        execution.clearAll();
    }

    @Test
    void preparedParticleStartRuntimeRetainsOneLPlusTwoCreditWhileNullPrepareChargesNothing() {
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertFalse(output.particle(particle(input, 0xff010203, 3)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var throwingBackend = new FakeBackend();
        throwingBackend.particleRuntimeFailureAtAttempt = 1;
        throwingBackend.particleRuntimeFailure = new IllegalStateException("prepared start");
        var throwingExecution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), throwingBackend);
        var catalog = catalog();
        throwingExecution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(
                P8ClientPresentationHandoffResult.PARTIALLY_PRESENTED,
                throwingExecution.present(
                        event(
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                                Optional.empty(),
                                1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(1, throwingBackend.particlePrepareCalls);
        assertEquals(1, throwingBackend.preparedParticleStartCalls);
        assertTrue(throwingBackend.particles.isEmpty());
        assertEquals(1L, throwingExecution.particleStartsThisTick());
        assertEquals(1L, throwingExecution.liveParticleCredits());
        assertEquals(1, throwingExecution.activePresentationCount());

        tick(throwingExecution, CATALOG_GENERATION);
        tick(throwingExecution, CATALOG_GENERATION);
        assertEquals(1, throwingExecution.activePresentationCount());
        tick(throwingExecution, CATALOG_GENERATION);
        assertEquals(0, throwingExecution.activePresentationCount());
        assertEquals(1L, throwingExecution.liveParticleCredits());
        tick(throwingExecution, CATALOG_GENERATION);
        assertEquals(1L, throwingExecution.liveParticleCredits());
        tick(throwingExecution, CATALOG_GENERATION);
        assertEquals(0L, throwingExecution.liveParticleCredits());

        var nullBackend = new FakeBackend();
        nullBackend.particleStartResult = false;
        var nullExecution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), nullBackend);
        nullExecution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                nullExecution.present(
                        event(
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                                Optional.empty(),
                                1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(1, nullBackend.particlePrepareCalls);
        assertEquals(0, nullBackend.preparedParticleStartCalls);
        assertEquals(0L, nullExecution.particleStartsThisTick());
        assertEquals(0L, nullExecution.liveParticleCredits());
        assertEquals(0, nullExecution.activePresentationCount());
    }

    @Test
    void primaryErrorIdentitySurvivesPartialSoundCleanupAndSecondaryError() {
        var primary = new AssertionError("primary");
        var secondary = new AssertionError("secondary cleanup");
        var soundFactory = soundFactory((configuration, input, output) -> {
            assertTrue(output.sound(sound(input, 0.5F, 1.0F)));
            throw primary;
        });
        var backend = new FakeBackend();
        backend.nextSoundStopError = secondary;
        var execution = execution(
                soundFactory, idleParticleFactory(), idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var thrown = assertThrows(AssertionError.class, () -> execution.present(
                event(
                        Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                        Optional.empty(),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION));

        assertSame(primary, thrown);
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(1, backend.handles.size());
        assertEquals(1, backend.handles.get(0).stopCalls);
        assertFalse(backend.handles.get(0).active);
        assertEquals(0L, execution.activeSoundHandles());
        assertEquals(0, execution.activePresentationCount());
        assertEquals(1L, execution.factoryCallsThisTick());
    }

    @Test
    void stopErrorWithLiveSoundStaysPendingUntilTickObservesInactive() throws Exception {
        var primary = new AssertionError("primary");
        var secondary = new AssertionError("sound remains active");
        var soundFactory = soundFactory((configuration, input, output) -> {
            assertTrue(output.sound(sound(input, 0.5F, 1.0F)));
            throw primary;
        });
        var backend = new FakeBackend();
        backend.nextSoundStopError = secondary;
        backend.nextSoundStopErrorLeavesActive = true;
        var execution = execution(
                soundFactory, idleParticleFactory(), idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var thrown = assertThrows(AssertionError.class, () -> execution.present(
                event(
                        Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                        Optional.empty(),
                        Optional.empty(),
                        1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION));

        assertSame(primary, thrown);
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(1, backend.handles.size());
        assertEquals(1, backend.handles.getFirst().stopCalls);
        assertTrue(backend.handles.getFirst().active);
        assertEquals(1L, execution.activeSoundHandles());
        assertEquals(0, execution.activePresentationCount());
        var pendingSoundStops = P8ClientPresentationExecution.class.getDeclaredField(
                "pendingSoundStops");
        pendingSoundStops.setAccessible(true);
        assertEquals(1, ((List<?>) pendingSoundStops.get(execution)).size());

        backend.handles.getFirst().active = false;
        tick(execution, CATALOG_GENERATION);

        assertEquals(1, backend.handles.getFirst().stopCalls);
        assertEquals(0L, execution.activeSoundHandles());
        assertEquals(0, execution.activePresentationCount());
        assertTrue(((List<?>) pendingSoundStops.get(execution)).isEmpty());
    }

    @Test
    void particleCreditReleaseRemainsConservativeAfterLogicalCompletion() {
        var particleFactory = particleFactory((configuration, input, output) -> {
            output.particle(particle(input, 0xff112233, 3));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), new FakeBackend());
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                                Optional.empty(),
                                1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));

        assertEquals(1L, execution.liveParticleCredits());
        assertEquals(1, execution.activePresentationCount());
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        assertEquals(1, execution.activePresentationCount());
        tick(execution, CATALOG_GENERATION);
        assertEquals(0, execution.activePresentationCount());
        assertEquals(1L, execution.liveParticleCredits());
        tick(execution, CATALOG_GENERATION);
        assertEquals(1L, execution.liveParticleCredits());
        tick(execution, CATALOG_GENERATION);
        assertEquals(0L, execution.liveParticleCredits());
    }

    @Test
    void catalogReplacementClearsOwnersButPreservesParticleCreditUntilOriginalExpiry() {
        var particleFactory = particleFactory((configuration, input, output) -> {
            output.particle(particle(input, 0xff112233, 3));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), new FakeBackend());
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                                Optional.empty(),
                                1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(1, execution.activePresentationCount());
        assertEquals(1L, execution.liveParticleCredits());

        execution.replaceCatalog(
                catalog(),
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(0, execution.activePresentationCount());
        assertEquals(1L, execution.liveParticleCredits());
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        assertEquals(1L, execution.liveParticleCredits());
        tick(execution, CATALOG_GENERATION);
        assertEquals(0L, execution.liveParticleCredits());
    }

    @Test
    void particlePreferencesAreDeterministicForFullReducedAndMinimal() {
        assertEquals(
                List.of(
                        0xff000000,
                        0xff000001,
                        0xff000002,
                        0xff000003,
                        0xff000004,
                        0xff000005,
                        0xff000006,
                        0xff000007),
                acceptedParticleColors(P8ClientParticlePreference.FULL));
        assertEquals(
                List.of(0xff000001, 0xff000003, 0xff000005, 0xff000007),
                acceptedParticleColors(P8ClientParticlePreference.REDUCED));
        assertEquals(
                List.of(0xff000003, 0xff000007),
                acceptedParticleColors(P8ClientParticlePreference.MINIMAL));
        assertEquals(
                acceptedParticleColors(P8ClientParticlePreference.MINIMAL),
                acceptedParticleColors(P8ClientParticlePreference.MINIMAL));
    }

    @Test
    void particlePreferenceAtLongMaxSequenceUsesBoundedModuloWithoutOverflow() {
        assertEquals(
                List.of(0xff000001, 0xff000003, 0xff000005, 0xff000007),
                acceptedParticleColors(
                        P8ClientParticlePreference.REDUCED, Long.MAX_VALUE));
        assertEquals(
                List.of(0xff000001, 0xff000005),
                acceptedParticleColors(
                        P8ClientParticlePreference.MINIMAL, Long.MAX_VALUE));
    }

    @Test
    void trailSamplingUsesExactQCapacityAndLifecycle() {
        var selected = new P8S2TestFixtures.TrailConfiguration(
                PARTICLE_ASSET, 3, 2, 200, 7);
        var trailFactory = trailFactory((configuration, input, output) -> {
            output.trail(new ClientProfileFactory.Trail(
                    configuration.particle(),
                    OptionalInt.empty(),
                    input.x(),
                    input.y(),
                    input.z(),
                    input.primaryArgb(),
                    configuration.sizeMilliBlocks() / 1_000.0F,
                    configuration.lifetimeTicks(),
                    configuration.sampleIntervalTicks()));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        var execution = execution(
                idleSoundFactory(), idleParticleFactory(), trailFactory, backend);
        var catalog = catalog(entry(
                SELECTED_TRAIL_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                selected,
                trailJson(3, 2, 7)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(Optional.empty(), Optional.empty(), Optional.of(SELECTED_TRAIL_ID), 1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(1, backend.particles.size());
        assertEquals(1L, execution.totalTrailSegments());
        assertEquals(1L, execution.activeTrails());

        tick(execution, CATALOG_GENERATION);
        assertEquals(1L, execution.totalTrailSegments());
        tick(execution, CATALOG_GENERATION);
        assertEquals(2L, execution.totalTrailSegments());
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        assertEquals(3L, execution.totalTrailSegments());
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        assertEquals(3L, execution.totalTrailSegments());
        assertEquals(4, backend.particles.size());
        assertEquals(4L, execution.liveParticleCredits());

        tick(execution, CATALOG_GENERATION);
        assertEquals(0L, execution.activeTrails());
        assertEquals(0L, execution.totalTrailSegments());
        assertEquals(0, execution.activePresentationCount());
        assertEquals(4L, execution.liveParticleCredits());
        tick(execution, CATALOG_GENERATION);
        assertEquals(4L, execution.liveParticleCredits());
        tick(execution, CATALOG_GENERATION);
        assertEquals(0L, execution.liveParticleCredits());
    }

    @Test
    void trailHistoryIsRetainedWhenTheSamplerParticleIsRejected() {
        var selected = new P8S2TestFixtures.TrailConfiguration(
                PARTICLE_ASSET, 4, 1, 200, 5);
        var trailFactory = trailFactory((configuration, input, output) -> {
            assertTrue(output.trail(trail(configuration, input)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.particleStartResult = false;
        var execution = execution(
                idleSoundFactory(), idleParticleFactory(), trailFactory, backend);
        var catalog = catalog(entry(
                SELECTED_TRAIL_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                selected,
                trailJson(4, 1, 5)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(Optional.empty(), Optional.empty(), Optional.of(SELECTED_TRAIL_ID), 1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));

        assertEquals(1, backend.particlePrepareCalls);
        assertEquals(0, backend.particles.size());
        assertEquals(0L, execution.particleStartsThisTick());
        assertEquals(0L, execution.liveParticleCredits());
        assertEquals(1L, execution.activeTrails());
        assertEquals(1L, execution.totalTrailSegments());
        assertEquals(1, execution.activePresentationCount());
        assertEquals(1, execution.trailRenderSnapshot().strips().size());
        assertEquals(1, execution.trailRenderSnapshot().strips().getFirst().positions().size());

        execution.clearActive();
        assertEquals(0L, execution.activeTrails());
        assertEquals(0L, execution.totalTrailSegments());
        assertTrue(execution.trailRenderSnapshot().strips().isEmpty());
    }

    @Test
    void renderErrorCleansExactTrailSnapshotAndReturnsTheSamePrimary() {
        var trailFactory = trailFactory((configuration, input, output) -> {
            assertTrue(output.trail(trail(configuration, input)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.particleStartResult = false;
        var execution = execution(
                idleSoundFactory(), idleParticleFactory(), trailFactory, backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_TRAIL_ID),
                                1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        var snapshot = execution.trailRenderSnapshot();
        var primary = new AssertionError("render");

        var thrown = assertThrows(
                AssertionError.class,
                () -> {
                    throw execution.renderErrorAfterCleanup(snapshot, primary);
                });

        assertSame(primary, thrown);
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(0L, execution.activeTrails());
        assertEquals(0L, execution.totalTrailSegments());
        assertEquals(0, execution.activePresentationCount());
        assertTrue(execution.trailRenderSnapshot().strips().isEmpty());
    }

    @Test
    void initialTrailSamplerErrorPreservesIdentityAndCleansTrailOwnership() {
        var trailFactory = trailFactory((configuration, input, output) -> {
            output.trail(trail(configuration, input));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var primary = new AssertionError("trail particle start");
        var backend = new FakeBackend();
        backend.particleRuntimeFailureAtAttempt = 1;
        backend.particleStartError = primary;
        var execution = execution(
                idleSoundFactory(), idleParticleFactory(), trailFactory, backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        var thrown = assertThrows(
                AssertionError.class,
                () -> execution.present(
                        event(
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_TRAIL_ID),
                                1L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));

        assertSame(primary, thrown);
        assertEquals(0, primary.getSuppressed().length);
        assertEquals(0L, execution.activeTrails());
        assertEquals(0L, execution.totalTrailSegments());
        assertEquals(0, execution.activePresentationCount());
        assertEquals(1L, execution.liveParticleCredits());
        execution.clearAll();
        assertEquals(0L, execution.liveParticleCredits());
    }

    @Test
    void untrackedEntityUnavailableMissAvoidsResolutionAndLeavesTrailOwned() {
        var selected = new P8S2TestFixtures.TrailConfiguration(
                PARTICLE_ASSET, 4, 2, 200, 8);
        var trailFactory = trailFactory((configuration, input, output) -> {
            assertTrue(output.trail(new ClientProfileFactory.Trail(
                    configuration.particle(),
                    OptionalInt.of(22),
                    input.x(),
                    input.y(),
                    input.z(),
                    input.primaryArgb(),
                    0.2F,
                    configuration.lifetimeTicks(),
                    configuration.sampleIntervalTicks())));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.trackedPositions.put(22, new P8ClientPosition(4.0D, 5.0D, 6.0D));
        var execution = execution(
                idleSoundFactory(), idleParticleFactory(), trailFactory, backend);
        var catalog = catalog(entry(
                SELECTED_TRAIL_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                selected,
                trailJson(4, 2, 8)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        execution.present(
                event(Optional.empty(), Optional.empty(), Optional.of(SELECTED_TRAIL_ID), 1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        assertEquals(1, backend.trackedPositionCalls);

        execution.onEntityUnavailable(999);

        assertEquals(1, backend.trackedPositionCalls);
        assertEquals(1L, execution.activeTrails());
        assertEquals(1, execution.activePresentationCount());

        execution.onEntityUnavailable(22);
        assertEquals(1, backend.trackedPositionCalls);
        assertEquals(0L, execution.activeTrails());
        assertEquals(0L, execution.totalTrailSegments());
        assertEquals(1, execution.activePresentationCount());
        assertEquals(1L, execution.liveParticleCredits());
        execution.clearAll();
        assertEquals(0L, execution.liveParticleCredits());
    }

    @Test
    void stationaryTrailHistoryCompactsToOneRenderPosition() {
        var selected = new P8S2TestFixtures.TrailConfiguration(
                PARTICLE_ASSET, 4, 1, 200, 5);
        var trailFactory = trailFactory((configuration, input, output) -> {
            assertTrue(output.trail(trail(configuration, input)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        var execution = execution(
                idleSoundFactory(), idleParticleFactory(), trailFactory, backend);
        var catalog = catalog(entry(
                SELECTED_TRAIL_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                selected,
                trailJson(4, 1, 5)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        execution.present(
                event(Optional.empty(), Optional.empty(), Optional.of(SELECTED_TRAIL_ID), 1L),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);
        tick(execution, CATALOG_GENERATION);

        assertEquals(4L, execution.totalTrailSegments());
        assertEquals(4, backend.particles.size());
        assertEquals(1, execution.trailRenderSnapshot().strips().size());
        assertEquals(
                List.of(new P8ClientPosition(1.0D, 2.0D, 3.0D)),
                execution.trailRenderSnapshot().strips().getFirst().positions());

        execution.clearActive();
        assertEquals(0L, execution.totalTrailSegments());
        assertTrue(execution.trailRenderSnapshot().strips().isEmpty());
    }

    @Test
    void actualParticleStartAndLiveCreditBudgetsAreIndependentAndBounded() {
        var selected = new P8S2TestFixtures.ParticleConfiguration(
                PARTICLE_ASSET, 256, 0, 250, 120);
        var particleFactory = particleFactory((configuration, input, output) -> {
            for (var index = 0; index < configuration.count(); index++) {
                output.particle(particle(input, 0xff000000 | index, 120));
            }
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), backend);
        var catalog = catalog(entry(
                SELECTED_PARTICLE_ID,
                P8S2TestFixtures.PARTICLE_TYPE_ID,
                P8S2TestFixtures.PARTICLE_TYPE,
                selected,
                particleJson(256, 120)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var event = event(
                Optional.empty(), Optional.of(SELECTED_PARTICLE_ID), Optional.empty(), 1L);

        for (var round = 0; round < 8; round++) {
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    execution.present(
                            event,
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    execution.present(
                            event,
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
            assertEquals(512L, execution.particleStartsThisTick());
            tick(execution, CATALOG_GENERATION);
        }

        assertEquals(4_096, backend.particles.size());
        assertEquals(4_096L, execution.liveParticleCredits());
        assertEquals(16, execution.activePresentationCount());
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event,
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(0L, execution.particleStartsThisTick());
        assertEquals(4_096, backend.particles.size());

        execution.clearActive();
        assertEquals(0L, execution.liveParticleCredits());
        assertEquals(0, execution.activePresentationCount());
    }

    @Test
    void soundPerTickAndActiveHandleBudgetsRemainIndependent() {
        var soundFactory = soundFactory((configuration, input, output) -> {
            output.sound(sound(input, 0.5F, 1.0F));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        var execution = execution(
                soundFactory, idleParticleFactory(), idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var event = event(
                Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                Optional.empty(),
                Optional.empty(),
                1L);

        for (var round = 0; round < 4; round++) {
            for (var attempt = 0; attempt < 9; attempt++) {
                assertEquals(
                        P8ClientPresentationHandoffResult.PRESENTED,
                        execution.present(
                                event,
                                catalog,
                                P8ClientResourceIndex.empty(),
                                CONNECTION_GENERATION,
                                WORLD_GENERATION,
                                RESOURCE_GENERATION));
            }
            assertEquals(8L, execution.soundStartsThisTick());
            if (round < 3) {
                tick(execution, CATALOG_GENERATION);
            }
        }

        assertEquals(32, backend.sounds.size());
        assertEquals(32L, execution.activeSoundHandles());
        assertEquals(32, execution.activePresentationCount());
        tick(execution, CATALOG_GENERATION);
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event,
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(0L, execution.soundStartsThisTick());
        assertEquals(32, backend.sounds.size());

        execution.clearActive();
        assertEquals(0L, execution.activeSoundHandles());
        assertEquals(0, execution.activePresentationCount());
        assertTrue(backend.handles.stream().allMatch(handle -> handle.stopCalls == 1));
    }

    @Test
    void activeTrailAndTotalSegmentBudgetsCloseAtTheirExactLimits() {
        var activeLimitConfiguration = new P8S2TestFixtures.TrailConfiguration(
                PARTICLE_ASSET, 1, 120, 200, 120);
        var activeLimitFactory = trailFactory((configuration, input, output) -> {
            output.trail(trail(configuration, input));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var activeLimitBackend = new FakeBackend();
        var activeLimitExecution = execution(
                idleSoundFactory(),
                idleParticleFactory(),
                activeLimitFactory,
                activeLimitBackend);
        var activeLimitCatalog = catalog(entry(
                SELECTED_TRAIL_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                activeLimitConfiguration,
                trailJson(1, 120, 120)));
        activeLimitExecution.replaceCatalog(
                activeLimitCatalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var activeLimitEvent = event(
                Optional.empty(), Optional.empty(), Optional.of(SELECTED_TRAIL_ID), 1L);

        for (var index = 0; index < 64; index++) {
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    activeLimitExecution.present(
                            activeLimitEvent,
                            activeLimitCatalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
        }
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                activeLimitExecution.present(
                        activeLimitEvent,
                        activeLimitCatalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(64L, activeLimitExecution.activeTrails());
        assertEquals(64L, activeLimitExecution.totalTrailSegments());
        assertEquals(64, activeLimitExecution.activePresentationCount());
        assertEquals(64, activeLimitBackend.particles.size());
        activeLimitExecution.clearActive();
        assertEquals(0L, activeLimitExecution.activeTrails());
        assertEquals(0L, activeLimitExecution.totalTrailSegments());

        var segmentLimitConfiguration = new P8S2TestFixtures.TrailConfiguration(
                PARTICLE_ASSET, 48, 1, 200, 120);
        var segmentLimitFactory = trailFactory((configuration, input, output) -> {
            output.trail(trail(configuration, input));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var segmentLimitBackend = new FakeBackend();
        var segmentLimitExecution = execution(
                idleSoundFactory(),
                idleParticleFactory(),
                segmentLimitFactory,
                segmentLimitBackend);
        var segmentLimitCatalog = catalog(entry(
                SELECTED_TRAIL_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                segmentLimitConfiguration,
                trailJson(48, 1, 120)));
        segmentLimitExecution.replaceCatalog(
                segmentLimitCatalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var segmentLimitEvent = event(
                Optional.empty(), Optional.empty(), Optional.of(SELECTED_TRAIL_ID), 1L);
        for (var index = 0; index < 32; index++) {
            segmentLimitExecution.present(
                    segmentLimitEvent,
                    segmentLimitCatalog,
                    P8ClientResourceIndex.empty(),
                    CONNECTION_GENERATION,
                    WORLD_GENERATION,
                    RESOURCE_GENERATION);
        }
        for (var age = 1; age < 48; age++) {
            tick(segmentLimitExecution, CATALOG_GENERATION);
        }
        assertEquals(32L, segmentLimitExecution.activeTrails());
        assertEquals(1_536L, segmentLimitExecution.totalTrailSegments());
        assertEquals(1_536, segmentLimitBackend.particles.size());

        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                segmentLimitExecution.present(
                        segmentLimitEvent,
                        segmentLimitCatalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(33L, segmentLimitExecution.activeTrails());
        assertEquals(1_536L, segmentLimitExecution.totalTrailSegments());
        assertEquals(1_537, segmentLimitBackend.particles.size());
        segmentLimitExecution.clearActive();
        assertEquals(0L, segmentLimitExecution.activeTrails());
        assertEquals(0L, segmentLimitExecution.totalTrailSegments());
        assertEquals(0L, segmentLimitExecution.liveParticleCredits());
    }

    @Test
    void factoryAndActivePresentationBudgetsCloseAtTheirExactLimits() {
        var noWorkFactory = soundFactory((configuration, input, output) ->
                ClientProfileFactory.Result.PRESENTED);
        var noWorkExecution = execution(
                noWorkFactory, idleParticleFactory(), idleTrailFactory(), new FakeBackend());
        var catalog = catalog();
        noWorkExecution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var soundEvent = event(
                Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                Optional.empty(),
                Optional.empty(),
                1L);
        for (var index = 0; index < 192; index++) {
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    noWorkExecution.present(
                            soundEvent,
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
        }
        assertEquals(
                P8ClientPresentationHandoffResult.UNAVAILABLE,
                noWorkExecution.present(
                        soundEvent,
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(192L, noWorkExecution.factoryCallsThisTick());
        assertEquals(192, noWorkFactory.presentedConfigurations.size());
        assertEquals(0, noWorkExecution.activePresentationCount());

        var particleFactory = particleFactory((configuration, input, output) -> {
            output.particle(particle(input, 0xff112233, 20));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var activeExecution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), new FakeBackend());
        activeExecution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        var particleEvent = event(
                Optional.empty(),
                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                Optional.empty(),
                1L);
        for (var index = 0; index < 128; index++) {
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    activeExecution.present(
                            particleEvent,
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
        }
        assertEquals(
                P8ClientPresentationHandoffResult.UNAVAILABLE,
                activeExecution.present(
                        particleEvent,
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(128, activeExecution.activePresentationCount());
        assertEquals(128L, activeExecution.factoryCallsThisTick());
        assertEquals(128L, activeExecution.liveParticleCredits());

        activeExecution.clearActive();
        assertEquals(0, activeExecution.activePresentationCount());
        assertEquals(0L, activeExecution.liveParticleCredits());
    }

    @Test
    void activeCapacityDropCannotInvokeLexicallyBetterFallbackOrEvictForNoWork() {
        var middle = new P8S2TestFixtures.ParticleConfiguration(
                PARTICLE_ASSET, 1, 0, 250, 20);
        var selected = new P8S2TestFixtures.ParticleConfiguration(
                PARTICLE_ASSET, 1, 0, 250, 20);
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertTrue(output.particle(particle(input, 0xff112233, 20)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.recipientContext = new P8ClientRecipientContext(
                PresentationOrdering.RecipientCategory.ORDINARY,
                PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED);
        var execution = execution(
                idleSoundFactory(), particleFactory, idleTrailFactory(), backend);
        var catalog = catalog(
                entry(
                        MIDDLE_PARTICLE_ID,
                        P8S2TestFixtures.PARTICLE_TYPE_ID,
                        P8S2TestFixtures.PARTICLE_TYPE,
                        middle,
                        particleJson(1, 20)),
                entry(
                        LAST_PARTICLE_ID,
                        P8S2TestFixtures.PARTICLE_TYPE_ID,
                        P8S2TestFixtures.PARTICLE_TYPE,
                        selected,
                        particleJson(1, 20)));
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        for (var sequence = 1L; sequence <= 128L; sequence++) {
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    execution.present(
                            event(
                                    Optional.empty(),
                                    Optional.of(MIDDLE_PARTICLE_ID),
                                    Optional.empty(),
                                    sequence),
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
        }

        assertEquals(
                P8ClientPresentationHandoffResult.UNAVAILABLE,
                execution.present(
                        event(
                                Optional.empty(),
                                Optional.of(LAST_PARTICLE_ID),
                                Optional.empty(),
                                128L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(128L, execution.factoryCallsThisTick());
        assertEquals(128, particleFactory.presentedConfigurations.size());
        assertTrue(particleFactory.presentedConfigurations.stream()
                .allMatch(configuration -> configuration == middle));
        assertEquals(128, execution.activePresentationCount());
        assertEquals(128L, execution.liveParticleCredits());
        assertEquals(0, execution.retainedEvictionTargetCount());

        for (var index = 0; index < 63; index++) {
            backend.recipientContext = new P8ClientRecipientContext(
                    PresentationOrdering.RecipientCategory.ORDINARY,
                    PresentationLimits.MAX_RECIPIENT_DISTANCE_SQUARED - index - 1.0D);
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    execution.present(
                            event(
                                    Optional.empty(),
                                    Optional.of(MIDDLE_PARTICLE_ID),
                                    Optional.empty(),
                                    129L + index),
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
            assertEquals(128, execution.activePresentationCount());
            assertEquals(0, execution.retainedEvictionTargetCount());
        }

        backend.recipientContext = new P8ClientRecipientContext(
                PresentationOrdering.RecipientCategory.TARGET_SELF, 0.0D);
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(
                                Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                                Optional.empty(),
                                Optional.empty(),
                                129L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(128, execution.activePresentationCount());
        assertEquals(0L, execution.activeSoundHandles());
        assertEquals(192L, execution.factoryCallsThisTick());
        assertEquals(0, execution.retainedEvictionTargetCount());
        execution.clearActive();
    }

    @Test
    void higherPriorityCandidateEvictsTheWorstOfOneHundredTwentyEightOwners() {
        var soundFactory = soundFactory((configuration, input, output) -> {
            assertTrue(output.sound(sound(input, 0.5F, 1.0F)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var particleFactory = particleFactory((configuration, input, output) -> {
            assertTrue(output.particle(particle(input, 0xff112233, 20)));
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.recipientContext = new P8ClientRecipientContext(
                PresentationOrdering.RecipientCategory.ORDINARY, 100.0D);
        var execution = execution(
                soundFactory, particleFactory, idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);

        for (var sequence = 1L; sequence <= 127L; sequence++) {
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    execution.present(
                            event(
                                    Optional.empty(),
                                    Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                                    Optional.empty(),
                                    sequence),
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
        }
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(
                                Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                                Optional.empty(),
                                Optional.empty(),
                                128L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));
        assertEquals(128, execution.activePresentationCount());
        assertEquals(1L, execution.activeSoundHandles());

        backend.recipientContext = new P8ClientRecipientContext(
                PresentationOrdering.RecipientCategory.TARGET_SELF, 0.0D);
        backend.beforeParticleStart =
                () -> assertEquals(127, execution.activePresentationCount());
        assertEquals(
                P8ClientPresentationHandoffResult.PRESENTED,
                execution.present(
                        event(
                                Optional.empty(),
                                Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                                Optional.empty(),
                                129L),
                        catalog,
                        P8ClientResourceIndex.empty(),
                        CONNECTION_GENERATION,
                        WORLD_GENERATION,
                        RESOURCE_GENERATION));

        assertEquals(128, execution.activePresentationCount());
        assertEquals(128L, execution.liveParticleCredits());
        assertEquals(0L, execution.activeSoundHandles());
        assertEquals(1, backend.handles.getFirst().stopCalls);
        assertFalse(backend.handles.getFirst().active);

        execution.clearActive();
        assertEquals(0, execution.activePresentationCount());
        assertEquals(0L, execution.liveParticleCredits());
    }

    @Test
    void everyGenerationMismatchCleansOwnedSoundAndTrailState() {
        var mismatches = new long[][] {
            {CONNECTION_GENERATION + 1L, WORLD_GENERATION, RESOURCE_GENERATION, CATALOG_GENERATION},
            {CONNECTION_GENERATION, WORLD_GENERATION + 1L, RESOURCE_GENERATION, CATALOG_GENERATION},
            {CONNECTION_GENERATION, WORLD_GENERATION, RESOURCE_GENERATION + 1L, CATALOG_GENERATION},
            {CONNECTION_GENERATION, WORLD_GENERATION, RESOURCE_GENERATION, CATALOG_GENERATION + 1L}
        };

        for (var mismatch : mismatches) {
            var soundFactory = soundFactory((configuration, input, output) -> {
                output.sound(sound(input, 0.5F, 1.0F));
                return ClientProfileFactory.Result.PRESENTED;
            });
            var trailFactory = trailFactory((configuration, input, output) -> {
                output.trail(new ClientProfileFactory.Trail(
                        configuration.particle(),
                        OptionalInt.empty(),
                        input.x(),
                        input.y(),
                        input.z(),
                        input.primaryArgb(),
                        0.2F,
                        10,
                        2));
                return ClientProfileFactory.Result.PRESENTED;
            });
            var backend = new FakeBackend();
            var execution = execution(
                    soundFactory, idleParticleFactory(), trailFactory, backend);
            var catalog = catalog();
            execution.replaceCatalog(
                    catalog,
                    P8ClientResourceIndex.empty(),
                    CONNECTION_GENERATION,
                    WORLD_GENERATION,
                    RESOURCE_GENERATION);
            assertEquals(
                    P8ClientPresentationHandoffResult.PRESENTED,
                    execution.present(
                            event(
                                    Optional.of(P8S2TestFixtures.DEFAULT_SOUND_ID),
                                    Optional.empty(),
                                    Optional.of(P8S2TestFixtures.DEFAULT_TRAIL_ID),
                                    1L),
                            catalog,
                            P8ClientResourceIndex.empty(),
                            CONNECTION_GENERATION,
                            WORLD_GENERATION,
                            RESOURCE_GENERATION));
            assertEquals(2, execution.activePresentationCount());
            assertEquals(1L, execution.liveParticleCredits());

            execution.onClientTick(mismatch[0], mismatch[1], mismatch[2], mismatch[3]);

            assertEquals(0, execution.activePresentationCount());
            assertEquals(0L, execution.activeSoundHandles());
            assertEquals(0L, execution.activeTrails());
            assertEquals(0L, execution.totalTrailSegments());
            assertEquals(1, backend.handles.get(0).stopCalls);
            assertEquals(1L, execution.liveParticleCredits());
            execution.clearAll();
            assertEquals(0L, execution.liveParticleCredits());
        }
    }

    @Test
    void resourceReloadGuardClaimsOnlyLatestPrepareExactlyOnceWithoutClientStartup()
            throws Exception {
        var nestedTypes = Arrays.asList(P8ClientPresentationLifecycle.class.getDeclaredClasses());
        var listenerType = nestedTypes.stream()
                .filter(type -> type.getSimpleName().equals("P8ResourceReloadListener"))
                .findFirst()
                .orElseThrow();
        var preparedType = nestedTypes.stream()
                .filter(type -> type.getSimpleName().equals("PreparedResourceIndex"))
                .findFirst()
                .orElseThrow();
        var state = new P8ClientPresentationState(() -> true);
        var constructor = listenerType.getDeclaredConstructor(P8ClientPresentationState.class);
        constructor.setAccessible(true);
        var listener = constructor.newInstance(state);
        var preparedConstructor = preparedType.getDeclaredConstructor(
                Object.class, P8ClientResourceIndex.class);
        preparedConstructor.setAccessible(true);
        var apply = listenerType.getDeclaredMethod(
                "apply", preparedType, ResourceManager.class, ProfilerFiller.class);
        apply.setAccessible(true);
        var latestPrepareField = listenerType.getDeclaredField("latestPrepare");
        latestPrepareField.setAccessible(true);
        var resourceManager = ResourceManager.class.cast(Proxy.newProxyInstance(
                ResourceManager.class.getClassLoader(),
                new Class<?>[] {ResourceManager.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("resource manager must not be queried during apply");
                }));
        var profiler = ProfilerFiller.class.cast(Proxy.newProxyInstance(
                ProfilerFiller.class.getClassLoader(),
                new Class<?>[] {ProfilerFiller.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("profiler must not be queried during apply");
                }));
        var staleIdentity = new Object();
        var currentIdentity = new Object();
        var nextIdentity = new Object();
        var stale = preparedConstructor.newInstance(
                staleIdentity, P8ClientResourceIndex.empty());
        var current = preparedConstructor.newInstance(
                currentIdentity, P8ClientResourceIndex.empty());
        var next = preparedConstructor.newInstance(
                nextIdentity, P8ClientResourceIndex.empty());

        latestPrepareField.set(listener, currentIdentity);
        apply.invoke(listener, stale, resourceManager, profiler);
        assertEquals(0L, state.resourceGeneration());
        apply.invoke(listener, current, resourceManager, profiler);
        assertEquals(1L, state.resourceGeneration());
        assertTrue(state.resourceReady());
        apply.invoke(listener, current, resourceManager, profiler);
        assertEquals(1L, state.resourceGeneration());

        latestPrepareField.set(listener, nextIdentity);
        apply.invoke(listener, current, resourceManager, profiler);
        assertEquals(1L, state.resourceGeneration());
        apply.invoke(listener, next, resourceManager, profiler);
        assertEquals(2L, state.resourceGeneration());
    }

    private static List<Integer> acceptedParticleColors(P8ClientParticlePreference preference) {
        return acceptedParticleColors(preference, 1L);
    }

    private static List<Integer> acceptedParticleColors(
            P8ClientParticlePreference preference, long sequence) {
        var factory = particleFactory((configuration, input, output) -> {
            for (var index = 0; index < 8; index++) {
                output.particle(particle(input, 0xff000000 | index, 2));
            }
            return ClientProfileFactory.Result.PRESENTED;
        });
        var backend = new FakeBackend();
        backend.preference = preference;
        var execution = execution(
                idleSoundFactory(), factory, idleTrailFactory(), backend);
        var catalog = catalog();
        execution.replaceCatalog(
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        execution.present(
                event(
                        Optional.empty(),
                        Optional.of(P8S2TestFixtures.DEFAULT_PARTICLE_ID),
                        Optional.empty(),
                        sequence),
                catalog,
                P8ClientResourceIndex.empty(),
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION);
        return backend.particles.stream()
                .map(ClientProfileFactory.Particle::argb)
                .toList();
    }

    private static void tick(
            P8ClientPresentationExecution execution, long catalogGeneration) {
        execution.onClientTick(
                CONNECTION_GENERATION,
                WORLD_GENERATION,
                RESOURCE_GENERATION,
                catalogGeneration);
    }

    private static P8ClientPresentationExecution execution(
            RecordingFactory<P8S2TestFixtures.SoundConfiguration> soundFactory,
            RecordingFactory<P8S2TestFixtures.ParticleConfiguration> particleFactory,
            RecordingFactory<P8S2TestFixtures.TrailConfiguration> trailFactory,
            FakeBackend backend) {
        var registries = new FakeRegistryView();
        registries.register(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.SOUND_TYPE,
                soundFactory);
        registries.register(
                P8S2TestFixtures.PARTICLE_TYPE_ID,
                P8S2TestFixtures.PARTICLE_TYPE,
                particleFactory);
        registries.register(
                P8S2TestFixtures.TRAIL_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE,
                trailFactory);
        return new P8ClientPresentationExecution(registries, backend);
    }

    private static RecordingFactory<P8S2TestFixtures.SoundConfiguration> soundFactory(
            FactoryPresentation<P8S2TestFixtures.SoundConfiguration> presentation) {
        return new RecordingFactory<>(
                (configuration, assets) -> assets.soundExists(configuration.sound())
                        ? ClientProfileFactory.Availability.AVAILABLE
                        : ClientProfileFactory.Availability.UNAVAILABLE,
                presentation);
    }

    private static RecordingFactory<P8S2TestFixtures.ParticleConfiguration> particleFactory(
            FactoryPresentation<P8S2TestFixtures.ParticleConfiguration> presentation) {
        return new RecordingFactory<>(
                (configuration, assets) -> assets.particleExists(configuration.particle())
                        ? ClientProfileFactory.Availability.AVAILABLE
                        : ClientProfileFactory.Availability.UNAVAILABLE,
                presentation);
    }

    private static RecordingFactory<P8S2TestFixtures.TrailConfiguration> trailFactory(
            FactoryPresentation<P8S2TestFixtures.TrailConfiguration> presentation) {
        return new RecordingFactory<>(
                (configuration, assets) -> assets.particleExists(configuration.particle())
                        ? ClientProfileFactory.Availability.AVAILABLE
                        : ClientProfileFactory.Availability.UNAVAILABLE,
                presentation);
    }

    private static RecordingFactory<P8S2TestFixtures.SoundConfiguration> idleSoundFactory() {
        return soundFactory((configuration, input, output) ->
                ClientProfileFactory.Result.PRESENTED);
    }

    private static RecordingFactory<P8S2TestFixtures.ParticleConfiguration> idleParticleFactory() {
        return particleFactory((configuration, input, output) ->
                ClientProfileFactory.Result.PRESENTED);
    }

    private static RecordingFactory<P8S2TestFixtures.TrailConfiguration> idleTrailFactory() {
        return trailFactory((configuration, input, output) ->
                ClientProfileFactory.Result.PRESENTED);
    }

    private static ClientProfileFactory.Sound sound(
            ClientProfileFactory.Input input, float volume, float pitch) {
        return new ClientProfileFactory.Sound(
                SOUND_ASSET, input.x(), input.y(), input.z(), volume, pitch);
    }

    private static SoundInstance simpleSound(
            ResourceLocation id,
            SoundSource source,
            float volume,
            float pitch,
            double x) {
        return new SimpleSoundInstance(
                id,
                source,
                volume,
                pitch,
                RandomSource.create(91L),
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                x,
                2.0D,
                3.0D,
                false);
    }

    private static IEventBus faultingEventBus(
            IEventBus delegate,
            String failingMethod,
            boolean delegateBeforeFailure,
            Error primary,
            int[] addCalls,
            int[] unregisterCalls) {
        var failed = new boolean[1];
        return IEventBus.class.cast(Proxy.newProxyInstance(
                IEventBus.class.getClassLoader(),
                new Class<?>[] {IEventBus.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("addListener")) {
                        addCalls[0]++;
                    } else if (method.getName().equals("unregister")) {
                        unregisterCalls[0]++;
                    }
                    if (!failed[0] && method.getName().equals(failingMethod)) {
                        failed[0] = true;
                        if (delegateBeforeFailure) {
                            invokeEventBus(delegate, method, arguments);
                        }
                        throw primary;
                    }
                    return invokeEventBus(delegate, method, arguments);
                }));
    }

    private static Object invokeEventBus(
            IEventBus delegate, java.lang.reflect.Method method, Object[] arguments) {
        try {
            return method.invoke(delegate, arguments);
        } catch (InvocationTargetException failure) {
            var cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError("unexpected checked event-bus failure", cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("event-bus proxy invocation failed", failure);
        }
    }

    private static ClientProfileFactory.Particle particle(
            ClientProfileFactory.Input input, int argb, int lifetimeTicks) {
        return new ClientProfileFactory.Particle(
                PARTICLE_ASSET,
                input.x(),
                input.y(),
                input.z(),
                0.0D,
                0.0D,
                0.0D,
                argb,
                0.25F,
                lifetimeTicks);
    }

    private static ClientProfileFactory.Trail trail(
            P8S2TestFixtures.TrailConfiguration configuration,
            ClientProfileFactory.Input input) {
        return new ClientProfileFactory.Trail(
                configuration.particle(),
                OptionalInt.empty(),
                input.x(),
                input.y(),
                input.z(),
                input.primaryArgb(),
                configuration.sizeMilliBlocks() / 1_000.0F,
                configuration.lifetimeTicks(),
                configuration.sampleIntervalTicks());
    }

    private static P8ProfileCatalogSnapshot catalog(P8ProfileCatalogEntry... additional) {
        var entries = new ArrayList<>(List.of(
                entry(
                        P8S2TestFixtures.DEFAULT_SOUND_ID,
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.SOUND_TYPE,
                        DEFAULT_SOUND,
                        DEFAULT_SOUND_JSON),
                entry(
                        P8S2TestFixtures.DEFAULT_PARTICLE_ID,
                        P8S2TestFixtures.PARTICLE_TYPE_ID,
                        P8S2TestFixtures.PARTICLE_TYPE,
                        DEFAULT_PARTICLE,
                        DEFAULT_PARTICLE_JSON),
                entry(
                        P8S2TestFixtures.DEFAULT_TRAIL_ID,
                        P8S2TestFixtures.TRAIL_TYPE_ID,
                        P8S2TestFixtures.TRAIL_TYPE,
                        DEFAULT_TRAIL,
                        DEFAULT_TRAIL_JSON)));
        entries.addAll(List.of(additional));
        entries.sort(Comparator.comparing(P8ProfileCatalogEntry::profileId));
        return P8ProfileCatalogSnapshot.incoming(CATALOG_GENERATION, entries);
    }

    private static <C extends ProfileConfiguration> P8ProfileCatalogEntry entry(
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileType<C> type,
            C configuration,
            String canonicalJson) {
        return P8ProfileCatalogEntry.incoming(
                profileId,
                typeId,
                type.channel(),
                type.clientFactoryKey().id(),
                type.currentConfigurationVersion(),
                0,
                Optional.of(canonicalJson),
                Optional.of(new P8DecodedProfileConfiguration<>(
                        type,
                        configuration,
                        type.estimateCost(configuration))));
    }

    private static PresentationEventPayload event(
            Optional<ResourceLocation> sound,
            Optional<ResourceLocation> particle,
            Optional<ResourceLocation> trail,
            long sequence) {
        return new PresentationEventPayload(
                CATALOG_GENERATION,
                PresentationEventKind.CAST_RELEASE,
                new PresentationSourceSummary(OptionalInt.of(11), OptionalInt.of(22)),
                OVERWORLD,
                new PresentationPosition(1.0D, 2.0D, 3.0D),
                new PresentationDirection((short) 32_767, (short) 0, (short) 0),
                new PresentationAppearance(
                        0xff010203,
                        0xffa0b0c0,
                        1_000,
                        sound,
                        particle,
                        trail,
                        Map.of(PARAMETER_ID, 37)),
                0x1020304050607080L,
                sequence);
    }

    private static String soundJson(int volumeMilli) {
        return "{\"pitch_milli\":1000,\"sound\":\"minecraft:entity.experience_orb.pickup\","
                + "\"volume_milli\":" + volumeMilli + '}';
    }

    private static String particleJson(int count, int lifetimeTicks) {
        return "{\"count\":" + count + ",\"lifetime_ticks\":" + lifetimeTicks
                + ",\"particle\":\"minecraft:enchant\",\"size_milli_blocks\":250,"
                + "\"speed_milli_blocks\":0}";
    }

    private static String trailJson(int segments, int interval, int lifetimeTicks) {
        return "{\"lifetime_ticks\":" + lifetimeTicks
                + ",\"particle\":\"minecraft:enchant\",\"sample_interval_ticks\":"
                + interval + ",\"segments\":" + segments + ",\"size_milli_blocks\":200}";
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return java.lang.reflect.Modifier.isPublic(modifiers)
                || java.lang.reflect.Modifier.isProtected(modifiers);
    }

    @FunctionalInterface
    private interface FactoryAvailability<C extends ProfileConfiguration> {
        ClientProfileFactory.Availability availability(
                C configuration, ClientProfileFactory.AssetView assets);
    }

    @FunctionalInterface
    private interface FactoryPresentation<C extends ProfileConfiguration> {
        ClientProfileFactory.Result present(
                C configuration,
                ClientProfileFactory.Input input,
                ClientProfileFactory.Output output);
    }

    private static final class RecordingFactory<C extends ProfileConfiguration>
            implements ClientProfileFactory<C> {
        private final FactoryAvailability<C> availability;
        private final FactoryPresentation<C> presentation;
        private final ArrayList<C> availabilityConfigurations = new ArrayList<>();
        private final ArrayList<C> presentedConfigurations = new ArrayList<>();
        private final ArrayList<Input> presentedInputs = new ArrayList<>();

        private RecordingFactory(
                FactoryAvailability<C> availability,
                FactoryPresentation<C> presentation) {
            this.availability = availability;
            this.presentation = presentation;
        }

        @Override
        public Availability availability(C configuration, AssetView assets) {
            availabilityConfigurations.add(configuration);
            return availability.availability(configuration, assets);
        }

        @Override
        public Result present(C configuration, Input input, Output output) {
            presentedConfigurations.add(configuration);
            presentedInputs.add(input);
            return presentation.present(configuration, input, output);
        }
    }

    private static final class FakeRegistryView implements P8ClientRegistryView {
        private final Map<ResourceLocation, ProfileType<?>> types = new HashMap<>();
        private final IdentityHashMap<ProfileType<?>, ResourceLocation> typeIds =
                new IdentityHashMap<>();
        private final Map<ResourceLocation, ClientProfileFactoryRegistration<?>> factories =
                new HashMap<>();
        private final IdentityHashMap<ClientProfileFactoryRegistration<?>, ResourceLocation>
                factoryIds = new IdentityHashMap<>();

        private <C extends ProfileConfiguration> void register(
                ResourceLocation id,
                ProfileType<C> type,
                ClientProfileFactory<C> factory) {
            register(id, type, new ClientProfileFactoryRegistration<>(
                    type.clientFactoryKey(), factory));
        }

        private <C extends ProfileConfiguration> void registerMismatched(
                ResourceLocation id,
                ProfileType<C> type,
                ClientProfileFactory<C> factory) {
            register(id, type, new ClientProfileFactoryRegistration<>(
                    new ClientFactoryKey<>(type.clientFactoryKey().id()), factory));
        }

        private void register(
                ResourceLocation id,
                ProfileType<?> type,
                ClientProfileFactoryRegistration<?> factory) {
            types.put(id, type);
            typeIds.put(type, id);
            factories.put(id, factory);
            factoryIds.put(factory, id);
        }

        @Override
        public Optional<ProfileType<?>> profileType(ResourceLocation id) {
            return Optional.ofNullable(types.get(id));
        }

        @Override
        public Optional<ResourceLocation> profileTypeId(ProfileType<?> type) {
            return Optional.ofNullable(typeIds.get(type));
        }

        @Override
        public Optional<ClientProfileFactoryRegistration<?>> factory(ResourceLocation id) {
            return Optional.ofNullable(factories.get(id));
        }

        @Override
        public Optional<ResourceLocation> factoryId(
                ClientProfileFactoryRegistration<?> registration) {
            return Optional.ofNullable(factoryIds.get(registration));
        }
    }

    private static final class FakeBackend implements P8ClientPresentationBackend {
        private final ArrayList<PresentationEventPayload> recipientPayloads = new ArrayList<>();
        private final ArrayList<StartedSound> sounds = new ArrayList<>();
        private final ArrayList<FakeSoundHandle> handles = new ArrayList<>();
        private final ArrayList<ClientProfileFactory.Particle> particles = new ArrayList<>();
        private final Map<Integer, P8ClientPosition> trackedPositions = new HashMap<>();
        private P8ClientParticlePreference preference = P8ClientParticlePreference.FULL;
        private float soundVolume = 1.0F;
        private int particlePrepareCalls;
        private int preparedParticleStartCalls;
        private int trackedPositionCalls;
        private int particleRuntimeFailureAtAttempt = -1;
        private RuntimeException particleRuntimeFailure;
        private Error particleStartError;
        private boolean particleStartResult = true;
        private Error nextSoundStopError;
        private boolean nextSoundStopErrorLeavesActive;
        private Runnable beforeParticleStart = () -> {};
        private P8ClientRecipientContext recipientContext = new P8ClientRecipientContext(
                PresentationOrdering.RecipientCategory.ORDINARY, 0.0D);

        @Override
        public boolean matchesDimension(ResourceLocation dimension) {
            return OVERWORLD.equals(dimension);
        }

        @Override
        public P8ClientRecipientContext recipientContext(PresentationEventPayload payload) {
            recipientPayloads.add(payload);
            return recipientContext;
        }

        @Override
        public boolean soundExists(ResourceLocation id) {
            return SOUND_ASSET.equals(id);
        }

        @Override
        public boolean particleExists(ResourceLocation id) {
            return PARTICLE_ASSET.equals(id);
        }

        @Override
        public boolean resourceExists(
                ResourceLocation id, P8ClientResourceIndex resources) {
            return resources.contains(id);
        }

        @Override
        public P8ClientParticlePreference particlePreference() {
            return preference;
        }

        @Override
        public float soundVolume() {
            return soundVolume;
        }

        @Override
        public P8ClientSoundHandle prepareSound(
                ClientProfileFactory.Sound command, long visualSeed) {
            var handle = new FakeSoundHandle(
                    nextSoundStopError,
                    nextSoundStopErrorLeavesActive,
                    () -> sounds.add(new StartedSound(command, visualSeed)));
            nextSoundStopError = null;
            nextSoundStopErrorLeavesActive = false;
            handles.add(handle);
            return handle;
        }

        @Override
        public P8ClientPreparedParticle prepareParticle(
                ClientProfileFactory.Particle command) {
            particlePrepareCalls++;
            if (particlePrepareCalls == particleRuntimeFailureAtAttempt) {
                return () -> {
                    beforeParticleStart.run();
                    preparedParticleStartCalls++;
                    if (particleStartError != null) {
                        throw particleStartError;
                    }
                    throw particleRuntimeFailure;
                };
            }
            if (!particleStartResult) {
                return null;
            }
            return () -> {
                beforeParticleStart.run();
                preparedParticleStartCalls++;
                particles.add(command);
            };
        }

        @Override
        public Optional<P8ClientPosition> trackedPosition(int entityId) {
            trackedPositionCalls++;
            return Optional.ofNullable(trackedPositions.get(entityId));
        }
    }

    private static final class DirectSoundControl
            extends MinecraftP8ClientPresentationBackend.P8SoundControl {
        private final IEventBus bus;
        private final SoundInstance unrelated;
        private SoundInstance original;
        private SoundInstance unrelatedFinal;
        private SoundInstance played;
        private SoundInstance active;
        private SoundInstance stopped;
        private Error playError;
        private int stopCalls;

        private DirectSoundControl(IEventBus bus, SoundInstance unrelated) {
            this.bus = bus;
            this.unrelated = unrelated;
        }

        @Override
        void play(SoundInstance sound) {
            original = sound;
            if (unrelated != null) {
                unrelatedFinal = bus.post(new PlaySoundEvent(null, unrelated)).getSound();
            }
            played = bus.post(new PlaySoundEvent(null, sound)).getSound();
            active = played;
            if (playError != null) {
                throw playError;
            }
        }

        @Override
        boolean active(SoundInstance sound) {
            return active == sound;
        }

        @Override
        void stop(SoundInstance sound) {
            if (active != sound) {
                throw new IllegalStateException("unexpected sound identity");
            }
            stopCalls++;
            stopped = sound;
            active = null;
        }
    }

    private static final class FakeSoundHandle implements P8ClientSoundHandle {
        private final Error stopError;
        private final boolean stopErrorLeavesActive;
        private final Runnable startAction;
        private boolean active;
        private int stopCalls;

        private FakeSoundHandle(
                Error stopError, boolean stopErrorLeavesActive, Runnable startAction) {
            this.stopError = stopError;
            this.stopErrorLeavesActive = stopErrorLeavesActive;
            this.startAction = startAction;
        }

        @Override
        public void start() {
            startAction.run();
            active = true;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void stop() {
            stopCalls++;
            if (stopError != null && stopErrorLeavesActive) {
                throw stopError;
            }
            active = false;
            if (stopError != null) {
                throw stopError;
            }
        }
    }

    private record StartedSound(ClientProfileFactory.Sound command, long visualSeed) {}
}
