package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileTypeCapabilities;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Direct tests for the bounded P8-S2 parser and its immutable typed catalog snapshot. */
final class P8ProfileCatalogTest {
    @Test
    void emptyDatapackSynthesizesExactTypedDefaults() {
        var service = activate(Map.of());
        var availability = service.profileAvailabilityView();
        var captured = service.captureAppearanceResolutionSnapshot().orElseThrow();
        var sound = captured.profiles()
                .find(P8S2TestFixtures.DEFAULT_SOUND_ID)
                .orElseThrow();
        var particle = captured.profiles()
                .find(P8S2TestFixtures.DEFAULT_PARTICLE_ID)
                .orElseThrow();
        var trail = captured.profiles()
                .find(P8S2TestFixtures.DEFAULT_TRAIL_ID)
                .orElseThrow();

        assertAll(
                () -> assertEquals(3, service.activeEntryCountForTesting()),
                () -> assertEquals(1L, captured.catalogGeneration()),
                () -> assertEquals(500, service.activeCatalogBodyBytesForTesting()),
                () -> assertEquals(0, service.activeDiagnosticCountForTesting()),
                () -> assertEquals(0L,
                        service.activeSuppressedDiagnosticCountForTesting()),
                () -> assertEquals(
                        new ProfileTypeCapabilities(
                                false,
                                false,
                                false,
                                false,
                                AppearanceParameterPolicy.none()),
                        sound.capabilities()),
                () -> assertEquals(new ProfileCost(0, 1, 0, 0, 1),
                        sound.estimatedCost()),
                () -> assertEquals(
                        new ProfileTypeCapabilities(
                                true,
                                false,
                                true,
                                false,
                                AppearanceParameterPolicy.none()),
                        particle.capabilities()),
                () -> assertEquals(new ProfileCost(8, 0, 0, 0, 20),
                        particle.estimatedCost()),
                () -> assertEquals(
                        new ProfileTypeCapabilities(
                                true,
                                true,
                                true,
                                true,
                                AppearanceParameterPolicy.none()),
                        trail.capabilities()),
                () -> assertEquals(new ProfileCost(0, 0, 1, 8, 16),
                        trail.estimatedCost()),
                () -> assertSame(
                        P8S2TestFixtures.SOUND_TYPE,
                        service.activeTypeForTesting(P8S2TestFixtures.DEFAULT_SOUND_ID)
                                .orElseThrow()),
                () -> assertSame(
                        P8S2TestFixtures.PARTICLE_TYPE,
                        service.activeTypeForTesting(P8S2TestFixtures.DEFAULT_PARTICLE_ID)
                                .orElseThrow()),
                () -> assertSame(
                        P8S2TestFixtures.TRAIL_TYPE,
                        service.activeTypeForTesting(P8S2TestFixtures.DEFAULT_TRAIL_ID)
                                .orElseThrow()),
                () -> assertEquals(
                        new P8S2TestFixtures.SoundConfiguration(
                                ResourceLocation.withDefaultNamespace(
                                        "entity.experience_orb.pickup"),
                                600,
                                1_000),
                        service.activeConfigurationForTesting(
                                        P8S2TestFixtures.DEFAULT_SOUND_ID)
                                .orElseThrow()),
                () -> assertEquals(
                        new P8S2TestFixtures.ParticleConfiguration(
                                ResourceLocation.withDefaultNamespace("enchant"),
                                8,
                                50,
                                250,
                                20),
                        service.activeConfigurationForTesting(
                                        P8S2TestFixtures.DEFAULT_PARTICLE_ID)
                                .orElseThrow()),
                () -> assertEquals(
                        new P8S2TestFixtures.TrailConfiguration(
                                ResourceLocation.withDefaultNamespace("enchant"),
                                8,
                                2,
                                200,
                                16),
                        service.activeConfigurationForTesting(
                                        P8S2TestFixtures.DEFAULT_TRAIL_ID)
                                .orElseThrow()),
                () -> assertEquals(
                        ProfileAvailability.AVAILABLE,
                        availability.availability(
                                AppearanceField.SOUND_PROFILE,
                                P8S2TestFixtures.DEFAULT_SOUND_ID)),
                () -> assertEquals(
                        ProfileAvailability.MISSING,
                        availability.availability(
                                AppearanceField.PARTICLE_PROFILE,
                                P8S2TestFixtures.DEFAULT_SOUND_ID)));
    }

