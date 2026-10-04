package com.yo1no.gramarye.harnessmixin;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11C4aConfigCatchProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Excluded exact original-constructor seams; no raw handler or callback replay. */
@Mixin(ServerPlayer.class)
abstract class P11C4aConstructorWithdrawalMixin {
    @Inject(method="<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at=@At(value="FIELD",target="Lnet/minecraft/server/level/ServerPlayer;advancements:Lnet/minecraft/server/PlayerAdvancements;",
                    opcode=181,shift=At.Shift.AFTER),require=1,expect=1,allow=1)
    private void withdrawal$association(MinecraftServer server,ServerLevel level,GameProfile profile,
            ClientInformation information,CallbackInfo ci){
        P11C4aConfigCatchProbe.constructorAssociation((ServerPlayer)(Object)this,false);
    }

    // Production's INVOKE-before adjustSpawnLocation seam has already marked unknown callback escape.
    @Inject(method="adjustSpawnLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            at=@At("HEAD"),require=1,expect=1,allow=1)
    private void withdrawal$callback(ServerLevel level,BlockPos proposed,CallbackInfoReturnable<BlockPos> cir){
        P11C4aConfigCatchProbe.constructorAssociation((ServerPlayer)(Object)this,true);
    }
}
