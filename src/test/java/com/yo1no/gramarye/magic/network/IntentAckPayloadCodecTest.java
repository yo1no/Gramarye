package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class IntentAckPayloadCodecTest {
    @Test
    void allNineDispositionCodesRoundTripWithTheirExactWireValues() {
        var acknowledgements = new IntentAcknowledgement[] {
            acknowledgement(
                    IntentAcknowledgement.Disposition.ACCEPTED,
                    IntentAcknowledgement.SEQUENCE_CONSUMED,
                    null),
            acknowledgement(IntentAcknowledgement.Disposition.REJECTED, 0, null),
            acknowledgement(
                    IntentAcknowledgement.Disposition.DUPLICATE,
                    IntentAcknowledgement.HAS_EXPECTED_NEXT,
                    2L),
            acknowledgement(
                    IntentAcknowledgement.Disposition.STALE,
                    IntentAcknowledgement.HAS_EXPECTED_NEXT
                            | IntentAcknowledgement.RESYNC_RECOMMENDED,
                    2L),
            acknowledgement(
                    IntentAcknowledgement.Disposition.SEQUENCE_GAP,
                    IntentAcknowledgement.HAS_EXPECTED_NEXT
                            | IntentAcknowledgement.RESYNC_RECOMMENDED,
                    2L),
            acknowledgement(
                    IntentAcknowledgement.Disposition.SEQUENCE_EXHAUSTED, 0, null),
            acknowledgement(IntentAcknowledgement.Disposition.RATE_LIMITED, 0, null),
            acknowledgement(IntentAcknowledgement.Disposition.SERVER_BUSY, 0, null),
            acknowledgement(
                    IntentAcknowledgement.Disposition.UNAVAILABLE,
                    IntentAcknowledgement.RESYNC_RECOMMENDED,
                    null)
        };

        for (var expectedCode = 0; expectedCode < acknowledgements.length; expectedCode++) {
            var payload = new IntentAckPayload(acknowledgements[expectedCode]);
            var encoded = P7S2CodecTestSupport.encode(
                    IntentAckPayload.STREAM_CODEC, payload);

            assertEquals(expectedCode, Byte.toUnsignedInt(encoded[Long.BYTES]));
            assertEquals(payload, P7S2CodecTestSupport.decode(
                    IntentAckPayload.STREAM_CODEC, encoded));
        }
        assertEquals("gramarye:intent_ack", IntentAckPayload.TYPE.id().toString());
    }

    @Test
    void absentAndPresentExpectedNextHaveExactTenAndEighteenByteLayouts() {
        var withoutExpected = new IntentAckPayload(acknowledgement(
                IntentAcknowledgement.Disposition.REJECTED, 0, null));
        var withExpected = new IntentAckPayload(acknowledgement(
                IntentAcknowledgement.Disposition.DUPLICATE,
                IntentAcknowledgement.HAS_EXPECTED_NEXT,
                Long.MAX_VALUE));

        var tenBytes = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC, withoutExpected);
        var eighteenBytes = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC, withExpected);
        var fields = ByteBuffer.wrap(eighteenBytes);

        assertEquals(10, tenBytes.length);
        assertEquals(18, eighteenBytes.length);
        assertEquals(P7NetworkBounds.ACTUAL_MAX_ACK_BODY_BYTES, eighteenBytes.length);
        assertEquals(1L, fields.getLong());
        assertEquals(2, Byte.toUnsignedInt(fields.get()));
        assertEquals(IntentAcknowledgement.HAS_EXPECTED_NEXT,
                Byte.toUnsignedInt(fields.get()));
        assertEquals(Long.MAX_VALUE, fields.getLong());
        assertEquals(0, fields.remaining());
    }

    @Test
    void flagBitsZeroThroughTwoAreEncodedExactly() {
        var acknowledgement = new IntentAcknowledgement(
                7,
                IntentAcknowledgement.Disposition.STALE,
                IntentAcknowledgement.HAS_EXPECTED_NEXT
                        | IntentAcknowledgement.RESYNC_RECOMMENDED,
                8L);

        var encoded = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC,
                new IntentAckPayload(acknowledgement));

        assertEquals(0b00000101, Byte.toUnsignedInt(encoded[9]));
        assertEquals(acknowledgement.flags(), Byte.toUnsignedInt(encoded[9]));
    }

    @Test
    void nonpositiveIntentSequenceRemainsStructuralForRejectedAcknowledgements() {
        for (var sequence : new long[] {0L, -1L, Long.MIN_VALUE}) {
            var payload = new IntentAckPayload(new IntentAcknowledgement(
                    sequence,
                    IntentAcknowledgement.Disposition.REJECTED,
                    0,
                    null));

            var decoded = P7S2CodecTestSupport.decode(
                    IntentAckPayload.STREAM_CODEC,
                    P7S2CodecTestSupport.encode(IntentAckPayload.STREAM_CODEC, payload));

            assertEquals(sequence, decoded.acknowledgement().sequence());
            assertEquals(IntentAcknowledgement.Disposition.REJECTED,
                    decoded.acknowledgement().disposition());
        }
    }

    @Test
    void unknownDispositionAndReservedFlagBitsAreDecoderFailures() {
        for (var body : new byte[][] {
            rawAck(1, 9, 0, buffer -> {}),
            rawAck(1, 255, 0, buffer -> {}),
            rawAck(1, 0, 1 << 3, buffer -> {}),
            rawAck(1, 0, 1 << 7, buffer -> {})
        }) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    IntentAckPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void expectedNextPresenceMustMatchFlagAndRejectTrailingUnexpectedValue() {
        var missingExpected = rawAck(
                1,
                IntentAcknowledgement.Disposition.DUPLICATE.semanticCode(),
                IntentAcknowledgement.HAS_EXPECTED_NEXT,
                buffer -> {});
        var unexpectedExpected = rawAck(
                1,
                IntentAcknowledgement.Disposition.REJECTED.semanticCode(),
                0,
                buffer -> buffer.writeLong(2));

        P7S2CodecTestSupport.assertDecodeFailure(
                IntentAckPayload.STREAM_CODEC, missingExpected);
        P7S2CodecTestSupport.assertDecodeFailure(
                IntentAckPayload.STREAM_CODEC, unexpectedExpected);
    }

    @Test
    void expectedNextMustBePositiveWheneverItsFlagIsPresent() {
        for (var expectedNext : new long[] {0L, -1L, Long.MIN_VALUE}) {
            var body = rawAck(
                    1,
                    IntentAcknowledgement.Disposition.DUPLICATE.semanticCode(),
                    IntentAcknowledgement.HAS_EXPECTED_NEXT,
                    buffer -> buffer.writeLong(expectedNext));

            P7S2CodecTestSupport.assertDecodeFailure(
                    IntentAckPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void everyTruncatedRequiredOrOptionalFieldIsADecoderFailure() {
        var tenBytes = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC,
                new IntentAckPayload(acknowledgement(
                        IntentAcknowledgement.Disposition.REJECTED, 0, null)));
        for (var length = 0; length < tenBytes.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    IntentAckPayload.STREAM_CODEC, Arrays.copyOf(tenBytes, length));
        }

        var eighteenBytes = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC,
                new IntentAckPayload(acknowledgement(
                        IntentAcknowledgement.Disposition.DUPLICATE,
                        IntentAcknowledgement.HAS_EXPECTED_NEXT,
                        2L)));
        for (var length = 10; length < eighteenBytes.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    IntentAckPayload.STREAM_CODEC,
                    Arrays.copyOf(eighteenBytes, length));
        }
    }

    @Test
    void trailingAndThirtyThreeByteBodiesAreDecoderFailures() {
        var valid = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC,
                new IntentAckPayload(acknowledgement(
                        IntentAcknowledgement.Disposition.REJECTED, 0, null)));

        P7S2CodecTestSupport.assertDecodeFailure(
                IntentAckPayload.STREAM_CODEC,
                P7S2CodecTestSupport.append(valid, 0x00));
        P7S2CodecTestSupport.assertDecodeFailure(
                IntentAckPayload.STREAM_CODEC, new byte[33]);
    }

    @Test
    void payloadAndWireShapeCannotCarryInternalReasonsThrowablesOrMessages() {
        var acknowledgementFields = Arrays.stream(
                        IntentAcknowledgement.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var payloadFields = Arrays.stream(IntentAckPayload.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
        var encoded = P7S2CodecTestSupport.encode(
                IntentAckPayload.STREAM_CODEC,
                new IntentAckPayload(acknowledgement(
                        IntentAcknowledgement.Disposition.REJECTED, 0, null)));

        assertEquals(10, encoded.length);
        assertTrue(acknowledgementFields.stream().allMatch(field ->
                Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
        assertFalse(acknowledgementFields.stream().anyMatch(field ->
                Throwable.class.isAssignableFrom(field.getType())
                        || field.getType() == String.class
                        || field.getType() == P7IntentFailureReason.class));
        assertFalse(payloadFields.stream().anyMatch(field ->
                Throwable.class.isAssignableFrom(field.getType())
                        || field.getType() == String.class
                        || field.getType() == P7IntentFailureReason.class));
    }

    @Test
    void successfulEncodeAndDecodeNeverRetainOrReleaseCallerOwnedBuffers() {
        var payload = new IntentAckPayload(acknowledgement(
                IntentAcknowledgement.Disposition.DUPLICATE,
                IntentAcknowledgement.HAS_EXPECTED_NEXT,
                2L));
        byte[] encoded;

        try (var owned = P7S2CodecTestSupport.emptyBuffer()) {
            IntentAckPayload.STREAM_CODEC.encode(owned.buffer(), payload);
            assertEquals(1, owned.referenceCount());
            encoded = new byte[owned.buffer().readableBytes()];
            owned.buffer().getBytes(owned.buffer().readerIndex(), encoded);
        }
        try (var owned = P7S2CodecTestSupport.bufferContaining(encoded)) {
            assertEquals(payload, IntentAckPayload.STREAM_CODEC.decode(owned.buffer()));
            assertEquals(1, owned.referenceCount());
        }
    }

    private static IntentAcknowledgement acknowledgement(
            IntentAcknowledgement.Disposition disposition,
            int flags,
            Long expectedNext) {
        return new IntentAcknowledgement(1, disposition, flags, expectedNext);
    }

    private static byte[] rawAck(
            long sequence,
            int disposition,
            int flags,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> tail) {
        return P7S2CodecTestSupport.body(buffer -> {
            buffer.writeLong(sequence);
            buffer.writeByte(disposition);
            buffer.writeByte(flags);
            tail.accept(buffer);
        });
    }
}
