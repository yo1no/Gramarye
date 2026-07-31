package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.IOUtilities;

/** Exact deterministic Store and pending-journal fixtures for P4-D3-B. */
public final class P4D3StoreJournalFixture {
    public static final int STORE_BYTES = 66_060_348;
    public static final int HISTORY_COUNT = 2_048;
    public static final int REVISION_COUNT = 4_095;
    public static final int CURRENT_JOURNAL_ENTRIES = 4_095;
    public static final int CURRENT_JOURNAL_BYTES = 1_048_324;
    public static final int PROSPECTIVE_JOURNAL_ENTRIES = 4_096;
    public static final int PROSPECTIVE_JOURNAL_BYTES = 1_048_538;
    public static final int SHARED_PADDING_BYTES = 15_549;
    public static final int FINAL_EXTRA_PADDING_BYTES = 1_759;

    private static final long SKILL_MSB = 0x5034_4433_0000_0000L;
    private static final long OWNER_MSB = 0x5034_4433_0000_0001L;
    private static final int SUBMISSION_OWNER_INDEX = 9;
    private static final int SUBMISSION_SKILL_INDEX = HISTORY_COUNT;

    private P4D3StoreJournalFixture() {
    }

    /** Builds the exact full Store once and derives every journal through production framing. */
    static Fixture build() {
        var histories = new ArrayList<SkillHistorySnapshot>(HISTORY_COUNT);
        for (var historyIndex = 0; historyIndex < HISTORY_COUNT; historyIndex++) {
            var id = skillId(historyIndex);
            var revisionCount = historyIndex == HISTORY_COUNT - 1 ? 1 : 2;
            var revisions = new ArrayList<SkillRevisionSnapshot>(revisionCount);
            for (var revisionValue = 0; revisionValue < revisionCount; revisionValue++) {
                var revision = new SkillRevision(revisionValue);
                var padding = SHARED_PADDING_BYTES;
                if (historyIndex == HISTORY_COUNT - 1 && revisionValue == 0) {
                    padding = Math.addExact(padding, FINAL_EXTRA_PADDING_BYTES);
                }
                revisions.add(new SkillRevisionSnapshot(
                        revision, document(id, revision, padding)));
            }
            histories.add(new SkillHistorySnapshot(
                    id, ownerForHistory(historyIndex), revisions));
        }
        var restored = SkillDefinitionStore.restore(new SkillDefinitionStoreSnapshot(histories));
        if (!(restored instanceof SkillDefinitionStoreRestoreResult.Restored success)) {
            throw new AssertionError("P4-D3 exact Store restore was rejected");
        }
        var store = success.store();
        var carrier = requireCarrier(store);
        requireStoreShape(store, carrier);

        var current = maximumJournal(store);
        var encodedCurrent = requireEncoded(current);
        if (encodedCurrent.entryCount() != CURRENT_JOURNAL_ENTRIES
                || encodedCurrent.byteCount() != CURRENT_JOURNAL_BYTES) {
            throw new AssertionError("P4-D3 current journal framing size changed: "
                    + encodedCurrent.entryCount() + '/' + encodedCurrent.byteCount());
        }
        var appended = current.append(new PendingAttachmentJournalEntry(
                submissionOwner(), submissionSkillId(), 0, 1, Optional.empty(),
                submissionTarget()));
        if (!(appended instanceof PendingAttachmentJournal.DomainMutation.Updated update)) {
            throw new AssertionError("P4-D3 prospective journal append was rejected");
        }
        var prospective = update.journal();
        var encodedProspective = requireEncoded(prospective);
        if (encodedProspective.entryCount() != PROSPECTIVE_JOURNAL_ENTRIES
                || encodedProspective.byteCount() != PROSPECTIVE_JOURNAL_BYTES
                || encodedProspective.byteCount()
                        > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES) {
            throw new AssertionError("P4-D3 prospective journal framing size changed: "
                    + encodedProspective.entryCount() + '/'
                    + encodedProspective.byteCount());
        }
        requireAudit(store, current);
        return new Fixture(store, carrier, current, encodedCurrent,
                prospective, encodedProspective);
    }

    static PendingAttachmentJournal singleJournal() {
        return admit(List.of(entry(0, 0)));
    }

    static PendingAttachmentJournal invalidTargetJournal() {
        var missing = new SkillId(new UUID(SKILL_MSB, 0x7fff_ffff_ffff_ff00L));
        return admit(List.of(new PendingAttachmentJournalEntryPhysicalV0(
                selectedOwner(), missing, 0, 1, Optional.empty(),
                new SkillReference(missing, new SkillRevision(0)))));
    }

