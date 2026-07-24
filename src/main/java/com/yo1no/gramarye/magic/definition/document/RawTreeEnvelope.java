package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

/** Package-internal V0 family-tagged, deeply isolated raw tree bytes. */
final class RawTreeEnvelope {
    private static final String FAMILY_FIELD = "family";
    private static final String REGISTRY_CONTEXT_FIELD = "registry_context";
    private static final String COMPRESSED_MAPS_FIELD = "compressed_maps";
    private static final String DATA_FIELD = "data";
    private static final Set<String> PHYSICAL_FIELDS = Set.of(
            FAMILY_FIELD,
            REGISTRY_CONTEXT_FIELD,
            COMPRESSED_MAPS_FIELD,
            DATA_FIELD);

    private final SerializedTreeContext context;
    private final ImmutableEncodedBytes data;

    private RawTreeEnvelope(SerializedTreeContext context, ImmutableEncodedBytes data) {
        this.context = Objects.requireNonNull(context, "context");
        this.data = Objects.requireNonNull(data, "data");
        if (data.size() == 0) {
            throw new IllegalArgumentException("raw tree bytes must not be empty");
        }
    }

    static SkillDocumentPersistenceResult<RawTreeEnvelope> capture(
            Dynamic<?> source,
            SkillDocumentPersistenceLocation location) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(location, "location");
        try {
            var contextResult = SupportedDynamicTrees.contextOf(source);
            if (contextResult.error().isPresent()) {
                return failure(new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location));
            }
            var copyResult = SupportedDynamicTrees.defensiveCopy(source);
            if (copyResult.error().isPresent()) {
                return failure(new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location));
            }
            var copy = copyResult.result().orElseThrow();
            var boundsFailure = boundsFailure(copy, location);
            if (boundsFailure != null) {
                return failure(boundsFailure);
            }
            var context = contextResult.result().orElseThrow();
            var maximum = byteLimit(location);
            var value = copy.getValue();
            ImmutableEncodedBytes encoded;
            if (context.family() == SerializedTreeFamily.JSON && value instanceof JsonElement json) {
                encoded = StrictJsonTreeCodec.encode(json, maximum);
            } else if (context.family() == SerializedTreeFamily.NBT && value instanceof Tag nbt) {
                encoded = StrictNbtTreeCodec.encode(nbt, maximum);
            } else {
                return failure(new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location));
            }
            return SkillDocumentPersistenceResult.success(new RawTreeEnvelope(context, encoded));
        } catch (BoundedByteEncoding.CapacityExceeded exception) {
            return failure(new SkillDocumentPersistenceFailure.RawEntryEncodedCapacityExceeded(
                    location, exception.observedAtLeast(), exception.maximum()));
        } catch (IOException exception) {
            return failure(new SkillDocumentPersistenceFailure.EncodeFailed(location));
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    location, exception));
        }
    }

    static SkillDocumentPersistenceResult<RawTreeEnvelope> decodePhysical(
            CompoundTag physical,
            SkillDocumentPersistenceLocation location) {
        Objects.requireNonNull(physical, "physical");
        Objects.requireNonNull(location, "location");
        try {
            var snapshot = physical.copy();
            if (!snapshot.getAllKeys().equals(PHYSICAL_FIELDS)) {
                return failure(new SkillDocumentPersistenceFailure.InvalidRawContext(location));
            }
            if (!(snapshot.get(FAMILY_FIELD) instanceof StringTag familyTag)) {
                return failure(new SkillDocumentPersistenceFailure.InvalidRawContext(location));
            }
            var family = family(familyTag.getAsString());
            if (family.isEmpty()) {
                return failure(new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location));
            }
            var registryContext = exactBoolean(snapshot.get(REGISTRY_CONTEXT_FIELD));
            var compressedMaps = exactBoolean(snapshot.get(COMPRESSED_MAPS_FIELD));
            if (registryContext.isEmpty() || compressedMaps.isEmpty()) {
                return failure(new SkillDocumentPersistenceFailure.InvalidRawContext(location));
            }
            if (!(snapshot.get(DATA_FIELD) instanceof ByteArrayTag bytesTag)) {
                return failure(new SkillDocumentPersistenceFailure.InvalidRawContext(location));
            }
            var bytes = bytesTag.getAsByteArray();
            var maximum = byteLimit(location);
            if (bytes.length == 0) {
                return failure(new SkillDocumentPersistenceFailure.InvalidRawContext(location));
            }
            if (bytes.length > maximum) {
                return failure(new SkillDocumentPersistenceFailure.RawEntryEncodedCapacityExceeded(
                        location, bytes.length, maximum));
            }
            var context = new SerializedTreeContext(
                    family.orElseThrow(),
                    registryContext.orElseThrow(),
                    compressedMaps.orElseThrow());
            return SkillDocumentPersistenceResult.success(new RawTreeEnvelope(
                    context,
                    ImmutableEncodedBytes.copyOf(bytes)));
        } catch (IllegalArgumentException exception) {
            return failure(new SkillDocumentPersistenceFailure.InvalidRawContext(location));
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    location, exception));
        }
    }

    CompoundTag encodePhysical() {
        var physical = new CompoundTag();
        physical.putString(FAMILY_FIELD, context.family().serializedName());
        physical.putByte(REGISTRY_CONTEXT_FIELD, (byte) (context.registryContext() ? 1 : 0));
        physical.putByte(COMPRESSED_MAPS_FIELD, (byte) (context.compressedMaps() ? 1 : 0));
        physical.putByteArray(DATA_FIELD, data.copyBytes());
        return physical;
    }

    SkillDocumentPersistenceResult<Dynamic<?>> hydrate(
            Optional<HolderLookup.Provider> provider,
            SkillDocumentPersistenceLocation location) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(location, "location");
        if (context.registryContext() && provider.isEmpty()) {
            return failure(new SkillDocumentPersistenceFailure.RegistryContextUnavailable(location));
        }
        return context.family() == SerializedTreeFamily.JSON
                ? hydrateJson(provider, location)
                : hydrateNbt(provider, location);
    }

    SerializedTreeContext context() {
        return context;
    }

    int byteCount() {
        return data.size();
    }

    byte[] copyData() {
        return data.copyBytes();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RawTreeEnvelope envelope
                        && context.equals(envelope.context)
                        && data.equals(envelope.data);
    }

    @Override
    public int hashCode() {
        return 31 * context.hashCode() + data.hashCode();
    }

    @Override
    public String toString() {
        return "RawTreeEnvelope[family=" + context.family().serializedName()
                + ", registryContext=" + context.registryContext()
                + ", compressedMaps=" + context.compressedMaps()
                + ", byteCount=" + data.size() + "]";
    }

    private SkillDocumentPersistenceResult<Dynamic<?>> hydrateJson(
            Optional<HolderLookup.Provider> provider,
            SkillDocumentPersistenceLocation location) {
        try {
            var value = StrictJsonTreeCodec.decode(data, byteLimit(location));
            var parent = context.compressedMaps() ? JsonOps.COMPRESSED : JsonOps.INSTANCE;
            var ops = context.registryContext()
                    ? RegistryOps.create(parent, provider.orElseThrow())
                    : parent;
            return bounded(new Dynamic<>(ops, value), location);
        } catch (BoundedByteEncoding.CapacityExceeded exception) {
            return failure(new SkillDocumentPersistenceFailure.RawEntryEncodedCapacityExceeded(
                    location, exception.observedAtLeast(), exception.maximum()));
        } catch (IOException exception) {
            return failure(new SkillDocumentPersistenceFailure.MalformedJsonRaw(location));
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    location, exception));
        }
    }

    private SkillDocumentPersistenceResult<Dynamic<?>> hydrateNbt(
            Optional<HolderLookup.Provider> provider,
            SkillDocumentPersistenceLocation location) {
        try {
            var value = StrictNbtTreeCodec.decode(data, byteLimit(location));
            var ops = context.registryContext()
                    ? RegistryOps.create(NbtOps.INSTANCE, provider.orElseThrow())
                    : NbtOps.INSTANCE;
            return bounded(new Dynamic<>(ops, value), location);
        } catch (BoundedByteEncoding.CapacityExceeded exception) {
            return failure(new SkillDocumentPersistenceFailure.RawEntryEncodedCapacityExceeded(
                    location, exception.observedAtLeast(), exception.maximum()));
        } catch (IOException exception) {
            return failure(new SkillDocumentPersistenceFailure.MalformedNbtRaw(location));
        } catch (RuntimeException exception) {
            return failure(SkillDocumentPersistenceFailure.InternalCodecException.from(
                    location, exception));
        }
    }

    private static SkillDocumentPersistenceResult<Dynamic<?>> bounded(
            Dynamic<?> dynamic,
            SkillDocumentPersistenceLocation location) {
        var failure = boundsFailure(dynamic, location);
        return failure == null
                ? SkillDocumentPersistenceResult.success(dynamic)
                : failure(failure);
    }

    private static SkillDocumentPersistenceFailure boundsFailure(
            Dynamic<?> dynamic,
            SkillDocumentPersistenceLocation location) {
        return switch (DynamicTreeBounds.check(
                dynamic,
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH,
                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES)) {
            case WITHIN_LIMITS -> null;
            case DEPTH_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    location, SkillDocumentPersistenceFailure.DocumentBoundKind.DEPTH);
            case NODE_COUNT_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    location, SkillDocumentPersistenceFailure.DocumentBoundKind.NODE_COUNT);
            case KEY_LENGTH_EXCEEDED -> new SkillDocumentPersistenceFailure.DocumentBoundsExceeded(
                    location, SkillDocumentPersistenceFailure.DocumentBoundKind.KEY_LENGTH);
            case UNSUPPORTED -> new SkillDocumentPersistenceFailure.UnsupportedRawFamily(location);
        };
    }

    private static Optional<SerializedTreeFamily> family(String serialized) {
        for (var family : SerializedTreeFamily.values()) {
            if (family.serializedName().equals(serialized)) {
                return Optional.of(family);
            }
        }
        return Optional.empty();
    }

    private static Optional<Boolean> exactBoolean(Tag tag) {
        if (!(tag instanceof ByteTag byteTag)) {
            return Optional.empty();
        }
        return switch (byteTag.getAsByte()) {
            case 0 -> Optional.of(false);
            case 1 -> Optional.of(true);
            default -> Optional.empty();
        };
    }

    private static int byteLimit(SkillDocumentPersistenceLocation location) {
        return location instanceof SkillDocumentPersistenceLocation.TopAppearance
                        || location instanceof SkillDocumentPersistenceLocation.AppearanceOverride
                ? MagicSafetyCeilings.MAX_UNPARSED_APPEARANCE_BYTES
                : MagicSafetyCeilings.MAX_RAW_PAYLOAD_BYTES;
    }

    private static <T> SkillDocumentPersistenceResult<T> failure(
            SkillDocumentPersistenceFailure failure) {
        return SkillDocumentPersistenceResult.failure(failure);
    }
}
