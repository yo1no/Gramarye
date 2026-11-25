package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.V0CatalogAuthority;
import com.yo1no.gramarye.U0OrdinaryTrace;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import com.yo1no.gramarye.magic.definition.scoped.U021FutureCatalogUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Excluded future-policy qualification only: same existing owned cold invocation. */
@Mixin(targets = "com.yo1no.gramarye.V0NormalColdLane")
abstract class U021FutureCatalogColdMixin {
    @WrapOperation(method = "prepare(Lcom/yo1no/gramarye/V0CaptureRequest;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog;prepare(Lcom/yo1no/gramarye/V0CatalogAuthority;JLcom/yo1no/gramarye/magic/api/id/SkillOwnerId;Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog$Selection;I)Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog$PrepareResult;"),
            require = 1, expect = 1, allow = 1)
    private V0RuntimeCatalog.PrepareResult u021$prepare(V0RuntimeCatalog catalog,
            V0CatalogAuthority authority, long ticket, SkillOwnerId owner,
            V0RuntimeCatalog.Selection selection, int maxManifestEntries,
            Operation<V0RuntimeCatalog.PrepareResult> original) {
        U021FutureCatalogUpdate.beforeOriginalPrepare(catalog, authority, ticket, owner, selection);
        V0RuntimeCatalog.PrepareResult actual;
        long startedNanos = System.nanoTime();
        try { actual = original.call(catalog, authority, ticket, owner, selection, maxManifestEntries); }
        finally {
            long elapsedNanos = System.nanoTime() - startedNanos;
            try { U0OrdinaryTrace.note(elapsedNanos < 0 ? "INVALID_CATALOG_TIMING" : "CATALOG_PREPARE_NANOS",
                    elapsedNanos, ticket, 0, 0, 0, 0, 0, 0, 0, ""); }
            catch (RuntimeException | Error observationFailure) {
                try { U0OrdinaryTrace.note("OBSERVATION_FAILURE",0,0,0,0,0,0,0,0,0,""); }
                catch (RuntimeException | Error recorderFailure) { /* Original prepare primary is preserved. */ }
            }
        }
        try { U021FutureCatalogUpdate.observeOriginalPrepare(catalog, authority, ticket, owner, selection, actual); }
        catch (RuntimeException | Error observationFailure) {
            try { U0OrdinaryTrace.note("OBSERVATION_FAILURE",0,0,0,0,0,0,0,0,0,""); }
            catch (RuntimeException | Error recorderFailure) { /* The actual original return remains primary. */ }
        }
        return actual;
    }
}
