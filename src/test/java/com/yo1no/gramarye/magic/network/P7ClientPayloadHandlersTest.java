package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalLong;
import java.util.function.Consumer;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

final class P7ClientPayloadHandlersTest {
    @Test
    void acknowledgementHandlerEnqueuesOneTypedTaskAndDefersExactValue() {
        var dispatchPort = new RecordingClientDispatchPort();
        var composition = composition(dispatchPort);
        var context = clientContext();
        var acknowledgement = new IntentAcknowledgement(
                11L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);

        P7ClientPayloadHandlers.handleIntentAcknowledgement(
                new IntentAckPayload(acknowledgement), context, composition);

        assertEquals(1, context.enqueueCalls());
        assertEquals(1, context.queuedTaskCount());
        assertEquals(0, context.playerCalls());
        assertEquals(0, dispatchPort.acknowledgementCalls);
        assertEquals(0, dispatchPort.manaCalls);
        assertEquals(0, dispatchPort.cooldownCalls);

        context.takeOnlyTask().run();

        assertEquals(1, dispatchPort.acknowledgementCalls);
        assertSame(acknowledgement, dispatchPort.lastAcknowledgement);
        assertEquals(0, dispatchPort.manaCalls);
        assertEquals(0, dispatchPort.cooldownCalls);
    }

    @Test
    void manaHandlerEnqueuesOneTypedTaskAndDefersExactValue() {
        var dispatchPort = new RecordingClientDispatchPort();
        var composition = composition(dispatchPort);
        var context = clientContext();
        var snapshot = new PlayerManaSnapshot(
                12L, PlayerManaSnapshot.Availability.AVAILABLE, 900L);

        P7ClientPayloadHandlers.handlePlayerManaSnapshot(
                new PlayerManaSyncPayload(snapshot), context, composition);

        assertEquals(1, context.enqueueCalls());
        assertEquals(1, context.queuedTaskCount());
        assertEquals(0, context.playerCalls());
        assertEquals(0, dispatchPort.acknowledgementCalls);
        assertEquals(0, dispatchPort.manaCalls);
        assertEquals(0, dispatchPort.cooldownCalls);

        context.takeOnlyTask().run();

        assertEquals(1, dispatchPort.manaCalls);
        assertSame(snapshot, dispatchPort.lastManaSnapshot);
        assertEquals(0, dispatchPort.acknowledgementCalls);
        assertEquals(0, dispatchPort.cooldownCalls);
    }

    @Test
    void cooldownHandlerEnqueuesOneTypedTaskAndDefersExactValue() {
        var dispatchPort = new RecordingClientDispatchPort();
        var composition = composition(dispatchPort);
        var context = clientContext();
        var snapshot = new SkillCooldownSnapshot(
                13L, List.of(new CooldownSnapshotEntry(4, 20)));

        P7ClientPayloadHandlers.handleSkillCooldownSnapshot(
                new SkillCooldownSyncPayload(snapshot), context, composition);

        assertEquals(1, context.enqueueCalls());
        assertEquals(1, context.queuedTaskCount());
        assertEquals(0, context.playerCalls());
        assertEquals(0, dispatchPort.acknowledgementCalls);
        assertEquals(0, dispatchPort.manaCalls);
        assertEquals(0, dispatchPort.cooldownCalls);

        context.takeOnlyTask().run();

        assertEquals(1, dispatchPort.cooldownCalls);
        assertSame(snapshot, dispatchPort.lastCooldownSnapshot);
        assertEquals(0, dispatchPort.acknowledgementCalls);
        assertEquals(0, dispatchPort.manaCalls);
    }

