package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** One strict, bounded JSONL observation from the exploratory P4-E0-R2 matrix. */
final class P4E0ResearchRunRecord {
    static final int SCHEMA_VERSION = 0;
    static final int MAXIMUM_JSON_LINE_BYTES = 65_536;
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "schema_version", "authority", "study_id", "plan_hash",
            "run_index", "run_id", "mode", "matrix", "frontier_kind",
            "axis", "shape", "profile", "heap_mib", "coordinate",
            "coordinate_unit", "timeout_seconds", "fixture_id", "parameters",
            "classification", "process_result", "elapsed_millis", "environment",
            "metrics", "fixture");

    record ProcessResult(
            int exitCode,
            boolean timedOut,
            boolean oomeExit,
            boolean reportObserved,
            String boundedFailureClass) {
        ProcessResult {
            if (exitCode < 0) {
                throw new IllegalArgumentException("negative child exit code");
            }
            boundedFailureClass = boundedText(
                    boundedFailureClass, 160, "boundedFailureClass", true);
            if (timedOut && oomeExit) {
                throw new IllegalArgumentException("child cannot be timeout and OOME");
            }
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("exit_code", exitCode);
            json.addProperty("timed_out", timedOut);
            json.addProperty("oome_exit", oomeExit);
            json.addProperty("report_observed", reportObserved);
            json.addProperty("bounded_failure_class", boundedFailureClass);
            return json;
        }
    }

    record Environment(
            String javaVersion,
            String vmName,
            String osName,
            String osArch,
            String fileStoreName,
            String fileStoreType) {
        Environment {
            javaVersion = boundedText(javaVersion, 80, "javaVersion", false);
            vmName = boundedText(vmName, 120, "vmName", false);
            osName = boundedText(osName, 80, "osName", false);
            osArch = boundedText(osArch, 80, "osArch", false);
            fileStoreName = boundedText(fileStoreName, 160, "fileStoreName", false);
            fileStoreType = boundedText(fileStoreType, 80, "fileStoreType", false);
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("java_version", javaVersion);
            json.addProperty("vm_name", vmName);
            json.addProperty("os_name", osName);
            json.addProperty("os_arch", osArch);
            json.addProperty("file_store_name", fileStoreName);
            json.addProperty("file_store_type", fileStoreType);
            return json;
        }
    }

    record FixtureEvidence(
            String fixtureId,
            String fixtureHash,
            long physicalBytes,
            long actualCompressedBytes,
            long actualDecompressedBytes) {
        FixtureEvidence {
            fixtureId = token(fixtureId, 96, false, "fixtureId");
            requireHash(fixtureHash, "fixtureHash");
            if (physicalBytes < 0 || actualCompressedBytes < 0
                    || actualDecompressedBytes < 0) {
                throw new IllegalArgumentException("negative fixture evidence");
            }
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("fixture_id", fixtureId);
            json.addProperty("fixture_hash", fixtureHash);
            json.addProperty("physical_bytes", physicalBytes);
            json.addProperty("actual_compressed_bytes", actualCompressedBytes);
            json.addProperty("actual_decompressed_bytes", actualDecompressedBytes);
            return json;
        }
    }

    private final String studyId;
    private final String planHash;
    private final P4E0ResearchMatrixPlan.RunSpec spec;
    private final P4E0ResearchResult.Classification classification;
    private final ProcessResult processResult;
    private final long elapsedMillis;
    private final Environment environment;
    private final Map<String, Long> metrics;
    private final FixtureEvidence fixture;

    P4E0ResearchRunRecord(
            String studyId,
            String planHash,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchResult.Classification classification,
            ProcessResult processResult,
            long elapsedMillis,
            Environment environment,
            Map<String, Long> metrics,
            FixtureEvidence fixture) {
        requireHash(studyId, "studyId");
        requireHash(planHash, "planHash");
        this.studyId = studyId;
        this.planHash = planHash;
        this.spec = Objects.requireNonNull(spec, "spec");
        this.classification = Objects.requireNonNull(classification, "classification");
        this.processResult = Objects.requireNonNull(processResult, "processResult");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.fixture = Objects.requireNonNull(fixture, "fixture");
        if (!spec.fixtureId().equals(fixture.fixtureId())) {
            throw new IllegalArgumentException("run and fixture identities differ");
        }
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("negative research elapsed time");
        }
        this.elapsedMillis = elapsedMillis;
        if (metrics == null || metrics.size() > 128) {
            throw new IllegalArgumentException("unbounded measured metric vector");
        }
        var metricCopy = new TreeMap<String, Long>();
        metrics.forEach((name, value) -> {
            var safeName = token(name, 80, false, "metric name");
            if (value == null || value < 0 || metricCopy.put(safeName, value) != null) {
                throw new IllegalArgumentException("invalid measured metric");
            }
        });
        this.metrics = Map.copyOf(metricCopy);
        requireClassificationConsistency();
    }

    String studyId() {
        return studyId;
    }

    String planHash() {
        return planHash;
    }

    P4E0ResearchMatrixPlan.RunSpec spec() {
        return spec;
    }

    P4E0ResearchResult.Classification classification() {
        return classification;
    }

    ProcessResult processResult() {
        return processResult;
    }

    long elapsedMillis() {
        return elapsedMillis;
    }

    Environment environment() {
        return environment;
    }

    Map<String, Long> metrics() {
        return metrics;
    }

    FixtureEvidence fixture() {
        return fixture;
    }

    String toJsonLine() {
        var json = new JsonObject();
        json.addProperty("schema_version", SCHEMA_VERSION);
        json.addProperty("authority", P4E0ResearchMatrixPlan.AUTHORITY);
        json.addProperty("study_id", studyId);
        json.addProperty("plan_hash", planHash);
        var specJson = spec.toJson();
        for (var field : specJson.entrySet()) {
            json.add(field.getKey(), field.getValue());
        }
        json.addProperty("classification", classification.name());
        json.add("process_result", processResult.toJson());
        json.addProperty("elapsed_millis", elapsedMillis);
        json.add("environment", environment.toJson());
        var metricJson = new JsonObject();
        new TreeMap<>(metrics).forEach(metricJson::addProperty);
        json.add("metrics", metricJson);
        json.add("fixture", fixture.toJson());
        var text = json.toString();
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                || text.getBytes(StandardCharsets.UTF_8).length
                        > MAXIMUM_JSON_LINE_BYTES) {
            throw new IllegalStateException("research JSONL record exceeded its bound");
        }
        return text;
    }

    void writeNew(Path path) throws IOException {
        atomicCreate(path, toJsonLine() + System.lineSeparator());
    }

    static P4E0ResearchRunRecord read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) > MAXIMUM_JSON_LINE_BYTES + 1L) {
            throw new IOException("bounded research run record is unavailable");
        }
        var text = Files.readString(path, StandardCharsets.UTF_8);
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        return parseLine(text);
    }

    static P4E0ResearchRunRecord parseLine(String line) throws IOException {
        if (line == null || line.isEmpty() || line.indexOf('\n') >= 0
                || line.indexOf('\r') >= 0
                || line.getBytes(StandardCharsets.UTF_8).length
                        > MAXIMUM_JSON_LINE_BYTES) {
            throw malformed();
        }
        try (var reader = new JsonReader(new StringReader(line))) {
            reader.setLenient(false);
            var names = beginUniqueObject(reader);
            Integer schemaVersion = null;
            String authority = null;
            String studyId = null;
            String planHash = null;
            Integer runIndex = null;
            String runId = null;
            P4E0ResearchMatrixPlan.Mode mode = null;
            P4E0ResearchMatrixPlan.Matrix matrix = null;
            P4E0ResearchMatrixPlan.FrontierKind frontierKind = null;
            String axis = null;
            String shape = null;
            String profile = null;
            Integer heapMiB = null;
            Long coordinate = null;
            String coordinateUnit = null;
            Integer timeoutSeconds = null;
            String fixtureId = null;
            Map<String, Long> parameters = null;
            P4E0ResearchResult.Classification classification = null;
            ProcessResult processResult = null;
            Long elapsedMillis = null;
            Environment environment = null;
            Map<String, Long> metrics = null;
            FixtureEvidence fixture = null;
            while (reader.hasNext()) {
                var name = uniqueName(reader, names);
                if (!TOP_LEVEL_FIELDS.contains(name)) {
                    throw malformed();
                }
                switch (name) {
                    case "schema_version" -> schemaVersion = exactInt(reader);
                    case "authority" -> authority = exactString(reader);
                    case "study_id" -> studyId = exactString(reader);
                    case "plan_hash" -> planHash = exactString(reader);
                    case "run_index" -> runIndex = exactInt(reader);
                    case "run_id" -> runId = exactString(reader);
                    case "mode" -> mode = enumValue(
                            P4E0ResearchMatrixPlan.Mode.class, exactString(reader));
                    case "matrix" -> matrix = enumValue(
                            P4E0ResearchMatrixPlan.Matrix.class, exactString(reader));
                    case "frontier_kind" -> frontierKind = enumValue(
                            P4E0ResearchMatrixPlan.FrontierKind.class,
                            exactString(reader));
                    case "axis" -> axis = exactString(reader);
                    case "shape" -> shape = exactString(reader);
                    case "profile" -> profile = exactString(reader);
                    case "heap_mib" -> heapMiB = exactInt(reader);
                    case "coordinate" -> coordinate = exactLong(reader);
                    case "coordinate_unit" -> coordinateUnit = exactString(reader);
                    case "timeout_seconds" -> timeoutSeconds = exactInt(reader);
                    case "fixture_id" -> fixtureId = exactString(reader);
                    case "parameters" -> parameters = exactLongMap(reader, 64);
                    case "classification" -> classification = enumValue(
                            P4E0ResearchResult.Classification.class,
                            exactString(reader));
                    case "process_result" -> processResult = readProcessResult(reader);
                    case "elapsed_millis" -> elapsedMillis = exactLong(reader);
                    case "environment" -> environment = readEnvironment(reader);
                    case "metrics" -> metrics = exactLongMap(reader, 128);
                    case "fixture" -> fixture = readFixture(reader);
                    default -> throw malformed();
                }
            }
            reader.endObject();
            if (!names.equals(TOP_LEVEL_FIELDS)
                    || reader.peek() != JsonToken.END_DOCUMENT
                    || schemaVersion == null || schemaVersion != SCHEMA_VERSION
                    || !P4E0ResearchMatrixPlan.AUTHORITY.equals(authority)
                    || studyId == null || planHash == null || runIndex == null
                    || runId == null || mode == null || matrix == null
                    || frontierKind == null || axis == null || shape == null
                    || profile == null || heapMiB == null || coordinate == null
                    || coordinateUnit == null || timeoutSeconds == null
                    || fixtureId == null || parameters == null
                    || classification == null || processResult == null
                    || elapsedMillis == null || environment == null
                    || metrics == null || fixture == null) {
                throw malformed();
            }
            var spec = new P4E0ResearchMatrixPlan.RunSpec(
                    runIndex, runId, mode, matrix, frontierKind, axis, shape,
                    profile, heapMiB, coordinate, coordinateUnit, timeoutSeconds,
                    fixtureId, parameters);
            return new P4E0ResearchRunRecord(
                    studyId, planHash, spec, classification, processResult,
                    elapsedMillis, environment, metrics, fixture);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformed(exception);
        }
    }

    private void requireClassificationConsistency() {
        if (classification == P4E0ResearchResult.Classification.COMPLETED
                && (processResult.exitCode() != 0 || processResult.timedOut()
                        || processResult.oomeExit() || !processResult.reportObserved())) {
            throw new IllegalArgumentException("completed child result is inconsistent");
        }
        if ((classification == P4E0ResearchResult.Classification.TIMEOUT)
                != processResult.timedOut()) {
            throw new IllegalArgumentException("timeout classification is inconsistent");
        }
        if ((classification == P4E0ResearchResult.Classification.OOME_EXIT)
                != processResult.oomeExit()) {
            throw new IllegalArgumentException("OOME classification is inconsistent");
        }
    }

    private static ProcessResult readProcessResult(JsonReader reader) throws IOException {
        var names = beginUniqueObject(reader);
        Integer exitCode = null;
        Boolean timedOut = null;
        Boolean oomeExit = null;
        Boolean reportObserved = null;
        String failureClass = null;
        var expected = Set.of(
                "exit_code", "timed_out", "oome_exit", "report_observed",
                "bounded_failure_class");
        while (reader.hasNext()) {
            var name = uniqueName(reader, names);
            if (!expected.contains(name)) {
                throw malformed();
            }
            switch (name) {
                case "exit_code" -> exitCode = exactInt(reader);
                case "timed_out" -> timedOut = exactBoolean(reader);
                case "oome_exit" -> oomeExit = exactBoolean(reader);
                case "report_observed" -> reportObserved = exactBoolean(reader);
                case "bounded_failure_class" -> failureClass = exactString(reader);
                default -> throw malformed();
            }
        }
        reader.endObject();
        if (!names.equals(expected) || exitCode == null || timedOut == null
                || oomeExit == null || reportObserved == null || failureClass == null) {
            throw malformed();
        }
        return new ProcessResult(
                exitCode, timedOut, oomeExit, reportObserved, failureClass);
    }

    private static Environment readEnvironment(JsonReader reader) throws IOException {
        var names = beginUniqueObject(reader);
        var expected = Set.of(
                "java_version", "vm_name", "os_name", "os_arch",
                "file_store_name", "file_store_type");
        var values = new TreeMap<String, String>();
        while (reader.hasNext()) {
            var name = uniqueName(reader, names);
            if (!expected.contains(name)) {
                throw malformed();
            }
            values.put(name, exactString(reader));
        }
        reader.endObject();
        if (!names.equals(expected)) {
            throw malformed();
        }
        return new Environment(
                values.get("java_version"), values.get("vm_name"),
                values.get("os_name"), values.get("os_arch"),
                values.get("file_store_name"), values.get("file_store_type"));
    }

    private static FixtureEvidence readFixture(JsonReader reader) throws IOException {
        var names = beginUniqueObject(reader);
        var expected = Set.of(
                "fixture_id", "fixture_hash", "physical_bytes",
                "actual_compressed_bytes", "actual_decompressed_bytes");
        String fixtureId = null;
        String fixtureHash = null;
        Long physicalBytes = null;
        Long compressedBytes = null;
        Long decompressedBytes = null;
        while (reader.hasNext()) {
            var name = uniqueName(reader, names);
            if (!expected.contains(name)) {
                throw malformed();
            }
            switch (name) {
                case "fixture_id" -> fixtureId = exactString(reader);
                case "fixture_hash" -> fixtureHash = exactString(reader);
                case "physical_bytes" -> physicalBytes = exactLong(reader);
                case "actual_compressed_bytes" -> compressedBytes = exactLong(reader);
                case "actual_decompressed_bytes" -> decompressedBytes = exactLong(reader);
                default -> throw malformed();
            }
        }
        reader.endObject();
        if (!names.equals(expected) || fixtureId == null || fixtureHash == null
                || physicalBytes == null || compressedBytes == null
                || decompressedBytes == null) {
            throw malformed();
        }
        return new FixtureEvidence(
                fixtureId, fixtureHash, physicalBytes, compressedBytes,
                decompressedBytes);
    }

    static Map<String, Long> exactLongMap(JsonReader reader, int maximumEntries)
            throws IOException {
        var names = beginUniqueObject(reader);
        var values = new TreeMap<String, Long>();
        while (reader.hasNext()) {
            var name = uniqueName(reader, names);
            token(name, 80, false, "numeric map key");
            values.put(name, exactLong(reader));
            if (values.size() > maximumEntries) {
                throw malformed();
            }
        }
        reader.endObject();
        return Map.copyOf(values);
    }

    static HashSet<String> beginUniqueObject(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw malformed();
        }
        reader.beginObject();
        return new HashSet<>();
    }

    static String uniqueName(JsonReader reader, Set<String> names) throws IOException {
        var name = reader.nextName();
        if (!names.add(name)) {
            throw malformed();
        }
        return name;
    }

    static String exactString(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            throw malformed();
        }
        return reader.nextString();
    }

    static boolean exactBoolean(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BOOLEAN) {
            throw malformed();
        }
        return reader.nextBoolean();
    }

    static int exactInt(JsonReader reader) throws IOException {
        var value = exactLong(reader);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw malformed();
        }
        return (int) value;
    }

    static long exactLong(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.NUMBER) {
            throw malformed();
        }
        var lexical = reader.nextString();
        if (!lexical.matches("-?(0|[1-9][0-9]*)")) {
            throw malformed();
        }
        try {
            return Long.parseLong(lexical);
        } catch (NumberFormatException exception) {
            throw malformed(exception);
        }
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String value)
            throws IOException {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException exception) {
            throw malformed(exception);
        }
    }

    static void atomicCreate(Path path, String text) throws IOException {
        var normalized = path.toAbsolutePath().normalize();
        var parent = normalized.getParent();
        if (parent == null) {
            throw new IOException("research output has no parent");
        }
        Files.createDirectories(parent);
        if (Files.exists(normalized)) {
            throw new IOException("research output already exists");
        }
        var temporary = Files.createTempFile(parent, ".p4-e0-r2-", ".tmp");
        try {
            Files.writeString(
                    temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, normalized, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, normalized);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static IOException malformed() {
        return new IOException("research JSON does not match its strict bounded schema");
    }

    static IOException malformed(Exception cause) {
        return new IOException(
                "research JSON does not match its strict bounded schema", cause);
    }

    static String token(
            String value, int maximumLength, boolean allowEmpty, String label) {
        if (value == null || value.length() > maximumLength
                || (!allowEmpty && value.isEmpty())
                || !value.matches(allowEmpty
                        ? "[A-Za-z0-9_.-]*" : "[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException(label + " is not a bounded token");
        }
        return value;
    }

    static String boundedText(
            String value, int maximumLength, String label, boolean allowEmpty) {
        if (value == null || value.length() > maximumLength
                || (!allowEmpty && value.isEmpty())) {
            throw new IllegalArgumentException(label + " is not bounded");
        }
        for (var index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(label + " contains a control character");
            }
        }
        return value;
    }

    static void requireHash(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " is not SHA-256");
        }
    }
}
