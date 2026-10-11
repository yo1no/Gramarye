package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1P8FallbackClientProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P8ClientPresentationExecution")
abstract class P11L1P8FallbackExecutionMixin {
    @Inject(method = "<init>(Lcom/yo1no/gramarye/P8ClientRegistryView;Lcom/yo1no/gramarye/P8ClientPresentationBackend;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$originalExecution(@Coerce Object registry, @Coerce Object backend, CallbackInfo callback) {
        P11L1P8FallbackClientProbe.constructed(this, backend);
    }

    @Coerce
    @WrapMethod(method = "present(Lcom/yo1no/gramarye/PresentationEventPayload;Lcom/yo1no/gramarye/P8ProfileCatalogSnapshot;Lcom/yo1no/gramarye/P8ClientResourceIndex;JJJ)Lcom/yo1no/gramarye/P8ClientPresentationHandoffResult;",
            require = 1, expect = 1, allow = 1)
    private Object p11$originalPresent(@Coerce Object payload, @Coerce Object catalog, @Coerce Object resources,
            long connection, long world, long resource, Operation<Object> original) {
        boolean observed = P11L1P8FallbackClientProbe.entering(this, payload, connection, world, resource);
        Object result = null; Throwable primary = null;
        try { result = original.call(payload, catalog, resources, connection, world, resource); return result; }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally { P11L1P8FallbackClientProbe.finished(observed, result, primary); }
    }
}
