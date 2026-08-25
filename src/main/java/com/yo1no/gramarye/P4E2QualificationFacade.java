package com.yo1no.gramarye;

import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.IExtensionPoint;

/** One bounded, mod-container-owned observation cell for P4-E2 qualification. */
public final class P4E2QualificationFacade implements IExtensionPoint {
    public enum RecoveryVariant {
        NO_PENDING,
        CLEARED,
        REPLAYED,
        CLEARED_AND_REPLAYED,
        CONFLICT,
        TARGET_INVALID,
        UNAVAILABLE
    }

    public enum RecoveryDetail {
        NONE,
        THIRD_STATE,
        CLEAR_PREPARATION_REJECTED,
        CLEAR_COMMIT_REJECTED,
        REPLAY_PREPARATION_REJECTED,
        REPLAY_CURRENTNESS_CHANGED,
        REPLAY_PUBLICATION_REJECTED,
        REPLAY_UNEXPECTED_NO_OP,
        TARGET_MISSING,
        TARGET_OWNER_MISMATCH,
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE,
        ATTACHMENT_PRESERVED_RAW_QUARANTINE,
        ATTACHMENT_OVERSIZE_QUARANTINE,
        RUNTIME_EXCEPTION
    }

    public enum ReconciliationVariant {
        NO_CHANGES,
        RECOVERY_CHANGED,
        CHANGED,
        DEFERRED,
        FAILED,
        GENERATION_EXHAUSTED
    }

    public enum ReconciliationDetail {
        NONE,
        RECOVERY_OPERATIONAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        ATTACHMENT_PRESERVED_RAW,
        ATTACHMENT_OVERSIZE,
        RECOVERY_CONFLICT,
        RECOVERY_TARGET_INVALID,
        RECOVERY_RUNTIME_FAILURE,
        PLAYER_GENERATION_EXHAUSTED,
        ATTACHMENT_CAPACITY_REJECTED,
        ATTACHMENT_INVARIANT_REJECTED,
        FRESHNESS_LOST,
        INTERNAL_RUNTIME_FAILURE
    }

    public enum E3AuditVariant {
        COMPLETE,
        INCOMPLETE,
        OVER_LIMIT,
        RECONCILIATION_REQUIRED,
        GENERATION_EXHAUSTED
    }

    public enum E3SnapshotVariant {
        COMPLETE,
        INCOMPLETE,
        TRUNCATED,
        OVER_LIMIT
    }

    public enum E3ReclaimVariant {
        COMPLETED_ZERO,
        COMPLETED_POSITIVE,
        REJECTED,
        UNAVAILABLE
    }

    public enum E3IndexTerminal {
        COMPLETE_INDEX,
        INCOMPLETE
    }

    /** Closed nominal crossing into the submission package. */
    public static sealed abstract class SubmissionView permits SubmissionViewImpl {
        private final P4E2QualificationFacade owner;

        private SubmissionView(P4E2QualificationFacade owner) {
            this.owner = owner;
        }

        public final boolean enabledFor(
                MinecraftServer server, long playerMost, long playerLeast) {
            return owner.enabledFor(server, playerMost, playerLeast);
        }

        public final void recordRecovery(
                MinecraftServer server,
                long playerMost,
                long playerLeast,
                RecoveryVariant variant,
                RecoveryDetail detail,
                int entriesCleared,
                int stepsReplayed) {
            owner.recordRecovery(
                    server,
                    playerMost,
                    playerLeast,
                    variant,
                    detail,
                    entriesCleared,
                    stepsReplayed);
        }

        public final void completeAfterContinuation(
                MinecraftServer server, long playerMost, long playerLeast) {
            owner.completeAfterContinuation(server, playerMost, playerLeast);
        }
    }

    /** Closed nominal crossing into the Store/coordinator package. */
    public static sealed abstract class StoreView permits StoreViewImpl {
        private final P4E2QualificationFacade owner;
        private final E3StartupView e3StartupView;

        private StoreView(P4E2QualificationFacade owner) {
            this.owner = owner;
            e3StartupView = new E3StartupViewImpl(owner);
        }

        public final boolean enabledFor(
                MinecraftServer server, long playerMost, long playerLeast) {
            return owner.enabledFor(server, playerMost, playerLeast);
        }

        public final void recordContinuation(
                MinecraftServer server, long playerMost, long playerLeast) {
            owner.recordContinuation(server, playerMost, playerLeast);
        }

        public final void recordInvalidationAttempt(
                MinecraftServer server, long playerMost, long playerLeast) {
            owner.recordInvalidationAttempt(server, playerMost, playerLeast);
        }

