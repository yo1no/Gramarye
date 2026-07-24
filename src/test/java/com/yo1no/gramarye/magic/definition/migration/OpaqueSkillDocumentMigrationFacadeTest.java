package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.TokenizedSkillDocumentMigrationInput;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class OpaqueSkillDocumentMigrationFacadeTest {
    @Test
    void migratedOutputHandleSnapshotsSourceAndAccessorAndHasBoundedSummary() throws Exception {
        var encoded = encodedDocument(0);
        var expected = encoded.clone();
        var handle = OpaqueSkillDocumentMigrationFacade.MigratedTokenizedDocument.copyOf(encoded);
        encoded[0] ^= 1;
        var firstCopy = handle.copyBytes();
        firstCopy[0] ^= 1;
        var secondCopy = handle.copyBytes();

        assertArrayEquals(expected, secondCopy);
        assertNotSame(firstCopy, secondCopy);
        assertEquals(expected.length, handle.byteCount());
        assertEquals(
                "MigratedTokenizedDocument[byteCount=" + expected.length + "]",
                handle.toString());
        assertFalse(handle.toString().contains("schema_version"));
        assertThrows(
                IllegalArgumentException.class,
                () -> OpaqueSkillDocumentMigrationFacade.MigratedTokenizedDocument.copyOf(
                        new byte[0]));
        assertEquals(
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES,
                OpaqueSkillDocumentMigrationFacade.MigratedTokenizedDocument.copyOf(
                        new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES]).byteCount());
        assertThrows(
                IllegalArgumentException.class,
                () -> OpaqueSkillDocumentMigrationFacade.MigratedTokenizedDocument.copyOf(
                        new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1]));
    }

    @Test
    void currentSchemaUsesProductionZeroStepPlan() throws Exception {
        var source = encodedDocument(0);

        var success = assertInstanceOf(
                OpaqueSkillDocumentMigrationFacade.Success.class,
                OpaqueSkillDocumentMigrationFacade.migrateTo(
                        source,
                        SkillMigrationPlans.production(),
                        0));

        assertFalse(success.migrated());
        assertTrue(success.factReport().facts().isEmpty());
        assertEquals(decoded(source), decoded(success.migratedDocument().copyBytes()));
    }

    @Test
    void adjacentFixtureStepsRunOnceInOrderAndReturnBoundedFacts() throws Exception {
        var order = new ArrayList<Integer>();
        var first = updatingStep(0, order);
        var second = updatingStep(1, order);

        var success = assertInstanceOf(
                OpaqueSkillDocumentMigrationFacade.Success.class,
                OpaqueSkillDocumentMigrationFacade.migrateTo(
                        encodedDocument(0),
                        new SkillMigrationPlan(List.of(second, first)),
                        2));

        assertTrue(success.migrated());
        assertEquals(List.of(0, 1), order);
        assertEquals(1, first.calls());
        assertEquals(1, second.calls());
        assertEquals(2, decoded(success.migratedDocument().copyBytes()).getInt("schema_version"));
        assertEquals(
                List.of(SkillMigrationFactCode.STEP_APPLIED, SkillMigrationFactCode.STEP_APPLIED),
                success.factReport().facts().stream().map(SkillMigrationFact::code).toList());
    }

    @Test
    void malformedFutureMissingPartialAndCompleteFailureAreTyped() throws Exception {
        var malformedBytes = Arrays.copyOf(encodedDocument(0), encodedDocument(0).length + 1);
        malformedBytes[malformedBytes.length - 1] = 99;
        var malformed = assertInstanceOf(
                OpaqueSkillDocumentMigrationFacade.Failure.class,
                OpaqueSkillDocumentMigrationFacade.migrateTo(
                        malformedBytes, SkillMigrationPlans.production(), 0));
        var future = migrationFailure(OpaqueSkillDocumentMigrationFacade.migrateTo(
                encodedDocument(2), SkillMigrationPlan.empty(), 1));
        var missing = migrationFailure(OpaqueSkillDocumentMigrationFacade.migrateTo(
                encodedDocument(0), SkillMigrationPlan.empty(), 1));
        var partial = migrationFailure(OpaqueSkillDocumentMigrationFacade.migrateTo(
                encodedDocument(0),
                new SkillMigrationPlan(List.of(step(0, input -> DataResult.error(
                        () -> "partial-secret",
                        new SkillMigrationStepOutput(withVersion(input, 1)))))),
                1));
        var complete = migrationFailure(OpaqueSkillDocumentMigrationFacade.migrateTo(
                encodedDocument(0),
                new SkillMigrationPlan(List.of(step(
                        0, input -> DataResult.error(() -> "complete-secret")))),
                1));

        assertEquals(
                OpaqueSkillDocumentMigrationFacade.FailureCode.MALFORMED_TOKENIZED_DOCUMENT,
                malformed.code());
        assertTrue(malformed.migrationFailure().isEmpty());
        assertEquals(SkillMigrationFailure.Code.FUTURE_SCHEMA_VERSION, future.code());
        assertEquals(SkillMigrationFailure.Code.MISSING_MIGRATION_EDGE, missing.code());
        assertEquals(SkillMigrationFailure.Code.STEP_RETURNED_PARTIAL, partial.code());
        assertEquals(SkillMigrationFailure.Code.STEP_FAILED, complete.code());
        assertFalse(partial.result().toString().contains("partial-secret"));
        assertFalse(complete.result().toString().contains("complete-secret"));
    }

    @Test
    void runtimeExceptionIsClassOnlyErrorEscapesAndOpsChangeFails() throws Exception {
        var runtime = migrationFailure(OpaqueSkillDocumentMigrationFacade.migrateTo(
                encodedDocument(0),
                new SkillMigrationPlan(List.of(step(0, input -> {
                    throw new IllegalStateException("runtime-secret");
                }))),
                1));
        var changedOps = migrationFailure(OpaqueSkillDocumentMigrationFacade.migrateTo(
                encodedDocument(0),
                new SkillMigrationPlan(List.of(step(0, input -> {
                    var root = new JsonObject();
                    root.addProperty("schema_version", 1);
                    return DataResult.success(new SkillMigrationStepOutput(
                            new Dynamic<>(JsonOps.INSTANCE, root)));
                }))),
                1));
        var errorStep = step(0, input -> {
            throw new AssertionError("must escape");
        });

        assertEquals(SkillMigrationFailure.Code.STEP_THREW_EXCEPTION, runtime.code());
        assertEquals(
                IllegalStateException.class.getName(),
                runtime.result().migrationFailure().orElseThrow().exceptionClassName().orElseThrow());
        assertFalse(runtime.result().toString().contains("runtime-secret"));
        assertEquals(SkillMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS, changedOps.code());
        assertThrows(
                AssertionError.class,
                () -> OpaqueSkillDocumentMigrationFacade.migrateTo(
                        encodedDocument(0), new SkillMigrationPlan(List.of(errorStep)), 1));
    }

    @Test
    void migratedOutputEncodingIsHardBoundedWithoutReturningPartialBytes() throws Exception {
        var oversized = step(0, input -> {
            var root = assertInstanceOf(CompoundTag.class, input.getValue());
            var largeScalar = "x".repeat(65_500);
            for (var index = 0; index < 17; index++) {
                root.putString("oversized_outer_field_" + index, largeScalar);
            }
            root.putInt("schema_version", 1);
            return DataResult.success(new SkillMigrationStepOutput(input));
        });

        var failed = assertInstanceOf(
                OpaqueSkillDocumentMigrationFacade.Failure.class,
                OpaqueSkillDocumentMigrationFacade.migrateTo(
                        encodedDocument(0), new SkillMigrationPlan(List.of(oversized)), 1));

        assertEquals(
                OpaqueSkillDocumentMigrationFacade.FailureCode.TOKENIZED_DOCUMENT_ENCODE_FAILED,
                failed.code());
        assertTrue(failed.migrationFailure().isEmpty());
        assertEquals(1, failed.factReport().facts().size());
    }

    @Test
    void publicFacadeSurfaceHasNoPlanDynamicTagOrMutableTreeParameters() {
        var publicMethods = Arrays.stream(OpaqueSkillDocumentMigrationFacade.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toList();
        var migrateTo = Arrays.stream(OpaqueSkillDocumentMigrationFacade.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("migrateTo"))
                .findFirst()
                .orElseThrow();

        assertEquals(1, publicMethods.size());
        assertEquals("migrate", publicMethods.getFirst().getName());
        assertEquals(
                List.of(TokenizedSkillDocumentMigrationInput.class),
                List.of(publicMethods.getFirst().getParameterTypes()));
        assertFalse(TokenizedSkillDocumentMigrationInput.class.isAssignableFrom(
                OpaqueSkillDocumentMigrationFacade.MigratedTokenizedDocument.class));
        assertFalse(Modifier.isPublic(migrateTo.getModifiers()));
        assertTrue(Arrays.stream(TokenizedSkillDocumentMigrationInput.class
                        .getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .noneMatch(type -> Dynamic.class.isAssignableFrom(type)
                        || net.minecraft.nbt.Tag.class.isAssignableFrom(type)));
    }

    private static MigrationFailure migrationFailure(OpaqueSkillDocumentMigrationFacade.Result result) {
        var failed = assertInstanceOf(OpaqueSkillDocumentMigrationFacade.Failure.class, result);
        assertEquals(
                OpaqueSkillDocumentMigrationFacade.FailureCode.SKILL_MIGRATION_FAILED,
                failed.code());
        return new MigrationFailure(
                failed.migrationFailure().orElseThrow().code(), failed);
    }

    private static TestStep updatingStep(int fromVersion, List<Integer> order) {
        return step(fromVersion, input -> {
            order.add(fromVersion);
            return DataResult.success(new SkillMigrationStepOutput(
                    withVersion(input, fromVersion + 1)));
        });
    }

    private static TestStep step(
            int fromVersion,
            Function<Dynamic<?>, DataResult<SkillMigrationStepOutput>> behavior) {
        return new TestStep(fromVersion, behavior);
    }

    private static Dynamic<?> withVersion(Dynamic<?> input, int version) {
        return withVersionCaptured(input, version);
    }

    private static <T> Dynamic<T> withVersionCaptured(Dynamic<T> input, int version) {
        return input.set(
                "schema_version",
                new Dynamic<>(input.getOps(), input.getOps().createInt(version)));
    }

    private static byte[] encodedDocument(int schemaVersion) throws Exception {
        var root = new CompoundTag();
        root.putInt("schema_version", schemaVersion);
        var sentinel = new CompoundTag();
        sentinel.putInt("__gramarye_opaque_raw_token_v0", 0);
        root.put("opaque", sentinel);
        var output = new ByteArrayOutputStream();
        var data = new DataOutputStream(output);
        NbtIo.writeAnyTag(root, data);
        data.flush();
        return output.toByteArray();
    }

    private static CompoundTag decoded(byte[] bytes) throws Exception {
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return assertInstanceOf(
                    CompoundTag.class,
                    NbtIo.readAnyTag(input, NbtAccounter.unlimitedHeap()));
        }
    }

    private record MigrationFailure(
            SkillMigrationFailure.Code code,
            OpaqueSkillDocumentMigrationFacade.Failure result) {
    }

    private static final class TestStep implements SkillMigrationStep {
        private final int fromVersion;
        private final Function<Dynamic<?>, DataResult<SkillMigrationStepOutput>> behavior;
        private final AtomicInteger calls = new AtomicInteger();

        private TestStep(
                int fromVersion,
                Function<Dynamic<?>, DataResult<SkillMigrationStepOutput>> behavior) {
            this.fromVersion = fromVersion;
            this.behavior = behavior;
        }

        @Override
        public int fromVersion() {
            return fromVersion;
        }

        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public DataResult<SkillMigrationStepOutput> migrate(
                Dynamic<?> defensiveLogicalDocumentCopy) {
            calls.incrementAndGet();
            return behavior.apply(defensiveLogicalDocumentCopy);
        }

        private int calls() {
            return calls.get();
        }
    }
}
