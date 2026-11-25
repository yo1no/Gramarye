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
        ENTRY_LIMIT(7), CLOCK(8), PUBLICATION_UNKNOWN(9), RELEASE_UNKNOWN(10), SAVE_FAILURE(11), ORPHAN_PENDING(12),
        LEGACY_ENTRY_LIMIT(13), POLICY_ENTRY_LIMIT(14);
        final int code;
        Reason(int code) { this.code = code; }
        static Reason of(int value) {
            for (var reason : values()) { if (reason.code == value) { return reason; } }
            return null;
        }
    }
    final Kind kind;
    final long clockFloor;
    final int schemaVersion;
    final Map<P11CooldownKey, Entry> entries;
    final Reason reason;
    private final Tag raw;
    final long observedAtLeast;
    final long maximum;

    private P11CastCooldownData(int schemaVersion, Kind kind, long clockFloor, Map<P11CooldownKey, Entry> entries, Reason reason,
            Tag raw, long observedAtLeast, long maximum) {
        if (schemaVersion != 1 && schemaVersion != 2) { throw new IllegalArgumentException("COOLDOWN_MATERIAL_VERSION"); }
        this.schemaVersion = schemaVersion; this.kind = kind; this.clockFloor = clockFloor; this.reason = reason;
        var ordered = new TreeMap<P11CooldownKey, Entry>(P11CooldownKey.ORDER);
        ordered.putAll(entries);
        this.entries = Collections.unmodifiableMap(ordered);
        this.raw = raw; this.observedAtLeast = observedAtLeast; this.maximum = maximum;
    }
    static P11CastCooldownData unbound() {
        return new P11CastCooldownData(1, Kind.UNBOUND, 0, Map.of(), null, null, 0, 0);
    }
    /** Original schema1 constructor. Its UUIDs are actual legacy SkillIds. */
    static P11CastCooldownData routed(long floor, Map<UUID, Entry> entries) {
        if (floor < 0 || entries.size() > 256) { throw new IllegalArgumentException("COOLDOWN_ROUTED_BOUNDS"); }
        var typed = new TreeMap<P11CooldownKey, Entry>(P11CooldownKey.ORDER);
        entries.forEach((id, entry) -> typed.put(P11CooldownKey.legacy(id), entry));
        return routed(1, floor, typed);
    }
    static P11CastCooldownData routed(int version, long floor, Map<P11CooldownKey, Entry> entries) {
        if ((version != 1 && version != 2) || floor < 0 || entries.size() > (version == 1 ? 256 : 320)) {
            throw new IllegalArgumentException("COOLDOWN_ROUTED_BOUNDS");
        }
        int legacy = 0, policy = 0;
        for (var row : entries.entrySet()) {
            if (row.getKey() == null || row.getValue() == null || !row.getKey().equals(row.getValue().key)) {
                throw new IllegalArgumentException("COOLDOWN_ROUTED_KEY");
            }
            if (row.getKey() instanceof P11CooldownKey.LegacySkill) { legacy++; } else { policy++; }
        }
        if ((version != 1 && version != 2) || floor < 0 || legacy > 256 || policy > 64 || version == 1 && policy != 0) {
            throw new IllegalArgumentException("COOLDOWN_ROUTED_BOUNDS");
        }
        return new P11CastCooldownData(version, Kind.ROUTED, floor, entries, null, null, 0, 0);
    }
    P11CastCooldownData replacing(long floor, Map<P11CooldownKey, Entry> entries) {
        return routed(schemaVersion, floor, entries);
    }
    static P11CastCooldownData raw(Reason reason, Tag alreadyBoundedCopy) { return raw(1, reason, alreadyBoundedCopy); }
    static P11CastCooldownData raw(int version, Reason reason, Tag alreadyBoundedCopy) {
        return new P11CastCooldownData(version, Kind.RAW, 0, Map.of(), reason,
                boundedCopy(alreadyBoundedCopy, version == 2 ? 125572 : 90180, 16), 0, 0);
    }
    static P11CastCooldownData marker(Reason reason, long observed, long maximum) { return marker(1, reason, observed, maximum); }
    static P11CastCooldownData marker(int version, Reason reason, long observed, long maximum) {
        return new P11CastCooldownData(version, Kind.MARKER, 0, Map.of(), reason, null, observed, maximum);
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
        return new P11CastCooldownData(schemaVersion, kind, clockFloor, entries, reason, raw == null ? null : raw.copy(),
                observedAtLeast, maximum);
    }

    static final class Entry {
        final P11CooldownKey key;
        /** Legacy-only identity; policy entries never borrow a SkillId. */
        final UUID skillId;
        final int releaseWindow;
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
            this(P11CooldownKey.legacy(skillId), kind, revision, duration, acceptedAt, attemptId,
                    releaseNotAfter, 101, releasedAt, expiresAt, reason, uncertainAt, releaseKnowledge,
                    alreadyBoundedCopy, observed, maximum);
        }
        Entry(P11CooldownKey key, int kind, int revision, int duration, long acceptedAt, UUID attemptId,
                long releaseNotAfter, int releaseWindow, long releasedAt, long expiresAt, Reason reason,
                long uncertainAt, int releaseKnowledge, Tag alreadyBoundedCopy, long observed, long maximum) {
            this.key = key; this.releaseWindow = releaseWindow;
            this.skillId = key instanceof P11CooldownKey.LegacySkill legacy ? legacy.skillId().value() : null;
            this.kind = kind; this.revision = revision; this.duration = duration;
            this.acceptedAt = acceptedAt; this.attemptId = attemptId; this.releaseNotAfter = releaseNotAfter;
            this.releasedAt = releasedAt; this.expiresAt = expiresAt; this.reason = reason;
            this.uncertainAt = uncertainAt; this.releaseKnowledge = releaseKnowledge;
            this.raw = alreadyBoundedCopy; observedAtLeast = observed; this.maximum = maximum;
        }
        static Entry pending(UUID skill, int revision, int duration, long accepted, UUID attempt, long bound) {
            return new Entry(skill, 1, revision, duration, accepted, attempt, bound, -1, -1, null, 0, 0, null, 0, 0);
        }
        static Entry pendingPolicy(P11CooldownKey.LogicalPolicy key, int duration, long accepted, UUID attempt, int window) {
            if (duration < 1 || duration > 600 || window < 4 || window > 41 || accepted < 0
                    || accepted > Long.MAX_VALUE - window - duration || attempt == null) {
                throw new IllegalArgumentException("COOLDOWN_POLICY_PENDING_BOUNDS");
            }
            return new Entry(key, 1, 0, duration, accepted, attempt, accepted + window,
                    window, -1, -1, null, 0, 0, null, 0, 0);
        }
        Entry active(long released) {
            return new Entry(key, 0, revision, duration, acceptedAt, attemptId, releaseNotAfter, releaseWindow,
                    released, Math.addExact(released, duration), null, 0, 0, null, 0, 0);
        }
        Entry uncertain(Reason why, long now, boolean arm, long released) {
            return new Entry(key, 2, revision, duration, acceptedAt, attemptId, releaseNotAfter, releaseWindow,
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
        static Entry policyRaw(P11CooldownKey.LogicalPolicy key, Reason reason, Tag alreadyBoundedCopy) {
            return new Entry(key, 3, 0, 0, 0, null, 0, 0, 0, 0, reason, 0, 0,
                    boundedCopy(alreadyBoundedCopy, 278, 8), 0, 0);
        }
        static Entry policyMarker(P11CooldownKey.LogicalPolicy key, Reason reason, long observed, long maximum) {
            return new Entry(key, 4, 0, 0, 0, null, 0, 0, 0, 0, reason, 0, 0, null, observed, maximum);
        }
        Tag rawCopy() { return raw.copy(); }
    }
}
