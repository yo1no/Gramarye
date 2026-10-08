package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

/** Actual logical encoder/reader checks; deliberately not native disk or crash qualification. */
final class P11CooldownNbtBoundaryTest {
    private static final UUID KEY = new UUID(-1L, Long.MIN_VALUE + 7);
    private static final UUID ATTEMPT = new UUID(Long.MIN_VALUE, -1L);

    @Test
    void normalFormsMatchActualWriteAnyTagNotAnEstimatedFormula() throws Exception {
        assertEquals(68, bytes(encode(Map.of())));
        var pending = pending(KEY);
        var active = pending.active(1002);
        var uncertain = pending.uncertain(P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, true, 1002);
        for (var pair : List.of(new Form(pending, 180, 46148), new Form(active, 223, 57156),
                new Form(uncertain, 296, 75844))) {
            var one = encode(Map.of(KEY, pair.entry));
            assertEquals(68 + pair.payload, bytes(one));
            var entry = one.getList("entries", Tag.TAG_COMPOUND).getCompound(0);
            assertEquals(1 + pair.payload, bytes(entry));
            var entries = new LinkedHashMap<UUID, P11CastCooldownData.Entry>();
            for (int i = 0; i < 256; i++) {
                var id = new UUID(0, i);
                entries.put(id, pair.entry.kind == 0 ? pending(id).active(1002)
                        : pair.entry.kind == 1 ? pending(id)
                        : pending(id).uncertain(P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, true, 1002));
            }
            var encoded = encode(entries);
            assertEquals(pair.root256, bytes(encoded));
            assertEquals(pair.root256, P11CastCooldownNbtSize.measure(encoded, 90180, 17).observedAtLeast());
            assertTrue(P11CastCooldownNbtSize.measure(encoded, 90180, 4).fits());
            assertIdempotent(encoded);
        }
    }

    @Test
    void preservationWrappersReachExactLocalAndWholeLimitsAndRemainIdempotent() throws Exception {
        var entries = new LinkedHashMap<UUID, P11CastCooldownData.Entry>();
        for (int i = 0; i < 256; i++) {
            UUID id = new UUID(0, i);
            var raw = encode(Map.of(id, pending(id).uncertain(
                    P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, true, 1002)))
                    .getList("entries", Tag.TAG_COMPOUND).getCompound(0);
            assertEquals(297, bytes(raw));
            entries.put(id, P11CastCooldownData.Entry.raw(id, P11CastCooldownData.Reason.MALFORMED, raw));
        }
        var routed = encode(entries);
        assertEquals(353, bytes(routed.getList("entries", Tag.TAG_COMPOUND).getCompound(0)));
        assertEquals(90180, bytes(routed));
        assertIdempotent(routed);
        var whole = P11CastCooldownCodec.write(P11CastCooldownData.raw(P11CastCooldownData.Reason.MALFORMED, routed));
        assertEquals(90226, bytes(whole));
        assertIdempotent(whole);
        var localMarker = encode(Map.of(KEY, P11CastCooldownData.Entry.marker(KEY,
                P11CastCooldownData.Reason.BYTE_LIMIT, 298, 297)));
        assertEquals(97, bytes(localMarker.getList("entries", Tag.TAG_COMPOUND).getCompound(0)));
        assertIdempotent(localMarker);
        var wholeMarker = P11CastCooldownCodec.write(P11CastCooldownData.marker(
                P11CastCooldownData.Reason.BYTE_LIMIT, 90181, 90180));
        assertEquals(87, bytes(wholeMarker));
        assertIdempotent(wholeMarker);
    }

