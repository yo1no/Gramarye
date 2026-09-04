#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT=''
if REPO_ROOT="$(
    cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
)"; then
    :
else
    printf '%s\n' 'P4-A3-B configuration verifier could not resolve the repository root' >&2
    exit 1
fi

SOURCE_FILE_LIST=''
JAR_LISTING=''
HELPER_FIXTURE=''

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

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
        0)
            return 0
            ;;
        1)
            fail "${message}"
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

require_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0

    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0)
            return 0
            ;;
        1)
            fail "${message}"
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

forbid_fixed() {
    local file="$1"
    local needle="$2"
    local message="$3"
    local status=0

    LC_ALL=C grep -Fq -- "${needle}" "${file}" || status=$?
    case "${status}" in
        0)
            fail "${message}"
            ;;
        1)
            return 0
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

forbid_ere() {
    local file="$1"
    local pattern="$2"
    local message="$3"
    local status=0

    LC_ALL=C grep -Eq -- "${pattern}" "${file}" || status=$?
    case "${status}" in
        0)
            fail "${message}"
            ;;
        1)
            return 0
            ;;
        *)
            grep_failed "${file}" "${status}"
            ;;
    esac
}

collect_regular_files() {
    local root="$1"
    local destination="$2"
    local status=0

    LC_ALL=C find "${root}" -type f -print0 > "${destination}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking ${root} (exit ${status})"
    fi
    if [[ ! -s "${destination}" ]]; then
        fail "P4-A3-B configuration verifier could not inspect ${root}: no files found"
    fi
}

forbid_fixed_in_file_list() {
    local file_list="$1"
    local needle="$2"
    local message="$3"
    local file

    while IFS= read -r -d '' file; do
        forbid_fixed "${file}" "${needle}" "${message}"
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

cleanup() {
    if [[ -n "${SOURCE_FILE_LIST}" ]]; then
        rm -f -- "${SOURCE_FILE_LIST}"
    fi
    if [[ -n "${JAR_LISTING}" ]]; then
        rm -f -- "${JAR_LISTING}"
    fi
    if [[ -n "${HELPER_FIXTURE}" ]]; then
        rm -f -- "${HELPER_FIXTURE}"
    fi
}

verify_search_helpers() {
    local missing_output=''
    local forbidden_output=''
    local tool_error_output=''
    local status=0

    HELPER_FIXTURE="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-a3-helper-fixture.XXXXXX")" \
        || fail 'P4-A3-B configuration verifier could not create its helper fixture'
    printf 'present contract\n' > "${HELPER_FIXTURE}"

    require_fixed \
        "${HELPER_FIXTURE}" \
        'present contract' \
        'P4-A3-B verifier self-check lost fixed required matching'
    require_ere \
        "${HELPER_FIXTURE}" \
        '^present contract$' \
        'P4-A3-B verifier self-check lost ERE required matching'
    forbid_fixed \
        "${HELPER_FIXTURE}" \
        'absent contract' \
        'P4-A3-B verifier self-check misclassified an absent fixed pattern'
    forbid_ere \
        "${HELPER_FIXTURE}" \
        '^absent contract$' \
        'P4-A3-B verifier self-check misclassified an absent ERE pattern'

    missing_output="$(
        {
            require_fixed \
                "${HELPER_FIXTURE}" \
                'missing contract' \
                'EXPECTED_MISSING_CONFIGURATION'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 || "${missing_output}" != 'EXPECTED_MISSING_CONFIGURATION' ]]; then
        fail 'P4-A3-B verifier self-check could not distinguish a missing pattern'
    fi

    status=0
    forbidden_output="$(
        {
            forbid_fixed \
                "${HELPER_FIXTURE}" \
                'present contract' \
                'EXPECTED_FORBIDDEN_CONFIGURATION'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 \
        || "${forbidden_output}" != 'EXPECTED_FORBIDDEN_CONFIGURATION' ]]; then
        fail 'P4-A3-B verifier self-check could not detect a forbidden pattern'
    fi

    status=0
    tool_error_output="$(
        {
            require_fixed \
                "${HELPER_FIXTURE}.missing" \
                'present contract' \
                'MUST_NOT_BE_REPORTED_AS_MISSING_CONFIGURATION'
        } 2>&1
    )" || status=$?
    if [[ "${status}" -ne 1 \
        || "${tool_error_output}" != *'grep failed while checking '* \
        || "${tool_error_output}" != *'(exit 2)'* \
        || "${tool_error_output}" == *'MUST_NOT_BE_REPORTED_AS_MISSING_CONFIGURATION'* ]]; then
        fail 'P4-A3-B verifier self-check could not distinguish a grep error'
    fi
}

