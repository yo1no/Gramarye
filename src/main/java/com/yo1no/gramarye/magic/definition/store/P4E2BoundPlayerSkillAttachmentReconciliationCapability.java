package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;

/** Sole package-private exact binding for one E2 validation result and source witness. */
final class P4E2BoundPlayerSkillAttachmentReconciliationCapability
        extends PlayerSkillAttachmentReconciliationCapability<
                P4E2OnlineReconciliationCoordinator,
                P4E2GroupedStoreValidation.Validated> {
    P4E2BoundPlayerSkillAttachmentReconciliationCapability(
            PlayerSkillAttachmentService owner,
            PlayerSkillAttachmentService.OnlineReconciliationHandle handle,
            SkillSubmissionRecoveryService.RecoveryContinuation continuation,
            P4E2OnlineReconciliationCoordinator coordinator,
            P4E2GroupedStoreValidation.Validated validation,
            int[] staleLatestOrdinals,
            int[] staleEquippedOrdinals,
            P4E2QualificationFacade.PlayerView qualificationPlayerView) {
        super(
                owner,
                handle,
                handle,
                continuation,
                continuation,
                coordinator,
                coordinator,
                validation,
                validation,
                staleLatestOrdinals,
                staleEquippedOrdinals,
                qualificationPlayerView);
    }
}
