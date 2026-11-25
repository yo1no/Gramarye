package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;

record V0ProvisionCompletion(V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
                             V0RuntimeCatalog.BootstrapReadiness result) {
    V0ProvisionCompletion {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(result, "result");
        if (ticket <= 0) {
            throw new IllegalArgumentException("Invalid V0 provision completion ticket");
        }
    }
}
