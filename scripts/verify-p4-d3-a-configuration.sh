#!/usr/bin/env bash
set -euo pipefail

# Use only baseline POSIX host tools; no developer search utility or shell alias is required.
PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in awk bash grep find git jar javap mktemp rm dirname pwd; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-D3-A configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-D3-A configuration verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

PRODUCTION_SOURCE_LIST=''
UNIT_SOURCE_LIST=''
D3_PROBE_SOURCE_LIST=''
D3_GAME_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
HELPER_FIXTURE=''
RECOVERY_SOURCE_SIGNATURES=''
RECOVERY_JAVAP_OUTPUT=''
RECOVERY_JAVAP_SIGNATURES=''

cleanup() {
    local temporary=''
    for temporary in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${UNIT_SOURCE_LIST}" \
        "${D3_PROBE_SOURCE_LIST}" \
        "${D3_GAME_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${HELPER_FIXTURE}" \
        "${RECOVERY_SOURCE_SIGNATURES}" \
        "${RECOVERY_JAVAP_OUTPUT}" \
        "${RECOVERY_JAVAP_SIGNATURES}"; do
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

require_regular_file() {
    local file="$1"
    local message="$2"
    if [[ ! -f "${file}" || -L "${file}" ]]; then
        fail "${message}"
    fi
}

require_line_count() {
    local file="$1"
    local expected="$2"
    local message="$3"
    local actual=''
    local status=0
    actual="$(LC_ALL=C awk 'END { print NR }' "${file}")" || status=$?
    [[ "${status}" -eq 0 ]] || fail "awk failed while checking ${file} (exit ${status})"
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

require_exact_line_count() {
    local file="$1"
    local line="$2"
    local expected="$3"
    local message="$4"
    local actual=''
    local status=0
    actual="$(LC_ALL=C grep -Fxc -- "${line}" "${file}")" || status=$?
    case "${status}" in
        0) ;;
        1) actual=0 ;;
        *) grep_failed "${file}" "${status}" ;;
    esac
    [[ "${actual}" -eq "${expected}" ]] \
        || fail "${message} (expected ${expected}, found ${actual})"
}

