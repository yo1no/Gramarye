#!/usr/bin/env bash
set -euo pipefail

# R1 must remain portable and independent of developer-only search tools and aliases.
PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find git jar mktemp rm dirname pwd sed; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-E0-R1 verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-E0-R1 verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

RESEARCH_SOURCE_LIST=''
GAME_SOURCE_LIST=''
TEST_SOURCE_LIST=''
RESOURCE_LIST=''
PRODUCTION_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
HELPER_FIXTURE=''
RUNTIME_BLOCK=''
HEAP_BLOCK=''
R2_BLOCK=''

cleanup() {
    local temporary=''
    for temporary in \
        "${RESEARCH_SOURCE_LIST}" \
        "${GAME_SOURCE_LIST}" \
        "${TEST_SOURCE_LIST}" \
        "${RESOURCE_LIST}" \
        "${PRODUCTION_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${HELPER_FIXTURE}" \
        "${RUNTIME_BLOCK}" \
        "${HEAP_BLOCK}" \
        "${R2_BLOCK}"; do
        if [[ -n "${temporary}" ]]; then
            rm -f -- "${temporary}"
        fi
    done
}
trap cleanup EXIT HUP INT TERM

grep_failed() {
    local file="$1"
    local status="$2"
    fail "grep failed while checking ${file} (exit ${status})"
}

require_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0) return 0 ;;
        1) fail "${message}" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

forbid_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

require_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0) return 0 ;;
        1) fail "${message}" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

forbid_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

require_fixed_count() {
    local file="$1"
    local needle="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

require_ere_count() {
    local file="$1"
    local pattern="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Ec -- "${pattern}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

require_regular_file() {
    local file="$1"
    local message="$2"
    [[ -f "${file}" && ! -L "${file}" ]] || fail "${message}"
}

collect_java_files() {
    local root="$1"
    local destination="$2"
    local status=0
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" \
        || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-E0-R1 verifier could not inspect Java sources under ${root}"
    fi
}

forbid_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file=''
    while IFS= read -r -d '' file; do
        forbid_fixed "${file}" "${needle}" "${message} (${file})"
    done < "${file_list}"
}

forbid_ere_in_file_list() {
    local file_list="$1"
    local pattern="$2"
    local message="$3"
    local file=''
    while IFS= read -r -d '' file; do
        forbid_ere "${file}" "${pattern}" "${message} (${file})"
    done < "${file_list}"
}

has_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local file=''
    local status=0
    while IFS= read -r -d '' file; do
        status=0
        LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
        case "${status}" in
            0) return 0 ;;
            1) ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
    done < "${file_list}"
    return 1
}

require_any_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    has_fixed_in_file_list "${file_list}" "${needle}" || fail "${message}"
}

verify_search_helpers() {
    local missing_output=''
    local forbidden_output=''
    local count_output=''
    local tool_error_output=''
    local status=0
    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-helper.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create helper fixture'
    printf '%s\n' 'present contract' > "${HELPER_FIXTURE}"
    require_fixed "${HELPER_FIXTURE}" 'present contract' \
        'P4-E0-R1 verifier self-check lost fixed required matching'
    forbid_fixed "${HELPER_FIXTURE}" 'absent contract' \
        'P4-E0-R1 verifier self-check misclassified an absent pattern'

    missing_output="$({ require_fixed "${HELPER_FIXTURE}" \
        'missing contract' 'EXPECTED_MISSING'; } 2>&1)" || status=$?
    [[ "${status}" -eq 1 && "${missing_output}" == 'EXPECTED_MISSING' ]] \
        || fail 'P4-E0-R1 verifier could not distinguish a missing contract'
    status=0
    forbidden_output="$({ forbid_fixed "${HELPER_FIXTURE}" \
        'present contract' 'EXPECTED_FORBIDDEN'; } 2>&1)" || status=$?
    [[ "${status}" -eq 1 && "${forbidden_output}" == 'EXPECTED_FORBIDDEN' ]] \
        || fail 'P4-E0-R1 verifier could not distinguish a forbidden contract'
    status=0
    count_output="$({ require_fixed_count "${HELPER_FIXTURE}" \
        'present contract' 2 'EXPECTED_COUNT'; } 2>&1)" || status=$?
    [[ "${status}" -eq 1 && "${count_output}" == EXPECTED_COUNT* ]] \
        || fail 'P4-E0-R1 verifier could not distinguish an exact-count mismatch'
    status=0
    tool_error_output="$({ require_fixed "${HELPER_FIXTURE}.missing" \
        'present contract' 'WRONG_MISSING'; } 2>&1)" || status=$?
    if [[ "${status}" -ne 1 \
            || "${tool_error_output}" != *'grep failed while checking '* \
            || "${tool_error_output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-E0-R1 verifier could not distinguish a grep error from a missing contract'
    fi
}

is_reviewed_research_path() {
    case "$1" in
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/player/P4E0ResearchAttachmentFixtures.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchCase.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchCombinedEnvelope.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchCombinedProfileFile.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchFixtureFactory.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchFixtureManifest.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchHashing.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMain.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMatrixFixtures.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMatrixPlan.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMatrixRunner.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchNbtMetrics.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchParameters.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2Main.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2PlanFactory.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchReportAggregator.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchResult.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchRunRecord.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchScenario.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QAuditBudget.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFixturePlan.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalEvidence.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalMain.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalResult.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalWorkload.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QJointRecords.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QModifiedUtf.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QPositiveWitnesses.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QStudyIdentity.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchCombinedStoreSession.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchGzipAdapter.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchRootWorkloads.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchStoreJournalFixtures.java | \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0R2QStoreJournalFixtures.java) return 0 ;;
        *) return 1 ;;
    esac
}

is_reviewed_game_path() {
    case "$1" in
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchCombinedCoordinator.java | \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java | \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2DedicatedDriver.java | \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalDedicatedDriver.java | \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QDedicatedDriver.java | \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchGameTestHolder.java) return 0 ;;
        *) return 1 ;;
    esac
}

