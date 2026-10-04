package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aHostExpiryProbe;
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
abstract class P11C4aHostExpiryEntryMixin implements P11C4aHostExpiryProbe.EntryView {
    @Shadow @Final private Connection connection;
    @Unique private Object expiry$control;
    public Connection expiry$connection() { return connection; }
    public Object expiry$control() { return expiry$control; }
    @Inject(method = "<init>(Lnet/minecraft/network/Connection;Ljava/util/UUID;Lcom/yo1no/gramarye/P11TransitionControl;Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher$Member;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void expiry$constructed(Connection connection, UUID id, @Coerce Object control, @Coerce Object member, CallbackInfo ci) { expiry$control = control; }
}
