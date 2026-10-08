package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SkillCooldownSyncPayloadCodecTest {
    @Test
    void emptyFullSnapshotRoundTripsAtTheExactTwentySevenByteMinimum() {
        var payload = payload(1, List.of());

        var encoded = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, payload);

        assertEquals(27, encoded.length);
        assertEquals(payload, P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC, encoded));
        assertEquals("gramarye:skill_cooldown_sync",
                SkillCooldownSyncPayload.TYPE.id().toString());
    }

    @Test
    void oneEntryCoversSlotAndRemainingTickBoundaries() {
        var cases = List.of(
                payload(1, List.of(P7S2CodecTestSupport.active(0, 1))),
                payload(Long.MAX_VALUE, List.of(
                        P7S2CodecTestSupport.active(63, 600))));

        var minimum = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, cases.get(0));
        var maximumValue = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, cases.get(1));

        assertEquals(51, minimum.length);
        assertEquals(52, maximumValue.length);
        for (var payload : cases) {
            assertEquals(payload, P7S2CodecTestSupport.decode(
                    SkillCooldownSyncPayload.STREAM_CODEC,
                    P7S2CodecTestSupport.encode(
                            SkillCooldownSyncPayload.STREAM_CODEC, payload)));
        }
    }

    @Test
    void sixtyFourMaximumWidthEntriesRoundTripAtExactlySixteenHundredTwentySevenBytes() {
        var entries = new ArrayList<CooldownSnapshotEntry>();
        for (var slot = 0; slot < 64; slot++) {
            entries.add(P7S2CodecTestSupport.active(slot, 600));
        }
        var payload = payload(1, entries);

        var encoded = P7S2CodecTestSupport.encode(
                SkillCooldownSyncPayload.STREAM_CODEC, payload);

        assertEquals(1627, encoded.length);
        assertEquals(payload.snapshot().encodedBodySize(), encoded.length);
        assertEquals(P7NetworkBounds.MAX_SYNC_ENTRIES_PER_PACKET,
                payload.snapshot().entries().size());
        assertEquals(payload, P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC, encoded));
    }

    @Test
    void entryCountSixtyFiveIsRejectedBeforeEntryAllocation() {
        var body = P7S2CodecTestSupport.body(buffer -> {
            header(buffer, 1);
            buffer.writeVarInt(65);
        });

        P7S2CodecTestSupport.assertDecodeFailure(
                SkillCooldownSyncPayload.STREAM_CODEC, body);
        var oversized = new java.util.AbstractList<P7ServerAuthorizationBoundary.SyncEntry>() {
            @Override public int size() { return 65; }
            @Override public P7ServerAuthorizationBoundary.SyncEntry get(int index) {
                throw new AssertionError("oversized projection must be rejected before copying");
            }
        };
        assertThrows(P7SemanticInvariantException.class, () -> new P7ServerAuthorizationBoundary.SyncProjection(
                1, 0, P7ServerAuthorizationBoundary.SyncSourceState.AVAILABLE,
                P7ServerAuthorizationBoundary.SyncReason.NONE, oversized));
    }

    @Test
    void duplicateAndUnsortedSlotsAreMalformed() {
        var duplicate = rawEntries(1, 2, buffer -> {
            activePrefix(buffer, 0);
            buffer.writeVarInt(1);
            activePrefix(buffer, 0);
            buffer.writeVarInt(2);
        });
        var unsorted = rawEntries(1, 2, buffer -> {
            activePrefix(buffer, 1);
            buffer.writeVarInt(1);
            activePrefix(buffer, 0);
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
            activePrefix(buffer, 0);
            buffer.writeVarInt(0);
        });
        var negative = rawEntries(1, 1, buffer -> {
            activePrefix(buffer, 0);
            buffer.writeVarInt(-1);
        });
        var slot64 = rawEntries(1, 1, buffer -> {
            activePrefix(buffer, 64);
            buffer.writeVarInt(1);
        });
        var slot255 = rawEntries(1, 1, buffer -> {
            activePrefix(buffer, 255);
            buffer.writeVarInt(1);
        });

        for (var body : new byte[][] {zero, negative, slot64, slot255}) {
            P7S2CodecTestSupport.assertDecodeFailure(
                    SkillCooldownSyncPayload.STREAM_CODEC, body);
        }
    }

    @Test
    void countAndRemainingTicksRequireStrictCanonicalBoundedVarInts() {
        var longPrefix = P7S2CodecTestSupport.body(buffer -> header(buffer, 1));
        var entryPrefix = rawEntries(1, 1, buffer -> activePrefix(buffer, 0));
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
                payload(1, List.of(P7S2CodecTestSupport.active(0, 128))));
        for (var length = 27; length < one.length; length++) {
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
        var source = new ArrayList<>(List.of(P7S2CodecTestSupport.active(0, 1)));
        var snapshot = P7S2CodecTestSupport.cooldown(1, source);

        source.clear();
        source.add(P7S2CodecTestSupport.active(1, 2));

        assertEquals(List.of(P7S2CodecTestSupport.active(0, 1)), snapshot.entries());
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.entries().add(P7S2CodecTestSupport.active(2, 3)));

        var decoded = P7S2CodecTestSupport.decode(
                SkillCooldownSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.encode(
                        SkillCooldownSyncPayload.STREAM_CODEC,
                        new SkillCooldownSyncPayload(snapshot)));
        assertEquals(List.of(P7S2CodecTestSupport.active(0, 1)),
                decoded.snapshot().entries());
    }

    @Test
    void successfulEncodeAndDecodeNeverRetainOrReleaseCallerOwnedBuffers() {
        var payload = payload(
                1, List.of(P7S2CodecTestSupport.active(63, 600)));
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

    @Test
    void sourceAndEntryCrossFieldsAreIdenticalAtConstructorAndDecoder() {
        var reference = P7S2CodecTestSupport.reference(0);
        for (var state : P7ServerAuthorizationBoundary.SyncEntryState.values()) {
            for (var reason : P7ServerAuthorizationBoundary.SyncReason.values()) {
                for (int remaining : new int[] {0, 1, 600, 601}) {
                    boolean valid = switch (state) {
                        case READY -> reason == P7ServerAuthorizationBoundary.SyncReason.NONE && remaining == 0;
                        case ACTIVE -> (reason == P7ServerAuthorizationBoundary.SyncReason.NONE
                                || reason == P7ServerAuthorizationBoundary.SyncReason.SAVE_FAILED) && remaining >= 1 && remaining <= 600;
                        case PENDING -> (reason == P7ServerAuthorizationBoundary.SyncReason.PENDING_RELEASE
                                || reason == P7ServerAuthorizationBoundary.SyncReason.SAVE_FAILED) && remaining == 0;
                        case RECOVERY_REQUIRED -> (reason == P7ServerAuthorizationBoundary.SyncReason.OUTCOME_UNCERTAIN
                                || reason == P7ServerAuthorizationBoundary.SyncReason.RECOVERY_REQUIRED
                                || reason == P7ServerAuthorizationBoundary.SyncReason.SAVE_FAILED) && remaining == 0;
                        case UNAVAILABLE -> reason != P7ServerAuthorizationBoundary.SyncReason.NONE
                                && reason != P7ServerAuthorizationBoundary.SyncReason.PENDING_RELEASE
                                && reason != P7ServerAuthorizationBoundary.SyncReason.EQUIPMENT_UNKNOWN && remaining == 0;
                    };
                    var body = rawEntries(1, 1, b -> {
                        b.writeByte(0); b.writeLong(Long.MIN_VALUE); b.writeLong(1); b.writeInt(0);
                        b.writeByte(state.wireCode()); b.writeByte(reason.wireCode());
                        if (state == P7ServerAuthorizationBoundary.SyncEntryState.ACTIVE || remaining != 0) {
                            b.writeVarInt(remaining);
                        }
                    });
                    if (valid) {
                        var entry = new CooldownSnapshotEntry(0, reference, state, reason, remaining);
                        assertEquals(entry, P7S2CodecTestSupport.decode(SkillCooldownSyncPayload.STREAM_CODEC, body)
                                .snapshot().entries().getFirst());
                    } else {
                        assertThrows(RuntimeException.class,
                                () -> new CooldownSnapshotEntry(0, reference, state, reason, remaining));
                        P7S2CodecTestSupport.assertDecodeFailure(SkillCooldownSyncPayload.STREAM_CODEC, body);
                    }
                }
            }
        }
        for (var source : P7ServerAuthorizationBoundary.SyncSourceState.values()) {
            for (var reason : P7ServerAuthorizationBoundary.SyncReason.values()) {
                boolean valid = switch (source) {
                    case AVAILABLE -> reason == P7ServerAuthorizationBoundary.SyncReason.NONE;
                    case PARTIAL -> switch (reason) { case MALFORMED, UNSUPPORTED_VERSION, BOUNDS -> true; default -> false; };
                    case UNAVAILABLE -> switch (reason) {
                        case NOT_READY, MALFORMED, UNSUPPORTED_VERSION, BOUNDS, CLOCK, PROVIDER,
                                EQUIPMENT_UNKNOWN, SOURCE_UNAVAILABLE -> true;
                        default -> false;
                    };
                };
                var bytes = P7S2CodecTestSupport.body(b -> {
                    b.writeLong(1); b.writeLong(1); b.writeLong(0);
                    b.writeByte(source.wireCode()); b.writeByte(reason.wireCode()); b.writeVarInt(0);
                });
                if (valid) {
                    assertEquals(new SkillCooldownSnapshot(1, 1, 0, source, reason, List.of()),
                            P7S2CodecTestSupport.decode(SkillCooldownSyncPayload.STREAM_CODEC, bytes).snapshot());
                } else {
                    assertThrows(RuntimeException.class, () -> new SkillCooldownSnapshot(1, 1, 0, source, reason, List.of()));
                    P7S2CodecTestSupport.assertDecodeFailure(SkillCooldownSyncPayload.STREAM_CODEC, bytes);
                }
            }
        }
    }

    @Test
    void sentinelUnknownEnumsWholeUnavailableAndExactReferenceAreNotGuessed() {
        var sentinel = new SkillCooldownSnapshot(1, 0, 0,
                P7ServerAuthorizationBoundary.SyncSourceState.UNAVAILABLE,
                P7ServerAuthorizationBoundary.SyncReason.SOURCE_UNAVAILABLE, List.of());
        assertEquals(sentinel, P7S2CodecTestSupport.decode(SkillCooldownSyncPayload.STREAM_CODEC,
                P7S2CodecTestSupport.encode(SkillCooldownSyncPayload.STREAM_CODEC, new SkillCooldownSyncPayload(sentinel))).snapshot());
        for (long epoch : new long[] {-1, 0}) {
            assertThrows(RuntimeException.class, () -> new SkillCooldownSnapshot(1, epoch, 0,
                    P7ServerAuthorizationBoundary.SyncSourceState.AVAILABLE,
                    P7ServerAuthorizationBoundary.SyncReason.NONE, List.of()));
        }
        assertThrows(RuntimeException.class, () -> new SkillCooldownSnapshot(1, 1, -1,
                P7ServerAuthorizationBoundary.SyncSourceState.AVAILABLE,
                P7ServerAuthorizationBoundary.SyncReason.NONE, List.of()));
        assertThrows(RuntimeException.class, () -> new SkillCooldownSnapshot(1, 0, 1,
                sentinel.sourceState(), sentinel.sourceReason(), List.of()));
        assertThrows(RuntimeException.class, () -> new SkillCooldownSnapshot(1, 1, 0,
                sentinel.sourceState(), sentinel.sourceReason(), List.of(P7S2CodecTestSupport.active(0, 1))));
        assertThrows(RuntimeException.class, () -> new SkillCooldownSnapshot(1, 1, 0,
                sentinel.sourceState(), P7ServerAuthorizationBoundary.SyncReason.EQUIPMENT_UNKNOWN,
                List.of(P7S2CodecTestSupport.active(0, 1))));
        var valid = P7S2CodecTestSupport.encode(SkillCooldownSyncPayload.STREAM_CODEC,
                payload(1, List.of(P7S2CodecTestSupport.active(0, 600))));
        for (int offset : new int[] {24, 25, 48, 49}) {
            var invalid = valid.clone(); invalid[offset] = (byte) 255;
            P7S2CodecTestSupport.assertDecodeFailure(SkillCooldownSyncPayload.STREAM_CODEC, invalid);
        }
        var negativeRevision = valid.clone();
        java.util.Arrays.fill(negativeRevision, 44, 48, (byte) 255);
        P7S2CodecTestSupport.assertDecodeFailure(SkillCooldownSyncPayload.STREAM_CODEC, negativeRevision);
        P7S2CodecTestSupport.assertDecodeFailure(SkillCooldownSyncPayload.STREAM_CODEC, new byte[1628]);
    }

    private static void header(net.minecraft.network.RegistryFriendlyByteBuf buffer, long sequence) {
        buffer.writeLong(sequence); buffer.writeLong(1); buffer.writeLong(0);
        buffer.writeByte(0); buffer.writeByte(0);
    }

    private static void activePrefix(net.minecraft.network.RegistryFriendlyByteBuf buffer, int slot) {
        buffer.writeByte(slot); buffer.writeLong(Long.MIN_VALUE); buffer.writeLong(slot + 1L);
        buffer.writeInt(0); buffer.writeByte(1); buffer.writeByte(0);
    }

    private static SkillCooldownSyncPayload payload(
            long sequence, List<CooldownSnapshotEntry> entries) {
        return new SkillCooldownSyncPayload(P7S2CodecTestSupport.cooldown(sequence, entries));
    }

    private static byte[] rawEntries(
            long sequence,
            int count,
            java.util.function.Consumer<net.minecraft.network.RegistryFriendlyByteBuf> entries) {
        return P7S2CodecTestSupport.body(buffer -> {
            header(buffer, sequence);
            buffer.writeVarInt(count);
            entries.accept(buffer);
        });
    }
}
