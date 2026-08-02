package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Strict bounded hand-off from the plain matrix to one dedicated Matrix-F child. */
final class P4E0ResearchCombinedProfileFile {
    private static final int SCHEMA_VERSION = 0;
    private static final int MAXIMUM_BYTES = 8_192;
    private static final Set<String> FIELDS = Set.of(
            "schema_version", "authority", "plan_hash", "run_index", "profile",
            "heap_mib", "directory", "directory_entries", "directory_shape",
            "selected_playerdata", "selected_fixture_id", "selected_fixture_shape",
            "selected_physical_bytes", "selected_decompressed_bytes",
            "selected_sha256", "compressed_guard_bytes", "decompressed_guard_bytes",
            "nbt_quota_bytes", "input_integrity_hash");

    private P4E0ResearchCombinedProfileFile() {
    }

    record Value(
            String planHash,
            int runIndex,
            P4E0ResearchCombinedEnvelope.Profile profile,
            String inputIntegrityHash) {
        Value {
            P4E0ResearchRunRecord.requireHash(planHash, "planHash");
            if (runIndex < 0) {
                throw new IllegalArgumentException("negative combined run index");
            }
            java.util.Objects.requireNonNull(profile, "profile");
            P4E0ResearchRunRecord.requireHash(
                    inputIntegrityHash, "inputIntegrityHash");
        }
    }

