#!/usr/bin/env bash
set -euo pipefail

PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find git jar mktemp rm dirname pwd sed; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-E0-R2Q verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-E0-R2Q verifier could not resolve repository root'
fi
cd "${REPO_ROOT}"

HELPER_FIXTURE=''
R2Q_SOURCE_LIST=''
R2Q_GAME_LIST=''
JAR_LIST=''
JAR_CONTENTS=''
FORMAL_BLOCK=''
FORMAL_PROFILE_BLOCK=''
FORMAL_RUN_BLOCK=''
FORMAL_TASK_LOOP_BLOCK=''
PREPARE_CASE_BLOCK=''
COUNTER_BLOCK=''
CASE_KIND_BLOCK=''
RUNTIME_BLOCK=''
OFFICIAL_CLASSIFICATION_BLOCK=''
SMOKE_TASK_BLOCK=''
FORMAL_ENTRY_BLOCK=''

cleanup() {
    local temporary=''
    for temporary in \
        "${HELPER_FIXTURE}" \
        "${R2Q_SOURCE_LIST}" \
        "${R2Q_GAME_LIST}" \
        "${JAR_LIST}" \
        "${JAR_CONTENTS}" \
        "${FORMAL_BLOCK}" \
        "${FORMAL_PROFILE_BLOCK}" \
        "${FORMAL_RUN_BLOCK}" \
        "${FORMAL_TASK_LOOP_BLOCK}" \
        "${PREPARE_CASE_BLOCK}" \
        "${COUNTER_BLOCK}" \
        "${CASE_KIND_BLOCK}" \
        "${RUNTIME_BLOCK}" \
        "${OFFICIAL_CLASSIFICATION_BLOCK}" \
        "${SMOKE_TASK_BLOCK}" \
        "${FORMAL_ENTRY_BLOCK}"; do
        if [[ -n "${temporary}" ]]; then
            rm -f -- "${temporary}"
        fi
    done
}
trap cleanup EXIT HUP INT TERM

grep_failed() {
    fail "grep failed while checking $1 (exit $2)"
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

require_count() {
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

require_fixed_before() {
    local file="$1"
    local first_needle="$2"
    local second_needle="$3"
    local message="$4"
    local first_match=''
    local second_match=''
    local first_line=''
    local second_line=''
    local status=0
    first_match="$(LC_ALL=C grep -Fn -- "${first_needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) fail "${message} (first marker is missing)" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    status=0
    second_match="$(LC_ALL=C grep -Fn -- "${second_needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) fail "${message} (second marker is missing)" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${first_match}" != *$'\n'* && "${second_match}" != *$'\n'* ]] \
        || fail "${message} (ordering marker is not unique)"
    first_line="${first_match%%:*}"
    second_line="${second_match%%:*}"
    [[ "${first_line}" =~ ^[0-9]+$ && "${second_line}" =~ ^[0-9]+$ \
            && "${first_line}" -lt "${second_line}" ]] \
        || fail "${message}"
}

null_record_count() {
    local file="$1"
    local count=0
    local value=''
    while IFS= read -r -d '' value; do
        count=$((count + 1))
    done < "${file}"
    printf '%s\n' "${count}"
}

require_regular_file() {
    [[ -f "$1" && ! -L "$1" ]] || fail "$2"
}

verify_helpers() {
    local output=''
    local status=0
    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-helper.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create helper fixture'
    printf '%s\n' 'present contract' 'later contract' > "${HELPER_FIXTURE}"
    require_fixed "${HELPER_FIXTURE}" 'present contract' \
        'P4-E0-R2Q self-test lost required matching'
    forbid_fixed "${HELPER_FIXTURE}" 'absent contract' \
        'P4-E0-R2Q self-test misclassified an absent pattern'
    require_fixed_before "${HELPER_FIXTURE}" 'present contract' 'later contract' \
        'P4-E0-R2Q self-test lost fixed-marker ordering'

    output="$({ require_fixed "${HELPER_FIXTURE}" missing EXPECTED_MISSING; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${output}" == 'EXPECTED_MISSING' ]] \
        || fail 'P4-E0-R2Q verifier cannot distinguish a missing contract'
    status=0
    output="$({ forbid_fixed "${HELPER_FIXTURE}" present EXPECTED_FORBIDDEN; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${output}" == 'EXPECTED_FORBIDDEN' ]] \
        || fail 'P4-E0-R2Q verifier cannot distinguish a forbidden contract'
    status=0
    output="$({ require_count "${HELPER_FIXTURE}" present 2 EXPECTED_COUNT; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${output}" == EXPECTED_COUNT* ]] \
        || fail 'P4-E0-R2Q verifier cannot distinguish an exact-count mismatch'
    status=0
    output="$({ require_fixed_before "${HELPER_FIXTURE}" \
            'later contract' 'present contract' EXPECTED_ORDER; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${output}" == 'EXPECTED_ORDER' ]] \
        || fail 'P4-E0-R2Q verifier cannot distinguish reversed marker order'
    status=0
    output="$({ require_fixed "${HELPER_FIXTURE}.missing" present WRONG_MISSING; } 2>&1)" \
        || status=$?
    if [[ "${status}" -ne 1 \
            || "${output}" != *'grep failed while checking '* \
            || "${output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-E0-R2Q verifier cannot distinguish tool error from missing input'
    fi
    is_reviewed_e1a_production_or_ledger_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java' \
        || fail 'P4-E0-R2Q verifier rejected an exact reviewed E1-A path'
    if is_reviewed_e1a_production_or_ledger_path \
            'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudgetExtra.java'; then
        fail 'P4-E0-R2Q verifier accepted a prefix-near E1-A production path'
    fi
    if is_reviewed_e1a_production_or_ledger_path \
            'docs/codex-spec/18_P4持久化與組合修正案.md'; then
        fail 'P4-E0-R2Q verifier accepted an authority path as E1-A work'
    fi
}

is_approved_p4e3_changed_path() {
    case "$1" in
        .github/workflows/build.yml | \
        build.gradle | \
        scripts/verify-p4-b2-b-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/P4E3StartupObservationTestAccess.java | \
        src/p4E3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupMemoryGameTests.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/player/P4E3PlayerDataFixture.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FileVerifier.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureBuilder.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3FixtureManifest.java | \
        src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3LeaseTerminalTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3StartupLifecycleTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_reviewed_e1a_production_or_ledger_path() {
    is_approved_p4e3_changed_path "$1" && return 0
    case "$1" in
        docs/architecture/P4-0-persistence-boundary.md | \
        docs/architecture/P4-E0-root-audit-boundary.md | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentAdmission.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSerializer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1BoundPlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditBudget.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditedCapture.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompleteRootHandoff.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditCounter.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1AuditStage.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1CompressedCapacityRejected.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FileMetadata.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FileSystemAccess.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1FinalFreshness.java | \
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
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1PendingJournalObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RawClaimBuffer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1RootSourceFamily.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1SourceInventory.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1StoreHistoryObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2ReconciliationResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInput.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java | \
        src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java) return 0 ;;
        *) return 1 ;;
    esac
}

verify_only_reviewed_e1a_production_or_ledger_changes() {
    local changed=''
    local status=0
    local path=''
    changed="$(git diff --name-only HEAD -- \
        src/main/java src/main/resources docs/codex-spec docs/architecture \
        .github/workflows gradle.properties)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking tracked production and authority paths'
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_e1a_production_or_ledger_path "${path}" \
            || fail "P4-E0-R2Q modified an unreviewed production, authority, workflow, or version path: ${path}"
    done <<< "${changed}"

    status=0
    changed="$(git ls-files --others --exclude-standard -- \
        src/main/java src/main/resources docs/codex-spec docs/architecture \
        .github/workflows gradle.properties)" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'git failed while checking untracked production and authority paths'
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_e1a_production_or_ledger_path "${path}" \
            || fail "P4-E0-R2Q added an unreviewed production, authority, workflow, or version path: ${path}"
    done <<< "${changed}"
}

verify_paths_and_boundaries() {
    local untracked=''
    local status=0
    for file in \
        build.gradle \
        scripts/verify-p4-b2-b-configuration.sh \
        scripts/verify-p4-e0-r-configuration.sh \
        scripts/verify-p4-e0-r2q-configuration.sh \
        src/p4E0Research/resources/p4-e0-r2q-profile-v0.json \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/player/P4E0ResearchAttachmentFixtures.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QAuditBudget.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QStudyIdentity.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalEvidence.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalMain.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalResult.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalWorkload.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QModifiedUtf.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QPositiveWitnesses.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFixturePlan.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QJointRecords.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0R2QStoreJournalFixtures.java \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QDedicatedDriver.java \
        src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalDedicatedDriver.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QProfileTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QAuditBudgetTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QPositiveWitnessTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QJointRecordsTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QRootProjectionTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QNegativeFixtureTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QModifiedUtfTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QExactGzipWitnessTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFixtureTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalContractTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalEvidenceTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalGateNegativeTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalResultTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QApiGateTest.java \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QStudyIdentityTest.java; do
        require_regular_file "${file}" "P4-E0-R2Q reviewed path is missing: ${file}"
    done

    verify_only_reviewed_e1a_production_or_ledger_changes
    forbid_fixed .github/workflows/build.yml 'p4-e0-r2q' \
        'P4-E0-R2Q must not add a workflow job'
    forbid_fixed .github/workflows/build.yml 'P4-E0-R2Q' \
        'P4-E0-R2Q must not add a workflow display name'
    forbid_fixed build.gradle "name.startsWith('p4E0R2QCase')" \
        'P4-E0-R2Q formal loaded-mod selection must use the exact generated inventory'
    forbid_fixed build.gradle "name.startsWith('p4E0R2Q')" \
        'P4-E0-R2Q loaded-mod selection must not use a broad phase prefix'
}

