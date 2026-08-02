package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict bounded inventory of the synthetic fixtures actually materialized for one R2 study. */
final class P4E0ResearchFixtureManifest {
    static final int SCHEMA_VERSION = 0;
    static final int MAXIMUM_JSON_BYTES = 4 * 1_048_576;
    private static final int MAXIMUM_FIXTURES = 4_096;
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "schema_version", "authority", "git_head", "study_id", "plan_hash",
            "seed", "disk_budget_bytes", "fixture_root_hash",
            "base_fixture_verification", "planned_runs", "materialized_fixtures",
            "conditional_points", "skipped_points");

    enum BaseFixtureVerification {
        VERIFIED
    }

    enum ConditionalDecision {
        MATERIALIZED,
        SKIPPED
    }

    record Entry(
            String fixtureId,
            P4E0ResearchMatrixPlan.Matrix matrix,
            String axis,
            String shape,
            long coordinate,
            long fileCount,
            long physicalBytes,
            long actualCompressedBytes,
            long actualDecompressedBytes,
            String hash,
            String generationCode) {
        Entry {
            fixtureId = P4E0ResearchRunRecord.token(
                    fixtureId, 96, false, "fixtureId");
            java.util.Objects.requireNonNull(matrix, "matrix");
            axis = P4E0ResearchRunRecord.token(axis, 80, false, "axis");
            shape = P4E0ResearchRunRecord.token(shape, 80, false, "shape");
            generationCode = P4E0ResearchRunRecord.token(
                    generationCode, 80, false, "generationCode");
            P4E0ResearchRunRecord.requireHash(hash, "fixture hash");
            if (coordinate < 0 || fileCount < 0 || physicalBytes < 0
                    || actualCompressedBytes < 0 || actualDecompressedBytes < 0) {
                throw new IllegalArgumentException("negative fixture manifest metric");
            }
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("fixture_id", fixtureId);
            json.addProperty("matrix", matrix.name());
            json.addProperty("axis", axis);
            json.addProperty("shape", shape);
            json.addProperty("coordinate", coordinate);
            json.addProperty("file_count", fileCount);
            json.addProperty("physical_bytes", physicalBytes);
            json.addProperty("actual_compressed_bytes", actualCompressedBytes);
            json.addProperty("actual_decompressed_bytes", actualDecompressedBytes);
            json.addProperty("hash", hash);
            json.addProperty("generation_code", generationCode);
            return json;
        }
    }

    record ConditionalPoint(
            String fixtureId,
            ConditionalDecision decision,
            String reasonCode,
            List<String> sourceRunIds) {
        ConditionalPoint {
            fixtureId = P4E0ResearchRunRecord.token(
                    fixtureId, 96, false, "fixtureId");
            java.util.Objects.requireNonNull(decision, "decision");
            reasonCode = P4E0ResearchRunRecord.token(
                    reasonCode, 80, false, "reasonCode");
            if (sourceRunIds == null || sourceRunIds.size() > 32) {
                throw new IllegalArgumentException("unbounded adaptive source list");
            }
            var identifiers = new HashSet<String>();
            var copy = new ArrayList<String>();
            for (var sourceRunId : sourceRunIds) {
                var safe = P4E0ResearchRunRecord.token(
                        sourceRunId, 96, false, "sourceRunId");
                if (!identifiers.add(safe)) {
                    throw new IllegalArgumentException("duplicate adaptive source run");
                }
                copy.add(safe);
            }
            copy.sort(String::compareTo);
            sourceRunIds = List.copyOf(copy);
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("fixture_id", fixtureId);
            json.addProperty("decision", decision.name());
            json.addProperty("reason_code", reasonCode);
            var sources = new JsonArray();
            sourceRunIds.forEach(sources::add);
            json.add("source_run_ids", sources);
            return json;
        }
    }

    private final String gitHead;
    private final String studyId;
    private final String planHash;
    private final long seed;
    private final long diskBudgetBytes;
    private final String fixtureRootHash;
    private final BaseFixtureVerification baseFixtureVerification;
    private final int plannedRuns;
    private final List<Entry> materializedFixtures;
    private final List<ConditionalPoint> conditionalPoints;
    private final List<String> skippedPoints;

    P4E0ResearchFixtureManifest(
            String gitHead,
            String studyId,
            String planHash,
            long seed,
            long diskBudgetBytes,
            String fixtureRootHash,
            BaseFixtureVerification baseFixtureVerification,
            int plannedRuns,
            List<Entry> materializedFixtures,
            List<ConditionalPoint> conditionalPoints) {
        if (gitHead == null || !gitHead.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("gitHead is not an exact commit hash");
        }
        P4E0ResearchRunRecord.requireHash(studyId, "studyId");
        P4E0ResearchRunRecord.requireHash(planHash, "planHash");
        P4E0ResearchRunRecord.requireHash(fixtureRootHash, "fixtureRootHash");
        if (diskBudgetBytes <= 0 || plannedRuns <= 0
                || plannedRuns > MAXIMUM_FIXTURES) {
            throw new IllegalArgumentException("invalid research manifest bound");
        }
        this.gitHead = gitHead;
        this.studyId = studyId;
        this.planHash = planHash;
        this.seed = seed;
        this.diskBudgetBytes = diskBudgetBytes;
        this.fixtureRootHash = fixtureRootHash;
        this.baseFixtureVerification = java.util.Objects.requireNonNull(
                baseFixtureVerification, "baseFixtureVerification");
        this.plannedRuns = plannedRuns;
        this.materializedFixtures = copyEntries(materializedFixtures);
        this.conditionalPoints = copyConditionalPoints(conditionalPoints);
        this.skippedPoints = this.conditionalPoints.stream()
                .filter(point -> point.decision() == ConditionalDecision.SKIPPED)
                .map(ConditionalPoint::fixtureId)
                .sorted()
                .toList();
    }

    String gitHead() {
        return gitHead;
    }

    String studyId() {
        return studyId;
    }

    String planHash() {
        return planHash;
    }

    long seed() {
        return seed;
    }

    long diskBudgetBytes() {
        return diskBudgetBytes;
    }

    String fixtureRootHash() {
        return fixtureRootHash;
    }

    int plannedRuns() {
        return plannedRuns;
    }

    List<Entry> materializedFixtures() {
        return materializedFixtures;
    }

    List<ConditionalPoint> conditionalPoints() {
        return conditionalPoints;
    }

    List<String> skippedPoints() {
        return skippedPoints;
    }

    String toBoundedJson() {
        var root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("authority", P4E0ResearchMatrixPlan.AUTHORITY);
        root.addProperty("git_head", gitHead);
        root.addProperty("study_id", studyId);
        root.addProperty("plan_hash", planHash);
        root.addProperty("seed", seed);
        root.addProperty("disk_budget_bytes", diskBudgetBytes);
        root.addProperty("fixture_root_hash", fixtureRootHash);
        root.addProperty("base_fixture_verification", baseFixtureVerification.name());
        root.addProperty("planned_runs", plannedRuns);
        var fixtures = new JsonArray();
        materializedFixtures.forEach(entry -> fixtures.add(entry.toJson()));
        root.add("materialized_fixtures", fixtures);
        var conditional = new JsonArray();
        conditionalPoints.forEach(point -> conditional.add(point.toJson()));
        root.add("conditional_points", conditional);
        var skipped = new JsonArray();
        skippedPoints.forEach(skipped::add);
        root.add("skipped_points", skipped);
        var text = root.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_JSON_BYTES) {
            throw new IllegalStateException("fixture manifest exceeded its research bound");
        }
        return text;
    }

    void writeNew(Path path) throws IOException {
        P4E0ResearchRunRecord.atomicCreate(
                path, toBoundedJson() + System.lineSeparator());
    }

    static P4E0ResearchFixtureManifest read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) > MAXIMUM_JSON_BYTES + 1L) {
            throw new IOException("bounded research fixture manifest is unavailable");
        }
        var text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return parse(text);
    }

    static P4E0ResearchFixtureManifest parse(String text) throws IOException {
        if (text == null || text.isEmpty()
                || text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_JSON_BYTES) {
            throw P4E0ResearchRunRecord.malformed();
        }
        try (var reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            var names = P4E0ResearchRunRecord.beginUniqueObject(reader);
            Integer schema = null;
            String authority = null;
            String gitHead = null;
            String studyId = null;
            String planHash = null;
            Long seed = null;
            Long diskBudget = null;
            String fixtureRootHash = null;
            BaseFixtureVerification verification = null;
            Integer plannedRuns = null;
            List<Entry> entries = null;
            List<ConditionalPoint> conditional = null;
            List<String> skipped = null;
            while (reader.hasNext()) {
                var name = P4E0ResearchRunRecord.uniqueName(reader, names);
                if (!TOP_LEVEL_FIELDS.contains(name)) {
                    throw P4E0ResearchRunRecord.malformed();
                }
                switch (name) {
                    case "schema_version" -> schema = P4E0ResearchRunRecord.exactInt(reader);
                    case "authority" -> authority = P4E0ResearchRunRecord.exactString(reader);
                    case "git_head" -> gitHead = P4E0ResearchRunRecord.exactString(reader);
                    case "study_id" -> studyId = P4E0ResearchRunRecord.exactString(reader);
                    case "plan_hash" -> planHash = P4E0ResearchRunRecord.exactString(reader);
                    case "seed" -> seed = P4E0ResearchRunRecord.exactLong(reader);
                    case "disk_budget_bytes" -> diskBudget =
                            P4E0ResearchRunRecord.exactLong(reader);
                    case "fixture_root_hash" -> fixtureRootHash =
                            P4E0ResearchRunRecord.exactString(reader);
                    case "base_fixture_verification" -> verification =
                            P4E0ResearchRunRecord.enumValue(
                                    BaseFixtureVerification.class,
                                    P4E0ResearchRunRecord.exactString(reader));
                    case "planned_runs" -> plannedRuns =
                            P4E0ResearchRunRecord.exactInt(reader);
                    case "materialized_fixtures" -> entries = readEntries(reader);
                    case "conditional_points" -> conditional =
                            readConditionalPoints(reader);
                    case "skipped_points" -> skipped = readTokens(reader);
                    default -> throw P4E0ResearchRunRecord.malformed();
                }
            }
            reader.endObject();
            if (!names.equals(TOP_LEVEL_FIELDS)
                    || reader.peek() != JsonToken.END_DOCUMENT
                    || schema == null || schema != SCHEMA_VERSION
                    || !P4E0ResearchMatrixPlan.AUTHORITY.equals(authority)
                    || gitHead == null || studyId == null || planHash == null
                    || seed == null || diskBudget == null || fixtureRootHash == null
                    || verification == null || plannedRuns == null || entries == null
                    || conditional == null || skipped == null) {
                throw P4E0ResearchRunRecord.malformed();
            }
            var result = new P4E0ResearchFixtureManifest(
                    gitHead, studyId, planHash, seed, diskBudget, fixtureRootHash,
                    verification, plannedRuns, entries, conditional);
            if (!result.skippedPoints.equals(skipped)) {
                throw P4E0ResearchRunRecord.malformed();
            }
            return result;
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw P4E0ResearchRunRecord.malformed(exception);
        }
    }

    private static List<Entry> readEntries(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw P4E0ResearchRunRecord.malformed();
        }
        var entries = new ArrayList<Entry>();
        reader.beginArray();
        while (reader.hasNext()) {
            entries.add(readEntry(reader));
            if (entries.size() > MAXIMUM_FIXTURES) {
                throw P4E0ResearchRunRecord.malformed();
            }
        }
        reader.endArray();
        return List.copyOf(entries);
    }

    private static Entry readEntry(JsonReader reader) throws IOException {
        var names = P4E0ResearchRunRecord.beginUniqueObject(reader);
        var expected = Set.of(
                "fixture_id", "matrix", "axis", "shape", "coordinate",
                "file_count", "physical_bytes", "actual_compressed_bytes",
                "actual_decompressed_bytes", "hash", "generation_code");
        String fixtureId = null;
        P4E0ResearchMatrixPlan.Matrix matrix = null;
        String axis = null;
        String shape = null;
        Long coordinate = null;
        Long fileCount = null;
        Long physicalBytes = null;
        Long compressedBytes = null;
        Long decompressedBytes = null;
        String hash = null;
        String generationCode = null;
        while (reader.hasNext()) {
            var name = P4E0ResearchRunRecord.uniqueName(reader, names);
            if (!expected.contains(name)) {
                throw P4E0ResearchRunRecord.malformed();
            }
            switch (name) {
                case "fixture_id" -> fixtureId = P4E0ResearchRunRecord.exactString(reader);
                case "matrix" -> matrix = P4E0ResearchRunRecord.enumValue(
                        P4E0ResearchMatrixPlan.Matrix.class,
                        P4E0ResearchRunRecord.exactString(reader));
                case "axis" -> axis = P4E0ResearchRunRecord.exactString(reader);
                case "shape" -> shape = P4E0ResearchRunRecord.exactString(reader);
                case "coordinate" -> coordinate = P4E0ResearchRunRecord.exactLong(reader);
                case "file_count" -> fileCount = P4E0ResearchRunRecord.exactLong(reader);
                case "physical_bytes" -> physicalBytes =
                        P4E0ResearchRunRecord.exactLong(reader);
                case "actual_compressed_bytes" -> compressedBytes =
                        P4E0ResearchRunRecord.exactLong(reader);
                case "actual_decompressed_bytes" -> decompressedBytes =
                        P4E0ResearchRunRecord.exactLong(reader);
                case "hash" -> hash = P4E0ResearchRunRecord.exactString(reader);
                case "generation_code" -> generationCode =
                        P4E0ResearchRunRecord.exactString(reader);
                default -> throw P4E0ResearchRunRecord.malformed();
            }
        }
        reader.endObject();
        if (!names.equals(expected) || fixtureId == null || matrix == null
                || axis == null || shape == null || coordinate == null
                || fileCount == null || physicalBytes == null || compressedBytes == null
                || decompressedBytes == null || hash == null || generationCode == null) {
            throw P4E0ResearchRunRecord.malformed();
        }
        return new Entry(
                fixtureId, matrix, axis, shape, coordinate, fileCount,
                physicalBytes, compressedBytes, decompressedBytes, hash,
                generationCode);
    }

    private static List<ConditionalPoint> readConditionalPoints(JsonReader reader)
            throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw P4E0ResearchRunRecord.malformed();
        }
        var points = new ArrayList<ConditionalPoint>();
        reader.beginArray();
        while (reader.hasNext()) {
            points.add(readConditionalPoint(reader));
            if (points.size() > MAXIMUM_FIXTURES) {
                throw P4E0ResearchRunRecord.malformed();
            }
        }
        reader.endArray();
        return List.copyOf(points);
    }

    private static ConditionalPoint readConditionalPoint(JsonReader reader)
            throws IOException {
        var names = P4E0ResearchRunRecord.beginUniqueObject(reader);
        var expected = Set.of("fixture_id", "decision", "reason_code", "source_run_ids");
        String fixtureId = null;
        ConditionalDecision decision = null;
        String reasonCode = null;
        List<String> sourceRuns = null;
        while (reader.hasNext()) {
            var name = P4E0ResearchRunRecord.uniqueName(reader, names);
            if (!expected.contains(name)) {
                throw P4E0ResearchRunRecord.malformed();
            }
            switch (name) {
                case "fixture_id" -> fixtureId = P4E0ResearchRunRecord.exactString(reader);
                case "decision" -> decision = P4E0ResearchRunRecord.enumValue(
                        ConditionalDecision.class,
                        P4E0ResearchRunRecord.exactString(reader));
                case "reason_code" -> reasonCode =
                        P4E0ResearchRunRecord.exactString(reader);
                case "source_run_ids" -> sourceRuns = readTokens(reader);
                default -> throw P4E0ResearchRunRecord.malformed();
            }
        }
        reader.endObject();
        if (!names.equals(expected) || fixtureId == null || decision == null
                || reasonCode == null || sourceRuns == null) {
            throw P4E0ResearchRunRecord.malformed();
        }
        return new ConditionalPoint(fixtureId, decision, reasonCode, sourceRuns);
    }

    private static List<String> readTokens(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw P4E0ResearchRunRecord.malformed();
        }
        var values = new ArrayList<String>();
        var unique = new HashSet<String>();
        reader.beginArray();
        while (reader.hasNext()) {
            var value = P4E0ResearchRunRecord.token(
                    P4E0ResearchRunRecord.exactString(reader),
                    96, false, "manifest token");
            if (!unique.add(value) || values.size() >= MAXIMUM_FIXTURES) {
                throw P4E0ResearchRunRecord.malformed();
            }
            values.add(value);
        }
        reader.endArray();
        values.sort(String::compareTo);
        return List.copyOf(values);
    }

    private static List<Entry> copyEntries(List<Entry> entries) {
        if (entries == null || entries.size() > MAXIMUM_FIXTURES) {
            throw new IllegalArgumentException("unbounded fixture manifest");
        }
        var identifiers = new HashSet<String>();
        var copy = new ArrayList<Entry>();
        for (var entry : entries) {
            var safe = java.util.Objects.requireNonNull(entry, "entry");
            if (!identifiers.add(safe.fixtureId())) {
                throw new IllegalArgumentException("duplicate materialized fixture");
            }
            copy.add(safe);
        }
        copy.sort(Comparator.comparing(Entry::fixtureId));
        return List.copyOf(copy);
    }

    private static List<ConditionalPoint> copyConditionalPoints(
            List<ConditionalPoint> points) {
        if (points == null || points.size() > MAXIMUM_FIXTURES) {
            throw new IllegalArgumentException("unbounded conditional fixture manifest");
        }
        var identifiers = new HashSet<String>();
        var copy = new ArrayList<ConditionalPoint>();
        for (var point : points) {
            var safe = java.util.Objects.requireNonNull(point, "point");
            if (!identifiers.add(safe.fixtureId())) {
                throw new IllegalArgumentException("duplicate conditional fixture point");
            }
            copy.add(safe);
        }
        copy.sort(Comparator.comparing(ConditionalPoint::fixtureId));
        return List.copyOf(copy);
    }
}
