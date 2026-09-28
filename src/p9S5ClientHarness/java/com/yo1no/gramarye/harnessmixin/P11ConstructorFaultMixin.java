package com.yo1no.gramarye.harnessmixin;

import com.mojang.authlib.GameProfile;
import com.yo1no.gramarye.P11AssociationFaultProbe;
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

/** Registered only by the explicit engineering launch argument, absent from ordinary JARs. */
@Mixin(ServerPlayer.class)
abstract class P11ConstructorFaultMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/server/level/ServerLevel;Lcom/mojang/authlib/GameProfile;Lnet/minecraft/server/level/ClientInformation;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/server/level/ServerPlayer;advancements:Lnet/minecraft/server/PlayerAdvancements;",
                    opcode = 181, shift = At.Shift.AFTER), require = 1, expect = 1, allow = 1)
    private void p11$associationFault(MinecraftServer server, ServerLevel level, GameProfile profile,
            ClientInformation information, CallbackInfo callback) {
        P11AssociationFaultProbe.afterAssociation((ServerPlayer) (Object) this);
    }

    @Inject(method = "adjustSpawnLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$unknownCallbackFault(ServerLevel level, BlockPos proposed,
            CallbackInfoReturnable<BlockPos> callback) {
        P11AssociationFaultProbe.callbackEntry((ServerPlayer) (Object) this);
    }
}
