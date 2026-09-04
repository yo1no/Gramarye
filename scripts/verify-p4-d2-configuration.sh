#!/usr/bin/env bash
set -euo pipefail

# Stay independent of developer-only search tools and aliases while retaining the selected JDK
# for optional production-JAR inspection.
if [[ -n "${JAVA_HOME:-}" ]]; then
    PATH="${JAVA_HOME}/bin:/usr/bin:/bin:/usr/sbin:/sbin"
else
    PATH='/usr/bin:/bin:/usr/sbin:/sbin'
fi
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find mktemp rm dirname pwd git; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-D2 configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-D2 configuration verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

PRODUCTION_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
HELPER_FIXTURE=''

cleanup() {
    local temporary=''
    for temporary in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${HELPER_FIXTURE}"; do
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
    if [[ "${actual}" -ne "${expected}" ]]; then
        fail "${message} (expected ${expected}, found ${actual})"
    fi
}

require_regular_file() {
    local file="$1"
    local message="$2"
    if [[ ! -f "${file}" || -L "${file}" ]]; then
        fail "${message}"
    fi
}

collect_java_files() {
    local root="$1"
    local destination="$2"
    local status=0
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" \
        || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-D2 configuration verifier could not inspect Java sources under ${root}"
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
    [[ "${found}" -eq 1 ]] || fail "${message} (expected one owner, found ${found})"
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
    [[ "${found}" -eq 1 ]] || fail "${message} (expected one owner, found ${found})"
}

