package com.yo1no.gramarye;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/** Detects mutation of retained native read input; not a read receipt or a material copy. */
final class P11NativeReadFingerprint {
    private final byte[] digest;

    private P11NativeReadFingerprint(byte[] digest) { this.digest = digest; }

    static P11NativeReadFingerprint capture(CompoundTag input) {
        if (!(P11StrictNbtSize.measure(input, Long.MAX_VALUE) instanceof P11StrictNbtSize.Fits)) {
            return null;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                NbtIo.write(input, output);
            }
            return new P11NativeReadFingerprint(digest.digest());
        } catch (IOException impossibleForNullOutput) {
            throw new IllegalStateException("P11_NATIVE_INPUT_FINGERPRINT_FAILED", impossibleForNullOutput);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("P11_SHA256_UNAVAILABLE", unavailable);
        }
    }

    boolean matches(CompoundTag input) {
        var current = capture(input);
        return current != null && Arrays.equals(digest, current.digest);
    }
}