    static void writePrimary(
            Path worldRoot,
            EncodedSkillStoreCarrier carrier,
            EncodedPendingAttachmentJournal journal,
            boolean nonCanonicalStore) throws IOException {
        Files.createDirectories(worldRoot.resolve("data"));
        var inner = SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                journal.pending(),
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(carrier.storeByteCount(), journal.byteCount())));
        var data = inner.createDataTag();
        if (nonCanonicalStore) {
            var canonical = new byte[carrier.storeByteCount()];
            carrier.copyStoreBlobInto(canonical, 0);
            data.putByteArray(
                    SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                    P4B2FixtureBuilder.reorderStoreRootFields(canonical));
        }
        var root = new CompoundTag();
        root.put(SkillSavedDataPersistenceSchema.DATA_FIELD, data);
        NbtUtils.addCurrentDataVersion(root);
        IOUtilities.writeNbtCompressed(root, primary(worldRoot));
    }

    public static Path primary(Path worldRoot) {
        return worldRoot.resolve("data").resolve("gramarye_skill_definitions.dat");
    }

    public static SkillId skillId(int index) {
        if (index < 0 || index > SUBMISSION_SKILL_INDEX) {
            throw new IllegalArgumentException("P4-D3 SkillId index is outside its fixture");
        }
        return new SkillId(new UUID(SKILL_MSB, index));
    }

    public static SkillReference target(int skillIndex, int revision) {
        return new SkillReference(skillId(skillIndex), new SkillRevision(revision));
    }

    public static SkillOwnerId selectedOwner() {
        return owner(0);
    }

    public static SkillOwnerId submissionOwner() {
        return owner(SUBMISSION_OWNER_INDEX);
    }

    public static SkillId submissionSkillId() {
        return skillId(SUBMISSION_SKILL_INDEX);
    }

    public static SkillReference submissionTarget() {
        return target(SUBMISSION_SKILL_INDEX, 0);
    }

    private static SkillOwnerId ownerForHistory(int historyIndex) {
        if (historyIndex < 2) {
            return selectedOwner();
        }
        return owner(1 + (historyIndex - 2) / 256);
    }

    private static SkillOwnerId owner(int index) {
        return new SkillOwnerId(new UUID(OWNER_MSB, index));
    }

    private static PendingAttachmentJournal maximumJournal(SkillDefinitionStore store) {
        var physical = new ArrayList<PendingAttachmentJournalEntryPhysicalV0>(
                CURRENT_JOURNAL_ENTRIES);
        for (var historyIndex = 0; historyIndex < HISTORY_COUNT; historyIndex++) {
            physical.add(entry(historyIndex, 0));
            if (historyIndex != HISTORY_COUNT - 1) {
                physical.add(entry(historyIndex, 1));
            }
        }
        var result = admit(physical);
        if (result.entryCount() != CURRENT_JOURNAL_ENTRIES) {
            throw new AssertionError("P4-D3 maximum journal entry count changed");
        }
        requireAudit(store, result);
        return result;
    }

    private static PendingAttachmentJournalEntryPhysicalV0 entry(
            int historyIndex, int revision) {
        var route = skillId(historyIndex);
        var target = new SkillReference(route, new SkillRevision(revision));
        var expected = revision == 0
                ? Optional.<SkillReference>empty()
                : Optional.of(new SkillReference(route, new SkillRevision(revision - 1)));
        return new PendingAttachmentJournalEntryPhysicalV0(
                ownerForHistory(historyIndex), route, revision, revision + 1,
                expected, target);
    }

    private static PendingAttachmentJournal admit(
            List<PendingAttachmentJournalEntryPhysicalV0> physical) {
        var admitted = PendingAttachmentJournal.admitPhysical(
                new PendingAttachmentJournalPhysicalV0(
                        PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION, physical));
        if (!(admitted instanceof PendingAttachmentJournal.DomainAdmission.Admitted success)) {
            throw new AssertionError("P4-D3 journal admission was rejected");
        }
        return success.journal();
    }

    static EncodedPendingAttachmentJournal requireEncoded(PendingAttachmentJournal journal) {
        var encoded = PendingAttachmentJournalFraming.encode(journal);
        if (!(encoded instanceof PendingAttachmentJournalFraming.JournalEncodingResult.Encoded
                success)) {
            throw new AssertionError("P4-D3 journal encoding was rejected");
        }
        return success.journal();
    }

    private static void requireAudit(
            SkillDefinitionStore store, PendingAttachmentJournal journal) {
        if (!(store.auditJournalTargets(journal)
                instanceof JournalTargetAuditResult.Audited)) {
            throw new AssertionError("P4-D3 journal targets failed Store audit");
        }
    }

    private static EncodedSkillStoreCarrier requireCarrier(SkillDefinitionStore store) {
        return switch (SkillStoreCarrierBuilder.rebuild(store)) {
            case CarrierBuildResult.Success success -> success.carrier();
            case CarrierBuildResult.Failure ignored ->
                    throw new AssertionError("P4-D3 legal Store carrier rebuild failed");
        };
    }

    private static void requireStoreShape(
            SkillDefinitionStore store, EncodedSkillStoreCarrier carrier) {
        if (carrier.storeByteCount() != STORE_BYTES
                || carrier.historyCount() != HISTORY_COUNT
                || carrier.revisionCount() != REVISION_COUNT) {
            throw new AssertionError("P4-D3 exact Store shape changed: "
                    + carrier.storeByteCount() + '/' + carrier.historyCount() + '/'
                    + carrier.revisionCount());
        }
        if (store.committedSkillCount(selectedOwner()) != 2
                || store.committedSkillCount(submissionOwner()) != 0) {
            throw new AssertionError("P4-D3 selected/submission owner distribution changed");
        }
        for (var ownerIndex = 1; ownerIndex <= 8; ownerIndex++) {
            var count = store.committedSkillCount(owner(ownerIndex));
            if (count <= 0
                    || count > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER) {
                throw new AssertionError("P4-D3 owner quota distribution changed");
            }
        }
    }

    private static SkillDocument document(
            SkillId id, SkillRevision revision, int paddingBytes) {
        var trigger = new CompoundTag();
        trigger.putInt("marker", revision.value());
        trigger.putByteArray("padding", new byte[paddingBytes]);
        var action = new CompoundTag();
        action.putInt("value", 1);
        var node = new NodeDocument(
                new DefinitionEnvelope(type("p4_d3_trigger"), 0,
                        new Dynamic<>(NbtOps.INSTANCE, trigger)),
                new DefinitionEnvelope(type("p4_d3_action"), 0,
                        new Dynamic<>(NbtOps.INSTANCE, action)),
                AppearanceOverrideDocument.none());
        return new SkillDocument(
                SkillDocument.CURRENT_SCHEMA_VERSION,
                id,
                revision,
                List.of(node),
                AppearanceDocument.defaultAppearance());
    }

    private static ResourceLocation type(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    /** Full retained fixture objects used by exact-shape tests and combined preparation. */
    record Fixture(
            SkillDefinitionStore store,
            EncodedSkillStoreCarrier carrier,
            PendingAttachmentJournal currentJournal,
            EncodedPendingAttachmentJournal encodedCurrent,
            PendingAttachmentJournal prospectiveJournal,
            EncodedPendingAttachmentJournal encodedProspective) {
        public Fixture {
            java.util.Objects.requireNonNull(store, "store");
            java.util.Objects.requireNonNull(carrier, "carrier");
            java.util.Objects.requireNonNull(currentJournal, "currentJournal");
            java.util.Objects.requireNonNull(encodedCurrent, "encodedCurrent");
            java.util.Objects.requireNonNull(prospectiveJournal, "prospectiveJournal");
            java.util.Objects.requireNonNull(encodedProspective, "encodedProspective");
        }

        public StoreJournalFacts facts() {
            return new StoreJournalFacts(
                    carrier.storeByteCount(), carrier.historyCount(), carrier.revisionCount(),
                    P4D3Hashing.sha256(carrier), encodedCurrent.byteCount(),
                    encodedCurrent.entryCount(), P4D3Hashing.sha256(
                            encodedCurrent.pending().copyBytes()),
                    currentJournal.targetReferences().size());
        }
    }

    public record StoreJournalFacts(
            int storeBytes,
            int histories,
            int revisions,
            String storeChecksum,
            int journalBytes,
            int journalEntries,
            String journalChecksum,
            int rootCount) {
        public StoreJournalFacts {
            P4D3Hashing.requireSha256(storeChecksum);
            P4D3Hashing.requireSha256(journalChecksum);
            if (storeBytes <= 0 || histories <= 0 || revisions <= 0
                    || journalBytes <= 0 || journalEntries <= 0 || rootCount <= 0) {
                throw new IllegalArgumentException("P4-D3 facts must be positive");
            }
        }
    }
}
