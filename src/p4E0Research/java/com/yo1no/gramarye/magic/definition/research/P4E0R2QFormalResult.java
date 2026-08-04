package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Strict immutable, bounded per-child result used only by the R2Q formal supervisor. */
final class P4E0R2QFormalResult {
    static final int SCHEMA_VERSION = 0;
    static final int MAXIMUM_JSON_BYTES = 65_536;
    static final int MAXIMUM_EXCEPTION_CLASS_BYTES = 192;

    private static final Set<String> FIELDS = Set.of(
            "schema_version",
            "study_id",
            "case_id",
            "case_index",
            "git_head",
            "git_tree",
            "profile_hash",
            "case_plan_hash",
            "fixture_hash",
            "case_fixture_checksum",
            "run_order_hash",
            "implementation_schema_version",
            "process_classification",
            "qualification_result",
            "target_counter",
            "maximum",
            "observed_at_least",
            "expected_failure_code",
            "observed_failure_code",
            "expected_stage",
            "observed_stage",
            "all_other_counters_within_limit",
            "counter_values",
            "dfu_invocations",
            "attachment_admissions",
            "raw_root_claims",
            "targets_audited",
            "reclaim_invocations",
            "heap_xms",
            "heap_xmx",
            "initial_committed",
            "sampled_peak_used",
            "heap_pool_peak_sum",
            "elapsed_millis",
            "semantic_checksum",
            "exception_class");

    private final String studyId;
    private final String caseId;
    private final int caseIndex;
    private final String gitHead;
    private final String gitTree;
    private final String profileHash;
    private final String casePlanHash;
    private final String fixtureHash;
    private final String caseFixtureChecksum;
    private final String runOrderHash;
    private final int implementationSchemaVersion;
    private final ProcessClassification processClassification;
    private final QualificationResult qualificationResult;
    private final Optional<P4E0R2QProfile.Counter> targetCounter;
    private final long maximum;
    private final long observedAtLeast;
    private final String expectedFailureCode;
    private final String observedFailureCode;
    private final String expectedStage;
    private final String observedStage;
    private final boolean allOtherCountersWithinLimit;
    private final P4E0R2QProfile.CounterValues observedCounters;
    private final long dfuInvocations;
    private final long attachmentAdmissions;
    private final long rawRootClaims;
    private final long targetsAudited;
    private final long reclaimInvocations;
    private final HeapFacts heap;
    private final long elapsedMillis;
    private final String boundedExceptionClass;
    private final String semanticChecksum;

    P4E0R2QFormalResult(
            String studyId,
            String caseId,
            int caseIndex,
            String gitHead,
            String gitTree,
            String profileHash,
            String casePlanHash,
            String fixtureHash,
            String runOrderHash,
            int implementationSchemaVersion,
            ProcessClassification processClassification,
            QualificationResult qualificationResult,
            Optional<P4E0R2QProfile.Counter> targetCounter,
            long maximum,
            long observedAtLeast,
            String expectedFailureCode,
            String observedFailureCode,
            String expectedStage,
            String observedStage,
            boolean allOtherCountersWithinLimit,
            P4E0R2QProfile.CounterValues observedCounters,
            long dfuInvocations,
            long attachmentAdmissions,
            long rawRootClaims,
            long targetsAudited,
            long reclaimInvocations,
            HeapFacts heap,
            long elapsedMillis,
            String boundedExceptionClass) {
        this(
                studyId, caseId, caseIndex, gitHead, gitTree, profileHash, casePlanHash,
                fixtureHash, fixtureHash, runOrderHash, implementationSchemaVersion,
                processClassification, qualificationResult, targetCounter, maximum,
                observedAtLeast, expectedFailureCode, observedFailureCode, expectedStage,
                observedStage, allOtherCountersWithinLimit, observedCounters, dfuInvocations,
                attachmentAdmissions, rawRootClaims, targetsAudited, reclaimInvocations, heap,
                elapsedMillis, boundedExceptionClass);
    }

