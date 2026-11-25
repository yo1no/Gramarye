package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;

/** Actual typed material and logical NBT, not authentication or physical save evidence. */
final class P11TypedCooldownCodecTest {
    private static final UUID ATTEMPT = new UUID(5, 7);
    private static final P11CooldownKey.LogicalPolicy KEY = key("gramarye:u0_bootstrap_qualification");

    @Test void policyGrammarIsExactAndFamiliesCannotAlias() {
        for (String name : List.of("", "a", ":b", "a:", "a:b:c", "minecraft", " A:b", "a:B", "a:b ",
                "a:b\u0000", "a:\u00e9", "a\\b:c", "a/b:c", "a:" + "b".repeat(127))) {
            assertNull(P11CooldownKey.parsePolicy(name), name);
        }
        for (String name : List.of("a:b", "gramarye:u0_bootstrap_qualification", "a.b-c_d:path/to.file-1")) {
            assertEquals(name, key(name).policyName().toString());
        }
        var rows = new TreeMap<P11CooldownKey, Integer>(P11CooldownKey.ORDER);
        rows.put(key("a:b0"), 3); rows.put(key("a:b"), 2);
        rows.put(P11CooldownKey.legacy(new UUID(-1, 0)), 1);
        rows.put(P11CooldownKey.legacy(new UUID(0, -1)), 0);
        assertEquals(List.of(0, 1, 2, 3), List.copyOf(rows.values()));
        assertNotEquals(KEY, P11CooldownKey.legacy(ATTEMPT));
    }

    @Test void versionOneGoldenInnerSurvivesExplicitMixedMigrationAndNeverAutoDowngrades() throws Exception {
        UUID id = new UUID(-1, Long.MIN_VALUE);
        var legacy = P11CastCooldownData.Entry.pending(id, 7, 120, 1000, ATTEMPT, 1101);
        var v1 = P11CastCooldownCodec.write(P11CastCooldownData.routed(1003, Map.of(id, legacy)));
        assertEquals(248, bytes(v1));
        assertEquals(v1, P11CastCooldownCodec.write(P11CastCooldownCodec.read(v1).copy()));
        assertEquals(1, P11CastCooldownCodec.read(v1).schemaVersion);
        var mixed = map(); mixed.put(legacy.key, legacy); mixed.put(KEY, pending(KEY));
        var v2 = encode(mixed);
        assertEquals(v1.getList("entries", 10).getCompound(0), v2.getList("entries", 10).getCompound(0).getCompound("legacy"));
        assertEquals(2, P11CastCooldownCodec.read(v2).schemaVersion);
        var removed = P11CastCooldownCodec.read(v2).replacing(1003, Map.of());
        assertEquals(2, P11CastCooldownCodec.write(removed.copy()).getInt("schema_version"));
        assertEquals(Tag.TAG_END, P11CastCooldownCodec.write(removed).getList("entries", 10).getElementType());
    }

    @Test void allThreeMixedNormalFormsMatchActualLogicalBytes() throws Exception {
        for (int kind : new int[] {1, 0, 2}) {
            var rows = map();
            for (int i = 0; i < 256; i++) {
                UUID id = new UUID(0, i);
                var entry = form(P11CastCooldownData.Entry.pending(id, 7, 120, 1000, ATTEMPT, 1101), kind);
                rows.put(entry.key, entry);
            }
            for (int i = 0; i < 64; i++) {
                var k = longKey(i); rows.put(k, form(pending(k), kind));
            }
            var encoded = encode(rows);
            int expected = kind == 1 ? 72516 : kind == 0 ? 86276 : 109636;
            assertEquals(expected, bytes(encoded));
            assertEquals(expected, P11CastCooldownNbtSize.measure(encoded, 125572, 16).observedAtLeast());
            var actual = P11CastCooldownCodec.read(encoded);
            assertEquals(P11CastCooldownData.Kind.ROUTED, actual.kind);
            assertEquals(320, actual.entries.size());
            assertRoundTrip(encoded);
        }
    }

