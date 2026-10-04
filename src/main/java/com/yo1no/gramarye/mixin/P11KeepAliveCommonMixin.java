package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11KeepAliveBoundary;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.profiling.ProfilerFiller;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves the native algorithm; only its state-field regions share the connection lock. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class P11KeepAliveCommonMixin implements P11KeepAliveBoundary.CommonAccess {
    @Shadow private long keepAliveTime;
    @Shadow private boolean keepAlivePending;
    @Shadow private long keepAliveChallenge;
    @Shadow private int latency;
    @Unique private long p11$keepAlivePhase;

    @Override
    public long p11$keepAlivePhase() { return p11$keepAlivePhase; }

    @Override
    public void p11$beginKeepAlivePhase(P11KeepAliveBoundary.ProtocolInstall install) {
        p11$keepAlivePhase = P11KeepAliveBoundary.installedPhase(install, (ServerCommonPacketListenerImpl) (Object) this);
    }

    @Override
    public boolean p11$ownsKeepAliveChallenge(long challenge) { return keepAlivePending && keepAliveChallenge == challenge; }

    @Override
    public void p11$copyKeepAlive(P11KeepAliveBoundary.Transfer transfer) {
        var self = (ServerCommonPacketListenerImpl) (Object) this;
        if (!P11KeepAliveBoundary.transferFrom(transfer, self)) {
            throw new IllegalStateException("P11_KEEPALIVE_COPY_WITHOUT_HANDOFF");
        }
        var target = P11KeepAliveBoundary.transferTarget(transfer, self.getConnection());
        ((P11KeepAliveBoundary.CommonAccess) target).p11$receiveKeepAlive(
                transfer, keepAliveTime, keepAlivePending, keepAliveChallenge, latency);
    }

    @Override
    public void p11$receiveKeepAlive(P11KeepAliveBoundary.Transfer transfer,
            long time, boolean pending, long challenge, int value) {
        if (!P11KeepAliveBoundary.transferTo(transfer, (ServerCommonPacketListenerImpl) (Object) this)) {
            throw new IllegalStateException("P11_KEEPALIVE_RECEIVE_WITHOUT_HANDOFF");
        }
        keepAliveTime = time;
        keepAlivePending = pending;
        keepAliveChallenge = challenge;
        latency = value;
        p11$keepAlivePhase = P11KeepAliveBoundary.transferPhase(transfer, (ServerCommonPacketListenerImpl) (Object) this);
    }

    @WrapMethod(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$ackRegion(ServerboundKeepAlivePacket packet, Operation<Void> original) {
        var region = P11KeepAliveBoundary.beginAck((ServerCommonPacketListenerImpl) (Object) this, packet);
        try { original.call(packet); }
        finally { P11KeepAliveBoundary.end(region); }
    }

    @WrapMethod(method = "keepConnectionAlive()V", require = 1, expect = 1, allow = 1)
    private void p11$tickRegion(Operation<Void> original) {
        var region = P11KeepAliveBoundary.beginTick((ServerCommonPacketListenerImpl) (Object) this);
        try { original.call(); }
        finally { P11KeepAliveBoundary.end(region); }
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;keepAlivePending:Z", opcode = Opcodes.GETFIELD),
            require = 1, expect = 1, allow = 1)
    private boolean p11$ackPending(ServerCommonPacketListenerImpl receiver, Operation<Boolean> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        if (region == null) { return original.call(receiver); }
        var current = P11KeepAliveBoundary.fields(region);
        return !P11KeepAliveBoundary.dropped(region) && original.call(current);
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;keepAliveChallenge:J", opcode = Opcodes.GETFIELD),
            require = 1, expect = 1, allow = 1)
    private long p11$ackChallenge(ServerCommonPacketListenerImpl receiver, Operation<Long> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        return original.call(region == null ? receiver : P11KeepAliveBoundary.fields(region));
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;keepAliveTime:J", opcode = Opcodes.GETFIELD),
            require = 1, expect = 1, allow = 1)
    private long p11$ackTime(ServerCommonPacketListenerImpl receiver, Operation<Long> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        return original.call(region == null ? receiver : P11KeepAliveBoundary.fields(region));
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;latency:I", opcode = Opcodes.GETFIELD),
            require = 1, expect = 1, allow = 1)
    private int p11$ackLatency(ServerCommonPacketListenerImpl receiver, Operation<Integer> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        return original.call(region == null ? receiver : P11KeepAliveBoundary.fields(region));
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;latency:I", opcode = Opcodes.PUTFIELD),
            require = 1, expect = 1, allow = 1)
    private void p11$ackLatencyWrite(ServerCommonPacketListenerImpl receiver, int value, Operation<Void> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        original.call(region == null ? receiver : P11KeepAliveBoundary.fields(region), value);
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;keepAlivePending:Z", opcode = Opcodes.PUTFIELD),
            require = 1, expect = 1, allow = 1)
    private void p11$ackConsumed(ServerCommonPacketListenerImpl receiver, boolean value, Operation<Void> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        try { original.call(region == null ? receiver : P11KeepAliveBoundary.fields(region), value); }
        finally { P11KeepAliveBoundary.release(region); }
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;getId()J"),
            require = 1, expect = 1, allow = 1)
    private long p11$ackId(ServerboundKeepAlivePacket packet, Operation<Long> original) {
        var region = P11KeepAliveBoundary.region((ServerCommonPacketListenerImpl) (Object) this);
        return region == null ? original.call(packet) : P11KeepAliveBoundary.packetId(region);
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 1, expect = 1, allow = 1)
    private long p11$ackClock(Operation<Long> original) {
        var region = P11KeepAliveBoundary.region((ServerCommonPacketListenerImpl) (Object) this);
        return region == null ? original.call() : P11KeepAliveBoundary.now(region);
    }

    @WrapOperation(method = "handleKeepAlive(Lnet/minecraft/network/protocol/common/ServerboundKeepAlivePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;isSingleplayerOwner()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$ackOwnerOutsideLock(ServerCommonPacketListenerImpl receiver, Operation<Boolean> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        P11KeepAliveBoundary.release(region);
        return P11KeepAliveBoundary.dropped(region) || original.call(receiver);
    }

    @WrapOperation(method = "keepConnectionAlive()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getMillis()J"), require = 1, expect = 1, allow = 1)
    private long p11$tickClock(Operation<Long> original) {
        long now = original.call();
        P11KeepAliveBoundary.observedTime(P11KeepAliveBoundary.region((ServerCommonPacketListenerImpl) (Object) this), now);
        return now;
    }

    @WrapOperation(method = "keepConnectionAlive()V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;keepAliveTime:J", opcode = Opcodes.GETFIELD),
            require = 1, expect = 1, allow = 1)
    private long p11$tickTime(ServerCommonPacketListenerImpl receiver, Operation<Long> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        if (region == null) { return original.call(receiver); }
        var current = P11KeepAliveBoundary.fields(region);
        return P11KeepAliveBoundary.dropped(region) ? P11KeepAliveBoundary.now(region) : original.call(current);
    }

    @WrapOperation(method = "keepConnectionAlive()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;checkIfClosed(J)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$closedOutsideLock(ServerCommonPacketListenerImpl receiver, long now, Operation<Boolean> original) {
        var region = P11KeepAliveBoundary.region(receiver);
        long previousTime = keepAliveTime;
        P11KeepAliveBoundary.release(region);
        boolean open = original.call(receiver, now);
        return open && P11KeepAliveBoundary.reacquireTick(region)
                && (region == null || (!keepAlivePending && keepAliveTime == previousTime));
    }

    @WrapOperation(method = "keepConnectionAlive()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$timeoutOutsideLock(ServerCommonPacketListenerImpl receiver, Component reason, Operation<Void> original) {
        P11KeepAliveBoundary.release(P11KeepAliveBoundary.region(receiver));
        original.call(receiver, reason);
    }

    @WrapOperation(method = "keepConnectionAlive()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$sendOutsideLock(ServerCommonPacketListenerImpl receiver, Packet<?> packet, Operation<Void> original) {
        P11KeepAliveBoundary.release(P11KeepAliveBoundary.region(receiver));
        original.call(receiver, packet);
    }

    @WrapOperation(method = "keepConnectionAlive()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getProfiler()Lnet/minecraft/util/profiling/ProfilerFiller;", ordinal = 1),
            require = 1, expect = 1, allow = 1)
    private ProfilerFiller p11$profilerOutsideLock(MinecraftServer server, Operation<ProfilerFiller> original) {
        P11KeepAliveBoundary.release(P11KeepAliveBoundary.region((ServerCommonPacketListenerImpl) (Object) this));
        return original.call(server);
    }
}