    @Test
    void strictScalarTypesAndCrossFieldsQuarantineTheKeyWithoutSkippingIt() {
        var original = encode(Map.of(KEY, pending(KEY)));
        for (var field : List.of("revision", "policy_version", "duration_ticks")) {
            var input = original.copy(); var entry = input.getList("entries", Tag.TAG_COMPOUND).getCompound(0);
            entry.putLong(field, entry.getInt(field));
            assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        }
        for (var field : List.of("accepted_at", "release_not_after")) {
            var input = original.copy(); var entry = input.getList("entries", Tag.TAG_COMPOUND).getCompound(0);
            entry.putInt(field, (int) entry.getLong(field));
            assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        }
        for (int duration : new int[] {Integer.MIN_VALUE, -1, 0, 601, Integer.MAX_VALUE}) {
            var input = original.copy(); input.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putInt("duration_ticks", duration);
            assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        }
        var future = original.copy(); future.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putInt("policy_version", 2);
        assertLocal(future, P11CastCooldownData.Reason.UNSUPPORTED);
        var bound = original.copy(); bound.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putLong("release_not_after", 1102);
        assertLocal(bound, P11CastCooldownData.Reason.MALFORMED);
        var floor = original.copy(); floor.putLong("clock_floor", 999);
        assertLocal(floor, P11CastCooldownData.Reason.MALFORMED);
        assertEquals(ATTEMPT, P11CastCooldownCodec.read(original).entries.get(KEY).attemptId);
    }

    @Test
    void completeRoutingPrecedesEveryLocalDecisionAndPreservesUnsignedUuidOrder() {
        var low = new UUID(0, -1L); var high = new UUID(-1L, 0);
        var original = encode(Map.of(high, pending(high), low, pending(low)));
        var read = P11CastCooldownCodec.read(original);
        assertEquals(List.of(low, high), List.copyOf(read.entries.keySet()));
        var duplicate = original.copy(); var list = duplicate.getList("entries", Tag.TAG_COMPOUND);
        list.set(1, list.getCompound(0).copy());
        assertWhole(duplicate, P11CastCooldownData.Reason.DUPLICATE_KEY);
        var missing = original.copy(); missing.getList("entries", Tag.TAG_COMPOUND).getCompound(1).remove("skill_id");
        assertWhole(missing, P11CastCooldownData.Reason.UNROUTABLE);
        var unsorted = original.copy(); var rows = unsorted.getList("entries", Tag.TAG_COMPOUND);
        var first = rows.getCompound(0); rows.set(0, rows.getCompound(1)); rows.set(1, first);
        assertWhole(unsorted, P11CastCooldownData.Reason.MALFORMED);
        var futureRoot = original.copy(); futureRoot.putInt("schema_version", 2);
        assertWhole(futureRoot, P11CastCooldownData.Reason.UNSUPPORTED);
        var mismatched = encode(Map.of(KEY, P11CastCooldownData.Entry.raw(KEY,
                P11CastCooldownData.Reason.MALFORMED, original.getList("entries", Tag.TAG_COMPOUND).getCompound(0))));
        assertWhole(mismatched, P11CastCooldownData.Reason.UNROUTABLE);
    }

    @Test
    void uncertainKnowledgeDoesNotInferArmFromPairAndRejectsHalfPairsAndClosedReasons() {
        var pending = pending(KEY);
        var unknown = encode(Map.of(KEY, pending.uncertain(P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, false, -1)));
        assertEquals(0, P11CastCooldownCodec.read(unknown).entries.get(KEY).releaseKnowledge);
        var candidate = unknown.copy(); var entry = candidate.getList("entries", Tag.TAG_COMPOUND).getCompound(0);
        entry.putLong("candidate_release_at", 1002); entry.putLong("candidate_expires_at", 1122);
        assertEquals(0, P11CastCooldownCodec.read(candidate).entries.get(KEY).releaseKnowledge);
        assertIdempotent(candidate);
        entry.putByte("release_knowledge", (byte) 1);
        assertEquals(1, P11CastCooldownCodec.read(candidate).entries.get(KEY).releaseKnowledge);
        for (int reason : new int[] {0, 1, 8, 9, 12, 127}) {
            var bad = candidate.copy(); bad.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putByte("reason", (byte) reason);
            assertLocal(bad, P11CastCooldownData.Reason.MALFORMED);
        }
        for (var field : List.of("candidate_release_at", "candidate_expires_at")) {
            var half = candidate.copy(); half.getList("entries", Tag.TAG_COMPOUND).getCompound(0).putLong(field, -1);
            assertLocal(half, P11CastCooldownData.Reason.MALFORMED);
        }
    }