    @Test void maximumMixedRawAndWholeWrappersReachExactBoundsAndAreIdempotent() throws Exception {
        var rows = map();
        for (int i = 0; i < 256; i++) {
            UUID id = new UUID(0, i);
            var raw = P11CastCooldownCodec.write(P11CastCooldownData.routed(1003,
                    Map.of(id, form(P11CastCooldownData.Entry.pending(id, 7, 120, 1000, ATTEMPT, 1101), 2))))
                    .getList("entries", 10).getCompound(0);
            assertEquals(297, bytes(raw));
            var entry = P11CastCooldownData.Entry.raw(id, P11CastCooldownData.Reason.MALFORMED, raw);
            rows.put(entry.key, entry);
        }
        for (int i = 0; i < 64; i++) {
            var k = longKey(i); var raw = policyBody(form(pending(k), 2));
            assertEquals(278, bytes(raw));
            rows.put(k, P11CastCooldownData.Entry.policyRaw(k, P11CastCooldownData.Reason.UNSUPPORTED, raw));
        }
        var routed = encode(rows); assertEquals(125572, bytes(routed)); assertRoundTrip(routed);
        var whole = P11CastCooldownCodec.write(P11CastCooldownData.raw(2, P11CastCooldownData.Reason.MALFORMED, routed));
        assertEquals(125618, bytes(whole)); assertRoundTrip(whole);
        var marker = P11CastCooldownCodec.write(P11CastCooldownData.marker(2,
                P11CastCooldownData.Reason.BYTE_LIMIT, 125573, 125572));
        assertEquals(87, bytes(marker)); assertRoundTrip(marker);
    }

    @Test void completeRoutingRejectsDuplicatesWrongOrderAndUnknownEnvelopesBeforeLocalDecode() {
        var valid = encode(Map.of(KEY, pending(KEY)));
        var duplicate = valid.copy(); var rows = duplicate.getList("entries", 10);
        rows.getCompound(0).getCompound("body").putInt("policy_version", 99);
        rows.add(rows.getCompound(0).copy());
        assertWhole(duplicate, P11CastCooldownData.Reason.DUPLICATE_KEY);
        var extra = valid.copy(); extra.getList("entries", 10).getCompound(0).putInt("extra", 0);
        assertWhole(extra, P11CastCooldownData.Reason.UNROUTABLE);
        var unknown = valid.copy(); unknown.getList("entries", 10).getCompound(0).putByte("key_kind", (byte) 2);
        assertWhole(unknown, P11CastCooldownData.Reason.UNROUTABLE);
        var badName = valid.copy(); badName.getList("entries", 10).getCompound(0).putString("policy_key", "u0_bootstrap_qualification");
        assertWhole(badName, P11CastCooldownData.Reason.UNROUTABLE);
        var two = map(); var a = key("a:b"); var b = key("a:c"); two.put(a, pending(a)); two.put(b, pending(b));
        var unordered = encode(two); var reverse = new ListTag();
        reverse.add(unordered.getList("entries", 10).getCompound(1).copy());
        reverse.add(unordered.getList("entries", 10).getCompound(0).copy()); unordered.put("entries", reverse);
        assertWhole(unordered, P11CastCooldownData.Reason.MALFORMED);
        var noRoute = valid.copy(); var later = valid.getList("entries", 10).getCompound(0).copy();
        later.putString("policy_key", "wrong"); noRoute.getList("entries", 10).getCompound(0).getCompound("body").putLong("duration_ticks", 20);
        noRoute.getList("entries", 10).add(later);
        assertWhole(noRoute, P11CastCooldownData.Reason.UNROUTABLE);
    }

