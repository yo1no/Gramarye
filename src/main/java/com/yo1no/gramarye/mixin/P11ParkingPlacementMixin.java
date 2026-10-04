package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11ConfigurationBoundary;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerList.class)
abstract class P11ParkingPlacementMixin {
    @WrapOperation(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$samePlayHandoff(Connection connection, ProtocolInfo<?> protocol, PacketListener listener,
            Operation<Void> original) {
        if (!P11ConfigurationBoundary.installParkedGame(connection, protocol, listener)) {
            original.call(connection, protocol, listener);
        }
    }
}
