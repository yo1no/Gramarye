package com.yo1no.gramarye.magic.definition.document;

/** Typed logical location for one migration-opaque raw subtree. */
sealed interface OpaqueRawTreeLocation
        permits OpaqueRawTreeLocation.TriggerPayload,
                OpaqueRawTreeLocation.ActionPayload,
                OpaqueRawTreeLocation.AppearanceOverride,
                OpaqueRawTreeLocation.TopAppearance {
    record TriggerPayload(int nodeIndex) implements OpaqueRawTreeLocation {
        public TriggerPayload {
            requireNodeIndex(nodeIndex);
        }
    }

    record ActionPayload(int nodeIndex) implements OpaqueRawTreeLocation {
        public ActionPayload {
            requireNodeIndex(nodeIndex);
        }
    }

    record AppearanceOverride(int nodeIndex) implements OpaqueRawTreeLocation {
        public AppearanceOverride {
            requireNodeIndex(nodeIndex);
        }
    }

    enum TopAppearance implements OpaqueRawTreeLocation {
        INSTANCE
    }

    private static void requireNodeIndex(int nodeIndex) {
        if (nodeIndex < 0) {
            throw new IllegalArgumentException("nodeIndex must be non-negative");
        }
    }
}
