package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;

/**
 * Closed nominal capability for one already measured player-skill Attachment admission.
 *
 * <p>The generic values remain opaque at this public boundary. The only permitted production
 * binding is package-private and is consumed immediately by the issuing store adapter.</p>
 */
public sealed abstract class PlayerSkillAttachmentAdmissionSource<I, P>
        extends PlayerSkillAttachmentService.OpaqueAdmissionSource<I, P>
        permits P4E1BoundPlayerSkillAttachmentAdmissionSource {
    PlayerSkillAttachmentAdmissionSource(
            PlayerSkillAttachmentService owner,
            I inputIdentity,
            I measurementInputIdentity,
            long exactEncodedWidth,
            P providerIdentity,
            P providerWitnessIdentity) {
        super(
                owner,
                inputIdentity,
                measurementInputIdentity,
                exactEncodedWidth,
                providerIdentity,
                providerWitnessIdentity);
    }
}
