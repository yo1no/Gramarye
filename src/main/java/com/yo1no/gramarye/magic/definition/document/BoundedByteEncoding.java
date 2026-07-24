package com.yo1no.gramarye.magic.definition.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** Shared fail-fast byte accounting and deterministic two-pass materialization. */
final class BoundedByteEncoding {
    private BoundedByteEncoding() {
    }

    static ImmutableEncodedBytes encode(long maximum, Encoder encoder) throws IOException {
        requireMaximum(maximum);
        Objects.requireNonNull(encoder, "encoder");

        var counting = new CountingOutput(maximum);
        encoder.encode(counting);
        var counted = counting.count();
        if (counted > Integer.MAX_VALUE) {
            throw new CapacityExceeded(maximum == Long.MAX_VALUE ? Long.MAX_VALUE : maximum + 1, maximum);
        }

        var accumulating = new AccumulatingOutput((int) counted, maximum);
        encoder.encode(accumulating);
        if (accumulating.count() != counted) {
            throw new NonDeterministicEncoding(counted, accumulating.count());
        }
        return ImmutableEncodedBytes.takeOwnership(accumulating.toByteArray());
    }

    static long checkedAdd(long left, long right) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException("aggregate values must be non-negative");
        }
        return Math.addExact(left, right);
    }

    static long checkedMultiply(long left, long right) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException("aggregate values must be non-negative");
        }
        return Math.multiplyExact(left, right);
    }

    static void requireWithinLimit(long observed, long maximum) throws CapacityExceeded {
        requireMaximum(maximum);
        if (observed < 0) {
            throw new IllegalArgumentException("observed must be non-negative");
        }
        if (observed > maximum) {
            throw new CapacityExceeded(maximum == Long.MAX_VALUE ? Long.MAX_VALUE : maximum + 1, maximum);
        }
    }

    private static void requireMaximum(long maximum) {
        if (maximum <= 0) {
            throw new IllegalArgumentException("maximum must be positive");
        }
    }

    @FunctionalInterface
    interface Encoder {
        void encode(OutputStream output) throws IOException;
    }

    static final class CapacityExceeded extends IOException {
        private final long observedAtLeast;
        private final long maximum;

        CapacityExceeded(long observedAtLeast, long maximum) {
            super("encoded byte capacity exceeded");
            if (maximum <= 0 || observedAtLeast <= maximum) {
                throw new IllegalArgumentException("observedAtLeast must exceed a positive maximum");
            }
            this.observedAtLeast = observedAtLeast;
            this.maximum = maximum;
        }

        long observedAtLeast() {
            return observedAtLeast;
        }

        long maximum() {
            return maximum;
        }
    }

    static final class NonDeterministicEncoding extends IOException {
        NonDeterministicEncoding(long expected, long actual) {
            super("deterministic encoding size changed from " + expected + " to " + actual);
        }
    }

    static final class CountingOutput extends OutputStream {
        private final long maximum;
        private long count;

        CountingOutput(long maximum) {
            requireMaximum(maximum);
            this.maximum = maximum;
        }

        @Override
        public void write(int value) throws CapacityExceeded {
            advance(1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws CapacityExceeded {
            Objects.requireNonNull(bytes, "bytes");
            Objects.checkFromIndexSize(offset, length, bytes.length);
            advance(length);
        }

        long count() {
            return count;
        }

        private void advance(int length) throws CapacityExceeded {
            if (length < 0) {
                throw new IllegalArgumentException("length must be non-negative");
            }
            if (length > maximum - count) {
                throw new CapacityExceeded(maximum + 1, maximum);
            }
            count += length;
        }
    }

    static final class AccumulatingOutput extends OutputStream {
        private final CountingOutput counter;
        private final ByteArrayOutputStream bytes;

        AccumulatingOutput(int expectedSize, long maximum) {
            if (expectedSize < 0) {
                throw new IllegalArgumentException("expectedSize must be non-negative");
            }
            this.counter = new CountingOutput(maximum);
            this.bytes = new ByteArrayOutputStream(expectedSize);
        }

        @Override
        public void write(int value) throws IOException {
            counter.write(value);
            bytes.write(value);
        }

        @Override
        public void write(byte[] source, int offset, int length) throws IOException {
            counter.write(source, offset, length);
            bytes.write(source, offset, length);
        }

        long count() {
            return counter.count();
        }

        byte[] toByteArray() {
            return bytes.toByteArray();
        }
    }
}
