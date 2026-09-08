package com.yo1no.gramarye;

import java.util.Objects;

/** Immutable count/byte cost with fail-closed checked arithmetic. */
record PresentationCost(long count, long bytes) {
    PresentationCost {
        if (count < 0L || bytes < 0L) {
            throw new IllegalArgumentException("presentation cost cannot be negative");
        }
    }

    static PresentationCost zero() {
        return new PresentationCost(0L, 0L);
    }

    static PresentationCost count(long count) {
        return new PresentationCost(count, 0L);
    }

    static PresentationCost bytes(long bytes) {
        return new PresentationCost(0L, bytes);
    }

    Arithmetic add(PresentationCost other) {
        Objects.requireNonNull(other, "other");
        try {
            return new Arithmetic.Exact(new PresentationCost(
                    Math.addExact(count, other.count), Math.addExact(bytes, other.bytes)));
        } catch (ArithmeticException overflow) {
            return Arithmetic.Overflow.INSTANCE;
        }
    }

    Arithmetic multiply(long factor) {
        if (factor < 0L) {
            throw new IllegalArgumentException("presentation cost factor cannot be negative");
        }
        try {
            return new Arithmetic.Exact(new PresentationCost(
                    Math.multiplyExact(count, factor), Math.multiplyExact(bytes, factor)));
        } catch (ArithmeticException overflow) {
            return Arithmetic.Overflow.INSTANCE;
        }
    }

    sealed abstract static class Arithmetic permits Arithmetic.Exact, Arithmetic.Overflow {
        private Arithmetic() {}

        static final class Exact extends Arithmetic {
            private final PresentationCost value;

            Exact(PresentationCost value) {
                this.value = Objects.requireNonNull(value, "value");
            }

            PresentationCost value() {
                return value;
            }
        }

        static final class Overflow extends Arithmetic {
            static final Overflow INSTANCE = new Overflow();

            private Overflow() {}
        }
    }
}