verify_recovery_factory_surface() {
    local recovery="$1"
    local class_root='build/classes/java/main'
    local class_name='com.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryService'
    local class_file="${class_root}/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.class"
    local status=0

    require_fixed_count "${recovery}" \
        'public static SkillSubmissionRecoveryService create(' 2 \
        'D3-A/E2 recovery service must expose exactly two reviewed factory overloads'

    RECOVERY_SOURCE_SIGNATURES="$(
        mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-recovery-source.XXXXXX"
    )" || fail 'P4-D3-A verifier could not create recovery source signature output'
    LC_ALL=C awk '
        function compact(value) {
            gsub(/[[:space:]]/, "", value)
            return value
        }
        /^[[:space:]]+public static .* create\($/ {
            collecting = 1
            signature = $0
            next
        }
        collecting {
            signature = signature $0
            if (index($0, ") {") > 0) {
                print compact(signature)
                collecting = 0
                signature = ""
            }
        }
        END {
            if (collecting) {
                exit 2
            }
        }
    ' "${recovery}" > "${RECOVERY_SOURCE_SIGNATURES}" || status=$?
    [[ "${status}" -eq 0 ]] \
        || fail "P4-D3-A verifier could not parse recovery factory sources (exit ${status})"
    require_line_count "${RECOVERY_SOURCE_SIGNATURES}" 2 \
        'D3-A/E2 recovery source must contain exactly two public static create overloads'
    require_exact_line_count "${RECOVERY_SOURCE_SIGNATURES}" \
        'publicstaticSkillSubmissionRecoveryServicecreate(PlayerSkillAttachmentServiceattachmentService,SkillDefinitionStoreSubmissionPortstorePort,P4E2OnlineReconciliationDependencyonlineReconciliationDependency){' \
        1 'D3-A/E2 three-parameter recovery factory source signature drifted'
    require_exact_line_count "${RECOVERY_SOURCE_SIGNATURES}" \
        'publicstaticSkillSubmissionRecoveryServicecreate(PlayerSkillAttachmentServiceattachmentService,SkillDefinitionStoreSubmissionPortstorePort,P4E2OnlineReconciliationDependencyonlineReconciliationDependency,P4E2QualificationFacade.SubmissionViewqualificationView){' \
        1 'D3-A/E2 four-parameter recovery factory source signature drifted'

    require_regular_file "${class_file}" \
        'P4-D3-A fresh recovery service class is missing for javap verification'
    [[ ! "${recovery}" -nt "${class_file}" ]] \
        || fail 'P4-D3-A recovery service class is older than its frozen source'
    RECOVERY_JAVAP_OUTPUT="$(
        mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-recovery-javap.XXXXXX"
    )" || fail 'P4-D3-A verifier could not create javap output'
    status=0
    javap -p -s -classpath "${class_root}" "${class_name}" \
        > "${RECOVERY_JAVAP_OUTPUT}" || status=$?
    [[ "${status}" -eq 0 ]] \
        || fail "javap failed while checking the fresh recovery service class (exit ${status})"

    RECOVERY_JAVAP_SIGNATURES="$(
        mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-recovery-descriptors.XXXXXX"
    )" || fail 'P4-D3-A verifier could not create recovery descriptor output'
    status=0
    LC_ALL=C awk '
        function compact(value) {
            gsub(/[[:space:]]/, "", value)
            return value
        }
        index($0, "  public static ") == 1 && index($0, " create(") > 0 {
            method = $0
            if ((getline descriptor) <= 0 || index(descriptor, "    descriptor: ") != 1) {
                exit 2
            }
            print compact(method) "|" compact(descriptor)
        }
    ' "${RECOVERY_JAVAP_OUTPUT}" > "${RECOVERY_JAVAP_SIGNATURES}" || status=$?
    [[ "${status}" -eq 0 ]] \
        || fail "P4-D3-A verifier could not parse recovery javap output (exit ${status})"
    require_line_count "${RECOVERY_JAVAP_SIGNATURES}" 2 \
        'D3-A/E2 recovery bytecode must contain exactly two public static create overloads'
    require_exact_line_count "${RECOVERY_JAVAP_SIGNATURES}" \
        'publicstaticcom.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryServicecreate(com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService,com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort,com.yo1no.gramarye.magic.definition.store.P4E2OnlineReconciliationDependency);|descriptor:(Lcom/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService;Lcom/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort;Lcom/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency;)Lcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService;' \
        1 'D3-A/E2 three-parameter recovery factory bytecode signature drifted'
    require_exact_line_count "${RECOVERY_JAVAP_SIGNATURES}" \
        'publicstaticcom.yo1no.gramarye.magic.definition.submission.SkillSubmissionRecoveryServicecreate(com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService,com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreSubmissionPort,com.yo1no.gramarye.magic.definition.store.P4E2OnlineReconciliationDependency,com.yo1no.gramarye.P4E2QualificationFacade$SubmissionView);|descriptor:(Lcom/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService;Lcom/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort;Lcom/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency;Lcom/yo1no/gramarye/P4E2QualificationFacade$SubmissionView;)Lcom/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService;' \
        1 'D3-A/E2 four-parameter recovery factory bytecode signature drifted'
}

collect_java_files() {
    local root="$1"
    local destination="$2"
    local status=0
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" \
        || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-D3-A configuration verifier could not inspect Java sources under ${root}"
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

forbid_fixed_in_file_list_except() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file=''
    local allowed_file=''
    local allowed=0
    shift 3
    while IFS= read -r -d '' file; do
        allowed=0
        for allowed_file in "$@"; do
            if [[ "${file}" == "${allowed_file}" ]]; then
                allowed=1
                break
            fi
        done
        if [[ "${allowed}" -eq 1 ]]; then
            continue
        fi
        forbid_fixed "${file}" "${needle}" "${message} (${file})"
    done < "${file_list}"
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

require_only_fixed_owner() {
    local needle="$1"
    local expected="$2"
    local message="$3"
    local file=''
    local found=0
    local status=0
    while IFS= read -r -d '' file; do
        status=0
        LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
        case "${status}" in
            0)
                [[ "${file}" == "${expected}" ]] || fail "${message} (${file})"
                found=$((found + 1))
                ;;
            1) ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
    done < "${PRODUCTION_SOURCE_LIST}"
    [[ "${found}" -eq 1 ]] \
        || fail "${message} (expected one owner, found ${found})"
}

