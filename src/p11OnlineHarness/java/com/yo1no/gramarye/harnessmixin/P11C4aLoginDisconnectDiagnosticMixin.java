package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aClientHarness;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only fixed-category observation; original disconnect/screen and error policy are unchanged. */
@Mixin(ClientHandshakePacketListenerImpl.class)
abstract class P11C4aLoginDisconnectDiagnosticMixin {
    @Shadow @Final private Connection connection;

    @Inject(method = "onDisconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"),
            require = 1, expect = 1, allow = 1)
    private void diagnostic$nativeLoginClosed(DisconnectionDetails details, CallbackInfo callback) {
        try {
            if (connection.getPacketListener() == (Object) this) {
                P11C4aClientHarness.loginDisconnected(connection, details.reason());
            }
        } catch (RuntimeException | Error secondary) {
            // No observer failure may replace original native disconnect or presentation.
        }
    }
}
