package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.submission.SkillCommitPrecondition;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure-Java truth for committed skill owners and immutable retained revisions.
 *
 * <p>This aggregate is intended for server logic-thread confinement. It performs no active
 * thread check and provides no arbitrary-thread linearizability guarantee.</p>
 */
public final class SkillDefinitionStore {
    private final Map<SkillId, StoredSkillHistory> histories;
    private final Map<SkillReference, Integer> activePinCounts;

    /** Creates an empty Store. */
    public SkillDefinitionStore() {
        histories = new HashMap<>();
        activePinCounts = new HashMap<>();
    }

    private SkillDefinitionStore(Map<SkillId, StoredSkillHistory> histories) {
        this.histories = new HashMap<>(Objects.requireNonNull(histories, "histories"));
        activePinCounts = new HashMap<>();
    }

    /** Returns the immutable document at the exact reference, if retained. */
    public Optional<SkillDocument> find(SkillReference reference) {
        Objects.requireNonNull(reference, "reference");
        var history = histories.get(reference.skillId());
        return history == null
                ? Optional.empty()
                : Optional.ofNullable(history.revisions().get(reference.revision()));
    }

    /** Returns the maximum retained revision for a skill identity, if present. */
    public Optional<SkillReference> latestReference(SkillId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        var history = histories.get(skillId);
        return history == null
                ? Optional.empty()
                : Optional.of(new SkillReference(skillId, history.revisions().lastKey()));
    }

    /** Returns the immutable committed owner binding for a skill identity, if present. */
    public Optional<SkillOwnerId> ownerOf(SkillId skillId) {
        Objects.requireNonNull(skillId, "skillId");
        var history = histories.get(skillId);
        return history == null ? Optional.empty() : Optional.of(history.owner());
    }

