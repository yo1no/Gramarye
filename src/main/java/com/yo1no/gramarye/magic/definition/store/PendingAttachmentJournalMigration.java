package com.yo1no.gramarye.magic.definition.store;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** One adjacent, immutable journal physical-schema migration edge. */
interface PendingAttachmentJournalMigrationStep {
    int fromVersion();

    default int toVersion() {
        return Math.addExact(fromVersion(), 1);
    }

    DataResult<PendingAttachmentJournalMigrationStepOutput> migrate(Dynamic<Tag> input);
}

final class PendingAttachmentJournalMigrationStepOutput {
    private final Dynamic<Tag> suppliedTree;
    private final Tag snapshot;

    PendingAttachmentJournalMigrationStepOutput(Dynamic<Tag> migratedTree) {
        this.suppliedTree = Objects.requireNonNull(migratedTree, "migratedTree");
        this.snapshot = Objects.requireNonNull(migratedTree.getValue(), "migratedTree value").copy();
    }

    Dynamic<Tag> migratedTree() {
        return new Dynamic<>(suppliedTree.getOps(), snapshot.copy());
    }

    boolean aliasesValue(Tag candidate) {
        return suppliedTree.getValue() == Objects.requireNonNull(candidate, "candidate");
    }

    @Override
    public String toString() {
        return "PendingAttachmentJournalMigrationStepOutput[ops="
                + suppliedTree.getOps().getClass().getSimpleName() + ']';
    }
}

/** Immutable sorted migration graph with one edge per from-version. */
final class PendingAttachmentJournalMigrationPlan {
    private static final PendingAttachmentJournalMigrationPlan EMPTY =
            new PendingAttachmentJournalMigrationPlan(List.of());

    private final List<PendingAttachmentJournalMigrationStep> steps;
    private final Map<Integer, PendingAttachmentJournalMigrationStep> byVersion;

    PendingAttachmentJournalMigrationPlan(
            List<? extends PendingAttachmentJournalMigrationStep> steps) {
        Objects.requireNonNull(steps, "steps");
        var ordered = new ArrayList<PendingAttachmentJournalMigrationStep>(steps.size());
        var indexed = new HashMap<Integer, PendingAttachmentJournalMigrationStep>();
        for (var step : steps) {
            Objects.requireNonNull(step, "step");
            var from = step.fromVersion();
            if (from < 0 || from == Integer.MAX_VALUE || step.toVersion() != from + 1) {
                throw new IllegalArgumentException(
                        "journal migrations must be non-negative adjacent edges");
            }
            if (indexed.putIfAbsent(from, step) != null) {
                throw new IllegalArgumentException("duplicate journal migration edge");
            }
            ordered.add(step);
        }
        ordered.sort(java.util.Comparator.comparingInt(
                PendingAttachmentJournalMigrationStep::fromVersion));
        this.steps = List.copyOf(ordered);
        this.byVersion = Map.copyOf(indexed);
    }

    static PendingAttachmentJournalMigrationPlan empty() {
        return EMPTY;
    }

    List<PendingAttachmentJournalMigrationStep> steps() {
        return steps;
    }

    Optional<PendingAttachmentJournalMigrationStep> stepFrom(int version) {
        return Optional.ofNullable(byVersion.get(version));
    }

    DataResult<PendingAttachmentJournalMigrationPlan> verifyCoverage(int currentVersion) {
        if (currentVersion < 0) {
            return DataResult.error(() -> "current journal schema must be non-negative");
        }
        for (var version = 0; version < currentVersion; version++) {
            var step = byVersion.get(version);
            if (step == null || step.toVersion() != version + 1) {
                var missing = version;
                return DataResult.error(() -> "missing journal migration edge from " + missing);
            }
        }
        return steps.size() == currentVersion
                ? DataResult.success(this)
                : DataResult.error(() -> "journal migration plan has an edge outside coverage");
    }
}

/** Applies already-strict-decoded legacy trees through adjacent NbtOps edges. */
final class PendingAttachmentJournalMigrator {
    private PendingAttachmentJournalMigrator() {
    }