verify_profile_manifest() {
    local manifest='src/p4E0Research/resources/p4-e0-r2q-profile-v0.json'
    local marker=''
    for marker in \
        '"schema_version": 0' \
        '"authority": "EXPLORATORY_NON_NORMATIVE"' \
        '"profile_name": "BALANCED_V0_1536_QUALIFICATION"' \
        '"candidate_values"' \
        '"directory_entries": 4096' \
        '"relevant_records": 2048' \
        '"compressed_bytes_per_file": 33559514' \
        '"decompressed_bytes_per_file": 268435456' \
        '"container_depth_per_file": 512' \
        '"compound_containers_per_file": 1024' \
        '"compound_field_entries_per_file": 65537' \
        '"list_elements_per_file": 65536' \
        '"byte_array_elements_per_file": 268435384' \
        '"int_array_elements_per_file": 65536' \
        '"long_array_elements_per_file": 65536' \
        '"modified_utf8_bytes_per_file": 67107692' \
        '"scalar_tags_per_file": 65537' \
        '"counter_coordinates"' \
        '"dependency_edges"' \
        '"failure_precedence"' \
        '"overrun_policy": "INCOMPLETE_AND_CONTINUE"' \
        '"bytes": 12884901888' \
        '"qualification_mib": 1536' \
        '"accepted_data_version": 3955' \
        '"max_dfu_records": 0' \
        '"raw_root_claims": 65536' \
        '"attachment_admissions": 1024' \
        '"compressed_bytes_total": 268440533' \
        '"decompressed_bytes_total": 536870912' \
        '"compound_containers_total": 131072' \
        '"compound_field_entries_total": 524288' \
        '"list_elements_total": 131072' \
        '"byte_array_elements_total": 456524705' \
        '"int_array_elements_total": 131072' \
        '"long_array_elements_total": 131072' \
        '"modified_utf8_bytes_total": 75497472' \
        '"scalar_tags_total": 458752'; do
        require_fixed "${manifest}" "${marker}" \
            "P4-E0-R2Q profile manifest is missing ${marker}"
    done
    forbid_fixed "${manifest}" '"qualification_mib": 1280' \
        'P4-E0-R2Q must not add a 1280 MiB qualification case'
    forbid_fixed "${manifest}" 'production_limit' \
        'P4-E0-R2Q profile must not claim production authority'
}

verify_case_plan_contract() {
    local profile='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java'
    local plan='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java'
    local slug=''
    COUNTER_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-counters.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create counter block'
    CASE_KIND_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-kinds.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create case-kind block'
    sed -n '/^    enum Counter {$/,/^        private final String slug;$/p' \
        "${profile}" > "${COUNTER_BLOCK}"
    sed -n '/^    enum CaseKind {$/,/^    }$/p' \
        "${plan}" > "${CASE_KIND_BLOCK}"
    [[ -s "${COUNTER_BLOCK}" && -s "${CASE_KIND_BLOCK}" ]] \
        || fail 'P4-E0-R2Q verifier could not isolate counter/case enums'

    require_ere_count "${COUNTER_BLOCK}" \
        '^[[:space:]]+[A-Z0-9_]+\("[a-z0-9_]+"\)[,;]$' 25 \
        'P4-E0-R2Q must expose exactly twenty-five typed counter slugs'
    for slug in \
        directory_entries \
        relevant_records \
        compressed_bytes_per_file \
        decompressed_bytes_per_file \
        container_depth_per_file \
        compound_containers_per_file \
        compound_field_entries_per_file \
        list_elements_per_file \
        byte_array_elements_per_file \
        int_array_elements_per_file \
        long_array_elements_per_file \
        modified_utf8_bytes_per_file \
        scalar_tags_per_file \
        compressed_bytes_total \
        decompressed_bytes_total \
        compound_containers_total \
        compound_field_entries_total \
        list_elements_total \
        byte_array_elements_total \
        int_array_elements_total \
        long_array_elements_total \
        modified_utf8_bytes_total \
        scalar_tags_total \
        attachment_admissions \
        raw_root_claims; do
        require_count "${COUNTER_BLOCK}" "(\"${slug}\")" 1 \
            "P4-E0-R2Q typed counter slug changed: ${slug}"
    done

    require_ere_count "${CASE_KIND_BLOCK}" \
        '^[[:space:]]+[A-Z0-9_]+[,;]?$' 5 \
        'P4-E0-R2Q case-kind vocabulary must contain exactly five values'
    for kind in \
        POSITIVE \
        COUNTER_MAX_PLUS_ONE \
        DATA_VERSION_MISSING \
        DATA_VERSION_WRONG_TYPE \
        DATA_VERSION_WRONG_VALUE; do
        require_fixed "${CASE_KIND_BLOCK}" "${kind}" \
            "P4-E0-R2Q case-kind vocabulary is missing ${kind}"
    done
    forbid_fixed "${CASE_KIND_BLOCK}" 'DFU' \
        'P4-E0-R2Q must not add an independent DFU case kind'

    for marker in \
        'CASE_COUNT = 29' \
        'CASE_PREFIX + "exact"' \
        'CASE_PREFIX + "over-" + counter.slug().replace' \
        'addDataVersion(cases, CaseKind.DATA_VERSION_MISSING' \
        'addDataVersion(cases, CaseKind.DATA_VERSION_WRONG_TYPE' \
        'addDataVersion(cases, CaseKind.DATA_VERSION_WRONG_VALUE' \
        'input.size() != CASE_COUNT' \
        '!identifiers.add(spec.caseId())' \
        '!counters.add(target)' \
        'EnumSet.allOf(P4E0R2QProfile.Counter.class)' \
        'dataVersionControls != 3' \
        'spec.expectedDfuInvocations() != 0'; do
        require_fixed "${plan}" "${marker}" \
            "P4-E0-R2Q exact 29-case contract is missing ${marker}"
    done
    forbid_fixed "${plan}" '"dfu-' \
        'P4-E0-R2Q must not add an independent lowercase DFU case ID'
    forbid_fixed "${plan}" '-dfu"' \
        'P4-E0-R2Q must not add an independent lowercase DFU case ID'
    forbid_fixed "${plan}" '1280' \
        'P4-E0-R2Q must not add a 1280 MiB case'
}

