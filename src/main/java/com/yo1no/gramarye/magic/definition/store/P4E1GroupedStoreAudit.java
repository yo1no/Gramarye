package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Package-private, same-tick grouped Store audit for a single B1 capture. */
final class P4E1GroupedStoreAudit {
    private final MinecraftServer serverIdentity;
    private final Thread creationThreadIdentity;
    private final int creationTick;

    P4E1GroupedStoreAudit(MinecraftServer server) {
        this.serverIdentity = Objects.requireNonNull(server, "server");
        SkillDefinitionStoreService.requireServerThread(server);
        this.creationThreadIdentity = Thread.currentThread();
        this.creationTick = server.getTickCount();
    }

    Result audit(P4E1GlobalSourceCapture.Captured capture) {
        return audit(capture, Thread.currentThread().threadId());
    }

    Result audit(P4E1GlobalSourceCapture.Captured capture, long observedThreadId) {
        Objects.requireNonNull(capture, "capture");
        var decision = ProductThreadPrecondition.classify(
                serverIdentity.getRunningThread().threadId(), observedThreadId);
        var claimed = capture.claim(this, decision);
        return auditClaimed(claimed);
    }

    void requireCaptureBinding(MinecraftServer server, Thread thread, int tick) {
        requireCaptureServerAndThreadBinding(server, thread);
        requireCaptureTickBinding(server, tick);
    }

    void requireCaptureBinding(
            MinecraftServer server,
            Thread thread,
            int tick,
            ProductThreadPrecondition.Decision decision) {
        Objects.requireNonNull(decision, "decision");
        requireCaptureServerAndThreadBinding(server, thread);
        if (decision == ProductThreadPrecondition.Decision.WRONG_THREAD) {
            throw new BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH");
        }
        requireCaptureTickBinding(server, tick);
    }

    private void requireCaptureServerAndThreadBinding(MinecraftServer server, Thread thread) {
        if (serverIdentity != Objects.requireNonNull(server, "server")) {
            throw new BindingException("P4E1_GROUPED_AUDIT_SERVER_MISMATCH");
        }
        if (creationThreadIdentity != Objects.requireNonNull(thread, "thread")
                || Thread.currentThread() != creationThreadIdentity
                || !server.isSameThread()) {
            throw new BindingException("P4E1_GROUPED_AUDIT_THREAD_MISMATCH");
        }
    }

    private void requireCaptureTickBinding(MinecraftServer server, int tick) {
        if (creationTick != tick || server.getTickCount() != creationTick) {
            throw new BindingException("P4E1_GROUPED_AUDIT_TICK_MISMATCH");
        }
    }

    private Result auditClaimed(P4E1GlobalSourceCapture.Claimed claimed) {
        var transferred = false;
        P4E1AuditedCapture pendingAudited = null;
        try {
            var captureSummary = claimed.summary(this);
            try {
                var before = currentness(claimed);
                if (before.isPresent()) {
                    return before.orElseThrow();
                }
                var journalSourceFailure = validateJournalSourceProofs(claimed);
                if (journalSourceFailure.isPresent()) {
                    return journalSourceFailure.orElseThrow();
                }

                var outcome = auditRaw(
                        claimed.rawInput(this),
                        skillId -> claimed.observeExactHistory(this, skillId));
                if (outcome instanceof RawOutcome.Terminal terminal) {
                    return terminal.result();
                }

                var after = currentness(claimed);
                if (after.isPresent()) {
                    return after.orElseThrow();
                }
                var valid = (RawOutcome.Valid) outcome;
                pendingAudited = claimed.moveToAudited(this, valid.distinctSkillIdCount());
                var result = new Result.Audited(pendingAudited);
                transferred = true;
                pendingAudited = null;
                return result;
            } catch (BindingException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                return internalFailure(captureSummary, exception);
            }
        } finally {
            if (pendingAudited != null) {
                pendingAudited.discardAfterResultPublicationFailure();
            }
            if (!transferred) {
                claimed.discardIfActive(this);
            }
        }
    }

    private Optional<Result.Incomplete> validateJournalSourceProofs(
            P4E1GlobalSourceCapture.Claimed claimed) {
        for (var index = 0; index < claimed.sourceCount(this); index++) {
            var source = claimed.sourceAt(this, index);
            if (source.witness() instanceof P4E1GlobalSourceCapture.SourceWitness.Journal journal
                    && !claimed.journalProofMatches(this, journal.proof())) {
                return Optional.of(Result.Incomplete.simple(
                        IncompleteReason.JOURNAL_TARGET_INVALID,
                        P4E1AuditStage.JOURNAL_READINESS,
                        claimed.summary(this)));
            }
        }
        return Optional.empty();
    }

