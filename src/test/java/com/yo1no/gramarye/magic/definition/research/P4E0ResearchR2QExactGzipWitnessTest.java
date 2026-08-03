package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.Deflater;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exact physical gzip witness for the locked R2Q per-file compressed coordinate. */
final class P4E0ResearchR2QExactGzipWitnessTest {
    private static final long EXACT_PHYSICAL_BYTES = 33_559_514L;
    private static final long PLUS_ONE_PHYSICAL_BYTES = EXACT_PHYSICAL_BYTES + 1L;
    private static final long DECOMPRESSED_GUARD = 64L;
    private static final int FNAME_TERMINATOR_BYTES = 1;
    private static final int FHCRC_BYTES = 2;

    @TempDir
    Path temporary;

    @Test
    void exactAndPlusOneAreLegalSingleMembersDifferingByOneFnameByte()
            throws Exception {
        var canonical = P4E0ResearchWireNbt.measure(
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                EXACT_PHYSICAL_BYTES,
                DECOMPRESSED_GUARD,
                P4E0ResearchR2QExactGzipWitnessTest::writeMinimalRoot);
        var fileNameBytes = Math.toIntExact(Math.subtractExact(
                Math.subtractExact(EXACT_PHYSICAL_BYTES, canonical.physicalBytes()),
                FNAME_TERMINATOR_BYTES + FHCRC_BYTES));
        var exactOptions = new P4E0ResearchWireNbt.HeaderOptions(
                0, fileNameBytes, 0, true, 0x5a);
        var plusOneOptions = new P4E0ResearchWireNbt.HeaderOptions(
                0, Math.addExact(fileNameBytes, 1), 0, true, 0x5a);

        var exactPath = temporary.resolve("exact-member.dat");
        var plusOnePath = temporary.resolve("plus-one-member.dat");
        var exact = P4E0ResearchWireNbt.write(
                exactPath,
                exactOptions,
                Deflater.DEFAULT_COMPRESSION,
                EXACT_PHYSICAL_BYTES,
                DECOMPRESSED_GUARD,
                P4E0ResearchR2QExactGzipWitnessTest::writeMinimalRoot);
        var plusOne = P4E0ResearchWireNbt.write(
                plusOnePath,
                plusOneOptions,
                Deflater.DEFAULT_COMPRESSION,
                PLUS_ONE_PHYSICAL_BYTES,
                DECOMPRESSED_GUARD,
                P4E0ResearchR2QExactGzipWitnessTest::writeMinimalRoot);

        var exactStrict = P4E0ResearchGzipAdapter.readWireDrain(
                exactPath, EXACT_PHYSICAL_BYTES, DECOMPRESSED_GUARD);
        var plusOneStrict = P4E0ResearchGzipAdapter.readWireDrain(
                plusOnePath, PLUS_ONE_PHYSICAL_BYTES, DECOMPRESSED_GUARD);
        var guardFailure = assertThrows(
                IOException.class,
                () -> P4E0ResearchGzipAdapter.readWireDrain(
                        plusOnePath, EXACT_PHYSICAL_BYTES, DECOMPRESSED_GUARD));

        assertAll(
                () -> assertEquals(EXACT_PHYSICAL_BYTES, exact.physicalBytes()),
                () -> assertEquals(PLUS_ONE_PHYSICAL_BYTES, plusOne.physicalBytes()),
                () -> assertEquals(EXACT_PHYSICAL_BYTES, Files.size(exactPath)),
                () -> assertEquals(PLUS_ONE_PHYSICAL_BYTES, Files.size(plusOnePath)),
                () -> assertEquals(
                        10L + fileNameBytes + FNAME_TERMINATOR_BYTES + FHCRC_BYTES,
                        exact.headerBytes()),
                () -> assertEquals(exact.headerBytes() + 1L, plusOne.headerBytes()),
                () -> assertEquals(exact.decompressedBytes(), plusOne.decompressedBytes()),
                () -> assertEquals(
                        exactStrict.decompressedRootBytes(),
                        plusOneStrict.decompressedRootBytes()),
                () -> assertEquals(EXACT_PHYSICAL_BYTES, exactStrict.physicalFileBytes()),
                () -> assertEquals(PLUS_ONE_PHYSICAL_BYTES, plusOneStrict.physicalFileBytes()),
                () -> assertEquals(
                        "research compressed guard exceeded", guardFailure.getMessage()));

        corruptFirstFhcrcByte(exactPath, exact.headerBytes());
        assertThrows(
                IOException.class,
                () -> P4E0ResearchGzipAdapter.readWireDrain(
                        exactPath, EXACT_PHYSICAL_BYTES, DECOMPRESSED_GUARD));
    }

    private static void writeMinimalRoot(java.io.DataOutput output) throws IOException {
        P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
        output.writeByte(Tag.TAG_INT);
        output.writeUTF("DataVersion");
        output.writeInt(3_955);
        output.writeByte(Tag.TAG_END);
    }

    private static void corruptFirstFhcrcByte(Path path, long headerBytes) throws IOException {
        var position = Math.subtractExact(headerBytes, FHCRC_BYTES);
        try (var channel = FileChannel.open(
                path, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            var value = ByteBuffer.allocate(1);
            channel.position(position);
            while (value.hasRemaining()) {
                if (channel.read(value) < 0) {
                    throw new IOException("truncated FHCRC witness");
                }
            }
            value.flip();
            value.put(0, (byte) (value.get(0) ^ 0x01));
            channel.position(position);
            while (value.hasRemaining()) {
                channel.write(value);
            }
            channel.force(true);
        }
    }
}
