package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class P11ClientMinecraftMixin implements P11ClientTransitions.TransportTickAccess {
    @Shadow private Connection pendingConnection;

    @Override
    public Connection p11$pendingConnection() { return pendingConnection; }

    @WrapMethod(method = "setScreen(Lnet/minecraft/client/gui/screens/Screen;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$deferPendingScreen(Screen requested, Operation<Void> original) {
        boolean tracked = P11ClientTransitions.beginScreenChange();
        boolean normal = false;
        try {
            original.call(requested);
            normal = true;
        } finally {
            P11ClientTransitions.endScreenChange(tracked, normal);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1)
    private void p11$releaseConnection(Screen screen, boolean transfer, CallbackInfo ci) {
        P11ClientTransitions.disconnected();
    }
}
