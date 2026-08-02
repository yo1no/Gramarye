package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.store.P4E0ResearchRootWorkloads;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;

/** Process supervisor and artifact publisher for the non-authoritative P4-E0-R2 study. */
public final class P4E0ResearchR2Main {
    private static final String CHILD_MODE = "--matrix-child";
    private static final String GIT_HEAD_PROPERTY = "gramarye.p4e0.gitHead";
    private static final String PLAN_FILE = "plan-v0.json";
    private static final String ACTIVE_LOCK = "matrix-child-active-v0.lock";
    private static final String RUNNING_MARKER_PREFIX = "P4_E0_R2_COMBINED_RUNNING_V0";
    private static final int OOME_EXIT_CODE = 3;
    private static final int TIMEOUT_EXIT_CODE = 124;
    private static final long CHILD_TERMINATION_SECONDS = 30L;
    private static final long MEBIBYTE = 1_048_576L;
    private static final String EMPTY_HASH = P4E0ResearchHashing.sha256("");

    private P4E0ResearchR2Main() {
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        if (args.length == 5 && CHILD_MODE.equals(args[0])) {
            runPlainChild(
                    exactRunIndex(args[1]),
                    Path.of(args[2]),
                    Path.of(args[3]),
                    exactPositiveLong(args[4], "disk budget"));
            return;
        }
        if (args.length == 7 && "classify-combined".equals(args[0])) {
            classifyCombined(
                    exactRunIndex(args[1]),
                    Path.of(args[2]),
                    Path.of(args[3]),
                    Path.of(args[4]),
                    Path.of(args[5]),
                    exactPositiveLong(args[6], "disk budget"));
            return;
        }
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "usage: prepare-plan|verify-fixtures|matrix|aggregate|validate "
                            + "<fixture-root> <report-root> <disk-budget-bytes>");
        }
        var fixtureRoot = requireFixtureRoot(Path.of(args[1]));
        var reportRoot = requireReportRoot(Path.of(args[2]));
        var diskBudget = exactPositiveLong(args[3], "disk budget");
        switch (args[0]) {
            case "prepare-plan" -> preparePlan(fixtureRoot, reportRoot, diskBudget);
            case "verify-fixtures" -> verifyPlanAndFixtureModel(
                    fixtureRoot, reportRoot, diskBudget);
            case "matrix" -> runPlainMatrix(fixtureRoot, reportRoot, diskBudget);
            case "aggregate" -> aggregate(fixtureRoot, reportRoot, diskBudget);
            case "validate" -> validatePublished(fixtureRoot, reportRoot, diskBudget);
            default -> throw new IllegalArgumentException("unknown P4-E0-R2 command");
        }
    }

    private static void preparePlan(Path fixtureRoot, Path reportRoot, long diskBudget)
            throws IOException {
        var plan = standardPlan();
        requirePlanShape(plan);
        Files.createDirectories(controlRoot(fixtureRoot));
        Files.createDirectories(reportRoot);
        var path = planPath(fixtureRoot);
        var expected = plan.canonicalJson() + System.lineSeparator();
        if (Files.exists(path)) {
            requireExactText(path, expected, 4L * MEBIBYTE);
        } else {
            P4E0ResearchRunRecord.atomicCreate(path, expected);
        }
        requireStudyIdentity(plan, diskBudget);
    }

    private static void verifyPlanAndFixtureModel(
            Path fixtureRoot, Path reportRoot, long diskBudget) throws IOException {
        preparePlan(fixtureRoot, reportRoot, diskBudget);
        var first = standardPlan();
        var second = standardPlan();
        if (!first.planHash().equals(second.planHash())
                || !first.canonicalJson().equals(second.canonicalJson())) {
            throw new IOException("R2 plan is not deterministic");
        }
        requirePlanShape(first);
        requireExactText(
                planPath(fixtureRoot),
                first.canonicalJson() + System.lineSeparator(),
                4L * MEBIBYTE);
    }

    private static void runPlainMatrix(
            Path fixtureRoot, Path reportRoot, long diskBudget) throws Exception {
        preparePlan(fixtureRoot, reportRoot, diskBudget);
        var plan = standardPlan();
        var studyId = requireStudyIdentity(plan, diskBudget);
        Files.createDirectories(matrixRoot(fixtureRoot));
        Files.createDirectories(rawRunsRoot(reportRoot));
        Files.createDirectories(childRunsRoot(reportRoot));
        var lockPath = controlRoot(fixtureRoot).resolve(ACTIVE_LOCK);
        try (var ignored = FileChannel.open(
                lockPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var observed = new ArrayList<P4E0ResearchRunRecord>();
            for (var spec : plan.runs()) {
                if (spec.mode() != P4E0ResearchMatrixPlan.Mode.PLAIN) {
                    continue;
                }
                var raw = rawRecordPath(reportRoot, spec);
                final P4E0ResearchRunRecord record;
                if (Files.exists(raw)) {
                    record = P4E0ResearchRunRecord.read(raw);
                    requireRecordIdentity(record, spec, plan, studyId);
                } else if (isConditionalDirectory(spec)
                        && !conditionalBaseAllows(
                                spec, observed, diskBudget, fixtureRoot)) {
                    record = syntheticRecord(
                            spec,
                            plan,
                            studyId,
                            P4E0ResearchResult.Classification
                                    .REJECTED_BY_RESEARCH_GUARD,
                            0,
                            false,
                            false,
                            true,
                            "",
                            0L,
                            environmentFor(matrixRoot(fixtureRoot)),
                            Map.of(
                                    "conditional_extension", 1L,
                                    "conditional_eligible", 0L,
                                    "conditional_executed", 0L));
                    record.writeNew(raw);
                } else {
                    record = supervisePlainChild(
                            spec, plan, studyId, fixtureRoot, reportRoot, diskBudget);
                    record.writeNew(raw);
                }
                observed.add(record);
                replacePartial(reportRoot, observed);
                cleanupPlainFixture(fixtureRoot, spec);
                if (isFatal(record.classification())) {
                    throw new IOException(
                            "R2 stopped at " + spec.runId() + " with "
                                    + record.classification().name());
                }
            }
            prepareCombinedProfiles(
                    fixtureRoot, reportRoot, diskBudget, plan, observed);
        } finally {
            Files.deleteIfExists(lockPath);
        }
    }

    private static P4E0ResearchRunRecord supervisePlainChild(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            Path fixtureRoot,
            Path reportRoot,
            long diskBudget) throws IOException {
        var childReport = childRecordPath(reportRoot, spec);
        Files.deleteIfExists(childReport);
        cleanupPlainFixture(fixtureRoot, spec);
        var started = System.nanoTime();
        final Process child;
        try {
            child = new ProcessBuilder(plainChildCommand(
                    spec, fixtureRoot, reportRoot, diskBudget))
                    .inheritIO()
                    .start();
        } catch (IOException exception) {
            return syntheticRecord(
                    spec,
                    plan,
                    studyId,
                    P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                    1,
                    false,
                    false,
                    false,
                    exception.getClass().getName(),
                    elapsedMillis(started),
                    environmentFor(matrixRoot(fixtureRoot)),
                    conditionalExecutionMetrics(spec, false));
        }

        var waitSeconds = Math.max(
                1L, spec.timeoutSeconds() - CHILD_TERMINATION_SECONDS);
        final boolean finished;
        try {
            finished = child.waitFor(waitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            terminate(child);
            Thread.currentThread().interrupt();
            return syntheticRecord(
                    spec,
                    plan,
                    studyId,
                    P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                    TIMEOUT_EXIT_CODE,
                    false,
                    false,
                    false,
                    exception.getClass().getName(),
                    elapsedMillis(started),
                    environmentFor(matrixRoot(fixtureRoot)),
                    conditionalExecutionMetrics(spec, true));
        }
        if (!finished) {
            terminate(child);
            var classification = classifyMissingChildResult(
                    TIMEOUT_EXIT_CODE, true, false);
            return syntheticRecord(
                    spec,
                    plan,
                    studyId,
                    classification,
                    TIMEOUT_EXIT_CODE,
                    true,
                    false,
                    false,
                    "java.util.concurrent.TimeoutException",
                    elapsedMillis(started),
                    environmentFor(matrixRoot(fixtureRoot)),
                    conditionalExecutionMetrics(spec, true));
        }

        var exitCode = child.exitValue();
        var reported = readMatchingChildRecord(
                childReport, spec, plan, studyId, exitCode);
        if (reported.isPresent()) {
            return reported.orElseThrow();
        }
        var classification = classifyMissingChildResult(exitCode, false, false);
        return syntheticRecord(
                spec,
                plan,
                studyId,
                classification,
                exitCode,
                false,
                classification == P4E0ResearchResult.Classification.OOME_EXIT,
                false,
                classification == P4E0ResearchResult.Classification.OOME_EXIT
                        ? "java.lang.OutOfMemoryError"
                        : "java.lang.IllegalStateException",
                elapsedMillis(started),
                environmentFor(matrixRoot(fixtureRoot)),
                conditionalExecutionMetrics(spec, true));
    }

    private static void runPlainChild(
            int runIndex, Path fixturePath, Path reportPath, long diskBudget)
            throws Exception {
        SharedConstants.tryDetectVersion();
        var fixtureRoot = requireFixtureRoot(fixturePath);
        var reportRoot = requireReportRoot(reportPath);
        var plan = standardPlan();
        var spec = plan.requireRun(runIndex);
        if (spec.mode() != P4E0ResearchMatrixPlan.Mode.PLAIN) {
            throw new IllegalArgumentException("dedicated run entered the plain child");
        }
        var studyId = requireStudyIdentity(plan, diskBudget);
        var output = childRecordPath(reportRoot, spec);
        Files.createDirectories(output.getParent());
        Files.deleteIfExists(output);
        Files.createDirectories(matrixRoot(fixtureRoot));
        var started = System.nanoTime();
        try (var sampler = P4E0ResearchResult.HeapSampler.start()) {
            try {
                var record = spec.matrix() == P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE
                        ? runRootChild(
                                spec, plan, studyId, fixtureRoot, sampler, started)
                        : runMatrixChild(
                                spec, plan, studyId, fixtureRoot, diskBudget,
                                sampler, started);
                record.writeNew(output);
            } catch (P4E0ResearchMatrixFixtures.ResearchGuardException exception) {
                failureFromChild(
                        spec,
                        plan,
                        studyId,
                        P4E0ResearchResult.Classification
                                .REJECTED_BY_RESEARCH_GUARD,
                        0,
                        exception,
                        sampler.finish(),
                        elapsedMillis(started),
                        fixtureFor(spec, fixtureRoot)).writeNew(output);
            } catch (IOException exception) {
                failureFromChild(
                        spec,
                        plan,
                        studyId,
                        P4E0ResearchResult.Classification.FIXTURE_INVALID,
                        1,
                        exception,
                        sampler.finish(),
                        elapsedMillis(started),
                        fixtureFor(spec, fixtureRoot)).writeNew(output);
                throw exception;
            } catch (RuntimeException exception) {
                failureFromChild(
                        spec,
                        plan,
                        studyId,
                        P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                        1,
                        exception,
                        sampler.finish(),
                        elapsedMillis(started),
                        fixtureFor(spec, fixtureRoot)).writeNew(output);
                throw exception;
            }
        }
    }

    private static P4E0ResearchRunRecord runMatrixChild(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            Path fixtureRoot,
            long diskBudget,
            P4E0ResearchResult.HeapSampler sampler,
            long started) throws IOException {
        var request = P4E0ResearchR2PlanFactory.plainRequest(
                spec, fixtureFor(spec, fixtureRoot), diskBudget);
        var observation = P4E0ResearchMatrixRunner.prepareAndRun(request);
        observation.retainAtSamplingPoint();
        var heap = sampler.finish();
        requireHeap(spec, heap);
        observation.retainAtSamplingPoint();
        var metrics = metrics(observation, heap);
        addConditionalExecutionMetrics(metrics, spec, true);
        var fixture = new P4E0ResearchRunRecord.FixtureEvidence(
                spec.fixtureId(),
                observation.fixture().treeHash(),
                observation.fixture().physicalBytes(),
                observation.observedPhysicalBytes(),
                observation.observedDecompressedBytes());
        return completedRecord(
                spec, plan, studyId, elapsedMillis(started),
                environmentFor(observation.fixture().root()), metrics, fixture);
    }

    private static P4E0ResearchRunRecord runRootChild(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            Path fixtureRoot,
            P4E0ResearchResult.HeapSampler sampler,
            long started) throws IOException {
        var root = fixtureFor(spec, fixtureRoot);
        if (Files.exists(root)) {
            throw new IOException("root-vector fixture already exists");
        }
        Files.createDirectories(root);
        var descriptor = root.resolve("root-vector-v0.txt");
        var descriptorText = spec.shape() + '|' + spec.coordinate() + '|'
                + P4E0ResearchR2PlanFactory.DEFAULT_SEED + '\n';
        Files.writeString(
                descriptor, descriptorText, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE_NEW);
        var capture = rootCapture(spec.shape());
        capture.retainAtPeak();
        var heap = sampler.finish();
        requireHeap(spec, heap);
        capture.retainAtPeak();
        var rootMetrics = capture.metrics();
        var metrics = heapMetrics(heap);
        addConditionalExecutionMetrics(metrics, spec, true);
        metrics.put("raw_root_count", (long) rootMetrics.rawRootCount());
        metrics.put("distinct_root_count", (long) rootMetrics.distinctRootCount());
        metrics.put("duplicate_root_count", (long) rootMetrics.duplicateRootCount());
        metrics.put("player_root_count", (long) rootMetrics.playerRootCount());
        metrics.put("journal_root_count", (long) rootMetrics.journalRootCount());
        metrics.put("first_missing_index_plus_one",
                Math.addExact((long) rootMetrics.firstMissingIndex(), 1L));
        metrics.put("roots_examined", (long) rootMetrics.rootsExamined());
        metrics.put("admission_ordinal", (long) rootMetrics.admission().ordinal());
        metrics.put("workload_admitted",
                rootMetrics.admission() == P4E0ResearchRootWorkloads.Admission.OVER_LIMIT
                        ? 0L : 1L);
        metrics.put("root_over_limit_observed",
                rootMetrics.admission() == P4E0ResearchRootWorkloads.Admission.OVER_LIMIT
                        ? 1L : 0L);
        var physical = Files.size(descriptor);
        var fixture = new P4E0ResearchRunRecord.FixtureEvidence(
                spec.fixtureId(), P4E0ResearchHashing.sha256(descriptor),
                physical, physical, 0L);
        return completedRecord(
                spec, plan, studyId, elapsedMillis(started),
                environmentFor(root), metrics, fixture);
    }

    private static P4E0ResearchRootWorkloads.Capture rootCapture(String shape) {
        return switch (shape) {
            case "EXACT_ALL_DISTINCT" -> P4E0ResearchRootWorkloads.exactAllDistinct();
            case "OVER_LIMIT_ALL_DISTINCT" ->
                    P4E0ResearchRootWorkloads.overLimitAllDistinct();
            case "EXACT_NINETY_PERCENT_DUPLICATES" ->
                    P4E0ResearchRootWorkloads.exactNinetyPercentDuplicates();
            case "OVER_LIMIT_NINETY_PERCENT_DUPLICATES" ->
                    P4E0ResearchRootWorkloads.overLimitNinetyPercentDuplicates();
            case "PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL" ->
                    P4E0ResearchRootWorkloads.playerRootsPlusMaximumJournal();
            case "FIRST_MISSING_BEGINNING" ->
                    P4E0ResearchRootWorkloads.firstMissingBeginning();
            case "FIRST_MISSING_MIDDLE" ->
                    P4E0ResearchRootWorkloads.firstMissingMiddle();
            case "FIRST_MISSING_END" -> P4E0ResearchRootWorkloads.firstMissingEnd();
            default -> throw new IllegalArgumentException("unknown root-capture shape");
        };
    }

    private static void prepareCombinedProfiles(
            Path fixtureRoot,
            Path reportRoot,
            long diskBudget,
            P4E0ResearchMatrixPlan plan,
            List<P4E0ResearchRunRecord> plainRecords) throws IOException {
        var completed = plainRecords.stream()
                .filter(record -> record.classification()
                        == P4E0ResearchResult.Classification.COMPLETED)
                .toList();
        var retainedBytes = combinedTreeBytes(fixtureRoot);
        for (var spec : plan.runs()) {
            if (spec.mode() != P4E0ResearchMatrixPlan.Mode.DEDICATED) {
                continue;
            }
            var profilePath = combinedProfilePath(fixtureRoot, spec);
            if (Files.exists(profilePath)) {
                var existing = P4E0ResearchCombinedProfileFile.read(profilePath);
                requireCombinedProfile(existing, spec, plan);
                continue;
            }
            var directoryRecord = selectDirectory(spec, completed);
            var playerdataRecord = selectPlayerdata(spec, completed);
            var estimate = Math.addExact(
                    directoryRecord.fixture().physicalBytes(),
                    playerdataRecord.fixture().physicalBytes());
            if (Math.addExact(retainedBytes, estimate) > diskBudget) {
                throw new P4E0ResearchMatrixFixtures.ResearchGuardException(
                        "combined_disk_budget");
            }
            var directoryRequest = P4E0ResearchR2PlanFactory.plainRequest(
                    directoryRecord.spec(),
                    matrixRoot(fixtureRoot).resolve("combined-directory-selection"),
                    diskBudget);
            var playerdataRequest = P4E0ResearchR2PlanFactory.plainRequest(
                    playerdataRecord.spec(),
                    matrixRoot(fixtureRoot).resolve("combined-playerdata-selection"),
                    diskBudget);
            var root = combinedInputRoot(fixtureRoot, spec);
            var remainingBudget = Math.subtractExact(diskBudget, retainedBytes);
            if (remainingBudget <= 0L) {
                throw new P4E0ResearchMatrixFixtures.ResearchGuardException(
                        "combined_disk_budget");
            }
            var input = P4E0ResearchMatrixFixtures.materializeCombinedInput(
                    new P4E0ResearchMatrixFixtures.CombinedInputSpec(
                            directoryRequest, playerdataRequest, root, remainingBudget));
            if (!input.directory().treeHash().equals(
                            directoryRecord.fixture().fixtureHash())
                    || !input.playerdata().treeHash().equals(
                            playerdataRecord.fixture().fixtureHash())) {
                throw new IOException("combined input differs from selected completed runs");
            }
            retainedBytes = Math.addExact(retainedBytes, input.actualBytes());
            if (retainedBytes > diskBudget) {
                throw new P4E0ResearchMatrixFixtures.ResearchGuardException(
                        "combined_disk_budget");
            }
            var selectedHash = P4E0ResearchHashing.sha256(input.selectedPlayerdata());
            var profile = new P4E0ResearchCombinedEnvelope.Profile(
                    P4E0ResearchCombinedEnvelope.ProfileKind.valueOf(spec.profile()),
                    spec.heapMiB(),
                    input.directory().root(),
                    Math.toIntExact(input.directory().directoryEntries()),
                    directoryRecord.spec().shape(),
                    input.selectedPlayerdata(),
                    playerdataRecord.spec().fixtureId(),
                    playerdataRecord.spec().shape(),
                    input.playerdata().physicalBytes(),
                    input.playerdata().decompressedBytes(),
                    selectedHash,
                    playerdataRequest.maximumCompressedBytes(),
                    playerdataRequest.maximumDecompressedBytes(),
                    playerdataRequest.nbtQuotaBytes());
            P4E0ResearchCombinedProfileFile.writeNew(
                    profilePath,
                    new P4E0ResearchCombinedProfileFile.Value(
                            plan.planHash(), spec.runIndex(), profile,
                            input.integrityHash()));
        }
        replacePartial(reportRoot, plainRecords);
    }

    private static P4E0ResearchRunRecord selectDirectory(
            P4E0ResearchMatrixPlan.RunSpec combined,
            List<P4E0ResearchRunRecord> completed) throws IOException {
        var shape = switch (combined.profile()) {
            case "BALANCED" -> "ONE_PERCENT_READY";
            case "DIRECTORY_HEAVY" -> "ALL_ZERO_ROOT";
            case "SINGLE_FILE_HEAVY" -> "ALL_IRRELEVANT";
            default -> throw new IOException("unknown combined profile");
        };
        var candidates = completed.stream()
                .filter(record -> record.spec().matrix()
                        == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)
                .filter(record -> record.spec().heapMiB() == combined.heapMiB())
                .filter(record -> record.spec().shape().equals(shape))
                .filter(record -> !combined.profile().equals("BALANCED")
                        || record.spec().coordinate() <= 4_096L)
                .filter(record -> !combined.profile().equals("SINGLE_FILE_HEAVY")
                        || record.spec().coordinate() <= 64L)
                .toList();
        return candidates.stream()
                .max(Comparator.comparingLong(record -> record.spec().coordinate()))
                .orElseThrow(() -> new IOException(
                        "combined profile has no completed directory coordinate"));
    }

    private static P4E0ResearchRunRecord selectPlayerdata(
            P4E0ResearchMatrixPlan.RunSpec combined,
            List<P4E0ResearchRunRecord> completed) throws IOException {
        return completed.stream()
                .filter(record -> record.spec().matrix()
                        == P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE)
                .filter(record -> record.spec().heapMiB() == combined.heapMiB())
                .max(Comparator
                        .comparingLong((P4E0ResearchRunRecord record) ->
                                record.fixture().actualDecompressedBytes())
                        .thenComparingLong(record ->
                                record.fixture().actualCompressedBytes()))
                .orElseThrow(() -> new IOException(
                        "combined profile has no completed playerdata coordinate"));
    }

    private static void aggregate(
            Path fixtureRoot, Path reportRoot, long diskBudget) throws IOException {
        verifyPlanAndFixtureModel(fixtureRoot, reportRoot, diskBudget);
        var plan = standardPlan();
        var studyId = requireStudyIdentity(plan, diskBudget);
        var records = loadCompleteRecords(reportRoot, plan, studyId);
        var manifest = buildManifest(fixtureRoot, diskBudget, plan, studyId, records);
        var formal = List.of(
                P4E0ResearchReportAggregator.RUNS_JSONL,
                P4E0ResearchReportAggregator.FRONTIERS_CSV,
                P4E0ResearchReportAggregator.SUMMARY_MARKDOWN,
                P4E0ResearchReportAggregator.FIXTURE_MANIFEST_JSON);
        var present = formal.stream().filter(name -> Files.exists(reportRoot.resolve(name)))
                .count();
        if (present == formal.size()) {
            requireManifestIdentity(
                    P4E0ResearchFixtureManifest.read(reportRoot.resolve(
                            P4E0ResearchReportAggregator.FIXTURE_MANIFEST_JSON)),
                    plan,
                    studyId,
                    diskBudget);
            P4E0ResearchReportAggregator.verifyPublished(reportRoot, plan);
        } else if (present != 0L) {
            throw new IOException("partial formal R2 report publication exists");
        } else {
            P4E0ResearchReportAggregator.writeStudy(
                    reportRoot, plan, manifest, records);
        }
    }

    private static void validatePublished(
            Path fixtureRoot, Path reportRoot, long diskBudget) throws IOException {
        verifyPlanAndFixtureModel(fixtureRoot, reportRoot, diskBudget);
        var plan = standardPlan();
        var studyId = requireStudyIdentity(plan, diskBudget);
        requireManifestIdentity(
                P4E0ResearchFixtureManifest.read(reportRoot.resolve(
                        P4E0ResearchReportAggregator.FIXTURE_MANIFEST_JSON)),
                plan,
                studyId,
                diskBudget);
        P4E0ResearchReportAggregator.verifyPublished(reportRoot, plan);
        var records = P4E0ResearchReportAggregator.readJsonl(
                reportRoot.resolve(P4E0ResearchReportAggregator.RUNS_JSONL),
                plan.runCount());
        if (records.size() != plan.runCount()) {
            throw new IOException("published R2 run count differs from plan");
        }
        var forbidden = List.of(
                "recommended" + "_max",
                "safe" + "_max",
                "production" + "_limit",
                "authority" + "_value");
        for (var output : List.of(
                P4E0ResearchReportAggregator.RUNS_JSONL,
                P4E0ResearchReportAggregator.FRONTIERS_CSV,
                P4E0ResearchReportAggregator.SUMMARY_MARKDOWN,
                P4E0ResearchReportAggregator.FIXTURE_MANIFEST_JSON)) {
            var text = Files.readString(reportRoot.resolve(output), StandardCharsets.UTF_8);
            for (var token : forbidden) {
                if (text.contains(token)) {
                    throw new IOException("R2 report contains forbidden authority vocabulary");
                }
            }
        }
        var summary = Files.readString(
                reportRoot.resolve(P4E0ResearchReportAggregator.SUMMARY_MARKDOWN),
                StandardCharsets.UTF_8);
        if (!summary.contains(P4E0ResearchMatrixPlan.DISCLAIMER)) {
            throw new IOException("R2 report omitted the exact non-authoritative disclaimer");
        }
    }

    private static P4E0ResearchFixtureManifest buildManifest(
            Path fixtureRoot,
            long diskBudget,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            List<P4E0ResearchRunRecord> records) throws IOException {
        var byFixture = records.stream().collect(Collectors.groupingBy(
                record -> record.spec().fixtureId(), LinkedHashMap::new,
                Collectors.toList()));
        var entries = new ArrayList<P4E0ResearchFixtureManifest.Entry>();
        for (var group : byFixture.entrySet()) {
            var completed = group.getValue().stream()
                    .filter(record -> record.classification()
                            == P4E0ResearchResult.Classification.COMPLETED)
                    .toList();
            if (!completed.isEmpty()) {
                var expected = completed.getFirst().fixture();
                if (completed.stream().anyMatch(record ->
                        !record.fixture().equals(expected))) {
                    throw new IOException(
                            "same R2 fixture produced different evidence across heaps");
                }
            }
            var selected = completed.isEmpty()
                    ? group.getValue().getFirst() : completed.getFirst();
            var evidence = selected.fixture();
            var fileCount = selected.metrics().getOrDefault(
                    "fixture_file_count",
                    selected.metrics().getOrDefault("directory_entries", 1L));
            var generation = group.getValue().stream().anyMatch(record ->
                    record.classification()
                            == P4E0ResearchResult.Classification.COMPLETED)
                    ? "MATERIALIZED" : "OBSERVATION_ONLY";
            entries.add(new P4E0ResearchFixtureManifest.Entry(
                    group.getKey(),
                    selected.spec().matrix(),
                    selected.spec().axis(),
                    selected.spec().shape(),
                    selected.spec().coordinate(),
                    fileCount,
                    evidence.physicalBytes(),
                    evidence.actualCompressedBytes(),
                    evidence.actualDecompressedBytes(),
                    evidence.fixtureHash(),
                    generation));
        }
        var conditional = plan.runs().stream()
                .filter(P4E0ResearchR2Main::isConditionalDirectory)
                .map(spec -> {
                    var sourceIds = List.of(findConditionalBase(plan, spec).runId());
                    var run = records.stream()
                            .filter(record -> record.spec().runIndex() == spec.runIndex())
                            .findFirst()
                            .orElseThrow();
                    var materialized = run.metrics().getOrDefault(
                            "conditional_executed", 0L) == 1L;
                    return new P4E0ResearchFixtureManifest.ConditionalPoint(
                            spec.runId(),
                            materialized
                                    ? P4E0ResearchFixtureManifest.ConditionalDecision
                                            .MATERIALIZED
                                    : P4E0ResearchFixtureManifest.ConditionalDecision.SKIPPED,
                            materialized ? "ELIGIBLE_POINT_EXECUTED"
                                    : "BASE_POINT_NOT_ELIGIBLE",
                            sourceIds);
                })
                .toList();
        var fixtureRootHash = P4E0ResearchHashing.sha256(entries.stream()
                .sorted(Comparator.comparing(P4E0ResearchFixtureManifest.Entry::fixtureId))
                .map(entry -> entry.fixtureId() + '|' + entry.hash() + '|'
                        + entry.physicalBytes())
                .collect(Collectors.joining("\n")));
        return new P4E0ResearchFixtureManifest(
                requiredGitHead(),
                studyId,
                plan.planHash(),
                P4E0ResearchR2PlanFactory.DEFAULT_SEED,
                diskBudget,
                fixtureRootHash,
                P4E0ResearchFixtureManifest.BaseFixtureVerification.VERIFIED,
                plan.runCount(),
                entries,
                conditional);
    }

    private static List<P4E0ResearchRunRecord> loadCompleteRecords(
            Path reportRoot, P4E0ResearchMatrixPlan plan, String studyId)
            throws IOException {
        var records = new ArrayList<P4E0ResearchRunRecord>(plan.runCount());
        for (var spec : plan.runs()) {
            var record = P4E0ResearchRunRecord.read(rawRecordPath(reportRoot, spec));
            requireRecordIdentity(record, spec, plan, studyId);
            if (isFatal(record.classification())) {
                throw new IOException("R2 contains a fatal child classification");
            }
            records.add(record);
        }
        return List.copyOf(records);
    }

    private static void classifyCombined(
            int runIndex,
            Path fixturePath,
            Path reportPath,
            Path exitPath,
            Path markerPath,
            long diskBudget) throws IOException {
        var fixtureRoot = requireFixtureRoot(fixturePath);
        var reportRoot = requireReportRoot(reportPath);
        var plan = standardPlan();
        var spec = plan.requireRun(runIndex);
        if (spec.mode() != P4E0ResearchMatrixPlan.Mode.DEDICATED) {
            throw new IllegalArgumentException("plain run entered dedicated classifier");
        }
        var studyId = requireStudyIdentity(plan, diskBudget);
        var raw = rawRecordPath(reportRoot, spec);
        if (Files.exists(raw)) {
            var existing = P4E0ResearchRunRecord.read(raw);
            requireRecordIdentity(existing, spec, plan, studyId);
            return;
        }
        var exactMarker = hasExactCombinedMarker(markerPath, spec, plan);
        final P4E0ResearchRunRecord record;
        if (!Files.isRegularFile(exitPath, LinkOption.NOFOLLOW_LINKS)
                || Files.size(exitPath) > 16L) {
            var classification = classifyMissingChildResult(
                    exactMarker ? TIMEOUT_EXIT_CODE : 0,
                    false,
                    exactMarker);
            record = syntheticCombinedRecord(
                    spec,
                    plan,
                    studyId,
                    classification,
                    exactMarker ? TIMEOUT_EXIT_CODE : 1,
                    exactMarker,
                    false,
                    false,
                    exactMarker
                            ? "java.util.concurrent.TimeoutException"
                            : "java.lang.IllegalStateException",
                    0L,
                    environmentFor(combinedInputRoot(fixtureRoot, spec)),
                    fixtureRoot);
        } else {
            final int exitCode;
            try {
                var text = Files.readString(exitPath, StandardCharsets.US_ASCII).trim();
                if (!text.matches("0|[1-9][0-9]{0,9}")) {
                    throw new NumberFormatException("invalid exit code");
                }
                exitCode = Integer.parseInt(text);
            } catch (RuntimeException exception) {
                throw new IOException("combined exit file is malformed", exception);
            }
            var child = readMatchingChildRecord(
                    combinedChildRecordPath(reportRoot, spec),
                    spec,
                    plan,
                    studyId,
                    exitCode);
            if (child.isPresent()) {
                record = child.orElseThrow();
            } else {
                var classification = classifyMissingChildResult(
                        exitCode, false, exactMarker);
                record = syntheticCombinedRecord(
                        spec,
                        plan,
                        studyId,
                        classification,
                        exitCode,
                        classification == P4E0ResearchResult.Classification.TIMEOUT,
                        classification == P4E0ResearchResult.Classification.OOME_EXIT,
                        false,
                        classification == P4E0ResearchResult.Classification.OOME_EXIT
                                ? "java.lang.OutOfMemoryError"
                                : classification
                                                == P4E0ResearchResult.Classification.TIMEOUT
                                        ? "java.util.concurrent.TimeoutException"
                                        : "java.lang.IllegalStateException",
                        0L,
                        environmentFor(combinedInputRoot(fixtureRoot, spec)),
                        fixtureRoot);
            }
        }
        record.writeNew(raw);
        replacePartial(reportRoot, loadAvailableRawRecords(reportRoot, plan, studyId));
        if (isFatal(record.classification())) {
            throw new IOException(
                    "R2 stopped at " + spec.runId() + " with "
                            + record.classification().name());
        }
    }

    static P4E0ResearchRunRecord completedCombinedRecord(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            long diskBudget,
            P4E0ResearchCombinedProfileFile.Value profile,
            P4E0ResearchCombinedEnvelope.Metrics envelope,
            P4E0ResearchResult.HeapMetrics heap,
            long elapsedMillis,
            Path environmentPath) throws IOException {
        var metrics = combinedMetrics(envelope, heap);
        var sources = combinedSources(plan, spec, profile);
        metrics.put("selected_directory_run_index",
                (long) sources.directory().runIndex());
        metrics.put("selected_playerdata_run_index",
                (long) sources.playerdata().runIndex());
        var store = envelope.storeMetrics();
        var hash = P4E0ResearchHashing.sha256(
                profile.inputIntegrityHash() + '|' + store.storeChecksum() + '|'
                        + store.filteredCarrierChecksum() + '|' + spec.profile());
        var physical = Math.addExact(
                envelope.directoryReferencedFileBytes(),
                Math.addExact(
                        envelope.selectedPhysicalBytes(),
                        store.storeBytes()));
        var fixture = new P4E0ResearchRunRecord.FixtureEvidence(
                spec.fixtureId(), hash, physical,
                envelope.selectedPhysicalBytes(),
                envelope.selectedDecompressedBytes());
        return completedRecord(
                spec,
                plan,
                requireStudyIdentity(plan, diskBudget),
                elapsedMillis,
                environmentFor(environmentPath),
                metrics,
                fixture);
    }

    static P4E0ResearchRunRecord failedCombinedRecord(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            long diskBudget,
            Exception exception,
            P4E0ResearchResult.Classification classification,
            int exitCode,
            Path environmentPath) throws IOException {
        return syntheticRecord(
                spec,
                plan,
                requireStudyIdentity(plan, diskBudget),
                classification,
                exitCode,
                false,
                false,
                true,
                exception.getClass().getName(),
                0L,
                environmentFor(environmentPath),
                Map.of());
    }

    static P4E0ResearchMatrixPlan standardPlan() {
        return P4E0ResearchR2PlanFactory.standardPlan();
    }

    static void verifyCombinedProfileInput(
            P4E0ResearchCombinedProfileFile.Value value,
            P4E0ResearchMatrixPlan.RunSpec combined,
            P4E0ResearchMatrixPlan plan,
            long diskBudget) throws IOException {
        var sources = combinedSources(plan, combined, value);
        var profile = value.profile();
        var directoryRequest = P4E0ResearchR2PlanFactory.plainRequest(
                sources.directory(), profile.directory(), diskBudget);
        var playerdataRoot = profile.selectedPlayerdata().getParent();
        if (playerdataRoot == null) {
            throw new IOException("combined playerdata has no fixture root");
        }
        var playerdataRequest = P4E0ResearchR2PlanFactory.plainRequest(
                sources.playerdata(), playerdataRoot, diskBudget);
        var directory = P4E0ResearchMatrixFixtures.verify(
                directoryRequest, profile.directory());
        var playerdata = P4E0ResearchMatrixFixtures.verify(
                playerdataRequest, playerdataRoot);
        if (directory.directoryEntries() != profile.directoryEntries()
                || playerdata.payloadFiles().size() != 1
                || !playerdata.payloadFiles().getFirst().equals(
                        profile.selectedPlayerdata())
                || playerdata.physicalBytes() != profile.selectedPhysicalBytes()
                || playerdata.decompressedBytes()
                        != profile.selectedDecompressedBytes()
                || !P4E0ResearchHashing.sha256(profile.selectedPlayerdata())
                        .equals(profile.selectedPlayerdataSha256())) {
            throw new IOException("combined profile fixture facts changed");
        }
        var actualBytes = Math.addExact(
                directory.physicalBytes(), playerdata.physicalBytes());
        var integrity = P4E0ResearchHashing.sha256(
                directory.treeHash() + '|' + playerdata.treeHash() + '|'
                        + actualBytes + '|' + directoryRequest.profile().name() + '|'
                        + playerdataRequest.profile().name());
        if (!integrity.equals(value.inputIntegrityHash())) {
            throw new IOException("combined profile input integrity changed");
        }
    }

    static Path combinedProfilePath(
            Path fixtureRoot, P4E0ResearchMatrixPlan.RunSpec spec) {
        return combinedInputRoot(requireFixtureRoot(fixtureRoot), spec)
                .resolve("profile-v0.json");
    }

    static Path combinedChildRecordPath(
            Path reportRoot, P4E0ResearchMatrixPlan.RunSpec spec) {
        return requireReportRoot(reportRoot).resolve("combined-child")
                .resolve(recordFileName(spec));
    }

    static Path combinedRunningMarkerPath(
            Path reportRoot, P4E0ResearchMatrixPlan.RunSpec spec) {
        return requireReportRoot(reportRoot).resolve("combined-running")
                .resolve(String.format(
                        Locale.ROOT, "%04d-%s.marker", spec.runIndex(), spec.runId()));
    }

    static String combinedRunningMarkerContent(
            P4E0ResearchMatrixPlan.RunSpec spec, P4E0ResearchMatrixPlan plan) {
        return RUNNING_MARKER_PREFIX + '|' + spec.runIndex() + '|'
                + spec.runId() + '|' + plan.planHash() + '\n';
    }

    static String requireStudyIdentity(P4E0ResearchMatrixPlan plan, long diskBudget) {
        return P4E0ResearchHashing.sha256(
                plan.planHash() + '|' + requiredGitHead() + '|'
                        + P4E0ResearchR2PlanFactory.DEFAULT_SEED + '|' + diskBudget);
    }

    static long exactPositiveLong(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException(label + " is not a positive decimal");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " is outside long range", exception);
        }
    }

    /** Pure parent-side classification; no result is inferred from child stdout. */
    static P4E0ResearchResult.Classification classifyMissingChildResult(
            int exitCode, boolean parentTimedOut, boolean exactDedicatedMarker) {
        if (exitCode < 0) {
            throw new IllegalArgumentException("negative child exit code");
        }
        if (parentTimedOut || (exitCode == TIMEOUT_EXIT_CODE && exactDedicatedMarker)) {
            return P4E0ResearchResult.Classification.TIMEOUT;
        }
        if (exitCode == OOME_EXIT_CODE) {
            return P4E0ResearchResult.Classification.OOME_EXIT;
        }
        if (exitCode == 0) {
            return P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE;
        }
        return P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE;
    }

    private static P4E0ResearchRunRecord failureFromChild(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            P4E0ResearchResult.Classification classification,
            int exitCode,
            Exception exception,
            P4E0ResearchResult.HeapMetrics heap,
            long elapsed,
            Path fixturePath) throws IOException {
        return syntheticRecord(
                spec,
                plan,
                studyId,
                classification,
                exitCode,
                false,
                false,
                true,
                exception.getClass().getName(),
                elapsed,
                environmentForNearest(fixturePath),
                withConditionalExecution(heapMetrics(heap), spec, true));
    }

    private static P4E0ResearchRunRecord completedRecord(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            long elapsed,
            P4E0ResearchRunRecord.Environment environment,
            Map<String, Long> metrics,
            P4E0ResearchRunRecord.FixtureEvidence fixture) {
        return new P4E0ResearchRunRecord(
                studyId,
                plan.planHash(),
                spec,
                P4E0ResearchResult.Classification.COMPLETED,
                new P4E0ResearchRunRecord.ProcessResult(0, false, false, true, ""),
                elapsed,
                environment,
                metrics,
                fixture);
    }

    private static P4E0ResearchRunRecord syntheticRecord(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            P4E0ResearchResult.Classification classification,
            int exitCode,
            boolean timedOut,
            boolean oome,
            boolean reportObserved,
            String failureClass,
            long elapsed,
            P4E0ResearchRunRecord.Environment environment,
            Map<String, Long> metrics) {
        return new P4E0ResearchRunRecord(
                studyId,
                plan.planHash(),
                spec,
                classification,
                new P4E0ResearchRunRecord.ProcessResult(
                        exitCode, timedOut, oome, reportObserved, failureClass),
                elapsed,
                environment,
                metrics,
                new P4E0ResearchRunRecord.FixtureEvidence(
                        spec.fixtureId(), EMPTY_HASH, 0L, 0L, 0L));
    }

    private static P4E0ResearchRunRecord syntheticCombinedRecord(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            P4E0ResearchResult.Classification classification,
            int exitCode,
            boolean timedOut,
            boolean oome,
            boolean reportObserved,
            String failureClass,
            long elapsed,
            P4E0ResearchRunRecord.Environment environment,
            Path fixtureRoot) throws IOException {
        var value = P4E0ResearchCombinedProfileFile.read(
                combinedProfilePath(fixtureRoot, spec));
        requireCombinedProfile(value, spec, plan);
        var sources = combinedSources(plan, spec, value);
        var profile = value.profile();
        var metrics = new LinkedHashMap<String, Long>();
        metrics.put("directory_entries", (long) profile.directoryEntries());
        metrics.put("selected_physical_bytes", profile.selectedPhysicalBytes());
        metrics.put("selected_decompressed_bytes", profile.selectedDecompressedBytes());
        metrics.put("selected_directory_run_index", (long) sources.directory().runIndex());
        metrics.put("selected_playerdata_run_index", (long) sources.playerdata().runIndex());
        var fixtureHash = P4E0ResearchHashing.sha256(
                value.inputIntegrityHash() + '|' + spec.profile() + '|' + spec.heapMiB());
        return new P4E0ResearchRunRecord(
                studyId,
                plan.planHash(),
                spec,
                classification,
                new P4E0ResearchRunRecord.ProcessResult(
                        exitCode, timedOut, oome, reportObserved, failureClass),
                elapsed,
                environment,
                metrics,
                new P4E0ResearchRunRecord.FixtureEvidence(
                        spec.fixtureId(),
                        fixtureHash,
                        profile.selectedPhysicalBytes(),
                        profile.selectedPhysicalBytes(),
                        profile.selectedDecompressedBytes()));
    }

    private static Map<String, Long> metrics(
            P4E0ResearchMatrixRunner.RunObservation observation,
            P4E0ResearchResult.HeapMetrics heap) {
        var metrics = heapMetrics(heap);
        metrics.put("fixture_file_count", observation.fixture().fileCount());
        metrics.put("logical_record_count", observation.fixture().logicalRecordCount());
        metrics.put("observed_physical_bytes", observation.observedPhysicalBytes());
        metrics.put("observed_decompressed_bytes", observation.observedDecompressedBytes());
        metrics.put("cpu_nanos", observation.cpuNanos());
        metrics.put("outcome_ordinal", (long) observation.outcome().ordinal());
        metrics.put("workload_admitted",
                observation.outcome()
                                == P4E0ResearchMatrixRunner.ObservationOutcome
                                        .PLATFORM_DEPTH_REJECTED
                        ? 0L : 1L);
        metrics.put("platform_depth_rejected",
                observation.outcome()
                                == P4E0ResearchMatrixRunner.ObservationOutcome
                                        .PLATFORM_DEPTH_REJECTED
                        ? 1L : 0L);
        var directory = observation.directory();
        metrics.put("directory_entries", directory.entries());
        metrics.put("canonical_primaries", directory.canonicalPrimaries());
        metrics.put("canonical_old", directory.canonicalOld());
        metrics.put("logical_routes", directory.logicalRoutes());
        metrics.put("irrelevant_entries", directory.irrelevantEntries());
        metrics.put("decoded_records", directory.decodedRecords());
        metrics.put("ready_records", directory.readyRecords());
        metrics.put("projected_roots", directory.projectedRoots());
        var aggregate = observation.aggregate();
        metrics.put("aggregate_record_count", aggregate.recordCount());
        metrics.put("aggregate_per_record_teardown_no_explicit_gc",
                aggregate.recordCount() > 0L ? 1L : 0L);
        metrics.put("cumulative_compressed_bytes", aggregate.cumulativeCompressedBytes());
        metrics.put("cumulative_decompressed_bytes", aggregate.cumulativeDecompressedBytes());
        metrics.put("cumulative_bytes", Math.addExact(
                aggregate.cumulativeCompressedBytes(),
                aggregate.cumulativeDecompressedBytes()));
        metrics.put("cumulative_cpu_nanos", aggregate.cumulativeCpuNanos());
        metrics.put("cumulative_cpu_millis",
                aggregate.cumulativeCpuNanos() / 1_000_000L);
        addNbtMetrics(metrics, observation.nbt());
        return metrics;
    }

    private static Map<String, Long> combinedMetrics(
            P4E0ResearchCombinedEnvelope.Metrics envelope,
            P4E0ResearchResult.HeapMetrics heap) {
        var metrics = heapMetrics(heap);
        metrics.put("directory_entries", (long) envelope.directoryEntriesRetained());
        metrics.put("directory_metadata_name_bytes", envelope.directoryMetadataNameBytes());
        metrics.put("directory_referenced_file_bytes",
                envelope.directoryReferencedFileBytes());
        metrics.put("selected_physical_bytes", envelope.selectedPhysicalBytes());
        metrics.put("selected_gzip_header_bytes", envelope.selectedGzipHeaderBytes());
        metrics.put("selected_decompressed_bytes", envelope.selectedDecompressedBytes());
        metrics.put("attachment_drafts", (long) envelope.attachmentDrafts());
        metrics.put("attachment_latest_states", (long) envelope.attachmentLatestStates());
        metrics.put("attachment_equipped_references",
                (long) envelope.attachmentEquippedReferences());
        metrics.put("attachment_projected_roots",
                (long) envelope.attachmentProjectedRoots());
        var roots = envelope.rootMetrics();
        metrics.put("raw_root_count", (long) roots.rawRootCount());
        metrics.put("distinct_root_count", (long) roots.distinctRootCount());
        metrics.put("duplicate_root_count", (long) roots.duplicateRootCount());
        metrics.put("player_root_count", (long) roots.playerRootCount());
        metrics.put("journal_root_count", (long) roots.journalRootCount());
        var store = envelope.storeMetrics();
        metrics.put("store_bytes", (long) store.storeBytes());
        metrics.put("store_histories", (long) store.storeHistories());
        metrics.put("store_revisions", (long) store.storeRevisions());
        metrics.put("current_journal_entries", (long) store.currentJournalEntries());
        metrics.put("current_journal_bytes", (long) store.currentJournalBytes());
        metrics.put("prospective_journal_entries",
                (long) store.prospectiveJournalEntries());
        metrics.put("prospective_journal_bytes", (long) store.prospectiveJournalBytes());
        metrics.put("prospective_journal_roots", (long) store.prospectiveJournalRoots());
        metrics.put("targets_audited", store.prospectiveTargetsAuditedAgainstCurrentStore()
                ? 1L : 0L);
        metrics.put("filtered_carrier_bytes", (long) store.filteredCarrierBytes());
        metrics.put("filtered_histories", (long) store.filteredHistories());
        metrics.put("filtered_revisions", (long) store.filteredRevisions());
        metrics.put("cumulative_bytes", Math.addExact(
                envelope.directoryReferencedFileBytes(),
                Math.addExact(envelope.selectedPhysicalBytes(), store.storeBytes())));
        metrics.put("cumulative_cpu_millis", 0L);
        metrics.put("workload_admitted", 1L);
        return metrics;
    }

    private static Map<String, Long> heapMetrics(P4E0ResearchResult.HeapMetrics heap) {
        var metrics = new LinkedHashMap<String, Long>();
        metrics.put("xms", heap.xms());
        metrics.put("xmx", heap.xmx());
        metrics.put("initial_committed", heap.initialCommitted());
        metrics.put("sampled_peak_used", heap.sampledPeakUsed());
        metrics.put("heap_pool_peak_sum", heap.heapPoolPeakSum());
        metrics.put("gc_count", heap.gcCount());
        metrics.put("gc_time_millis", heap.gcTimeMillis());
        return metrics;
    }

    private static Map<String, Long> conditionalExecutionMetrics(
            P4E0ResearchMatrixPlan.RunSpec spec, boolean executed) {
        return withConditionalExecution(new LinkedHashMap<>(), spec, executed);
    }

    private static Map<String, Long> withConditionalExecution(
            Map<String, Long> metrics,
            P4E0ResearchMatrixPlan.RunSpec spec,
            boolean executed) {
        addConditionalExecutionMetrics(metrics, spec, executed);
        return metrics;
    }

    private static void addConditionalExecutionMetrics(
            Map<String, Long> metrics,
            P4E0ResearchMatrixPlan.RunSpec spec,
            boolean executed) {
        if (!isConditionalDirectory(spec)) {
            return;
        }
        metrics.put("conditional_extension", 1L);
        metrics.put("conditional_eligible", 1L);
        metrics.put("conditional_executed", executed ? 1L : 0L);
    }

    private static void addNbtMetrics(
            Map<String, Long> metrics, P4E0ResearchNbtMetrics nbt) {
        metrics.put("max_container_depth", nbt.maxContainerDepth());
        metrics.put("compound_count", nbt.compoundCount());
        metrics.put("compound_entry_count", nbt.compoundEntryCount());
        metrics.put("list_count", nbt.listCount());
        metrics.put("list_element_count", nbt.listElementCount());
        metrics.put("scalar_tag_count", nbt.scalarTagCount());
        metrics.put("byte_array_count", nbt.byteArrayCount());
        metrics.put("byte_array_elements", nbt.byteArrayElements());
        metrics.put("int_array_count", nbt.intArrayCount());
        metrics.put("int_array_elements", nbt.intArrayElements());
        metrics.put("long_array_count", nbt.longArrayCount());
        metrics.put("long_array_elements", nbt.longArrayElements());
        metrics.put("string_count", nbt.stringCount());
        metrics.put("modified_utf8_bytes", nbt.modifiedUtf8Bytes());
        metrics.put("tag_count_total", nbt.tagCountTotal());
        metrics.put("value_elements_total", nbt.valueElementsTotal());
    }

    private static void requireHeap(
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchResult.HeapMetrics heap) {
        var expectedXmx = Math.multiplyExact((long) spec.heapMiB(), MEBIBYTE);
        if (heap.xms() != 512L * MEBIBYTE || heap.xmx() != expectedXmx) {
            throw new IllegalStateException(
                    "research child heap differs from the matrix coordinate");
        }
    }

    private static Optional<P4E0ResearchRunRecord> readMatchingChildRecord(
            Path path,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            int exitCode) {
        try {
            var record = P4E0ResearchRunRecord.read(path);
            requireRecordIdentity(record, spec, plan, studyId);
            if (record.processResult().exitCode() != exitCode
                    || !record.processResult().reportObserved()) {
                return Optional.empty();
            }
            return Optional.of(record);
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static void requireRecordIdentity(
            P4E0ResearchRunRecord record,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan,
            String studyId) throws IOException {
        if (!record.spec().equals(spec)
                || !record.planHash().equals(plan.planHash())
                || !record.studyId().equals(studyId)) {
            throw new IOException("research child record identity differs from plan");
        }
    }

    private static boolean isConditionalDirectory(P4E0ResearchMatrixPlan.RunSpec spec) {
        return spec.matrix() == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY
                && spec.coordinate() > 16_384L;
    }

    private static boolean conditionalBaseAllows(
            P4E0ResearchMatrixPlan.RunSpec spec,
            List<P4E0ResearchRunRecord> records,
            long diskBudget,
            Path fixtureRoot) throws IOException {
        var base = findConditionalBase(standardPlan(), spec);
        var predecessor = records.stream()
                .filter(record -> record.spec().runIndex() == base.runIndex())
                .findFirst();
        if (predecessor.isEmpty()) {
            return false;
        }
        var record = predecessor.orElseThrow();
        var conservative = Math.multiplyExact(spec.coordinate(), 131_072L);
        return record.classification() == P4E0ResearchResult.Classification.COMPLETED
                && record.elapsedMillis() < (spec.timeoutSeconds() * 1_000L) / 4L
                && record.fixture().physicalBytes() < diskBudget
                && conservative <= diskBudget
                && Files.getFileStore(matrixRoot(fixtureRoot))
                        .getUsableSpace() >= conservative;
    }

    private static P4E0ResearchMatrixPlan.RunSpec findConditionalBase(
            P4E0ResearchMatrixPlan plan, P4E0ResearchMatrixPlan.RunSpec spec) {
        return plan.runs().stream()
                .filter(candidate -> candidate.matrix()
                        == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)
                .filter(candidate -> candidate.shape().equals(spec.shape()))
                .filter(candidate -> candidate.heapMiB() == spec.heapMiB())
                .filter(candidate -> candidate.coordinate() == 16_384L)
                .findFirst()
                .orElseThrow();
    }

    private static boolean isFatal(P4E0ResearchResult.Classification classification) {
        return classification == P4E0ResearchResult.Classification.FIXTURE_INVALID
                || classification
                        == P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE
                || classification == P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE;
    }

    private static List<String> plainChildCommand(
            P4E0ResearchMatrixPlan.RunSpec spec,
            Path fixtureRoot,
            Path reportRoot,
            long diskBudget) throws IOException {
        var command = new ArrayList<String>();
        command.add(javaExecutable().toString());
        command.add("-Xms512m");
        command.add("-Xmx" + spec.heapMiB() + 'm');
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-D" + GIT_HEAD_PROPERTY + '=' + requiredGitHead());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(P4E0ResearchR2Main.class.getName());
        command.add(CHILD_MODE);
        command.add(Integer.toString(spec.runIndex()));
        command.add(fixtureRoot.toString());
        command.add(reportRoot.toString());
        command.add(Long.toString(diskBudget));
        return List.copyOf(command);
    }

    private static Path javaExecutable() throws IOException {
        var windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT).contains("win");
        var executable = Path.of(
                System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("R2 child Java executable is unavailable");
        }
        return executable;
    }

    private static void terminate(Process process) throws IOException {
        process.destroyForcibly();
        try {
            if (!process.waitFor(CHILD_TERMINATION_SECONDS, TimeUnit.SECONDS)
                    || process.isAlive()) {
                throw new IOException("research child remained alive after forced termination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("research child termination was interrupted", exception);
        }
    }

    private static void replacePartial(
            Path reportRoot, List<P4E0ResearchRunRecord> records) throws IOException {
        if (records.isEmpty()) {
            return;
        }
        var ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparingInt(record -> record.spec().runIndex()));
        var text = new StringBuilder();
        for (var record : ordered) {
            text.append(record.toJsonLine()).append('\n');
        }
        var path = reportRoot.resolve(P4E0ResearchReportAggregator.PARTIAL_RUNS_JSONL);
        Files.createDirectories(path.getParent());
        var temporary = Files.createTempFile(path.getParent(), ".p4-e0-r2-partial-", ".tmp");
        try {
            Files.writeString(
                    temporary, text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(
                        temporary, path, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static List<P4E0ResearchRunRecord> loadAvailableRawRecords(
            Path reportRoot, P4E0ResearchMatrixPlan plan, String studyId)
            throws IOException {
        var records = new ArrayList<P4E0ResearchRunRecord>();
        for (var spec : plan.runs()) {
            var path = rawRecordPath(reportRoot, spec);
            if (!Files.exists(path)) {
                continue;
            }
            var record = P4E0ResearchRunRecord.read(path);
            requireRecordIdentity(record, spec, plan, studyId);
            records.add(record);
        }
        return List.copyOf(records);
    }

    private static boolean hasExactCombinedMarker(
            Path markerPath,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan) {
        try {
            var normalized = markerPath.toAbsolutePath().normalize();
            var expected = combinedRunningMarkerContent(spec, plan);
            return Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(normalized)
                            == expected.getBytes(StandardCharsets.US_ASCII).length
                    && Files.readString(normalized, StandardCharsets.US_ASCII)
                            .equals(expected);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private static void requireCombinedProfile(
            P4E0ResearchCombinedProfileFile.Value value,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchMatrixPlan plan) throws IOException {
        if (!value.planHash().equals(plan.planHash())
                || value.runIndex() != spec.runIndex()
                || !value.profile().kind().name().equals(spec.profile())
                || value.profile().heapMiB() != spec.heapMiB()) {
            throw new IOException("combined profile hand-off differs from plan");
        }
    }

    private static CombinedSources combinedSources(
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchMatrixPlan.RunSpec combined,
            P4E0ResearchCombinedProfileFile.Value value) throws IOException {
        var profile = value.profile();
        var directory = plan.runs().stream()
                .filter(spec -> spec.matrix()
                        == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)
                .filter(spec -> spec.heapMiB() == combined.heapMiB())
                .filter(spec -> spec.shape().equals(profile.directoryShape()))
                .filter(spec -> spec.coordinate() == profile.directoryEntries())
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "combined directory source is outside the plan"));
        var playerdata = plan.runs().stream()
                .filter(spec -> spec.matrix()
                        == P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE)
                .filter(spec -> spec.heapMiB() == combined.heapMiB())
                .filter(spec -> spec.fixtureId().equals(profile.selectedFixtureId()))
                .filter(spec -> spec.shape().equals(profile.selectedFixtureShape()))
                .findFirst()
                .orElseThrow(() -> new IOException(
                        "combined playerdata source is outside the plan"));
        return new CombinedSources(directory, playerdata);
    }

    private static void requireManifestIdentity(
            P4E0ResearchFixtureManifest manifest,
            P4E0ResearchMatrixPlan plan,
            String studyId,
            long diskBudget) throws IOException {
        if (!manifest.gitHead().equals(requiredGitHead())
                || !manifest.studyId().equals(studyId)
                || !manifest.planHash().equals(plan.planHash())
                || manifest.seed() != P4E0ResearchR2PlanFactory.DEFAULT_SEED
                || manifest.diskBudgetBytes() != diskBudget
                || manifest.plannedRuns() != plan.runCount()) {
            throw new IOException("published R2 study identity is stale");
        }
    }

    private static void requirePlanShape(P4E0ResearchMatrixPlan plan) throws IOException {
        var counts = new LinkedHashMap<P4E0ResearchMatrixPlan.Matrix, Long>();
        for (var matrix : P4E0ResearchMatrixPlan.Matrix.values()) {
            counts.put(matrix, plan.runs().stream()
                    .filter(spec -> spec.matrix() == matrix).count());
        }
        if (plan.runCount() != 375
                || counts.get(P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY) != 140L
                || counts.get(P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE) != 60L
                || counts.get(P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY) != 80L
                || counts.get(P4E0ResearchMatrixPlan.Matrix.D_AGGREGATE_AUDIT) != 40L
                || counts.get(P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE) != 40L
                || counts.get(P4E0ResearchMatrixPlan.Matrix.F_COMBINED) != 15L) {
            throw new IOException("R2 plan does not match the exact matrix");
        }
        var heaps = plan.runs().stream().map(P4E0ResearchMatrixPlan.RunSpec::heapMiB)
                .distinct().sorted().toList();
        if (!heaps.equals(P4E0ResearchMatrixPlan.HEAP_GRID_MIB)) {
            throw new IOException("R2 heap grid changed");
        }
    }

    private static P4E0ResearchRunRecord.Environment environmentFor(Path path)
            throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return environmentForNearest(path);
    }

    private static P4E0ResearchRunRecord.Environment environmentForNearest(Path path)
            throws IOException {
        var candidate = path.toAbsolutePath().normalize();
        while (!Files.exists(candidate) && candidate.getParent() != null) {
            candidate = candidate.getParent();
        }
        var store = Files.getFileStore(candidate);
        var storeName = store.name().isEmpty() ? "UNNAMED" : store.name();
        var storeType = store.type().isEmpty() ? "UNKNOWN" : store.type();
        return new P4E0ResearchRunRecord.Environment(
                System.getProperty("java.version", "UNKNOWN"),
                System.getProperty("java.vm.name", "UNKNOWN"),
                System.getProperty("os.name", "UNKNOWN"),
                System.getProperty("os.arch", "UNKNOWN"),
                storeName,
                storeType);
    }

    private static String requiredGitHead() {
        var value = System.getProperty(GIT_HEAD_PROPERTY, "");
        if (!value.matches("[0-9a-f]{40}")) {
            throw new IllegalStateException("exact R2 git HEAD property is absent");
        }
        return value;
    }

    private static int exactRunIndex(String value) {
        var parsed = exactPositiveOrZeroLong(value, "run index");
        if (parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("run index is outside int range");
        }
        return (int) parsed;
    }

    private static long exactPositiveOrZeroLong(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("0|[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException(label + " is not an exact decimal");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " is outside long range", exception);
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static Path requireFixtureRoot(Path path) {
        var normalized = path.toAbsolutePath().normalize();
        var portable = normalized.toString().replace('\\', '/');
        if (!portable.endsWith("/build/p4-e0-research")) {
            throw new IllegalArgumentException("R2 fixture root is outside its build tree");
        }
        return normalized;
    }

    private static Path requireReportRoot(Path path) {
        var normalized = path.toAbsolutePath().normalize();
        var portable = normalized.toString().replace('\\', '/');
        if (!portable.endsWith("/build/reports/p4-e0-research")) {
            throw new IllegalArgumentException("R2 report root is outside its build tree");
        }
        return normalized;
    }

    private static Path controlRoot(Path fixtureRoot) {
        return fixtureRoot.resolve("matrix-control");
    }

    private static Path planPath(Path fixtureRoot) {
        return controlRoot(fixtureRoot).resolve(PLAN_FILE);
    }

    private static Path matrixRoot(Path fixtureRoot) {
        return fixtureRoot.resolve("matrix");
    }

    private static Path fixtureFor(
            P4E0ResearchMatrixPlan.RunSpec spec, Path fixtureRoot) {
        return matrixRoot(fixtureRoot).resolve(spec.runId());
    }

    private static Path combinedInputRoot(
            Path fixtureRoot, P4E0ResearchMatrixPlan.RunSpec spec) {
        return fixtureRoot.resolve("combined-input").resolve(spec.runId());
    }

    private static Path rawRunsRoot(Path reportRoot) {
        return reportRoot.resolve("raw-runs");
    }

    private static Path childRunsRoot(Path reportRoot) {
        return reportRoot.resolve("child-runs");
    }

    private static Path rawRecordPath(
            Path reportRoot, P4E0ResearchMatrixPlan.RunSpec spec) {
        return rawRunsRoot(reportRoot).resolve(recordFileName(spec));
    }

    private static Path childRecordPath(
            Path reportRoot, P4E0ResearchMatrixPlan.RunSpec spec) {
        return childRunsRoot(reportRoot).resolve(recordFileName(spec));
    }

    private static String recordFileName(P4E0ResearchMatrixPlan.RunSpec spec) {
        return String.format(
                Locale.ROOT, "%04d-%s.json", spec.runIndex(), spec.runId());
    }

    private static void cleanupPlainFixture(
            Path fixtureRoot, P4E0ResearchMatrixPlan.RunSpec spec) throws IOException {
        var root = fixtureFor(spec, fixtureRoot).toAbsolutePath().normalize();
        var parent = matrixRoot(fixtureRoot).toAbsolutePath().normalize();
        if (!root.getParent().equals(parent)
                || !root.getFileName().toString().equals(spec.runId())
                || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static long combinedTreeBytes(Path fixtureRoot) throws IOException {
        var root = fixtureRoot.resolve("combined-input");
        if (!Files.exists(root)) {
            return 0L;
        }
        var bytes = 0L;
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(candidate ->
                    Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                bytes = Math.addExact(bytes, Files.size(path));
            }
        }
        return bytes;
    }

    private static void requireExactText(Path path, String expected, long maximumBytes)
            throws IOException {
        var expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) != expectedBytes.length
                || Files.size(path) > maximumBytes
                || !Files.readString(path, StandardCharsets.UTF_8).equals(expected)) {
            throw new IOException("bounded R2 control file differs from expectation");
        }
    }

    private record CombinedSources(
            P4E0ResearchMatrixPlan.RunSpec directory,
            P4E0ResearchMatrixPlan.RunSpec playerdata) {
    }
}
