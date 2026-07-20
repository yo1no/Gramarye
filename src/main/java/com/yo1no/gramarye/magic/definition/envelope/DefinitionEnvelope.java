package com.yo1no.gramarye.magic.definition.envelope;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Registry-independent serialized boundary around one Trigger or Action payload. */
public final class DefinitionEnvelope {
    static final int MAX_TO_STRING_LENGTH = 256;
    private static final int MAX_TO_STRING_TYPE_ID_LENGTH = 128;

    private static final Codec<SerializedFields> SERIALIZED_FIELDS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("type").forGetter(SerializedFields::typeId),
            Codec.INT.fieldOf("schema_version").forGetter(SerializedFields::schemaVersion),
            Codec.PASSTHROUGH.fieldOf("payload").forGetter(SerializedFields::serializedPayload))
            .apply(instance, SerializedFields::new));

    public static final Codec<DefinitionEnvelope> CODEC = SERIALIZED_FIELDS_CODEC.flatXmap(
            DefinitionEnvelope::fromSerializedFields,
            envelope -> DataResult.success(new SerializedFields(
                    envelope.typeId(),
                    envelope.schemaVersion(),
                    envelope.copyRawPayload())));

    private final ResourceLocation typeId;
    private final int schemaVersion;
    private final RawPayloadSnapshot rawPayloadSnapshot;

    public DefinitionEnvelope(
            ResourceLocation typeId,
            int schemaVersion,
            Dynamic<?> rawPayload) {
        this(
                typeId,
                schemaVersion,
                RawPayloadSnapshot.requireSupported(Objects.requireNonNull(rawPayload, "rawPayload")));
    }

    private DefinitionEnvelope(
            ResourceLocation typeId,
            int schemaVersion,
            RawPayloadSnapshot rawPayloadSnapshot) {
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must not be negative");
        }
        this.schemaVersion = schemaVersion;
        this.rawPayloadSnapshot = Objects.requireNonNull(rawPayloadSnapshot, "rawPayloadSnapshot");
    }

    public ResourceLocation typeId() {
        return typeId;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    /** Returns a new deep payload snapshot; the envelope's internal tree is never exposed. */
    public Dynamic<?> copyRawPayload() {
        return rawPayloadSnapshot.copyDynamic();
    }

    RawPayloadSnapshot rawPayloadSnapshot() {
        return rawPayloadSnapshot;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof DefinitionEnvelope envelope
                && schemaVersion == envelope.schemaVersion
                && typeId.equals(envelope.typeId)
                && rawPayloadSnapshot.structurallyEquals(envelope.rawPayloadSnapshot);
    }

    @Override
    public int hashCode() {
        var result = typeId.hashCode();
        result = 31 * result + Integer.hashCode(schemaVersion);
        return 31 * result + rawPayloadSnapshot.structuralHashCode();
    }

    @Override
    public String toString() {
        return "DefinitionEnvelope[typeId=" + abbreviatedTypeId()
                + ", schemaVersion=" + schemaVersion
                + ", payloadFamily=" + rawPayloadSnapshot.familyName() + "]";
    }

    private static DataResult<DefinitionEnvelope> fromSerializedFields(SerializedFields fields) {
        if (fields.schemaVersion() < 0) {
            return DataResult.error(() -> "schema_version must not be negative");
        }
        return RawPayloadSnapshot.capture(fields.serializedPayload())
                .map(snapshot -> new DefinitionEnvelope(fields.typeId(), fields.schemaVersion(), snapshot));
    }

    private String abbreviatedTypeId() {
        var value = typeId.toString();
        if (value.length() <= MAX_TO_STRING_TYPE_ID_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TO_STRING_TYPE_ID_LENGTH - 3) + "...";
    }

    private record SerializedFields(
            ResourceLocation typeId,
            int schemaVersion,
            Dynamic<?> serializedPayload) {
    }
}
