package com.yo1no.gramarye.magic.definition.store;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/** Transient no-follow identity witness for one P4-E1 filesystem object. */
record P4E1FileMetadata(
        Object fileKey,
        long size,
        FileTime lastModifiedTime,
        boolean regularFile,
        boolean symbolicLink,
        boolean directory) {
    P4E1FileMetadata {
        if (size < 0L) {
            throw new IllegalArgumentException("filesystem size must be non-negative");
        }
        Objects.requireNonNull(lastModifiedTime, "lastModifiedTime");
    }

    static P4E1FileMetadata capture(BasicFileAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return new P4E1FileMetadata(
                attributes.fileKey(),
                attributes.size(),
                attributes.lastModifiedTime(),
                attributes.isRegularFile(),
                attributes.isSymbolicLink(),
                attributes.isDirectory());
    }

    boolean sameIdentityAndShape(P4E1FileMetadata other) {
        Objects.requireNonNull(other, "other");
        return Objects.equals(fileKey, other.fileKey)
                && size == other.size
                && lastModifiedTime.equals(other.lastModifiedTime)
                && regularFile == other.regularFile
                && symbolicLink == other.symbolicLink
                && directory == other.directory;
    }
}