    @Test void storedFamilyAndCombinedMaxPlusOneHaveDistinctWholeMarkers() {
        var base = encode(Map.of()); var rows = new ListTag();
        for (int i = 0; i < 257; i++) {
            UUID id = new UUID(0, i); var e = P11CastCooldownData.Entry.pending(id, 1, 20, 1000, ATTEMPT, 1101);
            rows.add(encode(Map.of(e.key, e)).getList("entries", 10).getCompound(0));
        }
        base.put("entries", rows); assertMarker(base, P11CastCooldownData.Reason.LEGACY_ENTRY_LIMIT, 257, 256);
        rows = new ListTag();
        for (int i = 0; i < 65; i++) { var k = longKey(i); rows.add(encode(Map.of(k, pending(k))).getList("entries", 10).getCompound(0)); }
        base.put("entries", rows); assertMarker(base, P11CastCooldownData.Reason.POLICY_ENTRY_LIMIT, 65, 64);
        var all = new ListTag();
        for (int i = 0; i < 256; i++) {
            UUID id = new UUID(0, i); var e = P11CastCooldownData.Entry.pending(id, 1, 20, 1000, ATTEMPT, 1101);
            all.add(encode(Map.of(e.key, e)).getList("entries", 10).getCompound(0));
        }
        for (Tag row : rows) { all.add(row.copy()); }
        base.put("entries", all); assertMarker(base, P11CastCooldownData.Reason.ENTRY_LIMIT, 321, 320);
    }

    @Test void exactPolicyTypesVersionAndCrossFieldsQuarantineOnlyTheirKnownKey() {
        var original = encode(Map.of(KEY, pending(KEY)));
        for (String field : List.of("policy_version", "duration_ticks", "release_window_ticks")) {
            var input = original.copy(); var body = body(input); body.putLong(field, body.getInt(field));
            assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        }
        for (String field : List.of("accepted_at", "release_not_after")) {
            var input = original.copy(); body(input).putInt(field, (int) body(input).getLong(field));
            assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        }
        var future = original.copy(); body(future).putInt("policy_version", 3);
        assertLocal(future, P11CastCooldownData.Reason.UNSUPPORTED);
        for (int window : new int[] {-1, 0, 3, 42, Integer.MAX_VALUE}) {
            var input = original.copy(); body(input).putInt("release_window_ticks", window);
            body(input).putLong("release_not_after", 1000L + window); assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        }
        var mismatch = original.copy(); body(mismatch).putLong("release_not_after", 1101);
        assertLocal(mismatch, P11CastCooldownData.Reason.MALFORMED);
        var zero = original.copy(); body(zero).putInt("duration_ticks", 0); assertLocal(zero, P11CastCooldownData.Reason.MALFORMED);
        var both = map(); both.put(KEY, pending(KEY)); var other = key("z:valid"); both.put(other, pending(other));
        var mixed = encode(both); body(mixed).putInt("policy_version", 3);
        var decoded = P11CastCooldownCodec.read(mixed);
        assertEquals(3, decoded.entries.get(KEY).kind); assertEquals(1, decoded.entries.get(other).kind);
    }

    @Test void policyUncertainKnowledgeAndCapturedWindowSurviveRestartWithoutCurrentLimits() {
        for (int window : new int[] {4, 25, 41}) {
            var entry = P11CastCooldownData.Entry.pendingPolicy(KEY, 600, 1000, ATTEMPT, window);
            var tag = encode(Map.of(KEY, entry));
            var decoded = P11CastCooldownCodec.read(tag).entries.get(KEY);
            assertEquals(window, decoded.releaseWindow); assertEquals(1000 + window, decoded.releaseNotAfter);
        }
        var uncertain = encode(Map.of(KEY, pending(KEY).uncertain(P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, false, -1)));
        var unknownBody = body(uncertain); unknownBody.putLong("candidate_release_at", 1002); unknownBody.putLong("candidate_expires_at", 1122);
        assertEquals(0, P11CastCooldownCodec.read(uncertain).entries.get(KEY).releaseKnowledge);
        unknownBody.putLong("candidate_expires_at", -1); assertLocal(uncertain, P11CastCooldownData.Reason.MALFORMED);
        var known = encode(Map.of(KEY, pending(KEY).uncertain(P11CastCooldownData.Reason.SAVE_FAILURE, 1003, true, 1002)));
        assertEquals(1, P11CastCooldownCodec.read(known).entries.get(KEY).releaseKnowledge);
        body(known).putByte("reason", (byte) P11CastCooldownData.Reason.PUBLICATION_UNKNOWN.code);
        assertLocal(known, P11CastCooldownData.Reason.MALFORMED);
    }

