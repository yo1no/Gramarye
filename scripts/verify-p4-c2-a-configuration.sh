#!/usr/bin/env bash
set -euo pipefail

# Keep this verifier independent of developer-only search tools and shell aliases while retaining
# the JDK selected by Gradle/CI for production-JAR inspection.
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

for required_tool in bash grep find mktemp rm jar dirname pwd; do
    command -v "${required_tool}" >/dev/null 2>&1 \
        || fail "P4-C2-A configuration verifier cannot find required tool: ${required_tool}"
done

REPO_ROOT=''
if REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"; then
    :
else
    fail 'P4-C2-A configuration verifier could not resolve the repository root'
fi
cd "${REPO_ROOT}"

PRODUCTION_SOURCE_LIST=''
C2_SOURCE_LIST=''
JAR_FILE_LIST=''
JAR_LISTING=''
PLAYER_SOURCE_LIST=''

cleanup() {
    for file in \
        "${PRODUCTION_SOURCE_LIST}" \
        "${C2_SOURCE_LIST}" \
        "${JAR_FILE_LIST}" \
        "${JAR_LISTING}" \
        "${PLAYER_SOURCE_LIST}"; do
        if [[ -n "${file}" ]]; then
            rm -f -- "${file}"
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
    LC_ALL=C find "${root}" -type f -name '*.java' -print0 > "${destination}" || status=$?
    if [[ "${status}" -ne 0 || ! -s "${destination}" ]]; then
        fail "P4-C2-A configuration verifier could not inspect Java sources under ${root}"
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

forbid_fixed_outside() {
    local file_list="$1"
    local needle="$2"
    local allowed_one="$3"
    local allowed_two="${4:-}"
    local message="$5"
    local file=''
    while IFS= read -r -d '' file; do
        if [[ "${file}" == "${allowed_one}" \
                || ( -n "${allowed_two}" && "${file}" == "${allowed_two}" ) ]]; then
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

verify_exact_sources_and_registration() {
    local package_path='src/main/java/com/yo1no/gramarye/magic/definition/player'
    local registration="${package_path}/PlayerSkillAttachments.java"
    local service="${package_path}/PlayerSkillAttachmentService.java"
    local game_tests="${package_path}/PlayerSkillAttachmentGameTests.java"
    local serialize_line=''
    local death_line=''

    PRODUCTION_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-production.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create production source list'
    C2_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-reviewed.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create reviewed source list'
    collect_java_files src/main/java "${PRODUCTION_SOURCE_LIST}"

    for source in \
        PlayerSkillAttachmentBuildResult \
        PlayerSkillAttachmentGameTests \
        PlayerSkillAttachmentService \
        PlayerSkillAttachments \
        ObservedPlayerSkillAttachment; do
        require_regular_file \
            "${package_path}/${source}.java" \
            "P4-C2-A reviewed production source is missing: ${source}.java"
        printf '%s\0' "${package_path}/${source}.java" >> "${C2_SOURCE_LIST}"
    done

    for literal in \
        'DeferredRegister<AttachmentType<?>>' \
        'DeferredHolder<' \
        'NeoForgeRegistries.Keys.ATTACHMENT_TYPES' \
        '"player_skills"' \
        'PlayerSkillAttachmentPersistenceBridge::freshEmptyReady' \
        '.serialize(PlayerSkillAttachmentSerializer.INSTANCE)' \
        '.copyOnDeath()' \
        '.build()'; do
        require_fixed "${registration}" "${literal}" \
            "P4-C2-A registration lost reviewed fragment ${literal}"
    done
    require_fixed_count "${registration}" '.serialize(PlayerSkillAttachmentSerializer.INSTANCE)' 1 \
        'P4-C2-A registration must wire the C1 serializer exactly once'
    require_fixed_count "${registration}" '.copyOnDeath()' 1 \
        'P4-C2-A registration must enable copyOnDeath exactly once'
    serialize_line="$(LC_ALL=C grep -Fn -- '.serialize(PlayerSkillAttachmentSerializer.INSTANCE)' "${registration}")"
    death_line="$(LC_ALL=C grep -Fn -- '.copyOnDeath()' "${registration}")"
    serialize_line="${serialize_line%%:*}"
    death_line="${death_line%%:*}"
    if [[ "${serialize_line}" -ge "${death_line}" ]]; then
        fail 'P4-C2-A registration must serialize before copyOnDeath'
    fi
    forbid_fixed "${registration}" '.sync(' \
        'P4-C2-A permanent Attachment must remain server-only and unsynchronized'

    for literal in \
        'AttachmentType' \
        'DeferredRegister<AttachmentType<?>>' \
        'DeferredHolder' \
        'NeoForgeRegistries.Keys.ATTACHMENT_TYPES' \
        '.copyOnDeath()'; do
        forbid_fixed_outside \
            "${PRODUCTION_SOURCE_LIST}" "${literal}" "${registration}" '' \
            'Attachment registration surface escaped its unique owner'
    done
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '"player_skills"' "${registration}" "${game_tests}" \
        'stable player skill Attachment ID escaped registration/tests'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '.getData(' "${service}" "${game_tests}" \
        'player Attachment getData escaped the controlled service/GameTest seam'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" '.setData(' "${service}" '' \
        'player Attachment setData escaped the controlled service'
    forbid_fixed_in_file_list \
        "${PRODUCTION_SOURCE_LIST}" '.removeData(' \
        'production must never remove the permanent player skill Attachment entry'
}

verify_phase_bounds_and_normal_tests() {
    local game_tests='src/main/java/com/yo1no/gramarye/magic/definition/player/PlayerSkillAttachmentGameTests.java'
    local normal_count=''

    for literal in \
        'PendingAttachmentJournal' \
        'SkillDefinitionSubmissionService' \
        'SkillDefinitionStore' \
        'SkillRetentionRootSnapshot' \
        'OfflineRoot' \
        'RootCollector' \
        'RootIndex' \
        'Reconciliation' \
        'CustomPacketPayload' \
        'StreamCodec' \
        'PayloadRegistrar' \
        'PacketDistributor' \
        'net.minecraft.client' \
        'PlayerEvent.Clone' \
        '.commit(' \
        '.reclaim(' \
        '.sync('; do
        forbid_fixed_in_file_list \
            "${C2_SOURCE_LIST}" "${literal}" \
            'P4-C2-A reviewed source contains later/forbidden surface'
    done
    forbid_ere_in_file_list \
        "${C2_SOURCE_LIST}" \
        'Long\.MAX_VALUE|long[[:space:]]+(expected|target|mutation)[A-Za-z]*Generation' \
        'P4-C2-A generation must remain int/Integer.MAX_VALUE'

    normal_count="$(count_fixed_in_file_list "${PRODUCTION_SOURCE_LIST}" '@GameTest(')"
    if [[ "${normal_count}" -ne 12 ]]; then
        fail "P4-C2-A plus reviewed P4-D3-A normal GameTest count must be twelve (found ${normal_count})"
    fi
    require_fixed_count "${game_tests}" '@GameTest(' 2 \
        'P4-C2-A normal holder must contain exactly two GameTests'
    require_fixed "${game_tests}" '@GameTestHolder(Gramarye.MOD_ID)' \
        'P4-C2-A normal holder lost the production GameTest namespace'

    require_regular_file \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'P4-D3-A reviewed recovery service is missing'
    require_fixed \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' \
        'PlayerEvent.PlayerLoggedInEvent' \
        'P4-D3-A reviewed login recovery event owner is missing'
    forbid_fixed_outside \
        "${PRODUCTION_SOURCE_LIST}" 'PlayerEvent' \
        'src/main/java/com/yo1no/gramarye/magic/definition/submission/SkillSubmissionRecoveryService.java' '' \
        'PlayerEvent escaped the exact P4-D3-A recovery-service allowlist'

    for literal in \
        "sourceSets.create('p4C2Probe')" \
        "sourceSets.create('p4C2GameTest')" \
        "tasks.register('p4C2FixedHeapGate')"; do
        require_fixed build.gradle "${literal}" \
            "P4-C2-A boundary lost reviewed test-only C2-B marker ${literal}"
    done
    require_fixed .github/workflows/build.yml 'p4-c-memory-gates:' \
        'P4-C2-A boundary lost the reviewed P4-C memory job'
    [[ -d src/p4C2Probe/java && -d src/p4C2GameTest/java ]] \
        || fail 'P4-C2-B isolated Java source roots are missing'
}

verify_generation_owner() {
    local generation='src/main/java/com/yo1no/gramarye/magic/definition/player/MutationGeneration.java'
    local file=''
    PLAYER_SOURCE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-player.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create player source list'
    collect_java_files \
        src/main/java/com/yo1no/gramarye/magic/definition/player \
        "${PLAYER_SOURCE_LIST}"
    while IFS= read -r -d '' file; do
        if [[ "${file}" == "${generation}" ]]; then
            continue
        fi
        forbid_ere \
            "${file}" \
            '(current|expected|mutation)[A-Za-z]*Generation[[:space:]]*\+[[:space:]]*1|current[[:space:]]*\+[[:space:]]*1' \
            'generation successor arithmetic escaped MutationGeneration'
    done < "${PLAYER_SOURCE_LIST}"
    require_fixed "${generation}" 'current == Integer.MAX_VALUE' \
        'MutationGeneration lost the checked exhaustion boundary'
    require_fixed "${generation}" 'OptionalInt.of(current + 1)' \
        'MutationGeneration lost the sole checked successor arithmetic'
    rm -f -- "${PLAYER_SOURCE_LIST}"
    PLAYER_SOURCE_LIST=''
}

verify_production_jar() {
    local jar_path=''
    local jar_count=0
    local status=0

    JAR_FILE_LIST="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-jars.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create JAR list'
    JAR_LISTING="$(mktemp "${TMPDIR:-/tmp}/gramarye-p4-c2-a-jar-listing.XXXXXX")" \
        || fail 'P4-C2-A verifier could not create JAR listing'
    LC_ALL=C find build/libs -maxdepth 1 -type f -name 'gramarye-*.jar' -print0 \
        > "${JAR_FILE_LIST}" || status=$?
    if [[ "${status}" -ne 0 ]]; then
        fail "find failed while checking build/libs (exit ${status})"
    fi
    while IFS= read -r -d '' jar_path; do
        jar_count=$((jar_count + 1))
        status=0
        jar tf "${jar_path}" > "${JAR_LISTING}" || status=$?
        if [[ "${status}" -ne 0 ]]; then
            fail "jar failed while checking ${jar_path} (exit ${status})"
        fi
        for class_name in \
            PlayerSkillAttachmentBuildResult \
            PlayerSkillAttachmentGameTests \
            PlayerSkillAttachmentService \
            PlayerSkillAttachments \
            ObservedPlayerSkillAttachment; do
            require_fixed \
                "${JAR_LISTING}" \
                "com/yo1no/gramarye/magic/definition/player/${class_name}.class" \
                "P4-C2-A production JAR lacks reviewed class ${class_name}"
        done
        for literal in \
            p4C2Probe p4C2GameTest gramarye_p4_c2 \
            P4D3 p4D3Probe p4D3GameTest gramarye_p4_d3; do
            forbid_fixed "${JAR_LISTING}" "${literal}" \
                "P4-C2-B fixture leaked into production JAR (${literal})"
        done
    done < "${JAR_FILE_LIST}"
    if [[ "${jar_count}" -lt 1 ]]; then
        fail 'P4-C2-A configuration verifier could not find a production JAR'
    fi
}

main() {
    verify_exact_sources_and_registration
    verify_phase_bounds_and_normal_tests
    verify_generation_owner
    verify_production_jar
    bash scripts/verify-p4-d1-configuration.sh
    printf '%s\n' 'Verified P4-C2-A registration, ownership, lifecycle, phase, and JAR contracts.'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
    main "$@"
fi
