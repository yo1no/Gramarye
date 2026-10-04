package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aRequiredProbe;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.payload.ModdedNetworkQueryComponent;
import net.neoforged.neoforge.network.payload.ModdedNetworkSetupFailedPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Network thread observation only. Original negotiation, payload, disconnect and exceptions are untouched. */
@Mixin(value = NetworkRegistry.class, remap = false)
abstract class P11C4aRequiredNegotiationMixin {
    @Inject(method = "initializeNeoForgeConnection(Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;Ljava/util/Map;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private static void required$query(ServerConfigurationPacketListener listener,
            Map<ConnectionProtocol, Set<ModdedNetworkQueryComponent>> channels, CallbackInfo callback) {
        P11C4aRequiredProbe.query(listener, channels);
    }
    @WrapOperation(method = "initializeNeoForgeConnection(Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;send(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"),
            require = 3, expect = 3, allow = 3)
    private static void required$sent(ServerConfigurationPacketListener listener, CustomPacketPayload payload, Operation<Void> original) {
        original.call(listener, payload);
        if (payload instanceof ModdedNetworkSetupFailedPayload failure) { P11C4aRequiredProbe.failedPayload(listener, failure.failureReasons().keySet()); }
    }
    @WrapOperation(method = "initializeNeoForgeConnection(Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/configuration/ServerConfigurationPacketListener;disconnect(Lnet/minecraft/network/chat/Component;)V"),
            require = 1, expect = 1, allow = 1)
    private static void required$closed(ServerConfigurationPacketListener listener, Component reason, Operation<Void> original) {
        original.call(listener, reason); P11C4aRequiredProbe.disconnected(listener, reason);
    }
}
