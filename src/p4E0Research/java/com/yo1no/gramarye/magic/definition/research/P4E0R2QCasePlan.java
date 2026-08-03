package com.yo1no.gramarye.magic.definition.research;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Exact immutable 1-positive, 25-counter, 3-DataVersion R2Q qualification plan. */
final class P4E0R2QCasePlan {
    static final int SCHEMA_VERSION = 0;
    static final int CASE_COUNT = 29;
    static final String CASE_PREFIX = "p4-e0-r2q-balanced-v0-1536-";

    private static final List<P4E0R2QProfile.Counter> COUNTER_PRECEDENCE = List.of(
            P4E0R2QProfile.Counter.DIRECTORY_ENTRIES,
            P4E0R2QProfile.Counter.RELEVANT_RECORDS,
            P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE,
            P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL,
            P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_PER_FILE,
            P4E0R2QProfile.Counter.DECOMPRESSED_BYTES_TOTAL,
            P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE,
            P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_TOTAL,
            P4E0R2QProfile.Counter.CONTAINER_DEPTH_PER_FILE,
            P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_PER_FILE,
            P4E0R2QProfile.Counter.COMPOUND_CONTAINERS_TOTAL,
            P4E0R2QProfile.Counter.SCALAR_TAGS_PER_FILE,
            P4E0R2QProfile.Counter.SCALAR_TAGS_TOTAL,
            P4E0R2QProfile.Counter.LIST_ELEMENTS_PER_FILE,
            P4E0R2QProfile.Counter.LIST_ELEMENTS_TOTAL,
            P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_PER_FILE,
            P4E0R2QProfile.Counter.BYTE_ARRAY_ELEMENTS_TOTAL,
            P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_PER_FILE,
            P4E0R2QProfile.Counter.INT_ARRAY_ELEMENTS_TOTAL,
            P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_PER_FILE,
            P4E0R2QProfile.Counter.LONG_ARRAY_ELEMENTS_TOTAL,
            P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE,
            P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_TOTAL,
            P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS,
            P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS);

    private final List<CaseSpec> cases;
    private final String canonicalJson;
    private final String planHash;

    private P4E0R2QCasePlan(List<CaseSpec> cases) {
        this.cases = validate(cases);
        this.canonicalJson = buildCanonicalJson(this.cases);
        this.planHash = P4E0ResearchHashing.sha256(canonicalJson);
    }

    static P4E0R2QCasePlan standard() {
        return Holder.PLAN;
    }

    List<CaseSpec> cases() {
        return cases;
    }

    String canonicalJson() {
        return canonicalJson;
    }

    String planHash() {
        return planHash;
    }