    /** Counts distinct active skill histories owned by {@code owner}. */
    public int committedSkillCount(SkillOwnerId owner) {
        Objects.requireNonNull(owner, "owner");
        var count = 0;
        for (var history : histories.values()) {
            if (history.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Observes one submission route with exactly one history lookup and without disclosing a
     * foreign owner's identity or latest revision.
     */
    StoreSubmissionAuthorityObservation observeSubmissionAuthority(
            SkillId skillId, SkillOwnerId requester) {
        Objects.requireNonNull(skillId, "skillId");
        Objects.requireNonNull(requester, "requester");
        var history = histories.get(skillId);
        if (history == null) {
            return new StoreSubmissionAuthorityObservation.Absent(skillId);
        }
        if (!history.owner().equals(requester)) {
            return new StoreSubmissionAuthorityObservation.ForeignOwned(skillId);
        }
        if (history.revisions().isEmpty()) {
            throw new IllegalStateException("stored history must retain a revision");
        }
        return new StoreSubmissionAuthorityObservation.Owned(
                new SkillReference(skillId, history.revisions().lastKey()));
    }

    /** Audits exact journal targets with at most one history lookup per distinct SkillId. */
    JournalTargetAuditResult auditJournalTargets(PendingAttachmentJournal journal) {
        Objects.requireNonNull(journal, "journal");
        var observed = new HashMap<SkillId, StoredSkillHistory>();
        var absent = new HashSet<SkillId>();
        for (var entryIndex = 0; entryIndex < journal.entries().size(); entryIndex++) {
            var entry = journal.entries().get(entryIndex);
            var skillId = entry.skillId();
            StoredSkillHistory history;
            if (absent.contains(skillId)) {
                return new JournalTargetAuditResult.Rejected(
                        targetAuditFailure(
                                PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                                entryIndex,
                                entry));
            }
            if (observed.containsKey(skillId)) {
                history = observed.get(skillId);
            } else {
                history = histories.get(skillId);
                if (history == null) {
                    absent.add(skillId);
                    return new JournalTargetAuditResult.Rejected(
                            targetAuditFailure(
                                    PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                                    entryIndex,
                                    entry));
                }
                observed.put(skillId, history);
            }
            if (!history.owner().equals(entry.owner())) {
                return new JournalTargetAuditResult.Rejected(
                        targetAuditFailure(
                                PendingAttachmentJournalFailure.Code.TARGET_OWNER_MISMATCH,
                                entryIndex,
                                entry));
            }
            if (!history.revisions().containsKey(entry.targetPointer().revision())) {
                return new JournalTargetAuditResult.Rejected(
                        targetAuditFailure(
                                PendingAttachmentJournalFailure.Code.TARGET_MISSING,
                                entryIndex,
                                entry));
            }
        }
        return new JournalTargetAuditResult.Audited(
                new JournalTargetAuditProof.AuditedExisting(journal));
    }

    private static PendingAttachmentJournalFailure targetAuditFailure(
            PendingAttachmentJournalFailure.Code code,
            int entryIndex,
            PendingAttachmentJournalEntry entry) {
        return new PendingAttachmentJournalFailure(
                code,
                PendingAttachmentJournalFailure.Stage.TARGET_AUDIT,
                PendingAttachmentJournalFailure.Field.TARGET_POINTER,
                -1,
                -1,
                entryIndex,
                Optional.of(entry.skillId()),
                Optional.of(entry.targetPointer()),
                Optional.empty());
    }

    /**
     * Pins an exact retained revision for an active in-memory caller.
     *
     * <p>The returned handle belongs to this Store instance and follows the Store's server
     * logic-thread confinement contract. Pins are transient lifecycle state: they are not
     * persisted, do not affect quota or latest-revision truth, and are not restored from Store
     * snapshots.</p>
     */
    public Optional<SkillRevisionPin> pin(SkillReference reference) {
        Objects.requireNonNull(reference, "reference");
        var history = histories.get(reference.skillId());
        if (history == null || !history.revisions().containsKey(reference.revision())) {
            return Optional.empty();
        }

        var nextCount = checkedIncrementPinCount(activePinCounts.getOrDefault(reference, 0));
        var handle = new SkillRevisionPin(this, reference);
        var result = Optional.of(handle);

        activePinCounts.put(reference, nextCount);
        return result;
    }

    /**
     * Reclaims every retained revision that is neither a history's latest revision, an exact
     * external root, nor protected by an active pin.
     *
     * <p>{@code externalRoots} must be captured by authoritative server composition and used
     * immediately in the same Store, world, and logic-thread call chain. A complete snapshot must
     * include offline and unloaded persistent roots; incomplete, truncated, over-limit, or stale
     * capture must be rejected or withheld by the caller. This method never discovers runtime or
     * persistence roots itself.</p>
     *
     * <p>Every typed rejection occurs before either histories or pin counts are modified. On
     * success, all replacement histories and the count-only result are prepared before the first
     * history replacement is published. The operation does not remove owner bindings, histories,
     * latest revisions, or quota identities.</p>
     */
    public SkillReclaimResult reclaim(SkillRetentionRootSnapshot externalRoots) {
        Objects.requireNonNull(externalRoots, "externalRoots");
        if (externalRoots == SkillRetentionRootSnapshot.Incomplete.INSTANCE) {
            return reclaimRejected(SkillReclaimFailure.IncompleteRootSnapshot.INSTANCE);
        }
        if (externalRoots == SkillRetentionRootSnapshot.Truncated.INSTANCE) {
            return reclaimRejected(SkillReclaimFailure.TruncatedRootSnapshot.INSTANCE);
        }
        if (externalRoots instanceof SkillRetentionRootSnapshot.OverLimit overLimit) {
            return reclaimRejected(new SkillReclaimFailure.RootCapacityExceeded(
                    overLimit.observedAtLeast(), overLimit.maximum()));
        }

        var complete = (SkillRetentionRootSnapshot.Complete) externalRoots;
        for (var root : complete.roots()) {
            if (!containsReference(root)) {
                return reclaimRejected(new SkillReclaimFailure.MissingExternalRoot(root));
            }
        }

        var distinctExternalRoots = new HashSet<>(complete.roots());
        requireActivePinInvariants();

        var replacements = new HashMap<SkillId, StoredSkillHistory>();
        var historiesScanned = histories.size();
        var revisionsScanned = 0;
        var revisionsReclaimed = 0;
        for (var entry : histories.entrySet()) {
            var history = entry.getValue();
            revisionsScanned += history.revisions().size();
            var retainedRevisions = retainedRevisions(
                    entry.getKey(), history, distinctExternalRoots);
            var replacement = history.retainRevisions(retainedRevisions);
            if (replacement != history) {
                replacements.put(entry.getKey(), replacement);
                revisionsReclaimed += history.revisions().size()
                        - replacement.revisions().size();
            }
        }

        var report = new SkillReclaimReport(
                historiesScanned,
                revisionsScanned,
                replacements.size(),
                revisionsReclaimed);
        var completed = new SkillReclaimResult.Completed(report);

        for (var replacement : replacements.entrySet()) {
            histories.put(replacement.getKey(), replacement.getValue());
        }
        return completed;
    }

    void releasePin(SkillReference reference) {
        Objects.requireNonNull(reference, "reference");
        var current = activePinCounts.get(reference);
        if (current == null || current <= 0) {
            throw new IllegalStateException("active pin count is missing or invalid");
        }
        if (current == 1) {
            activePinCounts.remove(reference);
            return;
        }
        activePinCounts.put(reference, current - 1);
    }

    static int checkedIncrementPinCount(int current) {
        if (current < 0) {
            throw new IllegalArgumentException("active pin count must be non-negative");
        }
        if (current == Integer.MAX_VALUE) {
            throw new IllegalStateException("active pin count is exhausted");
        }
        return current + 1;
    }

    private boolean containsReference(SkillReference reference) {
        var history = histories.get(reference.skillId());
        return history != null && history.revisions().containsKey(reference.revision());
    }

    private void requireActivePinInvariants() {
        for (var entry : activePinCounts.entrySet()) {
            var reference = Objects.requireNonNull(entry.getKey(), "active pin reference");
            var count = Objects.requireNonNull(entry.getValue(), "active pin count");
            if (count <= 0) {
                throw new IllegalStateException("active pin count must be positive");
            }
            if (!containsReference(reference)) {
                throw new IllegalStateException("active pin must reference a retained revision");
            }
        }
    }

    private Set<SkillRevision> retainedRevisions(
            SkillId skillId,
            StoredSkillHistory history,
            Set<SkillReference> externalRoots) {
        var latest = history.revisions().lastKey();
        var retained = new HashSet<SkillRevision>();
        for (var revision : history.revisions().keySet()) {
            var reference = new SkillReference(skillId, revision);
            if (revision.equals(latest)
                    || externalRoots.contains(reference)
                    || activePinCounts.containsKey(reference)) {
                retained.add(revision);
            }
        }
        return retained;
    }

    private static SkillReclaimResult reclaimRejected(SkillReclaimFailure failure) {
        return new SkillReclaimResult.Rejected(failure);
    }

    /**
     * Atomically admits and commits the revision proposed by {@code plan} within this aggregate.
     *
     * <p>The caller must complete fresh authorization immediately before this call and supply an
     * immutable current-policy quota snapshot. Neither the plan nor the quota is an authorization
     * credential, and this Store does not validate caller identity. It guarantees only domain
     * owner, CAS, quota, and technical-capacity consistency. A
     * {@link SkillStoreCommitResult.Committed} result records only the in-memory aggregate
     * mutation; it does not mean that SavedData, Attachment, or reconciliation work has completed.
     * This method follows the Store's server logic-thread confinement contract.</p>
     */
    public SkillStoreCommitResult commit(SkillSubmissionPlan plan, SkillQuota quota) {
        requireCommitInvariants(plan, quota);

        var owner = plan.owner();
        var precondition = plan.precondition();
        var document = plan.proposedDocument();
        return switch (precondition) {
            case SkillCommitPrecondition.ExpectedAbsent expected ->
                    commitExpectedAbsent(expected, owner, document, quota);
            case SkillCommitPrecondition.ExpectedLatest expected ->
                    commitExpectedLatest(expected, owner, document);
        };
    }

    SkillDefinitionStoreSnapshot snapshot() {
        var orderedHistories = histories.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().value()))
                .map(entry -> snapshotHistory(entry.getKey(), entry.getValue()))
                .toList();
        return new SkillDefinitionStoreSnapshot(orderedHistories);
    }

    static SkillDefinitionStoreRestoreResult restore(SkillDefinitionStoreSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var historySnapshots = snapshot.histories();
        if (historySnapshots.size()
                > SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES.canonicalMaximum()) {
            return restoreCapacityRejected(
                    SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                    historySnapshots.size());
        }

        var seenSkillIds = new HashSet<SkillId>();
        var ownerHistoryCounts = new HashMap<SkillOwnerId, Integer>();
        var globalRevisionCount = 0;

        for (var historySnapshot : historySnapshots) {
            var skillId = historySnapshot.skillId();
            if (!seenSkillIds.add(skillId)) {
                return rejected(new SkillDefinitionStoreRestoreFailure.DuplicateSkillId(skillId));
            }

            var revisionSnapshots = historySnapshot.revisions();
            if (revisionSnapshots.isEmpty()) {
                return rejected(new SkillDefinitionStoreRestoreFailure.EmptyHistory(skillId));
            }
            if (revisionSnapshots.size()
                    > SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS.canonicalMaximum()) {
                return restoreCapacityRejected(
                        SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                        revisionSnapshots.size());
            }

            var owner = historySnapshot.owner();
            var ownerHistoryCount = ownerHistoryCounts.getOrDefault(owner, 0) + 1;
            if (ownerHistoryCount
                    > SkillStoreCapacityScope.OWNER_SKILL_HISTORIES.canonicalMaximum()) {
                return restoreCapacityRejected(
                        SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                        ownerHistoryCount);
            }

            var prospectiveGlobalRevisionCount = globalRevisionCount + revisionSnapshots.size();
            if (prospectiveGlobalRevisionCount
                    > SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS.canonicalMaximum()) {
                return restoreCapacityRejected(
                        SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                        prospectiveGlobalRevisionCount);
            }

            var seenRevisions = new HashSet<SkillRevision>();
            for (var revisionSnapshot : revisionSnapshots) {
                var revision = revisionSnapshot.revision();
                var routeReference = new SkillReference(skillId, revision);
                if (!seenRevisions.add(revision)) {
                    return rejected(
                            new SkillDefinitionStoreRestoreFailure.DuplicateRevision(
                                    routeReference));
                }

                var document = revisionSnapshot.document();
                if (!skillId.equals(document.skillId())) {
                    return rejected(
                            new SkillDefinitionStoreRestoreFailure.DocumentSkillIdMismatch(
                                    skillId,
                                    document.skillId(),
                                    revision));
                }

                var documentReference = new SkillReference(
                        document.skillId(), document.revision());
                if (!revision.equals(document.revision())) {
                    return rejected(
                            new SkillDefinitionStoreRestoreFailure.DocumentRevisionMismatch(
                                    routeReference,
                                    documentReference));
                }
                if (document.schemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION) {
                    return rejected(
                            new SkillDefinitionStoreRestoreFailure.UnsupportedDocumentSchema(
                                    routeReference,
                                    document.schemaVersion(),
                                    SkillDocument.CURRENT_SCHEMA_VERSION));
                }
                if (document.nodes().isEmpty()) {
                    return rejected(
                            new SkillDefinitionStoreRestoreFailure.EmptyDocumentNodes(
                                    routeReference));
                }

            }

            ownerHistoryCounts.put(owner, ownerHistoryCount);
            globalRevisionCount = prospectiveGlobalRevisionCount;
        }

        var restoredHistories = new HashMap<SkillId, StoredSkillHistory>();
        for (var historySnapshot : historySnapshots) {
            var restoredRevisions = new TreeMap<SkillRevision, SkillDocument>(
                    Comparator.comparingInt(SkillRevision::value));
            for (var revisionSnapshot : historySnapshot.revisions()) {
                restoredRevisions.put(revisionSnapshot.revision(), revisionSnapshot.document());
            }
            restoredHistories.put(
                    historySnapshot.skillId(),
                    new StoredSkillHistory(historySnapshot.owner(), restoredRevisions));
        }

        return new SkillDefinitionStoreRestoreResult.Restored(
                new SkillDefinitionStore(restoredHistories));
    }

    private static SkillHistorySnapshot snapshotHistory(
            SkillId skillId,
            StoredSkillHistory history) {
        var revisions = new ArrayList<SkillRevisionSnapshot>(history.revisions().size());
        history.revisions().forEach((revision, document) ->
                revisions.add(new SkillRevisionSnapshot(revision, document)));
        return new SkillHistorySnapshot(skillId, history.owner(), revisions);
    }

    private SkillStoreCommitResult commitExpectedAbsent(
            SkillCommitPrecondition.ExpectedAbsent precondition,
            SkillOwnerId owner,
            SkillDocument document,
            SkillQuota quota) {
        var skillId = precondition.skillId();
        if (histories.containsKey(skillId)) {
            return new SkillStoreCommitResult.Conflict(
                    new SkillStoreCommitConflict.ExpectedAbsentButPresent(skillId));
        }

        var ownerSkillCount = committedSkillCount(owner);
        if (ownerSkillCount
                >= SkillStoreCapacityScope.OWNER_SKILL_HISTORIES.canonicalMaximum()) {
            return commitCapacityRejected(
                    SkillStoreCapacityScope.OWNER_SKILL_HISTORIES, ownerSkillCount);
        }

        var globalSkillCount = histories.size();
        if (globalSkillCount
                >= SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES.canonicalMaximum()) {
            return commitCapacityRejected(
                    SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES, globalSkillCount);
        }

        if (quota instanceof SkillQuota.Limited limited
                && ownerSkillCount >= limited.maxCommittedSkills()) {
            return new SkillStoreCommitResult.QuotaRejected(
                    skillId, ownerSkillCount, limited.maxCommittedSkills());
        }

        var globalRevisionCount = globalRetainedRevisionCount();
        if (globalRevisionCount
                >= SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS.canonicalMaximum()) {
            return commitCapacityRejected(
                    SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                    globalRevisionCount);
        }

        var revisions = new TreeMap<SkillRevision, SkillDocument>(
                Comparator.comparingInt(SkillRevision::value));
        revisions.put(document.revision(), document);
        var replacement = new StoredSkillHistory(owner, revisions);
        var committedReference = new SkillReference(skillId, document.revision());
        var committed = new SkillStoreCommitResult.Committed(committedReference);

        histories.put(skillId, replacement);
        return committed;
    }

    private SkillStoreCommitResult commitExpectedLatest(
            SkillCommitPrecondition.ExpectedLatest precondition,
            SkillOwnerId owner,
            SkillDocument document) {
        var expected = precondition.latest();
        var skillId = expected.skillId();
        var history = histories.get(skillId);
        if (history == null) {
            return new SkillStoreCommitResult.Conflict(
                    new SkillStoreCommitConflict.ExpectedLatestButAbsent(expected));
        }
        if (!history.owner().equals(owner)) {
            return new SkillStoreCommitResult.OwnerRejected(skillId);
        }

        var observed = new SkillReference(skillId, history.revisions().lastKey());
        if (!observed.equals(expected)) {
            return new SkillStoreCommitResult.Conflict(
                    new SkillStoreCommitConflict.LatestMismatch(expected, observed));
        }

        var successor = observed.revision().successor().orElseThrow(() ->
                new IllegalStateException("matched stored latest must have a successor"));
        if (!successor.equals(document.revision())) {
            throw new IllegalStateException(
                    "matched stored latest successor must equal the proposed revision");
        }

        var retainedRevisionCount = history.revisions().size();
        if (retainedRevisionCount
                >= SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS.canonicalMaximum()) {
            return commitCapacityRejected(
                    SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                    retainedRevisionCount);
        }

        var globalRevisionCount = globalRetainedRevisionCount();
        if (globalRevisionCount
                >= SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS.canonicalMaximum()) {
            return commitCapacityRejected(
                    SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                    globalRevisionCount);
        }

        var replacement = history.append(document);
        var committedReference = new SkillReference(skillId, document.revision());
        var committed = new SkillStoreCommitResult.Committed(committedReference);

        histories.put(skillId, replacement);
        return committed;
    }

    private int globalRetainedRevisionCount() {
        var count = 0;
        for (var history : histories.values()) {
            count += history.revisions().size();
        }
        return count;
    }

    private static void requireCommitInvariants(
            SkillSubmissionPlan plan,
            SkillQuota quota) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(quota, "quota");
        Objects.requireNonNull(plan.owner(), "plan.owner");
        var precondition = Objects.requireNonNull(plan.precondition(), "plan.precondition");
        var document = Objects.requireNonNull(
                plan.proposedDocument(), "plan.proposedDocument");

        if (document.schemaVersion() != SkillDocument.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "proposed document must use the current skill schema");
        }
        if (document.nodes().isEmpty()) {
            throw new IllegalArgumentException("proposed document must contain a node");
        }
        if (!precondition.skillId().equals(document.skillId())) {
            throw new IllegalArgumentException(
                    "commit precondition and proposed document SkillId must match");
        }

