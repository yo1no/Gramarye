package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class PlayerManaSyncPayloadCodecTest {
    private static final long MAX_BALANCE = 1_000_000_000L;

    @Test
    void availableZeroAndMaximumBalancesRoundTripAtExactlySeventeenBytes() {
        for (var balance : new long[] {0L, MAX_BALANCE}) {
            var payload = payload(
                    1, PlayerManaSnapshot.Availability.AVAILABLE, balance);
            var encoded = P7S2CodecTestSupport.encode(
                    PlayerManaSyncPayload.STREAM_CODEC, payload);

            assertEquals(17, encoded.length);
            assertEquals(payload, P7S2CodecTestSupport.decode(
                    PlayerManaSyncPayload.STREAM_CODEC, encoded));
        }
    }

    @Test
    void unavailableZeroAndMaximumSequenceRoundTripInCanonicalFieldOrder() {
        var payload = payload(
                Long.MAX_VALUE, PlayerManaSnapshot.Availability.UNAVAILABLE, 0);

        var encoded = P7S2CodecTestSupport.encode(
                PlayerManaSyncPayload.STREAM_CODEC, payload);
        var fields = ByteBuffer.wrap(encoded);

        assertEquals(17, encoded.length);
        assertEquals(Long.MAX_VALUE, fields.getLong());
        assertEquals(1, Byte.toUnsignedInt(fields.get()));
        assertEquals(0L, fields.getLong());
        assertEquals(0, fields.remaining());
        assertEquals(payload, P7S2CodecTestSupport.decode(
                PlayerManaSyncPayload.STREAM_CODEC, encoded));
        assertEquals("gramarye:player_mana_sync",
                PlayerManaSyncPayload.TYPE.id().toString());
    }

    @Test
    void unavailableNonzeroBalanceAndUnknownAvailabilityAreMalformed() {
        var unavailableNonzero = rawSnapshot(1, 1, 1);
        var unknownAvailability = rawSnapshot(1, 2, 0);

        P7S2CodecTestSupport.assertDecodeFailure(
                PlayerManaSyncPayload.STREAM_CODEC, unavailableNonzero);
        P7S2CodecTestSupport.assertDecodeFailure(
                PlayerManaSyncPayload.STREAM_CODEC, unknownAvailability);
    }

    @Test
    void syncSequenceMustBePositiveAndNeverWrap() {
        for (var sequence : new long[] {0L, -1L, Long.MIN_VALUE}) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    PlayerManaSyncPayload.STREAM_CODEC,
                    rawSnapshot(sequence, 0, 0));
        }

        var maximum = payload(
                Long.MAX_VALUE, PlayerManaSnapshot.Availability.AVAILABLE, 0);
        assertEquals(maximum, P7S2CodecTestSupport.decode(
                PlayerManaSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.encode(
                        PlayerManaSyncPayload.STREAM_CODEC, maximum)));
    }

    @Test
    void availableBalanceRejectsNegativeAndAboveMaximumValues() {
        for (var balance : new long[] {-1L, Long.MIN_VALUE, MAX_BALANCE + 1}) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    PlayerManaSyncPayload.STREAM_CODEC,
                    rawSnapshot(1, 0, balance));
        }
    }

    @Test
    void everyTruncationAndAnyTrailingByteAreDecoderFailures() {
        var valid = P7S2CodecTestSupport.encode(
                PlayerManaSyncPayload.STREAM_CODEC,
                payload(1, PlayerManaSnapshot.Availability.AVAILABLE, 0));

        for (var length = 0; length < valid.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    PlayerManaSyncPayload.STREAM_CODEC,
                    Arrays.copyOf(valid, length));
        }
        P7S2CodecTestSupport.assertDecodeFailure(
                PlayerManaSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.append(valid, 0x00));
    }

    @Test
    void fourThousandNinetySevenByteBodyExceedsTheHardSyncCeiling() {
        P7S2CodecTestSupport.assertDecodeFailure(
                PlayerManaSyncPayload.STREAM_CODEC, new byte[4097]);
    }

    @Test
    void decodedSnapshotIsImmutableAndIndependentOfReleasedInputBytes() {
        var encoded = P7S2CodecTestSupport.encode(
                PlayerManaSyncPayload.STREAM_CODEC,
                payload(7, PlayerManaSnapshot.Availability.AVAILABLE, 73));
        var decoded = P7S2CodecTestSupport.decode(
                PlayerManaSyncPayload.STREAM_CODEC, encoded);

        Arrays.fill(encoded, (byte) 0);

        assertEquals(7L, decoded.snapshot().syncSequence());
        assertEquals(PlayerManaSnapshot.Availability.AVAILABLE,
                decoded.snapshot().availability());
        assertEquals(73L, decoded.snapshot().balance());
        assertTrue(Arrays.stream(PlayerManaSnapshot.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
    }

    @Test
    void successfulEncodeAndDecodeNeverRetainOrReleaseCallerOwnedBuffers() {
        var payload = payload(
                1, PlayerManaSnapshot.Availability.AVAILABLE, MAX_BALANCE);
        byte[] encoded;

        try (var owned = P7S2CodecTestSupport.emptyBuffer()) {
            PlayerManaSyncPayload.STREAM_CODEC.encode(owned.buffer(), payload);
            assertEquals(1, owned.referenceCount());
            encoded = new byte[owned.buffer().readableBytes()];
            owned.buffer().getBytes(owned.buffer().readerIndex(), encoded);
        }
        try (var owned = P7S2CodecTestSupport.bufferContaining(encoded)) {
            assertEquals(payload,
                    PlayerManaSyncPayload.STREAM_CODEC.decode(owned.buffer()));
            assertEquals(1, owned.referenceCount());
        }
    }

    private static PlayerManaSyncPayload payload(
            long sequence,
            PlayerManaSnapshot.Availability availability,
            long balance) {
        return new PlayerManaSyncPayload(
                new PlayerManaSnapshot(sequence, availability, balance));
    }

    private static byte[] rawSnapshot(long sequence, int availability, long balance) {
        return P7S2CodecTestSupport.body(buffer -> {
            buffer.writeLong(sequence);
            buffer.writeByte(availability);
            buffer.writeLong(balance);
        });
    }
}
