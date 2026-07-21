package com.yo1no.gramarye.magic.definition.migration;

import static com.yo1no.gramarye.magic.definition.migration.P3B2TestFixtures.ACTION_ID;
import static com.yo1no.gramarye.magic.definition.migration.P3B2TestFixtures.EMPTY_READ_REPORT;
import static com.yo1no.gramarye.magic.definition.migration.P3B2TestFixtures.TRIGGER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionFailure;
import com.yo1no.gramarye.magic.definition.resolution.ActionResolution;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.definition.resolution.TriggerResolution;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SkillCandidateResolverTest {
    @Test
    void knownDefinitionsResolveOnceWithoutSemanticValidation() {
        var triggerDecodeCalls = new AtomicInteger();
        var actionDecodeCalls = new AtomicInteger();
        var triggerCodec = P3B2TestFixtures.TriggerData.CODEC.xmap(payload -> {
            triggerDecodeCalls.incrementAndGet();
            return payload;
        }, payload -> payload);
        var actionCodec = P3B2TestFixtures.ActionData.CODEC.xmap(payload -> {
            actionDecodeCalls.incrementAndGet();
            return payload;
        }, payload -> payload);
        var triggerDescriptor = new P3B2TestFixtures.TriggerDescriptor(
                0, PayloadMigrationPlan.empty(), triggerCodec);
        var actionDescriptor = new P3B2TestFixtures.ActionDescriptor(
                0, PayloadMigrationPlan.empty(), actionCodec);
        var triggerLookup = new P3B2TestFixtures.CountingTriggerLookup(
                TRIGGER_ID, triggerDescriptor);
        var actionLookup = new P3B2TestFixtures.CountingActionLookup(
                ACTION_ID, actionDescriptor);
        var document = P3B2TestFixtures.document(
                P3B2TestFixtures.triggerEnvelope(0, 7),
                P3B2TestFixtures.actionEnvelope(0, 9));

        var candidate = new SkillCandidateResolver(
                        triggerLookup, actionLookup, SkillMigrationPlan.empty())
                .resolve(document, EMPTY_READ_REPORT);

        var node = candidate.nodes().getFirst();
        var trigger = assertInstanceOf(TriggerResolution.Resolved.class, node.trigger());
        var action = assertInstanceOf(ActionResolution.Resolved.class, node.action());
        var triggerDefinition = assertInstanceOf(
                ResolvedTriggerDefinition.class, trigger.definition());
        var actionDefinition = assertInstanceOf(
                ResolvedActionDefinition.class, action.definition());
        assertEquals(new P3B2TestFixtures.TriggerData(7), triggerDefinition.payload());
        assertEquals(new P3B2TestFixtures.ActionData(9), actionDefinition.payload());
        assertEquals(1, triggerLookup.findCalls());
        assertEquals(1, actionLookup.findCalls());
        assertEquals(1, triggerDecodeCalls.get());
        assertEquals(1, actionDecodeCalls.get());
        assertEquals(0, triggerDescriptor.validationCalls());
        assertEquals(0, actionDescriptor.validationCalls());
        assertEquals(0, node.nodeIndex());
        assertSame(EMPTY_READ_REPORT, candidate.readReport());
        assertSame(document.appearance(), candidate.appearance());
    }

    @Test
    void triggerAndActionUnknownStatesAreIsolated() {
        var triggerDescriptor = new P3B2TestFixtures.TriggerDescriptor(
                0, PayloadMigrationPlan.empty());
        var actionDescriptor = new P3B2TestFixtures.ActionDescriptor(
                0, PayloadMigrationPlan.empty());
        var triggerUnknownDocument = P3B2TestFixtures.document(
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                P3B2TestFixtures.actionEnvelope(0, 2));
        var actionUnknownDocument = P3B2TestFixtures.document(
                P3B2TestFixtures.triggerEnvelope(0, 3),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 4));

        var first = new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(),
                        new P3B2TestFixtures.CountingActionLookup(ACTION_ID, actionDescriptor),
                        SkillMigrationPlan.empty())
                .resolve(triggerUnknownDocument, EMPTY_READ_REPORT)
                .nodes().getFirst();
        var second = new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(TRIGGER_ID, triggerDescriptor),
                        new P3B2TestFixtures.CountingActionLookup(),
                        SkillMigrationPlan.empty())
                .resolve(actionUnknownDocument, EMPTY_READ_REPORT)
                .nodes().getFirst();

        assertInstanceOf(TriggerResolution.Unknown.class, first.trigger());
        assertInstanceOf(ActionResolution.Resolved.class, first.action());
        assertInstanceOf(TriggerResolution.Resolved.class, second.trigger());
        assertInstanceOf(ActionResolution.Unknown.class, second.action());
    }

    @Test
    void resolvedTriggerIsIsolatedFromActionMigrationFailure() {
        var triggerDescriptor = new P3B2TestFixtures.TriggerDescriptor(
                0, PayloadMigrationPlan.empty());
        var actionDescriptor = new P3B2TestFixtures.ActionDescriptor(
                1, PayloadMigrationPlan.empty());
        var node = new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(TRIGGER_ID, triggerDescriptor),
                        new P3B2TestFixtures.CountingActionLookup(ACTION_ID, actionDescriptor),
                        SkillMigrationPlan.empty())
                .resolve(
                        P3B2TestFixtures.document(
                                P3B2TestFixtures.triggerEnvelope(0, 1),
                                P3B2TestFixtures.actionEnvelope(0, 2)),
                        EMPTY_READ_REPORT)
                .nodes().getFirst();

        assertInstanceOf(TriggerResolution.Resolved.class, node.trigger());
        var failed = assertInstanceOf(ActionResolution.MigrationFailed.class, node.action());
        assertEquals(
                PayloadMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                failed.failure().code());
    }

    @Test
    void migratedPayloadDecodesExactlyOnceAfterBothAdjacentSteps() {
        var first = new CountingPayloadStep(0);
        var second = new CountingPayloadStep(1);
        var decodeCalls = new AtomicInteger();
        var codec = P3B2TestFixtures.TriggerData.CODEC.xmap(payload -> {
            decodeCalls.incrementAndGet();
            return payload;
        }, payload -> payload);
        var descriptor = new P3B2TestFixtures.TriggerDescriptor(
                2,
                new PayloadMigrationPlan(List.of(second, first)),
                codec);

        var resolution = new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(TRIGGER_ID, descriptor),
                        new P3B2TestFixtures.CountingActionLookup(),
                        SkillMigrationPlan.empty())
                .resolve(
                        P3B2TestFixtures.document(
                                P3B2TestFixtures.triggerEnvelope(0, 17),
                                P3B2TestFixtures.envelope(
                                        P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 0)),
                        EMPTY_READ_REPORT)
                .nodes().getFirst().trigger();

        assertInstanceOf(TriggerResolution.Resolved.class, resolution);
        assertEquals(1, first.calls());
        assertEquals(1, second.calls());
        assertEquals(1, decodeCalls.get());
        assertEquals(0, descriptor.validationCalls());
    }

    @Test
    void futureMissingMigrationAndDecodeFailuresRemainDistinctAndRetainSource() {
        var futureSource = P3B2TestFixtures.triggerEnvelope(2, 1);
        var missingSource = P3B2TestFixtures.triggerEnvelope(0, 2);
        var malformedPayload = new JsonObject();
        malformedPayload.addProperty("value", "wrong");
        var malformedSource = new DefinitionEnvelope(
                TRIGGER_ID, 0, new Dynamic<>(JsonOps.INSTANCE, malformedPayload));

        var future = resolveTrigger(
                futureSource,
                new P3B2TestFixtures.TriggerDescriptor(1, new PayloadMigrationPlan(
                        List.of(new PassThroughPayloadStep(0)))));
        var missing = resolveTrigger(
                missingSource,
                new P3B2TestFixtures.TriggerDescriptor(1, PayloadMigrationPlan.empty()));
        var decode = resolveTrigger(
                malformedSource,
                new P3B2TestFixtures.TriggerDescriptor(0, PayloadMigrationPlan.empty()));

        var futureFailed = assertInstanceOf(TriggerResolution.MigrationFailed.class, future);
        var missingFailed = assertInstanceOf(TriggerResolution.MigrationFailed.class, missing);
        var decodeFailed = assertInstanceOf(TriggerResolution.DecodeFailed.class, decode);
        assertEquals(PayloadMigrationFailure.Code.FUTURE_SCHEMA_VERSION, futureFailed.failure().code());
        assertEquals(PayloadMigrationFailure.Code.MISSING_MIGRATION_EDGE, missingFailed.failure().code());
        assertEquals(DefinitionFailure.Code.PAYLOAD_DECODE_ERROR, decodeFailed.failure().code());
        assertSame(futureSource, futureFailed.originalEnvelope());
        assertSame(missingSource, missingFailed.originalEnvelope());
        assertSame(malformedSource, decodeFailed.originalEnvelope());
    }

    @Test
    void candidateCollectionsAreImmutableAndCandidateHasNoPersistenceOrExecutionApi() {
        var candidate = resolvedCandidateWithOnePayloadMigration();
        var resolved = assertInstanceOf(
                TriggerResolution.Resolved.class,
                candidate.nodes().getFirst().trigger());

        assertThrows(UnsupportedOperationException.class, () -> candidate.nodes().clear());
        assertEquals(0, resolved.sourceEnvelope().schemaVersion());
        assertEquals(1, resolved.definition().schemaVersion());
        assertTrue(ResolvedSkillCandidate.class.getDeclaredFields().length > 0);
        assertFalse(hasMethodNamed(ResolvedSkillCandidate.class, "execute"));
        assertFalse(hasMethodNamed(ResolvedSkillCandidate.class, "codec"));
        assertFalse(hasMethodNamed(ResolvedSkillCandidate.class, "write"));
    }

    @Test
    void nodeIndicesAndAppearanceDocumentsArePreservedFromListPosition() {
        var oneNode = P3B2TestFixtures.document(
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 2));
        NodeDocument node = oneNode.nodes().getFirst();
        var document = new SkillDocument(
                oneNode.schemaVersion(),
                oneNode.skillId(),
                oneNode.revision(),
                List.of(node, node, node),
                oneNode.appearance());

        var candidate = new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(),
                        new P3B2TestFixtures.CountingActionLookup(),
                        SkillMigrationPlan.empty())
                .resolve(document, EMPTY_READ_REPORT);

        assertEquals(List.of(0, 1, 2), candidate.nodes().stream()
                .map(com.yo1no.gramarye.magic.definition.resolution.ResolvedNodeCandidate::nodeIndex)
                .toList());
        assertSame(document.appearance(), candidate.appearance());
        assertTrue(candidate.nodes().stream()
                .allMatch(candidateNode -> candidateNode.appearanceOverride()
                        == node.appearanceOverride()));
    }

    @Test
    void fullPrefixFactsTruncateLaterPayloadFactsWithoutChangingOrder() {
        var prefix = new ArrayList<SkillMigrationFact>(MagicSafetyCeilings.MAX_PIPELINE_FACTS);
        for (var index = 0; index < MagicSafetyCeilings.MAX_PIPELINE_FACTS; index++) {
            prefix.add(new SkillMigrationFact(
                    SkillMigrationFactCode.STEP_APPLIED,
                    index,
                    index + 1,
                    OptionalInt.of(index)));
        }
        var report = new PipelineFactReport(prefix, false);
        var triggerDescriptor = new P3B2TestFixtures.TriggerDescriptor(
                1,
                new PayloadMigrationPlan(List.of(new PassThroughPayloadStep(0))));
        var document = P3B2TestFixtures.document(
                P3B2TestFixtures.triggerEnvelope(0, 1),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 2));
        var candidate = new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(TRIGGER_ID, triggerDescriptor),
                        new P3B2TestFixtures.CountingActionLookup(),
                        SkillMigrationPlan.empty())
                .resolve(document, EMPTY_READ_REPORT, report);

        assertEquals(MagicSafetyCeilings.MAX_PIPELINE_FACTS, candidate.pipelineFacts().facts().size());
        assertTrue(candidate.pipelineFacts().truncated());
        assertEquals(prefix, candidate.pipelineFacts().facts());
    }

    private static TriggerResolution resolveTrigger(
            DefinitionEnvelope source,
            P3B2TestFixtures.TriggerDescriptor descriptor) {
        var document = P3B2TestFixtures.document(
                source,
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 0));
        return new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(TRIGGER_ID, descriptor),
                        new P3B2TestFixtures.CountingActionLookup(),
                        SkillMigrationPlan.empty())
                .resolve(document, EMPTY_READ_REPORT)
                .nodes().getFirst().trigger();
    }

    private static ResolvedSkillCandidate resolvedCandidateWithOnePayloadMigration() {
        var descriptor = new P3B2TestFixtures.TriggerDescriptor(
                1,
                new PayloadMigrationPlan(List.of(new PassThroughPayloadStep(0))));
        return new SkillCandidateResolver(
                        new P3B2TestFixtures.CountingTriggerLookup(TRIGGER_ID, descriptor),
                        new P3B2TestFixtures.CountingActionLookup(),
                        SkillMigrationPlan.empty())
                .resolve(
                        P3B2TestFixtures.document(
                                P3B2TestFixtures.triggerEnvelope(0, 1),
                                P3B2TestFixtures.envelope(
                                        P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 2)),
                        EMPTY_READ_REPORT);
    }

    private static boolean hasMethodNamed(Class<?> type, String name) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals(name));
    }

    private record PassThroughPayloadStep(int fromVersion) implements PayloadMigrationStep {
        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public <T> DataResult<PayloadMigrationStepOutput<T>> migrate(
                Dynamic<T> defensivePayloadCopy) {
            return DataResult.success(new PayloadMigrationStepOutput<>(defensivePayloadCopy));
        }
    }

    private static final class CountingPayloadStep implements PayloadMigrationStep {
        private final int fromVersion;
        private final AtomicInteger calls = new AtomicInteger();

        private CountingPayloadStep(int fromVersion) {
            this.fromVersion = fromVersion;
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
            return DataResult.success(new PayloadMigrationStepOutput<>(defensivePayloadCopy));
        }

        private int calls() {
            return calls.get();
        }
    }
}
