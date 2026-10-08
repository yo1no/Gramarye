package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class P7ClientMirrorTest {
    @Test
    void connectedClientAppliesAckAndNewestFullSnapshotsOnly() {
        var mirror = new P7ClientMirror(() -> true, (c, l) -> true);
        connect(mirror);
        var generation = mirror.currentDispatchGeneration();
        var acknowledgement = new IntentAcknowledgement(
                41L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);

        mirror.onIntentAcknowledgement(generation, acknowledgement);
        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                3L, PlayerManaSnapshot.Availability.AVAILABLE, 90L));
        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                2L, PlayerManaSnapshot.Availability.UNAVAILABLE, 0L));
        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                3L, PlayerManaSnapshot.Availability.AVAILABLE, 999L));
        var newerCooldowns = List.of(
                P7S2CodecTestSupport.active(1, 10),
                P7S2CodecTestSupport.active(5, 20));
        mirror.onSkillCooldownSnapshot(
                generation, P7S2CodecTestSupport.cooldown(7L, newerCooldowns));
        mirror.onSkillCooldownSnapshot(
                generation, P7S2CodecTestSupport.cooldown(6L, List.of()));
        mirror.onSkillCooldownSnapshot(
                generation, P7S2CodecTestSupport.cooldown(7L, List.of()));

        assertSame(acknowledgement, mirror.lastAcknowledgement().orElseThrow());
        assertEquals(PlayerManaSnapshot.Availability.AVAILABLE,
                mirror.manaAvailability());
        assertEquals(90L, mirror.manaBalance());
        assertEquals(3L, mirror.lastAppliedManaSequence());
        assertEquals(newerCooldowns, mirror.cooldownEntries());
        assertEquals(7L, mirror.lastAppliedCooldownSequence());

        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                9L, PlayerManaSnapshot.Availability.UNAVAILABLE, 0L));
        assertEquals(PlayerManaSnapshot.Availability.UNAVAILABLE,
                mirror.manaAvailability());
        assertEquals(0L, mirror.manaBalance());
        assertEquals(9L, mirror.lastAppliedManaSequence());
        assertThrows(UnsupportedOperationException.class, () ->
                mirror.cooldownEntries().add(P7S2CodecTestSupport.active(6, 1)));
    }

    @Test
    void newerEmptyCooldownSnapshotClearsThePriorFullSnapshot() {
        var mirror = new P7ClientMirror(() -> true, (c, l) -> true);
        connect(mirror);
        var generation = mirror.currentDispatchGeneration();
        mirror.onSkillCooldownSnapshot(generation, P7S2CodecTestSupport.cooldown(
                1L, List.of(P7S2CodecTestSupport.active(2, 30))));

        mirror.onSkillCooldownSnapshot(
                generation, P7S2CodecTestSupport.cooldown(4L, List.of()));

        assertTrue(mirror.cooldownEntries().isEmpty());
        assertEquals(4L, mirror.lastAppliedCooldownSequence());
    }

    @Test
    void disconnectClearsEverythingAndSuppressesQueuedOldConnectionWork() {
        var mirror = populatedMirror();
        var oldGeneration = mirror.currentDispatchGeneration();
        var staleAck = new IntentAcknowledgement(
                50L, IntentAcknowledgement.Disposition.SERVER_BUSY, 0, null);
        var queuedOldAck = new P7IntentAckDispatchTask(staleAck, mirror, mirror.currentDispatchGeneration());
        var queuedOldMana = new P7ManaDispatchTask(new PlayerManaSnapshot(
                9L, PlayerManaSnapshot.Availability.AVAILABLE, 999L), mirror, mirror.currentDispatchGeneration());

        mirror.onDisconnected();
        queuedOldAck.run();
        queuedOldMana.run();

        assertEquals(1L, oldGeneration & 1L);
        assertCleared(mirror);

        connect(mirror);
        queuedOldAck.run();
        queuedOldMana.run();
        assertCleared(mirror);
    }

    @Test
    void worldUnloadClearsAndSuppressesOldTasksButKeepsNewConnectionWorkEligible() {
        var context = new P7RecordingPayloadContext(null, null, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        var mirror = new P7ClientMirror(() -> true, (c, l) -> true);
        mirror.onConnected(context.connection(), context.listener());
        var staleCooldown = new P7CooldownDispatchTask(P7S2CodecTestSupport.cooldown(
                10L, List.of(P7S2CodecTestSupport.active(8, 80))), mirror, mirror.currentDispatchGeneration());

        mirror.onClientWorldUnload();
        staleCooldown.run();
        assertCleared(mirror);
        assertEquals(0, mirror.captureDispatchGeneration(context.connection(), context.listener()));
        mirror.onPlayerContextReplaced(context.connection(), context.listener());
        staleCooldown.run();
        assertCleared(mirror);

        var freshMana = new PlayerManaSnapshot(
                1L, PlayerManaSnapshot.Availability.AVAILABLE, 7L);
        new P7ManaDispatchTask(freshMana, mirror, mirror.currentDispatchGeneration()).run();
        assertEquals(PlayerManaSnapshot.Availability.AVAILABLE,
                mirror.manaAvailability());
        assertEquals(7L, mirror.manaBalance());
        assertEquals(1L, mirror.lastAppliedManaSequence());
    }

    @Test
    void oldContextCannotCaptureNewGenerationAndNativePointerRetirementRejectsMainTask() {
        var oldContext = new P7RecordingPayloadContext(null, null, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        var currentContext = new P7RecordingPayloadContext(null, null, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        var current = new AtomicBoolean(true);
        var mirror = new P7ClientMirror(() -> true, (c, l) -> current.get());
        mirror.onConnected(oldContext.connection(), oldContext.listener());
        long oldGeneration = mirror.captureDispatchGeneration(oldContext.connection(), oldContext.listener());
        var snapshot = P7S2CodecTestSupport.cooldown(1, List.of(P7S2CodecTestSupport.active(0, 600)));
        var oldTask = new P7CooldownDispatchTask(snapshot, mirror, oldGeneration);
        mirror.onDisconnected();
        mirror.onConnected(currentContext.connection(), currentContext.listener());
        assertEquals(0, mirror.captureDispatchGeneration(oldContext.connection(), oldContext.listener()));
        assertEquals(0, mirror.captureDispatchGeneration(oldContext.connection(), currentContext.listener()));
        assertEquals(0, mirror.captureDispatchGeneration(currentContext.connection(), oldContext.listener()));
        oldTask.run();
        assertTrue(mirror.cooldownSnapshot().isEmpty());
        long generation = mirror.captureDispatchGeneration(currentContext.connection(), currentContext.listener());
        var freshTask = new P7CooldownDispatchTask(snapshot, mirror, generation);
        current.set(false);
        assertEquals(0, mirror.captureDispatchGeneration(currentContext.connection(), currentContext.listener()));
        freshTask.run();
        assertTrue(mirror.cooldownSnapshot().isEmpty());
        current.set(true);
        new P7CooldownDispatchTask(snapshot, mirror,
                mirror.captureDispatchGeneration(currentContext.connection(), currentContext.listener())).run();
        assertEquals(snapshot, mirror.cooldownSnapshot().orElseThrow());
        mirror.onSkillCooldownSnapshot(generation, new SkillCooldownSnapshot(1, 99, 99,
                P7ServerAuthorizationBoundary.SyncSourceState.AVAILABLE,
                P7ServerAuthorizationBoundary.SyncReason.NONE, List.of()));
        assertEquals(snapshot, mirror.cooldownSnapshot().orElseThrow());
    }

    @Test
    void sameConnectionWorldReplacementClearsPresentationWithoutReopeningSequences() {
        var context = new P7RecordingPayloadContext(null, null, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        var mirror = new P7ClientMirror(() -> true, (c, l) -> true);
        mirror.onConnected(context.connection(), context.listener());
        var oldGeneration = mirror.currentDispatchGeneration();
        mirror.onPlayerManaSnapshot(oldGeneration, new PlayerManaSnapshot(7,
                PlayerManaSnapshot.Availability.AVAILABLE, 20));
        mirror.onSkillCooldownSnapshot(oldGeneration, P7S2CodecTestSupport.cooldown(9,
                List.of(P7S2CodecTestSupport.active(0, 600))));
        mirror.onClientWorldUnload();
        assertTrue(mirror.cooldownSnapshot().isEmpty());
        assertEquals(7, mirror.lastAppliedManaSequence());
        assertEquals(9, mirror.lastAppliedCooldownSequence());
        mirror.onPlayerContextReplaced(context.connection(), context.listener());
        var generation = mirror.currentDispatchGeneration();
        assertTrue(generation > oldGeneration);
        for (long sequence : new long[] {1, 7}) {
            mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(sequence,
                    PlayerManaSnapshot.Availability.AVAILABLE, 99));
        }
        for (long sequence : new long[] {1, 9}) {
            mirror.onSkillCooldownSnapshot(generation, P7S2CodecTestSupport.cooldown(sequence, List.of()));
        }
        assertTrue(mirror.cooldownSnapshot().isEmpty());
        assertEquals(PlayerManaSnapshot.Availability.UNAVAILABLE, mirror.manaAvailability());
        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(8,
                PlayerManaSnapshot.Availability.AVAILABLE, 21));
        mirror.onSkillCooldownSnapshot(generation, P7S2CodecTestSupport.cooldown(10, List.of()));
        assertEquals(21, mirror.manaBalance());
        assertTrue(mirror.cooldownSnapshot().orElseThrow().entries().isEmpty());
        mirror.onDisconnected();
        mirror.onConnected(context.connection(), context.listener());
        assertEquals(0, mirror.lastAppliedManaSequence());
        assertEquals(0, mirror.lastAppliedCooldownSequence());
    }

    @Test
    void slotZeroHudDistinguishesNoDataKnownEmptyUnknownRoutingAndServerStates() {
        assertHud("syncing", P7CooldownHud.label(java.util.Optional.empty()));
        assertHud("unequipped", P7CooldownHud.label(java.util.Optional.of(
                P7S2CodecTestSupport.cooldown(1, List.of()))));
        for (var reason : List.of(P7ServerAuthorizationBoundary.SyncReason.EQUIPMENT_UNKNOWN,
                P7ServerAuthorizationBoundary.SyncReason.SOURCE_UNAVAILABLE)) {
            var unknown = new SkillCooldownSnapshot(1,
                    reason == P7ServerAuthorizationBoundary.SyncReason.SOURCE_UNAVAILABLE ? 0 : 1, 0,
                    P7ServerAuthorizationBoundary.SyncSourceState.UNAVAILABLE, reason, List.of());
            assertHud("unavailable", P7CooldownHud.label(java.util.Optional.of(unknown)));
        }
        for (var state : P7ServerAuthorizationBoundary.SyncEntryState.values()) {
            var reason = switch (state) {
                case READY -> P7ServerAuthorizationBoundary.SyncReason.NONE;
                case ACTIVE -> P7ServerAuthorizationBoundary.SyncReason.SAVE_FAILED;
                case PENDING -> P7ServerAuthorizationBoundary.SyncReason.PENDING_RELEASE;
                case RECOVERY_REQUIRED -> P7ServerAuthorizationBoundary.SyncReason.OUTCOME_UNCERTAIN;
                case UNAVAILABLE -> P7ServerAuthorizationBoundary.SyncReason.PROVIDER;
            };
            var snapshot = P7S2CodecTestSupport.cooldown(2, List.of(new CooldownSnapshotEntry(0,
                    P7S2CodecTestSupport.reference(0), state, reason, state == P7ServerAuthorizationBoundary.SyncEntryState.ACTIVE ? 600 : 0)));
            var component = P7CooldownHud.label(java.util.Optional.of(snapshot));
            assertHud(switch (state) {
                case READY -> "ready"; case ACTIVE -> "active"; case PENDING -> "pending";
                case RECOVERY_REQUIRED -> "recovery"; case UNAVAILABLE -> "unavailable";
            }, component);
            if (state == P7ServerAuthorizationBoundary.SyncEntryState.ACTIVE) {
                var translated = (net.minecraft.network.chat.contents.TranslatableContents) component.getContents();
                assertEquals(600, translated.getArgs()[0]);
                assertEquals(component, P7CooldownHud.label(java.util.Optional.of(snapshot)));
                assertEquals(600, snapshot.entries().getFirst().remainingTicks());
            }
        }
    }

    private static void assertHud(String suffix, net.minecraft.network.chat.Component component) {
        var translated = (net.minecraft.network.chat.contents.TranslatableContents) component.getContents();
        assertEquals("hud.gramarye.cooldown." + suffix, translated.getKey());
    }

    @Test
    void disconnectedOrWrongGenerationWorkCannotPopulateTheMirror() {
        var mirror = new P7ClientMirror(() -> true, (c, l) -> true);
        var disconnectedTask = new P7ManaDispatchTask(new PlayerManaSnapshot(
                1L, PlayerManaSnapshot.Availability.AVAILABLE, 5L), mirror, mirror.currentDispatchGeneration());

        disconnectedTask.run();
        connect(mirror);
        disconnectedTask.run();
        mirror.onPlayerManaSnapshot(
                mirror.currentDispatchGeneration() + 2L,
                new PlayerManaSnapshot(
                        2L, PlayerManaSnapshot.Availability.AVAILABLE, 6L));

        assertEquals(PlayerManaSnapshot.Availability.UNAVAILABLE,
                mirror.manaAvailability());
        assertEquals(0L, mirror.manaBalance());
        assertEquals(0L, mirror.lastAppliedManaSequence());
    }

    @Test
    void everyMutationAndReadRequiresTheConfiguredClientThread() {
        var clientThread = new AtomicBoolean(true);
        var mirror = new P7ClientMirror(clientThread::get, (c, l) -> true);
        connect(mirror);
        var generation = mirror.currentDispatchGeneration();
        clientThread.set(false);

        assertThrows(P7SemanticInvariantException.class, mirror::onDisconnected);
        assertThrows(P7SemanticInvariantException.class, () ->
                mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                        1L, PlayerManaSnapshot.Availability.AVAILABLE, 1L)));
        assertThrows(P7SemanticInvariantException.class, mirror::manaBalance);

        clientThread.set(true);
        assertEquals(0L, mirror.manaBalance());
        assertEquals(0L, mirror.lastAppliedManaSequence());
    }

    @Test
    void clientOnlySubscriberAndCommonFactoryKeepDedicatedDescriptorsSeparated()
            throws Exception {
        var root = projectRoot();
        var lifecycleSource = Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/"
                        + "P7ClientLifecycleEvents.java"));
        var factorySource = Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/"
                        + "P7ClientMirrorDispatchFactory.java"));

        assertTrue(lifecycleSource.contains(
                "@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)"));
        assertTrue(lifecycleSource.contains(
                "ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(lifecycleSource.contains("LevelEvent.Unload"));
        assertTrue(lifecycleSource.contains("P9ClientCastInput.onConnectionOpened()"));
        assertTrue(lifecycleSource.contains("P9ClientCastInput.onConnectionClosed()"));
        assertTrue(lifecycleSource.contains("P9ClientCastInput.onPlayerContextReplaced()"));
        assertTrue(lifecycleSource.contains("P9ClientCastInput.onClientWorldLoaded()"));
        assertTrue(lifecycleSource.contains("P9ClientCastInput.onClientWorldUnloaded()"));
        assertTrue(factorySource.contains("static P7ClientMirrorDispatchPort production()"));
        assertTrue(!factorySource.contains("net.minecraft.client"));
        assertTrue(!factorySource.contains("ClientPlayerNetworkEvent"));
    }

    private static void connect(P7ClientMirror mirror) {
        var context = new P7RecordingPayloadContext(null, null, net.minecraft.network.protocol.PacketFlow.CLIENTBOUND);
        mirror.onConnected(context.connection(), context.listener());
    }

    private static P7ClientMirror populatedMirror() {
        var mirror = new P7ClientMirror(() -> true, (c, l) -> true);
        connect(mirror);
        var generation = mirror.currentDispatchGeneration();
        mirror.onIntentAcknowledgement(generation, new IntentAcknowledgement(
                1L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null));
        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                1L, PlayerManaSnapshot.Availability.AVAILABLE, 30L));
        mirror.onSkillCooldownSnapshot(generation, P7S2CodecTestSupport.cooldown(
                1L, List.of(P7S2CodecTestSupport.active(3, 12))));
        return mirror;
    }

    private static void assertCleared(P7ClientMirror mirror) {
        assertTrue(mirror.lastAcknowledgement().isEmpty());
        assertEquals(PlayerManaSnapshot.Availability.UNAVAILABLE,
                mirror.manaAvailability());
        assertEquals(0L, mirror.manaBalance());
        assertTrue(mirror.cooldownEntries().isEmpty());
        assertEquals(0L, mirror.lastAppliedManaSequence());
        assertEquals(0L, mirror.lastAppliedCooldownSequence());
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
