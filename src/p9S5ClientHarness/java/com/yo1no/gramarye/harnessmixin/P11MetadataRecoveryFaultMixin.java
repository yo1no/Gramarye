package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeMetadataProbe;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService$RecoveryContinuation", remap = false)
abstract class P11MetadataRecoveryFaultMixin {
    @Shadow @Final private MetadataContinuation metadata;

    @Inject(method = "recoveryCompleted(Lcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService$RecoveryOutcome;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$afterRecovery(CallbackInfo callback) {
        P11NativeMetadataProbe.completed(metadata, "recovery", 0);
    }

    @Inject(method = "consume(Lcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService;Lcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService$RecoveryOutcome;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$actualConsume(CallbackInfo callback) { P11NativeMetadataProbe.consumed(metadata); }
}
