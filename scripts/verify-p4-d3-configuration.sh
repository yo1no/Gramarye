#!/usr/bin/env bash
set -euo pipefail

# Keep the phase gate independent of developer-only search tools and shell aliases.
PATH='/usr/bin:/bin'
export PATH

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

for required_tool in bash grep find git jar mktemp rm dirname pwd sed; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-D3-B configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-D3-B configuration verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

PRODUCTION_SOURCE_LIST=''
PROBE_SOURCE_LIST=''
GAME_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
HELPER_FIXTURE=''
CI_JOB_BLOCK=''
RUNTIME_MOD_BLOCK=''

cleanup() {
    local temporary=''
    for temporary in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${PROBE_SOURCE_LIST}" \
        "${GAME_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${HELPER_FIXTURE}" \
        "${CI_JOB_BLOCK}" \
        "${RUNTIME_MOD_BLOCK}"; do
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
        fail "P4-D3-B configuration verifier could not inspect Java sources under ${root}"
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

require_any_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
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
    fail "${message}"
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

require_any_fixed_in_d3_sources() {
    local needle="$1"
    local message="$2"
    if has_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${needle}" \
            || has_fixed_in_file_list "${GAME_SOURCE_LIST}" "${needle}"; then
        return 0
    fi
    fail "${message}"
}

verify_search_helpers() {
    local missing_output=''
    local forbidden_output=''
    local tool_error_output=''
    local status=0
    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-helper.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create helper fixture'
    printf '%s\n' 'present contract' > "${HELPER_FIXTURE}"
    require_fixed "${HELPER_FIXTURE}" 'present contract' \
        'P4-D3-B verifier self-check lost fixed required matching'
    forbid_fixed "${HELPER_FIXTURE}" 'absent contract' \
        'P4-D3-B verifier self-check misclassified an absent pattern'

    missing_output="$({ require_fixed "${HELPER_FIXTURE}" 'missing contract' 'EXPECTED_MISSING'; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${missing_output}" == 'EXPECTED_MISSING' ]] \
        || fail 'P4-D3-B verifier could not distinguish a missing contract'
    status=0
    forbidden_output="$({ forbid_fixed "${HELPER_FIXTURE}" 'present contract' 'EXPECTED_FORBIDDEN'; } 2>&1)" \
        || status=$?
    [[ "${status}" -eq 1 && "${forbidden_output}" == 'EXPECTED_FORBIDDEN' ]] \
        || fail 'P4-D3-B verifier could not distinguish a forbidden contract'
    status=0
    tool_error_output="$({ require_fixed "${HELPER_FIXTURE}.missing" 'present contract' 'WRONG_MISSING'; } 2>&1)" \
        || status=$?
    if [[ "${status}" -ne 1 \
            || "${tool_error_output}" != *'grep failed while checking '* \
            || "${tool_error_output}" == *'WRONG_MISSING'* ]]; then
        fail 'P4-D3-B verifier could not distinguish a grep error from a missing contract'
    fi
}

verify_production_no_diff() {
    local untracked=''
    local path=''
    local status=0
    git diff --quiet HEAD -- src/main/java src/main/resources \
        || fail 'P4-D3-B must not modify production Java or resources'
    untracked="$(git ls-files --others --exclude-standard -- \
        src/main/java src/main/resources)" || status=$?
    [[ "${status}" -eq 0 ]] \
        || fail "git failed while checking untracked production paths (exit ${status})"
    while IFS= read -r path; do
        [[ -z "${path}" ]] && continue
        fail "P4-D3-B added an untracked production path: ${path}"
    done <<< "${untracked}"
}

is_reviewed_probe_path() {
    case "$1" in
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/player/P4D3PlayerProbe.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3FileVerifier.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3FixtureBuilder.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3FixtureManifest.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3Hashing.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3ProbeCase.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3ProbeMain.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3ProbeSupport.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3RunMode.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store/P4D3StoreJournalFixture.java | \
        src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/submission/P4D3SubmissionProbe.java) return 0 ;;
        *) return 1 ;;
    esac
}

is_reviewed_game_path() {
    case "$1" in
        src/p4D3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4D3MemoryGameTests.java | \
        src/p4D3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4D3ProbeServerLifecycle.java) return 0 ;;
        *) return 1 ;;
    esac
}