    P4E0R2QFormalResult(
            String studyId,
            String caseId,
            int caseIndex,
            String gitHead,
            String gitTree,
            String profileHash,
            String casePlanHash,
            String fixtureHash,
            String caseFixtureChecksum,
            String runOrderHash,
            int implementationSchemaVersion,
            ProcessClassification processClassification,
            QualificationResult qualificationResult,
            Optional<P4E0R2QProfile.Counter> targetCounter,
            long maximum,
            long observedAtLeast,
            String expectedFailureCode,
            String observedFailureCode,
            String expectedStage,
            String observedStage,
            boolean allOtherCountersWithinLimit,
            P4E0R2QProfile.CounterValues observedCounters,
            long dfuInvocations,
            long attachmentAdmissions,
            long rawRootClaims,
            long targetsAudited,
            long reclaimInvocations,
            HeapFacts heap,
            long elapsedMillis,
            String boundedExceptionClass) {
        this.studyId = sha256(studyId, "studyId");
        this.caseId = caseId(caseId);
        if (caseIndex < 0 || caseIndex >= P4E0R2QCasePlan.CASE_COUNT) {
            throw new IllegalArgumentException("formal case index is outside the plan");
        }
        this.caseIndex = caseIndex;
        this.gitHead = gitObject(gitHead, "gitHead");
        this.gitTree = gitObject(gitTree, "gitTree");
        this.profileHash = sha256(profileHash, "profileHash");
        this.casePlanHash = sha256(casePlanHash, "casePlanHash");
        this.fixtureHash = sha256(fixtureHash, "fixtureHash");
        this.caseFixtureChecksum = sha256(caseFixtureChecksum, "caseFixtureChecksum");
        this.runOrderHash = sha256(runOrderHash, "runOrderHash");
        if (implementationSchemaVersion
                != P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION) {
            throw new IllegalArgumentException("formal implementation schema changed");
        }
        this.implementationSchemaVersion = implementationSchemaVersion;
        this.processClassification = Objects.requireNonNull(
                processClassification, "processClassification");
        this.qualificationResult = Objects.requireNonNull(
                qualificationResult, "qualificationResult");
        this.targetCounter = Objects.requireNonNull(targetCounter, "targetCounter");
        if (maximum < 0 || observedAtLeast < 0) {
            throw new IllegalArgumentException("formal observation is negative");
        }
        this.maximum = maximum;
        this.observedAtLeast = observedAtLeast;
        this.expectedFailureCode = vocabulary(expectedFailureCode, "expectedFailureCode");
        this.observedFailureCode = vocabulary(observedFailureCode, "observedFailureCode");
        this.expectedStage = stage(expectedStage, "expectedStage");
        this.observedStage = stage(observedStage, "observedStage");
        this.allOtherCountersWithinLimit = allOtherCountersWithinLimit;
        this.observedCounters = Objects.requireNonNull(observedCounters, "observedCounters");
        this.dfuInvocations = nonNegative(dfuInvocations, "dfuInvocations");
        this.attachmentAdmissions = nonNegative(
                attachmentAdmissions, "attachmentAdmissions");
        this.rawRootClaims = nonNegative(rawRootClaims, "rawRootClaims");
        this.targetsAudited = nonNegative(targetsAudited, "targetsAudited");
        this.reclaimInvocations = nonNegative(reclaimInvocations, "reclaimInvocations");
        this.heap = Objects.requireNonNull(heap, "heap");
        this.elapsedMillis = nonNegative(elapsedMillis, "elapsedMillis");
        this.boundedExceptionClass = exceptionClass(boundedExceptionClass);
        requireClassificationContract();
        this.semanticChecksum = P4E0ResearchHashing.sha256(semanticPayload());
        if (toJsonLine().getBytes(StandardCharsets.UTF_8).length > MAXIMUM_JSON_BYTES) {
            throw new IllegalArgumentException("formal result exceeds 65,536 bytes");
        }
    }

    enum ProcessClassification {
        COMPLETED,
        REJECTED_BY_RESEARCH_GUARD,
        FIXTURE_INVALID,
        INSTRUMENTATION_FAILURE,
        CHILD_EXIT_FAILURE,
        TIMEOUT,
        OOME_EXIT
    }

