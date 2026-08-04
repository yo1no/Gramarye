package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.P4D3ProbeCase;
import com.yo1no.gramarye.magic.definition.store.P4D3ProbeSupport;
import com.yo1no.gramarye.magic.definition.store.P4E0R2QStoreJournalFixtures;
import com.yo1no.gramarye.magic.definition.store.SkillRetentionRootSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.zip.Deflater;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/** Formal case-owned fixture lifecycle and dedicated-child qualification observations. */
final class P4E0R2QFormalWorkload {
    static final String CASE_MANIFEST = "case-manifest.json";
    static final String CHILD_RESULT = "child-result.json";
    static final String VERIFIED_RESULT = "verified-result.json";
    static final String PREPARE_FAILURE = "prepare-failure.json";
    static final String RUNNING_MARKER = "running.marker";
    static final String EXIT_MARKER = "exit-code.txt";
    static final String TIMEOUT_MARKER = "timeout.marker";
    static final String LARGE_FIXTURE_DIRECTORY = "fixture";
    static final String GAME_DIRECTORY = "game";
    static final String WORLD_DIRECTORY = "world";
    static final int MAXIMUM_MANIFEST_BYTES = 65_536;
    static final long POSITIVE_DISK_PROJECTION_BYTES = 805_306_368L;
    static final long FULL_CASE_DISK_PROJECTION_BYTES = 805_306_368L;
    static final long SMALL_CASE_DISK_PROJECTION_BYTES = 16_777_216L;

    private static final Set<String> MANIFEST_FIELDS = Set.of(
            "schema_version",
            "study_id",
            "case_id",
            "case_index",
            "case_kind",
            "fixture_root_hash",
            "run_order_hash",
            "target_counter",
            "maximum",
            "observed_at_least",
            "expected_failure_code",
            "expected_stage",
            "materialization",
            "directory_entries",
            "relevant_records",
            "fixture_checksum");
    private static final String ZERO_SHA256 = "0".repeat(64);
    private static final long CONFIGURED_XMS_BYTES = 512L * 1_024L * 1_024L;
    private static final long CONFIGURED_XMX_BYTES = 1_536L * 1_024L * 1_024L;

    private P4E0R2QFormalWorkload() {
    }

    static void prepareCase(
            Path caseRoot, P4E0R2QFormalEvidence.StudyControl control, int caseIndex)
            throws IOException {
        var root = normalized(caseRoot);
        var spec = caseSpec(caseIndex);
        if (Files.exists(root.resolve(CASE_MANIFEST), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("formal case manifest already exists");
        }
        Files.createDirectories(root);
        var workRoot = Objects.requireNonNull(
                Objects.requireNonNull(root.getParent(), "cases root").getParent(),
                "formal work root");
        var projection = switch (spec.kind()) {
            case POSITIVE, COUNTER_MAX_PLUS_ONE -> FULL_CASE_DISK_PROJECTION_BYTES;
            case DATA_VERSION_MISSING,
                    DATA_VERSION_WRONG_TYPE,
                    DATA_VERSION_WRONG_VALUE -> SMALL_CASE_DISK_PROJECTION_BYTES;
        };
        P4E0R2QFormalEvidence.requireDiskBudget(workRoot, projection);

        final MaterializedFacts shape;
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE) {
            materializePositive(root);
            shape = new MaterializedFacts(
                    "EXACT_PHYSICAL_POSITIVE", 4_096L, 2_048L,
                    expectedCaseFixtureChecksum(spec));
        } else if (spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
            var counterShape = materializeCounter(root, spec);
            shape = new MaterializedFacts(
                    counterShape.materialization(), counterShape.directoryEntries(),
                    counterShape.relevantRecords(),
                    expectedCaseFixtureChecksum(spec));
        } else {
            materializeDataVersion(root, spec);
            shape = new MaterializedFacts(
                    "WIRE_DATA_VERSION_CONTROL",
                    1L,
                    1L,
                    expectedCaseFixtureChecksum(spec));
        }
        var materialized = new MaterializedFacts(
                shape.materialization(), shape.directoryEntries(), shape.relevantRecords(),
                physicalFixtureChecksum(root, spec));
        writeManifest(root.resolve(CASE_MANIFEST), manifest(control, spec, materialized));
        readManifest(root.resolve(CASE_MANIFEST), control, spec, true);
        P4E0R2QFormalEvidence.requireDiskBudget(workRoot, 0L);
    }

