package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain already negotiated metadata only for the exact native same-connection return path. */
@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class P11ClientConfigurationMixin {
    // The pinned subclass redeclares this field and otherwise resets it to OTHER,
    // although ClientCommonPacketListenerImpl has already copied the same cookie.
    @Shadow private ConnectionType connectionType;
    @Shadow private boolean initializedConnection;

    @Inject(method = "<init>(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/Connection;Lnet/minecraft/client/multiplayer/CommonListenerCookie;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$inheritEstablishedNegotiation(Minecraft minecraft, Connection connection,
            CommonListenerCookie cookie, CallbackInfo callback) {
        if (P11ClientTransitions.canInheritConfigurationNegotiation(minecraft, connection, cookie)) {
            connectionType = cookie.connectionType();
            initializedConnection = true;
        }
    }
}