    enum QualificationResult {
        ADMITTED_EXACT,
        REJECTED_EXPECTED_COUNTER,
        REJECTED_EXPECTED_DATA_VERSION,
        NOT_OBSERVED
    }

    record HeapFacts(
            long xms,
            long xmx,
            long initialCommitted,
            long sampledPeakUsed,
            long heapPoolPeakSum) {
        HeapFacts {
            if (xms < 0 || xmx < 0 || initialCommitted < 0 || sampledPeakUsed < 0
                    || heapPoolPeakSum < 0 || (xmx > 0 && xms > xmx)) {
                throw new IllegalArgumentException("invalid formal heap facts");
            }
        }

        static HeapFacts unobserved() {
            return new HeapFacts(0L, 0L, 0L, 0L, 0L);
        }
    }

    static P4E0R2QFormalResult parseLine(String line) throws IOException {
        Objects.requireNonNull(line, "line");
        requireBound(line);
        if (!line.endsWith("\n") || line.indexOf('\r') >= 0
                || line.substring(0, line.length() - 1).contains("\n")) {
            throw new IOException("formal result framing is not one canonical JSON line");
        }
        requireNoDuplicateObjectFields(line.substring(0, line.length() - 1));
        final JsonObject json;
        try {
            json = JsonParser.parseString(line.substring(0, line.length() - 1))
                    .getAsJsonObject();
        } catch (RuntimeException exception) {
            throw new IOException("formal result JSON is malformed");
        }
        if (!json.keySet().equals(FIELDS)
                || exactInt(json, "schema_version") != SCHEMA_VERSION) {
            throw new IOException("formal result field schema changed");
        }
        var counters = parseCounters(requireObject(json, "counter_values"));
        final P4E0R2QFormalResult result;
        try {
            result = new P4E0R2QFormalResult(
                    exactString(json, "study_id"),
                    exactString(json, "case_id"),
                    exactInt(json, "case_index"),
                    exactString(json, "git_head"),
                    exactString(json, "git_tree"),
                    exactString(json, "profile_hash"),
                    exactString(json, "case_plan_hash"),
                    exactString(json, "fixture_hash"),
                    exactString(json, "case_fixture_checksum"),
                    exactString(json, "run_order_hash"),
                    exactInt(json, "implementation_schema_version"),
                    enumValue(ProcessClassification.class,
                            exactString(json, "process_classification")),
                    enumValue(QualificationResult.class,
                            exactString(json, "qualification_result")),
                    counter(exactString(json, "target_counter")),
                    exactLong(json, "maximum"),
                    exactLong(json, "observed_at_least"),
                    exactString(json, "expected_failure_code"),
                    exactString(json, "observed_failure_code"),
                    exactString(json, "expected_stage"),
                    exactString(json, "observed_stage"),
                    exactBoolean(json, "all_other_counters_within_limit"),
                    counters,
                    exactLong(json, "dfu_invocations"),
                    exactLong(json, "attachment_admissions"),
                    exactLong(json, "raw_root_claims"),
                    exactLong(json, "targets_audited"),
                    exactLong(json, "reclaim_invocations"),
                    new HeapFacts(
                            exactLong(json, "heap_xms"),
                            exactLong(json, "heap_xmx"),
                            exactLong(json, "initial_committed"),
                            exactLong(json, "sampled_peak_used"),
                            exactLong(json, "heap_pool_peak_sum")),
                    exactLong(json, "elapsed_millis"),
                    exactString(json, "exception_class"));
        } catch (RuntimeException exception) {
            throw new IOException("formal result values are invalid");
        }
        if (!result.semanticChecksum.equals(exactString(json, "semantic_checksum"))
                || !result.toJsonLine().equals(line)) {
            throw new IOException("formal result is not canonical or checksum-valid");
        }
        return result;
    }

    String toJsonLine() {
        var json = baseJson();
        json.addProperty("semantic_checksum", semanticChecksum);
        json.addProperty("exception_class", boundedExceptionClass);
        return json + "\n";
    }

