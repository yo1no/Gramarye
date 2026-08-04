package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.MinecraftServer;

/**
 * Research-only owner-distributed qualification fixture derived from the sanctioned P4-D3
 * documents and encoded solely by production Store and journal framing.
 *
 * <p>The prospective Store below is an audit oracle for fixture construction. It must not be
 * reported as an actual D2 submission; the dedicated R2Q lifecycle smoke owns that proof.</p>
 */
public final class P4E0R2QStoreJournalFixtures {
    public static final int OWNER_COUNT = 1_024;
    public static final int HISTORIES_PER_OWNER = 2;
    public static final int CURRENT_HISTORIES = 2_048;
    public static final int CURRENT_REVISIONS = 4_095;
    public static final int CURRENT_STORE_BYTES = 66_060_348;
    public static final String CURRENT_STORE_CHECKSUM =
            "5ab54eaa05281ec0f055cc235dec6acbf5406d568497ded7dcf0538932986bd9";
    public static final int CURRENT_JOURNAL_ENTRIES = 4_095;
    public static final int CURRENT_JOURNAL_BYTES = 1_048_324;
    public static final int PROSPECTIVE_HISTORIES = 2_049;
    public static final int PROSPECTIVE_REVISIONS = 4_096;
    public static final int PROSPECTIVE_JOURNAL_ENTRIES = 4_096;
    public static final int PROSPECTIVE_JOURNAL_BYTES = 1_048_538;
    public static final int LATEST_ROOTS = 2_049;
    public static final int EQUIPPED_ROOTS = 59_391;
    public static final int PLAYER_ROOTS = 61_440;
    public static final int JOURNAL_ROOTS = 4_096;
    public static final int RAW_ROOTS = 65_536;
    public static final int RAW_ROOTS_OVER = 65_537;

    private static final long OWNER_MSB = 0x5034_4433_0000_0001L;

    private P4E0R2QStoreJournalFixtures() {
    }

    /** Builds the exact full Store once and derives both journals through production code. */
    public static Fixture buildExact() {
        var d3 = P4D3StoreJournalFixture.build();
        var source = d3.store().snapshot().histories();
        if (source.size() != CURRENT_HISTORIES) {
            throw new AssertionError("R2Q source history count changed");
        }

        var redistributed = new ArrayList<SkillHistorySnapshot>(CURRENT_HISTORIES);
        for (var index = 0; index < source.size(); index++) {
            var history = source.get(index);
            redistributed.add(new SkillHistorySnapshot(
                    history.skillId(), owner(index / HISTORIES_PER_OWNER), history.revisions()));
        }
        var current = restore(redistributed);
        var carrier = rebuild(current);
        requireCurrentShape(current, carrier);

        var currentJournal = currentJournal(redistributed);
        var encodedCurrent = encode(currentJournal);
        if (encodedCurrent.entryCount() != CURRENT_JOURNAL_ENTRIES
                || encodedCurrent.byteCount() != CURRENT_JOURNAL_BYTES) {
            throw new AssertionError("R2Q current journal framing changed: "
                    + encodedCurrent.entryCount() + '/' + encodedCurrent.byteCount());
        }
        requireAudited(current, currentJournal, "current");

        var prospectiveMutation = currentJournal.append(new PendingAttachmentJournalEntry(
                submissionOwner(),
                P4D3StoreJournalFixture.submissionSkillId(),
                0,
                1,
                Optional.empty(),
                P4D3StoreJournalFixture.submissionTarget()));
        if (!(prospectiveMutation instanceof PendingAttachmentJournal.DomainMutation.Updated
                updated)) {
            throw new AssertionError("R2Q prospective journal append was rejected");
        }
        var prospectiveJournal = updated.journal();
        var encodedProspective = encode(prospectiveJournal);
        if (encodedProspective.entryCount() != PROSPECTIVE_JOURNAL_ENTRIES
                || encodedProspective.byteCount() != PROSPECTIVE_JOURNAL_BYTES) {
            throw new AssertionError("R2Q prospective journal framing changed: "
                    + encodedProspective.entryCount() + '/'
                    + encodedProspective.byteCount());
        }

        var prospectiveHistories = new ArrayList<>(redistributed);
        prospectiveHistories.add(submissionHistory(source.getFirst().revisions().getFirst()));
        var prospectiveAuditStore = restore(prospectiveHistories);
        if (prospectiveAuditStore.snapshot().histories().size() != PROSPECTIVE_HISTORIES
                || prospectiveAuditStore.snapshot().histories().stream()
                        .mapToInt(history -> history.revisions().size()).sum()
                        != PROSPECTIVE_REVISIONS) {
            throw new AssertionError("R2Q prospective audit Store shape changed");
        }
        requireAudited(prospectiveAuditStore, prospectiveJournal, "prospective");

        var roots = roots(prospectiveAuditStore, prospectiveJournal);
        var fixture = new Fixture(
                current,
                carrier,
                currentJournal,
                encodedCurrent,
                prospectiveAuditStore,
                prospectiveJournal,
                encodedProspective,
                roots);
        if (!fixture.facts().currentStoreChecksum().equals(CURRENT_STORE_CHECKSUM)) {
            throw new AssertionError("R2Q current Store checksum changed");
        }
        return fixture;
    }

