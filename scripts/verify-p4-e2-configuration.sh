#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${JAVA_HOME:-}" ]]; then
    PATH="${JAVA_HOME}/bin:/usr/bin:/bin:/usr/sbin:/sbin"
else
    PATH='/usr/bin:/bin:/usr/sbin:/sbin'
fi
export PATH

fail() {
    printf '%s\n' "P4-E2 configuration verification failed: $*" >&2
    exit 1
}

for required_tool in bash grep find git jar mktemp rm dirname pwd awk; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "required baseline tool is unavailable: ${required_tool}"
done

REPOSITORY_ROOT=''
if REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'could not resolve the repository root'
fi
cd "${REPOSITORY_ROOT}"

MAIN_JAVA="${REPOSITORY_ROOT}/src/main/java"
ROOT_PACKAGE="${MAIN_JAVA}/com/yo1no/gramarye"
STORE_ROOT="${MAIN_JAVA}/com/yo1no/gramarye/magic/definition/store"
PLAYER_ROOT="${MAIN_JAVA}/com/yo1no/gramarye/magic/definition/player"
SUBMISSION_ROOT="${MAIN_JAVA}/com/yo1no/gramarye/magic/definition/submission"
TEST_STORE_ROOT="${REPOSITORY_ROOT}/src/test/java/com/yo1no/gramarye/magic/definition/store"
TEST_PLAYER_ROOT="${REPOSITORY_ROOT}/src/test/java/com/yo1no/gramarye/magic/definition/player"
STORE_SERVICE="${STORE_ROOT}/SkillDefinitionStoreService.java"
AUDIT_SERVICE="${STORE_ROOT}/SkillRetentionRootAuditService.java"
PLAYER_SERVICE="${PLAYER_ROOT}/PlayerSkillAttachmentService.java"
MANA_ATTACHMENTS="${MAIN_JAVA}/com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java"
MANA_GAME_TESTS="${MAIN_JAVA}/com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java"
RECOVERY_SERVICE="${SUBMISSION_ROOT}/SkillSubmissionRecoveryService.java"
COORDINATOR="${STORE_ROOT}/P4E2OnlineReconciliationCoordinator.java"
FACADE="${ROOT_PACKAGE}/P4E2QualificationFacade.java"
GRAMARYE="${ROOT_PACKAGE}/Gramarye.java"
TEST_ROOT="${REPOSITORY_ROOT}/src/test/java/com/yo1no/gramarye"
P4C2_ADAPTER="${REPOSITORY_ROOT}/src/p4C2GameTest/java/com/yo1no/gramarye/P4E2QualificationFacadeTestAccess.java"
P4C2_OBSERVATION="${REPOSITORY_ROOT}/src/p4C2Probe/java/com/yo1no/gramarye/P4E2QualificationObservation.java"
P4C2_MEMORY_GAMETEST="${REPOSITORY_ROOT}/src/p4C2GameTest/java/com/yo1no/gramarye/magic/definition/player/P4C2MemoryGameTests.java"
P4C2_FILE_VERIFIER="${REPOSITORY_ROOT}/src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FileVerifier.java"

PRODUCTION_SOURCE_LIST=''
E2_SOURCE_LIST=''
E2_LEGACY_SLICE=''
E2_CROSSING_SLICE=''
E2_PREWARM_SLICE=''
E2_RECOVERY_OBSERVATION_SLICE=''
E2_RESULT_OBSERVATION_SLICE=''
JAR_FILE_LIST=''
JAR_LISTING=''
SELF_TEST_ROOT=''

cleanup() {
    local temporary=''
    for temporary in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${E2_SOURCE_LIST}" \
        "${E2_LEGACY_SLICE}" \
        "${E2_CROSSING_SLICE}" \
        "${E2_PREWARM_SLICE}" \
        "${E2_RECOVERY_OBSERVATION_SLICE}" \
        "${E2_RESULT_OBSERVATION_SLICE}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}"; do
        if [[ -n "${temporary}" ]]; then
            rm -f -- "${temporary}"
        fi
    done
    if [[ -n "${SELF_TEST_ROOT}" ]]; then
        rm -rf -- "${SELF_TEST_ROOT}"
    fi
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

require_fixed_count() {
    local file="$1"
    local needle="$2"
    local expected="$3"
    local message="$4"
    local count=0
    local status=0
    count="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) count=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${count}" -eq "${expected}" ]] \
        || fail "${message}: expected ${expected}, found ${count}"
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
    local expression="$2"
    local message="$3"
    local status=0
    LC_ALL=C grep -Eq -- "${expression}" "${file}" || status=$?
    case "${status}" in
        0) fail "${message}" ;;
        1) return 0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
}

require_regular_file() {
    local file="$1"
    local message="$2"
    [[ -f "${file}" && ! -L "${file}" ]] || fail "${message}"
}

count_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local total=0
    local count=0
    local status=0
    local file=''
    while IFS= read -r -d '' file; do
        status=0
        count="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
        case "${status}" in
            0) ;;
            1) count=0 ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
        total=$((total + count))
    done < "${file_list}"
    printf '%s\n' "${total}"
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

require_only_owner() {
    local needle="$1"
    local expected_owner="$2"
    local expected_count="$3"
    local message="$4"
    local file=''
    local total=0
    local count=0
    local status=0
    while IFS= read -r -d '' file; do
        status=0
        count="$(LC_ALL=C grep -Fc -- "${needle}" "${file}")" || status=$?
        case "${status}" in
            0) ;;
            1) count=0 ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
        if [[ "${count}" -gt 0 && "${file}" != "${expected_owner}" ]]; then
            fail "${message}: unexpected owner ${file}"
        fi
        total=$((total + count))
    done < "${PRODUCTION_SOURCE_LIST}"
    [[ "${total}" -eq "${expected_count}" ]] \
        || fail "${message}: expected ${expected_count}, found ${total}"
}

line_of_fixed() {
    local file="$1"
    local needle="$2"
    local match=''
    local status=0
    match="$(LC_ALL=C grep -Fn -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) fail "required ordering marker is absent from ${file}: ${needle}" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    match="${match%%$'\n'*}"
    printf '%s\n' "${match%%:*}"
}

