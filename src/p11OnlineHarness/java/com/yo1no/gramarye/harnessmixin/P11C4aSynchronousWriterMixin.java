package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aSynchronousWriterProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Observe the exact original admission/result without touching its writer or source policy. */
@Mixin(targets = "com.yo1no.gramarye.P11QualifiedSourceOwner", remap = false)
abstract class P11C4aSynchronousWriterMixin {
    @WrapMethod(method = "beginSynchronousPlayerWriter(Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;)Lcom/yo1no/gramarye/P11ReceiptLedger$PhysicalWriterReceipt;",
            require = 1, expect = 1, allow = 1)
    @Coerce
    private Object c4a$beginSynchronousWriter(@Coerce Object body, Operation<Object> original) {
        var observation = P11C4aSynchronousWriterProbe.before(this, body,
                P11C4aSynchronousWriterProbe.Point.BEGIN_PLAYER_WRITER);
        Object receipt = null;
        boolean normal = false;
        try {
            receipt = original.call(body);
            normal = true;
            return receipt;
        } finally {
            P11C4aSynchronousWriterProbe.after(observation, this, body, receipt, normal, false);
        }
    }

    @WrapMethod(method = "completedPlayerWrite(Lcom/yo1no/gramarye/P11QualifiedSourceOwner$Body;Lcom/yo1no/gramarye/P11ReceiptLedger$PhysicalWriterReceipt;)Z",
            require = 1, expect = 1, allow = 1)
    private boolean c4a$completedSynchronousWriter(@Coerce Object body, @Coerce Object receipt,
            Operation<Boolean> original) {
        var observation = P11C4aSynchronousWriterProbe.before(this, body,
                P11C4aSynchronousWriterProbe.Point.COMPLETED_PLAYER_WRITE);
        boolean result = false;
        boolean normal = false;
        try {
            result = original.call(body, receipt);
            normal = true;
            return result;
        } finally {
            P11C4aSynchronousWriterProbe.after(observation, this, body, receipt, normal, result);
        }
    }
}
