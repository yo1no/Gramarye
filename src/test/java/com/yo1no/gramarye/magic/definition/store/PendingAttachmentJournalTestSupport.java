package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;

final class PendingAttachmentJournalTestSupport {
    private PendingAttachmentJournalTestSupport() {
    }

    static SkillOwnerId owner(long value) {
        return new SkillOwnerId(new UUID(0, value));
    }

    static SkillId skill(long value) {
        return new SkillId(new UUID(0, value));
    }

    static SkillReference reference(SkillId skillId, int revision) {
        return new SkillReference(skillId, new SkillRevision(revision));
    }

    static PendingAttachmentJournalEntryPhysicalV0 physicalEntry(
            long owner,
            long skill,
            int expectedGeneration,
            int targetGeneration,
            Optional<SkillReference> expectedPointer,
            int targetRevision) {
        var skillId = skill(skill);
        return new PendingAttachmentJournalEntryPhysicalV0(
                owner(owner), skillId, expectedGeneration, targetGeneration,
                expectedPointer, reference(skillId, targetRevision));
    }

    static PendingAttachmentJournal journal(
            PendingAttachmentJournalEntryPhysicalV0... entries) {
        var admitted = (PendingAttachmentJournal.DomainAdmission.Admitted)
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, List.of(entries)));
        return admitted.journal();
    }

    static CompoundTag entryTag(PendingAttachmentJournalEntryPhysicalV0 entry) {
        var tag = new CompoundTag();
        tag.put(PendingAttachmentJournalSchema.OWNER, uuidTag(entry.owner().value()));
        tag.put(PendingAttachmentJournalSchema.SKILL_ID, uuidTag(entry.skillId().value()));
        tag.putInt(PendingAttachmentJournalSchema.EXPECTED_GENERATION,
                entry.expectedAttachmentGeneration());
        tag.putInt(PendingAttachmentJournalSchema.TARGET_GENERATION,
                entry.targetAttachmentGeneration());
        entry.expectedPointer().ifPresent(reference -> tag.put(
                PendingAttachmentJournalSchema.EXPECTED_POINTER,
                SkillReference.CODEC.encodeStart(NbtOps.INSTANCE, reference)
                        .result().orElseThrow()));
        tag.put(PendingAttachmentJournalSchema.TARGET_POINTER,
                SkillReference.CODEC.encodeStart(NbtOps.INSTANCE, entry.targetPointer())
                        .result().orElseThrow());
        return tag;
    }

    static byte[] rootBytes(List<PendingAttachmentJournalEntryPhysicalV0> entries)
            throws IOException {
        var root = new CompoundTag();
        root.putInt(PendingAttachmentJournalSchema.VERSION, 0);
        var list = new ListTag();
        entries.stream().map(PendingAttachmentJournalTestSupport::entryTag).forEach(list::add);
        root.put(PendingAttachmentJournalSchema.ENTRIES, list);
        return writeAny(root);
    }

    static byte[] writeAny(CompoundTag root) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            NbtIo.writeAnyTag(root, output);
        }
        return bytes.toByteArray();
    }

    static IntArrayTag uuidTag(UUID uuid) {
        return new IntArrayTag(new int[] {
                (int) (uuid.getMostSignificantBits() >>> 32),
                (int) uuid.getMostSignificantBits(),
                (int) (uuid.getLeastSignificantBits() >>> 32),
                (int) uuid.getLeastSignificantBits()
        });
    }
}
