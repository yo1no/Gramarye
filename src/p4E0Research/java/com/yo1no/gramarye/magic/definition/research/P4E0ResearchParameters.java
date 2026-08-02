package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

/** Immutable, non-authoritative inputs for one research child. */
record P4E0ResearchParameters(
        P4E0ResearchScenario scenario,
        int heapMiB,
        int directoryEntries,
        int relevantRecords,
        long compressedTargetBytes,
        long decompressedTargetBytes,
        int targetDepth,
        int targetCompoundEntries,
        int targetListElements,
        int targetArrayElements,
        int rootClaims,
        double readyRecordRatio,
        int preservedRawRecordCount,
        long seed,
        Path outputDirectory,
        long compressedGuardBytes,
        long decompressedGuardBytes,
        long nbtQuotaBytes) {

    P4E0ResearchParameters {
        Objects.requireNonNull(scenario, "scenario");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (heapMiB <= 0 || directoryEntries < 0 || relevantRecords < 0
                || compressedTargetBytes < 0 || decompressedTargetBytes < 0
                || targetDepth < 1 || targetCompoundEntries < 0
                || targetListElements < 0 || targetArrayElements < 0
                || rootClaims < 0 || preservedRawRecordCount < 0
                || readyRecordRatio < 0.0 || readyRecordRatio > 1.0
                || !Double.isFinite(readyRecordRatio)
                || compressedGuardBytes <= 0 || decompressedGuardBytes <= 0
                || nbtQuotaBytes <= 0) {
            throw new IllegalArgumentException("invalid P4-E0 research parameters");
        }
    }

    static P4E0ResearchParameters smoke(Path outputDirectory) {
        try (var stream = P4E0ResearchParameters.class.getResourceAsStream(
                "/p4-e0-research-smoke-v0.json")) {
            if (stream == null) {
                throw new IllegalStateException("research smoke resource is missing");
            }
            var root = JsonParser.parseReader(new InputStreamReader(
                    stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema_version").getAsInt() != 0
                    || !"NON_AUTHORITATIVE_RESEARCH_SMOKE".equals(
                            root.get("authority").getAsString())) {
                throw new IllegalStateException("research smoke resource policy changed");
            }
            return fromJson(root, outputDirectory).withSystemPropertyOverrides();
        } catch (IOException exception) {
            throw new IllegalStateException("research smoke resource could not be closed", exception);
        }
    }

    private static P4E0ResearchParameters fromJson(
            JsonObject root, Path outputDirectory) {
        return new P4E0ResearchParameters(
                P4E0ResearchScenario.valueOf(root.get("scenario").getAsString()),
                root.get("heap_mib").getAsInt(),
                root.get("directory_entries").getAsInt(),
                root.get("relevant_records").getAsInt(),
                root.get("compressed_target_bytes").getAsLong(),
                root.get("decompressed_target_bytes").getAsLong(),
                root.get("target_depth").getAsInt(),
                root.get("target_compound_entries").getAsInt(),
                root.get("target_list_elements").getAsInt(),
                root.get("target_array_elements").getAsInt(),
                root.get("root_claims").getAsInt(),
                root.get("ready_record_ratio").getAsDouble(),
                root.get("preserved_raw_record_count").getAsInt(),
                root.get("seed").getAsLong(),
                outputDirectory.toAbsolutePath().normalize(),
                root.get("compressed_guard_bytes").getAsLong(),
                root.get("decompressed_guard_bytes").getAsLong(),
                root.get("nbt_quota_bytes").getAsLong());
    }

    /**
     * Allows later research runs to vary every workload coordinate without changing source or
     * turning a measured value into authority. The CLI path remains the sole output-directory
     * owner so an override cannot escape the dedicated build tree.
     */
    private P4E0ResearchParameters withSystemPropertyOverrides() {
        var prefix = "gramarye.p4e0.research.";
        return new P4E0ResearchParameters(
                enumProperty(prefix + "scenario", scenario, P4E0ResearchScenario.class),
                intProperty(prefix + "heapMiB", heapMiB),
                intProperty(prefix + "directoryEntries", directoryEntries),
                intProperty(prefix + "relevantRecords", relevantRecords),
                longProperty(prefix + "compressedTargetBytes", compressedTargetBytes),
                longProperty(prefix + "decompressedTargetBytes", decompressedTargetBytes),
                intProperty(prefix + "targetDepth", targetDepth),
                intProperty(prefix + "targetCompoundEntries", targetCompoundEntries),
                intProperty(prefix + "targetListElements", targetListElements),
                intProperty(prefix + "targetArrayElements", targetArrayElements),
                intProperty(prefix + "rootClaims", rootClaims),
                doubleProperty(prefix + "readyRecordRatio", readyRecordRatio),
                intProperty(prefix + "preservedRawRecordCount", preservedRawRecordCount),
                longProperty(prefix + "seed", seed),
                outputDirectory,
                longProperty(prefix + "compressedGuardBytes", compressedGuardBytes),
                longProperty(prefix + "decompressedGuardBytes", decompressedGuardBytes),
                longProperty(prefix + "nbtQuotaBytes", nbtQuotaBytes));
    }

    private static int intProperty(String name, int fallback) {
        var value = System.getProperty(name);
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static long longProperty(String name, long fallback) {
        var value = System.getProperty(name);
        return value == null ? fallback : Long.parseLong(value);
    }

    private static double doubleProperty(String name, double fallback) {
        var value = System.getProperty(name);
        return value == null ? fallback : Double.parseDouble(value);
    }

    private static <E extends Enum<E>> E enumProperty(
            String name, E fallback, Class<E> type) {
        var value = System.getProperty(name);
        return value == null ? fallback : Enum.valueOf(type, value);
    }

    JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("scenario", scenario.name());
        json.addProperty("heap_mib", heapMiB);
        json.addProperty("directory_entries", directoryEntries);
        json.addProperty("relevant_records", relevantRecords);
        json.addProperty("compressed_target_bytes", compressedTargetBytes);
        json.addProperty("decompressed_target_bytes", decompressedTargetBytes);
        json.addProperty("target_depth", targetDepth);
        json.addProperty("target_compound_entries", targetCompoundEntries);
        json.addProperty("target_list_elements", targetListElements);
        json.addProperty("target_array_elements", targetArrayElements);
        json.addProperty("root_claims", rootClaims);
        json.addProperty("ready_record_ratio", readyRecordRatio);
        json.addProperty("preserved_raw_record_count", preservedRawRecordCount);
        json.addProperty("seed", seed);
        json.addProperty("output_directory", outputDirectory.toString());
        json.addProperty("compressed_guard_bytes", compressedGuardBytes);
        json.addProperty("decompressed_guard_bytes", decompressedGuardBytes);
        json.addProperty("nbt_quota_bytes", nbtQuotaBytes);
        return json;
    }
}