    NegativePreflight preflightNegative(
            CaseSpec spec,
            P4E0R2QFixturePlan.NegativeFixture fixture) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(fixture, "fixture");
        if (spec.kind() != CaseKind.COUNTER_MAX_PLUS_ONE
                || spec.targetCounter().isEmpty()
                || spec.expectedFailure().isEmpty()) {
            throw new IllegalArgumentException("R2Q counter fixture is not a valid negative");
        }
        var profile = P4E0R2QProfile.locked();
        var target = spec.targetCounter().orElseThrow();
        var actual = fixture.observedCounters();
        var proof = fixture.proof();
        var expectedObserved = Math.addExact(profile.maximum(target), 1L);
        if (actual.value(target) != expectedObserved
                || spec.maximum() != profile.maximum(target)
                || spec.observedAtLeast() != expectedObserved
                || proof.targetCounter() != target
                || proof.mutationKind() != spec.mutationKind()
                || !proof.coupledCounters().equals(spec.coupledCounters())) {
            throw new IllegalArgumentException("R2Q target is not exact maximum plus one");
        }
        for (var counter : P4E0R2QProfile.Counter.values()) {
            if (counter != target && actual.value(counter) > profile.maximum(counter)) {
                throw new IllegalArgumentException("R2Q negative has a second counter overrun");
            }
        }
        var first = firstExceeded(actual).orElseThrow(() ->
                new IllegalArgumentException("R2Q negative has no visible capacity failure"));
        var expected = spec.expectedFailure().orElseThrow();
        if (first != target || expected.counter().orElseThrow() != target
                || expected.stage() != stageFor(target)
                || expected.code() != FailureCode.COUNTER_CAPACITY_EXCEEDED) {
            throw new IllegalArgumentException("R2Q first-failure precedence changed");
        }
        return new NegativePreflight(
                target,
                actual.value(target),
                profile.maximum(target),
                expected.stage(),
                true,
                proof.proofKind());
    }

    DataVersionPreflight preflightDataVersion(
            CaseSpec spec,
            P4E0R2QFixturePlan.DataVersionFixture fixture) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(fixture, "fixture");
        if (spec.kind() == CaseKind.POSITIVE
                || spec.kind() == CaseKind.COUNTER_MAX_PLUS_ONE
                || fixture.caseKind() != spec.kind()
                || fixture.mutationKind() != spec.mutationKind()
                || fixture.sourceValue() != P4E0R2QProfile.locked().acceptedDataVersion()
                || fixture.expectedDfuInvocations() != 0
                || spec.expectedDfuInvocations() != 0) {
            throw new IllegalArgumentException("R2Q DataVersion fixture is not a typed control");
        }
        var failure = spec.expectedFailure().orElseThrow();
        if (failure.code() != expectedDataVersionFailure(spec.kind())
                || failure.stage() != FailureStage.DATA_VERSION
                || failure.counter().isPresent()) {
            throw new IllegalArgumentException("R2Q DataVersion first failure changed");
        }
        return new DataVersionPreflight(
                spec.kind(),
                failure.code(),
                failure.stage(),
                fixture.proofKind(),
                fixture.resultingState(),
                0);
    }

    static Optional<P4E0R2QProfile.Counter> firstExceeded(
            P4E0R2QProfile.CounterValues actual) {
        Objects.requireNonNull(actual, "actual");
        var profile = P4E0R2QProfile.locked();
        for (var counter : COUNTER_PRECEDENCE) {
            if (actual.value(counter) > profile.maximum(counter)) {
                return Optional.of(counter);
            }
        }
        return Optional.empty();
    }

    static FailureStage stageFor(P4E0R2QProfile.Counter counter) {
        return switch (counter) {
            case DIRECTORY_ENTRIES -> FailureStage.DIRECTORY_ENTRIES;
            case RELEVANT_RECORDS -> FailureStage.RELEVANT_RECORDS;
            case COMPRESSED_BYTES_PER_FILE -> FailureStage.PER_FILE_COMPRESSED;
            case COMPRESSED_BYTES_TOTAL -> FailureStage.AGGREGATE_COMPRESSED;
            case DECOMPRESSED_BYTES_PER_FILE -> FailureStage.PER_FILE_DECOMPRESSED;
            case DECOMPRESSED_BYTES_TOTAL -> FailureStage.AGGREGATE_DECOMPRESSED;
            case COMPOUND_FIELD_ENTRIES_PER_FILE,
                    COMPOUND_FIELD_ENTRIES_TOTAL ->
                    FailureStage.COMPOUND_FIELD_CHECKPOINT;
            case CONTAINER_DEPTH_PER_FILE,
                    COMPOUND_CONTAINERS_PER_FILE,
                    COMPOUND_CONTAINERS_TOTAL,
                    SCALAR_TAGS_PER_FILE,
                    SCALAR_TAGS_TOTAL -> FailureStage.DEPTH_CONTAINER_SCALAR_KIND;
            case LIST_ELEMENTS_PER_FILE,
                    LIST_ELEMENTS_TOTAL -> FailureStage.LIST_LENGTH;
            case BYTE_ARRAY_ELEMENTS_PER_FILE,
                    BYTE_ARRAY_ELEMENTS_TOTAL,
                    INT_ARRAY_ELEMENTS_PER_FILE,
                    INT_ARRAY_ELEMENTS_TOTAL,
                    LONG_ARRAY_ELEMENTS_PER_FILE,
                    LONG_ARRAY_ELEMENTS_TOTAL -> FailureStage.TYPED_ARRAY_LENGTH;
            case MODIFIED_UTF8_BYTES_PER_FILE,
                    MODIFIED_UTF8_BYTES_TOTAL -> FailureStage.MODIFIED_UTF_PREFIX;
            case ATTACHMENT_ADMISSIONS -> FailureStage.ATTACHMENT_ADMISSION_COUNTER;
            case RAW_ROOT_CLAIMS -> FailureStage.RAW_ROOT_CAPTURE;
        };
    }

    enum CaseKind {
        POSITIVE,
        COUNTER_MAX_PLUS_ONE,
        DATA_VERSION_MISSING,
        DATA_VERSION_WRONG_TYPE,
        DATA_VERSION_WRONG_VALUE
    }

    enum FailureStage {
        JOURNAL_READINESS("journal_readiness"),
        DIRECTORY_ENTRIES("directory_entries"),
        SOURCE_SELECTION("source_selection"),
        RELEVANT_RECORDS("relevant_records"),
        PER_FILE_COMPRESSED("per_file_compressed"),
        AGGREGATE_COMPRESSED("aggregate_compressed_checked_add"),
        GZIP_FRAMING("gzip_framing"),
        PER_FILE_DECOMPRESSED("per_file_decompressed"),
        AGGREGATE_DECOMPRESSED("aggregate_decompressed_checked_add"),
        COMPOUND_FIELD_CHECKPOINT("compound_field_checkpoint"),
        DEPTH_CONTAINER_SCALAR_KIND("depth_container_scalar_kind"),
        LIST_LENGTH("list_length"),
        TYPED_ARRAY_LENGTH("typed_array_length"),
        MODIFIED_UTF_PREFIX("modified_utf_prefix"),
        DATA_VERSION("data_version"),
        P4C_ADMISSION("p4c_admission"),
        ATTACHMENT_ADMISSION_COUNTER("attachment_admission_counter"),
        RAW_ROOT_CAPTURE("raw_root_capture"),
        STORE_REFERENCE_OWNER_AUDIT("store_reference_owner_audit");

        private final String slug;

        FailureStage(String slug) {
            this.slug = slug;
        }

        String slug() {
            return slug;
        }
    }

    enum FailureCode {
        COUNTER_CAPACITY_EXCEEDED,
        DATA_VERSION_MISSING,
        DATA_VERSION_WRONG_TYPE,
        DATA_VERSION_WRONG_VALUE
    }

    enum MutationKind {
        NONE,
        ADD_IRRELEVANT_DIRECTORY_ENTRY,
        ADD_SELECTED_PRIMARY_RECORD,
        ADD_GZIP_HEADER_BYTE_REBALANCE_TOTAL,
        ADD_DECOMPRESSED_PAYLOAD_BYTE_REBALANCE_TOTAL,
        ADD_CONTAINER_LEVEL,
        ADD_COMPOUND_CONTAINER_REBALANCE_TOTAL,
        ADD_COMPOUND_FIELD_REBALANCE_TOTAL,
        ADD_LIST_ELEMENT_REBALANCE_TOTAL,
        ADD_BYTE_ARRAY_ELEMENT_REBALANCE_TOTAL,
        ADD_INT_ARRAY_ELEMENT_REBALANCE_TOTAL,
        ADD_LONG_ARRAY_ELEMENT_REBALANCE_TOTAL,
        ADD_MODIFIED_UTF_BYTE_REBALANCE_TOTAL,
        ADD_SCALAR_TAG_REBALANCE_TOTAL,
        ADD_GZIP_HEADER_BYTE_TO_AGGREGATE,
        ADD_DECOMPRESSED_PAYLOAD_BYTE_TO_AGGREGATE,
        ADD_COMPOUND_CONTAINER_TO_AGGREGATE,
        ADD_COMPOUND_FIELD_TO_AGGREGATE,
        ADD_LIST_ELEMENT_TO_AGGREGATE,
        ADD_BYTE_ARRAY_ELEMENT_TO_AGGREGATE,
        ADD_INT_ARRAY_ELEMENT_TO_AGGREGATE,
        ADD_LONG_ARRAY_ELEMENT_TO_AGGREGATE,
        ADD_MODIFIED_UTF_BYTE_TO_AGGREGATE,
        ADD_SCALAR_TAG_TO_AGGREGATE,
        ADD_READY_ATTACHMENT_ADMISSION,
        ADD_EQUIPPED_RAW_ROOT_CLAIM,
        REMOVE_DATA_VERSION,
        REPLACE_DATA_VERSION_WITH_WRONG_TYPE,
        REPLACE_DATA_VERSION_WITH_WRONG_VALUE
    }

    record Failure(
            FailureCode code,
            FailureStage stage,
            Optional<P4E0R2QProfile.Counter> counter,
            long observedAtLeast,
            long maximum) {
        Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(stage, "stage");
            counter = Objects.requireNonNull(counter, "counter");
            var counterFailure = code == FailureCode.COUNTER_CAPACITY_EXCEEDED;
            if (counterFailure != counter.isPresent()
                    || (counterFailure && (observedAtLeast <= maximum || maximum < 0))
                    || (!counterFailure && (stage != FailureStage.DATA_VERSION
                            || observedAtLeast != 0L || maximum != 0L))) {
                throw new IllegalArgumentException("invalid R2Q bounded failure");
            }
        }
    }

    record CaseSpec(
            int index,
            String caseId,
            CaseKind kind,
            Optional<P4E0R2QProfile.Counter> targetCounter,
            long maximum,
            long observedAtLeast,
            MutationKind mutationKind,
            Set<P4E0R2QProfile.Counter> coupledCounters,
            Optional<Failure> expectedFailure,
            boolean allOtherCountersWithinLimit,
            int expectedDfuInvocations) {
        CaseSpec {
            if (index < 0 || caseId == null || caseId.length() > 112
                    || !caseId.matches("[a-z0-9][a-z0-9-]*")) {
                throw new IllegalArgumentException("invalid R2Q case identity");
            }
            Objects.requireNonNull(kind, "kind");
            targetCounter = Objects.requireNonNull(targetCounter, "targetCounter");
            Objects.requireNonNull(mutationKind, "mutationKind");
            coupledCounters = Set.copyOf(
                    Objects.requireNonNull(coupledCounters, "coupledCounters"));
            expectedFailure = Objects.requireNonNull(expectedFailure, "expectedFailure");
            if (maximum < 0 || observedAtLeast < 0 || expectedDfuInvocations != 0) {
                throw new IllegalArgumentException("invalid R2Q case bounds");
            }
        }
    }

    record NegativePreflight(
            P4E0R2QProfile.Counter targetCounter,
            long observedAtLeast,
            long maximum,
            FailureStage firstFailureStage,
            boolean allOtherCountersWithinLimit,
            P4E0R2QFixturePlan.PhysicalProofKind physicalProofKind) {
        NegativePreflight {
            Objects.requireNonNull(targetCounter, "targetCounter");
            Objects.requireNonNull(firstFailureStage, "firstFailureStage");
            Objects.requireNonNull(physicalProofKind, "physicalProofKind");
        }
    }

    record DataVersionPreflight(
            CaseKind caseKind,
            FailureCode failureCode,
            FailureStage firstFailureStage,
            P4E0R2QFixturePlan.DataVersionProofKind physicalProofKind,
            P4E0R2QFixturePlan.DataVersionTagState resultingState,
            int expectedDfuInvocations) {
        DataVersionPreflight {
            Objects.requireNonNull(caseKind, "caseKind");
            Objects.requireNonNull(failureCode, "failureCode");
            Objects.requireNonNull(firstFailureStage, "firstFailureStage");
            Objects.requireNonNull(physicalProofKind, "physicalProofKind");
            Objects.requireNonNull(resultingState, "resultingState");
            if (expectedDfuInvocations != 0) {
                throw new IllegalArgumentException("R2Q DataVersion control invoked DFU");
            }
        }
    }

    private static P4E0R2QCasePlan createStandard() {
        var profile = P4E0R2QProfile.locked();
        var cases = new ArrayList<CaseSpec>();
        cases.add(new CaseSpec(
                0,
                CASE_PREFIX + "exact",
                CaseKind.POSITIVE,
                Optional.empty(),
                0L,
                0L,
                MutationKind.NONE,
                Set.of(),
                Optional.empty(),
                true,
                0));
        for (var counter : P4E0R2QProfile.Counter.values()) {
            var maximum = profile.maximum(counter);
            var observed = Math.addExact(maximum, 1L);
            var recipe = P4E0R2QFixturePlan.recipeFor(counter);
            cases.add(new CaseSpec(
                    cases.size(),
                    CASE_PREFIX + "over-" + counter.slug().replace('_', '-'),
                    CaseKind.COUNTER_MAX_PLUS_ONE,
                    Optional.of(counter),
                    maximum,
                    observed,
                    recipe.mutationKind(),
                    recipe.coupledCounters(),
                    Optional.of(new Failure(
                            FailureCode.COUNTER_CAPACITY_EXCEEDED,
                            stageFor(counter),
                            Optional.of(counter),
                            observed,
                            maximum)),
                    true,
                    0));
        }
        addDataVersion(cases, CaseKind.DATA_VERSION_MISSING,
                "dataversion-missing", MutationKind.REMOVE_DATA_VERSION,
                FailureCode.DATA_VERSION_MISSING);
        addDataVersion(cases, CaseKind.DATA_VERSION_WRONG_TYPE,
                "dataversion-wrong-type",
                MutationKind.REPLACE_DATA_VERSION_WITH_WRONG_TYPE,
                FailureCode.DATA_VERSION_WRONG_TYPE);
        addDataVersion(cases, CaseKind.DATA_VERSION_WRONG_VALUE,
                "dataversion-wrong-value",
                MutationKind.REPLACE_DATA_VERSION_WITH_WRONG_VALUE,
                FailureCode.DATA_VERSION_WRONG_VALUE);
        return new P4E0R2QCasePlan(cases);
    }

    private static void addDataVersion(
            List<CaseSpec> cases,
            CaseKind kind,
            String suffix,
            MutationKind mutation,
            FailureCode failure) {
        cases.add(new CaseSpec(
                cases.size(),
                CASE_PREFIX + suffix,
                kind,
                Optional.empty(),
                0L,
                0L,
                mutation,
                Set.of(),
                Optional.of(new Failure(
                        failure,
                        FailureStage.DATA_VERSION,
                        Optional.empty(),
                        0L,
                        0L)),
                true,
                0));
        if (failure == FailureCode.COUNTER_CAPACITY_EXCEEDED) {
            throw new AssertionError("DataVersion control used a counter failure code");
        }
    }

    private static List<CaseSpec> validate(List<CaseSpec> input) {
        if (input == null || input.size() != CASE_COUNT) {
            throw new IllegalArgumentException("R2Q plan must contain exactly 29 cases");
        }
        var copy = new ArrayList<CaseSpec>(input.size());
        var identifiers = new HashSet<String>();
        var counters = EnumSet.noneOf(P4E0R2QProfile.Counter.class);
        var dataVersionControls = 0;
        for (var index = 0; index < input.size(); index++) {
            var spec = Objects.requireNonNull(input.get(index), "case");
            if (spec.index() != index || !identifiers.add(spec.caseId())
                    || spec.expectedDfuInvocations() != 0) {
                throw new IllegalArgumentException("R2Q plan order or identity changed");
            }
            if (spec.kind() == CaseKind.COUNTER_MAX_PLUS_ONE) {
                var target = spec.targetCounter().orElseThrow();
                var failure = spec.expectedFailure().orElseThrow();
                if (!counters.add(target)
                        || failure.counter().orElseThrow() != target
                        || failure.code() != FailureCode.COUNTER_CAPACITY_EXCEEDED) {
                    throw new IllegalArgumentException("duplicate R2Q counter negative");
                }
            } else if (spec.kind() != CaseKind.POSITIVE) {
                var failure = spec.expectedFailure().orElseThrow();
                if (failure.counter().isPresent()
                        || failure.stage() != FailureStage.DATA_VERSION
                        || failure.code() != expectedDataVersionFailure(spec.kind())) {
                    throw new IllegalArgumentException("invalid R2Q DataVersion control");
                }
                dataVersionControls++;
            } else if (spec.expectedFailure().isPresent()) {
                throw new IllegalArgumentException("R2Q positive has a failure");
            }
            copy.add(spec);
        }
        if (!counters.equals(EnumSet.allOf(P4E0R2QProfile.Counter.class))
                || dataVersionControls != 3
                || copy.stream().filter(spec -> spec.kind() == CaseKind.POSITIVE).count() != 1) {
            throw new IllegalArgumentException("R2Q plan coverage changed");
        }
        return List.copyOf(copy);
    }

    private static FailureCode expectedDataVersionFailure(CaseKind kind) {
        return switch (kind) {
            case DATA_VERSION_MISSING -> FailureCode.DATA_VERSION_MISSING;
            case DATA_VERSION_WRONG_TYPE -> FailureCode.DATA_VERSION_WRONG_TYPE;
            case DATA_VERSION_WRONG_VALUE -> FailureCode.DATA_VERSION_WRONG_VALUE;
            default -> throw new IllegalArgumentException("not a DataVersion control");
        };
    }

    private static String buildCanonicalJson(List<CaseSpec> cases) {
        var root = new JsonObject();
        root.addProperty("schema_version", SCHEMA_VERSION);
        root.addProperty("authority", P4E0R2QProfile.AUTHORITY);
        root.addProperty("profile_name", P4E0R2QProfile.PROFILE_NAME);
        var values = new JsonArray();
        for (var spec : cases) {
            var json = new JsonObject();
            json.addProperty("case_index", spec.index());
            json.addProperty("case_id", spec.caseId());
            json.addProperty("kind", spec.kind().name());
            json.addProperty("target_counter",
                    spec.targetCounter().map(P4E0R2QProfile.Counter::slug).orElse(""));
            json.addProperty("maximum", spec.maximum());
            json.addProperty("observed_at_least", spec.observedAtLeast());
            json.addProperty("mutation_kind", spec.mutationKind().name());
            var coupled = new JsonArray();
            spec.coupledCounters().stream()
                    .sorted(java.util.Comparator.comparingInt(Enum::ordinal))
                    .map(P4E0R2QProfile.Counter::slug)
                    .forEach(coupled::add);
            json.add("coupled_counters", coupled);
            json.addProperty("expected_first_failure",
                    spec.expectedFailure().map(failure -> failure.code().name()).orElse("NONE"));
            json.addProperty("expected_stage",
                    spec.expectedFailure().map(failure -> failure.stage().slug()).orElse("NONE"));
            json.addProperty(
                    "all_other_counters_within_limit",
                    spec.allOtherCountersWithinLimit());
            json.addProperty("expected_dfu_invocations", spec.expectedDfuInvocations());
            values.add(json);
        }
        root.add("cases", values);
        return root.toString();
    }

    private static final class Holder {
        private static final P4E0R2QCasePlan PLAN = createStandard();

        private Holder() {
        }
    }
}
