package com.yo1no.gramarye;

import static com.yo1no.gramarye.P11TransitionProtocol.*;
import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class P11TransitionWireTest {
    @Test
    void requestIsExactBigEndian36ByteBodyWithoutVarIntsOrIdentifiers() {
        var request = new Request(Scope.PLAY, 0x0102030405060708L, 2, 3, 41, Command.TRY, Kind.DEATH);
        var bytes = requestBytes(request);
        assertEquals(36, bytes.length);
        assertEquals("010201020304050607080000000000000002000000000000000300000000000000290000",
                ByteBufUtil.hexDump(bytes));
        assertEquals(request, decodeRequest(bytes));
        assertEquals("gramarye:transition_request", P11TransitionRequestPayload.TYPE.id().toString());
        assertEquals("gramarye-p11-transition-v1", P11TransitionWire.REGISTRAR_VERSION);
    }

    @Test
    void stateIsExactBigEndian54ByteBodyAndFullLongRangeDoesNotWrap() {
        var state = new State(Scope.PREPLAY, Long.MAX_VALUE, 2, 0, 41, Long.MAX_VALUE,
                Kind.JOIN, Outcome.NATIVE_FRAME, Availability.DISABLED, Reason.NONE, Long.MAX_VALUE);
        var bytes = stateBytes(state);
        assertEquals(54, bytes.length);
        assertEquals("01017fffffffffffffff000000000000000200000000000000000000000000000029"
                + "7fffffffffffffff020400007fffffffffffffff", ByteBufUtil.hexDump(bytes));
        assertEquals(state, decodeState(bytes));
        assertEquals("gramarye:transition_state", P11TransitionStatePayload.TYPE.id().toString());
    }

    @Test
    void codeMappingIsClosedExplicitAndIndependentOfEnumOrdinal() {
        Scope[] scopes = {Scope.CONFIG, Scope.PREPLAY, Scope.PLAY};
        Command[] commands = {Command.TRY, Command.STATUS};
        Kind[] kinds = {Kind.DEATH, Kind.END, Kind.JOIN, Kind.RETURN_TO_WORLD, Kind.ENTER_CONFIG};
        Outcome[] outcomes = {Outcome.BINDING, Outcome.PENDING, Outcome.NOT_STARTED, Outcome.RUNNING,
                Outcome.NATIVE_FRAME, Outcome.COMPLETED, Outcome.NOT_APPLICABLE, Outcome.FAULT,
                Outcome.UNKNOWN, Outcome.SUPERSEDED, Outcome.EXPIRED};
        Availability[] availability = {Availability.DISABLED, Availability.WAIT_NOTIFY, Availability.MAY_TRY};
        Reason[] reasons = {Reason.NONE, Reason.ACTIVE_OPERATION, Reason.ACTIVE_CONTEXT, Reason.ACTIVE_TRANSITION,
                Reason.NOT_APPLICABLE, Reason.SCENE_CHANGED, Reason.SOURCE_UNAVAILABLE, Reason.NATIVE_FAILURE,
                Reason.STATUS_UNAVAILABLE, Reason.WAIT_EXPIRED, Reason.CONTROL_EXHAUSTED,
                Reason.CONTROL_RATE_LIMIT, Reason.CONTROL_DISPATCH_BUSY};
        for (int i = 0; i < scopes.length; i++) { assertEquals(scopes[i], P11TransitionWire.scope(i)); assertEquals(i, P11TransitionWire.scopeCode(scopes[i])); }
        for (int i = 0; i < commands.length; i++) { assertEquals(commands[i], P11TransitionWire.command(i)); assertEquals(i, P11TransitionWire.commandCode(commands[i])); }
        for (int i = 0; i < kinds.length; i++) { assertEquals(kinds[i], P11TransitionWire.kind(i)); assertEquals(i, P11TransitionWire.kindCode(kinds[i])); }
        for (int i = 0; i < outcomes.length; i++) { assertEquals(outcomes[i], P11TransitionWire.outcome(i)); assertEquals(i, P11TransitionWire.outcomeCode(outcomes[i])); }
        for (int i = 0; i < availability.length; i++) { assertEquals(availability[i], P11TransitionWire.availability(i)); assertEquals(i, P11TransitionWire.availabilityCode(availability[i])); }
        for (int i = 0; i < reasons.length; i++) { assertEquals(reasons[i], P11TransitionWire.reason(i)); assertEquals(i, P11TransitionWire.reasonCode(reasons[i])); }
        for (int i = 0; i <= 255; i++) {
            final int code = i;
            if (i >= scopes.length) { assertThrows(IllegalArgumentException.class, () -> P11TransitionWire.scope(code)); }
            if (i >= commands.length) { assertThrows(IllegalArgumentException.class, () -> P11TransitionWire.command(code)); }
            if (i >= kinds.length) { assertThrows(IllegalArgumentException.class, () -> P11TransitionWire.kind(code)); }
            if (i >= outcomes.length) { assertThrows(IllegalArgumentException.class, () -> P11TransitionWire.outcome(code)); }
            if (i >= availability.length) { assertThrows(IllegalArgumentException.class, () -> P11TransitionWire.availability(code)); }
            if (i >= reasons.length) { assertThrows(IllegalArgumentException.class, () -> P11TransitionWire.reason(code)); }
        }
    }

    @Test
    void everyTruncationExtraByteUnknownVersionAndWireCodeIsRejected() {
        byte[] request = requestBytes(new Request(Scope.PLAY, 1, 2, 3, 41, Command.TRY, Kind.DEATH));
        byte[] state = stateBytes(new State(Scope.PLAY, 1, 2, 3, 41, 4, Kind.DEATH,
                Outcome.NOT_STARTED, Availability.WAIT_NOTIFY, Reason.ACTIVE_CONTEXT, 0));
        for (int size = 0; size < request.length; size++) {
            byte[] truncated = Arrays.copyOf(request, size);
            assertThrows(DecoderException.class, () -> decodeRequest(truncated));
        }
        for (int size = 0; size < state.length; size++) {
            byte[] truncated = Arrays.copyOf(state, size);
            assertThrows(DecoderException.class, () -> decodeState(truncated));
        }
        assertThrows(DecoderException.class, () -> decodeRequest(Arrays.copyOf(request, 37)));
        assertThrows(DecoderException.class, () -> decodeState(Arrays.copyOf(state, 55)));
        for (int offset : new int[] {0, 1, 34, 35}) {
            byte[] invalid = request.clone(); invalid[offset] = (byte) 255;
            assertThrows(DecoderException.class, () -> decodeRequest(invalid));
        }
        for (int offset : new int[] {0, 1, 42, 43, 44, 45}) {
            byte[] invalid = state.clone(); invalid[offset] = (byte) 255;
            assertThrows(DecoderException.class, () -> decodeState(invalid));
        }
    }

    @Test
    void zeroNegativeAndPhaseActorRoutesAreValidatedByTheSameConstructors() {
        var original = requestBytes(new Request(Scope.PLAY, 1, 2, 3, 41, Command.TRY, Kind.DEATH));
        for (int offset : new int[] {2, 10, 18, 26}) {
            for (long value : new long[] {0, -1, Long.MIN_VALUE}) {
                byte[] invalid = original.clone(); putLong(invalid, offset, value);
                assertThrows(DecoderException.class, () -> decodeRequest(invalid));
            }
        }
        for (Scope scope : Scope.values()) {
            for (Kind kind : Kind.values()) {
                for (Command command : Command.values()) {
                    for (long actor : new long[] {0, 1}) {
                        for (long sequence : new long[] {0, 1}) {
                            var raw = new FriendlyByteBuf(Unpooled.buffer());
                            try {
                                raw.writeByte(1).writeByte(P11TransitionWire.scopeCode(scope));
                                raw.writeLong(1).writeLong(2).writeLong(actor).writeLong(sequence);
                                raw.writeByte(P11TransitionWire.commandCode(command)).writeByte(P11TransitionWire.kindCode(kind));
                                byte[] bytes = ByteBufUtil.getBytes(raw);
                                Request expected;
                                try { expected = new Request(scope, 1, 2, actor, sequence, command, kind); }
                                catch (IllegalArgumentException rejected) {
                                    assertThrows(DecoderException.class, () -> decodeRequest(bytes));
                                    continue;
                                }
                                assertEquals(expected, decodeRequest(bytes));
                            } finally { raw.release(); }
                        }
                    }
                }
            }
        }
    }

    @Test
    void stateOutcomeCrossFieldsCannotBypassSharedSemanticValidator() {
        for (Outcome outcome : Outcome.values()) {
            for (Availability availability : Availability.values()) {
                for (Reason reason : Reason.values()) {
                    for (long target : new long[] {0, 4}) {
                        var raw = new FriendlyByteBuf(Unpooled.buffer());
                        try {
                            raw.writeByte(1).writeByte(2).writeLong(1).writeLong(2).writeLong(3);
                            raw.writeLong(41).writeLong(5).writeByte(0);
                            raw.writeByte(P11TransitionWire.outcomeCode(outcome))
                                    .writeByte(P11TransitionWire.availabilityCode(availability))
                                    .writeByte(P11TransitionWire.reasonCode(reason)).writeLong(target);
                            byte[] bytes = ByteBufUtil.getBytes(raw);
                            State expected;
                            try { expected = new State(Scope.PLAY, 1, 2, 3, 41, 5,
                                    Kind.DEATH, outcome, availability, reason, target); }
                            catch (IllegalArgumentException rejected) {
                                assertThrows(DecoderException.class, () -> decodeState(bytes));
                                continue;
                            }
                            assertEquals(expected, decodeState(bytes));
                        } finally { raw.release(); }
                    }
                }
            }
        }
    }

    @Test
    void serverZeroAndConfigurationEnterHaveOnlyTheirDeclaredUses() {
        var query = new Request(Scope.CONFIG, 1, 2, 0, 0, Command.STATUS, Kind.ENTER_CONFIG);
        assertEquals(query, decodeRequest(requestBytes(query)));
        var completed = new State(Scope.CONFIG, 1, 2, 0, 0, 8, Kind.ENTER_CONFIG,
                Outcome.COMPLETED, Availability.DISABLED, Reason.NONE, 0);
        assertEquals(completed, decodeState(stateBytes(completed)));
        assertThrows(IllegalArgumentException.class, () -> new Request(Scope.CONFIG,
                1, 2, 0, 1, Command.TRY, Kind.ENTER_CONFIG));
        for (Outcome outcome : new Outcome[] {Outcome.BINDING, Outcome.PENDING, Outcome.RUNNING, Outcome.NATIVE_FRAME}) {
            assertThrows(IllegalArgumentException.class, () -> new State(Scope.CONFIG,
                    1, 2, 0, 0, 1, Kind.ENTER_CONFIG, outcome, Availability.DISABLED, Reason.NONE, 0));
        }
    }

    private static byte[] requestBytes(Request request) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { P11TransitionRequestPayload.STREAM_CODEC.encode(buffer, new P11TransitionRequestPayload(request));
            return ByteBufUtil.getBytes(buffer); }
        finally { buffer.release(); }
    }

    private static byte[] stateBytes(State state) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { P11TransitionStatePayload.STREAM_CODEC.encode(buffer, new P11TransitionStatePayload(state));
            return ByteBufUtil.getBytes(buffer); }
        finally { buffer.release(); }
    }

    private static Request decodeRequest(byte[] bytes) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try { var payload = P11TransitionRequestPayload.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes()); return payload.request(); }
        finally { buffer.release(); }
    }

    private static State decodeState(byte[] bytes) {
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try { var payload = P11TransitionStatePayload.STREAM_CODEC.decode(buffer);
            assertEquals(0, buffer.readableBytes()); return payload.state(); }
        finally { buffer.release(); }
    }

    private static void putLong(byte[] bytes, int offset, long value) {
        var buffer = Unpooled.wrappedBuffer(bytes);
        try { buffer.setLong(offset, value); } finally { buffer.release(); }
    }
}
