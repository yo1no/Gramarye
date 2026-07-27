package com.yo1no.gramarye.magic.definition.player;

import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.Objects;

/** A byte-exact {@link DataOutput} counter that retains no output bytes. */
final class BoundedCountingDataOutput implements DataOutput {
    private final long maximum;
    private long current;

    BoundedCountingDataOutput(long maximum) {
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be non-negative");
        }
        this.maximum = maximum;
    }

    long byteCount() {
        return current;
    }

    @Override
    public void write(int value) throws CapacityExceeded {
        advance(1);
    }

    @Override
    public void write(byte[] bytes) throws CapacityExceeded {
        Objects.requireNonNull(bytes, "bytes");
        advance(bytes.length);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws CapacityExceeded {
        Objects.requireNonNull(bytes, "bytes");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        advance(length);
    }

    @Override
    public void writeBoolean(boolean value) throws CapacityExceeded {
        advance(1);
    }

    @Override
    public void writeByte(int value) throws CapacityExceeded {
        advance(1);
    }

    @Override
    public void writeShort(int value) throws CapacityExceeded {
        advance(2);
    }

    @Override
    public void writeChar(int value) throws CapacityExceeded {
        advance(2);
    }

    @Override
    public void writeInt(int value) throws CapacityExceeded {
        advance(4);
    }

    @Override
    public void writeLong(long value) throws CapacityExceeded {
        advance(8);
    }

    @Override
    public void writeFloat(float value) throws CapacityExceeded {
        advance(4);
    }

    @Override
    public void writeDouble(double value) throws CapacityExceeded {
        advance(8);
    }

    @Override
    public void writeBytes(String value) throws CapacityExceeded {
        advance(Objects.requireNonNull(value, "value").length());
    }

    @Override
    public void writeChars(String value) throws CapacityExceeded {
        advance(Math.multiplyExact((long) Objects.requireNonNull(value, "value").length(), 2));
    }

    @Override
    public void writeUTF(String value) throws IOException {
        Objects.requireNonNull(value, "value");
        long encodedLength = 0;
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            encodedLength = Math.addExact(encodedLength,
                    character >= 0x0001 && character <= 0x007F
                            ? 1
                            : character <= 0x07FF ? 2 : 3);
            if (encodedLength > 65_535) {
                throw new UTFDataFormatException("encoded string too long");
            }
        }
        advance(Math.addExact(2, encodedLength));
    }

    private void advance(long length) throws CapacityExceeded {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        if (length > maximum - current) {
            throw new CapacityExceeded(observedAtLeast(maximum), maximum);
        }
        current = Math.addExact(current, length);
    }

    private static long observedAtLeast(long maximum) {
        return maximum == Long.MAX_VALUE ? Long.MAX_VALUE : maximum + 1;
    }

    static final class CapacityExceeded extends IOException {
        private final long observedAtLeast;
        private final long maximum;

        CapacityExceeded(long observedAtLeast, long maximum) {
            super((String) null);
            this.observedAtLeast = observedAtLeast;
            this.maximum = maximum;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        long observedAtLeast() {
            return observedAtLeast;
        }

        long maximum() {
            return maximum;
        }
    }
}