main() {
    local literal
    local jar_path=''
    local status=0

    cd "${REPO_ROOT}"
    trap cleanup EXIT

    verify_search_helpers
    forbid_ere \
        scripts/verify-p4-a3-b-configuration.sh \
        '(^|[;&|()<>`[:space:]])([^;&|()<>`[:space:]]*/)?r[g]([;&|()<>`[:space:]]|$)' \
        'P4-A3-B configuration verifier must not invoke a non-portable search tool'

    for literal in \
        "sourceSets.create('p4A3Probe')" \
        "sourceSets.create('p4A3GameTest')" \
        "tasks.register('generateP4A3GameTestResources', Sync)" \
        "p4A3ProbeTestSupport" \
        "addModdingDependenciesTo(p4A3ProbeSourceSet)" \
        "addModdingDependenciesTo(p4A3GameTestSourceSet)" \
        "'p4A3HeapProbeManySmall', 'many-small'" \
        "'p4A3HeapProbeNearEntry', 'near-entry'" \
        "'p4A3HeapProbeMixed', 'mixed'" \
        "tasks.register('p4A3HeapProbe')" \
        "tasks.named('runP4A3HeapProbeServer', JavaExec)" \
        "'gramarye_p4_a3'" \
        "Duration.ofSeconds(180)" \
        "Duration.ofSeconds(300)" \
        "'-Xms512m'" \
        "'-Xmx1024m'" \
        "'-XX:+ExitOnOutOfMemoryError'"; do
        require_fixed \
            build.gradle \
            "${literal}" \
            "P4-A3-B configuration check missing ${literal} in build.gradle"
    done

    require_fixed \
        src/p4A3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4A3CarrierGameTests.java \
        '@GameTestHolder("gramarye_p4_a3")' \
        'P4-A3-B configuration check missing the dedicated GameTest holder namespace'
    require_fixed \
        src/p4A3GameTest/java/com/yo1no/gramarye/magic/definition/store/P4A3CarrierGameTests.java \
        'templateNamespace = "gramarye_p4_a3"' \
        'P4-A3-B configuration check missing the dedicated GameTest template namespace'

    for literal in \
        'p4-a3-memory-gates:' \
        'needs: build' \
        "java-version: '21'" \
        'timeout-minutes: 15' \
        'timeout-minutes: 5' \
        'timeout-minutes: 6' \
        './gradlew --no-daemon --console=plain verifyP4A3BConfiguration' \
        './gradlew --no-daemon --console=plain p4A3HeapProbe' \
        './gradlew --no-daemon --console=plain runP4A3HeapProbeServer'; do
        require_fixed \
            .github/workflows/build.yml \
            "${literal}" \
            "P4-A3-B configuration check missing ${literal} in .github/workflows/build.yml"
    done

    forbid_fixed \
        .github/workflows/build.yml \
        'continue-on-error' \
        'P4-A3-B memory Gate must not allow failure'
    forbid_fixed \
        .github/workflows/build.yml \
        'allow-failure' \
        'P4-A3-B memory Gate must not allow failure'

    SOURCE_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-a3-source-files.XXXXXX")" \
        || fail 'P4-A3-B configuration verifier could not create its source-file list'
    collect_regular_files src/main/java "${SOURCE_FILE_LIST}"

    for literal in \
        'P4A3HeapProbe' \
        'P4A3CarrierGameTests' \
        'P4B2ProbeMain' \
        'P4B2MemoryGameTests' \
        'gramarye_p4_b2' \
        'P4C2Probe' \
        'P4C2MemoryGameTests' \
        '"gramarye_p4_c2"' \
        'P4D3' \
        '"gramarye_p4_d3"'; do
        forbid_fixed_in_file_list \
            "${SOURCE_FILE_LIST}" \
            "${literal}" \
            'P4-A3-B or reviewed P4-B2-B probe code leaked into production Java sources'
    done
    # P4-B2-A now legitimately owns the SavedData/cache lifecycle and compressed-file ceiling.
    # Keep rejecting the JDK gzip path and any custom production gzip writer: B2-A must use the
    # reviewed non-concatenating Commons Compress reader and the platform-owned writer.
    for literal in \
        'GZIPInputStream' \
        'GZIPOutputStream'; do
        forbid_fixed_in_file_list \
            "${SOURCE_FILE_LIST}" \
            "${literal}" \
            'P4-B2-A production code bypassed the reviewed strict gzip boundary'
    done

    # P4-C2-A phase-local: exact Attachment registration, controlled ServerPlayer mutation,
    # prepared transition, and per-player roots are reviewed by the portable C2-A verifier.
    # D1/D2 composition and the single D3-A login recovery owner are reviewed by their own
    # portable gates. Manual clone hooks, offline roots, and unreviewed networking remain
    # forbidden.
    for literal in \
        'OfflineRoot' \
        'PacketDistributor'; do
        forbid_fixed_in_file_list \
            "${SOURCE_FILE_LIST}" \
            "${literal}" \
            'Unreviewed later lifecycle/root/network surface appeared in production'
    done
    forbid_fixed_in_file_list_except \
        "${SOURCE_FILE_LIST}" \
        'CustomPacketPayload' \
        'CustomPacketPayload escaped the exact P7-S2 payload owner allowlist' \
        'src/main/java/com/yo1no/gramarye/magic/network/CastIntentPayload.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/IntentAckPayload.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/PlayerManaSyncPayload.java' \
        'src/main/java/com/yo1no/gramarye/magic/network/SkillCooldownSyncPayload.java'
    forbid_fixed_in_file_list_except \
        "${SOURCE_FILE_LIST}" \
        'PayloadRegistrar' \
        'PayloadRegistrar escaped the exact P7-S2 registrar owner allowlist' \
        'src/main/java/com/yo1no/gramarye/magic/network/P7PayloadRegistrar.java'
    require_fixed \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'PlayerEvent.PlayerLoggedInEvent' \
        'P4-D3-A reviewed login recovery event owner is missing'
    while IFS= read -r -d '' file; do
        if [[ "${file}" != \
                'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' ]]; then
            forbid_fixed "${file}" 'PlayerEvent' \
                'PlayerEvent escaped the exact P4-D3-A recovery-service allowlist'
        fi
    done < "${SOURCE_FILE_LIST}"
    test -x scripts/verify-p4-c2-a-configuration.sh \
        || fail 'P4-C2-A portable configuration verifier is missing or not executable'

    test -f build/classes/java/p4A3Probe/com/yo1no/gramarye/magic/definition/store/P4A3HeapProbeMain.class
    test -f build/classes/java/p4A3GameTest/com/yo1no/gramarye/magic/definition/store/P4A3CarrierGameTests.class
    test -f build/resources/p4A3GameTest/data/gramarye_p4_a3/structure/p4_a3_probe.nbt

    jar_path="$(LC_ALL=C find \
        build/libs \
        -maxdepth 1 \
        -type f \
        -name 'gramarye-*.jar' \
        -print \
        -quit)" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking build/libs (exit ${status})"
    fi
    if [[ -z "${jar_path}" ]]; then
        fail 'P4-A3-B configuration check could not find the production JAR'
    fi

    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-a3-jar-listing.XXXXXX")" \
        || fail 'P4-A3-B configuration verifier could not create its JAR listing'
    status=0
    jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "jar failed while checking ${jar_path} (exit ${status})"
    fi
    for literal in \
        'P4A3' \
        'p4A3Probe' \
        'p4A3GameTest' \
        'P4B2' \
        'p4B2Probe' \
        'p4B2GameTest' \
        'gramarye_p4_b2' \
        'P4C2' \
        'p4C2Probe' \
        'p4C2GameTest' \
        'gramarye_p4_c2' \
        'P4D3' \
        'p4D3Probe' \
        'p4D3GameTest' \
        'gramarye_p4_d3'; do
        forbid_fixed \
            "${JAR_LISTING}" \
            "${literal}" \
            'P4-A3-B or reviewed P4-B2-B probe classes/resources leaked into the production JAR'
    done
    while IFS= read -r jar_entry; do
        if [[ "${jar_entry}" == \
                'com/yo1no/gramarye/magic/definition/store/SkillSavedDataLifecycleGameTests.class' \
                || "${jar_entry}" == \
                'com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests.class' \
                || "${jar_entry}" == \
                com/yo1no/gramarye/magic/definition/store/SkillSubmissionRecoveryGameTests\$*.class ]]; then
            continue
        fi
        if [[ "${jar_entry}" =~ (^|/)com/yo1no/gramarye/magic/definition/store/[^/]*(Test|Fixture|Fake|Dummy|Noop|Stub)[^/]*\.class$ ]]; then
            fail 'P4-B1/P4-B2-A test fixtures leaked into the production JAR'
        fi
    done < "${JAR_LISTING}"

    bash scripts/verify-p4-d1-configuration.sh
    printf 'Verified P4-A3-B task, source-set, CI, and JAR isolation contracts.\n'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
