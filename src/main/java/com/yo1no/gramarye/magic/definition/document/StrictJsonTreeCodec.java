package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/** Strict, bounded JSON tree bytes with duplicate rejection and stable key ordering. */
final class StrictJsonTreeCodec {
    private static final byte UTF8_BOM_FIRST = (byte) 0xEF;
    private static final byte UTF8_BOM_SECOND = (byte) 0xBB;
    private static final byte UTF8_BOM_THIRD = (byte) 0xBF;

    private StrictJsonTreeCodec() {
    }

    static ImmutableEncodedBytes encode(JsonElement value, long maximum) throws IOException {
        var snapshot = java.util.Objects.requireNonNull(value, "value").deepCopy();
        return BoundedByteEncoding.encode(maximum, output -> {
            var encoder = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            var characterOutput = new OutputStreamWriter(output, encoder);
            var writer = new JsonWriter(characterOutput);
            writer.setLenient(false);
            writer.setSerializeNulls(true);
            write(snapshot, writer);
            writer.flush();
        });
    }

    static JsonElement decode(ImmutableEncodedBytes encoded, long maximum) throws IOException {
        java.util.Objects.requireNonNull(encoded, "encoded");
        BoundedByteEncoding.requireWithinLimit(encoded.size(), maximum);
        var bytes = encoded.copyBytes();
        if (hasUtf8Bom(bytes)) {
            throw new MalformedTreeException("JSON");
        }

        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (var characters = new InputStreamReader(new ByteArrayInputStream(bytes), decoder);
                var reader = new JsonReader(characters)) {
            reader.setLenient(false);
            if (reader.peek() == JsonToken.END_DOCUMENT) {
                throw new MalformedTreeException("JSON");
            }
            var value = read(reader, 1);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new MalformedTreeException("JSON");
            }
            return value;
        } catch (MalformedTreeException exception) {
            throw exception;
        } catch (IOException | IllegalStateException | NumberFormatException exception) {
            throw new MalformedTreeException("JSON", exception);
        }
    }

    private static JsonElement read(JsonReader reader, int depth) throws IOException {
        if (depth > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH) {
            throw new MalformedTreeException("JSON");
        }
        return switch (reader.peek()) {
            case BEGIN_ARRAY -> readArray(reader, depth);
            case BEGIN_OBJECT -> readObject(reader, depth);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new LazilyParsedNumber(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new MalformedTreeException("JSON");
        };
    }

    private static JsonArray readArray(JsonReader reader, int depth) throws IOException {
        var result = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
            result.add(read(reader, depth + 1));
        }
        reader.endArray();
        return result;
    }

    private static JsonObject readObject(JsonReader reader, int depth) throws IOException {
        var result = new JsonObject();
        var names = new HashSet<String>();
        reader.beginObject();
        while (reader.hasNext()) {
            var name = reader.nextName();
            if (!names.add(name)) {
                throw new MalformedTreeException("JSON");
            }
            result.add(name, read(reader, depth + 1));
        }
        reader.endObject();
        return result;
    }

    private static void write(JsonElement value, JsonWriter writer) throws IOException {
        if (value == null || value.isJsonNull()) {
            writer.nullValue();
            return;
        }
        if (value instanceof JsonArray array) {
            writer.beginArray();
            for (var element : array) {
                write(element, writer);
            }
            writer.endArray();
            return;
        }
        if (value instanceof JsonObject object) {
            writer.beginObject();
            var names = new ArrayList<String>(object.keySet());
            names.sort(String::compareTo);
            for (var name : names) {
                writer.name(name);
                write(object.get(name), writer);
            }
            writer.endObject();
            return;
        }

        var primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            writer.value(primitive.getAsBoolean());
        } else if (primitive.isString()) {
            writer.value(primitive.getAsString());
        } else {
            var number = primitive.getAsNumber();
            var lexical = number.toString();
            if (lexical.equals("NaN")
                    || lexical.equals("Infinity")
                    || lexical.equals("-Infinity")) {
                throw new MalformedTreeException("JSON");
            }
            writer.value(number);
        }
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == UTF8_BOM_FIRST
                && bytes[1] == UTF8_BOM_SECOND
                && bytes[2] == UTF8_BOM_THIRD;
    }
}
