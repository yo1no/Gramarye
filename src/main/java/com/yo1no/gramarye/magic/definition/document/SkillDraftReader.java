package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import java.util.Objects;

/** Tolerant draft reader; normalization provenance remains outside the immutable draft model. */
public final class SkillDraftReader {
    private SkillDraftReader() {
    }

    public static DataResult<SkillDraftReadResult> read(Dynamic<?> input) {
        Objects.requireNonNull(input, "input");
        return TolerantDocumentReaders.readDraft(input);
    }
}