    static PendingAttachmentJournalMigrationResult migrateTo(
            Dynamic<Tag> source,
            int sourceVersion,
            PendingAttachmentJournalMigrationPlan plan,
            int currentVersion) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(plan, "plan");
        if (source.getOps() != NbtOps.INSTANCE || sourceVersion < 0 || currentVersion < 0) {
            throw new IllegalArgumentException(
                    "journal migration requires non-negative versions and NbtOps.INSTANCE");
        }
        if (sourceVersion > currentVersion) {
            return rejected(PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA);
        }
        if (plan.verifyCoverage(currentVersion).result().isEmpty()) {
            return rejected(PendingAttachmentJournalFailure.Code.MISSING_MIGRATION_EDGE);
        }
        var currentValue = source.getValue().copy();
        var version = sourceVersion;
        var edgeIndex = 0;
        while (version < currentVersion) {
            var edge = plan.stepFrom(version);
            if (edge.isEmpty()) {
                return rejected(PendingAttachmentJournalFailure.Code.MISSING_MIGRATION_EDGE);
            }
            var input = new Dynamic<Tag>(NbtOps.INSTANCE, currentValue.copy());
            DataResult<PendingAttachmentJournalMigrationStepOutput> attempted;
            try {
                attempted = edge.orElseThrow().migrate(input);
            } catch (RuntimeException exception) {
                return new PendingAttachmentJournalMigrationResult.Rejected(
                        PendingAttachmentJournalFailure.exception(
                                PendingAttachmentJournalFailure.Code.MIGRATION_EXCEPTION,
                                edgeIndex,
                                exception));
            }
            if (attempted == null
                    || attempted.error().isPresent()
                            && attempted.resultOrPartial().isEmpty()) {
                return rejected(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL);
            }
            if (attempted.error().isPresent() || attempted.result().isEmpty()) {
                return rejected(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL);
            }
            var stepOutput = attempted.result().orElseThrow();
            if (stepOutput.aliasesValue(input.getValue())
                    || !input.getValue().equals(currentValue)) {
                return rejected(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL);
            }
            var output = stepOutput.migratedTree();
            if (output.getOps() != input.getOps()
                    || !(output.getValue() instanceof CompoundTag compound)
                    || !(compound.get(PendingAttachmentJournalSchema.VERSION)
                            instanceof IntTag outputVersion)
                    || outputVersion.getAsInt() != version + 1) {
                return rejected(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL);
            }
            currentValue = compound.copy();
            version++;
            edgeIndex++;
        }
        return new PendingAttachmentJournalMigrationResult.Migrated(
                new Dynamic<>(NbtOps.INSTANCE, currentValue), edgeIndex > 0);
    }

    private static PendingAttachmentJournalMigrationResult.Rejected rejected(
            PendingAttachmentJournalFailure.Code code) {
        return new PendingAttachmentJournalMigrationResult.Rejected(
                PendingAttachmentJournalFailure.simple(code));
    }
}

sealed interface PendingAttachmentJournalMigrationResult
        permits PendingAttachmentJournalMigrationResult.Migrated,
                PendingAttachmentJournalMigrationResult.Rejected {
    final class Migrated implements PendingAttachmentJournalMigrationResult {
        private final CompoundTag tree;
        private final boolean migrationApplied;

        Migrated(Dynamic<Tag> tree, boolean migrationApplied) {
            Objects.requireNonNull(tree, "tree");
            if (tree.getOps() != NbtOps.INSTANCE
                    || !(tree.getValue() instanceof CompoundTag compound)) {
                throw new IllegalArgumentException(
                        "migrated journal result requires a CompoundTag under NbtOps.INSTANCE");
            }
            this.tree = compound.copy();
            this.migrationApplied = migrationApplied;
        }

        Dynamic<Tag> tree() {
            return new Dynamic<>(NbtOps.INSTANCE, tree.copy());
        }

        boolean migrationApplied() {
            return migrationApplied;
        }

        @Override
        public String toString() {
            return "Migrated[ops=" + NbtOps.INSTANCE.getClass().getSimpleName()
                    + ", migrationApplied=" + migrationApplied + ']';
        }
    }

    record Rejected(PendingAttachmentJournalFailure failure)
            implements PendingAttachmentJournalMigrationResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

/** Sole production journal migration provider; V0 intentionally has no legacy edges. */
final class PendingAttachmentJournalMigrationPlans {
    private static final PendingAttachmentJournalMigrationPlan PRODUCTION =
            PendingAttachmentJournalMigrationPlan.empty();

    private PendingAttachmentJournalMigrationPlans() {
    }

    static PendingAttachmentJournalMigrationPlan production() {
        return PRODUCTION;
    }
}
