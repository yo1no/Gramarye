package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.Objects;

sealed interface StorePersistenceEncodeResult
        permits StorePersistenceEncodeResult.Success, StorePersistenceEncodeResult.Failure {
    record Success(ImmutableStoreBlob blob) implements StorePersistenceEncodeResult {
        public Success {
            Objects.requireNonNull(blob, "blob");
        }
    }

    record Failure(StorePersistenceFailure failure) implements StorePersistenceEncodeResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

sealed interface StorePersistenceLoadResult
        permits StorePersistenceLoadResult.Loaded, StorePersistenceLoadResult.Failure {
    record Loaded(
            SkillDefinitionStore store,
            PipelineFactReport factReport,
            boolean rewritePending) implements StorePersistenceLoadResult {
        public Loaded {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(factReport, "factReport");
        }
    }

    record Failure(
            StorePersistenceFailure failure,
            PipelineFactReport factReport) implements StorePersistenceLoadResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(factReport, "factReport");
        }
    }
}