    @Test
    void canonicalConfigurationIsSortedAndReturnedDefensively() {
        var profileId = P8S2TestFixtures.id("canonical_sound");
        var service = activate(Map.of(
                profileId,
                P8S2TestFixtures.profile(
                        P8S2TestFixtures.SOUND_TYPE_ID,
                        "{\"volume_milli\":700,\"pitch_milli\":900,"
                                + "\"sound\":\"minecraft:block.note_block.chime\"}")));
        var expected = "{\"pitch_milli\":900,"
                + "\"sound\":\"minecraft:block.note_block.chime\","
                + "\"volume_milli\":700}";
        var first = service.activeCanonicalConfigurationBytesForTesting(profileId)
                .orElseThrow();
        first[0] = (byte) '!';

        assertAll(
                () -> assertEquals(ProfileChannel.SOUND,
                        service.activeChannelForTesting(profileId).orElseThrow()),
                () -> assertSame(
                        P8S2TestFixtures.SOUND_TYPE,
                        service.activeTypeForTesting(profileId).orElseThrow()),
                () -> assertInstanceOf(
                        P8S2TestFixtures.SoundConfiguration.class,
                        service.activeConfigurationForTesting(profileId).orElseThrow()),
                () -> assertArrayEquals(
                        expected.getBytes(StandardCharsets.UTF_8),
                        service.activeCanonicalConfigurationBytesForTesting(profileId)
                                .orElseThrow()),
                () -> assertEquals(
                        1 + expected.getBytes(StandardCharsets.UTF_8).length,
                        service.activeEnvelopeBytesForTesting(profileId).orElseThrow()));
    }

    @Test
    void malformedUnknownVersionedAndReservedEntriesAreOmittedWithoutPartialTruth() {
        var legal = P8S2TestFixtures.id("legal");
        var reserved = P8S2TestFixtures.DEFAULT_SOUND_ID;
        var badFormat = P8S2TestFixtures.id("bad_format");
        var badRootShape = P8S2TestFixtures.id("bad_root_shape");
        var duplicateRootKey = P8S2TestFixtures.id("duplicate_root_key");
        var unknownType = P8S2TestFixtures.id("unknown_type");
        var wrongVersion = P8S2TestFixtures.id("wrong_version");
        var invalidConfiguration = P8S2TestFixtures.id("invalid_configuration");
        var invalidEstimatedCost = P8S2TestFixtures.id("invalid_estimated_cost");
        var trailingContent = P8S2TestFixtures.id("trailing_content");
        var invalidUtf8 = P8S2TestFixtures.id("invalid_utf8");
        var sources = new LinkedHashMap<ResourceLocation, byte[]>();
        sources.put(legal, P8S2TestFixtures.profile(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.soundConfiguration(700)));
        sources.put(reserved, P8S2TestFixtures.profile(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.soundConfiguration(1)));
        sources.put(badFormat, bytes(
                "{\"format\":1,\"type\":\"gramarye:sound\",\"type_version\":0,"
                        + "\"configuration\":"
                        + P8S2TestFixtures.soundConfiguration(600)
                        + "}"));
        sources.put(badRootShape, bytes(
                "{\"format\":0,\"type\":\"gramarye:sound\",\"type_version\":0,"
                        + "\"configuration\":"
                        + P8S2TestFixtures.soundConfiguration(600)
                        + ",\"extra\":0}"));
        sources.put(duplicateRootKey, bytes(
                "{\"format\":0,\"format\":0,\"type\":\"gramarye:sound\","
                        + "\"type_version\":0,\"configuration\":"
                        + P8S2TestFixtures.soundConfiguration(600)
                        + "}"));
        sources.put(unknownType, P8S2TestFixtures.profile(
                P8S2TestFixtures.id("not_registered"),
                P8S2TestFixtures.soundConfiguration(600)));
        sources.put(wrongVersion, P8S2TestFixtures.profile(
                P8S2TestFixtures.SOUND_TYPE_ID,
                1,
                P8S2TestFixtures.soundConfiguration(600)));
        sources.put(invalidConfiguration, P8S2TestFixtures.profile(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.soundConfiguration(1_001)));
        sources.put(invalidEstimatedCost, P8S2TestFixtures.profile(
                P8S2TestFixtures.OVER_COST_TYPE_ID,
                P8S2TestFixtures.soundConfiguration(600)));
        sources.put(trailingContent, bytes(new String(
                        P8S2TestFixtures.profile(
                                P8S2TestFixtures.SOUND_TYPE_ID,
                                P8S2TestFixtures.soundConfiguration(600)),
                        StandardCharsets.UTF_8)
                + " true"));
        sources.put(invalidUtf8, new byte[] {(byte) 0xC3, (byte) 0x28});

        var service = activate(sources);

        assertEquals(4, service.activeEntryCountForTesting());
        assertEquals(
                new P8S2TestFixtures.SoundConfiguration(
                        ResourceLocation.withDefaultNamespace(
                                "entity.experience_orb.pickup"),
                        600,
                        1_000),
                service.activeConfigurationForTesting(reserved).orElseThrow());
        for (var rejected : List.of(
                badFormat,
                badRootShape,
                duplicateRootKey,
                unknownType,
                wrongVersion,
                invalidConfiguration,
                invalidEstimatedCost,
                trailingContent,
                invalidUtf8)) {
            assertTrue(service.activeConfigurationForTesting(rejected).isEmpty(),
                    () -> "unexpected retained Profile: " + rejected);
        }
    }