is_reviewed_test_path() {
    case "$1" in
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchConfigurationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchFixtureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMetricsTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2MatrixTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QAuditBudgetTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QExactGzipWitnessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFixtureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalContractTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalEvidenceTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalGateNegativeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalResultTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QJointRecordsTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QModifiedUtfTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QPositiveWitnessTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QProfileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QStudyIdentityTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QNegativeFixtureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QRootProjectionTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchReportAggregationTest.java) return 0 ;;
        *) return 1 ;;
    esac
}

is_reviewed_e1a_changed_path() {
    case "$1" in
        docs/architecture/P4-0-persistence-boundary.md | \
        docs/architecture/P4-E0-root-audit-boundary.md | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-d1-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentAdmission.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSerializer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditCounter.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditStage.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompressedCapacityRejected.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FileMetadata.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FileSystemAccess.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorStatus.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversal.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataDirectorySnapshot.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataFileReader.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataNbtScanner.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelector.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceAdmissionPreflight.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceFailure.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GlobalSourceCapture.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PendingJournalObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RootSourceFamily.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceInventory.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInput.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java | \
        src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentAdmissionTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentServiceTestSupport.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P3D3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4A2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4A3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1A1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1A1VisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B1CoreTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudgetTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorChildMatrixTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1HeapFloorProbeMain.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1IntegratedSnapshotTraversalTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataDirectorySnapshotTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataFileReaderTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataNbtScannerTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1PlayerDataSourceSelectorTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1RootAuditBridgeTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceAdmissionPreflightTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1TestBudgets.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInputTest.java) return 0 ;;
        *) return 1 ;;
    esac
}

is_reviewed_changed_path() {
    case "$1" in
        build.gradle | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        src/p4E0Research/resources/p4-e0-research-smoke-v0.json | \
        src/p4E0Research/resources/p4-e0-r2q-profile-v0.json) return 0 ;;
    esac
    is_reviewed_research_path "$1" \
        || is_reviewed_game_path "$1" \
        || is_reviewed_test_path "$1" \
        || is_reviewed_e1a_changed_path "$1"
}

verify_changed_path_allowlist() {
    local paths=''
    local status=0
    local path=''
    paths="$(git diff --name-only HEAD)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking tracked R1 paths'
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_changed_path "${path}" \
            || fail "P4-E0-R1 modified a prohibited tracked path: ${path}"
    done <<< "${paths}"

    status=0
    paths="$(git ls-files --others --exclude-standard)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking untracked R1 paths'
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_changed_path "${path}" \
            || fail "P4-E0-R1 added a prohibited untracked path: ${path}"
    done <<< "${paths}"
}

verify_prohibited_paths_unchanged() {
    local untracked=''
    local status=0
    git diff --quiet HEAD -- \
        src/main/resources \
        docs/codex-spec \
        .github/workflows gradle.properties \
        || fail 'P4-E0-R1 modified production, authority, workflow, or version truth'
    untracked="$(git ls-files --others --exclude-standard -- \
        src/main/resources docs/codex-spec .github/workflows)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking prohibited untracked paths'
    [[ -z "${untracked}" ]] \
        || fail "P4-E0-R1 added a prohibited untracked path: ${untracked}"
}

verify_exact_source_allowlists() {
    local file=''
    local relative=''
    local count=0
    while IFS= read -r -d '' file; do
        relative="${file#./}"
        is_reviewed_research_path "${relative}" \
            || fail "unreviewed P4-E0-R1 research source: ${relative}"
        count=$((count + 1))
    done < "${RESEARCH_SOURCE_LIST}"
    [[ "${count}" -eq 38 ]] \
        || fail "P4-E0-R2Q research source count must be thirty-eight (found ${count})"

    count=0
    while IFS= read -r -d '' file; do
        relative="${file#./}"
        is_reviewed_game_path "${relative}" \
            || fail "unreviewed P4-E0-R1 dedicated source: ${relative}"
        count=$((count + 1))
    done < "${GAME_SOURCE_LIST}"
    [[ "${count}" -eq 6 ]] \
        || fail "P4-E0-R2Q dedicated source count must be six (found ${count})"

    count=0
    while IFS= read -r -d '' file; do
        relative="${file#./}"
        is_reviewed_test_path "${relative}" \
            || fail "unreviewed P4-E0-R1 unit/configuration source: ${relative}"
        count=$((count + 1))
    done < "${TEST_SOURCE_LIST}"
    [[ "${count}" -eq 21 ]] \
        || fail "P4-E0-R2Q unit/configuration source count must be twenty-one (found ${count})"

    count=0
    while IFS= read -r -d '' file; do
        relative="${file#./}"
        case "${relative}" in
            src/p4E0Research/resources/p4-e0-research-smoke-v0.json | \
            src/p4E0Research/resources/p4-e0-r2q-profile-v0.json) ;;
            *) fail "unreviewed P4-E0 research resource: ${relative}" ;;
        esac
        count=$((count + 1))
    done < "${RESOURCE_LIST}"
    [[ "${count}" -eq 2 ]] \
        || fail "P4-E0-R2Q source resource count must be two (found ${count})"
}