    private Optional<Result.Incomplete> currentness(
            P4E1GlobalSourceCapture.Claimed claimed) {
        requireCaptureBinding(serverIdentity, creationThreadIdentity, creationTick);
        if (!claimed.storeCurrent(this)) {
            return Optional.of(Result.Incomplete.simple(
                    IncompleteReason.STORE_UNAVAILABLE,
                    P4E1AuditStage.STORE_REFERENCE_OWNER_AUDIT,
                    claimed.summary(this)));
        }
        return switch (claimed.journalCurrentness(this)) {
            case CURRENT -> Optional.empty();
            case LIFECYCLE_UNAVAILABLE -> Optional.of(Result.Incomplete.simple(
                    IncompleteReason.JOURNAL_UNAVAILABLE,
                    P4E1AuditStage.JOURNAL_READINESS,
                    claimed.summary(this)));
            case TARGET_INVALID -> Optional.of(Result.Incomplete.simple(
                    IncompleteReason.JOURNAL_TARGET_INVALID,
                    P4E1AuditStage.JOURNAL_READINESS,
                    claimed.summary(this)));
        };
    }

    private static Result.Incomplete internalFailure(
            P4E1GlobalSourceCapture.Summary summary, RuntimeException exception) {
        return new Result.Incomplete(
                IncompleteReason.INTERNAL_RUNTIME_FAILURE,
                P4E1AuditStage.STORE_REFERENCE_OWNER_AUDIT,
                Optional.empty(),
                Optional.empty(),
                0,
                P4E1SourceFailure.boundedExceptionClassName(exception),
                summary,
                0);
    }