        public final void recordInvalidationAccepted(
                MinecraftServer server, long playerMost, long playerLeast) {
            owner.recordInvalidationAccepted(server, playerMost, playerLeast);
        }

        public final void recordReconciliation(
                MinecraftServer server,
                long playerMost,
                long playerLeast,
                ReconciliationVariant variant,
                ReconciliationDetail detail,
                int recoveryEntriesCleared,
                int recoveryStepsReplayed,
                int staleLatestObserved,
                int staleLatestPruned,
                int staleEquippedObserved,
                int staleEquippedPruned,
                int missingCount,
                int ownerMismatchCount,
                boolean acceptedGenerationPresent) {
            owner.recordReconciliation(
                    server,
                    playerMost,
                    playerLeast,
                    variant,
                    detail,
                    recoveryEntriesCleared,
                    recoveryStepsReplayed,
                    staleLatestObserved,
                    staleLatestPruned,
                    staleEquippedObserved,
                    staleEquippedPruned,
                    missingCount,
                    ownerMismatchCount,
                    acceptedGenerationPresent);
        }

        public final void clearOnServerStopped() {
            owner.clearOnServerStopped();
        }

        public final E3StartupView e3StartupView() {
            return e3StartupView;
        }
    }

    /** Closed startup recording capability without arm, claim, consume, or raw authority. */
    public static sealed abstract class E3StartupView permits E3StartupViewImpl {
        private final P4E2QualificationFacade owner;

        private E3StartupView(P4E2QualificationFacade owner) {
            this.owner = owner;
        }

        public final boolean beginRecording(MinecraftServer exactServer) {
            return owner.beginE3StartupRecording(exactServer);
        }

        public final void recordAuditInvocation(MinecraftServer exactServer) {
            owner.recordE3AuditInvocation(exactServer);
        }

        public final void recordAuditResult(
                MinecraftServer exactServer,
                E3AuditVariant variant,
                long generation) {
            owner.recordE3AuditResult(exactServer, variant, generation);
        }

        public final void recordCompleteConsumeInvocation(MinecraftServer exactServer) {
            owner.recordE3CompleteConsumeInvocation(exactServer);
        }

        public final void recordSnapshotInvocation(MinecraftServer exactServer) {
            owner.recordE3SnapshotInvocation(exactServer);
        }

        public final void recordSnapshotResult(
                MinecraftServer exactServer,
                E3SnapshotVariant variant,
                int completeRootCount) {
            owner.recordE3SnapshotResult(exactServer, variant, completeRootCount);
        }

        public final void recordReclaimInvocation(
                MinecraftServer exactServer,
                boolean dirtyBefore) {
            owner.recordE3ReclaimInvocation(exactServer, dirtyBefore);
        }

        public final void recordReclaimResult(
                MinecraftServer exactServer,
                E3ReclaimVariant variant,
                int historiesScanned,
                int revisionsScanned,
                int historiesChanged,
                int revisionsReclaimed) {
            owner.recordE3ReclaimResult(
                    exactServer,
                    variant,
                    historiesScanned,
                    revisionsScanned,
                    historiesChanged,
                    revisionsReclaimed);
        }

        public final void recordDirtyAfter(
                MinecraftServer exactServer,
                boolean dirtyAfter) {
            owner.recordE3DirtyAfter(exactServer, dirtyAfter);
        }

        public final void recordIndexTerminal(
                MinecraftServer exactServer,
                E3IndexTerminal terminal,
                long generation) {
            owner.recordE3IndexTerminal(exactServer, terminal, generation);
        }

        public final void completeRecording(MinecraftServer exactServer) {
            owner.completeE3StartupRecording(exactServer);
        }

        public final void abortRecording(MinecraftServer exactServer) {
            owner.abortE3StartupRecording(exactServer);
        }

        public final void clearOnServerStopped(MinecraftServer exactServer) {
            owner.clearE3OnServerStopped(exactServer);
        }
    }

    /** Closed nominal crossing to the actual E2-bound player publication path. */
    public static sealed abstract class PlayerView permits PlayerViewImpl {
        private final P4E2QualificationFacade owner;

        private PlayerView(P4E2QualificationFacade owner) {
            this.owner = owner;
        }

        public final void recordE2SetDataAttempt(
                MinecraftServer server, long playerMost, long playerLeast) {
            owner.recordE2SetDataAttempt(server, playerMost, playerLeast);
        }

        public final void recordE2SetDataSuccess(
                MinecraftServer server, long playerMost, long playerLeast) {
            owner.recordE2SetDataSuccess(server, playerMost, playerLeast);
        }
    }

