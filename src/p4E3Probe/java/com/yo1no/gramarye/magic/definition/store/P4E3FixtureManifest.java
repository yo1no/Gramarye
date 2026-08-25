package com.yo1no.gramarye.magic.definition.store;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Strict bounded manifest for the deterministic P4-E3 first/restart world. */
record P4E3FixtureManifest(
        Vector vector,
        int storeHistories,
        int storeRevisions,
        int journalEntries,
        int journalBytes,
        String primarySha256,
        long primaryBytes,
        long primaryModifiedMillis,
        String playerdataInventorySha256) {
    static final String FILE_NAME = "fixture.json";
    static final int DATA_VERSION = 3955;
    static final long MAX_REPORT_BYTES = 65_536L;
    private static final int SCHEMA_VERSION = 0;
    private static final Set<String> FIELDS = Set.of(
            "schema_version", "data_version",
            "directory_entries", "relevant_records",
            "compressed_bytes_per_file", "decompressed_bytes_per_file",
            "container_depth_per_file", "compound_containers_per_file",
            "compound_field_entries_per_file", "list_elements_per_file",
            "byte_array_elements_per_file", "int_array_elements_per_file",
            "long_array_elements_per_file", "modified_utf8_bytes_per_file",
            "scalar_tags_per_file", "compressed_bytes_total",
            "decompressed_bytes_total", "compound_containers_total",
            "compound_field_entries_total", "list_elements_total",
            "byte_array_elements_total", "int_array_elements_total",
            "long_array_elements_total", "modified_utf8_bytes_total",
            "scalar_tags_total", "attachment_admissions", "raw_root_claims",
            "store_histories", "store_revisions", "journal_entries", "journal_bytes",
            "primary_sha256", "primary_bytes", "primary_modified_millis",
            "playerdata_inventory_sha256");

    P4E3FixtureManifest {
        Objects.requireNonNull(vector, "vector").requireLocked();
        requireSha256(primarySha256, "primarySha256");
        requireSha256(playerdataInventorySha256, "playerdataInventorySha256");
        if (storeHistories != 2_049 || storeRevisions != 4_096
                || journalEntries != 4_096 || journalBytes != 1_048_538
                || primaryBytes <= 0L || primaryModifiedMillis < 0L) {
            throw new IllegalArgumentException("P4-E3 fixture Store/disk facts changed");
        }
    }

    void write(Path reportRoot) throws IOException {
        var json = new JsonObject();
        json.addProperty("schema_version", SCHEMA_VERSION);
        json.addProperty("data_version", DATA_VERSION);
        vector.addTo(json);
        json.addProperty("store_histories", storeHistories);
        json.addProperty("store_revisions", storeRevisions);
        json.addProperty("journal_entries", journalEntries);
        json.addProperty("journal_bytes", journalBytes);
        json.addProperty("primary_sha256", primarySha256);
        json.addProperty("primary_bytes", primaryBytes);
        json.addProperty("primary_modified_millis", primaryModifiedMillis);
        json.addProperty("playerdata_inventory_sha256", playerdataInventorySha256);
        writeBounded(reportRoot.resolve(FILE_NAME), json);
    }

    static P4E3FixtureManifest read(Path reportRoot) throws IOException {
        var json = readBounded(reportRoot.resolve(FILE_NAME));
        if (!json.keySet().equals(FIELDS)
                || exactInt(json, "schema_version") != SCHEMA_VERSION
                || exactInt(json, "data_version") != DATA_VERSION) {
            throw new IOException("P4-E3 fixture manifest fields changed");
        }
        return new P4E3FixtureManifest(
                Vector.from(json),
                exactInt(json, "store_histories"),
                exactInt(json, "store_revisions"),
                exactInt(json, "journal_entries"),
                exactInt(json, "journal_bytes"),
                exactString(json, "primary_sha256"),
                exactLong(json, "primary_bytes"),
                exactLong(json, "primary_modified_millis"),
                exactString(json, "playerdata_inventory_sha256"));
    }

    static void writeBounded(Path path, JsonObject json) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(json, "json");
        var bytes = (json + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_REPORT_BYTES) {
            throw new IOException("P4-E3 report exceeds its bound");
        }
        Files.createDirectories(Objects.requireNonNull(path.getParent(), "report parent"));
        try (var channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            var source = ByteBuffer.wrap(bytes);
            while (source.hasRemaining()) {
                channel.write(source);
            }
            channel.force(true);
        }
    }

    static JsonObject readBounded(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) <= 0L || Files.size(path) > MAX_REPORT_BYTES) {
            throw new IOException("P4-E3 bounded report is absent or invalid");
        }
        try {
            var parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                throw new IOException("P4-E3 report root is not an object");
            }
            return parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("P4-E3 report JSON is malformed", exception);
        }
    }

    static int exactInt(JsonObject json, String field) throws IOException {
        var value = exactLong(json, field);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IOException("P4-E3 report integer is out of range: " + field);
        }
        return (int) value;
    }

    static long exactLong(JsonObject json, String field) throws IOException {
        try {
            var value = json.get(field);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                throw new IOException("P4-E3 report number is absent: " + field);
            }
            var text = value.getAsString();
            var parsed = Long.parseLong(text);
            if (!Long.toString(parsed).equals(text)) {
                throw new IOException("P4-E3 report number is not canonical: " + field);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IOException("P4-E3 report number is malformed: " + field, exception);
        }
    }

    static String exactString(JsonObject json, String field) throws IOException {
        var value = json.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("P4-E3 report string is absent: " + field);
        }
        var text = value.getAsString();
        if (text.isEmpty() || text.length() > 256) {
            throw new IOException("P4-E3 report string is unbounded: " + field);
        }
        return text;
    }

    static String sha256(Path path) throws IOException {
        var digest = sha256Digest();
        try (var input = Files.newInputStream(path)) {
            var buffer = new byte[16_384];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(String value) {
        var digest = sha256Digest();
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSha256(String value, String name) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " is not SHA-256");
        }
    }

    /** Exact vector observed at both startup traversals. */
    record Vector(
            long directoryEntries,
            long relevantRecords,
            long compressedBytesPerFile,
            long decompressedBytesPerFile,
            long containerDepthPerFile,
            long compoundContainersPerFile,
            long compoundFieldEntriesPerFile,
            long listElementsPerFile,
            long byteArrayElementsPerFile,
            long intArrayElementsPerFile,
            long longArrayElementsPerFile,
            long modifiedUtf8BytesPerFile,
            long scalarTagsPerFile,
            long compressedBytesTotal,
            long decompressedBytesTotal,
            long compoundContainersTotal,
            long compoundFieldEntriesTotal,
            long listElementsTotal,
            long byteArrayElementsTotal,
            long intArrayElementsTotal,
            long longArrayElementsTotal,
            long modifiedUtf8BytesTotal,
            long scalarTagsTotal,
            long attachmentAdmissions,
            long rawRootClaims) {
        static Vector locked() {
            return new Vector(
                    4_096, 2_048, 33_559_514, 268_435_456, 512,
                    1_024, 65_537, 65_536, 268_435_384, 65_536, 65_536,
                    67_107_692, 65_537, 268_440_533, 536_870_912,
                    131_072, 524_288, 131_072, 456_524_705, 131_072,
                    131_072, 75_497_472, 458_752, 1_024, 65_536);
        }

        private void requireLocked() {
            if (!equals(locked())) {
                throw new IllegalArgumentException("P4-E3 25-counter vector changed");
            }
        }

        private void addTo(JsonObject json) {
            json.addProperty("directory_entries", directoryEntries);
            json.addProperty("relevant_records", relevantRecords);
            json.addProperty("compressed_bytes_per_file", compressedBytesPerFile);
            json.addProperty("decompressed_bytes_per_file", decompressedBytesPerFile);
            json.addProperty("container_depth_per_file", containerDepthPerFile);
            json.addProperty("compound_containers_per_file", compoundContainersPerFile);
            json.addProperty("compound_field_entries_per_file", compoundFieldEntriesPerFile);
            json.addProperty("list_elements_per_file", listElementsPerFile);
            json.addProperty("byte_array_elements_per_file", byteArrayElementsPerFile);
            json.addProperty("int_array_elements_per_file", intArrayElementsPerFile);
            json.addProperty("long_array_elements_per_file", longArrayElementsPerFile);
            json.addProperty("modified_utf8_bytes_per_file", modifiedUtf8BytesPerFile);
            json.addProperty("scalar_tags_per_file", scalarTagsPerFile);
            json.addProperty("compressed_bytes_total", compressedBytesTotal);
            json.addProperty("decompressed_bytes_total", decompressedBytesTotal);
            json.addProperty("compound_containers_total", compoundContainersTotal);
            json.addProperty("compound_field_entries_total", compoundFieldEntriesTotal);
            json.addProperty("list_elements_total", listElementsTotal);
            json.addProperty("byte_array_elements_total", byteArrayElementsTotal);
            json.addProperty("int_array_elements_total", intArrayElementsTotal);
            json.addProperty("long_array_elements_total", longArrayElementsTotal);
            json.addProperty("modified_utf8_bytes_total", modifiedUtf8BytesTotal);
            json.addProperty("scalar_tags_total", scalarTagsTotal);
            json.addProperty("attachment_admissions", attachmentAdmissions);
            json.addProperty("raw_root_claims", rawRootClaims);
        }

        private static Vector from(JsonObject json) throws IOException {
            return new Vector(
                    exactLong(json, "directory_entries"),
                    exactLong(json, "relevant_records"),
                    exactLong(json, "compressed_bytes_per_file"),
                    exactLong(json, "decompressed_bytes_per_file"),
                    exactLong(json, "container_depth_per_file"),
                    exactLong(json, "compound_containers_per_file"),
                    exactLong(json, "compound_field_entries_per_file"),
                    exactLong(json, "list_elements_per_file"),
                    exactLong(json, "byte_array_elements_per_file"),
                    exactLong(json, "int_array_elements_per_file"),
                    exactLong(json, "long_array_elements_per_file"),
                    exactLong(json, "modified_utf8_bytes_per_file"),
                    exactLong(json, "scalar_tags_per_file"),
                    exactLong(json, "compressed_bytes_total"),
                    exactLong(json, "decompressed_bytes_total"),
                    exactLong(json, "compound_containers_total"),
                    exactLong(json, "compound_field_entries_total"),
                    exactLong(json, "list_elements_total"),
                    exactLong(json, "byte_array_elements_total"),
                    exactLong(json, "int_array_elements_total"),
                    exactLong(json, "long_array_elements_total"),
                    exactLong(json, "modified_utf8_bytes_total"),
                    exactLong(json, "scalar_tags_total"),
                    exactLong(json, "attachment_admissions"),
                    exactLong(json, "raw_root_claims"));
        }
    }
}