    @Test
    void synchronousEnqueueRuntimeExceptionPropagatesAsSameObjectWithoutDispatch() {
        var failure = new IllegalStateException("runtime enqueue failure");
        var dispatchPort = new RecordingClientDispatchPort();
        var acknowledgement = new IntentAcknowledgement(
                14L,
                IntentAcknowledgement.Disposition.ACCEPTED,
                IntentAcknowledgement.SEQUENCE_CONSUMED,
                null);

        assertSameEnqueueFailure(failure, dispatchPort, context ->
                P7ClientPayloadHandlers.handleIntentAcknowledgement(
                        new IntentAckPayload(acknowledgement), context,
                        composition(dispatchPort)));
        var mana = new PlayerManaSnapshot(
                15L, PlayerManaSnapshot.Availability.UNAVAILABLE, 0L);
        assertSameEnqueueFailure(failure, dispatchPort, context ->
                P7ClientPayloadHandlers.handlePlayerManaSnapshot(
                        new PlayerManaSyncPayload(mana), context,
                        composition(dispatchPort)));
        var cooldown = new SkillCooldownSnapshot(
                16L, List.of(new CooldownSnapshotEntry(3, 9)));
        assertSameEnqueueFailure(failure, dispatchPort, context ->
                P7ClientPayloadHandlers.handleSkillCooldownSnapshot(
                        new SkillCooldownSyncPayload(cooldown), context,
                        composition(dispatchPort)));
    }

    @Test
    void synchronousEnqueueErrorPropagatesAsSameObjectWithoutDispatch() {
        var failure = new AssertionError("error enqueue failure");
        var dispatchPort = new RecordingClientDispatchPort();
        var acknowledgement = new IntentAcknowledgement(
                17L,
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                0,
                null);
        assertSameEnqueueFailure(failure, dispatchPort, context ->
                P7ClientPayloadHandlers.handleIntentAcknowledgement(
                        new IntentAckPayload(acknowledgement), context,
                        composition(dispatchPort)));
        var mana = new PlayerManaSnapshot(
                18L, PlayerManaSnapshot.Availability.AVAILABLE, 1L);
        assertSameEnqueueFailure(failure, dispatchPort, context ->
                P7ClientPayloadHandlers.handlePlayerManaSnapshot(
                        new PlayerManaSyncPayload(mana), context,
                        composition(dispatchPort)));
        var cooldown = new SkillCooldownSnapshot(
                19L, List.of(new CooldownSnapshotEntry(4, 10)));
        assertSameEnqueueFailure(failure, dispatchPort, context ->
                P7ClientPayloadHandlers.handleSkillCooldownSnapshot(
                        new SkillCooldownSyncPayload(cooldown), context,
                        composition(dispatchPort)));
    }

    @Test
    void productionCompositionLeavesAllClientMirrorDispatchesAsNoOps() {
        var context = clientContext();
        var acknowledgement = new IntentAcknowledgement(
                16L,
                IntentAcknowledgement.Disposition.UNAVAILABLE,
                0,
                null);

        P7ClientPayloadHandlers.handleIntentAcknowledgement(
                new IntentAckPayload(acknowledgement),
                context,
                P7NetworkComposition.production());

        var queuedTask = context.takeOnlyTask();
        queuedTask.run();
        assertTrue(queuedTask instanceof P7IntentAckDispatchTask);
    }

    private static P7NetworkComposition composition(P7ClientMirrorDispatchPort dispatchPort) {
        return new P7NetworkComposition(
                ignored -> OptionalLong.empty(),
                new P7PendingPermitOwner(),
                ignored -> {},
                dispatchPort);
    }

    private static P7RecordingPayloadContext clientContext() {
        return clientContext(null);
    }

    private static P7RecordingPayloadContext clientContext(Throwable enqueueFailure) {
        return new P7RecordingPayloadContext(
                null, enqueueFailure, PacketFlow.CLIENTBOUND);
    }

    private static void assertSameEnqueueFailure(
            Throwable failure,
            RecordingClientDispatchPort dispatchPort,
            Consumer<P7RecordingPayloadContext> invocation) {
        var context = clientContext(failure);
        var observed = assertThrows(failure.getClass(), () -> invocation.accept(context));

        assertSame(failure, observed);
        assertEquals(PacketFlow.CLIENTBOUND, context.flow());
        assertEquals(1, context.enqueueCalls());
        assertEquals(0, context.queuedTaskCount());
        assertEquals(0, dispatchPort.totalCalls());
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

        int totalCalls() {
            return acknowledgementCalls + manaCalls + cooldownCalls;
        }
    }
}