    /** Pure two-pass core; it cannot publish an AuditedCapture or retain the raw backing. */
    static RawOutcome auditRaw(
            RawInput input, SkillDefinitionStore.P4E1HistoryLookup historyLookup) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(historyLookup, "historyLookup");
        var distinct = new LinkedHashMap<SkillId, ObservationSlot>();
        ObservationSlot firstSlot = null;
        ObservationSlot lastSlot = null;
        try {
            input.validateSourceTable();
            for (var globalOrdinal = 0; globalOrdinal < input.claimCount(); globalOrdinal++) {
                input.validateClaim(globalOrdinal);
                var skillId = input.referenceAt(globalOrdinal).skillId();
                if (!distinct.containsKey(skillId)) {
                    var slot = new ObservationSlot(skillId);
                    distinct.put(skillId, slot);
                    if (firstSlot == null) {
                        firstSlot = slot;
                    } else {
                        lastSlot.next = slot;
                    }
                    lastSlot = slot;
                }
            }

            for (var slot = firstSlot; slot != null; slot = slot.next) {
                var observation = Objects.requireNonNull(
                        historyLookup.observe(slot.skillId), "historyObservation");
                var installed = false;
                try {
                    slot.install(observation);
                    installed = true;
                } finally {
                    if (!installed) {
                        discardObservation(observation);
                    }
                }
            }

            for (var globalOrdinal = 0; globalOrdinal < input.claimCount(); globalOrdinal++) {
                var reference = input.referenceAt(globalOrdinal);
                var source = input.sourceAtClaim(globalOrdinal);
                var observation = distinct.get(reference.skillId()).requireInstalled();
                if (input.kindAt(globalOrdinal) == P4E1RawClaimBuffer.ClaimKind.JOURNAL_TARGET) {
                    if (observation instanceof P4E1StoreHistoryObservation.Absent
                            || !((P4E1StoreHistoryObservation.Present) observation)
                                    .contains(reference)) {
                        return new RawOutcome.Terminal(Result.Incomplete.journalTarget(
                                input.summary(), distinct.size(), globalOrdinal, reference));
                    }
                    continue;
                }

                var expectedOwner = new SkillOwnerId(source.playerId().orElseThrow());
                if (observation instanceof P4E1StoreHistoryObservation.Absent) {
                    return new RawOutcome.Terminal(Result.ReconciliationRequired.create(
                            ReconciliationReason.STORE_REFERENCE_MISSING,
                            dispositionForSource(source.kind()),
                            source,
                            input.kindAt(globalOrdinal),
                            globalOrdinal,
                            input.localOrdinalAt(globalOrdinal),
                            input.equippedSlotAt(globalOrdinal),
                            reference,
                            input.summary(),
                            distinct.size()));
                }
                var present = (P4E1StoreHistoryObservation.Present) observation;
                if (!present.ownerMatches(expectedOwner)) {
                    return new RawOutcome.Terminal(Result.ReconciliationRequired.create(
                            ReconciliationReason.STORE_OWNER_MISMATCH,
                            dispositionForSource(source.kind()),
                            source,
                            input.kindAt(globalOrdinal),
                            globalOrdinal,
                            input.localOrdinalAt(globalOrdinal),
                            input.equippedSlotAt(globalOrdinal),
                            reference,
                            input.summary(),
                            distinct.size()));
                }
                if (!present.contains(reference)) {
                    return new RawOutcome.Terminal(Result.ReconciliationRequired.create(
                            ReconciliationReason.STORE_REFERENCE_MISSING,
                            dispositionForSource(source.kind()),
                            source,
                            input.kindAt(globalOrdinal),
                            globalOrdinal,
                            input.localOrdinalAt(globalOrdinal),
                            input.equippedSlotAt(globalOrdinal),
                            reference,
                            input.summary(),
                            distinct.size()));
                }
            }
            return new RawOutcome.Valid(distinct.size());
        } finally {
            var slot = firstSlot;
            while (slot != null) {
                var next = slot.next;
                slot.discard();
                slot = next;
            }
            distinct.clear();
        }
    }

    static Disposition dispositionForSource(P4E1GlobalSourceCapture.SourceKind kind) {
        return switch (kind) {
            case ONLINE -> Disposition.ONLINE;
            case INTEGRATED_RUNTIME_SNAPSHOT -> Disposition.DEFERRED_INTEGRATED;
            case DISK_PRIMARY, DISK_OLD -> Disposition.DEFERRED_OFFLINE;
            case PENDING_JOURNAL -> throw new IllegalStateException(
                    "journal source cannot produce reconciliation");
        };
    }

    sealed interface Result {
        record Audited(P4E1AuditedCapture capture) implements Result {
            public Audited {
                Objects.requireNonNull(capture, "capture");
            }
        }

        record Incomplete(
                IncompleteReason reason,
                P4E1AuditStage stage,
                Optional<SkillId> skillId,
                Optional<SkillReference> reference,
                int globalOrdinal,
                String exceptionClassName,
                P4E1GlobalSourceCapture.Summary summary,
                int distinctSkillIdCount) implements Result {
            public Incomplete {
                Objects.requireNonNull(reason, "reason");
                Objects.requireNonNull(stage, "stage");
                skillId = Objects.requireNonNull(skillId, "skillId");
                reference = Objects.requireNonNull(reference, "reference");
                Objects.requireNonNull(exceptionClassName, "exceptionClassName");
                Objects.requireNonNull(summary, "summary");
                if (globalOrdinal < 0 || distinctSkillIdCount < 0
                        || !exceptionClassName.equals(
                                P4E1SourceFailure.boundedExceptionClassName(
                                        exceptionClassName))) {
                    throw new IllegalArgumentException("invalid bounded incomplete metadata");
                }
            }

            private static Incomplete simple(
                    IncompleteReason reason,
                    P4E1AuditStage stage,
                    P4E1GlobalSourceCapture.Summary summary) {
                return new Incomplete(
                        reason, stage, Optional.empty(), Optional.empty(), 0, "", summary, 0);
            }

            private static Incomplete journalTarget(
                    P4E1GlobalSourceCapture.Summary summary,
                    int distinctSkillIdCount,
                    int globalOrdinal,
                    SkillReference reference) {
                return new Incomplete(
                        IncompleteReason.JOURNAL_TARGET_INVALID,
                        P4E1AuditStage.STORE_REFERENCE_OWNER_AUDIT,
                        Optional.of(reference.skillId()),
                        Optional.of(reference),
                        globalOrdinal,
                        "",
                        summary,
                        distinctSkillIdCount);
            }
        }

        record ReconciliationRequired(
                ReconciliationReason reason,
                Disposition disposition,
                P4E1GlobalSourceCapture.SourceKind sourceKind,
                UUID playerId,
                P4E1RawClaimBuffer.ClaimKind claimKind,
                int globalOrdinal,
                int sourceLocalOrdinal,
                int equippedSlot,
                SkillId skillId,
                SkillReference reference,
                int staleObservedAtLeast,
                P4E1GlobalSourceCapture.Summary summary,
                int distinctSkillIdCount) implements Result {
            public ReconciliationRequired {
                Objects.requireNonNull(reason, "reason");
                Objects.requireNonNull(disposition, "disposition");
                Objects.requireNonNull(sourceKind, "sourceKind");
                Objects.requireNonNull(playerId, "playerId");
                Objects.requireNonNull(claimKind, "claimKind");
                Objects.requireNonNull(skillId, "skillId");
                Objects.requireNonNull(reference, "reference");
                Objects.requireNonNull(summary, "summary");
                if (globalOrdinal < 0 || sourceLocalOrdinal < 0 || equippedSlot < -1
                        || staleObservedAtLeast != 1 || distinctSkillIdCount < 0) {
                    throw new IllegalArgumentException("invalid reconciliation metadata");
                }
            }

            private static ReconciliationRequired create(
                    ReconciliationReason reason,
                    Disposition disposition,
                    P4E1GlobalSourceCapture.SourceEntry source,
                    P4E1RawClaimBuffer.ClaimKind claimKind,
                    int globalOrdinal,
                    int sourceLocalOrdinal,
                    int equippedSlot,
                    SkillReference reference,
                    P4E1GlobalSourceCapture.Summary summary,
                    int distinctSkillIdCount) {
                return new ReconciliationRequired(
                        reason,
                        disposition,
                        source.kind(),
                        source.playerId().orElseThrow(),
                        claimKind,
                        globalOrdinal,
                        sourceLocalOrdinal,
                        equippedSlot,
                        reference.skillId(),
                        reference,
                        1,
                        summary,
                        distinctSkillIdCount);
            }
        }
    }

    enum IncompleteReason {
        STORE_UNAVAILABLE,
        JOURNAL_UNAVAILABLE,
        JOURNAL_TARGET_INVALID,
        INTERNAL_RUNTIME_FAILURE
    }

    enum ReconciliationReason {
        STORE_REFERENCE_MISSING,
        STORE_OWNER_MISMATCH
    }

    enum Disposition {
        ONLINE,
        DEFERRED_INTEGRATED,
        DEFERRED_OFFLINE
    }

    sealed interface RawOutcome {
        record Valid(int distinctSkillIdCount) implements RawOutcome {
            public Valid {
                if (distinctSkillIdCount < 0) {
                    throw new IllegalArgumentException("distinct count must be non-negative");
                }
            }
        }

        record Terminal(Result result) implements RawOutcome {
            public Terminal {
                Objects.requireNonNull(result, "result");
                if (result instanceof Result.Audited) {
                    throw new IllegalArgumentException("raw audit cannot publish an audit capture");
                }
            }
        }
    }

    static final class RawInput {
        private final P4E1RawClaimBuffer claims;
        private final ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources;
        private final P4E1GlobalSourceCapture.Summary summary;

        RawInput(
                P4E1GroupedStoreAudit owner,
                P4E1RawClaimBuffer claims,
                ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources,
                P4E1GlobalSourceCapture.Summary summary) {
            Objects.requireNonNull(owner, "owner");
            this.claims = Objects.requireNonNull(claims, "claims");
            this.sources = Objects.requireNonNull(sources, "sources");
            this.summary = Objects.requireNonNull(summary, "summary");
        }

        /** Pure, non-publishing invariant boundary used without a server-bound capture. */
        RawInput(
                P4E1RawClaimBuffer claims,
                ArrayList<P4E1GlobalSourceCapture.SourceEntry> sources,
                P4E1GlobalSourceCapture.Summary summary) {
            this.claims = Objects.requireNonNull(claims, "claims");
            this.sources = Objects.requireNonNull(sources, "sources");
            this.summary = Objects.requireNonNull(summary, "summary");
        }

        private int claimCount() {
            return claims.size();
        }

        private SkillReference referenceAt(int index) {
            return claims.referenceAt(index);
        }

        private P4E1RawClaimBuffer.ClaimKind kindAt(int index) {
            return claims.claimKindAt(index);
        }

        private int localOrdinalAt(int index) {
            return claims.sourceLocalOrdinalAt(index);
        }

        private int equippedSlotAt(int index) {
            return claims.equippedSlotAt(index);
        }

        private P4E1GlobalSourceCapture.SourceEntry sourceAtClaim(int index) {
            return sources.get(claims.sourceTableIndexAt(index));
        }

        private P4E1GlobalSourceCapture.Summary summary() {
            return summary;
        }

        private void validateSourceTable() {
            var expectedStart = 0;
            for (var source : sources) {
                if (source.claimStart() != expectedStart) {
                    throw new IllegalStateException("P4E1_SOURCE_RANGE_NOT_CONTIGUOUS");
                }
                expectedStart = Math.addExact(expectedStart, source.claimCount());
                var playerFamily = source.family()
                        == P4E1RootSourceFamily.PLAYER_SKILL_ATTACHMENT;
                if (playerFamily == (source.kind()
                        == P4E1GlobalSourceCapture.SourceKind.PENDING_JOURNAL)) {
                    throw new IllegalStateException("P4E1_SOURCE_KIND_FAMILY_MISMATCH");
                }
                var witnessMatches = switch (source.kind()) {
                    case ONLINE -> source.witness()
                            instanceof P4E1GlobalSourceCapture.SourceWitness.Online;
                    case INTEGRATED_RUNTIME_SNAPSHOT -> source.witness()
                            instanceof P4E1GlobalSourceCapture.SourceWitness.Integrated;
                    case DISK_PRIMARY, DISK_OLD -> source.witness()
                            instanceof P4E1GlobalSourceCapture.SourceWitness.Disk;
                    case PENDING_JOURNAL -> source.witness()
                            instanceof P4E1GlobalSourceCapture.SourceWitness.Journal;
                };
                if (!witnessMatches) {
                    throw new IllegalStateException("P4E1_SOURCE_WITNESS_MISMATCH");
                }
            }
            if (expectedStart != claims.size()) {
                throw new IllegalStateException("P4E1_SOURCE_RANGE_SIZE_MISMATCH");
            }
        }

        private void validateClaim(int globalOrdinal) {
            var sourceIndex = claims.sourceTableIndexAt(globalOrdinal);
            if (sourceIndex < 0 || sourceIndex >= sources.size()) {
                throw new IllegalStateException("P4E1_CLAIM_SOURCE_INDEX_INVALID");
            }
            var source = sources.get(sourceIndex);
            var localOrdinal = claims.sourceLocalOrdinalAt(globalOrdinal);
            if (localOrdinal < 0
                    || localOrdinal >= source.claimCount()
                    || source.claimStart() + localOrdinal != globalOrdinal) {
                throw new IllegalStateException("P4E1_CLAIM_LOCAL_ORDINAL_INVALID");
            }
            var kind = claims.claimKindAt(globalOrdinal);
            var journal = source.family() == P4E1RootSourceFamily.PENDING_ATTACHMENT_JOURNAL;
            if (journal != (kind == P4E1RawClaimBuffer.ClaimKind.JOURNAL_TARGET)) {
                throw new IllegalStateException("P4E1_CLAIM_KIND_FAMILY_MISMATCH");
            }
            var slot = claims.equippedSlotAt(globalOrdinal);
            if ((kind == P4E1RawClaimBuffer.ClaimKind.PLAYER_EQUIPPED) != (slot >= 0)
                    || (kind != P4E1RawClaimBuffer.ClaimKind.PLAYER_EQUIPPED && slot != -1)) {
                throw new IllegalStateException("P4E1_CLAIM_EQUIPPED_SLOT_INVALID");
            }
        }
    }

    private static final class ObservationSlot {
        private SkillId skillId;
        private P4E1StoreHistoryObservation observation;
        private ObservationSlot next;

        private ObservationSlot(SkillId skillId) {
            this.skillId = Objects.requireNonNull(skillId, "skillId");
        }

        private void install(P4E1StoreHistoryObservation candidate) {
            if (observation != null) {
                throw new IllegalStateException("P4E1_DISTINCT_OBSERVATION_ALREADY_INSTALLED");
            }
            observation = Objects.requireNonNull(candidate, "candidate");
        }

        private P4E1StoreHistoryObservation requireInstalled() {
            return Objects.requireNonNull(observation, "observation");
        }

        private void discard() {
            discardObservation(observation);
            skillId = null;
            observation = null;
            next = null;
        }
    }

    private static void discardObservation(P4E1StoreHistoryObservation observation) {
        if (observation instanceof P4E1StoreHistoryObservation.Present present) {
            present.discard();
        }
    }

    static final class BindingException extends IllegalStateException {
        private BindingException(String code) {
            super(code);
        }
    }
}
