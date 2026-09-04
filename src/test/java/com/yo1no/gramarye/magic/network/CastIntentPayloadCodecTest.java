package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CastIntentPayloadCodecTest {
    private static final int AIM_MASK = 1 << P7NetworkBounds.AIM_PRESENT_BIT;
    private static final int ENTITY_MASK = 1 << P7NetworkBounds.ENTITY_HINT_PRESENT_BIT;

    @Test
    void minimumIntentRoundTripsInCanonicalOrderAtExactlyElevenBytes() {
        var payload = payload(1, 0, null, null);

        var encoded = P7S2CodecTestSupport.encode(CastIntentPayload.STREAM_CODEC, payload);
        var fields = ByteBuffer.wrap(encoded);

        assertEquals(11, encoded.length);
        assertEquals(1L, fields.getLong());
        assertEquals(0, Byte.toUnsignedInt(fields.get()));
        assertEquals(P7NetworkBounds.CAST_INPUT_KIND_CODE, Byte.toUnsignedInt(fields.get()));
        assertEquals(0, Byte.toUnsignedInt(fields.get()));
        assertEquals(0, fields.remaining());
        assertEquals(payload, P7S2CodecTestSupport.decode(
                CastIntentPayload.STREAM_CODEC, encoded));
        assertEquals("gramarye:cast_intent", CastIntentPayload.TYPE.id().toString());
    }

    @Test
    void aimOnlyRoundTripsWithQ15EdgesAndExactSeventeenByteBody() {
        var payload = payload(
                Long.MAX_VALUE,
                P7NetworkBounds.SLOT_MAX,
                new AimHint(P7NetworkBounds.Q15_MIN, P7NetworkBounds.Q15_MAX, 1),
                null);

        var encoded = P7S2CodecTestSupport.encode(CastIntentPayload.STREAM_CODEC, payload);

        assertEquals(17, encoded.length);
        assertEquals(payload, P7S2CodecTestSupport.decode(
                CastIntentPayload.STREAM_CODEC, encoded));
    }

    @Test
    void entityOnlyRoundTripsAtEveryCanonicalVarIntWidthOneThroughFive() {
        var entityIds = new int[] {1, 0x80, 0x4000, 0x20_0000, 0x1000_0000};

        for (var index = 0; index < entityIds.length; index++) {
            var payload = payload(1, 0, null, new EntityHint(entityIds[index]));
            var encoded = P7S2CodecTestSupport.encode(
                    CastIntentPayload.STREAM_CODEC, payload);

            assertEquals(12 + index, encoded.length, "VarInt width " + (index + 1));
            assertEquals(payload, P7S2CodecTestSupport.decode(
                    CastIntentPayload.STREAM_CODEC, encoded));
        }
    }

    @Test
    void aimAndMaximumEntityRoundTripAtTheExactTwentyTwoByteMaximum() {
        var payload = payload(
                1,
                63,
                new AimHint(-32767, 32767, 1),
                new EntityHint(Integer.MAX_VALUE));

        var encoded = P7S2CodecTestSupport.encode(CastIntentPayload.STREAM_CODEC, payload);

        assertEquals(P7NetworkBounds.ACTUAL_MAX_CAST_INTENT_BODY_BYTES, encoded.length);
        assertEquals(22, encoded.length);
        assertEquals(payload, P7S2CodecTestSupport.decode(
                CastIntentPayload.STREAM_CODEC, encoded));
    }

    @Test
    void fixedLongSequenceDecodesStructurallyForLaterSection58Classification() {
        for (var sequence : new long[] {0L, -1L, Long.MIN_VALUE}) {
            var decoded = P7S2CodecTestSupport.decode(
                    CastIntentPayload.STREAM_CODEC,
                    rawIntent(sequence, 0, 0, 0, buffer -> {}));

            assertEquals(sequence, decoded.intent().sequence());
            P7S2CodecTestSupport.assertEncodeFailure(
                    CastIntentPayload.STREAM_CODEC, decoded);
        }
    }

    @Test
    void positiveSequenceAndSlotBoundariesRoundTripWithoutWrap() {
        for (var payload : new CastIntentPayload[] {
            payload(1, 0, null, null),
            payload(Long.MAX_VALUE, 63, null, null)
        }) {
            var encoded = P7S2CodecTestSupport.encode(
                    CastIntentPayload.STREAM_CODEC, payload);
            assertEquals(payload, P7S2CodecTestSupport.decode(
                    CastIntentPayload.STREAM_CODEC, encoded));
        }
    }

    @Test
    void unknownInputKindReservedMaskAndInvalidSlotsAreMalformed() {
        var malformedBodies = new byte[][] {
            rawIntent(1, 0, 1, 0, buffer -> {}),
            rawIntent(1, 0, 0, 1 << 2, buffer -> {}),
            rawIntent(1, 64, 0, 0, buffer -> {}),
            rawIntent(1, 255, 0, 0, buffer -> {})
        };

        for (var body : malformedBodies) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    CastIntentPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void presenceMaskMustExactlyMatchItsOptionalWireFields() {
        var missingAim = rawIntent(1, 0, 0, AIM_MASK, buffer -> {});
        var missingEntity = rawIntent(1, 0, 0, ENTITY_MASK, buffer -> {});
        var unexpectedAim = rawIntent(1, 0, 0, 0, buffer -> {
            buffer.writeShort(1);
            buffer.writeShort(2);
            buffer.writeShort(3);
        });
        var unexpectedEntity = rawIntent(1, 0, 0, 0, buffer -> buffer.writeVarInt(1));

        for (var body : new byte[][] {
            missingAim, missingEntity, unexpectedAim, unexpectedEntity
        }) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    CastIntentPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void reservedQ15ValueAndAllZeroAimAreMalformed() {
        var reserved = rawIntent(1, 0, 0, AIM_MASK, buffer -> {
            buffer.writeShort(-32768);
            buffer.writeShort(1);
            buffer.writeShort(1);
        });
        var zero = rawIntent(1, 0, 0, AIM_MASK, buffer -> {
            buffer.writeShort(0);
            buffer.writeShort(0);
            buffer.writeShort(0);
        });

        P7S2CodecTestSupport.assertDecodeFailure(
                CastIntentPayload.STREAM_CODEC, reserved);
        P7S2CodecTestSupport.assertDecodeFailure(
                CastIntentPayload.STREAM_CODEC, zero);
    }

    @Test
    void entityHintRejectsZeroNegativeOverlongOverflowAndSixByteVarInts() {
        var prefix = rawIntent(1, 0, 0, ENTITY_MASK, buffer -> {});
        var negative = P7S2CodecTestSupport.body(buffer -> {
            buffer.writeLong(1);
            buffer.writeByte(0);
            buffer.writeByte(0);
            buffer.writeByte(ENTITY_MASK);
            buffer.writeVarInt(-1);
        });
        var malformedBodies = new byte[][] {
            P7S2CodecTestSupport.append(prefix, 0x00),
            negative,
            P7S2CodecTestSupport.append(prefix, 0x81, 0x00),
            P7S2CodecTestSupport.append(prefix, 0x80, 0x80, 0x80, 0x80, 0x80),
            P7S2CodecTestSupport.append(prefix, 0xff, 0xff, 0xff, 0xff, 0x08),
            P7S2CodecTestSupport.append(
                    prefix, 0x80, 0x80, 0x80, 0x80, 0x80, 0x00)
        };

        for (var body : malformedBodies) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    CastIntentPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void truncatedFixedAimAndVarIntFieldsAreDecoderFailures() {
        var minimum = P7S2CodecTestSupport.encode(
                CastIntentPayload.STREAM_CODEC, payload(1, 0, null, null));
        for (var length = 0; length < minimum.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    CastIntentPayload.STREAM_CODEC, Arrays.copyOf(minimum, length));
        }

        var aim = P7S2CodecTestSupport.encode(
                CastIntentPayload.STREAM_CODEC,
                payload(1, 0, new AimHint(1, 2, 3), null));
        for (var length = 11; length < aim.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    CastIntentPayload.STREAM_CODEC, Arrays.copyOf(aim, length));
        }

        var truncatedVarInt = P7S2CodecTestSupport.append(
                rawIntent(1, 0, 0, ENTITY_MASK, buffer -> {}), 0x80);
        P7S2CodecTestSupport.assertDecodeFailure(
                CastIntentPayload.STREAM_CODEC, truncatedVarInt);
    }

    @Test
    void trailingAndThirtyThreeByteBodiesFailBeforeAnyHandlerInvocation() {
        var valid = P7S2CodecTestSupport.encode(
                CastIntentPayload.STREAM_CODEC, payload(1, 0, null, null));
        var handlerCalls = new AtomicInteger();

        for (var body : new byte[][] {
            P7S2CodecTestSupport.append(valid, 0x00), new byte[33]
        }) {
            try (var owned = P7S2CodecTestSupport.bufferContaining(body)) {
                assertThrows(DecoderException.class, () -> {
                    CastIntentPayload.STREAM_CODEC.decode(owned.buffer());
                    handlerCalls.incrementAndGet();
                });
                owned.assertCallerStillOwnsExactlyOneReference();
            }
        }
        assertEquals(0, handlerCalls.get());
    }

    @Test
    void successfulEncodeAndDecodeNeverRetainOrReleaseCallerOwnedBuffers() {
        var payload = payload(1, 0, null, new EntityHint(Integer.MAX_VALUE));
        byte[] encoded;

        try (var owned = P7S2CodecTestSupport.emptyBuffer()) {
            CastIntentPayload.STREAM_CODEC.encode(owned.buffer(), payload);
            assertEquals(1, owned.referenceCount());
            encoded = new byte[owned.buffer().readableBytes()];
            owned.buffer().getBytes(owned.buffer().readerIndex(), encoded);
        }
        try (var owned = P7S2CodecTestSupport.bufferContaining(encoded)) {
            assertEquals(payload, CastIntentPayload.STREAM_CODEC.decode(owned.buffer()));
            assertEquals(1, owned.referenceCount());
        }
    }

    private static CastIntentPayload payload(
            long sequence, int slot, AimHint aim, EntityHint entity) {
        var mask = (aim == null ? 0 : AIM_MASK) | (entity == null ? 0 : ENTITY_MASK);
        return new CastIntentPayload(new CastIntent(
                sequence, slot, CastInputKind.CAST, mask, aim, entity));
    }

    private static byte[] rawIntent(
            long sequence,
            int slot,
            int inputKind,
            int mask,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> tail) {
        return P7S2CodecTestSupport.body(buffer -> {
            buffer.writeLong(sequence);
            buffer.writeByte(slot);
            buffer.writeByte(inputKind);
            buffer.writeByte(mask);
            tail.accept(buffer);
        });
    }
}