    static P4E0R2QFormalResult execute(
            MinecraftServer server,
            Path caseRoot,
            P4E0R2QFormalEvidence.StudyControl control,
            int caseIndex) throws IOException {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("formal R2Q child requires the server logic thread");
        }
        var spec = caseSpec(caseIndex);
        var normalizedRoot = normalized(caseRoot);
        var manifest = readManifest(
                normalizedRoot.resolve(CASE_MANIFEST), control, spec, true);
        var tracker = new HeapTracker();
        tracker.sample();
        var started = System.nanoTime();
        final P4E0R2QFormalResult result;
        try {
            result = switch (spec.kind()) {
                case POSITIVE -> executePositive(
                        server, normalized(caseRoot), control, spec, tracker, started,
                        manifest.fixtureChecksum());
                case COUNTER_MAX_PLUS_ONE -> executeCounter(
                        normalized(caseRoot), control, spec, tracker, started,
                        manifest.fixtureChecksum());
                case DATA_VERSION_MISSING,
                        DATA_VERSION_WRONG_TYPE,
                        DATA_VERSION_WRONG_VALUE -> executeDataVersion(
                                normalized(caseRoot), control, spec, tracker, started,
                                manifest.fixtureChecksum());
            };
        } catch (FixtureInvalidException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FixtureInvalidException(exception);
        }
        requireObservedCaseVector(spec, result.observedCounters());
        if (!result.caseFixtureChecksum().equals(manifest.fixtureChecksum())) {
            throw new IOException("formal fixture manifest checksum differs from observation");
        }
        return result;
    }

    /** Pure locked-plan result factory for aggregation and schema contract tests. */
    static P4E0R2QFormalResult preflightResult(
            P4E0R2QFormalEvidence.StudyControl control, int caseIndex) {
        var spec = caseSpec(caseIndex);
        return completedResult(
                control,
                spec,
                expectedCounters(spec),
                expectedDfu(spec),
                expectedAdmissions(spec),
                expectedRawRoots(spec),
                expectedTargetsAudited(spec),
                new P4E0R2QFormalResult.HeapFacts(
                        CONFIGURED_XMS_BYTES,
                        CONFIGURED_XMX_BYTES,
                        CONFIGURED_XMS_BYTES,
                        1L,
                        1L),
                1L,
                expectedCaseFixtureChecksum(spec));
    }

    /** Writes the bounded compiled-plan manifest used by aggregation-only contract tests. */
    static void writePreflightManifest(
            Path caseRoot,
            P4E0R2QFormalEvidence.StudyControl control,
            int caseIndex) throws IOException {
        var root = normalized(caseRoot);
        var spec = caseSpec(caseIndex);
        var counters = expectedCounters(spec);
        var facts = new MaterializedFacts(
                expectedMaterialization(spec),
                spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                        ? 4_096L
                        : spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE
                                ? counters.directoryEntries() : 1L,
                spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                        ? 2_048L
                        : spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE
                                ? counters.relevantRecords() : 1L,
                expectedCaseFixtureChecksum(spec));
        writeManifest(root.resolve(CASE_MANIFEST), manifest(control, spec, facts));
        readManifest(root.resolve(CASE_MANIFEST), control, spec, false);
    }

    static P4E0R2QFormalResult failedResult(
            P4E0R2QFormalEvidence.StudyControl control,
            int caseIndex,
            P4E0R2QFormalResult.ProcessClassification classification,
            String exceptionClass) {
        return failedResult(
                control, caseIndex, classification, exceptionClass,
                expectedCaseFixtureChecksum(caseSpec(caseIndex)));
    }

    static P4E0R2QFormalResult failedResult(
            P4E0R2QFormalEvidence.StudyControl control,
            int caseIndex,
            P4E0R2QFormalResult.ProcessClassification classification,
            String exceptionClass,
            String caseFixtureChecksum) {
        if (classification == P4E0R2QFormalResult.ProcessClassification.COMPLETED) {
            throw new IllegalArgumentException("failed formal result cannot be COMPLETED");
        }
        var spec = caseSpec(caseIndex);
        var failure = spec.expectedFailure();
        return new P4E0R2QFormalResult(
                control.studyId(), spec.caseId(), spec.index(), control.gitHead(),
                control.gitTree(), control.profileHash(), control.casePlanHash(),
                control.fixtureRootHash(), caseFixtureChecksum, control.runOrderHash(),
                control.implementationSchemaVersion(), classification,
                P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                spec.targetCounter(), spec.maximum(), spec.observedAtLeast(),
                failure.map(value -> value.code().name()).orElse("NONE"), "NONE",
                failure.map(value -> value.stage().slug()).orElse("NONE"), "NONE",
                spec.allOtherCountersWithinLimit(), zeroCounters(), 0L, 0L, 0L, 0L, 0L,
                new P4E0R2QFormalResult.HeapFacts(
                        CONFIGURED_XMS_BYTES, CONFIGURED_XMX_BYTES, 0L, 0L, 0L),
                0L, exceptionClass);
    }

    /**
     * Exercises case 04's complete parent-side physical preparation and strict measurement in a
     * caller-owned, non-formal fresh-JVM root. No formal child or result publication is involved.
     */
    static CounterPreparationRegression verifyCase04Preparation(Path runRoot)
            throws IOException {
        var case04 = caseSpec(4);
        if (case04.targetCounter().orElseThrow()
                        != P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE
                || case04.maximum() != 268_435_456L
                || case04.observedAtLeast() != 268_435_457L) {
            throw new IOException("R2Q case 04 identity changed before preparation regression");
        }
        return verifyCounterPreparations(runRoot, List.of(case04));
    }

    /**
     * Sequentially materializes, strictly measures, and removes every counter-negative physical
     * fixture. At most one case world exists at a time and no dedicated child is started.
     */
    static CounterPreparationRegression verifyAllCounterPreparations(Path runRoot)
            throws IOException {
        var counters = P4E0R2QCasePlan.standard().cases().stream()
                .filter(spec -> spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE)
                .toList();
        if (counters.size() != P4E0R2QProfile.COUNTER_COUNT) {
            throw new IOException("R2Q counter preparation coverage changed");
        }
        return verifyCounterPreparations(runRoot, counters);
    }

    private static CounterPreparationRegression verifyCounterPreparations(
            Path runRoot, List<P4E0R2QCasePlan.CaseSpec> cases) throws IOException {
        var root = normalized(runRoot);
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("R2Q pre-child regression root already exists");
        }
        Files.createDirectories(root);
        var case04Observed = 0L;
        var strictDataVersionChecks = 0;
        var ownerGuardClassifications = 0;
        var sequence = new StringBuilder("p4-e0-r2q-counter-preparation-v0\n");
        try (var rootCleanup = new RegressionRootCleanup(root)) {
            for (var spec : cases) {
                var caseRoot = root.resolve(String.format(
                        java.util.Locale.ROOT, "case-%02d", spec.index()));
                Files.createDirectory(caseRoot);
                try (var caseCleanup = new RegressionRootCleanup(caseRoot)) {
                    try {
                        materializeCounter(caseRoot, spec);
                    } catch (IllegalStateException exception) {
                        throw new IOException(
                                "R2Q pre-child materialization failed for "
                                        + spec.targetCounter().orElseThrow().slug(),
                                exception);
                    }
                    var worldRoot = caseRoot.resolve(GAME_DIRECTORY).resolve(WORLD_DIRECTORY);
                    P4E0R2QStoreJournalFixtures.requireStrictPrimaryDataVersion(
                            worldRoot, P4E0R2QProfile.locked().acceptedDataVersion());
                    strictDataVersionChecks++;

                    var fixture = P4E0R2QFixturePlan.negativeFixture(spec);
                    var preflight = P4E0R2QCasePlan.standard()
                            .preflightNegative(spec, fixture);
                    var observed = observeFullPhysicalCounter(caseRoot, spec, new HeapTracker());
                    var guardFailure = executeStrictCounter(caseRoot, spec, new HeapTracker());
                    requireCounterPreparation(
                            spec, preflight, fixture, observed, guardFailure);
                    ownerGuardClassifications++;
                    requireNoPrechildArtifacts(caseRoot);
                    var physicalChecksum = physicalFixtureChecksum(caseRoot, spec);
                    sequence.append(spec.index()).append('|')
                            .append(spec.caseId()).append('|')
                            .append(preflight.targetCounter().slug()).append('|')
                            .append(observed.value(preflight.targetCounter())).append('|')
                            .append(preflight.firstFailureStage().slug()).append('|')
                            .append(physicalChecksum).append('\n');
                    if (spec.index() == 4) {
                        case04Observed = observed.decompressedBytesPerFile();
                    }
                }
                if (Files.exists(caseRoot, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("R2Q pre-child case cleanup was incomplete");
                }
            }
        }
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("R2Q pre-child regression cleanup was incomplete");
        }
        return new CounterPreparationRegression(
                cases.size(), case04Observed, strictDataVersionChecks,
                ownerGuardClassifications, 0,
                P4E0ResearchHashing.sha256(sequence.toString()));
    }

    private static void requireCounterPreparation(
            P4E0R2QCasePlan.CaseSpec spec,
            P4E0R2QCasePlan.NegativePreflight preflight,
            P4E0R2QFixturePlan.NegativeFixture fixture,
            P4E0R2QProfile.CounterValues observed,
            P4E0R2QAuditBudget.AuditFailure guardFailure) throws IOException {
        var target = spec.targetCounter().orElseThrow();
        var expectedFailure = spec.expectedFailure().orElseThrow();
        if (!observed.equals(fixture.observedCounters())
                || preflight.targetCounter() != target
                || preflight.observedAtLeast() != Math.addExact(preflight.maximum(), 1L)
                || observed.value(target) != preflight.observedAtLeast()
                || expectedFailure.code()
                        != P4E0R2QCasePlan.FailureCode.COUNTER_CAPACITY_EXCEEDED
                || expectedFailure.stage() != preflight.firstFailureStage()
                || expectedFailure.counter().orElseThrow() != target
                || guardFailure.code()
                        != P4E0R2QAuditBudget.FailureCode.COUNTER_CAPACITY_EXCEEDED
                || guardFailure.stage() != preflight.firstFailureStage()
                || guardFailure.counter().orElseThrow() != target
                || guardFailure.maximum() != preflight.maximum()
                || guardFailure.observedAtLeast() != preflight.observedAtLeast()
                || !preflight.allOtherCountersWithinLimit()) {
            throw new IOException("R2Q pre-child counter identity or strict vector changed");
        }
        var profile = P4E0R2QProfile.locked();
        for (var counter : P4E0R2QProfile.Counter.values()) {
            if (counter != target && observed.value(counter) > profile.maximum(counter)) {
                throw new IOException("R2Q pre-child fixture has a second counter overrun");
            }
        }
        if (spec.index() == 4
                && (target != P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE
                        || observed.decompressedBytesPerFile() != 268_435_457L)) {
            throw new IOException("R2Q case 04 complete strict measurement changed");
        }
    }

    private static void requireNoPrechildArtifacts(Path caseRoot) throws IOException {
        for (var name : List.of(
                CHILD_RESULT,
                VERIFIED_RESULT,
                PREPARE_FAILURE,
                RUNNING_MARKER,
                EXIT_MARKER,
                TIMEOUT_MARKER)) {
            if (Files.exists(caseRoot.resolve(name), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("R2Q pre-child regression created a child artifact");
            }
        }
    }

    static void cleanupLargeCaseData(Path caseRoot) throws IOException {
        var root = normalized(caseRoot);
        deleteTreeIfPresent(root.resolve(LARGE_FIXTURE_DIRECTORY));
        deleteTreeIfPresent(root.resolve(GAME_DIRECTORY));
        if (Files.exists(root.resolve(LARGE_FIXTURE_DIRECTORY), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(root.resolve(GAME_DIRECTORY), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("formal case large-fixture cleanup was incomplete");
        }
    }

    static String readVerifiedManifestChecksum(
            Path caseRoot, P4E0R2QFormalEvidence.StudyControl control, int caseIndex)
            throws IOException {
        var spec = caseSpec(caseIndex);
        return readManifest(
                        normalized(caseRoot).resolve(CASE_MANIFEST), control, spec, false)
                .fixtureChecksum();
    }

    private static P4E0R2QFormalResult executePositive(
            MinecraftServer server,
            Path caseRoot,
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            HeapTracker tracker,
            long started,
            String caseFixtureChecksum) throws IOException {
        var recordsRoot = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY).resolve("records");
        var all = listRegularEntries(recordsRoot);
        if (all.size() != 4_096) {
            throw new IOException("formal positive directory entry count changed");
        }
        var selected = all.stream()
                .filter(path -> path.getFileName().toString().endsWith(".dat"))
                .sorted()
                .toList();
        if (selected.size() != 2_048) {
            throw new IOException("formal positive relevant record count changed");
        }

        var budget = new P4E0R2QAuditBudget();
        var liveBefore = P4D3ProbeSupport.observeLive(server);
        budget.requireJournalReady(liveBefore.journalReady());
        budget.observeDirectoryEntries(all.size());
        var maxima = new PerFileMaxima();
        var dfu = new P4E0R2QAuditBudget.DfuInvocationProbe();
        for (var path : selected) {
            var scan = P4E0ResearchWireNbt.scan(
                    path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
            budget.observeDataVersion(scan.dataVersion(), dfu);
            maxima.observe(scan);
            tracker.sample();
        }

        var retained = P4E0R2QStoreJournalFixtures.buildExact();
        var admissions = retained.admissionFacts();
        for (var index = 0; index < admissions.totalAdmissions(); index++) {
            budget.observeAttachmentAdmission(P4E0ResearchAttachmentFixtures.Variant.READY);
        }
        var raw = retained.exactRawRootClaims();
        var complete = budget.captureRawRoots(raw);
        if (!(complete instanceof SkillRetentionRootSnapshot.Complete accepted)
                || accepted.roots().size() != P4E0R2QStoreJournalFixtures.RAW_ROOTS) {
            throw new IOException("formal positive root snapshot is not exact Complete");
        }
        budget.requireStoreAudit(true);
        var observed = counters(budget.facts(), maxima);
        if (!observed.equals(P4E0R2QProfile.locked().candidateValues())
                || dfu.invocations() != 0) {
            throw new IOException("formal positive counter observation differs from profile");
        }

        var facts = retained.facts();
        var manifest = new P4D3ProbeSupport.ManifestView(
                P4D3ProbeCase.COMBINED,
                "R2Q_FORMAL_POSITIVE",
                "READY",
                ZERO_SHA256,
                ZERO_SHA256,
                facts.currentStoreBytes(),
                facts.currentHistories(),
                facts.currentRevisions(),
                facts.currentStoreChecksum(),
                facts.currentJournalBytes(),
                facts.currentJournalEntries(),
                facts.currentJournalEntries(),
                ZERO_SHA256,
                ZERO_SHA256,
                ZERO_SHA256,
                0L,
                ZERO_SHA256,
                ZERO_SHA256,
                0L,
                "NONE");
        try (var heldSave = P4D3ProbeSupport.beginHeldSavedDataSave(server, manifest)) {
            P4E0R2QMain.runExactActualSubmission(server, () -> {
                retained.retainAtPeak();
                Reference.reachabilityFence(complete);
                tracker.sample();
            });
            retained.retainAtPeak();
            Reference.reachabilityFence(complete);
            tracker.sample();
        }
        tracker.sample();
        return completedResult(
                control, spec, observed, 0L, admissions.totalAdmissions(), raw.size(),
                raw.size(), tracker.facts(), elapsedMillis(started), caseFixtureChecksum);
    }

    private static P4E0R2QFormalResult executeCounter(
            Path caseRoot,
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            HeapTracker tracker,
            long started,
            String caseFixtureChecksum) throws IOException {
        var fixture = P4E0R2QFixturePlan.negativeFixture(spec);
        var preflight = P4E0R2QCasePlan.standard().preflightNegative(spec, fixture);
        var observedCounters = observeFullPhysicalCounter(caseRoot, spec, tracker);
        if (!observedCounters.equals(fixture.observedCounters())
                ) {
            throw new IOException("formal counter physical vector or checksum changed");
        }
        var observedFailure = executeStrictCounter(caseRoot, spec, tracker);
        if (observedFailure.counter().orElseThrow() != preflight.targetCounter()
                || observedFailure.code()
                        != P4E0R2QAuditBudget.FailureCode.COUNTER_CAPACITY_EXCEEDED
                || observedFailure.stage() != preflight.firstFailureStage()
                || observedFailure.maximum() != preflight.maximum()
                || observedFailure.observedAtLeast() != preflight.observedAtLeast()) {
            throw new IOException("formal counter checkpoint classification changed");
        }
        tracker.sample();
        return completedResult(
                control,
                spec,
                observedCounters,
                0L,
                expectedAdmissions(spec),
                expectedRawRoots(spec),
                0L,
                tracker.facts(),
                elapsedMillis(started),
                caseFixtureChecksum);
    }

    private static P4E0R2QProfile.CounterValues observeFullPhysicalCounter(
            Path caseRoot,
            P4E0R2QCasePlan.CaseSpec spec,
            HeapTracker tracker) throws IOException {
        var records = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY).resolve("records");
        var entries = listRegularEntries(records);
        var selected = entries.stream()
                .filter(path -> path.getFileName().toString().endsWith(".dat"))
                .sorted()
                .toList();
        var profile = P4E0R2QProfile.locked();
        var perFile = new P4E0ResearchWireNbt.CheckpointLimits(
                Math.addExact(Math.toIntExact(profile.maximum(
                        P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE)), 1),
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE) + 1L);
        var aggregate = new P4E0ResearchWireNbt.AggregateCheckpointBudget(
                new P4E0ResearchWireNbt.CheckpointLimits(
                        perFile.containerDepth(),
                        profile.maximum(P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL) + 1L,
                        profile.maximum(P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL) + 1L));
        var limits = new P4E0ResearchWireNbt.ScanLimits(
                profile.maximum(P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE) + 1L,
                300_000_000L,
                perFile.containerDepth(),
                profile.maximum(P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE) + 1L,
                profile.maximum(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE) + 1L);
        var maxima = new PerFileMaxima();
        var compressedTotal = 0L;
        var decompressedTotal = 0L;
        for (var path : selected) {
            var scan = P4E0ResearchWireNbt.scan(path, limits, perFile, aggregate);
            if (scan.dataVersion().kind() != P4E0ResearchWireNbt.DataVersionKind.INT_TAG
                    || scan.dataVersion().intValue() != 3_955) {
                throw new IOException("formal counter fixture DataVersion changed");
            }
            compressedTotal = Math.addExact(compressedTotal, scan.physicalBytes());
            decompressedTotal = Math.addExact(decompressedTotal, scan.decompressedBytes());
            maxima.observe(scan);
            tracker.sample();
        }
        // Later-stage values are part of the preflighted physical plan. The strict pass below is
        // the only child path allowed to execute P4-C admission/root checkpoints, so an early
        // counter rejection cannot secretly cross a later stage while reporting zero side facts.
        var planned = P4E0R2QFixturePlan.negativeFixture(spec).observedCounters();
        var checkpoint = aggregate.observed();
        var observed = new P4E0R2QProfile.CounterValues(
                entries.size(), selected.size(), maxima.compressedBytes,
                maxima.decompressedBytes, maxima.containerDepth, maxima.compoundContainers,
                maxima.compoundFields, maxima.listElements, maxima.byteArrays, maxima.intArrays,
                maxima.longArrays, maxima.modifiedUtf, maxima.scalarTags, compressedTotal,
                decompressedTotal, checkpoint.compoundContainers(),
                checkpoint.compoundFieldEntries(), checkpoint.listElements(),
                checkpoint.byteArrayElements(), checkpoint.intArrayElements(),
                checkpoint.longArrayElements(), checkpoint.modifiedUtf8Bytes(),
                checkpoint.scalarTags(),
                planned.attachmentAdmissions(), planned.rawRootClaims());
        return observed;
    }

    private static P4E0R2QAuditBudget.AuditFailure executeStrictCounter(
            Path caseRoot,
            P4E0R2QCasePlan.CaseSpec spec,
            HeapTracker tracker) throws IOException {
        var records = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY).resolve("records");
        var entries = listRegularEntries(records);
        var selected = entries.stream()
                .filter(path -> path.getFileName().toString().endsWith(".dat"))
                .sorted()
                .toList();
        var target = spec.targetCounter().orElseThrow();
        var budget = new P4E0R2QAuditBudget();
        var dfu = new P4E0R2QAuditBudget.DfuInvocationProbe();
        try {
            budget.requireJournalReady(true);
            budget.observeDirectoryEntries(entries.size());
            for (var path : selected) {
                var scan = P4E0ResearchWireNbt.scan(
                        path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
                budget.observeDataVersion(scan.dataVersion(), dfu);
                tracker.sample();
            }
            var admissions = target == P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS
                    ? 1_025L : 1_024L;
            var ready = P4E0ResearchAttachmentFixtures.readyRootMax(false);
            for (var index = 0L; index < admissions; index++) {
                var admitted = P4E0ResearchAttachmentFixtures.admit(
                        ready.serializedTag(), net.minecraft.core.RegistryAccess.EMPTY);
                budget.observeAttachmentAdmission(admitted.variant());
            }
            var store = P4E0R2QStoreJournalFixtures.buildExact();
            var roots = target == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS
                    ? store.overRawRootClaims() : store.exactRawRootClaims();
            budget.captureRawRoots(roots);
            budget.requireStoreAudit(true);
            store.retainAtPeak();
        } catch (P4E0R2QAuditBudget.AuditFailure failure) {
            if (dfu.invocations() != 0L) {
                throw new IOException("formal counter unexpectedly invoked DFU");
            }
            return failure;
        }
        throw new IOException("formal full physical counter fixture did not reject");
    }

    private static P4E0R2QFormalResult executeDataVersion(
            Path caseRoot,
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            HeapTracker tracker,
            long started,
            String caseFixtureChecksum) throws IOException {
        var fixture = P4E0R2QFixturePlan.dataVersionFixture(spec);
        var preflight = P4E0R2QCasePlan.standard().preflightDataVersion(spec, fixture);
        var path = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY).resolve("data-version.dat");
        var budget = new P4E0R2QAuditBudget();
        budget.requireJournalReady(true);
        budget.observeDirectoryEntries(1L);
        var scan = P4E0ResearchWireNbt.scan(
                path, budget, P4E0R2QAuditBudget.SourceSelection.PRIMARY);
        var probe = new P4E0R2QAuditBudget.DfuInvocationProbe();
        final P4E0R2QAuditBudget.AuditFailure failure;
        try {
            budget.observeDataVersion(scan.dataVersion(), probe);
            throw new IOException("formal DataVersion fixture was unexpectedly admitted");
        } catch (P4E0R2QAuditBudget.AuditFailure observed) {
            failure = observed;
        }
        if (failure.counter().isPresent()
                || failure.code()
                        == P4E0R2QAuditBudget.FailureCode.COUNTER_CAPACITY_EXCEEDED
                || failure.code() != auditFailureCode(preflight.failureCode())
                || failure.stage() != preflight.firstFailureStage()
                || probe.invocations() != 0) {
            throw new IOException("formal DataVersion checkpoint classification changed");
        }
        tracker.sample();
        return completedResult(
                control, spec, zeroCounters(), 0L, 0L, 0L, 0L,
                tracker.facts(), elapsedMillis(started), caseFixtureChecksum);
    }

    private static P4E0R2QAuditBudget.FailureCode auditFailureCode(
            P4E0R2QCasePlan.FailureCode code) {
        return switch (code) {
            case COUNTER_CAPACITY_EXCEEDED ->
                    P4E0R2QAuditBudget.FailureCode.COUNTER_CAPACITY_EXCEEDED;
            case DATA_VERSION_MISSING -> P4E0R2QAuditBudget.FailureCode.DATA_VERSION_MISSING;
            case DATA_VERSION_WRONG_TYPE ->
                    P4E0R2QAuditBudget.FailureCode.DATA_VERSION_WRONG_TYPE;
            case DATA_VERSION_WRONG_VALUE ->
                    P4E0R2QAuditBudget.FailureCode.DATA_VERSION_WRONG_VALUE;
        };
    }

    private static MaterializedFacts materializePositive(Path caseRoot) throws IOException {
        var fixtureRoot = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY);
        var records = fixtureRoot.resolve("records");
        Files.createDirectories(records);
        var blueprint = P4E0R2QFixturePlan.locked();
        var writers = blueprint.jointRecords();
        var tuning = blueprint.compressed().files();
        var digestInput = new StringBuilder();
        for (var index = 0; index < tuning.size(); index++) {
            var file = tuning.get(index);
            var primary = records.resolve(String.format(
                    java.util.Locale.ROOT, "%04d.dat", index));
            var written = writers.write(
                    index, primary, file.headerOptions(), file.targetPhysicalBytes());
            if (written.physicalBytes() != file.targetPhysicalBytes()) {
                throw new IOException("formal positive tuned primary size changed");
            }
            digestInput.append(index).append(':').append(written.sha256()).append('\n');
            var old = records.resolve(String.format(
                    java.util.Locale.ROOT, "%04d.dat_old", index));
            writeNewBytes(old, new byte[] {(byte) (index & 0xff)});
        }
        if (listRegularEntries(records).size() != 4_096) {
            throw new IOException("formal positive physical directory is not exact");
        }
        var store = P4E0R2QStoreJournalFixtures.buildExact();
        var world = caseRoot.resolve(GAME_DIRECTORY).resolve(WORLD_DIRECTORY);
        store.writePrimary(world, true);
        var primary = com.yo1no.gramarye.magic.definition.store
                .P4D3StoreJournalFixture.primary(world);
        digestInput.append("store:").append(P4E0ResearchHashing.sha256(primary)).append('\n');
        store.retainAtPeak();
        return new MaterializedFacts(
                "EXACT_PHYSICAL_POSITIVE",
                4_096L,
                2_048L,
                P4E0ResearchHashing.sha256(digestInput.toString()));
    }

    private static MaterializedFacts materializeCounter(
            Path caseRoot, P4E0R2QCasePlan.CaseSpec spec) throws IOException {
        var target = spec.targetCounter().orElseThrow();
        var fixture = P4E0R2QFixturePlan.negativeFixture(spec);
        var preflight = P4E0R2QCasePlan.standard().preflightNegative(spec, fixture);
        var fixtureRoot = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY);
        var records = fixtureRoot.resolve("records");
        Files.createDirectories(records);
        var blueprint = P4E0R2QFixturePlan.locked();
        var plan = target == P4E0R2QProfile.Counter.RELEVANT_RECORDS
                ? P4E0R2QJointRecords.buildRelevantCompensation()
                : P4E0R2QJointRecords.buildNegative(target);
        var tuning = switch (target) {
            case COMPRESSED_BYTES_PER_FILE -> blueprint.compressed().perFileOverrun();
            case COMPRESSED_BYTES_TOTAL -> blueprint.compressed().aggregateOverrun();
            default -> P4E0R2QFixturePlan.tuneCompressedHeaders(
                    plan.canonicalPhysicalBytes()).files();
        };
        var relevantWitnessBytes = 0L;
        if (target == P4E0R2QProfile.Counter.RELEVANT_RECORDS) {
            var measured = P4E0ResearchWireNbt.measure(
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    Deflater.BEST_COMPRESSION,
                    P4E0R2QProfile.locked().maximum(
                            P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE),
                    P4E0R2QProfile.locked().maximum(
                            P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE),
                    P4E0R2QFormalWorkload::writeTinySelectedRecord);
            relevantWitnessBytes = measured.physicalBytes();
            tuning = reduceTunedTotal(tuning, relevantWitnessBytes);
        }
        for (var index = 0; index < plan.records().size(); index++) {
            var header = tuning.get(index);
            var primary = records.resolve(String.format(
                    java.util.Locale.ROOT, "%04d.dat", index));
            var written = plan.write(
                    index, primary, header.headerOptions(), header.targetPhysicalBytes());
            if (written.physicalBytes() != header.targetPhysicalBytes()) {
                throw new IOException("formal counter tuned primary size changed");
            }
        }
        var oldCount = target == P4E0R2QProfile.Counter.RELEVANT_RECORDS ? 2_047 : 2_048;
        for (var index = 0; index < oldCount; index++) {
            writeNewBytes(
                    records.resolve(String.format(
                            java.util.Locale.ROOT, "%04d.dat_old", index)),
                    new byte[] {(byte) (index & 0xff)});
        }
        if (target == P4E0R2QProfile.Counter.RELEVANT_RECORDS) {
            var extra = records.resolve("2048.dat");
            var written = P4E0ResearchWireNbt.write(
                    extra,
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    Deflater.BEST_COMPRESSION,
                    relevantWitnessBytes,
                    22L,
                    P4E0R2QFormalWorkload::writeTinySelectedRecord);
            if (written.physicalBytes() != relevantWitnessBytes) {
                throw new IOException("formal relevant witness size changed");
            }
        }
        if (target == P4E0R2QProfile.Counter.DIRECTORY_ENTRIES) {
            writeNewBytes(records.resolve("extra.irrelevant"), new byte[] {0x51});
        }
        var entries = listRegularEntries(records);
        var selected = entries.stream()
                .filter(path -> path.getFileName().toString().endsWith(".dat"))
                .count();
        if (entries.size() != fixture.observedCounters().directoryEntries()
                || selected != fixture.observedCounters().relevantRecords()) {
            throw new IOException("formal counter source-selection shape changed");
        }
        var store = P4E0R2QStoreJournalFixtures.buildExact();
        store.writePrimary(caseRoot.resolve(GAME_DIRECTORY).resolve(WORLD_DIRECTORY), true);
        store.retainAtPeak();
        return new MaterializedFacts(
                "FULL_PHYSICAL_MUTATION_" + preflight.physicalProofKind().name(),
                entries.size(), selected, expectedCaseFixtureChecksum(spec));
    }

    private static List<P4E0R2QFixturePlan.HeaderTuning> reduceTunedTotal(
            List<P4E0R2QFixturePlan.HeaderTuning> source, long reduction) {
        var changed = new ArrayList<>(source);
        var remaining = reduction;
        for (var index = changed.size() - 1; index >= 1 && remaining > 0L; index--) {
            var header = changed.get(index);
            var removable = Math.max(0L, header.fileNameBytes() - 1L);
            var removed = Math.min(remaining, removable);
            if (removed > 0L) {
                changed.set(index, new P4E0R2QFixturePlan.HeaderTuning(
                        header.fileIndex(), header.canonicalPhysicalBytes(),
                        Math.subtractExact(header.targetPhysicalBytes(), removed),
                        Math.toIntExact(Math.subtractExact(header.fileNameBytes(), removed))));
                remaining -= removed;
            }
        }
        if (remaining != 0L) {
            throw new IllegalArgumentException(
                    "R2Q relevant witness lacks compressed compensation headroom");
        }
        return List.copyOf(changed);
    }

    private static void writeTinySelectedRecord(java.io.DataOutput output) throws IOException {
        P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
        output.writeByte(Tag.TAG_INT);
        output.writeUTF("DataVersion");
        output.writeInt(3_955);
        output.writeByte(Tag.TAG_END);
    }

    private static P4E0ResearchWireNbt.WriteFacts materializeDataVersion(
            Path caseRoot, P4E0R2QCasePlan.CaseSpec spec) throws IOException {
        var fixture = P4E0R2QFixturePlan.dataVersionFixture(spec);
        P4E0R2QCasePlan.standard().preflightDataVersion(spec, fixture);
        var path = caseRoot.resolve(LARGE_FIXTURE_DIRECTORY).resolve("data-version.dat");
        return P4E0ResearchWireNbt.write(
                path,
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                1_048_576L,
                1_048_576L,
                output -> {
                    P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
                    switch (fixture.resultingState()) {
                        case MISSING -> {
                            // An empty root preserves a physically absent DataVersion field.
                        }
                        case STRING_TAG -> {
                            output.writeByte(Tag.TAG_STRING);
                            output.writeUTF("DataVersion");
                            output.writeUTF("wrong-type");
                        }
                        case INT_TAG_WRONG_VALUE -> {
                            output.writeByte(Tag.TAG_INT);
                            output.writeUTF("DataVersion");
                            output.writeInt(fixture.resultingIntValue());
                        }
                    }
                    output.writeByte(Tag.TAG_END);
                });
    }

    private static P4E0R2QFormalResult completedResult(
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            P4E0R2QProfile.CounterValues counters,
            long dfuInvocations,
            long attachmentAdmissions,
            long rawRootClaims,
            long targetsAudited,
            P4E0R2QFormalResult.HeapFacts heap,
            long elapsedMillis,
            String caseFixtureChecksum) {
        var expected = spec.expectedFailure();
        var qualification = switch (spec.kind()) {
            case POSITIVE -> P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT;
            case COUNTER_MAX_PLUS_ONE ->
                    P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER;
            case DATA_VERSION_MISSING,
                    DATA_VERSION_WRONG_TYPE,
                    DATA_VERSION_WRONG_VALUE ->
                    P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_DATA_VERSION;
        };
        var code = expected.map(value -> value.code().name()).orElse("NONE");
        var stage = expected.map(value -> value.stage().slug()).orElse("NONE");
        return new P4E0R2QFormalResult(
                control.studyId(), spec.caseId(), spec.index(), control.gitHead(),
                control.gitTree(), control.profileHash(), control.casePlanHash(),
                control.fixtureRootHash(), caseFixtureChecksum, control.runOrderHash(),
                control.implementationSchemaVersion(),
                P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                qualification,
                spec.targetCounter(), spec.maximum(), spec.observedAtLeast(),
                code, code, stage, stage, spec.allOtherCountersWithinLimit(), counters,
                dfuInvocations, attachmentAdmissions, rawRootClaims, targetsAudited, 0L,
                heap, elapsedMillis, "");
    }

    private static P4E0R2QProfile.CounterValues counters(
            P4E0R2QAuditBudget.Facts facts, PerFileMaxima maxima) {
        var structural = facts.structural();
        return new P4E0R2QProfile.CounterValues(
                facts.directoryEntries(),
                facts.relevantRecords(),
                maxima.compressedBytes,
                maxima.decompressedBytes,
                maxima.containerDepth,
                maxima.compoundContainers,
                maxima.compoundFields,
                maxima.listElements,
                maxima.byteArrays,
                maxima.intArrays,
                maxima.longArrays,
                maxima.modifiedUtf,
                maxima.scalarTags,
                facts.compressedBytes(),
                facts.decompressedBytes(),
                structural.compoundContainers(),
                structural.compoundFieldEntries(),
                structural.listElements(),
                structural.byteArrayElements(),
                structural.intArrayElements(),
                structural.longArrayElements(),
                structural.modifiedUtf8Bytes(),
                structural.scalarTags(),
                facts.attachmentAdmissions(),
                facts.rawRootClaims());
    }

    private static P4E0R2QProfile.CounterValues expectedCounters(
            P4E0R2QCasePlan.CaseSpec spec) {
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE) {
            return P4E0R2QProfile.locked().candidateValues();
        }
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
            return P4E0R2QFixturePlan.negativeFixture(spec).observedCounters();
        }
        return zeroCounters();
    }

    static String expectedCaseFixtureChecksum(P4E0R2QCasePlan.CaseSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var counters = expectedCounters(spec);
        var text = new StringBuilder("p4-e0-r2q-case-fixture-v0\n")
                .append("case_index=").append(spec.index())
                .append("\ncase_id=").append(spec.caseId())
                .append("\ncase_kind=").append(spec.kind().name())
                .append("\ntarget=").append(spec.targetCounter()
                        .map(P4E0R2QProfile.Counter::slug).orElse("NONE"))
                .append("\nmaximum=").append(spec.maximum())
                .append("\nobserved_at_least=").append(spec.observedAtLeast()).append('\n');
        for (var counter : P4E0R2QProfile.Counter.values()) {
            text.append("counter.").append(counter.slug()).append('=')
                    .append(counters.value(counter)).append('\n');
        }
        var failure = spec.expectedFailure();
        text.append("failure_code=")
                .append(failure.map(value -> value.code().name()).orElse("NONE"))
                .append("\nfailure_stage=")
                .append(failure.map(value -> value.stage().slug()).orElse("NONE"))
                .append('\n');
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
            var negative = P4E0R2QFixturePlan.negativeFixture(spec);
            P4E0R2QJointRecords.requireNegativeShape(spec.targetCounter().orElseThrow());
            text.append(counterFixturePayload(spec, negative));
            for (var compensation : negative.physicalBinding().compensations()) {
                text.append("compensation=").append(compensation.counter().name()).append(':')
                        .append(compensation.expectedDelta()).append(':')
                        .append(compensation.mechanism().name()).append(':')
                        .append(compensation.placement().name()).append('\n');
            }
        } else if (spec.kind() != P4E0R2QCasePlan.CaseKind.POSITIVE) {
            var dataVersion = P4E0R2QFixturePlan.dataVersionFixture(spec);
            text.append("data_version=").append(dataVersion.resultingState().name()).append(':')
                    .append(dataVersion.proofKind().name()).append(':')
                    .append(dataVersion.resultingIntValue()).append('\n');
        }
        text.append("store_bytes=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_STORE_BYTES)
                .append("\nstore_checksum=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_STORE_CHECKSUM).append('\n');
        return P4E0ResearchHashing.sha256(text.toString());
    }

    /**
     * Digests the case-owned physical fixture without retaining raw bytes. Relative names keep
     * primary and {@code .dat_old} source shapes distinct; per-file hashes bind the exact
     * gzip/NBT representation, while the locked Store/root scalars bind the generated root
     * envelope that is exercised later in the same child.
     */
    private static String physicalFixtureChecksum(
            Path caseRoot, P4E0R2QCasePlan.CaseSpec spec) throws IOException {
        var canonical = new StringBuilder("p4-e0-r2q-physical-case-v0\n")
                .append("plan=").append(expectedCaseFixtureChecksum(spec)).append('\n')
                .append("store_carrier=")
                .append(P4E0R2QStoreJournalFixtures.CURRENT_STORE_CHECKSUM).append('\n')
                .append("root_exact=").append(P4E0R2QStoreJournalFixtures.RAW_ROOTS)
                .append("\nroot_over=").append(P4E0R2QStoreJournalFixtures.RAW_ROOTS_OVER)
                .append('\n');
        appendPhysicalTree(
                canonical,
                caseRoot,
                caseRoot.resolve(LARGE_FIXTURE_DIRECTORY));
        var primary = com.yo1no.gramarye.magic.definition.store
                .P4D3StoreJournalFixture.primary(
                        caseRoot.resolve(GAME_DIRECTORY).resolve(WORLD_DIRECTORY));
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                || spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
            appendPhysicalFile(canonical, caseRoot, primary);
        } else if (Files.exists(primary, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("DataVersion control unexpectedly owns a Store primary");
        }
        return P4E0ResearchHashing.sha256(canonical.toString());
    }

    private static void appendPhysicalTree(
            StringBuilder canonical, Path caseRoot, Path tree) throws IOException {
        if (!Files.isDirectory(tree, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(tree)) {
            throw new IOException("formal physical fixture tree is unavailable");
        }
        final List<Path> paths;
        try (var walk = Files.walk(tree)) {
            paths = walk.sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (var path : paths) {
            if (Files.isSymbolicLink(path)) {
                throw new IOException("formal physical fixture contains a symbolic link");
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                canonical.append("directory=").append(portableRelative(caseRoot, path))
                        .append('\n');
            } else {
                appendPhysicalFile(canonical, caseRoot, path);
            }
        }
    }

    private static void appendPhysicalFile(
            StringBuilder canonical, Path caseRoot, Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("formal physical fixture contains a non-regular file");
        }
        canonical.append("file=").append(portableRelative(caseRoot, path))
                .append('|').append(Files.size(path)).append('|')
                .append(P4E0ResearchHashing.sha256(path)).append('\n');
    }

    private static String portableRelative(Path root, Path path) throws IOException {
        var relative = root.relativize(path).toString().replace('\\', '/');
        if (relative.isEmpty() || relative.startsWith("../")
                || relative.indexOf('\n') >= 0 || relative.indexOf('\r') >= 0) {
            throw new IOException("formal physical fixture path is invalid");
        }
        return relative;
    }

    private static void requireObservedCaseVector(
            P4E0R2QCasePlan.CaseSpec spec,
            P4E0R2QProfile.CounterValues observed) throws IOException {
        if (!observed.equals(expectedCounters(spec))) {
            throw new IOException("formal actual case vector differs from compiled fixture plan");
        }
    }

    private static long expectedDfu(P4E0R2QCasePlan.CaseSpec ignored) {
        return 0L;
    }

    private static long expectedAdmissions(P4E0R2QCasePlan.CaseSpec spec) {
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                || spec.targetCounter().orElse(null)
                        == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS) {
            return 1_024L;
        }
        if (spec.targetCounter().orElse(null)
                == P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS) {
            return 1_025L;
        }
        return 0L;
    }

    private static long expectedRawRoots(P4E0R2QCasePlan.CaseSpec spec) {
        if (spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE) {
            return 65_536L;
        }
        return spec.targetCounter().orElse(null) == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS
                ? 65_537L : 0L;
    }

    private static long expectedTargetsAudited(P4E0R2QCasePlan.CaseSpec spec) {
        return spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE ? 65_536L : 0L;
    }

    private static P4E0R2QProfile.CounterValues zeroCounters() {
        return new P4E0R2QProfile.CounterValues(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L);
    }

    private static String counterFixturePayload(
            P4E0R2QCasePlan.CaseSpec spec,
            P4E0R2QFixturePlan.NegativeFixture fixture) {
        var proof = fixture.proof();
        return "schema=0\ncase=" + spec.caseId()
                + "\ntarget=" + proof.targetCounter().slug()
                + "\nmutation=" + proof.mutationKind().name()
                + "\nproof=" + proof.proofKind().name()
                + "\nsource=" + proof.sourceValue()
                + "\nobserved=" + proof.observedValue() + "\n";
    }

    private static JsonObject manifest(
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            MaterializedFacts facts) {
        var expected = spec.expectedFailure();
        var json = new JsonObject();
        json.addProperty("schema_version", 0);
        json.addProperty("study_id", control.studyId());
        json.addProperty("case_id", spec.caseId());
        json.addProperty("case_index", spec.index());
        json.addProperty("case_kind", spec.kind().name());
        json.addProperty("fixture_root_hash", control.fixtureRootHash());
        json.addProperty("run_order_hash", control.runOrderHash());
        json.addProperty("target_counter", spec.targetCounter()
                .map(P4E0R2QProfile.Counter::slug).orElse("NONE"));
        json.addProperty("maximum", spec.maximum());
        json.addProperty("observed_at_least", spec.observedAtLeast());
        json.addProperty("expected_failure_code",
                expected.map(value -> value.code().name()).orElse("NONE"));
        json.addProperty("expected_stage",
                expected.map(value -> value.stage().slug()).orElse("NONE"));
        json.addProperty("materialization", facts.materialization());
        json.addProperty("directory_entries", facts.directoryEntries());
        json.addProperty("relevant_records", facts.relevantRecords());
        json.addProperty("fixture_checksum", facts.fixtureChecksum());
        return json;
    }

    private static void writeManifest(Path path, JsonObject json) throws IOException {
        var bytes = (json + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAXIMUM_MANIFEST_BYTES
                || Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("formal case manifest cannot be created");
        }
        Files.createDirectories(path.getParent());
        try (var channel = java.nio.channels.FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static CaseManifest readManifest(
            Path path,
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            boolean verifyPhysicalFixture) throws IOException {
        var text = readBounded(path, MAXIMUM_MANIFEST_BYTES);
        requireNoDuplicateManifestFields(text);
        final JsonObject json;
        try {
            json = JsonParser.parseString(text.stripTrailing()).getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("formal case manifest is malformed");
        }
        var expectedFailure = spec.expectedFailure();
        var expectedCounters = expectedCounters(spec);
        var expectedFacts = new MaterializedFacts(
                expectedMaterialization(spec),
                spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                        ? 4_096L
                        : spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE
                                ? expectedCounters.directoryEntries() : 1L,
                spec.kind() == P4E0R2QCasePlan.CaseKind.POSITIVE
                        ? 2_048L
                        : spec.kind() == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE
                                ? expectedCounters.relevantRecords() : 1L,
                ZERO_SHA256);
        try {
            if (!text.endsWith("\n") || text.indexOf('\r') >= 0
                    || !json.keySet().equals(MANIFEST_FIELDS)
                    || json.get("schema_version").getAsInt() != 0
                    || !json.get("study_id").getAsString().equals(control.studyId())
                    || !json.get("case_id").getAsString().equals(spec.caseId())
                    || json.get("case_index").getAsInt() != spec.index()
                    || !json.get("case_kind").getAsString().equals(spec.kind().name())
                    || !json.get("fixture_root_hash").getAsString()
                            .equals(control.fixtureRootHash())
                    || !json.get("run_order_hash").getAsString().equals(control.runOrderHash())
                    || !json.get("target_counter").getAsString().equals(spec.targetCounter()
                            .map(P4E0R2QProfile.Counter::slug).orElse("NONE"))
                    || json.get("maximum").getAsLong() != spec.maximum()
                    || json.get("observed_at_least").getAsLong() != spec.observedAtLeast()
                    || !json.get("expected_failure_code").getAsString().equals(
                            expectedFailure.map(value -> value.code().name()).orElse("NONE"))
                    || !json.get("expected_stage").getAsString().equals(
                            expectedFailure.map(value -> value.stage().slug()).orElse("NONE"))) {
                throw new IOException("formal case manifest identity changed");
            }
            var result = new CaseManifest(
                    json.get("fixture_checksum").getAsString(),
                    json.get("materialization").getAsString(),
                    json.get("directory_entries").getAsLong(),
                    json.get("relevant_records").getAsLong());
            if (!result.materialization().equals(expectedFacts.materialization())
                    || result.directoryEntries() != expectedFacts.directoryEntries()
                    || result.relevantRecords() != expectedFacts.relevantRecords()
                    || !(manifest(control, spec, result.asFacts()) + "\n").equals(text)
                    || (verifyPhysicalFixture
                            && !result.fixtureChecksum().equals(physicalFixtureChecksum(
                                    path.getParent(), spec)))) {
                throw new IOException("formal case manifest differs from compiled plan");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new IOException("formal case manifest values are invalid");
        }
    }

    private static String expectedMaterialization(P4E0R2QCasePlan.CaseSpec spec) {
        return switch (spec.kind()) {
            case POSITIVE -> "EXACT_PHYSICAL_POSITIVE";
            case COUNTER_MAX_PLUS_ONE -> "FULL_PHYSICAL_MUTATION_"
                    + P4E0R2QCasePlan.standard()
                            .preflightNegative(spec, P4E0R2QFixturePlan.negativeFixture(spec))
                            .physicalProofKind().name();
            case DATA_VERSION_MISSING,
                    DATA_VERSION_WRONG_TYPE,
                    DATA_VERSION_WRONG_VALUE -> "WIRE_DATA_VERSION_CONTROL";
        };
    }

    private static void requireNoDuplicateManifestFields(String text) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            var names = new java.util.HashSet<String>();
            reader.beginObject();
            while (reader.hasNext()) {
                if (!names.add(reader.nextName())) {
                    throw new IOException("formal case manifest has a duplicate field");
                }
                reader.skipValue();
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("formal case manifest has trailing input");
            }
        } catch (IllegalStateException exception) {
            throw new IOException("formal case manifest streaming parse failed");
        }
    }

    private static List<Path> listRegularEntries(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("formal fixture directory is unavailable");
        }
        try (var entries = Files.list(directory)) {
            var paths = entries.sorted().toList();
            for (var path : paths) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(path)) {
                    throw new IOException("formal fixture directory has a non-file entry");
                }
            }
            return paths;
        }
    }

    private static void writeNewBytes(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        try (var channel = java.nio.channels.FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
    }

    private static String readBounded(Path path, int maximum) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(path)) {
            throw new IOException("formal bounded case file is unavailable");
        }
        var output = new ByteArrayOutputStream(Math.min(maximum, 8_192));
        try (var input = Files.newInputStream(path, StandardOpenOption.READ)) {
            var buffer = new byte[8_192];
            while (output.size() <= maximum) {
                var read = input.read(buffer, 0, Math.min(buffer.length, maximum + 1 - output.size()));
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
            }
            if (output.size() > maximum || input.read() >= 0) {
                throw new IOException("formal bounded case file exceeds its cap");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray())).toString();
        } catch (java.nio.charset.CharacterCodingException exception) {
            throw new IOException("formal bounded case file is not UTF-8");
        }
    }

    private static void deleteTreeIfPresent(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(root)) {
            throw new IOException("formal cleanup refuses symbolic links");
        }
        try (var paths = Files.walk(root)) {
            var ordered = paths.sorted(Comparator.reverseOrder()).toList();
            for (var path : ordered) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("formal cleanup refuses symbolic links");
                }
                Files.delete(path);
            }
        }
    }

    private static P4E0R2QCasePlan.CaseSpec caseSpec(int index) {
        if (index < 0 || index >= P4E0R2QCasePlan.CASE_COUNT) {
            throw new IllegalArgumentException("formal R2Q case index is outside plan");
        }
        return P4E0R2QCasePlan.standard().cases().get(index);
    }

    private static Path normalized(Path value) {
        return Objects.requireNonNull(value, "path").toAbsolutePath().normalize();
    }

    private static long elapsedMillis(long started) {
        return Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started));
    }

    private record MaterializedFacts(
            String materialization,
            long directoryEntries,
            long relevantRecords,
            String fixtureChecksum) {
        private MaterializedFacts {
            if (materialization == null || !materialization.matches("[A-Z0-9_]{1,128}")
                    || directoryEntries < 0 || relevantRecords < 0
                    || fixtureChecksum == null || !fixtureChecksum.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid formal materialized fixture facts");
            }
        }
    }

    record CounterPreparationRegression(
            int casesVerified,
            long case04DecompressedBytesPerFile,
            int strictDataVersionChecks,
            int ownerGuardClassifications,
            int formalChildrenStarted,
            String sequenceChecksum) {
        CounterPreparationRegression {
            if ((casesVerified != 1 && casesVerified != P4E0R2QProfile.COUNTER_COUNT)
                    || (casesVerified == 1 && case04DecompressedBytesPerFile != 268_435_457L)
                    || (casesVerified == P4E0R2QProfile.COUNTER_COUNT
                            && case04DecompressedBytesPerFile != 268_435_457L)
                    || strictDataVersionChecks != casesVerified
                    || ownerGuardClassifications != casesVerified
                    || formalChildrenStarted != 0
                    || sequenceChecksum == null
                    || !sequenceChecksum.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "invalid R2Q counter preparation regression facts");
            }
        }
    }

    private static final class RegressionRootCleanup implements AutoCloseable {
        private final Path root;

        private RegressionRootCleanup(Path root) {
            this.root = normalized(root);
        }

        @Override
        public void close() throws IOException {
            deleteTreeIfPresent(root);
        }
    }

    static final class FixtureInvalidException extends IOException {
        private FixtureInvalidException(IOException cause) {
            super("formal physical fixture is invalid", cause);
        }
    }

    private record CaseManifest(
            String fixtureChecksum,
            String materialization,
            long directoryEntries,
            long relevantRecords) {
        private CaseManifest {
            if (fixtureChecksum == null || !fixtureChecksum.matches("[0-9a-f]{64}")
                    || materialization == null || !materialization.matches("[A-Z0-9_]{1,128}")
                    || directoryEntries < 0 || relevantRecords < 0) {
                throw new IllegalArgumentException("invalid formal case manifest");
            }
        }

        private MaterializedFacts asFacts() {
            return new MaterializedFacts(
                    materialization, directoryEntries, relevantRecords, fixtureChecksum);
        }
    }

    private static final class PerFileMaxima {
        private long compressedBytes;
        private long decompressedBytes;
        private long containerDepth;
        private long compoundContainers;
        private long compoundFields;
        private long listElements;
        private long byteArrays;
        private long intArrays;
        private long longArrays;
        private long modifiedUtf;
        private long scalarTags;

        private void observe(P4E0ResearchWireNbt.ScanFacts facts) {
            var nbt = facts.nbt();
            compressedBytes = Math.max(compressedBytes, facts.physicalBytes());
            decompressedBytes = Math.max(decompressedBytes, facts.decompressedBytes());
            containerDepth = Math.max(containerDepth, nbt.maxContainerDepth());
            compoundContainers = Math.max(compoundContainers, nbt.compoundCount());
            compoundFields = Math.max(compoundFields, nbt.compoundEntryCount());
            listElements = Math.max(listElements, nbt.listElementCount());
            byteArrays = Math.max(byteArrays, nbt.byteArrayElements());
            intArrays = Math.max(intArrays, nbt.intArrayElements());
            longArrays = Math.max(longArrays, nbt.longArrayElements());
            modifiedUtf = Math.max(modifiedUtf, nbt.modifiedUtf8Bytes());
            scalarTags = Math.max(scalarTags, nbt.scalarTagCount());
        }
    }

    private static final class HeapTracker {
        private final long initialCommitted = Runtime.getRuntime().totalMemory();
        private long sampledPeak;

        private void sample() {
            var usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            sampledPeak = Math.max(sampledPeak, usage.getUsed());
        }

        private P4E0R2QFormalResult.HeapFacts facts() {
            sample();
            var poolPeak = 0L;
            for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
                var usage = pool.getPeakUsage();
                if (usage != null && usage.getUsed() >= 0L) {
                    poolPeak = Math.addExact(poolPeak, usage.getUsed());
                }
            }
            var maximum = Runtime.getRuntime().maxMemory();
            if (maximum != CONFIGURED_XMX_BYTES) {
                throw new IllegalStateException("formal child JVM Xmx differs from 1536 MiB");
            }
            return new P4E0R2QFormalResult.HeapFacts(
                    CONFIGURED_XMS_BYTES,
                    maximum,
                    initialCommitted,
                    sampledPeak,
                    poolPeak);
        }
    }
}
