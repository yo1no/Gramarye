package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import org.junit.jupiter.api.Test;

class SkillDocumentMigratorTest {
    private static final HolderLookup.Provider EMPTY_PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void currentVersionIsNoOpSuccessWithoutAllocatingRevisionOrFacts() {
        var original = snapshot(documentJson(0));
        var result = success(SkillDocumentMigrator.migrateTo(original, SkillMigrationPlan.empty(), 0));

        assertSame(original, result.migratedSnapshot());
        assertTrue(result.factReport().facts().isEmpty());
        assertFalse(result.factReport().truncated());
    }

    @Test
    void futureVersionAndMissingEdgeFailWhilePreservingOriginal() {
        var futureOriginal = snapshot(documentJson(2));
        var future = failure(SkillDocumentMigrator.migrateTo(
                futureOriginal, SkillMigrationPlan.empty(), 1));
        var missingOriginal = snapshot(documentJson(0));
        var missing = failure(SkillDocumentMigrator.migrateTo(
                missingOriginal, SkillMigrationPlan.empty(), 1));

        assertSame(futureOriginal, future.originalSnapshot());
        assertEquals(SkillMigrationFailure.Code.FUTURE_SCHEMA_VERSION, future.failure().code());
        assertSame(missingOriginal, missing.originalSnapshot());
        assertEquals(SkillMigrationFailure.Code.MISSING_MIGRATION_EDGE, missing.failure().code());
    }

