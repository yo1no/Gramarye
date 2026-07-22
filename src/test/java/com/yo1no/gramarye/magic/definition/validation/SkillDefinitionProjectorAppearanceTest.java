package com.yo1no.gramarye.magic.definition.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceRejectionCode;
import com.yo1no.gramarye.magic.definition.document.AppearanceValidationTestAccess;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.inspection.ActionInspectionState;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.inspection.TriggerInspectionState;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SkillDefinitionProjectorAppearanceTest {
    @Test
    void topLevelAppearanceMappingIsExhaustiveRawFreeAndKeepsTypedProfiles() {
        var typedValue = appearance();
        var cases = List.of(
                new TopCase(AppearanceDocument.defaultAppearance(),
                        RuntimeNeutralAppearance.Default.INSTANCE),
                new TopCase(new AppearanceDocument.Decoded(typedValue),
                        new RuntimeNeutralAppearance.Typed(typedValue)),
                new TopCase(AppearanceValidationTestAccess.unparsedTop(
                                new Dynamic<>(JsonOps.INSTANCE, rawAppearance())),
                        new RuntimeNeutralAppearance.Fallback(AppearanceFallbackReason.UNPARSED)),
                new TopCase(new AppearanceDocument.Rejected(
                                AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED),
                        new RuntimeNeutralAppearance.Fallback(
                                AppearanceFallbackReason.REJECTED_DEPTH_LIMIT)),
                new TopCase(new AppearanceDocument.Rejected(
                                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED),
                        new RuntimeNeutralAppearance.Fallback(
                                AppearanceFallbackReason.REJECTED_NODE_LIMIT)));

        for (var testCase : cases) {
            var definition = project(testCase.source(), AppearanceOverrideDocument.none()).definition();
            assertEquals(testCase.expected(), definition.appearance());
        }

        var typed = assertInstanceOf(
                RuntimeNeutralAppearance.Typed.class,
                project(new AppearanceDocument.Decoded(typedValue),
                        AppearanceOverrideDocument.none()).definition().appearance());
        assertSame(typedValue, typed.definition());
        assertEquals(ProfileSelection.specified(SkillValidationTestFixtures.id("sound_profile")),
                typed.definition().soundProfile());
    }

    @Test
    void overrideAppearanceMappingIsExhaustiveRawFreeAndKeepsTypedProfiles() {
        var typedValue = appearanceOverride();
        var cases = List.of(
                new OverrideCase(AppearanceOverrideDocument.none(),
                        RuntimeNeutralAppearanceOverride.None.INSTANCE),
                new OverrideCase(new AppearanceOverrideDocument.Decoded(typedValue),
                        new RuntimeNeutralAppearanceOverride.Typed(typedValue)),
                new OverrideCase(AppearanceValidationTestAccess.unparsedOverride(
                                new Dynamic<>(JsonOps.INSTANCE, rawAppearance())),
                        new RuntimeNeutralAppearanceOverride.Fallback(
                                AppearanceFallbackReason.UNPARSED)),
                new OverrideCase(new AppearanceOverrideDocument.Rejected(
                                AppearanceRejectionCode.DEPTH_LIMIT_EXCEEDED),
                        new RuntimeNeutralAppearanceOverride.Fallback(
                                AppearanceFallbackReason.REJECTED_DEPTH_LIMIT)),
                new OverrideCase(new AppearanceOverrideDocument.Rejected(
                                AppearanceRejectionCode.NODE_LIMIT_EXCEEDED),
                        new RuntimeNeutralAppearanceOverride.Fallback(
                                AppearanceFallbackReason.REJECTED_NODE_LIMIT)));

        for (var testCase : cases) {
            var projected = project(AppearanceDocument.defaultAppearance(), testCase.source())
                    .definition().nodes().getFirst().appearanceOverride();
            assertEquals(testCase.expected(), projected);
        }

        var typed = assertInstanceOf(
                RuntimeNeutralAppearanceOverride.Typed.class,
                project(AppearanceDocument.defaultAppearance(),
                        new AppearanceOverrideDocument.Decoded(typedValue))
                        .definition().nodes().getFirst().appearanceOverride());
        assertSame(typedValue, typed.override());
        assertEquals(ProfileSelection.specified(SkillValidationTestFixtures.id("particle_profile")),
                typed.override().particleProfile());
    }

    @Test
    void projectionDoesNotReenterDescriptorInspectionValidationCapabilitiesOrCodec() {
        var triggerProjection = SkillValidationTestFixtures.triggerProjection(
                SourceSelection.NONE, TargetSelection.NONE, false);
        var actionProjection = SkillValidationTestFixtures.actionProjection(
                SourceSelection.NONE, TargetSelection.NONE, Set.of());
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(triggerProjection);
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(actionProjection);
        var candidate = SkillValidationTestFixtures.candidate(SkillValidationTestFixtures.node(
                0,
                SkillValidationTestFixtures.resolvedTrigger(trigger),
                SkillValidationTestFixtures.resolvedAction(action),
                AppearanceOverrideDocument.none()));
        var analysis = SkillValidationTestFixtures.analysis(
                candidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(triggerProjection),
                        new ActionInspectionState.Success(actionProjection)));
        var projector = new SkillDefinitionProjector();

        projector.project(analysis);
        projector.project(analysis);

        assertEquals(0, trigger.inspectorAccessorCalls());
        assertEquals(0, trigger.capabilityCalls());
        assertEquals(0, trigger.validatorCalls());
        assertEquals(0, trigger.codecCalls());
        assertEquals(0, action.inspectorAccessorCalls());
        assertEquals(0, action.capabilityCalls());
        assertEquals(0, action.validatorCalls());
        assertEquals(0, action.codecCalls());
    }

    @Test
    void runtimeNeutralAppearanceValuesRejectNullComponents() {
        assertThrows(NullPointerException.class, () ->
                new RuntimeNeutralAppearance.Typed(null));
        assertThrows(NullPointerException.class, () ->
                new RuntimeNeutralAppearance.Fallback(null));
        assertThrows(NullPointerException.class, () ->
                new RuntimeNeutralAppearanceOverride.Typed(null));
        assertThrows(NullPointerException.class, () ->
                new RuntimeNeutralAppearanceOverride.Fallback(null));
    }

    private static SkillValidationOutcome.Accepted project(
            AppearanceDocument top,
            AppearanceOverrideDocument override) {
        var triggerProjection = SkillValidationTestFixtures.triggerProjection(
                SourceSelection.NONE, TargetSelection.NONE, false);
        var actionProjection = SkillValidationTestFixtures.actionProjection(
                SourceSelection.NONE, TargetSelection.NONE, Set.of());
        var trigger = SkillValidationTestFixtures.TriggerDescriptor.successful(triggerProjection);
        var action = SkillValidationTestFixtures.ActionDescriptor.successful(actionProjection);
        var candidate = SkillValidationTestFixtures.candidate(
                0,
                top,
                new com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport(
                        List.of(), false),
                new com.yo1no.gramarye.magic.definition.migration.PipelineFactReport(
                        List.of(), false),
                SkillValidationTestFixtures.node(
                        0,
                        SkillValidationTestFixtures.resolvedTrigger(trigger),
                        SkillValidationTestFixtures.resolvedAction(action),
                        override));
        var analysis = SkillValidationTestFixtures.analysis(
                candidate,
                ValidationResult.valid(),
                SkillValidationTestFixtures.inspectedNode(
                        0,
                        new TriggerInspectionState.Success(triggerProjection),
                        new ActionInspectionState.Success(actionProjection)));
        return assertInstanceOf(
                SkillValidationOutcome.Accepted.class,
                new SkillDefinitionProjector().project(analysis));
    }

    private static AppearanceDefinition appearance() {
        return new AppearanceDefinition(
                OptionalInt.of(0xff112233),
                OptionalInt.empty(),
                ProfileSelection.specified(SkillValidationTestFixtures.id("sound_profile")),
                ProfileSelection.inherit(),
                ProfileSelection.disabled(),
                OptionalInt.of(1_000));
    }

    private static AppearanceOverride appearanceOverride() {
        return new AppearanceOverride(
                OptionalInt.empty(),
                OptionalInt.of(0xff445566),
                ProfileSelection.inherit(),
                ProfileSelection.specified(SkillValidationTestFixtures.id("particle_profile")),
                ProfileSelection.disabled(),
                OptionalInt.of(2_000));
    }

    private static JsonObject rawAppearance() {
        var nested = new JsonObject();
        nested.addProperty("secret", "raw-must-not-enter-projection");
        var root = new JsonObject();
        root.add("nested", nested);
        return root;
    }

    private record TopCase(
            AppearanceDocument source,
            RuntimeNeutralAppearance expected) {
    }

    private record OverrideCase(
            AppearanceOverrideDocument source,
            RuntimeNeutralAppearanceOverride expected) {
    }
}
