package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

/** Locks the approved V0 unnamed arbitrary-NBT Revision framing before P4-A2 implementation. */
class P4A2RevisionFramingGateTest {
    private static final int EXPECTED_WRAPPER_BYTES = 85;

    @Test
    void v0RevisionWrapperIsExactlyEightyFiveBytes() throws Exception {
        assertEncodedSize(0);
        assertEncodedSize(1);
        assertEncodedSize(4_096);
    }

    private static void assertEncodedSize(int documentByteCount) throws Exception {
        var revision = new CompoundTag();
        revision.putInt("revision", 0);
        revision.putString("document_encoding", "family_tagged_subtrees_v0");
        revision.putByteArray("document_bytes", new byte[documentByteCount]);

        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            NbtIo.writeAnyTag(revision, data);
        }

        assertEquals(EXPECTED_WRAPPER_BYTES + documentByteCount, output.size());
    }
}
