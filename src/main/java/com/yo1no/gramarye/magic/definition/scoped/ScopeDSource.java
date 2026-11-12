package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Isolated server-wide owner admission. There is no game lifecycle caller in M1. */
final class ScopeDSource {
    private static final String LOCK = ".owner-admission.lock";
    private static final String ADMITTED = ".admitted";
    private static final Set<Object> ACTIVE_ROOTS = ConcurrentHashMap.newKeySet();

    private ScopeDSource() {}

    static ScopeDStorage open(Path root, SkillOwnerId owner) throws IOException {
        Path requested = root.toAbsolutePath().normalize();
        if (requested.getFileSystem() != FileSystems.getDefault() || !Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unqualified catalog source root");
        }
        Path source = requested.toRealPath();
        Object sourceKey = key(source);
        String fileSystem = Files.getFileStore(source).type();
        if (!sourceKey.equals(key(requested)) || !Set.of("apfs", "ext4").contains(fileSystem)) {
            throw new IOException("Catalog source filesystem is not qualified");
        }
        if (!ACTIVE_ROOTS.add(sourceKey)) {
            throw new IOException("Catalog owner admission already active");
        }
        Path lockPath = source.resolve(LOCK);
        try (FileChannel lockFile = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS); FileLock lock = lockFile.tryLock()) {
            if (lock == null) {
                throw new IOException("Catalog owner admission already active");
            }
            Object lockKey = key(lockPath);
            Set<UUID> admitted = inventory(source);
            Path directory = source.resolve(owner.value().toString());
            if (!admitted.contains(owner.value())) {
                if (admitted.size() == ScopeDLimits.MAX_OWNERS) {
                    throw new IOException("Catalog owner capacity");
                }
                Files.createDirectory(directory);
                force(source);
                try (FileChannel marker = FileChannel.open(source.resolve(owner.value() + ADMITTED),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    marker.force(true);
                }
                force(source);
            }
            if (!sourceKey.equals(key(source)) || !lockKey.equals(key(lockPath))) {
                throw new IOException("Catalog owner source changed during admission");
            }
            return ScopeDStorage.open(directory, owner);
        } catch (OverlappingFileLockException unavailable) {
            throw new IOException("Catalog owner admission already active", unavailable);
        } finally {
            ACTIVE_ROOTS.remove(sourceKey);
        }
    }

    private static Set<UUID> inventory(Path source) throws IOException {
        Set<UUID> admitted = new HashSet<>();
        Set<UUID> directories = new HashSet<>();
        int entries = 0;
        try (var stream = Files.newDirectoryStream(source)) {
            for (Path entry : stream) {
                if (++entries > 2 * ScopeDLimits.MAX_OWNERS + 1) {
                    throw new IOException("Catalog source inventory capacity");
                }
                String name = entry.getFileName().toString();
                if (name.equals(LOCK)) {
                    continue;
                }
                if (name.endsWith(ADMITTED) && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
                        && Files.size(entry) == 0) {
                    admitted.add(uuid(name.substring(0, name.length() - ADMITTED.length())));
                } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    directories.add(uuid(name));
                } else {
                    throw new IOException("Unrecognized catalog owner inventory entry");
                }
            }
        }
        if (!directories.equals(admitted)) {
            throw new IOException("Incomplete catalog owner admission inventory");
        }
        return admitted;
    }

    private static UUID uuid(String name) throws IOException {
        try {
            UUID id = UUID.fromString(name);
            if (id.toString().equals(name)) {
                return id;
            }
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Invalid catalog owner inventory identity", malformed);
        }
        throw new IOException("Noncanonical catalog owner inventory identity");
    }

    private static Object key(Path path) throws IOException {
        return Objects.requireNonNull(Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS)
                .fileKey(), "Catalog source requires stable file identities");
    }

    private static void force(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            channel.force(true);
        }
    }
}
