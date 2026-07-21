package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;

/** Draft persistence writer with the same appearance guarantees as the formal document writer. */
public final class SkillDraftWriter {
    private SkillDraftWriter() {
    }

    public static <T> DataResult<T> write(SkillDraft draft, DynamicOps<T> ops) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(ops, "ops");
        return DocumentStorageWriters.writeDraftForStorage(draft, ops);
    }
}