verify_build_and_formal_gate() {
    local marker=''
    require_count build.gradle "sourceSets.create('p4E0Research" 2 \
        'P4-E0-R2Q must reuse exactly two research source sets'
    require_fixed scripts/verify-p4-b2-b-configuration.sh \
        "name == 'p4E0R2QDedicatedSmoke'" \
        'P4-E0-R2Q dedicated run is absent from the exact B2 loaded-mod allowlist'
    require_fixed scripts/verify-p4-b2-b-configuration.sh \
        "name == 'p4E0R2QRunnerDedicatedSmoke'" \
        'P4-E0-R2Q runner smoke is absent from the exact B2 loaded-mod allowlist'
    require_fixed scripts/verify-p4-b2-b-configuration.sh \
        "p4E0R2QConfiguredFormalRunNames.contains(name)" \
        'P4-E0-R2Q formal cases are absent from the exact B2 loaded-mod allowlist'

    FORMAL_PROFILE_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-profile.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create formal-profile block'
    sed -n '/^def p4E0R2QFormalJvmArgs = \[$/,/^def p4E0R2QConfiguredFormalRunNames = \[\]$/p' \
        build.gradle > "${FORMAL_PROFILE_BLOCK}"
    [[ -s "${FORMAL_PROFILE_BLOCK}" ]] \
        || fail 'P4-E0-R2Q exact formal runtime profile block is missing'
    require_ere_count "${FORMAL_PROFILE_BLOCK}" \
        "^[[:space:]]+'-(Xms512m|Xmx1536m|XX:\+ExitOnOutOfMemoryError)'[,]?$" 3 \
        'P4-E0-R2Q formal runtime must contain exactly three fixed JVM arguments'
    for marker in \
        "def p4E0R2QFormalJvmArgs = [" \
        "        '-Xms512m'," \
        "        '-Xmx1536m'," \
        "        '-XX:+ExitOnOutOfMemoryError'," \
        'def p4E0R2QFormalTimeoutSeconds = 900' \
        'def p4E0R2QFormalWatchdogSeconds = 870' \
        "def p4E0R2QFormalDiskBudgetBytes = '12884901888'" \
        'def p4E0R2QFormalCaseIndices = (0..<29).toList()' \
        "!= ['-Xms512m', '-Xmx1536m', '-XX:+ExitOnOutOfMemoryError']" \
        'p4E0R2QFormalTimeoutSeconds != 900' \
        'p4E0R2QFormalWatchdogSeconds != 870' \
        'p4E0R2QFormalWatchdogSeconds >= p4E0R2QFormalTimeoutSeconds' \
        "p4E0R2QFormalDiskBudgetBytes != '12884901888'" \
        'p4E0R2QFormalCaseIndices != (0..<29).toList()'; do
        require_count "${FORMAL_PROFILE_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q exact formal runtime profile changed: ${marker}"
    done
    require_count build.gradle \
        'def p4E0R2QFormalCaseIndices = (0..<29).toList()' 1 \
        'P4-E0-R2Q must define the exact 29-case coordinate once'
    for marker in \
        'p4E0R2QDedicatedSmoke {' \
        'p4E0R2QRunnerDedicatedSmoke {' \
        'def p4E0R2QSmokeTimeoutSeconds = 600' \
        "layout.buildDirectory.dir('p4-e0-r2q/dedicated-smoke')" \
        "layout.buildDirectory.dir('p4-e0-r2q/runner-dedicated-smoke')" \
        "layout.buildDirectory.dir('reports/p4-e0-r2q-smoke')" \
        "layout.buildDirectory.dir('reports/p4-e0-r2q-smoke/runtime')" \
        "layout.buildDirectory.dir('reports/p4-e0-r2q-smoke/supervisor')" \
        "layout.buildDirectory.dir('reports/p4-e0-r2q-smoke/supervisor/runner')" \
        "layout.buildDirectory.dir('reports/p4-e0-r2q')" \
        'p4E0R2QConfiguredRootPaths = [' \
        'left == right || left.startsWith(right) || right.startsWith(left)' \
        'P4-E0-R2Q official, smoke, and work roots must be pairwise disjoint' \
        'p4E0R2QDedicatedGameDirectory.get().asFile.deleteDir()' \
        "sourceSet = p4E0ResearchGameTestSourceSet" \
        "systemProperty 'gramarye.p4e0.research.runMode', 'r2q-smoke'" \
        "systemProperty 'gramarye.p4e0.research.runMode', 'r2q-runner-smoke'" \
        "systemProperty 'gramarye.p4e0.research.runMode', 'r2q-formal'" \
        'jvmArguments.addAll(p4E0R2QSmokeJvmArgs)' \
        'jvmArguments.addAll(p4E0R2QFormalJvmArgs)' \
        'timeout.set(java.time.Duration.ofSeconds(p4E0R2QSmokeTimeoutSeconds))' \
        'def p4E0R2QFormalTimeoutSeconds = 900' \
        'def p4E0R2QFormalWatchdogSeconds = 870' \
        "def p4E0R2QFormalDiskBudgetBytes = '12884901888'" \
        'def p4E0R2QFormalCaseIndices = (0..<29).toList()' \
        'p4E0R2QFormalCaseIndices.each { formalCaseIndex ->' \
        "systemProperty 'gramarye.p4e0.r2q.formal.caseIndex'" \
        "systemProperty 'gramarye.p4e0.r2q.formal.childResult'" \
        "systemProperty 'gramarye.p4e0.r2q.formal.runningMarker'" \
        "systemProperty 'gramarye.p4e0.r2q.formal.watchdogSeconds'" \
        "systemProperty 'gramarye.p4e0.r2q.formal.diskBudgetBytes'" \
        "'prepareP4E0R2Q'" \
        "'verifyP4E0R2QPreflightTests')" \
        "'verifyP4E0R2QFreshJvmDataVersion'," \
        "'verify-version-init'," \
        "'verifyP4E0R2QCase04Preparation'," \
        "'verify-case04-preparation'," \
        "'verifyP4E0R2QCounterPreparations'," \
        "'verify-counter-preparations'," \
        "'verifyP4E0R2QProfile'" \
        "'runP4E0R2QSmoke'" \
        "tasks.named('runP4E0R2QDedicatedSmoke', JavaExec)" \
        "'verifyP4E0R2QSmokeOutput'" \
        "'runP4E0R2QSupervisorSmoke'" \
        "tasks.named('runP4E0R2QRunnerDedicatedSmoke', JavaExec)" \
        "'verifyP4E0R2QSupervisorSmoke'" \
        "'verifyP4E0R2QConfiguration', Exec" \
        "tasks.register('p4E0R2QSmoke')" \
        "'prepareP4E0R2QFormalStudy'" \
        '"prepareP4E0R2QCase${formalCaseToken}"' \
        '"runP4E0R2QCase${formalCaseToken}"' \
        '"verifyP4E0R2QCase${formalCaseToken}"' \
        '"captureP4E0R2QCase${formalCaseToken}ParentFailure"' \
        "'aggregateP4E0R2QFormal'" \
        "'verifyP4E0R2QFormalArtifacts'" \
        "tasks.register('p4E0R2QStudy')" \
        'dependsOn(verifyP4E0R2QConfiguration)' \
        'dependsOn(verifyP4E0R2QPreflightTests)' \
        'dependsOn(verifyP4E0R2QFreshJvmDataVersion)' \
        'dependsOn(verifyP4E0R2QCase04Preparation)' \
        'dependsOn(verifyP4E0R2QCounterPreparations)' \
        'dependsOn(prepareP4E0R2Q)' \
        'dependsOn(verifyP4E0R2QProfile)' \
        'dependsOn(runP4E0R2QSmoke)' \
        'dependsOn(runP4E0R2QDedicatedSmoke)' \
        'dependsOn(verifyP4E0R2QSmokeOutput)' \
        'dependsOn(verifyP4E0R2QSupervisorSmoke)' \
        ".gradleProperty('p4E0R2QFormal')" \
        ".gradleProperty('p4E0ResearchDiskBudgetBytes')" \
        "commandLine('git', 'status', '--porcelain=v2', '--untracked-files=all')" \
        "commandLine('git', 'diff', '--exit-code')" \
        "commandLine('git', 'diff', '--cached', '--exit-code')" \
        "commandLine('git', 'ls-files', '--others', '--exclude-standard')" \
        "commandLine('git', 'symbolic-ref', '--quiet', '--short', 'HEAD')" \
        "commandLine('git', 'rev-parse', 'HEAD')" \
        "commandLine('git', 'rev-parse', 'HEAD^{tree}')" \
        "commandLine('git', 'rev-parse', 'origin/main')" \
        'p4E0R2QFormalMode.get() != '"'"'true'"'"'' \
        'p4E0R2QFormalDiskBudget.get() != p4E0R2QFormalDiskBudgetBytes' \
        "p4E0R2QGitBranch.standardOutput.asText.get().trim() != 'main'" \
        'head != originMain' \
        'def previousP4E0R2QFormalVerifier = prepareP4E0R2QFormalStudy' \
        'dependsOn(prerequisiteVerifier)' \
        'dependsOn(prepareCaseTask)' \
        'dependsOn(runCaseTask)' \
        'finalizedBy(captureFailureTask)' \
        'java.nio.file.StandardOpenOption.CREATE_NEW' \
        'channel.force(true)' \
        'previousP4E0R2QFormalVerifier = verifyCaseTask' \
        'dependsOn(previousP4E0R2QFormalVerifier)' \
        'dependsOn(aggregateP4E0R2QFormal)' \
        'dependsOn(verifyP4E0R2QFormalArtifacts)'; do
        require_fixed build.gradle "${marker}" \
            "P4-E0-R2Q build contract is missing ${marker}"
    done
    forbid_fixed build.gradle \
        "layout.buildDirectory.dir('reports/p4-e0-r2q/smoke')" \
        'P4-E0-R2Q smoke must not write below the official artifact root'
    forbid_fixed build.gradle \
        'reports/p4-e0-r2q-runner-smoke' \
        'P4-E0-R2Q must not retain the obsolete runner-smoke namespace'

    SMOKE_TASK_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-smoke-tasks.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create smoke-task block'
    sed -n "/^def verifyP4E0R2QFreshJvmDataVersion =/,/^def p4E0R2QGitStatus =/p" \
        build.gradle > "${SMOKE_TASK_BLOCK}"
    [[ -s "${SMOKE_TASK_BLOCK}" ]] \
        || fail 'P4-E0-R2Q smoke task block is missing'
    for marker in \
        'prepareP4E0R2QFormalStudy' \
        'aggregateP4E0R2QFormal' \
        'verifyP4E0R2QFormalArtifacts' \
        "tasks.register('p4E0R2QStudy')"; do
        forbid_fixed "${SMOKE_TASK_BLOCK}" "${marker}" \
            "P4-E0-R2Q smoke graph depends on formal task ${marker}"
    done
    for marker in \
        "'verifyP4E0R2QFreshJvmDataVersion'," \
        "'verify-version-init'," \
        'verifyP4E0R2QFreshJvmDataVersion.configure {' \
        'dependsOn(verifyP4E0R2QConfiguration)' \
        "dir('version-init-world').asFile.deleteDir()" \
        "file('fresh-jvm-dataversion-v0.json').asFile.delete()" \
        "'verifyP4E0R2QCase04Preparation'," \
        "'verify-case04-preparation'," \
        'verifyP4E0R2QCase04Preparation.configure {' \
        'dependsOn(verifyP4E0R2QFreshJvmDataVersion)' \
        "dir('case04-preparation-world').asFile.deleteDir()" \
        "file('case04-preparation-v0.json').asFile.delete()" \
        "'verifyP4E0R2QCounterPreparations'," \
        "'verify-counter-preparations'," \
        'verifyP4E0R2QCounterPreparations.configure {' \
        'dependsOn(verifyP4E0R2QCase04Preparation)' \
        "dir('counter-preparation-world').asFile.deleteDir()" \
        "file('counter-preparation-v0.json').asFile.delete()" \
        'dependsOn(verifyP4E0R2QCounterPreparations)'; do
        require_count "${SMOKE_TASK_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q fresh-JVM pre-child gate changed: ${marker}"
    done

    FORMAL_ENTRY_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-formal-entry.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create formal-entry block'
    sed -n "/^def p4E0R2QGitStatus =/,/^tasks.named('test', Test).configure/p" \
        build.gradle > "${FORMAL_ENTRY_BLOCK}"
    [[ -s "${FORMAL_ENTRY_BLOCK}" ]] \
        || fail 'P4-E0-R2Q formal-only task spine is missing'
    for marker in \
        'runP4E0R2QSmoke' \
        'runP4E0R2QDedicatedSmoke' \
        'runP4E0R2QSupervisorSmoke' \
        'runP4E0R2QRunnerDedicatedSmoke' \
        'verifyP4E0R2QFreshJvmDataVersion' \
        'verify-version-init' \
        'verifyP4E0R2QCase04Preparation' \
        'verify-case04-preparation' \
        'verifyP4E0R2QCounterPreparations' \
        'verify-counter-preparations'; do
        forbid_fixed "${FORMAL_ENTRY_BLOCK}" "${marker}" \
            "P4-E0-R2Q formal spine depends on smoke task ${marker}"
    done

    FORMAL_RUN_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-runs.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create formal-run block'
    sed -n '/^        p4E0R2QFormalCaseIndices.each { formalCaseIndex ->$/,/^        }$/p' \
        build.gradle > "${FORMAL_RUN_BLOCK}"
    [[ -s "${FORMAL_RUN_BLOCK}" ]] \
        || fail 'P4-E0-R2Q exact dedicated formal-run loop is missing'
    for marker in \
        'def formalCaseToken = String.format(' \
        "'%02d', formalCaseIndex" \
        'def formalRunName = "p4E0R2QCase${formalCaseToken}".toString()' \
        'p4E0R2QConfiguredFormalRunNames.add(formalRunName)' \
        'create(formalRunName) {' \
        "type = 'gameTestServer'" \
        'sourceSet = p4E0ResearchGameTestSourceSet' \
        "systemProperty 'gramarye.p4e0.research.runMode', 'r2q-formal'" \
        "systemProperty 'gramarye.p4e0.r2q.formal.enabled', 'true'" \
        "systemProperty 'gramarye.p4e0.r2q.formal.caseIndex'," \
        "systemProperty 'gramarye.p4e0.r2q.formal.studyControl'," \
        "systemProperty 'gramarye.p4e0.r2q.formal.caseRoot'," \
        "systemProperty 'gramarye.p4e0.r2q.formal.childResult'," \
        "systemProperty 'gramarye.p4e0.r2q.formal.runningMarker'," \
        "systemProperty 'gramarye.p4e0.r2q.formal.watchdogSeconds'," \
        'p4E0R2QFormalWatchdogSeconds.toString()' \
        "systemProperty 'gramarye.p4e0.r2q.formal.diskBudgetBytes'," \
        'p4E0R2QFormalDiskBudgetBytes' \
        'jvmArguments.addAll(p4E0R2QFormalJvmArgs)' \
        'taskBefore(tasks.named(p4E0ResearchGameTestSourceSet.classesTaskName))'; do
        require_count "${FORMAL_RUN_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q dedicated formal-run contract changed: ${marker}"
    done
    require_ere_count "${FORMAL_RUN_BLOCK}" \
        "^[[:space:]]+systemProperty 'gramarye[.]p4e0[.](research[.]runMode|r2q[.]formal[.](enabled|caseIndex|studyControl|caseRoot|childResult|runningMarker|watchdogSeconds|diskBudgetBytes))'[,]?" \
        9 'P4-E0-R2Q dedicated formal child property inventory changed'

    require_count build.gradle \
        'p4E0R2QConfiguredFormalRunNames.contains(name)' 1 \
        'P4-E0-R2Q exact formal loaded-mod membership must occur once'
    for marker in \
        'if (p4E0R2QConfiguredFormalRunNames' \
        '!= p4E0R2QFormalCaseIndices.collect { formalCaseIndex ->' \
        "'p4E0R2QCase%02d'," \
        "throw new GradleException('P4-E0-R2Q dedicated formal run inventory drift')"; do
        require_count build.gradle "${marker}" 1 \
            "P4-E0-R2Q exact dedicated run inventory changed: ${marker}"
    done

    RUNTIME_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-runtime.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create runtime block'
    sed -n '/^        p4E0ResearchHarness {$/,/^        }$/p' \
        build.gradle > "${RUNTIME_BLOCK}"
    [[ -s "${RUNTIME_BLOCK}" ]] \
        || fail 'P4-E0-R2Q dedicated runtime block is missing'
    require_ere_count "${RUNTIME_BLOCK}" '^[[:space:]]+sourceSet\(' 7 \
        'P4-E0-R2Q dedicated runtime must contain exactly seven source sets'
    for marker in \
        'sourceSet(sourceSets.main)' \
        'sourceSet(p4A3ProbeSourceSet)' \
        'sourceSet(p4B2ProbeSourceSet)' \
        'sourceSet(p4C2ProbeSourceSet)' \
        'sourceSet(p4D3ProbeSourceSet)' \
        'sourceSet(p4E0ResearchSourceSet)' \
        'sourceSet(p4E0ResearchGameTestSourceSet)'; do
        require_count "${RUNTIME_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q dedicated runtime changed ${marker}"
    done
    for marker in \
        'sourceSets.test' \
        'p4A3GameTestSourceSet' \
        'p4B2GameTestSourceSet' \
        'p4C2GameTestSourceSet' \
        'p4D3GameTestSourceSet'; do
        forbid_fixed "${RUNTIME_BLOCK}" "${marker}" \
            "P4-E0-R2Q dedicated runtime contains forbidden ${marker}"
    done

    require_count build.gradle \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName' 7 \
        'P4-E0-R2Q dedicated classpath must have exactly seven reviewed dependencies'
    require_count build.gradle \
        'p4E0ResearchGameTestSourceSet.implementationConfigurationName' 7 \
        'P4-E0-R2Q dedicated implementation configuration gained an unreviewed use'
    for marker in \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4D3ProbeSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, p4E0ResearchSourceSet.output)' \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName, commonsCompressCoordinate)'; do
        require_count build.gradle "${marker}" 1 \
            "P4-E0-R2Q dedicated classpath changed ${marker}"
    done
    for marker in \
        'p4E0ResearchGameTestImplementation' \
        'p4E0ResearchGameTestRuntimeOnly' \
        'p4E0ResearchGameTestRuntimeClasspath' \
        'p4E0ResearchGameTestSourceSet.runtimeOnlyConfigurationName' \
        'p4E0ResearchGameTestSourceSet.runtimeClasspathConfigurationName' \
        'sourceSets.test.output' \
        'testRuntimeClasspath'; do
        forbid_fixed build.gradle "${marker}" \
            "P4-E0-R2Q dedicated runtime acquired forbidden test/JUnit path ${marker}"
    done

    FORMAL_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-formal.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create formal-task block'
    sed -n "/^def p4E0R2QFormalMode =/,/^tasks.named('test', Test).configure/p" \
        build.gradle > "${FORMAL_BLOCK}"
    [[ -s "${FORMAL_BLOCK}" ]] || fail 'P4-E0-R2Q formal task block is missing'

    FORMAL_TASK_LOOP_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-task-loop.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create formal-task-loop block'
    sed -n '/^p4E0R2QFormalCaseIndices.each { formalCaseIndex ->$/,/^}$/p' \
        build.gradle > "${FORMAL_TASK_LOOP_BLOCK}"
    [[ -s "${FORMAL_TASK_LOOP_BLOCK}" ]] \
        || fail 'P4-E0-R2Q exact formal task loop is missing'
    for marker in mustRunAfter shouldRunAfter; do
        forbid_fixed "${FORMAL_BLOCK}" "${marker}" \
            "P4-E0-R2Q formal spine contains forbidden non-dependency ordering: ${marker}"
    done
    for marker in \
        'def formalCaseToken = String.format(' \
        "'%02d', formalCaseIndex" \
        'def prepareCaseTaskName = "prepareP4E0R2QCase${formalCaseToken}".toString()' \
        'def runCaseTaskName = "runP4E0R2QCase${formalCaseToken}".toString()' \
        'def verifyCaseTaskName = "verifyP4E0R2QCase${formalCaseToken}".toString()' \
        'def prerequisiteVerifier = previousP4E0R2QFormalVerifier' \
        'p4E0R2QConfiguredFormalPrepareTaskNames.add(prepareCaseTaskName)' \
        'p4E0R2QConfiguredFormalVerifyTaskNames.add(verifyCaseTaskName)' \
        'p4E0R2QConfiguredFormalFailureTaskNames.add(captureFailureTaskName)' \
        "'prepare-case'" \
        'dependsOn(prerequisiteVerifier)' \
        'def runCaseTask = tasks.named(runCaseTaskName, JavaExec)' \
        'timeout.set(java.time.Duration.ofSeconds(p4E0R2QFormalTimeoutSeconds))' \
        'requireP4E0R2QFormalGate()' \
        'finalizedBy(captureFailureTask)' \
        'dependsOn(runCaseTask)' \
        'previousP4E0R2QFormalVerifier = verifyCaseTask'; do
        require_count "${FORMAL_TASK_LOOP_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q direct serial case spine changed: ${marker}"
    done
    require_count "${FORMAL_TASK_LOOP_BLOCK}" 'dependsOn(prepareCaseTask)' 3 \
        'P4-E0-R2Q generated case/run setup dependency inventory changed'
    require_count "${FORMAL_TASK_LOOP_BLOCK}" 'finalizedBy(captureFailureTask)' 1 \
        'P4-E0-R2Q formal run must have exactly one generated parent-failure finalizer'
    require_count "${FORMAL_TASK_LOOP_BLOCK}" "'capture-failure'" 1 \
        'P4-E0-R2Q formal parent-failure command inventory changed'
    require_count "${FORMAL_TASK_LOOP_BLOCK}" \
        'captureP4E0R2QCase${formalCaseToken}ParentFailure' 1 \
        'P4-E0-R2Q formal parent-failure task inventory changed'
    require_count "${FORMAL_TASK_LOOP_BLOCK}" \
        'previousP4E0R2QFormalVerifier = verifyCaseTask' 1 \
        'P4-E0-R2Q formal spine must advance through one generated verifier loop'
    for marker in \
        'p4E0R2QExpectedFormalPrepareTaskNames =' \
        "'prepareP4E0R2QCase%02d'," \
        'p4E0R2QExpectedFormalVerifyTaskNames =' \
        "'verifyP4E0R2QCase%02d'," \
        'p4E0R2QExpectedFormalFailureTaskNames =' \
        "'captureP4E0R2QCase%02dParentFailure'," \
        '!= p4E0R2QExpectedFormalPrepareTaskNames' \
        '!= p4E0R2QExpectedFormalVerifyTaskNames' \
        '!= p4E0R2QExpectedFormalFailureTaskNames' \
        "throw new GradleException('P4-E0-R2Q formal task inventory drift')" \
        'aggregateP4E0R2QFormal.configure {' \
        'dependsOn(previousP4E0R2QFormalVerifier)' \
        'verifyP4E0R2QFormalArtifacts.configure {' \
        'dependsOn(aggregateP4E0R2QFormal)' \
        "tasks.register('p4E0R2QStudy') {" \
        'dependsOn(verifyP4E0R2QFormalArtifacts)'; do
        require_count "${FORMAL_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q full serial spine/inventory changed: ${marker}"
    done
}