    enum Phase {
        READY_FIRST,
        READY_RESTART
    }

    private enum State {
        IDLE,
        ARMED,
        RECORDING,
        COMPLETED,
        CONSUMED,
        ABORTED
    }

    private enum E3StartupState {
        IDLE,
        ARMED_BEFORE_SERVER_STARTING,
        RECORDING,
        COMPLETED,
        CONSUMED,
        ABORTED,
        CLEARED
    }

    static final class Session {
        private final P4E2QualificationFacade owner;
        private final long token;

        private Session(P4E2QualificationFacade owner, long token) {
            this.owner = owner;
            this.token = token;
        }
    }

    static final class E3StartupSession {
        private final P4E2QualificationFacade owner;
        private final long token;

        private E3StartupSession(P4E2QualificationFacade owner, long token) {
            this.owner = owner;
            this.token = token;
        }
    }

    record E3StartupSnapshot(
            long sessionToken,
            int auditInvocations,
            E3AuditVariant auditVariant,
            long auditGeneration,
            int completeConsumeInvocations,
            int snapshotInvocations,
            E3SnapshotVariant snapshotVariant,
            int completeRootCount,
            int reclaimInvocations,
            E3ReclaimVariant reclaimVariant,
            int historiesScanned,
            int revisionsScanned,
            int historiesChanged,
            int revisionsReclaimed,
            boolean dirtyBefore,
            boolean dirtyAfter,
            int indexTerminalObservations,
            E3IndexTerminal indexTerminal,
            long indexGeneration) {
    }

    private final SubmissionView submissionView = new SubmissionViewImpl(this);
    private final StoreView storeView = new StoreViewImpl(this);
    private final PlayerView playerView = new PlayerViewImpl(this);
    private State state = State.IDLE;
    private MinecraftServer server;
    private Thread logicThread;
    private long playerMost;
    private long playerLeast;
    private long caseId;
    private long nextToken;
    private long activeToken;
    private long completedToken;
    private Phase phase;
    private RecoveryVariant recoveryVariant;
    private RecoveryDetail recoveryDetail;
    private ReconciliationVariant reconciliationVariant;
    private ReconciliationDetail reconciliationDetail;
    private int recoveryHandlerCalls;
    private int entriesCleared;
    private int stepsReplayed;
    private boolean recoveryChanged;
    private int continuationCalls;
    private int invalidationAttempts;
    private int invalidationAccepted;
    private int setDataAttempts;
    private int setDataSuccesses;
    private int staleLatestObserved;
    private int staleLatestPruned;
    private int staleEquippedObserved;
    private int staleEquippedPruned;
    private int missingCount;
    private int ownerMismatchCount;
    private boolean acceptedGenerationPresent;
    private boolean recoveryRecorded;
    private boolean reconciliationRecorded;
    private E3StartupState e3State = E3StartupState.IDLE;
    private MinecraftServer e3SessionServer;
    private long e3NextToken;
    private long e3ActiveToken;
    private long e3CompletedToken;
    private boolean e3CompletedClaimed;
    private E3StartupSnapshot e3CompletedSnapshot;
    private int e3AuditInvocations;
    private boolean e3AuditResultRecorded;
    private E3AuditVariant e3AuditVariant;
    private long e3AuditGeneration = -1L;
    private int e3CompleteConsumeInvocations;
    private int e3SnapshotInvocations;
    private boolean e3SnapshotResultRecorded;
    private E3SnapshotVariant e3SnapshotVariant;
    private int e3CompleteRootCount = -1;
    private int e3ReclaimInvocations;
    private boolean e3ReclaimResultRecorded;
    private E3ReclaimVariant e3ReclaimVariant;
    private int e3HistoriesScanned = -1;
    private int e3RevisionsScanned = -1;
    private int e3HistoriesChanged = -1;
    private int e3RevisionsReclaimed = -1;
    private boolean e3DirtyBeforeRecorded;
    private boolean e3DirtyBefore;
    private boolean e3DirtyAfterRecorded;
    private boolean e3DirtyAfter;
    private int e3IndexTerminalObservations;
    private E3IndexTerminal e3IndexTerminal;
    private long e3IndexGeneration = -1L;

    P4E2QualificationFacade() {}

