package com.yo1no.gramarye.magic.definition.research;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.Deflater;
import net.minecraft.nbt.Tag;

/**
 * Streaming physical witnesses for every approved per-file NBT peak.
 *
 * <p>The witnesses deliberately share one qualification aggregate budget, proving that the peak
 * shapes can coexist below the approved aggregate tuple. This remains a unit/preflight fixture:
 * it does not materialize the full 2,048-record profile or start a child process.</p>
 */
final class P4E0R2QPositiveWitnesses {
    private static final int DATA_VERSION = 3_955;
    private static final int BUFFER_BYTES = 8_192;

    private P4E0R2QPositiveWitnesses() {
    }

    static Result materializeAndScan(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            throw new IOException("R2Q positive witness root is unavailable");
        }

        var budget = new P4E0R2QAuditBudget();
        budget.requireJournalReady(true);
        budget.observeDirectoryEntries(WitnessKind.values().length);
        var facts = new EnumMap<WitnessKind, P4E0ResearchWireNbt.ScanFacts>(
                WitnessKind.class);
        var dfu = new P4E0R2QAuditBudget.DfuInvocationProbe();
        for (var kind : WitnessKind.values()) {
            var path = root.resolve(kind.slug + ".dat");
            write(path, kind);
            var scanned = P4E0ResearchWireNbt.scan(
                    path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
            var dataVersion = budget.observeDataVersion(scanned.dataVersion(), dfu);
            if (dataVersion.acceptedValue() != DATA_VERSION
                    || dataVersion.dfuInvocations() != 0) {
                throw new IOException("R2Q positive witness invoked DFU");
            }
            requirePeak(kind, scanned);
            facts.put(kind, scanned);
        }
        return new Result(facts, budget.facts(), dfu.invocations());
    }