    @Test void overflowAndBoundaryReleaseCannotBecomeTrustedMaterial() {
        var input = encode(Map.of(KEY, pending(KEY)));
        input.putLong("clock_floor", Long.MAX_VALUE); var body = body(input);
        body.putLong("accepted_at", Long.MAX_VALUE - 24); body.putLong("release_not_after", Long.MAX_VALUE);
        assertLocal(input, P11CastCooldownData.Reason.MALFORMED);
        var active = encode(Map.of(KEY, pending(KEY).active(1002)));
        body(active).putLong("released_at", 1026); body(active).putLong("expires_at", 1146);
        assertLocal(active, P11CastCooldownData.Reason.MALFORMED);
    }

    @Test void keyLocalAndWholeByteAndDepthBoundsRefuseWithoutTruncating() {
        var tooLong = encode(Map.of(KEY, pending(KEY)));
        tooLong.getList("entries", 10).getCompound(0).putString("policy_key", "a:" + "b".repeat(127));
        assertWhole(tooLong, P11CastCooldownData.Reason.UNROUTABLE);
        var local = encode(Map.of(KEY, pending(KEY))); body(local).putByteArray("oversized", new byte[279]);
        var result = P11CastCooldownCodec.read(local).entries.get(KEY);
        assertEquals(4, result.kind); assertEquals(278, result.maximum); assertTrue(result.observedAtLeast >= 279);
        var localDepth = encode(Map.of(KEY, pending(KEY))); body(localDepth).put("nested", nested(9));
        result = P11CastCooldownCodec.read(localDepth).entries.get(KEY);
        assertEquals(4, result.kind); assertEquals(P11CastCooldownData.Reason.DEPTH_LIMIT, result.reason); assertEquals(8, result.maximum);
        var whole = encode(Map.of()); whole.putByteArray("oversized", new byte[125572]);
        assertMarker(whole, P11CastCooldownData.Reason.BYTE_LIMIT, 125573, 125572);
        var preserved = P11CastCooldownCodec.write(P11CastCooldownData.raw(2, P11CastCooldownData.Reason.MALFORMED, nested(16)));
        assertRoundTrip(preserved);
        var tooDeep = encode(Map.of()); tooDeep.put("extra", nested(17));
        assertMarker(tooDeep, P11CastCooldownData.Reason.DEPTH_LIMIT, 17, 16);
    }

    @Test void unknownOriginalFormatsAndRawCopiesDoNotTurnIntoEmptyReady() {
        UUID id = new UUID(1, 2); var v1 = P11CastCooldownCodec.write(P11CastCooldownData.routed(1003,
                Map.of(id, P11CastCooldownData.Entry.pending(id, 1, 20, 1000, ATTEMPT, 1101))));
        v1.putInt("extra", 7);
        var preserved = P11CastCooldownCodec.read(v1);
        assertEquals(1, preserved.schemaVersion); assertEquals(P11CastCooldownData.Kind.RAW, preserved.kind);
        assertRoundTrip(P11CastCooldownCodec.write(preserved));
        var v2 = encode(Map.of(KEY, pending(KEY))); v2.putInt("extra", 7);
        var raw = P11CastCooldownCodec.read(v2);
        assertEquals(2, raw.schemaVersion); assertEquals(P11CastCooldownData.Kind.RAW, raw.kind);
        var copy = raw.rawCopy(); ((CompoundTag) copy).putInt("mutated", 1);
        assertFalse(((CompoundTag) raw.rawCopy()).contains("mutated"));
        assertEquals(2, raw.copy().schemaVersion); assertRoundTrip(P11CastCooldownCodec.write(raw));
        // An original schema1 RAW wrapper may contain what is now a recognized
        // schema2 tree; the new reader must not reinterpret that old obligation.
        var oldUnknown = P11CastCooldownCodec.write(P11CastCooldownData.raw(
                P11CastCooldownData.Reason.UNSUPPORTED, encode(Map.of(KEY, pending(KEY)))));
        var oldUnknownRead = P11CastCooldownCodec.read(oldUnknown);
        assertEquals(1, oldUnknownRead.schemaVersion);
        assertEquals(P11CastCooldownData.Kind.RAW, oldUnknownRead.kind);
        assertEquals(oldUnknown, P11CastCooldownCodec.write(oldUnknownRead.copy()));
        var future = encode(Map.of()); future.putInt("schema_version", 3);
        var futureRaw = P11CastCooldownCodec.read(future);
        assertEquals(P11CastCooldownData.Kind.RAW, futureRaw.kind);
        assertEquals(P11CastCooldownData.Reason.UNSUPPORTED, futureRaw.reason);
        assertEquals(future, futureRaw.rawCopy());
    }

