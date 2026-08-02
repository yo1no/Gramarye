package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.SharedConstants;

/** CLI owner for the isolated P4-E0-R1 synthetic correctness smoke. */
public final class P4E0ResearchMain {
    private static final String CHILD_MODE = "--research-child";
    private static final String DEDICATED_CLASSIFY = "classify-dedicated";
    private static final String DEDICATED_CHILD_REPORT = "dedicated-child.json";
    private static final String DEDICATED_REPORT = "dedicated.json";
    private static final String DEDICATED_EXIT_FILE = "dedicated-exit-code.txt";
    static final String DEDICATED_RUNNING_MARKER = "dedicated-running-v0.txt";
    static final String DEDICATED_RUNNING_CONTENT = "P4_E0_RESEARCH_RUNNING_V0\n";
    private static final String RESEARCH_PROPERTY_PREFIX = "gramarye.p4e0.research.";
    private static final int HOTSPOT_EXIT_ON_OOME_CODE = 3;
    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final long CHILD_TIMEOUT_SECONDS = 540L;
    private static final long CHILD_TERMINATION_SECONDS = 30L;
    private static final long MEBIBYTE = 1_048_576L;

    private P4E0ResearchMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && CHILD_MODE.equals(args[0])) {
            runChild(args[1], Path.of(args[2]), Path.of(args[3]));
            return;
        }
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: prepare|run|verify <synthetic-fixture-root> <report-root>");
        }
        if (DEDICATED_CLASSIFY.equals(args[0])) {
            classifyDedicated(Path.of(args[1]), Path.of(args[2]));
            return;
        }
        superviseChild(args[0], Path.of(args[1]), Path.of(args[2]));
    }

    private static void runChild(String command, Path fixturePath, Path reportPath)
            throws Exception {
        SharedConstants.tryDetectVersion();
        var fixtureRoot = requireFixtureRoot(fixturePath);
        var reportRoot = requireReportRoot(reportPath);
        var report = reportRoot.resolve(reportName(command));
        var parameters = P4E0ResearchParameters.smoke(fixtureRoot);
        try {
            runCommand(command, fixtureRoot, reportRoot);
        } catch (Exception exception) {
            if (!Files.isRegularFile(report)) {
                failureResult(
                        parameters,
                        unobservedHeap(parameters),
                        0L,
                        1,
                        classifyExpectedFailure(exception),
                        exception.getClass().getName()).write(report);
            }
            throw exception;
        }
    }

    private static void runCommand(String command, Path fixtureRoot, Path reportRoot)
            throws Exception {
        switch (command) {
            case "prepare" -> execute(fixtureRoot, reportRoot, "prepare.json", true);
            case "run" -> execute(fixtureRoot, reportRoot, "standalone.json", false);
            case "verify" -> {
                verifyPriorReports(reportRoot);
                execute(fixtureRoot, reportRoot, "verify.json", false);
            }
            default -> throw new IllegalArgumentException("unknown research command");
        }
    }

    private static void superviseChild(String command, Path fixturePath, Path reportPath)
            throws Exception {
        var fixtureRoot = requireFixtureRoot(fixturePath);
        var reportRoot = requireReportRoot(reportPath);
        var report = reportRoot.resolve(reportName(command));
        Files.createDirectories(reportRoot);
        Files.deleteIfExists(report);
        var parameters = P4E0ResearchParameters.smoke(fixtureRoot);
        var started = System.nanoTime();

        final Process child;
        try {
            child = new ProcessBuilder(childCommand(
                    command, fixtureRoot, reportRoot, parameters))
                    .inheritIO()
                    .start();
        } catch (IOException exception) {
            failureResult(
                    parameters,
                    unobservedHeap(parameters),
                    elapsedMillis(started),
                    1,
                    P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                    exception.getClass().getName()).write(report);
            throw exception;
        }

        final boolean finished;
        try {
            finished = child.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            terminateChild(child);
            Thread.currentThread().interrupt();
            failureResult(
                    parameters,
                    unobservedHeap(parameters),
                    elapsedMillis(started),
                    TIMEOUT_EXIT_CODE,
                    P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                    exception.getClass().getName()).write(report);
            throw new IOException("research child supervision was interrupted");
        }
        if (!finished) {
            terminateChild(child);
            failureResult(
                    parameters,
                    unobservedHeap(parameters),
                    elapsedMillis(started),
                    TIMEOUT_EXIT_CODE,
                    classifyMissingChildReport(0, true),
                    "java.util.concurrent.TimeoutException").write(report);
            throw new IOException("research child timed out with bounded classification");
        }

        var exitCode = child.exitValue();
        var childReport = readReportIfBounded(report);
        if (exitCode == 0 && childReport != null) {
            requireCompleted(childReport);
            return;
        }
        if (exitCode != 0 && isMatchingFailureReport(childReport, exitCode)) {
            throw new IOException("research child failed with bounded classification");
        }

        var classification = exitCode == 0
                ? P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE
                : classifyMissingChildReport(exitCode, false);
        var failureClass = classification == P4E0ResearchResult.Classification.OOME_EXIT
                ? "java.lang.OutOfMemoryError" : "java.lang.IllegalStateException";
        failureResult(
                parameters,
                unobservedHeap(parameters),
                elapsedMillis(started),
                exitCode,
                classification,
                failureClass).write(report);
        throw new IOException("research child did not produce a valid bounded result");
    }

    static P4E0ResearchResult.Classification classifyMissingChildReport(
            int exitCode, boolean timedOut) {
        if (timedOut) {
            return P4E0ResearchResult.Classification.TIMEOUT;
        }
        return exitCode == HOTSPOT_EXIT_ON_OOME_CODE
                ? P4E0ResearchResult.Classification.OOME_EXIT
                : P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE;
    }

    static P4E0ResearchResult.Classification classifyDedicatedMissingExit(
            boolean exactRunningMarker) {
        return exactRunningMarker
                ? P4E0ResearchResult.Classification.TIMEOUT
                : P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE;
    }

    static List<String> childJvmArguments(int heapMiB) {
        if (heapMiB <= 0) {
            throw new IllegalArgumentException("research child heap must be positive");
        }
        return List.of(
                "-Xms512m",
                "-Xmx" + heapMiB + "m",
                "-XX:+ExitOnOutOfMemoryError");
    }

    private static List<String> childCommand(
            String command,
            Path fixtureRoot,
            Path reportRoot,
            P4E0ResearchParameters parameters) throws IOException {
        var javaExecutable = javaExecutable();
        var result = new ArrayList<String>();
        result.add(javaExecutable.toString());
        result.addAll(childJvmArguments(parameters.heapMiB()));
        System.getProperties().stringPropertyNames().stream()
                .filter(name -> name.startsWith(RESEARCH_PROPERTY_PREFIX)
                        || name.equals("gramarye.p4e0.gitHead"))
                .sorted()
                .forEach(name -> result.add(
                        "-D" + name + '=' + System.getProperty(name)));
        result.add("-cp");
        result.add(System.getProperty("java.class.path"));
        result.add(P4E0ResearchMain.class.getName());
        result.add(CHILD_MODE);
        result.add(command);
        result.add(fixtureRoot.toString());
        result.add(reportRoot.toString());
        return List.copyOf(result);
    }

    private static Path javaExecutable() throws IOException {
        var executableName = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
        var executable = Path.of(System.getProperty("java.home"), "bin", executableName)
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("research child Java executable is unavailable");
        }
        return executable;
    }

    private static void terminateChild(Process child) {
        child.destroyForcibly();
        try {
            child.waitFor(CHILD_TERMINATION_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /** Used by the isolated dedicated GameTest coordinator; it establishes no heap authority. */
    static void runDedicated(Path fixtureRoot, Path reportRoot) throws IOException {
        var safeReportRoot = requireReportRoot(reportRoot);
        Files.deleteIfExists(safeReportRoot.resolve(DEDICATED_CHILD_REPORT));
        try {
            SharedConstants.tryDetectVersion();
            execute(
                    requireFixtureRoot(fixtureRoot),
                    safeReportRoot,
                    DEDICATED_CHILD_REPORT,
                    false);
        } catch (IOException | RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IOException("dedicated research smoke failed", exception);
        }
    }

    static void markDedicatedRunning(Path reportRoot) throws IOException {
        var safeReportRoot = requireReportRoot(reportRoot);
        Files.createDirectories(safeReportRoot);
        Files.writeString(
                safeReportRoot.resolve(DEDICATED_RUNNING_MARKER),
                DEDICATED_RUNNING_CONTENT,
                StandardCharsets.US_ASCII);
    }

    static boolean hasExactDedicatedRunningMarker(Path reportRoot) {
        try {
            var safeReportRoot = requireReportRoot(reportRoot);
            var marker = safeReportRoot.resolve(DEDICATED_RUNNING_MARKER);
            var expectedBytes = DEDICATED_RUNNING_CONTENT.getBytes(StandardCharsets.US_ASCII);
            if (!Files.isRegularFile(marker) || Files.size(marker) != expectedBytes.length) {
                return false;
            }
            try (var input = Files.newInputStream(marker)) {
                return java.util.Arrays.equals(
                        expectedBytes, input.readNBytes(expectedBytes.length + 1));
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void classifyDedicated(Path fixturePath, Path reportPath)
            throws Exception {
        var fixtureRoot = requireFixtureRoot(fixturePath);
        var reportRoot = requireReportRoot(reportPath);
        var parameters = P4E0ResearchParameters.smoke(fixtureRoot);
        var childReportPath = reportRoot.resolve(DEDICATED_CHILD_REPORT);
        var finalReportPath = reportRoot.resolve(DEDICATED_REPORT);
        var exitPath = reportRoot.resolve(DEDICATED_EXIT_FILE);
        Files.deleteIfExists(finalReportPath);

        if (!Files.isRegularFile(exitPath)) {
            var exactRunningMarker = hasExactDedicatedRunningMarker(reportRoot);
            var classification = classifyDedicatedMissingExit(exactRunningMarker);
            failureResult(
                    parameters,
                    unobservedHeap(parameters),
                    0L,
                    exactRunningMarker ? TIMEOUT_EXIT_CODE : 1,
                    classification,
                    exactRunningMarker
                            ? "java.util.concurrent.TimeoutException"
                            : "java.lang.IllegalStateException").write(finalReportPath);
            throw new IOException(exactRunningMarker
                    ? "research dedicated child timed out"
                    : "research dedicated child did not enter its coordinator");
        }
        if (Files.size(exitPath) > 16L) {
            failureResult(
                    parameters,
                    unobservedHeap(parameters),
                    0L,
                    1,
                    P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                    "java.lang.IllegalStateException").write(finalReportPath);
            throw new IOException("research dedicated exit result is oversized");
        }

        final int exitCode;
        try {
            exitCode = Integer.parseInt(Files.readString(
                    exitPath, StandardCharsets.US_ASCII).trim());
        } catch (RuntimeException exception) {
            failureResult(
                    parameters,
                    unobservedHeap(parameters),
                    0L,
                    1,
                    P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                    exception.getClass().getName()).write(finalReportPath);
            throw new IOException("research dedicated exit result is malformed");
        }

        var childReport = readReportIfBounded(childReportPath);
        if (exitCode == 0 && childReport != null) {
            requireCompleted(childReport);
            Files.copy(
                    childReportPath,
                    finalReportPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        if (exitCode != 0 && isMatchingFailureReport(childReport, exitCode)) {
            Files.copy(
                    childReportPath,
                    finalReportPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("research dedicated child failed with bounded classification");
        }
        var classification = exitCode == 0
                ? P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE
                : classifyMissingChildReport(exitCode, false);
        failureResult(
                parameters,
                unobservedHeap(parameters),
                0L,
                exitCode,
                classification,
                classification == P4E0ResearchResult.Classification.OOME_EXIT
                        ? "java.lang.OutOfMemoryError"
                        : "java.lang.IllegalStateException").write(finalReportPath);
        throw new IOException("research dedicated child did not produce a valid result");
    }

    private static void execute(
            Path fixtureRoot,
            Path reportRoot,
            String reportName,
            boolean prepare) throws Exception {
        Files.createDirectories(reportRoot);
        var parameters = P4E0ResearchParameters.smoke(fixtureRoot);
        var started = System.nanoTime();
        try (var sampler = P4E0ResearchResult.HeapSampler.start()) {
            P4E0ResearchFixtureFactory.Observation observation;
            try {
                observation = prepare
                        ? P4E0ResearchFixtureFactory.prepareSmoke(parameters, fixtureRoot)
                        : P4E0ResearchFixtureFactory.observeSmoke(parameters, fixtureRoot);
            } catch (Exception exception) {
                var failure = failureResult(
                        parameters,
                        sampler.finish(),
                        elapsedMillis(started),
                        1,
                        classifyExpectedFailure(exception),
                        exception.getClass().getName());
                failure.write(reportRoot.resolve(reportName));
                throw exception;
            }
            observation.retainAtSamplingPoint();
            var heap = sampler.finish();
            observation.retainAtSamplingPoint();
            var expectedXmx = Math.multiplyExact(
                    (long) parameters.heapMiB(), MEBIBYTE);
            if (heap.xms() != 512L * MEBIBYTE || heap.xmx() != expectedXmx) {
                failureResult(
                        parameters,
                        heap,
                        elapsedMillis(started),
                        1,
                        P4E0ResearchResult.Classification.REJECTED_BY_RESEARCH_GUARD,
                        "java.lang.IllegalArgumentException")
                        .write(reportRoot.resolve(reportName));
                throw new IllegalArgumentException(
                        "research child heap differs from its configured coordinate");
            }
            var result = completedResult(
                    parameters, observation, heap, elapsedMillis(started));
            var report = reportRoot.resolve(reportName);
            result.write(report);
            System.out.println("P4_E0_RESEARCH classification=COMPLETED report="
                    + report.getFileName());
        }
    }

    private static P4E0ResearchResult completedResult(
            P4E0ResearchParameters parameters,
            P4E0ResearchFixtureFactory.Observation observation,
            P4E0ResearchResult.HeapMetrics heap,
            long elapsedMillis) {
        return new P4E0ResearchResult(
                parameters,
                observation.manifest(),
                elapsedMillis,
                heap,
                observation.directory(),
                observation.wire(),
                observation.nbt(),
                observation.attachment(),
                observation.roots(),
                observation.storeJournal(),
                observation.integrity(),
                0,
                P4E0ResearchResult.Classification.COMPLETED,
                "");
    }

    private static P4E0ResearchResult failureResult(
            P4E0ResearchParameters parameters,
            P4E0ResearchResult.HeapMetrics heap,
            long elapsedMillis,
            int processExitCode,
            P4E0ResearchResult.Classification classification,
            String failureClass) {
        var emptyHash = P4E0ResearchHashing.sha256("");
        return new P4E0ResearchResult(
                parameters,
                List.of(),
                elapsedMillis,
                heap,
                new P4E0ResearchResult.DirectoryMetrics(0, 0, 0, 0, 0, 0, 0),
                new P4E0ResearchResult.WireMetrics(0, 0, 0, 0, 0, 0, 0),
                P4E0ResearchNbtMetrics.zero(),
                new P4E0ResearchResult.AttachmentMetrics(0, 0, 0, 0, 0),
                new P4E0ResearchResult.RootMetrics(
                        0, 0, 0, "NOT_OBSERVED", "NOT_OBSERVED"),
                new P4E0ResearchResult.StoreJournalMetrics(
                        0, 0, 0, 0, 0, 0, "NOT_OBSERVED"),
                new P4E0ResearchResult.Integrity(
                        emptyHash, 0L, emptyHash, 0L, emptyHash),
                processExitCode,
                classification,
                failureClass);
    }

    private static P4E0ResearchResult.HeapMetrics unobservedHeap(
            P4E0ResearchParameters parameters) {
        return new P4E0ResearchResult.HeapMetrics(
                512L * MEBIBYTE,
                Math.multiplyExact((long) parameters.heapMiB(), MEBIBYTE),
                0L,
                0L,
                0L,
                0L,
                0L);
    }

    static P4E0ResearchResult.Classification classifyExpectedFailure(
            Exception exception) {
        if (exception instanceof IllegalArgumentException) {
            return P4E0ResearchResult.Classification.REJECTED_BY_RESEARCH_GUARD;
        }
        if (exception instanceof IOException || exception instanceof IllegalStateException) {
            return P4E0ResearchResult.Classification.FIXTURE_INVALID;
        }
        return P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE;
    }

    private static void verifyPriorReports(Path reportRoot) throws IOException {
        var prepare = readReport(reportRoot.resolve("prepare.json"));
        var standalone = readReport(reportRoot.resolve("standalone.json"));
        var dedicated = readReport(reportRoot.resolve("dedicated.json"));
        requireCompleted(prepare);
        requireCompleted(standalone);
        requireCompleted(dedicated);
        var expected = standalone.getAsJsonObject("integrity");
        var actual = dedicated.getAsJsonObject("integrity");
        for (var key : List.of(
                "fixture_hash",
                "fixture_file_count",
                "decoded_hash",
                "decoded_artifact_count",
                "semantic_checksum")) {
            if (!expected.get(key).equals(actual.get(key))) {
                throw new IOException("standalone and dedicated integrity differ");
            }
        }
    }

    private static boolean isMatchingFailureReport(JsonObject report, int exitCode) {
        if (report == null) {
            return false;
        }
        try {
            if (report.get("schema_version").getAsInt()
                            != P4E0ResearchResult.SCHEMA_VERSION
                    || report.getAsJsonObject("process_result")
                            .get("exit_code").getAsInt() != exitCode) {
                return false;
            }
            return P4E0ResearchResult.Classification.valueOf(
                            report.get("classification").getAsString())
                    != P4E0ResearchResult.Classification.COMPLETED;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static JsonObject readReportIfBounded(Path path) {
        try {
            return readReport(path);
        } catch (IOException exception) {
            return null;
        }
    }

    private static JsonObject readReport(Path path) throws IOException {
        if (!Files.isRegularFile(path)
                || Files.size(path) > P4E0ResearchResult.MAXIMUM_JSON_BYTES) {
            throw new IOException("bounded research report is missing or oversized");
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("bounded research report is malformed");
        }
    }

    private static void requireCompleted(JsonObject report) throws IOException {
        try {
            if (report.get("schema_version").getAsInt()
                            != P4E0ResearchResult.SCHEMA_VERSION
                    || !P4E0ResearchResult.Classification.COMPLETED.name().equals(
                            report.get("classification").getAsString())
                    || report.getAsJsonObject("process_result")
                            .get("exit_code").getAsInt() != 0) {
                throw new IOException("research child did not complete");
            }
        } catch (RuntimeException exception) {
            throw new IOException("research child did not complete");
        }
    }

    private static String reportName(String command) {
        return switch (command) {
            case "prepare" -> "prepare.json";
            case "run" -> "standalone.json";
            case "verify" -> "verify.json";
            default -> throw new IllegalArgumentException("unknown research command");
        };
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static Path requireFixtureRoot(Path path) {
        return requireBuildTree(path, "/build/p4-e0-research/");
    }

    private static Path requireReportRoot(Path path) {
        var normalized = path.toAbsolutePath().normalize();
        var text = normalized.toString().replace('\\', '/');
        if (!text.endsWith("/build/reports/p4-e0-research")
                && !text.contains("/build/reports/p4-e0-research/")) {
            throw new IllegalArgumentException("research report path is outside its build tree");
        }
        return normalized;
    }

    private static Path requireBuildTree(Path path, String marker) {
        var normalized = path.toAbsolutePath().normalize();
        var text = normalized.toString().replace('\\', '/');
        if (!text.contains(marker)) {
            throw new IllegalArgumentException("research fixture path is outside its build tree");
        }
        return normalized;
    }
}
