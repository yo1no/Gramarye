package com.yo1no.gramarye.magic.definition.research;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded parent supervisor for the isolated R2Q formal children. */
final class P4E0R2QFormalMain {
    private static final String CONTROL_FILE = "study-control.json";
    private static final String AGGREGATE_MARKER = "aggregate.ready";
    private static final String RUNNING = "RUNNING\n";
    private static final String COMPLETED = "COMPLETED\n";
    private static final String TIMEOUT = "TIMEOUT\n";
    private static final String PARENT_DEADLINE_MARKER = "parent-deadline.marker";
    private static final String SMOKE_BASELINE = "formal-evidence-before.txt";
    private static final int OOME_EXIT_CODE = 3;
    private static final int MAXIMUM_GIT_OUTPUT_BYTES = 131_072;
    private static final Duration GIT_TIMEOUT = Duration.ofSeconds(30L);

    private P4E0R2QFormalMain() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 4) {
            throw new IllegalArgumentException("formal R2Q command arguments are incomplete");
        }
        var command = arguments[0];
        var workRoot = normalized(arguments[arguments.length - 3]);
        var officialRoot = normalized(arguments[arguments.length - 2]);
        var smokeRoot = normalized(arguments[arguments.length - 1]);
        requirePairwiseDisjointRoots(workRoot, officialRoot, smokeRoot);
        switch (command) {
            case "prepare-study" -> prepareStudy(workRoot, officialRoot);
            case "prepare-case" -> prepareCase(workRoot, exactCase(arguments));
            case "verify-case" -> verifyCase(workRoot, exactCase(arguments));
            case "capture-failure" -> captureFailure(
                    workRoot, exactCase(arguments), parentFailure(arguments));
            case "aggregate" -> aggregate(workRoot);
            case "verify-artifacts" -> verifyArtifacts(workRoot, officialRoot);
            case "run-runner-smoke" -> runRunnerSmoke(workRoot, officialRoot, smokeRoot);
            case "verify-runner-smoke" ->
                    verifyRunnerSmoke(workRoot, smokeRoot, officialRoot);
            default -> throw new IllegalArgumentException("unknown formal R2Q command");
        }
    }

    static P4E0R2QFormalResult.ProcessClassification classifyParentEvidence(
            int exitCode, String marker, boolean resultPresent, boolean parentTimedOut) {
        if (exitCode == OOME_EXIT_CODE) {
            return P4E0R2QFormalResult.ProcessClassification.OOME_EXIT;
        }
        if (parentTimedOut && exitCode == 124) {
            return P4E0R2QFormalResult.ProcessClassification.TIMEOUT;
        }
        if (exitCode == 0 && resultPresent && COMPLETED.equals(marker)) {
            return P4E0R2QFormalResult.ProcessClassification.COMPLETED;
        }
        return P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE;
    }

    static void writeDedicatedRunnerSmoke(Path smokeRoot) throws IOException {
        writeSmokeSet(smokeRoot.resolve("dedicated-supervisor.jsonl"), "dedicated");
    }

    private static void prepareStudy(Path workRoot, Path officialRoot) throws IOException {
        var repository = requireFormalProperties();
        var revision = requireCleanRepository(repository);
        var control = P4E0R2QFormalEvidence.createControl(
                revision.head(), revision.tree(), P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
        var staleRoot = workRoot.resolveSibling("stale-evidence");
        var failedRoot = workRoot.resolveSibling("failed-evidence");
        P4E0R2QFormalEvidence.requireNoFormalStaging(
                officialRoot, staleRoot, failedRoot);
        if (Files.exists(failedRoot.resolve(control.studyId()), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(staleRoot.resolve(control.studyId()), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(staleRoot.resolve(control.gitHead()), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("formal study identity already has archived evidence");
        }
        var officialInspection = P4E0R2QFormalEvidence.inspectOfficialOutput(officialRoot);
        switch (officialInspection.classification()) {
            case ABSENT -> {
                // Publication already has the required absent target.
            }
            case EMPTY_OR_METADATA_ONLY ->
                    P4E0R2QFormalEvidence.removeEmptyOrMetadataOnlyOfficial(
                            repository, officialRoot, officialInspection);
            case VALID_OFFICIAL_SET -> {
                var previous = officialInspection.validatedControl().orElseThrow();
                if (previous.studyId().equals(control.studyId())
                        || previous.gitHead().equals(control.gitHead())) {
                    throw new IOException("formal study identity cannot be reused");
                }
                P4E0R2QFormalEvidence.archiveStaleOfficial(
                        officialRoot,
                        staleRoot,
                        previous.studyId());
            }
            case MALFORMED_NONEMPTY_OUTPUT -> throw new IOException(
                    "malformed nonempty formal output is preserved");
        }
        if (Files.exists(officialRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("official formal publication root must be absent");
        }
        removeGeneratedSkeleton(repository, workRoot);
        if (Files.exists(workRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("formal work root already exists");
        }
        Files.createDirectories(workRoot);
        P4E0R2QFormalEvidence.writeControl(workRoot.resolve(CONTROL_FILE), control);
        P4E0R2QFormalEvidence.requireDiskBudget(workRoot, 0L);
    }

    private static void prepareCase(Path workRoot, int caseIndex) throws IOException {
        requireFormalProperties();
        var control = P4E0R2QFormalEvidence.readControl(workRoot.resolve(CONTROL_FILE));
        try {
            P4E0R2QFormalWorkload.prepareCase(
                    P4E0R2QFormalEvidence.caseDirectory(workRoot, caseIndex),
                    control,
                    caseIndex);
        } catch (P4E0R2QFormalEvidence.ResearchGuardException exception) {
            preservePrepareFailure(
                    workRoot,
                    control,
                    caseIndex,
                    P4E0R2QFormalResult.ProcessClassification.REJECTED_BY_RESEARCH_GUARD);
            throw exception;
        } catch (IOException exception) {
            preservePrepareFailure(
                    workRoot,
                    control,
                    caseIndex,
                    P4E0R2QFormalResult.ProcessClassification.FIXTURE_INVALID);
            throw exception;
        } catch (RuntimeException exception) {
            preservePrepareFailure(
                    workRoot,
                    control,
                    caseIndex,
                    P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE);
            throw exception;
        }
    }

    private static void preservePrepareFailure(
            Path workRoot,
            P4E0R2QFormalEvidence.StudyControl control,
            int caseIndex,
            P4E0R2QFormalResult.ProcessClassification classification) throws IOException {
        var caseRoot = P4E0R2QFormalEvidence.caseDirectory(workRoot, caseIndex);
        Files.createDirectories(caseRoot);
        var result = P4E0R2QFormalWorkload.failedResult(
                control,
                caseIndex,
                classification,
                classification == P4E0R2QFormalResult.ProcessClassification.FIXTURE_INVALID
                        ? P4E0R2QFormalWorkload.FixtureInvalidException.class.getName()
                        : "java.lang.IllegalStateException",
                manifestChecksumOrFallback(caseRoot, control, caseIndex));
        var verified = caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT);
        if (!Files.exists(verified, LinkOption.NOFOLLOW_LINKS)) {
            P4E0R2QFormalEvidence.writeResult(verified, result);
        }
        preserveFailure(workRoot, control, classification.name());
    }

    private static void verifyCase(Path workRoot, int caseIndex) throws IOException {
        requireFormalProperties();
        var control = P4E0R2QFormalEvidence.readControl(workRoot.resolve(CONTROL_FILE));
        try {
            verifyCaseBody(workRoot, control, caseIndex);
        } catch (IOException | RuntimeException exception) {
            preserveUnexpectedFailure(workRoot, control, caseIndex);
            throw exception;
        }
    }

    private static void verifyCaseBody(
            Path workRoot, P4E0R2QFormalEvidence.StudyControl control, int caseIndex)
            throws IOException {
        var caseRoot = P4E0R2QFormalEvidence.caseDirectory(workRoot, caseIndex);
        var childPath = caseRoot.resolve(P4E0R2QFormalWorkload.CHILD_RESULT);
        var verifiedPath = caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT);
        var manifestChecksum = P4E0R2QFormalWorkload.readVerifiedManifestChecksum(
                caseRoot, control, caseIndex);
        var exitCode = readExitCode(caseRoot.resolve(P4E0R2QFormalWorkload.EXIT_MARKER));
        var marker = marker(caseRoot.resolve(P4E0R2QFormalWorkload.RUNNING_MARKER));
        var timeoutPath = caseRoot.resolve(P4E0R2QFormalWorkload.TIMEOUT_MARKER);
        var timeoutExists = Files.exists(timeoutPath, LinkOption.NOFOLLOW_LINKS);
        var timedOut = timeoutExists
                && P4E0R2QFormalEvidence.exactMarker(timeoutPath, TIMEOUT);
        if (timeoutExists && !timedOut) {
            throw new IOException("formal timeout marker is malformed");
        }
        var observation = observeChild(childPath, control, caseIndex, manifestChecksum);
        var parent = classifyParentEvidence(
                exitCode, marker, observation.validResult().isPresent(), timedOut);
        final P4E0R2QFormalResult result;
        if (parent == P4E0R2QFormalResult.ProcessClassification.TIMEOUT
                || parent == P4E0R2QFormalResult.ProcessClassification.OOME_EXIT) {
            result = P4E0R2QFormalWorkload.failedResult(
                    control, caseIndex, parent, "java.lang.Process", manifestChecksum);
        } else if (observation.invalidExisting()) {
            result = P4E0R2QFormalWorkload.failedResult(
                    control,
                    caseIndex,
                    P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE,
                    "java.io.IOException",
                    manifestChecksum);
        } else if (observation.validResult().isPresent()) {
            var child = observation.validResult().orElseThrow();
            if (child.processClassification()
                            == P4E0R2QFormalResult.ProcessClassification.COMPLETED
                    && parent != P4E0R2QFormalResult.ProcessClassification.COMPLETED) {
                throw new IOException("completed child lacks matching process evidence");
            }
            result = child;
        } else {
            result = P4E0R2QFormalWorkload.failedResult(
                    control, caseIndex, parent, "java.lang.Process", manifestChecksum);
        }
        if (result.processClassification()
                != P4E0R2QFormalResult.ProcessClassification.COMPLETED) {
            if (!Files.exists(verifiedPath, LinkOption.NOFOLLOW_LINKS)) {
                P4E0R2QFormalEvidence.writeResult(verifiedPath, result);
            }
            preserveFailure(workRoot, control, result.processClassification().name());
            throw new IOException("formal child did not complete: "
                    + result.processClassification().name());
        }
        P4E0R2QFormalEvidence.writeResult(verifiedPath, result);
        P4E0R2QFormalWorkload.cleanupLargeCaseData(caseRoot);
        P4E0R2QFormalEvidence.requireDiskBudget(workRoot, 0L);
    }

    /** Finalizer command used only when Gradle's 900-second JavaExec parent fails. */
    private static void captureFailure(
            Path workRoot,
            int caseIndex,
            String requestedFailure) throws IOException {
        requireFormalProperties();
        if (!Files.isDirectory(workRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var control = P4E0R2QFormalEvidence.readControl(workRoot.resolve(CONTROL_FILE));
        var caseRoot = P4E0R2QFormalEvidence.caseDirectory(workRoot, caseIndex);
        if (Files.exists(caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT),
                LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.exists(caseRoot.resolve(P4E0R2QFormalWorkload.EXIT_MARKER),
                LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var deadlinePath = caseRoot.resolve(PARENT_DEADLINE_MARKER);
        if (!Files.exists(deadlinePath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var deadline = readParentDeadline(deadlinePath);
        var child = caseRoot.resolve(P4E0R2QFormalWorkload.CHILD_RESULT);
        var manifestChecksum = manifestChecksumOrFallback(caseRoot, control, caseIndex);
        var observation = observeChild(child, control, caseIndex, manifestChecksum);
        var timeoutPath = caseRoot.resolve(P4E0R2QFormalWorkload.TIMEOUT_MARKER);
        var timeoutMarker = Files.exists(timeoutPath, LinkOption.NOFOLLOW_LINKS)
                && P4E0R2QFormalEvidence.exactMarker(timeoutPath, TIMEOUT);
        var deadlineTimeout = System.currentTimeMillis() >= deadline;
        var classification = timeoutMarker || deadlineTimeout
                ? P4E0R2QFormalResult.ProcessClassification.TIMEOUT
                : requestedParentClassification(requestedFailure);
        if (classification != P4E0R2QFormalResult.ProcessClassification.TIMEOUT) {
            if (observation.invalidExisting()) {
                classification = P4E0R2QFormalResult.ProcessClassification
                        .INSTRUMENTATION_FAILURE;
            } else if (observation.validResult().isPresent()
                    && observation.validResult().orElseThrow().processClassification()
                            != P4E0R2QFormalResult.ProcessClassification.COMPLETED) {
                classification = observation.validResult().orElseThrow()
                        .processClassification();
            }
        }
        if (classification == P4E0R2QFormalResult.ProcessClassification.TIMEOUT
                && !Files.exists(caseRoot.resolve(P4E0R2QFormalWorkload.TIMEOUT_MARKER),
                        LinkOption.NOFOLLOW_LINKS)) {
            P4E0R2QFormalEvidence.writeForcedMarker(
                    caseRoot.resolve(P4E0R2QFormalWorkload.TIMEOUT_MARKER), TIMEOUT);
        }
        var failed = P4E0R2QFormalWorkload.failedResult(
                control, caseIndex, classification,
                "org.gradle.process.ProcessExecutionException", manifestChecksum);
        if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) {
            P4E0R2QFormalEvidence.writeResult(child, failed);
        } else {
            P4E0R2QFormalEvidence.writeResult(
                    caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT), failed);
        }
        preserveFailure(workRoot, control, classification.name());
    }

    private static void aggregate(Path workRoot) throws IOException {
        requireFormalProperties();
        var control = P4E0R2QFormalEvidence.readControl(workRoot.resolve(CONTROL_FILE));
        try {
            var results = new ArrayList<P4E0R2QFormalResult>();
            for (var index = 0; index < P4E0R2QCasePlan.CASE_COUNT; index++) {
                var caseRoot = P4E0R2QFormalEvidence.caseDirectory(workRoot, index);
                var result = P4E0R2QFormalEvidence.readResult(
                        caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT));
                if (!result.hasFormalIdentity(control)
                        || !result.caseFixtureChecksum().equals(
                                P4E0R2QFormalWorkload.readVerifiedManifestChecksum(
                                        caseRoot, control, index))) {
                    throw new IOException("formal aggregation identity changed");
                }
                results.add(result);
            }
            P4E0R2QFormalEvidence.requireSuccessfulSet(results);
            P4E0R2QFormalEvidence.writeForcedMarker(
                    workRoot.resolve(AGGREGATE_MARKER), control.studyId() + "\n");
        } catch (IOException | RuntimeException exception) {
            preservePostprocessingFailure(workRoot, control, "AGGREGATE_FAILURE");
            throw exception;
        }
    }

    private static void verifyArtifacts(Path workRoot, Path officialRoot) throws IOException {
        requireFormalProperties();
        var control = P4E0R2QFormalEvidence.readControl(workRoot.resolve(CONTROL_FILE));
        try {
            if (!P4E0R2QFormalEvidence.exactMarker(
                    workRoot.resolve(AGGREGATE_MARKER), control.studyId() + "\n")) {
                throw new IOException("formal aggregate marker is absent");
            }
            P4E0R2QFormalEvidence.aggregateAndPublish(
                    workRoot, officialRoot, control,
                    P4E0R2QFormalEvidence.atomicDirectoryMover());
            P4E0R2QFormalEvidence.verifyOfficial(officialRoot, control);
        } catch (IOException | RuntimeException exception) {
            preservePostprocessingFailure(workRoot, control, "ARTIFACT_FAILURE");
            throw exception;
        }
    }

    private static void preservePostprocessingFailure(
            Path workRoot, P4E0R2QFormalEvidence.StudyControl control, String code)
            throws IOException {
        if (Files.isDirectory(workRoot, LinkOption.NOFOLLOW_LINKS)) {
            preserveFailure(workRoot, control, code);
        }
    }

    private static void runRunnerSmoke(
            Path workRoot, Path officialRoot, Path smokeRoot) throws IOException {
        if (Files.exists(smokeRoot, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isDirectory(smokeRoot, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(smokeRoot))) {
            throw new IOException("runner smoke root is not a directory");
        }
        Files.createDirectories(smokeRoot);
        P4E0R2QFormalEvidence.writeForcedMarker(
                smokeRoot.resolve(SMOKE_BASELINE), smokeEvidenceSnapshot(workRoot, officialRoot));
        writeSmokeSet(smokeRoot.resolve("standalone-supervisor.jsonl"), "standalone");
    }

    private static void verifyRunnerSmoke(
            Path workRoot, Path smokeRoot, Path officialRoot) throws IOException {
        requireSmokeSet(smokeRoot.resolve("standalone-supervisor.jsonl"), "standalone");
        requireSmokeSet(smokeRoot.resolve("runner/dedicated-supervisor.jsonl"), "dedicated");
        var before = boundedText(smokeRoot.resolve(SMOKE_BASELINE), 4_096);
        var after = smokeEvidenceSnapshot(workRoot, officialRoot);
        if (!before.equals(after)) {
            throw new IOException("runner smoke changed formal process or official evidence");
        }
        var report = "formal_children_started = 0\n"
                + "official_artifacts_published = 0\n"
                + "result = COMPLETED_NON_FORMAL_RUNNER_SMOKE\n";
        P4E0R2QFormalEvidence.writeForcedMarker(smokeRoot.resolve("runner-smoke.txt"), report);
    }

    private static void writeSmokeSet(Path path, String lane) throws IOException {
        var values = List.of(
                smokeResult(0, "positive", P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT),
                smokeResult(1, "counter",
                        P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER),
                smokeResult(2, "data_version",
                        P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_DATA_VERSION));
        var text = new StringBuilder("lane=").append(lane).append('\n');
        values.forEach(value -> text.append(value.toJsonLine()));
        P4E0R2QFormalEvidence.writeBoundedSmokeResults(path, text.toString());
    }

    private static void requireSmokeSet(Path path, String lane) throws IOException {
        var text = boundedText(path, 65_536);
        var lines = text.split("\n", -1);
        if (lines.length != 5 || !lines[0].equals("lane=" + lane)) {
            throw new IOException("runner smoke framing changed");
        }
        var expected = List.of(
                P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER,
                P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_DATA_VERSION);
        for (var index = 0; index < expected.size(); index++) {
            var result = P4E0R2QFormalResult.parseLine(lines[index + 1] + "\n");
            if (result.processClassification()
                            != P4E0R2QFormalResult.ProcessClassification.COMPLETED
                    || result.qualificationResult() != expected.get(index)
                    || result.caseIndex() != index
                    || !result.caseId().equals(List.of(
                                    "b1-smoke-positive",
                                    "b1-smoke-counter",
                                    "b1-smoke-data-version")
                            .get(index))) {
                throw new IOException("runner smoke classification tuple changed");
            }
        }
    }

    private static String smokeEvidenceSnapshot(Path workRoot, Path officialRoot)
            throws IOException {
        var canonical = new StringBuilder("schema_version=0\n");
        var processCount = 0;
        for (var index = 0; index < P4E0R2QCasePlan.CASE_COUNT; index++) {
            var caseRoot = P4E0R2QFormalEvidence.caseDirectory(workRoot, index);
            for (var name : List.of(
                    P4E0R2QFormalWorkload.CHILD_RESULT,
                    P4E0R2QFormalWorkload.VERIFIED_RESULT,
                    P4E0R2QFormalWorkload.RUNNING_MARKER,
                    P4E0R2QFormalWorkload.EXIT_MARKER,
                    P4E0R2QFormalWorkload.TIMEOUT_MARKER,
                    PARENT_DEADLINE_MARKER)) {
                var path = caseRoot.resolve(name);
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(path)
                            || Files.size(path) > P4E0R2QFormalResult.MAXIMUM_JSON_BYTES) {
                        throw new IOException("runner smoke found invalid formal process evidence");
                    }
                    processCount++;
                    canonical.append("process=").append(index).append(':').append(name)
                            .append(':').append(Files.size(path)).append(':')
                            .append(P4E0ResearchHashing.sha256(path)).append('\n');
                }
            }
        }
        var officialInspection = P4E0R2QFormalEvidence.inspectOfficialOutput(officialRoot);
        canonical.append("official_classification=")
                .append(officialInspection.classification()).append('\n');
        var officialCount = officialInspection.classification()
                        == P4E0R2QFormalEvidence.OfficialOutputClassification.VALID_OFFICIAL_SET
                ? P4E0R2QFormalEvidence.OFFICIAL_FILES.size() : 0;
        if (officialCount > 0) {
            for (var name : P4E0R2QFormalEvidence.OFFICIAL_FILES.stream().sorted().toList()) {
                var path = officialRoot.resolve(name);
                canonical.append("official=").append(name).append(':')
                        .append(Files.size(path)).append(':')
                        .append(P4E0ResearchHashing.sha256(path)).append('\n');
            }
        }
        return "formal_process_evidence_count=" + processCount
                + "\nofficial_artifact_count=" + officialCount
                + "\nstate_hash=" + P4E0ResearchHashing.sha256(canonical.toString()) + "\n";
    }

    private static P4E0R2QFormalResult smokeResult(
            int index, String code, P4E0R2QFormalResult.QualificationResult qualification) {
        var zero = zeroCounters();
        Optional<P4E0R2QProfile.Counter> target = qualification
                        == P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER
                ? Optional.of(P4E0R2QProfile.Counter.DIRECTORY_ENTRIES) : Optional.empty();
        var counters = target.isPresent()
                ? zero.with(P4E0R2QProfile.Counter.DIRECTORY_ENTRIES, 2L) : zero;
        var dataVersion = qualification
                == P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_DATA_VERSION;
        return new P4E0R2QFormalResult(
                "11".repeat(32), "b1-smoke-" + code.replace('_', '-'), index,
                "1".repeat(40), "2".repeat(40), "22".repeat(32), "33".repeat(32),
                "44".repeat(32), "55".repeat(32),
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalResult.ProcessClassification.COMPLETED, qualification,
                target, target.isPresent() ? 1L : 0L, target.isPresent() ? 2L : 0L,
                target.isPresent() ? "COUNTER_CAPACITY_EXCEEDED"
                        : dataVersion ? "DATA_VERSION_MISSING" : "NONE",
                target.isPresent() ? "COUNTER_CAPACITY_EXCEEDED"
                        : dataVersion ? "DATA_VERSION_MISSING" : "NONE",
                target.isPresent() ? "directory" : dataVersion ? "data_version" : "NONE",
                target.isPresent() ? "directory" : dataVersion ? "data_version" : "NONE",
                true, counters, 0L, 0L, 0L, 0L, 0L,
                P4E0R2QFormalResult.HeapFacts.unobserved(), 0L, "");
    }

    private static P4E0R2QProfile.CounterValues zeroCounters() {
        return new P4E0R2QProfile.CounterValues(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static Path requireFormalProperties() {
        if (!"true".equals(System.getProperty("gramarye.p4e0.r2q.formal.enabled"))) {
            throw new IllegalStateException("formal R2Q property is absent");
        }
        var budget = System.getProperty("gramarye.p4e0.r2q.formal.diskBudgetBytes");
        if (!Long.toString(P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES).equals(budget)) {
            throw new IllegalStateException("formal R2Q disk budget property changed");
        }
        return normalized(System.getProperty("gramarye.p4e0.r2q.formal.repositoryRoot"));
    }

    private static Revision requireCleanRepository(Path repository) throws IOException {
        if (!git(repository, "status", "--porcelain=v2", "--untracked-files=all").isEmpty()
                || gitExit(repository, "diff", "--exit-code") != 0
                || gitExit(repository, "diff", "--cached", "--exit-code") != 0
                || !git(repository, "symbolic-ref", "--quiet", "--short", "HEAD")
                        .equals("main\n")) {
            throw new IOException("formal R2Q repository gate rejected dirty or detached state");
        }
        var head = singleGitObject(git(repository, "rev-parse", "HEAD"));
        var tree = singleGitObject(git(repository, "rev-parse", "HEAD^{tree}"));
        var origin = singleGitObject(git(repository, "rev-parse", "origin/main"));
        if (!head.equals(origin)) {
            throw new IOException("formal R2Q HEAD differs from origin/main");
        }
        return new Revision(head, tree);
    }

    private static String git(Path root, String... arguments) throws IOException {
        var command = new ArrayList<String>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var reader = Executors.newSingleThreadExecutor()) {
            Future<String> output = reader.submit(
                    () -> boundedProcessOutput(process.getInputStream()));
            final boolean completed;
            try {
                completed = process.waitFor(GIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("formal git command was interrupted", exception);
            }
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("formal git command timed out");
            }
            final String text;
            try {
                text = output.get(5L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("formal git output drain was interrupted", exception);
            } catch (ExecutionException exception) {
                if (exception.getCause() instanceof IOException io) {
                    throw io;
                }
                throw new IOException("formal git output drain failed", exception);
            } catch (TimeoutException exception) {
                process.destroyForcibly();
                throw new IOException("formal git output drain timed out", exception);
            }
            if (process.exitValue() != 0) {
                throw new IOException("formal git command failed");
            }
            return text;
        }
    }

    private static int gitExit(Path root, String... arguments) throws IOException {
        try {
            git(root, arguments);
            return 0;
        } catch (IOException exception) {
            return 1;
        }
    }

    private static String boundedProcessOutput(InputStream input) throws IOException {
        var output = new ByteArrayOutputStream();
        var buffer = new byte[4_096];
        var total = 0;
        for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
            total = Math.addExact(total, read);
            if (total > MAXIMUM_GIT_OUTPUT_BYTES) {
                throw new IOException("formal git output exceeds bound");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static String singleGitObject(String value) throws IOException {
        if (!value.matches("[0-9a-f]{40}\\n")) {
            throw new IOException("formal git object output changed");
        }
        return value.stripTrailing();
    }

    private static int exactCase(String[] arguments) {
        if ((arguments.length != 5 && arguments.length != 6)
                || !arguments[1].matches("(?:[0-9]|1[0-9]|2[0-8])")) {
            throw new IllegalArgumentException("formal case argument is invalid");
        }
        return Integer.parseInt(arguments[1]);
    }

    private static String parentFailure(String[] arguments) {
        if (arguments.length != 6) {
            throw new IllegalArgumentException("parent failure classification is absent");
        }
        var value = arguments[2];
        if (!Set.of(
                        "AUTO_PARENT",
                        P4E0R2QFormalResult.ProcessClassification.TIMEOUT.name(),
                        P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE.name(),
                        P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE.name())
                .contains(value)) {
            throw new IllegalArgumentException("parent failure classification is invalid");
        }
        return value;
    }

    static void requirePairwiseDisjointRoots(Path workRoot, Path officialRoot, Path smokeRoot)
            throws IOException {
        var roots = List.of(
                workRoot.toAbsolutePath().normalize(),
                officialRoot.toAbsolutePath().normalize(),
                smokeRoot.toAbsolutePath().normalize());
        for (var leftIndex = 0; leftIndex < roots.size(); leftIndex++) {
            for (var rightIndex = leftIndex + 1; rightIndex < roots.size(); rightIndex++) {
                var left = roots.get(leftIndex);
                var right = roots.get(rightIndex);
                if (left.equals(right) || left.startsWith(right) || right.startsWith(left)) {
                    throw new IOException(
                            "formal official, smoke, and work roots are not disjoint");
                }
            }
        }
    }

    static void removeGeneratedSkeleton(Path repositoryRoot, Path workRoot) throws IOException {
        var repository = repositoryRoot.toAbsolutePath().normalize();
        var normalizedWork = workRoot.toAbsolutePath().normalize();
        if (!normalizedWork.equals(repository.resolve("build/p4-e0-r2q/formal"))) {
            throw new IOException("formal skeleton cleanup is outside its owned build root");
        }
        if (!Files.exists(workRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(workRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(workRoot)) {
            throw new IOException("formal work skeleton is not a directory");
        }
        var metadataFiles = new ArrayList<Path>();
        requireSkeletonDirectory(workRoot, Set.of("cases"), metadataFiles);
        var cases = workRoot.resolve("cases");
        var expected = new HashSet<String>();
        for (var index = 0; index < P4E0R2QCasePlan.CASE_COUNT; index++) {
            expected.add(String.format(java.util.Locale.ROOT, "%02d", index));
        }
        requireSkeletonDirectory(cases, Set.copyOf(expected), metadataFiles);
        for (var token : expected) {
            var caseRoot = cases.resolve(token);
            requireSkeletonDirectory(caseRoot, Set.of("game"), metadataFiles);
            requireSkeletonDirectory(caseRoot.resolve("game"), Set.of(), metadataFiles);
        }
        for (var metadata : metadataFiles) {
            Files.delete(metadata);
        }
        var ordered = new ArrayList<>(expected);
        ordered.sort(String::compareTo);
        for (var token : ordered.reversed()) {
            Files.delete(cases.resolve(token).resolve("game"));
            Files.delete(cases.resolve(token));
        }
        Files.delete(cases);
        Files.delete(workRoot);
    }

    private static void requireSkeletonDirectory(
            Path directory, Set<String> expectedDirectories, List<Path> metadataFiles)
            throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("formal skeleton contains a non-directory");
        }
        var observedDirectories = new HashSet<String>();
        try (var stream = Files.newDirectoryStream(directory)) {
            for (var child : stream) {
                var name = child.getFileName().toString();
                if (name.equals(P4E0R2QFormalEvidence.MACOS_METADATA_FILE)) {
                    if (Files.isSymbolicLink(child)
                            || !Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("formal skeleton metadata is not a regular file");
                    }
                    metadataFiles.add(child);
                } else if (expectedDirectories.contains(name)
                        && !Files.isSymbolicLink(child)
                        && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    observedDirectories.add(name);
                } else {
                    throw new IOException("formal skeleton contains unknown state");
                }
            }
        }
        if (!observedDirectories.equals(expectedDirectories)) {
            throw new IOException("formal generated skeleton is incomplete");
        }
    }

    private static int readExitCode(Path path) throws IOException {
        var text = boundedText(path, 64);
        if (!text.matches("-?[0-9]{1,10}\\n")) {
            throw new IOException("formal child exit marker is malformed");
        }
        try {
            return Integer.parseInt(text.stripTrailing());
        } catch (NumberFormatException exception) {
            throw new IOException("formal child exit marker is outside int", exception);
        }
    }

    private static long readParentDeadline(Path path) throws IOException {
        var text = boundedText(path, 128);
        if (!text.matches("deadline_epoch_millis=[1-9][0-9]{0,18}\\n")) {
            throw new IOException("formal parent deadline marker is malformed");
        }
        try {
            return Long.parseLong(text.substring(
                    "deadline_epoch_millis=".length(), text.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IOException("formal parent deadline marker is outside long", exception);
        }
    }

    private static P4E0R2QFormalResult.ProcessClassification requestedParentClassification(
            String requested) {
        if ("AUTO_PARENT".equals(requested)) {
            return P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE;
        }
        return P4E0R2QFormalResult.ProcessClassification.valueOf(requested);
    }

    private static ChildObservation observeChild(
            Path path,
            P4E0R2QFormalEvidence.StudyControl control,
            int caseIndex,
            String manifestChecksum) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return new ChildObservation(false, Optional.empty());
        }
        try {
            if (!regularBounded(path, P4E0R2QFormalResult.MAXIMUM_JSON_BYTES)) {
                return new ChildObservation(true, Optional.empty());
            }
            var result = P4E0R2QFormalEvidence.readResult(path);
            if (!result.hasFormalIdentity(control)
                    || result.caseIndex() != caseIndex
                    || !result.caseFixtureChecksum().equals(manifestChecksum)) {
                return new ChildObservation(true, Optional.empty());
            }
            return new ChildObservation(false, Optional.of(result));
        } catch (IOException | RuntimeException exception) {
            return new ChildObservation(true, Optional.empty());
        }
    }

    private static String manifestChecksumOrFallback(
            Path caseRoot, P4E0R2QFormalEvidence.StudyControl control, int caseIndex) {
        try {
            return P4E0R2QFormalWorkload.readVerifiedManifestChecksum(
                    caseRoot, control, caseIndex);
        } catch (IOException | RuntimeException exception) {
            return P4E0R2QFormalWorkload.expectedCaseFixtureChecksum(
                    P4E0R2QCasePlan.standard().cases().get(caseIndex));
        }
    }

    private static void preserveUnexpectedFailure(
            Path workRoot, P4E0R2QFormalEvidence.StudyControl control, int caseIndex)
            throws IOException {
        if (!Files.isDirectory(workRoot, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var caseRoot = P4E0R2QFormalEvidence.caseDirectory(workRoot, caseIndex);
        var verified = caseRoot.resolve(P4E0R2QFormalWorkload.VERIFIED_RESULT);
        if (!Files.exists(verified, LinkOption.NOFOLLOW_LINKS)) {
            var failed = P4E0R2QFormalWorkload.failedResult(
                    control,
                    caseIndex,
                    P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE,
                    "java.io.IOException",
                    manifestChecksumOrFallback(caseRoot, control, caseIndex));
            P4E0R2QFormalEvidence.writeResult(verified, failed);
        }
        preserveFailure(
                workRoot,
                control,
                P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE.name());
    }

    private static String marker(Path path) throws IOException {
        return Files.exists(path, LinkOption.NOFOLLOW_LINKS) ? boundedText(path, 4_096) : "";
    }

    private static boolean regularBounded(Path path, int maximum) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(path) && Files.size(path) <= maximum;
    }

    private static String boundedText(Path path, int maximum) throws IOException {
        if (!regularBounded(path, maximum)) {
            throw new IOException("bounded formal text is unavailable");
        }
        try (var input = Files.newInputStream(path)) {
            var bytes = input.readNBytes(maximum + 1);
            if (bytes.length > maximum || input.read() != -1) {
                throw new IOException("bounded formal text exceeds limit");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void preserveFailure(
            Path workRoot, P4E0R2QFormalEvidence.StudyControl control, String code)
            throws IOException {
        P4E0R2QFormalEvidence.preserveFailed(
                workRoot, workRoot.resolveSibling("failed-evidence"), control, code);
    }

    private static Path normalized(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("formal path argument is absent");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private record Revision(String head, String tree) {
    }

    private record ChildObservation(
            boolean invalidExisting, Optional<P4E0R2QFormalResult> validResult) {
        private ChildObservation {
            validResult = Objects.requireNonNull(validResult, "validResult");
            if (invalidExisting && validResult.isPresent()) {
                throw new IllegalArgumentException("invalid child evidence cannot be valid");
            }
        }
    }
}
