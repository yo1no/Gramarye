package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.P4E2QualificationFacade;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;

/** Closed nominal authority for one exact player-owned E2 immutable rebuild. */
public sealed abstract class PlayerSkillAttachmentReconciliationCapability<C, V>
        extends PlayerSkillAttachmentService.OpaqueReconciliationCapability<C, V>
        permits P4E2BoundPlayerSkillAttachmentReconciliationCapability {
    PlayerSkillAttachmentReconciliationCapability(
            PlayerSkillAttachmentService owner,
            PlayerSkillAttachmentService.OnlineReconciliationHandle handleIdentity,
            PlayerSkillAttachmentService.OnlineReconciliationHandle handleWitnessIdentity,
            SkillSubmissionRecoveryService.RecoveryContinuation continuationIdentity,
            SkillSubmissionRecoveryService.RecoveryContinuation continuationWitnessIdentity,
            C coordinatorIdentity,
            C coordinatorWitnessIdentity,
            V validationIdentity,
            V validationWitnessIdentity,
            int[] staleLatestOrdinals,
            int[] staleEquippedOrdinals) {
        super(
                owner,
                handleIdentity,
                handleWitnessIdentity,
                continuationIdentity,
                continuationWitnessIdentity,
                coordinatorIdentity,
                coordinatorWitnessIdentity,
                validationIdentity,
                validationWitnessIdentity,
                staleLatestOrdinals,
                staleEquippedOrdinals);
    }

    PlayerSkillAttachmentReconciliationCapability(
            PlayerSkillAttachmentService owner,
            PlayerSkillAttachmentService.OnlineReconciliationHandle handleIdentity,
            PlayerSkillAttachmentService.OnlineReconciliationHandle handleWitnessIdentity,
            SkillSubmissionRecoveryService.RecoveryContinuation continuationIdentity,
            SkillSubmissionRecoveryService.RecoveryContinuation continuationWitnessIdentity,
            C coordinatorIdentity,
            C coordinatorWitnessIdentity,
            V validationIdentity,
            V validationWitnessIdentity,
            int[] staleLatestOrdinals,
            int[] staleEquippedOrdinals,
            P4E2QualificationFacade.PlayerView qualificationPlayerView) {
        super(
                owner,
                handleIdentity,
                handleWitnessIdentity,
                continuationIdentity,
                continuationWitnessIdentity,
                coordinatorIdentity,
                coordinatorWitnessIdentity,
                validationIdentity,
                validationWitnessIdentity,
                staleLatestOrdinals,
                staleEquippedOrdinals,
                qualificationPlayerView);
    }
}
