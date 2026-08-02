package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable, reproducible plan for the non-authoritative P4-E0-R2 process grid. */
final class P4E0ResearchMatrixPlan {
    static final int SCHEMA_VERSION = 0;
    static final String AUTHORITY = "EXPLORATORY_NON_NORMATIVE";
    static final String DISCLAIMER = "Observed pass/fail frontiers are machine-, fixture- and "
            + "implementation-specific evidence. They do not become Gramarye authority until "
            + "explicitly approved in P4-E0-B.";
    static final List<Integer> HEAP_GRID_MIB = List.of(1024, 1280, 1536, 1792, 2048);
    static final int PLAIN_TIMEOUT_SECONDS = 600;
    static final int DEDICATED_TIMEOUT_SECONDS = 900;
    private static final int MAXIMUM_PLANNED_RUNS = 4_096;

    enum Mode {
        PLAIN,
        DEDICATED
    }

    enum Matrix {
        A_DIRECTORY,
        B_SINGLE_FILE,
        C_NBT_COMPLEXITY,
        D_AGGREGATE_AUDIT,
        E_ROOT_CAPTURE,
        F_COMBINED
    }

    enum FrontierKind {
        WORKLOAD_AT_FIXED_HEAP,
        HEAP_FOR_FIXED_PROFILE
    }

    record RunSpec(
            int runIndex,
            String runId,
            Mode mode,
            Matrix matrix,
            FrontierKind frontierKind,
            String axis,
            String shape,
            String profile,
            int heapMiB,
            long coordinate,
            String coordinateUnit,
            int timeoutSeconds,
            String fixtureId,
            Map<String, Long> parameters) {
        RunSpec {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(frontierKind, "frontierKind");
            runId = token(runId, 96, false, "runId");
            axis = token(axis, 80, false, "axis");
            shape = token(shape, 80, false, "shape");
            profile = token(profile, 80, true, "profile");
            coordinateUnit = token(coordinateUnit, 40, false, "coordinateUnit");
            fixtureId = token(fixtureId, 96, false, "fixtureId");
            if (runIndex < 0 || coordinate < 0 || !HEAP_GRID_MIB.contains(heapMiB)) {
                throw new IllegalArgumentException("invalid research run coordinate");
            }
            var maximumTimeout = mode == Mode.PLAIN
                    ? PLAIN_TIMEOUT_SECONDS : DEDICATED_TIMEOUT_SECONDS;
            if (timeoutSeconds <= 0 || timeoutSeconds > maximumTimeout) {
                throw new IllegalArgumentException("research child timeout is out of bounds");
            }
            if (mode == Mode.DEDICATED && matrix != Matrix.F_COMBINED) {
                throw new IllegalArgumentException("only combined profiles use dedicated mode");
            }
            if (frontierKind == FrontierKind.HEAP_FOR_FIXED_PROFILE
                    && coordinate != heapMiB) {
                throw new IllegalArgumentException("heap frontier coordinate differs from Xmx");
            }
            if (parameters == null || parameters.size() > 64) {
                throw new IllegalArgumentException("unbounded research parameters");
            }
            var copy = new TreeMap<String, Long>();
            parameters.forEach((name, value) -> {
                var safeName = token(name, 80, false, "parameter name");
                if (value == null) {
                    throw new IllegalArgumentException("null research parameter");
                }
                if (copy.put(safeName, value) != null) {
                    throw new IllegalArgumentException("duplicate research parameter");
                }
            });
            parameters = Map.copyOf(copy);
        }

        JsonObject toJson() {
            var json = new JsonObject();
            json.addProperty("run_index", runIndex);
            json.addProperty("run_id", runId);
            json.addProperty("mode", mode.name());
            json.addProperty("matrix", matrix.name());
            json.addProperty("frontier_kind", frontierKind.name());
            json.addProperty("axis", axis);
            json.addProperty("shape", shape);
            json.addProperty("profile", profile);
            json.addProperty("heap_mib", heapMiB);
            json.addProperty("coordinate", coordinate);
            json.addProperty("coordinate_unit", coordinateUnit);
            json.addProperty("timeout_seconds", timeoutSeconds);
            json.addProperty("fixture_id", fixtureId);
            var parameterJson = new JsonObject();
            new TreeMap<>(parameters).forEach(parameterJson::addProperty);
            json.add("parameters", parameterJson);
            return json;
        }
    }

    private final List<RunSpec> runs;
    private final String planHash;

    P4E0ResearchMatrixPlan(List<RunSpec> runs) {
        if (runs == null || runs.isEmpty() || runs.size() > MAXIMUM_PLANNED_RUNS) {
            throw new IllegalArgumentException("research plan run count is out of bounds");
        }
        var copy = new ArrayList<RunSpec>(runs.size());
        var identifiers = new HashSet<String>();
        for (var index = 0; index < runs.size(); index++) {
            var run = Objects.requireNonNull(runs.get(index), "run");
            if (run.runIndex() != index || !identifiers.add(run.runId())) {
                throw new IllegalArgumentException("research plan order or identity is invalid");
            }
            copy.add(run);
        }
        this.runs = List.copyOf(copy);
        this.planHash = P4E0ResearchHashing.sha256(canonicalJson());
    }

    List<RunSpec> runs() {
        return runs;
    }

    int runCount() {
        return runs.size();
    }

    String planHash() {
        return planHash;
    }

    RunSpec requireRun(int index) {
        if (index < 0 || index >= runs.size()) {
            throw new IllegalArgumentException("research run index is outside the plan");
        }
        return runs.get(index);
    }

    String canonicalJson() {
        var root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("authority", AUTHORITY);
        var heaps = new JsonArray();
        HEAP_GRID_MIB.forEach(heaps::add);
        root.add("heap_grid_mib", heaps);
        var runArray = new JsonArray();
        runs.forEach(run -> runArray.add(run.toJson()));
        root.add("runs", runArray);
        return root.toString();
    }

    private static String token(
            String value, int maximumLength, boolean allowEmpty, String label) {
        if (value == null || value.length() > maximumLength
                || (!allowEmpty && value.isEmpty())
                || !value.matches(allowEmpty
                        ? "[A-Za-z0-9_.-]*" : "[A-Za-z0-9][A-Za-z0-9_.-]*")) {
            throw new IllegalArgumentException(label + " is not a bounded token");
        }
        return value;
    }
}
