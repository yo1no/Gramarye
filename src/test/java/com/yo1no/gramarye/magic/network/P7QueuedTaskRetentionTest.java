package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class P7QueuedTaskRetentionTest {
    @Test
    void queuedCastIntentRetainsExactlyUuidEpochAndImmutableIntent() {
        var playerId = new UUID(0L, 301L);
        var intent = minimumIntent(81L);
        var queued = new P7QueuedCastIntent(playerId, 41L, intent);

        assertEquals(playerId, queued.authenticatedPlayerId());
        assertEquals(41L, queued.connectionEpoch());
        assertSame(intent, queued.intent());
        assertExactPrivateFinalFields(P7QueuedCastIntent.class, Map.of(
                "authenticatedPlayerId", UUID.class,
                "connectionEpoch", long.class,
                "intent", CastIntent.class));
    }

    @Test
    void queuedCastIntentRejectsAbsentIdentityIntentAndNonpositiveEpoch() {
        var playerId = new UUID(0L, 302L);
        var intent = minimumIntent(82L);

        assertThrows(NullPointerException.class, () ->
                new P7QueuedCastIntent(null, 1L, intent));
        assertThrows(NullPointerException.class, () ->
                new P7QueuedCastIntent(playerId, 1L, null));
        assertThrows(P7SemanticInvariantException.class, () ->
                new P7QueuedCastIntent(playerId, 0L, intent));
        assertThrows(P7SemanticInvariantException.class, () ->
                new P7QueuedCastIntent(playerId, -1L, intent));
    }

    @Test
    void serverAndClientTasksRetainOnlyTheirExactTypedHandoffFields() {
        assertExactPrivateFinalFields(P7ServerDispatchTask.class, Map.of(
                "queuedIntent", P7QueuedCastIntent.class,
                "dispatchPort", P7ServerIntentDispatchPort.class,
                "permit", P7PendingPermit.class));
        assertExactPrivateFinalFields(P7IntentAckDispatchTask.class, Map.of(
                "acknowledgement", IntentAcknowledgement.class,
                "dispatchPort", P7ClientMirrorDispatchPort.class,
                "dispatchGeneration", long.class));
        assertExactPrivateFinalFields(P7ManaDispatchTask.class, Map.of(
                "snapshot", PlayerManaSnapshot.class,
                "dispatchPort", P7ClientMirrorDispatchPort.class,
                "dispatchGeneration", long.class));
        assertExactPrivateFinalFields(P7CooldownDispatchTask.class, Map.of(
                "snapshot", SkillCooldownSnapshot.class,
                "dispatchPort", P7ClientMirrorDispatchPort.class,
                "dispatchGeneration", long.class));
    }

    @Test
    void queuedValuesAndTasksRetainNoContextPlayerBufferConnectionOrThrowable() {
        var retainedTypes = List.of(
                P7QueuedCastIntent.class,
                P7ServerDispatchTask.class,
                P7IntentAckDispatchTask.class,
                P7ManaDispatchTask.class,
                P7CooldownDispatchTask.class);

        for (var retainedType : retainedTypes) {
            for (var field : retainedType.getDeclaredFields()) {
                var fieldType = field.getType().getName();
                assertFalse(fieldType.contains("IPayloadContext"), retainedType.getName());
                assertFalse(fieldType.contains("ServerPlayer"), retainedType.getName());
                assertFalse(fieldType.contains("LocalPlayer"), retainedType.getName());
                assertFalse(fieldType.contains("ByteBuf"), retainedType.getName());
                assertFalse(fieldType.equals("net.minecraft.network.Connection"),
                        retainedType.getName());
                assertFalse(Throwable.class.isAssignableFrom(field.getType()),
                        retainedType.getName());
            }
        }
    }

    @Test
    void normalServerDispatchRunsOnceAndReleasesPermitExactlyOnce() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 303L);
        var permit = owner.acquire(playerId, 1L).permit().orElseThrow();
        var queued = new P7QueuedCastIntent(playerId, 1L, minimumIntent(83L));
        var calls = new int[1];
        var task = new P7ServerDispatchTask(queued, actual -> {
            calls[0]++;
            assertSame(queued, actual);
        }, permit);

        task.run();

        assertEquals(1, calls[0]);
        assertTrue(permit.released());
        assertEquals(0, owner.playerPending(playerId));
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
        assertThrows(P7SemanticInvariantException.class, permit::release);
    }

    @Test
    void serverDispatchRuntimeExceptionIsSameObjectAndStillReleasesWithoutRetry() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 304L);
        var permit = owner.acquire(playerId, 1L).permit().orElseThrow();
        var queued = new P7QueuedCastIntent(playerId, 1L, minimumIntent(84L));
        var failure = new IllegalStateException("runtime dispatch failure");
        var calls = new int[1];
        var task = new P7ServerDispatchTask(queued, ignored -> {
            calls[0]++;
            throw failure;
        }, permit);

        var observed = assertThrows(IllegalStateException.class, task::run);

        assertSame(failure, observed);
        assertEquals(1, calls[0]);
        assertTrue(permit.released());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void serverDispatchErrorIsSameObjectAndStillReleasesWithoutRetry() {
        var owner = new P7PendingPermitOwner();
        var playerId = new UUID(0L, 305L);
        var permit = owner.acquire(playerId, 1L).permit().orElseThrow();
        var queued = new P7QueuedCastIntent(playerId, 1L, minimumIntent(85L));
        var failure = new AssertionError("error dispatch failure");
        var calls = new int[1];
        var task = new P7ServerDispatchTask(queued, ignored -> {
            calls[0]++;
            throw failure;
        }, permit);

        var observed = assertThrows(AssertionError.class, task::run);

        assertSame(failure, observed);
        assertEquals(1, calls[0]);
        assertTrue(permit.released());
        assertEquals(0, owner.serverPending());
        assertEquals(0, owner.trackedPlayerCount());
    }

    @Test
    void clientTasksDispatchTheirExactTypedValuesOnce() {
        var acknowledgement = new IntentAcknowledgement(
                86L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);
        var mana = new PlayerManaSnapshot(
                87L, PlayerManaSnapshot.Availability.AVAILABLE, 50L);
        var cooldown = new SkillCooldownSnapshot(
                88L, List.of(new CooldownSnapshotEntry(1, 5)));
        var port = new RecordingClientDispatchPort();

        new P7IntentAckDispatchTask(acknowledgement, port).run();
        new P7ManaDispatchTask(mana, port).run();
        new P7CooldownDispatchTask(cooldown, port).run();

        assertEquals(1, port.acknowledgementCalls);
        assertEquals(1, port.manaCalls);
        assertEquals(1, port.cooldownCalls);
        assertSame(acknowledgement, port.lastAcknowledgement);
        assertSame(mana, port.lastManaSnapshot);
        assertSame(cooldown, port.lastCooldownSnapshot);
    }

    @Test
    void acknowledgementTaskPropagatesRuntimeExceptionAndErrorWithoutRetry() {
        var acknowledgement = new IntentAcknowledgement(
                89L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);
        assertClientTaskPropagatesBothFailureKinds(
                port -> new P7IntentAckDispatchTask(acknowledgement, port));
    }

    @Test
    void manaTaskPropagatesRuntimeExceptionAndErrorWithoutRetry() {
        var mana = new PlayerManaSnapshot(
                90L, PlayerManaSnapshot.Availability.UNAVAILABLE, 0L);
        assertClientTaskPropagatesBothFailureKinds(
                port -> new P7ManaDispatchTask(mana, port));
    }

    @Test
    void cooldownTaskPropagatesRuntimeExceptionAndErrorWithoutRetry() {
        var cooldown = new SkillCooldownSnapshot(
                91L, List.of(new CooldownSnapshotEntry(2, Integer.MAX_VALUE)));
        assertClientTaskPropagatesBothFailureKinds(
                port -> new P7CooldownDispatchTask(cooldown, port));
    }

    private static void assertClientTaskPropagatesBothFailureKinds(
            Function<P7ClientMirrorDispatchPort, Runnable> taskFactory) {
        var runtimeFailure = new IllegalStateException("runtime client dispatch failure");
        var runtimePort = new ThrowingClientDispatchPort(runtimeFailure);
        var observedRuntime = assertThrows(
                IllegalStateException.class, taskFactory.apply(runtimePort)::run);

        assertSame(runtimeFailure, observedRuntime);
        assertEquals(1, runtimePort.calls);

        var errorFailure = new AssertionError("error client dispatch failure");
        var errorPort = new ThrowingClientDispatchPort(errorFailure);
        var observedError = assertThrows(
                AssertionError.class, taskFactory.apply(errorPort)::run);

        assertSame(errorFailure, observedError);
        assertEquals(1, errorPort.calls);
    }

    private static void assertExactPrivateFinalFields(
            Class<?> type, Map<String, Class<?>> expectedFields) {
        var actualFields = Arrays.stream(type.getDeclaredFields())
                .collect(Collectors.toMap(
                        java.lang.reflect.Field::getName,
                        java.lang.reflect.Field::getType));

        assertEquals(expectedFields, actualFields);
        assertTrue(Arrays.stream(type.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
    }

    private static CastIntent minimumIntent(long sequence) {
        return new CastIntent(sequence, 0, CastInputKind.CAST, 0, null, null);
    }

    private static final class RecordingClientDispatchPort
            implements P7ClientMirrorDispatchPort {
        private int acknowledgementCalls;
        private int manaCalls;
        private int cooldownCalls;
        private IntentAcknowledgement lastAcknowledgement;
        private PlayerManaSnapshot lastManaSnapshot;
        private SkillCooldownSnapshot lastCooldownSnapshot;

        @Override
        public long captureDispatchGeneration() {
            return 1L;
        }

        @Override
        public void onIntentAcknowledgement(
                long dispatchGeneration, IntentAcknowledgement acknowledgement) {
            assertEquals(1L, dispatchGeneration);
            acknowledgementCalls++;
            lastAcknowledgement = acknowledgement;
        }

        @Override
        public void onPlayerManaSnapshot(
                long dispatchGeneration, PlayerManaSnapshot snapshot) {
            assertEquals(1L, dispatchGeneration);
            manaCalls++;
            lastManaSnapshot = snapshot;
        }

        @Override
        public void onSkillCooldownSnapshot(
                long dispatchGeneration, SkillCooldownSnapshot snapshot) {
            assertEquals(1L, dispatchGeneration);
            cooldownCalls++;
            lastCooldownSnapshot = snapshot;
        }
    }

    private static final class ThrowingClientDispatchPort
            implements P7ClientMirrorDispatchPort {
        private final Throwable failure;
        private int calls;

        private ThrowingClientDispatchPort(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public long captureDispatchGeneration() {
            return 1L;
        }

        @Override
        public void onIntentAcknowledgement(
                long dispatchGeneration, IntentAcknowledgement acknowledgement) {
            fail();
        }

        @Override
        public void onPlayerManaSnapshot(
                long dispatchGeneration, PlayerManaSnapshot snapshot) {
            fail();
        }

        @Override
        public void onSkillCooldownSnapshot(
                long dispatchGeneration, SkillCooldownSnapshot snapshot) {
            fail();
        }

        private void fail() {
            calls++;
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw (Error) failure;
        }
    }
}
