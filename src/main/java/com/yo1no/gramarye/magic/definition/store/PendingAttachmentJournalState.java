package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Derived, server-thread-confined operational view of the P4-B pending blob. */
sealed interface PendingAttachmentJournalState
        permits PendingAttachmentJournalState.Ready,
                PendingAttachmentJournalState.Unavailable {
    final class Ready implements PendingAttachmentJournalState {
        private final PendingAttachmentJournal journal;
        private final EncodedPendingAttachmentJournal encoded;
        private final OpaquePendingAttachmentUpdatesBlob sourcePending;
        private final boolean rewriteRequired;
        private final JournalTargetAuditProof targetAuditProof;

        Ready(
                PendingAttachmentJournal journal,
                EncodedPendingAttachmentJournal encoded,
                OpaquePendingAttachmentUpdatesBlob sourcePending,
                boolean rewriteRequired,
                JournalTargetAuditProof targetAuditProof) {
            this.journal = Objects.requireNonNull(journal, "journal");
            this.encoded = Objects.requireNonNull(encoded, "encoded");
            this.sourcePending = Objects.requireNonNull(sourcePending, "sourcePending");
            this.targetAuditProof = Objects.requireNonNull(
                    targetAuditProof, "targetAuditProof");
            if (!targetAuditProof.isFor(journal)) {
                throw new IllegalArgumentException(
                        "target audit proof must bind the exact journal identity");
            }
            if (encoded.entryCount() != journal.entryCount()) {
                throw new IllegalArgumentException(
                        "encoded journal entry count must match its domain journal");
            }
            this.rewriteRequired = rewriteRequired;
        }

        PendingAttachmentJournal journal() {
            return journal;
        }

        EncodedPendingAttachmentJournal encoded() {
            return encoded;
        }

        OpaquePendingAttachmentUpdatesBlob sourcePending() {
            return sourcePending;
        }

        boolean rewriteRequired() {
            return rewriteRequired;
        }

        JournalTargetAuditProof targetAuditProof() {
            return targetAuditProof;
        }

        @Override
        public String toString() {
            return "Ready[entryCount=" + journal.entryCount()
                    + ", byteCount=" + encoded.byteCount()
                    + ", rewriteRequired=" + rewriteRequired + ']';
        }
    }

    record Unavailable(PendingAttachmentJournalOperationalFailure failure)
            implements PendingAttachmentJournalState {
        public Unavailable {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

/** Bounded operational failure retained without a partial journal or raw payload. */
sealed interface PendingAttachmentJournalOperationalFailure
        permits PendingAttachmentJournalOperationalFailure.Persistence,
                PendingAttachmentJournalOperationalFailure.TargetAudit {
    record Persistence(PendingAttachmentJournalFailure failure)
            implements PendingAttachmentJournalOperationalFailure {
        public Persistence {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record TargetAudit(PendingAttachmentJournalFailure failure)
            implements PendingAttachmentJournalOperationalFailure {
        public TargetAudit {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
