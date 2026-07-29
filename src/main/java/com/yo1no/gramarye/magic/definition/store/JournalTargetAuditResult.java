package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

sealed interface JournalTargetAuditResult
        permits JournalTargetAuditResult.Audited,
                JournalTargetAuditResult.Rejected {
    record Audited(JournalTargetAuditProof.AuditedExisting proof)
            implements JournalTargetAuditResult {
        public Audited {
            Objects.requireNonNull(proof, "proof");
        }
    }

    record Rejected(PendingAttachmentJournalFailure failure)
            implements JournalTargetAuditResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