verify_source_boundary() {
    local file=''
    local source_count=''
    local game_count=''
    R2Q_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-source.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create source list'
    R2Q_GAME_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-game.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create dedicated list'
    find src/p4E0Research/java -type f -name 'P4E0R2Q*.java' -print0 \
        > "${R2Q_SOURCE_LIST}"
    find src/p4E0ResearchGameTest/java -type f -name 'P4E0R2Q*.java' -print0 \
        > "${R2Q_GAME_LIST}"
    source_count="$(null_record_count "${R2Q_SOURCE_LIST}")"
    game_count="$(null_record_count "${R2Q_GAME_LIST}")"
    [[ "${source_count}" -eq 14 && "${game_count}" -eq 2 ]] \
        || fail "P4-E0-R2Q exact source allowlist changed (${source_count}/${game_count})"
    printf '%s\0' \
        'src/p4E0Research/java/com/yo1no/gramarye/magic/definition/player/P4E0ResearchAttachmentFixtures.java' \
        'src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java' \
        >> "${R2Q_SOURCE_LIST}"
    printf '%s\0' \
        'src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchDedicatedCoordinator.java' \
        >> "${R2Q_GAME_LIST}"
    [[ -s "${R2Q_SOURCE_LIST}" && -s "${R2Q_GAME_LIST}" ]] \
        || fail 'P4-E0-R2Q source lists are empty'
    for list in "${R2Q_SOURCE_LIST}" "${R2Q_GAME_LIST}"; do
        while IFS= read -r -d '' file; do
            for forbidden in \
                'org.junit' 'System.gc(' 'Thread.sleep(' 'Files.readAllBytes' \
                'NbtAccounter.unlimitedHeap' 'java.lang.reflect' \
                'setAccessible(' 'sun.misc.Unsafe' '.reclaim(' \
                'RootIndex' 'Reconciliation' 'CustomPacketPayload' \
                'net.minecraft.client' 'WorldVersion' \
                'SharedConstants.setVersion(' 'setCurrentVersion('; do
                forbid_fixed "${file}" "${forbidden}" \
                    "P4-E0-R2Q source contains forbidden ${forbidden}: ${file}"
            done
            forbid_ere "${file}" \
                'catch[[:space:]]*\([^)]*(OutOfMemoryError|Error)' \
                "P4-E0-R2Q source catches Error/OOME: ${file}"
        done < "${list}"
    done
    for required_case_marker in \
        'p4-e0-r2q-balanced-v0-1536-' \
        'CASE_PREFIX + "exact"' \
        'CASE_PREFIX + "over-"' \
        '"dataversion-missing"' \
        '"dataversion-wrong-type"' \
        '"dataversion-wrong-value"' \
        'CASE_COUNT = 29'; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
            "${required_case_marker}" \
            "P4-E0-R2Q case plan is missing ${required_case_marker}"
    done
    for counter_slug in \
        directory_entries relevant_records \
        compressed_bytes_per_file decompressed_bytes_per_file \
        container_depth_per_file compound_containers_per_file \
        compound_field_entries_per_file list_elements_per_file \
        byte_array_elements_per_file int_array_elements_per_file \
        long_array_elements_per_file modified_utf8_bytes_per_file \
        scalar_tags_per_file compressed_bytes_total decompressed_bytes_total \
        compound_containers_total compound_field_entries_total \
        list_elements_total byte_array_elements_total int_array_elements_total \
        long_array_elements_total modified_utf8_bytes_total scalar_tags_total \
        attachment_admissions raw_root_claims; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QProfile.java \
            "(\"${counter_slug}\")" \
            "P4-E0-R2Q profile is missing counter slug ${counter_slug}"
    done
    for coverage_marker in \
        'for (var counter : P4E0R2QProfile.Counter.values())' \
        "CASE_PREFIX + \"over-\" + counter.slug().replace('_', '-')" \
        '!identifiers.add(spec.caseId())' \
        '!counters.add(target)' \
        'counters.equals(EnumSet.allOf(P4E0R2QProfile.Counter.class))' \
        'dataVersionControls != 3' \
        'spec.expectedDfuInvocations() != 0'; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
            "${coverage_marker}" \
            "P4-E0-R2Q case coverage is missing ${coverage_marker}"
    done
    forbid_fixed \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QCasePlan.java \
        'CASE_PREFIX + "dfu' \
        'P4-E0-R2Q must not define an independent DFU case'
    require_count build.gradle \
        'add(p4E0ResearchGameTestSourceSet.implementationConfigurationName' 7 \
        'P4-E0-R2Q dedicated runtime dependency allowlist changed'
    for forbidden_dependency in \
        'p4E0ResearchGameTestImplementation' \
        'p4E0ResearchGameTestRuntimeOnly' \
        'p4E0ResearchGameTestSourceSet.runtimeOnlyConfigurationName' \
        'testRuntimeClasspath'; do
        forbid_fixed build.gradle "${forbidden_dependency}" \
            "P4-E0-R2Q dedicated runtime contains forbidden dependency seam ${forbidden_dependency}"
    done
    for marker in \
        'P4E0R2QStoreJournalFixtures.buildExact()' \
        'installExactSubmission(server)' \
        'P4D3ProbeSupport.observeLive(server)' \
        '.resolveSibling("p4-e0-r2q")' \
        '.resolve("formal")' \
        'artifactRoot.resolve(artifact), LinkOption.NOFOLLOW_LINKS' \
        'exact_store_journal_root_preflight' \
        'exact_d2_prospective_observed'; do
        require_fixed \
            src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java \
            "${marker}" \
            "P4-E0-R2Q exact preflight/smoke is missing ${marker}"
    done
    forbid_fixed \
        src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java \
        'Files.walk(ownedRoot)' \
        'P4-E0-R2Q smoke must not mistake bounded failed-evidence for official artifacts'
}

