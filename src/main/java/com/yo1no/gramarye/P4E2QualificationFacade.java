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

        private StoreView(P4E2QualificationFacade owner) {
            this.owner = owner;
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

    static final class Session {
        private final P4E2QualificationFacade owner;
        private final long token;

        private Session(P4E2QualificationFacade owner, long token) {
            this.owner = owner;
            this.token = token;
        }
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

    private static final class PlayerViewImpl extends PlayerView {
        private PlayerViewImpl(P4E2QualificationFacade owner) {
            super(owner);
        }
    }
}
