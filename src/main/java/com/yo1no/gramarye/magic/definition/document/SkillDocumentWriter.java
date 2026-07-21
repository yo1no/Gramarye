package com.yo1no.gramarye.magic.definition.document;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.Objects;

/** Persistence writer with same-family raw preservation for unparsed appearance blobs. */
public final class SkillDocumentWriter {
    private SkillDocumentWriter() {
    }

    public static <T> DataResult<T> write(SkillDocument document, DynamicOps<T> ops) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(ops, "ops");
        return DocumentStorageWriters.writeDocumentForStorage(document, ops);
    }
}
