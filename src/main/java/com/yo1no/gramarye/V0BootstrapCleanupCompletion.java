package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;

/** A catalog-only result; it never grants whole Sunset or cooldown retirement credit. */
record V0BootstrapCleanupCompletion(V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
        V0RuntimeCatalog.BootstrapCleanup result, V0RuntimeCatalog.BootstrapCleanupReceipt receipt,
        V0RuntimeCatalog.BootstrapRosterProof roster) {
    V0BootstrapCleanupCompletion(V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
            V0RuntimeCatalog.BootstrapCleanup result) {
        this(authority, ticket, owner, result, null, null);
    }

    V0BootstrapCleanupCompletion {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(result, "result");
        if (ticket <= 0) {
            throw new IllegalArgumentException("Invalid V0 Bootstrap cleanup completion ticket");
        }
    }
}
