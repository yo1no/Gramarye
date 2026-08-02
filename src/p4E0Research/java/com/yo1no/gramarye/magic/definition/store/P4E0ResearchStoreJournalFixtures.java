package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;

/**
 * Research-only, non-authoritative retained Store/journal/carrier envelopes assembled solely
 * through the reviewed A3, B2, and D3 test seams and production framing.
 */
public final class P4E0ResearchStoreJournalFixtures {
    private P4E0ResearchStoreJournalFixtures() {
    }

    /** Small deterministic smoke envelope; it never invokes Store reclaim. */
    public static ReducedEnvelope reducedEnvelope() {
        var workload = P4A3ProbeWorkloads.smallDeterminismFixture();
        var carrier = requireCarrier(workload.store());
        var target = workload.expectedFirstLatest();
        var owner = workload.store().ownerOf(target.skillId()).orElseThrow();
        var mutation = PendingAttachmentJournal.empty().append(
                new PendingAttachmentJournalEntry(
                        owner,
                        target.skillId(),
                        0,
                        1,
                        Optional.empty(),
                        target));
        if (!(mutation instanceof PendingAttachmentJournal.DomainMutation.Updated updated)) {
            throw new AssertionError("research reduced journal append was rejected");
        }
        var journal = updated.journal();
        var encoded = requireEncoded(journal);
        if (!(workload.store().auditJournalTargets(journal)
                instanceof JournalTargetAuditResult.Audited)) {
            throw new AssertionError("research reduced journal target audit failed");
        }
        var inner = inner(carrier, encoded);
        return new ReducedEnvelope(workload, carrier, journal, encoded, inner);
    }

    /**
     * Lazily builds the exact D3 current pair. The prospective journal is deliberately exposed as
     * unmatched: its final target exists only after the actual D2 prospective Store transition.
     */
    public static FullCurrentEnvelope fullCurrentExact() {
        var fixture = P4D3StoreJournalFixture.build();
        if (!(fixture.store().auditJournalTargets(fixture.currentJournal())
                instanceof JournalTargetAuditResult.Audited)) {
            throw new AssertionError("research D3 current target audit failed");
        }
        if (fixture.store().auditJournalTargets(fixture.prospectiveJournal())
                instanceof JournalTargetAuditResult.Audited) {
            throw new AssertionError(
                    "research D3 prospective journal unexpectedly matched the current Store");
        }
        var inner = inner(fixture.carrier(), fixture.encodedCurrent());
        return new FullCurrentEnvelope(fixture, inner);
    }

    public static FullCurrentEnvelope fullCurrentEnvelope() {
        return fullCurrentExact();
    }

    /**
     * Constructs the exact public root-snapshot control plus one parameterized attempt without
     * calling reclaim. The R1 smoke requests 65,537; later research configurations may observe a
     * different raw-claim count without changing production constants.
     */
    public static RootBoundary rootBoundary(
            SkillReference repeatedReference, int attemptedClaims) {
        Objects.requireNonNull(repeatedReference, "repeatedReference");
        if (attemptedClaims < 0) {
            throw new IllegalArgumentException("research root claim count is negative");
        }
        var maximum = com.yo1no.gramarye.magic.limits.MagicSafetyCeilings
                .MAX_RETENTION_ROOTS_PER_RECLAIM;
        var exact = new ArrayList<SkillReference>(maximum);
        for (var index = 0; index < maximum; index++) {
            exact.add(repeatedReference);
        }
        var attempt = new ArrayList<SkillReference>(attemptedClaims);
        for (var index = 0; index < attemptedClaims; index++) {
            attempt.add(repeatedReference);
        }
        var accepted = SkillRetentionRootSnapshot.fromCompleteRoots(exact);
        var attempted = SkillRetentionRootSnapshot.fromCompleteRoots(attempt);
        if (!(accepted instanceof SkillRetentionRootSnapshot.Complete complete)
                || complete.roots().size() != maximum
                || attemptedClaims == maximum + 1
                        && (!(attempted instanceof SkillRetentionRootSnapshot.OverLimit exceeded)
                                || exceeded.observedAtLeast() != maximum + 1
                                || exceeded.maximum() != maximum)) {
            throw new AssertionError("research root boundary changed");
        }
        return new RootBoundary(exact, attempt, accepted, attempted);
    }