verify_build_contract() {
    local marker=''
    require_ere_count build.gradle \
        "sourceSets\\.create\\('p4E0Research[A-Za-z0-9_]*'\\)" 2 \
        'P4-E0-R1 must declare exactly two isolated source sets'
    for marker in \
        "sourceSets.create('p4E0Research')" \
        "sourceSets.create('p4E0ResearchGameTest')" \
        "tasks.register('generateP4E0ResearchGameTestResources', Sync)" \
        'data/gramarye_p4_e0_research/structure/p4_e0_research_smoke.nbt' \
        "'p4-e0-research/generated-gametest-resources'" \
        'addModdingDependenciesTo(p4E0ResearchSourceSet)' \
        'addModdingDependenciesTo(p4E0ResearchGameTestSourceSet)' \
        'p4E0ResearchHarness' \
        'sourceSet(p4E0ResearchSourceSet)' \
        'sourceSet(p4E0ResearchGameTestSourceSet)' \
        'add(p4E0ResearchSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4E0ResearchSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4E0ResearchSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4E0ResearchSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4E0ResearchSourceSet.implementationConfigurationName, p4D3ProbeSourceSet.output)' \
        'add(p4E0ResearchSourceSet.implementationConfigurationName, commonsCompressCoordinate)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4D3ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4E0ResearchSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, commonsCompressCoordinate)' \
        'testImplementation p4E0ResearchSourceSet.output' \
        "'cleanP4E0ResearchLauncherLogs', Delete" \
        "'cleanP4E0ResearchPostRunLogs', Delete" \
        "'verifyP4E0ResearchConfiguration', Exec" \
        "'classifyP4E0ResearchDedicatedSmoke'" \
        "tasks.register('p4E0ResearchSmoke')"; do
        require_fixed build.gradle "${marker}" \
            "P4-E0-R1 build contract is missing: ${marker}"
    done

    HEAP_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-heap.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create heap block'
    sed -n '/^def p4E0ResearchFixedHeapJvmArgs = \[$/,/^\]$/p' \
        build.gradle > "${HEAP_BLOCK}"
    require_fixed build.gradle "def p4E0ResearchDefaultHeapMiB = '1024'" \
        'P4-E0-R1 default heap coordinate changed'
    require_fixed build.gradle \
        ".systemProperty('gramarye.p4e0.research.heapMiB')" \
        'P4-E0-R1 heap override is not a Gradle system-property input'
    require_fixed "${HEAP_BLOCK}" '"-Xmx${p4E0ResearchHeapMiB}m"' \
        'P4-E0-R1 dedicated/standalone heap does not use the dynamic coordinate'
    forbid_fixed "${HEAP_BLOCK}" "'-Xmx1024m'" \
        'P4-E0-R1 research heap was frozen instead of using the override'

    for marker in \
        'def p4E0ResearchChildTimeoutSeconds = 540' \
        'def p4E0ResearchChildTerminationBudgetSeconds = 30' \
        'def p4E0ResearchSupervisorTimeoutSeconds = 600' \
        '<= p4E0ResearchChildTimeoutSeconds + p4E0ResearchChildTerminationBudgetSeconds' \
        'P4-E0 research supervisor timeout is not fail-closed'; do
        require_fixed build.gradle "${marker}" \
            "P4-E0-R1 supervisor timeout gate is missing ${marker}"
    done
    require_fixed_count build.gradle 'p4E0ResearchSupervisorTimeoutSeconds' 6 \
        'P4-E0-R1 supervisor timeout must own all three supervised commands'
    if ((600 < 600 || 600 <= 540 + 30)); then
        fail 'P4-E0-R1 supervisor timeout is not above the child termination budget'
    fi
    forbid_fixed build.gradle 'testImplementation p4E0ResearchGameTestSourceSet.output' \
        'P4-E0-R1 dedicated output must not enter the JUnit classpath'
    forbid_fixed build.gradle 'runtimeElements.extendsFrom p4E0' \
        'P4-E0-R1 output must not enter publication variants'

    RUNTIME_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-runtime.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create runtime block'
    sed -n '/^        p4E0ResearchHarness {$/,/^        }$/p' \
        build.gradle > "${RUNTIME_BLOCK}"
    require_fixed_count "${RUNTIME_BLOCK}" '        p4E0ResearchHarness {' 1 \
        'P4-E0-R1 dedicated mod group must appear exactly once'
    require_ere_count "${RUNTIME_BLOCK}" '^[[:space:]]+sourceSet\(' 7 \
        'P4-E0-R1 dedicated mod must contain exactly seven approved source sets'
    for marker in \
        'sourceSet(sourceSets.main)' \
        'sourceSet(p4A3ProbeSourceSet)' \
        'sourceSet(p4B2ProbeSourceSet)' \
        'sourceSet(p4C2ProbeSourceSet)' \
        'sourceSet(p4D3ProbeSourceSet)' \
        'sourceSet(p4E0ResearchSourceSet)' \
        'sourceSet(p4E0ResearchGameTestSourceSet)'; do
        require_fixed_count "${RUNTIME_BLOCK}" "${marker}" 1 \
            "P4-E0-R1 dedicated mod is missing or duplicates ${marker}"
    done
    for marker in \
        'sourceSets.test' \
        'p4A3GameTestSourceSet' \
        'p4B2GameTestSourceSet' \
        'p4C2GameTestSourceSet' \
        'p4D3GameTestSourceSet'; do
        forbid_fixed "${RUNTIME_BLOCK}" "${marker}" \
            "P4-E0-R1 dedicated mod includes forbidden output ${marker}"
    done

    for marker in \
        "'-Xms512m'" \
        "'-XX:+ExitOnOutOfMemoryError'" \
        "tasks.named(p4E0ResearchSourceSet.compileJavaTaskName, JavaCompile)" \
        "tasks.named(p4E0ResearchGameTestSourceSet.compileJavaTaskName, JavaCompile)" \
        "options.compilerArgs.addAll(['-Xlint:rawtypes', '-Xlint:unchecked'])" \
        'p4E0ResearchOverrideProperties' \
        'providers.systemProperty(propertyName)' \
        "delete(layout.projectDirectory.dir('logs'))" \
        'dependsOn(cleanP4E0ResearchLauncherLogs)' \
        'finalizedBy(cleanP4E0ResearchPostRunLogs)' \
        'mustRunAfter(classifyP4E0ResearchDedicatedSmoke)' \
        "layout.buildDirectory.dir('p4-e0-research/smoke')" \
        "layout.buildDirectory.dir('p4-e0-research/command-work')" \
        "layout.buildDirectory.dir('reports/p4-e0-research')" \
        'workingDir(p4E0ResearchCommandWorkingDirectory)' \
        'p4E0ResearchCommandWorkingDirectory.get().asFile.mkdirs()' \
        "tasks.named('runP4E0ResearchDedicatedSmoke', JavaExec)" \
        'dependsOn(verifyP4E0ResearchConfiguration)' \
        'dependsOn(prepareP4E0ResearchSmoke)' \
        'dependsOn(runP4E0ResearchSmoke)' \
        'dependsOn(runP4E0ResearchDedicatedSmoke)' \
        'dependsOn(classifyP4E0ResearchDedicatedSmoke)' \
        'ignoreExitValue = true' \
        'executionResult.get().exitValue' \
        'finalizedBy(classifyP4E0ResearchDedicatedSmoke)' \
        'dedicated-child.json' \
        'dedicated-exit-code.txt' \
        'dedicated-running-v0.txt' \
        'p4E0ResearchDedicatedRunningMarker,' \
        "systemProperty 'gramarye.p4e0.research.runMode', 'dedicated-smoke'" \
        "'gramarye.p4e0.research.scenario'," \
        'dependsOn(verifyP4E0ResearchSmokeOutput)'; do
        require_fixed build.gradle "${marker}" \
            "P4-E0-R1 task/heap/output contract is missing: ${marker}"
    done
    require_fixed_count build.gradle \
        "delete(layout.projectDirectory.dir('logs'))" 3 \
        'P4-E0 research must clean transient root launcher logs at all three reviewed lifecycle points'
    forbid_fixed build.gradle "'gramarye.p4e0.research.scenarioCase'," \
        'P4-E0-R1 retained the obsolete scenario override key'
    require_fixed_count build.gradle \
        "tasks.named(p4E0ResearchSourceSet.compileJavaTaskName, JavaCompile)" 1 \
        'P4-E0-R1 research compile lint owner changed'
    require_fixed_count build.gradle \
        "tasks.named(p4E0ResearchGameTestSourceSet.compileJavaTaskName, JavaCompile)" 1 \
        'P4-E0-R1 dedicated compile lint owner changed'

    for marker in \
        "name == 'p4E0ResearchDedicatedSmoke'" \
        "name == 'p4E0R2QDedicatedSmoke'" \
        '? p4E0ResearchMod : productionMod'; do
        require_fixed scripts/verify-p4-b2-b-configuration.sh "${marker}" \
            "P4-E0-R1 exact B2 runtime allowlist is missing ${marker}"
    done
    forbid_fixed build.gradle "name.startsWith('p4E0R2QCase')" \
        'P4-E0-R2Q formal cases must use exact generated loaded-mod membership'
    forbid_fixed build.gradle "name.startsWith('p4E0R2Q')" \
        'P4-E0-R2Q phase must not gain a broad loaded-mod prefix allowlist'
}

