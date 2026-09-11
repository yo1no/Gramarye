package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Direct transition tests for the root-owned P8-S2 active/pending authority. */
final class P8ServerPresentationServiceTest {
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

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
    void reloadRawWorkReachesExactAcceptedAndInspectedAggregateBounds() {
        var acceptedSource = P8S2TestFixtures.paddedTo(
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        P8S2TestFixtures.soundConfiguration(600)),
                PresentationLimits.MAX_PROFILE_RAW_SOURCE_BYTES);
        assertEquals(
                PresentationLimits.MAX_PROFILE_ACCEPTED_RELOAD_BYTES,
                (long) PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES
                        * acceptedSource.length);
        var accepted = P8ServerPresentationService.create();
        activate(
                accepted,
                candidate(repeatedSources(
                        "accepted_raw",
                        PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES,
                        acceptedSource)));

        assertAll(
                () -> assertEquals(66, accepted.activeEntryCountForTesting()),
                () -> assertTrue(accepted.hasActiveCapacityDiagnosticForTesting(
                        P8S2TestFixtures.id("accepted_raw_255"))),
                () -> assertTrue(
                        accepted.activeCatalogBodyBytesForTesting()
                                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES));

        var inspectedSource = P8S2TestFixtures.paddedTo(
                acceptedSource, PresentationLimits.MAX_PROFILE_INSPECTED_SOURCE_BYTES);
        assertEquals(
                PresentationLimits.MAX_PROFILE_INSPECTED_RELOAD_BYTES,
                (long) PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES
                        * inspectedSource.length);
        var inspected = P8ServerPresentationService.create();
        activate(
                inspected,
                candidate(repeatedSources(
                        "inspected_raw",
                        PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES,
                        inspectedSource)));

