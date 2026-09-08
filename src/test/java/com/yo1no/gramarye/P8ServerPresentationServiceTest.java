package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Direct transition tests for the root-owned P8-S2 active/pending authority. */
final class P8ServerPresentationServiceTest {
    @Test
    void pendingIsInaccessibleAndOnlyReferenceIdenticalTokenActivatesIt() {
        var profileId = P8S2TestFixtures.id("pending_sound");
        var service = P8ServerPresentationService.create();
        var view = service.profileAvailabilityView();
        var candidate = candidate(Map.of(
                profileId,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(700))));
        var exactIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        var wrongIdentity = P8ServerPresentationService.newReloadIdentityForTesting();

        assertSame(view, service.profileAvailabilityView());
        assertTrue(service.captureAppearanceResolutionSnapshot().isEmpty());
        assertEquals(
                ProfileAvailability.UNKNOWN,
                view.availability(AppearanceField.SOUND_PROFILE, profileId));

        service.stageCandidateForTesting(exactIdentity, candidate);

        assertAll(
                () -> assertEquals(4, service.pendingEntryCountForTesting()),
                () -> assertEquals(0, service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.catalogGenerationForTesting()),
                () -> assertTrue(
                        service.captureAppearanceResolutionSnapshot().isEmpty()),
                () -> assertEquals(
                        ProfileAvailability.UNKNOWN,
                        view.availability(AppearanceField.SOUND_PROFILE, profileId)),
                () -> assertFalse(service.activateCandidateForTesting(wrongIdentity)),
                () -> assertEquals(4, service.pendingEntryCountForTesting()));

        assertTrue(service.activateCandidateForTesting(exactIdentity));