verify_exact_source_allowlists() {
    local file=''
    local relative=''
    local probe_count=0
    local game_count=0
    while IFS= read -r -d '' file; do
        relative="${file#./}"
        is_reviewed_probe_path "${relative}" \
            || fail "unreviewed P4-D3-B probe source: ${relative}"
        probe_count=$((probe_count + 1))
    done < "${PROBE_SOURCE_LIST}"
    while IFS= read -r -d '' file; do
        relative="${file#./}"
        is_reviewed_game_path "${relative}" \
            || fail "unreviewed P4-D3-B dedicated source: ${relative}"
        game_count=$((game_count + 1))
    done < "${GAME_SOURCE_LIST}"
    [[ "${probe_count}" -eq 11 ]] \
        || fail "P4-D3-B probe source count must be eleven (found ${probe_count})"
    [[ "${game_count}" -eq 2 ]] \
        || fail "P4-D3-B dedicated source count must be two (found ${game_count})"
}

verify_build_contract() {
    local task=''
    local edge=''
    local current=''
    local previous=''
    require_ere_count build.gradle \
        "sourceSets\\.create\\('p4D3[A-Za-z0-9_]*'\\)" 2 \
        'P4-D3-B must declare exactly two phase source sets'
    for task in \
        "sourceSets.create('p4D3Probe')" \
        "sourceSets.create('p4D3GameTest')" \
        "tasks.register('generateP4D3GameTestResources', Sync)" \
        'data/gramarye_p4_d3/structure/p4_d3_probe.nbt' \
        'addModdingDependenciesTo(p4D3ProbeSourceSet)' \
        'addModdingDependenciesTo(p4D3GameTestSourceSet)' \
        'p4D3HeapProbe' \
        'sourceSet(p4D3ProbeSourceSet)' \
        'sourceSet(p4D3GameTestSourceSet)' \
        'add(p4D3ProbeSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4D3ProbeSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4D3ProbeSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4D3ProbeSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4D3GameTestSourceSet.implementationConfigurationName, sourceSets.main.output)' \
        'add(p4D3GameTestSourceSet.implementationConfigurationName, p4A3ProbeSourceSet.output)' \
        'add(p4D3GameTestSourceSet.implementationConfigurationName, p4B2ProbeSourceSet.output)' \
        'add(p4D3GameTestSourceSet.implementationConfigurationName, p4C2ProbeSourceSet.output)' \
        'add(p4D3GameTestSourceSet.implementationConfigurationName, p4D3ProbeSourceSet.output)' \
        'testImplementation p4D3ProbeSourceSet.output' \
        "'verifyP4D3Configuration', Exec" \
        "tasks.register('prepareP4D3Worlds', JavaExec)" \
        "tasks.register('p4D3FixedHeapGate')"; do
        require_fixed build.gradle "${task}" \
            "P4-D3-B build contract is missing: ${task}"
    done
    forbid_fixed build.gradle 'testImplementation p4D3GameTestSourceSet.output' \
        'P4-D3-B dedicated runtime must not enter the JUnit test classpath'
    forbid_fixed build.gradle 'runtimeElements.extendsFrom p4D3' \
        'P4-D3-B source-set output must not enter publication variants'

    RUNTIME_MOD_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-runtime.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create runtime-mod fixture'
    sed -n '/^        p4D3HeapProbe {$/,/^        }$/p' \
        build.gradle > "${RUNTIME_MOD_BLOCK}"
    require_fixed_count "${RUNTIME_MOD_BLOCK}" '        p4D3HeapProbe {' 1 \
        'P4-D3-B dedicated runtime block must appear exactly once'
    require_ere_count "${RUNTIME_MOD_BLOCK}" '^[[:space:]]+sourceSet\(' 3 \
        'P4-D3-B dedicated runtime must contain exactly three source sets'
    for task in \
        'sourceSet(sourceSets.main)' \
        'sourceSet(p4D3ProbeSourceSet)' \
        'sourceSet(p4D3GameTestSourceSet)'; do
        require_fixed_count "${RUNTIME_MOD_BLOCK}" "${task}" 1 \
            "P4-D3-B dedicated runtime is missing or duplicates ${task}"
    done
    for task in \
        'sourceSets.test' \
        'p4A3GameTestSourceSet' \
        'p4B2GameTestSourceSet' \
        'p4C2GameTestSourceSet' \
        'p4A3ProbeSourceSet' \
        'p4B2ProbeSourceSet' \
        'p4C2ProbeSourceSet'; do
        forbid_fixed "${RUNTIME_MOD_BLOCK}" "${task}" \
            "P4-D3-B dedicated runtime contains forbidden output ${task}"
    done

    require_ere_count build.gradle \
        '^[[:space:]]+p4D3(Crash(D|E|F|G|H|I|J1)(Restart)?|CombinedHeap(Restart)?)Server[[:space:]]*\{' \
        16 'P4-D3-B must declare exactly sixteen server run configurations'
    require_fixed_count build.gradle \
        'jvmArguments.addAll(p4D3FixedHeapJvmArgs)' 16 \
        'all sixteen P4-D3-B servers must use the fixed-heap argument set'
    require_fixed_count build.gradle \
        "systemProperty 'gramarye.p4d3.runMode'" 16 \
        'all sixteen P4-D3-B servers must select an exact run mode'
    require_fixed_count build.gradle \
        'sourceSet = p4D3GameTestSourceSet' 16 \
        'all sixteen P4-D3-B servers must use the dedicated source set'
    require_fixed_count build.gradle \
        'taskBefore(tasks.named(p4D3GameTestSourceSet.classesTaskName))' 16 \
        'all sixteen P4-D3-B servers must compile only the dedicated holder runtime'
    require_fixed_count build.gradle "tasks.named('runP4D3" 16 \
        'P4-D3-B must bind exactly sixteen server tasks'
    for task in \
        "'-Xms512m'" \
        "'-Xmx1024m'" \
        "'-XX:+ExitOnOutOfMemoryError'" \
        'timeout.set(java.time.Duration.ofSeconds(600))' \
        'timeout.set(java.time.Duration.ofSeconds(300))'; do
        require_fixed build.gradle "${task}" \
            "P4-D3-B fixed-heap/timeout contract is missing: ${task}"
    done

    for task in \
        runP4D3CrashDServer verifyP4D3CrashD \
        runP4D3CrashDRestartServer verifyP4D3CrashDRestart \
        runP4D3CrashEServer verifyP4D3CrashE \
        runP4D3CrashERestartServer verifyP4D3CrashERestart \
        runP4D3CrashFServer verifyP4D3CrashF \
        runP4D3CrashFRestartServer verifyP4D3CrashFRestart \
        runP4D3CrashGServer verifyP4D3CrashG \
        runP4D3CrashGRestartServer verifyP4D3CrashGRestart \
        runP4D3CrashHServer verifyP4D3CrashH \
        runP4D3CrashHRestartServer verifyP4D3CrashHRestart \
        runP4D3CrashIServer verifyP4D3CrashI \
        runP4D3CrashIRestartServer verifyP4D3CrashIRestart \
        runP4D3CrashJ1Server verifyP4D3CrashJ1 \
        runP4D3CrashJ1RestartServer verifyP4D3CrashJ1Restart \
        runP4D3CombinedHeapServer verifyP4D3CombinedHeap \
        runP4D3CombinedHeapRestartServer verifyP4D3CombinedHeapRestart; do
        require_fixed build.gradle "${task}" \
            "P4-D3-B task graph is missing ${task}"
    done

    require_fixed build.gradle 'runP4D3CrashDServer.configure {' \
        'P4-D3-B serial chain is missing its first server configuration'
    require_fixed build.gradle 'dependsOn(prepareP4D3Worlds)' \
        'P4-D3-B first server must depend on world preparation'
    for edge in \
        'verifyP4D3CrashD|runP4D3CrashDServer' \
        'runP4D3CrashDRestartServer|verifyP4D3CrashD' \
        'verifyP4D3CrashDRestart|runP4D3CrashDRestartServer' \
        'runP4D3CrashEServer|verifyP4D3CrashDRestart' \
        'verifyP4D3CrashE|runP4D3CrashEServer' \
        'runP4D3CrashERestartServer|verifyP4D3CrashE' \
        'verifyP4D3CrashERestart|runP4D3CrashERestartServer' \
        'runP4D3CrashFServer|verifyP4D3CrashERestart' \
        'verifyP4D3CrashF|runP4D3CrashFServer' \
        'runP4D3CrashFRestartServer|verifyP4D3CrashF' \
        'verifyP4D3CrashFRestart|runP4D3CrashFRestartServer' \
        'runP4D3CrashGServer|verifyP4D3CrashFRestart' \
        'verifyP4D3CrashG|runP4D3CrashGServer' \
        'runP4D3CrashGRestartServer|verifyP4D3CrashG' \
        'verifyP4D3CrashGRestart|runP4D3CrashGRestartServer' \
        'runP4D3CrashHServer|verifyP4D3CrashGRestart' \
        'verifyP4D3CrashH|runP4D3CrashHServer' \
        'runP4D3CrashHRestartServer|verifyP4D3CrashH' \
        'verifyP4D3CrashHRestart|runP4D3CrashHRestartServer' \
        'runP4D3CrashIServer|verifyP4D3CrashHRestart' \
        'verifyP4D3CrashI|runP4D3CrashIServer' \
        'runP4D3CrashIRestartServer|verifyP4D3CrashI' \
        'verifyP4D3CrashIRestart|runP4D3CrashIRestartServer' \
        'runP4D3CrashJ1Server|verifyP4D3CrashIRestart' \
        'verifyP4D3CrashJ1|runP4D3CrashJ1Server' \
        'runP4D3CrashJ1RestartServer|verifyP4D3CrashJ1' \
        'verifyP4D3CrashJ1Restart|runP4D3CrashJ1RestartServer' \
        'runP4D3CombinedHeapServer|verifyP4D3CrashJ1Restart' \
        'verifyP4D3CombinedHeap|runP4D3CombinedHeapServer' \
        'runP4D3CombinedHeapRestartServer|verifyP4D3CombinedHeap' \
        'verifyP4D3CombinedHeapRestart|runP4D3CombinedHeapRestartServer'; do
        current="${edge%%|*}"
        previous="${edge#*|}"
        require_fixed build.gradle "${current}.configure {" \
            "P4-D3-B serial chain is missing ${current} configuration"
        require_fixed build.gradle "dependsOn(${previous})" \
            "P4-D3-B serial chain is missing ${current} -> ${previous}"
    done
    require_fixed build.gradle 'dependsOn(verifyP4D3CombinedHeapRestart)' \
        'P4-D3-B aggregate must depend on the terminal verifier only'
}

