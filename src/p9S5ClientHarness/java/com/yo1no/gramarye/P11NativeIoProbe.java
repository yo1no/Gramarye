package com.yo1no.gramarye;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Excluded engineering companion: invokes actual native primitives outside any managed writer.
 * These observations do not establish a managed body, receipt, injection hit count, or consumer PASS.
 * Every fixture is new and retained; no production callbacks are invoked directly.
 */
final class P11NativeIoProbe {
    private static final int PAYLOAD_BYTES = 131_072;
    private static final int FAIL_AFTER_BYTES = 2_048;
    private static final long READ_BUDGET = 1_048_576;
    private static final byte[] OLD = "p11-owned-old".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW = "p11-owned-new".getBytes(StandardCharsets.UTF_8);

    private P11NativeIoProbe() { }

    enum Case {
        REPLACE_SUCCESS, REPLACE_FALSE, REPLACE_THROW, NBT_NORMAL, NBT_WRITE_FAILURE,
        NBT_CLOSE_FAILURE, NBT_WRITE_AND_CLOSE_FAILURE, NBT_ERROR_AND_CLOSE_FAILURE,
        NBT_STRING_FALLBACK
    }

    enum Outcome { RETURN_TRUE, RETURN_FALSE, RETURN_NORMAL, RETURN_NORMAL_LOSSY,
        THREW_IO, THREW_RUNTIME, THREW_ERROR }

    /** Scalar observations only; neither resources nor live/native object graphs escape. */
    record Observation(Case probe, Outcome outcome, long fileBytes, long delegatedBytes,
            int nativeCloseCalls, String primaryType, int suppressedCount, boolean identityPreserved,
            boolean nativeBodyWriteFrame, boolean readbackMatched) { }

    record Report(List<Observation> observations) {
        Report { observations = List.copyOf(observations); }

        @Override
        public String toString() {
            var text = new StringBuilder("P11-UNMANAGED-NATIVE-IO-V1\n"
                    + "scope=UNMANAGED_PRIMITIVE_IO\nmanagedConsumerProof=false\n"
                    + "manualBoundaryCallbacks=false\ninjectionHitCountProof=false\n");
            for (var observation : observations) { text.append(observation).append('\n'); }
            text.append("verifiedPrimitiveCases=").append(observations.size());
            return text.toString();
        }
    }

    /** Called once by the positive native cohort, before its player save, with a fresh owned path. */
    static Report run(Path freshOwnedDirectory) {
        Objects.requireNonNull(freshOwnedDirectory, "freshOwnedDirectory");
        require(freshOwnedDirectory.isAbsolute()
                        && freshOwnedDirectory.equals(freshOwnedDirectory.normalize())
                        && freshOwnedDirectory.getParent() != null,
                "native primitive directory must be normalized, absolute, and non-root");
        try {
            require(Files.isDirectory(freshOwnedDirectory.getParent(), LinkOption.NOFOLLOW_LINKS),
                    "native primitive parent must already be an owned directory");
            Files.createDirectory(freshOwnedDirectory);
            var observations = new ArrayList<Observation>();
            observations.add(replaceSuccess(freshOwnedDirectory));
            observations.add(replaceFalse(freshOwnedDirectory));
            observations.add(replaceThrow(freshOwnedDirectory));
            observations.add(nbtNormal(freshOwnedDirectory));
            observations.add(nbtFault(freshOwnedDirectory, Case.NBT_WRITE_FAILURE, true, false, false));
            observations.add(nbtFault(freshOwnedDirectory, Case.NBT_CLOSE_FAILURE, false, true, false));
            observations.add(nbtFault(freshOwnedDirectory, Case.NBT_WRITE_AND_CLOSE_FAILURE, true, true, false));
            observations.add(nbtFault(freshOwnedDirectory, Case.NBT_ERROR_AND_CLOSE_FAILURE, true, true, true));
            observations.add(nbtFallback(freshOwnedDirectory));
            require(observations.size() == Case.values().length, "missing native primitive case");
            return new Report(observations);
        } catch (IOException failure) {
            throw new IllegalStateException("native primitive fixture failed; owned files retained", failure);
        }
    }

