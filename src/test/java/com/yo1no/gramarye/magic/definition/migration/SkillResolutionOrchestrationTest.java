package com.yo1no.gramarye.magic.definition.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JavaOps;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentWriter;
import com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillResolutionOrchestrationTest {
    @Test
    void unsupportedRawInputStopsBeforeMigrationReaderAndResolution() {
        var triggerLookup = new P3B2TestFixtures.CountingTriggerLookup();
        var actionLookup = new P3B2TestFixtures.CountingActionLookup();
        var resolver = new SkillCandidateResolver(
                triggerLookup, actionLookup, SkillMigrationPlan.empty());
        var unsupported = new Dynamic<>(JavaOps.INSTANCE, Map.of("schema_version", 0));

        var rejected = assertInstanceOf(
                SkillResolutionResult.RawInputRejected.class,
                resolver.resolveFromRaw(unsupported));

        assertEquals(
                SkillMigrationFailure.Code.UNSUPPORTED_RAW_FAMILY,
                rejected.failure().code());
        assertEquals(0, triggerLookup.findCalls());
        assertEquals(0, actionLookup.findCalls());
    }

    @Test
    void skillMigrationFailureRetainsOriginalSnapshotAndStopsResolution() {
        var triggerLookup = new P3B2TestFixtures.CountingTriggerLookup();
        var actionLookup = new P3B2TestFixtures.CountingActionLookup();
        var resolver = new SkillCandidateResolver(
                triggerLookup, actionLookup, SkillMigrationPlan.empty());
        var raw = canonicalRaw(P3B2TestFixtures.document(
                P3B2TestFixtures.triggerEnvelope(0, 1),
                P3B2TestFixtures.actionEnvelope(0, 2)));

        var failed = assertInstanceOf(
                SkillResolutionResult.SkillMigrationFailed.class,
                resolver.resolveFromRawTo(raw, 1));

        assertEquals(
                SkillMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                failed.failure().failure().code());
        assertEquals(raw.getValue(), failed.failure().originalSnapshot().copyRawDocument().getValue());
        assertEquals(0, triggerLookup.findCalls());
        assertEquals(0, actionLookup.findCalls());
    }

    @Test
    void readerFailureUsesTypedBoundaryAndStopsEnvelopeResolution() {
        var triggerLookup = new P3B2TestFixtures.CountingTriggerLookup();
        var actionLookup = new P3B2TestFixtures.CountingActionLookup();
        var resolver = new SkillCandidateResolver(
                triggerLookup, actionLookup, SkillMigrationPlan.empty());
        var malformed = new JsonObject();
        malformed.addProperty("schema_version", 0);

        var failed = assertInstanceOf(
                SkillResolutionResult.ReadFailed.class,
                resolver.resolveFromRaw(new Dynamic<>(JsonOps.INSTANCE, malformed)));

        assertEquals(
                com.yo1no.gramarye.magic.definition.document.SkillDocumentReadFailureCode
                        .READER_REJECTED_INPUT,
                failed.failure().code());
        assertEquals(0, triggerLookup.findCalls());
        assertEquals(0, actionLookup.findCalls());
        assertEquals(1, SkillResolutionResult.ReadFailed.class.getRecordComponents().length);
    }

    @Test
    void successfulRawResolutionPreservesReaderReportAndMergesFactsInOrder() {
        var triggerPlan = new PayloadMigrationPlan(List.of(new PassThroughPayloadStep(0)));
        var triggerDescriptor = new P3B2TestFixtures.TriggerDescriptor(1, triggerPlan);
        var triggerLookup = new P3B2TestFixtures.CountingTriggerLookup(
                P3B2TestFixtures.TRIGGER_ID, triggerDescriptor);
        var actionLookup = new P3B2TestFixtures.CountingActionLookup();
        var skillPlan = new SkillMigrationPlan(List.of(
                new VersionSkillStep(0),
                new VersionSkillStep(1)));
        var resolver = new SkillCandidateResolver(triggerLookup, actionLookup, skillPlan);
        var sourceDocument = P3B2TestFixtures.document(
                P3B2TestFixtures.triggerEnvelope(0, 7),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 8));

        var success = assertInstanceOf(
                SkillResolutionResult.Success.class,
                resolver.resolveFromRawTo(canonicalRaw(sourceDocument), 2));
        var candidate = success.candidate();

        assertFalse(candidate.readReport().truncated());
        assertTrue(candidate.readReport().facts().isEmpty());
        assertEquals(
                List.of(
                        SkillMigrationFactCode.STEP_APPLIED,
                        SkillMigrationFactCode.STEP_APPLIED,
                        SkillMigrationFactCode.PAYLOAD_STEP_APPLIED),
                candidate.pipelineFacts().facts().stream()
                        .map(SkillMigrationFact::code)
                        .toList());
        assertEquals(1, triggerLookup.findCalls());
        assertEquals(1, actionLookup.findCalls());
        assertEquals(0, triggerDescriptor.validationCalls());
    }

    @Test
    void skillFactsAtHardCapTruncateLaterPayloadFacts() {
        var triggerPlan = new PayloadMigrationPlan(List.of(new PassThroughPayloadStep(0)));
        var triggerDescriptor = new P3B2TestFixtures.TriggerDescriptor(1, triggerPlan);
        var triggerLookup = new P3B2TestFixtures.CountingTriggerLookup(
                P3B2TestFixtures.TRIGGER_ID, triggerDescriptor);
        var actionLookup = new P3B2TestFixtures.CountingActionLookup();
        var steps = new ArrayList<SkillMigrationStep>(MagicSafetyCeilings.MAX_PIPELINE_FACTS);
        for (var version = 0; version < MagicSafetyCeilings.MAX_PIPELINE_FACTS; version++) {
            steps.add(new VersionSkillStep(version));
        }
        var resolver = new SkillCandidateResolver(
                triggerLookup, actionLookup, new SkillMigrationPlan(steps));
        var sourceDocument = P3B2TestFixtures.document(
                P3B2TestFixtures.triggerEnvelope(0, 7),
                P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 8));

        var candidate = assertInstanceOf(
                        SkillResolutionResult.Success.class,
                        resolver.resolveFromRawTo(
                                canonicalRaw(sourceDocument),
                                MagicSafetyCeilings.MAX_PIPELINE_FACTS))
                .candidate();

        assertEquals(
                MagicSafetyCeilings.MAX_PIPELINE_FACTS,
                candidate.pipelineFacts().facts().size());
        assertTrue(candidate.pipelineFacts().truncated());
        assertTrue(candidate.pipelineFacts().facts().stream()
                .allMatch(fact -> fact.code() == SkillMigrationFactCode.STEP_APPLIED));
    }

    @Test
    void directDocumentEntryPreservesExactReadReportReference() {
        var resolver = new SkillCandidateResolver(
                new P3B2TestFixtures.CountingTriggerLookup(),
                new P3B2TestFixtures.CountingActionLookup(),
                SkillMigrationPlan.empty());
        var report = P3B2TestFixtures.EMPTY_READ_REPORT;

        ResolvedSkillCandidate candidate = resolver.resolve(
                P3B2TestFixtures.document(
                        P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_TRIGGER_ID, 0, 1),
                        P3B2TestFixtures.envelope(P3B2TestFixtures.UNKNOWN_ACTION_ID, 0, 2)),
                report);

        assertSame(report, candidate.readReport());
    }

    private static Dynamic<JsonElement> canonicalRaw(
            com.yo1no.gramarye.magic.definition.document.SkillDocument document) {
        var encoded = SkillDocumentWriter.write(document, JsonOps.INSTANCE).result().orElseThrow();
        return new Dynamic<>(JsonOps.INSTANCE, encoded);
    }

    private record VersionSkillStep(int fromVersion) implements SkillMigrationStep {
        @Override
        public int toVersion() {
            return fromVersion + 1;
        }

        @Override
        public DataResult<SkillMigrationStepOutput> migrate(Dynamic<?> defensiveSourceCopy) {
            return DataResult.success(new SkillMigrationStepOutput(
                    withVersion(defensiveSourceCopy, toVersion())));
        }
    }

    private static Dynamic<?> withVersion(Dynamic<?> input, int version) {
        return withVersionCaptured(input, version);
    }

    private static <T> Dynamic<T> withVersionCaptured(Dynamic<T> input, int version) {
        return input.set(
                "schema_version",
                new Dynamic<>(input.getOps(), input.getOps().createInt(version)));
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
}
