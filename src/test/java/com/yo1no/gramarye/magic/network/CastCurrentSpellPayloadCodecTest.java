package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.*;
import static com.yo1no.gramarye.magic.network.P7S2CodecTestSupport.*;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class CastCurrentSpellPayloadCodecTest {
    @Test void exactEightByteBigEndianBodyHasNoOtherAuthorityFields() {
        for (long sequence : new long[] {1, 2, 256, 65_536, Long.MAX_VALUE}) {
            var payload = new CastCurrentSpellPayload(sequence);
            byte[] encoded = encode(CastCurrentSpellPayload.STREAM_CODEC, payload);
            assertEquals(8, encoded.length);
            assertArrayEquals(ByteBuffer.allocate(8).putLong(sequence).array(), encoded);
            assertEquals(payload, decode(CastCurrentSpellPayload.STREAM_CODEC, encoded));
        }
        assertEquals("gramarye-normal-spell-v1", CastCurrentSpellPayload.NORMAL_PROTOCOL_VERSION);
        assertEquals("gramarye:cast_current_spell", CastCurrentSpellPayload.TYPE.id().toString());
    }

    @Test void nonPositiveI64StructuresReachOriginalP7SequenceSemantics() {
        for (long sequence : new long[] {0, -1, Long.MIN_VALUE}) {
            var payload = decode(CastCurrentSpellPayload.STREAM_CODEC,
                    ByteBuffer.allocate(8).putLong(sequence).array());
            assertEquals(sequence, payload.sequence());
            assertEquals(IntentSequenceState.Classification.INVALID,
                    IntentSequenceState.initial().evaluate(payload.sequence()).classification());
            try (var owned = emptyBuffer()) {
                assertThrows(EncoderException.class,
                        () -> CastCurrentSpellPayload.STREAM_CODEC.encode(owned.buffer(), payload));
                assertEquals(0, owned.buffer().writerIndex());
            }
        }
    }

    @Test void everyTruncationAndTrailingByteRefusesBeforeReadingABody() {
        byte[] valid = ByteBuffer.allocate(8).putLong(1).array();
        for (int size = 0; size <= 96; size++) {
            if (size == 8) { continue; }
            try (var owned = bufferContaining(Arrays.copyOf(valid, size))) {
                assertThrows(DecoderException.class,
                        () -> CastCurrentSpellPayload.STREAM_CODEC.decode(owned.buffer()));
                assertEquals(0, owned.buffer().readerIndex());
            }
        }
        assertDecodeFailure(CastCurrentSpellPayload.STREAM_CODEC, Arrays.copyOf(valid, 4096));
    }

    @Test void decodingConsumesTheWholeEightByteBodyAndKeepsCallerOwnership() {
        try (var owned = bufferContaining(ByteBuffer.allocate(8).putLong(Long.MAX_VALUE).array())) {
            assertEquals(Long.MAX_VALUE, CastCurrentSpellPayload.STREAM_CODEC.decode(owned.buffer()).sequence());
            assertEquals(0, owned.buffer().readableBytes());
            owned.assertCallerStillOwnsExactlyOneReference();
        }
    }
}
