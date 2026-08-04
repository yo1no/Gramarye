package com.yo1no.gramarye.magic.definition.research;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Exact P4-E0-R1/R2/R2Q research-only source, resource, task, and schema allowlists. */
final class P4E0ResearchPhaseTypes {
    static final Set<String> RESEARCH_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/player/"
                    + "P4E0ResearchAttachmentFixtures.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchCase.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchCombinedEnvelope.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchCombinedProfileFile.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchFixtureFactory.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchFixtureManifest.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchHashing.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchMain.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchMatrixFixtures.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchMatrixPlan.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchMatrixRunner.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchNbtMetrics.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchParameters.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2PlanFactory.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2Main.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchReportAggregator.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchResult.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchRunRecord.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchScenario.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QAuditBudget.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QFixturePlan.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalEvidence.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalMain.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalResult.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalWorkload.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QJointRecords.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QModifiedUtf.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QPositiveWitnesses.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java",
            "com/yo1no/gramarye/magic/definition/research/P4E0R2QStudyIdentity.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchCombinedStoreSession.java",
            "com/yo1no/gramarye/magic/definition/store/P4E0ResearchGzipAdapter.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchRootWorkloads.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchStoreJournalFixtures.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0R2QStoreJournalFixtures.java");

    static final Set<String> GAME_TEST_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchCombinedCoordinator.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchDedicatedCoordinator.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2DedicatedDriver.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QFormalDedicatedDriver.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QDedicatedDriver.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchGameTestHolder.java");

    static final Set<String> RESOURCE_PATHS = Set.of(
            "p4-e0-research-smoke-v0.json",
            "p4-e0-r2q-profile-v0.json");

    static final Set<String> UNIT_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchConfigurationTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchFixtureTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchMetricsTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchPhaseTypes.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2MatrixTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QApiGateTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QAuditBudgetTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QExactGzipWitnessTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QFixtureTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QFormalContractTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QFormalEvidenceTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QFormalGateNegativeTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QFormalResultTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QModifiedUtfTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QJointRecordsTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QPositiveWitnessTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QProfileTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2QStudyIdentityTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QRootProjectionTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0R2QNegativeFixtureTest.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchReportAggregationTest.java");

    static final Set<String> PUBLIC_RESEARCH_TYPE_NAMES = Set.of(
            "com.yo1no.gramarye.magic.definition.player."
                    + "P4E0ResearchAttachmentFixtures",
            "com.yo1no.gramarye.magic.definition.research."
                    + "P4E0ResearchCombinedEnvelope",
            "com.yo1no.gramarye.magic.definition.research.P4E0ResearchMain",
            "com.yo1no.gramarye.magic.definition.research.P4E0ResearchR2Main",
            "com.yo1no.gramarye.magic.definition.research.P4E0R2QMain",
            "com.yo1no.gramarye.magic.definition.store."
                    + "P4E0ResearchCombinedStoreSession",
            "com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter",
            "com.yo1no.gramarye.magic.definition.store.P4E0ResearchRootWorkloads",
            "com.yo1no.gramarye.magic.definition.store."
                    + "P4E0ResearchStoreJournalFixtures",
            "com.yo1no.gramarye.magic.definition.store."
                    + "P4E0R2QStoreJournalFixtures");

    static final Set<String> CASE_NAMES = Set.of(
            "ZERO_ROOT_MINIMAL",
            "READY_ROOT_MAX",
            "PRESERVED_RAW_EXACT",
            "OVERSIZE_MARKER",
            "UNRELATED_WHOLE_NBT",
            "DEPTH_LADDER",
            "GZIP_HEADER_LADDER",
            "MIXED_DIRECTORY",
            "COMBINED_ENVELOPE");

    static final Set<String> SCENARIO_NAMES = Set.of(
            "CORRECTNESS_SMOKE",
            "PLAYERDATA_COORDINATE_SMOKE");

    static final Set<String> CLASSIFICATION_NAMES = Set.of(
            "COMPLETED",
            "REJECTED_BY_RESEARCH_GUARD",
            "FIXTURE_INVALID",
            "INSTRUMENTATION_FAILURE",
            "CHILD_EXIT_FAILURE",
            "TIMEOUT",
            "OOME_EXIT");

    static final Set<String> R2Q_QUALIFICATION_NAMES = Set.of(
            "ADMITTED_EXACT",
            "REJECTED_EXPECTED_COUNTER",
            "REJECTED_EXPECTED_DATA_VERSION",
            "NOT_OBSERVED");

    static final Set<String> R2Q_FORMAL_ARTIFACT_NAMES = Set.of(
            "runs.jsonl",
            "r2q-profile.json",
            "r2q-case-plan.json",
            "summary.md",
            "PROVENANCE.txt",
            "SHA256SUMS.txt");

    static final Set<String> R2Q_FORMAL_RESULT_TOP_LEVEL_KEYS = Set.of(
            "schema_version", "study_id", "case_id", "case_index", "git_head",
            "git_tree", "profile_hash", "case_plan_hash", "fixture_hash",
            "case_fixture_checksum", "run_order_hash", "implementation_schema_version",
            "process_classification", "qualification_result", "target_counter",
            "maximum", "observed_at_least", "expected_failure_code",
            "observed_failure_code", "expected_stage", "observed_stage",
            "all_other_counters_within_limit", "counter_values", "dfu_invocations",
            "attachment_admissions", "raw_root_claims", "targets_audited",
            "reclaim_invocations", "heap_xms", "heap_xmx", "initial_committed",
            "sampled_peak_used", "heap_pool_peak_sum", "elapsed_millis",
            "semantic_checksum", "exception_class");

    static final Set<String> R2_MATRIX_NAMES = Set.of(
            "A_DIRECTORY",
            "B_SINGLE_FILE",
            "C_NBT_COMPLEXITY",
            "D_AGGREGATE_AUDIT",
            "E_ROOT_CAPTURE",
            "F_COMBINED");

    static final Set<String> R2_REPORT_ARTIFACT_NAMES = Set.of(
            "runs.jsonl",
            "candidate-frontiers.csv",
            "summary.md",
            "fixture-manifest.json");

    static final Set<String> R2_RUN_TOP_LEVEL_KEYS = Set.of(
            "schema_version",
            "authority",
            "study_id",
            "plan_hash",
            "run_index",
            "run_id",
            "mode",
            "matrix",
            "frontier_kind",
            "axis",
            "shape",
            "profile",
            "heap_mib",
            "coordinate",
            "coordinate_unit",
            "timeout_seconds",
            "fixture_id",
            "parameters",
            "classification",
            "process_result",
            "elapsed_millis",
            "environment",
            "metrics",
            "fixture");

    static final List<Integer> R2_HEAP_GRID_MIB = List.of(
            1024, 1280, 1536, 1792, 2048);

    static final List<String> R2_COMBINED_TOKENS = List.of(
            "Balanced1024",
            "Balanced1280",
            "Balanced1536",
            "Balanced1792",
            "Balanced2048",
            "DirectoryHeavy1024",
            "DirectoryHeavy1280",
            "DirectoryHeavy1536",
            "DirectoryHeavy1792",
            "DirectoryHeavy2048",
            "SingleFileHeavy1024",
            "SingleFileHeavy1280",
            "SingleFileHeavy1536",
            "SingleFileHeavy1792",
            "SingleFileHeavy2048");

    private static final List<String> BASE_TASK_NAMES = List.of(
            "compileP4E0ResearchJava",
            "compileP4E0ResearchGameTestJava",
            "cleanP4E0ResearchLauncherLogs",
            "prepareP4E0ResearchSmoke",
            "runP4E0ResearchSmoke",
            "runP4E0ResearchDedicatedSmoke",
            "classifyP4E0ResearchDedicatedSmoke",
            "verifyP4E0ResearchSmokeOutput",
            "cleanP4E0ResearchPostRunLogs",
            "verifyP4E0ResearchConfiguration",
            "p4E0ResearchSmoke");

    private static final List<String> R2_AGGREGATE_TASK_NAMES = List.of(
            "prepareP4E0ResearchMatrixFixtures",
            "verifyP4E0ResearchMatrixFixtures",
            "p4E0ResearchMatrix",
            "p4E0ResearchCombined",
            "aggregateP4E0ResearchReports",
            "verifyP4E0ResearchReportSchema",
            "p4E0ResearchStudy");

    private static final List<String> R2Q_TASK_NAMES = List.of(
            "prepareP4E0R2Q",
            "verifyP4E0R2QPreflightTests",
            "verifyP4E0R2QProfile",
            "runP4E0R2QSmoke",
            "runP4E0R2QDedicatedSmoke",
            "verifyP4E0R2QSmokeOutput",
            "runP4E0R2QSupervisorSmoke",
            "runP4E0R2QRunnerDedicatedSmoke",
            "verifyP4E0R2QSupervisorSmoke",
            "verifyP4E0R2QConfiguration",
            "p4E0R2QSmoke",
            "prepareP4E0R2QFormalStudy",
            "aggregateP4E0R2QFormal",
            "verifyP4E0R2QFormalArtifacts",
            "p4E0R2QStudy");

    static final List<String> TASK_NAMES = Stream.of(
                    BASE_TASK_NAMES.stream(),
                    R2_COMBINED_TOKENS.stream()
                            .map(token -> "runP4E0ResearchCombined" + token),
                    R2_COMBINED_TOKENS.stream()
                            .map(token -> "classifyP4E0ResearchCombined" + token),
                    R2_AGGREGATE_TASK_NAMES.stream(),
                    R2Q_TASK_NAMES.stream())
            .flatMap(stream -> stream)
            .toList();

    static final Set<String> RESULT_TOP_LEVEL_KEYS = Set.of(
            "schema_version",
            "git_head",
            "scenario",
            "parameters",
            "fixture_manifest",
            "jvm",
            "os",
            "process_result",
            "elapsed_millis",
            "heap",
            "directory_metrics",
            "wire_metrics",
            "nbt_metrics",
            "attachment_metrics",
            "root_metrics",
            "store_journal_metrics",
            "integrity",
            "classification");

    static final Set<String> REQUIRED_METRIC_KEYS = Set.of(
            "physical_file_bytes",
            "gzip_header_bytes",
            "compressed_member_bytes",
            "decompressed_root_bytes",
            "root_framing_bytes",
            "max_container_depth",
            "compound_count",
            "compound_entry_count",
            "list_count",
            "list_element_count",
            "scalar_tag_count",
            "byte_array_elements",
            "int_array_elements",
            "long_array_elements",
            "string_count",
            "modified_utf8_bytes",
            "attachment_write_any_tag_bytes",
            "draft_count",
            "latest_count",
            "equipped_count",
            "projected_root_count",
            "directory_entries_observed",
            "canonical_primary_names",
            "canonical_old_names",
            "unique_uuid_records",
            "ignored_entries",
            "relevant_malformed_entries",
            "metadata_bytes_estimate",
            "compressed_bytes_total",
            "decompressed_bytes_total",
            "tag_count_total",
            "value_elements_total",
            "attachment_admission_count",
            "root_claims_raw",
            "distinct_root_references");

    private P4E0ResearchPhaseTypes() {
    }
}
