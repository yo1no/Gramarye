package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.util.Objects;

/** Immutable owner data only; provisioning cannot replay a cast or retain its actor. */
record V0ProvisionRequest(V0CatalogAuthority authority, long ticket, SkillOwnerId owner) {
    V0ProvisionRequest {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(owner, "owner");
        if (ticket <= 0) {
            throw new IllegalArgumentException("Invalid V0 provision ticket");
        }
    }
}
