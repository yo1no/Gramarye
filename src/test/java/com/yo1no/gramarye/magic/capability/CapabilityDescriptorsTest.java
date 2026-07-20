package com.yo1no.gramarye.magic.capability;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CapabilityDescriptorsTest {
    private static final TriggerEventKind EVENT_KIND = new TriggerEventKind(id("test_event"));

    @Test
    void triggerCapabilitiesDefensivelyCopyEverySet() {
        var eventKinds = new HashSet<>(Set.of(EVENT_KIND));
        var scopes = new HashSet<>(Set.of(TriggerSourceScope.CURRENT_INSTANCE));
        var granularities = new HashSet<>(Set.of(TriggerGranularity.PER_EVENT));
        var capabilities = new TriggerCapabilities(
                SourceRequirement.NONE,
                TargetRequirement.REQUIRED,
                false,
                eventKinds,
                scopes,
                granularities);

        eventKinds.clear();
        scopes.clear();
        granularities.clear();

        assertAll(
                () -> assertEquals(Set.of(EVENT_KIND), capabilities.eventKinds()),
                () -> assertEquals(Set.of(TriggerSourceScope.CURRENT_INSTANCE), capabilities.supportedSourceScopes()),
                () -> assertEquals(Set.of(TriggerGranularity.PER_EVENT), capabilities.supportedGranularities()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> capabilities.eventKinds().add(EVENT_KIND)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> capabilities.supportedSourceScopes().add(TriggerSourceScope.ANY_SOURCE)),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> capabilities.supportedGranularities().add(TriggerGranularity.PER_TARGET)));
    }

    @Test
    void triggerCapabilitiesRejectMissingStructuralCapabilities() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new TriggerCapabilities(
                        null,
                        TargetRequirement.NONE,
                        false,
                        Set.of(EVENT_KIND),
                        Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                        Set.of(TriggerGranularity.PER_EVENT))),
                () -> assertThrows(IllegalArgumentException.class, () -> new TriggerCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.NONE,
                        false,
                        Set.of(),
                        Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                        Set.of(TriggerGranularity.PER_EVENT))),
                () -> assertThrows(IllegalArgumentException.class, () -> new TriggerCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.NONE,
                        false,
                        Set.of(EVENT_KIND),
                        Set.of(),
                        Set.of(TriggerGranularity.PER_EVENT))),
                () -> assertThrows(IllegalArgumentException.class, () -> new TriggerCapabilities(
                        SourceRequirement.NONE,
                        TargetRequirement.NONE,
                        false,
                        Set.of(EVENT_KIND),
                        Set.of(TriggerSourceScope.CURRENT_INSTANCE),
                        Set.of())));
    }

    @Test
    void actionCapabilitiesDefensivelyCopyOutputKinds() {
        var outputKinds = new HashSet<>(Set.of(ActionOutputKind.EFFECT));
        var capabilities = actionCapabilities(
                TargetRequirement.REQUIRED,
                true,
                outputKinds,
                ControlClass.SOFT_CONTROL,
                AppearanceParameterPolicy.none());

        outputKinds.clear();

        assertAll(
                () -> assertEquals(Set.of(ActionOutputKind.EFFECT), capabilities.outputKinds()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> capabilities.outputKinds().add(ActionOutputKind.PROJECTILE)),
                () -> assertEquals(ControlClass.SOFT_CONTROL, capabilities.controlClass()));
    }

    @Test
    void actionCapabilitiesAllowExplicitEmptyPolicies() {
        var capabilities = actionCapabilities(
                TargetRequirement.NONE,
                false,
                Set.of(),
                ControlClass.NONE,
                AppearanceParameterPolicy.none());

        assertAll(
                () -> assertTrue(capabilities.outputKinds().isEmpty()),
                () -> assertTrue(capabilities.appearanceParameters().integerRanges().isEmpty()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> capabilities.appearanceParameters().integerRanges().put(
                                id("intensity"),
                                new AppearanceParameterPolicy.IntRange(0, 1))));
    }

    @Test
    void actionCapabilitiesRejectSelfTargetWithoutTarget() {
        assertThrows(IllegalArgumentException.class, () -> actionCapabilities(
                TargetRequirement.NONE,
                true,
                Set.of(),
                ControlClass.NONE,
                AppearanceParameterPolicy.none()));
    }

    @Test
    void appearanceParameterPolicyDefensivelyCopiesTypedRanges() {
        var parameter = id("intensity");
        var source = new HashMap<ResourceLocation, AppearanceParameterPolicy.IntRange>();
        source.put(parameter, new AppearanceParameterPolicy.IntRange(0, 1_000));
        var policy = new AppearanceParameterPolicy(source);

        source.clear();

        assertAll(
                () -> assertEquals(
                        Map.of(parameter, new AppearanceParameterPolicy.IntRange(0, 1_000)),
                        policy.integerRanges()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> policy.integerRanges().clear()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AppearanceParameterPolicy.IntRange(2, 1)));
    }

    @Test
    void controlClassesRemainTheMinimalFrozenSet() {
        assertArrayEquals(
                new ControlClass[]{ControlClass.NONE, ControlClass.SOFT_CONTROL, ControlClass.HARD_CONTROL},
                ControlClass.values());
    }

    private static ActionCapabilities actionCapabilities(
            TargetRequirement targetRequirement,
            boolean allowsSelfTarget,
            Set<ActionOutputKind> outputKinds,
            ControlClass controlClass,
            AppearanceParameterPolicy appearanceParameters) {
        return new ActionCapabilities(
                SourceRequirement.NONE,
                targetRequirement,
                allowsSelfTarget,
                outputKinds,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                controlClass,
                appearanceParameters);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }
}
