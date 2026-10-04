package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aRateFairProbe;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aRateFairLiveMixin {
    @Shadow @Final private MinecraftServer server;
    @Unique private Object rf$limits;
    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lcom/yo1no/gramarye/P11StartupLimits;Lcom/yo1no/gramarye/P11IdentityOwner;Lcom/yo1no/gramarye/P11QualifiedSourceOwner;)V", at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void rf$constructed(MinecraftServer server, @Coerce Object limits,
            @Coerce Object identities, @Coerce Object sources, CallbackInfo ci) { rf$limits = limits; }
    @Inject(method = "tick()V", at = @At("TAIL"), require = 1, expect = 1, allow = 1)
    private void rf$limits(CallbackInfo ci) {
        try { P11C4aRateFairProbe.limits(server, rf$limits); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
    }
    @WrapMethod(method = "ingress(Lcom/yo1no/gramarye/P11TransitionProtocol$Request;Lnet/minecraft/network/Connection;Lnet/neoforged/neoforge/common/extensions/ICommonPacketListener;)V", require = 1, expect = 1, allow = 1)
    private void rf$ingress(@Coerce Object request, Connection connection, ICommonPacketListener listener, Operation<Void> original) {
        Object call = null;
        try { call = P11C4aRateFairProbe.entering(connection); }
        catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        try { original.call(request, connection, listener); }
        finally {
            try { P11C4aRateFairProbe.entered(call); }
            catch (RuntimeException | Error failure) { P11C4aRateFairProbe.observerFailed(); }
        }
    }
}
