package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Closed, non-authoritative recovery-to-reconciliation composition boundary. */
public sealed interface P4E2OnlineReconciliationDependency
        permits P4E2OnlineReconciliationCoordinator {
    /** Continues one exact login call chain after its typed recovery outcome is available. */
    void reconcileAfterRecovery(
            ServerPlayer player,
            SkillSubmissionRecoveryService.RecoveryContinuation continuation,
            RecoveryKind kind,
            int entriesCleared,
            int stepsReplayed,
            Optional<String> existingExceptionClass);

    /** Exhaustive projection of the existing sealed P4-D outcome hierarchy. */
    enum RecoveryKind {
        NO_PENDING,
        CLEARED,
        REPLAYED,
        CLEARED_AND_REPLAYED,
        CONFLICT,
        TARGET_INVALID,
        JOURNAL_NOT_BOOTSTRAPPED,
        JOURNAL_UNAVAILABLE,
        STORE_UNAVAILABLE,
        AUTHORITY_UNAVAILABLE,
        ATTACHMENT_PRESERVED_RAW_QUARANTINE,
        ATTACHMENT_OVERSIZE_QUARANTINE,
        RUNTIME_EXCEPTION
    }
}
