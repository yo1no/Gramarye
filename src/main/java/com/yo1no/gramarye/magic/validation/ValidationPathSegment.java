package com.yo1no.gramarye.magic.validation;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** One typed segment in a deterministic validation path. */
public sealed interface ValidationPathSegment
        permits ValidationPathSegment.Field, ValidationPathSegment.Index {
    record Field(String name) implements ValidationPathSegment {
        public Field {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("field name must not be blank");
            }
            if (name.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("field name exceeds the string ceiling");
            }
            if (name.indexOf('.') >= 0 || name.indexOf('[') >= 0 || name.indexOf(']') >= 0) {
                throw new IllegalArgumentException("field name contains validation path syntax");
            }
            if (name.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("field name contains a control character");
            }
        }
    }

    record Index(int value) implements ValidationPathSegment {
        public Index {
            if (value < 0) {
                throw new IllegalArgumentException("path index must be non-negative");
            }
        }
    }
}
