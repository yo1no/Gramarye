package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import java.util.List;
import java.util.Objects;

/** Package-internal current physical Store envelope. Lists deliberately preserve raw order. */
final class StorePersistentEnvelopeV0 {
    private final int schemaVersion;
    private final List<ImmutableHistoryBlob> historyEntries;

    StorePersistentEnvelopeV0(int schemaVersion, List<ImmutableHistoryBlob> historyEntries) {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("schemaVersion must be non-negative");
        }
        this.schemaVersion = schemaVersion;
        this.historyEntries = List.copyOf(Objects.requireNonNull(historyEntries, "historyEntries"));
    }

    int schemaVersion() {
        return schemaVersion;
    }

    List<ImmutableHistoryBlob> historyEntries() {
        return historyEntries;
    }

    @Override
    public String toString() {
        return "StorePersistentEnvelopeV0[schemaVersion=" + schemaVersion
                + ", historyCount=" + historyEntries.size() + "]";
    }
}

/** Package-internal current physical history envelope. */
final class HistoryPersistentEnvelopeV0 {
    private final SkillId skillId;
    private final SkillOwnerId owner;
    private final List<ImmutableRevisionBlob> revisionEntries;

    HistoryPersistentEnvelopeV0(
            SkillId skillId,
            SkillOwnerId owner,
            List<ImmutableRevisionBlob> revisionEntries) {
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.revisionEntries = List.copyOf(
                Objects.requireNonNull(revisionEntries, "revisionEntries"));
    }

    SkillId skillId() {
        return skillId;
    }

    SkillOwnerId owner() {
        return owner;
    }

    List<ImmutableRevisionBlob> revisionEntries() {
        return revisionEntries;
    }

    @Override
    public String toString() {
        return "HistoryPersistentEnvelopeV0[skillId=" + skillId
                + ", revisionCount=" + revisionEntries.size() + "]";
    }
}

/** Package-internal current physical revision envelope. */
final class RevisionPersistentEnvelopeV0 {
    private final SkillRevision revision;
    private final String documentEncoding;
    private final EncodedSkillDocument document;

    RevisionPersistentEnvelopeV0(
            SkillRevision revision,
            String documentEncoding,
            EncodedSkillDocument document) {
        this.revision = Objects.requireNonNull(revision, "revision");
        this.documentEncoding = Objects.requireNonNull(documentEncoding, "documentEncoding");
        this.document = Objects.requireNonNull(document, "document");
    }

    SkillRevision revision() {
        return revision;
    }

    String documentEncoding() {
        return documentEncoding;
    }

    EncodedSkillDocument document() {
        return document;
    }

    @Override
    public String toString() {
        return "RevisionPersistentEnvelopeV0[revision=" + revision.value()
                + ", knownEncoding="
                + StorePersistenceSchema.DOCUMENT_ENCODING.equals(documentEncoding)
                + ", documentByteCount=" + document.byteCount() + "]";
    }
}
