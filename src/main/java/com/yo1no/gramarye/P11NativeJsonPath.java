package com.yo1no.gramarye;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/** Finite native filename identities for JSON-writer rejection; not a filesystem sandbox. */
final class P11NativeJsonPath {
    private P11NativeJsonPath() {}

    static Optional<UUID> playerId(Path exactDirectory, Path file) {
        return uuidFile(exactDirectory, file, ".json");
    }

    static Optional<UUID> playerDataId(Path exactDirectory, Path file) {
        var primary = uuidFile(exactDirectory, file, ".dat");
        return primary.isPresent() ? primary : uuidFile(exactDirectory, file, ".dat_old");
    }

    static Optional<UUID> protectedPlayerId(Path statsDirectory, Path advancementsDirectory,
            Path playerDirectory, Path file) {
        var stats = playerId(statsDirectory, file);
        if (stats.isPresent()) { return stats; }
        var advancements = playerId(advancementsDirectory, file);
        return advancements.isPresent() ? advancements : playerDataId(playerDirectory, file);
    }

    static boolean levelFile(Path exactDirectory, Path file) {
        String name = nativeName(exactDirectory, file);
        return "level.dat".equals(name) || "level.dat_old".equals(name);
    }

    private static Optional<UUID> uuidFile(Path exactDirectory, Path file, String suffix) {
        String name = nativeName(exactDirectory, file);
        if (name == null || name.length() != 36 + suffix.length() || !name.endsWith(suffix)) {
            return Optional.empty();
        }
        String stem = name.substring(0, 36);
        try {
            var id = UUID.fromString(stem);
            return id.toString().equalsIgnoreCase(stem) ? Optional.of(id) : Optional.empty();
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static String nativeName(Path exactDirectory, Path file) {
        if (exactDirectory == null || file == null) { return null; }
        var target = file.toAbsolutePath().normalize();
        if (!exactDirectory.toAbsolutePath().normalize().equals(target.getParent())) {
            return null;
        }
        return target.getFileName().toString();
    }
}