    ProcessClassification processClassification() {
        return processClassification;
    }

    QualificationResult qualificationResult() {
        return qualificationResult;
    }

    int caseIndex() {
        return caseIndex;
    }

    String caseId() {
        return caseId;
    }

    String studyId() {
        return studyId;
    }

    String gitHead() {
        return gitHead;
    }

    String gitTree() {
        return gitTree;
    }

    String profileHash() {
        return profileHash;
    }

    String casePlanHash() {
        return casePlanHash;
    }

    String fixtureHash() {
        return fixtureHash;
    }

    String caseFixtureChecksum() {
        return caseFixtureChecksum;
    }

    String runOrderHash() {
        return runOrderHash;
    }

    int implementationSchemaVersion() {
        return implementationSchemaVersion;
    }

    Optional<P4E0R2QProfile.Counter> targetCounter() {
        return targetCounter;
    }

    long maximum() {
        return maximum;
    }

    long observedAtLeast() {
        return observedAtLeast;
    }

    String expectedFailureCode() {
        return expectedFailureCode;
    }

    String observedFailureCode() {
        return observedFailureCode;
    }

    String expectedStage() {
        return expectedStage;
    }

    String observedStage() {
        return observedStage;
    }

    boolean allOtherCountersWithinLimit() {
        return allOtherCountersWithinLimit;
    }

    P4E0R2QProfile.CounterValues observedCounters() {
        return observedCounters;
    }

    long dfuInvocations() {
        return dfuInvocations;
    }

    long attachmentAdmissions() {
        return attachmentAdmissions;
    }

    long rawRootClaims() {
        return rawRootClaims;
    }

    long targetsAudited() {
        return targetsAudited;
    }

    long reclaimInvocations() {
        return reclaimInvocations;
    }

    HeapFacts heap() {
        return heap;
    }

    long elapsedMillis() {
        return elapsedMillis;
    }

    String semanticChecksum() {
        return semanticChecksum;
    }

    String boundedExceptionClass() {
        return boundedExceptionClass;
    }

    boolean hasFormalIdentity(P4E0R2QFormalEvidence.StudyControl control) {
        return studyId.equals(control.studyId())
                && gitHead.equals(control.gitHead())
                && gitTree.equals(control.gitTree())
                && profileHash.equals(control.profileHash())
                && casePlanHash.equals(control.casePlanHash())
                && fixtureHash.equals(control.fixtureRootHash())
                && runOrderHash.equals(control.runOrderHash())
                && implementationSchemaVersion == control.implementationSchemaVersion();
    }

