package com.yo1no.gramarye;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.Tag;

/** Immutable Attachment material. A decoded carrier alone is not a bound cooldown owner. */
final class P11CastCooldownData {
    static final Comparator<UUID> KEY_ORDER = (left, right) -> {
        int high = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return high != 0 ? high : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    };
    enum Kind { UNBOUND, ROUTED, RAW, MARKER }
    enum Reason {
        MALFORMED(1), UNSUPPORTED(2), UNROUTABLE(3), DUPLICATE_KEY(4), BYTE_LIMIT(5), DEPTH_LIMIT(6),
        ENTRY_LIMIT(7), CLOCK(8), PUBLICATION_UNKNOWN(9), RELEASE_UNKNOWN(10), SAVE_FAILURE(11), ORPHAN_PENDING(12);
        final int code;
        Reason(int code) { this.code = code; }
        static Reason of(int value) {
            for (var reason : values()) { if (reason.code == value) { return reason; } }
            return null;
        }
    }
    final Kind kind;
    final long clockFloor;
    final Map<UUID, Entry> entries;
    final Reason reason;
    private final Tag raw;
    final long observedAtLeast;
    final long maximum;

    private P11CastCooldownData(Kind kind, long clockFloor, Map<UUID, Entry> entries, Reason reason,
            Tag raw, long observedAtLeast, long maximum) {
        this.kind = kind; this.clockFloor = clockFloor; this.reason = reason;
        var ordered = new TreeMap<UUID, Entry>(KEY_ORDER);
        ordered.putAll(entries);
        this.entries = Collections.unmodifiableMap(ordered);
        this.raw = raw; this.observedAtLeast = observedAtLeast; this.maximum = maximum;
    }
    static P11CastCooldownData unbound() {
        return new P11CastCooldownData(Kind.UNBOUND, 0, Map.of(), null, null, 0, 0);
    }
    static P11CastCooldownData routed(long floor, Map<UUID, Entry> entries) {
        if (floor < 0 || entries.size() > 256) { throw new IllegalArgumentException("COOLDOWN_ROUTED_BOUNDS"); }
        return new P11CastCooldownData(Kind.ROUTED, floor, entries, null, null, 0, 0);
    }
    static P11CastCooldownData raw(Reason reason, Tag alreadyBoundedCopy) {
        return new P11CastCooldownData(Kind.RAW, 0, Map.of(), reason,
                boundedCopy(alreadyBoundedCopy, 90180, 16), 0, 0);
    }
    static P11CastCooldownData marker(Reason reason, long observed, long maximum) {
        return new P11CastCooldownData(Kind.MARKER, 0, Map.of(), reason, null, observed, maximum);
    }
    Tag rawCopy() { return raw.copy(); }
    private static Tag boundedCopy(Tag raw, long maximum, int depth) {
        if (raw == null || raw.getId() == Tag.TAG_END
                || !P11CastCooldownNbtSize.measure(raw, maximum, depth).fits()) {
            throw new IllegalArgumentException("COOLDOWN_RAW_OWNERSHIP_BOUNDS");
        }
        return raw.copy();
    }
    P11CastCooldownData copy() {
        return new P11CastCooldownData(kind, clockFloor, entries, reason, raw == null ? null : raw.copy(),
                observedAtLeast, maximum);
    }

    static final class Entry {
        final UUID skillId;
        final int kind;
        final int revision;
        final int duration;
        final long acceptedAt;
        final UUID attemptId;
        final long releaseNotAfter;
        final long releasedAt;
        final long expiresAt;
        final Reason reason;
        final long uncertainAt;
        final int releaseKnowledge;
        private final Tag raw;
        final long observedAtLeast;
        final long maximum;

        Entry(UUID skillId, int kind, int revision, int duration, long acceptedAt, UUID attemptId,
                long releaseNotAfter, long releasedAt, long expiresAt, Reason reason,
                long uncertainAt, int releaseKnowledge, Tag alreadyBoundedCopy, long observed, long maximum) {
            this.skillId = skillId; this.kind = kind; this.revision = revision; this.duration = duration;
            this.acceptedAt = acceptedAt; this.attemptId = attemptId; this.releaseNotAfter = releaseNotAfter;
            this.releasedAt = releasedAt; this.expiresAt = expiresAt; this.reason = reason;
            this.uncertainAt = uncertainAt; this.releaseKnowledge = releaseKnowledge;
            this.raw = alreadyBoundedCopy; observedAtLeast = observed; this.maximum = maximum;
        }
        static Entry pending(UUID skill, int revision, int duration, long accepted, UUID attempt, long bound) {
            return new Entry(skill, 1, revision, duration, accepted, attempt, bound, -1, -1, null, 0, 0, null, 0, 0);
        }
        Entry active(long released) {
            return new Entry(skillId, 0, revision, duration, acceptedAt, attemptId, releaseNotAfter,
                    released, Math.addExact(released, duration), null, 0, 0, null, 0, 0);
        }
        Entry uncertain(Reason why, long now, boolean arm, long released) {
            return new Entry(skillId, 2, revision, duration, acceptedAt, attemptId, releaseNotAfter,
                    arm ? released : -1, arm ? Math.addExact(released, duration) : -1,
                    why, now, arm ? 1 : 0, null, 0, 0);
        }
        static Entry raw(UUID skill, Reason reason, Tag alreadyBoundedCopy) {
            return new Entry(skill, 3, 0, 0, 0, null, 0, 0, 0, reason, 0, 0,
                    boundedCopy(alreadyBoundedCopy, 297, 8), 0, 0);
        }
        static Entry marker(UUID skill, Reason reason, long observed, long maximum) {
            return new Entry(skill, 4, 0, 0, 0, null, 0, 0, 0, reason, 0, 0, null, observed, maximum);
        }
        Tag rawCopy() { return raw.copy(); }
    }
}
