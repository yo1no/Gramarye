package com.yo1no.gramarye.magic.definition.player;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/** Exact reserved physical representation of destructive oversize quarantine. */
final class PlayerSkillAttachmentMarker {
    static final String ROOT_FIELD = "__gramarye_attachment_quarantine_v0";
    private static final String SCHEMA_VERSION = "schema_version";
    private static final String CODE = "code";
    private static final String OBSERVED_AT_LEAST = "observed_at_least";
    private static final String MAXIMUM = "maximum";
    private static final String CAPACITY_CODE = "encoded_capacity_exceeded";
    private static final Set<String> OUTER_FIELDS = Set.of(ROOT_FIELD);
    private static final Set<String> INNER_FIELDS =
            Set.of(SCHEMA_VERSION, CODE, OBSERVED_AT_LEAST, MAXIMUM);

    private PlayerSkillAttachmentMarker() {
    }

    static boolean isExact(Tag input) {
        if (!(input instanceof CompoundTag outer)
                || !outer.getAllKeys().equals(OUTER_FIELDS)
                || !(outer.get(ROOT_FIELD) instanceof CompoundTag marker)
                || !marker.getAllKeys().equals(INNER_FIELDS)
                || !(marker.get(SCHEMA_VERSION) instanceof IntTag schema)
                || schema.getAsInt() != 0
                || !(marker.get(CODE) instanceof StringTag code)
                || !CAPACITY_CODE.equals(code.getAsString())
                || !(marker.get(OBSERVED_AT_LEAST) instanceof LongTag observed)
                || !(marker.get(MAXIMUM) instanceof LongTag maximum)) {
            return false;
        }
        return observed.getAsLong() == AttachmentTagSize.observedAtLeast()
                && maximum.getAsLong() == AttachmentTagSize.maximum();
    }

    static CompoundTag freshTag() {
        var marker = new CompoundTag();
        marker.putInt(SCHEMA_VERSION, 0);
        marker.putString(CODE, CAPACITY_CODE);
        marker.putLong(
                OBSERVED_AT_LEAST,
                AttachmentTagSize.observedAtLeast());
        marker.putLong(MAXIMUM, AttachmentTagSize.maximum());
        var outer = new CompoundTag();
        outer.put(ROOT_FIELD, marker);
        return outer;
    }
}