verify_formal_model_contract() {
    local result='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalResult.java'
    local evidence='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalEvidence.java'
    local main_source='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalMain.java'
    local smoke_main='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QMain.java'
    local workload='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalWorkload.java'
    local joint_records='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QJointRecords.java'
    local wire='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchWireNbt.java'
    local store_fixtures='src/p4E0Research/java/com/yo1no/gramarye/magic/definition/store/P4E0R2QStoreJournalFixtures.java'
    local contract_test='src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalContractTest.java'
    local gate_test='src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalGateNegativeTest.java'
    local joint_test='src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QJointRecordsTest.java'
    local driver='src/p4E0ResearchGameTest/java/com/yo1no/gramarye/magic/definition/research/P4E0R2QFormalDedicatedDriver.java'
    local marker=''

    PREPARE_CASE_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-prepare-case.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create prepare-case block'
    sed -n '/^    private static void prepareCase(/,/^    private static void preservePrepareFailure(/p' \
        "${main_source}" > "${PREPARE_CASE_BLOCK}"
    [[ -s "${PREPARE_CASE_BLOCK}" ]] \
        || fail 'P4-E0-R2Q common formal prepare-case method is missing'
    require_count "${PREPARE_CASE_BLOCK}" 'SharedConstants.tryDetectVersion();' 1 \
        'P4-E0-R2Q every fresh prepare-case JVM must initialize the detected game version once'
    require_count "${PREPARE_CASE_BLOCK}" 'P4E0R2QFormalWorkload.prepareCase(' 1 \
        'P4-E0-R2Q common prepare-case workload invocation changed'
    require_fixed_before \
        "${PREPARE_CASE_BLOCK}" \
        'SharedConstants.tryDetectVersion();' \
        'P4E0R2QFormalWorkload.prepareCase(' \
        'P4-E0-R2Q game-version initialization must precede every formal fixture materialization'
    for marker in \
        'catch (P4E0R2QFormalEvidence.ResearchGuardException exception)' \
        'catch (IOException exception)' \
        'catch (RuntimeException exception)' \
        'P4E0R2QFormalResult.ProcessClassification.REJECTED_BY_RESEARCH_GUARD' \
        'P4E0R2QFormalResult.ProcessClassification.FIXTURE_INVALID' \
        'P4E0R2QFormalResult.ProcessClassification.INSTRUMENTATION_FAILURE' \
        'preservePrepareFailure('; do
        require_fixed "${PREPARE_CASE_BLOCK}" "${marker}" \
            "P4-E0-R2Q bounded prepare-case failure preservation changed: ${marker}"
    done
    forbid_ere "${PREPARE_CASE_BLOCK}" \
        'catch[[:space:]]*\([^)]*(OutOfMemoryError|Error)' \
        'P4-E0-R2Q prepare-case must not catch Error/OOME'
    require_fixed "${main_source}" \
        'case "prepare-case" -> prepareCase(workRoot, exactCase(arguments));' \
        'P4-E0-R2Q all formal case indices must dispatch through the common initialized prepare path'

    for marker in \
        'case "verify-case04-preparation" -> verifyCase04Preparation(' \
        'case "verify-counter-preparations" -> verifyCounterPreparations(' \
        'CASE_04_PRECHILD_REGRESSION' \
        'ALL_25_COUNTER_PRECHILD_REGRESSION' \
        '"owner_guard_classifications"' \
        '"formal_children_started"' \
        '"official_artifacts_published"' \
        '"complete_strict_wire_fixtures"' \
        '"strict_single_gzip_member"' \
        '"exact_unnamed_compound"' \
        '"decompressed_eof_exact"' \
        '"compressed_eof_exact"' \
        '"max_simultaneous_case_worlds"'; do
        require_fixed "${smoke_main}" "${marker}" \
            "P4-E0-R2Q pre-child result contract changed: ${marker}"
    done
    for marker in \
        'verifyCase04Preparation(Path runRoot)' \
        'verifyAllCounterPreparations(Path runRoot)' \
        'materializeCounter(caseRoot, spec);' \
        'observeFullPhysicalCounter(caseRoot, spec, new HeapTracker())' \
        'executeStrictCounter(caseRoot, spec, new HeapTracker())' \
        'P4E0R2QStoreJournalFixtures.requireStrictPrimaryDataVersion(' \
        'observed.decompressedBytesPerFile() != 268_435_457L' \
        'counter != target && observed.value(counter) > profile.maximum(counter)' \
        'try (var caseCleanup = new RegressionRootCleanup(caseRoot))' \
        'formalChildrenStarted != 0'; do
        require_fixed "${workload}" "${marker}" \
            "P4-E0-R2Q full physical pre-child regression changed: ${marker}"
    done
    forbid_ere "${workload}" \
        'catch[[:space:]]*\([^)]*(OutOfMemoryError|Error)' \
        'P4-E0-R2Q pre-child regression must not catch Error/OOME'

    for marker in \
        'diagnoseDecompressedPerFileConstruction()' \
        'new ConstructionDiagnostic(' \
        'COMPLETE_TARGET_REACHED_AT_MAX_PLUS_ONE' \
        'output.writeUTF("research_pay");' \
        '2_581L, 513, 513, 513, 0, 0, 0, 0, 522, 1' \
        'output.writeUTF("d");' \
        'MAXIMUM_DECOMPRESSED_BYTES + 1L'; do
        require_fixed "${joint_records}" "${marker}" \
            "P4-E0-R2Q case-04 arithmetic/construction proof changed: ${marker}"
    done
    forbid_fixed "${joint_records}" 'output.writeUTF("research_payl");' \
        'P4-E0-R2Q case-04 off-by-one field name returned'
    for marker in \
        'OutputLimitDiagnostic' \
        'countBeforeWrite' \
        'requestedWriteWidth' \
        'measurementCeiling' \
        'projectedCountAfterWrite'; do
        require_fixed "${wire}" "${marker}" \
            "P4-E0-R2Q bounded construction diagnostic changed: ${marker}"
    done
    for marker in \
        'case04IdentityArithmeticAndConstructionCeilingStayExact' \
        'depthNegativeInsertsOneEmptyNamedCompoundWithoutRenamingTheBaselineChain' \
        '268_435_456L, diagnostic.countBeforeWrite()' \
        '1L, diagnostic.requestedWriteWidth()' \
        '268_435_457L, diagnostic.projectedCountAfterWrite()'; do
        require_fixed "${joint_test}" "${marker}" \
            "P4-E0-R2Q case-04 scalar proof is missing: ${marker}"
    done

    for marker in \
        'case "verify-version-init" -> verifyFreshJvmDataVersion(' \
        'var fixture = P4E0R2QStoreJournalFixtures.buildExact();' \
        'fixture.writePrimary(worldRoot, true);' \
        'P4E0R2QStoreJournalFixtures.requireStrictPrimaryDataVersion(worldRoot, expected);' \
        'var expected = P4E0R2QProfile.locked().acceptedDataVersion();' \
        'result.addProperty("tag_type", Tag.TAG_INT);' \
        'result.addProperty("data_version", expected);' \
        'requireFreshJvmDataVersionResult(reportRoot);' \
        'result.get("tag_type").getAsInt() != Tag.TAG_INT' \
        'P4E0R2QProfile.locked().acceptedDataVersion()'; do
        require_fixed "${smoke_main}" "${marker}" \
            "P4-E0-R2Q fresh-JVM DataVersion command changed: ${marker}"
    done
    for marker in \
        'public static void requireStrictPrimaryDataVersion(Path worldRoot, int expected)' \
        'P4E0ResearchGzipAdapter.read(' \
        '.MAX_SKILL_SAVED_DATA_FILE_BYTES' \
        'SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES' \
        'SkillSavedDataPersistenceSchema.FINITE_WHOLE_ROOT_NBT_QUOTA' \
        'SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD' \
        'dataVersion instanceof IntTag version' \
        'version.getAsInt() != expected'; do
        require_fixed "${store_fixtures}" "${marker}" \
            "P4-E0-R2Q bounded strict primary DataVersion read changed: ${marker}"
    done
    forbid_fixed "${store_fixtures}" 'NbtAccounter.unlimitedHeap' \
        'P4-E0-R2Q fresh-JVM strict read must remain finitely accounted'
    for marker in \
        'everyFreshPrepareCaseInitializesTheDetectedVersionInsideFailurePreservation' \
        'freshJvmRegressionUsesTheActualBoundedPrimaryWriterAndStrictIntDataVersion' \
        'freshJvmCounterPreparationUsesTheFormalFixturePathWithoutStartingAChild' \
        'assertEquals(3_955, P4E0R2QProfile.locked().acceptedDataVersion())'; do
        require_fixed "${contract_test}" "${marker}" \
            "P4-E0-R2Q fresh-JVM contract test is missing ${marker}"
    done
    require_fixed "${gate_test}" \
        'prepareCaseFailureIsBoundedlyArchivedBeforeAnyChildOrOfficialArtifact' \
        'P4-E0-R2Q bounded prepare-case archive regression is missing'
    for marker in \
        'measurementPrepareFailureUsesDistinctBoundedParentEvidence' \
        'runtimePrepareFailureUsesDistinctBoundedParentEvidence' \
        'prepareArchiveCollisionIsSuppressedWithoutReplacingPrimaryFailure'; do
        require_fixed "${gate_test}" "${marker}" \
            "P4-E0-R2Q prepare-failure regression is missing: ${marker}"
    done

    OFFICIAL_CLASSIFICATION_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-official-classification.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create official-classification block'
    sed -n '/^    enum OfficialOutputClassification {$/,/^    }$/p' \
        "${evidence}" > "${OFFICIAL_CLASSIFICATION_BLOCK}"
    [[ -s "${OFFICIAL_CLASSIFICATION_BLOCK}" ]] \
        || fail 'P4-E0-R2Q official-output classification is missing'
    require_ere_count "${OFFICIAL_CLASSIFICATION_BLOCK}" \
        '^[[:space:]]+(ABSENT|EMPTY_OR_METADATA_ONLY|VALID_OFFICIAL_SET|MALFORMED_NONEMPTY_OUTPUT)[,]?$' \
        4 'P4-E0-R2Q official-output classification must contain exactly four values'
    for marker in \
        'ABSENT,' \
        'EMPTY_OR_METADATA_ONLY,' \
        'VALID_OFFICIAL_SET,' \
        'MALFORMED_NONEMPTY_OUTPUT'; do
        require_count "${OFFICIAL_CLASSIFICATION_BLOCK}" "${marker}" 1 \
            "P4-E0-R2Q official-output classification changed: ${marker}"
    done

    for marker in \
        'MAXIMUM_JSON_BYTES = 65_536' \
        'enum ProcessClassification {' \
        'COMPLETED,' \
        'REJECTED_BY_RESEARCH_GUARD,' \
        'FIXTURE_INVALID,' \
        'INSTRUMENTATION_FAILURE,' \
        'CHILD_EXIT_FAILURE,' \
        'TIMEOUT,' \
        'OOME_EXIT' \
        'enum QualificationResult {' \
        'ADMITTED_EXACT,' \
        'REJECTED_EXPECTED_COUNTER,' \
        'REJECTED_EXPECTED_DATA_VERSION,' \
        'NOT_OBSERVED' \
        '"counter_values"' \
        '"targets_audited"' \
        '"semantic_checksum"' \
        'observedAtLeast != Math.addExact(maximum, 1L)' \
        'attachmentAdmissions != 1_024L' \
        'rawRootClaims != 65_536L' \
        'targetsAudited != 65_536L' \
        'heap.xms() != 512L * 1_024L * 1_024L' \
        'heap.xmx() != 1_536L * 1_024L * 1_024L'; do
        require_fixed "${result}" "${marker}" \
            "P4-E0-R2Q formal result contract is missing ${marker}"
    done
    require_ere_count "${result}" \
        '^[[:space:]]+(COMPLETED|REJECTED_BY_RESEARCH_GUARD|FIXTURE_INVALID|INSTRUMENTATION_FAILURE|CHILD_EXIT_FAILURE|TIMEOUT|OOME_EXIT)[,]?$' \
        7 'P4-E0-R2Q process taxonomy must have exactly seven values'
    require_ere_count "${result}" \
        '^[[:space:]]+(ADMITTED_EXACT|REJECTED_EXPECTED_COUNTER|REJECTED_EXPECTED_DATA_VERSION|NOT_OBSERVED)[,]?$' \
        4 'P4-E0-R2Q qualification taxonomy must have exactly four values'
    for marker in 'java.nio.file.Path' 'Throwable ' 'getMessage(' 'getStackTrace('; do
        forbid_fixed "${result}" "${marker}" \
            "P4-E0-R2Q formal result contains forbidden raw diagnostic seam ${marker}"
    done

    for marker in \
        'FORMAL_DISK_BUDGET_BYTES = 12_884_901_888L' \
        'FORMAL_HEAP_MIB = 1_536' \
        'LOCKED_PROFILE_HASH =' \
        '"6a6f4541f4c23b9aefad465eb29ec0420d3a4f635f06b528ca07239a93f99418"' \
        'LOCKED_CASE_PLAN_HASH =' \
        '"23408739f292d2a5696c56c39b8b4b3978b3840af383930293efbe6b824f5035"' \
        'P4E0R2QProfile.manifestHash().equals(LOCKED_PROFILE_HASH)' \
        'P4E0R2QCasePlan.standard().planHash().equals(LOCKED_CASE_PLAN_HASH)' \
        'RUNS_FILE = "runs.jsonl"' \
        'PROFILE_FILE = "r2q-profile.json"' \
        'CASE_PLAN_FILE = "r2q-case-plan.json"' \
        'SUMMARY_FILE = "summary.md"' \
        'PROVENANCE_FILE = "PROVENANCE.txt"' \
        'CHECKSUMS_FILE = "SHA256SUMS.txt"' \
        'MACOS_METADATA_FILE = ".DS_Store"' \
        'inspectOfficialOutput(Path officialRoot)' \
        'removeEmptyOrMetadataOnlyOfficial(' \
        'repository.resolve("build/reports/p4-e0-r2q").normalize()' \
        'names.equals(Set.of(MACOS_METADATA_FILE))' \
        'hasExactOfficialFileShape(official)' \
        'Optional.of(readOfficialControl(official))' \
        'if (!hasExactOfficialFileShape(official))' \
        'var control = readProvenanceControl(official)' \
        'requireOfficialDirectory(official, control)' \
        '!readProvenanceControl(directory).equals(previous)' \
        'formal metadata-only output changed before cleanup' \
        'Files.delete(metadata)' \
        'Files.delete(official)' \
        'official R2Q evidence appeared before publication' \
        'results.size() != P4E0R2QCasePlan.CASE_COUNT' \
        'PublicationMover mover' \
        'mover.move(staging, official)' \
        'StandardCopyOption.ATOMIC_MOVE' \
        'catch (AtomicMoveNotSupportedException exception)' \
        'STALE_PROVENANCE.txt' \
        'OFFICIAL_SHA256SUMS.txt' \
        'preserveFailed(' \
        'FAILURE.txt' \
        'EXPLORATORY_NON_NORMATIVE' \
        'REJECTED_EXPECTED_COUNTER: 25' \
        'REJECTED_EXPECTED_DATA_VERSION: 3'; do
        require_fixed "${evidence}" "${marker}" \
            "P4-E0-R2Q formal evidence contract is missing ${marker}"
    done
    forbid_fixed "${evidence}" 'StandardCopyOption.REPLACE_EXISTING' \
        'P4-E0-R2Q formal evidence must never replace an artifact or archive'
    forbid_fixed "${evidence}" 'Files.move(source, target)' \
        'P4-E0-R2Q formal publication must not fall back to a non-atomic move'
    forbid_fixed "${evidence}" 'Files.isHidden(' \
        'P4-E0-R2Q formal evidence must not ignore arbitrary hidden files'
    forbid_ere "${evidence}" 'deleteTree[[:space:]]*\([[:space:]]*official(Root)?[[:space:]]*\)' \
        'P4-E0-R2Q metadata cleanup must not recursively delete the official root'

    for marker in \
        'var staging = parent.resolve(".p4-e0-r2q-" + control.studyId() + ".staging")' \
        'var target = safeRoot(staleRoot).resolve(identity)' \
        'var staging = target.getParent().resolve("." + identity + ".staging")' \
        'atomicMove(official, staging)' \
        'staging.resolve("STALE_PROVENANCE.txt")' \
        'requireStaleDirectory(staging, previous, identity)' \
        'requireNoFormalStaging(' \
        'name.startsWith(".p4-e0-r2q-") && name.endsWith(".staging")' \
        'var target = safeRoot(failedRoot).resolve(control.studyId())' \
        'var staging = target.getParent().resolve("." + control.studyId() + ".staging")' \
        'staging.resolve("study-control.json")' \
        'control.toJsonLine()' \
        'preserveManifestIfPresent(sourceCase, targetCase, control, index)' \
        'preserveResultIfPresent(sourceCase, targetCase, control, index, "child-result.json")' \
        'preserveResultIfPresent(' \
        'sourceCase, targetCase, control, index, "prepare-failure.json")' \
        '"case-manifest.json"' \
        '"child-result.json"' \
        '"verified-result.json"' \
        '"prepare-failure.json"' \
        '"running.marker"' \
        '"exit-code.txt"' \
        '"timeout.marker"' \
        '"parent-deadline.marker"' \
        '"FAILURE.txt"'; do
        require_fixed "${evidence}" "${marker}" \
            "P4-E0-R2Q stale/failed evidence topology changed: ${marker}"
    done
    for marker in \
        'static void preservePrepareFailureBoundedly(' \
        'P4E0R2QFormalWorkload.PREPARE_FAILURE' \
        'primaryFailure.getClass().getName()' \
        'catch (IOException | RuntimeException archiveFailure)' \
        'primaryFailure.addSuppressed(archiveFailure)'; do
        require_fixed "${main_source}" "${marker}" \
            "P4-E0-R2Q bounded prepare-failure preservation changed: ${marker}"
    done
    forbid_ere "${main_source}" \
        'catch[[:space:]]*\([^)]*(OutOfMemoryError|Error)' \
        'P4-E0-R2Q parent failure preservation must not catch Error/OOME'
    require_count "${evidence}" 'atomicMove(staging, target)' 2 \
        'P4-E0-R2Q stale and failed archives must each publish by one atomic staging move'
    forbid_fixed "${evidence}" 'atomicMove(official, target)' \
        'P4-E0-R2Q stale archive must not publish before bounded metadata is installed'
    require_fixed "${main_source}" 'workRoot.resolveSibling("stale-evidence")' \
        'P4-E0-R2Q stale evidence root topology changed'
    require_fixed "${main_source}" 'workRoot.resolveSibling("failed-evidence")' \
        'P4-E0-R2Q failed evidence root topology changed'
    for marker in \
        'P4E0R2QFormalEvidence.requireNoFormalStaging(' \
        'failedRoot.resolve(control.studyId())' \
        'staleRoot.resolve(control.studyId())' \
        'staleRoot.resolve(control.gitHead())' \
        'previous.studyId().equals(control.studyId())' \
        'previous.gitHead().equals(control.gitHead())' \
        'formal study identity cannot be reused'; do
        require_fixed "${main_source}" "${marker}" \
            "P4-E0-R2Q study/stale identity gate changed: ${marker}"
    done

    for marker in \
        'requirePairwiseDisjointRoots(workRoot, officialRoot, smokeRoot)' \
        'left.equals(right) || left.startsWith(right) || right.startsWith(left)' \
        'case ABSENT -> {' \
        'case EMPTY_OR_METADATA_ONLY ->' \
        'case VALID_OFFICIAL_SET -> {' \
        'case MALFORMED_NONEMPTY_OUTPUT -> throw new IOException(' \
        'malformed nonempty formal output is preserved' \
        'official formal publication root must be absent' \
        'removeGeneratedSkeleton(repository, workRoot)' \
        'formal skeleton cleanup is outside its owned build root' \
        'name.equals(P4E0R2QFormalEvidence.MACOS_METADATA_FILE)' \
        'formal skeleton metadata is not a regular file' \
        'formal skeleton contains unknown state' \
        'classifyParentEvidence(' \
        'readExitCode(' \
        'marker(' \
        'parentTimedOut' \
        'CHILD_EXIT_FAILURE' \
        'TIMEOUT' \
        'OOME_EXIT' \
        'COMPLETED_NON_FORMAL_RUNNER_SMOKE' \
        'formal_children_started' \
        'official_artifacts_published'; do
        require_fixed "${main_source}" "${marker}" \
            "P4-E0-R2Q formal supervisor contract is missing ${marker}"
    done
    require_count "${driver}" 'Runtime.getRuntime().halt(' 1 \
        'P4-E0-R2Q dedicated watchdog must have exactly one hard-halt site'
    require_fixed "${driver}" 'WATCHDOG_SECONDS = 870L' \
        'P4-E0-R2Q dedicated watchdog lost its exact timeout'

    for marker in \
        'officialOutputClassifiesAbsentEmptyAndExactMetadataWithoutGuessing' \
        'metadataOnlyCleanupRequiresTheExactResearchOwnedBuildRoot' \
        'partialUnknownHiddenAndSymlinkOfficialOutputIsMalformedAndPreserved' \
        'exactSixFileOfficialSetIsValidAndOnlyThenArchived' \
        'atomicPublicationRequiresTheOfficialRootToRemainAbsent'; do
        require_fixed \
            src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalEvidenceTest.java \
            "${marker}" \
            "P4-E0-R2Q official-output test matrix is missing ${marker}"
    done
    for marker in \
        'formalAndSmokeOutputsArePhysicallyDisjoint' \
        'normalVerificationEntriesDoNotDependOnFormalStudyTasks' \
        'formalOfficialSmokeAndWorkRootsRejectEqualityAndBothAncestorDirections' \
        'generatedSkeletonAcceptsOnlyExactCasesGamesAndExactMetadata' \
        'generatedSkeletonRejectsResultMarkerUnknownAndSymlinkWithoutPartialDeletion'; do
        require_fixed \
            src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalContractTest.java \
            "${marker}" \
            "P4-E0-R2Q formal-root contract test matrix is missing ${marker}"
    done
    require_fixed \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalGateNegativeTest.java \
        'cleanPrepareAcceptsAbsentEmptyAndExactMetadataOnlyOfficialRoots' \
        'P4-E0-R2Q clean absent/empty/metadata preflight matrix is missing'
    require_fixed \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalGateNegativeTest.java \
        'malformedLegacySmokeOutputIsPreservedBeforeCaseZero' \
        'P4-E0-R2Q malformed preflight negative control is missing'
    require_fixed \
        src/test/java/com/yo1no/gramarye/magic/definition/research/P4E0ResearchR2QFormalGateNegativeTest.java \
        'partialOfficialSetIsPreservedBeforeCaseZero' \
        'P4-E0-R2Q partial official-set preflight negative control is missing'
}

