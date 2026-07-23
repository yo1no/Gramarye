package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

sealed interface SkillDefinitionStoreRestoreFailure
        permits SkillDefinitionStoreRestoreFailure.CapacityExceeded,
                SkillDefinitionStoreRestoreFailure.DuplicateSkillId,
                SkillDefinitionStoreRestoreFailure.EmptyHistory,
                SkillDefinitionStoreRestoreFailure.DuplicateRevision,
                SkillDefinitionStoreRestoreFailure.DocumentSkillIdMismatch,
                SkillDefinitionStoreRestoreFailure.DocumentRevisionMismatch,
                SkillDefinitionStoreRestoreFailure.UnsupportedDocumentSchema,
                SkillDefinitionStoreRestoreFailure.EmptyDocumentNodes {
    record CapacityExceeded(SkillStoreCapacityScope scope, int current, int maximum)
            implements SkillDefinitionStoreRestoreFailure {
        public CapacityExceeded {
            Objects.requireNonNull(scope, "scope");
            if (current < 0 || maximum < 0 || current <= maximum) {
                throw new IllegalArgumentException(
                        "capacity metadata requires non-negative current > maximum");
            }
        }
    }

    record DuplicateSkillId(SkillId skillId) implements SkillDefinitionStoreRestoreFailure {
        public DuplicateSkillId {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    record EmptyHistory(SkillId skillId) implements SkillDefinitionStoreRestoreFailure {
        public EmptyHistory {
            Objects.requireNonNull(skillId, "skillId");
        }
    }

    record DuplicateRevision(SkillReference reference)
            implements SkillDefinitionStoreRestoreFailure {
        public DuplicateRevision {
            Objects.requireNonNull(reference, "reference");
        }
    }

    record DocumentSkillIdMismatch(
            SkillId routeSkillId,
            SkillId documentSkillId,
            SkillRevision routeRevision) implements SkillDefinitionStoreRestoreFailure {
        public DocumentSkillIdMismatch {
            Objects.requireNonNull(routeSkillId, "routeSkillId");
            Objects.requireNonNull(documentSkillId, "documentSkillId");
            Objects.requireNonNull(routeRevision, "routeRevision");
        }
    }

    record DocumentRevisionMismatch(
            SkillReference routeReference,
            SkillReference documentReference) implements SkillDefinitionStoreRestoreFailure {
        public DocumentRevisionMismatch {
            Objects.requireNonNull(routeReference, "routeReference");
            Objects.requireNonNull(documentReference, "documentReference");
        }
    }

    record UnsupportedDocumentSchema(
            SkillReference reference,
            int actual,
            int expected) implements SkillDefinitionStoreRestoreFailure {
        public UnsupportedDocumentSchema {
            Objects.requireNonNull(reference, "reference");
            if (actual < 0 || expected < 0) {
                throw new IllegalArgumentException("schema versions must be non-negative");
            }
        }
    }

    record EmptyDocumentNodes(SkillReference reference)
            implements SkillDefinitionStoreRestoreFailure {
        public EmptyDocumentNodes {
            Objects.requireNonNull(reference, "reference");
        }
    }
}
