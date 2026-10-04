package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1StatsMemoryProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11QualifiedSourceOwner")
abstract class P11L1StatsFailureCountMixin {
    @WrapMethod(method = "finishWriter(Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;Lcom/yo1no/gramarye/P11ReceiptLedger$PhysicalWriterReceipt;ZJ)V",
            require = 1, expect = 1, allow = 1)
    private void p11$l1ActualStatsFailure(@Coerce Object body, @Coerce Object receipt, boolean success,
            long durationNanos, Operation<Void> original) {
        original.call(body, receipt, success, durationNanos);
        P11L1StatsMemoryProbe.writerFinished(this, body, receipt, success);
    }
}