    private static Observation replaceSuccess(Path directory) throws IOException {
        Path current = createBytes(directory.resolve("replace-success.dat"), OLD);
        Path incoming = createBytes(directory.resolve("replace-success-incoming.dat"), NEW);
        Path backup = directory.resolve("replace-success.dat_old");
        boolean result = Util.safeReplaceOrMoveFile(current, incoming, backup, false);
        require(result && Arrays.equals(Files.readAllBytes(current), NEW)
                        && Arrays.equals(Files.readAllBytes(backup), OLD) && Files.notExists(incoming),
                "native successful replacement did not preserve its expected files");
        return plain(Case.REPLACE_SUCCESS, Outcome.RETURN_TRUE, Files.size(current), true);
    }

    private static Observation replaceFalse(Path directory) throws IOException {
        Path current = createBytes(directory.resolve("replace-false.dat"), OLD);
        Path incoming = createBytes(directory.resolve("replace-false-incoming.dat"), NEW);
        Path backup = Files.createDirectory(directory.resolve("replace-false.dat_old"));
        Path sentinel = createBytes(backup.resolve("owned-sentinel"), OLD);
        boolean result = Util.safeReplaceOrMoveFile(current, incoming, backup, false);
        require(!result && Arrays.equals(Files.readAllBytes(current), OLD)
                        && Arrays.equals(Files.readAllBytes(incoming), NEW)
                        && Arrays.equals(Files.readAllBytes(sentinel), OLD),
                "native false replacement changed its retained owned inputs");
        return plain(Case.REPLACE_FALSE, Outcome.RETURN_FALSE, Files.size(current), true);
    }

    private static Observation replaceThrow(Path directory) throws IOException {
        Path archive = directory.resolve("replace-closed-filesystem.zip");
        Path current;
        Path incoming;
        Path backup;
        // A real provider lifecycle failure, not a fabricated Path or replacement result.
        try (var fileSystem = FileSystems.newFileSystem(URI.create("jar:" + archive.toUri()),
                Map.of("create", "true"))) {
            current = createBytes(fileSystem.getPath("/current.dat"), OLD);
            incoming = createBytes(fileSystem.getPath("/incoming.dat"), NEW);
            backup = fileSystem.getPath("/backup.dat");
        }
        try {
            Util.safeReplaceOrMoveFile(current, incoming, backup, false);
            throw new IllegalStateException("native replace unexpectedly accepted a closed filesystem");
        } catch (ClosedFileSystemException expected) {
            require(hasFrame(expected, Util.class.getName(), "safeReplaceOrMoveFile"),
                    "closed filesystem failure did not pass through native replacement");
            return new Observation(Case.REPLACE_THROW, Outcome.THREW_RUNTIME, Files.size(archive),
                    0, 0, expected.getClass().getName(), expected.getSuppressed().length,
                    false, false, false);
        }
    }

    private static Observation nbtNormal(Path directory) throws IOException {
        var root = payload();
        Path output = Files.createFile(directory.resolve("nbt-normal.dat"));
        NbtIo.writeCompressed(root, output);
        require(root.equals(read(output)), "normal native NBT readback differed");
        return plain(Case.NBT_NORMAL, Outcome.RETURN_NORMAL, Files.size(output), true);
    }

    private static Observation nbtFault(Path directory, Case probe, boolean failWrite,
            boolean failClose, boolean useError) throws IOException {
        var root = payload();
        Path output = directory.resolve(probe.name().toLowerCase(java.util.Locale.ROOT) + ".dat");
        var writeIo = new IOException("owned native probe write failure");
        var writeError = new ProbeWriteError();
        var closeIo = new IOException("owned native probe close failure");
        Throwable observed = null;
        FaultOutput stream;
        // The outer owner closes the real descriptor even if the tested native body fails early.
        // It does not close FaultOutput or add probe-generated suppressed exceptions.
        try (var actual = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            stream = new FaultOutput(actual, failWrite, failClose, useError, writeIo, writeError, closeIo);
            try {
                NbtIo.writeCompressed(root, stream);
            } catch (IOException | ProbeWriteError expected) {
                observed = expected;
            }
        }
        Throwable expected = failWrite ? (useError ? writeError : writeIo) : closeIo;
        require(observed == expected, "native I/O did not preserve the actual primary failure identity");
        boolean bodyFrame = hasFrame(observed, NbtIo.class.getName(), "write");
        require(!failWrite || (stream.writeFailed && bodyFrame),
                "write failure did not arise inside the original NbtIo.write body");
        require(stream.closeCalls > 0 && stream.closeFailed == failClose,
                "original NBT close did not reach the requested real stream boundary");
        var suppressed = observed.getSuppressed();
        require(suppressed.length == (failWrite && failClose ? 1 : 0)
                        && (suppressed.length == 0 || suppressed[0] == closeIo),
                "native try-with-resources changed primary/suppressed failure identities");
        boolean readback = !failWrite;
        if (readback) {
            require(root.equals(read(output)), "close-failure bytes did not round-trip after real close");
        }
        return new Observation(probe, useError ? Outcome.THREW_ERROR : Outcome.THREW_IO,
                Files.size(output), stream.written, stream.closeCalls, observed.getClass().getName(),
                suppressed.length, true, bodyFrame, readback);
    }