verify_jar_isolation() {
    local jar_path=''
    local source_path=''
    local class_path=''
    local status=0
    JAR_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-jars.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create JAR list'
    JAR_CONTENTS="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e0-r2q-jar.XXXXXX")" \
        || fail 'P4-E0-R2Q verifier could not create JAR listing'
    find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_LIST}" || status=$?
    [[ "${status}" -eq 0 && -s "${JAR_LIST}" ]] \
        || fail 'P4-E0-R2Q verifier found no production JAR'
    while IFS= read -r -d '' jar_path; do
        status=0
        jar tf "${jar_path}" > "${JAR_CONTENTS}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed for ${jar_path}"
        while IFS= read -r -d '' source_path; do
            class_path="${source_path#src/p4E0Research/java/}"
            class_path="${class_path%.java}.class"
            forbid_fixed "${JAR_CONTENTS}" "${class_path}" \
                "P4-E0-R2Q class leaked into production JAR: ${class_path}"
        done < "${R2Q_SOURCE_LIST}"
        while IFS= read -r -d '' source_path; do
            class_path="${source_path#src/p4E0ResearchGameTest/java/}"
            class_path="${class_path%.java}.class"
            forbid_fixed "${JAR_CONTENTS}" "${class_path}" \
                "P4-E0-R2Q dedicated class leaked into production JAR: ${class_path}"
        done < "${R2Q_GAME_LIST}"
        forbid_fixed "${JAR_CONTENTS}" 'P4E0R2Q' \
            'P4-E0-R2Q nested/orphan class leaked into production JAR'
        forbid_fixed "${JAR_CONTENTS}" 'P4E0Research' \
            'P4-E0 research nested/orphan class leaked into production JAR'
        forbid_fixed "${JAR_CONTENTS}" 'p4-e0-r2q-profile-v0.json' \
            'P4-E0-R2Q profile resource leaked into production JAR'
    done < "${JAR_LIST}"
}

main() {
    verify_helpers
    require_regular_file scripts/verify-p4-e0-r2q-configuration.sh \
        'P4-E0-R2Q verifier is missing'
    bash -n scripts/verify-p4-e0-r2q-configuration.sh \
        || fail 'P4-E0-R2Q verifier failed bash -n'
    [[ -x scripts/verify-p4-e0-r2q-configuration.sh ]] \
        || fail 'P4-E0-R2Q verifier mode must be executable'
    verify_paths_and_boundaries
    verify_profile_manifest
    verify_case_plan_contract
    verify_build_and_formal_gate
    verify_source_boundary
    verify_formal_model_contract
    verify_jar_isolation
    printf '%s\n' \
        'Verified P4-E0-R2Q profile, smoke tasks, formal fail-closed entry, boundaries, and JAR isolation.'
}

main "$@"
