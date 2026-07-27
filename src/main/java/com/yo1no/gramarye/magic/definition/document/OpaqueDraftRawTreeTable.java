package com.yo1no.gramarye.magic.definition.document;

import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/** Immutable raw side table hidden from every SkillDraft logical migration step. */
record OpaqueDraftRawTreeTable(
        CompoundTag originalTokenizedTree,
        List<Entry> entries) {
    OpaqueDraftRawTreeTable {
        originalTokenizedTree = Objects.requireNonNull(
                originalTokenizedTree, "originalTokenizedTree").copy();
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    @Override
    public CompoundTag originalTokenizedTree() {
        return originalTokenizedTree.copy();
    }

    record Entry(String token, Location location, RawTreeEnvelope raw) {
        Entry {
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(raw, "raw");
        }
    }

    sealed interface Location
        permits Location.Trigger,
                Location.Action,
                Location.AppearanceOverride,
                Location.TopAppearance {
    record Trigger(int nodeIndex) implements Location {
        public Trigger {
            requireNodeIndex(nodeIndex);
        }
    }

    record Action(int nodeIndex) implements Location {
        public Action {
            requireNodeIndex(nodeIndex);
        }
    }

    record AppearanceOverride(int nodeIndex) implements Location {
        public AppearanceOverride {
            requireNodeIndex(nodeIndex);
        }
    }

    enum TopAppearance implements Location {
        INSTANCE
    }

    private static void requireNodeIndex(int nodeIndex) {
        if (nodeIndex < 0) {
            throw new IllegalArgumentException("nodeIndex must be non-negative");
        }
    }
}
}
