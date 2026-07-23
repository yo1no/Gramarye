package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

sealed interface SkillDefinitionStoreRestoreResult
        permits SkillDefinitionStoreRestoreResult.Restored,
                SkillDefinitionStoreRestoreResult.Rejected {
    record Restored(SkillDefinitionStore store) implements SkillDefinitionStoreRestoreResult {
        public Restored {
            Objects.requireNonNull(store, "store");
        }
    }

    record Rejected(SkillDefinitionStoreRestoreFailure failure)
            implements SkillDefinitionStoreRestoreResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
