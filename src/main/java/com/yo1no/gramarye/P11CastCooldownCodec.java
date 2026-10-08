package com.yo1no.gramarye;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.*;
import static com.yo1no.gramarye.P11CastCooldownData.Reason;

/** Frozen §11.6 logical NBT. Expected malformed input is data, never a default Ready. */
final class P11CastCooldownCodec {
    static final int LOCAL_BYTES = 297, ROOT_BYTES = 90180;
    private static final Set<String> COMMON = Set.of("skill_id", "kind", "revision", "policy_version",
            "duration_ticks", "accepted_at", "attempt_id", "release_not_after");
    private P11CastCooldownCodec() { }

    static P11CastCooldownData read(Tag input) {
        // END is the native compound terminator, not a value deliverable under a named
        // Attachment key. A synthetic invocation cannot be preserved as a named raw field.
        if (input == null || input.getId() == Tag.TAG_END) {
            throw new IllegalArgumentException("COOLDOWN_NONVALUE_INPUT");
        }
        if (!(input instanceof CompoundTag root)) { return preserveRoot(input, Reason.UNROUTABLE); }
        if (!type(root, "schema_version", Tag.TAG_INT)) { return preserveRoot(input, Reason.MALFORMED); }
        if (root.getInt("schema_version") != 1) { return preserveRoot(input, Reason.UNSUPPORTED); }
        if (!type(root, "kind", Tag.TAG_BYTE)) { return preserveRoot(input, Reason.MALFORMED); }
        int kind = root.getByte("kind");
        if (kind == 1 && keys(root, "schema_version", "kind", "reason", "raw")
                && type(root, "reason", Tag.TAG_BYTE)) {
            var reason = Reason.of(root.getByte("reason"));
            if (reason != null && reason.code >= 1 && reason.code <= 4) {
                var raw = root.get("raw");
                var measured = P11CastCooldownNbtSize.measure(raw, ROOT_BYTES, 16);
                if (measured.fits()) { return P11CastCooldownData.raw(reason, raw.copy()); }
            }
            return preserveRoot(input, Reason.MALFORMED);
        }
        if (kind == 2 && marker(root, true)) {
            return P11CastCooldownData.marker(Reason.of(root.getByte("reason")),
                    root.getLong("observed_at_least"), root.getLong("maximum"));
        }
        if (kind != 0 || !keys(root, "schema_version", "kind", "clock_floor", "entries")
                || !type(root, "clock_floor", Tag.TAG_LONG) || root.getLong("clock_floor") < 0
                || !(root.get("entries") instanceof ListTag list)) {
            return preserveRoot(input, Reason.MALFORMED);
        }
        if (list.size() > 256) { return P11CastCooldownData.marker(Reason.ENTRY_LIMIT, 257, 256); }
        if ((!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || (list.isEmpty() && list.getElementType() != Tag.TAG_END && list.getElementType() != Tag.TAG_COMPOUND)) {
            return preserveRoot(input, Reason.UNROUTABLE);
        }
        // Complete routing precedes local quarantine. Never keep a convenient valid prefix.
        var seen = new HashSet<UUID>();
        UUID previous = null;
        for (Tag value : list) {
            if (!(value instanceof CompoundTag entry) || uuid(entry, "skill_id") == null) {
                return preserveRoot(input, Reason.UNROUTABLE);
            }
            UUID key = uuid(entry, "skill_id");
            if (!seen.add(key)) { return preserveRoot(input, Reason.DUPLICATE_KEY); }
            if (previous != null && P11CastCooldownData.KEY_ORDER.compare(previous, key) >= 0) {
                return preserveRoot(input, Reason.MALFORMED);
            }
            previous = key;
            if (type(entry, "kind", Tag.TAG_BYTE) && entry.getByte("kind") == 3
                    && entry.get("raw") instanceof CompoundTag raw && uuid(raw, "skill_id") != null
                    && !key.equals(uuid(raw, "skill_id"))) { return preserveRoot(input, Reason.UNROUTABLE); }
        }
        var result = new TreeMap<UUID, P11CastCooldownData.Entry>(P11CastCooldownData.KEY_ORDER);
        long floor = root.getLong("clock_floor");
        for (Tag value : list) {
            var entry = (CompoundTag) value;
            var key = uuid(entry, "skill_id");
            result.put(key, readEntry(key, entry, floor));
        }
        // A routeable oversized individual entry becomes only its local marker; the
        // original excess bytes must not poison independently routed keys.
        return P11CastCooldownData.routed(floor, result);
    }

    private static P11CastCooldownData.Entry readEntry(UUID key, CompoundTag input, long floor) {
        if (!type(input, "kind", Tag.TAG_BYTE)) { return preserveEntry(key, input, Reason.MALFORMED); }
        int kind = input.getByte("kind");
        if (kind == 3 && keys(input, "skill_id", "kind", "reason", "raw")
                && type(input, "reason", Tag.TAG_BYTE) && input.get("raw") instanceof CompoundTag raw) {
            var reason = Reason.of(input.getByte("reason"));
            var size = P11CastCooldownNbtSize.measure(raw, LOCAL_BYTES, 8);
            if ((reason == Reason.MALFORMED || reason == Reason.UNSUPPORTED) && size.fits()) {
                return P11CastCooldownData.Entry.raw(key, reason, raw.copy());
            }
        }
        if (kind == 4 && marker(input, false)) {
            return P11CastCooldownData.Entry.marker(key, Reason.of(input.getByte("reason")),
                    input.getLong("observed_at_least"), input.getLong("maximum"));
        }
        if (kind < 0 || kind > 2) { return preserveEntry(key, input, Reason.MALFORMED); }
        var expected = new HashSet<>(COMMON);
        if (kind == 0) { expected.addAll(Set.of("released_at", "expires_at")); }
        if (kind == 2) { expected.addAll(Set.of("reason", "uncertain_at", "release_knowledge",
                "candidate_release_at", "candidate_expires_at")); }
        if (!input.getAllKeys().equals(expected) || !type(input, "revision", Tag.TAG_INT)
                || !type(input, "policy_version", Tag.TAG_INT) || !type(input, "duration_ticks", Tag.TAG_INT)
                || !type(input, "accepted_at", Tag.TAG_LONG) || uuid(input, "attempt_id") == null
                || !type(input, "release_not_after", Tag.TAG_LONG)) {
            return preserveEntry(key, input, Reason.MALFORMED);
        }
        if (input.getInt("policy_version") != 1) { return preserveEntry(key, input, Reason.UNSUPPORTED); }
        int revision = input.getInt("revision"), duration = input.getInt("duration_ticks");
        long accepted = input.getLong("accepted_at"), bound = input.getLong("release_not_after");
        if (revision < 0 || duration < 1 || duration > 600 || accepted < 0 || accepted > floor
                || accepted > Long.MAX_VALUE - 101 || bound != accepted + 101 || bound > Long.MAX_VALUE - duration) {
            return preserveEntry(key, input, Reason.MALFORMED);
        }
        long released = -1, expires = -1, uncertain = 0;
        int knowledge = 0;
        Reason reason = null;
        if (kind == 0) {
            if (!type(input, "released_at", Tag.TAG_LONG) || !type(input, "expires_at", Tag.TAG_LONG)) {
                return preserveEntry(key, input, Reason.MALFORMED);
            }
            released = input.getLong("released_at"); expires = input.getLong("expires_at");
            if (!activePair(accepted, bound, floor, duration, released, expires)) {
                return preserveEntry(key, input, Reason.MALFORMED);
            }
        }
        if (kind == 2) {
            if (!type(input, "reason", Tag.TAG_BYTE) || !type(input, "uncertain_at", Tag.TAG_LONG)
                    || !type(input, "release_knowledge", Tag.TAG_BYTE)
                    || !type(input, "candidate_release_at", Tag.TAG_LONG)
                    || !type(input, "candidate_expires_at", Tag.TAG_LONG)) {
                return preserveEntry(key, input, Reason.MALFORMED);
            }
            reason = Reason.of(input.getByte("reason")); uncertain = input.getLong("uncertain_at");
            knowledge = input.getByte("release_knowledge");
            released = input.getLong("candidate_release_at"); expires = input.getLong("candidate_expires_at");
            boolean pair = activePair(accepted, bound, floor, duration, released, expires);
            if (uncertain < accepted || uncertain > floor || (knowledge != 0 && knowledge != 1)
                    || (reason != Reason.PUBLICATION_UNKNOWN && reason != Reason.RELEASE_UNKNOWN && reason != Reason.SAVE_FAILURE)
                    || (reason == Reason.PUBLICATION_UNKNOWN && knowledge != 0)
                    || (knowledge == 1 && !pair) || (knowledge == 0 && !pair && (released != -1 || expires != -1))) {
                return preserveEntry(key, input, Reason.MALFORMED);
            }
        }
        return new P11CastCooldownData.Entry(key, kind, revision, duration, accepted, uuid(input, "attempt_id"),
                bound, released, expires, reason, uncertain, knowledge, null, 0, 0);
    }

    private static boolean activePair(long accepted, long bound, long floor, int duration, long released, long expires) {
        return released >= accepted && released <= bound && released <= floor
                && released <= Long.MAX_VALUE - duration && expires == released + duration;
    }
    private static boolean marker(CompoundTag tag, boolean root) {
        if (!(root ? keys(tag, "schema_version", "kind", "reason", "observed_at_least", "maximum")
                : keys(tag, "skill_id", "kind", "reason", "observed_at_least", "maximum"))
                || !type(tag, "reason", Tag.TAG_BYTE) || !type(tag, "observed_at_least", Tag.TAG_LONG)
                || !type(tag, "maximum", Tag.TAG_LONG)) { return false; }
        var reason = Reason.of(tag.getByte("reason"));
        long maximum = tag.getLong("maximum");
        return tag.getLong("observed_at_least") > maximum && ((reason == Reason.BYTE_LIMIT && maximum == (root ? ROOT_BYTES : LOCAL_BYTES))
                || (reason == Reason.DEPTH_LIMIT && maximum == (root ? 16 : 8))
                || (root && reason == Reason.ENTRY_LIMIT && maximum == 256));
    }
    private static P11CastCooldownData preserveRoot(Tag input, Reason why) {
        var measured = P11CastCooldownNbtSize.measure(input, ROOT_BYTES, 16);
        if (!measured.fits()) {
            return P11CastCooldownData.marker(measured.depthExceeded() ? Reason.DEPTH_LIMIT : Reason.BYTE_LIMIT,
                    measured.observedAtLeast(), measured.depthExceeded() ? 16 : ROOT_BYTES);
        }
        return P11CastCooldownData.raw(why, input.copy());
    }
    private static P11CastCooldownData.Entry preserveEntry(UUID key, CompoundTag input, Reason why) {
        var measured = P11CastCooldownNbtSize.measure(input, LOCAL_BYTES, 8);
        if (!measured.fits()) {
            return P11CastCooldownData.Entry.marker(key, measured.depthExceeded() ? Reason.DEPTH_LIMIT : Reason.BYTE_LIMIT,
                    measured.observedAtLeast(), measured.depthExceeded() ? 8 : LOCAL_BYTES);
        }
        return P11CastCooldownData.Entry.raw(key, why, input.copy());
    }

    static CompoundTag write(P11CastCooldownData data) {
        if (data.kind == P11CastCooldownData.Kind.UNBOUND) { throw new IllegalStateException("COOLDOWN_UNBOUND_MATERIAL"); }
        var root = new CompoundTag(); root.putInt("schema_version", 1);
        if (data.kind == P11CastCooldownData.Kind.RAW) {
            root.putByte("kind", (byte) 1); root.putByte("reason", (byte) data.reason.code); root.put("raw", data.rawCopy());
        } else if (data.kind == P11CastCooldownData.Kind.MARKER) {
            root.putByte("kind", (byte) 2); putMarker(root, data.reason, data.observedAtLeast, data.maximum);
        } else {
            root.putByte("kind", (byte) 0); root.putLong("clock_floor", data.clockFloor);
            var entries = new ListTag();
            for (var entry : data.entries.values()) { entries.add(writeEntry(entry)); }
            root.put("entries", entries);
        }
        return root;
    }
    private static CompoundTag writeEntry(P11CastCooldownData.Entry entry) {
        var tag = new CompoundTag(); putUuid(tag, "skill_id", entry.skillId); tag.putByte("kind", (byte) entry.kind);
        if (entry.kind == 3) { tag.putByte("reason", (byte) entry.reason.code); tag.put("raw", entry.rawCopy()); return tag; }
        if (entry.kind == 4) { putMarker(tag, entry.reason, entry.observedAtLeast, entry.maximum); return tag; }
        tag.putInt("revision", entry.revision); tag.putInt("policy_version", 1); tag.putInt("duration_ticks", entry.duration);
        tag.putLong("accepted_at", entry.acceptedAt); putUuid(tag, "attempt_id", entry.attemptId);
        tag.putLong("release_not_after", entry.releaseNotAfter);
        if (entry.kind == 0) { tag.putLong("released_at", entry.releasedAt); tag.putLong("expires_at", entry.expiresAt); }
        if (entry.kind == 2) {
            tag.putByte("reason", (byte) entry.reason.code); tag.putLong("uncertain_at", entry.uncertainAt);
            tag.putByte("release_knowledge", (byte) entry.releaseKnowledge);
            tag.putLong("candidate_release_at", entry.releasedAt); tag.putLong("candidate_expires_at", entry.expiresAt);
        }
        return tag;
    }
    private static void putMarker(CompoundTag tag, Reason reason, long observed, long maximum) {
        tag.putByte("reason", (byte) reason.code); tag.putLong("observed_at_least", observed); tag.putLong("maximum", maximum);
    }
    private static void putUuid(CompoundTag tag, String key, UUID value) {
        tag.putIntArray(key, new int[] { (int) (value.getMostSignificantBits() >>> 32), (int) value.getMostSignificantBits(),
                (int) (value.getLeastSignificantBits() >>> 32), (int) value.getLeastSignificantBits() });
    }
    private static UUID uuid(CompoundTag tag, String key) {
        if (!(tag.get(key) instanceof IntArrayTag array) || array.size() != 4) { return null; }
        var value = array.getAsIntArray();
        return new UUID(((long) value[0] << 32) | Integer.toUnsignedLong(value[1]),
                ((long) value[2] << 32) | Integer.toUnsignedLong(value[3]));
    }
    private static boolean type(CompoundTag tag, String key, int id) { return tag.get(key) != null && tag.get(key).getId() == id; }
    private static boolean keys(CompoundTag tag, String... keys) { return tag.getAllKeys().equals(Set.of(keys)); }
}
