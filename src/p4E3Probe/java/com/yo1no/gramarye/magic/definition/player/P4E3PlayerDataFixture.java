package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;

/** Builds the actual P4-C Ready payloads used by the isolated P4-E3 disk fixture. */
public final class P4E3PlayerDataFixture {
    private P4E3PlayerDataFixture() {
    }

    /**
     * Rebuilds, serializes, and re-admits one owner projection through the production P4-C
     * persistence path. The returned tag is a defensive copy and is never retained by production.
     */
    public static ReadyPayload ready(
            List<SkillReference> expectedLatest, int equippedCount) {
        var latestReferences = List.copyOf(Objects.requireNonNull(
                expectedLatest, "expectedLatest"));
        if (latestReferences.size() < 2 || equippedCount < 0 || equippedCount > 64) {
            throw new IllegalArgumentException("P4-E3 owner projection is outside its fixture");
        }

        var latest = latestReferences.stream()
                .map(reference -> new PlayerLatestState(
                        reference.skillId(), Optional.of(reference), 0))
                .toList();
        var equipped = new ArrayList<EquippedSkillReference>(equippedCount);
        for (var slot = 0; slot < equippedCount; slot++) {
            equipped.add(new EquippedSkillReference(
                    slot, latestReferences.get(slot % latestReferences.size())));
        }

        var rebuilt = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                List.of(), latest, equipped, PlayerSkillEditorState.empty());
        if (!(rebuilt instanceof PlayerSkillAttachmentBuildResult.Built built)) {
            throw new AssertionError("P4-E3 legal Ready fixture was rejected during rebuild");
        }
        var serialized = PlayerSkillAttachmentSerializer.INSTANCE.write(
                built.ready(), RegistryAccess.EMPTY);
        if (!(serialized instanceof CompoundTag canonical)) {
            throw new AssertionError("P4-E3 Ready fixture did not serialize as Compound");
        }
        var admitted = PlayerSkillAttachmentSerializer.INSTANCE.read(
                null, canonical.copy(), RegistryAccess.EMPTY);
        if (!(admitted instanceof PlayerSkillAttachmentReady ready)
                || ready.drafts().size() != 0
                || ready.latestStates().size() != latestReferences.size()
                || ready.equipped().size() != equippedCount) {
            throw new AssertionError("P4-E3 Ready fixture changed during production admission");
        }
        var admittedLatest = ready.latestStates().stream()
                .flatMap(state -> state.pointer().stream())
                .toList();
        var admittedEquipped = ready.equipped().stream()
                .map(EquippedSkillReference::reference)
                .toList();
        if (!admittedLatest.equals(latestReferences)
                || !admittedEquipped.equals(equipped.stream()
                        .map(EquippedSkillReference::reference)
                        .toList())) {
            throw new AssertionError("P4-E3 Ready root projection changed");
        }
        return new ReadyPayload(
                canonical.copy(), List.copyOf(admittedLatest),
                List.copyOf(admittedEquipped));
    }

    /** Probe-only physical payload and its exact typed root projection. */
    public record ReadyPayload(
            CompoundTag attachment,
            List<SkillReference> latestRoots,
            List<SkillReference> equippedRoots) {
        public ReadyPayload {
            attachment = Objects.requireNonNull(attachment, "attachment").copy();
            latestRoots = List.copyOf(Objects.requireNonNull(latestRoots, "latestRoots"));
            equippedRoots = List.copyOf(Objects.requireNonNull(
                    equippedRoots, "equippedRoots"));
        }

        @Override
        public CompoundTag attachment() {
            return attachment.copy();
        }
    }
}
