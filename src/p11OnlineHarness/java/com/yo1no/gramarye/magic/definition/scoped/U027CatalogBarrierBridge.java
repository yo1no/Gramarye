package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.V0CatalogAuthority;

/** EXCLUDED TEST-ONLY bridge to an existing package-private lifecycle method; absent from both distributables. */
public final class U027CatalogBarrierBridge {
    private U027CatalogBarrierBridge() { }
    public static boolean seal(V0RuntimeCatalog catalog, V0CatalogAuthority authority) {
        return catalog != null && authority != null && authority.isOwnerThread() && catalog.stopBootstrap(authority);
    }
}