        switch (precondition) {
            case SkillCommitPrecondition.ExpectedAbsent ignored -> {
                if (document.revision().value() != 0) {
                    throw new IllegalArgumentException(
                            "ExpectedAbsent requires proposed revision zero");
                }
            }
            case SkillCommitPrecondition.ExpectedLatest expected -> {
                var successor = expected.latest().revision().successor().orElseThrow(() ->
                        new IllegalArgumentException(
                                "ExpectedLatest must have revision space"));
                if (!successor.equals(document.revision())) {
                    throw new IllegalArgumentException(
                            "ExpectedLatest successor must equal the proposed revision");
                }
            }
        }
    }

    private static SkillStoreCommitResult commitCapacityRejected(
            SkillStoreCapacityScope scope,
            int current) {
        return new SkillStoreCommitResult.CapacityRejected(
                scope, current, scope.canonicalMaximum());
    }

    private static SkillDefinitionStoreRestoreResult restoreCapacityRejected(
            SkillStoreCapacityScope scope,
            int current) {
        return rejected(new SkillDefinitionStoreRestoreFailure.CapacityExceeded(
                scope, current, scope.canonicalMaximum()));
    }

    private static SkillDefinitionStoreRestoreResult rejected(
            SkillDefinitionStoreRestoreFailure failure) {
        return new SkillDefinitionStoreRestoreResult.Rejected(failure);
    }
}