verify_r2_build_contract() {
    local marker=''
    local profile=''
    local heap=''
    local run_name=''
    local run_count=0

    require_fixed build.gradle \
        'def p4E0ResearchR2HeapGridMiB = [1024, 1280, 1536, 1792, 2048]' \
        'P4-E0-R2 fixed heap grid changed'
    require_fixed build.gradle \
        'def p4E0ResearchR2FirstCombinedRunIndex = 360' \
        'P4-E0-R2 combined run index origin changed'
    require_fixed_count build.gradle \
        "def researchRunIndex = coordinate['researchIndex'] as int" 2 \
        'P4-E0-R2 must bind both loops through exact coordinate records'
    require_fixed_count build.gradle \
        'p4E0ResearchR2CombinedCoordinates.each { coordinate ->' 2 \
        'P4-E0-R2 run and task loops must share the exact coordinate records'
    require_fixed_count build.gradle '[researchIndex:' 15 \
        'P4-E0-R2 exact coordinate list must contain fifteen records'
    require_fixed build.gradle \
        'def p4E0ResearchR2ExpectedCombinedCoordinateSignatures = [' \
        'P4-E0-R2 exact coordinate signature allowlist is missing'
    require_fixed build.gradle \
        "} != p4E0ResearchR2ExpectedCombinedCoordinateSignatures) {" \
        'P4-E0-R2 exact coordinate signature assertion is missing'
    require_fixed_count build.gradle 'researchRunIndex.toString()' 2 \
        'P4-E0-R2 run and classifier coordinates are not independently bound'
    forbid_fixed build.gradle \
        'def runIndex = p4E0ResearchR2FirstCombinedRunIndex' \
        'P4-E0-R2 run index is shadowed by the NeoForge runs DSL delegate'
    forbid_fixed build.gradle 'runIndex.toString()' \
        'P4-E0-R2 uses the shadow-prone run index expression'
    forbid_fixed build.gradle 'p4E0ResearchR2Profiles.eachWithIndex' \
        'P4-E0-R2 run indices must not depend on Groovy closure index binding'
    require_fixed build.gradle \
        'def prepareRunTaskName = "prepareP4E0ResearchCombined${token}Run"' \
        'P4-E0-R2 prepare-task name binding is missing'
    require_fixed build.gradle \
        'tasks.named(prepareRunTaskName).configure {' \
        'P4-E0-R2 does not configure exact ModDev prepare tasks'
    require_fixed build.gradle \
        $'tasks.named(prepareRunTaskName).configure {\n            outputs.upToDateWhen { false }\n        }' \
        'P4-E0-R2 launcher metadata must regenerate before every dedicated run'
    require_fixed build.gradle \
        ".gradleProperty('p4E0ResearchDiskBudgetBytes')" \
        'P4-E0-R2 disk budget is not the required Gradle property'
    forbid_fixed build.gradle \
        ".systemProperty('gramarye.p4e0.research.diskBudgetBytes')" \
        'P4-E0-R2 disk budget must use -P, not a JVM system property'
    require_fixed build.gradle ".getOrElse('12884901888')" \
        'P4-E0-R2 default disk budget changed'
    require_fixed build.gradle "commandLine('git', 'rev-parse', 'HEAD')" \
        'P4-E0-R2 does not bind reports to the current Git commit'

    run_count=360
    for marker in \
        BALANCED:Balanced \
        DIRECTORY_HEAVY:DirectoryHeavy \
        SINGLE_FILE_HEAVY:SingleFileHeavy; do
        profile="${marker%%:*}"
        run_name="${marker#*:}"
        for heap in 1024 1280 1536 1792 2048; do
            require_fixed build.gradle \
                "'${run_count}:${profile}:${run_name}:${heap}'" \
                'P4-E0-R2 exact index/profile/heap mapping changed'
            run_count=$((run_count + 1))
        done
    done
    [[ "${run_count}" -eq 375 ]] \
        || fail 'P4-E0-R2 verifier did not enumerate indices 360 through 374'

    for profile in BALANCED DIRECTORY_HEAVY SINGLE_FILE_HEAVY; do
        require_fixed build.gradle "[id: '${profile}'" \
            "P4-E0-R2 is missing dedicated profile ${profile}"
    done
    run_count=0
    for heap in 1024 1280 1536 1792 2048; do
        for profile in Balanced DirectoryHeavy SingleFileHeavy; do
            run_name="p4E0ResearchCombined${profile}${heap}"
            require_fixed build.gradle "'${run_name}'" \
                "P4-E0-R2 is missing exact dedicated run ${run_name}"
            run_count=$((run_count + 1))
        done
    done
    [[ "${run_count}" -eq 15 ]] \
        || fail 'P4-E0-R2 verifier did not enumerate exactly fifteen runs'
    [[ "$((7 + run_count * 2))" -eq 37 ]] \
        || fail 'P4-E0-R2 task graph must contain exactly thirty-seven R2 tasks'
    require_ere_count build.gradle \
        "^[[:space:]]*'p4E0ResearchCombined(Balanced|DirectoryHeavy|SingleFileHeavy)(1024|1280|1536|1792|2048)',?$" \
        15 'P4-E0-R2 exact dedicated run allowlist must contain fifteen names'

    for marker in \
        "systemProperty 'gramarye.p4e0.research.runMode', 'r2-combined'" \
        "systemProperty 'gramarye.p4e0.research.runIndex'" \
        "systemProperty 'gramarye.p4e0.research.heapMiB'" \
        "systemProperty 'gramarye.p4e0.research.profile'" \
        "systemProperty 'gramarye.p4e0.research.diskBudgetBytes'" \
        "systemProperty 'gramarye.p4e0.gitHead'" \
        "systemProperty 'gramarye.p4e0.research.childReport'" \
        "systemProperty 'gramarye.p4e0.research.runningMarker'" \
        "systemProperty 'gramarye.p4e0.research.exitFile'" \
        "systemProperty 'gramarye.p4e0.research.watchdogSeconds', '870'" \
        "'-Xms512m'" \
        '"-Xmx${heapMiB}m"' \
        "'-XX:+ExitOnOutOfMemoryError'" \
        "name.startsWith('p4E0ResearchCombined')" \
        'P4-E0-R2 dedicated run coordinate drift'; do
        require_fixed build.gradle "${marker}" \
            "P4-E0-R2 dedicated configuration is missing ${marker}"
    done

    R2_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2-block.XXXXXX")" \
        || fail 'P4-E0-R2 verifier could not create task block'
    sed -n \
        '/^def p4E0ResearchR2FixtureDirectory =$/,/^tasks.named('\''test'\'', Test).configure/p' \
        build.gradle > "${R2_BLOCK}"
    [[ -s "${R2_BLOCK}" ]] || fail 'P4-E0-R2 task block is missing'

    for marker in \
        "layout.buildDirectory.dir('p4-e0-research')" \
        'P4E0ResearchR2Main' \
        'def p4E0ResearchR2CombinedTimeoutSeconds = 900' \
        'def p4E0ResearchR2WatchdogSeconds = 870' \
        "layout.buildDirectory.dir('p4-e0-research/r2/combined-worlds')" \
        'target.parent != root' \
        'java.nio.file.LinkOption.NOFOLLOW_LINKS' \
        'java.nio.file.Files.walk(target)' \
        'java.util.Comparator.reverseOrder()' \
        'P4-E0-R2 refused a non-isolated combined-world deletion' \
        'combinedWorldDirectory.get().asFile, true' \
        'combinedWorldDirectory.get().asFile, false' \
        "'prepareP4E0ResearchMatrixFixtures'" \
        "'verifyP4E0ResearchMatrixFixtures'" \
        "'p4E0ResearchMatrix'" \
        "'p4E0ResearchCombined'" \
        "'aggregateP4E0ResearchReports'" \
        "'verifyP4E0ResearchReportSchema'" \
        "tasks.register('p4E0ResearchStudy')" \
        "'prepare-plan'" \
        "'verify-fixtures'" \
        "'matrix'" \
        "'classify-combined'" \
        "'aggregate'" \
        "'validate'" \
        'dependsOn(verifyP4E0ResearchConfiguration)' \
        'dependsOn(prepareP4E0ResearchMatrixFixtures)' \
        'dependsOn(verifyP4E0ResearchMatrixFixtures)' \
        'dependsOn(prerequisiteTask)' \
        'dependsOn(runTask)' \
        'dependsOn(previousP4E0ResearchR2Task)' \
        'dependsOn(p4E0ResearchCombined)' \
        'dependsOn(aggregateP4E0ResearchReports)' \
        'dependsOn(verifyP4E0ResearchReportSchema)' \
        'ignoreExitValue = true' \
        'executionResult.get().exitValue' \
        'outputs.upToDateWhen { false }' \
        'combined-child/${evidenceStem}.json' \
        '${researchRunIndex}.exit-code.txt' \
        'combined-running/${evidenceStem}.marker'; do
        require_fixed "${R2_BLOCK}" "${marker}" \
            "P4-E0-R2 hard-serial task contract is missing ${marker}"
    done
    forbid_fixed "${R2_BLOCK}" 'mustRunAfter' \
        'P4-E0-R2 must use hard dependsOn edges, not ordering hints'
    forbid_fixed "${R2_BLOCK}" 'dependsOn(runTask, ' \
        'P4-E0-R2 classifier introduced sibling dependencies'
    require_fixed_count "${R2_BLOCK}" \
        'combinedWorldDirectory.get().asFile, true' 1 \
        'P4-E0-R2 must reset each exact world once before its run'
    require_fixed_count "${R2_BLOCK}" \
        'combinedWorldDirectory.get().asFile, false' 1 \
        'P4-E0-R2 must delete each exact world once after successful classification'
    forbid_fixed .github/workflows/build.yml 'p4-e0-research' \
        'P4-E0-R2 must remain outside CI'
}

