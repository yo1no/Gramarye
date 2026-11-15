package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;

record V0CaptureCompletion(V0CatalogAuthority authority, long ticket,
        V0RuntimeCatalog.PrepareResult result) {
    V0CaptureCompletion {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(result, "result");
        if (ticket <= 0) {
            throw new IllegalArgumentException("Invalid V0 capture completion ticket");
        }
    }
}
