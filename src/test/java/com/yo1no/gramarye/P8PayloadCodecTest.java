package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

/** Direct malicious-input and canonical-byte tests for the exact P8 wire codecs. */
final class P8PayloadCodecTest {
    private static final ResourceLocation DEFAULT_PARTICLE = id("default_particle");
    private static final ResourceLocation DEFAULT_SOUND = id("default_sound");
    private static final ResourceLocation DEFAULT_TRAIL = id("default_trail");
    private static final ResourceLocation PARTICLE_TYPE = id("particle");
    private static final ResourceLocation SOUND_TYPE = id("sound");
    private static final ResourceLocation TRAIL_TYPE = id("trail");

    private static final String SOUND_JSON =
            "{\"pitch_milli\":1000,\"sound\":\"minecraft:entity.experience_orb.pickup\","
                    + "\"volume_milli\":600}";
    private static final String PARTICLE_JSON =
            "{\"count\":8,\"lifetime_ticks\":20,\"particle\":\"minecraft:enchant\","
                    + "\"size_milli_blocks\":250,\"speed_milli_blocks\":50}";
    private static final String TRAIL_JSON =
            "{\"lifetime_ticks\":16,\"particle\":\"minecraft:enchant\","
                    + "\"sample_interval_ticks\":2,\"segments\":8,"
                    + "\"size_milli_blocks\":200}";