require_only_ere_owner() {
    local pattern="$1"
    local expected="$2"
    local message="$3"
    local file=''
    local found=0
    local status=0
    while IFS= read -r -d '' file; do
        status=0
        LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
        case "${status}" in
            0)
                [[ "${file}" == "${expected}" ]] || fail "${message} (${file})"
                found=$((found + 1))
                ;;
            1) ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
    done < "${PRODUCTION_SOURCE_LIST}"
    [[ "${found}" -eq 1 ]] \
        || fail "${message} (expected one owner, found ${found})"
}

require_exact_ere_owners() {
    local pattern="$1"
    local expected_count="$2"
    local message="$3"
    local file=''
    local expected=''
    local found=0
    local allowed=0
    local status=0
    shift 3
    while IFS= read -r -d '' file; do
        status=0
        LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
        case "${status}" in
            0)
                allowed=0
                for expected in "$@"; do
                    if [[ "${file}" == "${expected}" ]]; then
                        allowed=1
                        break
                    fi
                done
                [[ "${allowed}" -eq 1 ]] || fail "${message} (${file})"
                found=$((found + 1))
                ;;
            1) ;;
            *) grep_failed "${file}" "${status}" ;;
        esac
    done < "${PRODUCTION_SOURCE_LIST}"
    [[ "${found}" -eq "${expected_count}" ]] \
        || fail "${message} (expected ${expected_count} owners, found ${found})"
}

verify_search_helpers() {
    local missing_output=''
    local forbidden_output=''
    local tool_error_output=''
    local status=0
    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-helper.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create helper fixture'
    printf '%s\n' 'present contract' > "${HELPER_FIXTURE}"
    require_fixed "${HELPER_FIXTURE}" 'present contract' \
        'P4-D3-A verifier self-check lost fixed required matching'
    forbid_fixed "${HELPER_FIXTURE}" 'absent contract' \
        'P4-D3-A verifier self-check misclassified an absent pattern'

    missing_output="$({ require_fixed "${HELPER_FIXTURE}" 'missing contract' 'EXPECTED_MISSING'; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${missing_output}" == 'EXPECTED_MISSING' ]] \
        || fail 'P4-D3-A verifier could not distinguish a missing contract'
    status=0
    forbidden_output="$({ forbid_fixed "${HELPER_FIXTURE}" 'present contract' 'EXPECTED_FORBIDDEN'; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${forbidden_output}" == 'EXPECTED_FORBIDDEN' ]] \
        || fail 'P4-D3-A verifier could not distinguish a forbidden contract'
    status=0
    tool_error_output="$({ require_fixed "${HELPER_FIXTURE}.missing" 'present contract' 'WRONG_MISSING'; } 2>&1)" \
        || status=$?
    if [[ "${status}" -ne 1 \
            || "${tool_error_output}" != *'grep failed while checking '* \
            || "${tool_error_output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-D3-A verifier could not distinguish a grep error from a missing contract'
    fi
}

is_reviewed_d3a_production_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/Gramarye.java | \
        src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentAdmission.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachments.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSerializer.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentSourceObservation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2ReconciliationResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentReconciliationCapability.java | \
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
        src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentAdmissionSource.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/StrictSingleMemberGzipInput.java | \
        src/main/java/com/yo1no/gramarye/magic/limits/MagicSafetyCeilings.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStore.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachmentDefinitionBridge.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult.java | \
        src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java) return 0 ;;
        *) return 1 ;;
    esac
}

is_approved_p6_s3_production_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectCommitPlan.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionGuard.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectExecutionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectStepOutcome.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/EffectTrace.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/P6EffectBounds.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/effect/P6ExecutionInvariantException.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageEffectCommitPort.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectCommitPlan.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionEngine.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionGuard.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectExecutionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectRequest.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectResolution.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectStep.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectStepOutcome.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/EffectTrace.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6EffectBounds.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6ExecutionInvariantException.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionExecutorRegistry.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionInvocation.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/DamageActionExecutor.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionResult.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/ActionDamageTransactionEngine.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_approved_p6_s4_r1_production_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/P5RuntimeVocabulary.java | \
        src/main/java/com/yo1no/gramarye/SkillRuntimeService.java | \
        src/main/java/com/yo1no/gramarye/P6RuntimeExecutionCapability.java | \
        src/main/java/com/yo1no/gramarye/P6RuntimeExecutionPortAdapter.java | \
        src/main/java/com/yo1no/gramarye/magic/runtime/mana/P6RuntimeExecutionBridge.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

