package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Closed machine-readable metadata boundary for validation issues. */
public sealed interface ValidationIssueMetadata
        permits ValidationIssueMetadata.None,
                ValidationIssueMetadata.Limit,
                ValidationIssueMetadata.Schema,
                ValidationIssueMetadata.Reference,
                ValidationIssueMetadata.ExceptionClass {
    static ValidationIssueMetadata none() {
        return None.INSTANCE;
    }

    enum None implements ValidationIssueMetadata {
        INSTANCE
    }

    record Limit(int actual, int maximum) implements ValidationIssueMetadata {
    }

    record Schema(int actual, int expected) implements ValidationIssueMetadata {
        public Schema {
            if (actual < 0 || expected < 0) {
                throw new IllegalArgumentException("schema versions must be non-negative");
            }
        }
    }

    record Reference(int currentNode, int referencedNode) implements ValidationIssueMetadata {
        public Reference {
            if (currentNode < 0) {
                throw new IllegalArgumentException("currentNode must be non-negative");
            }
        }
    }

    final class ExceptionClass implements ValidationIssueMetadata {
        private final String className;

        private ExceptionClass(String className) {
            this.className = Objects.requireNonNull(className, "className");
            if (className.isEmpty()) {
                throw new IllegalArgumentException("exception class name must not be empty");
            }
            if (className.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("exception class name exceeds the string ceiling");
            }
        }

        public static ExceptionClass from(Class<?> exceptionClass) {
            return new ExceptionClass(Objects.requireNonNull(exceptionClass, "exceptionClass").getName());
        }

        public String className() {
            return className;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || other instanceof ExceptionClass metadata && className.equals(metadata.className);
        }

        @Override
        public int hashCode() {
            return className.hashCode();
        }

        @Override
        public String toString() {
            return "ExceptionClass[className=" + className + "]";
        }
    }
}
