package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Immutable, research-only lock for the maintainer-approved P4-E0-R2Q candidate. */
record P4E0R2QProfile(
        CounterValues candidateValues,
        int acceptedDataVersion,
        int maxDfuRecords,
        int qualificationHeapMiB,
        OverrunPolicy overrunPolicy,
        long researchDiskBudgetBytes) {
    static final int SCHEMA_VERSION = 0;
    static final String PROFILE_NAME = "BALANCED_V0_1536_QUALIFICATION";
    static final String AUTHORITY = "EXPLORATORY_NON_NORMATIVE";
    static final String RESOURCE_NAME = "/p4-e0-r2q-profile-v0.json";
    static final int COUNTER_COUNT = 25;
    private static final int MAXIMUM_MANIFEST_BYTES = 65_536;
    private static final long TWELVE_GIBIBYTES = 12_884_901_888L;

    P4E0R2QProfile {
        Objects.requireNonNull(candidateValues, "candidateValues");
        Objects.requireNonNull(overrunPolicy, "overrunPolicy");
        requireRelationships(candidateValues);
        if (!candidateValues.equals(approvedCandidateValues())
                || acceptedDataVersion != 3_955
                || maxDfuRecords != 0
                || qualificationHeapMiB != 1_536
                || overrunPolicy != OverrunPolicy.INCOMPLETE_AND_CONTINUE
                || researchDiskBudgetBytes != TWELVE_GIBIBYTES) {
            throw new IllegalArgumentException("P4-E0-R2Q locked profile tuple changed");
        }
    }

    static P4E0R2QProfile locked() {
        return Holder.PROFILE;
    }

    long maximum(Counter counter) {
        return candidateValues.value(counter);
    }

    static String manifestText() {
        return Holder.MANIFEST_TEXT;
    }

    static String manifestHash() {
        return Holder.MANIFEST_HASH;
    }

    static JsonObject manifestJson() {
        return Holder.MANIFEST.deepCopy();
    }

    enum OverrunPolicy {
        INCOMPLETE_AND_CONTINUE
    }

    enum Counter {
        DIRECTORY_ENTRIES("directory_entries"),
        RELEVANT_RECORDS("relevant_records"),
        COMPRESSED_BYTES_PER_FILE("compressed_bytes_per_file"),
        DECOMPRESSED_BYTES_PER_FILE("decompressed_bytes_per_file"),
        CONTAINER_DEPTH_PER_FILE("container_depth_per_file"),
        COMPOUND_CONTAINERS_PER_FILE("compound_containers_per_file"),
        COMPOUND_FIELD_ENTRIES_PER_FILE("compound_field_entries_per_file"),
        LIST_ELEMENTS_PER_FILE("list_elements_per_file"),
        BYTE_ARRAY_ELEMENTS_PER_FILE("byte_array_elements_per_file"),
        INT_ARRAY_ELEMENTS_PER_FILE("int_array_elements_per_file"),
        LONG_ARRAY_ELEMENTS_PER_FILE("long_array_elements_per_file"),
        MODIFIED_UTF8_BYTES_PER_FILE("modified_utf8_bytes_per_file"),
        SCALAR_TAGS_PER_FILE("scalar_tags_per_file"),
        COMPRESSED_BYTES_TOTAL("compressed_bytes_total"),
        DECOMPRESSED_BYTES_TOTAL("decompressed_bytes_total"),
        COMPOUND_CONTAINERS_TOTAL("compound_containers_total"),
        COMPOUND_FIELD_ENTRIES_TOTAL("compound_field_entries_total"),
        LIST_ELEMENTS_TOTAL("list_elements_total"),
        BYTE_ARRAY_ELEMENTS_TOTAL("byte_array_elements_total"),
        INT_ARRAY_ELEMENTS_TOTAL("int_array_elements_total"),
        LONG_ARRAY_ELEMENTS_TOTAL("long_array_elements_total"),
        MODIFIED_UTF8_BYTES_TOTAL("modified_utf8_bytes_total"),
        SCALAR_TAGS_TOTAL("scalar_tags_total"),
        ATTACHMENT_ADMISSIONS("attachment_admissions"),
        RAW_ROOT_CLAIMS("raw_root_claims");

        private final String slug;

        Counter(String slug) {
            this.slug = slug;
        }

        String slug() {
            return slug;
        }

        static Counter fromSlug(String slug) {
            for (var counter : values()) {
                if (counter.slug.equals(slug)) {
                    return counter;
                }
            }
            throw new IllegalArgumentException("unknown R2Q counter slug");
        }
    }

    /** All 25 candidate coordinates remain named fields; this is not a string-keyed truth map. */
    record CounterValues(
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
        CounterValues {
            var values = new long[] {
                directoryEntries,
                relevantRecords,
                compressedBytesPerFile,
                decompressedBytesPerFile,
                containerDepthPerFile,
                compoundContainersPerFile,
                compoundFieldEntriesPerFile,
                listElementsPerFile,
                byteArrayElementsPerFile,
                intArrayElementsPerFile,
                longArrayElementsPerFile,
                modifiedUtf8BytesPerFile,
                scalarTagsPerFile,
                compressedBytesTotal,
                decompressedBytesTotal,
                compoundContainersTotal,
                compoundFieldEntriesTotal,
                listElementsTotal,
                byteArrayElementsTotal,
                intArrayElementsTotal,
                longArrayElementsTotal,
                modifiedUtf8BytesTotal,
                scalarTagsTotal,
                attachmentAdmissions,
                rawRootClaims
            };
            for (var value : values) {
                if (value < 0) {
                    throw new IllegalArgumentException("negative R2Q counter");
                }
            }
        }

        long value(Counter counter) {
            Objects.requireNonNull(counter, "counter");
            return switch (counter) {
                case DIRECTORY_ENTRIES -> directoryEntries;
                case RELEVANT_RECORDS -> relevantRecords;
                case COMPRESSED_BYTES_PER_FILE -> compressedBytesPerFile;
                case DECOMPRESSED_BYTES_PER_FILE -> decompressedBytesPerFile;
                case CONTAINER_DEPTH_PER_FILE -> containerDepthPerFile;
                case COMPOUND_CONTAINERS_PER_FILE -> compoundContainersPerFile;
                case COMPOUND_FIELD_ENTRIES_PER_FILE -> compoundFieldEntriesPerFile;
                case LIST_ELEMENTS_PER_FILE -> listElementsPerFile;
                case BYTE_ARRAY_ELEMENTS_PER_FILE -> byteArrayElementsPerFile;
                case INT_ARRAY_ELEMENTS_PER_FILE -> intArrayElementsPerFile;
                case LONG_ARRAY_ELEMENTS_PER_FILE -> longArrayElementsPerFile;
                case MODIFIED_UTF8_BYTES_PER_FILE -> modifiedUtf8BytesPerFile;
                case SCALAR_TAGS_PER_FILE -> scalarTagsPerFile;
                case COMPRESSED_BYTES_TOTAL -> compressedBytesTotal;
                case DECOMPRESSED_BYTES_TOTAL -> decompressedBytesTotal;
                case COMPOUND_CONTAINERS_TOTAL -> compoundContainersTotal;
                case COMPOUND_FIELD_ENTRIES_TOTAL -> compoundFieldEntriesTotal;
                case LIST_ELEMENTS_TOTAL -> listElementsTotal;
                case BYTE_ARRAY_ELEMENTS_TOTAL -> byteArrayElementsTotal;
                case INT_ARRAY_ELEMENTS_TOTAL -> intArrayElementsTotal;
                case LONG_ARRAY_ELEMENTS_TOTAL -> longArrayElementsTotal;
                case MODIFIED_UTF8_BYTES_TOTAL -> modifiedUtf8BytesTotal;
                case SCALAR_TAGS_TOTAL -> scalarTagsTotal;
                case ATTACHMENT_ADMISSIONS -> attachmentAdmissions;
                case RAW_ROOT_CLAIMS -> rawRootClaims;
            };
        }

        CounterValues with(Counter counter, long value) {
            var values = toArray();
            values[counter.ordinal()] = value;
            return fromArray(values);
        }

        private long[] toArray() {
            var values = new long[COUNTER_COUNT];
            for (var counter : Counter.values()) {
                values[counter.ordinal()] = value(counter);
            }
            return values;
        }

        private static CounterValues fromArray(long[] values) {
            if (values.length != COUNTER_COUNT) {
                throw new IllegalArgumentException("wrong R2Q counter vector length");
            }
            return new CounterValues(
                    values[0], values[1], values[2], values[3], values[4],
                    values[5], values[6], values[7], values[8], values[9],
                    values[10], values[11], values[12], values[13], values[14],
                    values[15], values[16], values[17], values[18], values[19],
                    values[20], values[21], values[22], values[23], values[24]);
        }
    }

    private static CounterValues approvedCandidateValues() {
        return new CounterValues(
                4_096L,
                2_048L,
                33_559_514L,
                268_435_456L,
                512L,
                1_024L,
                65_537L,
                65_536L,
                268_435_384L,
                65_536L,
                65_536L,
                67_107_692L,
                65_537L,
                268_440_533L,
                536_870_912L,
                131_072L,
                524_288L,
                131_072L,
                456_524_705L,
                131_072L,
                131_072L,
                75_497_472L,
                458_752L,
                1_024L,
                65_536L);
    }

    private static void requireRelationships(CounterValues values) {
        if (values.attachmentAdmissions() > values.relevantRecords()
                || values.relevantRecords() > values.directoryEntries()
                || values.rawRootClaims()
                        != MagicSafetyCeilings.MAX_RETENTION_ROOTS_PER_RECLAIM
                || values.containerDepthPerFile() != 512L) {
            throw new IllegalArgumentException("invalid R2Q profile relationship");
        }
        requireAtMost(values.compressedBytesPerFile(), values.compressedBytesTotal());
        requireAtMost(values.decompressedBytesPerFile(), values.decompressedBytesTotal());
        requireAtMost(values.compoundContainersPerFile(), values.compoundContainersTotal());
        requireAtMost(
                values.compoundFieldEntriesPerFile(), values.compoundFieldEntriesTotal());
        requireAtMost(values.listElementsPerFile(), values.listElementsTotal());
        requireAtMost(values.byteArrayElementsPerFile(), values.byteArrayElementsTotal());
        requireAtMost(values.intArrayElementsPerFile(), values.intArrayElementsTotal());
        requireAtMost(values.longArrayElementsPerFile(), values.longArrayElementsTotal());
        requireAtMost(values.modifiedUtf8BytesPerFile(), values.modifiedUtf8BytesTotal());
        requireAtMost(values.scalarTagsPerFile(), values.scalarTagsTotal());
    }

    private static void requireAtMost(long perFile, long aggregate) {
        if (perFile > aggregate) {
            throw new IllegalArgumentException("R2Q per-file coordinate exceeds aggregate");
        }
    }

    private static String readManifestText() {
        try (var input = P4E0R2QProfile.class.getResourceAsStream(RESOURCE_NAME)) {
            if (input == null) {
                throw new IllegalStateException("R2Q profile manifest resource is missing");
            }
            var bytes = new ByteArrayOutputStream();
            var buffer = new byte[8_192];
            for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                if (read == 0) {
                    continue;
                }
                if (bytes.size() + read > MAXIMUM_MANIFEST_BYTES) {
                    throw new IllegalStateException("R2Q profile manifest is unbounded");
                }
                bytes.write(buffer, 0, read);
            }
            var text = bytes.toString(StandardCharsets.UTF_8);
            if (!text.endsWith("\n") || text.indexOf('\r') >= 0
                    || text.startsWith("\ufeff")) {
                throw new IllegalStateException("R2Q profile manifest framing changed");
            }
            return text;
        } catch (IOException exception) {
            throw new IllegalStateException("R2Q profile manifest could not be read", exception);
        }
    }

    private static JsonObject validateManifest(String text, P4E0R2QProfile profile) {
        var root = JsonParser.parseString(text).getAsJsonObject();
        var expectedRootFields = Set.of(
                "schema_version", "authority", "profile_name", "candidate_values",
                "counter_coordinates", "dependency_edges", "failure_precedence",
                "heap", "disk_budget", "overrun_policy");
        if (!root.keySet().equals(expectedRootFields)
                || root.get("schema_version").getAsInt() != SCHEMA_VERSION
                || !AUTHORITY.equals(root.get("authority").getAsString())
                || !PROFILE_NAME.equals(root.get("profile_name").getAsString())
                || !profile.overrunPolicy().name().equals(
                        root.get("overrun_policy").getAsString())) {
            throw new IllegalStateException("R2Q profile manifest header changed");
        }
        var candidates = root.getAsJsonObject("candidate_values");
        var expectedCandidateNames = EnumSet.allOf(Counter.class).stream()
                .map(Counter::slug)
                .collect(java.util.stream.Collectors.toSet());
        var candidateNames = candidates.keySet().stream()
                .filter(name -> !name.equals("accepted_data_version")
                        && !name.equals("max_dfu_records"))
                .collect(java.util.stream.Collectors.toSet());
        if (!candidateNames.equals(expectedCandidateNames)
                || candidates.size() != COUNTER_COUNT + 2
                || candidates.get("accepted_data_version").getAsInt()
                        != profile.acceptedDataVersion()
                || candidates.get("max_dfu_records").getAsInt()
                        != profile.maxDfuRecords()) {
            throw new IllegalStateException("R2Q manifest candidate set changed");
        }
        for (var counter : Counter.values()) {
            if (candidates.get(counter.slug()).getAsLong() != profile.maximum(counter)) {
                throw new IllegalStateException("R2Q manifest candidate value changed");
            }
        }
        var coordinates = root.getAsJsonObject("counter_coordinates");
        if (!coordinates.keySet().equals(expectedCandidateNames)) {
            throw new IllegalStateException("R2Q counter coordinate set changed");
        }
        for (var coordinate : coordinates.entrySet()) {
            if (!coordinate.getValue().isJsonObject()) {
                throw new IllegalStateException("R2Q counter coordinate is incomplete");
            }
            var policy = coordinate.getValue().getAsJsonObject();
            if (!policy.keySet().equals(Set.of("measure", "checkpoint"))
                    || policy.get("measure").getAsString().isBlank()
                    || policy.get("checkpoint").getAsString().isBlank()
                    || policy.get("measure").getAsString().length() > 240
                    || policy.get("checkpoint").getAsString().length() > 240) {
                throw new IllegalStateException("R2Q counter coordinate policy changed");
            }
        }
        var expectedEdges = Set.of(
                "attachment_admissions>relevant_records",
                "relevant_records>directory_entries",
                "compressed_bytes_per_file>compressed_bytes_total",
                "decompressed_bytes_per_file>decompressed_bytes_total",
                "compound_containers_per_file>compound_containers_total",
                "compound_field_entries_per_file>compound_field_entries_total",
                "list_elements_per_file>list_elements_total",
                "byte_array_elements_per_file>byte_array_elements_total",
                "int_array_elements_per_file>int_array_elements_total",
                "long_array_elements_per_file>long_array_elements_total",
                "modified_utf8_bytes_per_file>modified_utf8_bytes_total",
                "scalar_tags_per_file>scalar_tags_total");
        var observedEdges = new java.util.HashSet<String>();
        for (var value : root.getAsJsonArray("dependency_edges")) {
            if (!value.isJsonObject()
                    || !value.getAsJsonObject().keySet().equals(Set.of("lower", "upper"))) {
                throw new IllegalStateException("R2Q dependency edge shape changed");
            }
            var edge = value.getAsJsonObject();
            if (!observedEdges.add(
                    edge.get("lower").getAsString() + '>'
                            + edge.get("upper").getAsString())) {
                throw new IllegalStateException("R2Q dependency edge duplicated");
            }
        }
        var heap = root.getAsJsonObject("heap");
        var disk = root.getAsJsonObject("disk_budget");
        var precedence = new java.util.ArrayList<String>();
        for (var value : root.getAsJsonArray("failure_precedence")) {
            precedence.add(value.getAsString());
        }
        var expectedPrecedence = java.util.Arrays.stream(
                        P4E0R2QCasePlan.FailureStage.values())
                .map(P4E0R2QCasePlan.FailureStage::slug)
                .toList();
        if (!heap.keySet().equals(Set.of("qualification_mib"))
                || heap.get("qualification_mib").getAsInt()
                        != profile.qualificationHeapMiB()
                || !disk.keySet().equals(Set.of("bytes"))
                || disk.get("bytes").getAsLong() != profile.researchDiskBudgetBytes()
                || !observedEdges.equals(expectedEdges)
                || !precedence.equals(expectedPrecedence)) {
            throw new IllegalStateException("R2Q manifest execution policy changed");
        }
        return root;
    }

    private static final class Holder {
        private static final P4E0R2QProfile PROFILE = new P4E0R2QProfile(
                approvedCandidateValues(),
                3_955,
                0,
                1_536,
                OverrunPolicy.INCOMPLETE_AND_CONTINUE,
                TWELVE_GIBIBYTES);
        private static final String MANIFEST_TEXT = readManifestText();
        private static final JsonObject MANIFEST = validateManifest(MANIFEST_TEXT, PROFILE);
        private static final String MANIFEST_HASH =
                P4E0ResearchHashing.sha256(MANIFEST_TEXT);

        private Holder() {
        }
    }
}
