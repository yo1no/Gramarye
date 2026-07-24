package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

sealed interface StorePersistenceMigrationResult
        permits StorePersistenceMigrationResult.Success, StorePersistenceMigrationResult.Failure {
    record Success(CompoundTag migratedTree, PipelineFactReport factReport, boolean migrated)
            implements StorePersistenceMigrationResult {
        public Success {
            migratedTree = Objects.requireNonNull(migratedTree, "migratedTree").copy();
            Objects.requireNonNull(factReport, "factReport");
        }

        @Override
        public CompoundTag migratedTree() {
            return migratedTree.copy();
        }

        @Override
        public String toString() {
            return "Success[migrated=" + migrated
                    + ", factCount=" + factReport.facts().size()
                    + ", factsTruncated=" + factReport.truncated() + "]";
        }
    }

    record Failure(
            StorePersistenceMigrationFailure failure,
            PipelineFactReport factReport) implements StorePersistenceMigrationResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(factReport, "factReport");
        }

        @Override
        public String toString() {
            return "Failure[code=" + failure.code()
                    + ", factCount=" + factReport.facts().size()
                    + ", factsTruncated=" + factReport.truncated() + "]";
        }
    }
}
