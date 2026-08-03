package com.yo1no.gramarye.magic.definition.research;

import java.io.DataInput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.util.Objects;

/** Prefix-aware Java modified-UTF reader used only by the R2Q research scanner. */
final class P4E0R2QModifiedUtf {
    private P4E0R2QModifiedUtf() {
    }

    @FunctionalInterface
    interface LengthObserver {
        void observe(int encodedBytes) throws IOException;
    }

    enum Scope {
        PER_FILE,
        AGGREGATE
    }

    static String read(DataInput input) throws IOException {
        return read(input, ignored -> { });
    }

    /**
     * Reads the unsigned byte-length prefix and invokes {@code observer} before allocating either
     * decode buffer or the resulting String.
     */
    static String read(DataInput input, LengthObserver observer) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(observer, "observer");
        var encodedBytes = input.readUnsignedShort();
        observer.observe(encodedBytes);

        var bytes = new byte[encodedBytes];
        var characters = new char[encodedBytes];
        input.readFully(bytes);
        var byteIndex = 0;
        var characterIndex = 0;
        while (byteIndex < encodedBytes) {
            var first = bytes[byteIndex] & 0xff;
            if (first > 0x7f) {
                break;
            }
            byteIndex++;
            characters[characterIndex++] = (char) first;
        }
        while (byteIndex < encodedBytes) {
            var first = bytes[byteIndex] & 0xff;
            switch (first >> 4) {
                case 0, 1, 2, 3, 4, 5, 6, 7 -> {
                    byteIndex++;
                    characters[characterIndex++] = (char) first;
                }
                case 12, 13 -> {
                    byteIndex += 2;
                    if (byteIndex > encodedBytes) {
                        throw malformed("partial character at end");
                    }
                    var second = bytes[byteIndex - 1] & 0xff;
                    if ((second & 0xc0) != 0x80) {
                        throw malformed("malformed continuation byte");
                    }
                    characters[characterIndex++] = (char) (((first & 0x1f) << 6)
                            | (second & 0x3f));
                }
                case 14 -> {
                    byteIndex += 3;
                    if (byteIndex > encodedBytes) {
                        throw malformed("partial character at end");
                    }
                    var second = bytes[byteIndex - 2] & 0xff;
                    var third = bytes[byteIndex - 1] & 0xff;
                    if ((second & 0xc0) != 0x80 || (third & 0xc0) != 0x80) {
                        throw malformed("malformed continuation byte");
                    }
                    characters[characterIndex++] = (char) (((first & 0x0f) << 12)
                            | ((second & 0x3f) << 6)
                            | (third & 0x3f));
                }
                default -> throw malformed("malformed leading byte");
            }
        }
        return new String(characters, 0, characterIndex);
    }

    private static UTFDataFormatException malformed(String reason) {
        return new UTFDataFormatException(reason);
    }

    /** Aggregate counter shared across file scans; no raw bytes or names are retained. */
    static final class AggregateBudget {
        private final long maximum;
        private long observed;

        AggregateBudget(long maximum) {
            if (maximum < 0 || maximum == Long.MAX_VALUE) {
                throw new IllegalArgumentException("modified-UTF aggregate maximum must be finite");
            }
            this.maximum = maximum;
        }

        long maximum() {
            return maximum;
        }

        long observed() {
            return observed;
        }
    }

    /** Atomic per-file/aggregate prefix checkpoint. */
    static final class Budget implements LengthObserver {
        private final long perFileMaximum;
        private final AggregateBudget aggregate;
        private long perFileObserved;

        Budget(long perFileMaximum, AggregateBudget aggregate) {
            if (perFileMaximum < 0 || perFileMaximum == Long.MAX_VALUE) {
                throw new IllegalArgumentException("modified-UTF per-file maximum must be finite");
            }
            this.perFileMaximum = perFileMaximum;
            this.aggregate = Objects.requireNonNull(aggregate, "aggregate");
        }

        @Override
        public void observe(int encodedBytes) throws CapacityException {
            if (encodedBytes < 0) {
                throw new IllegalArgumentException("negative modified-UTF byte length");
            }
            var nextPerFile = checkedNext(
                    perFileObserved, encodedBytes, perFileMaximum, Scope.PER_FILE);
            var nextAggregate = checkedNext(
                    aggregate.observed, encodedBytes, aggregate.maximum, Scope.AGGREGATE);
            perFileObserved = nextPerFile;
            aggregate.observed = nextAggregate;
        }

        long perFileMaximum() {
            return perFileMaximum;
        }

        long perFileObserved() {
            return perFileObserved;
        }

        AggregateBudget aggregate() {
            return aggregate;
        }

        private static long checkedNext(
                long current, int increment, long maximum, Scope scope)
                throws CapacityException {
            final long next;
            try {
                next = Math.addExact(current, (long) increment);
            } catch (ArithmeticException exception) {
                throw new CapacityException(scope, Math.addExact(maximum, 1L), maximum);
            }
            if (next > maximum) {
                throw new CapacityException(scope, Math.addExact(maximum, 1L), maximum);
            }
            return next;
        }
    }

    static final class CapacityException extends IOException {
        private final Scope scope;
        private final long observedAtLeast;
        private final long maximum;

        CapacityException(Scope scope, long observedAtLeast, long maximum) {
            super("modified-UTF research capacity exceeded");
            this.scope = Objects.requireNonNull(scope, "scope");
            if (observedAtLeast <= maximum || maximum < 0) {
                throw new IllegalArgumentException("invalid modified-UTF capacity failure");
            }
            this.observedAtLeast = observedAtLeast;
            this.maximum = maximum;
        }

        Scope scope() {
            return scope;
        }

        long observedAtLeast() {
            return observedAtLeast;
        }

        long maximum() {
            return maximum;
        }
    }
}
