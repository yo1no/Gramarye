package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11C4aPreplayChatProbe;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.SignatureValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(ServerGamePacketListenerImpl.class)
abstract class P11C4aPreplayChatValidationMixin {
    @WrapOperation(method="handleChatSessionUpdate(Lnet/minecraft/network/protocol/game/ServerboundChatSessionUpdatePacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/network/chat/RemoteChatSession$Data;validate(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/util/SignatureValidator;)Lnet/minecraft/network/chat/RemoteChatSession;"),require=1,expect=1,allow=1)
    private RemoteChatSession chat$validated(RemoteChatSession.Data data,GameProfile profile,SignatureValidator validator,Operation<RemoteChatSession> original) {
        var result=original.call(data,profile,validator);
        P11C4aPreplayChatProbe.validated((ServerGamePacketListenerImpl)(Object)this); return result;
    }
    @WrapOperation(method="handleChatSessionUpdate(Lnet/minecraft/network/protocol/game/ServerboundChatSessionUpdatePacket;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/server/network/ServerGamePacketListenerImpl;resetPlayerChatState(Lnet/minecraft/network/chat/RemoteChatSession;)V"),require=1,expect=1,allow=1)
    private void chat$reset(ServerGamePacketListenerImpl listener,RemoteChatSession session,Operation<Void> original) {
        original.call(listener,session); P11C4aPreplayChatProbe.reset(listener);
    }
}
