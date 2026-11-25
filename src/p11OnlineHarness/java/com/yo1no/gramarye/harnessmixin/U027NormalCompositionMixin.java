package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** EXCLUDED QUALIFICATION: future-policy variant only; never included in either product JAR. */
@Mixin(targets = "com.yo1no.gramarye.V0NormalComposition")
abstract class U027NormalCompositionMixin {
    @Coerce
    @WrapOperation(method = "open(Lcom/yo1no/gramarye/V0SlotStartup;)Ljava/util/Optional;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/V0SlotBinding;createNormal(Lcom/yo1no/gramarye/magic/definition/scoped/V0RuntimeCatalog;Lcom/yo1no/gramarye/V0ColdMailbox;Lcom/yo1no/gramarye/V0NormalColdLane;)Lcom/yo1no/gramarye/V0SlotBinding;"),
            require = 1, expect = 1, allow = 1)
    private static Object u027$policy(@Coerce Object catalog, @Coerce Object mailbox, @Coerce Object cold,
            Operation<Object> original, @Coerce Object startup) {
        // The last parameter captures the sole enclosing method argument; no local/field read or shadow.
        return com.yo1no.gramarye.U027FuturePolicyBridge.bind(catalog, mailbox, cold, startup, original);
    }
}
