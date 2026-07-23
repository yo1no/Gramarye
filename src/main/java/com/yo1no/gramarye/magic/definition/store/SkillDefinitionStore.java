package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Pure-Java truth for committed skill owners and immutable retained revisions.
 *
 * <p>This aggregate is intended for server logic-thread confinement. It performs no active
 * thread check and provides no arbitrary-thread linearizability guarantee.</p>
 */
public final class SkillDefinitionStore {
    private final Map<SkillId, StoredSkillHistory> histories;

    /** Creates an empty Store. */
    public SkillDefinitionStore() {
        histories = new HashMap<>();
    }

    private SkillDefinitionStore(Map<SkillId, StoredSkillHistory> histories) {
        this.histories = new HashMap<>(Objects.requireNonNull(histories, "histories"));
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
        if (historySnapshots.size() > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL) {
            return capacityRejected(
                    SkillStoreCapacityScope.GLOBAL_SKILL_HISTORIES,
                    historySnapshots.size(),
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL);
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
                    > MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL) {
                return capacityRejected(
                        SkillStoreCapacityScope.SKILL_RETAINED_REVISIONS,
                        revisionSnapshots.size(),
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL);
            }

            var owner = historySnapshot.owner();
            var ownerHistoryCount = ownerHistoryCounts.getOrDefault(owner, 0) + 1;
            if (ownerHistoryCount > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER) {
                return capacityRejected(
                        SkillStoreCapacityScope.OWNER_SKILL_HISTORIES,
                        ownerHistoryCount,
                        MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER);
            }

            var prospectiveGlobalRevisionCount = globalRevisionCount + revisionSnapshots.size();
            if (prospectiveGlobalRevisionCount
                    > MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL) {
                return capacityRejected(
                        SkillStoreCapacityScope.GLOBAL_RETAINED_REVISIONS,
                        prospectiveGlobalRevisionCount,
                        MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL);
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

    private static SkillDefinitionStoreRestoreResult capacityRejected(
            SkillStoreCapacityScope scope,
            int current,
            int maximum) {
        return rejected(new SkillDefinitionStoreRestoreFailure.CapacityExceeded(
                scope, current, maximum));
    }

    private static SkillDefinitionStoreRestoreResult rejected(
            SkillDefinitionStoreRestoreFailure failure) {
        return new SkillDefinitionStoreRestoreResult.Rejected(failure);
    }
}
