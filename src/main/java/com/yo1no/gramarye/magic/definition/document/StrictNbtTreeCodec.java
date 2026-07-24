package com.yo1no.gramarye.magic.definition.document;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Bounded uncompressed arbitrary-tag framing; it never converts through JSON. */
final class StrictNbtTreeCodec {
    private static final long ENCODED_BYTE_QUOTA_MULTIPLIER = 2;
    private static final long NODE_QUOTA_BYTES = 65;

    private StrictNbtTreeCodec() {
    }

    static ImmutableEncodedBytes encode(Tag value, long maximum) throws IOException {
        var snapshot = Objects.requireNonNull(value, "value").copy();
        requireEncodable(snapshot, true);
        return BoundedByteEncoding.encode(maximum, output -> {
            var data = new DataOutputStream(output);
            NbtIo.writeAnyTag(snapshot, data);
            data.flush();
        });
    }

    static Tag decode(ImmutableEncodedBytes encoded, long maximum) throws IOException {
        Objects.requireNonNull(encoded, "encoded");
        BoundedByteEncoding.requireWithinLimit(encoded.size(), maximum);
        if (encoded.size() == 0) {
            throw new MalformedTreeException("NBT");
        }

        var bytes = encoded.copyBytes();
        var quota = accounterQuota(bytes.length);
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            var result = NbtIo.readAnyTag(
                    input,
                    new NbtAccounter(quota, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH));
            if (input.read() != -1) {
                throw new MalformedTreeException("NBT");
            }
            return result;
        } catch (MalformedTreeException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new MalformedTreeException("NBT", exception);
        }
    }

    static long accounterQuota(long encodedLength) {
        if (encodedLength <= 0) {
            throw new IllegalArgumentException("encodedLength must be positive");
        }
        return BoundedByteEncoding.checkedAdd(
                BoundedByteEncoding.checkedMultiply(
                        ENCODED_BYTE_QUOTA_MULTIPLIER,
                        encodedLength),
                BoundedByteEncoding.checkedMultiply(
                        NODE_QUOTA_BYTES,
                        MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES));
    }

    private static void requireEncodable(Tag value, boolean root) throws MalformedTreeException {
        if (!root && value instanceof EndTag) {
            throw new MalformedTreeException("NBT");
        }
        if (value instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (child == null) {
                    throw new MalformedTreeException("NBT");
                }
                requireEncodable(child, false);
            }
        } else if (value instanceof ListTag list) {
            for (var child : list) {
                requireEncodable(child, false);
            }
        }
    }
}
