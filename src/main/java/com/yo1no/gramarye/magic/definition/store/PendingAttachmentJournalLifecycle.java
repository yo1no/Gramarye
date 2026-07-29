package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Explicit P4-D journal bootstrap lifecycle carried by a P4-B Ready state. */
sealed interface PendingAttachmentJournalLifecycle
        permits PendingAttachmentJournalLifecycle.Uninitialized,
                PendingAttachmentJournalLifecycle.Installed {
    enum Uninitialized implements PendingAttachmentJournalLifecycle {
        INSTANCE
    }

    record Installed(PendingAttachmentJournalState state)
            implements PendingAttachmentJournalLifecycle {
        public Installed {
            Objects.requireNonNull(state, "state");
        }
    }
}
