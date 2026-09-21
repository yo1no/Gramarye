package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class P9ClientCastInputTest {
    @Test
    void exactNineGateRowsShortCircuitInAuthorityOrder() {
        var decisions = List.of(
                P9ClientCastInput.GateDecision.NO_WORLD,
                P9ClientCastInput.GateDecision.NO_PLAYER,
                P9ClientCastInput.GateDecision.NO_PLAY_CONNECTION,
                P9ClientCastInput.GateDecision.SENDER_SESSION_UNAVAILABLE,
                P9ClientCastInput.GateDecision.SCREEN_OPEN,
                P9ClientCastInput.GateDecision.WINDOW_INACTIVE,
                P9ClientCastInput.GateDecision.CLIENT_PAUSED,
                P9ClientCastInput.GateDecision.PENDING_FLUSH_REQUIRED,
                P9ClientCastInput.GateDecision.ALLOW);

        for (var firstFalse = 0; firstFalse < decisions.size(); firstFalse++) {
            var values = new boolean[8];
            java.util.Arrays.fill(values, true);
            if (firstFalse < values.length) {
                values[firstFalse] = false;
            }
            var probe = new OrderedGateProbe(values);

            assertEquals(decisions.get(firstFalse),
                    P9ClientCastInput.evaluateGates(probe));
            assertEquals(firstFalse < values.length ? firstFalse + 1 : values.length,
                    probe.readCount());
        }
    }

    @Test
    void firstSequenceBuildsTheExactExistingP7Payload() {
        var sequence = new P9ClientCastInput.OutgoingSequence();
        var sent = new ArrayList<CastIntentPayload>();

        assertTrue(sequence.sendNext(sent::add));

        assertEquals(1, sent.size());
        var intent = sent.getFirst().intent();
        assertEquals(1L, intent.sequence());
        assertEquals(0, intent.slot());
        assertEquals(CastInputKind.CAST, intent.inputKind());
        assertEquals(0, intent.presenceMask());
        assertTrue(intent.aimHint().isEmpty());
        assertTrue(intent.entityHint().isEmpty());
        assertEquals(2L, sequence.expectedNext().orElseThrow());
    }

    @Test
    void maximumSequenceIsAttemptedOnceThenExhaustsUntilReset() {
        var sequence = new P9ClientCastInput.OutgoingSequence(Long.MAX_VALUE);
        var sent = new ArrayList<CastIntentPayload>();

        assertTrue(sequence.sendNext(sent::add));
        assertEquals(Long.MAX_VALUE, sent.getFirst().intent().sequence());
        assertTrue(sequence.expectedNext().isEmpty());
        assertFalse(sequence.sendNext(sent::add));
        assertEquals(1, sent.size());

        sequence.reset();
        assertEquals(1L, sequence.expectedNext().orElseThrow());
    }

    @Test
    void runtimeExceptionPropagatesByIdentityAfterConsumingSequence() {
        var sequence = new P9ClientCastInput.OutgoingSequence();
        var failure = new IllegalStateException("uncertain send");

        var thrown = assertThrows(IllegalStateException.class,
                () -> sequence.sendNext(payload -> {
                    assertEquals(1L, payload.intent().sequence());
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(2L, sequence.expectedNext().orElseThrow());
    }

    @Test
    void errorPropagatesByIdentityAfterConsumingSequence() {
        var sequence = new P9ClientCastInput.OutgoingSequence();
        var failure = new AssertionError("uncertain send");

        var thrown = assertThrows(AssertionError.class,
                () -> sequence.sendNext(payload -> {
                    assertEquals(1L, payload.intent().sequence());
                    throw failure;
                }));

        assertSame(failure, thrown);
        assertEquals(2L, sequence.expectedNext().orElseThrow());
    }

    @Test
    void physicalOwnerRetainsOneMappingOneSenderAndTransitionOnlyAuxiliaries()
            throws Exception {
        var root = projectRoot();
        var input = Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/P9ClientCastInput.java"));
        var registration = Files.readString(root.resolve(
                "src/main/java/com/yo1no/gramarye/magic/network/P9ClientKeyMappings.java"));

        assertTrue(input.contains("\"key.gramarye.cast\""));
        assertTrue(input.contains("\"key.categories.gramarye\""));
        assertTrue(input.contains("KeyConflictContext.IN_GAME"));
        assertTrue(input.contains("KeyModifier.NONE"));
        assertTrue(input.contains("InputConstants.Type.KEYSYM"));
        assertTrue(input.contains("GLFW.GLFW_KEY_R"));
        assertEquals(1, occurrences(input, "PacketDistributor.sendToServer("));
        assertEquals(1, occurrences(input, "new KeyMapping("));
        assertTrue(input.contains("private boolean pendingFlushRequired = true;"));
        assertTrue(input.contains("dropAllPendingClicks();"));
        assertFalse(input.contains("KeyMapping.releaseAll"));
        assertFalse(input.contains("setDown("));
        assertFalse(input.contains("java.lang.reflect"));
        assertTrue(registration.contains("@SuppressWarnings(\"removal\")"));
        assertTrue(registration.contains("bus = EventBusSubscriber.Bus.MOD"));
        assertTrue(registration.contains("P9ClientCastInput.registerKeyMapping(event)"));
        assertFalse(registration.contains("sendToServer"));
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        for (var index = source.indexOf(fragment); index >= 0;
                index = source.indexOf(fragment, index + fragment.length())) {
            count++;
        }
        return count;
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

    private static final class OrderedGateProbe implements P9ClientCastInput.GateProbe {
        private final boolean[] values;
        private int reads;

        private OrderedGateProbe(boolean[] values) {
            this.values = values.clone();
        }

        int readCount() {
            return reads;
        }

        private boolean read(int expectedIndex) {
            assertEquals(expectedIndex, reads, "gate read out of authority order");
            reads++;
            return values[expectedIndex];
        }

        @Override
        public boolean worldPresent() {
            return read(0);
        }

        @Override
        public boolean playerPresent() {
            return read(1);
        }

        @Override
        public boolean playConnectionPresent() {
            return read(2);
        }

        @Override
        public boolean senderSessionAvailable() {
            return read(3);
        }

        @Override
        public boolean screenAbsent() {
            return read(4);
        }

        @Override
        public boolean windowActive() {
            return read(5);
        }

        @Override
        public boolean clientNotPaused() {
            return read(6);
        }

        @Override
        public boolean noPendingFlush() {
            return read(7);
        }
    }
}