    /** Strictly reads the exact primary and requires its platform DataVersion IntTag. */
    public static void requireStrictPrimaryDataVersion(Path worldRoot, int expected)
            throws IOException {
        var observed = P4E0ResearchGzipAdapter.read(
                P4D3StoreJournalFixture.primary(
                        Objects.requireNonNull(worldRoot, "worldRoot")),
                com.yo1no.gramarye.magic.limits.MagicSafetyCeilings
                        .MAX_SKILL_SAVED_DATA_FILE_BYTES,
                SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES,
                SkillSavedDataPersistenceSchema.FINITE_WHOLE_ROOT_NBT_QUOTA);
        var dataVersion = observed.decodedRoot().get(
                SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD);
        if (!(dataVersion instanceof IntTag version) || version.getAsInt() != expected) {
            throw new IOException("R2Q strict primary DataVersion changed");
        }
    }

    public static SkillOwnerId owner(int index) {
        if (index < 0 || index >= OWNER_COUNT) {
            throw new IllegalArgumentException("R2Q owner index is outside its fixture");
        }
        return new SkillOwnerId(new UUID(OWNER_MSB, index));
    }

    public static SkillOwnerId submissionOwner() {
        // Reuses the authenticated identity expected by the existing D3 actual-D2 driver.
        return owner(9);
    }

    /**
     * Installs a test-owned production service from the exact owner-distributed primary prepared
     * for the dedicated R2Q smoke. The normal composition-root instance may already be installed;
     * this is the same reviewed cache-replacement seam used by the D3 combined fixture.
     */
    public static ExactSubmissionContext installExactSubmission(MinecraftServer server) {
        SkillDefinitionStoreService.requireServerThread(server);
        var primary = SkillSavedDataPrimaryIngress.resolvePrimaryPath(server);
        if (!Files.isRegularFile(primary)) {
            throw new IllegalStateException(
                    "R2Q exact submission smoke requires its synthetic primary");
        }
        var service = new SkillDefinitionStoreService();
        service.install(server);
        var bootstrapped = service.submissionPort().bootstrapJournal(server);
        if (!(bootstrapped
                        instanceof SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready ready)
                || ready.entryCount() != CURRENT_JOURNAL_ENTRIES) {
            service.uninstall(server);
            throw new AssertionError("R2Q exact journal bootstrap changed");
        }
        var count = service.committedSkillCount(server, submissionOwner());
        if (!(count instanceof SkillSubsystemResult.Available<Integer> available)
                || available.value() != HISTORIES_PER_OWNER) {
            service.uninstall(server);
            throw new AssertionError("R2Q exact owner distribution changed");
        }
        return new ExactSubmissionContext(server, service);
    }

