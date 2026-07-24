package com.yo1no.gramarye.magic.definition.tree;

import java.util.Objects;

/** Immutable serialization context for one supported JSON or NBT tree. */
public record SerializedTreeContext(
        SerializedTreeFamily family,
        boolean registryContext,
        boolean compressedMaps) {
    public SerializedTreeContext {
        Objects.requireNonNull(family, "family");
        if (family == SerializedTreeFamily.NBT && compressedMaps) {
            throw new IllegalArgumentException("NBT trees cannot use compressed maps");
        }
    }
}
