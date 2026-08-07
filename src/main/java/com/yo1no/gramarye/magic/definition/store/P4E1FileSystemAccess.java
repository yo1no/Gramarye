package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Narrow package-private filesystem seam used for deterministic identity/race tests. */
abstract class P4E1FileSystemAccess {
    static final P4E1FileSystemAccess SYSTEM = new SystemAccess();

    abstract BasicFileAttributes readAttributes(Path path) throws IOException;

    abstract DirectoryStream<Path> openDirectory(Path directory) throws IOException;

    abstract FileChannel openRead(Path path) throws IOException;

    private static final class SystemAccess extends P4E1FileSystemAccess {
        private static final Set<OpenOption> READ_NOFOLLOW = Set.of(
                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

        private SystemAccess() {
        }

        @Override
        BasicFileAttributes readAttributes(Path path) throws IOException {
            return Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            return Files.newDirectoryStream(directory);
        }

        @Override
        FileChannel openRead(Path path) throws IOException {
            return FileChannel.open(path, READ_NOFOLLOW);
        }
    }
}
