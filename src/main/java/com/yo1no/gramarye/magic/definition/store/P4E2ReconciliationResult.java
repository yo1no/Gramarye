package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Package-owned bounded terminal result for one synchronous E2 login batch. */
sealed interface P4E2ReconciliationResult
        permits P4E2ReconciliationResult.NoChanges,
                P4E2ReconciliationResult.RecoveryChanged,
                P4E2ReconciliationResult.Changed,
                P4E2ReconciliationResult.Deferred,
                P4E2ReconciliationResult.Failed,
                P4E2ReconciliationResult.GenerationExhausted {
    record NoChanges(Summary summary) implements P4E2ReconciliationResult {
        public NoChanges {
            Objects.requireNonNull(summary, "summary");
            requireNoPrune(summary);
            requireNoStaleObserved(summary);
            if (summary.recoveryChanged() || summary.acceptedGeneration().isPresent()) {
                throw new IllegalArgumentException("NoChanges summary is inconsistent");
            }
        }
    }

    record RecoveryChanged(Summary summary) implements P4E2ReconciliationResult {
        public RecoveryChanged {
            Objects.requireNonNull(summary, "summary");
            requireNoPrune(summary);
            requireNoStaleObserved(summary);
            if (!summary.recoveryChanged() || summary.acceptedGeneration().isEmpty()) {
                throw new IllegalArgumentException("RecoveryChanged summary is inconsistent");
            }
        }
    }

    record Changed(Summary summary) implements P4E2ReconciliationResult {
        public Changed {
            Objects.requireNonNull(summary, "summary");
            if (summary.totalPruned() <= 0
                    || summary.staleLatestPruned() != summary.staleLatestObserved()
                    || summary.staleEquippedPruned() != summary.staleEquippedObserved()
                    || summary.acceptedGeneration().isEmpty()) {
                throw new IllegalArgumentException("Changed summary is inconsistent");
            }
        }
    }

    record Deferred(Summary summary, DeferredReason reason)
            implements P4E2ReconciliationResult {
        public Deferred {
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(reason, "reason");
            requireNoPrune(summary);
        }
    }

    record Failed(Summary summary, FailureReason reason, Optional<String> exceptionClass)
            implements P4E2ReconciliationResult {
        private static final int MAX_EXCEPTION_CLASS_LENGTH = 160;

        public Failed {
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(reason, "reason");
            exceptionClass = Objects.requireNonNull(exceptionClass, "exceptionClass");
            requireNoPrune(summary);
            if (exceptionClass.stream().anyMatch(value -> value.isEmpty()
                    || value.length() > MAX_EXCEPTION_CLASS_LENGTH)) {
                throw new IllegalArgumentException("exceptionClass is outside its bound");
            }
            if ((reason == FailureReason.INTERNAL_RUNTIME_FAILURE
                            || reason == FailureReason.RECOVERY_RUNTIME_FAILURE)
                    != exceptionClass.isPresent()) {
                throw new IllegalArgumentException(
                        "only runtime failures carry an exception class");
            }
        }
    }

    record GenerationExhausted(Summary summary) implements P4E2ReconciliationResult {
        public GenerationExhausted {
            Objects.requireNonNull(summary, "summary");
            requireNoPrune(summary);
            if (summary.acceptedGeneration().isPresent()) {
                throw new IllegalArgumentException(
                        "GenerationExhausted cannot carry an accepted generation");
            }
        }
    }

    record Summary(
            int recoveryEntriesCleared,
            int recoveryStepsReplayed,
            int staleLatestObserved,
            int staleLatestPruned,
            int staleEquippedObserved,
            int staleEquippedPruned,
            int missingCount,
            int ownerMismatchCount,
            OptionalLong acceptedGeneration) {
        public Summary {
            acceptedGeneration = Objects.requireNonNull(
                    acceptedGeneration, "acceptedGeneration");
            requireBounded(recoveryEntriesCleared, "recoveryEntriesCleared",
                    MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES);
            requireBounded(recoveryStepsReplayed, "recoveryStepsReplayed",
                    MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES);
            requireBounded(staleLatestObserved, "staleLatestObserved",
                    MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES);
            requireBounded(staleLatestPruned, "staleLatestPruned",
                    MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES);
            requireBounded(staleEquippedObserved, "staleEquippedObserved",
                    MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES);
            requireBounded(staleEquippedPruned, "staleEquippedPruned",
                    MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES);
            var maximumClaims = Math.addExact(
                    MagicSafetyCeilings.MAX_PLAYER_LATEST_STATES,
                    MagicSafetyCeilings.MAX_PLAYER_EQUIPPED_REFERENCES);
            requireBounded(missingCount, "missingCount", maximumClaims);
            requireBounded(ownerMismatchCount, "ownerMismatchCount", maximumClaims);
            if (staleLatestPruned > staleLatestObserved
                    || staleEquippedPruned > staleEquippedObserved
                    || (long) recoveryEntriesCleared + recoveryStepsReplayed
                            > MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES
                    || (long) missingCount + ownerMismatchCount
                            != (long) staleLatestObserved + staleEquippedObserved
                    || acceptedGeneration.stream().anyMatch(value -> value <= 0L)) {
                throw new IllegalArgumentException("reconciliation summary is inconsistent");
            }
        }

        boolean recoveryChanged() {
            return recoveryEntriesCleared > 0 || recoveryStepsReplayed > 0;
        }

        int totalPruned() {
            return Math.addExact(staleLatestPruned, staleEquippedPruned);
        }

        private static void requireBounded(int value, String name, int maximum) {
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException(name + " is outside its bound");
            }
        }
    }

    private static void requireNoPrune(Summary summary) {
        if (summary.totalPruned() != 0) {
            throw new IllegalArgumentException("terminal result cannot report a prune");
        }
    }

    private static void requireNoStaleObserved(Summary summary) {
        if (summary.staleLatestObserved() != 0 || summary.staleEquippedObserved() != 0) {
            throw new IllegalArgumentException("unchanged result cannot report stale routes");
        }
    }

    enum DeferredReason {
        RECOVERY_OPERATIONAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        ATTACHMENT_PRESERVED_RAW,
        ATTACHMENT_OVERSIZE
    }

    enum FailureReason {
        RECOVERY_CONFLICT,
        RECOVERY_TARGET_INVALID,
        RECOVERY_RUNTIME_FAILURE,
        PLAYER_GENERATION_EXHAUSTED,
        ATTACHMENT_CAPACITY_REJECTED,
        ATTACHMENT_INVARIANT_REJECTED,
        FRESHNESS_LOST,
        INTERNAL_RUNTIME_FAILURE
    }
}