verify_fixture_and_metric_contract() {
    local marker=''
    local factory='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchFixtureFactory.java'
    local gzip='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchGzipAdapter.java'
    local main='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMain.java'
    local result='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchResult.java'
    for marker in \
        ZERO_ROOT_MINIMAL READY_ROOT_MAX PRESERVED_RAW_EXACT OVERSIZE_MARKER \
        UNRELATED_WHOLE_NBT DEPTH_LADDER GZIP_HEADER_LADDER MIXED_DIRECTORY \
        COMBINED_ENVELOPE \
        3_955 16_777_216 65_536 65_537 'SHA-256'; do
        require_any_fixed_in_file_list "${RESEARCH_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 fixture sources are missing marker ${marker}"
    done
    for marker in \
        'selection-primary-over-arbitrary-old' \
        'PRIMARY_PATH_SELECTED_ZERO_ROOT' \
        'selection-old-with-primary-missing' \
        'OLD_PATH_SELECTED_ZERO_ROOT' \
        'selection-paired-arbitrary-old-not-selected' \
        'MALFORMED_OLD_PRESENT_DISTINCT_NOT_SELECTED' \
        'zeroDirectoryShape.directoryEntriesObserved() != 16L' \
        'zeroDirectoryShape.canonicalPrimaryNames() != 16L' \
        'zeroCount != 16 || zeroProjectedRoots != 0L'; do
        require_fixed "${factory}" "${marker}" \
            "P4-E0-R1 filesystem/selection evidence is missing ${marker}"
    done
    for marker in \
        FileChannel BoundedChannelInputStream GzipHeaderVerifier BufferedInputStream \
        GzipCompressorInputStream 'NbtAccounter.create('; do
        require_fixed "${gzip}" "${marker}" \
            "P4-E0-R1 strict gzip adapter is missing ${marker}"
    done
    for marker in \
        'java.util.zip.GZIPInputStream' \
        'Files.readAllBytes' \
        'NbtAccounter.unlimitedHeap' \
        '.available(' \
        'getCompressedCount(' \
        'concatenated=true' \
        'concatenated = true'; do
        forbid_fixed "${gzip}" "${marker}" \
            "P4-E0-R1 strict gzip adapter contains forbidden ${marker}"
    done

    for marker in \
        physical_file_bytes gzip_header_bytes compressed_member_bytes \
        decompressed_root_bytes root_framing_bytes max_container_depth \
        compound_count compound_entry_count list_count list_element_count \
        scalar_tag_count byte_array_elements int_array_elements long_array_elements \
        string_count modified_utf8_bytes attachment_write_any_tag_bytes \
        draft_count latest_count equipped_count projected_root_count \
        directory_entries_observed canonical_primary_names canonical_old_names \
        unique_uuid_records ignored_entries relevant_malformed_entries \
        metadata_bytes_estimate compressed_bytes_total decompressed_bytes_total \
        tag_count_total value_elements_total attachment_admission_count \
        root_claims_raw distinct_root_references; do
        require_any_fixed_in_file_list "${RESEARCH_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 metrics schema is missing ${marker}"
    done
    for marker in \
        schema_version git_head scenario parameters fixture_manifest jvm os \
        process_result elapsed_millis heap directory_metrics wire_metrics \
        nbt_metrics attachment_metrics root_metrics store_journal_metrics \
        integrity classification \
        COMPLETED REJECTED_BY_RESEARCH_GUARD FIXTURE_INVALID \
        INSTRUMENTATION_FAILURE CHILD_EXIT_FAILURE TIMEOUT OOME_EXIT; do
        require_any_fixed_in_file_list "${RESEARCH_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 result schema is missing ${marker}"
    done
    for marker in \
        'new ProcessBuilder(' \
        'waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)' \
        'destroyForcibly()' \
        'HOTSPOT_EXIT_ON_OOME_CODE = 3' \
        'CHILD_TIMEOUT_SECONDS = 540L' \
        'CHILD_TERMINATION_SECONDS = 30L' \
        'Files.deleteIfExists(report)' \
        '.inheritIO()' \
        'var childReport = readReportIfBounded(report);' \
        'var exitCode = child.exitValue();' \
        '"-Xms512m"' \
        '"-XX:+ExitOnOutOfMemoryError"' \
        'DEDICATED_RUNNING_MARKER = "dedicated-running-v0.txt"' \
        'DEDICATED_RUNNING_CONTENT = "P4_E0_RESEARCH_RUNNING_V0\n"' \
        'classifyDedicatedMissingExit(exactRunningMarker)' \
        'hasExactDedicatedRunningMarker(reportRoot)' \
        'input.readNBytes(expectedBytes.length + 1)'; do
        require_fixed "${main}" "${marker}" \
            "P4-E0-R1 parent/child classification is missing ${marker}"
    done
    for marker in \
        'child.getInputStream(' \
        'child.getErrorStream(' \
        '.redirectOutput(' \
        '.redirectError(' \
        '.readLine('; do
        forbid_fixed "${main}" "${marker}" \
            "P4-E0-R1 parent must not classify a child by stdout/stderr API ${marker}"
    done
    require_fixed \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java \
        'P4E0ResearchMain.markDedicatedRunning(reportRoot);' \
        'P4-E0-R1 dedicated coordinator does not publish its bounded RUNNING marker'
    require_fixed \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java \
        'System.getProperty("gramarye.p4e0.research.runMode")' \
        'P4-E0-R1 dedicated coordinator reused the workload scenario as a run mode'
    require_fixed "${result}" \
        'process.addProperty("exit_code", processExitCode)' \
        'P4-E0-R1 process exit code is not parent-observed data'
    forbid_fixed "${result}" \
        'process.addProperty("exit_code", 0)' \
        'P4-E0-R1 process exit code must not be hard-coded'
}

