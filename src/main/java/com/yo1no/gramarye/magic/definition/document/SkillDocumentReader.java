package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** Tolerant storage/import reader; it never performs registry resolution or semantic validation. */
public final class SkillDocumentReader {
    private SkillDocumentReader() {
    }

    public static DataResult<SkillDocumentReadResult> read(Dynamic<?> input) {
        Objects.requireNonNull(input, "input");
        return TolerantDocumentReaders.readDocument(input);
    }
}
