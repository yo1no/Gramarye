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
        var mirror = new P7ClientMirror(() -> true);
        mirror.onConnected();
        var generation = mirror.captureDispatchGeneration();
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
                new CooldownSnapshotEntry(1, 10),
                new CooldownSnapshotEntry(5, 20));
        mirror.onSkillCooldownSnapshot(
                generation, new SkillCooldownSnapshot(7L, newerCooldowns));
        mirror.onSkillCooldownSnapshot(
                generation, new SkillCooldownSnapshot(6L, List.of()));
        mirror.onSkillCooldownSnapshot(
                generation, new SkillCooldownSnapshot(7L, List.of()));

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
                mirror.cooldownEntries().add(new CooldownSnapshotEntry(6, 1)));
    }

    @Test
    void newerEmptyCooldownSnapshotClearsThePriorFullSnapshot() {
        var mirror = new P7ClientMirror(() -> true);
        mirror.onConnected();
        var generation = mirror.captureDispatchGeneration();
        mirror.onSkillCooldownSnapshot(generation, new SkillCooldownSnapshot(
                1L, List.of(new CooldownSnapshotEntry(2, 30))));

        mirror.onSkillCooldownSnapshot(
                generation, new SkillCooldownSnapshot(4L, List.of()));

        assertTrue(mirror.cooldownEntries().isEmpty());
        assertEquals(4L, mirror.lastAppliedCooldownSequence());
    }

    @Test
    void disconnectClearsEverythingAndSuppressesQueuedOldConnectionWork() {
        var mirror = populatedMirror();
        var oldGeneration = mirror.captureDispatchGeneration();
        var staleAck = new IntentAcknowledgement(
                50L, IntentAcknowledgement.Disposition.SERVER_BUSY, 0, null);
        var queuedOldAck = new P7IntentAckDispatchTask(staleAck, mirror);
        var queuedOldMana = new P7ManaDispatchTask(new PlayerManaSnapshot(
                9L, PlayerManaSnapshot.Availability.AVAILABLE, 999L), mirror);

        mirror.onDisconnected();
        queuedOldAck.run();
        queuedOldMana.run();

        assertEquals(1L, oldGeneration & 1L);
        assertCleared(mirror);

        mirror.onConnected();
        queuedOldAck.run();
        queuedOldMana.run();
        assertCleared(mirror);
    }

    @Test
    void worldUnloadClearsAndSuppressesOldTasksButKeepsNewConnectionWorkEligible() {
        var mirror = populatedMirror();
        var staleCooldown = new P7CooldownDispatchTask(new SkillCooldownSnapshot(
                10L, List.of(new CooldownSnapshotEntry(8, 80))), mirror);

        mirror.onClientWorldUnload();
        staleCooldown.run();
        assertCleared(mirror);

        var freshMana = new PlayerManaSnapshot(
                1L, PlayerManaSnapshot.Availability.AVAILABLE, 7L);
        new P7ManaDispatchTask(freshMana, mirror).run();
        assertEquals(PlayerManaSnapshot.Availability.AVAILABLE,
                mirror.manaAvailability());
        assertEquals(7L, mirror.manaBalance());
        assertEquals(1L, mirror.lastAppliedManaSequence());
    }

    @Test
    void disconnectedOrWrongGenerationWorkCannotPopulateTheMirror() {
        var mirror = new P7ClientMirror(() -> true);
        var disconnectedTask = new P7ManaDispatchTask(new PlayerManaSnapshot(
                1L, PlayerManaSnapshot.Availability.AVAILABLE, 5L), mirror);

        disconnectedTask.run();
        mirror.onConnected();
        disconnectedTask.run();
        mirror.onPlayerManaSnapshot(
                mirror.captureDispatchGeneration() + 2L,
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
        var mirror = new P7ClientMirror(clientThread::get);
        mirror.onConnected();
        var generation = mirror.captureDispatchGeneration();
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
        assertTrue(factorySource.contains("static P7ClientMirrorDispatchPort production()"));
        assertTrue(!factorySource.contains("net.minecraft.client"));
        assertTrue(!factorySource.contains("ClientPlayerNetworkEvent"));
    }

    private static P7ClientMirror populatedMirror() {
        var mirror = new P7ClientMirror(() -> true);
        mirror.onConnected();
        var generation = mirror.captureDispatchGeneration();
        mirror.onIntentAcknowledgement(generation, new IntentAcknowledgement(
                1L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null));
        mirror.onPlayerManaSnapshot(generation, new PlayerManaSnapshot(
                1L, PlayerManaSnapshot.Availability.AVAILABLE, 30L));
        mirror.onSkillCooldownSnapshot(generation, new SkillCooldownSnapshot(
                1L, List.of(new CooldownSnapshotEntry(3, 12))));
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
