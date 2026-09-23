package com.yo1no.gramarye;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Fixed-resource structural ingress. Descriptor legality belongs to the existing resolver. */
final class P10TemplateCodec {
    static final ResourceLocation TEMPLATE_ID = ResourceLocation.fromNamespaceAndPath("gramarye", "starter_bolt_v0");
    static final ResourceLocation RESOURCE_ID = ResourceLocation.fromNamespaceAndPath(
            "gramarye", "gramarye/skill_templates/starter_bolt_v0.json");
    static final int MAX_RAW_BYTES = 262_144;
    static final int MAX_DEPTH = 32;
    static final int MAX_TREE_VALUES = 16_384;
    static final int MAX_STRING_LENGTH = 256;
    static final int MAX_NODES = 64;
    static final int MAX_PAYLOAD_BYTES = 65_536;
    private static final Set<String> ROOT_KEYS = Set.of("nodes", "appearance");
    private static final Set<String> NODE_KEYS = Set.of("trigger", "action");
    private static final Set<String> NODE_OVERRIDE_KEYS = Set.of("trigger", "action", "appearance_override");
    private static final Set<String> ENVELOPE_KEYS = Set.of("type", "schema_version", "payload");

    private P10TemplateCodec() {
    }

    /** The caller owns the stream. I/O faults propagate; expected data failures retain only a code. */
    static Result decode(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        try {
            // readSource's byte/character buffers cease to be retained before any payload encoding.
            var parsed = readSource(input);
            var root = requireObject(parsed.root());
            requireKeys(root, ROOT_KEYS);
            if (!(root.get("nodes") instanceof JsonArray sourceNodes)) {
                throw rejected(DecodeReason.SHELL);
            }
            if (sourceNodes.size() > MAX_NODES) {
                throw rejected(DecodeReason.NODE_LIMIT);
            }
            var nodes = new ArrayList<NodeDocument>(sourceNodes.size());
            for (var value : sourceNodes) {
                var node = requireObject(value);
                requireKeys(node, node.has("appearance_override") ? NODE_OVERRIDE_KEYS : NODE_KEYS);
                checkEnvelope(node.get("trigger"));
                checkEnvelope(node.get("action"));
                var decoded = NodeDocument.CODEC.parse(JsonOps.INSTANCE, node);
                if (decoded.error().isPresent()) {
                    throw rejected(DecodeReason.CANONICAL_NODE);
                }
                nodes.add(decoded.result().orElseThrow());
            }

            // This empty adapter shell reaches the existing strict Appearance codec, not the
            // tolerant persistence reader. Its identity is never part of the template body.
            var appearanceShell = new JsonObject();
            appearanceShell.addProperty("schema_version", SkillDocument.CURRENT_SCHEMA_VERSION);
            appearanceShell.addProperty("skill_id", "00000000-0000-0000-0000-000000000000");
            appearanceShell.addProperty("revision", 0);
            appearanceShell.add("nodes", new JsonArray());
            appearanceShell.add("appearance", root.get("appearance"));
            var appearance = SkillDocument.CODEC.parse(JsonOps.INSTANCE, appearanceShell);
            if (appearance.error().isPresent()) {
                throw rejected(DecodeReason.CANONICAL_APPEARANCE);
            }
            return new Decoded(new P10TemplateBody(nodes, appearance.result().orElseThrow().appearance()),
                    parsed.rawBytes(), parsed.treeValues());
        } catch (IngressFailure failure) {
            return new Rejected(failure.reason);
        }
    }

    private static Parsed readSource(InputStream input) throws IOException, IngressFailure {
        // The fixed policy is below its hard ceiling; policy+1 proves rejection without ever
        // allocating the larger hard-ceiling buffer or constructing an over-policy tree.
        var bytes = input.readNBytes(MAX_RAW_BYTES + 1);
        if (bytes.length > MAX_RAW_BYTES) {
            throw rejected(DecodeReason.RAW_BYTES);
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf) {
            throw rejected(DecodeReason.UTF8);
        }
        final CharBuffer characters;
        try {
            characters = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException failure) {
            throw rejected(DecodeReason.UTF8);
        }
        var parser = new Parser(characters);
        var root = parser.value(1);
        parser.whitespace();
        if (parser.peek() != -1) {
            throw rejected(DecodeReason.JSON);
        }
        return new Parsed(root, bytes.length, parser.values);
    }