    @Test
    void boundedCountingStopsAtLimitPlusOneBeforeCopiesAndDepthIsContainerTreeDepth() {
        var raw297 = new ByteArrayTag(new byte[292]); // anyTag 1 + length4 +292
        assertEquals(297, P11CastCooldownNbtSize.measure(raw297, 297, 8).observedAtLeast());
        var raw298 = new ByteArrayTag(new byte[293]);
        assertEquals(298, P11CastCooldownNbtSize.measure(raw298, 297, 8).observedAtLeast());
        assertFalse(P11CastCooldownNbtSize.measure(raw298, 297, 8).fits());
        assertTrue(P11CastCooldownNbtSize.measure(nested(8), 297, 8).fits());
        var deep = P11CastCooldownNbtSize.measure(nested(9), 297, 8);
        assertTrue(deep.depthExceeded()); assertEquals(9, deep.observedAtLeast());
        var root = P11CastCooldownCodec.read(nested(17));
        assertEquals(P11CastCooldownData.Kind.MARKER, root.kind);
        assertEquals(P11CastCooldownData.Reason.DEPTH_LIMIT, root.reason);
        assertEquals(17, root.observedAtLeast); assertEquals(16, root.maximum);
        var huge = P11CastCooldownCodec.read(new ByteArrayTag(new byte[90180]));
        assertEquals(P11CastCooldownData.Kind.MARKER, huge.kind);
        assertEquals(90181, huge.observedAtLeast); assertEquals(90180, huge.maximum);
        var maxDepthRaw = P11CastCooldownCodec.write(P11CastCooldownData.raw(
                P11CastCooldownData.Reason.MALFORMED, nested(16)));
        assertTrue(P11CastCooldownNbtSize.measure(maxDepthRaw, 90226, 17).fits());
        assertIdempotent(maxDepthRaw);
    }

    @Test
    void entryLimitAndCanonicalEmptyAreDistinctFromPreservationByteLimits() {
        var root = encode(Map.of());
        assertEquals(Tag.TAG_END, root.getList("entries", Tag.TAG_COMPOUND).getElementType());
        var rows = new ListTag();
        for (int i = 0; i < 257; i++) {
            var id = new UUID(0, i);
            rows.add(encode(Map.of(id, pending(id))).getList("entries", Tag.TAG_COMPOUND).getCompound(0));
        }
        root.put("entries", rows);
        var result = P11CastCooldownCodec.read(root);
        assertEquals(P11CastCooldownData.Kind.MARKER, result.kind);
        assertEquals(P11CastCooldownData.Reason.ENTRY_LIMIT, result.reason);
        assertEquals(257, result.observedAtLeast); assertEquals(256, result.maximum);
        assertIdempotent(P11CastCooldownCodec.write(result));
    }

    private record Form(P11CastCooldownData.Entry entry, int payload, int root256) { }
    private static P11CastCooldownData.Entry pending(UUID id) {
        return P11CastCooldownData.Entry.pending(id, 7, 120, 1000, ATTEMPT, 1101);
    }
    private static CompoundTag encode(Map<UUID, P11CastCooldownData.Entry> entries) {
        return P11CastCooldownCodec.write(P11CastCooldownData.routed(1003, entries));
    }
    private static CompoundTag nested(int depth) {
        var root = new CompoundTag(); var current = root;
        for (int i = 1; i < depth; i++) { var child = new CompoundTag(); current.put("n", child); current = child; }
        return root;
    }
    private static int bytes(Tag tag) throws Exception {
        var sink = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(sink)) { NbtIo.writeAnyTag(tag, output); }
        return sink.size();
    }
    private static void assertLocal(CompoundTag input, P11CastCooldownData.Reason why) {
        var data = P11CastCooldownCodec.read(input);
        assertEquals(P11CastCooldownData.Kind.ROUTED, data.kind);
        assertEquals(1, data.entries.size());
        assertEquals(3, data.entries.get(KEY).kind); assertEquals(why, data.entries.get(KEY).reason);
        assertIdempotent(P11CastCooldownCodec.write(data));
    }
    private static void assertWhole(Tag input, P11CastCooldownData.Reason why) {
        var data = P11CastCooldownCodec.read(input);
        assertEquals(P11CastCooldownData.Kind.RAW, data.kind); assertEquals(why, data.reason);
        assertEquals(input, data.rawCopy());
        assertIdempotent(P11CastCooldownCodec.write(data));
    }
    private static void assertIdempotent(Tag input) {
        var once = P11CastCooldownCodec.write(P11CastCooldownCodec.read(input));
        var twice = P11CastCooldownCodec.write(P11CastCooldownCodec.read(once).copy());
        assertEquals(once, twice);
    }
}