is_approved_p7_s1_production_path() {
    case "$1" in
        src/main/java/com/yo1no/gramarye/magic/network/P7NetworkBounds.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7SemanticInvariantException.java | \
        src/main/java/com/yo1no/gramarye/magic/network/CastInputKind.java | \
        src/main/java/com/yo1no/gramarye/magic/network/AimHint.java | \
        src/main/java/com/yo1no/gramarye/magic/network/EntityHint.java | \
        src/main/java/com/yo1no/gramarye/magic/network/CastIntent.java | \
        src/main/java/com/yo1no/gramarye/magic/network/CastIntentValidation.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7IntentFailureReason.java | \
        src/main/java/com/yo1no/gramarye/magic/network/IntentAcknowledgement.java | \
        src/main/java/com/yo1no/gramarye/magic/network/ConnectionEpochState.java | \
        src/main/java/com/yo1no/gramarye/magic/network/P7SessionIdentity.java | \
        src/main/java/com/yo1no/gramarye/magic/network/IntentSequenceState.java | \
        src/main/java/com/yo1no/gramarye/magic/network/IntentTokenBucket.java | \
        src/main/java/com/yo1no/gramarye/magic/network/IntentTickBudget.java | \
        src/main/java/com/yo1no/gramarye/magic/network/RateStrikeState.java | \
        src/main/java/com/yo1no/gramarye/magic/network/PendingPermitAccounting.java | \
        src/main/java/com/yo1no/gramarye/magic/network/CastIntentAdmissionSemantics.java)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

verify_change_boundary() {
    local changed=''
    local untracked=''
    local path=''
    local status=0
    git diff --quiet HEAD -- src/main/resources \
        || fail 'P4-E1-A must not modify production resources'
    changed="$(git diff --name-only HEAD -- src/main/java)" || status=$?
    [[ "${status}" -eq 0 ]] \
        || fail "git failed while checking tracked production Java (exit ${status})"
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_d3a_production_path "${path}" \
            || is_approved_p6_s3_production_path "${path}" \
            || is_approved_p6_s4_r1_production_path "${path}" \
            || is_approved_p7_s1_production_path "${path}" \
            || fail "production Java escaped exact reviewed D3-A/E1-A allowlist: ${path}"
    done <<< "${changed}"
    status=0
    untracked="$(git ls-files --others --exclude-standard -- \
        src/main/java src/main/resources)" || status=$?
    [[ "${status}" -eq 0 ]] \
        || fail "git failed while checking untracked production Java (exit ${status})"
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        is_reviewed_d3a_production_path "${path}" \
            || is_approved_p6_s3_production_path "${path}" \
            || is_approved_p6_s4_r1_production_path "${path}" \
            || is_approved_p7_s1_production_path "${path}" \
            || fail "untracked production path escaped exact reviewed D3-A/E1-A allowlist: ${path}"
    done <<< "${untracked}"
}