    private void requireClassificationContract() {
        var lockedFormal = profileHash.equals(P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH)
                && casePlanHash.equals(P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH)
                && runOrderHash.equals(P4E0R2QFormalEvidence.formalRunOrderHash());
        P4E0R2QCasePlan.CaseSpec spec = null;
        if (lockedFormal) {
            spec = P4E0R2QCasePlan.standard().cases().get(caseIndex);
            if (!caseId.equals(spec.caseId())) {
                throw new IllegalArgumentException("formal result differs from the locked case");
            }
        }
        if (processClassification == ProcessClassification.COMPLETED) {
            if (qualificationResult == QualificationResult.NOT_OBSERVED
                    || !boundedExceptionClass.isEmpty()) {
                throw new IllegalArgumentException("completed result lacks qualification");
            }
            if (lockedFormal && (heap.xms() != 512L * 1_024L * 1_024L
                    || heap.xmx() != 1_536L * 1_024L * 1_024L
                    || heap.initialCommitted() <= 0L
                    || heap.sampledPeakUsed() <= 0L
                    || heap.heapPoolPeakSum() <= 0L
                    || elapsedMillis <= 0L)) {
                throw new IllegalArgumentException("completed formal result used wrong heap tier");
            }
        } else if (qualificationResult != QualificationResult.NOT_OBSERVED) {
            throw new IllegalArgumentException("failed process carries qualification evidence");
        }
        switch (qualificationResult) {
            case ADMITTED_EXACT -> {
                if (targetCounter.isPresent() || maximum != 0L || observedAtLeast != 0L
                        || !expectedFailureCode.equals("NONE")
                        || !observedFailureCode.equals("NONE")
                        || !expectedStage.equals("NONE") || !observedStage.equals("NONE")
                        || !allOtherCountersWithinLimit
                        || (lockedFormal && (spec.kind()
                                        != P4E0R2QCasePlan.CaseKind.POSITIVE
                                || dfuInvocations != 0L
                                || attachmentAdmissions != 1_024L
                                || rawRootClaims != 65_536L
                                || targetsAudited != 65_536L
                                || reclaimInvocations != 0L))) {
                    throw new IllegalArgumentException("exact admission carries rejection facts");
                }
                if (lockedFormal) {
                    for (var counter : P4E0R2QProfile.Counter.values()) {
                        if (observedCounters.value(counter)
                                != P4E0R2QProfile.locked().maximum(counter)) {
                            throw new IllegalArgumentException(
                                    "exact admission counter vector differs from profile");
                        }
                    }
                }
            }
            case REJECTED_EXPECTED_COUNTER -> {
                if (targetCounter.isEmpty()
                        || !expectedFailureCode.equals("COUNTER_CAPACITY_EXCEEDED")
                        || !observedFailureCode.equals(expectedFailureCode)
                        || !observedStage.equals(expectedStage)
                        || observedAtLeast != Math.addExact(maximum, 1L)
                        || !allOtherCountersWithinLimit) {
                    throw new IllegalArgumentException("counter rejection is not exact");
                }
                if (lockedFormal) {
                    var target = targetCounter.orElseThrow();
                    var failure = spec.expectedFailure().orElseThrow();
                    if (spec.kind() != P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE
                            || spec.targetCounter().orElseThrow() != target
                            || maximum != spec.maximum()
                            || observedAtLeast != spec.observedAtLeast()
                            || observedCounters.value(target) != observedAtLeast
                            || !expectedStage.equals(failure.stage().slug())
                            || !expectedFailureCode.equals(failure.code().name())
                            || dfuInvocations != 0L
                            || reclaimInvocations != 0L
                            || targetsAudited != 0L) {
                        throw new IllegalArgumentException(
                                "counter result differs from locked case semantics");
                    }
                    if (target == P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS) {
                        if (attachmentAdmissions != observedAtLeast || rawRootClaims != 0L) {
                            throw new IllegalArgumentException(
                                    "attachment rejection crossed a later checkpoint");
                        }
                    } else if (target == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS) {
                        if (attachmentAdmissions != 1_024L
                                || rawRootClaims != observedAtLeast) {
                            throw new IllegalArgumentException(
                                    "root rejection checkpoint facts changed");
                        }
                    } else if (attachmentAdmissions != 0L || rawRootClaims != 0L) {
                        throw new IllegalArgumentException(
                                "early counter rejection crossed a later checkpoint");
                    }
                }
            }
            case REJECTED_EXPECTED_DATA_VERSION -> {
                if (targetCounter.isPresent() || maximum != 0L || observedAtLeast != 0L
                        || !expectedFailureCode.startsWith("DATA_VERSION_")
                        || !observedFailureCode.equals(expectedFailureCode)
                        || !expectedStage.equals("data_version")
                        || !observedStage.equals(expectedStage)
                        || java.util.Arrays.stream(P4E0R2QProfile.Counter.values())
                                .anyMatch(counter -> observedCounters.value(counter) != 0L)
                        || (lockedFormal && (spec.kind()
                                        == P4E0R2QCasePlan.CaseKind.POSITIVE
                                || spec.kind()
                                        == P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE
                                || !expectedFailureCode.equals(
                                        spec.expectedFailure().orElseThrow().code().name())
                                || dfuInvocations != 0L
                                || attachmentAdmissions != 0L
                                || rawRootClaims != 0L
                                || targetsAudited != 0L
                                || reclaimInvocations != 0L))) {
                    throw new IllegalArgumentException("DataVersion rejection is not exact");
                }
            }
            case NOT_OBSERVED -> {
                // Process taxonomy is the bounded truth for failed observations.
            }
        }
        for (var counter : P4E0R2QProfile.Counter.values()) {
            var value = observedCounters.value(counter);
            if (value < 0
                    || (qualificationResult == QualificationResult.REJECTED_EXPECTED_COUNTER
                            && targetCounter.orElseThrow() != counter
                            && value > P4E0R2QProfile.locked().maximum(counter))) {
                throw new IllegalArgumentException("formal counter vector is invalid");
            }
        }
    }

