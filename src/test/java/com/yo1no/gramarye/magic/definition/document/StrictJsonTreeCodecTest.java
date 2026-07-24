package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class StrictJsonTreeCodecTest {
    @Test
    void recursivelySortsObjectKeysWhilePreservingArrayOrder() throws Exception {
        var nested = new JsonObject();
        nested.addProperty("z", 1);
        nested.addProperty("a", 2);
        var array = new JsonArray();
        array.add("second");
        array.add("first");
        var root = new JsonObject();
        root.add("z", nested);
        root.add("a", array);

        var encoded = StrictJsonTreeCodec.encode(root, 1_024);

        assertEquals(
                "{\"a\":[\"second\",\"first\"],\"z\":{\"a\":2,\"z\":1}}",
                new String(encoded.copyBytes(), StandardCharsets.UTF_8));
        assertEquals(root, StrictJsonTreeCodec.decode(encoded, 1_024));
    }

    @Test
    void preservesNumericLexemesIncludingOnePointZeroZero() throws Exception {
        var source = bytes("{\"large\":-1.2300e+100,\"value\":1.00}");
        var decoded = StrictJsonTreeCodec.decode(source, 1_024);
        var encoded = StrictJsonTreeCodec.encode(decoded, 1_024);

        assertArrayEquals(
                "{\"large\":-1.2300e+100,\"value\":1.00}".getBytes(StandardCharsets.UTF_8),
                encoded.copyBytes());
    }

    @Test
    void validUtf8RoundTripsWithoutBomOrReplacement() throws Exception {
        var expected = "\"魔法・Grimoire\"".getBytes(StandardCharsets.UTF_8);

        var decoded = StrictJsonTreeCodec.decode(
                ImmutableEncodedBytes.copyOf(expected), expected.length);
        var encoded = StrictJsonTreeCodec.encode(decoded, expected.length);

        assertArrayEquals(expected, encoded.copyBytes());
    }

    @Test
    void rejectsDuplicateKeysBeforeBuildingAnObject() {
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(bytes("{\"same\":1,\"same\":2}"), 1_024));
    }

    @Test
    void rejectsBomMalformedUtf8EmptyTrailingAndNonFiniteInput() {
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(
                        ImmutableEncodedBytes.copyOf(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '0'}),
                        1_024));
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(
                        ImmutableEncodedBytes.copyOf(new byte[] {(byte) 0xC3, 0x28}),
                        1_024));
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(bytes(" \n\t"), 1_024));
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(bytes("{} []"), 1_024));
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(bytes("NaN"), 1_024));
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.decode(bytes("Infinity"), 1_024));
        assertThrows(MalformedTreeException.class,
                () -> StrictJsonTreeCodec.encode(new JsonPrimitive(Double.NaN), 1_024));
    }

    @Test
    void exactEncodedMaximumSucceedsAndMaximumPlusOneFails() throws Exception {
        var atLimit = new JsonPrimitive("x".repeat(30));
        var overLimit = new JsonPrimitive("x".repeat(31));

        var exact = StrictJsonTreeCodec.encode(atLimit, 32);
        assertEquals(32, exact.size());
        assertEquals(atLimit, StrictJsonTreeCodec.decode(exact, 32));
        assertThrows(BoundedByteEncoding.CapacityExceeded.class,
                () -> StrictJsonTreeCodec.decode(exact, 31));
        assertThrows(BoundedByteEncoding.CapacityExceeded.class,
                () -> StrictJsonTreeCodec.encode(overLimit, 32));
    }

    @Test
    void writerValidatesArbitraryNumberImplementations() {
        var malformed = new JsonPrimitive(new LazilyParsedNumber("not-a-number"));

        assertThrows(IllegalArgumentException.class,
                () -> StrictJsonTreeCodec.encode(malformed, 1_024));
    }

    private static ImmutableEncodedBytes bytes(String value) {
        return ImmutableEncodedBytes.copyOf(value.getBytes(StandardCharsets.UTF_8));
    }
}