verify_fixture_and_interruption_contract() {
    local marker=''
    local package_path='src/p4D3Probe/java/com/yo1no/gramarye/magic/definition/store'
    for marker in \
        '66_060_348' \
        '2_048' \
        '4_095' \
        '4_096' \
        '1_048_538'; do
        require_any_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${marker}" \
            "P4-D3-B probe sources are missing exact marker ${marker}"
    done
    for marker in \
        'G_REPLAY_APPLIED_PLAYERDATA_NOT_SAVED' \
        'H_CLEAR_IN_MEMORY_NOT_SAVED'; do
        require_any_fixed_in_d3_sources "${marker}" \
            "P4-D3-B sources are missing exact interruption marker ${marker}"
    done
    for marker in \
        CRASH_D_RESTART CRASH_E_RESTART CRASH_F_RESTART CRASH_G_RESTART \
        CRASH_H_RESTART CRASH_I_RESTART CRASH_J1_RESTART COMBINED_RESTART; do
        require_any_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${marker}" \
            "P4-D3-B probe sources are missing paired mode ${marker}"
    done
    require_fixed_count \
        'src/p4D3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4D3MemoryGameTests.java' \
        'Runtime.getRuntime().halt(0)' 2 \
        'P4-D3-B must contain exactly two hard-halt syntax locations'
    [[ "$(count_fixed_in_file_list "${GAME_SOURCE_LIST}" \
            'Runtime.getRuntime().halt(0)')" -eq 2 ]] \
        || fail 'P4-D3-B hard-halt sites escaped the dedicated GameTest holder'
    [[ "$(count_fixed_in_file_list "${PROBE_SOURCE_LIST}" \
            'Runtime.getRuntime().halt(0)')" -eq 0 ]] \
        || fail 'P4-D3-B hard halt appeared in probe code'
    require_fixed_count \
        'src/p4D3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4D3MemoryGameTests.java' \
        '@GameTest(' 1 \
        'P4-D3-B dedicated holder must expose one run-mode dispatcher'
    for marker in \
        'static final long MAX_BYTES = 4_096' \
        'channel.force(true)' \
        'values.stringPropertyNames().equals(KEYS)'; do
        require_fixed "${package_path}/P4D3FixtureManifest.java" "${marker}" \
            "P4-D3-B bounded manifest contract is missing ${marker}"
    done
    for marker in \
        'SkillSavedDataPrimaryIngress.load(' \
        'PendingAttachmentJournalFraming.load(' \
        'sourcePending.contentEquals(' \
        'auditJournalTargets(journal)' \
        'P4D3PlayerProbe.readPlayerdata(' \
        'requireManifestMatches(manifest, actual)'; do
        require_fixed "${package_path}/P4D3FileVerifier.java" "${marker}" \
            "P4-D3-B strict external verifier is missing ${marker}"
    done
}