    Session arm(
            MinecraftServer exactServer,
            long expectedPlayerMost,
            long expectedPlayerLeast,
            long boundedCaseId,
            Phase expectedPhase) {
        if (state != State.IDLE) {
            throw new IllegalStateException("already armed");
        }
        var validatedServer = Objects.requireNonNull(exactServer, "exactServer");
        var validatedPhase = Objects.requireNonNull(expectedPhase, "expectedPhase");
        var token = Math.incrementExact(nextToken);
        var validatedLogicThread = Thread.currentThread();
        var session = new Session(this, token);
        server = validatedServer;
        logicThread = validatedLogicThread;
        playerMost = expectedPlayerMost;
        playerLeast = expectedPlayerLeast;
        caseId = boundedCaseId;
        phase = validatedPhase;
        nextToken = token;
        activeToken = token;
        state = State.ARMED;
        return session;
    }

    Snapshot consume(Session exactSession) {
        requireSession(exactSession);
        if (state != State.COMPLETED) {
            throw new IllegalStateException("record is not completed");
        }
        var result = new Snapshot(
                caseId,
                phase,
                recoveryVariant,
                recoveryDetail,
                reconciliationVariant,
                reconciliationDetail,
                entriesCleared,
                stepsReplayed,
                continuationCalls,
                invalidationAttempts,
                invalidationAccepted,
                setDataAttempts,
                setDataSuccesses,
                acceptedGenerationPresent);
        state = State.CONSUMED;
        clear();
        return result;
    }

    void discard(Session exactSession) {
        requireSession(exactSession);
        state = State.ABORTED;
        clear();
    }

    SubmissionView submissionView() {
        return submissionView;
    }

    StoreView storeView() {
        return storeView;
    }

    PlayerView playerView() {
        return playerView;
    }