last_line_of_fixed() {
    local file="$1"
    local needle="$2"
    local match=''
    local status=0
    match="$(LC_ALL=C grep -Fn -- "${needle}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) fail "required ordering marker is absent from ${file}: ${needle}" ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    match="${match##*$'\n'}"
    printf '%s\n' "${match%%:*}"
}

append_exclusive_slice() {
    local file="$1"
    local start_marker="$2"
    local end_marker="$3"
    local destination="$4"
    local label="$5"
    require_fixed_count "${file}" "${start_marker}" 1 \
        "${label} start marker must be exact"
    require_fixed_count "${file}" "${end_marker}" 1 \
        "${label} end marker must be exact"
    LC_ALL=C awk -v start="${start_marker}" -v finish="${end_marker}" '
        index($0, start) { active = 1; starts++ }
        active && index($0, finish) { finishes++; active = 0; next }
        active { print }
        END {
            if (starts != 1 || finishes != 1 || active) {
                exit 3
            }
        }
    ' "${file}" >> "${destination}" \
        || fail "could not extract exact ${label} source slice"
    printf '\n' >> "${destination}"
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

is_approved_p6_s2_r3_changed_path() {
    case "$1" in
        docs/architecture/P5-A-server-runtime-event-kernel.md | \
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d2-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        scripts/verify-p4-e3-configuration.sh | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachments.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachmentDefinitionBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/runtime/mana/ManaBoundaryTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_allowed_changed_path() {
    is_approved_p4e3_changed_path "$1" && return 0
    is_approved_p6_s2_r3_changed_path "$1" && return 0
    case "$1" in
        scripts/verify-p4-c2-a-configuration.sh | \
        scripts/verify-p4-c2-b-configuration.sh | \
        scripts/verify-p4-d1-configuration.sh | \
        scripts/verify-p4-d2-configuration.sh | \
        scripts/verify-p4-d3-a-configuration.sh | \
        scripts/verify-p4-d3-configuration.sh | \
        scripts/verify-p4-e0-r-configuration.sh | \
        scripts/verify-p4-e0-r2q-configuration.sh | \
        scripts/verify-p4-e1-configuration.sh | \
        scripts/verify-p4-e2-configuration.sh | \
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2ReconciliationResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java | \
        src/p4C2GameTest/java/com/yo1no/gramarye/P4E2QualificationFacadeTestAccess.java | \
        src/p4C2GameTest/java/com/yo1no/gramarye/magic/definition/player/P4C2MemoryGameTests.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/P4E2QualificationObservation.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FileVerifier.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/player/P4C2FixtureBuilder.java | \
        src/p4C2Probe/java/com/yo1no/gramarye/magic/definition/store/P4C2StoreProbe.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationFacadeVisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/P4E2QualificationObservationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/P4C2FixtureTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/player/P4E2AtomicReconciliationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2BPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4C2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D1ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4D3PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4DPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1AApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BCompleteHandoffTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1B2BIndexLifecycleTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E1BApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidationTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2PhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2VisibilityCompileTest.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/store/P4EPhaseTypes.java | \
        src/test/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryServiceTest.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

verify_changed_paths() {
    local temporary=''
    local tracked=''
    local untracked=''
    local path=''
    local candidate=''
    local staged_status=0
    temporary="$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e2-paths.XXXXXX")"
    tracked="${temporary}/tracked.bin"
    untracked="${temporary}/untracked.bin"

    git diff --cached --quiet --no-ext-diff -- || staged_status=$?
    case "${staged_status}" in
        0) ;;
        1) fail 'the P4-E2 verifier requires an empty staged diff' ;;
        *) fail 'git failed while checking the staged diff' ;;
    esac
    git diff --name-only --no-renames --no-ext-diff -z HEAD -- > "${tracked}" \
        || fail 'git failed while reading tracked P4-E2 changes'
    git ls-files --others --exclude-standard -z -- > "${untracked}" \
        || fail 'git failed while reading untracked P4-E2 changes'

    for inventory in "${tracked}" "${untracked}"; do
        while IFS= read -r -d '' path; do
            is_allowed_changed_path "${path}" \
                || fail "changed path is outside the exact P4-E2 allowlist: ${path}"
            candidate="${REPOSITORY_ROOT}/${path}"
            [[ -f "${candidate}" && ! -L "${candidate}" ]] \
                || fail "allowed P4-E2 path is absent, non-regular, or a symlink: ${path}"
            if [[ "${path}" == scripts/*.sh && ! -x "${candidate}" ]]; then
                fail "allowed P4-E2 verifier is not executable: ${path}"
            fi
        done < "${inventory}"
    done
    rm -rf -- "${temporary}"
}

self_regression() {
    SELF_TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/gramarye-p4-e2-self.XXXXXX")"
    printf '%s\n' 'present-marker' > "${SELF_TEST_ROOT}/present.txt"
    require_fixed "${SELF_TEST_ROOT}/present.txt" 'present-marker' \
        'self-test rejected a present marker'
    forbid_fixed "${SELF_TEST_ROOT}/present.txt" 'absent-marker' \
        'self-test accepted an absent forbidden marker'
    printf '%s\n' before BEGIN inside END after > "${SELF_TEST_ROOT}/slice-source.txt"
    : > "${SELF_TEST_ROOT}/slice-output.txt"
    append_exclusive_slice \
        "${SELF_TEST_ROOT}/slice-source.txt" BEGIN END \
        "${SELF_TEST_ROOT}/slice-output.txt" 'self-test'
    require_fixed "${SELF_TEST_ROOT}/slice-output.txt" inside \
        'self-test lost content inside an exact slice'
    forbid_fixed "${SELF_TEST_ROOT}/slice-output.txt" before \
        'self-test included content before an exact slice'
    forbid_fixed "${SELF_TEST_ROOT}/slice-output.txt" END \
        'self-test included the exclusive end marker'
    forbid_fixed "${SELF_TEST_ROOT}/slice-output.txt" after \
        'self-test included content after an exact slice'

    is_allowed_changed_path \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.java' \
        || fail 'self-test rejected an exact P4-E2 production path'
    is_allowed_changed_path \
        'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2ApiGateTest.java' \
        || fail 'self-test rejected an exact P4-E2 Gate path'
    for approved in \
        'build.gradle' \
        '.github/workflows/build.yml' \
        'scripts/verify-p4-e3-configuration.sh' \
        'src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3ProbeMain.java' \
        'src/test/java/com/yo1no/gramarye/magic/definition/store/P4B2BApiGateTest.java' \
        'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2LifecycleOrderingTest.java' \
        'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E3ApiGateTest.java'; do
        is_allowed_changed_path "${approved}" \
            || fail "self-test rejected an exact approved P4-E3 path: ${approved}"
    done
    for rejected in \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2Unexpected.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/AuditUnexpected.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/ReconciliationUnexpected.java' \
        'src/test/java/com/yo1no/gramarye/magic/definition/store/P4E2UnexpectedTest.java' \
        'build.gradle.extra' \
        'src/p4E3Probe/java/com/yo1no/gramarye/magic/definition/store/P4E3Unexpected.java' \
        '.github/workflows/p4-e2.yml'; do
        if is_allowed_changed_path "${rejected}"; then
            fail "self-test accepted a prefix or phase escape path: ${rejected}"
        fi
    done
    rm -rf -- "${SELF_TEST_ROOT}"
    SELF_TEST_ROOT=''
}

self_regression
verify_changed_paths

PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-production.XXXXXX")"
E2_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-exact.XXXXXX")"
E2_LEGACY_SLICE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-legacy-slice.XXXXXX")"
E2_CROSSING_SLICE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-crossing.XXXXXX")"
E2_PREWARM_SLICE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-prewarm.XXXXXX")"
E2_RECOVERY_OBSERVATION_SLICE="$(mktemp \
    "${TMPDIR:-/tmp}/gramarye-p4-e2-recovery-observation.XXXXXX")"
E2_RESULT_OBSERVATION_SLICE="$(mktemp \
    "${TMPDIR:-/tmp}/gramarye-p4-e2-result-observation.XXXXXX")"
find "${MAIN_JAVA}" -type f -name '*.java' -print0 > "${PRODUCTION_SOURCE_LIST}" \
    || fail 'could not enumerate production Java sources'
[[ -s "${PRODUCTION_SOURCE_LIST}" ]] || fail 'production Java inventory is empty'

facade_owner_count=0
while IFS= read -r -d '' source; do
    facade_status=0
    LC_ALL=C grep -Fq -- 'P4E2QualificationFacade' "${source}" || facade_status=$?
    case "${facade_status}" in
        0)
            case "${source}" in
                "${FACADE}" | \
                "${GRAMARYE}" | \
                "${PLAYER_SERVICE}" | \
                "${STORE_SERVICE}" | \
                "${AUDIT_SERVICE}" | \
                "${COORDINATOR}" | \
                "${STORE_ROOT}/PlayerSkillAttachmentReconciliationCapability.java" | \
                "${STORE_ROOT}/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java" | \
                "${RECOVERY_SERVICE}") ;;
                *) fail "qualification facade escaped the exact eight-path production allowlist: ${source}" ;;
            esac
            facade_owner_count=$((facade_owner_count + 1))
            ;;
        1) ;;
        *) grep_failed "${source}" "${facade_status}" ;;
    esac
done < "${PRODUCTION_SOURCE_LIST}"
[[ "${facade_owner_count}" -eq 9 ]] \
    || fail "qualification facade production owner inventory changed (${facade_owner_count}/9)"

for source in \
    "${FACADE}" \
    "${STORE_ROOT}/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java" \
    "${STORE_ROOT}/P4E2GroupedStoreValidation.java" \
    "${STORE_ROOT}/P4E2OnlineReconciliationCoordinator.java" \
    "${STORE_ROOT}/P4E2OnlineReconciliationDependency.java" \
    "${STORE_ROOT}/P4E2ReconciliationResult.java" \
    "${STORE_ROOT}/PlayerSkillAttachmentReconciliationCapability.java"; do
    require_regular_file "${source}" "exact P4-E2 production source is absent: ${source}"
    printf '%s\0' "${source}" >> "${E2_SOURCE_LIST}"
done

for test_source in \
    "${TEST_ROOT}/P4E2QualificationFacadeTest.java" \
    "${TEST_ROOT}/P4E2QualificationFacadeVisibilityCompileTest.java" \
    "${TEST_ROOT}/P4E2QualificationObservationTest.java" \
    "${TEST_STORE_ROOT}/P4E2ApiGateTest.java" \
    "${TEST_PLAYER_ROOT}/P4E2AtomicReconciliationTest.java" \
    "${TEST_STORE_ROOT}/P4E2GroupedStoreValidationTest.java" \
    "${TEST_STORE_ROOT}/P4E2LifecycleOrderingTest.java" \
    "${TEST_STORE_ROOT}/P4E2PhaseTypes.java" \
    "${TEST_STORE_ROOT}/P4E2VisibilityCompileTest.java"; do
    require_regular_file "${test_source}" "required exact P4-E2 test is absent: ${test_source}"
done
require_regular_file "${P4C2_ADAPTER}" \
    'required exact P4-C2 qualification adapter is absent'
require_regular_file "${P4C2_OBSERVATION}" \
    'required exact P4-C2 direct-observation transport is absent'

require_fixed_count "${TEST_STORE_ROOT}/P4E2VisibilityCompileTest.java" \
    '"-proc:none"' 1 \
    'the E2 visibility probe must disable unrelated annotation processors exactly once'
proc_none_line="$(line_of_fixed \
    "${TEST_STORE_ROOT}/P4E2VisibilityCompileTest.java" '"-proc:none"')"
compiler_task_line="$(line_of_fixed \
    "${TEST_STORE_ROOT}/P4E2VisibilityCompileTest.java" 'compiler.getTask(')"
[[ "${proc_none_line}" -lt "${compiler_task_line}" ]] \
    || fail 'annotation processing must be disabled before every E2 visibility probe compile'
require_fixed_count "${TEST_ROOT}/P4E2QualificationFacadeVisibilityCompileTest.java" \
    '"-proc:none"' 1 \
    'the facade visibility probe must disable unrelated annotation processors exactly once'

# Freeze the six-argument primitive crossing independently of the private coordinator status.
# Only the coordinator may allocate that implementation detail after early invalidation.
append_exclusive_slice \
    "${STORE_ROOT}/P4E2OnlineReconciliationDependency.java" \
    '    void reconcileAfterRecovery(' \
    '    /** Exhaustive projection of the existing sealed P4-D outcome hierarchy. */' \
    "${E2_CROSSING_SLICE}" 'public primitive recovery crossing'
append_exclusive_slice \
    "${COORDINATOR}" \
    '    public void reconcileAfterRecovery(' \
    '    P4E2ReconciliationResult reconcile(' \
    "${E2_CROSSING_SLICE}" 'coordinator dependency implementation'
append_exclusive_slice \
    "${COORDINATOR}" \
    '    P4E2ReconciliationResult reconcile(' \
    '    private P4E2ReconciliationResult reconcileEligible(' \
    "${E2_CROSSING_SLICE}" 'package-owned coordinator entry point'
append_exclusive_slice \
    "${RECOVERY_SERVICE}" \
    '    private static void requireE2RecoveryVocabularyInitialized() {' \
    '    private static P4E2OnlineReconciliationDependency.RecoveryKind recoveryKind(' \
    "${E2_PREWARM_SLICE}" 'pre-recovery E2 vocabulary prewarm'
append_exclusive_slice \
    "${RECOVERY_SERVICE}" \
    '            var outcome = recoverPersistedPlayer(player);' \
    '            continuation.consume(this, outcome);' \
    "${E2_RECOVERY_OBSERVATION_SLICE}" 'actual RecoveryOutcome direct observation'
append_exclusive_slice \
    "${COORDINATOR}" \
    '        var result = reconcile(' \
    '            qualificationStoreView.recordReconciliation(' \
    "${E2_RESULT_OBSERVATION_SLICE}" 'actual E2 result direct observation'
require_fixed_count "${E2_CROSSING_SLICE}" 'ServerPlayer player,' 3 \
    'all three recovery crossing declarations must start with ServerPlayer'
require_fixed_count "${E2_CROSSING_SLICE}" \
    'SkillSubmissionRecoveryService.RecoveryContinuation continuation,' 3 \
    'all three recovery crossing declarations must carry the opaque continuation'
require_fixed_count "${E2_CROSSING_SLICE}" 'RecoveryKind kind,' 3 \
    'all three recovery crossing declarations must carry the primitive recovery kind'
require_fixed_count "${E2_CROSSING_SLICE}" 'int entriesCleared,' 3 \
    'all three recovery crossing declarations must carry entriesCleared'
require_fixed_count "${E2_CROSSING_SLICE}" 'int stepsReplayed,' 3 \
    'all three recovery crossing declarations must carry stepsReplayed'
require_fixed_count "${E2_CROSSING_SLICE}" \
    'Optional<String> existingExceptionClass)' 3 \
    'all three recovery crossing declarations must carry Optional<String> last'
forbid_fixed "${E2_CROSSING_SLICE}" 'RecoveryStatus status,' \
    'RecoveryStatus must not be a recovery entry-point parameter'
forbid_fixed "${E2_CROSSING_SLICE}" 'RecoveryStatus status)' \
    'RecoveryStatus must not be a recovery entry-point parameter'
require_fixed_count "${E2_PREWARM_SLICE}" \
    'P4E2OnlineReconciliationDependency.RecoveryKind.NO_PENDING.ordinal() != 0' 1 \
    'prewarm must initialize the recovery-kind enum exactly once'
require_fixed_count "${E2_PREWARM_SLICE}" \
    'recoveryKind(RecoveryUnavailableReason.JOURNAL_NOT_BOOTSTRAPPED)' 1 \
    'prewarm must initialize the recovery switch map exactly once'
for recovery_case in \
    'case NoPending ' \
    'case Cleared ' \
    'case Replayed ' \
    'case ClearedAndReplayed ' \
    'case Conflict ' \
    'case TargetInvalid ' \
    'case Unavailable '; do
    require_fixed_count "${E2_RECOVERY_OBSERVATION_SLICE}" "${recovery_case}" 1 \
        "actual RecoveryOutcome switch is missing ${recovery_case}"
done
forbid_fixed "${E2_RECOVERY_OBSERVATION_SLICE}" 'default' \
    'actual RecoveryOutcome observation must remain exhaustive without default'
require_fixed_count "${E2_RECOVERY_OBSERVATION_SLICE}" \
    'exactView.recordRecovery(' 7 \
    'each actual RecoveryOutcome variant must record one bounded direct projection'
for result_case in \
    'case P4E2ReconciliationResult.NoChanges ' \
    'case P4E2ReconciliationResult.RecoveryChanged ' \
    'case P4E2ReconciliationResult.Changed ' \
    'case P4E2ReconciliationResult.Deferred ' \
    'case P4E2ReconciliationResult.Failed ' \
    'case P4E2ReconciliationResult.GenerationExhausted '; do
    require_fixed_count "${E2_RESULT_OBSERVATION_SLICE}" "${result_case}" 1 \
        "actual E2 result switch is missing ${result_case}"
done
forbid_fixed "${E2_RESULT_OBSERVATION_SLICE}" 'default' \
    'actual E2 result observation must remain exhaustive without default'

# Inspect only the exact E2-owned portions of legacy services. Their unrelated P4-B/P4-C/P4-D
# methods retain reviewed side effects and must not become blanket phase exceptions.
append_exclusive_slice \
    "${RECOVERY_SERVICE}" \
    '    private void onPlayerLoggedIn(' \
    '    private static Map<SkillId, LatestTuple> indexLatestStates(' \
    "${E2_LEGACY_SLICE}" 'recovery continuation dispatch and status projection'
append_exclusive_slice \
    "${RECOVERY_SERVICE}" \
    '    public static final class RecoveryContinuation {' \
    '    interface Dependencies {' \
    "${E2_LEGACY_SLICE}" 'opaque recovery continuation'
append_exclusive_slice \
    "${PLAYER_SERVICE}" \
    '    public OnlineReconciliationHandle observeOnlineForReconciliation(' \
    '    private PlayerSkillAttachmentSourceObservation requireOnlineRootHandle(' \
    "${E2_LEGACY_SLICE}" 'player E2 observation, rebuild, and publication operations'
append_exclusive_slice \
    "${PLAYER_SERVICE}" \
    '    public interface OnlineReconciliationSink {' \
    '    public sealed interface RootAuditAdmissionResult' \
    "${E2_LEGACY_SLICE}" 'player E2 nominal types'
append_exclusive_slice \
    "${STORE_SERVICE}" \
    '    private final SkillRetentionRootAuditService rootAuditService;' \
    '    public SkillSubsystemResult<Optional<SkillDocument>> find(' \
    "${E2_LEGACY_SLICE}" 'Store E2 construction and registration wiring'
append_exclusive_slice \
    "${STORE_SERVICE}" \
    '    public P4E2OnlineReconciliationDependency onlineReconciliationDependency() {' \
    '    void requireP4E1AuditLifecycle(' \
    "${E2_LEGACY_SLICE}" 'Store E2 observation and freshness operations'
append_exclusive_slice \
    "${AUDIT_SERVICE}" \
    '    InvalidationResult invalidateForReconciliation(MinecraftServer server) {' \
    '    void removeServer(MinecraftServer server) {' \
    "${E2_LEGACY_SLICE}" 'audit E2 invalidation facade'
append_exclusive_slice \
    "${AUDIT_SERVICE}" \
    '        InvalidationResult invalidate(Object candidateOwner, Object candidateServer) {' \
    '        void remove(Object candidateOwner, Object candidateServer) {' \
    "${E2_LEGACY_SLICE}" 'audit E2 invalidation state transition'
[[ -s "${E2_LEGACY_SLICE}" ]] \
    || fail 'exact legacy E2 service slice inventory is empty'

require_only_owner 'new SkillRetentionRootAuditService(' "${STORE_SERVICE}" 1 \
    'audit service construction must have one exact Store-service owner'
require_only_owner 'new P4E2OnlineReconciliationCoordinator(' "${STORE_SERVICE}" 1 \
    'E2 coordinator construction must have one exact Store-service owner'
require_only_owner 'new P4E2QualificationFacade()' "${GRAMARYE}" 1 \
    'qualification facade construction must have one exact composition-root owner'
require_only_owner 'registerExtensionPoint(' "${GRAMARYE}" 1 \
    'extension registration must have one exact composition-root owner'
require_fixed "${GRAMARYE}" \
    'exactContainer.registerExtensionPoint(P4E2QualificationFacade.class, exactFacade);' \
    'Gramarye must use the direct-object extension registration overload'
forbid_fixed "${GRAMARYE}" 'Supplier' \
    'Gramarye must not use Supplier extension registration'
forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" 'getCustomExtension(' \
    'production code must not retrieve the qualification extension'
forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" 'ModLoadingContext' \
    'production code must not use ModLoadingContext as a runtime locator'
require_fixed_count "${P4C2_ADAPTER}" 'getCustomExtension(P4E2QualificationFacade.class)' 1 \
    'test-only adapter must have one exact custom-extension retrieval callsite'
require_fixed_count "${P4C2_ADAPTER}" 'getModContainerById(Gramarye.MOD_ID)' 1 \
    'test-only adapter must retrieve only the exact Gramarye container'
require_only_owner 'PlayerEvent.PlayerLoggedInEvent' "${RECOVERY_SERVICE}" 1 \
    'login listener must have one exact recovery-service owner'
require_only_owner '.reconcileAfterRecovery(' "${RECOVERY_SERVICE}" 1 \
    'typed P4-E2 continuation must have one exact production callsite'
require_fixed \
    "${STORE_ROOT}/P4E2OnlineReconciliationDependency.java" \
    'SkillSubmissionRecoveryService.RecoveryContinuation continuation,' \
    'the public E2 dependency must require the exact opaque recovery continuation'
require_only_owner 'new RecoveryStatus(' "${COORDINATOR}" 1 \
    'RecoveryStatus allocation must have one exact coordinator owner'
require_only_owner 'record RecoveryStatus(' "${COORDINATOR}" 1 \
    'RecoveryStatus declaration must have one exact private coordinator owner'
require_fixed "${COORDINATOR}" '    private record RecoveryStatus(' \
    'RecoveryStatus must remain a private coordinator implementation detail'
forbid_fixed "${STORE_ROOT}/P4E2OnlineReconciliationDependency.java" 'RecoveryStatus' \
    'the public dependency must not expose the coordinator recovery status'
forbid_fixed "${RECOVERY_SERVICE}" 'recoveryStatus(' \
    'the recovery service must not retain a dead RecoveryStatus projection helper'
require_fixed_count "${PLAYER_SERVICE}" '.setData(' 1 \
    'player-skill Attachment publication must retain one service write'
require_fixed_count "${MANA_ATTACHMENTS}" '.setData(' 1 \
    'mana Attachment access must retain one package-private write'
[[ "$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" '.setData(')" -eq 2 ]] \
    || fail 'live Attachment setData escaped the exact two reviewed access owners'
require_only_owner '.invalidateForReconciliation(' "${COORDINATOR}" 1 \
    'E2 index invalidation must have at most one exact coordinator callsite'
require_fixed_count "${COORDINATOR}" \
    'qualificationStoreView.recordInvalidationAttempt(' 1 \
    'central invalidation helper must record one exact attempt coordinate'
require_fixed_count "${COORDINATOR}" \
    'qualificationStoreView.recordInvalidationAccepted(' 1 \
    'central invalidation helper must record one exact Accepted coordinate'
require_fixed_count "${PLAYER_SERVICE}" \
    'qualificationPlayerView.recordE2SetDataAttempt(' 1 \
    'player publisher must record one exact E2 setData attempt coordinate'
require_fixed_count "${PLAYER_SERVICE}" \
    'qualificationPlayerView.recordE2SetDataSuccess(' 1 \
    'player publisher must record one exact E2 setData success coordinate'
require_fixed_count "${PLAYER_SERVICE}" \
    'publishReplacement(' 6 \
    'the three-call/two-overload replacement publisher topology must remain exact'
require_fixed_count "${PLAYER_SERVICE}" \
    '    private static void publishReplacement(' 2 \
    'the replacement publisher must retain exactly its unobserved and E2-bound overloads'
require_fixed_count "${PLAYER_SERVICE}" \
    'publishReplacement(player, transition.replacement);' 1 \
    'the prepared-transition caller must retain the unobserved publisher overload'
require_fixed_count "${PLAYER_SERVICE}" \
    'player, ((PlayerSkillAttachmentBuildResult.Built) rebuilt).ready());' 1 \
    'the mutation caller must retain the exact multiline unobserved publisher invocation'
require_fixed_count "${PLAYER_SERVICE}" \
    'publishReplacement(player, replacement, qualificationPlayerView);' 1 \
    'only the E2 publication caller may carry the exact PlayerView'
require_fixed_count "${PLAYER_SERVICE}" \
    'publishReplacement(player, replacement, null);' 1 \
    'the shared non-E2 publisher overload must pass no PlayerView'
require_fixed_count "${STORE_SERVICE}" \
    'gameBus.addListener(this::onServerStopped);' 1 \
    'the existing Store server-stop listener registration must remain singular'
require_only_owner 'qualificationStoreView.clearOnServerStopped();' \
    "${STORE_SERVICE}" 1 \
    'qualification cleanup must remain in the existing Store stop owner'
for forbidden_facade_surface in \
    'ThreadLocal' \
    'java.util.Map' \
    'java.util.List' \
    'java.util.Set' \
    'java.util.Queue' \
    'Runnable' \
    'Consumer' \
    'Function' \
    'Path' \
    'File' \
    'Logger'; do
    forbid_fixed "${FACADE}" "${forbidden_facade_surface}" \
        "facade contains forbidden mutable/callback/I-O surface ${forbidden_facade_surface}"
done
forbid_ere "${FACADE}" \
    'static[[:space:]]+(final[[:space:]]+)?(boolean|byte|short|int|long|float|double|char|String|ThreadLocal|Map|List|Set|Queue|P4E2QualificationFacade)[[:space:]]' \
    'facade must not declare static state'
require_fixed_count "${E2_LEGACY_SLICE}" \
    'exactDependency.reconcileAfterRecovery(' 1 \
    'exact recovery slice must dispatch one typed E2 continuation'
for recovery_accessor in \
    'exactOutcome.e2Kind()' \
    'exactOutcome.e2EntriesCleared()' \
    'exactOutcome.e2StepsReplayed()' \
    'exactOutcome.e2ExceptionClass()'; do
    require_fixed_count "${E2_LEGACY_SLICE}" "${recovery_accessor}" 1 \
        "opaque continuation must dispatch one allocation-free accessor: ${recovery_accessor}"
done
require_fixed_count "${E2_LEGACY_SLICE}" \
    'publishReplacement(player, replacement, qualificationPlayerView);' 1 \
    'exact player E2 slice must use one PlayerView-bound private publisher'
require_fixed_count "${E2_LEGACY_SLICE}" \
    'return slot.lifecycle.invalidate(this, server);' 1 \
    'exact audit E2 facade slice must delegate one invalidation'

recovery_line="$(line_of_fixed "${RECOVERY_SERVICE}" 'recoverPersistedPlayer(player)')"
continuation_line="$(line_of_fixed "${RECOVERY_SERVICE}" '.reconcileAfterRecovery(')"
prewarm_call_line="$(line_of_fixed \
    "${RECOVERY_SERVICE}" 'requireE2RecoveryVocabularyInitialized();')"
require_fixed_count "${RECOVERY_SERVICE}" \
    'requireE2RecoveryVocabularyInitialized();' 1 \
    'login must invoke the E2 vocabulary prewarm exactly once'
[[ "${prewarm_call_line}" -lt "${recovery_line}" ]] \
    || fail 'both E2 vocabulary prewarm triggers must be reached before recovery'
[[ "${recovery_line}" -lt "${continuation_line}" ]] \
    || fail 'P4-D recovery must precede the typed P4-E2 continuation'
outcome_line="$(line_of_fixed \
    "${RECOVERY_SERVICE}" 'var exactOutcome = Objects.requireNonNull(outcome')"
kind_line="$(line_of_fixed "${RECOVERY_SERVICE}" 'var kind = exactOutcome.e2Kind();')"
entries_line="$(line_of_fixed \
    "${RECOVERY_SERVICE}" 'var entriesCleared = exactOutcome.e2EntriesCleared();')"
steps_line="$(line_of_fixed \
    "${RECOVERY_SERVICE}" 'var stepsReplayed = exactOutcome.e2StepsReplayed();')"
exception_line="$(line_of_fixed \
    "${RECOVERY_SERVICE}" \
    'var existingExceptionClass = exactOutcome.e2ExceptionClass();')"
[[ "${outcome_line}" -lt "${kind_line}" \
        && "${kind_line}" -lt "${entries_line}" \
        && "${entries_line}" -lt "${steps_line}" \
        && "${steps_line}" -lt "${exception_line}" \
        && "${exception_line}" -lt "${continuation_line}" ]] \
    || fail 'opaque continuation primitive dispatch order drifted'
changed_line="$(line_of_fixed \
    "${COORDINATOR}" 'if (entriesCleared > 0 || stepsReplayed > 0)')"
early_invalidation_line="$(line_of_fixed \
    "${COORDINATOR}" 'var invalidation = invalidate(server, player);')"
status_allocation_line="$(line_of_fixed \
    "${COORDINATOR}" 'status = recoveryStatus(')"
[[ "${changed_line}" -lt "${early_invalidation_line}" \
        && "${early_invalidation_line}" -lt "${status_allocation_line}" ]] \
    || fail 'RecoveryStatus must be allocated only after changed-recovery invalidation'
invalidation_line="$(last_line_of_fixed \
    "${COORDINATOR}" 'var invalidation = invalidate(server, player);')"
publication_line="$(line_of_fixed "${COORDINATOR}" '.publishPreparedReconciliation(')"
[[ "${invalidation_line}" -lt "${publication_line}" ]] \
    || fail 'accepted invalidation must precede the player-owned publication operation'
attempt_line="$(line_of_fixed \
    "${COORDINATOR}" 'qualificationStoreView.recordInvalidationAttempt(')"
actual_invalidation_line="$(line_of_fixed \
    "${COORDINATOR}" 'var result = rootAuditService.invalidateForReconciliation(server);')"
accepted_record_line="$(line_of_fixed \
    "${COORDINATOR}" 'qualificationStoreView.recordInvalidationAccepted(')"
[[ "${attempt_line}" -lt "${actual_invalidation_line}" \
        && "${actual_invalidation_line}" -lt "${accepted_record_line}" ]] \
    || fail 'attempt/actual invalidation/Accepted direct coordinate order drifted'
set_data_attempt_line="$(line_of_fixed \
    "${PLAYER_SERVICE}" 'qualificationPlayerView.recordE2SetDataAttempt(')"
set_data_line="$(line_of_fixed "${PLAYER_SERVICE}" 'player.setData(type, replacement);')"
set_data_success_line="$(line_of_fixed \
    "${PLAYER_SERVICE}" 'qualificationPlayerView.recordE2SetDataSuccess(')"
[[ "${set_data_attempt_line}" -lt "${set_data_line}" \
        && "${set_data_line}" -lt "${set_data_success_line}" ]] \
    || fail 'attempt/actual setData/normal-return success coordinate order drifted'
applied_line="$(line_of_fixed \
    "${PLAYER_SERVICE}" 'return ReconciliationPublication.APPLIED;')"
e2_publisher_line="$(line_of_fixed \
    "${PLAYER_SERVICE}" 'publishReplacement(player, replacement, qualificationPlayerView);')"
[[ "${e2_publisher_line}" -lt "${applied_line}" ]] \
    || fail 'APPLIED must remain after the E2-bound publisher returns normally'

for forbidden in \
    'SkillRetentionRootSnapshot.fromCompleteRoots' \
    '.reclaim(' \
    '.commit(' \
    '.pin(' \
    '.setData(' \
    'prepareJournalPrefixClear(' \
    'commitPreparedJournalClear(' \
    'NbtIo.' \
    'CompoundTag' \
    'java.nio.file' \
    'CompletableFuture' \
    'java.util.concurrent' \
    'ExecutorService' \
    'Executors.' \
    'parallelStream(' \
    'new Thread(' \
    'Thread.sleep(' \
    'Thread.yield(' \
    'java.lang.reflect' \
    'Class.forName(' \
    'getDeclaredMethod(' \
    'getDeclaredField(' \
    'MethodHandles' \
    'VarHandle' \
    'setAccessible(' \
    'sun.misc.Unsafe' \
    '@SuppressWarnings' \
    'CustomPacketPayload' \
    'PayloadRegistrar' \
    'PlayerEvent.Clone' \
    'PlayerLoggedOutEvent'; do
    forbid_fixed_in_file_list "${E2_SOURCE_LIST}" "${forbidden}" \
        "forbidden P4-E2 authority or later-phase token: ${forbidden}"
    forbid_fixed "${E2_LEGACY_SLICE}" "${forbidden}" \
        "forbidden authority in exact legacy E2 service slices: ${forbidden}"
done

forbid_fixed "${E2_LEGACY_SLICE}" 'P4E3' \
    'legacy E2 service slices must not acquire P4-E3 authority'
while IFS= read -r -d '' source; do
    [[ "${source}" == "${FACADE}" ]] && continue
    forbid_fixed "${source}" 'P4E3' \
        "P4-E3 surface escaped the exact approved facade (${source})"
done < "${E2_SOURCE_LIST}"

for error_catch in \
    'catch[[:space:]]*\([[:space:]]*Throwable' \
    'catch[[:space:]]*\([[:space:]]*Error' \
    'catch[[:space:]]*\([[:space:]]*OutOfMemoryError'; do
    file=''
    while IFS= read -r -d '' file; do
        forbid_ere "${file}" "${error_catch}" \
            "exact P4-E2 source catches Error, OOME, or Throwable (${file})"
    done < "${E2_SOURCE_LIST}"
    forbid_ere "${E2_LEGACY_SLICE}" "${error_catch}" \
        'exact legacy E2 service slice catches Error, OOME, or Throwable'
done

require_only_owner \
    'SkillRetentionRootSnapshot.fromCompleteRoots' "${STORE_SERVICE}" 1 \
    'snapshot factory must have one exact P4-E3 Store-service owner'
for direct_marker in \
    'public static final int SCHEMA_VERSION = 1;' \
    'public static final String CASE_ID = "p4-c2-ready";' \
    'public static final String FILE_NAME = "p4-e2-direct-observation.json";' \
    'public static final String COMPLETION_MARKER = "P4_E2_DIRECT_OBSERVATION_COMPLETE";' \
    'public static final int MAX_FILE_BYTES = 65_536;' \
    'input.readNBytes(MAX_FILE_BYTES + 1)' \
    'StandardCopyOption.ATOMIC_MOVE' \
    'LinkOption.NOFOLLOW_LINKS' \
    'if (index != text.length())'; do
    require_fixed "${P4C2_OBSERVATION}" "${direct_marker}" \
        "direct-observation strict transport is missing ${direct_marker}"
done
forbid_fixed "${P4C2_OBSERVATION}" 'java.util.regex' \
    'direct-observation parser must not use regex or substring matching'
for json_field in \
    schema_version case_id phase player_uuid recovery_handler_calls \
    typed_recovery_outcome entries_cleared steps_replayed recovery_changed \
    e2_continuation_calls e2_result_variant invalidation_attempts \
    invalidation_accepted invalidation_generation_present \
    e2_set_data_attempts e2_set_data_successes completion_marker; do
    require_fixed_count "${P4C2_OBSERVATION}" "\\\"${json_field}\\\"" 2 \
        "canonical writer/parser field inventory changed for ${json_field}"
done
for adapter_marker in \
    'P4E2QualificationFacadeTestAccess.armReady(' \
    'P4E2QualificationFacadeTestAccess.consumeReady(' \
    'P4E2QualificationFacadeTestAccess.discardPreservingPrimary(' \
    'observation.writeNewIn(worldRoot.getParent());'; do
    require_fixed_count "${P4C2_MEMORY_GAMETEST}" "${adapter_marker}" 1 \
        "P4-C2 actual-login direct adapter integration changed: ${adapter_marker}"
done
for failure_cleanup_marker in \
    'Throwable primaryFailure = null;' \
    'catch (RuntimeException | Error failure)' \
    'observationHandle, primaryFailure);'; do
    require_fixed_count "${P4C2_MEMORY_GAMETEST}" "${failure_cleanup_marker}" 1 \
        "P4-C2 primary-failure cleanup changed: ${failure_cleanup_marker}"
done
for failure_cleanup_marker in \
    'if (primaryFailure == null)' \
    'catch (RuntimeException | Error ignoredCleanupFailure)' \
    'Do not retry, allocate diagnostics, or replace the original throwable.'; do
    require_fixed_count "${P4C2_ADAPTER}" "${failure_cleanup_marker}" 1 \
        "P4-C2 adapter no-mask cleanup changed: ${failure_cleanup_marker}"
done
forbid_fixed "${P4C2_ADAPTER}" 'addSuppressed' \
    'P4-C2 adapter must not allocate suppressed diagnostics while preserving Error/OOME'
for verifier_marker in \
    'P4E2QualificationObservation.readDirectFrom(gameDirectory)' \
    'requireDirectFileCount(gameDirectory, 1);' \
    'requireDirectFileCount(gameDirectory, 0);' \
    'P4C2_READY_FIRST.json' \
    'P4C2_READY_RESTART.json' \
    'Files.getFileStore(source).equals(Files.getFileStore(reportRoot))' \
    'Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);'; do
    require_fixed_count "${P4C2_FILE_VERIFIER}" "${verifier_marker}" 1 \
        "P4-C2 strict direct verifier/archive changed: ${verifier_marker}"
done
total_game_test_count="$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" '@GameTest(')"
mana_game_test_count="$(LC_ALL=C grep -Fc -- '@GameTest(' "${MANA_GAME_TESTS}")"
baseline_game_test_count=$((total_game_test_count - mana_game_test_count))
[[ "${baseline_game_test_count}" -eq 12 ]] \
    || fail 'historical P4 normal GameTest inventory must remain exactly 12'
[[ "${mana_game_test_count}" -eq 7 ]] \
    || fail 'P6-S2 mana GameTest inventory must remain exactly 7'
[[ "${total_game_test_count}" -eq 19 ]] \
    || fail 'combined production GameTest inventory must remain exactly 19'

git diff --quiet HEAD -- \
    gradle.properties settings.gradle gradle \
    docs/codex-spec src/main/resources src/test/resources \
    || fail 'P4-E2 must not change authority/resource/version truth'
git diff --quiet HEAD -- \
    docs/architecture \
    ':(exclude)docs/architecture/P5-A-server-runtime-event-kernel.md' \
    || fail 'P4-E2 changed architecture truth outside the exact P6-A1.2 amendment'
[[ ! -e "${REPOSITORY_ROOT}/src/p4E2Probe" ]] \
    || fail 'P4-E2 must not add a probe source set'
[[ ! -e "${REPOSITORY_ROOT}/src/p4E2GameTest" ]] \
    || fail 'P4-E2 must not add a GameTest source set'
[[ ! -e "${REPOSITORY_ROOT}/src/main/java/com/yo1no/gramarye/magic/definition/store/P4E3.java" ]] \
    || fail 'P4-E3 production ownership must remain absent'

JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-jars.XXXXXX")"
find "${REPOSITORY_ROOT}/build/libs" -maxdepth 1 -type f -name '*.jar' -print0 \
    > "${JAR_FILE_LIST}" || fail 'could not inspect production JARs'
jar_count=0
jar_file=''
while IFS= read -r -d '' candidate; do
    jar_count=$((jar_count + 1))
    jar_file="${candidate}"
done < "${JAR_FILE_LIST}"
[[ "${jar_count}" -eq 1 ]] \
    || fail "expected exactly one production JAR, found ${jar_count}"
JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-e2-jar-listing.XXXXXX")"
jar tf "${jar_file}" > "${JAR_LISTING}" \
    || fail 'could not inspect the production JAR'

for production_class in \
    'com/yo1no/gramarye/P4E2QualificationFacade.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$SubmissionView.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$StoreView.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$PlayerView.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$SubmissionViewImpl.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$StoreViewImpl.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$PlayerViewImpl.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$Session.class' \
    'com/yo1no/gramarye/P4E2QualificationFacade$Snapshot.class' \
    'com/yo1no/gramarye/magic/definition/store/P4E2BoundPlayerSkillAttachmentReconciliationCapability.class' \
    'com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.class' \
    'com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.class' \
    'com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.class' \
    'com/yo1no/gramarye/magic/definition/store/P4E2ReconciliationResult.class' \
    'com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentReconciliationCapability.class'; do
    require_fixed "${JAR_LISTING}" "${production_class}" \
        "production JAR is missing exact P4-E2 class ${production_class}"
done

for forbidden_jar_entry in \
    'P4E2QualificationFacadeTestAccess' \
    'P4E2QualificationObservation' \
    'P4E2QualificationFacadeTest' \
    'P4E2QualificationFacadeVisibilityCompileTest' \
    'P4E2ApiGateTest' \
    'P4E2AtomicReconciliationTest' \
    'P4E2GroupedStoreValidationTest' \
    'P4E2LifecycleOrderingTest' \
    'P4E2PhaseTypes' \
    'P4E2VisibilityCompileTest' \
    'P4E0Research' \
    'P4E3StartupObservationTestAccess' \
    'P4E3StartupMemoryGameTests' \
    'P4E3ProbeMain' \
    'P4E3FixtureBuilder' \
    'P4E3FixtureManifest' \
    'P4E3FileVerifier' \
    'P4E3PlayerDataFixture' \
    'P4E3ApiGateTest' \
    'P4E3StartupLifecycleTest' \
    'P4E3LeaseTerminalTest' \
    'org/junit/' \
    'org/hamcrest/'; do
    forbid_fixed "${JAR_LISTING}" "${forbidden_jar_entry}" \
        "production JAR contains forbidden test/research/later-phase entry ${forbidden_jar_entry}"
done

printf '%s\n' 'P4-E2 configuration verification passed.'
