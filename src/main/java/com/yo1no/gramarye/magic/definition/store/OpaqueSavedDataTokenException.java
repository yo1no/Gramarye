package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/** Fixed-code failure raised while validating B1 opaque-blob token invariants. */
final class OpaqueSavedDataTokenException extends Exception {
    private static final long serialVersionUID = 1L;

    private final Code code;

    OpaqueSavedDataTokenException(Code code) {
        super(Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    Code code() {
        return code;
    }

    enum Code {
        MALFORMED_TOKEN,
        UNKNOWN_TOKEN,
        MISSING_TOKEN,
        RELOCATED_TOKEN,
        UNEXPECTED_CURRENT_FIELD,
        INVALID_CURRENT_SCHEMA
    }
}
