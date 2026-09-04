package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkillCooldownSyncPayloadCodecTest {
    @Test
    void emptyFullSnapshotRoundTripsAtTheExactNineByteMinimum() {
        var payload = payload(1, List.of());

        var encoded = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, payload);

        assertEquals(9, encoded.length);
        assertEquals(payload, P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC, encoded));
        assertEquals("gramarye:skill_cooldown_sync",
                SkillCooldownSyncPayload.TYPE.id().toString());
    }

    @Test
    void oneEntryCoversSlotAndRemainingTickBoundaries() {
        var cases = List.of(
                payload(1, List.of(new CooldownSnapshotEntry(0, 1))),
                payload(Long.MAX_VALUE, List.of(
                        new CooldownSnapshotEntry(63, Integer.MAX_VALUE))));

        var minimum = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, cases.get(0));
        var maximumValue = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, cases.get(1));

        assertEquals(11, minimum.length);
        assertEquals(15, maximumValue.length);
        for (var payload : cases) {
            assertEquals(payload, P7S2CodecTestSupport.decode(
                    SkillCooldownSyncPayload.STREAM_CODEC,
                    P7S2CodecTestSupport.encode(
                            SkillCooldownSyncPayload.STREAM_CODEC, payload)));
        }
    }

    @Test
    void sixtyFourMaximumWidthEntriesRoundTripAtExactlyThreeHundredNinetyThreeBytes() {
        var entries = new ArrayList<CooldownSnapshotEntry>();
        for (var slot = 0; slot < 64; slot++) {
            entries.add(new CooldownSnapshotEntry(slot, Integer.MAX_VALUE));
        }
        var payload = payload(1, entries);

        var encoded = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, payload);

        assertEquals(393, encoded.length);
        assertEquals(payload.snapshot().encodedBodySize(), encoded.length);
        assertEquals(P7NetworkBounds.MAX_SYNC_ENTRIES_PER_PACKET,
                payload.snapshot().entries().size());
        assertEquals(payload, P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC, encoded));
    }

    @Test
    void entryCountSixtyFiveIsRejectedBeforeEntryAllocation() {
        var body = P7S2CodecTestSupport.body(buffer -> {
            buffer.writeLong(1);
            buffer.writeVarInt(65);
        });

        P7S2CodecTestSupport.assertDecodeFailure(
                SkillCooldownSyncPayload.STREAM_CODEC, body);
    }

    @Test
    void duplicateAndUnsortedSlotsAreMalformed() {
        var duplicate = rawEntries(1, 2, buffer -> {
            buffer.writeByte(0);
            buffer.writeVarInt(1);
            buffer.writeByte(0);
            buffer.writeVarInt(2);
        });
        var unsorted = rawEntries(1, 2, buffer -> {
            buffer.writeByte(1);
            buffer.writeVarInt(1);
            buffer.writeByte(0);
            buffer.writeVarInt(2);
        });

        P7S2CodecTestSupport.assertDecodeFailure(
                SkillCooldownSyncPayload.STREAM_CODEC, duplicate);
        P7S2CodecTestSupport.assertDecodeFailure(
                SkillCooldownSyncPayload.STREAM_CODEC, unsorted);
    }

    @Test
    void zeroNegativeRemainingTicksAndInvalidSlotsAreMalformed() {
        var zero = rawEntries(1, 1, buffer -> {
            buffer.writeByte(0);
            buffer.writeVarInt(0);
        });
        var negative = rawEntries(1, 1, buffer -> {
            buffer.writeByte(0);
            buffer.writeVarInt(-1);
        });
        var slot64 = rawEntries(1, 1, buffer -> {
            buffer.writeByte(64);
            buffer.writeVarInt(1);
        });
        var slot255 = rawEntries(1, 1, buffer -> {
            buffer.writeByte(255);
            buffer.writeVarInt(1);
        });

        for (var body : new byte[][] {zero, negative, slot64, slot255}) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    SkillCooldownSyncPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void countAndRemainingTicksRequireStrictCanonicalBoundedVarInts() {
        var longPrefix = P7S2CodecTestSupport.body(buffer -> buffer.writeLong(1));
        var entryPrefix = rawEntries(1, 1, buffer -> buffer.writeByte(0));
        var malformedBodies = new byte[][] {
            P7S2CodecTestSupport.append(longPrefix, 0x80, 0x00),
            P7S2CodecTestSupport.append(
                    longPrefix, 0x80, 0x80, 0x80, 0x80, 0x80),
            P7S2CodecTestSupport.append(entryPrefix, 0x81, 0x00),
            P7S2CodecTestSupport.append(
                    entryPrefix, 0x80, 0x80, 0x80, 0x80, 0x80),
            P7S2CodecTestSupport.append(
                    entryPrefix, 0xff, 0xff, 0xff, 0xff, 0x08),
            P7S2CodecTestSupport.append(entryPrefix, 0x80)
        };

        for (var body : malformedBodies) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    SkillCooldownSyncPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void syncSequenceMustBePositiveAndAcceptsLongMaximumWithoutWrap() {
        for (var sequence : new long[] {0L, -1L, Long.MIN_VALUE}) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    SkillCooldownSyncPayload.STREAM_CODEC,
                    rawEntries(sequence, 0, buffer -> {}));
        }

        var maximum = payload(Long.MAX_VALUE, List.of());
        assertEquals(maximum, P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.encode(
                        SkillCooldownSyncPayload.STREAM_CODEC, maximum)));
    }

    @Test
    void truncatedRequiredAndEntryFieldsAreDecoderFailures() {
        var empty = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, payload(1, List.of()));
        for (var length = 0; length < empty.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    SkillCooldownSyncPayload.STREAM_CODEC,
                    Arrays.copyOf(empty, length));
        }

        var one = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC,
                payload(1, List.of(new CooldownSnapshotEntry(0, 128))));
        for (var length = 9; length < one.length; length++) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    SkillCooldownSyncPayload.STREAM_CODEC,
                    Arrays.copyOf(one, length));
        }
    }

    @Test
    void trailingAndFourThousandNinetySevenByteBodiesAreDecoderFailures() {
        var valid = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, payload(1, List.of()));

        P7S2CodecTestSupport.assertDecodeFailure(
                SkillCooldownSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.append(valid, 0x00));
        P7S2CodecTestSupport.assertDecodeFailure(
                SkillCooldownSyncPayload.STREAM_CODEC, new byte[4097]);
    }

    @Test
    void snapshotDefensivelyCopiesSourceAndExposesAnUnmodifiableList() {
        var source = new ArrayList<>(List.of(new CooldownSnapshotEntry(0, 1)));
        var snapshot = new SkillCooldownSnapshot(1, source);

        source.clear();
        source.add(new CooldownSnapshotEntry(1, 2));

        assertEquals(List.of(new CooldownSnapshotEntry(0, 1)), snapshot.entries());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.entries().add(new CooldownSnapshotEntry(2, 3)));

        var decoded = P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.encode(
                        SkillCooldownSyncPayload.STREAM_CODEC,
                        new SkillCooldownSyncPayload(snapshot)));
        assertEquals(List.of(new CooldownSnapshotEntry(0, 1)),
                decoded.snapshot().entries());
    }

    @Test
    void successfulEncodeAndDecodeNeverRetainOrReleaseCallerOwnedBuffers() {
        var payload = payload(
                1, List.of(new CooldownSnapshotEntry(63, Integer.MAX_VALUE)));
        byte[] encoded;

        try (var owned = P7S2CodecTestSupport.emptyBuffer()) {
            SkillCooldownSyncPayload.STREAM_CODEC.encode(owned.buffer(), payload);
            assertEquals(1, owned.referenceCount());
            encoded = new byte[owned.buffer().readableBytes()];
            owned.buffer().getBytes(owned.buffer().readerIndex(), encoded);
        }
        try (var owned = P7S2CodecTestSupport.bufferContaining(encoded)) {
            assertEquals(payload,
                    SkillCooldownSyncPayload.STREAM_CODEC.decode(owned.buffer()));
            assertEquals(1, owned.referenceCount());
        }
    }

    private static SkillCooldownSyncPayload payload(
            long sequence, List<CooldownSnapshotEntry> entries) {
        return new SkillCooldownSyncPayload(new SkillCooldownSnapshot(sequence, entries));
    }

    private static byte[] rawEntries(
            long sequence,
            int count,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> entries) {
        return P7S2CodecTestSupport.body(buffer -> {
            buffer.writeLong(sequence);
            buffer.writeVarInt(count);
            entries.accept(buffer);
        });
    }
}