    private static SkillDefinitionStore restore(List<SkillHistorySnapshot> histories) {
        return switch (SkillDefinitionStore.restore(
                new SkillDefinitionStoreSnapshot(histories))) {
            case SkillDefinitionStoreRestoreResult.Restored restored -> restored.store();
            case SkillDefinitionStoreRestoreResult.Rejected ignored ->
                    throw new AssertionError("R2Q legal Store restore was rejected");
        };
    }

    private static EncodedSkillStoreCarrier rebuild(SkillDefinitionStore store) {
        return switch (SkillStoreCarrierBuilder.rebuild(store)) {
            case CarrierBuildResult.Success success -> success.carrier();
            case CarrierBuildResult.Failure ignored ->
                    throw new AssertionError("R2Q legal Store carrier rebuild failed");
        };
    }

    private static PendingAttachmentJournal currentJournal(
            List<SkillHistorySnapshot> histories) {
        var entries = new ArrayList<PendingAttachmentJournalEntryPhysicalV0>(
                CURRENT_JOURNAL_ENTRIES);
        for (var history : histories) {
            SkillReference previous = null;
            var generation = 0;
            for (var revision : history.revisions()) {
                var target = new SkillReference(history.skillId(), revision.revision());
                entries.add(new PendingAttachmentJournalEntryPhysicalV0(
                        history.owner(),
                        history.skillId(),
                        generation,
                        Math.addExact(generation, 1),
                        Optional.ofNullable(previous),
                        target));
                previous = target;
                generation++;
            }
        }
        if (entries.size() != CURRENT_JOURNAL_ENTRIES) {
            throw new AssertionError("R2Q current journal entry derivation changed");
        }
        var admitted = PendingAttachmentJournal.admitPhysical(
                new PendingAttachmentJournalPhysicalV0(
                        PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION, entries));
        if (!(admitted instanceof PendingAttachmentJournal.DomainAdmission.Admitted success)) {
            throw new AssertionError("R2Q current journal domain admission was rejected");
        }
        return success.journal();
    }

    private static EncodedPendingAttachmentJournal encode(PendingAttachmentJournal journal) {
        return switch (PendingAttachmentJournalFraming.encode(journal)) {
            case PendingAttachmentJournalFraming.JournalEncodingResult.Encoded encoded ->
                    encoded.journal();
            case PendingAttachmentJournalFraming.JournalEncodingResult.Rejected ignored ->
                    throw new AssertionError("R2Q legal journal framing was rejected");
        };
    }

    private static SkillHistorySnapshot submissionHistory(SkillRevisionSnapshot template) {
        var source = template.document();
        var revision = new SkillRevision(0);
        var document = new SkillDocument(
                source.schemaVersion(),
                P4D3StoreJournalFixture.submissionSkillId(),
                revision,
                source.nodes(),
                source.appearance());
        return new SkillHistorySnapshot(
                P4D3StoreJournalFixture.submissionSkillId(),
                submissionOwner(),
                List.of(new SkillRevisionSnapshot(revision, document)));
    }

