package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class SkillSavedDataCarrierMigratorTest {
    @Test
    void productionSchemaZeroUsesOneImmutableEmptyCoveredPlan() {
        var first = SkillSavedDataCarrierMigrationPlans.production();
        var second = SkillSavedDataCarrierMigrationPlans.production();

        assertSame(first, second);
        assertTrue(first.steps().isEmpty());
        assertTrue(first.verifyCoverage(0).result().isPresent());
        assertThrows(UnsupportedOperationException.class, () -> first.steps().clear());
    }

    @Test
    void planRejectsNullDuplicateNonAdjacentAndIncompleteCoverage() {
        var zero = step(0, tree -> DataResult.success(outputWithVersion(tree, 1)));
        assertThrows(NullPointerException.class,
                () -> new SkillSavedDataCarrierMigrationPlan(null));
        assertThrows(NullPointerException.class,
                () -> new SkillSavedDataCarrierMigrationPlan(
                        java.util.Arrays.asList(zero, null)));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSavedDataCarrierMigrationPlan(List.of(zero, zero)));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSavedDataCarrierMigrationPlan(List.of(new TestStep(
                        0, 2, ignored -> DataResult.error(() -> "unused")))));
        assertTrue(new SkillSavedDataCarrierMigrationPlan(List.of(zero))
                .verifyCoverage(2).error().isPresent());
    }

    @Test
    void currentNoOpPreservesExactOpaqueBytesAndDoesNotExposeThemToMigration() {
        var source = snapshot(0, new byte[] {10, 0}, new byte[] {11, 22, 33});

        var success = assertInstanceOf(
                SkillSavedDataCarrierMigrationResult.Success.class,
                SkillSavedDataCarrierMigrator.migrate(source));

        assertFalse(success.migrated());
        assertTrue(success.factReport().facts().isEmpty());
        assertArrayEquals(new byte[] {10, 0}, success.carrier().storeBlob().copyBytes());
        assertArrayEquals(new byte[] {11, 22, 33}, success.carrier().pending().copyBytes());
        assertFalse(source.copyTokenizedTree().toString().contains("11, 22, 33"));
    }

    @Test
    void legacyStepSeesOnlySentinelsAndReinsertsBothBlobsByteExactly() {
        var store = new byte[] {10, 0};
        var pending = new byte[] {91, 92, 93, 94};
        var calls = new AtomicInteger();
        var zero = step(0, tree -> {
            calls.incrementAndGet();
            var root = (CompoundTag) tree.getValue();
            assertInstanceOf(CompoundTag.class,
                    root.get(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD));
            assertInstanceOf(CompoundTag.class,
                    root.get(SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD));
            assertTrue(root.getAllKeys().stream().noneMatch(key ->
                    root.get(key) instanceof net.minecraft.nbt.ByteArrayTag));
            return DataResult.success(outputWithVersion(tree, 1));
        });

        var success = assertInstanceOf(
                SkillSavedDataCarrierMigrationResult.Success.class,
                SkillSavedDataCarrierMigrator.migrateTo(
                        snapshot(0, store, pending),
                        new SkillSavedDataCarrierMigrationPlan(List.of(zero)),
                        1));

        assertEquals(1, calls.get());
        assertTrue(success.migrated());
        assertArrayEquals(store, success.carrier().storeBlob().copyBytes());
        assertArrayEquals(pending, success.carrier().pending().copyBytes());
        assertEquals(
                List.of(SkillMigrationFactCode.SAVED_DATA_STEP_APPLIED),
                success.factReport().facts().stream().map(fact -> fact.code()).toList());
    }

    @Test
    void futureMissingPartialFailureAndRuntimeExceptionAreTypedAndErrorEscapes() {
        assertFailure(
                SkillSavedDataCarrierMigrator.migrateTo(
                        snapshot(1, new byte[] {10, 0}, new byte[0]),
                        SkillSavedDataCarrierMigrationPlan.empty(),
                        0),
                SkillSavedDataCarrierMigrationFailure.Code.FUTURE_SCHEMA_VERSION);
        assertFailure(
                SkillSavedDataCarrierMigrator.migrateTo(
                        snapshot(0, new byte[] {10, 0}, new byte[0]),
                        SkillSavedDataCarrierMigrationPlan.empty(),
                        1),
                SkillSavedDataCarrierMigrationFailure.Code.MISSING_MIGRATION_EDGE);

        var partial = step(0, tree -> DataResult.error(
                () -> "partial", outputWithVersion(tree, 1)));
        assertFailure(migrateOne(partial),
                SkillSavedDataCarrierMigrationFailure.Code.STEP_RETURNED_PARTIAL);

        var failed = step(0, ignored -> DataResult.error(() -> "failed"));
        assertFailure(migrateOne(failed),
                SkillSavedDataCarrierMigrationFailure.Code.STEP_FAILED);

        var thrown = step(0, ignored -> {
            throw new IllegalStateException("secret-message");
        });
        var thrownFailure = assertFailure(
                migrateOne(thrown),
                SkillSavedDataCarrierMigrationFailure.Code.STEP_THREW_EXCEPTION);
        assertEquals(
                IllegalStateException.class.getName(),
                thrownFailure.exceptionClassName().orElseThrow());
        assertFalse(thrownFailure.toString().contains("secret-message"));

        var fatal = step(0, ignored -> {
            throw new AssertionError("fatal");
        });
        assertThrows(AssertionError.class, () -> migrateOne(fatal));
    }

    @Test
    void tokenDeletionExchangeUnknownAndExtraCurrentFieldFailClosed() {
        var delete = step(0, tree -> {
            var root = ((CompoundTag) tree.getValue()).copy();
            root.remove(SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD);
            root.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 1);
            return DataResult.success(output(tree, root));
        });
        var exchange = step(0, tree -> {
            var root = ((CompoundTag) tree.getValue()).copy();
            var store = root.get(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD).copy();
            root.put(
                    SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD,
                    root.get(SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD).copy());
            root.put(SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD, store);
            root.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 1);
            return DataResult.success(output(tree, root));
        });
        var unknown = step(0, tree -> {
            var root = ((CompoundTag) tree.getValue()).copy();
            ((CompoundTag) root.get(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD))
                    .putInt(OpaqueSavedDataBlobTokens.TOKEN_FIELD, 99);
            root.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 1);
            return DataResult.success(output(tree, root));
        });
        var extra = step(0, tree -> {
            var root = ((CompoundTag) tree.getValue()).copy();
            root.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 1);
            root.putInt("unexpected", 1);
            return DataResult.success(output(tree, root));
        });

        for (var invalid : List.of(delete, exchange, unknown, extra)) {
            assertFailure(
                    migrateOne(invalid),
                    SkillSavedDataCarrierMigrationFailure.Code
                            .OPAQUE_TOKEN_INVARIANT_VIOLATION);
        }
    }

    @Test
    void changedOpsAndWrongOutputVersionFailClosed() {
        var provider = HolderLookup.Provider.create(Stream.empty());
        var changedOps = step(0, source -> {
            var root = ((CompoundTag) source.getValue()).copy();
            root.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, 1);
            return DataResult.success(new SkillSavedDataCarrierMigrationStepOutput(
                    new Dynamic<Tag>(
                            RegistryOps.create(NbtOps.INSTANCE, provider), root)));
        });
        var wrongVersion = step(0, source ->
                DataResult.success(outputWithVersion(source, 7)));

        assertFailure(
                migrateOne(changedOps),
                SkillSavedDataCarrierMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS);
        assertFailure(
                migrateOne(wrongVersion),
                SkillSavedDataCarrierMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH);
    }

    @Test
    void failedMutatingStepCannotChangeTheOriginalSnapshot() {
        var source = snapshot(0, new byte[] {10, 0}, new byte[] {4, 2});
        var mutating = step(0, tree -> {
            ((CompoundTag) tree.getValue()).putString("secret", "mutated");
            return DataResult.error(() -> "failed");
        });

        assertFailure(
                SkillSavedDataCarrierMigrator.migrateTo(
                        source,
                        new SkillSavedDataCarrierMigrationPlan(List.of(mutating)),
                        1),
                SkillSavedDataCarrierMigrationFailure.Code.STEP_FAILED);
        assertTrue(source.copyTokenizedTree().get("secret") == null);
    }

    private static SkillSavedDataCarrierMigrationResult migrateOne(
            SkillSavedDataCarrierMigrationStep step) {
        return SkillSavedDataCarrierMigrator.migrateTo(
                snapshot(0, new byte[] {10, 0}, new byte[] {1, 2}),
                new SkillSavedDataCarrierMigrationPlan(List.of(step)),
                1);
    }

    private static SkillSavedDataCarrierMigrationFailure assertFailure(
            SkillSavedDataCarrierMigrationResult result,
            SkillSavedDataCarrierMigrationFailure.Code code) {
        var failure = assertInstanceOf(
                SkillSavedDataCarrierMigrationResult.Failure.class, result).failure();
        assertEquals(code, failure.code());
        return failure;
    }

    private static TokenizedSavedDataCarrierSnapshot snapshot(
            int version,
            byte[] store,
            byte[] pending) {
        return OpaqueSavedDataBlobTokens.tokenize(
                version,
                ImmutableStoreBlob.copyOf(store),
                OpaquePendingAttachmentUpdatesBlob.capture(pending));
    }

    private static SkillSavedDataCarrierMigrationStep step(
            int from,
            Function<Dynamic<Tag>, DataResult<SkillSavedDataCarrierMigrationStepOutput>> behavior) {
        return new TestStep(from, from + 1, behavior);
    }

    private static SkillSavedDataCarrierMigrationStepOutput outputWithVersion(
            Dynamic<Tag> source,
            int version) {
        var copied = ((CompoundTag) source.getValue()).copy();
        copied.putInt(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD, version);
        return output(source, copied);
    }

    private static SkillSavedDataCarrierMigrationStepOutput output(
            Dynamic<Tag> source,
            CompoundTag tree) {
        return new SkillSavedDataCarrierMigrationStepOutput(
                new Dynamic<Tag>(source.getOps(), tree));
    }

    private record TestStep(
            int fromVersion,
            int toVersion,
            Function<Dynamic<Tag>, DataResult<SkillSavedDataCarrierMigrationStepOutput>> behavior)
            implements SkillSavedDataCarrierMigrationStep {
        private TestStep {
            java.util.Objects.requireNonNull(behavior, "behavior");
        }

        @Override
        public DataResult<SkillSavedDataCarrierMigrationStepOutput> migrate(
                Dynamic<Tag> defensiveSourceCopy) {
            return behavior.apply(defensiveSourceCopy);
        }
    }
}
