package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.DebugSampleSubscriber;
import net.minecraft.client.multiplayer.PingDebugMonitor;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
abstract class P11ClientPacketListenerMixin implements P11ClientTransitions.PlayAccess {
    @Unique private boolean p11$loginAllowed;

    @Override
    public boolean p11$actualLoginAllowed() { return p11$loginAllowed; }

    @WrapMethod(method = "lambda$handleGameEvent$7()V", require = 1, expect = 1, allow = 1)
    private void p11$endIntent(Operation<Void> original) {
        if (!P11ClientTransitions.replaceEndRespawn((ClientPacketListener) (Object) this)) {
            original.call();
        }
    }

    @ModifyExpressionValue(method = "handleGameEvent(Lnet/minecraft/network/protocol/game/ClientboundGameEventPacket;)V",
            at = @At(value = "NEW", target = "(ZLjava/lang/Runnable;)Lnet/minecraft/client/gui/screens/WinScreen;"),
            require = 1, expect = 1, allow = 1)
    private WinScreen p11$nativeEndScreen(WinScreen screen) {
        P11ClientTransitions.nativeSceneScreen((ClientPacketListener) (Object) this, screen);
        return screen;
    }

    @ModifyExpressionValue(method = "handlePlayerCombatKill(Lnet/minecraft/network/protocol/game/ClientboundPlayerCombatKillPacket;)V",
            at = @At(value = "NEW", target = "(Lnet/minecraft/network/chat/Component;Z)Lnet/minecraft/client/gui/screens/DeathScreen;"),
            require = 1, expect = 1, allow = 1)
    private DeathScreen p11$nativeDeathScreen(DeathScreen screen) {
        P11ClientTransitions.nativeSceneScreen((ClientPacketListener) (Object) this, screen);
        return screen;
    }

    @WrapMethod(method = "handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$bundle(ClientboundBundlePacket packet, Operation<Void> original) {
        var scope = P11ClientTransitions.beginBundle((ClientPacketListener) (Object) this);
        try {
            original.call(packet);
        } finally {
            P11ClientTransitions.endBundle(scope);
        }
    }

    @WrapOperation(method = "handleBundlePacket(Lnet/minecraft/network/protocol/game/ClientboundBundlePacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/Packet;handle(Lnet/minecraft/network/PacketListener;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$adjacentFrame(Packet<?> packet, PacketListener listener, Operation<Void> original) {
        var scope = P11ClientTransitions.beginMember((ClientPacketListener) (Object) this, packet);
        boolean normal = false;
        try {
            original.call(packet, listener);
            normal = true;
        } finally {
            P11ClientTransitions.endMember(scope, normal);
        }
    }

    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;isEncrypted()Z"),
            require = 1, expect = 1, allow = 1)
    private void p11$loginAfterNativeChatReset(ClientboundLoginPacket packet, CallbackInfo ci) {
        // The native three chat resets precede this seam; original prepareKeyPair follows.
        p11$loginAllowed = true;
        P11ClientTransitions.nativeFrameMaterial((ClientPacketListener) (Object) this, packet);
    }

    @WrapOperation(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;setLocalMode(Lnet/minecraft/world/level/GameType;Lnet/minecraft/world/level/GameType;)V"),
            require = 1, expect = 1, allow = 1)
    private void p11$respawnNativeTail(MultiPlayerGameMode mode, GameType current,
            GameType previous, Operation<Void> original, ClientboundRespawnPacket packet) {
        original.call(mode, current, previous);
        P11ClientTransitions.nativeFrameMaterial((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$loginApplied(ClientboundLoginPacket packet, CallbackInfo ci) {
        P11ClientTransitions.nativeFrameApplied((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleRespawn(Lnet/minecraft/network/protocol/game/ClientboundRespawnPacket;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$respawnApplied(ClientboundRespawnPacket packet, CallbackInfo ci) {
        P11ClientTransitions.nativeFrameApplied((ClientPacketListener) (Object) this, packet);
    }

    @Inject(method = "handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V", shift = At.Shift.AFTER),
            require = 1, expect = 1, allow = 1)
    private void p11$configurationStarted(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        p11$loginAllowed = false;
    }

    @WrapMethod(method = "handleConfigurationStart(Lnet/minecraft/network/protocol/game/ClientboundStartConfigurationPacket;)V",
            require = 1, expect = 1, allow = 1)
    private void p11$configurationApplied(ClientboundStartConfigurationPacket packet, Operation<Void> original) {
        var listener = (ClientPacketListener) (Object) this;
        boolean current = Minecraft.getInstance().isSameThread()
                && listener.getConnection().getPacketListener() == listener;
        original.call(packet);
        if (current) { P11ClientTransitions.configurationStarted(listener); }
    }

    @Inject(method = "setKeyPair(Lnet/minecraft/world/entity/player/ProfileKeyPair;)V",
            at = @At("HEAD"), cancellable = true, require = 1, expect = 1, allow = 1)
    private void p11$lateKeyCompletion(ProfileKeyPair keyPair, CallbackInfo ci) {
        if (!P11ClientTransitions.allowActorFunctions((ClientPacketListener) (Object) this)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ProfileKeyPairManager;shouldRefreshKeyPair()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$preplayKeys(ProfileKeyPairManager manager, Operation<Boolean> original) {
        return P11ClientTransitions.allowActorFunctions((ClientPacketListener) (Object) this)
                && original.call(manager);
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PingDebugMonitor;tick()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$preplayPing(PingDebugMonitor monitor, Operation<Void> original) {
        if (P11ClientTransitions.allowActorFunctions((ClientPacketListener) (Object) this)) {
            original.call(monitor);
        }
    }

    @WrapOperation(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/DebugSampleSubscriber;tick()V"),
            require = 1, expect = 1, allow = 1)
    private void p11$preplayDebug(DebugSampleSubscriber subscriber, Operation<Void> original) {
        if (P11ClientTransitions.allowActorFunctions((ClientPacketListener) (Object) this)) {
            original.call(subscriber);
        }
    }
}