    private static RootEnvelope roots(
            SkillDefinitionStore prospectiveStore,
            PendingAttachmentJournal prospectiveJournal) {
        var histories = prospectiveStore.snapshot().histories();
        var byOwner = new HashMap<SkillOwnerId, List<SkillReference>>();
        for (var history : histories) {
            byOwner.computeIfAbsent(history.owner(), ignored -> new ArrayList<>())
                    .add(new SkillReference(
                            history.skillId(), history.revisions().getLast().revision()));
        }
        var latestByOwner = new ArrayList<List<SkillReference>>(OWNER_COUNT);
        for (var ownerIndex = 0; ownerIndex < OWNER_COUNT; ownerIndex++) {
            var owner = owner(ownerIndex);
            var owned = byOwner.get(owner);
            if (owned == null || owned.size() < HISTORIES_PER_OWNER) {
                throw new AssertionError("R2Q owner root distribution is incomplete");
            }
            latestByOwner.add(List.copyOf(owned));
        }
        var attachments = P4E0ResearchAttachmentFixtures
                .admitR2QProfileReadyAttachments(latestByOwner);
        var latest = attachments.latestRoots();
        var equipped = attachments.equippedRoots();
        if (latest.size() != LATEST_ROOTS || equipped.size() != EQUIPPED_ROOTS) {
            throw new AssertionError("R2Q admitted player-root distribution changed");
        }

        var journal = List.copyOf(prospectiveJournal.targetReferences());
        if (journal.size() != JOURNAL_ROOTS) {
            throw new AssertionError("R2Q journal-root distribution changed");
        }
        requireOwnerAudit(prospectiveStore, attachments.ownerProjections());

        var exactInput = new ArrayList<SkillReference>(RAW_ROOTS);
        exactInput.addAll(latest);
        exactInput.addAll(equipped);
        exactInput.addAll(journal);
        var exact = SkillRetentionRootSnapshot.fromCompleteRoots(exactInput);
        if (!(exact instanceof SkillRetentionRootSnapshot.Complete complete)
                || complete.roots().size() != RAW_ROOTS) {
            throw new AssertionError("R2Q exact raw roots were not admitted");
        }

        // The +1 mutation is exactly the approved final-record equipped change 57 -> 58.
        var overInput = new ArrayList<SkillReference>(RAW_ROOTS_OVER);
        overInput.addAll(latest);
        overInput.addAll(equipped);
        overInput.add(equipped.getLast());
        overInput.addAll(journal);
        var over = SkillRetentionRootSnapshot.fromCompleteRoots(overInput);
        if (!(over instanceof SkillRetentionRootSnapshot.OverLimit exceeded)
                || exceeded.observedAtLeast() != RAW_ROOTS_OVER
                || exceeded.maximum() != RAW_ROOTS) {
            throw new AssertionError("R2Q raw-root cap+1 was not observed");
        }
        return new RootEnvelope(
                attachments, latest, equipped, journal, exactInput, exact, overInput, over);
    }

    private static void requireOwnerAudit(
            SkillDefinitionStore store,
            List<P4E0ResearchAttachmentFixtures.OwnerRootProjection> projections) {
        for (var projection : projections) {
            var expectedOwner = owner(projection.ownerIndex());
            for (var reference : concat(
                    projection.latestRoots(), projection.equippedRoots())) {
                if (store.find(reference).isEmpty()
                        || !store.ownerOf(reference.skillId())
                                .equals(Optional.of(expectedOwner))) {
                    throw new AssertionError(
                            "R2Q admitted player root failed exact reference/owner audit");
                }
            }
        }
    }

    private static List<SkillReference> concat(
            List<SkillReference> first, List<SkillReference> second) {
        var joined = new ArrayList<SkillReference>(first.size() + second.size());
        joined.addAll(first);
        joined.addAll(second);
        return joined;
    }

    private static void requireCurrentShape(
            SkillDefinitionStore store, EncodedSkillStoreCarrier carrier) {
        if (carrier.storeByteCount() != CURRENT_STORE_BYTES
                || carrier.historyCount() != CURRENT_HISTORIES
                || carrier.revisionCount() != CURRENT_REVISIONS) {
            throw new AssertionError("R2Q redistributed Store framing changed: "
                    + carrier.storeByteCount() + '/' + carrier.historyCount() + '/'
                    + carrier.revisionCount());
        }
        for (var index = 0; index < OWNER_COUNT; index++) {
            if (store.committedSkillCount(owner(index)) != HISTORIES_PER_OWNER) {
                throw new AssertionError("R2Q owner distribution changed at index " + index);
            }
        }
    }

    private static void requireAudited(
            SkillDefinitionStore store,
            PendingAttachmentJournal journal,
            String stage) {
        if (!(store.auditJournalTargets(journal) instanceof JournalTargetAuditResult.Audited)) {
            throw new AssertionError("R2Q " + stage + " journal target audit failed");
        }
    }

    /** Retained exact fixture. No method invokes Store reclaim. */
    public static final class Fixture {
        private final SkillDefinitionStore currentStore;
        private final EncodedSkillStoreCarrier currentCarrier;
        private final PendingAttachmentJournal currentJournal;
        private final EncodedPendingAttachmentJournal encodedCurrent;
        private final SkillDefinitionStore prospectiveAuditStore;
        private final PendingAttachmentJournal prospectiveJournal;
        private final EncodedPendingAttachmentJournal encodedProspective;
        private final RootEnvelope roots;

