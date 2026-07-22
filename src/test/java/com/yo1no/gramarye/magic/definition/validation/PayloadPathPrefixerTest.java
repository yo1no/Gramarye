package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationPathSegment;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PayloadPathPrefixerTest {
    @Test
    void typedPrefixPreservesFieldAndIndexSegments() {
        var prefix = ValidationPath.empty()
                .field("nodes")
                .index(3)
                .field("trigger")
                .field("payload");
        var relative = ValidationPath.empty().field("source").index(2);

        var success = assertInstanceOf(
                PayloadPathPrefixer.Result.Success.class,
                PayloadPathPrefixer.prefix(prefix, relative));

        assertEquals("nodes[3].trigger.payload.source[2]", success.path().render());
        assertEquals(
                List.of(
                        new ValidationPathSegment.Field("nodes"),
                        new ValidationPathSegment.Index(3),
                        new ValidationPathSegment.Field("trigger"),
                        new ValidationPathSegment.Field("payload"),
                        new ValidationPathSegment.Field("source"),
                        new ValidationPathSegment.Index(2)),
                success.path().segments());
    }

    @Test
    void segmentAndRenderOverflowReturnExactLimitMetadata() {
        var prefix = ValidationPath.empty().field("payload");
        var segmentRelative = new ValidationPath(IntStream.range(
                        0, MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS)
                .mapToObj(index -> (ValidationPathSegment) new ValidationPathSegment.Index(index))
                .toList());
        var segmentOverflow = assertInstanceOf(
                PayloadPathPrefixer.Result.Overflow.class,
                PayloadPathPrefixer.prefix(prefix, segmentRelative));
        assertEquals(
                new ValidationIssueMetadata.Limit(
                        MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS + 1,
                        MagicSafetyCeilings.MAX_VALIDATION_PATH_SEGMENTS),
                segmentOverflow.metadata());

        var renderRelative = ValidationPath.empty().field(
                "x".repeat(MagicSafetyCeilings.MAX_STRING_LENGTH));
        var renderOverflow = assertInstanceOf(
                PayloadPathPrefixer.Result.Overflow.class,
                PayloadPathPrefixer.prefix(prefix, renderRelative));
        assertEquals(
                new ValidationIssueMetadata.Limit(
                        "payload".length() + 1 + MagicSafetyCeilings.MAX_STRING_LENGTH,
                        MagicSafetyCeilings.MAX_STRING_LENGTH),
                renderOverflow.metadata());
    }

    @Test
    void analysisConstructorEnforcesSchemaAndInspectionContract() {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, true));
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));
        var current = SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action)));
        var inspected = new NodeProjectionResolver().inspect(current);

        new SkillValidationAnalysis(current, Optional.of(inspected), ValidationResult.valid());
        assertThrows(IllegalArgumentException.class, () ->
                new SkillValidationAnalysis(current, Optional.empty(), ValidationResult.valid()));

        var future = SkillValidationTestFixtures.candidate(
                1,
                com.yo1no.gramarye.magic.definition.document.AppearanceDocument.defaultAppearance(),
                new com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport(
                        List.of(), false),
                new com.yo1no.gramarye.magic.definition.migration.PipelineFactReport(
                        List.of(), false));
        assertThrows(IllegalArgumentException.class, () ->
                new SkillValidationAnalysis(future, Optional.empty(), ValidationResult.valid()));
    }
}