    void armE3Startup(MinecraftServer exactServer) {
        if (e3State != E3StartupState.IDLE) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_ALREADY_ACTIVE");
        }
        requireE3ServerThread(exactServer);
        var next = Math.incrementExact(e3NextToken);
        resetE3Coordinates();
        e3SessionServer = exactServer;
        e3NextToken = next;
        e3ActiveToken = next;
        e3State = E3StartupState.ARMED_BEFORE_SERVER_STARTING;
    }

    E3StartupSession claimE3Startup(MinecraftServer exactServer) {
        if (e3State != E3StartupState.COMPLETED) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        if (e3CompletedClaimed) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_ALREADY_CLAIMED");
        }
        var claimed = new E3StartupSession(this, e3CompletedToken);
        e3CompletedClaimed = true;
        return claimed;
    }

    E3StartupSnapshot consumeE3Startup(
            MinecraftServer exactServer,
            E3StartupSession exactSession) {
        if (e3State != E3StartupState.COMPLETED) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        requireE3Session(exactSession);
        if (!e3CompletedClaimed) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_SESSION");
        }
        var result = e3CompletedSnapshot;
        if (result == null) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        e3State = E3StartupState.CONSUMED;
        clearE3Cell();
        return result;
    }

    void abortE3Startup(
            MinecraftServer exactServer,
            E3StartupSession exactSession) {
        if (e3State != E3StartupState.COMPLETED) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        requireE3Session(exactSession);
        e3State = E3StartupState.ABORTED;
        clearE3Cell();
    }

    private boolean beginE3StartupRecording(MinecraftServer exactServer) {
        if (e3State == E3StartupState.IDLE) {
            return false;
        }
        if (e3State != E3StartupState.ARMED_BEFORE_SERVER_STARTING) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        e3State = E3StartupState.RECORDING;
        return true;
    }

    private void recordE3AuditInvocation(MinecraftServer exactServer) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (e3AuditInvocations != 0) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3AuditInvocations = 1;
    }

    private void recordE3AuditResult(
            MinecraftServer exactServer,
            E3AuditVariant variant,
            long generation) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (e3AuditInvocations != 1 || e3AuditResultRecorded || variant == null
                || generation < 0L) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3AuditVariant = variant;
        e3AuditGeneration = generation;
        e3AuditResultRecorded = true;
    }

    private void recordE3CompleteConsumeInvocation(MinecraftServer exactServer) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (!e3AuditResultRecorded || e3AuditVariant != E3AuditVariant.COMPLETE
                || e3CompleteConsumeInvocations != 0) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3CompleteConsumeInvocations = 1;
    }

    private void recordE3SnapshotInvocation(MinecraftServer exactServer) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (e3CompleteConsumeInvocations != 1 || e3SnapshotInvocations != 0) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3SnapshotInvocations = 1;
    }

    private void recordE3SnapshotResult(
            MinecraftServer exactServer,
            E3SnapshotVariant variant,
            int completeRootCount) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        var validCount = variant == E3SnapshotVariant.COMPLETE
                ? completeRootCount >= 0
                : completeRootCount == -1;
        if (e3SnapshotInvocations != 1 || e3SnapshotResultRecorded
                || variant == null || !validCount) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3SnapshotVariant = variant;
        e3CompleteRootCount = completeRootCount;
        e3SnapshotResultRecorded = true;
    }

    private void recordE3ReclaimInvocation(
            MinecraftServer exactServer,
            boolean dirtyBefore) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (!e3SnapshotResultRecorded || e3SnapshotVariant != E3SnapshotVariant.COMPLETE
                || e3ReclaimInvocations != 0) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3DirtyBefore = dirtyBefore;
        e3DirtyBeforeRecorded = true;
        e3ReclaimInvocations = 1;
    }

    private void recordE3ReclaimResult(
            MinecraftServer exactServer,
            E3ReclaimVariant variant,
            int historiesScanned,
            int revisionsScanned,
            int historiesChanged,
            int revisionsReclaimed) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        var completed = variant == E3ReclaimVariant.COMPLETED_ZERO
                || variant == E3ReclaimVariant.COMPLETED_POSITIVE;
        var countsValid = completed
                ? historiesScanned >= 0 && revisionsScanned >= 0
                        && historiesChanged >= 0 && revisionsReclaimed >= 0
                : historiesScanned == -1 && revisionsScanned == -1
                        && historiesChanged == -1 && revisionsReclaimed == -1;
        var zeroPositiveValid = variant != E3ReclaimVariant.COMPLETED_ZERO
                || revisionsReclaimed == 0;
        zeroPositiveValid &= variant != E3ReclaimVariant.COMPLETED_POSITIVE
                || revisionsReclaimed > 0;
        if (e3ReclaimInvocations != 1 || e3ReclaimResultRecorded || variant == null
                || !countsValid || !zeroPositiveValid) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3ReclaimVariant = variant;
        e3HistoriesScanned = historiesScanned;
        e3RevisionsScanned = revisionsScanned;
        e3HistoriesChanged = historiesChanged;
        e3RevisionsReclaimed = revisionsReclaimed;
        e3ReclaimResultRecorded = true;
    }

    private void recordE3DirtyAfter(MinecraftServer exactServer, boolean dirtyAfter) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (!e3ReclaimResultRecorded || e3DirtyAfterRecorded) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3DirtyAfter = dirtyAfter;
        e3DirtyAfterRecorded = true;
    }

    private void recordE3IndexTerminal(
            MinecraftServer exactServer,
            E3IndexTerminal terminal,
            long generation) {
        if (!e3RecordingEnabled(exactServer)) {
            return;
        }
        if (!e3AuditResultRecorded || e3AuditVariant != E3AuditVariant.COMPLETE
                || e3IndexTerminalObservations != 0 || terminal == null
                || generation != e3AuditGeneration) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        var snapshotTerminal = e3CompleteConsumeInvocations == 1
                && e3SnapshotInvocations == 1
                && e3SnapshotResultRecorded
                && e3SnapshotVariant != E3SnapshotVariant.COMPLETE
                && e3ReclaimInvocations == 0
                && terminal == E3IndexTerminal.INCOMPLETE;
        var reclaimTerminal = e3CompleteConsumeInvocations == 1
                && e3SnapshotInvocations == 1
                && e3SnapshotResultRecorded
                && e3SnapshotVariant == E3SnapshotVariant.COMPLETE
                && e3ReclaimInvocations == 1
                && e3ReclaimResultRecorded
                && e3DirtyBeforeRecorded
                && e3DirtyAfterRecorded
                && ((terminal == E3IndexTerminal.COMPLETE_INDEX)
                        == (e3ReclaimVariant == E3ReclaimVariant.COMPLETED_ZERO));
        if (!snapshotTerminal && !reclaimTerminal) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
        e3IndexTerminal = terminal;
        e3IndexGeneration = generation;
        e3IndexTerminalObservations = 1;
    }

    private void completeE3StartupRecording(MinecraftServer exactServer) {
        if (e3State == E3StartupState.IDLE) {
            return;
        }
        if (e3State != E3StartupState.RECORDING) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        requireCompleteE3Coordinates();
        var completed = new E3StartupSnapshot(
                e3ActiveToken,
                e3AuditInvocations,
                e3AuditVariant,
                e3AuditGeneration,
                e3CompleteConsumeInvocations,
                e3SnapshotInvocations,
                e3SnapshotVariant,
                e3CompleteRootCount,
                e3ReclaimInvocations,
                e3ReclaimVariant,
                e3HistoriesScanned,
                e3RevisionsScanned,
                e3HistoriesChanged,
                e3RevisionsReclaimed,
                e3DirtyBefore,
                e3DirtyAfter,
                e3IndexTerminalObservations,
                e3IndexTerminal,
                e3IndexGeneration);
        e3CompletedSnapshot = completed;
        e3CompletedToken = e3ActiveToken;
        e3ActiveToken = 0L;
        e3CompletedClaimed = false;
        resetE3Coordinates();
        e3SessionServer = exactServer;
        e3State = E3StartupState.COMPLETED;
    }

    private void abortE3StartupRecording(MinecraftServer exactServer) {
        if (e3State == E3StartupState.IDLE) {
            return;
        }
        if (e3State != E3StartupState.ARMED_BEFORE_SERVER_STARTING
                && e3State != E3StartupState.RECORDING
                && e3State != E3StartupState.COMPLETED) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        e3State = E3StartupState.ABORTED;
        clearE3Cell();
    }

    private void clearE3OnServerStopped(MinecraftServer exactServer) {
        if (e3State == E3StartupState.IDLE) {
            return;
        }
        requireE3Context(exactServer);
        e3State = E3StartupState.CLEARED;
        clearE3Cell();
    }

    private boolean e3RecordingEnabled(MinecraftServer exactServer) {
        if (e3State == E3StartupState.IDLE) {
            return false;
        }
        if (e3State != E3StartupState.RECORDING) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_STATE");
        }
        requireE3Context(exactServer);
        return true;
    }

    private void requireCompleteE3Coordinates() {
        var auditTerminal = e3AuditInvocations == 1 && e3AuditResultRecorded
                && e3AuditVariant != E3AuditVariant.COMPLETE
                && e3CompleteConsumeInvocations == 0
                && e3SnapshotInvocations == 0
                && e3ReclaimInvocations == 0
                && e3IndexTerminalObservations == 0;
        var snapshotTerminal = e3AuditVariant == E3AuditVariant.COMPLETE
                && e3CompleteConsumeInvocations == 1
                && e3SnapshotInvocations == 1
                && e3SnapshotResultRecorded
                && e3SnapshotVariant != E3SnapshotVariant.COMPLETE
                && e3ReclaimInvocations == 0
                && e3IndexTerminalObservations == 1
                && e3IndexTerminal == E3IndexTerminal.INCOMPLETE
                && e3IndexGeneration == e3AuditGeneration;
        var reclaimTerminal = e3AuditVariant == E3AuditVariant.COMPLETE
                && e3CompleteConsumeInvocations == 1
                && e3SnapshotInvocations == 1
                && e3SnapshotResultRecorded
                && e3SnapshotVariant == E3SnapshotVariant.COMPLETE
                && e3ReclaimInvocations == 1
                && e3ReclaimResultRecorded
                && e3DirtyBeforeRecorded
                && e3DirtyAfterRecorded
                && e3IndexTerminalObservations == 1
                && e3IndexGeneration == e3AuditGeneration
                && ((e3IndexTerminal == E3IndexTerminal.COMPLETE_INDEX)
                        == (e3ReclaimVariant == E3ReclaimVariant.COMPLETED_ZERO));
        if (!auditTerminal && !snapshotTerminal && !reclaimTerminal) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_INVALID_COORDINATE");
        }
    }

    private void requireE3Session(E3StartupSession exactSession) {
        if (exactSession == null || exactSession.owner != this
                || exactSession.token == 0L
                || exactSession.token != e3CompletedToken) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_SESSION");
        }
    }

    private void requireE3Context(MinecraftServer exactServer) {
        if (e3SessionServer != exactServer || exactServer == null
                || !exactServer.isSameThread()) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_CONTEXT");
        }
    }

    private static void requireE3ServerThread(MinecraftServer exactServer) {
        if (exactServer == null || !exactServer.isSameThread()) {
            throw e3Failure("P4E3_STARTUP_OBSERVATION_WRONG_CONTEXT");
        }
    }

    private void resetE3Coordinates() {
        e3AuditInvocations = 0;
        e3AuditResultRecorded = false;
        e3AuditVariant = null;
        e3AuditGeneration = -1L;
        e3CompleteConsumeInvocations = 0;
        e3SnapshotInvocations = 0;
        e3SnapshotResultRecorded = false;
        e3SnapshotVariant = null;
        e3CompleteRootCount = -1;
        e3ReclaimInvocations = 0;
        e3ReclaimResultRecorded = false;
        e3ReclaimVariant = null;
        e3HistoriesScanned = -1;
        e3RevisionsScanned = -1;
        e3HistoriesChanged = -1;
        e3RevisionsReclaimed = -1;
        e3DirtyBeforeRecorded = false;
        e3DirtyBefore = false;
        e3DirtyAfterRecorded = false;
        e3DirtyAfter = false;
        e3IndexTerminalObservations = 0;
        e3IndexTerminal = null;
        e3IndexGeneration = -1L;
    }

    private void clearE3Cell() {
        e3SessionServer = null;
        e3ActiveToken = 0L;
        e3CompletedToken = 0L;
        e3CompletedClaimed = false;
        e3CompletedSnapshot = null;
        resetE3Coordinates();
        e3State = E3StartupState.IDLE;
    }

    private static IllegalStateException e3Failure(String code) {
        return new IllegalStateException(code);
    }

    private boolean enabledFor(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (state != State.ARMED && state != State.RECORDING) {
            return false;
        }
        requireContext(exactServer, exactPlayerMost, exactPlayerLeast);
        return true;
    }

    private void recordRecovery(
            MinecraftServer exactServer,
            long exactPlayerMost,
            long exactPlayerLeast,
            RecoveryVariant variant,
            RecoveryDetail detail,
            int cleared,
            int replayed) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (recoveryRecorded) {
            throw new IllegalStateException("recovery was already recorded");
        }
        requireNonNegative(cleared, "cleared");
        requireNonNegative(replayed, "replayed");
        recoveryVariant = Objects.requireNonNull(variant, "variant");
        recoveryDetail = Objects.requireNonNull(detail, "detail");
        recoveryHandlerCalls = 1;
        entriesCleared = cleared;
        stepsReplayed = replayed;
        recoveryChanged = cleared > 0 || replayed > 0;
        recoveryRecorded = true;
        state = State.RECORDING;
    }

    private void recordContinuation(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (continuationCalls != 0) {
            throw new IllegalStateException("continuation was already recorded");
        }
        continuationCalls = 1;
        state = State.RECORDING;
    }

    private void recordInvalidationAttempt(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (invalidationAttempts != 0) {
            throw new IllegalStateException("invalidation attempt was already recorded");
        }
        invalidationAttempts = 1;
        state = State.RECORDING;
    }

    private void recordInvalidationAccepted(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (invalidationAttempts != 1 || invalidationAccepted != 0) {
            throw new IllegalStateException("invalidation acceptance coordinate is invalid");
        }
        invalidationAccepted = 1;
        acceptedGenerationPresent = true;
        state = State.RECORDING;
    }

    private void recordReconciliation(
            MinecraftServer exactServer,
            long exactPlayerMost,
            long exactPlayerLeast,
            ReconciliationVariant variant,
            ReconciliationDetail detail,
            int recoveryEntriesCleared,
            int recoveryStepsReplayed,
            int staleLatestObserved,
            int staleLatestPruned,
            int staleEquippedObserved,
            int staleEquippedPruned,
            int missingCount,
            int ownerMismatchCount,
            boolean generationPresent) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (reconciliationRecorded) {
            throw new IllegalStateException("reconciliation was already recorded");
        }
        requireNonNegative(staleLatestObserved, "staleLatestObserved");
        requireNonNegative(staleLatestPruned, "staleLatestPruned");
        requireNonNegative(staleEquippedObserved, "staleEquippedObserved");
        requireNonNegative(staleEquippedPruned, "staleEquippedPruned");
        requireNonNegative(missingCount, "missingCount");
        requireNonNegative(ownerMismatchCount, "ownerMismatchCount");
        if (!recoveryRecorded
                || recoveryEntriesCleared != entriesCleared
                || recoveryStepsReplayed != stepsReplayed) {
            throw new IllegalStateException("recovery accounting did not remain identity-bound");
        }
        reconciliationVariant = Objects.requireNonNull(variant, "variant");
        reconciliationDetail = Objects.requireNonNull(detail, "detail");
        if (generationPresent != acceptedGenerationPresent) {
            throw new IllegalStateException("accepted generation coordinate is inconsistent");
        }
        this.staleLatestObserved = staleLatestObserved;
        this.staleLatestPruned = staleLatestPruned;
        this.staleEquippedObserved = staleEquippedObserved;
        this.staleEquippedPruned = staleEquippedPruned;
        this.missingCount = missingCount;
        this.ownerMismatchCount = ownerMismatchCount;
        reconciliationRecorded = true;
        state = State.RECORDING;
    }

    private void recordE2SetDataAttempt(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (setDataAttempts != 0) {
            throw new IllegalStateException("setData attempt was already recorded");
        }
        setDataAttempts = 1;
        state = State.RECORDING;
    }

    private void recordE2SetDataSuccess(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (!enabledFor(exactServer, exactPlayerMost, exactPlayerLeast)) {
            return;
        }
        if (setDataAttempts != 1 || setDataSuccesses != 0) {
            throw new IllegalStateException("setData success coordinate is invalid");
        }
        setDataSuccesses = 1;
        state = State.RECORDING;
    }

    private void completeAfterContinuation(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (state == State.IDLE) {
            return;
        }
        requireContext(exactServer, exactPlayerMost, exactPlayerLeast);
        var mandatoryCoordinatesComplete = state == State.RECORDING
                && recoveryRecorded
                && reconciliationRecorded
                && recoveryHandlerCalls == 1
                && recoveryChanged == (entriesCleared > 0 || stepsReplayed > 0)
                && continuationCalls == 1
                && setDataSuccesses <= setDataAttempts
                && invalidationAccepted <= invalidationAttempts
                && staleLatestPruned <= staleLatestObserved
                && staleEquippedPruned <= staleEquippedObserved
                && (reconciliationVariant != ReconciliationVariant.CHANGED
                        || (setDataAttempts == 1 && setDataSuccesses == 1));
        if (!mandatoryCoordinatesComplete) {
            // The production wrapper reaches this method normally only after every mandatory
            // coordinate was recorded. A correct-context incomplete cell is an abnormal partial.
            state = State.ABORTED;
            clear();
            return;
        }
        completedToken = activeToken;
        activeToken = 0L;
        server = null;
        logicThread = null;
        state = State.COMPLETED;
    }

    private void clearOnServerStopped() {
        if (state != State.IDLE) {
            state = State.ABORTED;
            clear();
        }
    }

    private void requireSession(Session exactSession) {
        var expectedToken = state == State.COMPLETED ? completedToken : activeToken;
        if (exactSession == null
                || exactSession.owner != this
                || expectedToken == 0L
                || exactSession.token != expectedToken) {
            throw new IllegalStateException("wrong session");
        }
    }

    private void requireContext(
            MinecraftServer exactServer, long exactPlayerMost, long exactPlayerLeast) {
        if (server != exactServer
                || logicThread != Thread.currentThread()
                || playerMost != exactPlayerMost
                || playerLeast != exactPlayerLeast) {
            throw new IllegalStateException("wrong qualification context");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private void clear() {
        state = State.IDLE;
        server = null;
        logicThread = null;
        playerMost = 0L;
        playerLeast = 0L;
        caseId = 0L;
        activeToken = 0L;
        completedToken = 0L;
        phase = null;
        recoveryVariant = null;
        recoveryDetail = null;
        reconciliationVariant = null;
        reconciliationDetail = null;
        recoveryHandlerCalls = 0;
        entriesCleared = 0;
        stepsReplayed = 0;
        recoveryChanged = false;
        continuationCalls = 0;
        invalidationAttempts = 0;
        invalidationAccepted = 0;
        setDataAttempts = 0;
        setDataSuccesses = 0;
        staleLatestObserved = 0;
        staleLatestPruned = 0;
        staleEquippedObserved = 0;
        staleEquippedPruned = 0;
        missingCount = 0;
        ownerMismatchCount = 0;
        acceptedGenerationPresent = false;
        recoveryRecorded = false;
        reconciliationRecorded = false;
    }

    record Snapshot(
            long caseId,
            Phase phase,
            RecoveryVariant recoveryVariant,
            RecoveryDetail recoveryDetail,
            ReconciliationVariant reconciliationVariant,
            ReconciliationDetail reconciliationDetail,
            int entriesCleared,
            int stepsReplayed,
            int continuationCalls,
            int invalidationAttempts,
            int invalidationAccepted,
            int setDataAttempts,
            int setDataSuccesses,
            boolean acceptedGenerationPresent) {}

    private static final class SubmissionViewImpl extends SubmissionView {
        private SubmissionViewImpl(P4E2QualificationFacade owner) {
            super(owner);
        }
    }

    private static final class StoreViewImpl extends StoreView {
        private StoreViewImpl(P4E2QualificationFacade owner) {
            super(owner);
        }
    }

    private static final class E3StartupViewImpl extends E3StartupView {
        private E3StartupViewImpl(P4E2QualificationFacade owner) {
            super(owner);
        }
    }

    private static final class PlayerViewImpl extends PlayerView {
        private PlayerViewImpl(P4E2QualificationFacade owner) {
            super(owner);
        }
    }
}
