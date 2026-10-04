package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11C4aParkingTransferProbe;
import com.yo1no.gramarye.P11KeepAliveBoundary;
import com.yo1no.gramarye.P11ParkingPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = P11KeepAliveBoundary.class, remap = false)
abstract class P11C4aParkingTransferBoundaryMixin {
    @WrapMethod(method = "installGame(Lnet/minecraft/network/Connection;Lcom/yo1no/gramarye/P11ParkingPacketListener;Lnet/minecraft/server/network/ServerGamePacketListenerImpl;)V",
            require = 1, expect = 1, allow = 1)
    private static void c4a$actualSamePlaySwap(Connection connection, P11ParkingPacketListener previous,
            ServerGamePacketListenerImpl next, Operation<Void> original) {
        P11C4aParkingTransferProbe.beforeInstall(connection, previous, next);
        boolean normal = false;
        try { original.call(connection, previous, next); normal = true; }
        finally { P11C4aParkingTransferProbe.afterInstall(connection, previous, next, normal); }
    }
}
