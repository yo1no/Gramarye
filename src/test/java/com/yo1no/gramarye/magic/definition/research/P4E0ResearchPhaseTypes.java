package com.yo1no.gramarye.magic.definition.research;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** Exact P4-E0-R1/R2 research-only source, resource, task, and schema allowlists. */
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
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchCombinedStoreSession.java",
            "com/yo1no/gramarye/magic/definition/store/P4E0ResearchGzipAdapter.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchRootWorkloads.java",
            "com/yo1no/gramarye/magic/definition/store/"
                    + "P4E0ResearchStoreJournalFixtures.java");

    static final Set<String> GAME_TEST_SOURCE_PATHS = Set.of(
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchCombinedCoordinator.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchDedicatedCoordinator.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchR2DedicatedDriver.java",
            "com/yo1no/gramarye/magic/definition/research/"
                    + "P4E0ResearchGameTestHolder.java");

    static final Set<String> RESOURCE_PATHS = Set.of(
            "p4-e0-research-smoke-v0.json");

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
                    + "P4E0ResearchReportAggregationTest.java");

    static final Set<String> PUBLIC_RESEARCH_TYPE_NAMES = Set.of(
            "com.yo1no.gramarye.magic.definition.player."
                    + "P4E0ResearchAttachmentFixtures",
            "com.yo1no.gramarye.magic.definition.research."
                    + "P4E0ResearchCombinedEnvelope",
            "com.yo1no.gramarye.magic.definition.research.P4E0ResearchMain",
            "com.yo1no.gramarye.magic.definition.research.P4E0ResearchR2Main",
            "com.yo1no.gramarye.magic.definition.store."
                    + "P4E0ResearchCombinedStoreSession",
            "com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter",
            "com.yo1no.gramarye.magic.definition.store.P4E0ResearchRootWorkloads",
            "com.yo1no.gramarye.magic.definition.store."
                    + "P4E0ResearchStoreJournalFixtures");

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

    static final List<String> TASK_NAMES = Stream.of(
                    BASE_TASK_NAMES.stream(),
                    R2_COMBINED_TOKENS.stream()
                            .map(token -> "runP4E0ResearchCombined" + token),
                    R2_COMBINED_TOKENS.stream()
                            .map(token -> "classifyP4E0ResearchCombined" + token),
                    R2_AGGREGATE_TASK_NAMES.stream())
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