verify_phase_boundary() {
    local token=''
    for token in \
        'Thread.sleep(' \
        'System.gc(' \
        'java.lang.reflect' \
        'setAccessible(' \
        'sun.misc.Unsafe' \
        '@SuppressWarnings' \
        '.reclaim(' \
        'OfflineRoot' \
        'RootCollector' \
        'RootIndex' \
        'Reconciliation' \
        'CustomPacketPayload' \
        'PayloadRegistrar' \
        'PacketDistributor' \
        'net.minecraft.client' \
        'org.junit'; do
        forbid_fixed_in_file_list "${PROBE_SOURCE_LIST}" "${token}" \
            "P4-D3-B probe code opened a forbidden surface: ${token}"
        forbid_fixed_in_file_list "${GAME_SOURCE_LIST}" "${token}" \
            "P4-D3-B dedicated code opened a forbidden surface: ${token}"
    done
    for token in \
        'Runtime.getRuntime().halt(0)' \
        '@GameTestHolder("gramarye_p4_d3")' \
        'P4D3ProbeMain'; do
        forbid_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" "${token}" \
            "P4-D3-B test-only surface leaked into production: ${token}"
    done
}

verify_ci_job() {
    CI_JOB_BLOCK="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-ci.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create CI job fixture'
    sed -n '/^  p4-d-memory-gates:/,$p' \
        .github/workflows/build.yml > "${CI_JOB_BLOCK}"
    for marker in \
        '  p4-d-memory-gates:' \
        '    name: P4-D memory gates' \
        '      - build' \
        '      - p4-a3-memory-gates' \
        '      - p4-b-memory-gates' \
        '      - p4-c-memory-gates' \
        '    timeout-minutes: 45' \
        './gradlew --no-daemon --console=plain verifyP4D3Configuration' \
        './gradlew --no-daemon --console=plain p4D3FixedHeapGate'; do
        require_fixed "${CI_JOB_BLOCK}" "${marker}" \
            "P4-D memory CI job is missing: ${marker}"
    done
    for marker in \
        'continue-on-error' \
        'allow-failure' \
        '|| true' \
        '--exclude-task' \
        '    if:'; do
        forbid_fixed "${CI_JOB_BLOCK}" "${marker}" \
            "P4-D memory CI job contains an escape: ${marker}"
    done
}