verify_search_helpers() {
    local missing_output=''
    local forbidden_output=''
    local tool_error_output=''
    local status=0
    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d2-helper.XXXXXX")" \
        || fail 'P4-D2 verifier could not create helper fixture'
    printf '%s\n' 'present contract' > "${HELPER_FIXTURE}"
    require_fixed "${HELPER_FIXTURE}" 'present contract' \
        'P4-D2 verifier self-check lost fixed required matching'
    forbid_fixed "${HELPER_FIXTURE}" 'absent contract' \
        'P4-D2 verifier self-check misclassified an absent pattern'

    missing_output="$({ require_fixed "${HELPER_FIXTURE}" 'missing contract' 'EXPECTED_MISSING'; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${missing_output}" == 'EXPECTED_MISSING' ]] \
        || fail 'P4-D2 verifier could not distinguish a missing contract'
    status=0
    forbidden_output="$({ forbid_fixed "${HELPER_FIXTURE}" 'present contract' 'EXPECTED_FORBIDDEN'; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${forbidden_output}" == 'EXPECTED_FORBIDDEN' ]] \
        || fail 'P4-D2 verifier could not distinguish a forbidden contract'
    status=0
    tool_error_output="$({ require_fixed "${HELPER_FIXTURE}.missing" 'present contract' 'WRONG_MISSING'; } 2>&1)" \
        || status=$?
    if [[ "${status}" -ne 1 \
            || "${tool_error_output}" != *'grep failed while checking '* \
            || "${tool_error_output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-D2 verifier could not distinguish a grep error from a missing contract'
    fi
}

verify_facade_and_runtime_counter_gate() {
    local root='src/main/java/com/yo1no/gramarye/magic/definition/submission'
    local facade="${root}/SkillDefinitionSubmissionService.java"
    local game_tests="${root}/SkillDefinitionSubmissionGameTests.java"
    local unit_test='src/test/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionServiceTest.java'
    local api_gate='src/test/java/com/yo1no/gramarye/magic/definition/store/P4D2BApiGateTest.java'
    local literal=''

    for literal in "${facade}" "${game_tests}" "${unit_test}" "${api_gate}"; do
        require_regular_file "${literal}" "P4-D2-B reviewed source is missing: ${literal}"
    done
    require_fixed "${facade}" 'public final class SkillDefinitionSubmissionService' \
        'P4-D2-B facade lost its public final shape'
    require_ere "${facade}" \
        'public[[:space:]]+SkillSubmissionCompositionOutcome[[:space:]]+submit[[:space:]]*\(' \
        'P4-D2-B facade lost its authenticated submit entry point'
    require_fixed_count "${facade}" 'public SkillSubmissionCompositionOutcome submit(' 1 \
        'P4-D2-B facade must expose one submit entry point'
    require_fixed "${facade}" 'if (!server.isSameThread())' \
        'P4-D2-B facade lost its server-thread check'
    require_fixed "${facade}" 'new SkillOwnerId(player.getUUID())' \
        'P4-D2-B facade must derive owner from the authenticated player'
    for literal in \
        'dependencies.findDraft(' \
        'dependencies.precheck(' \
        'dependencies.observeSubmissionAuthority(' \
        'dependencies.checkAuthority(' \
        'dependencies.snapshotPolicy(' \
        'dependencies.prepareAndMap(' \
        'dependencies.prepareLatestTransitionToCurrent(' \
        'dependencies.prepareSubmissionCommit(' \
        'dependencies.checkPreparedTransitionCurrent(' \
        'dependencies.commitPreparedSubmission(' \
        'dependencies.publishPreparedTransition('; do
        require_fixed_count "${facade}" "${literal}" 1 \
            "P4-D2-B submit orchestration lost exactly-once call ${literal}"
    done
    for literal in '.setData(' '.commit(' '.reclaim(' '@SuppressWarnings' \
            'PlayerSkillAttachmentState' 'SkillDefinitionStore '; do
        forbid_fixed "${facade}" "${literal}" \
            "P4-D2-B facade bypassed its controlled dependency boundary (${literal})"
    done
    forbid_ere "${facade}" \
        '(current|expected|target|mutation)[A-Za-z]*Generation[[:space:]]*\+[[:space:]]*1' \
        'P4-D2-B facade must not duplicate P4-C generation arithmetic'
    forbid_ere "${game_tests}" \
        '(current|expected|target|mutation)[A-Za-z]*Generation[[:space:]]*\+[[:space:]]*1' \
        'P4-D2-B GameTests must not introduce production generation arithmetic'

    for literal in \
        'draft_lookup' \
        'c1_precheck' \
        'store_authority' \
        'c2_authority' \
        'policy_snapshot' \
        'c3_prepare' \
        'c4_map' \
        'transition_prepare' \
        'd1_prepare' \
        'currentness' \
        'd1_commit' \
        'transition_publish'; do
        require_fixed "${unit_test}" "\"${literal}\"" \
            "P4-D2-B first-order runtime counter Gate lost stage ${literal}"
    done
    require_fixed "${unit_test}" \
        'successInvokesEveryFirstOrderStageExactlyOnceInTheApprovedOrder' \
        'P4-D2-B first-order success counter Gate is missing'
    require_fixed "${unit_test}" 'assertSame(dependencies.preparedReport' \
        'P4-D2-B runtime counter Gate lost report-identity coverage'
}

verify_root_and_normal_gametests() {
    local root='src/main/java/com/yo1no/gramarye/Gramarye.java'
    local player_tests='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java'
    local submission_tests='src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillDefinitionSubmissionGameTests.java'
    local mana_tests='src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaLifecycleGameTests.java'
    local total_count=''
    local mana_count=''
    local baseline_count=''
    local literal=''

    for literal in \
        'private final SkillIdSource skillIdSource;' \
        'private final SkillDraftCreationService skillDraftCreationService;' \
        'private final SkillSubmissionPolicyProvider skillSubmissionPolicyProvider;' \
        'private final SkillDefinitionSubmissionService skillDefinitionSubmissionService;' \
        'SkillDraftCreationService.randomUuidSkillIdSource()' \
        'new SkillDraftCreationService(' \
        'SkillSubmissionPolicyProvider.defaults()' \
        'SkillDefinitionSubmissionService.production('; do
        require_fixed_count "${root}" "${literal}" 1 \
            "P4-D2-B composition root lost exact owner ${literal}"
    done
    for literal in \
        'public SkillIdSource ' \
        'public SkillDraftCreationService ' \
        'public SkillSubmissionPolicyProvider ' \
        'public SkillDefinitionSubmissionService '; do
        forbid_fixed "${root}" "${literal}" \
            'P4-D2-B composition root exposed a service/provider locator'
    done

    require_fixed_count "${player_tests}" \
        'public static PlayerSkillAttachmentService newServiceForSubmissionGameTests()' 1 \
        'P4-D2-B lost the one narrow P4-C GameTest bridge'
    require_fixed_count "${submission_tests}" '@GameTest(' 2 \
        'P4-D2-B submission holder must contain exactly two normal GameTests'
    require_fixed "${submission_tests}" '@GameTestHolder(Gramarye.MOD_ID)' \
        'P4-D2-B submission holder lost the normal production namespace'
    require_fixed "${submission_tests}" \
        'fullSubmissionCommitsStoreJournalThenAttachmentExactlyOnce' \
        'P4-D2-B full-success GameTest is missing'
    require_fixed "${submission_tests}" \
        'postCommitAttachmentDriftReturnsPendingRecovery' \
        'P4-D2-B postcommit-drift GameTest is missing'
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

verify_static_ownership_and_phase_bounds() {
    local store_port='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreSubmissionPort.java'
    local player_service='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentService.java'
    local mana_attachments='src/main/java/com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java'
    local saved_data='src/main/java/com/yo1no/gramarye/magic/definition/store/GramaryeSkillSavedData.java'
    local store_service='src/main/java/com/yo1no/gramarye/magic/definition/store/SkillDefinitionStoreService.java'
    local uuid_source='src/main/java/com/yo1no/gramarye/magic/definition/submission/RandomUuidSkillIdSource.java'
    local default_provider='src/main/java/com/yo1no/gramarye/magic/definition/submission/DefaultSkillSubmissionPolicyProvider.java'
    local file=''
    local literal=''

    require_only_ere_owner '\.[[:space:]]*commit[[:space:]]*\(' "${store_port}" \
        'Store commit escaped the unique D1 submission port'
    require_exact_ere_owners '\.[[:space:]]*setData[[:space:]]*\(' 2 \
        'live Attachment setData escaped the exact reviewed access owners' \
        "${player_service}" "${mana_attachments}"
    require_exact_ere_owners '\.[[:space:]]*reclaim[[:space:]]*\(' 2 \
        'Store reclaim escaped the reviewed P4-B lifecycle owners' \
        "${saved_data}" "${store_service}"
    require_only_ere_owner \
        '\.[[:space:]]*prepareJournalPrefixClear[[:space:]]*\(' \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'journal-prefix clear preparation escaped the exact D3-A recovery service'
    require_only_ere_owner \
        '\.[[:space:]]*commitPreparedJournalClear[[:space:]]*\(' \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'journal-prefix clear publication escaped the exact D3-A recovery service'
    require_only_fixed_owner 'UUID.randomUUID()' "${uuid_source}" \
        'UUID minting escaped the unique reviewed source'
    require_only_fixed_owner 'SkillQuota.Unlimited.INSTANCE' "${default_provider}" \
        'default quota escaped the unique reviewed provider'
    require_only_fixed_owner \
        'new ValidationContext(MagicPolicyLimits.DEFAULTS)' "${default_provider}" \
        'default validation context escaped the unique reviewed provider'

    require_only_fixed_owner \
        'PlayerLoggedInEvent' \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'PlayerLoggedInEvent escaped the exact D3-A recovery service'
    for literal in \
        'PlayerLoggedOutEvent' \
        'OfflineRoot' \
        'RootCollector' \
        'RootIndex' \
        'PacketDistributor' \
        'org.junit'; do
        forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" "${literal}" \
            "P4-D3/P4-E/network/test surface appeared in production (${literal})"
    done
    forbid_fixed_in_file_list_except \
        "${PRODUCTION_SOURCE_LIST}" \
        'CustomPacketPayload' \
        'CustomPacketPayload escaped the exact P7-S2 payload owner allowlist' \
        'src/main/java/com/yo1no/gramarye/magic/network/CastIntentPayload.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/IntentAckPayload.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/PlayerManaSyncPayload.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/SkillCooldownSyncPayload.java'
    forbid_fixed_in_file_list_except \
        "${PRODUCTION_SOURCE_LIST}" \
        'PayloadRegistrar' \
        'PayloadRegistrar escaped the exact P7-S2 registrar owner allowlist' \
        'src/main/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrar.java'
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
    for literal in \
        'P4D2BApiGateTest' \
        'SkillDefinitionSubmissionServiceTest'; do
        forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" "${literal}" \
            "P4-D2-B JUnit fixture leaked into production (${literal})"
    done
    require_fixed build.gradle "sourceSets.create('p4D3Probe')" \
        'P4-D3-B reviewed probe source set is missing'
    require_fixed build.gradle "sourceSets.create('p4D3GameTest')" \
        'P4-D3-B reviewed dedicated source set is missing'
    require_fixed .github/workflows/build.yml '    name: P4-D memory gates' \
        'P4-D3-B reviewed CI memory Gate is missing'
}

verify_optional_jar_isolation() {
    local jar_path=''
    local status=0
    if ! command -v jar >/dev/null 2>&1; then
        return 0
    fi
    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d2-jars.XXXXXX")" \
        || fail 'P4-D2 verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d2-jar.XXXXXX")" \
        || fail 'P4-D2 verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'P4-D2 verifier could not inspect production JARs'
    while IFS= read -r -d '' jar_path; do
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed while checking ${jar_path}"
        for fixture in \
            P4D2BApiGateTest \
            SkillDefinitionSubmissionServiceTest \
            P4D3BApiGateTest \
            P4D3FixtureTest \
            P4D3MemoryGameTests \
            P4D3ProbeMain \
            P4D3ProbeServerLifecycle; do
            forbid_fixed "${JAR_LISTING}" "${fixture}" \
                "P4-D2-B JUnit fixture leaked into production JAR (${fixture})"
        done
    done < "${JAR_FILE_LIST}"
}

main() {
    verify_search_helpers
    forbid_ere scripts/verify-p4-d2-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-D2 portable verifier must not invoke rg'
    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d2-production.XXXXXX")" \
        || fail 'P4-D2 verifier could not create production source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    verify_facade_and_runtime_counter_gate
    verify_root_and_normal_gametests
    verify_static_ownership_and_phase_bounds
    verify_optional_jar_isolation
    printf '%s\n' \
        'Verified P4-D2-B facade, orchestration, roots, GameTests, and reviewed D3-B test configuration.'
}

main "$@"