    @Test
    void rawEnvelopeDepthNodeAndStringBoundsAreExact() {
        var rawMaximum = P8S2TestFixtures.id("raw_maximum");
        var rawOver = P8S2TestFixtures.id("raw_over");
        var envelopeMaximum = P8S2TestFixtures.id("envelope_maximum");
        var envelopeOver = P8S2TestFixtures.id("envelope_over");
        var depthMaximum = P8S2TestFixtures.id("depth_maximum");
        var depthOver = P8S2TestFixtures.id("depth_over");
        var nodesMaximum = P8S2TestFixtures.id("nodes_maximum");
        var nodesOver = P8S2TestFixtures.id("nodes_over");
        var stringMaximum = P8S2TestFixtures.id("string_maximum");
        var stringOver = P8S2TestFixtures.id("string_over");

        var sources = new LinkedHashMap<ResourceLocation, byte[]>();
        var normal = P8S2TestFixtures.profile(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.soundConfiguration(600));
        sources.put(rawMaximum, P8S2TestFixtures.paddedTo(normal, 16_384));
        sources.put(rawOver, P8S2TestFixtures.paddedTo(normal, 16_385));
        sources.put(envelopeMaximum, P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.stringArrayConfigurationOfBytes(2_047)));
        sources.put(envelopeOver, P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.stringArrayConfigurationOfBytes(2_048)));
        sources.put(depthMaximum, P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.nestedArrayPayload(9)));
        sources.put(depthOver, P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.nestedArrayPayload(10)));
        sources.put(nodesMaximum, P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.arrayPayload(250)));
        sources.put(nodesOver, P8S2TestFixtures.profile(
                P8S2TestFixtures.TREE_TYPE_ID,
                P8S2TestFixtures.arrayPayload(251)));
        sources.put(stringMaximum, P8S2TestFixtures.profile(
                P8S2TestFixtures.STRING_TYPE_ID,
                "{\"value\":\"" + "x".repeat(128) + "\"}"));
        sources.put(stringOver, P8S2TestFixtures.profile(
                P8S2TestFixtures.STRING_TYPE_ID,
                "{\"value\":\"" + "x".repeat(129) + "\"}"));

        var service = activate(sources);

        assertAll(
                () -> assertEquals(8, service.activeEntryCountForTesting()),
                () -> assertPresent(service, rawMaximum),
                () -> assertMissing(service, rawOver),
                () -> assertEquals(2_048,
                        service.activeEnvelopeBytesForTesting(envelopeMaximum)
                                .orElseThrow()),
                () -> assertMissing(service, envelopeOver),
                () -> assertPresent(service, depthMaximum),
                () -> assertMissing(service, depthOver),
                () -> assertPresent(service, nodesMaximum),
                () -> assertMissing(service, nodesOver),
                () -> assertPresent(service, stringMaximum),
                () -> assertMissing(service, stringOver));
    }

    @Test
    void capacityRetainsAscendingIdsDeterministically() {
        var ascending = new LinkedHashMap<ResourceLocation, byte[]>();
        for (var index = 0; index < 64; index++) {
            ascending.put(
                    P8S2TestFixtures.id("capacity_sound_%02d".formatted(index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.SOUND_TYPE_ID,
                            P8S2TestFixtures.soundConfiguration(index)));
        }
        var reverseEntries = List.copyOf(ascending.entrySet());
        var reversed = new LinkedHashMap<ResourceLocation, byte[]>();
        IntStream.range(0, reverseEntries.size())
                .map(index -> reverseEntries.size() - index - 1)
                .forEach(index -> reversed.put(
                        reverseEntries.get(index).getKey(),
                        reverseEntries.get(index).getValue()));

        var first = activate(ascending);
        var second = activate(reversed);
        var firstRetained = P8S2TestFixtures.id("capacity_sound_00");
        var lastRetained = P8S2TestFixtures.id("capacity_sound_62");
        var firstOmitted = P8S2TestFixtures.id("capacity_sound_63");

        assertAll(
                () -> assertEquals(66, first.activeEntryCountForTesting()),
                () -> assertEquals(66, second.activeEntryCountForTesting()),
                () -> assertPresent(first, firstRetained),
                () -> assertPresent(first, lastRetained),
                () -> assertMissing(first, firstOmitted),
                () -> assertPresent(second, firstRetained),
                () -> assertPresent(second, lastRetained),
                () -> assertMissing(second, firstOmitted),
                () -> assertArrayEquals(
                        first.activeCanonicalConfigurationBytesForTesting(lastRetained)
                                .orElseThrow(),
                        second.activeCanonicalConfigurationBytesForTesting(lastRetained)
                                .orElseThrow()));
    }

    @Test
    void threeChannelCapacityIsBoundedToOneHundredNinetyTwoEntries() {
        var sources = new LinkedHashMap<ResourceLocation, byte[]>();
        for (var index = 0; index < 64; index++) {
            sources.put(
                    P8S2TestFixtures.id("all_sound_%02d".formatted(index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.SOUND_TYPE_ID,
                            P8S2TestFixtures.soundConfiguration(index)));
            sources.put(
                    P8S2TestFixtures.id("all_particle_%02d".formatted(index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.PARTICLE_TYPE_ID,
                            P8S2TestFixtures.particleConfiguration(index)));
            sources.put(
                    P8S2TestFixtures.id("all_trail_%02d".formatted(index)),
                    P8S2TestFixtures.profile(
                            P8S2TestFixtures.TRAIL_TYPE_ID,
                            P8S2TestFixtures.trailConfiguration(
                                    Math.min(48, Math.max(1, index)))));
        }

        var service = activate(sources);

        assertAll(
                () -> assertEquals(192, service.activeEntryCountForTesting()),
                () -> assertMissing(service, P8S2TestFixtures.id("all_sound_63")),
                () -> assertMissing(service, P8S2TestFixtures.id("all_particle_63")),
                () -> assertMissing(service, P8S2TestFixtures.id("all_trail_63")),
                () -> assertPresent(service, P8S2TestFixtures.id("all_sound_62")),
                () -> assertPresent(service, P8S2TestFixtures.id("all_particle_62")),
                () -> assertPresent(service, P8S2TestFixtures.id("all_trail_62")));
    }

    @Test
    void discoveryProbeAcceptsAtMostTwoHundredFiftySevenFixtureInputs() {
        var exactProbe = new LinkedHashMap<ResourceLocation, byte[]>();
        for (var index = 0; index < 257; index++) {
            exactProbe.put(
                    P8S2TestFixtures.id("probe_%03d".formatted(index)),
                    index < 255
                            ? bytes("not-json")
                            : P8S2TestFixtures.profile(
                                    P8S2TestFixtures.SOUND_TYPE_ID,
                                    P8S2TestFixtures.soundConfiguration(index)));
        }
        var service = activate(exactProbe);
        exactProbe.put(P8S2TestFixtures.id("probe_257"), bytes("not-json"));

        assertAll(
                () -> assertEquals(4, service.activeEntryCountForTesting()),
                () -> assertPresent(service, P8S2TestFixtures.id("probe_255")),
                () -> assertMissing(service, P8S2TestFixtures.id("probe_256")),
                () -> assertEquals(256, service.activeDiagnosticCountForTesting()),
                () -> assertEquals(0L,
                        service.activeSuppressedDiagnosticCountForTesting()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P8ServerPresentationService.loadCandidateForTesting(
                                P8S2TestFixtures.registry(), exactProbe)));
    }

    @Test
    void diagnosticsAreBoundedAndSuppressTheFirstOverflowDetail() {
        var sources = new LinkedHashMap<ResourceLocation, byte[]>();
        for (var index = 0; index < 257; index++) {
            sources.put(
                    P8S2TestFixtures.id("diagnostic_%03d".formatted(index)),
                    bytes("not-json"));
        }

        var service = activate(sources);

        assertAll(
                () -> assertEquals(3, service.activeEntryCountForTesting()),
                () -> assertEquals(256, service.activeDiagnosticCountForTesting()),
                () -> assertEquals(1L,
                        service.activeSuppressedDiagnosticCountForTesting()),
                () -> assertEquals(500, service.activeCatalogBodyBytesForTesting()),
                () -> assertTrue(service.activeCatalogBodyBytesForTesting()
                        <= PresentationLimits.MAX_PROFILE_CATALOG_BODY_BYTES));
    }

    @Test
    void everyRequiredDefaultTypeMustExistBeforeACompleteCandidateCanBeBuilt() {
        for (var missingType : List.of(
                P8S2TestFixtures.SOUND_TYPE_ID,
                P8S2TestFixtures.PARTICLE_TYPE_ID,
                P8S2TestFixtures.TRAIL_TYPE_ID)) {
            assertThrows(
                    IllegalStateException.class,
                    () -> P8ServerPresentationService.loadCandidateForTesting(
                            P8S2TestFixtures.registryWithoutRequiredType(missingType),
                            Map.of()),
                    () -> "missing required type did not abort: " + missingType);
        }
    }

    @Test
    void unexpectedCodecValidationAndEstimateFailuresAbortTheWholeCandidate() {
        var active = activate(Map.of());
        for (var failingType : List.of(
                P8S2TestFixtures.UNEXPECTED_CODEC_TYPE_ID,
                P8S2TestFixtures.UNEXPECTED_VALIDATION_TYPE_ID,
                P8S2TestFixtures.UNEXPECTED_ESTIMATE_TYPE_ID)) {
            var failure = assertThrows(
                    IllegalStateException.class,
                    () -> P8ServerPresentationService.loadCandidateForTesting(
                            P8S2TestFixtures.registry(),
                            Map.of(
                                    P8S2TestFixtures.id("profile_" + failingType.getPath()),
                                    P8S2TestFixtures.profile(
                                            failingType,
                                            P8S2TestFixtures.soundConfiguration(600)))));
            assertTrue(failure.getMessage().startsWith("unexpected Profile "));
            assertAll(
                    () -> assertEquals(1L, active.catalogGenerationForTesting()),
                    () -> assertEquals(3, active.activeEntryCountForTesting()),
                    () -> assertEquals(500, active.activeCatalogBodyBytesForTesting()));
        }
    }

    private static P8ServerPresentationService activate(
            Map<ResourceLocation, byte[]> sources) {
        var service = P8ServerPresentationService.create();
        var candidate = P8ServerPresentationService.loadCandidateForTesting(
                P8S2TestFixtures.registry(), sources);
        var identity = P8ServerPresentationService.newReloadIdentityForTesting();
        service.stageCandidateForTesting(identity, candidate);
        assertTrue(service.activateCandidateForTesting(identity));
        return service;
    }

    private static void assertPresent(
            P8ServerPresentationService service, ResourceLocation profileId) {
        assertTrue(service.activeConfigurationForTesting(profileId).isPresent(),
                () -> "expected retained Profile: " + profileId);
    }

    private static void assertMissing(
            P8ServerPresentationService service, ResourceLocation profileId) {
        assertTrue(service.activeConfigurationForTesting(profileId).isEmpty(),
                () -> "expected omitted Profile: " + profileId);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