verify_exact_surfaces() {
    local recovery='src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java'
    local game_tests='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.java'
    local port='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java'
    local player='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java'
    local store_service='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java'
    local root='src/main/java/com/yo1no/gramarye/Gramarye.java'
    local mana_tests='src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java'
    local literal=''

    for literal in "${recovery}" "${game_tests}" "${port}" "${player}" \
            "${store_service}" "${root}"; do
        require_regular_file "${literal}" "P4-D3-A reviewed source is missing: ${literal}"
    done

    require_fixed_count "${port}" \
        'public PendingRecoveryProjection observePendingRecovery(' 1 \
        'D1 port must expose exactly one owner recovery projection operation'
    for literal in \
        'public sealed interface PendingRecoveryProjection' \
        'public record PendingSkillRecoveryChain(' \
        'public record PendingRecoveryStep(' \
        'public enum PendingRecoveryTargetFailure' \
        'public enum PendingRecoveryUnavailableReason'; do
        require_fixed "${port}" "${literal}" \
            "D1 port lost reviewed recovery surface ${literal}"
    done
    require_fixed_count "${player}" \
        'public Result<List<LatestStateView>> observeLatestStates(' 1 \
        'P4-C service must expose exactly one batch latest-state observation'
    verify_recovery_factory_surface "${recovery}"
    require_fixed_count "${recovery}" 'public void registerOn(IEventBus neoForgeEventBus)' 1 \
        'D3-A recovery service must expose one event registration entry'
    require_fixed_count "${recovery}" 'PlayerEvent.PlayerLoggedInEvent' 1 \
        'D3-A recovery service must mention exactly one login event type'
    require_fixed_count "${recovery}" 'neoForgeEventBus.addListener(this::onPlayerLoggedIn)' 1 \
        'D3-A recovery service must register exactly one login listener'
    require_fixed_count "${recovery}" 'recoverPersistedPlayer(player)' 1 \
        'D3-A login listener must invoke recovery exactly once'
    require_fixed "${store_service}" 'install(server);' \
        'P4-B startup callback lost Store installation'
    require_fixed "${store_service}" 'submissionPort.bootstrapJournal(server);' \
        'P4-B startup callback lost immediate journal bootstrap'
    require_fixed_count "${root}" 'SkillSubmissionRecoveryService.create(' 1 \
        'composition root must create exactly one recovery service'
    require_only_fixed_owner 'SkillSubmissionRecoveryService.create(' "${root}" \
        'recovery service production construction escaped the exact composition root'
    require_fixed_count "${root}" \
        'skillSubmissionRecoveryService.registerOn(NeoForge.EVENT_BUS)' 1 \
        'composition root must register exactly one recovery service'

    require_fixed_count "${game_tests}" '@GameTest(' 3 \
        'D3-A recovery holder must contain exactly three normal GameTests'
    require_fixed "${game_tests}" '@GameTestHolder(Gramarye.MOD_ID)' \
        'D3-A recovery holder lost the normal production namespace'
    for literal in \
        persistedBaseReplaysPendingChainOnLogin \
        persistedIntermediateClearsPrefixBeforeReplayOnLogin \
        persistedFinalClearsPendingChainWithoutReplayOnLogin; do
        require_fixed_count "${game_tests}" "public static void ${literal}(" 1 \
            "D3-A normal recovery GameTest is missing: ${literal}"
    done
    local total_count=''
    local mana_count=''
    local baseline_count=''
    require_regular_file "${mana_tests}" 'P6-S2 mana GameTest holder is missing'
    total_count="$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" '@GameTest(')"
    mana_count="$(LC_ALL=C grep -Fc -- '@GameTest(' "${mana_tests}")"
    baseline_count=$((total_count - mana_count))
    [[ "${baseline_count}" -eq 12 ]] \
        || fail "historical P4 normal GameTest inventory must remain twelve (found ${baseline_count})"
    [[ "${mana_count}" -eq 7 ]] \
        || fail "P6-S2 mana GameTest inventory must remain seven (found ${mana_count})"
    [[ "${total_count}" -eq 19 ]] \
        || fail "combined production GameTest inventory must remain nineteen (found ${total_count})"
}