        private Fixture(
                SkillDefinitionStore currentStore,
                EncodedSkillStoreCarrier currentCarrier,
                PendingAttachmentJournal currentJournal,
                EncodedPendingAttachmentJournal encodedCurrent,
                SkillDefinitionStore prospectiveAuditStore,
                PendingAttachmentJournal prospectiveJournal,
                EncodedPendingAttachmentJournal encodedProspective,
                RootEnvelope roots) {
            this.currentStore = currentStore;
            this.currentCarrier = currentCarrier;
            this.currentJournal = currentJournal;
            this.encodedCurrent = encodedCurrent;
            this.prospectiveAuditStore = prospectiveAuditStore;
            this.prospectiveJournal = prospectiveJournal;
            this.encodedProspective = encodedProspective;
            this.roots = roots;
        }

        public Facts facts() {
            return new Facts(
                    currentCarrier.storeByteCount(),
                    currentCarrier.historyCount(),
                    currentCarrier.revisionCount(),
                    OWNER_COUNT,
                    encodedCurrent.entryCount(),
                    encodedCurrent.byteCount(),
                    prospectiveAuditStore.snapshot().histories().size(),
                    prospectiveAuditStore.snapshot().histories().stream()
                            .mapToInt(history -> history.revisions().size()).sum(),
                    encodedProspective.entryCount(),
                    encodedProspective.byteCount(),
                    roots.latest().size(),
                    roots.equipped().size(),
                    roots.journal().size(),
                    roots.exactInput().size(),
                    roots.overInput().size(),
                    roots.exactResult() instanceof SkillRetentionRootSnapshot.Complete,
                    roots.overResult() instanceof SkillRetentionRootSnapshot.OverLimit,
                    P4D3Hashing.sha256(currentCarrier));
        }

        /** Facts from the same 1,024 Ready states whose projections form this root envelope. */
        public P4E0ResearchAttachmentFixtures.AdmissionFacts admissionFacts() {
            return roots.attachments().admissionFacts();
        }

        /** Writes the exact current pair through the sanctioned D3 SavedData fixture writer. */
        public void writePrimary(Path worldRoot) throws IOException {
            writePrimary(worldRoot, false);
        }

        /** Writes the same pair with the reviewed D3 noncanonical Store-root field ordering. */
        public void writePrimary(Path worldRoot, boolean nonCanonicalStore) throws IOException {
            P4D3StoreJournalFixture.writePrimary(
                    Objects.requireNonNull(worldRoot, "worldRoot"),
                    currentCarrier,
                    encodedCurrent,
                    nonCanonicalStore);
        }

        /** Defensive immutable raw-root input for the formal Complete projection checkpoint. */
        public List<SkillReference> exactRawRootClaims() {
            return List.copyOf(roots.exactInput());
        }

        /** Defensive immutable raw-root input for the formal cap+1 projection checkpoint. */
        public List<SkillReference> overRawRootClaims() {
            return List.copyOf(roots.overInput());
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(currentStore);
            Reference.reachabilityFence(currentCarrier);
            Reference.reachabilityFence(currentJournal);
            Reference.reachabilityFence(encodedCurrent);
            Reference.reachabilityFence(prospectiveAuditStore);
            Reference.reachabilityFence(prospectiveJournal);
            Reference.reachabilityFence(encodedProspective);
            roots.retainAtPeak();
        }
    }

    /** Opaque lifecycle handle used by the exact actual-D2 dedicated smoke. */
    public static final class ExactSubmissionContext implements AutoCloseable {
        private final MinecraftServer server;
        private final SkillDefinitionStoreService service;
        private boolean closed;

        private ExactSubmissionContext(
                MinecraftServer server, SkillDefinitionStoreService service) {
            this.server = Objects.requireNonNull(server, "server");
            this.service = Objects.requireNonNull(service, "service");
        }

