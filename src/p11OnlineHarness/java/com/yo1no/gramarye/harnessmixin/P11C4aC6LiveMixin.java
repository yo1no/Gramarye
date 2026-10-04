package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aC6NativeProbe;
import com.yo1no.gramarye.P11C4aC6ExpiryProbe;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.ArrayList;
import java.util.Map;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only this isolated mixin package is reserved by the loader, never the production package. */
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aC6LiveMixin {
    @Shadow @Final private MinecraftServer server;
    @Unique private Object c6$limits;
    @Shadow @Final private Map<Connection, ?> entries;
    @Shadow private int waiting;
    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/P11StartupLimits;Lcom/yo1no/gramarye/P11IdentityOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void c6$constructed(MinecraftServer server, @Coerce Object limits,
            @Coerce Object identities, @Coerce Object sources, CallbackInfo ci) {
        c6$limits = limits;
    }
    @Inject(method = "prepareAndDispatch()V", at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void c6$before(CallbackInfo ci) { c6$sample(); }
    @Inject(method = "prepareAndDispatch()V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void c6$after(CallbackInfo ci) { c6$sample(); }
    @WrapOperation(method = "disconnect(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;Ljava/lang/String;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"),
            require = 1, expect = 1, allow = 1)
    private void c6$expiryDisconnect(ServerCommonPacketListenerImpl listener, Component message,
            Operation<Void> original, @Coerce Object entry, String reason) {
        P11C4aC6ExpiryProbe.disconnecting(server, this, entry, reason, listener);
        boolean normal = false;
        try { original.call(listener, message); normal = true; }
        finally { P11C4aC6ExpiryProbe.disconnected(server, entry, normal); }
    }
    @Inject(method = "retire(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V",
            at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void c6$retired(@Coerce Object entry, CallbackInfo ci) {
        int k; boolean present;
        synchronized (entries) { k = waiting; present = entries.containsKey(((P11C4aC6NativeProbe.EntryView) entry).c6$connection()); }
        P11C4aC6ExpiryProbe.retired(server, this, entry, k, present);
    }
    @Unique private void c6$sample() {
        var values = new ArrayList<P11C4aC6NativeProbe.EntrySample>();
        int k;
        synchronized (entries) {
            k = waiting;
            for (var raw : entries.values()) {
                var entry = (P11C4aC6NativeProbe.EntryView) raw;
                values.add(new P11C4aC6NativeProbe.EntrySample(entry.c6$connection(), entry.c6$control(),
                        entry.c6$waiting(), entry.c6$capacityRejected(), entry.c6$terminalPhase()));
            }
        }
        P11C4aC6NativeProbe.live(server, this, c6$limits, k, values);
    }
}