    static void writeNew(Path path, Value value) throws IOException {
        var profile = value.profile();
        var json = new JsonObject();
        json.addProperty("schema_version", SCHEMA_VERSION);
        json.addProperty("authority", P4E0ResearchMatrixPlan.AUTHORITY);
        json.addProperty("plan_hash", value.planHash());
        json.addProperty("run_index", value.runIndex());
        json.addProperty("profile", profile.kind().name());
        json.addProperty("heap_mib", profile.heapMiB());
        json.addProperty("directory", profile.directory().toString());
        json.addProperty("directory_entries", profile.directoryEntries());
        json.addProperty("directory_shape", profile.directoryShape());
        json.addProperty("selected_playerdata", profile.selectedPlayerdata().toString());
        json.addProperty("selected_fixture_id", profile.selectedFixtureId());
        json.addProperty("selected_fixture_shape", profile.selectedFixtureShape());
        json.addProperty("selected_physical_bytes", profile.selectedPhysicalBytes());
        json.addProperty("selected_decompressed_bytes", profile.selectedDecompressedBytes());
        json.addProperty("selected_sha256", profile.selectedPlayerdataSha256());
        json.addProperty("compressed_guard_bytes", profile.compressedGuardBytes());
        json.addProperty("decompressed_guard_bytes", profile.decompressedGuardBytes());
        json.addProperty("nbt_quota_bytes", profile.nbtQuotaBytes());
        json.addProperty("input_integrity_hash", value.inputIntegrityHash());
        var text = json.toString() + System.lineSeparator();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
            throw new IOException("combined profile hand-off exceeded its bound");
        }
        P4E0ResearchRunRecord.atomicCreate(path, text);
    }

    static Value read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) > MAXIMUM_BYTES) {
            throw new IOException("combined profile hand-off is unavailable");
        }
        var text = Files.readString(path, StandardCharsets.UTF_8);
        var lineSeparator = System.lineSeparator();
        if (!text.endsWith(lineSeparator)
                || text.length() == lineSeparator.length()
                || text.substring(0, text.length() - lineSeparator.length())
                        .endsWith(lineSeparator)) {
            throw new IOException("combined profile hand-off framing is not canonical");
        }
        text = text.substring(0, text.length() - lineSeparator.length());
        if (text.isEmpty()
                || text.charAt(0) != '{'
                || text.charAt(text.length() - 1) != '}'
                || text.indexOf('\r') >= 0
                || text.indexOf('\n') >= 0) {
            throw new IOException("combined profile hand-off framing is not canonical");
        }
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            var names = P4E0ResearchRunRecord.beginUniqueObject(reader);
            Integer schema = null;
            String authority = null;
            String planHash = null;
            Integer runIndex = null;
            P4E0ResearchCombinedEnvelope.ProfileKind profileKind = null;
            Integer heap = null;
            String directory = null;
            Integer directoryEntries = null;
            String directoryShape = null;
            String selected = null;
            String selectedFixtureId = null;
            String selectedShape = null;
            Long physical = null;
            Long decompressed = null;
            String selectedHash = null;
            Long compressedGuard = null;
            Long decompressedGuard = null;
            Long quota = null;
            String inputHash = null;
            while (reader.hasNext()) {
                var name = P4E0ResearchRunRecord.uniqueName(reader, names);
                if (!FIELDS.contains(name)) {
                    throw P4E0ResearchRunRecord.malformed();
                }
                switch (name) {
                    case "schema_version" -> schema = P4E0ResearchRunRecord.exactInt(reader);
                    case "authority" -> authority = P4E0ResearchRunRecord.exactString(reader);
                    case "plan_hash" -> planHash = P4E0ResearchRunRecord.exactString(reader);
                    case "run_index" -> runIndex = P4E0ResearchRunRecord.exactInt(reader);
                    case "profile" -> profileKind = P4E0ResearchRunRecord.enumValue(
                            P4E0ResearchCombinedEnvelope.ProfileKind.class,
                            P4E0ResearchRunRecord.exactString(reader));
                    case "heap_mib" -> heap = P4E0ResearchRunRecord.exactInt(reader);
                    case "directory" -> directory = P4E0ResearchRunRecord.exactString(reader);
                    case "directory_entries" -> directoryEntries =
                            P4E0ResearchRunRecord.exactInt(reader);
                    case "directory_shape" -> directoryShape =
                            P4E0ResearchRunRecord.exactString(reader);
                    case "selected_playerdata" -> selected =
                            P4E0ResearchRunRecord.exactString(reader);
                    case "selected_fixture_id" -> selectedFixtureId =
                            P4E0ResearchRunRecord.exactString(reader);
                    case "selected_fixture_shape" -> selectedShape =
                            P4E0ResearchRunRecord.exactString(reader);
                    case "selected_physical_bytes" -> physical =
                            P4E0ResearchRunRecord.exactLong(reader);
                    case "selected_decompressed_bytes" -> decompressed =
                            P4E0ResearchRunRecord.exactLong(reader);
                    case "selected_sha256" -> selectedHash =
                            P4E0ResearchRunRecord.exactString(reader);
                    case "compressed_guard_bytes" -> compressedGuard =
                            P4E0ResearchRunRecord.exactLong(reader);
                    case "decompressed_guard_bytes" -> decompressedGuard =
                            P4E0ResearchRunRecord.exactLong(reader);
                    case "nbt_quota_bytes" -> quota =
                            P4E0ResearchRunRecord.exactLong(reader);
                    case "input_integrity_hash" -> inputHash =
                            P4E0ResearchRunRecord.exactString(reader);
                    default -> throw P4E0ResearchRunRecord.malformed();
                }
            }
            reader.endObject();
            if (!names.equals(FIELDS) || reader.peek() != JsonToken.END_DOCUMENT
                    || schema == null || schema != SCHEMA_VERSION
                    || !P4E0ResearchMatrixPlan.AUTHORITY.equals(authority)
                    || planHash == null || runIndex == null || profileKind == null
                    || heap == null || directory == null || directoryEntries == null
                    || directoryShape == null || selected == null
                    || selectedFixtureId == null || selectedShape == null
                    || physical == null || decompressed == null || selectedHash == null
                    || compressedGuard == null || decompressedGuard == null
                    || quota == null || inputHash == null) {
                throw P4E0ResearchRunRecord.malformed();
            }
            return new Value(
                    planHash,
                    runIndex,
                    new P4E0ResearchCombinedEnvelope.Profile(
                            profileKind,
                            heap,
                            Path.of(directory),
                            directoryEntries,
                            directoryShape,
                            Path.of(selected),
                            selectedFixtureId,
                            selectedShape,
                            physical,
                            decompressed,
                            selectedHash,
                            compressedGuard,
                            decompressedGuard,
                            quota),
                    inputHash);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw P4E0ResearchRunRecord.malformed(exception);
        }
    }
}