    private static P11CooldownKey.LogicalPolicy key(String value) { return java.util.Objects.requireNonNull(P11CooldownKey.parsePolicy(value)); }
    private static P11CooldownKey.LogicalPolicy longKey(int n) { return key("g:" + "a".repeat(124) + String.format(java.util.Locale.ROOT, "%02d", n)); }
    private static P11CastCooldownData.Entry pending(P11CooldownKey.LogicalPolicy key) {
        return P11CastCooldownData.Entry.pendingPolicy(key, 120, 1000, ATTEMPT, 25);
    }
    private static P11CastCooldownData.Entry form(P11CastCooldownData.Entry pending, int kind) {
        return kind == 1 ? pending : kind == 0 ? pending.active(1002)
                : pending.uncertain(P11CastCooldownData.Reason.RELEASE_UNKNOWN, 1003, true, 1002);
    }
    private static TreeMap<P11CooldownKey, P11CastCooldownData.Entry> map() { return new TreeMap<>(P11CooldownKey.ORDER); }
    private static CompoundTag encode(Map<P11CooldownKey, P11CastCooldownData.Entry> entries) {
        return P11CastCooldownCodec.write(P11CastCooldownData.routed(2, 1003, entries));
    }
    private static CompoundTag body(CompoundTag root) { return root.getList("entries", 10).getCompound(0).getCompound("body"); }
    private static CompoundTag policyBody(P11CastCooldownData.Entry entry) { return body(encode(Map.of(entry.key, entry))); }
    private static CompoundTag nested(int depth) {
        var result = new CompoundTag(); var at = result;
        for (int i = 1; i < depth; i++) { var next = new CompoundTag(); at.put("n", next); at = next; }
        return result;
    }
    private static int bytes(Tag tag) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var stream = new DataOutputStream(out)) { NbtIo.writeAnyTag(tag, stream); }
        return out.size();
    }
    private static void assertLocal(CompoundTag input, P11CastCooldownData.Reason why) {
        var result = P11CastCooldownCodec.read(input);
        assertEquals(P11CastCooldownData.Kind.ROUTED, result.kind);
        assertEquals(3, result.entries.get(KEY).kind); assertEquals(why, result.entries.get(KEY).reason);
        assertRoundTrip(P11CastCooldownCodec.write(result));
    }
    private static void assertWhole(CompoundTag input, P11CastCooldownData.Reason why) {
        var result = P11CastCooldownCodec.read(input);
        assertEquals(P11CastCooldownData.Kind.RAW, result.kind); assertEquals(why, result.reason);
        assertEquals(input, result.rawCopy()); assertRoundTrip(P11CastCooldownCodec.write(result));
    }
    private static void assertMarker(CompoundTag input, P11CastCooldownData.Reason why, long observed, long maximum) {
        var result = P11CastCooldownCodec.read(input);
        assertEquals(P11CastCooldownData.Kind.MARKER, result.kind); assertEquals(why, result.reason);
        assertEquals(observed, result.observedAtLeast); assertEquals(maximum, result.maximum);
        assertRoundTrip(P11CastCooldownCodec.write(result));
    }
    private static void assertRoundTrip(Tag tag) {
        var once = P11CastCooldownCodec.write(P11CastCooldownCodec.read(tag));
        assertEquals(once, P11CastCooldownCodec.write(P11CastCooldownCodec.read(once).copy()));
    }
}