    private static EncodedSkillStoreCarrier requireCarrier(SkillDefinitionStore store) {
        return switch (SkillStoreCarrierBuilder.rebuild(store)) {
            case CarrierBuildResult.Success success -> success.carrier();
            case CarrierBuildResult.Failure ignored ->
                    throw new AssertionError("research legal Store carrier rebuild failed");
        };
    }

    private static EncodedPendingAttachmentJournal requireEncoded(
            PendingAttachmentJournal journal) {
        return switch (PendingAttachmentJournalFraming.encode(journal)) {
            case PendingAttachmentJournalFraming.JournalEncodingResult.Encoded encoded ->
                    encoded.journal();
            case PendingAttachmentJournalFraming.JournalEncodingResult.Rejected ignored ->
                    throw new AssertionError("research legal journal framing failed");
        };
    }

    private static SkillSavedDataInnerCarrier inner(
            EncodedSkillStoreCarrier carrier,
            EncodedPendingAttachmentJournal journal) {
        return SkillSavedDataInnerCarrier.fromPrevalidatedFraming(
                carrier,
                journal.pending(),
                Math.addExact(
                        SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                        Math.addExact(carrier.storeByteCount(), journal.byteCount())));
    }

    private static StoreJournalFacts facts(
            EncodedSkillStoreCarrier carrier,
            EncodedPendingAttachmentJournal journal,
            int roots,
            boolean targetsAuditedAgainstStore) {
        return new StoreJournalFacts(
                carrier.storeByteCount(),
                carrier.historyCount(),
                carrier.revisionCount(),
                P4D3Hashing.sha256(carrier),
                journal.byteCount(),
                journal.entryCount(),
                roots,
                targetsAuditedAgainstStore);
    }

    public record StoreJournalFacts(
            int storeBytes,
            int historyCount,
            int revisionCount,
            String storeChecksum,
            int journalBytes,
            int journalEntries,
            int journalRoots,
            boolean targetsAuditedAgainstStore) {
        public StoreJournalFacts {
            P4D3Hashing.requireSha256(storeChecksum);
            if (storeBytes <= 0 || historyCount < 0 || revisionCount < 0
                    || journalBytes < 0 || journalEntries < 0 || journalRoots < 0) {
                throw new IllegalArgumentException("research Store/journal facts are invalid");
            }
        }
    }

    public record CarrierFacts(
            int storeBytes,
            int historyCount,
            int revisionCount,
            String storeChecksum) {
        public CarrierFacts {
            P4D3Hashing.requireSha256(storeChecksum);
            if (storeBytes <= 0 || historyCount < 0 || revisionCount < 0) {
                throw new IllegalArgumentException("research carrier facts are invalid");
            }
        }
    }

    public static final class ReducedEnvelope {
        private final P4A3ProbeWorkload workload;
        private final EncodedSkillStoreCarrier carrier;
        private final PendingAttachmentJournal journal;
        private final EncodedPendingAttachmentJournal encoded;
        private final SkillSavedDataInnerCarrier inner;

        private ReducedEnvelope(
                P4A3ProbeWorkload workload,
                EncodedSkillStoreCarrier carrier,
                PendingAttachmentJournal journal,
                EncodedPendingAttachmentJournal encoded,
                SkillSavedDataInnerCarrier inner) {
            this.workload = Objects.requireNonNull(workload, "workload");
            this.carrier = Objects.requireNonNull(carrier, "carrier");
            this.journal = Objects.requireNonNull(journal, "journal");
            this.encoded = Objects.requireNonNull(encoded, "encoded");
            this.inner = Objects.requireNonNull(inner, "inner");
        }

        public StoreJournalFacts facts() {
            return P4E0ResearchStoreJournalFixtures.facts(
                    carrier, encoded, journal.targetReferences().size(), true);
        }

        public List<SkillReference> journalRoots() {
            return List.copyOf(journal.targetReferences());
        }

        public CompoundTag savedDataInnerTag() {
            return inner.createDataTag();
        }

        public SkillReference sampleReference() {
            return workload.expectedFirstLatest();
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(workload);
            Reference.reachabilityFence(carrier);
            Reference.reachabilityFence(journal);
            Reference.reachabilityFence(encoded);
            Reference.reachabilityFence(inner);
        }
    }

