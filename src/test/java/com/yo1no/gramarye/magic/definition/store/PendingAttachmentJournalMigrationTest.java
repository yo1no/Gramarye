package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class PendingAttachmentJournalMigrationTest {
    @Test
    void productionV0PlanIsUniqueEmptyAndCovered() {
        var first = PendingAttachmentJournalMigrationPlans.production();
        var second = PendingAttachmentJournalMigrationPlans.production();

        assertSame(first, second);
        assertTrue(first.steps().isEmpty());
        assertTrue(first.verifyCoverage(0).result().isPresent());
    }

    @Test
    void planRejectsNullDuplicateAndNonAdjacentEdges() {
        var step = step(0, input -> DataResult.success(output(input, 1)));

        assertThrows(NullPointerException.class,
                () -> new PendingAttachmentJournalMigrationPlan(null));
        assertThrows(IllegalArgumentException.class,
                () -> new PendingAttachmentJournalMigrationPlan(List.of(step, step)));
        assertThrows(IllegalArgumentException.class,
                () -> new PendingAttachmentJournalMigrationPlan(List.of(
                        new TestStep(0, 2, ignored -> DataResult.error(() -> "unused")))));
    }

    @Test
    void adjacentSyntheticLegacyEdgesUseSameOpsAndFreshTrees() {
        var calls = new AtomicInteger();
        var zero = step(0, input -> {
            assertSame(NbtOps.INSTANCE, input.getOps());
            assertEquals(0, calls.getAndIncrement());
            return DataResult.success(output(input, 1));
        });
        var one = step(1, input -> {
            assertEquals(1, calls.getAndIncrement());
            return DataResult.success(output(input, 2));
        });
        var source = root(0);

        var migrated = assertInstanceOf(
                PendingAttachmentJournalMigrationResult.Migrated.class,
                PendingAttachmentJournalMigrator.migrateTo(
                        new Dynamic<Tag>(NbtOps.INSTANCE, source),
                        0,
                        new PendingAttachmentJournalMigrationPlan(List.of(one, zero)),
                        2));

        assertEquals(2, calls.get());
        assertTrue(migrated.migrationApplied());
        assertEquals(2, ((CompoundTag) migrated.tree().getValue())
                .getInt(PendingAttachmentJournalSchema.VERSION));
        assertEquals(0, source.getInt(PendingAttachmentJournalSchema.VERSION));
    }

    @Test
    void migrationOutputAndResultAccessorsAreDefensiveAndAliasIsRejected() {
        var source = new Dynamic<Tag>(NbtOps.INSTANCE, root(0));
        var stepOutput = output(source, 1);
        var firstStepTree = (CompoundTag) stepOutput.migratedTree().getValue();
        firstStepTree.putInt("mutated", 1);
        assertFalse(((CompoundTag) stepOutput.migratedTree().getValue()).contains("mutated"));

        assertCode(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL,
                migrateOne(step(0, input -> DataResult.success(
                        new PendingAttachmentJournalMigrationStepOutput(input)))));
        assertCode(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL,
                migrateOne(step(0, input -> {
                    ((CompoundTag) input.getValue()).putInt("in_place", 1);
                    return DataResult.success(output(input, 1));
                })));

        var migrated = assertInstanceOf(
                PendingAttachmentJournalMigrationResult.Migrated.class,
                migrateOne(step(0, input -> DataResult.success(output(input, 1)))));
        var firstResultTree = (CompoundTag) migrated.tree().getValue();
        var secondResultTree = (CompoundTag) migrated.tree().getValue();
        assertNotSame(firstResultTree, secondResultTree);
        firstResultTree.putInt("mutated", 1);
        assertFalse(((CompoundTag) migrated.tree().getValue()).contains("mutated"));
    }

    @Test
    void migratorRequiresExactPlanCoverage() {
        var zero = step(0, input -> DataResult.success(output(input, 1)));
        var outside = step(2, input -> DataResult.success(output(input, 3)));

        assertCode(PendingAttachmentJournalFailure.Code.MISSING_MIGRATION_EDGE,
                PendingAttachmentJournalMigrator.migrateTo(
                        new Dynamic<Tag>(NbtOps.INSTANCE, root(0)),
                        0,
                        new PendingAttachmentJournalMigrationPlan(List.of(zero, outside)),
                        1));
    }

    @Test
    void syntheticLegacyBytesAreStrictlyScannedBeforeFiniteMaterialization()
            throws Exception {
        var materializations = new AtomicInteger();
        var sourceBytes = legacyBytes(false, false, false);
        var legacy = strictLegacyMaterialize(sourceBytes, materializations);
        var migrated = assertInstanceOf(
                PendingAttachmentJournalMigrationResult.Migrated.class,
                PendingAttachmentJournalMigrator.migrateTo(
                        legacy,
                        0,
                        new PendingAttachmentJournalMigrationPlan(List.of(
                                step(0, input -> DataResult.success(output(input, 1))))),
                        1));

        assertEquals(1, materializations.get());
        assertSyntheticCurrentV1((CompoundTag) migrated.tree().getValue());

        for (var rejected : List.of(
                legacyBytes(true, false, false),
                legacyBytes(false, true, false),
                legacyBytes(false, false, true))) {
            assertThrows(IllegalArgumentException.class,
                    () -> strictLegacyMaterialize(rejected, materializations));
        }
        assertEquals(1, materializations.get());
    }

    @Test
    void currentNoOpMissingPartialRuntimeAndErrorPoliciesAreTyped() {
        var current = assertInstanceOf(
                PendingAttachmentJournalMigrationResult.Migrated.class,
                PendingAttachmentJournalMigrator.migrateTo(
                        new Dynamic<Tag>(NbtOps.INSTANCE, root(0)),
                        0,
                        PendingAttachmentJournalMigrationPlan.empty(),
                        0));
        assertFalse(current.migrationApplied());

        assertCode(PendingAttachmentJournalFailure.Code.MISSING_MIGRATION_EDGE,
                PendingAttachmentJournalMigrator.migrateTo(
                        new Dynamic<Tag>(NbtOps.INSTANCE, root(0)),
                        0,
                        PendingAttachmentJournalMigrationPlan.empty(),
                        1));
        assertCode(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL,
                migrateOne(step(0, ignored -> DataResult.error(
                        () -> "partial", output(new Dynamic<Tag>(NbtOps.INSTANCE, root(0)), 1)))));
        assertCode(PendingAttachmentJournalFailure.Code.MIGRATION_PARTIAL,
                migrateOne(step(0, ignored -> null)));

        var runtime = assertInstanceOf(
                PendingAttachmentJournalMigrationResult.Rejected.class,
                migrateOne(step(0, ignored -> {
                    throw new IllegalStateException("secret-message");
                })));
        assertEquals(PendingAttachmentJournalFailure.Code.MIGRATION_EXCEPTION,
                runtime.failure().code());
        assertEquals(IllegalStateException.class.getName(),
                runtime.failure().exceptionClassName().orElseThrow());
        assertFalse(runtime.failure().toString().contains("secret-message"));

        assertThrows(AssertionError.class, () -> migrateOne(step(0, ignored -> {
            throw new AssertionError("not caught");
        })));
    }

    private static PendingAttachmentJournalMigrationResult migrateOne(
            PendingAttachmentJournalMigrationStep step) {
        return PendingAttachmentJournalMigrator.migrateTo(
                new Dynamic<Tag>(NbtOps.INSTANCE, root(0)),
                0,
                new PendingAttachmentJournalMigrationPlan(List.of(step)),
                1);
    }

    private static void assertCode(
            PendingAttachmentJournalFailure.Code code,
            PendingAttachmentJournalMigrationResult result) {
        assertEquals(code, assertInstanceOf(
                PendingAttachmentJournalMigrationResult.Rejected.class, result)
                .failure().code());
    }

    private static PendingAttachmentJournalMigrationStep step(
            int from,
            Function<Dynamic<Tag>, DataResult<PendingAttachmentJournalMigrationStepOutput>> body) {
        return new TestStep(from, from + 1, body);
    }

    private static PendingAttachmentJournalMigrationStepOutput output(
            Dynamic<Tag> input, int version) {
        var copy = ((CompoundTag) input.getValue()).copy();
        copy.putInt(PendingAttachmentJournalSchema.VERSION, version);
        return new PendingAttachmentJournalMigrationStepOutput(
                new Dynamic<Tag>(input.getOps(), copy));
    }

    private static CompoundTag root(int version) {
        var tag = new CompoundTag();
        tag.putInt(PendingAttachmentJournalSchema.VERSION, version);
        return tag;
    }

    private static byte[] legacyBytes(
            boolean duplicateVersion,
            boolean wrongPayloadType,
            boolean trailing) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF(PendingAttachmentJournalSchema.VERSION);
            output.writeInt(0);
            if (duplicateVersion) {
                output.writeByte(Tag.TAG_INT);
                output.writeUTF(PendingAttachmentJournalSchema.VERSION);
                output.writeInt(0);
            }
            output.writeByte(wrongPayloadType ? Tag.TAG_STRING : Tag.TAG_INT);
            output.writeUTF("legacy_payload");
            if (wrongPayloadType) {
                output.writeUTF("wrong");
            } else {
                output.writeInt(7);
            }
            output.writeByte(Tag.TAG_END);
            if (trailing) {
                output.writeByte(0);
            }
        }
        return bytes.toByteArray();
    }

    private static Dynamic<Tag> strictLegacyMaterialize(
            byte[] bytes,
            AtomicInteger materializations) throws Exception {
        if (!(PendingAttachmentJournalWireScan.scan(bytes)
                instanceof PendingAttachmentJournalWireScan.Result.Scanned scanned)
                || !scanned.schemaSeen()
                || scanned.schemaTagType() != Tag.TAG_INT
                || scanned.schemaVersion() != 0) {
            throw new IllegalArgumentException("synthetic legacy framing rejected");
        }
        var cursor = new PendingAttachmentJournalCursor(bytes);
        if (cursor.readUnsignedByte() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("synthetic legacy root type");
        }
        var fields = new HashSet<String>();
        var versionSeen = false;
        var payloadSeen = false;
        while (true) {
            var type = cursor.readUnsignedByte();
            if (type == Tag.TAG_END) {
                break;
            }
            var name = cursor.readModifiedUtf();
            if (!fields.add(name)) {
                throw new IllegalArgumentException("synthetic legacy duplicate");
            }
            if (PendingAttachmentJournalSchema.VERSION.equals(name)) {
                if (type != Tag.TAG_INT || cursor.readInt() != 0) {
                    throw new IllegalArgumentException("synthetic legacy version");
                }
                versionSeen = true;
            } else if ("legacy_payload".equals(name)) {
                if (type != Tag.TAG_INT) {
                    throw new IllegalArgumentException("synthetic legacy payload type");
                }
                cursor.readInt();
                payloadSeen = true;
            } else {
                throw new IllegalArgumentException("synthetic legacy unknown field");
            }
        }
        if (!versionSeen || !payloadSeen || !cursor.finished()) {
            throw new IllegalArgumentException("synthetic legacy incomplete");
        }

        materializations.incrementAndGet();
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var value = NbtIo.readAnyTag(input, new NbtAccounter(1_048_576, 64));
            if (!(value instanceof CompoundTag) || input.read() != -1) {
                throw new IllegalArgumentException("synthetic legacy finite read failed");
            }
            return new Dynamic<>(NbtOps.INSTANCE, value.copy());
        }
    }

    private static void assertSyntheticCurrentV1(CompoundTag tree) {
        assertEquals(2, tree.size());
        assertEquals(1, tree.getInt(PendingAttachmentJournalSchema.VERSION));
        assertEquals(7, tree.getInt("legacy_payload"));
    }

    private record TestStep(
            int fromVersion,
            int toVersion,
            Function<Dynamic<Tag>, DataResult<PendingAttachmentJournalMigrationStepOutput>> body)
            implements PendingAttachmentJournalMigrationStep {
        private TestStep {
            java.util.Objects.requireNonNull(body, "body");
        }

        @Override
        public DataResult<PendingAttachmentJournalMigrationStepOutput> migrate(
                Dynamic<Tag> input) {
            return body.apply(input);
        }
    }
}