verify_ownership_and_phase_boundary() {
    local recovery='src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java'
    local port='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java'
    local player='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java'
    local mana_attachments='src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java'
    local saved_data='src/main/java/com/yo1no/gramarye/magic/definition/store/GramaryeSkillSavedData.java'
    local store_service='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java'
    local literal=''

    require_only_fixed_owner 'PlayerLoggedInEvent' "${recovery}" \
        'PlayerLoggedInEvent escaped the unique D3-A recovery service'
    require_only_ere_owner \
        '\.[[:space:]]*prepareJournalPrefixClear[[:space:]]*\(' "${recovery}" \
        'journal-prefix clear preparation escaped the unique D3-A recovery service'
    require_only_ere_owner \
        '\.[[:space:]]*commitPreparedJournalClear[[:space:]]*\(' "${recovery}" \
        'journal-prefix clear commit escaped the unique D3-A recovery service'
    require_only_ere_owner '\.[[:space:]]*commit[[:space:]]*\(' "${port}" \
        'Store commit escaped the unique D1 port'
    require_exact_ere_owners '\.[[:space:]]*setData[[:space:]]*\(' 2 \
        'live Attachment setData escaped the exact reviewed access owners' \
        "${player}" "${mana_attachments}"
    require_exact_ere_owners '\.[[:space:]]*reclaim[[:space:]]*\(' 2 \
        'Store reclaim escaped reviewed P4-B lifecycle owners' \
        "${saved_data}" "${store_service}"

    for literal in \
        PlayerLoggedOutEvent \
        OfflineRoot \
        RootCollector \
        RootIndex \
        CustomPacketPayload \
        PayloadRegistrar \
        PacketDistributor \
        'Runtime.getRuntime().halt' \
        'Runtime.halt(' \
        'org.junit'; do
        forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" "${literal}" \
            "D3-B/P4-E/network/test surface appeared in production (${literal})"
    done
    forbid_fixed_in_file_list_except \
        "${PRODUCTION_SOURCE_LIST}" \
        'Reconciliation' \
        'reconciliation escaped the exact B2-A/B2-B owners' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E1GroupedStoreAudit.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditResult.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillRetentionRootAuditService.java' \
        'src/main/java/com/yo1no/gramarye/Gramarye.java' \
        'src/main/java/com/yo1no/gramarye/P4E2QualificationFacade.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2BoundPlayerSkillAttachmentReconciliationCapability.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2GroupedStoreValidation.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationCoordinator.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2OnlineReconciliationDependency.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/P4E2ReconciliationResult.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/PlayerSkillAttachmentReconciliationCapability.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java' \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java'
    forbid_fixed "${recovery}" 'SkillDefinitionSubmissionService' \
        'D3-A recovery must not call the D2 submission facade'
    forbid_fixed "${recovery}" '.reclaim(' \
        'D3-A recovery must not invoke Store reclaim'
    forbid_fixed "${recovery}" '.sync(' \
        'D3-A recovery must remain server-only'
    [[ -d src/p4D3Probe/java && -d src/p4D3GameTest/java ]] \
        || fail 'P4-D3-B reviewed source-set roots are missing'
    require_fixed_count build.gradle "sourceSets.create('p4D3" 2 \
        'P4-D3-B must add exactly two reviewed source sets'
    require_fixed build.gradle "sourceSets.create('p4D3Probe')" \
        'P4-D3-B reviewed probe source set is missing'
    require_fixed build.gradle "sourceSets.create('p4D3GameTest')" \
        'P4-D3-B reviewed dedicated source set is missing'
    require_fixed .github/workflows/build.yml '    name: P4-D memory gates' \
        'P4-D3-B reviewed CI memory job is missing'

    require_fixed_count \
        'src/p4D3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4D3MemoryGameTests.java' \
        'Runtime.getRuntime().halt(0)' 2 \
        'P4-D3-B must own exactly two hard-halt syntax sites in its dedicated holder'
    [[ "$(count_fixed_in_file_list "${D3_GAME_SOURCE_LIST}" \
            'Runtime.getRuntime().halt(0)')" -eq 2 ]] \
        || fail 'P4-D3-B hard-halt syntax sites escaped the dedicated holder'
    [[ "$(count_fixed_in_file_list "${D3_PROBE_SOURCE_LIST}" \
            'Runtime.getRuntime().halt(0)')" -eq 0 ]] \
        || fail 'P4-D3-B hard halt appeared in probe sources'
}

verify_optional_jar_isolation() {
    local jar_path=''
    local status=0
    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-jars.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-jar.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'P4-D3-A verifier could not inspect production JARs'
    while IFS= read -r -d '' jar_path; do
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed while checking ${jar_path}"
        for fixture in \
            P4D3AApiGateTest \
            P4D3BApiGateTest \
            P4D3PhaseTypes \
            P4D3FixtureTest \
            P4D3MemoryGameTests \
            P4D3ProbeMain \
            P4D3ProbeServerLifecycle \
            PlayerSkillLatestStateBatchTest \
            SkillDefinitionStorePendingRecoveryProjectionTest \
            SkillSubmissionRecoveryServiceTest; do
            forbid_fixed "${JAR_LISTING}" "${fixture}" \
                "P4-D3-A unit/API fixture leaked into production JAR (${fixture})"
        done
    done < "${JAR_FILE_LIST}"
}

main() {
    verify_search_helpers
    forbid_ere scripts/verify-p4-d3-a-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-D3-A portable verifier must not invoke a developer-only search tool'
    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-production.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create production source list'
    UNIT_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-tests.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create unit source list'
    D3_PROBE_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-probe.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create D3-B probe source list'
    D3_GAME_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-a-game.XXXXXX")" \
        || fail 'P4-D3-A verifier could not create D3-B dedicated source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    collect_java_files src/test/java "${UNIT_SOURCE_LIST}"
    collect_java_files src/p4D3Probe/java "${D3_PROBE_SOURCE_LIST}"
    collect_java_files src/p4D3GameTest/java "${D3_GAME_SOURCE_LIST}"
    verify_change_boundary
    verify_exact_surfaces
    verify_ownership_and_phase_boundary
    verify_optional_jar_isolation
    printf '%s\n' \
        'Verified exact P4-D3-A bootstrap, recovery APIs, login ownership, normal GameTests, and later-phase absence.'
}

main "$@"