    private static void write(Path path, WitnessKind kind) throws IOException {
        P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.BEST_COMPRESSION,
                P4E0R2QProfile.locked().maximum(
                        P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE),
                P4E0R2QProfile.locked().maximum(
                        P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE),
                payload(kind));
    }

    static P4E0ResearchWireNbt.PayloadWriter payload(WitnessKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case HCA -> P4E0R2QPositiveWitnesses::writeHca;
            case DEPTH -> P4E0R2QPositiveWitnesses::writeDepth;
            case COMPOUND_CONTAINERS ->
                    P4E0R2QPositiveWitnesses::writeCompoundContainers;
            case FIELDS_AND_SCALARS ->
                    P4E0R2QPositiveWitnesses::writeFieldsAndScalars;
            case LIST -> P4E0R2QPositiveWitnesses::writeList;
            case INT_ARRAY -> P4E0R2QPositiveWitnesses::writeIntArray;
            case LONG_ARRAY -> P4E0R2QPositiveWitnesses::writeLongArray;
            case MODIFIED_UTF -> P4E0R2QPositiveWitnesses::writeModifiedUtf;
        };
    }

    private static void writeHca(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF("UUID");
        output.writeInt(4);
        output.writeInt(0x5034_4530);
        output.writeInt(0x5232_5100);
        output.writeInt(0x0000_0000);
        output.writeInt(0x0000_0001);
        output.writeByte(Tag.TAG_BYTE_ARRAY);
        output.writeUTF("research_payload");
        output.writeInt(268_435_384);
        writeRepeated(output, 268_435_384, 0x5a);
        output.writeByte(Tag.TAG_END);
    }

    private static void writeDepth(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        for (var level = 1; level < 512; level++) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("d");
        }
        for (var level = 0; level < 512; level++) {
            output.writeByte(Tag.TAG_END);
        }
    }

    private static void writeCompoundContainers(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_LIST);
        output.writeUTF("c");
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeInt(1_023);
        for (var index = 0; index < 1_023; index++) {
            output.writeByte(Tag.TAG_END);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeFieldsAndScalars(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        for (var index = 0; index < 65_536; index++) {
            output.writeByte(Tag.TAG_BYTE);
            output.writeUTF(String.format(Locale.ROOT, "f%05d", index));
            output.writeByte(index);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeList(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_LIST);
        output.writeUTF("l");
        output.writeByte(Tag.TAG_BYTE);
        output.writeInt(65_536);
        writeRepeated(output, 65_536, 0x5a);
        output.writeByte(Tag.TAG_END);
    }

    private static void writeIntArray(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF("i");
        output.writeInt(65_536);
        for (var index = 0; index < 65_536; index++) {
            output.writeInt(index);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeLongArray(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        output.writeByte(Tag.TAG_LONG_ARRAY);
        output.writeUTF("g");
        output.writeInt(65_536);
        for (var index = 0; index < 65_536; index++) {
            output.writeLong(0x5034_4530_5232_5100L ^ index);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeModifiedUtf(DataOutput output) throws IOException {
        start(output);
        writeDataVersion(output);
        var full = "u".repeat(65_535);
        var tail = "u".repeat(59_232);
        for (var index = 0; index < 1_024; index++) {
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF(String.format(Locale.ROOT, "u%05d", index));
            output.writeUTF(index == 1_023 ? tail : full);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void start(DataOutput output) throws IOException {
        P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
    }

    private static void writeDataVersion(DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_INT);
        output.writeUTF("DataVersion");
        output.writeInt(DATA_VERSION);
    }

    private static void writeRepeated(DataOutput output, int length, int value)
            throws IOException {
        var bytes = new byte[BUFFER_BYTES];
        java.util.Arrays.fill(bytes, (byte) value);
        var remaining = length;
        while (remaining > 0) {
            var count = Math.min(remaining, bytes.length);
            output.write(bytes, 0, count);
            remaining -= count;
        }
    }

    private static void requirePeak(
            WitnessKind kind, P4E0ResearchWireNbt.ScanFacts scan) throws IOException {
        var nbt = scan.nbt();
        var profile = P4E0R2QProfile.locked();
        var observed = switch (kind) {
            case HCA -> scan.decompressedBytes();
            case DEPTH -> nbt.maxContainerDepth();
            case COMPOUND_CONTAINERS -> nbt.compoundCount();
            case FIELDS_AND_SCALARS -> nbt.compoundEntryCount();
            case LIST -> nbt.listElementCount();
            case INT_ARRAY -> nbt.intArrayElements();
            case LONG_ARRAY -> nbt.longArrayElements();
            case MODIFIED_UTF -> nbt.modifiedUtf8Bytes();
        };
        var counter = switch (kind) {
            case HCA -> P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE;
            case DEPTH -> P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE;
            case COMPOUND_CONTAINERS ->
                    P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE;
            case FIELDS_AND_SCALARS ->
                    P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE;
            case LIST -> P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE;
            case INT_ARRAY -> P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE;
            case LONG_ARRAY -> P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE;
            case MODIFIED_UTF -> P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE;
        };
        if (observed != profile.maximum(counter)
                || (kind == WitnessKind.HCA
                        && nbt.byteArrayElements()
                                != profile.maximum(
                                        P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE))
                || (kind == WitnessKind.FIELDS_AND_SCALARS
                        && nbt.scalarTagCount()
                                != profile.maximum(
                                        P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE))
                || (kind == WitnessKind.LIST
                        && nbt.scalarTagCount()
                                != profile.maximum(
                                        P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE))) {
            throw new IOException("R2Q physical per-file peak changed: " + kind.slug);
        }
    }

    enum WitnessKind {
        HCA("hca-exact"),
        DEPTH("depth-exact"),
        COMPOUND_CONTAINERS("compound-containers-exact"),
        FIELDS_AND_SCALARS("fields-scalars-exact"),
        LIST("list-exact"),
        INT_ARRAY("int-array-exact"),
        LONG_ARRAY("long-array-exact"),
        MODIFIED_UTF("modified-utf-exact");

        private final String slug;

        WitnessKind(String slug) {
            this.slug = slug;
        }
    }

    record Result(
            Map<WitnessKind, P4E0ResearchWireNbt.ScanFacts> witnesses,
            P4E0R2QAuditBudget.Facts aggregate,
            int dfuInvocations) {
        Result {
            witnesses = Map.copyOf(Objects.requireNonNull(witnesses, "witnesses"));
            Objects.requireNonNull(aggregate, "aggregate");
            if (witnesses.size() != WitnessKind.values().length || dfuInvocations != 0) {
                throw new IllegalArgumentException("R2Q physical witness set is incomplete");
            }
        }
    }
}
