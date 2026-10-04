package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aC6NativeProbe;
import java.util.UUID;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService$Entry", remap = false)
abstract class P11C4aC6EntryViewMixin implements P11C4aC6NativeProbe.EntryView {
    @Shadow @Final private Connection connection;
    @Unique private Object c6$control;
    @Shadow private boolean waiting;
    @Shadow private volatile boolean configurationCapacityRejected;
    @Shadow private volatile long configurationTerminalPhase;
    public Connection c6$connection() { return connection; }
    @Inject(method = "<init>(Lnet/minecraft/network/Connection;Ljava/util/UUID;Lcom/yo1no/gramarye/P11TransitionControl;Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher$Member;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c6$constructed(Connection connection, UUID playerId, @Coerce Object control,
            @Coerce Object member, CallbackInfo ci) { c6$control = control; }
    public Object c6$control() { return c6$control; }
    public boolean c6$waiting() { return waiting; }
    public boolean c6$capacityRejected() { return configurationCapacityRejected; }
    public long c6$terminalPhase() { return configurationTerminalPhase; }
}
