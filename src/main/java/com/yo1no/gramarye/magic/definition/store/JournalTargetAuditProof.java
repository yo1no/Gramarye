package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/** Nominal proof that every journal target is or will be present in the paired Store state. */
sealed interface JournalTargetAuditProof
        permits JournalTargetAuditProof.AuditedExisting,
                JournalTargetAuditProof.ConditionalOnExactCommit {
    boolean isFor(PendingAttachmentJournal journal);

    final class AuditedExisting implements JournalTargetAuditProof {
        private final PendingAttachmentJournal journal;

        AuditedExisting(PendingAttachmentJournal journal) {
            this.journal = Objects.requireNonNull(journal, "journal");
        }

        @Override
        public boolean isFor(PendingAttachmentJournal candidate) {
            return journal == Objects.requireNonNull(candidate, "candidate");
        }
    }

    /**
     * Exact prepare-time condition discharged immediately before its prebuilt Ready is published.
     * A successful discharge clears every base-state and carrier-update reference.
     */
    final class ConditionalOnExactCommit implements JournalTargetAuditProof {
        private final PendingAttachmentJournal journal;
        private PendingAttachmentJournalState.Ready baseJournalState;
        private PreparedCarrierUpdate preparedCarrierUpdate;
        private SkillOwnerId planOwner;
        private SkillId skillId;
        private SkillReference targetReference;
        private SkillReference expectedCommittedReference;
        private boolean satisfied;

        ConditionalOnExactCommit(
                PendingAttachmentJournalState.Ready baseJournalState,
                PreparedCarrierUpdate preparedCarrierUpdate,
                PendingAttachmentJournal journal,
                SkillOwnerId planOwner,
                SkillId skillId,
                SkillReference targetReference,
                SkillReference expectedCommittedReference) {
            this.baseJournalState = Objects.requireNonNull(
                    baseJournalState, "baseJournalState");
            this.preparedCarrierUpdate = Objects.requireNonNull(
                    preparedCarrierUpdate, "preparedCarrierUpdate");
            this.journal = Objects.requireNonNull(journal, "journal");
            this.planOwner = Objects.requireNonNull(planOwner, "planOwner");
            this.skillId = Objects.requireNonNull(skillId, "skillId");
            this.targetReference = Objects.requireNonNull(
                    targetReference, "targetReference");
            this.expectedCommittedReference = Objects.requireNonNull(
                    expectedCommittedReference, "expectedCommittedReference");
            if (!skillId.equals(targetReference.skillId())
                    || !targetReference.equals(expectedCommittedReference)
                    || !preparedCarrierUpdate.proposedReference().equals(targetReference)) {
                throw new IllegalArgumentException(
                        "conditional target-audit proof inputs must identify one exact target");
            }
        }

        @Override
        public boolean isFor(PendingAttachmentJournal candidate) {
            return journal == Objects.requireNonNull(candidate, "candidate");
        }

        boolean satisfy(
                PendingAttachmentJournalState.Ready candidateBase,
                PreparedCarrierUpdate candidateUpdate,
                SkillOwnerId candidateOwner,
                SkillId candidateSkillId,
                SkillReference candidateTarget,
                SkillReference committed,
                JournalTargetAuditProof candidateProof) {
            Objects.requireNonNull(candidateBase, "candidateBase");
            Objects.requireNonNull(candidateUpdate, "candidateUpdate");
            Objects.requireNonNull(candidateOwner, "candidateOwner");
            Objects.requireNonNull(candidateSkillId, "candidateSkillId");
            Objects.requireNonNull(candidateTarget, "candidateTarget");
            Objects.requireNonNull(committed, "committed");
            Objects.requireNonNull(candidateProof, "candidateProof");
            if (satisfied) {
                throw new IllegalStateException("conditional target-audit proof already satisfied");
            }
            if (baseJournalState != candidateBase
                    || preparedCarrierUpdate != candidateUpdate
                    || !planOwner.equals(candidateOwner)
                    || !skillId.equals(candidateSkillId)
                    || !targetReference.equals(candidateTarget)
                    || !expectedCommittedReference.equals(committed)
                    || candidateProof != this) {
                return false;
            }
            baseJournalState = null;
            preparedCarrierUpdate = null;
            planOwner = null;
            skillId = null;
            targetReference = null;
            expectedCommittedReference = null;
            satisfied = true;
            return true;
        }

        boolean isSatisfied() {
            return satisfied;
        }
    }
}
