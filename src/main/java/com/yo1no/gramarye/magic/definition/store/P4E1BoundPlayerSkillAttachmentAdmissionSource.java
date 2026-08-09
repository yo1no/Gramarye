package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/** The sole package-private Tag/provider binding for P4-E1 source-local admission. */
final class P4E1BoundPlayerSkillAttachmentAdmissionSource
        extends PlayerSkillAttachmentAdmissionSource<Tag, HolderLookup.Provider> {
    P4E1BoundPlayerSkillAttachmentAdmissionSource(
            PlayerSkillAttachmentService owner,
            Tag inputIdentity,
            Tag measurementInputIdentity,
            long exactEncodedWidth,
            HolderLookup.Provider providerIdentity,
            HolderLookup.Provider providerWitnessIdentity) {
        super(
                owner,
                inputIdentity,
                measurementInputIdentity,
                exactEncodedWidth,
                providerIdentity,
                providerWitnessIdentity);
    }

    static PlayerSkillAttachmentService.RootAuditAdmissionResult admitDiskObservation(
            PlayerSkillAttachmentService service,
            P4E1PlayerDataNbtScanner.AttachmentObservation.Present observation,
            HolderLookup.Provider provider) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(provider, "provider");
        var tag = observation.tag();
        return service.admitForRootAudit(
                new P4E1BoundPlayerSkillAttachmentAdmissionSource(
                        service,
                        tag,
                        tag,
                        observation.exactWriteAnyTagBytes(),
                        provider,
                        provider));
    }

    static PlayerSkillAttachmentService.RootAuditAdmissionResult admitIntegratedObservation(
            PlayerSkillAttachmentService service,
            P4E1IntegratedSnapshotTraversal.AttachmentObservation observation,
            HolderLookup.Provider provider) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(provider, "provider");
        var tag = observation.tagIdentity();
        return service.admitForRootAudit(
                new P4E1BoundPlayerSkillAttachmentAdmissionSource(
                        service,
                        tag,
                        tag,
                        observation.exactEncodedWidth(),
                        provider,
                        provider));
    }
}
