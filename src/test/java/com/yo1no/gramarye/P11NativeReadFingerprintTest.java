package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class P11NativeReadFingerprintTest {
    @Test
    void unchangedNativeReadInputMatchesButAliasedMutationDoesNot() {
        var root = new CompoundTag();
        var shoulder = new CompoundTag();
        shoulder.putString("id", "minecraft:parrot");
        root.put("ShoulderEntityLeft", shoulder);
        var before = P11NativeReadFingerprint.capture(root);
        assertNotNull(before);
        assertTrue(before.matches(root));
        shoulder.putBoolean("changed", true);
        assertFalse(before.matches(root));
    }

    @Test
    void lossyNativeStringFallbackCannotMintAnInputFingerprint() {
        var root = new CompoundTag();
        root.putString("s", "\u0000".repeat(32768));
        assertNull(P11NativeReadFingerprint.capture(root));
    }
}
