package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11NativeMetadataProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataContinuation;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService.MetadataInitialStage;
import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MetadataContinuation.class, remap = false)
abstract class P11MetadataStageFaultMixin {
    @Inject(method = "observeInitialSync(Lcom/yo1no/gramarye/P11NativeStorageBoundary$MetadataLease;JLcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService$MetadataInitialStage;)V",
            at = @At("HEAD"), require = 1, expect = 1, allow = 1)
    private void p11$beforeInitialSync(P11NativeStorageBoundary.MetadataLease lease, long epoch,
            MetadataInitialStage stage, CallbackInfo callback) {
        P11NativeMetadataProbe.initialSync((MetadataContinuation) (Object) this, lease, epoch, stage, false);
    }

    @Inject(method = "observeInitialSync(Lcom/yo1no/gramarye/P11NativeStorageBoundary$MetadataLease;JLcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService$MetadataInitialStage;)V",
            at = @At("RETURN"), require = 3, expect = 3, allow = 3)
    private void p11$afterInitialSync(P11NativeStorageBoundary.MetadataLease lease, long epoch,
            MetadataInitialStage stage, CallbackInfo callback) {
        P11NativeMetadataProbe.initialSync((MetadataContinuation) (Object) this, lease, epoch, stage, true);
    }

    @Inject(method = "reconciliationCompleted(Lcom/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency;ZLcom/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary$LoginReadyPort;)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$afterReconciliation(CallbackInfo callback) {
        P11NativeMetadataProbe.completed((MetadataContinuation) (Object) this, "reconciliation", 0);
    }

    @Inject(method = "sessionOpened(Lcom/yo1no/gramarye/magic/network/P7ServerAuthorizationBoundary$LoginReadyPort;J)V",
            at = @At("RETURN"), require = 1, expect = 1, allow = 1)
    private void p11$afterSession(P7ServerAuthorizationBoundary.LoginReadyPort port, long epoch,
            CallbackInfo callback) {
        P11NativeMetadataProbe.completed((MetadataContinuation) (Object) this, "session", epoch);
    }
}
