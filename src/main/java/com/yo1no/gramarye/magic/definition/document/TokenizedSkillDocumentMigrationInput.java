package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.Objects;

/**
 * Opaque, bounded input minted only by the document persistence package for skill migration.
 *
 * <p>The public type permits the migration facade to consume the input without exposing a public
 * constructor or factory that could bypass document tokenization and conformance checks.</p>
 */
public final class TokenizedSkillDocumentMigrationInput {
    private final byte[] bytes;

    private TokenizedSkillDocumentMigrationInput(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0 || bytes.length > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
            throw new IllegalArgumentException(
                    "tokenized migration input byte count is outside the hard ceiling");
        }
        this.bytes = bytes.clone();
    }

    static TokenizedSkillDocumentMigrationInput copyOf(byte[] bytes) {
        return new TokenizedSkillDocumentMigrationInput(bytes);
    }

    public int byteCount() {
        return bytes.length;
    }

    /** Returns a fresh copy; the tokenized document tree is never exposed. */
    public byte[] copyBytes() {
        return bytes.clone();
    }

    @Override
    public String toString() {
        return "TokenizedSkillDocumentMigrationInput[byteCount=" + bytes.length + "]";
    }
}
