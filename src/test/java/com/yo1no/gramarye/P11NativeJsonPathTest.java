package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure native-path identity, not file IO or native source qualification. */
final class P11NativeJsonPathTest {
    private static final UUID ID = UUID.fromString("ed657ae6-fc90-45b1-a1ca-ae51a32f1462");
    private static final Path DIRECTORY = Path.of("owned-test-world", "stats").toAbsolutePath();

    @Test
    void canonicalDirectoryAndFullUuidIdentifyOnlyTheNativeJsonCounterpart() {
        assertEquals(Optional.of(ID), P11NativeJsonPath.playerId(DIRECTORY,
                DIRECTORY.resolve(ID + ".json")));
        assertEquals(Optional.of(ID), P11NativeJsonPath.playerId(DIRECTORY,
                DIRECTORY.resolve(ID.toString().toUpperCase(Locale.ROOT) + ".json")),
                "UUID casing cannot hide a counterpart on case-insensitive native filesystems");
        assertEquals(Optional.of(ID), P11NativeJsonPath.playerId(DIRECTORY,
                DIRECTORY.resolve("unused").resolve("..").resolve(ID + ".json")));
    }

    @Test
    void sameUuidOutsideExactDirectoryDoesNotGrantAnyManagedIdentity() {
        assertTrue(P11NativeJsonPath.playerId(DIRECTORY,
                DIRECTORY.resolveSibling("advancements").resolve(ID + ".json")).isEmpty());
        assertTrue(P11NativeJsonPath.playerId(DIRECTORY,
                DIRECTORY.resolve("nested").resolve(ID + ".json")).isEmpty());
        assertTrue(P11NativeJsonPath.playerId(DIRECTORY,
                DIRECTORY.resolve("..").resolve(ID + ".json")).isEmpty());
    }

    @Test
    void malformedOrNonNativeNamesAreNeverUuidEvidence() {
        for (var name : new String[] {"1-1-1-1-1.json", ID + ".json_old", ID + ".dat",
                ID + ".json.tmp", "not-a-player.json", ID + ".JSON"}) {
            assertTrue(P11NativeJsonPath.playerId(DIRECTORY, DIRECTORY.resolve(name)).isEmpty());
        }
        assertTrue(P11NativeJsonPath.playerId(DIRECTORY, null).isEmpty());
        assertTrue(P11NativeJsonPath.playerId(null, DIRECTORY.resolve(ID + ".json")).isEmpty());
    }

    @Test
    void counterpartProtectionRecognizesBothJsonOwnersAndPlayerPrimaryAndBackup() {
        var advancements = DIRECTORY.resolveSibling("advancements");
        var players = DIRECTORY.resolveSibling("playerdata");
        for (var file : new Path[] {DIRECTORY.resolve(ID + ".json"),
                advancements.resolve(ID + ".json"), players.resolve(ID + ".dat"),
                players.resolve(ID + ".dat_old")}) {
            assertEquals(Optional.of(ID), P11NativeJsonPath.protectedPlayerId(
                    DIRECTORY, advancements, players, file));
        }
        assertTrue(P11NativeJsonPath.protectedPlayerId(DIRECTORY, advancements, players,
                advancements.resolve(ID + ".dat")).isEmpty());
        assertTrue(P11NativeJsonPath.protectedPlayerId(DIRECTORY, advancements, players,
                players.resolve(ID + ".json")).isEmpty());
        assertTrue(P11NativeJsonPath.protectedPlayerId(DIRECTORY, advancements, players,
                players.resolve(ID + ".dat_tmp")).isEmpty());
    }

    @Test
    void hostProtectionNamesOnlyOriginalLevelPrimaryAndBackupInTheExactWorldRoot() {
        var world = DIRECTORY.getParent();
        assertTrue(P11NativeJsonPath.levelFile(world, world.resolve("level.dat")));
        assertTrue(P11NativeJsonPath.levelFile(world, world.resolve("level.dat_old")));
        assertFalse(P11NativeJsonPath.levelFile(world, DIRECTORY.resolve("level.dat")));
        assertFalse(P11NativeJsonPath.levelFile(world, world.resolve("level.dat_new")));
        assertFalse(P11NativeJsonPath.levelFile(world, world.resolve("other.dat")));
        assertFalse(P11NativeJsonPath.levelFile(world, null));
    }
}
