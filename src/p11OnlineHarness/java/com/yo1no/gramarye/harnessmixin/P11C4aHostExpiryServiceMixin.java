package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aHostExpiryProbe;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aHostExpiryServiceMixin implements P11C4aHostExpiryProbe.ServiceView {
    @Shadow @Final private Map<Connection, ?> entries;
    @Shadow private int waiting;
    @Shadow private volatile boolean stopping;
    @Unique private Object expiry$limits;
    public Object expiry$limits() { return expiry$limits; }
    public P11C4aHostExpiryProbe.Snapshot expiry$snapshot(Connection connection) {
        synchronized (entries) { return new P11C4aHostExpiryProbe.Snapshot(waiting, entries.containsKey(connection), stopping); }
    }
    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/P11StartupLimits;Lcom/yo1no/gramarye/P11IdentityOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void expiry$constructed(MinecraftServer server, @Coerce Object limits, @Coerce Object identities, @Coerce Object source, CallbackInfo ci) { expiry$limits = limits; }
    @WrapOperation(method = "earlyTask(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Lcom/yo1no/gramarye/P11TransitionControl$Drain;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11LiveTransitionService;gate(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)Lcom/yo1no/gramarye/P11TransitionControl$Gate;"),
            require = 1, expect = 1, allow = 1)
    @Coerce private Object expiry$gate(@Coerce Object service, @Coerce Object entry, Operation<Object> original) {
        return P11C4aHostExpiryProbe.originalGate(service, entry, original);
    }
    @Inject(method = "disconnect(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Ljava/lang/String;)V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void expiry$disconnectEntry(@Coerce Object entry, String reason, CallbackInfo ci) { P11C4aHostExpiryProbe.disconnect(this, entry, reason, false); }
    @Inject(method = "disconnect(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Ljava/lang/String;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void expiry$disconnectReturn(@Coerce Object entry, String reason, CallbackInfo ci) { P11C4aHostExpiryProbe.disconnect(this, entry, reason, true); }
    @Inject(method = "retire(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void expiry$retired(@Coerce Object entry, CallbackInfo ci) { P11C4aHostExpiryProbe.retired(this, entry); }
    @Inject(method = "stop()V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void expiry$stopped(CallbackInfo ci) { P11C4aHostExpiryProbe.stoppedControl(this); }
}
