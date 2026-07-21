package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed, non-persistent Reader rejection that deliberately excludes DFU diagnostics and raw input.
 */
public record SkillDocumentReadFailure(SkillDocumentReadFailureCode code) {
    public SkillDocumentReadFailure {
        Objects.requireNonNull(code, "code");
    }

    /**
     * Converts only the success/error state of a Reader result. The DFU error message is never
     * inspected or retained.
     */
    public static Optional<SkillDocumentReadFailure> fromReadResult(
            DataResult<SkillDocumentReadResult> readResult) {
        Objects.requireNonNull(readResult, "readResult");
        return readResult.error().isPresent()
                ? Optional.of(new SkillDocumentReadFailure(
                        SkillDocumentReadFailureCode.READER_REJECTED_INPUT))
                : Optional.empty();
    }
}
