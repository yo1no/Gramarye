package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.migration.SkillMigrationFactCode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class StorePersistenceMigratorTest {
    @Test
    void productionSchemaZeroHasOneEmptyCoveredPlan() {
        var first = StorePersistenceMigrationPlans.production();
        var second = StorePersistenceMigrationPlans.production();

        assertSame(first, second);
        assertTrue(first.steps().isEmpty());
        assertTrue(first.verifyCoverage(StorePersistenceSchema.CURRENT_SCHEMA_VERSION)
                .result().isPresent());
    }

    @Test
    void planRejectsNullDuplicateAndNonAdjacentEdges() {
        var zero = step(0, tree -> DataResult.success(outputWithVersion(tree, 1)));
        assertThrows(NullPointerException.class,
                () -> new StorePersistenceMigrationPlan(null));
        assertThrows(NullPointerException.class,
                () -> new StorePersistenceMigrationPlan(java.util.Arrays.asList(zero, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new StorePersistenceMigrationPlan(List.of(zero, zero)));
        assertThrows(IllegalArgumentException.class,
                () -> new StorePersistenceMigrationPlan(List.of(new TestStep(
                        0, 2, ignored -> DataResult.error(() -> "unused")))));
        assertTrue(new StorePersistenceMigrationPlan(List.of(zero))
                .verifyCoverage(2).error().isPresent());
    }

    @Test
    void currentNoOpAndFutureFailurePreserveInput() {
        var current = root(0);
        current.putString("secret", "do-not-log-this-tree");
        var success = assertInstanceOf(StorePersistenceMigrationResult.Success.class,
                StorePersistenceMigrator.migrate(current, StorePersistenceMigrationPlan.empty()));
        var future = assertInstanceOf(StorePersistenceMigrationResult.Failure.class,
                StorePersistenceMigrator.migrate(root(1), StorePersistenceMigrationPlan.empty()));

        current.putInt("after", 1);
        assertTrue(success.migratedTree().get("after") == null);
        assertTrue(success.factReport().facts().isEmpty());
        assertTrue(success.toString().contains("do-not-log-this-tree") == false);
        assertTrue(new StorePersistenceMigrationStepOutput(
                        new Dynamic<Tag>(NbtOps.INSTANCE, current))
                .toString().contains("do-not-log-this-tree") == false);
        assertEquals(StorePersistenceMigrationFailure.Code.FUTURE_SCHEMA_VERSION,
                future.failure().code());
    }

    @Test
    void adjacentStepsRunOnceInOrderAndEmitStoreFacts() {
        var calls = new AtomicInteger();
        var zero = step(0, tree -> {
            assertEquals(0, calls.getAndIncrement());
            return DataResult.success(outputWithVersion(tree, 1));
        });
        var one = step(1, tree -> {
            assertEquals(1, calls.getAndIncrement());
            return DataResult.success(outputWithVersion(tree, 2));
        });

        var result = assertInstanceOf(StorePersistenceMigrationResult.Success.class,
                StorePersistenceMigrator.migrateTo(
                        root(0), new StorePersistenceMigrationPlan(List.of(one, zero)), 2));

        assertEquals(2, calls.get());
        assertEquals(2, result.migratedTree().getInt("store_schema_version"));
        assertEquals(List.of(
                        SkillMigrationFactCode.STORE_STEP_APPLIED,
                        SkillMigrationFactCode.STORE_STEP_APPLIED),
                result.factReport().facts().stream().map(fact -> fact.code()).toList());
        assertTrue(result.migrated());
    }

    @Test
    void missingPartialErrorExceptionOpsAndOutputVersionAreTyped() {
        assertFailure(
                StorePersistenceMigrator.migrateTo(root(0), StorePersistenceMigrationPlan.empty(), 1),
                StorePersistenceMigrationFailure.Code.MISSING_MIGRATION_EDGE);

        var partial = step(0, tree -> DataResult.error(
                () -> "partial", outputWithVersion(tree, 1)));
        assertFailure(migrateOne(partial),
                StorePersistenceMigrationFailure.Code.STEP_RETURNED_PARTIAL);

        var failed = step(0, ignored -> DataResult.error(() -> "failed"));
        assertFailure(migrateOne(failed), StorePersistenceMigrationFailure.Code.STEP_FAILED);

        var thrown = step(0, ignored -> {
            throw new IllegalStateException("secret-message");
        });
        var thrownFailure = assertFailure(
                migrateOne(thrown), StorePersistenceMigrationFailure.Code.STEP_THREW_EXCEPTION);
        assertEquals(IllegalStateException.class.getName(),
                thrownFailure.exceptionClassName().orElseThrow());
        assertTrue(thrownFailure.toString().contains("secret-message") == false);

        var wrongVersion = step(0, tree -> DataResult.success(outputWithVersion(tree, 7)));
        assertFailure(migrateOne(wrongVersion),
                StorePersistenceMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH);
    }

    @Test
    void changedOpsIsRejectedEvenWhenTheNbtFamilyIsUnchanged() {
        var provider = HolderLookup.Provider.create(Stream.empty());
        var changedOps = step(0, source -> {
            var output = outputWithVersion(source, 1).migratedTree();
            return DataResult.success(new StorePersistenceMigrationStepOutput(
                    new Dynamic<Tag>(RegistryOps.create(NbtOps.INSTANCE, provider),
                            output.getValue())));
        });

        assertFailure(migrateOne(changedOps),
                StorePersistenceMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS);
    }

    @Test
    void failedStepMutationCannotChangeCallerOrFailureOriginalSnapshot() {
        var source = root(0);
        var mutatingFailure = step(0, input -> {
            ((CompoundTag) input.getValue()).putString("secret", "mutated");
            return DataResult.error(() -> "failed");
        });

        var failure = assertInstanceOf(StorePersistenceMigrationResult.Failure.class,
                StorePersistenceMigrator.migrateTo(
                        source, new StorePersistenceMigrationPlan(List.of(mutatingFailure)), 1));

        assertTrue(source.get("secret") == null);
        assertTrue(failure.toString().contains("secret") == false);
    }

    @Test
    void factsStopAtHardCapAndReportTruncationWithoutFailingMigration() {
        var count = com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_PIPELINE_FACTS + 1;
        var steps = IntStream.range(0, count)
                .mapToObj(version -> step(version, source ->
                        DataResult.success(outputWithVersion(source, version + 1))))
                .toList();

        var success = assertInstanceOf(StorePersistenceMigrationResult.Success.class,
                StorePersistenceMigrator.migrateTo(
                        root(0), new StorePersistenceMigrationPlan(steps), count));

        assertEquals(
                com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_PIPELINE_FACTS,
                success.factReport().facts().size());
        assertTrue(success.factReport().truncated());
    }

    @Test
    void errorsAreNotCaught() {
        var step = step(0, ignored -> {
            throw new AssertionError("test error");
        });
        assertThrows(AssertionError.class, () -> migrateOne(step));
    }

    private static StorePersistenceMigrationResult migrateOne(StorePersistenceMigrationStep step) {
        return StorePersistenceMigrator.migrateTo(
                root(0), new StorePersistenceMigrationPlan(List.of(step)), 1);
    }

    private static StorePersistenceMigrationFailure assertFailure(
            StorePersistenceMigrationResult result,
            StorePersistenceMigrationFailure.Code code) {
        var failure = assertInstanceOf(StorePersistenceMigrationResult.Failure.class, result)
                .failure();
        assertEquals(code, failure.code());
        return failure;
    }

    private static StorePersistenceMigrationStep step(
            int from,
            Function<Dynamic<Tag>, DataResult<StorePersistenceMigrationStepOutput>> behavior) {
        return new TestStep(from, from + 1, behavior);
    }

    private static StorePersistenceMigrationStepOutput outputWithVersion(
            Dynamic<Tag> source,
            int version) {
        var copied = ((CompoundTag) source.getValue()).copy();
        copied.putInt("store_schema_version", version);
        return new StorePersistenceMigrationStepOutput(
                new Dynamic<Tag>(source.getOps(), copied));
    }

    private static CompoundTag root(int version) {
        var root = new CompoundTag();
        root.putInt("store_schema_version", version);
        root.put("history_entries", new net.minecraft.nbt.ListTag());
        return root;
    }

    private record TestStep(
            int fromVersion,
            int toVersion,
            Function<Dynamic<Tag>, DataResult<StorePersistenceMigrationStepOutput>> behavior)
            implements StorePersistenceMigrationStep {
        private TestStep {
            java.util.Objects.requireNonNull(behavior, "behavior");
        }

        @Override
        public DataResult<StorePersistenceMigrationStepOutput> migrate(
                Dynamic<Tag> defensiveSourceCopy) {
            return behavior.apply(defensiveSourceCopy);
        }
    }
}