    private String semanticPayload() {
        var json = baseJson();
        json.addProperty("exception_class", boundedExceptionClass);
        return json.toString();
    }

    private JsonObject baseJson() {
        var json = new JsonObject();
        json.addProperty("schema_version", SCHEMA_VERSION);
        json.addProperty("study_id", studyId);
        json.addProperty("case_id", caseId);
        json.addProperty("case_index", caseIndex);
        json.addProperty("git_head", gitHead);
        json.addProperty("git_tree", gitTree);
        json.addProperty("profile_hash", profileHash);
        json.addProperty("case_plan_hash", casePlanHash);
        json.addProperty("fixture_hash", fixtureHash);
        json.addProperty("case_fixture_checksum", caseFixtureChecksum);
        json.addProperty("run_order_hash", runOrderHash);
        json.addProperty("implementation_schema_version", implementationSchemaVersion);
        json.addProperty("process_classification", processClassification.name());
        json.addProperty("qualification_result", qualificationResult.name());
        json.addProperty("target_counter", targetCounter
                .map(P4E0R2QProfile.Counter::slug).orElse("NONE"));
        json.addProperty("maximum", maximum);
        json.addProperty("observed_at_least", observedAtLeast);
        json.addProperty("expected_failure_code", expectedFailureCode);
        json.addProperty("observed_failure_code", observedFailureCode);
        json.addProperty("expected_stage", expectedStage);
        json.addProperty("observed_stage", observedStage);
        json.addProperty("all_other_counters_within_limit", allOtherCountersWithinLimit);
        json.add("counter_values", counterJson(observedCounters));
        json.addProperty("dfu_invocations", dfuInvocations);
        json.addProperty("attachment_admissions", attachmentAdmissions);
        json.addProperty("raw_root_claims", rawRootClaims);
        json.addProperty("targets_audited", targetsAudited);
        json.addProperty("reclaim_invocations", reclaimInvocations);
        json.addProperty("heap_xms", heap.xms());
        json.addProperty("heap_xmx", heap.xmx());
        json.addProperty("initial_committed", heap.initialCommitted());
        json.addProperty("sampled_peak_used", heap.sampledPeakUsed());
        json.addProperty("heap_pool_peak_sum", heap.heapPoolPeakSum());
        json.addProperty("elapsed_millis", elapsedMillis);
        return json;
    }

    private static JsonObject counterJson(P4E0R2QProfile.CounterValues values) {
        var json = new JsonObject();
        for (var counter : P4E0R2QProfile.Counter.values()) {
            json.addProperty(counter.slug(), values.value(counter));
        }
        return json;
    }

    private static P4E0R2QProfile.CounterValues parseCounters(JsonObject json)
            throws IOException {
        var names = EnumSet.allOf(P4E0R2QProfile.Counter.class).stream()
                .map(P4E0R2QProfile.Counter::slug).collect(java.util.stream.Collectors.toSet());
        if (!json.keySet().equals(names)) {
            throw new IOException("formal counter field set changed");
        }
        var values = new long[P4E0R2QProfile.COUNTER_COUNT];
        for (var counter : P4E0R2QProfile.Counter.values()) {
            values[counter.ordinal()] = exactLong(json, counter.slug());
        }
        return new P4E0R2QProfile.CounterValues(
                values[0], values[1], values[2], values[3], values[4],
                values[5], values[6], values[7], values[8], values[9],
                values[10], values[11], values[12], values[13], values[14],
                values[15], values[16], values[17], values[18], values[19],
                values[20], values[21], values[22], values[23], values[24]);
    }

