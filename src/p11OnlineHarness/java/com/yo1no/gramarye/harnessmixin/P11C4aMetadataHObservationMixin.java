package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aMetadataHProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataInitialStage;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** RETURN after the actual metadata observation, before its caller performs the original submit. */
@Mixin(value = P11NativeStorageBoundary.class, remap = false)
abstract class P11C4aMetadataHObservationMixin {
    @Inject(method = "metadataInitialSync(Lnet/minecraft/server/level/ServerPlayer;JJLcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService$MetadataInitialStage;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private static void c4a$metadataH(ServerPlayer actor, long epoch, long generation, MetadataInitialStage stage, CallbackInfo callback) {
        P11C4aMetadataHProbe.metadataReturned(actor, epoch, stage);
    }
}