verify_r2_research_contract() {
    local marker=''
    local plan='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2PlanFactory.java'
    local matrix='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchMatrixPlan.java'
    local aggregator='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchReportAggregator.java'
    local roots='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchRootWorkloads.java'
    local combined='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchCombinedEnvelope.java'
    local store='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0ResearchCombinedStoreSession.java'
    local r2main='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2Main.java'
    local driver='src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2DedicatedDriver.java'

    for marker in \
        'List.of(1024, 1280, 1536, 1792, 2048)' \
        'PLAIN_TIMEOUT_SECONDS = 600' \
        'DEDICATED_TIMEOUT_SECONDS = 900' \
        'EXPLORATORY_NON_NORMATIVE' \
        'Observed pass/fail frontiers are machine-, fixture- and ' \
        'implementation-specific evidence. They do not become Gramarye authority until ' \
        'explicitly approved in P4-E0-B.'; do
        require_fixed "${matrix}" "${marker}" \
            "P4-E0-R2 matrix authority boundary is missing ${marker}"
    done

    for marker in \
        'List.of(64L, 256L, 1_024L, 4_096L, 16_384L, 32_768L, 65_536L)' \
        '1L, 4L, 16L, 32L, 64L, 96L, 128L' \
        'List.of(16L, 32L, 64L, 128L, 256L)' \
        'List.of(64L, 128L, 256L, 512L, 513L)' \
        'List.of(65_536L, 262_144L, 1_048_576L)' \
        'List.of(65_536L, 262_144L, 1_048_576L, 4_194_304L)' \
        'List.of(64L, 256L, 512L, 1_024L)' \
        'List.of(256L, 512L, 1_024L, 2_048L)' \
        'EXACT_ALL_DISTINCT' \
        'OVER_LIMIT_ALL_DISTINCT' \
        'EXACT_NINETY_PERCENT_DUPLICATES' \
        'OVER_LIMIT_NINETY_PERCENT_DUPLICATES' \
        'PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL' \
        'FIRST_MISSING_BEGINNING' \
        'FIRST_MISSING_MIDDLE' \
        'FIRST_MISSING_END' \
        'BALANCED", "DIRECTORY_HEAVY", "SINGLE_FILE_HEAVY' \
        '"raw_root_attempt", 65_537L' \
        '"journal_entries", 4_096L' \
        '"store_bytes", 66_060_348L'; do
        require_fixed "${plan}" "${marker}" \
            "P4-E0-R2 plan lost required coordinate ${marker}"
    done

    for marker in \
        'runs.jsonl' \
        'candidate-frontiers.csv' \
        'summary.md' \
        'fixture-manifest.json' \
        '"largest_observed_completed", "smallest_observed_failed"' \
        '"smallest_observed_rejected", "smallest_observed_oome_or_timeout"' \
        'DISCLAIMER'; do
        require_fixed "${aggregator}" "${marker}" \
            "P4-E0-R2 report contract is missing ${marker}"
    done

    for marker in \
        'EXACT_ROOT_COUNT = 65_536' \
        'OVER_LIMIT_ROOT_COUNT = 65_537' \
        'OVER_LIMIT_NINETY_PERCENT_DUPLICATES' \
        'FIRST_MISSING_BEGINNING' \
        'FIRST_MISSING_MIDDLE' \
        'FIRST_MISSING_END' \
        'combinedPlayerAndJournalOverLimit'; do
        require_fixed "${roots}" "${marker}" \
            "P4-E0-R2 root workload is missing ${marker}"
    done
    for marker in \
        'BALANCED' \
        'DIRECTORY_HEAVY' \
        'SINGLE_FILE_HEAVY' \
        'beginHeldPlatformSave' \
        'OVER_LIMIT_ROOT_COUNT' \
        'journalRootCount() != 4_096'; do
        require_fixed "${combined}" "${marker}" \
            "P4-E0-R2 combined envelope is missing ${marker}"
    done
    for marker in \
        'exact 66,060,348-byte carrier' \
        'P4D3StoreJournalFixture.STORE_BYTES' \
        'P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_ENTRIES' \
        'P4D3StoreJournalFixture.PROSPECTIVE_JOURNAL_BYTES' \
        'beginHeldSave' \
        'retainAtPeak'; do
        require_fixed "${store}" "${marker}" \
            "P4-E0-R2 combined Store session is missing ${marker}"
    done

    for marker in \
        'prepare-plan' \
        'verify-fixtures' \
        'classify-combined' \
        'new ProcessBuilder(' \
        '.inheritIO()' \
        'destroyForcibly()' \
        'matrix-child-active-v0.lock' \
        'spec.coordinate() > 16_384L' \
        'record.elapsedMillis() < (spec.timeoutSeconds() * 1_000L) / 4L' \
        'Files.getFileStore(matrixRoot(fixtureRoot))' \
        '.getUsableSpace() >= conservative' \
        'OOME_EXIT' \
        'TIMEOUT' \
        'FIXTURE_INVALID' \
        'INSTRUMENTATION_FAILURE' \
        'CHILD_EXIT_FAILURE'; do
        require_fixed "${r2main}" "${marker}" \
            "P4-E0-R2 supervisor/classifier is missing ${marker}"
    done
    for marker in \
        'gramarye.p4e0.research.runIndex' \
        'gramarye.p4e0.research.heapMiB' \
        'gramarye.p4e0.research.runningMarker' \
        'TIMEOUT_EXIT_CODE = 124' \
        'Runtime.getRuntime().halt(TIMEOUT_EXIT_CODE)' \
        'P4E0ResearchCombinedCoordinator.run'; do
        require_fixed "${driver}" "${marker}" \
            "P4-E0-R2 dedicated driver is missing ${marker}"
    done

    for marker in \
        'child.getInputStream(' \
        'child.getErrorStream(' \
        '.readLine(' \
        'System.gc(' \
        'Thread.sleep(' \
        'Files.readAllBytes' \
        'NbtAccounter.unlimitedHeap' \
        '.reclaim('; do
        forbid_fixed "${r2main}" "${marker}" \
            "P4-E0-R2 supervisor contains forbidden ${marker}"
    done
    for marker in recommended_max safe_max production_limit authority_value; do
        forbid_fixed_in_file_list "${RESEARCH_SOURCE_LIST}" "${marker}" \
            "P4-E0-R2 source could emit forbidden vocabulary ${marker}"
        forbid_fixed_in_file_list "${GAME_SOURCE_LIST}" "${marker}" \
            "P4-E0-R2 dedicated source could emit forbidden vocabulary ${marker}"
    done
}