        assertAll(
                () -> assertEquals(3, inspected.activeEntryCountForTesting()),
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_DIAGNOSTIC_KEYS,
                        inspected.activeDiagnosticCountForTesting()),
                () -> assertTrue(inspected.hasActiveDiagnosticDetailForTesting(
                        P8S2TestFixtures.id("inspected_raw_255"))),
                () -> assertEquals(
                        0L, inspected.activeSuppressedDiagnosticCountForTesting()));
    }

    @Test
    void reloadJsonWorkReachesTheExactAggregateNodeMaximum() {
        var maximumNodeSource = P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.arrayPayload(250));
        assertEquals(
                PresentationLimits.MAX_PROFILE_JSON_NODES_PER_RELOAD,
                (long) PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES
                        * PresentationLimits.MAX_PROFILE_JSON_NODES_PER_ENTRY);
        var service = P8ServerPresentationService.create();

        activate(
                service,
                candidate(repeatedSources(
                        "maximum_nodes",
                        PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES,
                        maximumNodeSource)));

        assertAll(
                () -> assertEquals(66, service.activeEntryCountForTesting()),
                () -> assertTrue(service.hasActiveCapacityDiagnosticForTesting(
                        P8S2TestFixtures.id("maximum_nodes_255"))),
                () -> assertTrue(
                        service.activeCatalogBodyBytesForTesting()
                                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES));
    }

    @Test
    void serverOwnerRetainsExactMaximumActiveAndPendingReferenceCounts() {
        var service = P8ServerPresentationService.create();
        activate(service, maximumCatalogCandidate("active_maximum"));
        var pendingIdentity = P8ServerPresentationService.newReloadIdentityForTesting();

        service.stageCandidateForTesting(
                pendingIdentity, maximumCatalogCandidate("pending_maximum"));

        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_INSTANCES,
                        service.activeEntryCountForTesting()),
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_INSTANCES,
                        service.pendingEntryCountForTesting()),
                () -> assertTrue(
                        service.activeCatalogBodyBytesForTesting()
                                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES),
                () -> assertTrue(
                        service.pendingCatalogBodyBytesForTesting()
                                <= PresentationLimits.MAX_CLIENT_CATALOG_RETAINED_BODY_BYTES));
    }

    @Test
    void activationClearsMaximumEventsAndSchedulesMaximumReadinessRecords() {
        var service = P8ServerPresentationService.create();
        activate(service, maximumCatalogCandidate("activation_active"));
        seedMaximumReadiness(service);
        service.setTickStateForTesting(maximumBufferedTickState(70L));
        var pendingIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(
                pendingIdentity, maximumCatalogCandidate("activation_pending"));

        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_CATALOG_READINESS_RECORDS,
                        service.readinessRecordCountForTesting()),
                () -> assertEquals(
                        Math.toIntExact(
                                PresentationLimits.MAX_CURRENT_TICK_EVENT_BUFFER_EVENTS),
                        service.bufferedEventsForTesting().size()),
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_INSTANCES,
                        service.pendingEntryCountForTesting()));

        assertTrue(service.activateCandidateAndScheduleAllForTesting(
                pendingIdentity, 71L));

        var due = service.dueCatalogsForTesting(71L);
        assertAll(
                () -> assertEquals(2L, service.catalogGenerationForTesting()),
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_INSTANCES,
                        service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertTrue(service.bufferedEventsForTesting().isEmpty()),
                () -> assertEquals(
                        PresentationLimits.MAX_CATALOG_READINESS_RECORDS,
                        service.readinessRecordCountForTesting()),
                () -> assertEquals(
                        PresentationLimits.MAX_CATALOG_READINESS_RECORDS,
                        due.size()),
                () -> assertTrue(due.stream()
                        .allMatch(key -> key.catalogGeneration() == 2L)));
    }

    @Test
    void serverDiagnosticInsertionRetainsExact128ByteIdAndOmits129ByteDetail() {
        var exact = ResourceLocation.fromNamespaceAndPath("a", "x".repeat(126));
        var over = ResourceLocation.fromNamespaceAndPath("a", "y".repeat(127));
        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        exact.toString().getBytes(StandardCharsets.UTF_8).length),
                () -> assertEquals(
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES + 1,
                        over.toString().getBytes(StandardCharsets.UTF_8).length));
        var service = P8ServerPresentationService.create();

        activate(
                service,
                candidate(Map.of(
                        exact, "not-json".getBytes(StandardCharsets.UTF_8),
                        over, "not-json".getBytes(StandardCharsets.UTF_8))));

        assertAll(
                () -> assertEquals(2, service.activeDiagnosticCountForTesting()),
                () -> assertTrue(service.hasActiveDiagnosticDetailForTesting(exact)),
                () -> assertFalse(service.hasActiveDiagnosticDetailForTesting(over)));
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
    void stopClearsMaximumReadinessEventsAndPendingCatalogAtTheActualOwner() {
        var service = P8ServerPresentationService.create();
        activate(service, maximumCatalogCandidate("cleanup_active"));
        seedMaximumReadiness(service);
        service.setTickStateForTesting(maximumBufferedTickState(80L));
        var lateIdentity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(
                lateIdentity, maximumCatalogCandidate("cleanup_pending"));

        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_CATALOG_READINESS_RECORDS,
                        service.readinessRecordCountForTesting()),
                () -> assertEquals(
                        Math.toIntExact(
                                PresentationLimits.MAX_CURRENT_TICK_EVENT_BUFFER_EVENTS),
                        service.bufferedEventsForTesting().size()),
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_INSTANCES,
                        service.pendingEntryCountForTesting()));

        service.stopForTesting();

        assertAll(
                () -> assertEquals(0, service.activeEntryCountForTesting()),
                () -> assertEquals(0, service.pendingEntryCountForTesting()),
                () -> assertEquals(0, service.readinessRecordCountForTesting()),
                () -> assertTrue(service.bufferedEventsForTesting().isEmpty()),
                () -> assertEquals(0L, service.catalogGenerationForTesting()),
                () -> assertFalse(service.activateCandidateForTesting(lateIdentity)));
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
    void suppressedRuntimeDiagnosticCountSaturatesAtLongMaximum() {
        var sources = new LinkedHashMap<ResourceLocation, byte[]>();
        for (var index = 0;
                index < PresentationLimits.MAX_PROFILE_DIAGNOSTIC_KEYS;
                index++) {
            sources.put(
                    P8S2TestFixtures.id("saturated_diagnostic_%03d".formatted(index)),
                    "not-json".getBytes(StandardCharsets.UTF_8));
        }
        var service = P8ServerPresentationService.create();
        activate(service, candidate(sources));
        service.setRuntimeSuppressedDiagnosticCountForTesting(Long.MAX_VALUE - 1L);

        service.recordObserverRuntimeException();

        assertAll(
                () -> assertEquals(
                        PresentationLimits.MAX_PROFILE_DIAGNOSTIC_KEYS,
                        service.activeDiagnosticCountForTesting()),
                () -> assertEquals(
                        Long.MAX_VALUE,
                        service.activeSuppressedDiagnosticCountForTesting()),
                () -> assertFalse(service.hasRuntimeDiagnosticForTesting(
                        P8ServerRuntimeDiagnosticCode.OBSERVER_RUNTIME_EXCEPTION)));

        service.recordObserverRuntimeException();

        assertAll(
                () -> assertEquals(
                        Long.MAX_VALUE,
                        service.activeSuppressedDiagnosticCountForTesting()),
                () -> assertFalse(service.hasRuntimeDiagnosticForTesting(
                        P8ServerRuntimeDiagnosticCode.OBSERVER_RUNTIME_EXCEPTION)));
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

    private static Map<ResourceLocation, byte[]> repeatedSources(
            String prefix, int count, byte[] source) {
        var sources = new LinkedHashMap<ResourceLocation, byte[]>(count);
        for (var index = 0; index < count; index++) {
            sources.put(
                    P8S2TestFixtures.id("%s_%03d".formatted(prefix, index)),
                    source);
        }
        return sources;
    }

    private static P8ServerPresentationService.PreparedCatalog maximumCatalogCandidate(
            String prefix) {
        var sources = new LinkedHashMap<ResourceLocation, byte[]>();
        for (var index = 0;
                index < PresentationLimits.MAX_PROFILE_INSTANCES_PER_CHANNEL - 1;
                index++) {
            sources.put(
                    P8S2TestFixtures.id("%s_sound_%02d".formatted(prefix, index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.SOUND_TYPE_ID,
                            P8S2TestFixtures.soundConfiguration(index)));
            sources.put(
                    P8S2TestFixtures.id("%s_particle_%02d".formatted(prefix, index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.PARTICLE_TYPE_ID,
                            P8S2TestFixtures.particleConfiguration(index)));
            sources.put(
                    P8S2TestFixtures.id("%s_trail_%02d".formatted(prefix, index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.TRAIL_TYPE_ID,
                            P8S2TestFixtures.trailConfiguration(
                                    Math.min(48, Math.max(1, index)))));
        }
        return candidate(sources);
    }

    private static void seedMaximumReadiness(P8ServerPresentationService service) {
        for (var index = 0;
                index < PresentationLimits.MAX_CATALOG_READINESS_RECORDS;
                index++) {
            assertTrue(service.openReadinessRecordForTesting(
                    new UUID(0L, index + 1L),
                    service.catalogGenerationForTesting(),
                    0L));
        }
    }

    private static P8TickState maximumBufferedTickState(long tick) {
        var scratch = P8TickState.empty().scratch(tick);
        for (var sequence = 1L;
                sequence <= PresentationLimits.MAX_CURRENT_TICK_EVENT_BUFFER_EVENTS;
                sequence++) {
            assertEquals(
                    PresentationDegradation.Outcome.UNCHANGED,
                    scratch.admitBuffered(buffered(tick, sequence)).outcome());
        }
        return scratch.freeze();
    }

    private static P8BufferedPresentation buffered(long tick, long sequence) {
        var appearance = new EffectiveAppearance(
                0xff_ffffff,
                0xff_ffffff,
                1_000,
                disabled(ProfileChannel.SOUND),
                disabled(ProfileChannel.PARTICLE),
                disabled(ProfileChannel.TRAIL),
                Map.of());
        var event = ((AcceptedPresentationEvent) PresentationEvent.createServer(
                        1L,
                        PresentationEventKind.CAST_RELEASE.wireCode(),
                        OptionalInt.empty(),
                        OptionalInt.empty(),
                        OVERWORLD,
                        0.0D,
                        64.0D,
                        0.0D,
                        1.0D,
                        0.0D,
                        0.0D,
                        appearance,
                        sequence))
                .event();
        var direction = event.direction();
        var wireAppearance = event.appearance();
        var identity = new PresentationCoalescing.Identity(
                tick,
                sequence,
                0,
                event.catalogGeneration(),
                event.kind().wireCode(),
                event.sourceSummary().sourceEntityId(),
                event.sourceSummary().targetEntityId(),
                event.dimension(),
                event.position().x(),
                event.position().y(),
                event.position().z(),
                direction.xQ15(),
                direction.yQ15(),
                direction.zQ15(),
                wireAppearance.primaryArgb(),
                wireAppearance.secondaryArgb(),
                wireAppearance.soundProfileId(),
                wireAppearance.particleProfileId(),
                wireAppearance.trailProfileId(),
                new TreeMap<>(wireAppearance.parameters()),
                wireAppearance.intensityMilli(),
                List.of());
        return new P8BufferedPresentation(
                tick,
                sequence,
                0,
                event,
                PresentationCoalescing.Value.create(identity, sequence),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static ResolvedProfile disabled(ProfileChannel channel) {
        return new ResolvedProfile(
                channel,
                Optional.empty(),
                ProfileResolutionReason.EXPLICITLY_DISABLED);
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
