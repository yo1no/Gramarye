package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.AppearanceValidationTestAccess;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SkillValidationAnalyzerAppearanceTest {
    @Test
    void defaultDecodedAndNoneDoNotProduceFallbackIssues() {
        var decoded = new AppearanceDocument.Decoded(appearance(
                ProfileSelection.inherit(),
                ProfileSelection.inherit(),
                ProfileSelection.inherit()));
        var candidate = candidate(decoded, AppearanceOverrideDocument.none());

        var analysis = analyzer(ProfileAvailabilityView.unknown()).analyze(
                candidate, new ValidationContext(MagicPolicyLimits.DEFAULTS));

        assertFalse(has(analysis, SkillValidationIssueCodes.APPEARANCE_UNPARSED_FALLBACK));
        assertFalse(has(analysis, SkillValidationIssueCodes.APPEARANCE_REJECTED_DEPTH_FALLBACK));
        assertFalse(has(analysis, SkillValidationIssueCodes.APPEARANCE_REJECTED_NODE_LIMIT_FALLBACK));

        var defaultAnalysis = analyzer(ProfileAvailabilityView.unknown()).analyze(
                candidate(AppearanceDocument.defaultAppearance(), AppearanceOverrideDocument.none()),
                new ValidationContext(MagicPolicyLimits.DEFAULTS));
        assertFalse(has(defaultAnalysis, SkillValidationIssueCodes.APPEARANCE_UNPARSED_FALLBACK));
    }

    @Test
    void unparsedAppearanceUsesCallerPolicyForDepthAndNodeWarnings() {
        var raw = nestedTree(4, 5);
        var top = AppearanceValidationTestAccess.unparsedTop(
                new Dynamic<>(JsonOps.INSTANCE, raw));
        var override = AppearanceValidationTestAccess.unparsedOverride(
                new Dynamic<>(JsonOps.INSTANCE, raw));
        var context = contextWithAppearancePolicy(2, 3);

        var analysis = analyzer(ProfileAvailabilityView.unknown()).analyze(
                candidate(top, override), context);

        assertEquals(2, count(analysis, SkillValidationIssueCodes.APPEARANCE_UNPARSED_FALLBACK));
        assertEquals(2, count(analysis, SkillValidationIssueCodes.APPEARANCE_POLICY_DEPTH_EXCEEDED));
        assertEquals(
                2,
                count(analysis, SkillValidationIssueCodes.APPEARANCE_POLICY_NODE_COUNT_EXCEEDED));
        assertTrue(analysis.report().issues().stream()
                .filter(issue -> issue.code().value().getPath().startsWith("appearance.policy"))
                .allMatch(issue -> issue.metadata().equals(ValidationIssueMetadata.none())));
    }

    @Test
    void rejectedAppearanceMapsReasonsWithoutRawAccess() {
        var top = new AppearanceDocument.Rejected(AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED);
        var override = new AppearanceOverrideDocument.Rejected(
                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED);

        var analysis = analyzer(ProfileAvailabilityView.unknown()).analyze(
                candidate(top, override), new ValidationContext(MagicPolicyLimits.DEFAULTS));

        assertTrue(has(analysis, SkillValidationIssueCodes.APPEARANCE_REJECTED_DEPTH_FALLBACK));
        assertTrue(has(
                analysis, SkillValidationIssueCodes.APPEARANCE_REJECTED_NODE_LIMIT_FALLBACK));
        assertFalse(has(analysis, SkillValidationIssueCodes.APPEARANCE_UNPARSED_FALLBACK));
    }

    @Test
    void specifiedProfilesQueryInTopThenNodeAndMapAvailability() {
        var sound = SkillValidationTestFixtures.id("sound");
        var particle = SkillValidationTestFixtures.id("particle");
        var trail = SkillValidationTestFixtures.id("trail");
        var top = new AppearanceDocument.Decoded(appearance(
                ProfileSelection.specified(sound),
                ProfileSelection.specified(particle),
                ProfileSelection.specified(trail)));
        var override = new AppearanceOverrideDocument.Decoded(override(
                ProfileSelection.specified(sound),
                ProfileSelection.inherit(),
                ProfileSelection.disabled()));
        var calls = new ArrayList<String>();
        ProfileAvailabilityView view = (field, id) -> {
            calls.add(field + ":" + id.getPath());
            return switch (field) {
                case SOUND_PROFILE -> ProfileAvailability.MISSING;
                case PARTICLE_PROFILE -> ProfileAvailability.AVAILABLE;
                case TRAIL_PROFILE -> ProfileAvailability.UNKNOWN;
                case PRIMARY_ARGB, SECONDARY_ARGB, INTENSITY_MILLI ->
                        throw new AssertionError("non-profile field queried");
            };
        };

        var analysis = analyzer(view).analyze(
                candidate(top, override), new ValidationContext(MagicPolicyLimits.DEFAULTS));

        assertEquals(List.of(
                "SOUND_PROFILE:sound",
                "PARTICLE_PROFILE:particle",
                "TRAIL_PROFILE:trail",
                "SOUND_PROFILE:sound"), calls);
        assertEquals(2, count(analysis, SkillValidationIssueCodes.APPEARANCE_PROFILE_MISSING));
        assertFalse(analysis.report().hasErrors());
        assertEquals(List.of(
                "appearance.sound_profile",
                "nodes[0].appearance_override.sound_profile"),
                analysis.report().issues().stream()
                        .filter(issue -> issue.code().equals(
                                SkillValidationIssueCodes.APPEARANCE_PROFILE_MISSING))
                        .map(issue -> issue.path().render()).toList());
    }

    @Test
    void profileRuntimeExceptionIsWarningWithoutMessageAndErrorEscapes() {
        var top = new AppearanceDocument.Decoded(appearance(
                ProfileSelection.specified(SkillValidationTestFixtures.id("sound")),
                ProfileSelection.inherit(),
                ProfileSelection.inherit()));
        var analysis = analyzer((field, id) -> {
            throw new IllegalStateException("secret-profile-message");
        }).analyze(
                candidate(top, AppearanceOverrideDocument.none()),
                new ValidationContext(MagicPolicyLimits.DEFAULTS));

        var issue = analysis.report().issues().stream()
                .filter(value -> value.code().equals(
                        SkillValidationIssueCodes.APPEARANCE_PROFILE_AVAILABILITY_EXCEPTION))
                .findFirst().orElseThrow();
        assertEquals(com.yo1no.gramarye.magic.validation.ValidationSeverity.WARNING, issue.severity());
        assertEquals(
                IllegalStateException.class.getName(),
                ((ValidationIssueMetadata.ExceptionClass) issue.metadata()).className());
        assertFalse(analysis.report().toString().contains("secret-profile-message"));
        assertFalse(analysis.report().hasErrors());

        assertThrows(AssertionError.class, () -> analyzer((field, id) -> {
            throw new AssertionError("must escape");
        }).analyze(
                candidate(top, AppearanceOverrideDocument.none()),
                new ValidationContext(MagicPolicyLimits.DEFAULTS)));
    }

    @Test
    void profileViewIsNotQueriedForDefaultUnparsedRejectedOrDisabled() {
        var calls = new AtomicInteger();
        ProfileAvailabilityView view = (field, id) -> {
            calls.incrementAndGet();
            return ProfileAvailability.AVAILABLE;
        };
        var disabled = new AppearanceDocument.Decoded(appearance(
                ProfileSelection.disabled(),
                ProfileSelection.inherit(),
                ProfileSelection.inherit()));
        var unparsed = AppearanceValidationTestAccess.unparsedOverride(
                new Dynamic<>(JsonOps.INSTANCE, new JsonPrimitive("bad")));

        analyzer(view).analyze(candidate(disabled, unparsed),
                new ValidationContext(MagicPolicyLimits.DEFAULTS));
        analyzer(view).analyze(candidate(
                        AppearanceDocument.defaultAppearance(),
                        new AppearanceOverrideDocument.Rejected(
                                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED)),
                new ValidationContext(MagicPolicyLimits.DEFAULTS));

        assertEquals(0, calls.get());
    }

    private static SkillValidationAnalyzer analyzer(ProfileAvailabilityView view) {
        return new SkillValidationAnalyzer(new NodeProjectionResolver(), view);
    }

    private static com.yo1no.gramarye.magic.definition.resolution.ResolvedSkillCandidate candidate(
            AppearanceDocument top,
            AppearanceOverrideDocument override) {
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(
                SkillValidationTestFixtures.triggerProjection(
                        SourceSelection.NONE, TargetSelection.NONE, true));
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(
                SkillValidationTestFixtures.actionProjection(
                        SourceSelection.NONE, TargetSelection.NONE, Set.of()));
        return SkillValidationTestFixtures.candidate(
                0,
                top,
                new SkillDocumentReadReport(List.of(), false),
                new PipelineFactReport(List.of(), false),
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(trigger),
                        SkillValidationTestFixtures.resolvedAction(action),
                        override));
    }

    private static AppearanceDefinition appearance(
            ProfileSelection sound,
            ProfileSelection particle,
            ProfileSelection trail) {
        return new AppearanceDefinition(
                OptionalInt.of(0x10203040),
                OptionalInt.empty(),
                sound,
                particle,
                trail,
                OptionalInt.of(1_000));
    }

    private static AppearanceOverride override(
            ProfileSelection sound,
            ProfileSelection particle,
            ProfileSelection trail) {
        return new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.empty(),
                sound,
                particle,
                trail,
                OptionalInt.empty());
    }

    private static JsonObject nestedTree(int depth, int leafCount) {
        var root = new JsonObject();
        var current = root;
        for (var index = 1; index < depth; index++) {
            var child = new JsonObject();
            current.add("child", child);
            current = child;
        }
        var values = new JsonArray();
        for (var index = 0; index < leafCount; index++) {
            values.add(index);
        }
        current.add("values", values);
        return root;
    }

    private static ValidationContext contextWithAppearancePolicy(int depth, int nodes) {
        var defaults = MagicPolicyLimits.DEFAULTS;
        return new ValidationContext(new MagicPolicyLimits(
                defaults.maxNodes(),
                defaults.maxStringLength(),
                defaults.maxRawPayloadBytes(),
                defaults.maxRuntimeTags(),
                defaults.maxVisitedTargets(),
                defaults.maxAppearanceIntensity(),
                depth,
                nodes,
                defaults.maxSkillDocumentDepth(),
                defaults.maxSkillDocumentBytes(),
                defaults.maxSkillDocumentTreeNodes()));
    }

    private static boolean has(SkillValidationAnalysis analysis, ValidationIssueCode code) {
        return count(analysis, code) > 0;
    }

    private static int count(SkillValidationAnalysis analysis, ValidationIssueCode code) {
        return (int) analysis.report().issues().stream()
                .filter(issue -> issue.code().equals(code))
                .count();
    }
}
