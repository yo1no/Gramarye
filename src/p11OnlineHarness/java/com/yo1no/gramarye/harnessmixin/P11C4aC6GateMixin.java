package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aC6EarlyGateProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** EXTERNAL DRAFT: two named native reentrancy locations, not a gate/permit replacement. */
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aC6GateMixin {
    @WrapOperation(method = "earlyTask(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Lcom/yo1no/gramarye/P11TransitionControl$Drain;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11LiveTransitionService;gate(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)Lcom/yo1no/gramarye/P11TransitionControl$Gate;"),
            require = 1, expect = 1, allow = 1)
    @Coerce
    private Object c6$earlyGate(@Coerce Object service, @Coerce Object entry, Operation<Object> original) {
        return P11C4aC6EarlyGateProbe.originalGate(service, entry,
                P11C4aC6EarlyGateProbe.Point.CONFIG_EARLY, original);
    }

    @WrapOperation(method = "admit(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Lcom/yo1no/gramarye/P11TransitionControl$Drain;Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;)Lcom/yo1no/gramarye/P11LiveTransitionService$Ticket;",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11LiveTransitionService;gate(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)Lcom/yo1no/gramarye/P11TransitionControl$Gate;"),
            require = 1, expect = 1, allow = 1)
    @Coerce
    private Object c6$retryGate(@Coerce Object service, @Coerce Object entry, Operation<Object> original) {
        return P11C4aC6EarlyGateProbe.originalGate(service, entry,
                ((P11C4aC6EarlyGateProbe.EntryConnection) entry).p11$c6Connection().getPacketListener()
                    instanceof com.yo1no.gramarye.P11ParkingPacketListener
                    ? P11C4aC6EarlyGateProbe.Point.PREPLAY_RETRY : P11C4aC6EarlyGateProbe.Point.PLAY_FIRST,
                original);
    }
}
