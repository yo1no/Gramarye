package com.yo1no.gramarye.magic.definition.store;

import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

/** Transient no-follow identity metadata used only while one primary channel is open. */
record PrimaryFileMetadata(
        Object fileKey,
        long size,
        FileTime lastModifiedTime,
        boolean regularFile,
        boolean symbolicLink,
        boolean directory) {
    PrimaryFileMetadata {
        if (size < 0) {
            throw new IllegalArgumentException("primary file size must be non-negative");
        }
        Objects.requireNonNull(lastModifiedTime, "lastModifiedTime");
    }

    static PrimaryFileMetadata capture(BasicFileAttributes attributes) {
        Objects.requireNonNull(attributes, "attributes");
        return new PrimaryFileMetadata(
                attributes.fileKey(),
                attributes.size(),
                attributes.lastModifiedTime(),
                attributes.isRegularFile(),
                attributes.isSymbolicLink(),
                attributes.isDirectory());
    }

    boolean sameIdentityAndShape(PrimaryFileMetadata other) {
        Objects.requireNonNull(other, "other");
        return Objects.equals(fileKey, other.fileKey)
                && size == other.size
                && lastModifiedTime.equals(other.lastModifiedTime)
                && regularFile == other.regularFile
                && symbolicLink == other.symbolicLink
                && directory == other.directory;
    }
}
