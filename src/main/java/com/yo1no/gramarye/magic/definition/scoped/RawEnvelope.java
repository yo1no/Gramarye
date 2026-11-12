package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.scoped.ScopeDData.RawLocation;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeContext;
import com.yo1no.gramarye.magic.definition.tree.SerializedTreeFamily;
import com.yo1no.gramarye.magic.definition.tree.SupportedDynamicTrees;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;

/** Same-family bytes bound to one typed raw slot; mutable input/output trees are never retained. */
final class RawEnvelope {
    private final RawLocation location;
    private final String tag;
    private final int payloadVersion;
    private final SerializedTreeContext context;
    private final byte[] bytes;

    private RawEnvelope(RawLocation location, String tag, int payloadVersion,
                        SerializedTreeContext context, byte[] bytes) throws IOException {
        this.location = Objects.requireNonNull(location);
        ScopeDData.tag(tag);
        this.tag = tag;
        if (payloadVersion < 0) {
            throw new IllegalArgumentException("invalid raw payload version");
        }
        this.payloadVersion = payloadVersion;
        this.context = Objects.requireNonNull(context);
        if (bytes.length == 0 || bytes.length > MAX_RAW_SLOT_BYTES) {
            throw new IOException("raw slot byte capacity exceeded or empty");
        }
        this.bytes = bytes.clone();
        preflight();
    }

    static RawEnvelope fromBytes(RawLocation location, String tag, int payloadVersion,
                                 SerializedTreeContext context, byte[] bytes) throws IOException {
        return new RawEnvelope(location, tag, payloadVersion, context, bytes);
    }

    static RawEnvelope capture(RawLocation location, String tag, int payloadVersion, Dynamic<?> source)
            throws IOException {
        var context = SupportedDynamicTrees.contextOf(source).result()
                .orElseThrow(() -> new IOException("unsupported raw family or ops"));
        if (DynamicTreeBounds.check(source, MAX_RAW_DEPTH, MAX_RAW_VALUES) != DynamicTreeBounds.Result.WITHIN_LIMITS) {
            throw new IOException("raw tree depth or value capacity exceeded");
        }
        var output = new ScopeDBoundedOutput(MAX_RAW_SLOT_BYTES);
        // The writer materializes independent bytes. The resulting framing is checked again before retention.
        if (source.getValue() instanceof JsonElement json) {
            var characters = new OutputStreamWriter(output, StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
            try (var writer = new JsonWriter(characters)) {
                writer.setSerializeNulls(true);
                Streams.write(json, writer);
            }
        } else if (source.getValue() instanceof Tag nbt) {
            try (var writer = new DataOutputStream(output)) {
                NbtIo.writeAnyTag(nbt, writer);
            }
        } else {
            throw new IOException("unsupported raw tree value");
        }
        return fromBytes(location, tag, payloadVersion, context, output.copyBytes());
    }

    Dynamic<?> hydrate(Optional<HolderLookup.Provider> provider) throws IOException {
        if (context.registryContext() && provider.isEmpty()) {
            throw new IOException("raw registry context unavailable");
        }
        if (context.family() == SerializedTreeFamily.JSON) {
            try (var reader = jsonReader()) {
                var parent = context.compressedMaps() ? JsonOps.COMPRESSED : JsonOps.INSTANCE;
                var ops = context.registryContext() ? RegistryOps.create(parent, provider.orElseThrow()) : parent;
                return new Dynamic<>(ops, JsonParser.parseReader(reader));
            }
        }
        try (var input = dataInput()) {
            long quota = Math.addExact(Math.multiplyExact(3L, bytes.length), Math.multiplyExact(160L, MAX_RAW_VALUES));
            var value = NbtIo.readAnyTag(input, new NbtAccounter(quota, MAX_RAW_DEPTH));
            var ops = context.registryContext() ? RegistryOps.create(NbtOps.INSTANCE, provider.orElseThrow()) : NbtOps.INSTANCE;
            return new Dynamic<>(ops, value);
        }
    }

    RawLocation location() { return location; }
    String tag() { return tag; }
    int payloadVersion() { return payloadVersion; }
    SerializedTreeContext context() { return context; }
    int byteCount() { return bytes.length; }
    byte[] copyBytes() { return bytes.clone(); }

    @Override
    public boolean equals(Object other) {
        return other instanceof RawEnvelope value && location.equals(value.location)
                && tag.equals(value.tag) && payloadVersion == value.payloadVersion
                && context.equals(value.context) && Arrays.equals(bytes, value.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(location, tag, payloadVersion, context) + Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RawEnvelope[" + context.family() + ", " + bytes.length + " bytes]";
    }

    private void preflight() throws IOException {
        var budget = new TreeBudget();
        if (context.family() == SerializedTreeFamily.JSON) {
            checkJsonStringControls();
            try (var reader = jsonReader()) {
                scanJson(reader, 1, budget);
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw new IOException("trailing raw JSON bytes");
                }
            } catch (IllegalStateException | IllegalArgumentException exception) {
                throw new IOException("malformed raw JSON", exception);
            }
        } else {
            try (var input = dataInput()) {
                scanNbt(input, input.readUnsignedByte(), 1, budget);
                if (input.available() != 0) {
                    throw new IOException("trailing raw NBT bytes");
                }
            }
        }
    }

    private void checkJsonStringControls() throws IOException {
        // Gson 2.10 leniency=false still accepts some unescaped controls in quoted strings.
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            throw new IOException("raw JSON must not contain a BOM");
        }
        boolean quoted = false;
        boolean escaped = false;
        for (byte value : bytes) {
            if (quoted && (value & 0xff) < 0x20) {
                throw new IOException("unescaped raw JSON control character");
            }
            if (escaped) {
                escaped = false;
            } else if (quoted && value == '\\') {
                escaped = true;
            } else if (value == '"') {
                quoted = !quoted;
            }
        }
    }

