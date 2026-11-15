package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;

/** Test access to the real P5 slot capability issuers. */
public final class V0CatalogTestAuthority {
    private V0CatalogTestAuthority() {}

    public static V0CatalogAuthority open(int manifestEntries) {
        return V0CatalogAuthority.open(1, new V0StartupLimits(2, 4, 2, manifestEntries, 24, 24, 40, 16, 0));
    }

    public static V0CatalogAuthority.Reservation reserve(V0CatalogAuthority owner,
                                                         V0RuntimeCatalog.Candidate candidate) {
        return owner.reserve(candidate);
    }

    public static V0CatalogAuthority.ReleasePermit release(V0CatalogAuthority owner, V0RuntimeCatalog.Lease lease) {
        return owner.permitRelease(lease);
    }
}
