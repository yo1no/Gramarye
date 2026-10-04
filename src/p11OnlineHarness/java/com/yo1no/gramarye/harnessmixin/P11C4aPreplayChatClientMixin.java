package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aPreplayChatClientProbe;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.network.chat.LocalChatSession;
import net.minecraft.network.chat.SignedMessageChain;
import net.minecraft.network.chat.LastSeenMessagesTracker;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
abstract class P11C4aPreplayChatClientMixin {
    @Shadow private LocalChatSession chatSession;
    @Shadow private SignedMessageChain.Encoder signedMessageEncoder;
    @Shadow private LastSeenMessagesTracker lastSeenMessages;
    @Shadow private MessageSignatureCache messageSignatureCache;
    @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ProfileKeyPairManager;prepareKeyPair()Ljava/util/concurrent/CompletableFuture;"),require=1,expect=1,allow=1)
    private CompletableFuture<?> chat$prepare(ProfileKeyPairManager manager,Operation<CompletableFuture<?>> original) {
        var result=original.call(manager);
        P11C4aPreplayChatClientProbe.prepared((ClientPacketListener)(Object)this,
                chatSession==null && lastSeenMessages!=null && messageSignatureCache!=null);
        return result;
    }
    @WrapOperation(method="handleLogin(Lnet/minecraft/network/protocol/game/ClientboundLoginPacket;)V",
            at=@At(value="INVOKE",target="Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),require=1,expect=1,allow=1)
    private CompletableFuture<?> chat$completion(CompletableFuture<?> future,Consumer<?> consumer,Executor executor,Operation<CompletableFuture<?>> original) {
        var listener=(ClientPacketListener)(Object)this;
        var result=original.call(future,consumer,P11C4aPreplayChatClientProbe.executor(listener,executor));
        P11C4aPreplayChatClientProbe.continuation(listener,result);
        return result;
    }
    @WrapMethod(method="setKeyPair(Lnet/minecraft/world/entity/player/ProfileKeyPair;)V",require=1,expect=1,allow=1)
    private void chat$key(ProfileKeyPair key,Operation<Void> original) {
        var listener=(ClientPacketListener)(Object)this;
        if (!P11C4aPreplayChatClientProbe.keyObserved(listener)) { original.call(key); return; }
        Object beforeSession=chatSession, beforeEncoder=signedMessageEncoder;
        boolean normal=false;
        try { original.call(key); normal=true; }
        finally { P11C4aPreplayChatClientProbe.keyReturned(listener,
                beforeSession==chatSession && beforeEncoder==signedMessageEncoder,chatSession!=null,normal); }
    }
    @WrapOperation(method="setKeyPair(Lnet/minecraft/world/entity/player/ProfileKeyPair;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"),require=1,expect=1,allow=1)
    private void chat$nativeChat(ClientPacketListener listener,Packet<?> packet,Operation<Void> original) {
        P11C4aPreplayChatClientProbe.send(listener,packet,original);
    }
    @WrapMethod(method="tick()V",require=1,expect=1,allow=1)
    private void chat$nativeTick(Operation<Void> original) {
        boolean selected=P11C4aPreplayChatClientProbe.beforeTick((ClientPacketListener)(Object)this);
        boolean normal=false;
        try { original.call(); normal=true; }
        finally { P11C4aPreplayChatClientProbe.afterTick(selected,normal); }
    }
}