verify_phase_boundary() {
    local marker=''
    for marker in \
        'org.junit' \
        'System.gc(' \
        'Thread.sleep(' \
        'Files.readAllBytes' \
        'NbtAccounter.unlimitedHeap' \
        'java.lang.reflect' \
        'setAccessible(' \
        'sun.misc.Unsafe' \
        '.reclaim(' \
        'ServerStartingEvent' \
        'PlayerLoggedInEvent' \
        'RootIndex' \
        'Reconciliation' \
        'CustomPacketPayload' \
        'net.minecraft.client' \
        'MAX_PLAYERDATA_NBT_TREE_NODES' \
        'MAX_PLAYERDATA_AUDIT_WORK_UNITS'; do
        forbid_fixed_in_file_list "${RESEARCH_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 research source opened forbidden surface ${marker}"
        forbid_fixed_in_file_list "${GAME_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 dedicated source opened forbidden surface ${marker}"
    done
    forbid_ere_in_file_list "${RESEARCH_SOURCE_LIST}" \
        'catch[[:space:]]*\([^)]*OutOfMemoryError' \
        'P4-E0-R1 research source must not catch OOME'
    forbid_ere_in_file_list "${GAME_SOURCE_LIST}" \
        'catch[[:space:]]*\([^)]*OutOfMemoryError' \
        'P4-E0-R1 dedicated source must not catch OOME'
    for marker in \
        '@TempDir' \
        'Files.createTempDirectory' \
        'java.io.tmpdir' \
        '".minecraft"' \
        '/.minecraft/' \
        '/Users/'; do
        forbid_fixed_in_file_list "${RESEARCH_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 research data escaped build/: ${marker}"
        forbid_fixed_in_file_list "${GAME_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 dedicated data escaped build/: ${marker}"
    done
    for marker in \
        P4E0Research p4-e0-research gramarye_p4_e0_research; do
        forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" "${marker}" \
            "P4-E0-R1 test-only surface leaked into production: ${marker}"
    done
    for marker in \
            MAX_PLAYERDATA_NBT_TREE_NODES \
            MAX_PLAYERDATA_AUDIT_WORK_UNITS; do
        forbid_fixed \
            src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java \
            "${marker}" \
            "P4-E0-R1 research-only constant leaked into MagicSafetyCeilings: ${marker}"
    done
    forbid_fixed .github/workflows/build.yml 'p4-e0-research' \
        'P4-E0-R1 must not add a workflow job'
}