    @Test
    void adjacentStepsRunOnceInOrderAndEmitDeterministicFacts() {
        var order = new ArrayList<Integer>();
        var zero = updatingStep(0, order);
        var one = updatingStep(1, order);
        var original = snapshot(documentJson(0));
        var result = success(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(one, zero)), 2));

        assertEquals(List.of(0, 1), order);
        assertEquals(1, zero.calls());
        assertEquals(1, one.calls());
        assertEquals(2, probe(result.migratedSnapshot()));
        assertEquals(List.of(
                new SkillMigrationFact(SkillMigrationFactCode.STEP_APPLIED, 0, 1, java.util.OptionalInt.of(0)),
                new SkillMigrationFact(SkillMigrationFactCode.STEP_APPLIED, 1, 2, java.util.OptionalInt.of(1))),
                result.factReport().facts());
        assertFalse(result.factReport().truncated());
        assertThrows(UnsupportedOperationException.class, () -> result.factReport().facts().clear());
    }

    @Test
    void adjacentMigrationAlsoPreservesNbtOpsAndProducesNbtSnapshot() {
        var root = new CompoundTag();
        root.putInt("schema_version", 0);
        root.putString("kept", "nbt");
        var original = RawSkillDocumentSnapshot.of(new Dynamic<>(NbtOps.INSTANCE, root));
        var step = updatingStep(0, new ArrayList<>());

        var result = success(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(step)), 1));
        var migrated = assertInstanceOf(CompoundTag.class, result.migratedSnapshot()
                .copyRawDocument().getValue());

        assertEquals(1, migrated.getInt("schema_version"));
        assertEquals("nbt", migrated.getString("kept"));
        assertSame(NbtOps.INSTANCE, result.migratedSnapshot().copyRawDocument().getOps());
    }

    @Test
    void stepReceivesCopyAndMutatingItCannotChangePreviousOrOriginalSnapshot() {
        var originalTree = documentJson(0);
        var expected = originalTree.deepCopy();
        var original = snapshot(originalTree);
        var step = new TestStep(0, 1, input -> {
            var root = assertInstanceOf(JsonObject.class, input.getValue());
            root.addProperty("step_mutated_copy", true);
            root.getAsJsonObject("appearance").addProperty("step_nested_mutation", true);
            return DataResult.success(new SkillMigrationStepOutput(withVersion(input, 1)));
        });

        var result = success(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(step)), 1));

        assertEquals(expected, original.copyRawDocument().getValue());
        assertFalse(((JsonObject) original.copyRawDocument().getValue()).has("step_mutated_copy"));
        assertTrue(((JsonObject) result.migratedSnapshot().copyRawDocument().getValue())
                .get("step_mutated_copy").getAsBoolean());
        assertNotSame(
                original.copyRawDocument().getValue(),
                result.migratedSnapshot().copyRawDocument().getValue());
    }

    @Test
    void partialAndCompleteDataErrorsAreRejectedWithoutUsingPayload() {
        var original = snapshot(documentJson(0));
        var partialStep = new TestStep(0, 1, input -> DataResult.error(
                () -> "partial-secret",
                new SkillMigrationStepOutput(withVersion(input, 1))));
        var failedStep = new TestStep(0, 1, input -> DataResult.error(() -> "failure-secret"));

        var partial = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(partialStep)), 1));
        var failed = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(failedStep)), 1));

        assertEquals(SkillMigrationFailure.Code.STEP_RETURNED_PARTIAL, partial.failure().code());
        assertEquals(SkillMigrationFailure.Code.STEP_FAILED, failed.failure().code());
        assertFalse(partial.failure().toString().contains("partial-secret"));
        assertFalse(failed.failure().toString().contains("failure-secret"));
    }

    @Test
    void failedStepCannotMutateThePreservedOriginalSnapshot() {
        var source = documentJson(0);
        var expected = source.deepCopy();
        var original = snapshot(source);
        var step = new TestStep(0, 1, input -> {
            var copy = assertInstanceOf(JsonObject.class, input.getValue());
            copy.addProperty("mutated_before_failure", true);
            copy.getAsJsonObject("appearance").addProperty("nested_mutation", true);
            return DataResult.error(() -> "step failed");
        });

        var failed = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(step)), 1));

        assertSame(original, failed.originalSnapshot());
        assertEquals(expected, failed.originalSnapshot().copyRawDocument().getValue());
    }

    @Test
    void runtimeExceptionIsBoundedToClassNameAndErrorIsNotCaught() {
        var original = snapshot(documentJson(0));
        var runtime = new TestStep(0, 1, input -> {
            throw new IllegalStateException("unique-secret-exception-message");
        });
        var error = new TestStep(0, 1, input -> {
            throw new AssertionError("must escape");
        });

        var failed = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(runtime)), 1));

        assertEquals(SkillMigrationFailure.Code.STEP_THREW_EXCEPTION, failed.failure().code());
        assertEquals(IllegalStateException.class.getName(), failed.failure().exceptionClassName().orElseThrow());
        assertFalse(failed.failure().toString().contains("unique-secret-exception-message"));
        assertThrows(
                AssertionError.class,
                () -> SkillDocumentMigrator.migrateTo(
                        original, new SkillMigrationPlan(List.of(error)), 1));
    }

    @Test
    void changedJsonOpsInstanceAndStrippedRegistryOpsWrapperAreRejected() {
        var plainOriginal = snapshot(documentJson(0));
        var changedInstance = new TestStep(0, 1, input -> {
            var changed = withJsonVersion(assertInstanceOf(JsonElement.class, input.getValue()), 1);
            return DataResult.success(new SkillMigrationStepOutput(
                    new Dynamic<>(JsonOps.COMPRESSED, changed)));
        });
        var registryOps = RegistryOps.create(JsonOps.INSTANCE, EMPTY_PROVIDER);
        var wrappedOriginal = RawSkillDocumentSnapshot.of(new Dynamic<>(registryOps, documentJson(0)));
        var strippedWrapper = new TestStep(0, 1, input -> {
            var changed = withJsonVersion(assertInstanceOf(JsonElement.class, input.getValue()), 1);
            return DataResult.success(new SkillMigrationStepOutput(
                    new Dynamic<>(JsonOps.INSTANCE, changed)));
        });

        var changed = failure(SkillDocumentMigrator.migrateTo(
                plainOriginal, new SkillMigrationPlan(List.of(changedInstance)), 1));
        var stripped = failure(SkillDocumentMigrator.migrateTo(
                wrappedOriginal, new SkillMigrationPlan(List.of(strippedWrapper)), 1));

        assertEquals(SkillMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS, changed.failure().code());
        assertEquals(SkillMigrationFailure.Code.STEP_CHANGED_DYNAMIC_OPS, stripped.failure().code());
    }

    @Test
    void stepOutputDepthAndTreeNodeCeilingsAreRechecked() {
        var original = snapshot(documentJson(0));
        var tooDeep = new TestStep(0, 1, input -> DataResult.success(
                new SkillMigrationStepOutput(new Dynamic<>(JsonOps.INSTANCE, nestedJson(65)))));
        var tooManyNodes = new TestStep(0, 1, input -> DataResult.success(
                new SkillMigrationStepOutput(new Dynamic<>(JsonOps.INSTANCE, oversizedDocument(1)))));

        var depthFailure = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(tooDeep)), 1));
        var nodeFailure = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(tooManyNodes)), 1));

        assertEquals(SkillMigrationFailure.Code.GLOBAL_DEPTH_EXCEEDED, depthFailure.failure().code());
        assertEquals(
                SkillMigrationFailure.Code.GLOBAL_TREE_NODE_LIMIT_EXCEEDED,
                nodeFailure.failure().code());
        assertSame(original, depthFailure.originalSnapshot());
        assertSame(original, nodeFailure.originalSnapshot());
    }

    @Test
    void outputSchemaVersionMustExactlyMatchAdjacentTarget() {
        var original = snapshot(documentJson(0));
        var mismatch = new TestStep(0, 1, input -> DataResult.success(
                new SkillMigrationStepOutput(withVersion(input, 2))));
        var malformed = new TestStep(0, 1, input -> DataResult.success(
                new SkillMigrationStepOutput(input.remove("schema_version"))));

        var wrongVersion = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(mismatch)), 1));
        var missingVersion = failure(SkillDocumentMigrator.migrateTo(
                original, new SkillMigrationPlan(List.of(malformed)), 1));

        assertEquals(SkillMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH, wrongVersion.failure().code());
        assertEquals(SkillMigrationFailure.Code.STEP_OUTPUT_VERSION_MISMATCH, missingVersion.failure().code());
    }

    @Test
    void factCollectorCapsAt1024WithoutFailingMigration() {
        var current = MagicSafetyCeilings.MAX_PIPELINE_FACTS + 1;
        var steps = IntStream.range(0, current)
                .mapToObj(version -> updatingStep(version, new ArrayList<>()))
                .map(SkillMigrationStep.class::cast)
                .toList();
        var result = success(SkillDocumentMigrator.migrateTo(
                snapshot(documentJson(0)), new SkillMigrationPlan(steps), current));

        assertEquals(current, probe(result.migratedSnapshot()));
        assertEquals(MagicSafetyCeilings.MAX_PIPELINE_FACTS, result.factReport().facts().size());
        assertTrue(result.factReport().truncated());
        assertEquals(0, result.factReport().facts().get(0).fromVersion());
        assertEquals(
                MagicSafetyCeilings.MAX_PIPELINE_FACTS - 1,
                result.factReport().facts().get(MagicSafetyCeilings.MAX_PIPELINE_FACTS - 1).fromVersion());
    }

    @Test
    void factReportDefensivelyCopiesCallerCollectionAndEnforcesHardCap() {
        var source = new ArrayList<SkillMigrationFact>();
        source.add(new SkillMigrationFact(
                SkillMigrationFactCode.STEP_APPLIED, 0, 1, java.util.OptionalInt.empty()));
        var report = new PipelineFactReport(source, false);
        source.clear();
        var overLimit = IntStream.range(0, MagicSafetyCeilings.MAX_PIPELINE_FACTS + 1)
                .mapToObj(index -> new SkillMigrationFact(
                        SkillMigrationFactCode.STEP_APPLIED,
                        index,
                        index + 1,
                        java.util.OptionalInt.empty()))
                .toList();

        assertEquals(1, report.facts().size());
        assertThrows(UnsupportedOperationException.class, () -> report.facts().clear());
        assertThrows(IllegalArgumentException.class, () -> new PipelineFactReport(overLimit, true));
    }

    @Test
    void skillLevelStepCanChangeOuterFieldsButPreservesEnvelopePayloadsAndUnknownType() {
        var document = documentJson(0);
        var triggerBefore = document.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("trigger").get("payload").deepCopy();
        var actionBefore = document.getAsJsonArray("nodes").get(0).getAsJsonObject()
                .getAsJsonObject("action").get("payload").deepCopy();
        var step = new TestStep(0, 1, input -> {
            var changed = withVersion(input, 1);
            return DataResult.success(new SkillMigrationStepOutput(
                    setBoolean(changed, "outer_migrated", true)));
        });

        var result = success(SkillDocumentMigrator.migrateTo(
                snapshot(document), new SkillMigrationPlan(List.of(step)), 1));
        var output = (JsonObject) result.migratedSnapshot().copyRawDocument().getValue();
        var node = output.getAsJsonArray("nodes").get(0).getAsJsonObject();

        assertTrue(output.get("outer_migrated").getAsBoolean());
        assertEquals("future:unknown_trigger", node.getAsJsonObject("trigger").get("type").getAsString());
        assertEquals(triggerBefore, node.getAsJsonObject("trigger").get("payload"));
        assertEquals(actionBefore, node.getAsJsonObject("action").get("payload"));
    }

    @Test
    void stepOutputHasOnlyTreeAndNoFactChannel() {
        var components = SkillMigrationStepOutput.class.getRecordComponents();
        var secret = new JsonObject();
        secret.addProperty("secret", "step-output-secret");
        var output = new SkillMigrationStepOutput(new Dynamic<>(JsonOps.INSTANCE, secret));

        assertEquals(1, components.length);
        assertEquals("migratedTree", components[0].getName());
        assertFalse(components[0].getType().getName().contains("Fact"));
        assertFalse(output.toString().contains("step-output-secret"));
    }

    private static TestStep updatingStep(int fromVersion, List<Integer> order) {
        return new TestStep(fromVersion, fromVersion + 1, input -> {
            order.add(fromVersion);
            return DataResult.success(new SkillMigrationStepOutput(withVersion(input, fromVersion + 1)));
        });
    }

    private static Dynamic<?> withVersion(Dynamic<?> input, int version) {
        return withVersionCaptured(input, version);
    }

    private static <T> Dynamic<T> withVersionCaptured(Dynamic<T> input, int version) {
        return input.set(
                "schema_version",
                new Dynamic<>(input.getOps(), input.getOps().createInt(version)));
    }

    private static Dynamic<?> setBoolean(Dynamic<?> input, String field, boolean value) {
        return setBooleanCaptured(input, field, value);
    }

    private static <T> Dynamic<T> setBooleanCaptured(Dynamic<T> input, String field, boolean value) {
        return input.set(field, new Dynamic<>(input.getOps(), input.getOps().createBoolean(value)));
    }

    private static JsonElement withJsonVersion(JsonElement input, int version) {
        var copy = input.deepCopy().getAsJsonObject();
        copy.addProperty("schema_version", version);
        return copy;
    }

    private static RawSkillDocumentSnapshot snapshot(JsonElement root) {
        return RawSkillDocumentSnapshot.of(new Dynamic<>(JsonOps.INSTANCE, root));
    }

    private static JsonObject documentJson(int version) {
        var triggerPayload = new JsonObject();
        var triggerNested = new JsonObject();
        triggerNested.addProperty("kept", "trigger");
        triggerPayload.add("nested", triggerNested);
        var actionPayload = new JsonObject();
        var actionArray = new JsonArray();
        actionArray.add("action");
        actionPayload.add("items", actionArray);
        var trigger = envelope("future:unknown_trigger", triggerPayload);
        var action = envelope("future:unknown_action", actionPayload);
        var node = new JsonObject();
        node.add("trigger", trigger);
        node.add("action", action);
        var nodes = new JsonArray();
        nodes.add(node);
        var root = new JsonObject();
        root.addProperty("schema_version", version);
        root.add("nodes", nodes);
        root.add("appearance", new JsonObject());
        return root;
    }

    private static JsonObject envelope(String type, JsonElement payload) {
        var envelope = new JsonObject();
        envelope.addProperty("type", type);
        envelope.addProperty("schema_version", 27);
        envelope.add("payload", payload);
        return envelope;
    }

    private static JsonElement nestedJson(int depth) {
        JsonElement value = JsonNull.INSTANCE;
        for (var index = 1; index < depth; index++) {
            var parent = new JsonObject();
            parent.add("next", value);
            value = parent;
        }
        return value;
    }

    private static JsonObject oversizedDocument(int version) {
        var elements = new JsonArray(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES);
        for (var index = 0; index < MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES; index++) {
            elements.add(index);
        }
        var root = new JsonObject();
        root.addProperty("schema_version", version);
        root.add("oversized", elements);
        return root;
    }

    private static int probe(RawSkillDocumentSnapshot snapshot) {
        return assertInstanceOf(
                        SkillSchemaVersionProbe.Result.Success.class,
                        SkillSchemaVersionProbe.probe(snapshot))
                .schemaVersion();
    }

    private static SkillMigrationResult.Success success(SkillMigrationResult result) {
        return assertInstanceOf(SkillMigrationResult.Success.class, result);
    }

    private static SkillMigrationResult.Failure failure(SkillMigrationResult result) {
        return assertInstanceOf(SkillMigrationResult.Failure.class, result);
    }

    private static final class TestStep implements SkillMigrationStep {
        private final int fromVersion;
        private final int toVersion;
        private final Function<Dynamic<?>, DataResult<SkillMigrationStepOutput>> behavior;
        private final AtomicInteger calls = new AtomicInteger();

        private TestStep(
                int fromVersion,
                int toVersion,
                Function<Dynamic<?>, DataResult<SkillMigrationStepOutput>> behavior) {
            this.fromVersion = fromVersion;
            this.toVersion = toVersion;
            this.behavior = behavior;
        }

        @Override
        public int fromVersion() {
            return fromVersion;
        }

        @Override
        public int toVersion() {
            return toVersion;
        }

        @Override
        public DataResult<SkillMigrationStepOutput> migrate(Dynamic<?> defensiveSourceCopy) {
            calls.incrementAndGet();
            return behavior.apply(defensiveSourceCopy);
        }

        int calls() {
            return calls.get();
        }
    }
}
