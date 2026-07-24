package com.yo1no.gramarye.magic.definition.document;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedByteEncodingTest {
    @Test
    void exactMaximumSucceedsAndMaximumPlusOneStopsDuringTheCountingPass() throws Exception {
        var exact = BoundedByteEncoding.encode(4, output -> output.write(new byte[] {1, 2, 3, 4}));
        var writes = new AtomicInteger();

        var exceeded = assertThrows(
                BoundedByteEncoding.CapacityExceeded.class,
                () -> BoundedByteEncoding.encode(4, output -> {
                    for (var value = 0; value < 10; value++) {
                        writes.incrementAndGet();
                        output.write(value);
                    }
                }));

        assertArrayEquals(new byte[] {1, 2, 3, 4}, exact.copyBytes());
        assertEquals(5, writes.get());
        assertEquals(5, exceeded.observedAtLeast());
        assertEquals(4, exceeded.maximum());
    }

    @Test
    void twoPassEncodingRejectsNondeterministicOutputWithoutReturningPartialBytes() {
        var pass = new AtomicInteger();

        assertThrows(
                BoundedByteEncoding.NonDeterministicEncoding.class,
                () -> BoundedByteEncoding.encode(10, output -> {
                    var size = pass.incrementAndGet() == 1 ? 3 : 4;
                    output.write(new byte[size]);
                }));
    }

    @Test
    void checkedAggregatesRejectNegativeValuesAndOverflow() {
        assertEquals(9, BoundedByteEncoding.checkedAdd(4, 5));
        assertEquals(20, BoundedByteEncoding.checkedMultiply(4, 5));
        assertThrows(ArithmeticException.class,
                () -> BoundedByteEncoding.checkedAdd(Long.MAX_VALUE, 1));
        assertThrows(ArithmeticException.class,
                () -> BoundedByteEncoding.checkedMultiply(Long.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> BoundedByteEncoding.checkedAdd(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> BoundedByteEncoding.checkedMultiply(1, -1));
    }

    @Test
    void immutableBytesDefensivelyCopyBothDirectionsAndHaveBoundedToString() throws IOException {
        var source = new byte[] {1, 2, 3};
        var encoded = ImmutableEncodedBytes.copyOf(source);
        source[0] = 9;
        var first = encoded.copyBytes();
        first[1] = 9;
        var second = encoded.copyBytes();

        assertArrayEquals(new byte[] {1, 2, 3}, second);
        assertNotEquals(first[1], second[1]);
        assertEquals(3, encoded.size());
        assertTrue(encoded.toString().contains("byteCount=3"));
        assertFalse(encoded.toString().contains("1, 2, 3"));
        assertTrue(encoded.toString().length() < 96);
    }
}