    @Test
    void catalogHasExactCanonicalBytesAndTypedRoundTrip() {
        var payload = new ProfileCatalogPayload(7L, defaultEntries());
        var encoded = encodeCatalog(payload);
        var expected = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(7L);
            buffer.writeVarInt(3);
            writeCatalogEntry(
                    buffer,
                    DEFAULT_PARTICLE,
                    PARTICLE_TYPE,
                    ProfileChannel.PARTICLE,
                    PARTICLE_TYPE,
                    envelope(0, PARTICLE_JSON));
            writeCatalogEntry(
                    buffer,
                    DEFAULT_SOUND,
                    SOUND_TYPE,
                    ProfileChannel.SOUND,
                    SOUND_TYPE,
                    envelope(0, SOUND_JSON));
            writeCatalogEntry(
                    buffer,
                    DEFAULT_TRAIL,
                    TRAIL_TYPE,
                    ProfileChannel.TRAIL,
                    TRAIL_TYPE,
                    envelope(0, TRAIL_JSON));
        });

        assertAll(
                () -> assertEquals("gramarye:profile_catalog",
                        ProfileCatalogPayload.TYPE.id().toString()),
                () -> assertEquals(500, encoded.length),
                () -> assertEquals(encoded.length, payload.bodySize()),
                () -> assertArrayEquals(expected, encoded));

        var decoded = decodeCatalog(encoded);
        assertAll(
                () -> assertEquals(7L, decoded.catalogGeneration()),
                () -> assertEquals(3, decoded.entries().size()),
                () -> assertEquals(500, decoded.bodySize()),
                () -> assertTrue(decoded.entries().stream()
                        .allMatch(entry -> entry.decodedConfiguration().isPresent())),
                () -> assertArrayEquals(encoded, encodeCatalog(decoded)));
    }

    @Test
    void catalogSafelySkipsUnknownAndMakesInvalidKnownEntryUnavailable() {
        var invalidKnown = id("aaa_invalid_known");
        var unknown = id("aab_unknown");
        var unknownType = id("not_registered");
        var nonCanonicalKnownJson =
                "{ \"pitch_milli\":1000, \"sound\":"
                        + "\"minecraft:entity.experience_orb.pickup\","
                        + "\"volume_milli\":600 }";
        var encoded = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(8L);
            buffer.writeVarInt(5);
            writeCatalogEntry(
                    buffer,
                    invalidKnown,
                    SOUND_TYPE,
                    ProfileChannel.SOUND,
                    SOUND_TYPE,
                    envelope(0, nonCanonicalKnownJson));
            writeCatalogEntry(
                    buffer,
                    unknown,
                    unknownType,
                    ProfileChannel.SOUND,
                    unknownType,
                    new byte[] {0, (byte) 0xff, (byte) 0xfe});
            writeDefaultEntries(buffer);
        });

        var decoded = decodeCatalog(encoded);
        var invalidKnownEntry = decoded.snapshot().entry(invalidKnown);
        var unknownEntry = decoded.snapshot().entry(unknown);
        assertAll(
                () -> assertEquals(5, decoded.entries().size()),
                () -> assertEquals(encoded.length, decoded.bodySize()),
                () -> assertTrue(invalidKnownEntry.decodedConfiguration().isEmpty()),
                () -> assertTrue(
                        invalidKnownEntry.retainedCanonicalConfigurationJson().isEmpty()),
                () -> assertTrue(unknownEntry.decodedConfiguration().isEmpty()),
                () -> assertTrue(unknownEntry.retainedCanonicalConfigurationJson().isEmpty()),
                () -> assertTrue(decoded.snapshot()
                        .entry(DEFAULT_SOUND)
                        .decodedConfiguration()
                        .isPresent()));
    }

    @Test
    void catalogRejectsInvalidRequiredDefaultsWithoutConstructingAPayload() {
        var invalidRequiredDefault = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(3);
            writeCatalogEntry(
                    buffer,
                    DEFAULT_PARTICLE,
                    PARTICLE_TYPE,
                    ProfileChannel.PARTICLE,
                    PARTICLE_TYPE,
                    envelope(0, PARTICLE_JSON));
            writeCatalogEntry(
                    buffer,
                    DEFAULT_SOUND,
                    SOUND_TYPE,
                    ProfileChannel.SOUND,
                    SOUND_TYPE,
                    envelope(0, "{}"));
            writeCatalogEntry(
                    buffer,
                    DEFAULT_TRAIL,
                    TRAIL_TYPE,
                    ProfileChannel.TRAIL,
                    TRAIL_TYPE,
                    envelope(0, TRAIL_JSON));
        });
        var missingRequiredDefault = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(3);
            writeCatalogEntry(
                    buffer,
                    id("aaa_other"),
                    id("not_registered"),
                    ProfileChannel.SOUND,
                    id("not_registered"),
                    new byte[] {0, '{', '}'});
            writeCatalogEntry(
                    buffer,
                    DEFAULT_PARTICLE,
                    PARTICLE_TYPE,
                    ProfileChannel.PARTICLE,
                    PARTICLE_TYPE,
                    envelope(0, PARTICLE_JSON));
            writeCatalogEntry(
                    buffer,
                    DEFAULT_TRAIL,
                    TRAIL_TYPE,
                    ProfileChannel.TRAIL,
                    TRAIL_TYPE,
                    envelope(0, TRAIL_JSON));
        });

        assertAll(
                () -> assertCatalogDecodeFailure(invalidRequiredDefault),
                () -> assertCatalogDecodeFailure(missingRequiredDefault));
    }

    @Test
    void catalogRejectsBoundsTruncationTrailingAndNonCanonicalVarInts() {
        var valid = encodeCatalog(new ProfileCatalogPayload(1L, defaultEntries()));
        var nonCanonicalCount = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeByte(0x83);
            buffer.writeByte(0x00);
        });
        var nonCanonicalConfigurationVersion = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(3);
            writeCatalogEntry(
                    buffer,
                    DEFAULT_PARTICLE,
                    PARTICLE_TYPE,
                    ProfileChannel.PARTICLE,
                    PARTICLE_TYPE,
                    new byte[] {(byte) 0x80, 0, '{', '}'});
        });
        var overlongId = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(3);
            buffer.writeVarInt(129);
            buffer.writeBytes(new byte[129]);
        });
        var oversizedEnvelope = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(3);
            writeResourceLocation(buffer, DEFAULT_PARTICLE);
            writeResourceLocation(buffer, PARTICLE_TYPE);
            buffer.writeByte(ProfileChannel.PARTICLE.wireCode());
            writeResourceLocation(buffer, PARTICLE_TYPE);
            buffer.writeVarInt(2_049);
        });
        var unsorted = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(3);
            writeCatalogEntry(
                    buffer,
                    DEFAULT_SOUND,
                    SOUND_TYPE,
                    ProfileChannel.SOUND,
                    SOUND_TYPE,
                    envelope(0, SOUND_JSON));
            writeCatalogEntry(
                    buffer,
                    DEFAULT_PARTICLE,
                    PARTICLE_TYPE,
                    ProfileChannel.PARTICLE,
                    PARTICLE_TYPE,
                    envelope(0, PARTICLE_JSON));
        });

        var oversizedBody = new byte[PresentationLimits.MAX_PROFILE_CATALOG_BODY_BYTES + 1];
        assertAll(
                () -> assertCatalogDecodeFailure(withCatalogHeader(1, 0L, 3)),
                () -> assertCatalogDecodeFailure(withCatalogHeader(0, 1L, 2)),
                () -> assertCatalogDecodeFailure(withCatalogHeader(0, 1L, 193)),
                () -> assertCatalogDecodeFailure(nonCanonicalCount),
                () -> assertCatalogDecodeFailure(nonCanonicalConfigurationVersion),
                () -> assertCatalogDecodeFailure(overlongId),
                () -> assertCatalogDecodeFailure(oversizedEnvelope),
                () -> assertCatalogDecodeFailure(unsorted),
                () -> assertCatalogDecodeFailure(Arrays.copyOf(valid, valid.length - 1)),
                () -> assertCatalogDecodeFailure(append(valid, 0)),
                () -> assertCatalogDecodeFailure(oversizedBody));
    }

    @Test
    void catalogConfigurationJsonDepthAndNodeBoundsAreExact() {
        var depthId = id("aaa_depth_boundary");
        var nodesId = id("aaa_nodes_boundary");
        var depthMaximum = decodeCatalog(catalogWithTreeEntry(
                depthId, treeJsonWithExactDepth(12)));
        var depthOver = decodeCatalog(catalogWithTreeEntry(
                depthId, treeJsonWithExactDepth(13)));
        var nodesMaximum = decodeCatalog(catalogWithTreeEntry(
                nodesId, treeJsonWithExactNodes(256)));
        var nodesOver = decodeCatalog(catalogWithTreeEntry(
                nodesId, treeJsonWithExactNodes(257)));

        assertAll(
                () -> assertTrue(depthMaximum.snapshot()
                        .entry(depthId)
                        .decodedConfiguration()
                        .isPresent()),
                () -> assertTrue(depthOver.snapshot()
                        .entry(depthId)
                        .decodedConfiguration()
                        .isEmpty()),
                () -> assertTrue(nodesMaximum.snapshot()
                        .entry(nodesId)
                        .decodedConfiguration()
                        .isPresent()),
                () -> assertTrue(nodesOver.snapshot()
                        .entry(nodesId)
                        .decodedConfiguration()
                        .isEmpty()));
    }

    @Test
    void catalogValueConstructionRejectsNonCanonicalAndOutOfContractValues() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P8ProfileCatalogEntry(
                                DEFAULT_SOUND,
                                SOUND_TYPE,
                                ProfileChannel.SOUND,
                                SOUND_TYPE,
                                0,
                                "{ \"pitch_milli\":1000}")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ProfileCatalogPayload(0L, defaultEntries())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ProfileCatalogPayload(1L, defaultEntries().subList(0, 2))),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ProfileCatalogPayload(
                                1L,
                                List.of(
                                        defaultSoundEntry(),
                                        defaultParticleEntry(),
                                        defaultTrailEntry()))));
    }

    @Test
    void presentationEventHasExactCanonicalBytesAndPreservesQ15() {
        var overrides = new TreeMap<ResourceLocation, Integer>(
                Comparator.comparing(ResourceLocation::toString));
        overrides.put(id("alpha"), -17);
        overrides.put(ResourceLocation.fromNamespaceAndPath("minecraft", "zeta"), 23);
        var payload = new PresentationEventPayload(
                9L,
                PresentationEventKind.HIT,
                new PresentationSourceSummary(OptionalInt.of(12), OptionalInt.of(13)),
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                new PresentationPosition(1.25D, -2.5D, 3.75D),
                new PresentationDirection((short) 18_918, (short) -18_919, (short) 18_918),
                new PresentationAppearance(
                        0x80abcdef,
                        0x10203040,
                        1_000,
                        Optional.of(DEFAULT_SOUND),
                        Optional.of(DEFAULT_PARTICLE),
                        Optional.empty(),
                        overrides),
                Long.MIN_VALUE,
                42L);
        var encoded = encodeEvent(payload);
        var expected = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(9L);
            buffer.writeByte(PresentationEventKind.HIT.wireCode());
            buffer.writeByte(3);
            buffer.writeVarInt(12);
            buffer.writeVarInt(13);
            writeResourceLocation(
                    buffer, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"));
            buffer.writeDouble(1.25D);
            buffer.writeDouble(-2.5D);
            buffer.writeDouble(3.75D);
            buffer.writeShort(18_918);
            buffer.writeShort(-18_919);
            buffer.writeShort(18_918);
            buffer.writeInt(0x80abcdef);
            buffer.writeInt(0x10203040);
            writeSelection(buffer, Optional.of(DEFAULT_SOUND));
            writeSelection(buffer, Optional.of(DEFAULT_PARTICLE));
            writeSelection(buffer, Optional.empty());
            buffer.writeVarInt(2);
            writeResourceLocation(buffer, id("alpha"));
            buffer.writeInt(-17);
            writeResourceLocation(
                    buffer, ResourceLocation.fromNamespaceAndPath("minecraft", "zeta"));
            buffer.writeInt(23);
            buffer.writeVarInt(1_000);
            buffer.writeLong(Long.MIN_VALUE);
            buffer.writeLong(42L);
        });

        var decoded = decodeEvent(encoded);
        assertAll(
                () -> assertEquals("gramarye:presentation_event",
                        PresentationEventPayload.TYPE.id().toString()),
                () -> assertArrayEquals(expected, encoded),
                () -> assertEquals(encoded.length, payload.bodySize()),
                () -> assertEquals(payload, decoded),
                () -> assertEquals((short) 18_918, decoded.direction().xQ15()),
                () -> assertEquals((short) -18_919, decoded.direction().yQ15()),
                () -> assertEquals((short) 18_918, decoded.direction().zQ15()),
                () -> assertEquals(
                        encoded.length + PresentationLimits.EVENT_PACKET_OVERHEAD_BYTES,
                        encoded.length + 29),
                () -> assertArrayEquals(encoded, encodeEvent(decoded)));
    }

    @Test
    void presentationEventRejectsEnumFlagsGeometryQ15GenerationsAndSequence() {
        assertAll(
                () -> assertEventDecodeFailure(minimalEventBody(1, 1L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 0L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 11, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 4,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", Double.NaN, 0.0D, 0.0D, 1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 30_000_001.0D, 0.0D, 0.0D,
                        1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 0.0D, 2_049.0D, 0.0D,
                        1, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D,
                        Short.MIN_VALUE, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 0, 0, 0, 0, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 2, 0, 1L)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 9, 1L)),
                () -> assertEventDecodeFailure(minimalEventBodyWithIntensity(10_001)),
                () -> assertEventDecodeFailure(minimalEventBody(0, 1L, 0, 0,
                        "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 0, 0L)));
    }

    @Test
    void presentationEventRejectsMalformedTruncatedTrailingAndNonCanonicalVarInts() {
        var valid = minimalEventBody(0, 1L, 0, 0,
                "gramarye:test", 0.0D, 0.0D, 0.0D, 1, 0, 0, 0, 0, 1L);
        var nonCanonicalEntityId = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeByte(0);
            buffer.writeByte(1);
            buffer.writeByte(0x81);
            buffer.writeByte(0x00);
            writeMinimalEventTail(
                    buffer,
                    "gramarye:test",
                    0.0D,
                    0.0D,
                    0.0D,
                    1,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1L);
        });
        var overlongDimension = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeByte(0);
            buffer.writeByte(0);
            buffer.writeVarInt(129);
            buffer.writeBytes(new byte[129]);
        });
        var unsortedOverrides = body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeByte(0);
            buffer.writeByte(0);
            writeResourceLocation(buffer, id("test"));
            buffer.writeDouble(0.0D);
            buffer.writeDouble(0.0D);
            buffer.writeDouble(0.0D);
            buffer.writeShort(1);
            buffer.writeShort(0);
            buffer.writeShort(0);
            buffer.writeInt(0);
            buffer.writeInt(0);
            buffer.writeByte(0);
            buffer.writeByte(0);
            buffer.writeByte(0);
            buffer.writeVarInt(2);
            writeResourceLocation(buffer, id("zeta"));
            buffer.writeInt(1);
            writeResourceLocation(buffer, id("alpha"));
            buffer.writeInt(2);
            buffer.writeVarInt(0);
            buffer.writeLong(0L);
            buffer.writeLong(1L);
        });
        var oversizedBody = new byte[PresentationLimits.MAX_EVENT_BODY_BYTES + 1];

        assertAll(
                () -> assertEventDecodeFailure(nonCanonicalEntityId),
                () -> assertEventDecodeFailure(overlongDimension),
                () -> assertEventDecodeFailure(unsortedOverrides),
                () -> assertEventDecodeFailure(Arrays.copyOf(valid, valid.length - 1)),
                () -> assertEventDecodeFailure(append(valid, 0)),
                () -> assertEventDecodeFailure(oversizedBody));
    }

    private static List<P8ProfileCatalogEntry> defaultEntries() {
        var entries = new ArrayList<>(List.of(
                defaultParticleEntry(), defaultSoundEntry(), defaultTrailEntry()));
        entries.sort(Comparator.comparing(P8ProfileCatalogEntry::profileId));
        return List.copyOf(entries);
    }

    private static P8ProfileCatalogEntry defaultParticleEntry() {
        return new P8ProfileCatalogEntry(
                DEFAULT_PARTICLE,
                PARTICLE_TYPE,
                ProfileChannel.PARTICLE,
                PARTICLE_TYPE,
                0,
                PARTICLE_JSON);
    }

    private static P8ProfileCatalogEntry defaultSoundEntry() {
        return new P8ProfileCatalogEntry(
                DEFAULT_SOUND,
                SOUND_TYPE,
                ProfileChannel.SOUND,
                SOUND_TYPE,
                0,
                SOUND_JSON);
    }

    private static P8ProfileCatalogEntry defaultTrailEntry() {
        return new P8ProfileCatalogEntry(
                DEFAULT_TRAIL,
                TRAIL_TYPE,
                ProfileChannel.TRAIL,
                TRAIL_TYPE,
                0,
                TRAIL_JSON);
    }

    private static void writeDefaultEntries(RegistryFriendlyByteBuf buffer) {
        writeCatalogEntry(
                buffer,
                DEFAULT_PARTICLE,
                PARTICLE_TYPE,
                ProfileChannel.PARTICLE,
                PARTICLE_TYPE,
                envelope(0, PARTICLE_JSON));
        writeCatalogEntry(
                buffer,
                DEFAULT_SOUND,
                SOUND_TYPE,
                ProfileChannel.SOUND,
                SOUND_TYPE,
                envelope(0, SOUND_JSON));
        writeCatalogEntry(
                buffer,
                DEFAULT_TRAIL,
                TRAIL_TYPE,
                ProfileChannel.TRAIL,
                TRAIL_TYPE,
                envelope(0, TRAIL_JSON));
    }

    private static byte[] catalogWithTreeEntry(
            ResourceLocation profileId, String canonicalJson) {
        return body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeVarInt(4);
            writeCatalogEntry(
                    buffer,
                    profileId,
                    P8S2TestFixtures.TREE_TYPE_ID,
                    ProfileChannel.SOUND,
                    P8S2TestFixtures.TREE_TYPE_ID,
                    envelope(0, canonicalJson));
            writeDefaultEntries(buffer);
        });
    }

    private static String treeJsonWithExactDepth(int exactDepth) {
        return P8S2TestFixtures.nestedArrayPayload(exactDepth - 2);
    }

    private static String treeJsonWithExactNodes(int exactNodes) {
        return P8S2TestFixtures.arrayPayload(exactNodes - 2);
    }

    private static void writeCatalogEntry(
            RegistryFriendlyByteBuf buffer,
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel channel,
            ResourceLocation factoryId,
            byte[] entryEnvelope) {
        writeResourceLocation(buffer, profileId);
        writeResourceLocation(buffer, typeId);
        buffer.writeByte(channel.wireCode());
        writeResourceLocation(buffer, factoryId);
        buffer.writeVarInt(entryEnvelope.length);
        buffer.writeBytes(entryEnvelope);
    }

    private static byte[] envelope(int configurationVersion, String canonicalJson) {
        return body(buffer -> {
            buffer.writeVarInt(configurationVersion);
            buffer.writeBytes(canonicalJson.getBytes(StandardCharsets.UTF_8));
        });
    }

    private static byte[] withCatalogHeader(int format, long generation, int count) {
        return body(buffer -> {
            buffer.writeByte(format);
            buffer.writeLong(generation);
            buffer.writeVarInt(count);
        });
    }

    private static byte[] minimalEventBody(
            int format,
            long catalogGeneration,
            int kind,
            int sourceFlags,
            String dimension,
            double x,
            double y,
            double z,
            int directionX,
            int directionY,
            int directionZ,
            int soundSelectionCode,
            int overrideCount,
            long sequence) {
        return body(buffer -> {
            buffer.writeByte(format);
            buffer.writeLong(catalogGeneration);
            buffer.writeByte(kind);
            buffer.writeByte(sourceFlags);
            writeMinimalEventTail(
                    buffer,
                    dimension,
                    x,
                    y,
                    z,
                    directionX,
                    directionY,
                    directionZ,
                    soundSelectionCode,
                    overrideCount,
                    0,
                    sequence);
        });
    }

    private static byte[] minimalEventBodyWithIntensity(int intensity) {
        return body(buffer -> {
            buffer.writeByte(0);
            buffer.writeLong(1L);
            buffer.writeByte(0);
            buffer.writeByte(0);
            writeMinimalEventTail(
                    buffer,
                    "gramarye:test",
                    0.0D,
                    0.0D,
                    0.0D,
                    1,
                    0,
                    0,
                    0,
                    0,
                    intensity,
                    1L);
        });
    }

    private static void writeMinimalEventTail(
            RegistryFriendlyByteBuf buffer,
            String dimension,
            double x,
            double y,
            double z,
            int directionX,
            int directionY,
            int directionZ,
            int soundSelectionCode,
            int overrideCount,
            int intensity,
            long sequence) {
        writeResourceLocation(buffer, dimension);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeShort(directionX);
        buffer.writeShort(directionY);
        buffer.writeShort(directionZ);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeByte(soundSelectionCode);
        buffer.writeByte(0);
        buffer.writeByte(0);
        buffer.writeVarInt(overrideCount);
        buffer.writeVarInt(intensity);
        buffer.writeLong(0L);
        buffer.writeLong(sequence);
    }

    private static void writeSelection(
            RegistryFriendlyByteBuf buffer, Optional<ResourceLocation> selection) {
        if (selection.isEmpty()) {
            buffer.writeByte(0);
            return;
        }
        buffer.writeByte(1);
        writeResourceLocation(buffer, selection.orElseThrow());
    }

    private static void writeResourceLocation(
            RegistryFriendlyByteBuf buffer, ResourceLocation id) {
        writeResourceLocation(buffer, id.toString());
    }

    private static void writeResourceLocation(
            RegistryFriendlyByteBuf buffer, String id) {
        var encoded = id.getBytes(StandardCharsets.UTF_8);
        buffer.writeVarInt(encoded.length);
        buffer.writeBytes(encoded);
    }

    private static byte[] encodeCatalog(ProfileCatalogPayload payload) {
        try (var owned = emptyBuffer()) {
            ProfileCatalogPayload.STREAM_CODEC.encode(owned.buffer(), payload);
            return owned.bytes();
        }
    }

    private static ProfileCatalogPayload decodeCatalog(byte[] encoded) {
        try (var owned = bufferContaining(encoded)) {
            return P8PayloadCodecSupport.decodeProfileCatalog(
                    owned.buffer(), P8S2TestFixtures.registry());
        }
    }

    private static void assertCatalogDecodeFailure(byte[] encoded) {
        try (var owned = bufferContaining(encoded)) {
            assertThrows(
                    DecoderException.class,
                    () -> P8PayloadCodecSupport.decodeProfileCatalog(
                            owned.buffer(), P8S2TestFixtures.registry()));
        }
    }

    private static byte[] encodeEvent(PresentationEventPayload payload) {
        try (var owned = emptyBuffer()) {
            PresentationEventPayload.STREAM_CODEC.encode(owned.buffer(), payload);
            return owned.bytes();
        }
    }

    private static PresentationEventPayload decodeEvent(byte[] encoded) {
        try (var owned = bufferContaining(encoded)) {
            return PresentationEventPayload.STREAM_CODEC.decode(owned.buffer());
        }
    }

    private static void assertEventDecodeFailure(byte[] encoded) {
        try (var owned = bufferContaining(encoded)) {
            assertThrows(
                    DecoderException.class,
                    () -> PresentationEventPayload.STREAM_CODEC.decode(owned.buffer()));
        }
    }

    private static byte[] body(Consumer<RegistryFriendlyByteBuf> writer) {
        try (var owned = emptyBuffer()) {
            writer.accept(owned.buffer());
            return owned.bytes();
        }
    }

    private static byte[] append(byte[] source, int unsignedByte) {
        var result = Arrays.copyOf(source, source.length + 1);
        result[source.length] = (byte) unsignedByte;
        return result;
    }

    private static OwnedBuffer emptyBuffer() {
        return new OwnedBuffer(new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.EMPTY, ConnectionType.NEOFORGE));
    }

    private static OwnedBuffer bufferContaining(byte[] source) {
        var backing = Unpooled.buffer(Math.max(1, source.length));
        backing.writeBytes(source);
        return new OwnedBuffer(new RegistryFriendlyByteBuf(
                backing, RegistryAccess.EMPTY, ConnectionType.NEOFORGE));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private static final class OwnedBuffer implements AutoCloseable {
        private final RegistryFriendlyByteBuf buffer;
        private boolean closed;

        private OwnedBuffer(RegistryFriendlyByteBuf buffer) {
            this.buffer = buffer;
            assertEquals(1, buffer.refCnt());
        }

        RegistryFriendlyByteBuf buffer() {
            assertFalse(closed);
            return buffer;
        }

        byte[] bytes() {
            assertFalse(closed);
            assertEquals(1, buffer.refCnt());
            return ByteBufUtil.getBytes(
                    buffer, buffer.readerIndex(), buffer.readableBytes(), false);
        }

        @Override
        public void close() {
            assertFalse(closed);
            assertEquals(1, buffer.refCnt());
            assertTrue(buffer.release());
            assertEquals(0, buffer.refCnt());
            closed = true;
        }
    }
}
