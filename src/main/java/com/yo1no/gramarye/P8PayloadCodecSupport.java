package com.yo1no.gramarye;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceLocation;

/** Canonical bounded codecs shared by the two P8 clientbound payloads. */
final class P8PayloadCodecSupport {
    private static final int FORMAT_V0 = 0;
    private static final int MIN_CATALOG_ENTRIES = 3;
    private static final int SOURCE_ENTITY_PRESENT = 1;
    private static final int TARGET_ENTITY_PRESENT = 1 << 1;
    private static final int ALLOWED_SOURCE_FLAGS =
            SOURCE_ENTITY_PRESENT | TARGET_ENTITY_PRESENT;
    private static final int SELECTION_DISABLED = 0;
    private static final int SELECTION_SPECIFIED = 1;

    private static final Gson CANONICAL_JSON =
            new GsonBuilder().disableHtmlEscaping().create();
    private static final ValidationContext PROFILE_VALIDATION_CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    private P8PayloadCodecSupport() {
        throw new AssertionError("no instances");
    }

    static void encodeProfileCatalog(
            RegistryFriendlyByteBuf buffer, ProfileCatalogPayload payload) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(payload, "payload");
        var start = buffer.writerIndex();
        try {
            buffer.writeByte(FORMAT_V0);
            buffer.writeLong(payload.catalogGeneration());
            buffer.writeVarInt(payload.entries().size());
            for (var entry : payload.entries()) {
                writeResourceLocation(
                        buffer,
                        entry.profileId(),
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        "catalog Profile ID");
                writeResourceLocation(
                        buffer,
                        entry.typeId(),
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        "catalog type ID");
                buffer.writeByte(entry.channel().wireCode());
                writeResourceLocation(
                        buffer,
                        entry.clientFactoryId(),
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        "catalog client factory ID");
                buffer.writeVarInt(entry.envelopeBytes());
                buffer.writeVarInt(entry.configurationVersion());
                buffer.writeBytes(entry.canonicalConfigurationJsonForEncoding()
                        .getBytes(StandardCharsets.UTF_8));
            }
            requireEncodedBodySize(
                    buffer.writerIndex() - start,
                    payload.bodySize(),
                    PresentationLimits.MAX_PROFILE_CATALOG_BODY_BYTES,
                    "Profile catalog");
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode P8 Profile catalog", failure);
        }
    }

    static ProfileCatalogPayload decodeProfileCatalog(RegistryFriendlyByteBuf buffer) {
        return decodeProfileCatalog(buffer, MagicRegistries.profileTypeRegistry());
    }

    static ProfileCatalogPayload decodeProfileCatalog(
            RegistryFriendlyByteBuf buffer, Registry<ProfileType<?>> profileTypes) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(profileTypes, "profileTypes");
        requireDecodeBodySize(
                buffer.readableBytes(),
                1 + Long.BYTES + 1,
                PresentationLimits.MAX_PROFILE_CATALOG_BODY_BYTES,
                "Profile catalog");
        try {
            requireFormat(buffer.readUnsignedByte(), "Profile catalog");
            var catalogGeneration = buffer.readLong();
            if (catalogGeneration < 1L) {
                throw malformed("Profile catalog generation is not positive");
            }
            var entryCount = readCanonicalVarInt(buffer, "Profile catalog entry count");
            if (entryCount < MIN_CATALOG_ENTRIES
                    || entryCount > PresentationLimits.MAX_PROFILE_INSTANCES) {
                throw malformed("Profile catalog entry count is outside its bound");
            }

            var entries = new ArrayList<P8ProfileCatalogEntry>(entryCount);
            ResourceLocation previousId = null;
            for (var index = 0; index < entryCount; index++) {
                var profileId = readResourceLocation(
                        buffer,
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        "catalog Profile ID");
                if (previousId != null && previousId.compareTo(profileId) >= 0) {
                    throw malformed("Profile catalog IDs are not strictly ascending");
                }
                var typeId = readResourceLocation(
                        buffer,
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        "catalog type ID");
                var channel = ProfileChannel.fromWireCode(buffer.readUnsignedByte())
                        .orElseThrow(() -> malformed("Profile catalog channel is unknown"));
                var clientFactoryId = readResourceLocation(
                        buffer,
                        PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                        "catalog client factory ID");
                var envelopeLength = readCanonicalVarInt(
                        buffer, "Profile catalog envelope length");
                if (envelopeLength < PresentationLimits.MIN_PROFILE_ENVELOPE_BYTES
                        || envelopeLength > PresentationLimits.MAX_PROFILE_ENVELOPE_BYTES
                        || envelopeLength > buffer.readableBytes()) {
                    throw malformed("Profile catalog envelope length is invalid");
                }

                var envelope = buffer.readSlice(envelopeLength);
                var configurationVersion = readCanonicalVarInt(
                        envelope, "Profile configuration version");
                if (configurationVersion < 0
                        || configurationVersion
                                > PresentationLimits.MAX_PROFILE_CONFIGURATION_VERSION) {
                    throw malformed("Profile configuration version is outside its bound");
                }
                Optional<String> retainedJson = Optional.empty();
                Optional<P8DecodedProfileConfiguration<?>> decodedConfiguration =
                        Optional.empty();
                var localType = profileTypes.getOptional(typeId);
                if (localType.isPresent()
                        && matchesKnownType(
                                localType.orElseThrow(),
                                channel,
                                clientFactoryId,
                                configurationVersion)) {
                    var canonicalJson = tryReadCanonicalConfigurationJson(envelope);
                    if (canonicalJson.isPresent()) {
                        decodedConfiguration = decodeKnownConfiguration(
                                localType.orElseThrow(),
                                canonicalJson.orElseThrow().element());
                        if (decodedConfiguration.isPresent()) {
                            retainedJson = Optional.of(canonicalJson.orElseThrow().source());
                        }
                    }
                } else {
                    envelope.skipBytes(envelope.readableBytes());
                }
                requireFullyConsumed(envelope, "Profile catalog envelope");
                entries.add(P8ProfileCatalogEntry.incoming(
                        profileId,
                        typeId,
                        channel,
                        clientFactoryId,
                        configurationVersion,
                        envelopeLength,
                        retainedJson,
                        decodedConfiguration));
                previousId = profileId;
            }
            requireFullyConsumed(buffer, "Profile catalog");
            var payload = ProfileCatalogPayload.incoming(catalogGeneration, entries);
            payload.snapshot().requireLocallyDecodedDefaults();
            return payload;
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed P8 Profile catalog", failure);
        }
    }

    static void encodePresentationEvent(
            RegistryFriendlyByteBuf buffer, PresentationEventPayload payload) {
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(payload, "payload");
        var start = buffer.writerIndex();
        try {
            buffer.writeByte(FORMAT_V0);
            buffer.writeLong(payload.catalogGeneration());
            buffer.writeByte(payload.kind().wireCode());
            var source = payload.sourceSummary();
            var sourceFlags = 0;
            if (source.sourceEntityId().isPresent()) {
                sourceFlags |= SOURCE_ENTITY_PRESENT;
            }
            if (source.targetEntityId().isPresent()) {
                sourceFlags |= TARGET_ENTITY_PRESENT;
            }
            buffer.writeByte(sourceFlags);
            source.sourceEntityId().ifPresent(buffer::writeVarInt);
            source.targetEntityId().ifPresent(buffer::writeVarInt);
            writeResourceLocation(
                    buffer,
                    payload.dimension(),
                    PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                    "presentation dimension");
            buffer.writeDouble(payload.position().x());
            buffer.writeDouble(payload.position().y());
            buffer.writeDouble(payload.position().z());
            buffer.writeShort(payload.direction().xQ15());
            buffer.writeShort(payload.direction().yQ15());
            buffer.writeShort(payload.direction().zQ15());
            var appearance = payload.appearance();
            buffer.writeInt(appearance.primaryArgb());
            buffer.writeInt(appearance.secondaryArgb());
            writeSelection(buffer, appearance.soundProfileId());
            writeSelection(buffer, appearance.particleProfileId());
            writeSelection(buffer, appearance.trailProfileId());
            buffer.writeVarInt(appearance.parameters().size());
            for (var entry : appearance.parameters().entrySet()) {
                writeResourceLocation(
                        buffer,
                        entry.getKey(),
                        PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES,
                        "presentation override key");
                buffer.writeInt(entry.getValue());
            }
            buffer.writeVarInt(appearance.intensityMilli());
            buffer.writeLong(payload.visualSeed());
            buffer.writeLong(payload.sequence());
            requireEncodedBodySize(
                    buffer.writerIndex() - start,
                    payload.bodySize(),
                    PresentationLimits.MAX_EVENT_BODY_BYTES,
                    "presentation event");
        } catch (EncoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new EncoderException("unable to encode P8 presentation event", failure);
        }
    }

    static PresentationEventPayload decodePresentationEvent(
            RegistryFriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        var encodedBodySize = buffer.readableBytes();
        requireDecodeBodySize(
                encodedBodySize,
                1 + Long.BYTES + 1 + 1,
                PresentationLimits.MAX_EVENT_BODY_BYTES,
                "presentation event");
        try {
            requireFormat(buffer.readUnsignedByte(), "presentation event");
            var catalogGeneration = buffer.readLong();
            if (catalogGeneration < 1L) {
                throw malformed("presentation-event catalog generation is not positive");
            }
            var kind = PresentationEventKind.fromWireCode(buffer.readUnsignedByte())
                    .orElseThrow(() -> malformed("presentation-event kind is unknown"));
            var sourceFlags = buffer.readUnsignedByte();
            if ((sourceFlags & ~ALLOWED_SOURCE_FLAGS) != 0) {
                throw malformed("presentation-event source flags are reserved");
            }
            var sourceEntityId = (sourceFlags & SOURCE_ENTITY_PRESENT) == 0
                    ? OptionalInt.empty()
                    : OptionalInt.of(readPositiveCanonicalVarInt(
                            buffer, "presentation source entity ID"));
            var targetEntityId = (sourceFlags & TARGET_ENTITY_PRESENT) == 0
                    ? OptionalInt.empty()
                    : OptionalInt.of(readPositiveCanonicalVarInt(
                            buffer, "presentation target entity ID"));
            var dimension = readResourceLocation(
                    buffer,
                    PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                    "presentation dimension");
            var position = new PresentationPosition(
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            var direction = new PresentationDirection(
                    buffer.readShort(), buffer.readShort(), buffer.readShort());
            var primaryArgb = buffer.readInt();
            var secondaryArgb = buffer.readInt();
            var soundProfile = readSelection(buffer, "sound");
            var particleProfile = readSelection(buffer, "particle");
            var trailProfile = readSelection(buffer, "trail");
            var overrideCount = readCanonicalVarInt(
                    buffer, "presentation override count");
            if (overrideCount < 0
                    || overrideCount > PresentationLimits.MAX_EVENT_OVERRIDES) {
                throw malformed("presentation override count is outside its bound");
            }
            var overrides = new TreeMap<ResourceLocation, Integer>();
            ResourceLocation previousKey = null;
            for (var index = 0; index < overrideCount; index++) {
                var key = readResourceLocation(
                        buffer,
                        PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES,
                        "presentation override key");
                if (previousKey != null
                        && previousKey.toString().compareTo(key.toString()) >= 0) {
                    throw malformed("presentation override keys are not strictly ascending");
                }
                overrides.put(key, buffer.readInt());
                previousKey = key;
            }
            var intensity = readCanonicalVarInt(buffer, "presentation intensity");
            if (intensity < 0 || intensity > PresentationLimits.MAX_INTENSITY_MILLI) {
                throw malformed("presentation intensity is outside its bound");
            }
            var visualSeed = buffer.readLong();
            var sequence = buffer.readLong();
            if (sequence < PresentationLimits.MIN_SEQUENCE) {
                throw malformed("presentation sequence is not positive");
            }
            requireFullyConsumed(buffer, "presentation event");
            var payload = new PresentationEventPayload(
                    catalogGeneration,
                    kind,
                    new PresentationSourceSummary(sourceEntityId, targetEntityId),
                    dimension,
                    position,
                    direction,
                    new PresentationAppearance(
                            primaryArgb,
                            secondaryArgb,
                            intensity,
                            soundProfile,
                            particleProfile,
                            trailProfile,
                            overrides),
                    visualSeed,
                    sequence);
            if (payload.bodySize() != encodedBodySize) {
                throw malformed("presentation-event body size is not canonical");
            }
            return payload;
        } catch (DecoderException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new DecoderException("malformed P8 presentation event", failure);
        }
    }

    static int profileCatalogEntryBodySize(P8ProfileCatalogEntry entry) {
        Objects.requireNonNull(entry, "entry");
        var bytes = resourceLocationBodySize(entry.profileId());
        bytes = Math.addExact(bytes, resourceLocationBodySize(entry.typeId()));
        bytes = Math.addExact(bytes, 1);
        bytes = Math.addExact(bytes, resourceLocationBodySize(entry.clientFactoryId()));
        bytes = Math.addExact(bytes, canonicalVarIntSize(entry.envelopeBytes()));
        return Math.addExact(bytes, entry.envelopeBytes());
    }

    static int profileCatalogBodySize(Iterable<P8ProfileCatalogEntry> entries) {
        Objects.requireNonNull(entries, "entries");
        var count = 0;
        var bytes = Math.addExact(1, Long.BYTES);
        for (var entry : entries) {
            count = Math.addExact(count, 1);
            bytes = Math.addExact(bytes, Objects.requireNonNull(entry, "entry").wireBodyBytes());
        }
        bytes = Math.addExact(bytes, canonicalVarIntSize(count));
        return bytes;
    }

    static int presentationEventBodySize(PresentationEventPayload payload) {
        Objects.requireNonNull(payload, "payload");
        var bytes = Math.addExact(1, Long.BYTES);
        bytes = Math.addExact(bytes, 1 + 1);
        var source = payload.sourceSummary();
        if (source.sourceEntityId().isPresent()) {
            bytes = Math.addExact(
                    bytes, canonicalVarIntSize(source.sourceEntityId().orElseThrow()));
        }
        if (source.targetEntityId().isPresent()) {
            bytes = Math.addExact(
                    bytes, canonicalVarIntSize(source.targetEntityId().orElseThrow()));
        }
        bytes = Math.addExact(bytes, resourceLocationBodySize(payload.dimension()));
        bytes = Math.addExact(bytes, Double.BYTES * 3);
        bytes = Math.addExact(bytes, Short.BYTES * 3);
        bytes = Math.addExact(bytes, Integer.BYTES * 2);
        var appearance = payload.appearance();
        bytes = Math.addExact(bytes, selectionBodySize(appearance.soundProfileId()));
        bytes = Math.addExact(bytes, selectionBodySize(appearance.particleProfileId()));
        bytes = Math.addExact(bytes, selectionBodySize(appearance.trailProfileId()));
        bytes = Math.addExact(bytes, canonicalVarIntSize(appearance.parameters().size()));
        for (var entry : appearance.parameters().entrySet()) {
            requireResourceLocation(
                    entry.getKey(),
                    PresentationLimits.MAX_PARAMETER_KEY_UTF8_BYTES,
                    "presentation override key");
            bytes = Math.addExact(bytes, resourceLocationBodySize(entry.getKey()));
            bytes = Math.addExact(bytes, Integer.BYTES);
        }
        bytes = Math.addExact(bytes, canonicalVarIntSize(appearance.intensityMilli()));
        bytes = Math.addExact(bytes, Long.BYTES * 2);
        return bytes;
    }

    static int canonicalVarIntSize(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("canonical VarInt value cannot be negative");
        }
        return VarInt.getByteSize(value);
    }

    static ResourceLocation requireResourceLocation(
            ResourceLocation value, int maximumUtf8Bytes, String label) {
        Objects.requireNonNull(value, label);
        if (utf8Bytes(value.toString()).length > maximumUtf8Bytes) {
            throw new IllegalArgumentException(label + " exceeds its UTF-8 bound");
        }
        return value;
    }

    static void requireCanonicalConfigurationJson(String source) {
        Objects.requireNonNull(source, "source");
        var encoded = utf8Bytes(source);
        if (encoded.length < 2
                || encoded.length > PresentationLimits.MAX_PROFILE_ENVELOPE_BYTES) {
            throw new IllegalArgumentException(
                    "canonical Profile configuration JSON length is invalid");
        }
        var canonical = parseCanonicalConfigurationJson(encoded);
        if (!canonical.source().equals(source)) {
            throw new IllegalArgumentException(
                    "Profile configuration JSON is not canonical");
        }
    }

    private static Optional<CanonicalJson> tryReadCanonicalConfigurationJson(
            ByteBuf envelope) {
        var jsonLength = envelope.readableBytes();
        var bytes = new byte[jsonLength];
        envelope.readBytes(bytes);
        try {
            if (jsonLength < 2
                    || jsonLength > PresentationLimits.MAX_PROFILE_ENVELOPE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(parseCanonicalConfigurationJson(bytes));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    private static CanonicalJson parseCanonicalConfigurationJson(byte[] source) {
        var decoded = decodeUtf8(source, "Profile configuration JSON");
        requireBoundedStrictJsonBeforeTreeMaterialization(decoded);
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(decoded);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Profile configuration JSON is malformed", failure);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Profile configuration JSON is not an object");
        }
        var canonicalElement = sortJson(parsed);
        var canonicalSource = CANONICAL_JSON.toJson(canonicalElement);
        if (!canonicalSource.equals(decoded)) {
            throw new IllegalArgumentException("Profile configuration JSON is not canonical");
        }
        return new CanonicalJson(canonicalSource, canonicalElement.getAsJsonObject());
    }

    private static void requireBoundedStrictJsonBeforeTreeMaterialization(String source) {
        var nodes = new int[] {0};
        try (var reader = new JsonReader(new StringReader(source))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException(
                        "Profile configuration JSON is not an object");
            }
            scanJsonValue(reader, 1, nodes);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException(
                        "Profile configuration JSON contains trailing content");
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException(
                    "Profile configuration JSON is malformed or exceeds its bound", failure);
        }
    }

    private static void scanJsonValue(JsonReader reader, int depth, int[] nodes)
            throws IOException {
        requireJsonValueAvailable(depth, nodes);
        nodes[0]++;
        switch (reader.peek()) {
            case BEGIN_OBJECT -> scanJsonObject(reader, depth, nodes);
            case BEGIN_ARRAY -> scanJsonArray(reader, depth, nodes);
            case STRING -> requireJsonString(reader.nextString());
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IllegalArgumentException(
                    "Profile configuration JSON contains an invalid token");
        }
    }

    private static void scanJsonObject(JsonReader reader, int depth, int[] nodes)
            throws IOException {
        var names = new HashSet<String>();
        reader.beginObject();
        while (reader.hasNext()) {
            requireJsonValueAvailable(depth + 1, nodes);
            var name = reader.nextName();
            requireJsonString(name);
            if (!names.add(name)) {
                throw new IllegalArgumentException(
                        "Profile configuration JSON contains a duplicate key");
            }
            scanJsonValue(reader, depth + 1, nodes);
        }
        reader.endObject();
    }

    private static void scanJsonArray(JsonReader reader, int depth, int[] nodes)
            throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            scanJsonValue(reader, depth + 1, nodes);
        }
        reader.endArray();
    }

    private static void requireJsonValueAvailable(int depth, int[] nodes) {
        if (depth > PresentationLimits.MAX_PROFILE_JSON_DEPTH
                || nodes[0] == PresentationLimits.MAX_PROFILE_JSON_NODES_PER_ENTRY) {
            throw new IllegalArgumentException("Profile configuration JSON exceeds its bound");
        }
    }

    private static boolean matchesKnownType(
            ProfileType<?> type,
            ProfileChannel channel,
            ResourceLocation clientFactoryId,
            int configurationVersion) {
        Objects.requireNonNull(type, "type");
        return type.channel() == channel
                && type.currentConfigurationVersion() == configurationVersion
                && type.clientFactoryKey().id().equals(clientFactoryId);
    }

    private static <C extends ProfileConfiguration>
            Optional<P8DecodedProfileConfiguration<?>> decodeKnownConfiguration(
                    ProfileType<C> type,
                    JsonObject configuration) {
        Objects.requireNonNull(type, "type");
        var decoded = type.configurationCodec().codec().parse(JsonOps.INSTANCE, configuration);
        if (decoded.error().isPresent() || decoded.result().isEmpty()) {
            return Optional.empty();
        }
        var value = decoded.result().orElseThrow();
        var validation = Objects.requireNonNull(
                type.validate(value, PROFILE_VALIDATION_CONTEXT),
                "Profile validation result");
        if (!validation.isValid()) {
            return Optional.empty();
        }
        var estimatedCost = Objects.requireNonNull(
                type.estimateCost(value), "Profile estimated cost");
        return Optional.of(new P8DecodedProfileConfiguration<>(
                type, value, estimatedCost));
    }

    private static void writeSelection(
            RegistryFriendlyByteBuf buffer, Optional<ResourceLocation> selection) {
        Objects.requireNonNull(selection, "selection");
        if (selection.isEmpty()) {
            buffer.writeByte(SELECTION_DISABLED);
            return;
        }
        buffer.writeByte(SELECTION_SPECIFIED);
        writeResourceLocation(
                buffer,
                selection.orElseThrow(),
                PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                "presentation Profile selection");
    }

    private static Optional<ResourceLocation> readSelection(
            RegistryFriendlyByteBuf buffer, String channel) {
        return switch (buffer.readUnsignedByte()) {
            case SELECTION_DISABLED -> Optional.empty();
            case SELECTION_SPECIFIED -> Optional.of(readResourceLocation(
                    buffer,
                    PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                    channel + " Profile selection"));
            default -> throw malformed(channel + " Profile selection code is invalid");
        };
    }

    private static int selectionBodySize(Optional<ResourceLocation> selection) {
        Objects.requireNonNull(selection, "selection");
        return selection.isEmpty()
                ? 1
                : Math.addExact(1, resourceLocationBodySize(selection.orElseThrow()));
    }

    private static void writeResourceLocation(
            ByteBuf buffer,
            ResourceLocation value,
            int maximumUtf8Bytes,
            String label) {
        requireResourceLocation(value, maximumUtf8Bytes, label);
        var encoded = utf8Bytes(value.toString());
        writeCanonicalVarInt(buffer, encoded.length);
        buffer.writeBytes(encoded);
    }

    private static ResourceLocation readResourceLocation(
            ByteBuf buffer, int maximumUtf8Bytes, String label) {
        var length = readCanonicalVarInt(buffer, label + " length");
        if (length < 1 || length > maximumUtf8Bytes || length > buffer.readableBytes()) {
            throw malformed(label + " length is invalid");
        }
        var encoded = new byte[length];
        buffer.readBytes(encoded);
        var decoded = decodeUtf8(encoded, label);
        var value = ResourceLocation.tryParse(decoded);
        if (value == null || !value.toString().equals(decoded)) {
            throw malformed(label + " is invalid");
        }
        return value;
    }

    private static int resourceLocationBodySize(ResourceLocation value) {
        requireResourceLocation(
                value,
                PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES,
                "resource location");
        var payloadBytes = utf8Bytes(value.toString()).length;
        return Math.addExact(canonicalVarIntSize(payloadBytes), payloadBytes);
    }

    private static String decodeUtf8(byte[] encoded, String label) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException(label + " is not valid UTF-8", failure);
        }
    }

    private static byte[] utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static int readPositiveCanonicalVarInt(ByteBuf buffer, String label) {
        var value = readCanonicalVarInt(buffer, label);
        if (value < 1) {
            throw malformed(label + " is not positive");
        }
        return value;
    }

    private static int readCanonicalVarInt(ByteBuf buffer, String label) {
        var value = 0;
        for (var position = 0; position < 5; position++) {
            var rawByte = buffer.readUnsignedByte();
            if (position == 4 && (rawByte & 0xf8) != 0) {
                throw malformed(label + " exceeds Integer.MAX_VALUE");
            }
            value |= (rawByte & 0x7f) << (position * 7);
            if ((rawByte & 0x80) == 0) {
                if (position + 1 != canonicalVarIntSize(value)) {
                    throw malformed(label + " is not canonically encoded");
                }
                return value;
            }
        }
        throw malformed(label + " exceeds five bytes");
    }

    private static void writeCanonicalVarInt(ByteBuf buffer, int value) {
        if (value < 0) {
            throw new EncoderException("canonical VarInt value cannot be negative");
        }
        var remaining = value;
        while ((remaining & ~0x7f) != 0) {
            buffer.writeByte((remaining & 0x7f) | 0x80);
            remaining >>>= 7;
        }
        buffer.writeByte(remaining);
    }

    private static void requireFormat(int format, String label) {
        if (format != FORMAT_V0) {
            throw malformed(label + " format is unknown");
        }
    }

    private static void requireEncodedBodySize(
            int actual, int expected, int maximum, String label) {
        if (actual != expected || actual < 0 || actual > maximum) {
            throw new EncoderException(label + " encoded body size is invalid");
        }
    }

    private static void requireDecodeBodySize(
            int actual, int minimum, int maximum, String label) {
        if (actual < minimum || actual > maximum) {
            throw malformed(label + " body size is invalid");
        }
    }

    private static void requireFullyConsumed(ByteBuf buffer, String label) {
        if (buffer.readableBytes() != 0) {
            throw malformed(label + " contains trailing bytes");
        }
    }

    private static void requireJsonString(String value) {
        if (utf8Bytes(value).length > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalArgumentException("Profile JSON string exceeds its UTF-8 bound");
        }
    }

    private static JsonElement sortJson(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            var members = new TreeMap<String, JsonElement>();
            for (Map.Entry<String, JsonElement> member
                    : element.getAsJsonObject().entrySet()) {
                members.put(member.getKey(), member.getValue());
            }
            members.forEach((name, value) -> result.add(name, sortJson(value)));
            return result;
        }
        if (element.isJsonArray()) {
            var result = new JsonArray();
            for (var value : element.getAsJsonArray()) {
                result.add(sortJson(value));
            }
            return result;
        }
        return element.deepCopy();
    }

    private static DecoderException malformed(String message) {
        return new DecoderException(message);
    }

    private record CanonicalJson(String source, JsonObject element) {
        private CanonicalJson {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(element, "element");
        }
    }
}