    private JsonReader jsonReader() {
        var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        var reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes), decoder));
        reader.setLenient(false);
        return reader;
    }

    private DataInputStream dataInput() {
        return new DataInputStream(new ByteArrayInputStream(bytes));
    }

    private static void scanJson(JsonReader reader, int depth, TreeBudget budget) throws IOException {
        budget.accept(1, depth);
        switch (reader.peek()) {
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    scanJson(reader, depth + 1, budget);
                }
                reader.endArray();
            }
            case BEGIN_OBJECT -> {
                var names = new HashSet<String>();
                reader.beginObject();
                while (reader.hasNext()) {
                    var name = reader.nextName();
                    ScopeDData.text(name, MAX_SYMBOL_BYTES);
                    if (!names.add(name)) {
                        throw new IOException("duplicate raw JSON field");
                    }
                    scanJson(reader, depth + 1, budget);
                }
                reader.endObject();
            }
            case STRING -> {
                if (!StandardCharsets.UTF_8.newEncoder().canEncode(reader.nextString())) {
                    throw new IOException("invalid raw JSON string");
                }
            }
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("missing raw JSON value");
        }
    }

    /** Locked NbtIo AnyTag framing (type byte, unnamed payload). Bounds precede Tag materialization. */
    private static void scanNbt(DataInputStream input, int type, int depth, TreeBudget budget) throws IOException {
        budget.accept(1, depth);
        switch (type) {
            case Tag.TAG_END -> {
                if (depth != 1) {
                    throw new IOException("unexpected raw NBT end tag");
                }
            }
            case Tag.TAG_BYTE -> input.skipNBytes(1);
            case Tag.TAG_SHORT -> input.skipNBytes(2);
            case Tag.TAG_INT, Tag.TAG_FLOAT -> input.skipNBytes(4);
            case Tag.TAG_LONG, Tag.TAG_DOUBLE -> input.skipNBytes(8);
            case Tag.TAG_STRING -> nbtString(input);
            case Tag.TAG_BYTE_ARRAY -> nbtArray(input, 1, depth, budget);
            case Tag.TAG_INT_ARRAY -> nbtArray(input, 4, depth, budget);
            case Tag.TAG_LONG_ARRAY -> nbtArray(input, 8, depth, budget);
            case Tag.TAG_LIST -> {
                int element = input.readUnsignedByte();
                int count = input.readInt();
                if (count < 0 || count > budget.remaining || count > input.available()
                        || element > Tag.TAG_LONG_ARRAY || (element == Tag.TAG_END && count > 0)) {
                    throw new IOException("invalid raw NBT list type or count");
                }
                for (int index = 0; index < count; index++) {
                    scanNbt(input, element, depth + 1, budget);
                }
            }
            case Tag.TAG_COMPOUND -> {
                var keys = new HashSet<String>();
                int child;
                while ((child = input.readUnsignedByte()) != Tag.TAG_END) {
                    var key = nbtString(input);
                    try {
                        ScopeDData.text(key, MAX_SYMBOL_BYTES);
                    } catch (IllegalArgumentException exception) {
                        throw new IOException("invalid raw NBT key", exception);
                    }
                    if (!keys.add(key)) {
                        throw new IOException("invalid or duplicate raw NBT key");
                    }
                    scanNbt(input, child, depth + 1, budget);
                }
            }
            default -> throw new IOException("unsupported raw NBT tag");
        }
    }

    private static void nbtArray(DataInputStream input, int width, int depth, TreeBudget budget) throws IOException {
        int count = input.readInt();
        budget.accept(count, depth + 1);
        if (count > input.available() / width) {
            throw new IOException("truncated raw NBT array");
        }
        input.skipNBytes((long) count * width);
    }

    private static String nbtString(DataInputStream input) throws IOException {
        input.mark(2);
        int length = input.readUnsignedShort();
        if (length > input.available()) {
            throw new IOException("truncated raw NBT string");
        }
        input.reset();
        return input.readUTF();
    }

    private static final class TreeBudget {
        private int remaining = MAX_RAW_VALUES;

        private void accept(int count, int depth) throws IOException {
            if (count < 0 || count > remaining || (count > 0 && depth > MAX_RAW_DEPTH)) {
                throw new IOException("raw tree depth or value capacity exceeded");
            }
            remaining -= count;
        }
    }
}