    private static void checkEnvelope(JsonElement value) throws IngressFailure {
        var envelope = requireObject(value);
        requireKeys(envelope, ENVELOPE_KEYS);
        if (!(envelope.get("type") instanceof JsonPrimitive type) || !type.isString()
                || !(envelope.get("schema_version") instanceof JsonPrimitive schema) || !schema.isNumber()) {
            throw rejected(DecodeReason.SHELL);
        }
        try {
            if (schema.getAsBigDecimal().intValueExact() < 0) {
                throw rejected(DecodeReason.SHELL);
            }
        } catch (NumberFormatException | ArithmeticException failure) {
            throw rejected(DecodeReason.SHELL);
        }
        measurePayload(envelope.get("payload"));
    }

    private static void measurePayload(JsonElement payload) throws IngressFailure {
        try {
            var output = new PayloadByteCounter();
            var encoder = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            var writer = new JsonWriter(new OutputStreamWriter(output, encoder));
            writer.setLenient(false);
            writer.setSerializeNulls(true);
            writeCanonical(payload, writer);
            writer.flush();
        } catch (PayloadLimit failure) {
            throw rejected(DecodeReason.PAYLOAD_BYTES);
        } catch (IOException failure) {
            // This writer only targets a bounded in-memory counter: an encoding fault is data.
            throw rejected(DecodeReason.UTF8);
        }
    }

