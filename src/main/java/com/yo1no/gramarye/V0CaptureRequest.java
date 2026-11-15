package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.util.Objects;

record V0CaptureRequest(V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
        V0RuntimeCatalog.Selection selection, long inclusivePreparationDeadlineTick,
        int maxManifestEntries) {
    V0CaptureRequest {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(selection, "selection");
        if (ticket <= 0 || inclusivePreparationDeadlineTick < 0 || maxManifestEntries <= 0) {
            throw new IllegalArgumentException("Invalid V0 capture request");
        }
    }
}
