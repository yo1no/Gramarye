package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11LivePlayAccess;
import com.yo1no.gramarye.P11LiveTransitionBoundary;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;

/** Incoming raw requests cannot enter the native mutation prefix without a root-owned ticket. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11LivePlayMixin implements P11LivePlayAccess {
    @Shadow private boolean waitingForSwitchToConfig;
    @Shadow public ServerPlayer player;
    // This native listener is never reused for the new Login. No actor/connection graph is added.
    @Unique private boolean p11$configurationTickRetired;

    @Override
    @Invoker("handleClientCommand")
    public abstract void p11$performRespawn(ServerboundClientCommandPacket packet);

    @Override
    @Invoker("switchToConfig")
    public abstract void p11$switchToConfig();

    @Override
    public boolean p11$configurationActorRetired() {
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        return listener.getMainThreadEventLoop().isSameThread() && p11$retiredConfigurationActor();
    }

    @WrapMethod(method = "handleClientCommand(Lnet/minecraft/network/protocol/game/ServerboundClientCommandPacket;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$clientCommand(ServerboundClientCommandPacket packet, Operation<Void> original) {
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        if (!listener.getMainThreadEventLoop().isSameThread()
                || packet.getAction() != ServerboundClientCommandPacket.Action.PERFORM_RESPAWN) {
            // Preserve original PacketUtils scheduling/error ownership and REQUEST_STATS.
            original.call(packet);
            return;
        }
        P11LiveTransitionBoundary.performRespawn(listener, packet, original);
    }

    @WrapMethod(method = "switchToConfig()V", require = 1, expect = 1, allow = 1)
    private void p11$enterConfiguration(Operation<Void> original) {
        var listener = (ServerGamePacketListenerImpl) (Object) this;
        P11LiveTransitionBoundary.switchToConfig(listener, args -> {
            // F0 never invokes this adapter; inactive ordinary compatibility cannot certify it.
            // Mark before native callbacks/ACK can reenter or retire the root's TO_CONFIG interval.
            if (P11LiveTransitionBoundary.configurationTickRetirementAuthorized(listener)) {
                p11$configurationTickRetired = true;
            }
            return original.call(args);
        });
    }

    @Unique
    private boolean p11$retiredConfigurationActor() {
        return p11$configurationTickRetired && waitingForSwitchToConfig
                && player.isRemoved() && player.hasDisconnected();
    }

    @ModifyExpressionValue(method = "tick()V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;ackBlockChangesUpTo:I",
            opcode = Opcodes.GETFIELD, ordinal = 0), require = 1, expect = 1, allow = 1)
    private int p11$retiredBlockAck(int original) {
        // Suppress only this old actor's ACK producer branch; do not consume/reset its field.
        return p11$retiredConfigurationActor() ? -1 : original;
    }

    @WrapOperation(method = "tick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$retiredPlayerTick(ServerPlayer actor, Operation<Void> original) {
        // Keep the native game-listener tick, flight checks, keepalive and idle policy intact.
        if (!p11$retiredConfigurationActor()) { original.call(actor); }
    }

    @WrapOperation(method = "handleConfigurationAcknowledged(Lnet/minecraft/network/protocol/game/ServerboundConfigurationAcknowledgedPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;setupInboundProtocol(Lnet/minecraft/network/ProtocolInfo;Lnet/minecraft/network/PacketListener;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$configurationTerminal(Connection connection, ProtocolInfo<?> protocol,
            PacketListener listener, Operation<Void> original) {
        var previous = (ServerGamePacketListenerImpl) (Object) this;
        boolean current = connection.getPacketListener() == previous;
        // The native field write alone is not terminal: preserve the subsequent pipeline work.
        original.call(connection, protocol, listener);
        if (current) {
            P11LiveTransitionBoundary.configurationAcknowledged(previous,
                    (ServerConfigurationPacketListenerImpl) listener);
        }
    }
}