    private static Optional<P4E0R2QProfile.Counter> counter(String value) {
        return value.equals("NONE")
                ? Optional.empty()
                : Optional.of(P4E0R2QProfile.Counter.fromSlug(value));
    }

    private static void requireNoDuplicateObjectFields(String text) throws IOException {
        try (var reader = new JsonReader(new StringReader(text))) {
            consume(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IOException("formal JSON has trailing input");
            }
        } catch (IllegalStateException exception) {
            throw new IOException("formal JSON token stream is malformed");
        }
    }

    private static void consume(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                var names = new HashSet<String>();
                while (reader.hasNext()) {
                    var name = reader.nextName();
                    if (!names.add(name)) {
                        throw new IOException("formal JSON has a duplicate object field");
                    }
                    consume(reader);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    consume(reader);
                }
                reader.endArray();
            }
            case STRING -> reader.nextString();
            case NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new IOException("formal JSON token is invalid");
        }
    }

    private static JsonObject requireObject(JsonObject json, String field) throws IOException {
        try {
            return json.getAsJsonObject(field);
        } catch (RuntimeException exception) {
            throw new IOException("formal JSON object field has wrong type");
        }
    }

    private static String exactString(JsonObject json, String field) throws IOException {
        try {
            var value = json.get(field);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                throw new IOException("formal JSON string field has wrong type");
            }
            return value.getAsString();
        } catch (RuntimeException exception) {
            throw new IOException("formal JSON string field is malformed");
        }
    }

    private static long exactLong(JsonObject json, String field) throws IOException {
        var text = exactNumber(json, field);
        if (!text.matches("0|-?[1-9][0-9]*")) {
            throw new IOException("formal JSON integer is not canonical");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IOException("formal JSON integer is outside long range");
        }
    }

    private static int exactInt(JsonObject json, String field) throws IOException {
        try {
            return Math.toIntExact(exactLong(json, field));
        } catch (ArithmeticException exception) {
            throw new IOException("formal JSON integer is outside int range");
        }
    }

    private static String exactNumber(JsonObject json, String field) throws IOException {
        try {
            var value = json.get(field);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isNumber()) {
                throw new IOException("formal JSON number field has wrong type");
            }
            return value.getAsString();
        } catch (RuntimeException exception) {
            throw new IOException("formal JSON number field is malformed");
        }
    }

    private static boolean exactBoolean(JsonObject json, String field) throws IOException {
        try {
            var value = json.get(field);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IOException("formal JSON boolean field has wrong type");
            }
            return value.getAsBoolean();
        } catch (RuntimeException exception) {
            throw new IOException("formal JSON boolean field is malformed");
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value);
    }

    private static String sha256(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " is not a lowercase SHA-256");
        }
        return value;
    }

    private static String gitObject(String value, String label) {
        Objects.requireNonNull(value, label);
        if (!value.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException(label + " is not a lowercase Git object id");
        }
        return value;
    }

    private static String caseId(String value) {
        Objects.requireNonNull(value, "caseId");
        if (value.length() > 112 || !value.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("formal case id is invalid");
        }
        return value;
    }

    private static String vocabulary(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length() > 96 || !value.matches("NONE|[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(label + " is outside bounded vocabulary");
        }
        return value;
    }

    private static String stage(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length() > 96 || !value.matches("NONE|[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException(label + " is outside bounded stage vocabulary");
        }
        return value;
    }

    private static String exceptionClass(String value) {
        Objects.requireNonNull(value, "boundedExceptionClass");
        if (value.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_EXCEPTION_CLASS_BYTES
                || (!value.isEmpty()
                        && !value.matches("[A-Za-z_$][A-Za-z0-9_$.]*"))) {
            throw new IllegalArgumentException("formal exception class is not bounded");
        }
        return value;
    }

    private static long nonNegative(long value, String label) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " is negative");
        }
        return value;
    }

    private static void requireBound(String line) throws IOException {
        if (line.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_JSON_BYTES) {
            throw new IOException("formal result exceeds 65,536 bytes");
        }
    }
}
