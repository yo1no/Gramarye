package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.List;
import java.util.Objects;

/** Only immutable owner data and its one-use cleanup capability cross to the cold owner. */
record V0BootstrapCleanupRequest(V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
        V0CatalogAuthority.BootstrapRetirementPermit permit, V0RuntimeCatalog.BootstrapRosterProof roster,
        List<V0RuntimeCatalog.BootstrapCleanupReceipt> receipts) {
    V0BootstrapCleanupRequest(V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
            V0CatalogAuthority.BootstrapRetirementPermit permit) {
        this(authority, ticket, owner, permit, null, null);
    }

    static V0BootstrapCleanupRequest roster(V0CatalogAuthority authority, long ticket,
            List<V0RuntimeCatalog.BootstrapCleanupReceipt> receipts) {
        return new V0BootstrapCleanupRequest(authority, ticket, V0BootstrapRetirementCoordinator.SEED_OWNER,
                null, null, List.copyOf(receipts));
    }

    static V0BootstrapCleanupRequest seed(V0CatalogAuthority authority, long ticket,
            V0CatalogAuthority.BootstrapRetirementPermit permit, V0RuntimeCatalog.BootstrapRosterProof roster) {
        return new V0BootstrapCleanupRequest(authority, ticket, V0BootstrapRetirementCoordinator.SEED_OWNER,
                permit, Objects.requireNonNull(roster), null);
    }

    V0BootstrapCleanupRequest {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(owner, "owner");
        boolean seed = owner.equals(V0BootstrapRetirementCoordinator.SEED_OWNER);
        if (receipts != null) {
            receipts = List.copyOf(receipts);
            if (!seed || permit != null || roster != null || receipts.size() > 31) {
                throw new IllegalArgumentException("Invalid V0 Bootstrap roster request");
            }
        } else if (permit == null || seed != (roster != null)) {
            throw new IllegalArgumentException("Invalid V0 Bootstrap cleanup capability");
        }
        if (ticket <= 0) {
            throw new IllegalArgumentException("Invalid V0 Bootstrap cleanup ticket");
        }
    }
}
