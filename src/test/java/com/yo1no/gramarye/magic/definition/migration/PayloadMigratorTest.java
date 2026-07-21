package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class PayloadMigratorTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void adjacentChainRunsEachStepOncePreservesTypeAndEmitsOrderedFacts() {
        var zero = new TestStep(0, Behavior.SUCCESS);
        var one = new TestStep(1, Behavior.SUCCESS);
        var source = source(JsonOps.INSTANCE, 0);
        var before = source.copyRawPayload().getValue();
        var facts = new PipelineFactCollector();

        var migrated = success(PayloadMigrator.migrate(
                source,
                2,
                new PayloadMigrationPlan(List.of(one, zero)),
                3,
                facts));

        assertEquals(1, zero.calls());
        assertEquals(1, one.calls());
        assertEquals(source.typeId(), migrated.transientEnvelope().typeId());
        assertEquals(2, migrated.transientEnvelope().schemaVersion());
        assertEquals(0, source.schemaVersion());
        assertEquals(before, source.copyRawPayload().getValue());
        assertEquals(List.of(
                new SkillMigrationFact(
                        SkillMigrationFactCode.PAYLOAD_STEP_APPLIED,
                        0,
                        1,
                        java.util.OptionalInt.of(0),
                        java.util.OptionalInt.of(3)),
                new SkillMigrationFact(
                        SkillMigrationFactCode.PAYLOAD_STEP_APPLIED,
                        1,
                        2,
                        java.util.OptionalInt.of(1),
                        java.util.OptionalInt.of(3))), facts.report().facts());
    }

    @Test
    void currentFutureAndMissingEdgeHaveDistinctOutcomes() {
        var source = source(JsonOps.INSTANCE, 1);
        var unusedCurrentEdge = new TestStep(1, Behavior.SUCCESS);

        var current = success(PayloadMigrator.migrate(
                source,
                1,
                new PayloadMigrationPlan(List.of(unusedCurrentEdge)),
                0,
                new PipelineFactCollector()));
        var future = failure(PayloadMigrator.migrate(
                source, 0, PayloadMigrationPlan.empty(), 0, new PipelineFactCollector()));
        var missing = failure(PayloadMigrator.migrate(
                source(JsonOps.INSTANCE, 0),
                1,
                PayloadMigrationPlan.empty(),
                0,
                new PipelineFactCollector()));

        assertSame(source, current.transientEnvelope());
        assertEquals(0, unusedCurrentEdge.calls());
        assertEquals(PayloadMigrationFailure.Code.FUTURE_SCHEMA_VERSION, future.failure().code());
        assertEquals(PayloadMigrationFailure.Code.MISSING_MIGRATION_EDGE, missing.failure().code());
    }

    @Test
    void errorPartialAndRuntimeExceptionAreContainedWithoutMessage() {
        var secret = "payload-migration-secret";
        var error = migrateOne(new TestStep(0, Behavior.ERROR));
        var partial = migrateOne(new TestStep(0, Behavior.PARTIAL));
        var runtime = migrateOne(new ThrowingStep(0, new IllegalStateException(secret)));

        assertEquals(PayloadMigrationFailure.Code.STEP_FAILED, failure(error).failure().code());
        assertEquals(
                PayloadMigrationFailure.Code.STEP_RETURNED_PARTIAL,
                failure(partial).failure().code());
        var runtimeFailure = failure(runtime).failure();
        assertEquals(PayloadMigrationFailure.Code.STEP_THREW_EXCEPTION, runtimeFailure.code());
        assertEquals(IllegalStateException.class.getName(),
                runtimeFailure.exceptionClassName().orElseThrow());
        assertFalse(runtimeFailure.toString().contains(secret));
    }

    @Test
    void ErrorIsNotCaught() {
        var step = new PayloadMigrationStep() {
            @Override
            public int fromVersion() {
                return 0;
            }

            @Override
            public int toVersion() {
                return 1;
            }

            @Override
            public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                    Dynamic<T> defensivePayloadCopy) {
                throw new AssertionError("must escape");
            }
        };

        assertThrows(AssertionError.class, () -> migrateOne(step));
    }

    @Test
    void exactOpsIdentityRejectsCompressedJsonAndRegistryWrapperStripping() {
        var plain = failure(PayloadMigrator.migrate(
                source(JsonOps.INSTANCE, 0),
                1,
                new PayloadMigrationPlan(List.of(new CompressedJsonStep())),
                0,
                new PipelineFactCollector()));
        var registryOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var wrapped = failure(PayloadMigrator.migrate(
                source(registryOps, 0),
                1,
                new PayloadMigrationPlan(List.of(new StripRegistryOpsStep())),
                0,
                new PipelineFactCollector()));

        assertEquals(PayloadMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS, plain.failure().code());
        assertEquals(PayloadMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS, wrapped.failure().code());
    }

    @Test
    void migratedPayloadUsesSharedDepthAndTreeNodeHardBounds() {
        var maximumDepth = success(migrateOne(new TestStep(0, Behavior.MAXIMUM_DEPTH)));
        var depth = failure(migrateOne(new TestStep(0, Behavior.TOO_DEEP)));
        var maximumNodes = success(migrateOne(new TestStep(0, Behavior.MAXIMUM_NODES)));
        var nodes = failure(migrateOne(new TestStep(0, Behavior.TOO_MANY_NODES)));

        assertEquals(1, maximumDepth.transientEnvelope().schemaVersion());
        assertEquals(
                PayloadMigrationFailure.Code.PAYLOAD_TREE_DEPTH_EXCEEDED,
                depth.failure().code());
        assertEquals(1, maximumNodes.transientEnvelope().schemaVersion());
        assertEquals(
                PayloadMigrationFailure.Code.PAYLOAD_TREE_NODE_LIMIT_EXCEEDED,
                nodes.failure().code());
    }

    @Test
    void mutatingStepInputCannotChangeOriginalEnvelopeTree() {
        var source = source(JsonOps.INSTANCE, 0);
        var before = source.copyRawPayload().getValue();
        var step = new PayloadMigrationStep() {
            @Override
            public int fromVersion() {
                return 0;
            }

            @Override
            public int toVersion() {
                return 1;
            }

            @Override
            public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                    Dynamic<T> defensivePayloadCopy) {
                var exposed = (JsonObject) defensivePayloadCopy.getValue();
                var nested = new JsonObject();
                nested.addProperty("changed", true);
                exposed.addProperty("root_changed", true);
                exposed.add("nested", nested);
                return DataResult.success(new PayloadMigrationStepOutput<>(defensivePayloadCopy));
            }
        };

        var migrated = success(migrateOne(step));

        assertEquals(before, source.copyRawPayload().getValue());
        var migratedPayload = (JsonObject) migrated.transientEnvelope().copyRawPayload().getValue();
        assertTrue(migratedPayload.get("root_changed").getAsBoolean());
        assertTrue(migratedPayload.getAsJsonObject("nested").get("changed").getAsBoolean());
    }

    private static PayloadMigrator.Result migrateOne(PayloadMigrationStep step) {
        return PayloadMigrator.migrate(
                source(JsonOps.INSTANCE, 0),
                1,
                new PayloadMigrationPlan(List.of(step)),
                0,
                new PipelineFactCollector());
    }

    private static DefinitionEnvelope source(
            com.mojang.serialization.DynamicOps<JsonElement> ops,
            int schemaVersion) {
        var payload = new JsonObject();
        payload.addProperty("value", 1);
        return new DefinitionEnvelope(
                P3B2TestFixtures.TRIGGER_ID,
                schemaVersion,
                new Dynamic<>(ops, payload));
    }

    private static PayloadMigrator.Result.Success success(PayloadMigrator.Result result) {
        return assertInstanceOf(PayloadMigrator.Result.Success.class, result);
    }

    private static PayloadMigrator.Result.Failure failure(PayloadMigrator.Result result) {
        return assertInstanceOf(PayloadMigrator.Result.Failure.class, result);
    }

    private enum Behavior {
        SUCCESS,
        ERROR,
        PARTIAL,
        MAXIMUM_DEPTH,
        TOO_DEEP,
        MAXIMUM_NODES,
        TOO_MANY_NODES
    }

    private static final class TestStep implements PayloadMigrationStep {
        private final int fromVersion;
        private final Behavior behavior;
        private final AtomicInteger calls = new AtomicInteger();

        private TestStep(int fromVersion, Behavior behavior) {
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
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            calls.incrementAndGet();
            return switch (behavior) {
                case SUCCESS -> DataResult.success(new PayloadMigrationStepOutput<>(
                        defensivePayloadCopy.set(
                                "migrated_to",
                                new Dynamic<>(
                                        defensivePayloadCopy.getOps(),
                                        defensivePayloadCopy.getOps().createInt(toVersion())))));
                case ERROR -> DataResult.error(() -> "ignored error message");
                case PARTIAL -> DataResult.error(
                        () -> "ignored partial message",
                        new PayloadMigrationStepOutput<>(defensivePayloadCopy));
                case MAXIMUM_DEPTH -> DataResult.success(new PayloadMigrationStepOutput<>(
                        nested(defensivePayloadCopy, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH)));
                case TOO_DEEP -> DataResult.success(new PayloadMigrationStepOutput<>(
                        nested(defensivePayloadCopy, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH + 1)));
                case MAXIMUM_NODES -> DataResult.success(new PayloadMigrationStepOutput<>(
                        withTotalNodes(
                                defensivePayloadCopy,
                                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES)));
                case TOO_MANY_NODES -> DataResult.success(new PayloadMigrationStepOutput<>(
                        withTotalNodes(
                                defensivePayloadCopy,
                                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES + 1)));
            };
        }

        int calls() {
            return calls.get();
        }
    }

    private record ThrowingStep(int fromVersion, RuntimeException exception)
            implements PayloadMigrationStep {
        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            throw exception;
        }
    }

    private static final class CompressedJsonStep implements PayloadMigrationStep {
        @Override
        public int fromVersion() {
            return 0;
        }

        @Override
        public int toVersion() {
            return 1;
        }

        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            return DataResult.success(new PayloadMigrationStepOutput<>(
                    compressedJson(defensivePayloadCopy)));
        }
    }

    private static final class StripRegistryOpsStep implements PayloadMigrationStep {
        @Override
        public int fromVersion() {
            return 0;
        }

        @Override
        public int toVersion() {
            return 1;
        }

        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            return DataResult.success(new PayloadMigrationStepOutput<>(
                    plainJson(defensivePayloadCopy)));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Dynamic<T> compressedJson(Dynamic<T> input) {
        var json = ((JsonElement) input.getValue()).deepCopy();
        return (Dynamic<T>) (Dynamic<?>) new Dynamic<>(JsonOps.COMPRESSED, json);
    }

    @SuppressWarnings("unchecked")
    private static <T> Dynamic<T> plainJson(Dynamic<T> input) {
        var json = ((JsonElement) input.getValue()).deepCopy();
        return (Dynamic<T>) (Dynamic<?>) new Dynamic<>(JsonOps.INSTANCE, json);
    }

    private static <T> Dynamic<T> nested(Dynamic<T> input, int depth) {
        var ops = input.getOps();
        T value = ops.createString("leaf");
        for (var index = 1; index < depth; index++) {
            value = ops.createMap(Stream.of(Pair.of(ops.createString("next"), value)));
        }
        return new Dynamic<>(ops, value);
    }

    private static <T> Dynamic<T> withTotalNodes(Dynamic<T> input, int totalNodes) {
        var ops = input.getOps();
        // The list root is one node, so its elements contribute totalNodes - 1 nodes.
        var values = IntStream.range(0, totalNodes - 1)
                .mapToObj(ops::createInt);
        return new Dynamic<>(ops, ops.createList(values));
    }
}