    /** Matches StrictJsonTreeCodec exactly; only counting replaces byte accumulation. */
    private static void writeCanonical(JsonElement value, JsonWriter writer) throws IOException {
        if (value.isJsonNull()) {
            writer.nullValue();
        } else if (value instanceof JsonArray array) {
            writer.beginArray();
            for (var element : array) {
                writeCanonical(element, writer);
            }
            writer.endArray();
        } else if (value instanceof JsonObject object) {
            writer.beginObject();
            var names = new ArrayList<>(object.keySet());
            names.sort(String::compareTo);
            for (var name : names) {
                writer.name(name);
                writeCanonical(object.get(name), writer);
            }
            writer.endObject();
        } else {
            var primitive = value.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                writer.value(primitive.getAsBoolean());
            } else if (primitive.isString()) {
                writer.value(primitive.getAsString());
            } else {
                writer.value(primitive.getAsNumber());
            }
        }
    }

    private static JsonObject requireObject(JsonElement value) throws IngressFailure {
        if (!(value instanceof JsonObject object)) {
            throw rejected(DecodeReason.SHELL);
        }
        return object;
    }

    private static void requireKeys(JsonObject object, Set<String> expected) throws IngressFailure {
        if (!object.keySet().equals(expected)) {
            throw rejected(DecodeReason.SHELL);
        }
        for (var value : object.entrySet()) {
            if (value.getValue().isJsonNull()) {
                throw rejected(DecodeReason.SHELL);
            }
        }
    }

    private static IngressFailure rejected(DecodeReason reason) {
        return new IngressFailure(reason);
    }

    sealed interface Result permits Decoded, Rejected {
    }

    record Decoded(P10TemplateBody body, int rawBytes, int treeValues) implements Result {
        Decoded {
            Objects.requireNonNull(body, "body");
        }
    }

    record Rejected(DecodeReason reason) implements Result {
        Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Sanitized detail only; all these cases have the one top-level DECODE_FAILED classification. */
    enum DecodeReason {
        RAW_BYTES, UTF8, JSON, DUPLICATE_KEY, DEPTH, TREE_VALUES, STRING_LENGTH,
        SHELL, NODE_LIMIT, PAYLOAD_BYTES, CANONICAL_NODE, CANONICAL_APPEARANCE
    }

    private record Parsed(JsonElement root, int rawBytes, int treeValues) {
    }

    private static final class IngressFailure extends Exception {
        private static final long serialVersionUID = 1L;
        private final DecodeReason reason;

        private IngressFailure(DecodeReason reason) {
            super(reason.name());
            this.reason = reason;
        }
    }

    private static final class PayloadLimit extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class PayloadByteCounter extends OutputStream {
        private int count;

        @Override
        public void write(int value) throws PayloadLimit {
            advance(1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws PayloadLimit {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            advance(length);
        }

        private void advance(int length) throws PayloadLimit {
            if (length > MAX_PAYLOAD_BYTES - count) {
                throw new PayloadLimit();
            }
            count += length;
        }
    }

    /** Token retention is bounded before appending, unlike JsonReader.nextString(). */
    private static final class Parser {
        private final CharBuffer input;
        private int values;

        private Parser(CharBuffer input) {
            this.input = input;
        }

        private JsonElement value(int depth) throws IngressFailure {
            if (depth > MAX_DEPTH) {
                throw rejected(DecodeReason.DEPTH);
            }
            if (values == MAX_TREE_VALUES) {
                throw rejected(DecodeReason.TREE_VALUES);
            }
            values++;
            whitespace();
            return switch (peek()) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> new JsonPrimitive(string());
                case 't' -> literal("true", new JsonPrimitive(true));
                case 'f' -> literal("false", new JsonPrimitive(false));
                case 'n' -> literal("null", JsonNull.INSTANCE);
                default -> number();
            };
        }

        private JsonObject object(int depth) throws IngressFailure {
            take('{');
            var object = new JsonObject();
            whitespace();
            if (peek() == '}') {
                take('}');
                return object;
            }
            while (true) {
                whitespace();
                var key = string();
                if (object.has(key)) {
                    throw rejected(DecodeReason.DUPLICATE_KEY);
                }
                whitespace();
                take(':');
                object.add(key, value(depth + 1));
                whitespace();
                if (peek() == '}') {
                    take('}');
                    return object;
                }
                take(',');
            }
        }

        private JsonArray array(int depth) throws IngressFailure {
            take('[');
            var array = new JsonArray();
            whitespace();
            if (peek() == ']') {
                take(']');
                return array;
            }
            while (true) {
                array.add(value(depth + 1));
                whitespace();
                if (peek() == ']') {
                    take(']');
                    return array;
                }
                take(',');
            }
        }

        private String string() throws IngressFailure {
            take('"');
            var text = new StringBuilder();
            while (true) {
                var current = next();
                if (current == '"') {
                    var result = text.toString();
                    for (var index = 0; index < result.length(); index++) {
                        var character = result.charAt(index);
                        if (Character.isHighSurrogate(character)) {
                            if (++index == result.length() || !Character.isLowSurrogate(result.charAt(index))) {
                                throw rejected(DecodeReason.UTF8);
                            }
                        } else if (Character.isLowSurrogate(character)) {
                            throw rejected(DecodeReason.UTF8);
                        }
                    }
                    return result;
                }
                if (current < 0x20) {
                    throw rejected(DecodeReason.JSON);
                }
                if (current == '\\') {
                    current = switch (next()) {
                        case '"' -> '"';
                        case '\\' -> '\\';
                        case '/' -> '/';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'u' -> unicode();
                        default -> throw rejected(DecodeReason.JSON);
                    };
                }
                if (text.length() == MAX_STRING_LENGTH) {
                    throw rejected(DecodeReason.STRING_LENGTH);
                }
                text.append((char) current);
            }
        }

        private int unicode() throws IngressFailure {
            var value = 0;
            for (var index = 0; index < 4; index++) {
                var digit = next();
                var decoded = digit >= '0' && digit <= '9' ? digit - '0'
                        : digit >= 'a' && digit <= 'f' ? digit - 'a' + 10
                        : digit >= 'A' && digit <= 'F' ? digit - 'A' + 10 : -1;
                if (decoded < 0) {
                    throw rejected(DecodeReason.JSON);
                }
                value = (value << 4) | decoded;
            }
            return value;
        }

        private JsonElement literal(String expected, JsonElement value) throws IngressFailure {
            for (var index = 0; index < expected.length(); index++) {
                take(expected.charAt(index));
            }
            return value;
        }

        private JsonPrimitive number() throws IngressFailure {
            var start = input.position();
            if (peek() == '-') {
                next();
            }
            if (peek() == '0') {
                next();
            } else {
                if (peek() < '1' || peek() > '9') {
                    throw rejected(DecodeReason.JSON);
                }
                digits();
            }
            if (peek() == '.') {
                next();
                requireDigits();
            }
            if (peek() == 'e' || peek() == 'E') {
                next();
                if (peek() == '+' || peek() == '-') {
                    next();
                }
                requireDigits();
            }
            var end = input.position();
            var lexical = input.duplicate();
            lexical.position(start);
            lexical.limit(end);
            return new JsonPrimitive(new LazilyParsedNumber(lexical.toString()));
        }

        private void requireDigits() throws IngressFailure {
            if (peek() < '0' || peek() > '9') {
                throw rejected(DecodeReason.JSON);
            }
            digits();
        }

        private void digits() {
            while (peek() >= '0' && peek() <= '9') {
                input.get();
            }
        }

        private void whitespace() {
            while (peek() == ' ' || peek() == '\t' || peek() == '\r' || peek() == '\n') {
                input.get();
            }
        }

        private int peek() {
            return input.hasRemaining() ? input.get(input.position()) : -1;
        }

        private int next() throws IngressFailure {
            if (!input.hasRemaining()) {
                throw rejected(DecodeReason.JSON);
            }
            return input.get();
        }

        private void take(int expected) throws IngressFailure {
            if (next() != expected) {
                throw rejected(DecodeReason.JSON);
            }
        }
    }
}