        public SkillDefinitionStoreSubmissionPort submissionPort() {
            if (closed) {
                throw new IllegalStateException("R2Q exact submission context is closed");
            }
            return service.submissionPort();
        }

        @Override
        public void close() {
            SkillDefinitionStoreService.requireServerThread(server);
            if (closed) {
                throw new IllegalStateException("R2Q exact submission context closed twice");
            }
            closed = true;
            service.uninstall(server);
        }
    }

    public record Facts(
            int currentStoreBytes,
            int currentHistories,
            int currentRevisions,
            int ownerCount,
            int currentJournalEntries,
            int currentJournalBytes,
            int prospectiveHistories,
            int prospectiveRevisions,
            int prospectiveJournalEntries,
            int prospectiveJournalBytes,
            int latestRoots,
            int equippedRoots,
            int journalRoots,
            int exactRawRoots,
            int overRawRoots,
            boolean exactRootsComplete,
            boolean overRootsRejected,
            String currentStoreChecksum) {
        public Facts {
            if (currentStoreBytes <= 0 || currentHistories <= 0 || currentRevisions <= 0
                    || ownerCount <= 0 || currentJournalEntries <= 0
                    || currentJournalBytes <= 0 || prospectiveHistories <= 0
                    || prospectiveRevisions <= 0 || prospectiveJournalEntries <= 0
                    || prospectiveJournalBytes <= 0 || latestRoots <= 0
                    || equippedRoots <= 0 || journalRoots <= 0 || exactRawRoots <= 0
                    || overRawRoots <= exactRawRoots
                    || !Objects.requireNonNull(currentStoreChecksum, "currentStoreChecksum")
                            .matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("R2Q Store/journal facts are invalid");
            }
        }
    }

    private static final class RootEnvelope {
        private final P4E0ResearchAttachmentFixtures.R2QReadyAttachments attachments;
        private final List<SkillReference> latest;
        private final List<SkillReference> equipped;
        private final List<SkillReference> journal;
        private final List<SkillReference> exactInput;
        private final SkillRetentionRootSnapshot exactResult;
        private final List<SkillReference> overInput;
        private final SkillRetentionRootSnapshot overResult;

        private RootEnvelope(
                P4E0ResearchAttachmentFixtures.R2QReadyAttachments attachments,
                List<SkillReference> latest,
                List<SkillReference> equipped,
                List<SkillReference> journal,
                List<SkillReference> exactInput,
                SkillRetentionRootSnapshot exactResult,
                List<SkillReference> overInput,
                SkillRetentionRootSnapshot overResult) {
            this.attachments = Objects.requireNonNull(attachments, "attachments");
            this.latest = List.copyOf(latest);
            this.equipped = List.copyOf(equipped);
            this.journal = List.copyOf(journal);
            this.exactInput = List.copyOf(exactInput);
            this.exactResult = Objects.requireNonNull(exactResult, "exactResult");
            this.overInput = List.copyOf(overInput);
            this.overResult = Objects.requireNonNull(overResult, "overResult");
        }

        private P4E0ResearchAttachmentFixtures.R2QReadyAttachments attachments() {
            return attachments;
        }

        private List<SkillReference> latest() {
            return latest;
        }

        private List<SkillReference> equipped() {
            return equipped;
        }

        private List<SkillReference> journal() {
            return journal;
        }

        private List<SkillReference> exactInput() {
            return exactInput;
        }

        private SkillRetentionRootSnapshot exactResult() {
            return exactResult;
        }

        private List<SkillReference> overInput() {
            return overInput;
        }

        private SkillRetentionRootSnapshot overResult() {
            return overResult;
        }

        private void retainAtPeak() {
            attachments.retainAtPeak();
            Reference.reachabilityFence(latest);
            Reference.reachabilityFence(equipped);
            Reference.reachabilityFence(journal);
            Reference.reachabilityFence(exactInput);
            Reference.reachabilityFence(exactResult);
            Reference.reachabilityFence(overInput);
            Reference.reachabilityFence(overResult);
        }
    }
}