    private static Observation nbtFallback(Path directory) throws IOException {
        var root = new CompoundTag();
        // 32,768 NUL characters require 65,536 modified-UTF bytes: one byte over writeUTF's bound.
        String oversized = "\0".repeat(32_768);
        root.putString("oversized", oversized);
        root.putInt("marker", 11);
        Path output = Files.createFile(directory.resolve("nbt-string-fallback.dat"));
        NbtIo.writeCompressed(root, output);
        var readback = read(output);
        require(readback.contains("oversized", 8) && readback.getString("oversized").isEmpty()
                        && readback.getInt("marker") == 11
                        && root.getString("oversized").equals(oversized),
                "native oversized-string fallback did not exhibit its actual lossy normal return");
        return plain(Case.NBT_STRING_FALLBACK, Outcome.RETURN_NORMAL_LOSSY, Files.size(output), false);
    }

    private static CompoundTag payload() {
        var root = new CompoundTag();
        var bytes = new byte[PAYLOAD_BYTES];
        new Random(0x5031314E42544CL).nextBytes(bytes);
        root.putByteArray("owned_incompressible_payload", bytes);
        root.putInt("marker", 11);
        return root;
    }

    private static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(READ_BUDGET));
    }

    private static Path createBytes(Path path, byte[] bytes) throws IOException {
        return Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static Observation plain(Case probe, Outcome outcome, long bytes, boolean readback) {
        return new Observation(probe, outcome, bytes, 0, 0, "NONE", 0, false, false, readback);
    }

    private static boolean hasFrame(Throwable failure, String owner, String method) {
        return Arrays.stream(failure.getStackTrace()).anyMatch(frame ->
                frame.getClassName().equals(owner) && frame.getMethodName().equals(method));
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static final class ProbeWriteError extends Error {
        private ProbeWriteError() { super("owned native probe write Error"); }
    }

    /** One-shot faults at actual delegated I/O; all other bytes go to an owned physical file. */
    private static final class FaultOutput extends OutputStream {
        private final OutputStream actual;
        private final boolean failWrite;
        private final boolean failClose;
        private final boolean useError;
        private final IOException writeIo;
        private final ProbeWriteError writeError;
        private final IOException closeIo;
        private long written;
        private int closeCalls;
        private boolean writeFailed;
        private boolean closeFailed;

        private FaultOutput(OutputStream actual, boolean failWrite, boolean failClose, boolean useError,
                IOException writeIo, ProbeWriteError writeError, IOException closeIo) {
            this.actual = actual;
            this.failWrite = failWrite;
            this.failClose = failClose;
            this.useError = useError;
            this.writeIo = writeIo;
            this.writeError = writeError;
            this.closeIo = closeIo;
        }

        @Override
        public void write(int value) throws IOException {
            if (failWrite && !writeFailed && written >= FAIL_AFTER_BYTES) { failWriteOnce(); }
            actual.write(value);
            written++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length > 0 && failWrite && !writeFailed && written + length > FAIL_AFTER_BYTES) {
                int accepted = (int) Math.max(0, FAIL_AFTER_BYTES - written);
                if (accepted > 0) {
                    actual.write(bytes, offset, accepted);
                    written += accepted;
                }
                failWriteOnce();
            }
            actual.write(bytes, offset, length);
            written += length;
        }

        private void failWriteOnce() throws IOException {
            writeFailed = true;
            if (useError) { writeError.fillInStackTrace(); throw writeError; }
            writeIo.fillInStackTrace();
            throw writeIo;
        }

        @Override
        public void flush() throws IOException { actual.flush(); }

        @Override
        public void close() throws IOException {
            closeCalls++;
            actual.close();
            if (failClose && !closeFailed) {
                closeFailed = true;
                closeIo.fillInStackTrace();
                throw closeIo;
            }
        }
    }
}
