package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeOperationBoundary;
import java.util.Deque;
import java.util.List;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExecutionContext.class)
abstract class P11ExecutionContextMixin implements P11NativeOperationBoundary.ContextAccess {
    @Shadow private int commandQuota;
    @Shadow @Final private Deque<CommandQueueEntry<?>> commandQueue;
    @Shadow @Final private List<CommandQueueEntry<?>> newTopCommands;
    @Unique private final P11NativeOperationBoundary.Context p11$context =
            P11NativeOperationBoundary.createContext((ExecutionContext<?>) (Object) this);

    @Override public P11NativeOperationBoundary.Context p11$nativeContext() { return p11$context; }

    @WrapMethod(method = "runCommandQueue()V", require = 1, expect = 1, allow = 1)
    private void p11$drain(Operation<Void> original) {
        P11NativeOperationBoundary.queueStarted(p11$context);
        boolean normal = false;
        try { original.call(); normal = true; }
        finally {
            P11NativeOperationBoundary.queueReturned(p11$context, normal, commandQuota,
                    commandQueue.isEmpty(), newTopCommands.isEmpty());
        }
    }

    @Inject(method = "handleQueueOverflow()V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$overflow(CallbackInfo callback) { P11NativeOperationBoundary.queueOverflow(p11$context); }

    @Inject(method = "discardAtDepthOrHigher(I)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$discard(int depth, CallbackInfo callback) { P11NativeOperationBoundary.queueDiscarded(p11$context); }

    @WrapMethod(method = "close()V", require = 1, expect = 1, allow = 1)
    private void p11$tracerClose(Operation<Void> original) {
        try { original.call(); }
        finally { P11NativeOperationBoundary.tracerClosed(p11$context); }
    }
}