        assertAll(
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertEquals(4, service.activeEntryCountForTesting()),
                () -> assertEquals(1, service.catalogGenerationForTesting()),
                () -> assertEquals(
                        1L,
                        service.captureAppearanceResolutionSnapshot()
                                .orElseThrow()
                                .catalogGeneration()),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        view.availability(AppearanceField.SOUND_PROFILE, profileId)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> view.availability(AppearanceField.PRIMARY_ARGB, profileId)));
    }

    @Test
    void overlappingStageReplacesPendingAndFailedGlobalCompletionPreservesActive() {
        var profileId = P8S2TestFixtures.id("replacement_sound");
        var service = P8ServerPresentationService.create();
        var empty = candidate(Map.of());
        var populated = candidate(Map.of(
                profileId,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(600))));
        var firstIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        var secondIdentity = P8ServerPresentationService.newReloadIdentityForTesting();

        service.stageCandidateForTesting(firstIdentity, empty);
        service.stageCandidateForTesting(secondIdentity, populated);

        assertAll(
                () -> assertEquals(4, service.pendingEntryCountForTesting()),
                () -> assertFalse(service.activateCandidateForTesting(firstIdentity)),
                () -> assertEquals(4, service.pendingEntryCountForTesting()),
                () -> assertTrue(service.activateCandidateForTesting(secondIdentity)),
                () -> assertEquals(1, service.catalogGenerationForTesting()),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, profileId)));

        var failedGlobalIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(failedGlobalIdentity, empty);

        assertAll(
                () -> assertEquals(3, service.pendingEntryCountForTesting()),
                () -> assertEquals(1, service.catalogGenerationForTesting()),
                () -> assertEquals(4, service.activeEntryCountForTesting()),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, profileId)));

        var begunNewerIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.beginReloadCycleForTesting(begunNewerIdentity);

        assertAll(
                () -> assertFalse(
                        service.activateCandidateForTesting(failedGlobalIdentity)),
                () -> assertFalse(
                        service.activateCandidateForTesting(begunNewerIdentity)),
                () -> assertEquals(3, service.pendingEntryCountForTesting()),
                () -> assertEquals(1, service.catalogGenerationForTesting()),
                () -> assertEquals(4, service.activeEntryCountForTesting()));

        service.stageCandidateForTesting(begunNewerIdentity, empty);
        assertTrue(service.activateCandidateForTesting(begunNewerIdentity));

        assertAll(
                () -> assertEquals(2, service.catalogGenerationForTesting()),
                () -> assertEquals(3, service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, profileId)));

        var noOpIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(noOpIdentity, empty);
        assertTrue(service.activateCandidateForTesting(noOpIdentity));
        assertEquals(3, service.catalogGenerationForTesting());
    }

    @Test
    void stableAvailabilityViewAtomicallyObservesChannelAndConfigurationReplacement() {
        var profileId = P8S2TestFixtures.id("same_profile_id");
        var service = P8ServerPresentationService.create();
        var stableView = service.profileAvailabilityView();
        activate(
                service,
                candidate(Map.of(
                        profileId,
                        P8S2TestFixtures.profile(
                                P8S2TestFixtures.SOUND_TYPE_ID,
                                P8S2TestFixtures.soundConfiguration(600)))));

        assertAll(
                () -> assertSame(stableView, service.profileAvailabilityView()),
                () -> assertSame(
                        P8S2TestFixtures.SOUND_TYPE,
                        service.activeTypeForTesting(profileId).orElseThrow()),
                () -> assertEquals(
                        600,
                        ((P8S2TestFixtures.SoundConfiguration)
                                        service.activeConfigurationForTesting(profileId)
                                                .orElseThrow())
                                .volumeMilli()),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        stableView.availability(AppearanceField.SOUND_PROFILE, profileId)),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        stableView.availability(AppearanceField.PARTICLE_PROFILE, profileId)));

        activate(
                service,
                candidate(Map.of(
                        profileId,
                        P8S2TestFixtures.profile(
                                P8S2TestFixtures.ALTERNATE_PARTICLE_TYPE_ID,
                                P8S2TestFixtures.soundConfiguration(900)))));

        assertAll(
                () -> assertSame(stableView, service.profileAvailabilityView()),
                () -> assertEquals(2, service.catalogGenerationForTesting()),
                () -> assertSame(
                        P8S2TestFixtures.ALTERNATE_PARTICLE_TYPE,
                        service.activeTypeForTesting(profileId).orElseThrow()),
                () -> assertEquals(
                        900,
                        ((P8S2TestFixtures.SoundConfiguration)
                                        service.activeConfigurationForTesting(profileId)
                                                .orElseThrow())
                                .volumeMilli()),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        stableView.availability(AppearanceField.SOUND_PROFILE, profileId)),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        stableView.availability(AppearanceField.PARTICLE_PROFILE, profileId)));
    }

    @Test
    void stopClearsActivePendingGenerationAndRejectsLateActivation() {
        var profileId = P8S2TestFixtures.id("stopped_sound");
        var service = P8ServerPresentationService.create();
        var populated = candidate(Map.of(
                profileId,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(600))));
        activate(service, populated);
        var lateIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(lateIdentity, candidate(Map.of()));

        service.stopForTesting();

        assertAll(
                () -> assertEquals(0, service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertEquals(0, service.catalogGenerationForTesting()),
                () -> assertTrue(
                        service.captureAppearanceResolutionSnapshot().isEmpty()),
                () -> assertFalse(service.activateCandidateForTesting(lateIdentity)),
                () -> assertEquals(
                        ProfileAvailability.UNKNOWN,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, profileId)));

        activate(service, candidate(Map.of()));
        assertAll(
                () -> assertEquals(1, service.catalogGenerationForTesting()),
                () -> assertEquals(3, service.activeEntryCountForTesting()));
    }

    @Test
    void generationMaximumIsUsableOnceAndNeverWraps() {
        var profileId = P8S2TestFixtures.id("generation_sound");
        var service = P8ServerPresentationService.create();
        activate(
                service,
                candidate(Map.of(
                        profileId,
                        P8S2TestFixtures.profile(
                                P8S2TestFixtures.SOUND_TYPE_ID,
                                P8S2TestFixtures.soundConfiguration(600)))));
        service.setCatalogGenerationHighWaterForTesting(Long.MAX_VALUE - 1L);
        var maximumIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(maximumIdentity, candidate(Map.of()));

        assertTrue(service.activateCandidateForTesting(maximumIdentity));
        assertAll(
                () -> assertEquals(Long.MAX_VALUE, service.catalogGenerationForTesting()),
                () -> assertEquals(3, service.activeEntryCountForTesting()),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, profileId)));

        var exhaustedIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(exhaustedIdentity, candidate(Map.of(
                profileId,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(900)))));

        assertAll(
                () -> assertFalse(service.activateCandidateForTesting(exhaustedIdentity)),
                () -> assertEquals(Long.MAX_VALUE, service.catalogGenerationForTesting()),
                () -> assertEquals(3, service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertFalse(service.activateCandidateForTesting(exhaustedIdentity)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.setCatalogGenerationHighWaterForTesting(-1L)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> service.setCatalogGenerationHighWaterForTesting(0L)));
    }

    @Test
    void newerReloadStartPreventsAnOlderPendingTokenFromActivating() {
        var service = P8ServerPresentationService.create();
        var candidate = candidate(Map.of());
        var older = P8ServerPresentationService.newReloadIdentityForTesting();
        var newer = P8ServerPresentationService.newReloadIdentityForTesting();

        service.stageCandidateForTesting(older, candidate);
        service.beginReloadCycleForTesting(newer);

        assertAll(
                () -> assertFalse(service.activateCandidateForTesting(older)),
                () -> assertEquals(0, service.catalogGenerationForTesting()),
                () -> assertEquals(0, service.activeEntryCountForTesting()),
                () -> assertEquals(3, service.pendingEntryCountForTesting()));

        service.stageCandidateForTesting(newer, candidate);
        assertTrue(service.activateCandidateForTesting(newer));
        assertEquals(1, service.catalogGenerationForTesting());
    }

    @Test
    void staleLateApplyCannotStageOverTheCurrentPendingOrActiveCatalog() {
        var activeProfile = P8S2TestFixtures.id("late_apply_active_sound");
        var staleProfile = P8S2TestFixtures.id("late_apply_stale_sound");
        var service = P8ServerPresentationService.create();
        activate(
                service,
                candidate(Map.of(
                        activeProfile,
                        P8S2TestFixtures.profile(
                                P8S2TestFixtures.SOUND_TYPE_ID,
                                P8S2TestFixtures.soundConfiguration(600)))));
        var staleCandidate = candidate(Map.of(
                staleProfile,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(700))));
        var currentCandidate = candidate(Map.of());
        var staleIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        var currentIdentity = P8ServerPresentationService.newReloadIdentityForTesting();

        service.beginReloadCycleForTesting(staleIdentity);
        service.beginReloadCycleForTesting(currentIdentity);
        service.completeReloadCycleForTesting(staleIdentity, staleCandidate);

        assertAll(
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertEquals(1L, service.catalogGenerationForTesting()),
                () -> assertEquals(4, service.activeEntryCountForTesting()),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, activeProfile)));

        service.completeReloadCycleForTesting(currentIdentity, currentCandidate);
        service.completeReloadCycleForTesting(staleIdentity, staleCandidate);

        assertAll(
                () -> assertEquals(3, service.pendingEntryCountForTesting()),
                () -> assertEquals(1L, service.catalogGenerationForTesting()),
                () -> assertEquals(4, service.activeEntryCountForTesting()),
                () -> assertFalse(service.activateCandidateForTesting(staleIdentity)),
                () -> assertEquals(3, service.pendingEntryCountForTesting()),
                () -> assertTrue(service.activateCandidateForTesting(currentIdentity)));

        assertAll(
                () -> assertEquals(2L, service.catalogGenerationForTesting()),
                () -> assertEquals(3, service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, activeProfile)),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        service.profileAvailabilityView().availability(
                                AppearanceField.SOUND_PROFILE, staleProfile)));
    }

    private static P8ServerPresentationService.PreparedCatalog candidate(
            Map<ResourceLocation, byte[]> sources) {
        return P8ServerPresentationService.loadCandidateForTesting(
                P8S2TestFixtures.registry(), sources);
    }

    private static void activate(
            P8ServerPresentationService service,
            P8ServerPresentationService.PreparedCatalog candidate) {
        var identity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(identity, candidate);
        assertTrue(service.activateCandidateForTesting(identity));
    }
}
