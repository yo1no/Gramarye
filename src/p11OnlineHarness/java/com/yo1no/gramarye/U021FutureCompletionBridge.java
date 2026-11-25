package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.definition.scoped.U021FutureCatalogUpdate;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;

/** Excluded qualifier bridge. The caller passes only actual original method arguments/result. */
public final class U021FutureCompletionBridge {
    private U021FutureCompletionBridge() { }
    public static void observeOriginalReturn(Object suppliedCompletion, Object originalResult) {
        if (!(suppliedCompletion instanceof V0CaptureCompletion completion)
                || !(completion.result() instanceof V0RuntimeCatalog.Candidate candidate)) { return; }
        if (originalResult == V0Admission.ACCEPTED) {
            U021FutureCatalogUpdate.afterActualAccepted(completion.authority(), completion.ticket(), candidate);
        } else {
            U021FutureCatalogUpdate.afterActualOther(completion.authority(), completion.ticket(), candidate);
        }
    }
    public static void stopped(V0CatalogAuthority authority) { U021FutureCatalogUpdate.stopped(authority); }
}