verify_jar_isolation() {
    local jar_path=''
    local source_name=''
    local status=0
    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-jars.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-jar.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    [[ "${status}" -eq 0 ]] || fail 'P4-D3-B verifier could not inspect production JARs'
    [[ -s "${JAR_FILE_LIST}" ]] \
        || fail 'P4-D3-B verifier found no production JAR to inspect'
    while IFS= read -r -d '' jar_path; do
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        [[ "${status}" -eq 0 ]] || fail "jar failed while checking ${jar_path}"
        while IFS= read -r -d '' source_name; do
            source_name="${source_name##*/}"
            source_name="${source_name%.java}"
            forbid_fixed "${JAR_LISTING}" "${source_name}" \
                "P4-D3-B probe fixture leaked into production JAR: ${source_name}"
        done < "${PROBE_SOURCE_LIST}"
        while IFS= read -r -d '' source_name; do
            source_name="${source_name##*/}"
            source_name="${source_name%.java}"
            forbid_fixed "${JAR_LISTING}" "${source_name}" \
                "P4-D3-B dedicated fixture leaked into production JAR: ${source_name}"
        done < "${GAME_SOURCE_LIST}"
        for source_name in \
            P4D3BApiGateTest \
            P4D3FixtureTest; do
            forbid_fixed "${JAR_LISTING}" "${source_name}" \
                "P4-D3-B unit/API fixture leaked into production JAR: ${source_name}"
        done
    done < "${JAR_FILE_LIST}"
}

main() {
    verify_search_helpers
    require_regular_file build.gradle 'P4-D3-B verifier cannot inspect build.gradle'
    require_regular_file .github/workflows/build.yml \
        'P4-D3-B verifier cannot inspect the workflow'
    require_regular_file scripts/verify-p4-d3-configuration.sh \
        'P4-D3-B portable verifier is missing'
    forbid_ere scripts/verify-p4-d3-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-D3-B portable verifier must not invoke a developer-only search tool'

    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-production.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create production source list'
    PROBE_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-probe.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create probe source list'
    GAME_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-d3-game.XXXXXX")" \
        || fail 'P4-D3-B verifier could not create dedicated source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"
    collect_java_files src/p4D3Probe/java "${PROBE_SOURCE_LIST}"
    collect_java_files src/p4D3GameTest/java "${GAME_SOURCE_LIST}"

    verify_production_no_diff
    verify_exact_source_allowlists
    verify_build_contract
    verify_fixture_and_interruption_contract
    verify_phase_boundary
    verify_ci_job
    verify_jar_isolation
    printf '%s\n' \
        'Verified exact P4-D3-B source sets, fixtures, serial tasks, interruption sites, CI, and isolation.'
}

main "$@"
