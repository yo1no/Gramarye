package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Bounded, non-persistent and machine-readable tolerant-read provenance. */
public record ReadFact(
        ReadFactCode code,
        ReadLocationKind locationKind,
        OptionalInt nodeIndex,
        Optional<AppearanceField> field) {
    public ReadFact {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(locationKind, "locationKind");
        Objects.requireNonNull(nodeIndex, "nodeIndex");
        field = Objects.requireNonNull(field, "field");
        if (nodeIndex.isPresent() && nodeIndex.getAsInt() < 0) {
            throw new IllegalArgumentException("nodeIndex must be non-negative");
        }
    }
}
