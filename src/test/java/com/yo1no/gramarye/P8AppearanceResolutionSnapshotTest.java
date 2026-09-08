package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverride;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Direct A/B proof for the package-private, call-scoped P8 resolution snapshot. */
final class P8AppearanceResolutionSnapshotTest {
    @Test
    void oneCaptureBindsSelectedFallbackCapabilityCostAndGenerationToA() {
        var parameterId = P8S2TestFixtures.id("snapshot_density");
        var selectedSound = P8S2TestFixtures.id("snapshot_selected_sound");
        var selectedParticle = P8S2TestFixtures.id("snapshot_selected_particle");
        var selectedTrail = P8S2TestFixtures.id("snapshot_selected_trail");
        var sources = Map.of(
                selectedSound,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SNAPSHOT_SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(700)),
                selectedParticle,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SNAPSHOT_PARTICLE_TYPE_ID,
                        P8S2TestFixtures.particleConfiguration(7)),
                selectedTrail,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SNAPSHOT_TRAIL_TYPE_ID,
                        P8S2TestFixtures.trailConfiguration(5)));
        var candidateA = P8ServerPresentationService.loadCandidateForTesting(
                P8S2TestFixtures.snapshotRegistry(false, parameterId), sources);
        var candidateB = P8ServerPresentationService.loadCandidateForTesting(
                P8S2TestFixtures.snapshotRegistry(true, parameterId), sources);
        var service = P8ServerPresentationService.create();
        activate(service, candidateA);

        {
            var capturedA = service.captureAppearanceResolutionSnapshot().orElseThrow();
            var stableAView = capturedA.profiles();
            var aParticleBefore = stableAView.find(selectedParticle).orElseThrow();

            activate(service, candidateB);

            var selectedA = resolveSelected(
                    stableAView,
                    parameterId,
                    selectedSound,
                    selectedParticle,
                    selectedTrail);
            var fallbackA = resolveFallback(stableAView, selectedSound);
            var eventA = acceptedEvent(
                    capturedA.catalogGeneration(), selectedA.appearance(), 1L);
            var aSound = stableAView.find(selectedSound).orElseThrow();
            var aParticle = stableAView.find(selectedParticle).orElseThrow();
            var aTrail = stableAView.find(selectedTrail).orElseThrow();
            var aDefaultSound = stableAView
                    .find(AppearanceSemantics.DEFAULT_SOUND_PROFILE)
                    .orElseThrow();
            var aDefaultParticle = stableAView
                    .find(AppearanceSemantics.DEFAULT_PARTICLE_PROFILE)
                    .orElseThrow();
            var aDefaultTrail = stableAView
                    .find(AppearanceSemantics.DEFAULT_TRAIL_PROFILE)
                    .orElseThrow();

            assertAll(
                    () -> assertEquals(1L, capturedA.catalogGeneration()),
                    () -> assertSame(stableAView, capturedA.profiles()),
                    () -> assertEquals(aParticleBefore, aParticle),
                    () -> assertEquals(1L, eventA.catalogGeneration()),
                    () -> assertEquals(
                            selectedA.appearance().wireAppearance(), eventA.appearance()),
                    () -> assertEquals(
                            AppearancePatchOutcome.APPLIED, selectedA.patchOutcome()),
                    () -> assertEquals(Optional.of(selectedSound),
                            selectedA.appearance().soundProfile().id()),
                    () -> assertEquals(Optional.of(selectedParticle),
                            selectedA.appearance().particleProfile().id()),
                    () -> assertEquals(Optional.of(selectedTrail),
                            selectedA.appearance().trailProfile().id()),
                    () -> assertEquals(Map.of(parameterId, 5),
                            selectedA.appearance().parameters()),
                    () -> assertTrue(aSound.capabilities().supportsPrimaryColor()),
                    () -> assertTrue(aParticle.capabilities().supportsDirection()),
                    () -> assertTrue(aTrail.capabilities().supportsTrail()),
                    () -> assertEquals(new ProfileCost(0, 3, 0, 0, 1),
                            aSound.estimatedCost()),
                    () -> assertEquals(new ProfileCost(7, 0, 0, 0, 20),
                            aParticle.estimatedCost()),
                    () -> assertEquals(new ProfileCost(0, 0, 1, 5, 16),
                            aTrail.estimatedCost()),
                    () -> assertEquals(new ProfileCost(0, 1, 0, 0, 1),
                            aDefaultSound.estimatedCost()),
                    () -> assertEquals(new ProfileCost(8, 0, 0, 0, 20),
                            aDefaultParticle.estimatedCost()),
                    () -> assertEquals(new ProfileCost(0, 0, 1, 8, 16),
                            aDefaultTrail.estimatedCost()),
                    () -> assertEquals(Optional.of(AppearanceSemantics.DEFAULT_SOUND_PROFILE),
                            fallbackA.appearance().soundProfile().id()),
                    () -> assertEquals(
                            ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT,
                            fallbackA.appearance().soundProfile().reason()),
                    () -> assertEquals(Optional.of(
                                    AppearanceSemantics.DEFAULT_PARTICLE_PROFILE),
                            fallbackA.appearance().particleProfile().id()),
                    () -> assertEquals(
                            ProfileResolutionReason.WRONG_CHANNEL_SELECTION_USED_DEFAULT,
                            fallbackA.appearance().particleProfile().reason()),
                    () -> assertEquals(Optional.of(AppearanceSemantics.DEFAULT_TRAIL_PROFILE),
                            fallbackA.appearance().trailProfile().id()),
                    () -> assertEquals(
                            ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT,
                            fallbackA.appearance().trailProfile().reason()));
        }

        var capturedB = service.captureAppearanceResolutionSnapshot().orElseThrow();
        var selectedB = resolveSelected(
                capturedB.profiles(),
                parameterId,
                selectedSound,
                selectedParticle,
                selectedTrail);
        var fallbackB = resolveFallback(capturedB.profiles(), selectedSound);
        var eventB = acceptedEvent(
                capturedB.catalogGeneration(), selectedB.appearance(), 2L);
        var bSound = capturedB.profiles().find(selectedSound).orElseThrow();
        var bParticle = capturedB.profiles().find(selectedParticle).orElseThrow();
        var bTrail = capturedB.profiles().find(selectedTrail).orElseThrow();
        var bDefaultSound = capturedB.profiles()
                .find(AppearanceSemantics.DEFAULT_SOUND_PROFILE)
                .orElseThrow();
        var bDefaultParticle = capturedB.profiles()
                .find(AppearanceSemantics.DEFAULT_PARTICLE_PROFILE)
                .orElseThrow();
        var bDefaultTrail = capturedB.profiles()
                .find(AppearanceSemantics.DEFAULT_TRAIL_PROFILE)
                .orElseThrow();

        assertAll(
                () -> assertEquals(2L, capturedB.catalogGeneration()),
                () -> assertSame(capturedB.profiles(), capturedB.profiles()),
                () -> assertEquals(2L, eventB.catalogGeneration()),
                () -> assertEquals(
                        selectedB.appearance().wireAppearance(), eventB.appearance()),
                () -> assertEquals(
                        AppearancePatchOutcome.IGNORED_PARAMETER_OUT_OF_RANGE,
                        selectedB.patchOutcome()),
                () -> assertEquals(Optional.of(selectedSound),
                        selectedB.appearance().soundProfile().id()),
                () -> assertEquals(Optional.of(selectedParticle),
                        selectedB.appearance().particleProfile().id()),
                () -> assertEquals(Optional.of(selectedTrail),
                        selectedB.appearance().trailProfile().id()),
                () -> assertTrue(selectedB.appearance().parameters().isEmpty()),
                () -> assertFalse(bSound.capabilities().supportsPrimaryColor()),
                () -> assertFalse(bParticle.capabilities().supportsDirection()),
                () -> assertTrue(bParticle.capabilities().supportsSecondaryColor()),
                () -> assertFalse(bTrail.capabilities().supportsTrail()),
                () -> assertEquals(new ProfileCost(0, 4, 0, 0, 1),
                        bSound.estimatedCost()),
                () -> assertEquals(new ProfileCost(8, 0, 0, 0, 20),
                        bParticle.estimatedCost()),
                () -> assertEquals(new ProfileCost(0, 0, 1, 6, 16),
                        bTrail.estimatedCost()),
                () -> assertEquals(new ProfileCost(0, 2, 0, 0, 1),
                        bDefaultSound.estimatedCost()),
                () -> assertEquals(new ProfileCost(9, 0, 0, 0, 20),
                        bDefaultParticle.estimatedCost()),
                () -> assertEquals(new ProfileCost(0, 0, 1, 9, 16),
                        bDefaultTrail.estimatedCost()),
                () -> assertEquals(Optional.of(AppearanceSemantics.DEFAULT_SOUND_PROFILE),
                        fallbackB.appearance().soundProfile().id()),
                () -> assertEquals(
                        ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT,
                        fallbackB.appearance().soundProfile().reason()),
                () -> assertEquals(Optional.of(AppearanceSemantics.DEFAULT_PARTICLE_PROFILE),
                        fallbackB.appearance().particleProfile().id()),
                () -> assertEquals(
                        ProfileResolutionReason.WRONG_CHANNEL_SELECTION_USED_DEFAULT,
                        fallbackB.appearance().particleProfile().reason()),
                () -> assertEquals(Optional.of(AppearanceSemantics.DEFAULT_TRAIL_PROFILE),
                        fallbackB.appearance().trailProfile().id()),
                () -> assertEquals(
                        ProfileResolutionReason.MISSING_SELECTION_USED_DEFAULT,
                        fallbackB.appearance().trailProfile().reason()));

        var pendingIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(pendingIdentity, candidateA);
        final long pendingGeneration;
        final PresentationProfileDescriptor pendingParticle;
        {
            var capturedWhilePending =
                    service.captureAppearanceResolutionSnapshot().orElseThrow();
            pendingGeneration = capturedWhilePending.catalogGeneration();
            pendingParticle = capturedWhilePending.profiles()
                    .find(selectedParticle)
                    .orElseThrow();
        }
        service.beginReloadCycleForTesting(
                P8ServerPresentationService.newReloadIdentityForTesting());
        {
            var capturedAfterFailedCompletion =
                    service.captureAppearanceResolutionSnapshot().orElseThrow();
            assertAll(
                    () -> assertEquals(2L, pendingGeneration),
                    () -> assertEquals(bParticle, pendingParticle),
                    () -> assertEquals(2L,
                            capturedAfterFailedCompletion.catalogGeneration()),
                    () -> assertEquals(
                            bParticle,
                            capturedAfterFailedCompletion.profiles()
                                    .find(selectedParticle).orElseThrow()));
        }
    }

    @Test
    void resolvedProfileCostFeedsDegradationWithoutMutatingTheCapturedCatalog() {
        var parameterId = P8S2TestFixtures.id("immutability_density");
        var selectedParticle = P8S2TestFixtures.id("immutability_particle");
        var service = P8ServerPresentationService.create();
        activate(
                service,
                P8ServerPresentationService.loadCandidateForTesting(
                        P8S2TestFixtures.snapshotRegistry(false, parameterId),
                        Map.of(
                                selectedParticle,
                                P8S2TestFixtures.profile(
                                        P8S2TestFixtures.SNAPSHOT_PARTICLE_TYPE_ID,
                                        P8S2TestFixtures.particleConfiguration(23)))));
        var captured = service.captureAppearanceResolutionSnapshot().orElseThrow();
        var descriptor = captured.profiles().find(selectedParticle).orElseThrow();
        var stored = descriptor.estimatedCost();
        var resolved = resolveParticle(captured.profiles(), selectedParticle);
        var event = acceptedEvent(captured.catalogGeneration(), resolved.appearance(), 1L);

        var degraded = PresentationDegradation.decide(
                new PresentationDegradation.Candidate(
                        stored.particleStarts(), 1, false, false),
                new PresentationDegradation.Constraints(7L, false, 1, false, false),
                Optional.empty());
        var sameGeneration = PresentationCoalescing.decide(
                PresentationCoalescing.Value.create(
                        identity(captured.catalogGeneration(), selectedParticle), 1L),
                PresentationCoalescing.Value.create(
                        identity(captured.catalogGeneration(), selectedParticle), 2L));
        var differentGeneration = PresentationCoalescing.decide(
                PresentationCoalescing.Value.create(
                        identity(captured.catalogGeneration(), selectedParticle), 3L),
                PresentationCoalescing.Value.create(
                        identity(captured.catalogGeneration() + 1L, selectedParticle), 4L));

        assertAll(
                () -> assertEquals(Optional.of(selectedParticle),
                        resolved.appearance().particleProfile().id()),
                () -> assertEquals(ProfileResolutionReason.SELECTED,
                        resolved.appearance().particleProfile().reason()),
                () -> assertEquals(1L, event.catalogGeneration()),
                () -> assertEquals(Optional.of(selectedParticle),
                        event.appearance().particleProfileId()),
                () -> assertEquals(new ProfileCost(23, 0, 0, 0, 20), stored),
                () -> assertEquals(7,
                        degraded.candidate().orElseThrow().particleCount()),
                () -> assertEquals(
                        PresentationCoalescing.Outcome.EXACT_IDENTITY_COALESCED,
                        sameGeneration.outcome()),
                () -> assertEquals(
                        PresentationCoalescing.Outcome.DISTINCT,
                        differentGeneration.outcome()),
                () -> assertEquals(stored, descriptor.estimatedCost()),
                () -> assertEquals(
                        descriptor,
                        captured.profiles().find(selectedParticle).orElseThrow()));
    }

    @Test
    void semanticAndEventOutputsCannotRetainCatalogOrRawProfileObjects() {
        var forbiddenTypes = Set.of(
                P8ServerPresentationService.class.getName(),
                P8ServerPresentationService.AppearanceResolutionSnapshot.class.getName(),
                PresentationProfileView.class.getName(),
                "com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration",
                "com.yo1no.gramarye.magic.presentation.api.ProfileType",
                "com.mojang.serialization.Dynamic",
                "com.google.gson.JsonElement");

        for (var outputType : List.of(
                AppearanceResolution.class,
                EffectiveAppearance.class,
                ResolvedProfile.class,
                PresentationAppearance.class,
                PresentationEvent.class)) {
            for (var field : outputType.getDeclaredFields()) {
                var retainedType = field.getGenericType().getTypeName();
                for (var forbiddenType : forbiddenTypes) {
                    assertFalse(
                            retainedType.contains(forbiddenType),
                            () -> outputType.getName() + "." + field.getName()
                                    + " retains " + retainedType);
                }
            }
        }
    }

    private static AppearanceResolution resolveSelected(
            PresentationProfileView profiles,
            ResourceLocation parameterId,
            ResourceLocation sound,
            ResourceLocation particle,
            ResourceLocation trail) {
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                new ProfileSelection.Specified(sound),
                new ProfileSelection.Specified(particle),
                new ProfileSelection.Specified(trail),
                OptionalInt.empty()));
        var patch = new AppearanceEventPatch(
                new AppearanceOverride(
                        OptionalInt.of(0xFF112233),
                        OptionalInt.of(0xFF445566),
                        ProfileSelection.Inherit.INSTANCE,
                        ProfileSelection.Inherit.INSTANCE,
                        ProfileSelection.Inherit.INSTANCE,
                        OptionalInt.of(750)),
                Map.of(parameterId, 5));
        return AppearanceSemantics.resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.of(patch),
                new AppearanceParameterPolicy(Map.of(
                        parameterId, new AppearanceParameterPolicy.IntRange(0, 10))),
                profiles);
    }

    private static AppearanceResolution resolveFallback(
            PresentationProfileView profiles, ResourceLocation wrongParticle) {
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                new ProfileSelection.Specified(P8S2TestFixtures.id("missing_sound")),
                new ProfileSelection.Specified(wrongParticle),
                new ProfileSelection.Specified(P8S2TestFixtures.id("missing_trail")),
                OptionalInt.empty()));
        return AppearanceSemantics.resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                AppearanceParameterPolicy.none(),
                profiles);
    }

    private static AppearanceResolution resolveParticle(
            PresentationProfileView profiles, ResourceLocation particle) {
        var skill = new RuntimeNeutralAppearance.Typed(new AppearanceDefinition(
                OptionalInt.empty(),
                OptionalInt.empty(),
                ProfileSelection.Disabled.INSTANCE,
                new ProfileSelection.Specified(particle),
                ProfileSelection.Disabled.INSTANCE,
                OptionalInt.empty()));
        return AppearanceSemantics.resolve(
                skill,
                RuntimeNeutralAppearanceOverride.None.INSTANCE,
                Optional.empty(),
                AppearanceParameterPolicy.none(),
                profiles);
    }

    private static PresentationEvent acceptedEvent(
            long catalogGeneration, EffectiveAppearance appearance, long sequence) {
        return assertInstanceOf(
                        AcceptedPresentationEvent.class,
                        PresentationEvent.createServer(
                                catalogGeneration,
                                PresentationEventKind.HIT.wireCode(),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                ResourceLocation.withDefaultNamespace("overworld"),
                                0.0D,
                                0.0D,
                                0.0D,
                                1.0D,
                                0.0D,
                                0.0D,
                                appearance,
                                sequence))
                .event();
    }

    private static PresentationCoalescing.Identity identity(
            long generation, ResourceLocation particleProfile) {
        return new PresentationCoalescing.Identity(
                1L,
                1L,
                0,
                generation,
                0,
                OptionalInt.empty(),
                OptionalInt.empty(),
                ResourceLocation.withDefaultNamespace("overworld"),
                0.0D,
                0.0D,
                0.0D,
                1,
                0,
                0,
                0xFFFFFFFF,
                0xFFFFFFFF,
                Optional.of(AppearanceSemantics.DEFAULT_SOUND_PROFILE),
                Optional.of(particleProfile),
                Optional.of(AppearanceSemantics.DEFAULT_TRAIL_PROFILE),
                new TreeMap<>(),
                1_000,
                List.of(new UUID(0L, 1L)));
    }

    private static void activate(
            P8ServerPresentationService service,
            P8ServerPresentationService.PreparedCatalog candidate) {
        var identity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(identity, candidate);
        assertTrue(service.activateCandidateForTesting(identity));
    }
}
