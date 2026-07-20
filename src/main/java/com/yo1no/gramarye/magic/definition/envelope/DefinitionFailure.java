package com.yo1no.gramarye.magic.definition.envelope;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/** Bounded diagnostic for a definition that could not be resolved to a typed payload. */
public record DefinitionFailure(Code code, String diagnostic) {
    public DefinitionFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(diagnostic, "diagnostic");
        if (diagnostic.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("diagnostic exceeds the technical string ceiling");
        }
    }

    /** Creates a failure while truncating third-party diagnostics to the technical string ceiling. */
    public static DefinitionFailure of(Code code, String diagnostic) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(diagnostic, "diagnostic");
        var boundedDiagnostic = diagnostic.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? diagnostic
                : diagnostic.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
        return new DefinitionFailure(code, boundedDiagnostic);
    }

    public enum Code {
        UNKNOWN_TYPE,
        UNSUPPORTED_SCHEMA_VERSION,
        PAYLOAD_DECODE_ERROR,
        CODEC_EXCEPTION
    }
}
