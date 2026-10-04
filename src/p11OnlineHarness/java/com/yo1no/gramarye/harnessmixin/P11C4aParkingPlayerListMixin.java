package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aParkingProbe;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerList.class)
abstract class P11C4aParkingPlayerListMixin {
    @WrapMethod(method = "placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V", require = 1, expect = 1, allow = 1)
    private void c4a$actualPlace(Connection connection, ServerPlayer player, CommonListenerCookie cookie, Operation<Void> original) {
        P11C4aParkingProbe.place(connection, true, false);
        boolean normal = false;
        try { original.call(connection, player, cookie); normal = true; }
        finally { P11C4aParkingProbe.place(connection, false, normal); }
    }
}