    public static final class FullCurrentEnvelope {
        private final P4D3StoreJournalFixture.Fixture fixture;
        private final SkillSavedDataInnerCarrier currentInner;

        private FullCurrentEnvelope(
                P4D3StoreJournalFixture.Fixture fixture,
                SkillSavedDataInnerCarrier currentInner) {
            this.fixture = Objects.requireNonNull(fixture, "fixture");
            this.currentInner = Objects.requireNonNull(currentInner, "currentInner");
        }

        public StoreJournalFacts currentFacts() {
            return facts(
                    fixture.carrier(),
                    fixture.encodedCurrent(),
                    fixture.currentJournal().targetReferences().size(),
                    true);
        }

        /** Facts only; this journal must not be described as audited against currentFacts(). */
        public StoreJournalFacts unmatchedProspectiveJournalFacts() {
            return facts(
                    fixture.carrier(),
                    fixture.encodedProspective(),
                    fixture.prospectiveJournal().targetReferences().size(),
                    false);
        }

        public List<SkillReference> currentJournalRoots() {
            return List.copyOf(fixture.currentJournal().targetReferences());
        }

        public List<SkillReference> unmatchedProspectiveJournalRoots() {
            return List.copyOf(fixture.prospectiveJournal().targetReferences());
        }

        public CompoundTag currentSavedDataInnerTag() {
            return currentInner.createDataTag();
        }

        /**
         * Builds and retains a carrier filtered to the latest revision of every D3 history. This
         * calls only A3 filterAfterReclaim with a synthetic snapshot, never Store reclaim.
         */
        public FilteredCarrier latestOnlyFilteredCarrier() {
            var histories = fixture.store().snapshot().histories().stream()
                    .map(history -> new SkillHistorySnapshot(
                            history.skillId(),
                            history.owner(),
                            List.of(history.revisions().getLast())))
                    .toList();
            var snapshot = new SkillDefinitionStoreSnapshot(histories);
            var filtered = SkillStoreCarrierBuilder.filterAfterReclaim(
                    fixture.carrier(), snapshot);
            return new FilteredCarrier(snapshot, filtered);
        }

        public SkillReference sampleReference() {
            return P4D3StoreJournalFixture.target(0, 0);
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(fixture);
            Reference.reachabilityFence(currentInner);
        }
    }

    public static final class FilteredCarrier {
        private final SkillDefinitionStoreSnapshot syntheticSnapshot;
        private final EncodedSkillStoreCarrier carrier;

        private FilteredCarrier(
                SkillDefinitionStoreSnapshot syntheticSnapshot,
                EncodedSkillStoreCarrier carrier) {
            this.syntheticSnapshot = Objects.requireNonNull(
                    syntheticSnapshot, "syntheticSnapshot");
            this.carrier = Objects.requireNonNull(carrier, "carrier");
        }

        public CarrierFacts facts() {
            return new CarrierFacts(
                    carrier.storeByteCount(),
                    carrier.historyCount(),
                    carrier.revisionCount(),
                    P4D3Hashing.sha256(carrier));
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(syntheticSnapshot);
            Reference.reachabilityFence(carrier);
        }
    }

    public static final class RootBoundary {
        private final List<SkillReference> exactInput;
        private final List<SkillReference> overInput;
        private final SkillRetentionRootSnapshot exactResult;
        private final SkillRetentionRootSnapshot overResult;

        private RootBoundary(
                List<SkillReference> exactInput,
                List<SkillReference> overInput,
                SkillRetentionRootSnapshot exactResult,
                SkillRetentionRootSnapshot overResult) {
            this.exactInput = List.copyOf(exactInput);
            this.overInput = List.copyOf(overInput);
            this.exactResult = exactResult;
            this.overResult = overResult;
        }

        public int exactInputCount() {
            return exactInput.size();
        }

        public int overInputCount() {
            return overInput.size();
        }

        public boolean exactAccepted() {
            return exactResult instanceof SkillRetentionRootSnapshot.Complete;
        }

        public boolean overRejected() {
            return overResult instanceof SkillRetentionRootSnapshot.OverLimit;
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(exactInput);
            Reference.reachabilityFence(overInput);
            Reference.reachabilityFence(exactResult);
            Reference.reachabilityFence(overResult);
        }
    }
}