verify_jar_isolation() {
    local jar_path=''
    local source_path=''
    local class_path=''
    local status=0
    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-jars.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-jar.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    [[ "${status}" -eq 0 && -s "${JAR_FILE_LIST}" ]] \
        || fail 'P4-E0-R1 verifier found no production JAR to inspect'
    while IFS= read -r -d '' jar_path; do
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed while checking ${jar_path}"
        while IFS= read -r -d '' source_path; do
            class_path="${source_path#src/p4E0Research/java/}"
            class_path="${class_path%.java}.class"
            forbid_fixed "${JAR_LISTING}" "${class_path}" \
                "P4-E0-R1 research class leaked into production JAR: ${class_path}"
        done < "${RESEARCH_SOURCE_LIST}"
        while IFS= read -r -d '' source_path; do
            class_path="${source_path#src/p4E0ResearchGameTest/java/}"
            class_path="${class_path%.java}.class"
            forbid_fixed "${JAR_LISTING}" "${class_path}" \
                "P4-E0-R1 dedicated class leaked into production JAR: ${class_path}"
        done < "${GAME_SOURCE_LIST}"
        while IFS= read -r -d '' source_path; do
            forbid_fixed "${JAR_LISTING}" "${source_path#src/p4E0Research/resources/}" \
                'P4-E0 research resource leaked into production JAR'
        done < "${RESOURCE_LIST}"
    done < "${JAR_FILE_LIST}"
}

main() {
    local executable=''
    verify_search_helpers
    require_regular_file build.gradle 'P4-E0-R1 verifier cannot inspect build.gradle'
    require_regular_file .github/workflows/build.yml \
        'P4-E0-R1 verifier cannot inspect the workflow'
    require_regular_file scripts/verify-p4-e0-r-configuration.sh \
        'P4-E0-R1 portable verifier is missing'
    bash -n scripts/verify-p4-e0-r-configuration.sh \
        || fail 'P4-E0-R1 portable verifier failed bash -n'
    executable="$(find scripts/verify-p4-e0-r-configuration.sh \
        -prune -type f -perm 755 -print)"
    [[ "${executable}" == 'scripts/verify-p4-e0-r-configuration.sh' ]] \
        || fail 'P4-E0-R1 portable verifier mode must be 100755'
    forbid_ere scripts/verify-p4-e0-r-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-E0-R1 portable verifier must not invoke a developer-only search tool'

    RESEARCH_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-research.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create research source list'
    GAME_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-game.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create dedicated source list'
    TEST_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-tests.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create unit source list'
    RESOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-resources.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create resource list'
    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r-production.XXXXXX")" \
        || fail 'P4-E0-R1 verifier could not create production source list'
    collect_java_files src/p4E0Research/java "${RESEARCH_SOURCE_LIST}"
    collect_java_files src/p4E0ResearchGameTest/java "${GAME_SOURCE_LIST}"
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    LC_ALL=C find src/test/java -type f \
        \( -name 'P4E0Research*.java' -o -name 'P4E0R2Q*.java' \) -print0 \
        > "${TEST_SOURCE_LIST}"
    LC_ALL=C find src/p4E0Research/resources -type f -print0 > "${RESOURCE_LIST}"

    verify_changed_path_allowlist
    verify_prohibited_paths_unchanged
    verify_exact_source_allowlists
    verify_build_contract
    verify_r2_build_contract
    verify_fixture_and_metric_contract
    verify_r2_research_contract
    verify_phase_boundary
    verify_jar_isolation
    printf '%s\n' \
        'Verified exact P4-E0-R1/R2 research sources, matrix tasks, boundaries, and JAR isolation.'
}

main "$@"
